begin;

create function public.reserve_order_payment(
  p_client_id uuid, p_request_id uuid, p_dispatch_kind text, p_service_id uuid,
  p_appointment_id uuid, p_quote_version bigint, p_method text, p_card_id uuid,
  p_expected_amount numeric default null, p_creation_enabled boolean default false
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  existing public.payments%rowtype;
  card public.saved_cards%rowtype;
  appointment public.appointments%rowtype;
  search public.service_searches%rowtype;
  service_uuid uuid := p_service_id;
  price numeric(10,2);
  attempt_uuid uuid := gen_random_uuid();
  token_uuid uuid := gen_random_uuid();
begin
  if p_client_id is null or p_request_id is null or p_dispatch_kind not in ('MOBILE_SEQUENTIAL','PITTEC_AUTO_SEQUENTIAL')
    or p_method not in ('PIX','SAVED_CARD') or p_dispatch_kind is null or p_method is null then
    raise exception using errcode='22023', message='INVALID_INPUT';
  end if;
  if not exists(select 1 from public.account_roles where user_id=p_client_id and account_type='CLIENT') then
    raise exception using errcode='42501', message='CLIENT_REQUIRED';
  end if;
  perform pg_advisory_xact_lock(hashtextextended(p_request_id::text,2200));
  if exists(select 1 from public.payments where request_id=p_request_id and client_id<>p_client_id)
    or exists(select 1 from public.service_searches where id=p_request_id and client_id<>p_client_id) then
    raise exception using errcode='42501', message='INVALID_PAYMENT_CONTEXT';
  end if;
  select * into existing from public.payments where request_id=p_request_id and (
    sync_state <> 'CURRENT' or status in ('PENDING','APPROVED','REFUND_PENDING','REFUND_FAILED')
  ) order by is_compensation,created_at desc limit 1 for update;
  if existing.id is not null then
    if existing.dispatch_kind<>p_dispatch_kind
      or (existing.provider_api='ORDERS' and (
        (p_dispatch_kind='MOBILE_SEQUENTIAL' and existing.service_id is distinct from p_service_id)
        or (p_dispatch_kind='PITTEC_AUTO_SEQUENTIAL' and (existing.appointment_id is distinct from p_appointment_id
          or existing.quote_version is distinct from p_quote_version)))) then
      raise exception using errcode='22023', message='INVALID_PAYMENT_CONTEXT';
    end if;
    return jsonb_build_object('created',false,'payment',to_jsonb(existing));
  end if;
  if not p_creation_enabled then raise exception using errcode='55000', message='SERVICE_UNAVAILABLE'; end if;
  if p_dispatch_kind='PITTEC_AUTO_SEQUENTIAL' then
    select * into search from public.service_searches where id=p_request_id for update;
    select * into appointment from public.appointments where id=p_appointment_id for update;
    if search.client_id is distinct from p_client_id or search.status<>'AWAITING_PAYMENT'
      or appointment.client_id is distinct from p_client_id or appointment.dispatch_search_id is distinct from p_request_id
      or appointment.status<>'AWAITING_PAYMENT' or appointment.quote_version is distinct from p_quote_version
      or appointment.client_confirmed_quote_version is distinct from p_quote_version then
      raise exception using errcode='22023', message='INVALID_PAYMENT_CONTEXT';
    end if;
    service_uuid := appointment.service_id;
    price := appointment.quoted_price;
  else
    if exists(select 1 from public.service_searches where id=p_request_id) then
      raise exception using errcode='22023', message='INVALID_PAYMENT_CONTEXT';
    end if;
    select min(offer.price) into price from public.services s
    join public.service_categories category on category.id=s.category_id and category.active
    join public.provider_services offer on offer.service_id=s.id and offer.active
    join public.providers provider on provider.id=offer.provider_id and provider.active and provider.available
    where s.id=service_uuid and s.active and s.dispatch_kind='MOBILE_SEQUENTIAL';
  end if;
  if price is null or price<=0 then
    raise exception using errcode='22023', message='SERVICE_NOT_FOUND';
  end if;
  if p_expected_amount is not null and p_expected_amount<>price then
    raise exception using errcode='22023', message='PRICE_CHANGED';
  end if;
  if p_method='SAVED_CARD' then
    select * into card from public.saved_cards where id=p_card_id and client_id=p_client_id;
    if card.id is null then raise exception using errcode='P0002', message='CARD_NOT_FOUND'; end if;
  end if;
  insert into public.payments(id,request_id,client_id,dispatch_kind,service_id,appointment_id,quote_version,
    method,saved_card_id,card_brand,card_last_four_digits,amount,currency,provider_api,external_reference,
    idempotency_key,status,sync_state,next_action_at,work_action,lease_token,lease_until)
  values(attempt_uuid,p_request_id,p_client_id,p_dispatch_kind,service_uuid,p_appointment_id,p_quote_version,
    p_method,card.id,card.brand,card.last_four_digits,price,'BRL','ORDERS',attempt_uuid::text,
    attempt_uuid::text,'PENDING','CREATING',now()+interval '90 seconds','RECONCILE',token_uuid,now()+interval '90 seconds')
  returning * into existing;
  return jsonb_build_object('created',true,'payment',to_jsonb(existing));
end;
$$;
revoke all on function public.reserve_order_payment(uuid,uuid,text,uuid,uuid,bigint,text,uuid,numeric,boolean) from public,anon,authenticated;
grant execute on function public.reserve_order_payment(uuid,uuid,text,uuid,uuid,bigint,text,uuid,numeric,boolean) to service_role;

commit;
