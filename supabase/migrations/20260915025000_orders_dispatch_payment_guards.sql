begin;
create function private.payment_can_release(p public.payments, p_service uuid) returns boolean
language sql stable security definer set search_path='' as $$
  select p.status='APPROVED' and p.sync_state='CURRENT' and p.cancel_requested_at is null
    and not p.is_compensation and p.currency='BRL'
    and (p.service_id=p_service or (p.provider_api='PAYMENTS' and p.service_id is null))
    and (p.provider_api='PAYMENTS' or (p.settled_amount-coalesce(p.refunded_amount,0)=p.amount))
    and coalesce(p.refunded_amount,0)=0
    and not exists(select 1 from public.payments other where other.request_id=p.request_id
      and other.id<>p.id and (other.sync_state<>'CURRENT' or other.status in ('PENDING','APPROVED','REFUND_PENDING','REFUND_FAILED')));
$$;
revoke all on function private.payment_can_release(public.payments,uuid) from public,anon,authenticated;

create or replace function public.start_mobile_dispatch(
  p_search_id uuid,
  p_service_id uuid,
  p_latitude double precision,
  p_longitude double precision,
  p_accuracy_m double precision
) returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  existing_id uuid;
  kind text;
begin
  if not private.dispatch_is_client() then
    raise exception using errcode = '42501', message = 'Client required';
  end if;

  if p_latitude not between -90 and 90
    or p_longitude not between -180 and 180
    or p_accuracy_m <= 0
    or p_accuracy_m > 100 then
    raise exception using errcode = '22023', message = 'Invalid search point';
  end if;

  if not exists (
    select 1 from public.payments
     where request_id = p_search_id
       and client_id = auth.uid()
       and private.payment_can_release(payments, p_service_id)
  ) then
    raise exception using errcode = 'P0001', message = 'PAYMENT_REQUIRED';
  end if;

  select dispatch_kind
    into kind
    from public.services
   where id = p_service_id
     and active;

  if kind is distinct from 'MOBILE_SEQUENTIAL' then
    raise exception using errcode = '22023', message = 'Service unavailable';
  end if;

  perform pg_advisory_xact_lock(hashtextextended(auth.uid()::text, 1301));

  -- The request UUID is the idempotency key. This includes terminal searches,
  -- which are intentionally omitted from the "active search" lookup below.
  select id
    into existing_id
    from public.service_searches
   where id = p_search_id
     and client_id = auth.uid()
     and service_id = p_service_id;

  if existing_id is not null then
    return private.dispatch_snapshot(existing_id);
  end if;

  -- Do not disclose whether a request UUID belongs to another account/service.
  if exists (select 1 from public.service_searches where id = p_search_id) then
    raise exception using errcode = 'P0002', message = 'Search unavailable';
  end if;

  select id
    into existing_id
    from public.service_searches
   where client_id = auth.uid()
     and service_id = p_service_id
     and status in ('SEARCHING', 'OPTION_FOUND', 'AWAITING_PROVIDER', 'ACCEPTED', 'IN_PROGRESS')
   order by updated_at desc
   limit 1;

  if existing_id is not null then
    return private.dispatch_snapshot(existing_id);
  end if;

  insert into public.service_searches(
    id,
    client_id,
    service_id,
    search_latitude,
    search_longitude,
    search_point,
    search_accuracy_m
  ) values (
    p_search_id,
    auth.uid(),
    p_service_id,
    p_latitude,
    p_longitude,
    extensions.st_setsrid(
      extensions.st_makepoint(p_longitude, p_latitude),
      4326
    )::extensions.geography,
    p_accuracy_m
  );

  perform private.advance_mobile_dispatch(p_search_id);
  return private.dispatch_snapshot(p_search_id);
end;
$$;

create or replace function private.reconcile_pittec_payment_change()
returns trigger language plpgsql security definer set search_path = '' as $$
declare
  search_row public.service_searches%rowtype;
  appointment_row public.appointments%rowtype;
