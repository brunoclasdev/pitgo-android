begin;

alter table public.service_searches drop constraint if exists service_searches_last_event_valid;
alter table public.service_searches add constraint service_searches_last_event_valid check (
  last_event is null or last_event in (
    'PROVIDER_REJECTED','OFFER_EXPIRED','PROVIDER_CANCELLED','SEARCH_CONTINUES',
    'CLIENT_CONFIRMATION_TIMEOUT','QUOTE_DECLINED','PAYMENT_TIMEOUT'
  )
);

-- Older quotes may have entered AWAITING_PAYMENT before quote_expires_at was
-- introduced. Give them the same 15-minute deadline based on confirmation.
update public.appointments a
set quote_expires_at = coalesce(a.client_confirmed_at, a.updated_at, now()) + interval '15 minutes'
from public.services service
where service.id = a.service_id
  and service.dispatch_kind = 'PITTEC_AUTO_SEQUENTIAL'
  and a.status = 'AWAITING_PAYMENT'
  and a.quote_expires_at is null;

-- Apply the rule immediately to already overdue records; the scheduled job
-- handles all later records.
select public.expire_due_pittec_payments(500);

commit;
