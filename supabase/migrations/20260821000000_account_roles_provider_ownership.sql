create extension if not exists pgcrypto;

create table if not exists public.account_roles (
  user_id uuid primary key references auth.users(id) on delete cascade,
  account_type text not null,
  created_at timestamptz not null default now(),
  constraint account_roles_type_valid check (account_type in ('CLIENT', 'PROVIDER'))
);

-- Every pre-009 profile remains a client, even when it also owns a legacy provider.
insert into public.account_roles (user_id, account_type)
select profile.id, 'CLIENT'
from public.profiles profile
on conflict (user_id) do nothing;

do $$
declare
  fk_name text;
begin
  if to_regclass('public.providers') is null then
    raise exception using errcode = 'P0002', message = 'Provider schema must be installed first';
  end if;

  if exists (
    select 1 from public.providers provider
    left join auth.users auth_user on auth_user.id = provider.id
    where auth_user.id is null
  ) then
    raise exception using errcode = 'P0002', message = 'Legacy provider without Auth owner';
  end if;

  alter table public.providers add column if not exists account_id uuid;
  update public.providers set account_id = id where account_id is null;

  if exists (
    select account_id from public.providers group by account_id having count(*) > 1
  ) then
    raise exception using errcode = 'P0002', message = 'Duplicate provider account ownership';
  end if;

  -- Drop only the legacy providers.id -> auth.users FK. Provider PK and child FKs stay intact.
  for fk_name in
    select constraint_name
    from information_schema.referential_constraints
    where constraint_schema = 'public'
      and unique_constraint_schema = 'auth'
      and constraint_name in (
        select constraint_name
        from information_schema.key_column_usage
        where table_schema = 'public' and table_name = 'providers' and column_name = 'id'
      )
  loop
    execute format('alter table public.providers drop constraint %I', fk_name);
  end loop;

  alter table public.providers alter column account_id set not null;
  alter table public.providers alter column id set default gen_random_uuid();

  if not exists (
    select 1 from pg_constraint
    where conrelid = 'public.providers'::regclass
      and conname = 'providers_account_id_fkey'
  ) then
    alter table public.providers
      add constraint providers_account_id_fkey
      foreign key (account_id) references auth.users(id) on delete restrict;
  end if;

  if not exists (
    select 1 from pg_constraint
    where conrelid = 'public.providers'::regclass
      and conname = 'providers_account_id_unique'
  ) then
    alter table public.providers
      add constraint providers_account_id_unique unique (account_id);
  end if;
end;
$$;

create or replace function private.has_account_type(expected_type text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1 from public.account_roles role
    where role.user_id = auth.uid() and role.account_type = expected_type
  );
$$;

revoke all on function private.has_account_type(text) from public, anon;
grant execute on function private.has_account_type(text) to authenticated;

-- The hook rejects unknown signup intents before auth.users is written. The trigger below repeats
-- the check and is the transactional authority.
create or replace function private.validate_account_signup(input jsonb)
returns jsonb
language plpgsql
security invoker
set search_path = ''
as $$
declare
  requested_type text := input -> 'user' -> 'user_metadata' ->> 'account_type';
begin
  if requested_type is null or requested_type not in ('CLIENT', 'PROVIDER') then
    return jsonb_build_object(
      'error', jsonb_build_object(
        'http_code', 400,
        'message', 'Invalid account registration'
      )
    );
  end if;
  return '{}'::jsonb;
end;
$$;

revoke all on function private.validate_account_signup(jsonb) from public, anon, authenticated;
grant usage on schema private to supabase_auth_admin;
grant execute on function private.validate_account_signup(jsonb) to supabase_auth_admin;

drop trigger if exists on_auth_user_created_create_profile on auth.users;

create or replace function private.handle_new_user_account()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  requested_type text := new.raw_user_meta_data ->> 'account_type';
  normalized_cpf text;
  normalized_cnpj text;
