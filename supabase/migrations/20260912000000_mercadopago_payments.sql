-- Mercado Pago payments (Pix and saved card), spec 018-mercadopago-payments.
--
-- Adds `saved_cards` (opt-in, tokenized-only card references) and `payments` (one row per
-- charge attempt, tied to the same `service_searches.id` already used as the idempotency key by
-- `start_mobile_dispatch`/`start_pittec_dispatch`). Both dispatch-start RPCs are replaced in full
-- to add a `PAYMENT_REQUIRED` guard; the cancellation/exhausted-search RPCs are replaced in full
-- to move an approved payment to `REFUND_PENDING` when no service happened. All Mercado Pago
-- network calls stay in the `mp-*` Edge Functions (service role, MP_ACCESS_TOKEN as a secret);
-- this migration never talks to Mercado Pago directly.
begin;

create table public.saved_cards (
  id uuid primary key default gen_random_uuid(),
  client_id uuid not null references auth.users(id) on delete cascade,
  mp_customer_id text not null,
  mp_card_id text not null,
  brand text not null,
  last_four_digits text not null check (last_four_digits ~ '^[0-9]{4}$'),
  created_at timestamptz not null default now(),
  constraint saved_cards_mp_card_id_unique unique (mp_card_id)
);
create index saved_cards_client_id_idx on public.saved_cards (client_id);

create table public.payments (
  id uuid primary key default gen_random_uuid(),
  -- Same id as service_searches.id (start_mobile_dispatch's p_search_id / start_pittec_dispatch's
  -- p_request_id) — one solicitation, at most one *live* payment (see the partial unique index
  -- below); older PENDING/REJECTED/EXPIRED attempts for the same request are kept for history.
  request_id uuid not null,
  client_id uuid not null references auth.users(id) on delete cascade,
  dispatch_kind text not null check (dispatch_kind in ('MOBILE_SEQUENTIAL', 'PITTEC_AUTO_SEQUENTIAL')),
  method text not null check (method in ('PIX', 'SAVED_CARD')),
  saved_card_id uuid references public.saved_cards(id) on delete set null,
  -- Denormalized at charge time so payment history/receipts survive the card being removed later
  -- (FR-007 edge case: removing a card must not affect payments already approved with it).
  card_brand text,
  card_last_four_digits text,
  amount numeric(10, 2) not null check (amount > 0),
  status text not null default 'PENDING' check (status in (
    'PENDING', 'APPROVED', 'REJECTED', 'EXPIRED', 'CANCELLED',
    'REFUND_PENDING', 'REFUNDED', 'REFUND_FAILED'
  )),
  mp_payment_id text,
  idempotency_key text not null,
  refund_attempts smallint not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  approved_at timestamptz,
  constraint payments_mp_payment_id_unique unique (mp_payment_id),
  constraint payments_idempotency_key_unique unique (idempotency_key)
);
create index payments_request_id_idx on public.payments (request_id);
-- FR-011/SC-006: at most one live (not yet finished) payment per solicitation at a time.
create unique index payments_one_live_per_request
  on public.payments (request_id)
  where status in ('PENDING', 'APPROVED', 'REFUND_PENDING');

-- ---------------------------------------------------------------------------------------------
-- Dispatch-start guard: no despacho without an approved payment (FR-009/FR-014).
-- ---------------------------------------------------------------------------------------------

-- Full body reproduced from 20260823070000_make_mobile_dispatch_start_idempotent.sql, with the
-- PAYMENT_REQUIRED check inserted right after the input validation.
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
       and status = 'APPROVED'
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

