-- PitTec on-demand discovery, automatic sequential dispatch and accepted-trip routing.
-- Forward-only: existing mobile/Guincho migrations remain immutable.
begin;

do $$
begin
  if to_regclass('public.providers') is null
    or to_regclass('public.services') is null
    or to_regclass('public.provider_services') is null
    or to_regclass('public.provider_discovery_locations') is null
    or to_regclass('public.service_searches') is null
    or to_regclass('private.dispatch_candidates') is null
    or to_regclass('public.appointments') is null
    or to_regclass('public.client_current_locations') is null
    or to_regclass('public.provider_current_locations') is null then
    raise exception 'PitTec prerequisites are missing';
  end if;
  if exists (
    select 1 from public.providers
    where location_mode is not null and location_mode not in ('FIXED','MOBILE','PITTEC')
  ) then
    raise exception 'Unknown provider location mode prevents PitTec migration';
  end if;
  if exists (
    select 1 from public.services
    where dispatch_kind not in ('BROWSE','MOBILE_SEQUENTIAL','PITTEC_AUTO_SEQUENTIAL')
  ) then
    raise exception 'Unknown service dispatch kind prevents PitTec migration';
  end if;
end
$$;

alter table public.services drop constraint if exists services_dispatch_kind_check;
alter table public.services add constraint services_dispatch_kind_check
  check (dispatch_kind in ('BROWSE','MOBILE_SEQUENTIAL','PITTEC_AUTO_SEQUENTIAL'));

alter table public.providers drop constraint if exists providers_location_mode_valid;
alter table public.providers add constraint providers_location_mode_valid
  check (location_mode in ('FIXED','MOBILE','PITTEC')) not valid;
alter table public.providers add column if not exists pending_location_mode text;
alter table public.providers drop constraint if exists providers_pending_location_mode_valid;
alter table public.providers add constraint providers_pending_location_mode_valid
  check (pending_location_mode is null or pending_location_mode in ('FIXED','MOBILE','PITTEC'));

alter table public.service_searches add column if not exists request_captured_at timestamptz;
alter table public.service_searches add column if not exists last_event text;
alter table public.service_searches add column if not exists last_event_at timestamptz;
alter table public.service_searches drop constraint if exists service_searches_last_event_valid;
alter table public.service_searches add constraint service_searches_last_event_valid check (
  last_event is null or last_event in ('PROVIDER_REJECTED','OFFER_EXPIRED','PROVIDER_CANCELLED')
);

alter table public.appointments add column if not exists arrived_at timestamptz;

create table if not exists public.appointment_route_snapshots (
  appointment_id uuid primary key references public.appointments(id) on delete cascade,
  provider_location_revision bigint not null check (provider_location_revision > 0),
  geometry jsonb not null,
  distance_meters integer not null check (distance_meters between 0 and 2000000),
  generated_at timestamptz not null default now(),
  provider text not null check (provider = btrim(provider) and provider <> '' and char_length(provider) <= 40),
  constraint appointment_route_geometry_check check (
    jsonb_typeof(geometry) = 'object'
    and geometry ->> 'type' = 'LineString'
    and jsonb_typeof(geometry -> 'coordinates') = 'array'
    and jsonb_array_length(geometry -> 'coordinates') between 2 and 10000
  )
);
alter table public.appointment_route_snapshots enable row level security;
revoke all on table public.appointment_route_snapshots from public, anon, authenticated;
grant select on table public.appointment_route_snapshots to authenticated;

drop policy if exists appointment_route_snapshots_select_participant
  on public.appointment_route_snapshots;
create policy appointment_route_snapshots_select_participant
on public.appointment_route_snapshots for select to authenticated using (
  exists (
    select 1
    from public.appointments as appointment
    left join public.providers as provider on provider.id = appointment.provider_id
    where appointment.id = appointment_route_snapshots.appointment_id
      and appointment.status in ('ACCEPTED','IN_PROGRESS')
      and (appointment.client_id = auth.uid() or provider.account_id = auth.uid())
  )
);

create index if not exists appointment_route_snapshots_generated_idx
  on public.appointment_route_snapshots(generated_at);
create index if not exists provider_pittec_eligibility_idx
  on public.providers(available, active, location_mode)
  where location_mode = 'PITTEC';

do $$
declare
  pittec_category_id uuid;
  pittec_service_count integer;
begin
  select count(*) into pittec_service_count
  from public.services where lower(btrim(name)) = 'pittec';
  if pittec_service_count > 1 then
    raise exception 'Catalog contains more than one PitTec service';
  end if;

  insert into public.service_categories(name,description,active)
  select 'PitTec','Atendimento técnico sob demanda',true
  where not exists (
    select 1 from public.service_categories where lower(btrim(name)) = 'pittec'
  );
  select id into pittec_category_id
  from public.service_categories where lower(btrim(name)) = 'pittec'
  order by created_at limit 1;

  if pittec_service_count = 0 then
    insert into public.services(category_id,name,description,active,dispatch_kind)
    values(
      pittec_category_id,
      'PitTec',
      'PitTec: seu Técnico sob demanda no local.',
      true,
      'PITTEC_AUTO_SEQUENTIAL'
    );
  else
    update public.services
    set category_id = pittec_category_id,
        description = 'PitTec: seu Técnico sob demanda no local.',
        dispatch_kind = 'PITTEC_AUTO_SEQUENTIAL',
        updated_at = now()
    where lower(btrim(name)) = 'pittec';
  end if;
end
$$;

create or replace function private.is_pittec_service(p_service_id uuid)
returns boolean language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from public.services as service
    join public.service_categories as category on category.id = service.category_id
    where service.id = p_service_id
      and service.active and category.active
      and service.dispatch_kind = 'PITTEC_AUTO_SEQUENTIAL'
  );
$$;

drop function if exists public.list_home_services();
create function public.list_home_services()
returns table(
  service_id uuid,
  name text,
  description text,
  category_name text,
  minimum_price numeric,
  dispatch_kind text
) language sql stable security definer set search_path = '' as $$
  select
    service.id,
    service.name,
    service.description,
    category.name,
    min(offer.price) filter (
      where offer.active and provider.active and provider.available
    ),
    service.dispatch_kind
  from public.services as service
  join public.service_categories as category on category.id = service.category_id
  left join public.provider_services as offer on offer.service_id = service.id
  left join public.providers as provider on provider.id = offer.provider_id
  where service.active and category.active
    and auth.uid() is not null and private.has_account_type('CLIENT')
  group by service.id, service.name, service.description, category.name, service.dispatch_kind
  order by service.name;
