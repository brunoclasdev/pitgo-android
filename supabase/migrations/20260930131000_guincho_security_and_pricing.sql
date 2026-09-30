begin;
alter table public.payments drop constraint payments_dispatch_kind_check;
alter table public.payments add constraint payments_dispatch_kind_check
check(dispatch_kind in ('MOBILE_SEQUENTIAL','PITTEC_AUTO_SEQUENTIAL','GUINCHO_DIRECT'));
alter table public.payments add constraint payments_guincho_context_check
check(dispatch_kind<>'GUINCHO_DIRECT' or (appointment_id is not null and quote_version>0 and currency='BRL'));

alter table public.service_searches drop constraint service_search_status_check;
alter table public.service_searches add constraint service_search_status_check check(status in (
 'SEARCHING','OPTION_FOUND','AWAITING_PROVIDER','AWAITING_CLIENT_CONFIRMATION','AWAITING_PAYMENT',
 'ACCEPTED','IN_PROGRESS','AWAITING_CLIENT_COMPLETION_CONFIRMATION','DISPUTED','CANCELLED','COMPLETED','EXHAUSTED'));
alter table public.appointments drop constraint appointments_status_check;
alter table public.appointments add constraint appointments_status_check check(status in (
 'PENDING','AWAITING_CLIENT_CONFIRMATION','AWAITING_PAYMENT','ACCEPTED','IN_PROGRESS',
 'AWAITING_CLIENT_COMPLETION_CONFIRMATION','DISPUTED','COMPLETED','CANCELLED','PROVIDER_REJECTED','EXPIRED'));
-- Existing operational indices already cover payment/confirmation and exclude DISPUTED.
insert into public.guincho_pricing_rules(distance_rate_per_km,markup_percent,fee_percent,fee_fixed_amount)
select 3,10,fee_percent,fee_fixed_amount from public.pittec_pricing_rules
where active and effective_from<=now() and (effective_until is null or effective_until>now())
order by effective_from desc,version desc limit 1;

alter table public.guincho_pricing_rules enable row level security;
alter table public.guincho_trip_drafts enable row level security;
alter table public.guincho_quotes enable row level security;
alter table public.appointment_disputes enable row level security;
revoke all on public.guincho_pricing_rules,public.guincho_trip_drafts,public.guincho_quotes,public.appointment_disputes
from public,anon,authenticated;
-- Quotes contain internal prices; only projections through RPCs may be read by participants.
grant select on public.guincho_trip_drafts to authenticated;
create policy guincho_draft_owner on public.guincho_trip_drafts for select to authenticated using(client_id=auth.uid());
grant all on public.guincho_pricing_rules,public.guincho_trip_drafts,public.guincho_quotes,public.appointment_disputes to service_role;
grant usage,select on sequence public.guincho_pricing_rules_version_seq,public.guincho_quotes_version_seq to service_role;
commit;
