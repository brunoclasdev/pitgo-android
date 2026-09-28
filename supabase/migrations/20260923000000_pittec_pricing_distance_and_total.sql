begin;
alter table public.pittec_pricing_rules
 add column if not exists distance_rate_per_km numeric(10,2) check (distance_rate_per_km >= 0),
 add column if not exists payment_test_mode boolean not null default true,
 add column if not exists test_payment_amount numeric(10,2) not null default 1.00 check (test_payment_amount > 0);
update public.pittec_pricing_rules
set distance_rate_per_km = 2.00,
    payment_test_mode = true,
    test_payment_amount = 1.00
where active;
alter table public.appointments
 add column if not exists service_amount numeric(10,2),
 add column if not exists distance_meters numeric(12,2),
 add column if not exists distance_rate_per_km numeric(10,2),
 add column if not exists distance_charge_amount numeric(10,2),
 add column if not exists payment_amount numeric(10,2),
 add column if not exists payment_test_mode boolean,
 add column if not exists service_started_at timestamptz,
 add column if not exists client_start_acknowledged_at timestamptz;
alter table public.appointments add constraint appointments_distance_quote_consistent check (
 service_amount is null or (service_amount > 0 and distance_meters >= 0 and distance_rate_per_km >= 0
 and distance_charge_amount >= 0 and provider_amount = service_amount + distance_charge_amount
 and customer_total_amount = provider_amount + markup_amount + customer_fee_amount
 and payment_amount > 0));
-- Existing paid quotes are immutable. New test orders use R$ 1.00 while the quote keeps its real total.


create or replace function private.pittec_calculate_pricing(p_provider_amount numeric, p_distance_meters numeric)
returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare rule_row public.pittec_pricing_rules%rowtype; base numeric(10,2); markup numeric(10,2); fee numeric(10,2); distance_charge numeric(10,2); payment_amount numeric(10,2);
begin
  base := round(p_provider_amount::numeric,2);
  select * into rule_row from public.pittec_pricing_rules
  where active and effective_from <= now() and (effective_until is null or effective_until > now())
  order by effective_from desc, version desc limit 1;
  if rule_row.id is null or rule_row.distance_rate_per_km is null or p_provider_amount is null or p_provider_amount <= 0 or p_distance_meters is null or p_distance_meters < 0 then raise exception using errcode='22023', message='PITTEC_PRICING_UNAVAILABLE'; end if;
  distance_charge := round(p_distance_meters / 1000 * rule_row.distance_rate_per_km, 2);
  base := base + distance_charge;
  markup := round(base * rule_row.markup_percent / 100, 2);
  fee := round(base * rule_row.fee_percent / 100 + rule_row.fee_fixed_amount, 2);
  payment_amount := case when rule_row.payment_test_mode then rule_row.test_payment_amount else round(base + markup + fee, 2) end;
  return jsonb_build_object('rule_id',rule_row.id,'rule_version',rule_row.version,
    'service_amount',round(p_provider_amount,2),'distance_meters',p_distance_meters,'distance_rate_per_km',rule_row.distance_rate_per_km,'distance_charge_amount',distance_charge,'provider_amount',base,'markup_amount',markup,'customer_fee_amount',fee,
    'customer_total_amount',round(base + markup + fee,2),'payment_amount',payment_amount,'payment_test_mode',rule_row.payment_test_mode,'currency',rule_row.currency,
    'quote_expires_at',now() + interval '15 minutes');
end; $$;

