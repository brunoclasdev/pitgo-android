-- Resolve PL/pgSQL variable/column ambiguities reported by plpgsql_check.
-- This is forward-only because the original dispatch migration is already applied remotely.

begin;

create or replace function public.publish_mobile_provider_location(
  p_latitude double precision,p_longitude double precision,p_accuracy_m double precision,
  p_captured_at timestamptz,p_tracker_instance_id uuid,p_tracker_sequence bigint
) returns jsonb language plpgsql security definer set search_path='' as $$
declare
  owned_provider public.providers%rowtype;
  current_location public.provider_discovery_locations%rowtype;
  active_appointment_uuid uuid;
  next_version bigint;
begin
  if not private.dispatch_is_provider() then
    raise exception using errcode='42501',message='Provider required';
  end if;

  select provider.* into owned_provider
  from public.providers as provider
  where provider.account_id=auth.uid()
  for update;

  if owned_provider.id is null or not owned_provider.active or not owned_provider.available
    or owned_provider.location_mode<>'MOBILE' then
    raise exception using errcode='22023',message='Provider ineligible';
  end if;
  if p_latitude not between -90 and 90 or p_longitude not between -180 and 180
    or p_accuracy_m<=0 or p_accuracy_m>100 or p_captured_at<now()-interval '30 seconds'
    or p_captured_at>now()+interval '10 seconds' or p_tracker_sequence<=0 then
    raise exception using errcode='22023',message='Invalid location';
  end if;

  select location.* into current_location
  from public.provider_discovery_locations as location
  where location.provider_id=owned_provider.id
  for update;

  if current_location.provider_id is not null
    and current_location.tracker_instance_id<>p_tracker_instance_id
    and current_location.published_at>=now()-interval '30 seconds' then
    return jsonb_build_object('outcome','PUBLISHER_CONFLICT','version',current_location.version);
  end if;
  if current_location.provider_id is not null
    and current_location.tracker_instance_id=p_tracker_instance_id
    and (p_tracker_sequence<=current_location.tracker_sequence
      or p_captured_at<current_location.captured_at) then
    return jsonb_build_object('outcome','IGNORED','version',current_location.version);
  end if;

  next_version:=coalesce(current_location.version,0)+1;
  insert into public.provider_discovery_locations(
    provider_id,latitude,longitude,position,accuracy_m,captured_at,published_at,version,
    tracker_instance_id,tracker_sequence
  ) values(
    owned_provider.id,p_latitude,p_longitude,
    extensions.st_setsrid(extensions.st_makepoint(p_longitude,p_latitude),4326)::extensions.geography,
    p_accuracy_m,p_captured_at,now(),next_version,p_tracker_instance_id,p_tracker_sequence
  )
  on conflict(provider_id) do update set
    latitude=excluded.latitude,longitude=excluded.longitude,position=excluded.position,
    accuracy_m=excluded.accuracy_m,captured_at=excluded.captured_at,
    published_at=excluded.published_at,version=excluded.version,
    tracker_instance_id=excluded.tracker_instance_id,tracker_sequence=excluded.tracker_sequence;

  select appointment.id into active_appointment_uuid
  from public.appointments as appointment
  where appointment.provider_id=owned_provider.id
    and appointment.status in ('ACCEPTED','IN_PROGRESS')
  order by appointment.updated_at desc
  limit 1;

  if active_appointment_uuid is not null then
    insert into public.provider_current_locations(
      appointment_id,latitude,longitude,accuracy_m,captured_at,published_at,version,
      tracker_instance_id,tracker_sequence
    ) values(
      active_appointment_uuid,p_latitude,p_longitude,p_accuracy_m,p_captured_at,now(),
      next_version,p_tracker_instance_id,p_tracker_sequence
    )
    on conflict(appointment_id) do update set
      latitude=excluded.latitude,longitude=excluded.longitude,accuracy_m=excluded.accuracy_m,
      captured_at=excluded.captured_at,published_at=excluded.published_at,
      version=public.provider_current_locations.version+1,
      tracker_instance_id=excluded.tracker_instance_id,
      tracker_sequence=excluded.tracker_sequence;
  end if;

  return jsonb_build_object(
    'outcome','PUBLISHED','version',next_version,
    'target',case when active_appointment_uuid is null then 'DISCOVERY' else 'APPOINTMENT' end,
    'published_at',now()
  );
end;
$$;

create or replace function public.stop_mobile_provider_location(p_tracker_instance_id uuid)
returns jsonb language plpgsql security definer set search_path='' as $$
declare
  owned_provider_uuid uuid;
  removed integer;
begin
  if not private.dispatch_is_provider() then
    raise exception using errcode='42501',message='Provider required';
  end if;
  select provider.id into owned_provider_uuid
  from public.providers as provider
  where provider.account_id=auth.uid();

  delete from public.provider_discovery_locations as location
  where location.provider_id=owned_provider_uuid
    and location.tracker_instance_id=p_tracker_instance_id;
  get diagnostics removed=row_count;
  return jsonb_build_object('outcome',case when removed=1 then 'STOPPED' else 'IGNORED' end);
end;
$$;

create or replace function public.respond_to_mobile_dispatch(
  p_appointment_id uuid,p_accept boolean,p_expected_revision bigint
) returns jsonb language plpgsql security definer set search_path='' as $$
declare
  request_row public.appointments%rowtype;
  owned_provider_uuid uuid;
