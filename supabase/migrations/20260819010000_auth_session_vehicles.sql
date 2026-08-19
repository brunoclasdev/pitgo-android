create extension if not exists pgcrypto;

create schema if not exists private;
revoke all on schema private from public, anon, authenticated;

-- Reconcile the checked-in profile schema with the schema that may already exist remotely.
alter table public.profiles add column if not exists avatar_url text;
alter table public.profiles add column if not exists active boolean;

do $$
begin
  if exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'profiles' and column_name = 'activity'
  ) then
    execute 'update public.profiles set active = coalesce(active, activity)';
    execute 'alter table public.profiles drop column activity';
  end if;

  if exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'profiles' and column_name = 'activty'
  ) then
    execute 'update public.profiles set active = coalesce(active, activty)';
    execute 'alter table public.profiles drop column activty';
  end if;
end;
$$;

update public.profiles set active = true where active is null;
alter table public.profiles alter column active set default true;
alter table public.profiles alter column active set not null;

do $$
begin
  if not exists (
    select 1 from pg_constraint
    where conrelid = 'public.profiles'::regclass and conname = 'profiles_name_required'
  ) then
    alter table public.profiles
      add constraint profiles_name_required
      check (name is not null and btrim(name) <> '') not valid;
  end if;

  if not exists (
    select 1 from pg_constraint
    where conrelid = 'public.profiles'::regclass and conname = 'profiles_phone_required'
  ) then
    alter table public.profiles
      add constraint profiles_phone_required
      check (phone is not null and phone ~ '^[0-9]{10,11}$') not valid;
  end if;

  if not exists (
    select 1 from pg_constraint
    where conrelid = 'public.profiles'::regclass and conname = 'profiles_exactly_one_document'
  ) then
    alter table public.profiles
      add constraint profiles_exactly_one_document
      check (num_nonnulls(cpf, cnpj) = 1) not valid;
  end if;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
drop trigger if exists on_auth_user_created_create_profile on auth.users;
drop function if exists public.handle_new_user();

create or replace function private.handle_new_user_profile()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  normalized_cpf text;
  normalized_cnpj text;
begin
  normalized_cpf := nullif(
    pg_catalog.regexp_replace(coalesce(new.raw_user_meta_data ->> 'cpf', ''), '[^0-9]', '', 'g'),
    ''
  );
  normalized_cnpj := nullif(
    pg_catalog.regexp_replace(
      pg_catalog.upper(coalesce(new.raw_user_meta_data ->> 'cnpj', '')),
      '[^0-9A-Z]',
      '',
      'g'
    ),
    ''
  );

  insert into public.profiles (id, name, email, phone, cpf, cnpj, active)
  values (
    new.id,
    pg_catalog.btrim(coalesce(new.raw_user_meta_data ->> 'name', '')),
    pg_catalog.lower(pg_catalog.btrim(coalesce(new.email, ''))),
    pg_catalog.regexp_replace(
      coalesce(new.raw_user_meta_data ->> 'phone', ''),
      '[^0-9]',
      '',
      'g'
    ),
    normalized_cpf,
    normalized_cnpj,
    true
  );

  return new;
end;
$$;

revoke all on function private.handle_new_user_profile() from public, anon, authenticated;

create trigger on_auth_user_created_create_profile
after insert on auth.users
for each row execute function private.handle_new_user_profile();

alter table public.profiles enable row level security;

do $$
declare
  policy_name text;
begin
  for policy_name in
    select policyname from pg_policies
    where schemaname = 'public' and tablename = 'profiles'
  loop
    execute format('drop policy if exists %I on public.profiles', policy_name);
  end loop;
end;
$$;

create policy profiles_select_own
on public.profiles
for select
to authenticated
using ((select auth.uid()) = id);

revoke all on table public.profiles from anon, authenticated;
grant select on table public.profiles to authenticated;