begin
  if requested_type is null or requested_type not in ('CLIENT', 'PROVIDER') then
    raise exception using errcode = '22023', message = 'Invalid account registration';
  end if;

  insert into public.account_roles (user_id, account_type)
  values (new.id, requested_type);

  if requested_type = 'CLIENT' then
    normalized_cpf := nullif(
      pg_catalog.regexp_replace(coalesce(new.raw_user_meta_data ->> 'cpf', ''), '[^0-9]', '', 'g'),
      ''
    );
    normalized_cnpj := nullif(
      pg_catalog.regexp_replace(
        pg_catalog.upper(coalesce(new.raw_user_meta_data ->> 'cnpj', '')),
        '[^0-9A-Z]', '', 'g'
      ),
      ''
    );

    insert into public.profiles (id, name, email, phone, cpf, cnpj)
    values (
      new.id,
      pg_catalog.btrim(coalesce(new.raw_user_meta_data ->> 'name', '')),
      pg_catalog.lower(pg_catalog.btrim(coalesce(new.email, ''))),
      pg_catalog.regexp_replace(
        coalesce(new.raw_user_meta_data ->> 'phone', ''), '[^0-9]', '', 'g'
      ),
      normalized_cpf,
      normalized_cnpj
    );
  end if;

  return new;
end;
$$;

revoke all on function private.handle_new_user_account() from public, anon, authenticated;

create trigger on_auth_user_created_create_account
after insert on auth.users
for each row execute function private.handle_new_user_account();

create or replace function public.get_my_account_context()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  current_user_id uuid := auth.uid();
  current_type text;
  profile_count integer;
  profile_active boolean;
  provider_count integer;
  current_provider_id uuid;
  address_count integer := 0;
  offer_count integer := 0;
  client_state text;
  provider_state text;
  legacy_available boolean := false;
begin
  if current_user_id is null then
    raise exception using errcode = '42501', message = 'Authenticated session required';
  end if;

  select role.account_type into current_type
  from public.account_roles role
  where role.user_id = current_user_id;

  if current_type is null then
    raise exception using errcode = 'P0002', message = 'Account context unavailable';
  end if;

  select count(*), bool_and(profile.active)
  into profile_count, profile_active
  from public.profiles profile where profile.id = current_user_id;

  select count(*), (array_agg(provider.id))[1]
  into provider_count, current_provider_id
  from public.providers provider where provider.account_id = current_user_id;

  if current_type = 'CLIENT' then
    client_state := case
      when profile_count = 0 then 'MISSING'
      when profile_count <> 1 then 'INCONSISTENT'
      when not coalesce(profile_active, false) then 'INACTIVE'
      else 'READY'
    end;
    legacy_available := provider_count = 1;
    provider_state := null;
    current_provider_id := null;
  else
    client_state := null;
    if profile_count <> 0 then
      provider_state := 'INCOMPLETE';
    elsif provider_count = 0 then
      provider_state := 'ONBOARDING';
    elsif provider_count <> 1 then
      provider_state := 'INCOMPLETE';
    else
      select count(*) into address_count
      from public.addresses address where address.provider_id = current_provider_id;
      select count(*) into offer_count
      from public.provider_services offer where offer.provider_id = current_provider_id;
      provider_state := case
        when address_count = 1 and offer_count >= 1 then 'READY'
        else 'INCOMPLETE'
      end;
    end if;
  end if;

  return pg_catalog.jsonb_build_object(
    'account_type', current_type,
    'client_state', client_state,
    'provider_state', provider_state,
    'provider_id', current_provider_id,
    'legacy_provider_migration_available', legacy_available
  );
end;
$$;

revoke all on function public.get_my_account_context() from public, anon;
grant execute on function public.get_my_account_context() to authenticated;

alter table public.account_roles enable row level security;
revoke all on table public.account_roles from public, anon, authenticated;

do $$
declare
  policy_name text;