begin
  if not private.dispatch_is_provider() then
    raise exception using errcode='42501',message='Provider required';
  end if;
  select provider.id into owned_provider_uuid
  from public.providers as provider
  where provider.account_id=auth.uid();
  select appointment.* into request_row
  from public.appointments as appointment
  where appointment.id=p_appointment_id
    and appointment.provider_id=owned_provider_uuid
  for update;
  if request_row.id is null then
    raise exception using errcode='P0002',message='Request unavailable';
  end if;
  if request_row.status='PENDING' and request_row.response_deadline<=now() then
    perform private.expire_mobile_dispatch_appointment(request_row.id);
    return jsonb_build_object('outcome','NONE');
  end if;
  if request_row.revision<>p_expected_revision then
    return jsonb_build_object(
      'outcome','STALE_REVISION',
      'snapshot',private.dispatch_snapshot(request_row.dispatch_search_id)
    );
  end if;
  if request_row.status<>'PENDING' then
    return private.dispatch_snapshot(request_row.dispatch_search_id);
  end if;
  if p_accept then
    update public.appointments set status='ACCEPTED',accepted_at=now(),revision=revision+1,updated_at=now()
    where id=request_row.id;
    update private.dispatch_candidates set status='ACCEPTED',decided_at=now()
    where id=request_row.dispatch_candidate_id;
    update public.service_searches set status='ACCEPTED',revision=revision+1,updated_at=now()
    where id=request_row.dispatch_search_id;
  else
    update public.appointments set status='PROVIDER_REJECTED',ended_at=now(),revision=revision+1,updated_at=now()
    where id=request_row.id;
    update private.dispatch_candidates set status='PROVIDER_REJECTED',decided_at=now()
    where id=request_row.dispatch_candidate_id;
    update public.service_searches set status='SEARCHING',current_appointment_id=null,
      current_candidate_id=null,revision=revision+1,updated_at=now()
    where id=request_row.dispatch_search_id;
    perform private.advance_mobile_dispatch(request_row.dispatch_search_id);
    return jsonb_build_object('outcome','NONE');
  end if;
  return private.dispatch_snapshot(request_row.dispatch_search_id);
end;
$$;

create or replace function public.start_mobile_appointment(
  p_appointment_id uuid,p_expected_revision bigint
) returns jsonb language plpgsql security definer set search_path='' as $$
declare
  appointment_row public.appointments%rowtype;
  owned_provider_uuid uuid;
begin
  if not private.dispatch_is_provider() then
    raise exception using errcode='42501',message='Provider required';
  end if;
  select provider.id into owned_provider_uuid
  from public.providers as provider
  where provider.account_id=auth.uid();
  select appointment.* into appointment_row
  from public.appointments as appointment
  where appointment.id=p_appointment_id
    and appointment.provider_id=owned_provider_uuid
  for update;
  if appointment_row.id is null then
    raise exception using errcode='P0002',message='Appointment unavailable';
  end if;
  if appointment_row.revision=p_expected_revision and appointment_row.status='ACCEPTED' then
    update public.appointments set status='IN_PROGRESS',revision=revision+1,updated_at=now()
    where id=appointment_row.id;
    update public.service_searches set status='IN_PROGRESS',revision=revision+1,updated_at=now()
    where id=appointment_row.dispatch_search_id;
  end if;
  return private.dispatch_snapshot(appointment_row.dispatch_search_id);
end;
$$;

create or replace function public.complete_mobile_appointment(
  p_appointment_id uuid,p_expected_revision bigint
) returns jsonb language plpgsql security definer set search_path='' as $$
declare
  appointment_row public.appointments%rowtype;
  owned_provider_uuid uuid;
begin
  if not private.dispatch_is_provider() then
    raise exception using errcode='42501',message='Provider required';
  end if;
  select provider.id into owned_provider_uuid
  from public.providers as provider
  where provider.account_id=auth.uid();
  select appointment.* into appointment_row
  from public.appointments as appointment
  where appointment.id=p_appointment_id
    and appointment.provider_id=owned_provider_uuid
  for update;
  if appointment_row.id is null then
    raise exception using errcode='P0002',message='Appointment unavailable';
  end if;
  if appointment_row.revision=p_expected_revision
    and appointment_row.status in ('ACCEPTED','IN_PROGRESS') then
    update public.appointments set status='COMPLETED',ended_at=now(),revision=revision+1,updated_at=now()
    where id=appointment_row.id;
    update public.service_searches set status='COMPLETED',search_latitude=null,search_longitude=null,
      search_point=null,search_accuracy_m=null,revision=revision+1,updated_at=now(),ended_at=now()
    where id=appointment_row.dispatch_search_id;
  end if;
  return private.dispatch_snapshot(appointment_row.dispatch_search_id);
end;
$$;

revoke all on function public.publish_mobile_provider_location(
  double precision,double precision,double precision,timestamptz,uuid,bigint
) from public,anon;
revoke all on function public.stop_mobile_provider_location(uuid) from public,anon;
revoke all on function public.respond_to_mobile_dispatch(uuid,boolean,bigint) from public,anon;
revoke all on function public.start_mobile_appointment(uuid,bigint) from public,anon;
revoke all on function public.complete_mobile_appointment(uuid,bigint) from public,anon;
grant execute on function public.publish_mobile_provider_location(
  double precision,double precision,double precision,timestamptz,uuid,bigint
) to authenticated;
grant execute on function public.stop_mobile_provider_location(uuid) to authenticated;
grant execute on function public.respond_to_mobile_dispatch(uuid,boolean,bigint) to authenticated;
grant execute on function public.start_mobile_appointment(uuid,bigint) to authenticated;
grant execute on function public.complete_mobile_appointment(uuid,bigint) to authenticated;

notify pgrst,'reload schema';

commit;
