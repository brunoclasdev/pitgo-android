-- specs/001-pittec-client-dashboard: internal dashboard access-level table.
--
-- Dashboard staff (Manager/Admin/Owner) are Supabase Auth users that are NOT mobile CLIENT/PROVIDER
-- accounts (see the app_metadata signup guard in this same migration set). This table is the only
-- place their access level lives; RLS + the RPCs below enforce the strictly-increasing creation
-- hierarchy from FR-013: Owner creates Admin, Admin creates Manager, nobody creates a peer or
-- higher level. Nothing here touches the mobile-owned account_roles/profiles/providers tables.

create table if not exists public.dashboard_staff (
  user_id uuid primary key references auth.users(id) on delete cascade,
  access_level text not null,
  created_by uuid references auth.users(id) on delete set null,
  active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint dashboard_staff_access_level_valid
    check (access_level in ('MANAGER', 'ADMIN', 'OWNER'))
);
create index if not exists dashboard_staff_access_level_idx
  on public.dashboard_staff (access_level);
alter table public.dashboard_staff enable row level security;
revoke all on table public.dashboard_staff from public, anon, authenticated;
grant select on table public.dashboard_staff to authenticated;
create or replace function private.set_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;
create trigger dashboard_staff_set_updated_at
before update on public.dashboard_staff
for each row execute function private.set_updated_at();
-- private.dashboard_access_level(): the caller's own level, or null if not staff / inactive.
create or replace function private.dashboard_access_level()
returns text
language sql
stable
security definer
set search_path = ''
as $$
  select staff.access_level
  from public.dashboard_staff staff
  where staff.user_id = auth.uid() and staff.active;
$$;
revoke all on function private.dashboard_access_level() from public, anon;
grant execute on function private.dashboard_access_level() to authenticated;
-- private.dashboard_access_at_least(min_level): hierarchy check, MANAGER < ADMIN < OWNER.
create or replace function private.dashboard_access_at_least(min_level text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select case private.dashboard_access_level()
    when 'OWNER' then true
    when 'ADMIN' then min_level in ('ADMIN', 'MANAGER')
    when 'MANAGER' then min_level = 'MANAGER'
    else false
  end;
$$;
revoke all on function private.dashboard_access_at_least(text) from public, anon;
grant execute on function private.dashboard_access_at_least(text) to authenticated;
create policy dashboard_staff_select_staff
on public.dashboard_staff
for select
to authenticated
using (private.dashboard_access_level() is not null);
-- public.create_dashboard_staff(): inserts the access-level row for an auth user that was just
-- created server-side (service_role, Admin API, with app_metadata.dashboard_staff = true — see
-- the signup guard migration). Enforces FR-013: a caller can only grant a level strictly below
-- their own.
create or replace function public.create_dashboard_staff(p_user_id uuid, p_access_level text)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  caller_level text := private.dashboard_access_level();
begin
  if caller_level is null then
    raise exception using errcode = '42501', message = 'STAFF_REQUIRED';
  end if;
  if p_access_level not in ('MANAGER', 'ADMIN') then
    raise exception using errcode = '22023', message = 'INVALID_ACCESS_LEVEL';
  end if;
  if (caller_level = 'OWNER' and p_access_level <> 'ADMIN')
    or (caller_level = 'ADMIN' and p_access_level <> 'MANAGER')
    or caller_level = 'MANAGER'
  then
    raise exception using errcode = '42501', message = 'HIERARCHY_VIOLATION';
  end if;

  insert into public.dashboard_staff (user_id, access_level, created_by)
  values (p_user_id, p_access_level, auth.uid());
end;
$$;
revoke all on function public.create_dashboard_staff(uuid, text) from public, anon;
grant execute on function public.create_dashboard_staff(uuid, text) to authenticated;
-- public.set_dashboard_staff_active(): activate/deactivate a staff account, same hierarchy rule
-- as creation (a caller may only act on accounts strictly below their own level).
create or replace function public.set_dashboard_staff_active(p_user_id uuid, p_active boolean)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  caller_level text := private.dashboard_access_level();
  target_level text;
begin
  select staff.access_level into target_level
  from public.dashboard_staff staff where staff.user_id = p_user_id;
  if target_level is null then
    raise exception using errcode = 'P0002', message = 'STAFF_NOT_FOUND';
  end if;
  if not (
    (caller_level = 'OWNER' and target_level = 'ADMIN')
    or (caller_level = 'ADMIN' and target_level = 'MANAGER')
  ) then
    raise exception using errcode = '42501', message = 'HIERARCHY_VIOLATION';
  end if;

  update public.dashboard_staff set active = p_active where user_id = p_user_id;
end;
$$;
revoke all on function public.set_dashboard_staff_active(uuid, boolean) from public, anon;
grant execute on function public.set_dashboard_staff_active(uuid, boolean) to authenticated;