begin
  for policy_name in
    select policyname from pg_policies
    where schemaname = 'public' and tablename = 'profiles'
  loop execute format('drop policy if exists %I on public.profiles', policy_name); end loop;

  for policy_name in
    select policyname from pg_policies
    where schemaname = 'public' and tablename = 'vehicles'
  loop execute format('drop policy if exists %I on public.vehicles', policy_name); end loop;

  for policy_name in
    select policyname from pg_policies
    where schemaname = 'public' and tablename = 'providers'
  loop execute format('drop policy if exists %I on public.providers', policy_name); end loop;

  for policy_name in
    select policyname from pg_policies
    where schemaname = 'public' and tablename = 'addresses'
  loop execute format('drop policy if exists %I on public.addresses', policy_name); end loop;

  for policy_name in
    select policyname from pg_policies
    where schemaname = 'public' and tablename = 'provider_services'
  loop execute format('drop policy if exists %I on public.provider_services', policy_name); end loop;
end;
$$;

create policy profiles_select_client_own on public.profiles
for select to authenticated
using (private.has_account_type('CLIENT') and id = (select auth.uid()));

create policy vehicles_select_client_own on public.vehicles
for select to authenticated
using (private.has_account_type('CLIENT') and user_id = (select auth.uid()));

create policy providers_select_provider_own on public.providers
for select to authenticated
using (private.has_account_type('PROVIDER') and account_id = (select auth.uid()));

create policy addresses_select_provider_own on public.addresses
for select to authenticated
using (
  private.has_account_type('PROVIDER') and exists (
    select 1 from public.providers provider
    where provider.id = addresses.provider_id and provider.account_id = (select auth.uid())
  )
);

create policy provider_services_select_provider_own on public.provider_services
for select to authenticated
using (
  private.has_account_type('PROVIDER') and exists (
    select 1 from public.providers provider
    where provider.id = provider_services.provider_id and provider.account_id = (select auth.uid())
  )
);

revoke all on table public.profiles from anon, authenticated;
revoke all on table public.vehicles from anon, authenticated;
revoke all on table public.providers from anon, authenticated;
revoke all on table public.addresses from anon, authenticated;
revoke all on table public.provider_services from anon, authenticated;
grant select on table public.profiles to authenticated;
grant select on table public.vehicles to authenticated;
grant select on table public.providers to authenticated;
grant select on table public.addresses to authenticated;
grant select on table public.provider_services to authenticated;

create table if not exists private.provider_ownership_transfers (
  id uuid primary key default gen_random_uuid(),
  provider_id uuid not null references public.providers(id) on delete cascade,
  source_account_id uuid not null references auth.users(id) on delete cascade,
  target_account_id uuid references auth.users(id) on delete restrict,
  token_hash bytea not null unique,
  status text not null default 'PENDING',
  expires_at timestamptz not null,
  created_at timestamptz not null default now(),
  claimed_at timestamptz,
  constraint provider_transfers_status_valid
    check (status in ('PENDING', 'CLAIMED', 'CANCELLED', 'EXPIRED')),
  constraint provider_transfers_claim_consistent check (
    (status = 'CLAIMED' and target_account_id is not null and claimed_at is not null)
    or status <> 'CLAIMED'
  )
);

create unique index if not exists provider_transfers_one_pending
  on private.provider_ownership_transfers(provider_id)
  where status = 'PENDING';

revoke all on table private.provider_ownership_transfers from public, anon, authenticated;

