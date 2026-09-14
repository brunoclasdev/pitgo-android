-- Continuous PitTec dispatch, quote confirmation and payment-gated tracking (spec 020).
-- Forward-only: previous migrations remain immutable.
begin;

do $$
begin
  if to_regclass('public.service_searches') is null
    or to_regclass('private.dispatch_candidates') is null
    or to_regclass('public.appointments') is null
    or to_regclass('public.payments') is null
    or to_regclass('public.client_current_locations') is null
    or to_regclass('public.provider_current_locations') is null then
    raise exception 'PitTec call tracking prerequisites are missing';
  end if;
end
$$;

alter table public.service_searches
  add column if not exists next_dispatch_at timestamptz;

alter table public.appointments
  add column if not exists quote_version bigint not null default 1,
  add column if not exists client_confirmation_deadline timestamptz,
  add column if not exists client_confirmed_at timestamptz,
  add column if not exists client_confirmed_quote_version bigint;

alter table public.payments
  add column if not exists appointment_id uuid,
  add column if not exists quote_version bigint,
  add column if not exists currency text not null default 'BRL';

do $$
begin
  if not exists (
    select 1 from pg_constraint where conname = 'payments_appointment_fkey'
  ) then
    alter table public.payments add constraint payments_appointment_fkey
      foreign key (appointment_id) references public.appointments(id) on delete set null;
  end if;
end
$$;

alter table public.service_searches drop constraint if exists service_search_status_check;
alter table public.service_searches add constraint service_search_status_check check (status in (
  'SEARCHING','OPTION_FOUND','AWAITING_PROVIDER','AWAITING_CLIENT_CONFIRMATION','AWAITING_PAYMENT',
  'ACCEPTED','IN_PROGRESS','CANCELLED','COMPLETED','EXHAUSTED'
));
alter table public.service_searches drop constraint if exists service_searches_last_event_valid;
alter table public.service_searches add constraint service_searches_last_event_valid check (
  last_event is null or last_event in (
    'PROVIDER_REJECTED','OFFER_EXPIRED','PROVIDER_CANCELLED','SEARCH_CONTINUES',
    'CLIENT_CONFIRMATION_TIMEOUT','QUOTE_DECLINED'
  )
);

alter table public.appointments drop constraint if exists appointments_status_check;
alter table public.appointments add constraint appointments_status_check check (status in (
  'PENDING','AWAITING_CLIENT_CONFIRMATION','AWAITING_PAYMENT','ACCEPTED','IN_PROGRESS',
  'COMPLETED','CANCELLED','PROVIDER_REJECTED','EXPIRED'
));
alter table public.appointments drop constraint if exists appointments_quote_version_check;
alter table public.appointments add constraint appointments_quote_version_check
  check (quote_version > 0 and (
    client_confirmed_quote_version is null or client_confirmed_quote_version > 0
  ));
alter table public.appointments drop constraint if exists appointments_confirmation_state_check;
alter table public.appointments add constraint appointments_confirmation_state_check check (
  status not in ('AWAITING_CLIENT_CONFIRMATION','AWAITING_PAYMENT') or (
    accepted_at is not null
    and client_confirmation_deadline is not null
    and quoted_price is not null
    and quoted_price > 0
  )
);

alter table public.payments drop constraint if exists payments_currency_check;
alter table public.payments add constraint payments_currency_check check (currency = 'BRL');
alter table public.payments drop constraint if exists payments_pittec_context_check;
alter table public.payments add constraint payments_pittec_context_check check (
  dispatch_kind <> 'PITTEC_AUTO_SEQUENTIAL'
  or (appointment_id is not null and quote_version is not null and quote_version > 0)
) not valid;

drop index if exists public.service_searches_client_service_active_unique;
create unique index service_searches_client_service_active_unique
  on public.service_searches(client_id, service_id)
  where status in (
    'SEARCHING','OPTION_FOUND','AWAITING_PROVIDER','AWAITING_CLIENT_CONFIRMATION',
    'AWAITING_PAYMENT','ACCEPTED','IN_PROGRESS'
  );

drop index if exists public.one_active_appointment_per_provider;
create unique index one_active_appointment_per_provider
  on public.appointments(provider_id)
  where status in (
    'PENDING','AWAITING_CLIENT_CONFIRMATION','AWAITING_PAYMENT','ACCEPTED','IN_PROGRESS'
  );

create index if not exists service_searches_due_pittec_idx
  on public.service_searches(next_dispatch_at, id)
  where status = 'SEARCHING' and next_dispatch_at is not null;

create index if not exists appointments_confirmation_deadline_idx
  on public.appointments(client_confirmation_deadline, id)
  where status = 'AWAITING_CLIENT_CONFIRMATION';

create index if not exists payments_appointment_quote_idx
  on public.payments(appointment_id, quote_version, created_at desc)
  where dispatch_kind = 'PITTEC_AUTO_SEQUENTIAL';

-- Backfill links when the legacy flow already created a PitTec appointment. The NOT VALID check
-- deliberately permits historical rows without an appointment while enforcing the contract for
-- every new payment.
update public.payments as payment
set appointment_id = coalesce(payment.appointment_id, (
      select appointment.id
      from public.appointments as appointment
      where appointment.dispatch_search_id = payment.request_id
      order by appointment.created_at desc
      limit 1
    )),
    quote_version = coalesce(payment.quote_version, 1),
    currency = 'BRL'
where payment.dispatch_kind = 'PITTEC_AUTO_SEQUENTIAL';

create or replace function private.apply_pending_provider_mode(p_provider_id uuid)
returns void language plpgsql security definer set search_path = '' as $$
begin
  update public.providers
  set location_mode = pending_location_mode,
      pending_location_mode = null,
      updated_at = now()
  where id = p_provider_id and pending_location_mode is not null
    and not exists (
      select 1 from public.appointments as appointment
      where appointment.provider_id = p_provider_id
        and appointment.status in (
          'PENDING','AWAITING_CLIENT_CONFIRMATION','AWAITING_PAYMENT','ACCEPTED','IN_PROGRESS'
        )
    );
