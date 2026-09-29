-- specs/001-pittec-client-dashboard: audit trail for dashboard management actions (FR-017).
--
-- Stores who did what and when, never the changed values (constitution §3.2 — PII is never
-- logged). Lives in the `private` schema, same convention as `private.provider_ownership_transfers`
-- in the mobile schema: never selected directly by anon/authenticated, only through the
-- SECURITY DEFINER read RPC below, gated to ADMIN/OWNER. Retention (12 months, FR-017) is handled
-- by a scheduled purge job outside this migration.

create table if not exists private.dashboard_audit_log (
  id uuid primary key default gen_random_uuid(),
  actor_user_id uuid not null references auth.users(id) on delete set null,
  action text not null,
  entity_type text not null,
  entity_id uuid not null,
  occurred_at timestamptz not null default now()
);
create index if not exists dashboard_audit_log_occurred_at_idx
  on private.dashboard_audit_log (occurred_at desc);
create index if not exists dashboard_audit_log_entity_idx
  on private.dashboard_audit_log (entity_type, entity_id);
create index if not exists dashboard_audit_log_actor_idx
  on private.dashboard_audit_log (actor_user_id);
alter table private.dashboard_audit_log enable row level security;
revoke all on table private.dashboard_audit_log from public, anon, authenticated;
-- public.record_dashboard_action(): every mutating dashboard RPC calls this right after a
-- successful write. Any authenticated staff member may record an action for themselves; there is
-- no read grant on the table itself.
create or replace function public.record_dashboard_action(
  p_action text, p_entity_type text, p_entity_id uuid
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  if private.dashboard_access_level() is null then
    raise exception using errcode = '42501', message = 'STAFF_REQUIRED';
  end if;

  insert into private.dashboard_audit_log (actor_user_id, action, entity_type, entity_id)
  values (auth.uid(), p_action, p_entity_type, p_entity_id);
end;
$$;
revoke all on function public.record_dashboard_action(text, text, uuid) from public, anon;
grant execute on function public.record_dashboard_action(text, text, uuid) to authenticated;
-- public.list_dashboard_audit_log(): paginated read, ADMIN/OWNER only (FR-017 accountability
-- without letting every Manager browse everyone else's actions).
create or replace function public.list_dashboard_audit_log(
  p_actor_user_id uuid default null,
  p_entity_type text default null,
  p_limit integer default 50,
  p_offset integer default 0
)
returns setof private.dashboard_audit_log
language sql
stable
security definer
set search_path = ''
as $$
  select log.*
  from private.dashboard_audit_log log
  where private.dashboard_access_at_least('ADMIN')
    and (p_actor_user_id is null or log.actor_user_id = p_actor_user_id)
    and (p_entity_type is null or log.entity_type = p_entity_type)
  order by log.occurred_at desc
  limit least(greatest(p_limit, 1), 200)
  offset greatest(p_offset, 0);
$$;
revoke all on function public.list_dashboard_audit_log(uuid, text, integer, integer)
  from public, anon;
grant execute on function public.list_dashboard_audit_log(uuid, text, integer, integer)
  to authenticated;
