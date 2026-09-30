begin;
create or replace function public.simulate_guincho_payment(p_request_id uuid,p_status text,p_method text default 'PIX') returns jsonb
language plpgsql security definer set search_path='' as $$
#variable_conflict use_column
declare a public.appointments%rowtype; s public.service_searches%rowtype; p public.payments%rowtype; payment_uuid uuid:=gen_random_uuid();
begin
 if not exists(select 1 from private.guincho_runtime_settings where simulation_enabled) then
 raise exception using errcode='42501',message='SIMULATION_DISABLED'; end if;
 if p_status is null or p_status not in ('PENDING','APPROVED','REJECTED','EXPIRED','CANCELLED')
 or p_method is null or p_method not in ('PIX','SAVED_CARD') then raise exception using errcode='22023',message='INVALID_INPUT'; end if;
 perform pg_advisory_xact_lock(hashtextextended(p_request_id::text,2200));
 select * into s from public.service_searches where id=p_request_id and client_id=auth.uid() and dispatch_kind='GUINCHO_DIRECT' for update;
 if s.id is null then raise exception using errcode='42501',message='CLIENT_REQUIRED'; end if;
 select * into a from public.appointments where id=s.current_appointment_id for update;
 select * into p from public.payments where request_id=s.id order by created_at desc limit 1 for update;
 if p.id is not null and p.provider_api<>'SIMULATED' then raise exception using errcode='22023',message='INVALID_PAYMENT_CONTEXT'; end if;
 if p.id is null then
  if a.status<>'AWAITING_PAYMENT' or a.quote_expires_at<=now() or p_status<>'PENDING' then
   raise exception using errcode='22023',message='INVALID_TRANSITION'; end if;
  if not exists(select 1 from public.guincho_quotes q join public.guincho_trip_drafts d on d.id=q.draft_id
   join public.provider_services o on o.id=q.provider_service_id join public.guincho_pricing_rules r on r.id=q.pricing_rule_id
   where q.id=a.guincho_quote_id and d.revision=q.input_revision and o.active and o.price=q.base_amount
   and r.active and r.version=q.pricing_rule_version and r.effective_from<=now()
   and (r.effective_until is null or r.effective_until>now())) then
   raise exception using errcode='22023',message='PRICE_CHANGED'; end if;
  insert into public.payments(id,request_id,client_id,dispatch_kind,service_id,appointment_id,quote_version,
   method,amount,currency,provider_api,external_reference,idempotency_key,status,sync_state)
  values(payment_uuid,s.id,auth.uid(),'GUINCHO_DIRECT',a.service_id,a.id,a.quote_version,p_method,
   a.customer_total_amount,'BRL','SIMULATED','SIMULATED-'||payment_uuid::text,payment_uuid::text,'PENDING','CURRENT') returning * into p;
 elsif p.status='PENDING' and p_status<>'PENDING' then
  update public.payments set status=p_status,settled_amount=case when p_status='APPROVED' then amount else 0 end,
   revision=revision+1,updated_at=now() where id=p.id returning * into p;
 end if;
 select * into p from public.payments where id=p.id;
 return private.payment_snapshot(p);
end; $$;
commit;