-- Full body reproduced from 20260902000000_pittec_on_demand.sql, with the same guard inserted
-- right after the input validation.
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

  if not exists (
    select 1 from public.payments
     where request_id = p_request_id
       and client_id = auth.uid()
       and status = 'APPROVED'
  ) then
    raise exception using errcode = 'P0001', message = 'PAYMENT_REQUIRED';
  end if;

  perform pg_advisory_xact_lock(hashtextextended(auth.uid()::text,1501));
  select search.id into existing_search_id
  from public.service_searches as search
  join public.services as service on service.id = search.service_id
  where search.client_id = auth.uid()
    and service.dispatch_kind = 'PITTEC_AUTO_SEQUENTIAL'
    and search.status in ('SEARCHING','AWAITING_PROVIDER','ACCEPTED','IN_PROGRESS')
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
    search_accuracy_m,current_radius_m,request_captured_at
  ) values (
    p_request_id,auth.uid(),p_service_id,'SEARCHING',p_latitude,p_longitude,
    extensions.st_setsrid(extensions.st_makepoint(p_longitude,p_latitude),4326)::extensions.geography,
    p_accuracy_meters,10000,p_captured_at
  );
  perform private.advance_pittec_dispatch(p_request_id);
  return jsonb_build_object('outcome','STARTED','snapshot',private.pittec_dispatch_snapshot(p_request_id));
end;
$$;

-- ---------------------------------------------------------------------------------------------
-- Automatic refund trigger: no service happened, so an approved payment must not stay charged
-- (FR-015/FR-016/FR-017). Marks the payment REFUND_PENDING; the app calls mp-refund-payment
-- immediately after these RPCs return (see features/payment), which performs the actual Mercado
-- Pago refund with its own retry/fallback (research.md §7, FR-018).
-- ---------------------------------------------------------------------------------------------

-- Full body reproduced from 20260824030000_cycle_dispatch_candidates_upsert.sql, with the
-- REFUND_PENDING transition inserted in the EXHAUSTED branch.
create or replace function private.advance_mobile_dispatch(p_search_id uuid)
returns jsonb language plpgsql security definer set search_path='' as $$
declare s public.service_searches%rowtype;
declare chosen record;
declare next_sequence integer;
declare candidate_id uuid;
declare radius integer;
declare last_sequence integer;
begin
  select * into s from public.service_searches where id=p_search_id for update;
  if s.id is null then raise exception using errcode='P0002', message='Search unavailable'; end if;
  if s.search_point is null then raise exception using errcode='22023', message='Search location unavailable'; end if;
  radius := s.current_radius_m;
  select max(sequence) into last_sequence from private.dispatch_candidates where search_id=s.id;
  loop
    select p.id provider_id, ps.id provider_service_id, p.name provider_name,
      concat_ws(' - ',nullif(btrim(ad.neighborhood),''),nullif(btrim(ad.city),''),nullif(btrim(ad.state),'')) locality,
      extensions.st_distance(l.position,s.search_point) distance_meters, ps.price, ps.is_free
    into chosen
    from public.provider_services ps
    join public.providers p on p.id=ps.provider_id
    join public.account_roles ar on ar.user_id=p.account_id and ar.account_type='PROVIDER'
    join public.provider_discovery_locations l on l.provider_id=p.id
    left join public.addresses ad on ad.provider_id=p.id
    where ps.service_id=s.service_id and ps.active and p.active and p.available
      and p.location_mode='MOBILE' and l.published_at >= now()-interval '30 seconds'
      and extensions.st_dwithin(l.position,s.search_point,radius)
      and not exists(
        select 1 from private.dispatch_candidates dc
        where dc.search_id=s.id and dc.provider_id=p.id and dc.sequence=last_sequence
      )
      and not exists(select 1 from public.appointments ap where ap.provider_id=p.id and ap.status in ('PENDING','ACCEPTED','IN_PROGRESS'))
    order by extensions.st_distance(l.position,s.search_point),p.id limit 1;
    exit when chosen.provider_id is not null or radius=50000;
    radius := case radius when 10000 then 25000 else 50000 end;
  end loop;

  if chosen.provider_id is null then
    update public.service_searches set status='EXHAUSTED',current_radius_m=50000,
      current_candidate_id=null,search_latitude=null,search_longitude=null,search_point=null,
      search_accuracy_m=null,revision=revision+1,updated_at=now(),ended_at=now() where id=s.id;
    update public.payments set status = 'REFUND_PENDING', updated_at = now()
      where request_id = s.id and status = 'APPROVED';
    return jsonb_build_object('outcome','EXHAUSTED');
  end if;
  select coalesce(max(sequence),0)+1 into next_sequence from private.dispatch_candidates where search_id=s.id;
  insert into private.dispatch_candidates(search_id,provider_id,provider_service_id,sequence,
    status,provider_name,locality,distance_meters,quoted_price,is_free,decided_at)
  values(s.id,chosen.provider_id,chosen.provider_service_id,next_sequence,'PRESENTED',
    chosen.provider_name,coalesce(nullif(chosen.locality,''),'Localidade não informada'),
    chosen.distance_meters,chosen.price,chosen.is_free,null)
  on conflict (search_id,provider_id) do update set
    sequence=excluded.sequence, status='PRESENTED', provider_service_id=excluded.provider_service_id,
    provider_name=excluded.provider_name, locality=excluded.locality,
    distance_meters=excluded.distance_meters, quoted_price=excluded.quoted_price,
    is_free=excluded.is_free, decided_at=null
  returning id into candidate_id;
  update public.service_searches set status='OPTION_FOUND',current_radius_m=radius,
    current_candidate_id=candidate_id,revision=revision+1,updated_at=now() where id=s.id;
  return jsonb_build_object('outcome','ADVANCED');
