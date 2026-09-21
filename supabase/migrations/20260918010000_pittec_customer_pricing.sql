begin;

-- PitTec customer pricing is calculated and frozen server-side.  The legacy
-- quoted_price column remains the provider amount for compatibility with the
-- existing MOBILE_SEQUENTIAL/Orders schema; it is never used as the customer
-- charge for PITTEC.
create table if not exists public.pittec_pricing_rules (
  id uuid primary key default gen_random_uuid(),
  version bigint generated always as identity unique,
  markup_percent numeric(7,4) not null default 40.0000,
  fee_percent numeric(7,4) not null default 0,
  fee_fixed_amount numeric(10,2) not null default 0,
  currency text not null default 'BRL',
  effective_from timestamptz not null default now(),
  effective_until timestamptz,
  active boolean not null default true,
  created_at timestamptz not null default now(),
  constraint pittec_pricing_rules_positive check (markup_percent >= 0 and fee_percent >= 0 and fee_fixed_amount >= 0),
  constraint pittec_pricing_rules_currency check (currency = 'BRL'),
  constraint pittec_pricing_rules_window check (effective_until is null or effective_until > effective_from)
);

create unique index if not exists pittec_pricing_rules_one_active
  on public.pittec_pricing_rules(active) where active;
insert into public.pittec_pricing_rules(markup_percent,fee_percent,fee_fixed_amount)
select 40,0,0 where not exists (select 1 from public.pittec_pricing_rules);

alter table public.appointments
  add column if not exists pricing_rule_id uuid references public.pittec_pricing_rules(id),
  add column if not exists provider_amount numeric(10,2),
  add column if not exists markup_amount numeric(10,2),
  add column if not exists customer_fee_amount numeric(10,2),
  add column if not exists customer_total_amount numeric(10,2),
  add column if not exists pricing_currency text,
  add column if not exists pricing_rule_version bigint,
  add column if not exists quote_expires_at timestamptz;

create index if not exists appointments_pittec_quote_expiry
  on public.appointments(quote_expires_at) where status = 'AWAITING_CLIENT_CONFIRMATION';

alter table public.pittec_pricing_rules enable row level security;
revoke all on public.pittec_pricing_rules from anon, authenticated;

create or replace function private.pittec_calculate_pricing(p_provider_amount numeric)
returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare rule_row public.pittec_pricing_rules%rowtype; base numeric(10,2); markup numeric(10,2); fee numeric(10,2);
begin
  base := round(p_provider_amount::numeric,2);
  select * into rule_row from public.pittec_pricing_rules
  where active and effective_from <= now() and (effective_until is null or effective_until > now())
  order by effective_from desc, version desc limit 1;
  if rule_row.id is null then raise exception using errcode='22023', message='PITTEC_PRICING_UNAVAILABLE'; end if;
  markup := round(base * rule_row.markup_percent / 100, 2);
  fee := round(base * rule_row.fee_percent / 100 + rule_row.fee_fixed_amount, 2);
  return jsonb_build_object('rule_id',rule_row.id,'rule_version',rule_row.version,
    'provider_amount',base,'markup_amount',markup,'customer_fee_amount',fee,
    'customer_total_amount',round(base + markup + fee,2),'currency',rule_row.currency,
    'quote_expires_at',now() + interval '15 minutes');
end; $$;

-- Role-aware projection.  The client receives only markup/fees/total and the
-- provider receives only the frozen base amount.
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
  is_client := auth.uid()=s.client_id; is_provider := auth.uid() is not distinct from provider_account;
  if not is_client and not is_provider then raise exception using errcode='42501',message='PARTICIPANT_REQUIRED'; end if;
  select name into service_name from public.services where id=s.service_id;
  select status into pay from public.payments where request_id=s.id order by created_at desc limit 1;
  q := case when a.id is null or (a.status='PENDING' and not is_provider) then null
    when is_provider then jsonb_build_object('provider_amount',coalesce(a.provider_amount,a.quoted_price),'currency',coalesce(a.pricing_currency,'BRL'),'version',a.quote_version,'expires_at',coalesce(a.quote_expires_at,a.client_confirmation_deadline))
    else jsonb_build_object('markup_amount',a.markup_amount,'customer_fee_amount',a.customer_fee_amount,'customer_total_amount',a.customer_total_amount,'currency',coalesce(a.pricing_currency,'BRL'),'version',a.quote_version,'expires_at',coalesce(a.quote_expires_at,a.client_confirmation_deadline)) end;
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
      when is_provider and a.status='ACCEPTED' then jsonb_build_array('OPEN_TRACKING','ARRIVE','CANCEL')
      when is_provider and a.status='IN_PROGRESS' then jsonb_build_array('OPEN_TRACKING','COMPLETE','CANCEL') else '[]'::jsonb end,
    'offer',case when is_provider and a.status='PENDING' then jsonb_build_object('approximate_distance_meters',round(c.distance_meters/500)*500,'region',c.locality,'quoted_price',a.quoted_price,'currency','BRL','quote_version',a.quote_version) end,
    'provider',case when s.status in ('AWAITING_CLIENT_CONFIRMATION','AWAITING_PAYMENT','ACCEPTED','IN_PROGRESS','COMPLETED','CANCELLED') and provider_name is not null then jsonb_build_object('provider_id',a.provider_id,'display_name',provider_name) end,
    'appointment',case when a.id is null then null else jsonb_build_object('appointment_id',a.id,'status',a.status,'response_deadline',a.response_deadline,'revision',a.revision,'accepted_at',a.accepted_at,'arrived_at',a.arrived_at,'cancelled_by',a.cancelled_by,'quote',q) end));