create or replace function public.begin_provider_ownership_migration(p_transfer_token text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  current_user_id uuid := auth.uid();
  owned_provider_id uuid;
  hashed_token bytea;
begin
  if current_user_id is null or not private.has_account_type('CLIENT') then
    raise exception using errcode = '42501', message = 'Client session required';
  end if;
  if p_transfer_token is null or p_transfer_token !~ '^[A-Za-z0-9_-]{43,128}$' then
    raise exception using errcode = '22023', message = 'Invalid transfer proof';
  end if;

  select provider.id into owned_provider_id
  from public.providers provider
  where provider.account_id = current_user_id;
  if owned_provider_id is null then
    raise exception using errcode = 'P0002', message = 'Provider migration unavailable';
  end if;
  if not exists (select 1 from public.addresses where provider_id = owned_provider_id)
    or not exists (select 1 from public.provider_services where provider_id = owned_provider_id)
  then
    raise exception using errcode = 'P0002', message = 'Provider migration unavailable';
  end if;

  perform pg_catalog.pg_advisory_xact_lock(
    pg_catalog.hashtextextended(owned_provider_id::text, 9001)
  );
  update private.provider_ownership_transfers
    set status = 'EXPIRED'
  where provider_id = owned_provider_id and status = 'PENDING' and expires_at <= now();

  hashed_token := extensions.digest(p_transfer_token, 'sha256');
  if exists (
    select 1 from private.provider_ownership_transfers transfer
    where transfer.provider_id = owned_provider_id and transfer.status = 'PENDING'
      and transfer.token_hash <> hashed_token
  ) then
    raise exception using errcode = '55000', message = 'Provider migration already pending';
  end if;

  insert into private.provider_ownership_transfers (
    provider_id, source_account_id, token_hash, expires_at
  ) values (owned_provider_id, current_user_id, hashed_token, now() + interval '15 minutes')
  on conflict (token_hash) do update
    set expires_at = greatest(private.provider_ownership_transfers.expires_at, excluded.expires_at)
    where private.provider_ownership_transfers.status = 'PENDING';

  return jsonb_build_object('outcome', 'PENDING', 'expires_in_seconds', 900);
end;
$$;

create or replace function public.claim_provider_ownership_migration(p_transfer_token text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  current_user_id uuid := auth.uid();
  transfer private.provider_ownership_transfers%rowtype;
begin
  if current_user_id is null or not private.has_account_type('PROVIDER') then
    raise exception using errcode = '42501', message = 'Provider session required';
  end if;
  if exists (select 1 from public.profiles where id = current_user_id)
    or exists (select 1 from public.providers where account_id = current_user_id)
  then
    raise exception using errcode = '55000', message = 'Provider migration unavailable';
  end if;
  if p_transfer_token is null or p_transfer_token !~ '^[A-Za-z0-9_-]{43,128}$' then
    raise exception using errcode = '22023', message = 'Invalid transfer proof';
  end if;

  select * into transfer
  from private.provider_ownership_transfers candidate
  where candidate.token_hash = extensions.digest(p_transfer_token, 'sha256')
  for update;
  if transfer.id is null or transfer.status <> 'PENDING' or transfer.expires_at <= now() then
    raise exception using errcode = 'P0002', message = 'Provider migration unavailable';
  end if;
  if not private.has_account_type('PROVIDER') then
    raise exception using errcode = '42501', message = 'Provider session required';
  end if;

  perform pg_catalog.pg_advisory_xact_lock(
    pg_catalog.hashtextextended(transfer.provider_id::text, 9001)
  );
  update public.providers
    set account_id = current_user_id, updated_at = now()
  where id = transfer.provider_id and account_id = transfer.source_account_id;
  if not found then
    raise exception using errcode = 'P0002', message = 'Provider migration unavailable';
  end if;

  update private.provider_ownership_transfers
    set status = 'CLAIMED', target_account_id = current_user_id, claimed_at = now()
  where id = transfer.id;
  return jsonb_build_object('outcome', 'MIGRATED');
end;
$$;

revoke all on function public.begin_provider_ownership_migration(text) from public, anon;
revoke all on function public.claim_provider_ownership_migration(text) from public, anon;
grant execute on function public.begin_provider_ownership_migration(text) to authenticated;
grant execute on function public.claim_provider_ownership_migration(text) to authenticated;
