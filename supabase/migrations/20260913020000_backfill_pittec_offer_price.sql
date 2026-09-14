-- 20260913010000_pittec_call_tracking.sql introduced a new discovery condition
-- (offer.price > 0 and not offer.is_free) that did not exist before. Every PitTec provider
-- registered under the previous free-by-design model (spec 016) has is_free=true/price=0 in
-- provider_services, which the new condition now excludes entirely from advance_pittec_dispatch —
-- existing PitTec providers silently stopped being discoverable. Backfill them to a real price so
-- they remain dispatchable under the new quote-based flow. Forward-only, idempotent (WHERE guards
-- against re-touching rows already fixed).
begin;

update public.provider_services as offer
set price = 50.00,
    is_free = false,
    updated_at = now()
from public.providers as provider
where offer.provider_id = provider.id
  and provider.location_mode = 'PITTEC'
  and exists (
    select 1 from public.services as service
    where service.id = offer.service_id
      and service.dispatch_kind = 'PITTEC_AUTO_SEQUENTIAL'
  )
  and (offer.is_free or offer.price <= 0);

commit;