end; $$;

-- Provider acceptance now freezes the pricing tuple and gives both parties a
-- fifteen minute confirmation window. This runs after the prior dispatch guard.
create or replace function public.respond_to_mobile_dispatch(p_appointment_id uuid,p_accept boolean,p_expected_revision bigint)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare a public.appointments%rowtype; s public.service_searches%rowtype; owned uuid; kind text; p jsonb;
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
    p := private.pittec_calculate_pricing(a.quoted_price);
    update public.appointments set provider_amount=(p->>'provider_amount')::numeric,markup_amount=(p->>'markup_amount')::numeric,customer_fee_amount=(p->>'customer_fee_amount')::numeric,customer_total_amount=(p->>'customer_total_amount')::numeric,pricing_currency=p->>'currency',pricing_rule_id=(p->>'rule_id')::uuid,pricing_rule_version=(p->>'rule_version')::bigint,quote_expires_at=(p->>'quote_expires_at')::timestamptz,status='AWAITING_CLIENT_CONFIRMATION',accepted_at=now(),client_confirmation_deadline=(p->>'quote_expires_at')::timestamptz,revision=revision+1,updated_at=now() where id=a.id;
    update private.dispatch_candidates set status='ACCEPTED',decided_at=now() where id=a.dispatch_candidate_id;
    update public.service_searches set status='AWAITING_CLIENT_CONFIRMATION',next_dispatch_at=null,revision=revision+1,updated_at=now() where id=s.id;
    return private.pittec_dispatch_snapshot(s.id);
  end if;
  if p_accept then update public.appointments set status='ACCEPTED',accepted_at=now(),revision=revision+1,updated_at=now() where id=a.id; update public.service_searches set status='ACCEPTED',revision=revision+1,updated_at=now() where id=s.id; update private.dispatch_candidates set status='ACCEPTED',decided_at=now() where id=a.dispatch_candidate_id; return case when kind='PITTEC_AUTO_SEQUENTIAL' then private.pittec_dispatch_snapshot(s.id) else private.dispatch_snapshot(s.id) end; end if;
  update public.appointments set status='PROVIDER_REJECTED',ended_at=now(),revision=revision+1,updated_at=now() where id=a.id; perform private.apply_pending_provider_mode(a.provider_id); update private.dispatch_candidates set status='PROVIDER_REJECTED',decided_at=now() where id=a.dispatch_candidate_id; update public.service_searches set status='SEARCHING',current_appointment_id=null,current_candidate_id=null,next_dispatch_at=case when kind='PITTEC_AUTO_SEQUENTIAL' then now() else null end,revision=revision+1,updated_at=now() where id=s.id; if kind='PITTEC_AUTO_SEQUENTIAL' then perform private.advance_pittec_dispatch(s.id); return jsonb_build_object('outcome','DECLINED'); end if; perform private.advance_mobile_dispatch(s.id); return jsonb_build_object('outcome','NONE');
end; $$;

create or replace function public.reserve_order_payment(p_client_id uuid,p_request_id uuid,p_dispatch_kind text,p_service_id uuid,p_appointment_id uuid,p_quote_version bigint,p_method text,p_card_id uuid,p_expected_amount numeric default null,p_creation_enabled boolean default false)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare existing public.payments%rowtype; card public.saved_cards%rowtype; a public.appointments%rowtype; s public.service_searches%rowtype; price numeric(10,2); attempt uuid:=gen_random_uuid(); token uuid:=gen_random_uuid(); service_uuid uuid:=p_service_id;
begin
  if not p_creation_enabled then raise exception using errcode='55000',message='SERVICE_UNAVAILABLE'; end if;
  perform pg_advisory_xact_lock(hashtextextended(p_request_id::text,2200));
  select * into existing from public.payments where request_id=p_request_id and (sync_state<>'CURRENT' or status in ('PENDING','APPROVED','REFUND_PENDING','REFUND_FAILED')) order by is_compensation,created_at desc limit 1 for update;
  if existing.id is not null then return jsonb_build_object('created',false,'payment',to_jsonb(existing)); end if;
  if p_dispatch_kind='PITTEC_AUTO_SEQUENTIAL' then
    select * into s from public.service_searches where id=p_request_id for update; select * into a from public.appointments where id=p_appointment_id for update;
    if s.client_id is distinct from p_client_id or s.status<>'AWAITING_PAYMENT' or a.client_id is distinct from p_client_id or a.status<>'AWAITING_PAYMENT' or a.quote_version is distinct from p_quote_version or a.client_confirmed_quote_version is distinct from p_quote_version or a.quote_expires_at<=now() then raise exception using errcode='22023',message='QUOTE_EXPIRED'; end if;
    service_uuid:=a.service_id; price:=a.customer_total_amount;
  else
    select min(offer.price) into price from public.provider_services offer where offer.service_id=service_uuid and offer.active;
  end if;
  if price is null or price<=0 or (p_expected_amount is not null and p_expected_amount<>price) then raise exception using errcode='22023',message='PRICE_CHANGED'; end if;
  if p_method='SAVED_CARD' then select * into card from public.saved_cards where id=p_card_id and client_id=p_client_id; if card.id is null then raise exception using errcode='P0002',message='CARD_NOT_FOUND'; end if; end if;
  insert into public.payments(id,request_id,client_id,dispatch_kind,service_id,appointment_id,quote_version,method,saved_card_id,card_brand,card_last_four_digits,amount,currency,provider_api,external_reference,idempotency_key,status,sync_state,next_action_at,work_action,lease_token,lease_until) values(attempt,p_request_id,p_client_id,p_dispatch_kind,service_uuid,p_appointment_id,p_quote_version,p_method,card.id,card.brand,card.last_four_digits,price,'BRL','ORDERS',attempt::text,attempt::text,'PENDING','CREATING',now()+interval '90 seconds','RECONCILE',token,now()+interval '90 seconds') returning * into existing;
  return jsonb_build_object('created',true,'payment',to_jsonb(existing));
