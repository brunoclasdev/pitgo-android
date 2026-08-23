create extension if not exists pgcrypto;

-- Reconcile only unambiguous legacy naming. Ambiguous schemas abort instead of losing data.
do $$
begin
  if to_regclass('public.provider') is not null and to_regclass('public.providers') is null then
    alter table public.provider rename to providers;
  elsif to_regclass('public.provider') is not null and to_regclass('public.providers') is not null then
    raise exception using errcode = 'P0002', message = 'Ambiguous provider tables';
  end if;

  if to_regclass('public.service_category') is not null
    and to_regclass('public.service_categories') is null then
    alter table public.service_category rename to service_categories;
  elsif to_regclass('public.service_category') is not null
    and to_regclass('public.service_categories') is not null then
    raise exception using errcode = 'P0002', message = 'Ambiguous service category tables';
  end if;

  if to_regclass('public.addresses') is not null then
    if exists (
      select 1 from information_schema.columns
      where table_schema = 'public' and table_name = 'addresses' and column_name = 'neighborhood'
    ) and not exists (
      select 1 from information_schema.columns
      where table_schema = 'public' and table_name = 'addresses' and column_name = 'neighbornhood'
    ) then
      alter table public.addresses rename column neighborhood to neighbornhood;
    elsif exists (
      select 1 from information_schema.columns
      where table_schema = 'public' and table_name = 'addresses' and column_name = 'neighborhood'
    ) and exists (
      select 1 from information_schema.columns
      where table_schema = 'public' and table_name = 'addresses' and column_name = 'neighbornhood'
    ) then
      raise exception using errcode = 'P0002', message = 'Ambiguous address neighborhood columns';
    end if;
  end if;

  if to_regclass('public.services') is not null then
    if exists (
      select 1 from information_schema.columns
      where table_schema = 'public' and table_name = 'services'
        and column_name = 'service_category_id'
    ) and not exists (
      select 1 from information_schema.columns
      where table_schema = 'public' and table_name = 'services' and column_name = 'category_id'
    ) then
      alter table public.services rename column service_category_id to category_id;
    elsif exists (
      select 1 from information_schema.columns
      where table_schema = 'public' and table_name = 'services'
        and column_name = 'service_category_id'
    ) and exists (
      select 1 from information_schema.columns
      where table_schema = 'public' and table_name = 'services' and column_name = 'category_id'
    ) then
      raise exception using errcode = 'P0002', message = 'Ambiguous service category columns';
    end if;
  end if;
end;
$$;

create table if not exists public.service_categories (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  description text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint service_categories_name_normalized
    check (name = btrim(name) and name <> '' and char_length(name) <= 150),
  constraint service_categories_description_length
    check (description is null or char_length(description) <= 1000)
);

create table if not exists public.services (
  id uuid primary key default gen_random_uuid(),
  category_id uuid not null references public.service_categories(id) on delete restrict,
  name text not null,
  description text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint services_name_normalized
    check (name = btrim(name) and name <> '' and char_length(name) <= 150),
  constraint services_description_length
    check (description is null or char_length(description) <= 1000)
);

create table if not exists public.providers (
  id uuid primary key references auth.users(id) on delete cascade,
  name text not null,
  description text,
  email text not null,
  phone text not null,
  cnpj text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint providers_name_normalized
    check (name = btrim(name) and name <> '' and char_length(name) <= 150),
  constraint providers_description_length
    check (description is null or char_length(description) <= 2000),
  constraint providers_email_normalized check (
    email = lower(btrim(email)) and char_length(email) <= 254
      and email ~ '^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$'
  ),
  constraint providers_phone_normalized check (
    phone ~ '^[0-9]{10,11}$' and private.is_valid_brazilian_phone(phone)
  ),
  constraint providers_cnpj_normalized check (
    cnpj = upper(cnpj) and cnpj ~ '^[0-9A-Z]{12}[0-9]{2}$'
      and private.is_valid_cnpj(cnpj)
  ),
  constraint providers_email_unique unique (email),
  constraint providers_cnpj_unique unique (cnpj)
);

