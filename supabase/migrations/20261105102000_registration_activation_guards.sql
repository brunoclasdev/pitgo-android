create function private.registration_activation_guard() returns trigger language plpgsql security definer set search_path='' as $$
declare uid uuid; mode text;
begin
 if not private.registration_enabled() then return new; end if;
 if tg_table_name='profiles' then uid:=new.id; mode:='CLIENT'; else uid:=new.account_id; mode:='PROVIDER'; end if;
 if private.registration_staff() and new.active and not old.active and not exists(select 1 from private.registration_cases where user_id=uid and account_type=mode and (status='APPROVED' or not verification_required)) then
 raise exception using errcode='42501',message='REGISTRATION_REQUIRED'; end if;
 return new;
end $$;
create trigger registration_profile_activation before update on public.profiles for each row execute function private.registration_activation_guard();
create trigger registration_provider_activation before update on public.providers for each row execute function private.registration_activation_guard();


-- Preserve the deployed provider-kind, central-pricing and address-proof validations.
CREATE OR REPLACE FUNCTION public.create_provider_registration(p_provider jsonb, p_address jsonb, p_offers jsonb)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  current_user_id uuid := auth.uid();
  new_provider_id uuid := gen_random_uuid();
  proof private.provider_geocode_proofs%rowtype;
  proof_token text := p_address ->> 'verification_token';
  commercial_kind text := nullif(upper(p_provider ->> 'provider_kind'),'');
  provider_mode text := upper(coalesce(p_provider ->> 'location_mode',''));
  normalized_neighborhood text := btrim(coalesce(
    p_address ->> 'neighborhood', p_address ->> 'neighbornhood', ''
  ));
  offer_count integer;
