begin;

alter table public.payments add column checkout_environment text not null default 'prod'
 check (checkout_environment in ('prod','homol'));

-- Guincho keeps its commercial quote; payment_amount records the actual charge.
-- Existing Prod reservations already have payment_amount = customer_total_amount.
do $$
declare signature text; definition text;
begin
 foreach signature in array array[
  'public.reserve_order_payment(uuid,uuid,text,uuid,uuid,bigint,text,uuid,numeric,boolean)',
  'private.reconcile_guincho_payment_change()',
  'public.respond_guincho_request(uuid,bigint,uuid,boolean)',
  'private.guincho_provider_transition(uuid,bigint,text)'
 ] loop
  definition := pg_get_functiondef(signature::regprocedure);
  if position('a.customer_total_amount' in definition)=0 then
   raise exception 'Unexpected Guincho payment contract: %', signature;
  end if;
  definition := replace(definition,'a.customer_total_amount','coalesce(a.payment_amount,a.customer_total_amount)');
  execute definition;
 end loop;
end $$;

-- Only the authenticated Edge Function can reserve real Homol charges.
-- The Android Homol checkout generates one cent-precise amount per request.
create function public.reserve_homol_order_payment(
 p_client_id uuid,p_request_id uuid,p_dispatch_kind text,p_service_id uuid,
 p_appointment_id uuid,p_quote_version bigint,p_method text,p_card_id uuid,
 p_expected_amount numeric default null,p_creation_enabled boolean default false
) returns jsonb language plpgsql security definer set search_path='' as $$
declare reservation jsonb; payment_uuid uuid; has_existing boolean;
begin
 if p_expected_amount is null or p_expected_amount not between 1.00 and 2.00
 or p_expected_amount<>round(p_expected_amount,2) then
  raise exception using errcode='22023',message='INVALID_INPUT';
 end if;
 perform pg_advisory_xact_lock(hashtextextended(p_request_id::text,2200));
 select exists(select 1 from public.payments where request_id=p_request_id and
  (sync_state<>'CURRENT' or status in ('PENDING','APPROVED','REFUND_PENDING','REFUND_FAILED')))
 into has_existing;
 if not has_existing and p_dispatch_kind in ('PITTEC_AUTO_SEQUENTIAL','GUINCHO_DIRECT') then
  update public.appointments set payment_amount=p_expected_amount,payment_test_mode=true
   where id=p_appointment_id and client_id=p_client_id and dispatch_search_id=p_request_id
   and quote_version=p_quote_version and status='AWAITING_PAYMENT';
 end if;
 -- Preserve all existing ownership, quote, deadline, card and dispatch guards.
 reservation := public.reserve_order_payment(p_client_id,p_request_id,p_dispatch_kind,p_service_id,
  p_appointment_id,p_quote_version,p_method,p_card_id,
  case when p_dispatch_kind='MOBILE_SEQUENTIAL' or has_existing then null else p_expected_amount end,
  p_creation_enabled);
 if (reservation->>'created')::boolean then
  payment_uuid := (reservation->'payment'->>'id')::uuid;
  update public.payments set amount=p_expected_amount,checkout_environment='homol'
   where id=payment_uuid and status='PENDING' and sync_state='CREATING';
  select jsonb_build_object('created',true,'payment',to_jsonb(p)) into reservation
   from public.payments p where id=payment_uuid;
 end if;
 return reservation;
end $$;
revoke all on function public.reserve_homol_order_payment(uuid,uuid,text,uuid,uuid,bigint,text,uuid,numeric,boolean)
 from public,anon,authenticated;
grant execute on function public.reserve_homol_order_payment(uuid,uuid,text,uuid,uuid,bigint,text,uuid,numeric,boolean)
 to service_role;

commit;