create table if not exists public.vehicles (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  brand text not null,
  model text not null,
  version text,
  manufacture_year integer,
  model_year integer,
  plate text,
  renavam text,
  chassis text,
  color text,
  fuel_type text,
  transmission text,
  engine text,
  mileage integer,
  is_primary boolean not null default false,
  active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.vehicles add column if not exists version text;
alter table public.vehicles add column if not exists manufacture_year integer;
alter table public.vehicles add column if not exists model_year integer;
alter table public.vehicles add column if not exists plate text;
alter table public.vehicles add column if not exists renavam text;
alter table public.vehicles add column if not exists chassis text;
alter table public.vehicles add column if not exists color text;
alter table public.vehicles add column if not exists fuel_type text;
alter table public.vehicles add column if not exists transmission text;
alter table public.vehicles add column if not exists engine text;
alter table public.vehicles add column if not exists mileage integer;
alter table public.vehicles add column if not exists is_primary boolean not null default false;
alter table public.vehicles add column if not exists active boolean not null default true;
alter table public.vehicles add column if not exists created_at timestamptz not null default now();
alter table public.vehicles add column if not exists updated_at timestamptz not null default now();

update public.vehicles set is_primary = false where is_primary and not active;

do $$
begin
  if not exists (
    select 1 from pg_constraint
    where conrelid = 'public.vehicles'::regclass and contype = 'f'
      and conname = 'fk_vehicles_user'
  ) then
    alter table public.vehicles
      add constraint fk_vehicles_user foreign key (user_id)
      references public.profiles(id) on delete cascade;
  end if;

  if not exists (
    select 1 from pg_constraint
    where conrelid = 'public.vehicles'::regclass and conname = 'vehicles_brand_nonblank'
  ) then
    alter table public.vehicles add constraint vehicles_brand_nonblank
      check (brand = btrim(brand) and brand <> '') not valid;
  end if;

  if not exists (
    select 1 from pg_constraint
    where conrelid = 'public.vehicles'::regclass and conname = 'vehicles_model_nonblank'
  ) then
    alter table public.vehicles add constraint vehicles_model_nonblank
      check (model = btrim(model) and model <> '') not valid;
  end if;

  if not exists (
    select 1 from pg_constraint
    where conrelid = 'public.vehicles'::regclass and conname = 'vehicles_primary_requires_active'
  ) then
    alter table public.vehicles add constraint vehicles_primary_requires_active
      check (not is_primary or active) not valid;
  end if;

  if not exists (
    select 1 from pg_constraint
    where conrelid = 'public.vehicles'::regclass and conname = 'vehicles_mileage_nonnegative'
  ) then
    alter table public.vehicles add constraint vehicles_mileage_nonnegative
      check (mileage is null or mileage >= 0) not valid;
  end if;

  if not exists (
    select 1 from pg_constraint
    where conrelid = 'public.vehicles'::regclass and conname = 'vehicles_manufacture_year_range'
  ) then
    alter table public.vehicles add constraint vehicles_manufacture_year_range
      check (manufacture_year is null or manufacture_year >= 1900) not valid;
  end if;

  if not exists (
    select 1 from pg_constraint
    where conrelid = 'public.vehicles'::regclass and conname = 'vehicles_model_year_range'
  ) then
    alter table public.vehicles add constraint vehicles_model_year_range
      check (model_year is null or model_year >= 1900) not valid;
  end if;

  if not exists (
    select 1 from pg_constraint
    where conrelid = 'public.vehicles'::regclass and conname = 'vehicles_plate_format'
  ) then
    alter table public.vehicles add constraint vehicles_plate_format
      check (
        plate is null or (
          plate = upper(btrim(plate)) and
          (plate ~ '^[A-Z]{3}[0-9]{4}$' or plate ~ '^[A-Z]{3}[0-9][A-Z][0-9]{2}$')
        )
      ) not valid;
  end if;

  if not exists (
    select 1 from pg_constraint
    where conrelid = 'public.vehicles'::regclass and conname = 'vehicles_renavam_format'
  ) then
    alter table public.vehicles add constraint vehicles_renavam_format
      check (renavam is null or renavam ~ '^[0-9]{11}$') not valid;
  end if;

  if not exists (
    select 1 from pg_constraint
    where conrelid = 'public.vehicles'::regclass and conname = 'vehicles_chassis_format'
  ) then
    alter table public.vehicles add constraint vehicles_chassis_format
      check (
        chassis is null or
        (chassis = upper(btrim(chassis)) and chassis ~ '^[A-HJ-NPR-Z0-9]{17}$')
      ) not valid;
  end if;
end;
$$;

create unique index if not exists vehicles_plate_unique
  on public.vehicles (upper(plate)) where plate is not null;
create unique index if not exists vehicles_renavam_unique
  on public.vehicles (renavam) where renavam is not null;
create unique index if not exists vehicles_chassis_unique
  on public.vehicles (upper(chassis)) where chassis is not null;
create unique index if not exists one_primary_vehicle_per_user
  on public.vehicles (user_id) where is_primary = true;

alter table public.vehicles enable row level security;

do $$
declare
  policy_name text;
begin
  for policy_name in
    select policyname from pg_policies
    where schemaname = 'public' and tablename = 'vehicles'
  loop
    execute format('drop policy if exists %I on public.vehicles', policy_name);
  end loop;
end;
$$;

create policy vehicles_select_own
on public.vehicles
for select
to authenticated
using ((select auth.uid()) = user_id);

revoke all on table public.vehicles from anon, authenticated;
grant select on table public.vehicles to authenticated;

create or replace function public.create_vehicle(
  p_brand text,
  p_model text,
  p_version text,
  p_manufacture_year integer,
  p_model_year integer,
  p_plate text,
  p_renavam text,
  p_chassis text,
  p_color text,
  p_fuel_type text,
  p_transmission text,
  p_engine text,
  p_mileage integer,
  p_make_primary boolean
)
returns setof public.vehicles
language plpgsql
security definer
set search_path = ''
as $$
declare
  current_user_id uuid := auth.uid();
  normalized_brand text := pg_catalog.btrim(p_brand);
  normalized_model text := pg_catalog.btrim(p_model);
  normalized_plate text := nullif(pg_catalog.upper(pg_catalog.btrim(p_plate)), '');
  normalized_renavam text := nullif(pg_catalog.btrim(p_renavam), '');
  normalized_chassis text := nullif(pg_catalog.upper(pg_catalog.btrim(p_chassis)), '');
  should_be_primary boolean;
  inserted_vehicle public.vehicles%rowtype;
  maximum_year integer := extract(year from current_date)::integer + 1;
begin
  if current_user_id is null then
    raise exception using errcode = '42501', message = 'authentication required';
  end if;

  if not exists (
    select 1 from public.profiles
    where id = current_user_id and active = true
  ) then
    raise exception using errcode = '42501', message = 'profile unavailable';
  end if;

  perform pg_catalog.pg_advisory_xact_lock(
    pg_catalog.hashtextextended(current_user_id::text, 0)
  );

  if normalized_brand is null or normalized_brand = '' or
     normalized_model is null or normalized_model = '' then
    raise exception using errcode = '22023', message = 'invalid vehicle';
  end if;
  if normalized_plate is not null and
     normalized_plate !~ '^[A-Z]{3}[0-9]{4}$' and
     normalized_plate !~ '^[A-Z]{3}[0-9][A-Z][0-9]{2}$' then
    raise exception using errcode = '22023', message = 'invalid plate';
  end if;
  if normalized_renavam is not null and normalized_renavam !~ '^[0-9]{11}$' then
    raise exception using errcode = '22023', message = 'invalid renavam';
  end if;
  if normalized_chassis is not null and normalized_chassis !~ '^[A-HJ-NPR-Z0-9]{17}$' then
    raise exception using errcode = '22023', message = 'invalid chassis';
  end if;
  if p_manufacture_year is not null and
     (p_manufacture_year < 1900 or p_manufacture_year > maximum_year) then
    raise exception using errcode = '22023', message = 'invalid manufacture year';
  end if;
  if p_model_year is not null and
     (p_model_year < 1900 or p_model_year > maximum_year) then
    raise exception using errcode = '22023', message = 'invalid model year';
  end if;
  if p_mileage is not null and p_mileage < 0 then
    raise exception using errcode = '22023', message = 'invalid mileage';
  end if;

  should_be_primary := coalesce(p_make_primary, false) or not exists (
    select 1 from public.vehicles
    where user_id = current_user_id and active = true
  );

  if should_be_primary then
    update public.vehicles
    set is_primary = false, updated_at = pg_catalog.now()
    where user_id = current_user_id and is_primary = true;
  end if;

  insert into public.vehicles (
    user_id, brand, model, version, manufacture_year, model_year, plate, renavam,
    chassis, color, fuel_type, transmission, engine, mileage, is_primary, active
  ) values (
    current_user_id,
    normalized_brand,
    normalized_model,
    nullif(pg_catalog.btrim(p_version), ''),
    p_manufacture_year,
    p_model_year,
    normalized_plate,
    normalized_renavam,
    normalized_chassis,
    nullif(pg_catalog.btrim(p_color), ''),
    nullif(pg_catalog.btrim(p_fuel_type), ''),
    nullif(pg_catalog.btrim(p_transmission), ''),
    nullif(pg_catalog.btrim(p_engine), ''),
    p_mileage,
    should_be_primary,
    true
  ) returning * into inserted_vehicle;

  return next inserted_vehicle;
end;
$$;

create or replace function public.set_primary_vehicle(p_vehicle_id uuid)
returns setof public.vehicles
language plpgsql
security definer
set search_path = ''
as $$
declare
  current_user_id uuid := auth.uid();
  selected_vehicle public.vehicles%rowtype;
begin
  if current_user_id is null then
    raise exception using errcode = '42501', message = 'authentication required';
  end if;

  perform pg_catalog.pg_advisory_xact_lock(
    pg_catalog.hashtextextended(current_user_id::text, 0)
  );

  select * into selected_vehicle
  from public.vehicles
  where id = p_vehicle_id and user_id = current_user_id and active = true
  for update;

  if not found then
    raise exception using errcode = 'P0002', message = 'vehicle unavailable';
  end if;

  update public.vehicles
  set is_primary = false, updated_at = pg_catalog.now()
  where user_id = current_user_id and is_primary = true and id <> p_vehicle_id;

  update public.vehicles
  set is_primary = true, updated_at = pg_catalog.now()
  where id = p_vehicle_id
  returning * into selected_vehicle;

  return next selected_vehicle;
end;
$$;

revoke all on function public.create_vehicle(
  text, text, text, integer, integer, text, text, text, text, text, text, text, integer, boolean
) from public, anon;
revoke all on function public.set_primary_vehicle(uuid) from public, anon;
grant execute on function public.create_vehicle(
  text, text, text, integer, integer, text, text, text, text, text, text, text, integer, boolean
) to authenticated;
grant execute on function public.set_primary_vehicle(uuid) to authenticated;