create or replace function public.respond_to_mobile_dispatch(p_appointment_id uuid,p_accept boolean,p_expected_revision bigint)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare a public.appointments%rowtype; s public.service_searches%rowtype; owned uuid; kind text; p jsonb; service_price numeric; distance numeric;
begin
  if not private.dispatch_is_provider() then raise exception using errcode='42501',message='PROVIDER_REQUIRED'; end if;
  select id into owned from public.providers where account_id=auth.uid();
  select * into a from public.appointments where id=p_appointment_id and provider_id=owned for update;
  if a.id is null then raise exception using errcode='P0002',message='REQUEST_UNAVAILABLE'; end if;
  select * into s from public.service_searches where id=a.dispatch_search_id for update;
  select dispatch_kind into kind from public.services where id=a.service_id;
  if a.status='PENDING' and a.response_deadline<=now() then perform private.expire_mobile_dispatch_appointment(a.id); return jsonb_build_object('outcome','OFFER_EXPIRED'); end if;
  if a.revision<>p_expected_revision then return jsonb_build_object('outcome','REVISION_CONFLICT','snapshot',case when kind='PITTEC_AUTO_SEQUENTIAL' then private.pittec_dispatch_snapshot(s.id) else private.dispatch_snapshot(s.id) end); end if;
  if a.status<>'PENDING' or s.current_appointment_id is distinct from a.id then return jsonb_build_object('outcome','OFFER_UNAVAILABLE'); end if;
  if p_accept and kind='PITTEC_AUTO_SEQUENTIAL' then
    select price into service_price from public.provider_services where id=a.provider_service_id and active;
    select distance_meters into distance from private.dispatch_candidates where id=a.dispatch_candidate_id;
    p := private.pittec_calculate_pricing(service_price,distance);
    update public.appointments set service_amount=(p->>'service_amount')::numeric,distance_meters=(p->>'distance_meters')::numeric,distance_rate_per_km=(p->>'distance_rate_per_km')::numeric,distance_charge_amount=(p->>'distance_charge_amount')::numeric,payment_amount=(p->>'payment_amount')::numeric,payment_test_mode=(p->>'payment_test_mode')::boolean,quoted_price=(p->>'provider_amount')::numeric,provider_amount=(p->>'provider_amount')::numeric,markup_amount=(p->>'markup_amount')::numeric,customer_fee_amount=(p->>'customer_fee_amount')::numeric,customer_total_amount=(p->>'customer_total_amount')::numeric,pricing_currency=p->>'currency',pricing_rule_id=(p->>'rule_id')::uuid,pricing_rule_version=(p->>'rule_version')::bigint,quote_expires_at=(p->>'quote_expires_at')::timestamptz,status='AWAITING_CLIENT_CONFIRMATION',accepted_at=now(),client_confirmation_deadline=(p->>'quote_expires_at')::timestamptz,revision=revision+1,updated_at=now() where id=a.id;
    update private.dispatch_candidates set status='ACCEPTED',decided_at=now() where id=a.dispatch_candidate_id;
    update public.service_searches set status='AWAITING_CLIENT_CONFIRMATION',next_dispatch_at=null,revision=revision+1,updated_at=now() where id=s.id;
    return private.pittec_dispatch_snapshot(s.id);
  end if;
  if p_accept then update public.appointments set status='ACCEPTED',accepted_at=now(),revision=revision+1,updated_at=now() where id=a.id; update public.service_searches set status='ACCEPTED',revision=revision+1,updated_at=now() where id=s.id; update private.dispatch_candidates set status='ACCEPTED',decided_at=now() where id=a.dispatch_candidate_id; return case when kind='PITTEC_AUTO_SEQUENTIAL' then private.pittec_dispatch_snapshot(s.id) else private.dispatch_snapshot(s.id) end; end if;
  update public.appointments set status='PROVIDER_REJECTED',ended_at=now(),revision=revision+1,updated_at=now() where id=a.id; perform private.apply_pending_provider_mode(a.provider_id); update private.dispatch_candidates set status='PROVIDER_REJECTED',decided_at=now() where id=a.dispatch_candidate_id; update public.service_searches set status='SEARCHING',current_appointment_id=null,current_candidate_id=null,next_dispatch_at=case when kind='PITTEC_AUTO_SEQUENTIAL' then now() else null end,revision=revision+1,updated_at=now() where id=s.id; if kind='PITTEC_AUTO_SEQUENTIAL' then perform private.advance_pittec_dispatch(s.id); return jsonb_build_object('outcome','DECLINED'); end if; perform private.advance_mobile_dispatch(s.id); return jsonb_build_object('outcome','NONE');
end; $$;

create or replace function private.pittec_dispatch_snapshot(p_search_id uuid)
returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare s public.service_searches%rowtype; a public.appointments%rowtype; c private.dispatch_candidates%rowtype;
  provider_account uuid; provider_name text; service_name text; is_client boolean; is_provider boolean; pay text; q jsonb;