end;
$$;

create or replace function private.pittec_dispatch_snapshot(p_search_id uuid)
returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare
  search_row public.service_searches%rowtype;
  candidate_row private.dispatch_candidates%rowtype;
  appointment_row public.appointments%rowtype;
  provider_account_id uuid;
  provider_display_name text;
  service_display_name text;
  caller_is_client boolean;
  caller_is_provider boolean;
  payment_status_value text;
  quote_value jsonb;
begin
  select search.* into search_row
  from public.service_searches as search where search.id = p_search_id;
  if search_row.id is null or not private.is_pittec_service(search_row.service_id) then
    return null;
  end if;
  if search_row.current_candidate_id is not null then
    select candidate.* into candidate_row
    from private.dispatch_candidates as candidate
    where candidate.id = search_row.current_candidate_id;
  end if;
  if search_row.current_appointment_id is not null then
    select appointment.* into appointment_row
    from public.appointments as appointment
    where appointment.id = search_row.current_appointment_id;
    select provider.account_id, provider.name
    into provider_account_id, provider_display_name
    from public.providers as provider where provider.id = appointment_row.provider_id;
  end if;
  caller_is_client := auth.uid() = search_row.client_id;
  caller_is_provider := auth.uid() is not distinct from provider_account_id;
  if not caller_is_client and not caller_is_provider then
    raise exception using errcode = '42501', message = 'PARTICIPANT_REQUIRED';
  end if;
  select service.name into service_display_name
  from public.services as service where service.id = search_row.service_id;
  select payment.status into payment_status_value
  from public.payments as payment
  where payment.request_id = search_row.id
  order by payment.created_at desc
  limit 1;

  quote_value := case
    when appointment_row.id is null then null
    when caller_is_provider or appointment_row.status <> 'PENDING' then jsonb_build_object(
      'amount', appointment_row.quoted_price,
      'currency', 'BRL',
      'version', appointment_row.quote_version,
      'client_confirmation_deadline', appointment_row.client_confirmation_deadline,
      'client_confirmed_at', appointment_row.client_confirmed_at
    )
    else null
  end;

  return jsonb_strip_nulls(jsonb_build_object(
    'search_id', search_row.id,
    'service_id', search_row.service_id,
    'service_name', service_display_name,
    'dispatch_kind', 'PITTEC_AUTO_SEQUENTIAL',
    'status', search_row.status,
    'radius_meters', search_row.current_radius_m,
    'revision', search_row.revision,
    'server_now', now(),
    'next_dispatch_at', search_row.next_dispatch_at,
    'last_event', search_row.last_event,
    'last_event_at', search_row.last_event_at,
    'tracking_authorized', search_row.status in ('ACCEPTED','IN_PROGRESS'),
    'payment', jsonb_build_object('status', coalesce(payment_status_value, 'NOT_STARTED')),
    'allowed_actions', case
      when caller_is_client and search_row.status in ('SEARCHING','AWAITING_PROVIDER')
        then jsonb_build_array('CANCEL')
      when caller_is_client and search_row.status = 'AWAITING_CLIENT_CONFIRMATION'
        then jsonb_build_array('CONFIRM_QUOTE','DECLINE_QUOTE','CANCEL')
      when caller_is_client and search_row.status = 'AWAITING_PAYMENT'
        then jsonb_build_array('OPEN_PAYMENT','CANCEL')
      when caller_is_client and search_row.status in ('ACCEPTED','IN_PROGRESS')
        then jsonb_build_array('OPEN_TRACKING','CANCEL')
      when caller_is_provider and appointment_row.status = 'PENDING'
        then jsonb_build_array('ACCEPT','DECLINE')
      when caller_is_provider and appointment_row.status in (
        'AWAITING_CLIENT_CONFIRMATION','AWAITING_PAYMENT'
      ) then jsonb_build_array('CANCEL')
      when caller_is_provider and appointment_row.status = 'ACCEPTED'
        then jsonb_build_array('OPEN_TRACKING','ARRIVE','CANCEL')
      when caller_is_provider and appointment_row.status = 'IN_PROGRESS'
        then jsonb_build_array('OPEN_TRACKING','COMPLETE','CANCEL')
      else '[]'::jsonb
    end,
    'offer', case
      when caller_is_provider and appointment_row.status = 'PENDING' then jsonb_build_object(
        'approximate_distance_meters', round(candidate_row.distance_meters / 500) * 500,
        'region', candidate_row.locality,
        'quoted_price', appointment_row.quoted_price,
        'currency', 'BRL',
        'quote_version', appointment_row.quote_version
      ) else null
    end,
    'provider', case
      when search_row.status in (
        'AWAITING_CLIENT_CONFIRMATION','AWAITING_PAYMENT','ACCEPTED','IN_PROGRESS',
        'COMPLETED','CANCELLED'
      ) and provider_display_name is not null then jsonb_build_object(
        'provider_id', appointment_row.provider_id,
        'display_name', provider_display_name
      ) else null
    end,
    'appointment', case when appointment_row.id is null then null else jsonb_build_object(
      'appointment_id', appointment_row.id,
      'status', appointment_row.status,
      'response_deadline', appointment_row.response_deadline,
      'revision', appointment_row.revision,
      'accepted_at', appointment_row.accepted_at,
      'arrived_at', appointment_row.arrived_at,
      'cancelled_by', appointment_row.cancelled_by,
      'quoted_price', appointment_row.quoted_price,
      'is_free', appointment_row.is_free,
      'quote_version', appointment_row.quote_version,
      'client_confirmation_deadline', appointment_row.client_confirmation_deadline,
      'client_confirmed_at', appointment_row.client_confirmed_at,
      'client_confirmed_quote_version', appointment_row.client_confirmed_quote_version,
      'quote', quote_value
    ) end
  ));