end;
$$;

-- Full body reproduced from 20260902000000_pittec_on_demand.sql, with the REFUND_PENDING
-- transition inserted in the NO_PROVIDER branch.
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
  if search_row.status not in ('SEARCHING','AWAITING_PROVIDER') then
    return private.pittec_dispatch_snapshot(search_row.id);
  end if;
  if search_row.search_point is null then
    raise exception using errcode = '22023', message = 'INVALID_LOCATION';
  end if;

  radius := greatest(10000,search_row.current_radius_m);
  loop
    select
      provider.id as provider_id,
      offer.id as provider_service_id,
      provider.name as provider_name,
      coalesce(
        nullif(concat_ws(' - ',nullif(btrim(client_address.city),''),nullif(btrim(client_address.state),'')),''),
        case radius when 10000 then 'Região de até 10 km' when 25000 then 'Região de até 25 km' else 'Região de até 50 km' end
      ) as locality,
      extensions.st_distance(location.position,search_row.search_point) as distance_meters,
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
      and provider.active and provider.available and provider.location_mode = 'PITTEC'
      and location.published_at >= now() - interval '30 seconds'
      and extensions.st_dwithin(location.position,search_row.search_point,radius)
      and not exists (
        select 1 from private.dispatch_candidates as prior
        where prior.search_id = search_row.id and prior.provider_id = provider.id
      )
      and not exists (
        select 1 from public.appointments as active_appointment
        where active_appointment.provider_id = provider.id
          and active_appointment.status in ('PENDING','ACCEPTED','IN_PROGRESS')
      )
    order by extensions.st_distance(location.position,search_row.search_point), provider.id
    limit 1;

    if chosen.provider_id is null then
      if radius = 10000 then radius := 25000; continue; end if;
      if radius = 25000 then radius := 50000; continue; end if;
      update public.service_searches
      set status = 'EXHAUSTED', current_radius_m = 50000,
          current_candidate_id = null, current_appointment_id = null,
          search_latitude = null, search_longitude = null, search_point = null,
          search_accuracy_m = null, request_captured_at = null,
          revision = revision + 1, updated_at = now(), ended_at = now()
      where id = search_row.id;
      update public.payments set status = 'REFUND_PENDING', updated_at = now()
        where request_id = search_row.id and status = 'APPROVED';
      return jsonb_build_object('outcome','NO_PROVIDER');
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
        service_id,provider_service_id,quoted_price,is_free,response_deadline,revision
      ) values (
        search_row.client_id,chosen.provider_id,'PENDING',search_row.id,candidate_uuid,
        search_row.service_id,chosen.provider_service_id,chosen.price,chosen.is_free,
        now() + interval '60 seconds',1
      ) returning id into appointment_uuid;
    exception when unique_violation then
      insert into private.dispatch_candidates(
        search_id,provider_id,provider_service_id,sequence,status,provider_name,
        locality,distance_meters,quoted_price,is_free,decided_at
      ) values (
        search_row.id,chosen.provider_id,chosen.provider_service_id,next_sequence,'INVALIDATED',
        chosen.provider_name,chosen.locality,chosen.distance_meters,chosen.price,chosen.is_free,now()
      ) on conflict (search_id,provider_id) do nothing;
      continue;
    end;

    update public.service_searches
    set status = 'AWAITING_PROVIDER', current_radius_m = radius,
        current_candidate_id = candidate_uuid, current_appointment_id = appointment_uuid,
        revision = revision + 1, updated_at = now()
    where id = search_row.id;
    return jsonb_build_object('outcome','OFFERED','appointment_id',appointment_uuid);
  end loop;