begin
  if new.dispatch_kind <> 'PITTEC_AUTO_SEQUENTIAL' or new.status <> 'APPROVED' then
    return new;
  end if;
  select search.* into search_row
  from public.service_searches as search
  where search.id = new.request_id for update;
  if search_row.id is null then
    update public.payments set status = 'REFUND_PENDING', updated_at = now()
    where id = new.id and status = 'APPROVED';
    return new;
  end if;
  select appointment.* into appointment_row
  from public.appointments as appointment
  where appointment.id = new.appointment_id
    and appointment.dispatch_search_id = search_row.id
  for update;
  if appointment_row.id is null
    or new.client_id <> search_row.client_id
    or appointment_row.client_id <> new.client_id
    or new.amount <> appointment_row.quoted_price
    or new.currency <> 'BRL'
    or not private.payment_can_release(new, appointment_row.service_id)
    or new.quote_version is distinct from appointment_row.quote_version
    or appointment_row.client_confirmed_quote_version is distinct from appointment_row.quote_version
    or appointment_row.status <> 'AWAITING_PAYMENT'
    or search_row.status <> 'AWAITING_PAYMENT' then
    update public.payments set status = 'REFUND_PENDING', updated_at = now()
    where id = new.id and status = 'APPROVED';
    return new;
  end if;

  update public.appointments
  set status = 'ACCEPTED', revision = revision + 1, updated_at = now()
  where id = appointment_row.id and status = 'AWAITING_PAYMENT';
  update public.service_searches
  set status = 'ACCEPTED', revision = revision + 1, updated_at = now()
  where id = search_row.id and status = 'AWAITING_PAYMENT';
  insert into public.client_current_locations(
    appointment_id,latitude,longitude,accuracy_m,captured_at,published_at
  ) values (
    appointment_row.id,search_row.search_latitude,search_row.search_longitude,
    search_row.search_accuracy_m,coalesce(search_row.request_captured_at,now()),now()
  ) on conflict (appointment_id) do update set
    latitude = excluded.latitude,
    longitude = excluded.longitude,
    accuracy_m = excluded.accuracy_m,
    captured_at = excluded.captured_at,
    published_at = excluded.published_at;
  delete from public.provider_discovery_locations
  where provider_id = appointment_row.provider_id;
  return new;
end;
$$;

create or replace function public.arrive_pittec_appointment(
  p_request_id uuid,
  p_appointment_id uuid,
  p_expected_revision bigint
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  appointment_probe public.appointments%rowtype;
  appointment_row public.appointments%rowtype;
  search_row public.service_searches%rowtype;
  owned_provider_uuid uuid;
begin
  if not private.dispatch_is_provider() then
    raise exception using errcode = '42501', message = 'PROVIDER_REQUIRED';
  end if;
  select provider.id into owned_provider_uuid
  from public.providers as provider where provider.account_id = auth.uid();
  select appointment.* into appointment_probe
  from public.appointments as appointment
  where appointment.id = p_appointment_id and appointment.provider_id = owned_provider_uuid;
  if appointment_probe.id is null or not private.is_pittec_service(appointment_probe.service_id) then
    raise exception using errcode = 'P0002', message = 'APPOINTMENT_UNAVAILABLE';
  end if;
  select search.* into search_row from public.service_searches as search
  where search.id = appointment_probe.dispatch_search_id for update;
  select appointment.* into appointment_row from public.appointments as appointment
  where appointment.id = appointment_probe.id for update;
  if appointment_row.revision <> p_expected_revision then
    return jsonb_build_object(
      'outcome','REVISION_CONFLICT','request_id',p_request_id,
      'snapshot',private.pittec_dispatch_snapshot(appointment_row.dispatch_search_id)
    );
  end if;
  if appointment_row.status = 'IN_PROGRESS' then
    return jsonb_build_object(
      'outcome','ARRIVED','request_id',p_request_id,
      'snapshot',private.pittec_dispatch_snapshot(appointment_row.dispatch_search_id)
    );
  end if;
  if appointment_row.status <> 'ACCEPTED' or search_row.status <> 'ACCEPTED' then
    return jsonb_build_object(
      'outcome','INVALID_TRANSITION','request_id',p_request_id,
      'snapshot',private.pittec_dispatch_snapshot(appointment_row.dispatch_search_id)
    );
  end if;
  if not exists (
    select 1 from public.payments as payment
    where payment.request_id = search_row.id
      and payment.client_id = appointment_row.client_id
      and payment.appointment_id = appointment_row.id
      and payment.amount = appointment_row.quoted_price
      and payment.currency = 'BRL'
      and payment.quote_version = appointment_row.quote_version
      and private.payment_can_release(payment, appointment_row.service_id)
  ) then
    raise exception using errcode = 'P0001', message = 'PAYMENT_REQUIRED';
  end if;
  update public.appointments
  set status = 'IN_PROGRESS', arrived_at = now(), revision = revision + 1, updated_at = now()
  where id = appointment_row.id;
  update public.service_searches
  set status = 'IN_PROGRESS', revision = revision + 1, updated_at = now()
  where id = search_row.id;
  return jsonb_build_object(
    'outcome','ARRIVED','request_id',p_request_id,
    'snapshot',private.pittec_dispatch_snapshot(search_row.id)
  );
end;
$$;
commit;