end;
$$;

create or replace function private.advance_pittec_dispatch(p_search_id uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  search_row public.service_searches%rowtype;
  chosen record;
  radius integer;
  next_sequence integer;
  candidate_uuid uuid;
  appointment_uuid uuid;
begin
  select search.* into search_row
  from public.service_searches as search where search.id = p_search_id for update;
  if search_row.id is null or not private.is_pittec_service(search_row.service_id) then
    raise exception using errcode = 'P0002', message = 'PITTEC_SEARCH_UNAVAILABLE';
  end if;
  if search_row.status <> 'SEARCHING' then
    return jsonb_build_object('outcome','UNCHANGED');
  end if;
  if search_row.search_point is null then
    raise exception using errcode = '22023', message = 'INVALID_LOCATION';
  end if;

  radius := greatest(10000, search_row.current_radius_m);
  loop
    select
      provider.id as provider_id,
      offer.id as provider_service_id,
      provider.name as provider_name,
      coalesce(
        nullif(concat_ws(' - ',nullif(btrim(client_address.city),''),nullif(btrim(client_address.state),'')),''),
        case radius when 10000 then 'Região de até 10 km'
          when 25000 then 'Região de até 25 km' else 'Região de até 50 km' end
      ) as locality,
      extensions.st_distance(location.position, search_row.search_point) as distance_meters,
      offer.price,
      offer.is_free
    into chosen
    from public.provider_services as offer
    join public.providers as provider on provider.id = offer.provider_id
    join public.account_roles as role
      on role.user_id = provider.account_id and role.account_type = 'PROVIDER'
    join public.provider_discovery_locations as location on location.provider_id = provider.id
    left join public.client_addresses as client_address on client_address.client_id = search_row.client_id
    where offer.service_id = search_row.service_id and offer.active
      and offer.price > 0 and not offer.is_free
      and provider.active and provider.available and provider.location_mode = 'PITTEC'
      and location.published_at >= now() - interval '30 seconds'
      and extensions.st_dwithin(location.position, search_row.search_point, radius)
      and not exists (
        select 1 from private.dispatch_candidates as prior
        where prior.search_id = search_row.id and prior.provider_id = provider.id
      )
      and not exists (
        select 1 from public.appointments as active_appointment
        where active_appointment.provider_id = provider.id
          and active_appointment.status in (
            'PENDING','AWAITING_CLIENT_CONFIRMATION','AWAITING_PAYMENT','ACCEPTED','IN_PROGRESS'
          )
      )
    order by extensions.st_distance(location.position, search_row.search_point), provider.id
    limit 1;

    if chosen.provider_id is null then
      if radius = 10000 then radius := 25000; continue; end if;
      if radius = 25000 then radius := 50000; continue; end if;
      update public.service_searches
      set status = 'SEARCHING', current_radius_m = 50000,
          current_candidate_id = null, current_appointment_id = null,
          next_dispatch_at = now() + interval '5 seconds',
          last_event = 'SEARCH_CONTINUES', last_event_at = now(),
          revision = revision + 1, updated_at = now(), ended_at = null
      where id = search_row.id;
      return jsonb_build_object('outcome','SEARCH_CONTINUES');
    end if;

    select coalesce(max(candidate.sequence),0) + 1 into next_sequence
    from private.dispatch_candidates as candidate where candidate.search_id = search_row.id;
    begin
      insert into private.dispatch_candidates(
        search_id,provider_id,provider_service_id,sequence,status,provider_name,
        locality,distance_meters,quoted_price,is_free,decided_at
      ) values (
        search_row.id,chosen.provider_id,chosen.provider_service_id,next_sequence,'REQUESTED',
        chosen.provider_name,chosen.locality,chosen.distance_meters,chosen.price,chosen.is_free,now()
      ) returning id into candidate_uuid;

      insert into public.appointments(
        client_id,provider_id,status,dispatch_search_id,dispatch_candidate_id,
        service_id,provider_service_id,quoted_price,is_free,response_deadline,revision,quote_version
      ) values (
        search_row.client_id,chosen.provider_id,'PENDING',search_row.id,candidate_uuid,
        search_row.service_id,chosen.provider_service_id,chosen.price,chosen.is_free,
        now() + interval '60 seconds',1,1
      ) returning id into appointment_uuid;
    exception when unique_violation then
      update private.dispatch_candidates
      set status = 'INVALIDATED', decided_at = now()
      where id = candidate_uuid;
      continue;
    end;

    update public.service_searches
    set status = 'AWAITING_PROVIDER', current_radius_m = radius,
        current_candidate_id = candidate_uuid, current_appointment_id = appointment_uuid,
        next_dispatch_at = null, revision = revision + 1, updated_at = now()
    where id = search_row.id;
    return jsonb_build_object('outcome','OFFERED','appointment_id',appointment_uuid);
  end loop;
end;
$$;

create or replace function private.expire_pittec_confirmation(p_appointment_id uuid)
returns boolean language plpgsql security definer set search_path = '' as $$
declare
  appointment_row public.appointments%rowtype;
begin
  select appointment.* into appointment_row
  from public.appointments as appointment
  where appointment.id = p_appointment_id
    and appointment.status = 'AWAITING_CLIENT_CONFIRMATION'
    and appointment.client_confirmation_deadline <= now()
  for update;
  if appointment_row.id is null then return false; end if;

  update public.appointments
  set status = 'EXPIRED', cancellation_reason = 'CLIENT_CONFIRMATION_TIMEOUT',
      ended_at = now(), revision = revision + 1, updated_at = now()
  where id = appointment_row.id;
  update public.service_searches
  set status = 'CANCELLED', search_latitude = null, search_longitude = null,
      search_point = null, search_accuracy_m = null, request_captured_at = null,
      next_dispatch_at = null, last_event = 'CLIENT_CONFIRMATION_TIMEOUT',
      last_event_at = now(), revision = revision + 1, updated_at = now(), ended_at = now()
  where id = appointment_row.dispatch_search_id
    and status = 'AWAITING_CLIENT_CONFIRMATION';
  perform private.apply_pending_provider_mode(appointment_row.provider_id);
  return true;
end;
$$;

create or replace function public.advance_due_pittec_searches(p_limit integer default 100)
returns integer language plpgsql security definer set search_path = '' as $$
declare
  search_uuid uuid;
  advanced_count integer := 0;
begin
  if p_limit not between 1 and 500 then
    raise exception using errcode = '22023', message = 'INVALID_LIMIT';
  end if;
  for search_uuid in
    select search.id
    from public.service_searches as search
    join public.services as service on service.id = search.service_id
    where search.status = 'SEARCHING'
      and search.next_dispatch_at <= now()
      and service.dispatch_kind = 'PITTEC_AUTO_SEQUENTIAL'
    order by search.next_dispatch_at, search.id
    for update of search skip locked
    limit p_limit
  loop
    perform private.advance_pittec_dispatch(search_uuid);
    advanced_count := advanced_count + 1;
  end loop;
  return advanced_count;
end;
$$;

create or replace function public.expire_due_pittec_confirmations(p_limit integer default 100)
returns integer language plpgsql security definer set search_path = '' as $$
declare
  appointment_uuid uuid;
  expired_count integer := 0;
begin
  if p_limit not between 1 and 500 then
    raise exception using errcode = '22023', message = 'INVALID_LIMIT';
  end if;
  for appointment_uuid in
    select appointment.id
    from public.appointments as appointment
    join public.services as service on service.id = appointment.service_id
    where appointment.status = 'AWAITING_CLIENT_CONFIRMATION'
      and appointment.client_confirmation_deadline <= now()
      and service.dispatch_kind = 'PITTEC_AUTO_SEQUENTIAL'
    order by appointment.client_confirmation_deadline, appointment.id
    for update of appointment skip locked
    limit p_limit
  loop
    if private.expire_pittec_confirmation(appointment_uuid) then
      expired_count := expired_count + 1;
    end if;
  end loop;
  return expired_count;
end;
$$;

create or replace function public.start_pittec_dispatch(
  p_request_id uuid,
  p_service_id uuid,
  p_latitude double precision,
  p_longitude double precision,
  p_accuracy_meters double precision,
  p_captured_at timestamptz
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  existing_search_id uuid;
begin
  if not private.dispatch_is_client() then
    raise exception using errcode = '42501', message = 'CLIENT_REQUIRED';
  end if;
  if not private.is_pittec_service(p_service_id) then
    raise exception using errcode = '22023', message = 'PITTEC_SERVICE_REQUIRED';
  end if;
  if p_latitude not between -90 and 90 or p_longitude not between -180 and 180
    or p_accuracy_meters <= 0 or p_accuracy_meters > 100
    or p_captured_at < now() - interval '30 seconds'
    or p_captured_at > now() + interval '10 seconds' then
    raise exception using errcode = '22023', message = 'INVALID_LOCATION';
  end if;

  perform pg_advisory_xact_lock(hashtextextended(auth.uid()::text,1501));
  select search.id into existing_search_id
  from public.service_searches as search
  join public.services as service on service.id = search.service_id
  where search.client_id = auth.uid()
    and service.dispatch_kind = 'PITTEC_AUTO_SEQUENTIAL'
    and search.status in (
      'SEARCHING','AWAITING_PROVIDER','AWAITING_CLIENT_CONFIRMATION',
      'AWAITING_PAYMENT','ACCEPTED','IN_PROGRESS'
    )
  order by search.updated_at desc limit 1;
  if existing_search_id is not null then
    return jsonb_build_object(
      'outcome','ACTIVE_PITTEC_DISPATCH_EXISTS',
      'snapshot',private.pittec_dispatch_snapshot(existing_search_id)
    );
  end if;
  if exists (select 1 from public.service_searches where id = p_request_id) then
    raise exception using errcode = '23505', message = 'REQUEST_ID_UNAVAILABLE';
  end if;
  insert into public.service_searches(
    id,client_id,service_id,status,search_latitude,search_longitude,search_point,
    search_accuracy_m,current_radius_m,request_captured_at,next_dispatch_at
  ) values (
    p_request_id,auth.uid(),p_service_id,'SEARCHING',p_latitude,p_longitude,
    extensions.st_setsrid(extensions.st_makepoint(p_longitude,p_latitude),4326)::extensions.geography,
    p_accuracy_meters,10000,p_captured_at,now()
  );
  perform private.advance_pittec_dispatch(p_request_id);
  return jsonb_build_object('outcome','STARTED','snapshot',private.pittec_dispatch_snapshot(p_request_id));
end;
$$;

create or replace function public.get_my_active_pittec_dispatch()
returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  search_uuid uuid;
  appointment_uuid uuid;
  search_status text;
  next_dispatch timestamptz;
begin
  if private.dispatch_is_client() then
    select search.id, search.current_appointment_id, search.status, search.next_dispatch_at
    into search_uuid, appointment_uuid, search_status, next_dispatch
    from public.service_searches as search
    join public.services as service on service.id = search.service_id
    where search.client_id = auth.uid()
      and service.dispatch_kind = 'PITTEC_AUTO_SEQUENTIAL'
      and search.status in (
        'SEARCHING','AWAITING_PROVIDER','AWAITING_CLIENT_CONFIRMATION',
        'AWAITING_PAYMENT','ACCEPTED','IN_PROGRESS'
      )
    order by search.updated_at desc limit 1;
  elsif private.dispatch_is_provider() then
    select search.id, search.current_appointment_id, search.status, search.next_dispatch_at
    into search_uuid, appointment_uuid, search_status, next_dispatch
    from public.service_searches as search
    join public.services as service on service.id = search.service_id
    join public.appointments as appointment on appointment.dispatch_search_id = search.id
    join public.providers as provider on provider.id = appointment.provider_id
    where provider.account_id = auth.uid()
      and service.dispatch_kind = 'PITTEC_AUTO_SEQUENTIAL'
      and appointment.status in (
        'PENDING','AWAITING_CLIENT_CONFIRMATION','AWAITING_PAYMENT','ACCEPTED','IN_PROGRESS'
      )
    order by appointment.updated_at desc limit 1;
  else
    raise exception using errcode = '42501', message = 'ACCOUNT_REQUIRED';
  end if;
  if search_uuid is null then
    return jsonb_build_object('outcome','NONE','server_now',now());
  end if;
  if appointment_uuid is not null then
    perform private.expire_mobile_dispatch_appointment(appointment_uuid);
    perform private.expire_pittec_confirmation(appointment_uuid);
  end if;
  if search_status = 'SEARCHING' and coalesce(next_dispatch, now()) <= now() then
    perform private.advance_pittec_dispatch(search_uuid);
  end if;
  return jsonb_build_object(
    'outcome','ACTIVE',
    'snapshot',private.pittec_dispatch_snapshot(search_uuid)
  );
end;
$$;

create or replace function public.respond_to_mobile_dispatch(
  p_appointment_id uuid,
  p_accept boolean,
  p_expected_revision bigint
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  appointment_probe public.appointments%rowtype;
  appointment_row public.appointments%rowtype;
  search_row public.service_searches%rowtype;
  owned_provider_uuid uuid;
  dispatch_kind_value text;
begin
  if not private.dispatch_is_provider() then
    raise exception using errcode = '42501', message = 'PROVIDER_REQUIRED';
  end if;
  select provider.id into owned_provider_uuid
  from public.providers as provider where provider.account_id = auth.uid();
  select appointment.* into appointment_probe
  from public.appointments as appointment
  where appointment.id = p_appointment_id and appointment.provider_id = owned_provider_uuid;
  if appointment_probe.id is null then
    raise exception using errcode = 'P0002', message = 'REQUEST_UNAVAILABLE';
  end if;
  select search.* into search_row from public.service_searches as search
  where search.id = appointment_probe.dispatch_search_id for update;
  select appointment.* into appointment_row from public.appointments as appointment
  where appointment.id = appointment_probe.id for update;
  select service.dispatch_kind into dispatch_kind_value
  from public.services as service where service.id = appointment_row.service_id;

  if appointment_row.status = 'PENDING' and appointment_row.response_deadline <= now() then
    perform private.expire_mobile_dispatch_appointment(appointment_row.id);
    return jsonb_build_object('outcome','OFFER_EXPIRED');
  end if;
  if appointment_row.revision <> p_expected_revision then
    return jsonb_build_object(
      'outcome','REVISION_CONFLICT',
      'snapshot',case when dispatch_kind_value = 'PITTEC_AUTO_SEQUENTIAL'
        then private.pittec_dispatch_snapshot(appointment_row.dispatch_search_id)
        else private.dispatch_snapshot(appointment_row.dispatch_search_id) end
    );
  end if;
  if appointment_row.status <> 'PENDING'
    or search_row.current_appointment_id is distinct from appointment_row.id then
    return jsonb_build_object('outcome','OFFER_UNAVAILABLE');
  end if;

  if p_accept then
    if not exists (
      select 1 from public.providers as provider
      join public.provider_services as offer on offer.provider_id = provider.id
      join public.provider_discovery_locations as location on location.provider_id = provider.id
      where provider.id = appointment_row.provider_id
        and provider.active and provider.available and offer.active
        and offer.id = appointment_row.provider_service_id
        and location.published_at >= now() - interval '30 seconds'
        and (dispatch_kind_value <> 'PITTEC_AUTO_SEQUENTIAL' or provider.location_mode = 'PITTEC')
    ) then
      raise exception using errcode = '22023', message = 'PROVIDER_INELIGIBLE';
    end if;
    if dispatch_kind_value = 'PITTEC_AUTO_SEQUENTIAL' then
      if appointment_row.quoted_price is null or appointment_row.quoted_price <= 0 then
        raise exception using errcode = '22023', message = 'PITTEC_PRICE_REQUIRED';
      end if;
      update public.appointments
      set status = 'AWAITING_CLIENT_CONFIRMATION', accepted_at = now(),
          client_confirmation_deadline = now() + interval '2 minutes',
          client_confirmed_at = null, client_confirmed_quote_version = null,
          revision = revision + 1, updated_at = now()
      where id = appointment_row.id and status = 'PENDING';
      update private.dispatch_candidates set status = 'ACCEPTED', decided_at = now()
      where id = appointment_row.dispatch_candidate_id;
      update public.service_searches
      set status = 'AWAITING_CLIENT_CONFIRMATION', next_dispatch_at = null,
          revision = revision + 1, updated_at = now()
      where id = appointment_row.dispatch_search_id and current_appointment_id = appointment_row.id;
      return private.pittec_dispatch_snapshot(appointment_row.dispatch_search_id);
    end if;
    update public.appointments
    set status = 'ACCEPTED', accepted_at = now(), revision = revision + 1, updated_at = now()
    where id = appointment_row.id;
    update private.dispatch_candidates set status = 'ACCEPTED', decided_at = now()
    where id = appointment_row.dispatch_candidate_id;
    update public.service_searches
    set status = 'ACCEPTED', revision = revision + 1, updated_at = now()
    where id = appointment_row.dispatch_search_id;
    return private.dispatch_snapshot(appointment_row.dispatch_search_id);
  end if;

  update public.appointments
  set status = 'PROVIDER_REJECTED', ended_at = now(), revision = revision + 1, updated_at = now()
  where id = appointment_row.id;
  perform private.apply_pending_provider_mode(appointment_row.provider_id);
  update private.dispatch_candidates
  set status = 'PROVIDER_REJECTED', decided_at = now()
  where id = appointment_row.dispatch_candidate_id;
  update public.service_searches
  set status = 'SEARCHING', current_appointment_id = null, current_candidate_id = null,
      next_dispatch_at = case when dispatch_kind_value = 'PITTEC_AUTO_SEQUENTIAL' then now() else null end,
      last_event = case when dispatch_kind_value = 'PITTEC_AUTO_SEQUENTIAL'
        then 'PROVIDER_REJECTED' else last_event end,
      last_event_at = case when dispatch_kind_value = 'PITTEC_AUTO_SEQUENTIAL'
        then now() else last_event_at end,
      revision = revision + 1, updated_at = now()
  where id = appointment_row.dispatch_search_id;
  if dispatch_kind_value = 'PITTEC_AUTO_SEQUENTIAL' then
    perform private.advance_pittec_dispatch(appointment_row.dispatch_search_id);
    return jsonb_build_object(
      'outcome','DECLINED',
      'snapshot',private.pittec_dispatch_snapshot(appointment_row.dispatch_search_id)
    );
  end if;
  perform private.advance_mobile_dispatch(appointment_row.dispatch_search_id);
  return jsonb_build_object('outcome','NONE');
end;
$$;

create or replace function public.respond_to_pittec_quote(
  p_request_id uuid,
  p_appointment_id uuid,
  p_quote_version bigint,
  p_accept boolean,
  p_expected_revision bigint
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  search_row public.service_searches%rowtype;
  appointment_row public.appointments%rowtype;
begin
  if not private.dispatch_is_client() then
    raise exception using errcode = '42501', message = 'CLIENT_REQUIRED';
  end if;
  select search.* into search_row
  from public.service_searches as search
  where search.id = p_request_id and search.client_id = auth.uid()
  for update;
  if search_row.id is null or not private.is_pittec_service(search_row.service_id) then
    return jsonb_build_object('outcome','NOT_FOUND');
  end if;
  select appointment.* into appointment_row
  from public.appointments as appointment
  where appointment.id = p_appointment_id
    and appointment.dispatch_search_id = search_row.id
    and appointment.client_id = auth.uid()
  for update;
  if appointment_row.id is null then
    return jsonb_build_object('outcome','NOT_FOUND');
  end if;
  if search_row.revision <> p_expected_revision
    or appointment_row.quote_version <> p_quote_version then
    return jsonb_build_object(
      'outcome','REVISION_CONFLICT',
      'snapshot',private.pittec_dispatch_snapshot(search_row.id)
    );
  end if;
  if appointment_row.status = 'AWAITING_PAYMENT'
    and appointment_row.client_confirmed_quote_version = p_quote_version
    and p_accept then
    return jsonb_build_object(
      'outcome','CONFIRMED',
      'snapshot',private.pittec_dispatch_snapshot(search_row.id)
    );
  end if;
  if appointment_row.status <> 'AWAITING_CLIENT_CONFIRMATION'
    or search_row.status <> 'AWAITING_CLIENT_CONFIRMATION' then
    return jsonb_build_object(
      'outcome','INVALID_TRANSITION',
      'snapshot',private.pittec_dispatch_snapshot(search_row.id)
    );
  end if;
  if appointment_row.client_confirmation_deadline <= now() then
    perform private.expire_pittec_confirmation(appointment_row.id);
    return jsonb_build_object(
      'outcome','CONFIRMATION_EXPIRED',
      'snapshot',private.pittec_dispatch_snapshot(search_row.id)
    );
  end if;
  if not p_accept then
    update public.appointments
    set status = 'CANCELLED', cancelled_by = 'CLIENT',
        cancellation_reason = 'QUOTE_DECLINED', cancelled_at = now(), ended_at = now(),
        revision = revision + 1, updated_at = now()
    where id = appointment_row.id;
    update public.service_searches
    set status = 'CANCELLED', search_latitude = null, search_longitude = null,
        search_point = null, search_accuracy_m = null, request_captured_at = null,
        next_dispatch_at = null, last_event = 'QUOTE_DECLINED', last_event_at = now(),
        revision = revision + 1, updated_at = now(), ended_at = now()
    where id = search_row.id;
    perform private.apply_pending_provider_mode(appointment_row.provider_id);
    return jsonb_build_object(
      'outcome','DECLINED',
      'snapshot',private.pittec_dispatch_snapshot(search_row.id)
    );
  end if;

  update public.appointments
  set status = 'AWAITING_PAYMENT', client_confirmed_at = now(),
      client_confirmed_quote_version = quote_version,
      revision = revision + 1, updated_at = now()
  where id = appointment_row.id;
  update public.service_searches
  set status = 'AWAITING_PAYMENT', revision = revision + 1, updated_at = now()
  where id = search_row.id;
  return jsonb_build_object(
    'outcome','CONFIRMED',
    'snapshot',private.pittec_dispatch_snapshot(search_row.id)
  );
end;
$$;

create or replace function public.cancel_pittec_search(
  p_request_id uuid,
  p_search_id uuid,
  p_expected_revision bigint
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  search_row public.service_searches%rowtype;
  appointment_row public.appointments%rowtype;
begin
  if not private.dispatch_is_client() then
    raise exception using errcode = '42501', message = 'CLIENT_REQUIRED';
  end if;
  select search.* into search_row
  from public.service_searches as search
  where search.id = p_search_id and search.client_id = auth.uid()
  for update;
  if search_row.id is null or not private.is_pittec_service(search_row.service_id) then
    raise exception using errcode = 'P0002', message = 'PITTEC_SEARCH_UNAVAILABLE';
  end if;
  if search_row.revision <> p_expected_revision then
    return jsonb_build_object(
      'outcome','REVISION_CONFLICT','request_id',p_request_id,
      'snapshot',private.pittec_dispatch_snapshot(search_row.id)
    );
  end if;
  if search_row.status not in (
    'SEARCHING','AWAITING_PROVIDER','AWAITING_CLIENT_CONFIRMATION','AWAITING_PAYMENT',
    'ACCEPTED','IN_PROGRESS'
  ) then
    return jsonb_build_object(
      'outcome','INVALID_TRANSITION','request_id',p_request_id,
      'snapshot',private.pittec_dispatch_snapshot(search_row.id)
    );
  end if;
  if search_row.current_appointment_id is not null then
    select appointment.* into appointment_row
    from public.appointments as appointment
    where appointment.id = search_row.current_appointment_id for update;
    if appointment_row.status in (
      'PENDING','AWAITING_CLIENT_CONFIRMATION','AWAITING_PAYMENT','ACCEPTED','IN_PROGRESS'
    ) then
      update public.appointments
      set status = 'CANCELLED', cancelled_by = 'CLIENT', cancellation_reason = 'CLIENT_CANCELLED',
          cancelled_at = now(), ended_at = now(), revision = revision + 1, updated_at = now()
      where id = appointment_row.id;
      update private.dispatch_candidates
      set status = 'INVALIDATED', decided_at = now()
      where id = appointment_row.dispatch_candidate_id and status <> 'ACCEPTED';
      perform private.apply_pending_provider_mode(appointment_row.provider_id);
    end if;
  end if;
  update public.service_searches
  set status = 'CANCELLED', search_latitude = null, search_longitude = null,
      search_point = null, search_accuracy_m = null, request_captured_at = null,
      next_dispatch_at = null, revision = revision + 1, updated_at = now(), ended_at = now()
  where id = search_row.id;
  update public.payments set status = 'REFUND_PENDING', updated_at = now()
  where request_id = search_row.id and status = 'APPROVED';
  return jsonb_build_object(
    'outcome','CANCELLED','request_id',p_request_id,
    'snapshot',private.pittec_dispatch_snapshot(search_row.id)
  );
end;
$$;

create or replace function public.cancel_mobile_appointment(
  p_appointment_id uuid,
  p_reason text,
  p_expected_revision bigint
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  appointment_probe public.appointments%rowtype;
  appointment_row public.appointments%rowtype;
  search_row public.service_searches%rowtype;
  owned_provider_uuid uuid;
  actor_value text;
  dispatch_kind_value text;
begin
  if nullif(btrim(p_reason),'') is null then
    raise exception using errcode = '22023', message = 'CANCELLATION_REASON_REQUIRED';
  end if;
  select provider.id into owned_provider_uuid
  from public.providers as provider where provider.account_id = auth.uid();
  select appointment.* into appointment_probe
  from public.appointments as appointment where appointment.id = p_appointment_id;
  if appointment_probe.id is null
    or (appointment_probe.client_id <> auth.uid()
      and appointment_probe.provider_id is distinct from owned_provider_uuid) then
    raise exception using errcode = '42501', message = 'PARTICIPANT_REQUIRED';
  end if;
  select search.* into search_row from public.service_searches as search
  where search.id = appointment_probe.dispatch_search_id for update;
  select appointment.* into appointment_row from public.appointments as appointment
  where appointment.id = appointment_probe.id for update;
  select service.dispatch_kind into dispatch_kind_value
  from public.services as service where service.id = appointment_row.service_id;
  if appointment_row.revision <> p_expected_revision then
    return jsonb_build_object(
      'outcome','REVISION_CONFLICT',
      'snapshot',case when dispatch_kind_value = 'PITTEC_AUTO_SEQUENTIAL'
        then private.pittec_dispatch_snapshot(appointment_row.dispatch_search_id)
        else private.dispatch_snapshot(appointment_row.dispatch_search_id) end
    );
  end if;
  if appointment_row.status not in (
    'PENDING','AWAITING_CLIENT_CONFIRMATION','AWAITING_PAYMENT','ACCEPTED','IN_PROGRESS'
  ) then
    return case when dispatch_kind_value = 'PITTEC_AUTO_SEQUENTIAL'
      then private.pittec_dispatch_snapshot(appointment_row.dispatch_search_id)
      else private.dispatch_snapshot(appointment_row.dispatch_search_id) end;
  end if;

  actor_value := case when appointment_row.client_id = auth.uid() then 'CLIENT' else 'PROVIDER' end;
  update public.appointments
  set status = 'CANCELLED', cancelled_by = actor_value,
      cancellation_reason = btrim(p_reason), cancelled_at = now(), ended_at = now(),
      revision = revision + 1, updated_at = now()
  where id = appointment_row.id;
  delete from public.appointment_route_snapshots where appointment_id = appointment_row.id;

  if actor_value = 'PROVIDER'
    and (dispatch_kind_value <> 'PITTEC_AUTO_SEQUENTIAL'
      or appointment_row.status in ('PENDING','AWAITING_CLIENT_CONFIRMATION')) then
    update private.dispatch_candidates
    set status = 'PROVIDER_CANCELLED', decided_at = now()
    where id = appointment_row.dispatch_candidate_id;
    update public.service_searches
    set status = 'SEARCHING', current_appointment_id = null, current_candidate_id = null,
        next_dispatch_at = case when dispatch_kind_value = 'PITTEC_AUTO_SEQUENTIAL' then now() else null end,
        last_event = case when dispatch_kind_value = 'PITTEC_AUTO_SEQUENTIAL'
          then 'PROVIDER_CANCELLED' else last_event end,
        last_event_at = case when dispatch_kind_value = 'PITTEC_AUTO_SEQUENTIAL'
          then now() else last_event_at end,
        revision = revision + 1, updated_at = now()
    where id = appointment_row.dispatch_search_id;
    perform private.apply_pending_provider_mode(appointment_row.provider_id);
    if dispatch_kind_value = 'PITTEC_AUTO_SEQUENTIAL' then
      perform private.advance_pittec_dispatch(appointment_row.dispatch_search_id);
      return jsonb_build_object('outcome','PROVIDER_CANCELLED_SEARCH_RESUMED');
    end if;
    perform private.advance_mobile_dispatch(appointment_row.dispatch_search_id);
    return jsonb_build_object('outcome','NONE');
  end if;

  update public.service_searches
  set status = 'CANCELLED', search_latitude = null, search_longitude = null,
      search_point = null, search_accuracy_m = null, request_captured_at = null,
      next_dispatch_at = null, revision = revision + 1, updated_at = now(), ended_at = now()
  where id = appointment_row.dispatch_search_id;
  update public.payments set status = 'REFUND_PENDING', updated_at = now()
  where request_id = appointment_row.dispatch_search_id and status = 'APPROVED';
  perform private.apply_pending_provider_mode(appointment_row.provider_id);
  return case when dispatch_kind_value = 'PITTEC_AUTO_SEQUENTIAL'
    then private.pittec_dispatch_snapshot(appointment_row.dispatch_search_id)
    else private.dispatch_snapshot(appointment_row.dispatch_search_id) end;
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

drop trigger if exists reconcile_pittec_payment on public.payments;
create trigger reconcile_pittec_payment
after insert or update of status on public.payments
for each row execute function private.reconcile_pittec_payment_change();

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
      and payment.status = 'APPROVED'
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

create or replace function public.publish_client_location(
  p_appointment_id uuid,
  p_latitude double precision,
  p_longitude double precision,
  p_accuracy_m double precision,
  p_captured_at timestamptz
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  appointment_row public.appointments%rowtype;
  dispatch_kind_value text;
begin
  if not private.dispatch_is_client() then
    raise exception using errcode = '42501', message = 'CLIENT_REQUIRED';
  end if;
  if p_latitude not between -90 and 90 or p_longitude not between -180 and 180
    or p_accuracy_m <= 0 or p_accuracy_m > 5000
    or p_captured_at < now() - interval '5 minutes'
    or p_captured_at > now() + interval '10 seconds' then
    raise exception using errcode = '22023', message = 'INVALID_LOCATION';
  end if;
  select appointment.* into appointment_row
  from public.appointments as appointment
  where appointment.id = p_appointment_id
    and appointment.client_id = auth.uid()
    and appointment.status in ('ACCEPTED','IN_PROGRESS')
  for update;
  if appointment_row.id is null then
    raise exception using errcode = 'P0002', message = 'APPOINTMENT_UNAVAILABLE';
  end if;
  select service.dispatch_kind into dispatch_kind_value
  from public.services as service where service.id = appointment_row.service_id;
  if dispatch_kind_value = 'PITTEC_AUTO_SEQUENTIAL' then
    raise exception using errcode = '22023', message = 'PITTEC_DESTINATION_IMMUTABLE';
  end if;
  insert into public.client_current_locations(
    appointment_id,latitude,longitude,accuracy_m,captured_at,published_at
  ) values (
    appointment_row.id,p_latitude,p_longitude,p_accuracy_m,p_captured_at,now()
  ) on conflict (appointment_id) do update set
    latitude = excluded.latitude,
    longitude = excluded.longitude,
    accuracy_m = excluded.accuracy_m,
    captured_at = excluded.captured_at,
    published_at = excluded.published_at;
  return jsonb_build_object('outcome','PUBLISHED','published_at',now());
end;
$$;

create or replace function private.can_read_payment_topic(p_topic text)
returns boolean language sql stable security definer set search_path = '' as $$
  select split_part(p_topic, ':', 1) = 'payment'
    and exists (
      select 1 from public.payments as payment
      where payment.request_id::text = split_part(p_topic, ':', 2)
        and payment.client_id = auth.uid()
    );
$$;

drop policy if exists payment_receive on realtime.messages;
create policy payment_receive on realtime.messages
for select to authenticated using (
  extension = 'broadcast' and private.can_read_payment_topic(realtime.topic())
);

drop policy if exists appointment_location_receive on realtime.messages;
create policy appointment_location_receive on realtime.messages
for select to authenticated using (
  extension = 'broadcast'
  and split_part(realtime.topic(), ':', 1) = 'appointment-location'
  and exists (
    select 1
    from public.appointments as appointment
    left join public.providers as provider on provider.id = appointment.provider_id
    where appointment.id::text = split_part(realtime.topic(), ':', 2)
      and appointment.status in ('ACCEPTED','IN_PROGRESS')
      and (appointment.client_id = auth.uid() or provider.account_id = auth.uid())
  )
);

revoke all on function private.pittec_dispatch_snapshot(uuid) from public, anon, authenticated;
revoke all on function private.advance_pittec_dispatch(uuid) from public, anon, authenticated;
revoke all on function private.expire_pittec_confirmation(uuid) from public, anon, authenticated;
revoke all on function private.reconcile_pittec_payment_change() from public, anon, authenticated;
revoke all on function private.can_read_payment_topic(text) from public, anon, authenticated;
revoke all on function public.advance_due_pittec_searches(integer) from public, anon, authenticated;
revoke all on function public.expire_due_pittec_confirmations(integer) from public, anon, authenticated;
revoke all on function public.respond_to_pittec_quote(uuid,uuid,bigint,boolean,bigint)
  from public, anon, authenticated;
grant execute on function public.respond_to_pittec_quote(uuid,uuid,bigint,boolean,bigint)
  to authenticated;

do $$
begin
  perform cron.unschedule(jobid) from cron.job where jobname = 'pitgo-advance-pittec-searches';
  perform cron.unschedule(jobid) from cron.job where jobname = 'pitgo-expire-pittec-confirmations';
  perform cron.schedule(
    'pitgo-advance-pittec-searches','1 second',
    'select public.advance_due_pittec_searches(100);'
  );
  perform cron.schedule(
    'pitgo-expire-pittec-confirmations','1 second',
    'select public.expire_due_pittec_confirmations(100);'
  );
exception when others then
  raise notice 'pg_cron scheduling must be enabled by the deployment operator';
end
$$;

notify pgrst, 'reload schema';
commit;