end;
$$;

-- Full body reproduced from 20260902000000_pittec_on_demand.sql, with the REFUND_PENDING
-- transition inserted right before the terminal CANCELLED update.
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
      'outcome','REVISION_CONFLICT',
      'request_id',p_request_id,
      'snapshot',private.pittec_dispatch_snapshot(search_row.id)
    );
  end if;
  if search_row.status not in ('SEARCHING','AWAITING_PROVIDER') then
    return jsonb_build_object(
      'outcome','INVALID_TRANSITION',
      'request_id',p_request_id,
      'snapshot',private.pittec_dispatch_snapshot(search_row.id)
    );
  end if;
  if search_row.current_appointment_id is not null then
    select appointment.* into appointment_row
    from public.appointments as appointment
    where appointment.id = search_row.current_appointment_id for update;
    if appointment_row.status = 'PENDING' then
      update public.appointments
      set status = 'CANCELLED', cancelled_by = 'CLIENT', cancelled_at = now(),
          ended_at = now(), revision = revision + 1, updated_at = now()
      where id = appointment_row.id;
      update private.dispatch_candidates
      set status = 'INVALIDATED', decided_at = now()
      where id = appointment_row.dispatch_candidate_id;
    end if;
  end if;
  update public.service_searches
  set status = 'CANCELLED', current_candidate_id = null, current_appointment_id = null,
      search_latitude = null, search_longitude = null, search_point = null,
      search_accuracy_m = null, request_captured_at = null,
      revision = revision + 1, updated_at = now(), ended_at = now()
  where id = search_row.id;
  update public.payments set status = 'REFUND_PENDING', updated_at = now()
    where request_id = search_row.id and status = 'APPROVED';
  return jsonb_build_object(
    'outcome','CANCELLED','request_id',p_request_id,
    'snapshot',private.pittec_dispatch_snapshot(search_row.id)
  );
end;
$$;