begin
  if current_user_id is null or not private.has_account_type('PROVIDER') then
    raise exception using errcode = '42501', message = 'PROVIDER_REQUIRED';
  end if;
  perform pg_advisory_xact_lock(hashtextextended(current_user_id::text,9010));
  if exists (select 1 from public.providers where account_id=current_user_id) then
    if private.registration_enabled() and exists(select 1 from private.registration_cases c join private.registration_submissions s on s.id=c.current_submission_id where c.user_id=current_user_id and c.account_type='PROVIDER' and c.status in ('INCOMPLETE','CORRECTION_REQUESTED') and s.state='DRAFT') then
      select id into new_provider_id from public.providers where account_id=current_user_id;
    else
      select count(*) into offer_count from public.provider_services o join public.providers p on p.id=o.provider_id where p.account_id=current_user_id;
      return jsonb_build_object('outcome','ALREADY_REGISTERED','offer_count',offer_count);
    end if;
  end if;
  if commercial_kind is not null and commercial_kind not in ('PITTEC','GUINCHO','STORE') then
    raise exception using errcode='22023', message='INVALID_PROVIDER_KIND';
  end if;
  if commercial_kind is not null then
    provider_mode := case commercial_kind when 'PITTEC' then 'PITTEC' when 'GUINCHO' then 'MOBILE' else 'FIXED' end;
  end if;
  if commercial_kind in ('PITTEC','GUINCHO') then
    -- Compatibility values only: current tariffs are resolved from central configuration.
    select coalesce(jsonb_agg(item || jsonb_build_object('price','0','duration_minutes',60,'is_free',true)),'[]'::jsonb)
      into p_offers from jsonb_array_elements(coalesce(p_offers,'[]'::jsonb)) item;
  elsif commercial_kind='STORE' then
    p_offers := '[]'::jsonb;
  end if;
  if provider_mode not in ('FIXED','MOBILE','PITTEC') then
    raise exception using errcode = '22023', message = 'INVALID_PROVIDER_MODE';
  end if;
  if commercial_kind='GUINCHO' and (
    regexp_replace(upper(coalesce(p_provider->'vehicle'->>'plate','')),'[^A-Z0-9]','','g') !~ '^[A-Z]{3}[0-9][A-Z0-9][0-9]{2}$'
    or length(btrim(coalesce(p_provider->'vehicle'->>'make',''))) not between 1 and 100
    or length(btrim(coalesce(p_provider->'vehicle'->>'model',''))) not between 1 and 100
    or length(btrim(coalesce(p_provider->'vehicle'->>'color',''))) not between 1 and 60) then
    raise exception using errcode='22023',message='VEHICLE_REQUIRED';
  end if;
  if proof_token is null or proof_token !~ '^[A-Za-z0-9_-]{43,128}$' then
    raise exception using errcode = '22023', message = 'LOCATION_VERIFICATION_REQUIRED';
  end if;
  select candidate.* into proof
  from private.provider_geocode_proofs as candidate
  where candidate.account_id = current_user_id
    and candidate.token_hash = extensions.digest(proof_token,'sha256')
  for update;
  if proof.id is null or proof.used_at is not null or proof.expires_at <= now()
    or (proof.device_distance_m > 500 and not proof.mismatch_confirmed) then
    raise exception using errcode = '22023', message = 'LOCATION_VERIFICATION_UNAVAILABLE';
  end if;
  if exists (select 1 from unnest(array['street','number','neighborhood','city','state','zip_code','country']) field
    where btrim(coalesce(proof.normalized_address ->> field,'')) is distinct from btrim(coalesce(p_address ->> field,''))) then
    raise exception using errcode='22023', message='ADDRESS_CHANGED';
  end if;
  if normalized_neighborhood = '' then
    raise exception using errcode = '22023', message = 'INVALID_ADDRESS';
  end if;
  if p_offers is null or jsonb_typeof(p_offers) <> 'array'
    or (jsonb_array_length(p_offers) = 0 and commercial_kind is distinct from 'STORE') then
    raise exception using errcode = '22023', message = 'OFFER_REQUIRED';
  end if;
  if (select count(*) <> count(distinct (item ->> 'service_id')::uuid)
      from jsonb_array_elements(p_offers) as item) then
    raise exception using errcode = '22023', message = 'DUPLICATE_SERVICE';
  end if;

  -- Lock both entities through the insert: catalog changes cannot invalidate this save.
  perform category.id from public.service_categories category
    where category.id in (select service.category_id from public.services service
      join jsonb_array_elements(p_offers) item on service.id = (item ->> 'service_id')::uuid)
    order by category.id for share;
  perform service.id from public.services service
    join jsonb_array_elements(p_offers) item on service.id = (item ->> 'service_id')::uuid
    order by service.id for share of service;
  if exists (
    select 1 from jsonb_array_elements(p_offers) item
    left join public.services service on service.id = (item ->> 'service_id')::uuid
    left join public.service_categories category on category.id = service.category_id
    where service.id is null or not service.active or category.id is null or not category.active
  ) then
    raise exception using errcode = 'P0002', message = 'CATALOG_CHANGED';
  end if;
  if provider_mode = 'FIXED' and commercial_kind is distinct from 'STORE' and (
    (select count(distinct service.category_id) from public.services service
      join jsonb_array_elements(p_offers) item on service.id = (item ->> 'service_id')::uuid) <> 1
    or exists (select 1 from public.services service
      join jsonb_array_elements(p_offers) item on service.id = (item ->> 'service_id')::uuid
      where service.dispatch_kind <> 'BROWSE')
  ) then
    raise exception using errcode = '22023', message = 'INVALID_OFFER';
  end if;
  if provider_mode in ('MOBILE','PITTEC') and (
    jsonb_array_length(p_offers) <> 1 or not exists (
      select 1 from public.services service
      where service.id = (p_offers -> 0 ->> 'service_id')::uuid
        and service.dispatch_kind = case provider_mode
          when 'MOBILE' then 'MOBILE_SEQUENTIAL' else 'PITTEC_AUTO_SEQUENTIAL' end
    )
  ) then
    raise exception using errcode = '22023', message = 'INVALID_OFFER';
  end if;

  if exists (
    select 1
    from jsonb_array_elements(p_offers) as item
    left join public.services as service on service.id = (item ->> 'service_id')::uuid
    left join public.service_categories as category on category.id = service.category_id
    where service.id is null or not service.active or category.id is null or not category.active
      or coalesce(item ->> 'duration_minutes','') !~ '^[0-9]+$'
      or (item ->> 'duration_minutes')::integer not between 1 and 10080
      or coalesce(item ->> 'price','') !~ '^[0-9]+(\.[0-9]{1,2})?$'
      or (coalesce((item ->> 'is_free')::boolean,false)
        and (item ->> 'price')::numeric <> 0)
      or (not coalesce((item ->> 'is_free')::boolean,false)
        and (item ->> 'price')::numeric <= 0)
  ) then
    raise exception using errcode = '22023', message = 'INVALID_OFFER';
  end if;

  insert into public.providers(
    id,account_id,name,description,email,phone,cnpj,location_mode,provider_kind,active,available
  ) values (
    new_provider_id,current_user_id,
    btrim(coalesce(p_provider ->> 'name','')),
    nullif(btrim(coalesce(p_provider ->> 'description','')),''),
    lower(btrim(coalesce(p_provider ->> 'email',''))),
    regexp_replace(coalesce(p_provider ->> 'phone',''),'[^0-9]','','g'),
    regexp_replace(upper(coalesce(p_provider ->> 'cnpj','')),'[^0-9A-Z]','','g'),
    provider_mode,commercial_kind,true,false
  ) on conflict(id) do update set name=excluded.name,description=excluded.description,email=excluded.email,phone=excluded.phone,cnpj=excluded.cnpj,location_mode=excluded.location_mode,provider_kind=excluded.provider_kind;
  delete from public.provider_categories where provider_id=new_provider_id;
  delete from public.provider_services where provider_id=new_provider_id;
  delete from public.addresses where provider_id=new_provider_id;
  if commercial_kind='GUINCHO' then
    insert into public.provider_vehicles(provider_id,plate,make,model,color,guincho_size_id)
    values(new_provider_id,regexp_replace(upper(p_provider->'vehicle'->>'plate'),'[^A-Z0-9]','','g'),
      btrim(p_provider->'vehicle'->>'make'),btrim(p_provider->'vehicle'->>'model'),btrim(p_provider->'vehicle'->>'color'), (p_provider->'vehicle'->>'guincho_size_id')::uuid) on conflict do nothing;
  end if;
  insert into public.addresses(
    provider_id,street,number,neighborhood,zip_code,city,state,country,complement,
    latitude,longitude,address_point,geocode_place_id,geocoded_at,geocode_expires_at,
    location_verified_at,verification_distance_m,mismatch_confirmed
  ) values (
    new_provider_id,
    btrim(coalesce(p_address ->> 'street','')),
    btrim(coalesce(p_address ->> 'number','')),
    normalized_neighborhood,
    regexp_replace(coalesce(p_address ->> 'zip_code',''),'[^0-9]','','g'),
    btrim(coalesce(p_address ->> 'city','')),
    upper(btrim(coalesce(p_address ->> 'state',''))),'BR',
    nullif(btrim(coalesce(p_address ->> 'complement','')),''),
    proof.latitude,proof.longitude,
    extensions.st_setsrid(extensions.st_makepoint(proof.longitude,proof.latitude),4326)::extensions.geography,
    proof.place_id,proof.geocoded_at,proof.geocoded_at + interval '30 days',now(),
    proof.device_distance_m,proof.mismatch_confirmed
  );
  insert into public.provider_services(
    provider_id,service_id,price,duration_minutes,description,is_free
  ) select
    new_provider_id,(item ->> 'service_id')::uuid,(item ->> 'price')::numeric,
    (item ->> 'duration_minutes')::integer,
    nullif(btrim(coalesce(item ->> 'description','')),''),
    coalesce((item ->> 'is_free')::boolean,false)
  from jsonb_array_elements(p_offers) as item;
  insert into public.provider_categories(provider_id,category_id)
  select distinct new_provider_id, service.category_id
  from jsonb_array_elements(p_offers) as item
  join public.services as service on service.id = (item ->> 'service_id')::uuid;
  update private.provider_geocode_proofs set used_at = now() where id = proof.id;
  return jsonb_build_object(
    'outcome','CREATED','provider_id',new_provider_id,
    'offer_count',jsonb_array_length(p_offers)
  );
end;
$function$
;
