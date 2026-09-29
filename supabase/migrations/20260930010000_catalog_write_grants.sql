-- Catalog mutations must go through the staff-gated SECURITY DEFINER RPCs.
-- Preserve existing RLS/read policies, IDs, offers and service_role access.
begin;
revoke all on table public.service_categories from public, anon, authenticated;
revoke all on table public.services from public, anon, authenticated;
grant select on table public.service_categories to authenticated;
grant select on table public.services to authenticated;

-- Existing active-only policies hide deactivated offers from their owning provider.
-- Add read access only to catalog rows referenced by that provider's historical offers.
create policy services_select_provider_history on public.services
for select to authenticated
using (exists (
  select 1 from public.provider_services offer
  join public.providers provider on provider.id = offer.provider_id
  where offer.service_id = services.id and provider.account_id = (select auth.uid())
));
create policy service_categories_select_provider_history on public.service_categories
for select to authenticated
using (exists (
  select 1 from public.services service
  join public.provider_services offer on offer.service_id = service.id
  join public.providers provider on provider.id = offer.provider_id
  where service.category_id = service_categories.id
    and provider.account_id = (select auth.uid())
));
commit;