create table if not exists public.addresses (
  id uuid primary key default gen_random_uuid(),
  provider_id uuid not null references public.providers(id) on delete cascade,
  street text not null,
  number text not null,
  neighbornhood text not null,
  zip_code text not null,
  city text not null,
  state text not null,
  country text not null default 'BR',
  complement text,
  latitude numeric(9,6),
  longitude numeric(10,6),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint addresses_street_normalized
    check (street = btrim(street) and street <> '' and char_length(street) <= 160),
  constraint addresses_number_normalized
    check (number = btrim(number) and number <> '' and char_length(number) <= 40),
  constraint addresses_neighbornhood_normalized
    check (neighbornhood = btrim(neighbornhood) and neighbornhood <> '' and char_length(neighbornhood) <= 100),
  constraint addresses_zip_code_normalized check (zip_code ~ '^[0-9]{8}$'),
  constraint addresses_city_normalized
    check (city = btrim(city) and city <> '' and char_length(city) <= 100),
  constraint addresses_state_normalized check (
    state = upper(state) and state = any (array[
      'AC','AL','AP','AM','BA','CE','DF','ES','GO','MA','MT','MS','MG','PA','PB','PR',
      'PE','PI','RJ','RN','RS','RO','RR','SC','SP','SE','TO'
    ])
  ),
  constraint addresses_country_brazil check (country = 'BR'),
  constraint addresses_complement_length
    check (complement is null or char_length(complement) <= 160),
  constraint addresses_coordinates_pair
    check ((latitude is null) = (longitude is null)),
  constraint addresses_latitude_range
    check (latitude is null or latitude between -90 and 90),
  constraint addresses_longitude_range
    check (longitude is null or longitude between -180 and 180),
  constraint addresses_provider_unique unique (provider_id)
);

create table if not exists public.provider_services (
  id uuid primary key default gen_random_uuid(),
  provider_id uuid not null references public.providers(id) on delete cascade,
  service_id uuid not null references public.services(id) on delete restrict,
  price numeric(12,2) not null,
  duration_minutes integer not null,
  description text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint provider_services_price_valid
    check (price >= 0 and scale(price) <= 2),
  constraint provider_services_duration_valid
    check (duration_minutes between 1 and 10080),
  constraint provider_services_description_length
    check (description is null or char_length(description) <= 1000),
  constraint provider_services_provider_service_unique unique (provider_id, service_id)
);

create unique index if not exists service_categories_name_unique
  on public.service_categories (lower(name));
create unique index if not exists services_category_name_unique
  on public.services (category_id, lower(name));
create unique index if not exists providers_email_unique
  on public.providers (email);
create unique index if not exists providers_cnpj_unique
  on public.providers (cnpj);
create unique index if not exists addresses_provider_unique
  on public.addresses (provider_id);
create unique index if not exists provider_services_provider_service_unique
  on public.provider_services (provider_id, service_id);
create index if not exists services_category_id_idx on public.services (category_id);
create index if not exists provider_services_service_id_idx on public.provider_services (service_id);

alter table public.providers enable row level security;
alter table public.addresses enable row level security;
alter table public.service_categories enable row level security;
alter table public.services enable row level security;
alter table public.provider_services enable row level security;

do $$
declare
  target_table text;
  policy_name text;
begin
  foreach target_table in array array[
    'providers', 'addresses', 'service_categories', 'services', 'provider_services'
  ] loop
    for policy_name in
      select policyname from pg_policies
      where schemaname = 'public' and tablename = target_table
    loop
      execute format('drop policy if exists %I on public.%I', policy_name, target_table);
    end loop;
  end loop;
end;
$$;

create policy providers_select_own on public.providers
for select to authenticated using ((select auth.uid()) = id);
create policy addresses_select_own on public.addresses
for select to authenticated using ((select auth.uid()) = provider_id);
create policy provider_services_select_own on public.provider_services
for select to authenticated using ((select auth.uid()) = provider_id);
create policy service_categories_select_authenticated on public.service_categories
for select to authenticated using (true);
create policy services_select_authenticated on public.services
for select to authenticated using (true);

