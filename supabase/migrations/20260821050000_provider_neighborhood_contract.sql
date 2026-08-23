-- Canonicalize the address neighborhood column and keep the RPC compatible with
-- older Android builds that still send the legacy "neighbornhood" JSON key.
do $$
begin
  if exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'addresses'
      and column_name = 'neighbornhood'
  ) and not exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'addresses'
      and column_name = 'neighborhood'
  ) then
    alter table public.addresses rename column neighbornhood to neighborhood;
  elsif not exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'addresses'
      and column_name = 'neighborhood'
  ) then
    raise exception using errcode = 'P0002', message = 'Address neighborhood column unavailable';
  end if;
end;
$$;

create or replace function public.create_provider_registration(
  p_provider jsonb,
  p_address jsonb,
  p_offers jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  current_user_id uuid := auth.uid();
  new_provider_id uuid := gen_random_uuid();
  proof private.provider_geocode_proofs%rowtype;
  proof_token text := p_address ->> 'verification_token';
  provider_mode text := upper(coalesce(p_provider ->> 'location_mode', ''));
  normalized_neighborhood text := btrim(coalesce(
    p_address ->> 'neighborhood',
    p_address ->> 'neighbornhood',
    ''
  ));
  offer_count integer;
begin
  if current_user_id is null or not private.has_account_type('PROVIDER') then
    raise exception using errcode = '42501', message = 'Provider session required';
  end if;

  perform pg_catalog.pg_advisory_xact_lock(
    pg_catalog.hashtextextended(current_user_id::text, 9010)
  );

  if exists (select 1 from public.providers where account_id = current_user_id) then
    select count(*) into offer_count from public.provider_services offer
    join public.providers provider on provider.id = offer.provider_id
    where provider.account_id = current_user_id;
    return jsonb_build_object(
      'outcome', 'ALREADY_REGISTERED',
      'offer_count', offer_count
    );
  end if;

  if provider_mode not in ('FIXED', 'MOBILE') then
    raise exception using errcode = '22023', message = 'Invalid provider mode';
  end if;

  if proof_token is null or proof_token !~ '^[A-Za-z0-9_-]{43,128}$' then
    raise exception using errcode = '22023', message = 'Location verification required';
  end if;

  select * into proof from private.provider_geocode_proofs candidate
  where candidate.account_id = current_user_id
    and candidate.token_hash = extensions.digest(proof_token, 'sha256')
  for update;

  if proof.id is null or proof.used_at is not null or proof.expires_at <= now()
    or (proof.device_distance_m > 500 and not proof.mismatch_confirmed)
  then
    raise exception using errcode = '22023', message = 'Location verification unavailable';
  end if;

  if normalized_neighborhood = '' then
    raise exception using errcode = '22023', message = 'Invalid address';
  end if;

  if p_offers is null or jsonb_typeof(p_offers) <> 'array'
    or jsonb_array_length(p_offers) = 0 then
    raise exception using errcode = '22023', message = 'At least one offer is required';
  end if;

  if (select count(*) <> count(distinct (item ->> 'service_id')::uuid)
      from jsonb_array_elements(p_offers) item) then
    raise exception using errcode = '22023', message = 'Duplicate service';
  end if;

  if exists (
    select 1 from jsonb_array_elements(p_offers) item
    left join public.services service on service.id = (item ->> 'service_id')::uuid
    left join public.service_categories category on category.id = service.category_id
    where service.id is null or not service.active or category.id is null or not category.active
      or coalesce(item ->> 'duration_minutes', '') !~ '^[0-9]+$'
      or (item ->> 'duration_minutes')::integer not between 1 and 10080
      or coalesce(item ->> 'price', '') !~ '^[0-9]+(\.[0-9]{1,2})?$'
      or ((item ->> 'is_free')::boolean and (item ->> 'price')::numeric <> 0)
      or (not (item ->> 'is_free')::boolean and (item ->> 'price')::numeric <= 0)
  ) then
    raise exception using errcode = '22023', message = 'Invalid offer';
  end if;

  insert into public.providers (
    id, account_id, name, description, email, phone, cnpj,
    location_mode, active, available
  ) values (
    new_provider_id, current_user_id,
    btrim(coalesce(p_provider ->> 'name', '')),
    nullif(btrim(coalesce(p_provider ->> 'description', '')), ''),
    lower(btrim(coalesce(p_provider ->> 'email', ''))),
    regexp_replace(coalesce(p_provider ->> 'phone', ''), '[^0-9]', '', 'g'),
    regexp_replace(upper(coalesce(p_provider ->> 'cnpj', '')), '[^0-9A-Z]', '', 'g'),
    provider_mode, true, false
  );

  insert into public.addresses (
    provider_id, street, number, neighborhood, zip_code, city, state, country,
    complement, latitude, longitude, address_point, geocode_place_id, geocoded_at,
    geocode_expires_at, location_verified_at, verification_distance_m, mismatch_confirmed
  ) values (
    new_provider_id,
    btrim(coalesce(p_address ->> 'street', '')),
    btrim(coalesce(p_address ->> 'number', '')),
    normalized_neighborhood,
    regexp_replace(coalesce(p_address ->> 'zip_code', ''), '[^0-9]', '', 'g'),
    btrim(coalesce(p_address ->> 'city', '')),
    upper(btrim(coalesce(p_address ->> 'state', ''))),
    'BR',
    nullif(btrim(coalesce(p_address ->> 'complement', '')), ''),
    proof.latitude,
    proof.longitude,
    extensions.ST_SetSRID(
      extensions.ST_MakePoint(proof.longitude, proof.latitude),
      4326
    )::extensions.geography,
    proof.place_id,
    proof.geocoded_at,
    proof.geocoded_at + interval '30 days',
    now(),
    proof.device_distance_m,
    proof.mismatch_confirmed
  );

  insert into public.provider_services (
    provider_id, service_id, price, duration_minutes, description, is_free
  ) select
    new_provider_id,
    (item ->> 'service_id')::uuid,
    (item ->> 'price')::numeric,
    (item ->> 'duration_minutes')::integer,
    nullif(btrim(coalesce(item ->> 'description', '')), ''),
    coalesce((item ->> 'is_free')::boolean, false)
  from jsonb_array_elements(p_offers) item;

  insert into public.provider_categories(provider_id, category_id)
  select distinct new_provider_id, service.category_id
  from jsonb_array_elements(p_offers) item
  join public.services service on service.id = (item ->> 'service_id')::uuid;

  update private.provider_geocode_proofs
  set used_at = now()
  where id = proof.id;

  return jsonb_build_object(
    'outcome', 'CREATED',
    'provider_id', new_provider_id,
    'offer_count', jsonb_array_length(p_offers)
  );
end;
$$;

revoke all on function public.create_provider_registration(jsonb, jsonb, jsonb)
  from public, anon, authenticated;
grant execute on function public.create_provider_registration(jsonb, jsonb, jsonb)
  to authenticated;
