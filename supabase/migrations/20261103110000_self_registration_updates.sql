begin;
-- Only the authenticated owner can read or change these registration fields.
create function public.get_my_registration() returns jsonb language plpgsql stable security definer set search_path='' as $$
declare p public.providers%rowtype; c public.profiles%rowtype; address jsonb;
begin
 if auth.uid() is null then raise exception using errcode='42501',message='SESSION_REQUIRED'; end if;
 if private.has_account_type('PROVIDER') then
  select * into p from public.providers where account_id=auth.uid() and active;
  if p.id is null then raise exception using errcode='42501',message='REGISTRATION_UNAVAILABLE'; end if;
  select jsonb_build_object('street',a.street,'number',a.number,'neighborhood',a.neighborhood,'zip_code',a.zip_code,
   'city',a.city,'state',a.state,'complement',a.complement,'updated_at',a.updated_at) into address from public.addresses a where a.provider_id=p.id;
  return jsonb_build_object('owner_id',auth.uid(),'account_type','PROVIDER','name',p.name,'phone',p.phone,'email',p.email,
   'document',p.cnpj,'description',p.description,'provider_kind',p.provider_kind,'updated_at',p.updated_at,'address',address);
 elsif private.has_account_type('CLIENT') then
  select * into c from public.profiles where id=auth.uid() and active;
  if c.id is null then raise exception using errcode='42501',message='REGISTRATION_UNAVAILABLE'; end if;
  select jsonb_build_object('street',a.street,'number',a.number,'neighborhood',a.neighborhood,'zip_code',a.zip_code,
   'city',a.city,'state',a.state,'complement',a.complement,'updated_at',a.updated_at) into address from public.client_addresses a where a.client_id=c.id;
  return jsonb_build_object('owner_id',auth.uid(),'account_type','CLIENT','name',c.name,'phone',c.phone,'email',c.email,
   'document',coalesce(c.cpf,c.cnpj),'description',null,'provider_kind',null,'updated_at',c.updated_at,'address',address);
 end if;
 raise exception using errcode='42501',message='REGISTRATION_UNAVAILABLE';
end $$;
create function public.update_my_registration(p_expected_updated_at timestamptz,p_name text,p_phone text,
 p_description text default null,p_address jsonb default null,p_address_expected_updated_at timestamptz default null,p_verification_token text default null)
returns jsonb language plpgsql security definer set search_path='' as $$
declare p public.providers%rowtype; c public.profiles%rowtype; old_address_time timestamptz;
 proof private.provider_geocode_proofs%rowtype; street text; number text; neighborhood text; postal_code text; city text; state text; complement text;
 normalized_phone text:=regexp_replace(coalesce(p_phone,''),'[^0-9]','','g');