revoke all on table public.providers from anon, authenticated;
revoke all on table public.addresses from anon, authenticated;
revoke all on table public.service_categories from anon, authenticated;
revoke all on table public.services from anon, authenticated;
revoke all on table public.provider_services from anon, authenticated;
grant select on table public.providers to authenticated;
grant select on table public.addresses to authenticated;
grant select on table public.service_categories to authenticated;
grant select on table public.services to authenticated;
grant select on table public.provider_services to authenticated;

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
  normalized_name text := pg_catalog.btrim(coalesce(p_provider ->> 'name', ''));
  normalized_description text := nullif(pg_catalog.btrim(coalesce(p_provider ->> 'description', '')), '');
  normalized_email text := pg_catalog.lower(pg_catalog.btrim(coalesce(p_provider ->> 'email', '')));
  normalized_phone text := pg_catalog.regexp_replace(coalesce(p_provider ->> 'phone', ''), '[^0-9]', '', 'g');
  normalized_cnpj text := pg_catalog.regexp_replace(
    pg_catalog.upper(coalesce(p_provider ->> 'cnpj', '')), '[^0-9A-Z]', '', 'g'
  );
  normalized_street text := pg_catalog.btrim(coalesce(p_address ->> 'street', ''));
  normalized_number text := pg_catalog.btrim(coalesce(p_address ->> 'number', ''));
  normalized_neighbornhood text := pg_catalog.btrim(coalesce(p_address ->> 'neighbornhood', ''));
  normalized_zip_code text := pg_catalog.regexp_replace(coalesce(p_address ->> 'zip_code', ''), '[^0-9]', '', 'g');
  normalized_city text := pg_catalog.btrim(coalesce(p_address ->> 'city', ''));
  normalized_state text := pg_catalog.upper(pg_catalog.btrim(coalesce(p_address ->> 'state', '')));
  normalized_complement text := nullif(pg_catalog.btrim(coalesce(p_address ->> 'complement', '')), '');
  normalized_latitude numeric;
  normalized_longitude numeric;
  existing_address_count integer;
  existing_offer_count integer;
  inserted_offer_count integer;
