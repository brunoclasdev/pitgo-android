-- specs/001-pittec-client-dashboard: let dashboard-staff auth users skip the CLIENT/PROVIDER
-- signup path, without touching that path for anyone else.
--
-- private.validate_account_signup() and private.handle_new_user_account() (both defined in the
-- Android repo's supabase/migrations/20260821000000_account_roles_provider_ownership.sql, applied
-- to this same shared project) currently reject any auth.users insert whose
-- raw_user_meta_data.account_type is not 'CLIENT'/'PROVIDER'. Dashboard staff accounts are
-- neither, and are created server-side via the Supabase Admin API
-- (services/dashboard-staff.ts::createDashboardStaff), which still runs through both of these
-- gates.
--
-- Fix: an additive guard at the top of each function. If the insert carries
-- app_metadata.dashboard_staff = 'true', both functions return immediately — no account_type is
-- required, and no account_roles/profiles row is created for that user. app_metadata can only be
-- set by a service_role-authenticated call (the public mobile signup flow only ever sets
-- user_metadata), so every existing and future mobile app version is completely unaffected: this
-- branch never fires for them. Everything below the guard is the exact, unmodified body from the
-- original migration (research.md §6, constitution §3.1 — paridade / não quebrar apps).

create or replace function private.validate_account_signup(input jsonb)
returns jsonb
language plpgsql
security invoker
set search_path = ''
as $$
declare
  requested_type text := input -> 'user' -> 'user_metadata' ->> 'account_type';
begin
  if (input -> 'user' -> 'app_metadata' ->> 'dashboard_staff') = 'true' then
    return '{}'::jsonb;
  end if;

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
  if (new.raw_app_meta_data ->> 'dashboard_staff') = 'true' then
    return new;
  end if;

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
