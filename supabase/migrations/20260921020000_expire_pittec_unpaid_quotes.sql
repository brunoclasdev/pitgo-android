begin;

create or replace function private.expire_pittec_payment(p_appointment_id uuid)
returns boolean language plpgsql security definer set search_path = '' as $$
declare
  appointment_row public.appointments%rowtype;
  search_row public.service_searches%rowtype;
begin
  select a.* into appointment_row
  from public.appointments a
  join public.services service on service.id = a.service_id
  where a.id = p_appointment_id
    and service.dispatch_kind = 'PITTEC_AUTO_SEQUENTIAL'
    and a.status = 'AWAITING_PAYMENT'
    and a.quote_expires_at is not null
    and a.quote_expires_at <= now()
  for update;
  if appointment_row.id is null then return false; end if;

  select s.* into search_row
  from public.service_searches s
  where s.id = appointment_row.dispatch_search_id
    and s.status = 'AWAITING_PAYMENT'
  for update;
  if search_row.id is null then return false; end if;

  if exists (
    select 1 from public.payments p
    where p.request_id = search_row.id
      and p.status in ('APPROVED','REFUND_PENDING','REFUNDED','REFUND_FAILED')
  ) then return false; end if;

  update public.payments
  set status = 'EXPIRED', updated_at = now()
  where request_id = search_row.id and status = 'PENDING';

  update public.appointments
  set status = 'EXPIRED', cancellation_reason = 'PAYMENT_TIMEOUT',
      ended_at = now(), revision = revision + 1, updated_at = now()
  where id = appointment_row.id;

  update public.service_searches
  set status = 'CANCELLED', search_latitude = null, search_longitude = null,
      search_point = null, search_accuracy_m = null, request_captured_at = null,
      next_dispatch_at = null, last_event = 'PAYMENT_TIMEOUT', last_event_at = now(),
      revision = revision + 1, updated_at = now(), ended_at = now()
  where id = search_row.id;

  perform private.apply_pending_provider_mode(appointment_row.provider_id);
  return true;
end;
$$;

create or replace function public.expire_due_pittec_payments(p_limit integer default 100)
returns integer language plpgsql security definer set search_path = '' as $$
declare
  appointment_uuid uuid;
  expired_count integer := 0;
begin
  if p_limit not between 1 and 500 then
    raise exception using errcode = '22023', message = 'INVALID_LIMIT';
  end if;
  for appointment_uuid in
    select a.id
    from public.appointments a
    join public.services service on service.id = a.service_id
    where service.dispatch_kind = 'PITTEC_AUTO_SEQUENTIAL'
      and a.status = 'AWAITING_PAYMENT'
      and a.quote_expires_at is not null
      and a.quote_expires_at <= now()
    order by a.quote_expires_at, a.id
    for update of a skip locked
    limit p_limit
  loop
    if private.expire_pittec_payment(appointment_uuid) then
      expired_count := expired_count + 1;
    end if;
  end loop;
  return expired_count;
end;
$$;

revoke all on function private.expire_pittec_payment(uuid) from public, anon, authenticated;
revoke all on function public.expire_due_pittec_payments(integer) from public, anon, authenticated;

do $$
begin
  if exists (select 1 from cron.job where jobname = 'pitgo-expire-pittec-payments') then
    perform cron.alter_job(
      job_id := (select jobid from cron.job where jobname = 'pitgo-expire-pittec-payments'),
      schedule := '30 seconds'
    );
  else
    perform cron.schedule(
      'pitgo-expire-pittec-payments', '30 seconds',
      'select public.expire_due_pittec_payments(100);'
    );
  end if;
exception when others then
  raise notice 'pg_cron scheduling must be enabled by the deployment operator';
end
$$;

commit;