begin
  select * into s from public.service_searches where id=p_search_id;
  if s.id is null or not private.is_pittec_service(s.service_id) then return null; end if;
  if s.current_candidate_id is not null then select * into c from private.dispatch_candidates where id=s.current_candidate_id; end if;
  if s.current_appointment_id is not null then
    select * into a from public.appointments where id=s.current_appointment_id;
    select account_id,name into provider_account,provider_name from public.providers where id=a.provider_id;
  end if;
  is_client := auth.uid()=s.client_id; is_provider := auth.uid() is not null and auth.uid()=provider_account;
  if not is_client and not is_provider then raise exception using errcode='42501',message='PARTICIPANT_REQUIRED'; end if;
  select name into service_name from public.services where id=s.service_id;
  select status into pay from public.payments where request_id=s.id order by created_at desc limit 1;
  q := case when a.id is null or (a.status='PENDING' and not is_provider) then null
    when is_provider then jsonb_build_object('service_amount',a.service_amount,'distance_charge_amount',a.distance_charge_amount,'distance_meters',a.distance_meters,'provider_amount',coalesce(a.provider_amount,a.quoted_price),'currency',coalesce(a.pricing_currency,'BRL'),'version',a.quote_version,'expires_at',coalesce(a.quote_expires_at,a.client_confirmation_deadline))
    else jsonb_build_object('customer_total_amount',a.customer_total_amount,'currency',coalesce(a.pricing_currency,'BRL'),'version',a.quote_version,'expires_at',coalesce(a.quote_expires_at,a.client_confirmation_deadline)) end;
  return jsonb_strip_nulls(jsonb_build_object('search_id',s.id,'service_id',s.service_id,'service_name',service_name,
    'dispatch_kind','PITTEC_AUTO_SEQUENTIAL','status',s.status,'radius_meters',s.current_radius_m,'revision',s.revision,'server_now',now(),
    'next_dispatch_at',s.next_dispatch_at,'last_event',s.last_event,'last_event_at',s.last_event_at,
    'tracking_authorized',s.status in ('ACCEPTED','IN_PROGRESS'),'payment',jsonb_build_object('status',coalesce(pay,'NOT_STARTED')),
    'allowed_actions',case when is_client and s.status in ('SEARCHING','AWAITING_PROVIDER') then jsonb_build_array('CANCEL')
      when is_client and s.status='AWAITING_CLIENT_CONFIRMATION' then jsonb_build_array('CONFIRM_QUOTE','DECLINE_QUOTE','CANCEL')
      when is_client and s.status='AWAITING_PAYMENT' then jsonb_build_array('OPEN_PAYMENT','CANCEL')
      when is_client and s.status in ('ACCEPTED','IN_PROGRESS') then jsonb_build_array('OPEN_TRACKING','CANCEL')
      when is_provider and a.status='PENDING' then jsonb_build_array('ACCEPT','DECLINE')
      when is_provider and a.status in ('AWAITING_CLIENT_CONFIRMATION','AWAITING_PAYMENT') then jsonb_build_array('CANCEL')
      when is_provider and a.status='ACCEPTED' then jsonb_build_array('OPEN_TRACKING',case when a.arrived_at is null then 'ARRIVE' else 'START' end,'CANCEL')
      when is_provider and a.status='IN_PROGRESS' then jsonb_build_array('OPEN_TRACKING','COMPLETE','CANCEL') else '[]'::jsonb end,
    'offer',case when is_provider and a.status='PENDING' then jsonb_build_object('approximate_distance_meters',round(c.distance_meters/500)*500,'region',c.locality,'quoted_price',a.quoted_price,'currency','BRL','quote_version',a.quote_version) end,
    'provider',case when s.status in ('AWAITING_CLIENT_CONFIRMATION','AWAITING_PAYMENT','ACCEPTED','IN_PROGRESS','COMPLETED','CANCELLED') and provider_name is not null then jsonb_build_object('provider_id',a.provider_id,'display_name',provider_name) end,
    'appointment',case when a.id is null then null else jsonb_build_object('appointment_id',a.id,'status',a.status,'response_deadline',a.response_deadline,'revision',a.revision,'accepted_at',a.accepted_at,'arrived_at',a.arrived_at,'service_started_at',a.service_started_at,'client_start_acknowledged_at',a.client_start_acknowledged_at,'cancelled_by',a.cancelled_by,'quote',q) end));
end; $$;