$$;

create or replace function public.list_nearby_pittecs(
  p_service_id uuid,
  p_latitude double precision,
  p_longitude double precision,
  p_accuracy_meters double precision,
  p_captured_at timestamptz,
  p_limit integer default 200
) returns table(
  marker_id text,
  latitude double precision,
  longitude double precision,
  accuracy_radius_meters integer
) language plpgsql stable security definer set search_path = '' as $$
declare
  center_point extensions.geography;
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
    or p_captured_at > now() + interval '10 seconds'
    or p_limit not between 1 and 200 then
    raise exception using errcode = '22023', message = 'INVALID_LOCATION';
  end if;
  center_point := extensions.st_setsrid(
    extensions.st_makepoint(p_longitude,p_latitude),4326
  )::extensions.geography;

  return query
  select
    substr(encode(extensions.digest(
      provider.id::text || ':' || date_trunc('hour',now())::text,
      'sha256'
    ),'hex'),1,24),
    extensions.st_y(extensions.st_snaptogrid(location.position::geometry,0.005))::double precision,
    extensions.st_x(extensions.st_snaptogrid(location.position::geometry,0.005))::double precision,
    500
  from public.provider_services as offer
  join public.providers as provider on provider.id = offer.provider_id
  join public.provider_discovery_locations as location on location.provider_id = provider.id
  where offer.service_id = p_service_id and offer.active
    and provider.active and provider.available and provider.location_mode = 'PITTEC'
    and location.published_at >= now() - interval '30 seconds'
    and extensions.st_dwithin(location.position,center_point,50000)
    and not exists (
      select 1 from public.appointments as active_appointment
      where active_appointment.provider_id = provider.id
        and active_appointment.status in ('PENDING','ACCEPTED','IN_PROGRESS')
    )
  order by extensions.st_distance(location.position,center_point), provider.id
  limit least(p_limit,200);
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
begin
  select search.* into search_row
  from public.service_searches as search where search.id = p_search_id;
  if search_row.id is null or not private.is_pittec_service(search_row.service_id) then
    return null;
  end if;
  if search_row.current_candidate_id is not null then
    select candidate.* into candidate_row from private.dispatch_candidates as candidate
    where candidate.id = search_row.current_candidate_id;
  end if;
  if search_row.current_appointment_id is not null then
    select appointment.* into appointment_row from public.appointments as appointment
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

  return jsonb_strip_nulls(jsonb_build_object(
    'search_id', search_row.id,
    'service_id', search_row.service_id,
    'service_name', service_display_name,
    'dispatch_kind', 'PITTEC_AUTO_SEQUENTIAL',
    'status', search_row.status,
    'radius_meters', search_row.current_radius_m,
    'revision', search_row.revision,
    'server_now', now(),
    'last_event', search_row.last_event,
    'last_event_at', search_row.last_event_at,
    'allowed_actions', case
      when caller_is_client and search_row.status in ('SEARCHING','AWAITING_PROVIDER')
        then jsonb_build_array('CANCEL')
      when caller_is_client and search_row.status in ('ACCEPTED','IN_PROGRESS')
        then jsonb_build_array('CANCEL')
      when caller_is_provider and appointment_row.status = 'PENDING'
        then jsonb_build_array('ACCEPT','DECLINE')
      when caller_is_provider and appointment_row.status = 'ACCEPTED'
        then jsonb_build_array('ARRIVE','CANCEL')
      when caller_is_provider and appointment_row.status = 'IN_PROGRESS'
        then jsonb_build_array('COMPLETE','CANCEL')
      else '[]'::jsonb
    end,
    'offer', case
      when caller_is_provider and appointment_row.status = 'PENDING' then jsonb_build_object(
        'approximate_distance_meters', round(candidate_row.distance_meters / 500) * 500,
        'region', candidate_row.locality
      ) else null
    end,
    'provider', case
      when search_row.status in ('ACCEPTED','IN_PROGRESS','COMPLETED','CANCELLED')
        and provider_display_name is not null then jsonb_build_object(
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
      'cancelled_by', appointment_row.cancelled_by
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
        and appointment.status in ('PENDING','ACCEPTED','IN_PROGRESS')
    );
end;
$$;

create or replace function private.expire_mobile_dispatch_appointment(p_appointment_id uuid)
returns void language plpgsql security definer set search_path = '' as $$
declare
  appointment_row public.appointments%rowtype;
  dispatch_kind_value text;
begin
  select appointment.* into appointment_row
  from public.appointments as appointment
  where appointment.id = p_appointment_id
    and appointment.status = 'PENDING'
    and appointment.response_deadline <= now()
  for update;
  if appointment_row.id is null then return; end if;

  select service.dispatch_kind into dispatch_kind_value
  from public.services as service where service.id = appointment_row.service_id;
  update public.appointments
  set status = 'EXPIRED', ended_at = now(), revision = revision + 1, updated_at = now()
  where id = appointment_row.id;
  perform private.apply_pending_provider_mode(appointment_row.provider_id);
  update private.dispatch_candidates
  set status = 'PROPOSAL_EXPIRED', decided_at = now()
  where id = appointment_row.dispatch_candidate_id;
  update public.service_searches
  set status = 'SEARCHING', current_appointment_id = null, current_candidate_id = null,
      last_event = case when dispatch_kind_value = 'PITTEC_AUTO_SEQUENTIAL'
        then 'OFFER_EXPIRED' else last_event end,
      last_event_at = case when dispatch_kind_value = 'PITTEC_AUTO_SEQUENTIAL'
        then now() else last_event_at end,
      revision = revision + 1, updated_at = now()
  where id = appointment_row.dispatch_search_id;
  if dispatch_kind_value = 'PITTEC_AUTO_SEQUENTIAL' then
    perform private.advance_pittec_dispatch(appointment_row.dispatch_search_id);
  else
    perform private.advance_mobile_dispatch(appointment_row.dispatch_search_id);
  end if;
end;
$$;

create or replace function public.get_my_active_mobile_dispatch()
returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  search_uuid uuid;
  pending_appointment_uuid uuid;
begin
  if private.dispatch_is_client() then
    select search.id, search.current_appointment_id
    into search_uuid, pending_appointment_uuid
    from public.service_searches as search
    join public.services as service on service.id = search.service_id
    where search.client_id = auth.uid()
      and service.dispatch_kind = 'MOBILE_SEQUENTIAL'
      and search.status in ('SEARCHING','OPTION_FOUND','AWAITING_PROVIDER','ACCEPTED','IN_PROGRESS')
    order by search.updated_at desc limit 1;
  elsif private.dispatch_is_provider() then
    select search.id, search.current_appointment_id
    into search_uuid, pending_appointment_uuid
    from public.service_searches as search
    join public.services as service on service.id = search.service_id
    join public.appointments as appointment on appointment.dispatch_search_id = search.id
    join public.providers as provider on provider.id = appointment.provider_id
    where provider.account_id = auth.uid()
      and service.dispatch_kind = 'MOBILE_SEQUENTIAL'
      and appointment.status in ('PENDING','ACCEPTED','IN_PROGRESS')
    order by appointment.updated_at desc limit 1;
  else
    raise exception using errcode = '42501', message = 'ACCOUNT_REQUIRED';
  end if;
  if search_uuid is null then
    return jsonb_build_object('outcome','NONE','server_now',now());
  end if;
  perform private.expire_mobile_dispatch_appointment(pending_appointment_uuid);
  return jsonb_build_object('outcome','ACTIVE','snapshot',private.dispatch_snapshot(search_uuid));
end;
$$;

create or replace function public.get_my_pending_mobile_dispatch()
returns jsonb language sql security definer set search_path = '' as $$
  select public.get_my_active_mobile_dispatch();
$$;

create or replace function public.get_my_active_pittec_dispatch()
returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  search_uuid uuid;
  pending_appointment_uuid uuid;
begin
  if private.dispatch_is_client() then
    select search.id, search.current_appointment_id
    into search_uuid, pending_appointment_uuid
    from public.service_searches as search
    join public.services as service on service.id = search.service_id
    where search.client_id = auth.uid()
      and service.dispatch_kind = 'PITTEC_AUTO_SEQUENTIAL'
      and search.status in ('SEARCHING','AWAITING_PROVIDER','ACCEPTED','IN_PROGRESS')
    order by search.updated_at desc limit 1;
  elsif private.dispatch_is_provider() then
    select search.id, search.current_appointment_id
    into search_uuid, pending_appointment_uuid
    from public.service_searches as search
    join public.services as service on service.id = search.service_id
    join public.appointments as appointment on appointment.dispatch_search_id = search.id
    join public.providers as provider on provider.id = appointment.provider_id
    where provider.account_id = auth.uid()
      and service.dispatch_kind = 'PITTEC_AUTO_SEQUENTIAL'
      and appointment.status in ('PENDING','ACCEPTED','IN_PROGRESS')
    order by appointment.updated_at desc limit 1;
  else
    raise exception using errcode = '42501', message = 'ACCOUNT_REQUIRED';
  end if;
  if search_uuid is null then
    return jsonb_build_object('outcome','NONE','server_now',now());
  end if;
  perform private.expire_mobile_dispatch_appointment(pending_appointment_uuid);
  return jsonb_build_object(
    'outcome','ACTIVE',
    'snapshot',private.pittec_dispatch_snapshot(search_uuid)
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
  return jsonb_build_object(
    'outcome','CANCELLED','request_id',p_request_id,
    'snapshot',private.pittec_dispatch_snapshot(search_row.id)
  );
end;
$$;

create or replace function public.respond_to_mobile_dispatch(
  p_appointment_id uuid,
  p_accept boolean,
  p_expected_revision bigint
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  appointment_row public.appointments%rowtype;
  owned_provider_uuid uuid;
  dispatch_kind_value text;
  search_row public.service_searches%rowtype;
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
    raise exception using errcode = 'P0002', message = 'REQUEST_UNAVAILABLE';
  end if;
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
  if appointment_row.status <> 'PENDING' then
    return case when dispatch_kind_value = 'PITTEC_AUTO_SEQUENTIAL'
      then private.pittec_dispatch_snapshot(appointment_row.dispatch_search_id)
      else private.dispatch_snapshot(appointment_row.dispatch_search_id) end;
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
    update public.appointments
    set status = 'ACCEPTED', accepted_at = now(), revision = revision + 1, updated_at = now()
    where id = appointment_row.id;
    update private.dispatch_candidates set status = 'ACCEPTED', decided_at = now()
    where id = appointment_row.dispatch_candidate_id;
    update public.service_searches
    set status = 'ACCEPTED', revision = revision + 1, updated_at = now()
    where id = appointment_row.dispatch_search_id;

    if dispatch_kind_value = 'PITTEC_AUTO_SEQUENTIAL' then
      select search.* into search_row from public.service_searches as search
      where search.id = appointment_row.dispatch_search_id;
      insert into public.client_current_locations(
        appointment_id,latitude,longitude,accuracy_m,captured_at,published_at
      ) values (
        appointment_row.id,search_row.search_latitude,search_row.search_longitude,
        search_row.search_accuracy_m,coalesce(search_row.request_captured_at,now()),now()
      ) on conflict (appointment_id) do update set
        latitude = excluded.latitude, longitude = excluded.longitude,
        accuracy_m = excluded.accuracy_m, captured_at = excluded.captured_at,
        published_at = excluded.published_at;
      delete from public.provider_discovery_locations
      where provider_id = appointment_row.provider_id;
      return private.pittec_dispatch_snapshot(appointment_row.dispatch_search_id);
    end if;
  else
    update public.appointments
    set status = 'PROVIDER_REJECTED', ended_at = now(), revision = revision + 1, updated_at = now()
    where id = appointment_row.id;
    perform private.apply_pending_provider_mode(appointment_row.provider_id);
    update private.dispatch_candidates
    set status = 'PROVIDER_REJECTED', decided_at = now()
    where id = appointment_row.dispatch_candidate_id;
    update public.service_searches
    set status = 'SEARCHING', current_appointment_id = null, current_candidate_id = null,
        last_event = case when dispatch_kind_value = 'PITTEC_AUTO_SEQUENTIAL'
          then 'PROVIDER_REJECTED' else last_event end,
        last_event_at = case when dispatch_kind_value = 'PITTEC_AUTO_SEQUENTIAL'
          then now() else last_event_at end,
        revision = revision + 1, updated_at = now()
    where id = appointment_row.dispatch_search_id;
    if dispatch_kind_value = 'PITTEC_AUTO_SEQUENTIAL' then
      perform private.advance_pittec_dispatch(appointment_row.dispatch_search_id);
    else
      perform private.advance_mobile_dispatch(appointment_row.dispatch_search_id);
    end if;
    return jsonb_build_object('outcome','DECLINED');
  end if;
  return private.dispatch_snapshot(appointment_row.dispatch_search_id);
end;
$$;

create or replace function public.arrive_pittec_appointment(
  p_request_id uuid,
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
  if appointment_row.id is null or not private.is_pittec_service(appointment_row.service_id) then
    raise exception using errcode = 'P0002', message = 'APPOINTMENT_UNAVAILABLE';
  end if;
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
  if appointment_row.status <> 'ACCEPTED' then
    return jsonb_build_object(
      'outcome','INVALID_TRANSITION','request_id',p_request_id,
      'snapshot',private.pittec_dispatch_snapshot(appointment_row.dispatch_search_id)
    );
  end if;
  update public.appointments
  set status = 'IN_PROGRESS', arrived_at = now(), revision = revision + 1, updated_at = now()
  where id = appointment_row.id;
  update public.service_searches
  set status = 'IN_PROGRESS', revision = revision + 1, updated_at = now()
  where id = appointment_row.dispatch_search_id;
  return jsonb_build_object(
    'outcome','ARRIVED','request_id',p_request_id,
    'snapshot',private.pittec_dispatch_snapshot(appointment_row.dispatch_search_id)
  );
end;
$$;

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
    if appointment_row.revision = p_expected_revision and appointment_row.status = 'ACCEPTED' then
      update public.appointments
      set status = 'IN_PROGRESS', arrived_at = now(), revision = revision + 1, updated_at = now()
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
  perform private.apply_pending_provider_mode(appointment_row.provider_id);
  return case when dispatch_kind_value = 'PITTEC_AUTO_SEQUENTIAL'
    then private.pittec_dispatch_snapshot(appointment_row.dispatch_search_id)
    else private.dispatch_snapshot(appointment_row.dispatch_search_id) end;
end;
$$;

create or replace function public.complete_mobile_appointment(
  p_appointment_id uuid,
  p_expected_revision bigint
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  appointment_row public.appointments%rowtype;
  owned_provider_uuid uuid;
  dispatch_kind_value text;
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
  if dispatch_kind_value = 'PITTEC_AUTO_SEQUENTIAL' and appointment_row.status <> 'IN_PROGRESS' then
    return jsonb_build_object(
      'outcome','INVALID_TRANSITION',
      'snapshot',private.pittec_dispatch_snapshot(appointment_row.dispatch_search_id)
    );
  end if;
  if dispatch_kind_value <> 'PITTEC_AUTO_SEQUENTIAL'
    and appointment_row.status not in ('ACCEPTED','IN_PROGRESS') then
    return private.dispatch_snapshot(appointment_row.dispatch_search_id);
  end if;
  update public.appointments
  set status = 'COMPLETED', ended_at = now(), revision = revision + 1, updated_at = now()
  where id = appointment_row.id;
  update public.service_searches
  set status = 'COMPLETED', search_latitude = null, search_longitude = null,
      search_point = null, search_accuracy_m = null, request_captured_at = null,
      revision = revision + 1, updated_at = now(), ended_at = now()
  where id = appointment_row.dispatch_search_id;
  delete from public.appointment_route_snapshots where appointment_id = appointment_row.id;
  perform private.apply_pending_provider_mode(appointment_row.provider_id);
  return case when dispatch_kind_value = 'PITTEC_AUTO_SEQUENTIAL'
    then private.pittec_dispatch_snapshot(appointment_row.dispatch_search_id)
    else private.dispatch_snapshot(appointment_row.dispatch_search_id) end;
end;
$$;

create or replace function public.get_pittec_route_context(p_appointment_id uuid)
returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare
  appointment_row public.appointments%rowtype;
  provider_account_id uuid;
  provider_location public.provider_current_locations%rowtype;
  client_location public.client_current_locations%rowtype;
begin
  select appointment.* into appointment_row
  from public.appointments as appointment where appointment.id = p_appointment_id;
  select provider.account_id into provider_account_id
  from public.providers as provider where provider.id = appointment_row.provider_id;
  if appointment_row.id is null or not private.is_pittec_service(appointment_row.service_id)
    or appointment_row.status not in ('ACCEPTED','IN_PROGRESS') then
    raise exception using errcode = '22023', message = 'APPOINTMENT_NOT_TRACKABLE';
  end if;
  if auth.uid() <> appointment_row.client_id and auth.uid() is distinct from provider_account_id then
    raise exception using errcode = '42501', message = 'NOT_PARTICIPANT';
  end if;
  select location.* into provider_location
  from public.provider_current_locations as location
  where location.appointment_id = appointment_row.id;
  select location.* into client_location
  from public.client_current_locations as location
  where location.appointment_id = appointment_row.id;
  if provider_location.appointment_id is null or client_location.appointment_id is null then
    return jsonb_build_object('outcome','LOCATION_NOT_READY');
  end if;
  return jsonb_build_object(
    'outcome','READY',
    'appointment_id',appointment_row.id,
    'provider_location_revision',provider_location.version,
    'provider',jsonb_build_object(
      'latitude',provider_location.latitude,'longitude',provider_location.longitude,
      'captured_at',provider_location.captured_at
    ),
    'client',jsonb_build_object(
      'latitude',client_location.latitude,'longitude',client_location.longitude,
      'captured_at',client_location.captured_at
    )
  );
end;
$$;

create or replace function public.get_pittec_route_snapshot(p_appointment_id uuid)
returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare
  context_value jsonb;
  route_row public.appointment_route_snapshots%rowtype;
begin
  context_value := public.get_pittec_route_context(p_appointment_id);
  if context_value ->> 'outcome' <> 'READY' then return context_value; end if;
  select route.* into route_row from public.appointment_route_snapshots as route
  where route.appointment_id = p_appointment_id;
  if route_row.appointment_id is null then
    return jsonb_build_object('outcome','NOT_FOUND');
  end if;
  return jsonb_build_object(
    'outcome','FOUND','status','ROUTE','appointment_id',route_row.appointment_id,
    'provider_location_revision',route_row.provider_location_revision,
    'geometry',route_row.geometry,'distance_meters',route_row.distance_meters,
    'generated_at',route_row.generated_at
  );
end;
$$;

create or replace function public.upsert_pittec_route_snapshot(
  p_appointment_id uuid,
  p_provider_location_revision bigint,
  p_geometry jsonb,
  p_distance_meters integer,
  p_provider text
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  route_row public.appointment_route_snapshots%rowtype;
begin
  if auth.role() <> 'service_role' then
    raise exception using errcode = '42501', message = 'SERVICE_ROLE_REQUIRED';
  end if;
  if p_provider_location_revision <= 0 or p_distance_meters not between 0 and 2000000
    or jsonb_typeof(p_geometry) <> 'object'
    or p_geometry ->> 'type' <> 'LineString'
    or jsonb_typeof(p_geometry -> 'coordinates') <> 'array'
    or jsonb_array_length(p_geometry -> 'coordinates') not between 2 and 10000
    or nullif(btrim(p_provider),'') is null then
    raise exception using errcode = '22023', message = 'INVALID_ROUTE';
  end if;
  if not exists (
    select 1 from public.appointments as appointment
    where appointment.id = p_appointment_id
      and appointment.status in ('ACCEPTED','IN_PROGRESS')
      and private.is_pittec_service(appointment.service_id)
  ) then
    raise exception using errcode = '22023', message = 'APPOINTMENT_NOT_TRACKABLE';
  end if;
  insert into public.appointment_route_snapshots(
    appointment_id,provider_location_revision,geometry,distance_meters,generated_at,provider
  ) values (
    p_appointment_id,p_provider_location_revision,p_geometry,p_distance_meters,now(),btrim(p_provider)
  ) on conflict (appointment_id) do update set
    provider_location_revision = excluded.provider_location_revision,
    geometry = excluded.geometry,
    distance_meters = excluded.distance_meters,
    generated_at = excluded.generated_at,
    provider = excluded.provider
  where excluded.provider_location_revision >= public.appointment_route_snapshots.provider_location_revision
  returning * into route_row;
  if route_row.appointment_id is null then
    select route.* into route_row from public.appointment_route_snapshots as route
    where route.appointment_id = p_appointment_id;
  end if;
  return jsonb_build_object(
    'outcome','UPSERTED','appointment_id',route_row.appointment_id,
    'provider_location_revision',route_row.provider_location_revision,
    'geometry',route_row.geometry,'distance_meters',route_row.distance_meters,
    'generated_at',route_row.generated_at
  );
end;
$$;

create or replace function public.publish_mobile_provider_location(
  p_latitude double precision,
  p_longitude double precision,
  p_accuracy_m double precision,
  p_captured_at timestamptz,
  p_tracker_instance_id uuid,
  p_tracker_sequence bigint
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  owned_provider public.providers%rowtype;
  discovery_location public.provider_discovery_locations%rowtype;
  appointment_location public.provider_current_locations%rowtype;
  active_appointment_uuid uuid;
  current_version bigint;
  current_instance uuid;
  current_sequence bigint;
  current_published_at timestamptz;
  current_captured_at timestamptz;
  next_version bigint;
begin
  if not private.dispatch_is_provider() then
    raise exception using errcode = '42501', message = 'PROVIDER_REQUIRED';
  end if;
  select provider.* into owned_provider
  from public.providers as provider where provider.account_id = auth.uid() for update;
  if owned_provider.id is null or not owned_provider.active or not owned_provider.available
    or owned_provider.location_mode not in ('MOBILE','PITTEC') then
    raise exception using errcode = '22023', message = 'PROVIDER_INELIGIBLE';
  end if;
  if p_latitude not between -90 and 90 or p_longitude not between -180 and 180
    or p_accuracy_m <= 0 or p_accuracy_m > 100
    or p_captured_at < now() - interval '30 seconds'
    or p_captured_at > now() + interval '10 seconds'
    or p_tracker_sequence <= 0 then
    raise exception using errcode = '22023', message = 'INVALID_LOCATION';
  end if;

  select appointment.id into active_appointment_uuid
  from public.appointments as appointment
  where appointment.provider_id = owned_provider.id
    and appointment.status in ('ACCEPTED','IN_PROGRESS')
  order by appointment.updated_at desc limit 1;

  if active_appointment_uuid is not null then
    select location.* into appointment_location
    from public.provider_current_locations as location
    where location.appointment_id = active_appointment_uuid for update;
    current_version := appointment_location.version;
    current_instance := appointment_location.tracker_instance_id;
    current_sequence := appointment_location.tracker_sequence;
    current_published_at := appointment_location.published_at;
    current_captured_at := appointment_location.captured_at;
  else
    select location.* into discovery_location
    from public.provider_discovery_locations as location
    where location.provider_id = owned_provider.id for update;
    current_version := discovery_location.version;
    current_instance := discovery_location.tracker_instance_id;
    current_sequence := discovery_location.tracker_sequence;
    current_published_at := discovery_location.published_at;
    current_captured_at := discovery_location.captured_at;
  end if;

  if current_instance is not null and current_instance <> p_tracker_instance_id
    and current_published_at >= now() - interval '30 seconds' then
    return jsonb_build_object('outcome','PUBLISHER_CONFLICT','version',current_version);
  end if;
  if current_instance = p_tracker_instance_id
    and (p_tracker_sequence <= current_sequence or p_captured_at < current_captured_at) then
    return jsonb_build_object('outcome','IGNORED','version',current_version);
  end if;
  next_version := coalesce(current_version,0) + 1;

  if active_appointment_uuid is not null then
    insert into public.provider_current_locations(
      appointment_id,latitude,longitude,accuracy_m,captured_at,published_at,version,
      tracker_instance_id,tracker_sequence
    ) values (
      active_appointment_uuid,p_latitude,p_longitude,p_accuracy_m,p_captured_at,now(),
      next_version,p_tracker_instance_id,p_tracker_sequence
    ) on conflict (appointment_id) do update set
      latitude = excluded.latitude, longitude = excluded.longitude,
      accuracy_m = excluded.accuracy_m, captured_at = excluded.captured_at,
      published_at = excluded.published_at, version = excluded.version,
      tracker_instance_id = excluded.tracker_instance_id,
      tracker_sequence = excluded.tracker_sequence;
    if owned_provider.location_mode = 'PITTEC' then
      delete from public.provider_discovery_locations where provider_id = owned_provider.id;
    end if;
  end if;

  if active_appointment_uuid is null or owned_provider.location_mode = 'MOBILE' then
    insert into public.provider_discovery_locations(
      provider_id,latitude,longitude,position,accuracy_m,captured_at,published_at,version,
      tracker_instance_id,tracker_sequence
    ) values (
      owned_provider.id,p_latitude,p_longitude,
      extensions.st_setsrid(extensions.st_makepoint(p_longitude,p_latitude),4326)::extensions.geography,
      p_accuracy_m,p_captured_at,now(),next_version,p_tracker_instance_id,p_tracker_sequence
    ) on conflict (provider_id) do update set
      latitude = excluded.latitude, longitude = excluded.longitude, position = excluded.position,
      accuracy_m = excluded.accuracy_m, captured_at = excluded.captured_at,
      published_at = excluded.published_at, version = excluded.version,
      tracker_instance_id = excluded.tracker_instance_id,
      tracker_sequence = excluded.tracker_sequence;
  end if;

  return jsonb_build_object(
    'outcome','PUBLISHED','version',next_version,
    'target',case when active_appointment_uuid is null then 'DISCOVERY' else 'APPOINTMENT' end,
    'published_at',now()
  );
end;
$$;

create or replace function public.stop_mobile_provider_location(p_tracker_instance_id uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  owned_provider_uuid uuid;
  removed integer := 0;
  current_removed integer := 0;
begin
  if not private.dispatch_is_provider() then
    raise exception using errcode = '42501', message = 'PROVIDER_REQUIRED';
  end if;
  select provider.id into owned_provider_uuid
  from public.providers as provider where provider.account_id = auth.uid();
  delete from public.provider_discovery_locations as location
  where location.provider_id = owned_provider_uuid
    and location.tracker_instance_id = p_tracker_instance_id;
  get diagnostics removed = row_count;
  delete from public.provider_current_locations as location
  using public.appointments as appointment
  where location.appointment_id = appointment.id
    and appointment.provider_id = owned_provider_uuid
    and location.tracker_instance_id = p_tracker_instance_id;
  get diagnostics current_removed = row_count;
  return jsonb_build_object(
    'outcome',case when removed + current_removed > 0 then 'STOPPED' else 'IGNORED' end
  );
end;
$$;

create or replace function public.publish_provider_location(
  p_appointment_id uuid,
  p_latitude double precision,
  p_longitude double precision,
  p_accuracy_m double precision,
  p_captured_at timestamptz,
  p_tracker_instance_id uuid,
  p_tracker_sequence bigint
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  owned_provider public.providers%rowtype;
  appointment_row public.appointments%rowtype;
  current_location public.provider_current_locations%rowtype;
  next_version bigint;
begin
  if not private.dispatch_is_provider() then
    raise exception using errcode = '42501', message = 'PROVIDER_REQUIRED';
  end if;
  select provider.* into owned_provider
  from public.providers as provider where provider.account_id = auth.uid() for update;
  if owned_provider.id is null or not owned_provider.active or not owned_provider.available
    or owned_provider.location_mode not in ('MOBILE','PITTEC') then
    raise exception using errcode = '42501', message = 'PROVIDER_INELIGIBLE';
  end if;
  select appointment.* into appointment_row
  from public.appointments as appointment
  where appointment.id = p_appointment_id and appointment.provider_id = owned_provider.id
    and appointment.status in ('ACCEPTED','IN_PROGRESS') for update;
  if appointment_row.id is null then
    raise exception using errcode = '42501', message = 'ACTIVE_APPOINTMENT_REQUIRED';
  end if;
  if p_latitude not between -90 and 90 or p_longitude not between -180 and 180
    or p_accuracy_m <= 0 or p_accuracy_m > 100
    or p_captured_at < now() - interval '30 seconds'
    or p_captured_at > now() + interval '10 seconds'
    or p_tracker_sequence <= 0 then
    raise exception using errcode = '22023', message = 'INVALID_LOCATION';
  end if;
  select location.* into current_location
  from public.provider_current_locations as location
  where location.appointment_id = p_appointment_id for update;
  if current_location.appointment_id is not null
    and current_location.tracker_instance_id <> p_tracker_instance_id
    and current_location.published_at >= now() - interval '30 seconds' then
    return jsonb_build_object('outcome','PUBLISHER_CONFLICT','version',current_location.version);
  end if;
  if current_location.tracker_instance_id = p_tracker_instance_id
    and (p_tracker_sequence <= current_location.tracker_sequence
      or p_captured_at < current_location.captured_at) then
    return jsonb_build_object('outcome','IGNORED','version',current_location.version);
  end if;
  next_version := coalesce(current_location.version,0) + 1;
  insert into public.provider_current_locations(
    appointment_id,latitude,longitude,accuracy_m,captured_at,published_at,version,
    tracker_instance_id,tracker_sequence
  ) values (
    p_appointment_id,p_latitude,p_longitude,p_accuracy_m,p_captured_at,now(),next_version,
    p_tracker_instance_id,p_tracker_sequence
  ) on conflict (appointment_id) do update set
    latitude = excluded.latitude, longitude = excluded.longitude,
    accuracy_m = excluded.accuracy_m, captured_at = excluded.captured_at,
    published_at = excluded.published_at, version = excluded.version,
    tracker_instance_id = excluded.tracker_instance_id,
    tracker_sequence = excluded.tracker_sequence;
  if owned_provider.location_mode = 'PITTEC' then
    delete from public.provider_discovery_locations where provider_id = owned_provider.id;
  end if;
  return jsonb_build_object('outcome','PUBLISHED','version',next_version);
end;
$$;

create or replace function private.clear_provider_discovery_location()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  if not new.active or not new.available or new.location_mode not in ('MOBILE','PITTEC') then
    delete from public.provider_discovery_locations where provider_id = new.id;
  end if;
  return new;
end;
$$;

create or replace function private.clear_ineligible_provider_location()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  if tg_table_name = 'appointments' and new.status not in ('ACCEPTED','IN_PROGRESS') then
    delete from public.provider_current_locations where appointment_id = new.id;
  elsif tg_table_name = 'providers'
    and (not new.active or not new.available or new.location_mode not in ('MOBILE','PITTEC')) then
    delete from public.provider_current_locations as location
    using public.appointments as appointment
    where location.appointment_id = appointment.id and appointment.provider_id = new.id;
  end if;
  return new;
end;
$$;

create or replace function public.get_appointment_client_location(p_appointment_id uuid)
returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare
  location_row public.client_current_locations%rowtype;
begin
  if not exists (
    select 1 from public.appointments as appointment
    left join public.providers as provider on provider.id = appointment.provider_id
    where appointment.id = p_appointment_id
      and appointment.status in ('ACCEPTED','IN_PROGRESS')
      and (appointment.client_id = auth.uid() or provider.account_id = auth.uid())
  ) then
    raise exception using errcode = '42501', message = 'PARTICIPANT_REQUIRED';
  end if;
  select location.* into location_row from public.client_current_locations as location
  where location.appointment_id = p_appointment_id;
  if location_row.appointment_id is null then
    return jsonb_build_object('outcome','NOT_FOUND');
  end if;
  return jsonb_build_object(
    'outcome','FOUND','location',jsonb_build_object(
      'latitude',location_row.latitude,'longitude',location_row.longitude,
      'accuracy_m',location_row.accuracy_m,'captured_at',location_row.captured_at,
      'published_at',location_row.published_at
    )
  );
end;
$$;

create or replace function public.set_provider_availability(
  p_available boolean,
  p_location_mode text default null
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  provider_row public.providers%rowtype;
  requested_mode text := nullif(upper(btrim(coalesce(p_location_mode,''))), '');
  has_active_appointment boolean;
begin
  if auth.uid() is null or not private.has_account_type('PROVIDER') then
    raise exception using errcode = '42501', message = 'PROVIDER_REQUIRED';
  end if;
  if requested_mode is not null and requested_mode not in ('FIXED','MOBILE','PITTEC') then
    raise exception using errcode = '22023', message = 'INVALID_PROVIDER_MODE';
  end if;
  select provider.* into provider_row
  from public.providers as provider where provider.account_id = auth.uid() for update;
  if provider_row.id is null then
    raise exception using errcode = 'P0002', message = 'PROVIDER_UNAVAILABLE';
  end if;
  select exists (
    select 1 from public.appointments as appointment
    where appointment.provider_id = provider_row.id
      and appointment.status in ('PENDING','ACCEPTED','IN_PROGRESS')
  ) into has_active_appointment;
  if has_active_appointment and not p_available then
    raise exception using errcode = '22023', message = 'ACTIVE_APPOINTMENT_REQUIRED';
  end if;
  if p_available and coalesce(requested_mode,provider_row.location_mode) = 'PITTEC'
    and not exists (
      select 1 from public.provider_services as offer
      join public.services as service on service.id = offer.service_id
      where offer.provider_id = provider_row.id and offer.active and service.active
        and service.dispatch_kind = 'PITTEC_AUTO_SEQUENTIAL'
    ) then
    raise exception using errcode = '22023', message = 'PITTEC_OFFER_REQUIRED';
  end if;
  update public.providers
  set available = p_available,
      location_mode = case when has_active_appointment then location_mode
        else coalesce(requested_mode,location_mode) end,
      pending_location_mode = case
        when has_active_appointment and requested_mode is distinct from location_mode
          then requested_mode
        when not has_active_appointment then null
        else pending_location_mode end,
      updated_at = now()
  where id = provider_row.id
  returning * into provider_row;
  return jsonb_build_object(
    'provider_id',provider_row.id,'available',provider_row.available,
    'location_mode',provider_row.location_mode,
    'pending_location_mode',provider_row.pending_location_mode
  );
end;
$$;

create or replace function public.create_provider_registration(
  p_provider jsonb,
  p_address jsonb,
  p_offers jsonb
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  current_user_id uuid := auth.uid();
  new_provider_id uuid := gen_random_uuid();
  proof private.provider_geocode_proofs%rowtype;
  proof_token text := p_address ->> 'verification_token';
  provider_mode text := upper(coalesce(p_provider ->> 'location_mode',''));
  normalized_neighborhood text := btrim(coalesce(
    p_address ->> 'neighborhood', p_address ->> 'neighbornhood', ''
  ));
  offer_count integer;
begin
  if current_user_id is null or not private.has_account_type('PROVIDER') then
    raise exception using errcode = '42501', message = 'PROVIDER_REQUIRED';
  end if;
  perform pg_advisory_xact_lock(hashtextextended(current_user_id::text,9010));
  if exists (select 1 from public.providers where account_id = current_user_id) then
    select count(*) into offer_count
    from public.provider_services as offer
    join public.providers as provider on provider.id = offer.provider_id
    where provider.account_id = current_user_id;
    return jsonb_build_object('outcome','ALREADY_REGISTERED','offer_count',offer_count);
  end if;
  if provider_mode not in ('FIXED','MOBILE','PITTEC') then
    raise exception using errcode = '22023', message = 'INVALID_PROVIDER_MODE';
  end if;
  if proof_token is null or proof_token !~ '^[A-Za-z0-9_-]{43,128}$' then
    raise exception using errcode = '22023', message = 'LOCATION_VERIFICATION_REQUIRED';
  end if;
  select candidate.* into proof
  from private.provider_geocode_proofs as candidate
  where candidate.account_id = current_user_id
    and candidate.token_hash = extensions.digest(proof_token,'sha256')
  for update;
  if proof.id is null or proof.used_at is not null or proof.expires_at <= now()
    or (proof.device_distance_m > 500 and not proof.mismatch_confirmed) then
    raise exception using errcode = '22023', message = 'LOCATION_VERIFICATION_UNAVAILABLE';
  end if;
  if normalized_neighborhood = '' then
    raise exception using errcode = '22023', message = 'INVALID_ADDRESS';
  end if;
  if p_offers is null or jsonb_typeof(p_offers) <> 'array'
    or jsonb_array_length(p_offers) = 0 then
    raise exception using errcode = '22023', message = 'OFFER_REQUIRED';
  end if;
  if (select count(*) <> count(distinct (item ->> 'service_id')::uuid)
      from jsonb_array_elements(p_offers) as item) then
    raise exception using errcode = '22023', message = 'DUPLICATE_SERVICE';
  end if;
  if exists (
    select 1
    from jsonb_array_elements(p_offers) as item
    left join public.services as service on service.id = (item ->> 'service_id')::uuid
    left join public.service_categories as category on category.id = service.category_id
    where service.id is null or not service.active or category.id is null or not category.active
      or coalesce(item ->> 'duration_minutes','') !~ '^[0-9]+$'
      or (item ->> 'duration_minutes')::integer not between 1 and 10080
      or coalesce(item ->> 'price','') !~ '^[0-9]+(\.[0-9]{1,2})?$'
      or (coalesce((item ->> 'is_free')::boolean,false)
        and (item ->> 'price')::numeric <> 0)
      or (not coalesce((item ->> 'is_free')::boolean,false)
        and (item ->> 'price')::numeric <= 0)
  ) then
    raise exception using errcode = '22023', message = 'INVALID_OFFER';
  end if;

  insert into public.providers(
    id,account_id,name,description,email,phone,cnpj,location_mode,active,available
  ) values (
    new_provider_id,current_user_id,
    btrim(coalesce(p_provider ->> 'name','')),
    nullif(btrim(coalesce(p_provider ->> 'description','')),''),
    lower(btrim(coalesce(p_provider ->> 'email',''))),
    regexp_replace(coalesce(p_provider ->> 'phone',''),'[^0-9]','','g'),
    regexp_replace(upper(coalesce(p_provider ->> 'cnpj','')),'[^0-9A-Z]','','g'),
    provider_mode,true,false
  );
  insert into public.addresses(
    provider_id,street,number,neighborhood,zip_code,city,state,country,complement,
    latitude,longitude,address_point,geocode_place_id,geocoded_at,geocode_expires_at,
    location_verified_at,verification_distance_m,mismatch_confirmed
  ) values (
    new_provider_id,
    btrim(coalesce(p_address ->> 'street','')),
    btrim(coalesce(p_address ->> 'number','')),
    normalized_neighborhood,
    regexp_replace(coalesce(p_address ->> 'zip_code',''),'[^0-9]','','g'),
    btrim(coalesce(p_address ->> 'city','')),
    upper(btrim(coalesce(p_address ->> 'state',''))),'BR',
    nullif(btrim(coalesce(p_address ->> 'complement','')),''),
    proof.latitude,proof.longitude,
    extensions.st_setsrid(extensions.st_makepoint(proof.longitude,proof.latitude),4326)::extensions.geography,
    proof.place_id,proof.geocoded_at,proof.geocoded_at + interval '30 days',now(),
    proof.device_distance_m,proof.mismatch_confirmed
  );
  insert into public.provider_services(
    provider_id,service_id,price,duration_minutes,description,is_free
  ) select
    new_provider_id,(item ->> 'service_id')::uuid,(item ->> 'price')::numeric,
    (item ->> 'duration_minutes')::integer,
    nullif(btrim(coalesce(item ->> 'description','')),''),
    coalesce((item ->> 'is_free')::boolean,false)
  from jsonb_array_elements(p_offers) as item;
  insert into public.provider_categories(provider_id,category_id)
  select distinct new_provider_id, service.category_id
  from jsonb_array_elements(p_offers) as item
  join public.services as service on service.id = (item ->> 'service_id')::uuid;
  update private.provider_geocode_proofs set used_at = now() where id = proof.id;
  return jsonb_build_object(
    'outcome','CREATED','provider_id',new_provider_id,
    'offer_count',jsonb_array_length(p_offers)
  );
end;
$$;

create or replace function private.clear_pittec_route_snapshot()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  if new.status not in ('ACCEPTED','IN_PROGRESS') then
    delete from public.appointment_route_snapshots where appointment_id = new.id;
  end if;
  return new;
end;
$$;
drop trigger if exists pittec_route_snapshot_cleanup on public.appointments;
create trigger pittec_route_snapshot_cleanup
after update of status on public.appointments
for each row execute function private.clear_pittec_route_snapshot();

create or replace function private.broadcast_pittec_route_change()
returns trigger language plpgsql security definer set search_path = '' as $$
declare
  appointment_uuid uuid := coalesce(new.appointment_id,old.appointment_id);
  route_revision bigint := coalesce(new.provider_location_revision,old.provider_location_revision);
begin
  perform realtime.send(
    jsonb_build_object(
      'appointment_id',appointment_uuid,
      'revision',route_revision,
      'state',case when tg_op = 'DELETE' then 'DELETED' else 'AVAILABLE' end
    ),
    'ROUTE_INVALIDATED',
    'appointment-route:' || appointment_uuid::text,
    true
  );
  return coalesce(new,old);
end;
$$;
drop trigger if exists pittec_route_snapshot_broadcast on public.appointment_route_snapshots;
create trigger pittec_route_snapshot_broadcast
after insert or update or delete on public.appointment_route_snapshots
for each row execute function private.broadcast_pittec_route_change();

drop policy if exists pittec_route_receive on realtime.messages;
create policy pittec_route_receive on realtime.messages for select to authenticated using (
  extension = 'broadcast'
  and split_part(realtime.topic(),':',1) = 'appointment-route'
  and exists (
    select 1 from public.appointments as appointment
    left join public.providers as provider on provider.id = appointment.provider_id
    where appointment.id::text = split_part(realtime.topic(),':',2)
      and appointment.status in ('ACCEPTED','IN_PROGRESS')
      and (appointment.client_id = auth.uid() or provider.account_id = auth.uid())
  )
);

revoke all on function private.is_pittec_service(uuid) from public,anon,authenticated;
revoke all on function private.pittec_dispatch_snapshot(uuid) from public,anon,authenticated;
revoke all on function private.advance_pittec_dispatch(uuid) from public,anon,authenticated;
revoke all on function private.apply_pending_provider_mode(uuid) from public,anon,authenticated;
revoke all on function private.clear_pittec_route_snapshot() from public,anon,authenticated;
revoke all on function private.broadcast_pittec_route_change() from public,anon,authenticated;

do $$
declare signature regprocedure;
begin
  for signature in
    select procedure.oid::regprocedure
    from pg_proc as procedure
    join pg_namespace as namespace on namespace.oid = procedure.pronamespace
    where namespace.nspname = 'public' and procedure.proname in (
      'list_home_services','list_nearby_pittecs','start_pittec_dispatch',
      'get_my_active_pittec_dispatch','cancel_pittec_search',
      'respond_to_mobile_dispatch','arrive_pittec_appointment',
      'start_mobile_appointment','cancel_mobile_appointment','complete_mobile_appointment',
      'publish_mobile_provider_location','stop_mobile_provider_location',
      'publish_provider_location','get_appointment_client_location',
      'set_provider_availability','create_provider_registration',
      'get_pittec_route_context','get_pittec_route_snapshot'
    )
  loop
    execute format('revoke all on function %s from public, anon, authenticated',signature);
    execute format('grant execute on function %s to authenticated',signature);
  end loop;
end
$$;
revoke all on function public.upsert_pittec_route_snapshot(uuid,bigint,jsonb,integer,text)
  from public,anon,authenticated;
grant execute on function public.upsert_pittec_route_snapshot(uuid,bigint,jsonb,integer,text)
  to service_role;

notify pgrst,'reload schema';
commit;
