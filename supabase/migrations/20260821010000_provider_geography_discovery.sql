create extension if not exists postgis with schema extensions;

alter table public.service_categories add column if not exists active boolean not null default true;
alter table public.services add column if not exists active boolean not null default true;
alter table public.providers add column if not exists location_mode text;
alter table public.providers add column if not exists active boolean not null default true;
alter table public.providers add column if not exists available boolean not null default false;
alter table public.provider_services add column if not exists is_free boolean not null default false;

do $$
begin
  if not exists (
    select 1 from pg_constraint where conrelid = 'public.providers'::regclass
      and conname = 'providers_location_mode_valid'
  ) then
    alter table public.providers add constraint providers_location_mode_valid
      check (location_mode in ('FIXED', 'MOBILE')) not valid;
  end if;
  if not exists (
    select 1 from pg_constraint where conrelid = 'public.provider_services'::regclass
      and conname = 'provider_services_free_price_valid'
  ) then
    alter table public.provider_services add constraint provider_services_free_price_valid
      check ((is_free and price = 0) or (not is_free and price > 0)) not valid;
  end if;
end;
$$;

create table if not exists public.provider_categories (
  provider_id uuid not null references public.providers(id) on delete cascade,
  category_id uuid not null references public.service_categories(id) on delete restrict,
  created_at timestamptz not null default now(),
  primary key (provider_id, category_id)
);

alter table public.addresses add column if not exists address_point extensions.geography(Point, 4326);
alter table public.addresses add column if not exists geocode_place_id text;
alter table public.addresses add column if not exists geocoded_at timestamptz;
alter table public.addresses add column if not exists geocode_expires_at timestamptz;
alter table public.addresses add column if not exists location_verified_at timestamptz;
alter table public.addresses add column if not exists verification_distance_m numeric(10,2);
alter table public.addresses add column if not exists mismatch_confirmed boolean not null default false;

create index if not exists addresses_address_point_gix
  on public.addresses using gist(address_point);

create or replace function private.enforce_provider_transfer_location_readiness()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if old.account_id is distinct from new.account_id and (
    new.location_mode is null or not exists (
      select 1 from public.addresses address
      where address.provider_id = new.id and address.address_point is not null
        and address.location_verified_at is not null
        and address.geocode_expires_at > now()
    )
  ) then
    raise exception using errcode = 'P0002', message = 'Provider migration requires location verification';
  end if;
  return new;
end;
$$;

drop trigger if exists enforce_provider_transfer_location_readiness on public.providers;
create trigger enforce_provider_transfer_location_readiness
before update of account_id on public.providers
for each row execute function private.enforce_provider_transfer_location_readiness();

create table if not exists private.provider_geocode_proofs (
  id uuid primary key default gen_random_uuid(),
  account_id uuid not null references auth.users(id) on delete cascade,
  token_hash bytea not null unique,
  normalized_address jsonb not null,
  place_id text not null,
  latitude numeric(9,6) not null check (latitude between -90 and 90),
  longitude numeric(10,6) not null check (longitude between -180 and 180),
  device_distance_m numeric(10,2) not null check (device_distance_m >= 0),
  mismatch_confirmed boolean not null,
  geocoded_at timestamptz not null,
  expires_at timestamptz not null,
  used_at timestamptz,
  created_at timestamptz not null default now()
);

revoke all on table private.provider_geocode_proofs from public, anon, authenticated;

alter table public.provider_categories enable row level security;
revoke all on table public.provider_categories from public, anon, authenticated;
grant select on table public.provider_categories to authenticated;

create policy provider_categories_select_owner on public.provider_categories
for select to authenticated using (
  private.has_account_type('PROVIDER') and exists (
    select 1 from public.providers provider
    where provider.id = provider_categories.provider_id
      and provider.account_id = (select auth.uid())
  )
);

drop policy if exists service_categories_select_authenticated on public.service_categories;
drop policy if exists services_select_authenticated on public.services;
create policy service_categories_select_active on public.service_categories
for select to authenticated using (active = true);
create policy services_select_active on public.services
for select to authenticated using (active = true);