end; $$;

revoke all on function public.reserve_order_payment(uuid,uuid,text,uuid,uuid,bigint,text,uuid,numeric,boolean) from public,anon,authenticated;
grant execute on function public.reserve_order_payment(uuid,uuid,text,uuid,uuid,bigint,text,uuid,numeric,boolean) to service_role;

-- Approval and arrival must validate the frozen customer total, never the
-- provider base amount.
create or replace function private.reconcile_pittec_payment_change()
returns trigger language plpgsql security definer set search_path = '' as $$
declare a public.appointments%rowtype; s public.service_searches%rowtype;
begin
  if new.dispatch_kind<>'PITTEC_AUTO_SEQUENTIAL' or new.status<>'APPROVED' then return new; end if;
  select * into s from public.service_searches where id=new.request_id for update;
  select * into a from public.appointments where id=new.appointment_id and dispatch_search_id=s.id for update;
  if a.id is null or new.client_id<>s.client_id or new.amount<>a.customer_total_amount or new.currency<>'BRL'
    or new.quote_version is distinct from a.quote_version or a.client_confirmed_quote_version is distinct from a.quote_version
    or a.quote_expires_at<=now() or a.status<>'AWAITING_PAYMENT' or s.status<>'AWAITING_PAYMENT'
    or not private.payment_can_release(new,a.service_id) then
    update public.payments set status='REFUND_PENDING',updated_at=now() where id=new.id and status='APPROVED'; return new;
  end if;
  update public.appointments set status='ACCEPTED',revision=revision+1,updated_at=now() where id=a.id;
  update public.service_searches set status='ACCEPTED',revision=revision+1,updated_at=now() where id=s.id;
  return new;
end; $$;

create or replace function public.arrive_pittec_appointment(p_request_id uuid,p_appointment_id uuid,p_expected_revision bigint)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare a public.appointments%rowtype; s public.service_searches%rowtype; owned_provider_uuid uuid;
begin
  if not private.dispatch_is_provider() then raise exception using errcode='42501',message='PROVIDER_REQUIRED'; end if;
  select id into owned_provider_uuid from public.providers where account_id=auth.uid();
  select * into a from public.appointments where id=p_appointment_id and provider_id=owned_provider_uuid for update;
  select * into s from public.service_searches where id=a.dispatch_search_id for update;
  if a.id is null or s.id is null then raise exception using errcode='P0002',message='APPOINTMENT_UNAVAILABLE'; end if;
  if a.revision<>p_expected_revision then return jsonb_build_object('outcome','REVISION_CONFLICT','snapshot',private.pittec_dispatch_snapshot(s.id)); end if;
  if a.status='IN_PROGRESS' then return jsonb_build_object('outcome','ARRIVED','snapshot',private.pittec_dispatch_snapshot(s.id)); end if;
  if a.status<>'ACCEPTED' or s.status<>'ACCEPTED' then return jsonb_build_object('outcome','INVALID_TRANSITION','snapshot',private.pittec_dispatch_snapshot(s.id)); end if;
  if not exists(select 1 from public.payments p where p.request_id=s.id and p.appointment_id=a.id and p.amount=a.customer_total_amount and p.currency='BRL' and p.quote_version=a.quote_version and private.payment_can_release(p,a.service_id)) then raise exception using errcode='P0001',message='PAYMENT_REQUIRED'; end if;
  update public.appointments set status='IN_PROGRESS',arrived_at=now(),revision=revision+1,updated_at=now() where id=a.id;
  update public.service_searches set status='IN_PROGRESS',revision=revision+1,updated_at=now() where id=s.id;
  return jsonb_build_object('outcome','ARRIVED','snapshot',private.pittec_dispatch_snapshot(s.id));
end; $$;

commit;