begin
  if current_user_id is null then
    raise exception using errcode = '42501', message = 'Authenticated session required';
  end if;

  perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(current_user_id::text, 7007));

  if not exists (
    select 1 from public.profiles
    where id = current_user_id and active = true
  ) then
    raise exception using errcode = '42501', message = 'Active profile required';
  end if;

  if exists (select 1 from public.providers where id = current_user_id) then
    select count(*) into existing_address_count from public.addresses
      where provider_id = current_user_id;
    select count(*) into existing_offer_count from public.provider_services
      where provider_id = current_user_id;
    if existing_address_count = 1 and existing_offer_count >= 1 then
      return pg_catalog.jsonb_build_object(
        'outcome', 'ALREADY_REGISTERED', 'offer_count', existing_offer_count
      );
    end if;
    raise exception using errcode = 'P0002', message = 'Existing provider aggregate is incomplete';
  end if;

  if normalized_name = '' or char_length(normalized_name) > 150
    or normalized_description is not null and char_length(normalized_description) > 2000
    or char_length(normalized_email) > 254
    or normalized_email !~ '^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$'
    or not private.is_valid_brazilian_phone(normalized_phone)
    or not private.is_valid_cnpj(normalized_cnpj)
  then
    raise exception using errcode = '22023', message = 'Invalid provider input';
  end if;

  if normalized_street = '' or char_length(normalized_street) > 160
    or normalized_number = '' or char_length(normalized_number) > 40
    or normalized_neighbornhood = '' or char_length(normalized_neighbornhood) > 100
    or normalized_zip_code !~ '^[0-9]{8}$'
    or normalized_city = '' or char_length(normalized_city) > 100
    or normalized_state <> all (array[
      'AC','AL','AP','AM','BA','CE','DF','ES','GO','MA','MT','MS','MG','PA','PB','PR',
      'PE','PI','RJ','RN','RS','RO','RR','SC','SP','SE','TO'
    ])
    or normalized_complement is not null and char_length(normalized_complement) > 160
    or coalesce(p_address ->> 'country', 'BR') <> 'BR'
  then
    raise exception using errcode = '22023', message = 'Invalid address input';
  end if;

  if nullif(p_address ->> 'latitude', '') is not null then
    normalized_latitude := (p_address ->> 'latitude')::numeric;
  end if;
  if nullif(p_address ->> 'longitude', '') is not null then
    normalized_longitude := (p_address ->> 'longitude')::numeric;
  end if;
  if (normalized_latitude is null) <> (normalized_longitude is null)
    or normalized_latitude is not null and normalized_latitude not between -90 and 90
    or normalized_longitude is not null and normalized_longitude not between -180 and 180
  then
    raise exception using errcode = '22023', message = 'Invalid address coordinates';
  end if;

  if p_offers is null or pg_catalog.jsonb_typeof(p_offers) <> 'array'
    or pg_catalog.jsonb_array_length(p_offers) < 1
  then
    raise exception using errcode = '22023', message = 'At least one offer is required';
  end if;

  if exists (
    select 1
    from pg_catalog.jsonb_array_elements(p_offers) offer
    where pg_catalog.jsonb_typeof(offer) <> 'object'
      or nullif(offer ->> 'service_id', '') is null
      or coalesce(offer ->> 'duration_minutes', '') !~ '^[0-9]+$'
      or (offer ->> 'duration_minutes')::integer not between 1 and 10080
      or pg_catalog.jsonb_typeof(offer -> 'is_free') <> 'boolean'
      or coalesce(offer ->> 'price', '') !~ '^[0-9]+(\.[0-9]{1,2})?$'
      or ((offer ->> 'is_free')::boolean and (offer ->> 'price')::numeric <> 0)
      or (not (offer ->> 'is_free')::boolean and (offer ->> 'price')::numeric <= 0)
      or char_length(coalesce(offer ->> 'description', '')) > 1000
  ) then
    raise exception using errcode = '22023', message = 'Invalid offer input';
  end if;

  if (
    select count(*) <> count(distinct (offer ->> 'service_id')::uuid)
    from pg_catalog.jsonb_array_elements(p_offers) offer
  ) then
    raise exception using errcode = '22023', message = 'Duplicate service offer';
  end if;

  if exists (
    select 1 from pg_catalog.jsonb_array_elements(p_offers) offer
    left join public.services service on service.id = (offer ->> 'service_id')::uuid
    left join public.service_categories category on category.id = service.category_id
    where service.id is null or category.id is null
  ) then
    raise exception using errcode = 'P0002', message = 'Catalog service unavailable';
  end if;

  insert into public.providers (id, name, description, email, phone, cnpj)
  values (
    current_user_id, normalized_name, normalized_description,
    normalized_email, normalized_phone, normalized_cnpj
  );

  insert into public.addresses (
    provider_id, street, number, neighbornhood, zip_code, city, state, country,
    complement, latitude, longitude
  ) values (
    current_user_id, normalized_street, normalized_number, normalized_neighbornhood,
    normalized_zip_code, normalized_city, normalized_state, 'BR', normalized_complement,
    normalized_latitude, normalized_longitude
  );

  insert into public.provider_services (
    provider_id, service_id, price, duration_minutes, description
  )
  select
    current_user_id,
    (offer ->> 'service_id')::uuid,
    (offer ->> 'price')::numeric,
    (offer ->> 'duration_minutes')::integer,
    nullif(pg_catalog.btrim(coalesce(offer ->> 'description', '')), '')
  from pg_catalog.jsonb_array_elements(p_offers) offer;

  get diagnostics inserted_offer_count = row_count;
  return pg_catalog.jsonb_build_object(
    'outcome', 'CREATED', 'offer_count', inserted_offer_count
  );
end;
$$;

revoke all on function public.create_provider_registration(jsonb, jsonb, jsonb)
  from public, anon, authenticated;
grant execute on function public.create_provider_registration(jsonb, jsonb, jsonb)
  to authenticated;