drop function if exists public.create_provider_registration(jsonb, jsonb, jsonb);
create function public.create_provider_registration(
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
    return jsonb_build_object('outcome', 'ALREADY_REGISTERED', 'offer_count', offer_count);
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
    provider_id, street, number, neighbornhood, zip_code, city, state, country,
    complement, latitude, longitude, address_point, geocode_place_id, geocoded_at,
    geocode_expires_at, location_verified_at, verification_distance_m, mismatch_confirmed
  ) values (
    new_provider_id,
    btrim(coalesce(p_address ->> 'street', '')),
    btrim(coalesce(p_address ->> 'number', '')),
    btrim(coalesce(p_address ->> 'neighbornhood', '')),
    regexp_replace(coalesce(p_address ->> 'zip_code', ''), '[^0-9]', '', 'g'),
    btrim(coalesce(p_address ->> 'city', '')),
    upper(btrim(coalesce(p_address ->> 'state', ''))), 'BR',
    nullif(btrim(coalesce(p_address ->> 'complement', '')), ''),
    proof.latitude, proof.longitude,
    extensions.ST_SetSRID(extensions.ST_MakePoint(proof.longitude, proof.latitude), 4326)::extensions.geography,
    proof.place_id, proof.geocoded_at, proof.geocoded_at + interval '30 days', now(),
    proof.device_distance_m, proof.mismatch_confirmed
  );

  insert into public.provider_services (
    provider_id, service_id, price, duration_minutes, description, is_free
  ) select new_provider_id, (item ->> 'service_id')::uuid,
    (item ->> 'price')::numeric, (item ->> 'duration_minutes')::integer,
    nullif(btrim(coalesce(item ->> 'description', '')), ''),
    coalesce((item ->> 'is_free')::boolean, false)
  from jsonb_array_elements(p_offers) item;

  insert into public.provider_categories(provider_id, category_id)
  select distinct new_provider_id, service.category_id
  from jsonb_array_elements(p_offers) item
  join public.services service on service.id = (item ->> 'service_id')::uuid;

  update private.provider_geocode_proofs set used_at = now() where id = proof.id;
  return jsonb_build_object(
    'outcome', 'CREATED', 'provider_id', new_provider_id,
    'offer_count', jsonb_array_length(p_offers)
  );
end;
$$;

revoke all on function public.create_provider_registration(jsonb, jsonb, jsonb)
  from public, anon, authenticated;
grant execute on function public.create_provider_registration(jsonb, jsonb, jsonb)
  to authenticated;

