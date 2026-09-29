begin;

-- Once a service is truly accepted, the provider has a finite window to finalize it
-- (complete_mobile_appointment). If they never do — nor cancel — the client's captured
-- payment must not stay held forever. This deadline is the enforcement clock for that.
alter table public.appointments
  add column if not exists service_deadline_at timestamptz;

create index if not exists appointments_stalled_deadline_idx
  on public.appointments(service_deadline_at)
  where service_deadline_at is not null and status in ('ACCEPTED','IN_PROGRESS');

alter table public.appointments drop constraint if exists appointments_cancellation_check;
alter table public.appointments add constraint appointments_cancellation_check check (
  cancelled_by is null or cancelled_by in ('CLIENT','PROVIDER','SYSTEM')
);

-- Mobile-sequential appointments reach ACCEPTED directly here; start the finalization
-- clock at that moment. (PitTec reaches ACCEPTED later, after payment — see
-- private.reconcile_pittec_payment_change below.)
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
  if p_accept then update public.appointments set status='ACCEPTED',accepted_at=now(),service_deadline_at=now()+interval '4 hours',revision=revision+1,updated_at=now() where id=a.id; update public.service_searches set status='ACCEPTED',revision=revision+1,updated_at=now() where id=s.id; update private.dispatch_candidates set status='ACCEPTED',decided_at=now() where id=a.dispatch_candidate_id; return case when kind='PITTEC_AUTO_SEQUENTIAL' then private.pittec_dispatch_snapshot(s.id) else private.dispatch_snapshot(s.id) end; end if;
  update public.appointments set status='PROVIDER_REJECTED',ended_at=now(),revision=revision+1,updated_at=now() where id=a.id; perform private.apply_pending_provider_mode(a.provider_id); update private.dispatch_candidates set status='PROVIDER_REJECTED',decided_at=now() where id=a.dispatch_candidate_id; update public.service_searches set status='SEARCHING',current_appointment_id=null,current_candidate_id=null,next_dispatch_at=case when kind='PITTEC_AUTO_SEQUENTIAL' then now() else null end,revision=revision+1,updated_at=now() where id=s.id; if kind='PITTEC_AUTO_SEQUENTIAL' then perform private.advance_pittec_dispatch(s.id); return jsonb_build_object('outcome','DECLINED'); end if; perform private.advance_mobile_dispatch(s.id); return jsonb_build_object('outcome','NONE');
end; $$;

-- PitTec only reaches ACCEPTED here, once the payment is approved; start the
-- finalization clock at this transition instead of at the earlier quote offer.
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
  update public.appointments set status = 'ACCEPTED', service_deadline_at = now() + interval '4 hours', revision = revision + 1, updated_at = now()
  where id = appointment_row.id and status = 'AWAITING_PAYMENT';
  update public.service_searches set status = 'ACCEPTED', revision = revision + 1, updated_at = now()
  where id = search_row.id and status = 'AWAITING_PAYMENT';
  return new;
end; $$;

-- Force-cancels one appointment (and its search) that blew its finalization deadline.
-- Reuses the client-cancellation semantics already relied on by
-- private.persist_payment_cancellation to flip the payment to REFUND_PENDING — no
-- separate refund logic needed here, the existing worker takes it from there.
create or replace function private.expire_stalled_appointment(p_appointment_id uuid)
returns void language plpgsql security definer set search_path = '' as $$
declare a public.appointments%rowtype;
begin
  select * into a from public.appointments
  where id = p_appointment_id and status in ('ACCEPTED','IN_PROGRESS')
    and service_deadline_at is not null and service_deadline_at <= now()
  for update;
  if a.id is null then return; end if;
  update public.appointments
  set status = 'CANCELLED', cancelled_by = 'SYSTEM',
      cancellation_reason = 'SERVICE_NOT_FINALIZED_TIMEOUT',
      cancelled_at = now(), ended_at = now(), revision = revision + 1, updated_at = now()
  where id = a.id;
  delete from public.appointment_route_snapshots where appointment_id = a.id;
  update public.service_searches
  set status = 'CANCELLED', search_latitude = null, search_longitude = null,
      search_point = null, search_accuracy_m = null, request_captured_at = null,
      revision = revision + 1, updated_at = now(), ended_at = now()
  where id = a.dispatch_search_id and status not in ('CANCELLED','COMPLETED','EXHAUSTED');
  perform private.apply_pending_provider_mode(a.provider_id);
end;
$$;

create or replace function public.expire_due_stalled_appointments(p_limit integer default 100)
returns integer language plpgsql security definer set search_path = '' as $$
declare row record; total integer := 0;
begin
  for row in
    select id from public.appointments
    where status in ('ACCEPTED','IN_PROGRESS')
      and service_deadline_at is not null and service_deadline_at <= now()
    order by service_deadline_at
    for update skip locked
    limit greatest(1, least(p_limit, 500))
  loop
    perform private.expire_stalled_appointment(row.id);
    total := total + 1;
  end loop;
  return total;
end;
$$;

revoke all on function private.expire_stalled_appointment(uuid) from public,anon,authenticated;
revoke all on function public.expire_due_stalled_appointments(integer) from public,anon,authenticated;

do $$
begin
  perform cron.unschedule(jobid) from cron.job where jobname='pitgo-expire-stalled-appointments';
  perform cron.schedule('pitgo-expire-stalled-appointments','* * * * *',
    'select public.expire_due_stalled_appointments(100);');
exception when others then
  raise notice 'pg_cron scheduling must be enabled by the deployment operator';
end
$$;

commit;