begin
 if auth.uid() is null then raise exception using errcode='42501',message='SESSION_REQUIRED'; end if;
 if p_expected_updated_at is null or p_name is null or length(btrim(p_name)) not between 1 and 150
 or normalized_phone !~ '^[0-9]{10,11}$' or not private.is_valid_brazilian_phone(normalized_phone)
 or length(coalesce(p_description,''))>2000 then raise exception using errcode='22023',message='INVALID_REGISTRATION'; end if;
 if p_address is not null then
  if jsonb_typeof(p_address)<>'object' then raise exception using errcode='22023',message='INVALID_ADDRESS'; end if;
  street:=btrim(coalesce(p_address->>'street','')); number:=btrim(coalesce(p_address->>'number',''));
  neighborhood:=btrim(coalesce(p_address->>'neighborhood','')); postal_code:=regexp_replace(coalesce(p_address->>'zip_code',''),'[^0-9]','','g');
  city:=btrim(coalesce(p_address->>'city','')); state:=upper(btrim(coalesce(p_address->>'state',''))); complement:=nullif(btrim(coalesce(p_address->>'complement','')),'');
  if length(street) not between 1 and 160 or length(number) not between 1 and 40 or length(neighborhood) not between 1 and 100
   or postal_code !~ '^[0-9]{8}$' or length(city) not between 1 and 100 or state<>all(array['AC','AL','AP','AM','BA','CE','DF','ES','GO','MA','MT','MS','MG','PA','PB','PR','PE','PI','RJ','RN','RS','RO','RR','SC','SP','SE','TO'])
   or length(coalesce(complement,''))>160 then raise exception using errcode='22023',message='INVALID_ADDRESS'; end if;
 end if;
 -- Shared lock with registration creation prevents concurrent owner-level mutations.
 perform pg_advisory_xact_lock(hashtextextended(auth.uid()::text,9010));
 if private.has_account_type('PROVIDER') then
  select * into p from public.providers where account_id=auth.uid() and active for update;
  if p.id is null then raise exception using errcode='42501',message='REGISTRATION_UNAVAILABLE'; end if;
  if p.updated_at is distinct from p_expected_updated_at then raise exception using errcode='40001',message='REGISTRATION_CONFLICT'; end if;
  if p_address is not null then
   select updated_at into old_address_time from public.addresses where provider_id=p.id for update;
   if old_address_time is distinct from p_address_expected_updated_at then raise exception using errcode='40001',message='REGISTRATION_CONFLICT'; end if;
   if p_verification_token is null or p_verification_token !~ '^[A-Za-z0-9_-]{43,128}$' then raise exception using errcode='22023',message='ADDRESS_VERIFICATION_REQUIRED'; end if;
   select * into proof from private.provider_geocode_proofs where account_id=auth.uid() and token_hash=extensions.digest(p_verification_token,'sha256') for update;
   if proof.id is null or proof.used_at is not null or proof.expires_at<=now() then raise exception using errcode='22023',message='ADDRESS_VERIFICATION_REQUIRED'; end if;
   if (street,number,neighborhood,postal_code,city,state) is distinct from
    (btrim(proof.normalized_address->>'street'),btrim(proof.normalized_address->>'number'),btrim(proof.normalized_address->>'neighborhood'),proof.normalized_address->>'zip_code',btrim(proof.normalized_address->>'city'),upper(proof.normalized_address->>'state')) then
    raise exception using errcode='22023',message='ADDRESS_CHANGED'; end if;
   insert into public.addresses(provider_id,street,number,neighborhood,zip_code,city,state,country,complement,latitude,longitude,address_point,
    geocode_place_id,geocoded_at,geocode_expires_at,location_verified_at,verification_distance_m,mismatch_confirmed)
   values(p.id,street,number,neighborhood,postal_code,city,state,'BR',complement,proof.latitude,proof.longitude,
    extensions.st_setsrid(extensions.st_makepoint(proof.longitude,proof.latitude),4326)::extensions.geography,
    proof.place_id,proof.geocoded_at,proof.geocoded_at+interval '30 days',now(),proof.device_distance_m,proof.mismatch_confirmed)
   on conflict(provider_id) do update set street=excluded.street,number=excluded.number,neighborhood=excluded.neighborhood,zip_code=excluded.zip_code,
    city=excluded.city,state=excluded.state,complement=excluded.complement,latitude=excluded.latitude,longitude=excluded.longitude,address_point=excluded.address_point,
    geocode_place_id=excluded.geocode_place_id,geocoded_at=excluded.geocoded_at,geocode_expires_at=excluded.geocode_expires_at,location_verified_at=excluded.location_verified_at,
    verification_distance_m=excluded.verification_distance_m,mismatch_confirmed=excluded.mismatch_confirmed,updated_at=now();
   update private.provider_geocode_proofs set used_at=now() where id=proof.id;
  end if;
  update public.providers set name=btrim(p_name),phone=normalized_phone,description=nullif(btrim(coalesce(p_description,'')),''),updated_at=now() where id=p.id;
 elsif private.has_account_type('CLIENT') then
  select * into c from public.profiles where id=auth.uid() and active for update;
  if c.id is null then raise exception using errcode='42501',message='REGISTRATION_UNAVAILABLE'; end if;
  if c.updated_at is distinct from p_expected_updated_at then raise exception using errcode='40001',message='REGISTRATION_CONFLICT'; end if;
  if p_address is not null then
   select updated_at into old_address_time from public.client_addresses where client_id=c.id for update;
   if old_address_time is distinct from p_address_expected_updated_at then raise exception using errcode='40001',message='REGISTRATION_CONFLICT'; end if;
   perform public.upsert_my_client_address(street,number,neighborhood,postal_code,city,state,complement,null,null);
  end if;
  update public.profiles set name=btrim(p_name),phone=normalized_phone,updated_at=now() where id=c.id;
 else raise exception using errcode='42501',message='REGISTRATION_UNAVAILABLE'; end if;
 return public.get_my_registration();
end $$;
create function public.update_my_guincho_vehicle(p_vehicle_id uuid,p_expected_revision bigint,p_vehicle jsonb) returns jsonb
language plpgsql security definer set search_path='' as $$
declare v public.provider_vehicles%rowtype; normalized_plate text:=regexp_replace(upper(coalesce(p_vehicle->>'plate','')),'[^A-Z0-9]','','g');
begin
 select vehicle.* into v from public.provider_vehicles vehicle join public.providers provider on provider.id=vehicle.provider_id
 where vehicle.id=p_vehicle_id and provider.account_id=auth.uid() and provider.provider_kind='GUINCHO' and provider.active for update of vehicle;
 if v.id is null then raise exception using errcode='42501',message='PROVIDER_REQUIRED'; end if;
 if p_expected_revision is null or v.revision<>p_expected_revision then raise exception using errcode='40001',message='CATALOG_CONFLICT'; end if;
 if p_vehicle is null or normalized_plate !~ '^[A-Z]{3}[0-9][A-Z0-9][0-9]{2}$'
 or length(btrim(coalesce(p_vehicle->>'make',''))) not between 1 and 100 or length(btrim(coalesce(p_vehicle->>'model',''))) not between 1 and 100
 or length(btrim(coalesce(p_vehicle->>'color',''))) not between 1 and 60 then raise exception using errcode='22023',message='VEHICLE_REQUIRED'; end if;
 update public.provider_vehicles set plate=normalized_plate,make=btrim(p_vehicle->>'make'),model=btrim(p_vehicle->>'model'),color=btrim(p_vehicle->>'color'),
  guincho_size_id=(p_vehicle->>'guincho_size_id')::uuid where id=v.id returning * into v;
 return to_jsonb(v);
end $$;
revoke all on function public.get_my_registration(),public.update_my_registration(timestamptz,text,text,text,jsonb,timestamptz,text),public.update_my_guincho_vehicle(uuid,bigint,jsonb) from public,anon;
grant execute on function public.get_my_registration(),public.update_my_registration(timestamptz,text,text,text,jsonb,timestamptz,text),public.update_my_guincho_vehicle(uuid,bigint,jsonb) to authenticated;
notify pgrst,'reload schema';
commit;
