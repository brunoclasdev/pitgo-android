begin;
alter function public.reserve_order_payment(uuid,uuid,text,uuid,uuid,bigint,text,uuid,numeric,boolean)
rename to reserve_order_payment_before_guincho;
revoke all on function public.reserve_order_payment_before_guincho(uuid,uuid,text,uuid,uuid,bigint,text,uuid,numeric,boolean)
from public,anon,authenticated,service_role;

create function public.reserve_order_payment(p_client_id uuid,p_request_id uuid,p_dispatch_kind text,p_service_id uuid,
 p_appointment_id uuid,p_quote_version bigint,p_method text,p_card_id uuid,
 p_expected_amount numeric default null,p_creation_enabled boolean default false)
returns jsonb language plpgsql security definer set search_path='' as $$
declare a public.appointments%rowtype; s public.service_searches%rowtype;
 p public.payments%rowtype; card public.saved_cards%rowtype; attempt uuid:=gen_random_uuid(); lease uuid:=gen_random_uuid();
begin
 if p_dispatch_kind is distinct from 'GUINCHO_DIRECT' then
  return public.reserve_order_payment_before_guincho(p_client_id,p_request_id,p_dispatch_kind,p_service_id,
   p_appointment_id,p_quote_version,p_method,p_card_id,p_expected_amount,p_creation_enabled);
 end if;
 if p_client_id is null or p_request_id is null or p_appointment_id is null or p_quote_version is null
 or p_quote_version<=0 or p_method is null or p_method not in ('PIX','SAVED_CARD') then
 raise exception using errcode='22023',message='INVALID_INPUT'; end if;
 perform pg_advisory_xact_lock(hashtextextended(p_request_id::text,2200));
 select * into s from public.service_searches where id=p_request_id for update;
 select * into a from public.appointments where id=p_appointment_id for update;
 if s.client_id is distinct from p_client_id or s.dispatch_kind is distinct from 'GUINCHO_DIRECT'
 or a.client_id is distinct from p_client_id or a.dispatch_search_id is distinct from s.id
 or a.guincho_quote_id is null or a.quote_version is distinct from p_quote_version
 or (p_service_id is not null and a.service_id is distinct from p_service_id) then
 raise exception using errcode='22023',message='INVALID_PAYMENT_CONTEXT'; end if;
 select * into p from public.payments where request_id=s.id and
 (sync_state<>'CURRENT' or status in ('PENDING','APPROVED','REFUND_PENDING','REFUND_FAILED'))
 order by is_compensation,created_at desc limit 1 for update;
 if p.id is not null then
  if p.client_id<>p_client_id or p.dispatch_kind<>'GUINCHO_DIRECT' or p.appointment_id is distinct from a.id
  or p.quote_version is distinct from a.quote_version or p.amount is distinct from a.customer_total_amount
  or p.currency<>'BRL' then raise exception using errcode='22023',message='INVALID_PAYMENT_CONTEXT'; end if;
  return jsonb_build_object('created',false,'payment',to_jsonb(p));
 end if;
 if not p_creation_enabled then raise exception using errcode='55000',message='SERVICE_UNAVAILABLE'; end if;
 if a.status<>'AWAITING_PAYMENT' or s.status<>'AWAITING_PAYMENT' then
 raise exception using errcode='22023',message='INVALID_PAYMENT_CONTEXT'; end if;
 if a.quote_expires_at<=now() then raise exception using errcode='22023',message='QUOTE_EXPIRED'; end if;
 if not exists(select 1 from public.guincho_quotes q join public.guincho_trip_drafts d on d.id=q.draft_id
 join public.provider_services o on o.id=q.provider_service_id join public.guincho_pricing_rules r on r.id=q.pricing_rule_id
 where q.id=a.guincho_quote_id and d.revision=q.input_revision and o.active and o.price=q.base_amount
 and r.active and r.version=q.pricing_rule_version and r.effective_from<=now()
 and (r.effective_until is null or r.effective_until>now())) then
 raise exception using errcode='22023',message='PRICE_CHANGED'; end if;
 if p_expected_amount is distinct from a.customer_total_amount then
 raise exception using errcode='22023',message='PRICE_CHANGED'; end if;
 if p_method='SAVED_CARD' then
  select * into card from public.saved_cards where id=p_card_id and client_id=p_client_id;
  if card.id is null then raise exception using errcode='22023',message='CARD_NOT_FOUND'; end if;
 end if;
 insert into public.payments(id,request_id,client_id,dispatch_kind,service_id,appointment_id,quote_version,
 method,saved_card_id,card_brand,card_last_four_digits,amount,currency,provider_api,external_reference,idempotency_key,
 status,sync_state,next_action_at,work_action,lease_token,lease_until)
 values(attempt,s.id,p_client_id,'GUINCHO_DIRECT',a.service_id,a.id,a.quote_version,p_method,card.id,card.brand,
 card.last_four_digits,a.customer_total_amount,'BRL','ORDERS',attempt::text,attempt::text,'PENDING','CREATING',
 now()+interval '90 seconds','RECONCILE',lease,now()+interval '90 seconds') returning * into p;
 return jsonb_build_object('created',true,'payment',to_jsonb(p));
end; $$;
revoke all on function public.reserve_order_payment(uuid,uuid,text,uuid,uuid,bigint,text,uuid,numeric,boolean) from public,anon,authenticated;
grant execute on function public.reserve_order_payment(uuid,uuid,text,uuid,uuid,bigint,text,uuid,numeric,boolean) to service_role;
commit;