create or replace function public.reserve_order_payment(
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
    if appointment.quote_expires_at is null or appointment.quote_expires_at <= now() then
      raise exception using errcode='22023',message='QUOTE_EXPIRED'; end if;
    price := coalesce(appointment.payment_amount, appointment.customer_total_amount);
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
  -- Legacy mobile validation mode; PitTec uses its frozen customer total.
  -- Keep the appointment/service context checks above, but never trust a client amount.
  if p_dispatch_kind='MOBILE_SEQUENTIAL' then price := 1.00; end if;
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



create or replace function public.arrive_pittec_appointment(p_request_id uuid,p_appointment_id uuid,p_expected_revision bigint)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare a public.appointments%rowtype; s public.service_searches%rowtype; owned_provider_uuid uuid;
begin
  if not private.dispatch_is_provider() then raise exception using errcode='42501',message='PROVIDER_REQUIRED'; end if;
  select id into owned_provider_uuid from public.providers where account_id=auth.uid();
  select * into a from public.appointments where id=p_appointment_id and provider_id=owned_provider_uuid for update;
  select * into s from public.service_searches where id=a.dispatch_search_id for update;
  if a.id is null or s.id is null then raise exception using errcode='P0002',message='APPOINTMENT_UNAVAILABLE'; end if;
  if a.arrived_at is not null and a.status in ('ACCEPTED','IN_PROGRESS') then return jsonb_build_object('outcome','ARRIVED','snapshot',private.pittec_dispatch_snapshot(s.id)); end if;
  if a.revision<>p_expected_revision then return jsonb_build_object('outcome','REVISION_CONFLICT','snapshot',private.pittec_dispatch_snapshot(s.id)); end if;
  if a.status='IN_PROGRESS' then return jsonb_build_object('outcome','ARRIVED','snapshot',private.pittec_dispatch_snapshot(s.id)); end if;
  if a.status<>'ACCEPTED' or s.status<>'ACCEPTED' then return jsonb_build_object('outcome','INVALID_TRANSITION','snapshot',private.pittec_dispatch_snapshot(s.id)); end if;
  if not exists(select 1 from public.payments p where p.request_id=s.id and p.appointment_id=a.id and p.amount=coalesce(a.payment_amount,a.customer_total_amount) and p.currency='BRL' and p.quote_version=a.quote_version and private.payment_can_release(p,a.service_id)) then raise exception using errcode='P0001',message='PAYMENT_REQUIRED'; end if;
  update public.appointments set arrived_at=now(),revision=revision+1,updated_at=now() where id=a.id;
  update public.service_searches set revision=revision+1,updated_at=now() where id=s.id;
  return jsonb_build_object('outcome','ARRIVED','snapshot',private.pittec_dispatch_snapshot(s.id));
end; $$;

create or replace function public.start_mobile_appointment(
  p_appointment_id uuid,
  p_expected_revision bigint
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  appointment_row public.appointments%rowtype;
  owned_provider_uuid uuid;
begin
  if not private.dispatch_is_provider() then
    raise exception using errcode = '42501', message = 'PROVIDER_REQUIRED';
  end if;
  select provider.id into owned_provider_uuid
  from public.providers as provider where provider.account_id = auth.uid();
  select appointment.* into appointment_row
  from public.appointments as appointment
  where appointment.id = p_appointment_id and appointment.provider_id = owned_provider_uuid
  for update;
  if appointment_row.id is null then
    raise exception using errcode = 'P0002', message = 'APPOINTMENT_UNAVAILABLE';
  end if;
  if private.is_pittec_service(appointment_row.service_id) then
    if appointment_row.status = 'IN_PROGRESS' then return private.pittec_dispatch_snapshot(appointment_row.dispatch_search_id); end if;
    if appointment_row.arrived_at is null then raise exception using errcode='22023',message='ARRIVAL_REQUIRED'; end if;
    if not exists(select 1 from public.payments p where p.request_id=appointment_row.dispatch_search_id
      and p.appointment_id=appointment_row.id and p.quote_version=appointment_row.quote_version
      and p.amount=coalesce(appointment_row.payment_amount,appointment_row.customer_total_amount) and p.currency='BRL'
      and private.payment_can_release(p,appointment_row.service_id)) then
      raise exception using errcode='22023',message='PAYMENT_REQUIRED'; end if;
    if appointment_row.revision = p_expected_revision and appointment_row.status = 'ACCEPTED' then
      update public.appointments
      set status = 'IN_PROGRESS', service_started_at = now(), revision = revision + 1, updated_at = now()
      where id = appointment_row.id;
      update public.service_searches
      set status = 'IN_PROGRESS', revision = revision + 1, updated_at = now()
      where id = appointment_row.dispatch_search_id;
    end if;
    return private.pittec_dispatch_snapshot(appointment_row.dispatch_search_id);
  end if;
  if appointment_row.revision = p_expected_revision and appointment_row.status = 'ACCEPTED' then
    update public.appointments
    set status = 'IN_PROGRESS', revision = revision + 1, updated_at = now()
    where id = appointment_row.id;
    update public.service_searches
    set status = 'IN_PROGRESS', revision = revision + 1, updated_at = now()
    where id = appointment_row.dispatch_search_id;
  end if;
  return private.dispatch_snapshot(appointment_row.dispatch_search_id);
end;
$$;


create or replace function public.acknowledge_pittec_start(p_appointment_id uuid,p_expected_revision bigint)
returns jsonb language plpgsql security definer set search_path='' as $$
declare a public.appointments%rowtype;
begin
 select * into a from public.appointments where id=p_appointment_id and client_id=auth.uid() for update;
 if a.id is null then raise exception using errcode='42501',message='PARTICIPANT_REQUIRED'; end if;
 if a.status <> 'IN_PROGRESS' or not private.is_pittec_service(a.service_id) then raise exception using errcode='22023',message='INVALID_TRANSITION'; end if;
 if a.client_start_acknowledged_at is null and a.revision=p_expected_revision then
  update public.appointments set client_start_acknowledged_at=now(),revision=revision+1,updated_at=now() where id=a.id;
  update public.service_searches set revision=revision+1,updated_at=now() where id=a.dispatch_search_id;
 end if;
 return private.pittec_dispatch_snapshot(a.dispatch_search_id);
end; $$;
revoke all on function public.acknowledge_pittec_start(uuid,bigint) from public,anon;
grant execute on function public.acknowledge_pittec_start(uuid,bigint) to authenticated;

-- The payment order amount is the frozen test amount (R$ 1.00 today),
-- while customer_total_amount remains the real commercial quote.
create or replace function private.reconcile_pittec_payment_change()
returns trigger language plpgsql security definer set search_path = '' as $$
declare search_row public.service_searches%rowtype; appointment_row public.appointments%rowtype;
begin
  if new.dispatch_kind <> 'PITTEC_AUTO_SEQUENTIAL' or new.status <> 'APPROVED' then return new; end if;
  select * into search_row from public.service_searches where id = new.request_id for update;
  select * into appointment_row from public.appointments where id = new.appointment_id and dispatch_search_id = new.request_id for update;
  if appointment_row.id is null
    or search_row.id is null
    or new.client_id <> search_row.client_id
    or appointment_row.client_id <> new.client_id
    or new.amount <> coalesce(appointment_row.payment_amount, appointment_row.customer_total_amount)
    or new.currency <> 'BRL'
    or not private.payment_can_release(new, appointment_row.service_id)
    or new.quote_version is distinct from appointment_row.quote_version
    or appointment_row.client_confirmed_quote_version is distinct from appointment_row.quote_version
    or appointment_row.quote_expires_at <= now()
    or appointment_row.status <> 'AWAITING_PAYMENT'
    or search_row.status <> 'AWAITING_PAYMENT' then
    update public.payments set status = 'REFUND_PENDING', updated_at = now() where id = new.id and status = 'APPROVED';
    return new;
  end if;
  update public.appointments set status = 'ACCEPTED', revision = revision + 1, updated_at = now()
  where id = appointment_row.id and status = 'AWAITING_PAYMENT';
  update public.service_searches set status = 'ACCEPTED', revision = revision + 1, updated_at = now()
  where id = search_row.id and status = 'AWAITING_PAYMENT';
  return new;
end; $$;
revoke all on function private.reconcile_pittec_payment_change() from public, anon, authenticated;

-- Financial columns must only be read through participant-specific projections.
revoke select on public.appointments from authenticated;
create or replace function public.get_pittec_appointment_snapshot(p_appointment_id uuid)
returns jsonb language plpgsql stable security definer set search_path='' as $$
declare search_id uuid;
begin
 select a.dispatch_search_id into search_id from public.appointments a
 join public.providers p on p.id=a.provider_id
 where a.id=p_appointment_id and (a.client_id=auth.uid() or p.account_id=auth.uid());
 if search_id is null then raise exception using errcode='42501',message='PARTICIPANT_REQUIRED'; end if;
 return private.pittec_dispatch_snapshot(search_id);
end; $$;
revoke all on function public.get_pittec_appointment_snapshot(uuid) from public,anon;
grant execute on function public.get_pittec_appointment_snapshot(uuid) to authenticated;
revoke all on function private.pittec_calculate_pricing(numeric,numeric) from public,anon,authenticated;
notify pgrst, 'reload schema';
commit;
