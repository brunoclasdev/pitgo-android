-- The realtime.messages RLS policy created in 20260823020000 (mobile_dispatch_receive) embeds
-- direct EXISTS subqueries against public.service_searches/public.appointments/public.providers.
-- RLS predicates run with the querying role's own privileges (authenticated), not as security
-- definer. That same migration revoked ALL privileges on service_searches/provider_discovery_locations
-- from authenticated, so Postgres has to reject the whole OR'd USING expression with
-- "permission denied for table service_searches" as soon as it needs to plan/evaluate either
-- branch — including the provider-dispatch branch, which never touches that table itself but is
-- OR'd alongside the branch that does. Supabase Realtime reports any such evaluation error back
-- to the client as a generic "Unauthorized", which is what both CLIENT and PROVIDER devices hit
-- when subscribing to their private dispatch channels.
--
-- Fix: move each EXISTS check into its own SECURITY DEFINER helper (same pattern already used by
-- private.dispatch_is_client/private.dispatch_is_provider), so the policy only needs EXECUTE on
-- the helper — never table-level SELECT — matching the "no direct DML for the APK" model the rest
-- of this feature already follows.
begin;

create or replace function private.can_read_mobile_dispatch_search(p_search_id text)
returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (
    select 1
    from public.service_searches s
    left join public.appointments a on a.dispatch_search_id = s.id
    left join public.providers p on p.id = a.provider_id
    where s.id::text = p_search_id
      and (s.client_id = auth.uid() or p.account_id = auth.uid())
  );
$$;

create or replace function private.can_read_provider_dispatch(p_provider_id text)
returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from public.providers p
    where p.id::text = p_provider_id and p.account_id = auth.uid()
  );
$$;

revoke all on function private.can_read_mobile_dispatch_search(text) from public, anon, authenticated;
revoke all on function private.can_read_provider_dispatch(text) from public, anon, authenticated;
grant execute on function private.can_read_mobile_dispatch_search(text) to authenticated;
grant execute on function private.can_read_provider_dispatch(text) to authenticated;

drop policy if exists mobile_dispatch_receive on realtime.messages;
create policy mobile_dispatch_receive on realtime.messages for select to authenticated using (
  extension = 'broadcast' and (
    (split_part(realtime.topic(), ':', 1) = 'mobile-dispatch'
      and private.can_read_mobile_dispatch_search(split_part(realtime.topic(), ':', 2)))
    or
    (split_part(realtime.topic(), ':', 1) = 'provider-dispatch'
      and private.can_read_provider_dispatch(split_part(realtime.topic(), ':', 2)))
  )
);

notify pgrst, 'reload schema';

commit;