create or replace function public.set_provider_availability(
  p_available boolean,
  p_location_mode text default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare updated_provider public.providers%rowtype;
begin
  if auth.uid() is null or not private.has_account_type('PROVIDER') then
    raise exception using errcode = '42501', message = 'Provider session required';
  end if;
  update public.providers set
    available = p_available,
    location_mode = coalesce(nullif(upper(p_location_mode), ''), location_mode),
    updated_at = now()
  where account_id = auth.uid()
  returning * into updated_provider;
  if updated_provider.id is null then
    raise exception using errcode = 'P0002', message = 'Provider unavailable';
  end if;
  return jsonb_build_object(
    'provider_id', updated_provider.id,
    'available', updated_provider.available,
    'location_mode', updated_provider.location_mode
  );
end;
$$;

revoke all on function public.set_provider_availability(boolean, text) from public, anon;
grant execute on function public.set_provider_availability(boolean, text) to authenticated;

create or replace function public.list_home_services()
returns table(service_id uuid, name text, description text, category_name text, minimum_price numeric)
language sql
stable
security definer
set search_path = ''
as $$
  select service.id, service.name, service.description, category.name, min(offer.price)
  from public.services service
  join public.service_categories category on category.id = service.category_id
  left join public.provider_services offer on offer.service_id = service.id
  left join public.providers provider on provider.id = offer.provider_id
    and provider.active and provider.available
  where service.active and category.active and auth.uid() is not null
    and private.has_account_type('CLIENT')
  group by service.id, service.name, service.description, category.name
  order by service.name;
$$;

create or replace function public.discover_service_providers(
  p_service_id uuid,
  p_center_lat double precision,
  p_center_long double precision,
  p_radius_meters integer default 25000,
  p_limit integer default 100
)
returns table(
  provider_id uuid, display_name text, location_mode text,
  latitude double precision, longitude double precision,
  distance_meters double precision, minimum_price numeric
)
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
  if auth.uid() is null or not private.has_account_type('CLIENT') then
    raise exception using errcode = '42501', message = 'Client session required';
  end if;
  if p_center_lat not between -90 and 90 or p_center_long not between -180 and 180
    or p_radius_meters not between 1 and 200000 or p_limit not between 1 and 200 then
    raise exception using errcode = '22023', message = 'Invalid discovery area';
  end if;
  return query
  select provider.id, provider.name, provider.location_mode,
    address.latitude::double precision, address.longitude::double precision,
    extensions.ST_Distance(
      address.address_point,
      extensions.ST_SetSRID(extensions.ST_MakePoint(p_center_long, p_center_lat), 4326)::extensions.geography
    ), min(offer.price)
  from public.provider_services offer
  join public.providers provider on provider.id = offer.provider_id
  join public.addresses address on address.provider_id = provider.id
  join public.services service on service.id = offer.service_id
  join public.service_categories category on category.id = service.category_id
  join public.provider_categories provider_category
    on provider_category.provider_id = provider.id
    and provider_category.category_id = category.id
  join public.account_roles owner_role
    on owner_role.user_id = provider.account_id and owner_role.account_type = 'PROVIDER'
  where offer.service_id = p_service_id and service.active and category.active
    and provider.active and provider.available and address.address_point is not null
    and address.geocode_expires_at > now()
    and extensions.ST_DWithin(
      address.address_point,
      extensions.ST_SetSRID(extensions.ST_MakePoint(p_center_long, p_center_lat), 4326)::extensions.geography,
      p_radius_meters
    )
  group by provider.id, provider.name, provider.location_mode,
    address.latitude, address.longitude, address.address_point
  order by 6, provider.id
  limit p_limit;
end;
$$;

revoke all on function public.list_home_services() from public, anon;
revoke all on function public.discover_service_providers(uuid, double precision, double precision, integer, integer)
  from public, anon;
grant execute on function public.list_home_services() to authenticated;
grant execute on function public.discover_service_providers(uuid, double precision, double precision, integer, integer)
  to authenticated;

-- Operations must create Vault secrets `project_url` and `geocode_refresh_secret`.
-- The scheduled call never embeds either value in versioned SQL.
create extension if not exists pg_net with schema extensions;
create extension if not exists pg_cron with schema pg_catalog;

create or replace function private.invoke_geocode_refresh()
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  project_url text;
  refresh_secret text;
begin
  select decrypted_secret into project_url
  from vault.decrypted_secrets where name = 'project_url';
  select decrypted_secret into refresh_secret
  from vault.decrypted_secrets where name = 'geocode_refresh_secret';
  if project_url is null or refresh_secret is null then return; end if;
  perform net.http_post(
    url := project_url || '/functions/v1/refresh-provider-geocodes',
    headers := jsonb_build_object(
      'content-type', 'application/json', 'x-refresh-secret', refresh_secret
    ),
    body := '{}'::jsonb
  );
end;
$$;

revoke all on function private.invoke_geocode_refresh() from public, anon, authenticated;
do $$
begin
  if not exists (select 1 from cron.job where jobname = 'refresh-provider-geocodes-daily') then
    perform cron.schedule(
      'refresh-provider-geocodes-daily', '17 3 * * *',
      'select private.invoke_geocode_refresh()'
    );
  end if;
end;
$$;