-- Full body reproduced from 20260902000000_pittec_on_demand.sql, with the REFUND_PENDING
-- transition inserted only in the client-cancel branch (FR-016: a provider cancelling just
-- resumes the search — no refund — and completion never reaches this function at all).
create or replace function public.cancel_mobile_appointment(
  p_appointment_id uuid,
  p_reason text,
  p_expected_revision bigint
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  appointment_row public.appointments%rowtype;
  owned_provider_uuid uuid;
  actor_value text;
  dispatch_kind_value text;
begin
  if nullif(btrim(p_reason),'') is null then
    raise exception using errcode = '22023', message = 'CANCELLATION_REASON_REQUIRED';
  end if;
  select provider.id into owned_provider_uuid
  from public.providers as provider where provider.account_id = auth.uid();
  select appointment.* into appointment_row
  from public.appointments as appointment where appointment.id = p_appointment_id for update;
  if appointment_row.id is null
    or (appointment_row.client_id <> auth.uid()
      and appointment_row.provider_id is distinct from owned_provider_uuid) then
    raise exception using errcode = '42501', message = 'PARTICIPANT_REQUIRED';
  end if;
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
  if appointment_row.status not in ('PENDING','ACCEPTED','IN_PROGRESS') then
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

  if actor_value = 'PROVIDER' then
    update private.dispatch_candidates
    set status = 'PROVIDER_CANCELLED', decided_at = now()
    where id = appointment_row.dispatch_candidate_id;
    update public.service_searches
    set status = 'SEARCHING', current_appointment_id = null, current_candidate_id = null,
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
      revision = revision + 1, updated_at = now(), ended_at = now()
  where id = appointment_row.dispatch_search_id;
  update public.payments set status = 'REFUND_PENDING', updated_at = now()
    where request_id = appointment_row.dispatch_search_id and status = 'APPROVED';
  perform private.apply_pending_provider_mode(appointment_row.provider_id);
  return case when dispatch_kind_value = 'PITTEC_AUTO_SEQUENTIAL'
    then private.pittec_dispatch_snapshot(appointment_row.dispatch_search_id)
    else private.dispatch_snapshot(appointment_row.dispatch_search_id) end;
end;
$$;

-- ---------------------------------------------------------------------------------------------
-- Client-facing read/write RPCs (contracts/payment-rpcs.md). Direct table access stays revoked;
-- everything else — INSERT/UPDATE of payments/saved_cards — is done by the mp-* Edge Functions
-- through the service role, same pattern as geocode-provider-address.
-- ---------------------------------------------------------------------------------------------

create or replace function public.get_payment_status(p_request_id uuid)
returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare row_data public.payments%rowtype;
begin
  select * into row_data
  from public.payments
  where request_id = p_request_id and client_id = auth.uid()
  order by created_at desc
  limit 1;
  if row_data.id is null then
    return jsonb_build_object('outcome', 'NOT_FOUND');
  end if;
  return jsonb_build_object(
    'outcome', 'FOUND',
    'payment_id', row_data.id,
    'status', row_data.status,
    'method', row_data.method
  );
end;
$$;

create or replace function public.list_saved_cards()
returns table (
  id uuid,
  brand text,
  last_four_digits text,
  created_at timestamptz
) language sql stable security definer set search_path = '' as $$
  select saved_card.id, saved_card.brand, saved_card.last_four_digits, saved_card.created_at
  from public.saved_cards as saved_card
  where saved_card.client_id = auth.uid()
  order by saved_card.created_at desc;
$$;

create or replace function public.remove_saved_card(p_saved_card_id uuid)
returns void language plpgsql security definer set search_path = '' as $$
begin
  delete from public.saved_cards
  where id = p_saved_card_id and client_id = auth.uid();
  if not found then
    raise exception using errcode = 'P0002', message = 'CARD_NOT_FOUND';
  end if;
end;
$$;

-- ---------------------------------------------------------------------------------------------
-- Realtime: private "payment:<request_id>" broadcast, same pattern as mobile-dispatch/pittec-route.
-- ---------------------------------------------------------------------------------------------

create or replace function private.broadcast_payment_change()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  perform realtime.send(
    jsonb_build_object('request_id', new.request_id, 'payment_id', new.id, 'status', new.status),
    'CHANGED', 'payment:' || new.request_id::text, true
  );
  return new;
end;
$$;

drop trigger if exists payment_status_broadcast on public.payments;
create trigger payment_status_broadcast after insert or update on public.payments
  for each row execute function private.broadcast_payment_change();

drop policy if exists payment_receive on realtime.messages;
create policy payment_receive on realtime.messages for select to authenticated using (
  extension = 'broadcast' and split_part(realtime.topic(), ':', 1) = 'payment' and exists (
    select 1 from public.payments as payment
    where payment.request_id::text = split_part(realtime.topic(), ':', 2)
      and payment.client_id = auth.uid()
  )
);

-- ---------------------------------------------------------------------------------------------
-- RLS and grants: same convention as service_searches/dispatch_candidates — RLS enabled, all
-- direct table privileges revoked, access only through the SECURITY DEFINER RPCs above (reads)
-- or the mp-* Edge Functions' service-role client (writes).
-- ---------------------------------------------------------------------------------------------

alter table public.payments enable row level security;
alter table public.saved_cards enable row level security;
revoke all on public.payments, public.saved_cards from public, anon, authenticated;

revoke all on function public.get_payment_status(uuid) from public, anon, authenticated;
revoke all on function public.list_saved_cards() from public, anon, authenticated;
revoke all on function public.remove_saved_card(uuid) from public, anon, authenticated;
grant execute on function public.get_payment_status(uuid) to authenticated;
grant execute on function public.list_saved_cards() to authenticated;
grant execute on function public.remove_saved_card(uuid) to authenticated;

notify pgrst, 'reload schema';

commit;
