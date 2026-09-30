-- PitTec completion is two-step: provider requests completion, client confirms it.
begin;

alter table public.service_searches drop constraint if exists service_search_status_check;
alter table public.service_searches add constraint service_search_status_check check (status in (
  'SEARCHING','OPTION_FOUND','AWAITING_PROVIDER','AWAITING_CLIENT_CONFIRMATION','AWAITING_PAYMENT',
  'ACCEPTED','IN_PROGRESS','AWAITING_CLIENT_COMPLETION_CONFIRMATION','CANCELLED','COMPLETED','EXHAUSTED'
));
alter table public.appointments drop constraint if exists appointments_status_check;
alter table public.appointments add constraint appointments_status_check check (status in (
  'PENDING','AWAITING_CLIENT_CONFIRMATION','AWAITING_PAYMENT','ACCEPTED','IN_PROGRESS',
  'AWAITING_CLIENT_COMPLETION_CONFIRMATION','COMPLETED','CANCELLED','PROVIDER_REJECTED','EXPIRED'
));
alter table public.appointments add column if not exists completion_requested_at timestamptz;
alter table public.appointments add column if not exists client_completion_confirmed_at timestamptz;

drop index if exists public.service_searches_client_service_active_unique;
create unique index service_searches_client_service_active_unique on public.service_searches(client_id, service_id)
where status in ('SEARCHING','OPTION_FOUND','AWAITING_PROVIDER','AWAITING_CLIENT_CONFIRMATION','AWAITING_PAYMENT','ACCEPTED','IN_PROGRESS','AWAITING_CLIENT_COMPLETION_CONFIRMATION');
drop index if exists public.one_active_appointment_per_provider;
create unique index one_active_appointment_per_provider on public.appointments(provider_id)
where status in ('PENDING','AWAITING_CLIENT_CONFIRMATION','AWAITING_PAYMENT','ACCEPTED','IN_PROGRESS','AWAITING_CLIENT_COMPLETION_CONFIRMATION');

-- Keep the already audited snapshot contract and decorate it with the new completion state.
alter function private.pittec_dispatch_snapshot(uuid) rename to pittec_dispatch_snapshot_base;
create or replace function private.pittec_dispatch_snapshot(p_search_id uuid)
returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare
  snapshot jsonb;
  search_row public.service_searches%rowtype;
  appointment_row public.appointments%rowtype;
  caller_is_client boolean;
  caller_is_provider boolean;
begin
  snapshot := private.pittec_dispatch_snapshot_base(p_search_id);
  select search.* into search_row from public.service_searches as search where search.id = p_search_id;
  select appointment.* into appointment_row from public.appointments as appointment where appointment.id = search_row.current_appointment_id;
  caller_is_client := auth.uid() = search_row.client_id;
  select exists(
    select 1 from public.providers as provider where provider.id = appointment_row.provider_id and provider.account_id = auth.uid()
  ) into caller_is_provider;
  snapshot := jsonb_set(snapshot, '{tracking_authorized}', to_jsonb(search_row.status in ('ACCEPTED','IN_PROGRESS','AWAITING_CLIENT_COMPLETION_CONFIRMATION')), true);
  snapshot := jsonb_set(snapshot, '{appointment,completion_requested_at}', to_jsonb(appointment_row.completion_requested_at), true);
  snapshot := jsonb_set(snapshot, '{appointment,client_completion_confirmed_at}', to_jsonb(appointment_row.client_completion_confirmed_at), true);
  if search_row.status = 'AWAITING_CLIENT_COMPLETION_CONFIRMATION' then
    snapshot := jsonb_set(snapshot, '{allowed_actions}', case
      when caller_is_client then jsonb_build_array('CONFIRM_COMPLETION')
      when caller_is_provider then jsonb_build_array('OPEN_TRACKING')
      else '[]'::jsonb end, true);
  end if;
  return snapshot;
end;
$$;

create or replace function public.get_my_active_pittec_dispatch()
returns jsonb language plpgsql security definer set search_path = '' as $$
declare search_uuid uuid; appointment_uuid uuid; search_status text; next_dispatch timestamptz;
begin
  if private.dispatch_is_client() then
    select search.id, search.current_appointment_id, search.status, search.next_dispatch_at into search_uuid, appointment_uuid, search_status, next_dispatch
    from public.service_searches as search join public.services as service on service.id = search.service_id
    where search.client_id = auth.uid() and service.dispatch_kind = 'PITTEC_AUTO_SEQUENTIAL'
      and search.status in ('SEARCHING','AWAITING_PROVIDER','AWAITING_CLIENT_CONFIRMATION','AWAITING_PAYMENT','ACCEPTED','IN_PROGRESS','AWAITING_CLIENT_COMPLETION_CONFIRMATION')
    order by search.updated_at desc limit 1;
  elsif private.dispatch_is_provider() then
    select search.id, search.current_appointment_id, search.status, search.next_dispatch_at into search_uuid, appointment_uuid, search_status, next_dispatch
    from public.service_searches as search join public.services as service on service.id = search.service_id
    join public.appointments as appointment on appointment.dispatch_search_id = search.id join public.providers as provider on provider.id = appointment.provider_id
    where provider.account_id = auth.uid() and service.dispatch_kind = 'PITTEC_AUTO_SEQUENTIAL'
      and appointment.status in ('PENDING','AWAITING_CLIENT_CONFIRMATION','AWAITING_PAYMENT','ACCEPTED','IN_PROGRESS','AWAITING_CLIENT_COMPLETION_CONFIRMATION')
    order by appointment.updated_at desc limit 1;
  else raise exception using errcode = '42501', message = 'ACCOUNT_REQUIRED'; end if;
  if search_uuid is null then return jsonb_build_object('outcome','NONE','server_now',now()); end if;
  if appointment_uuid is not null then perform private.expire_mobile_dispatch_appointment(appointment_uuid); perform private.expire_pittec_confirmation(appointment_uuid); end if;
  if search_status = 'SEARCHING' and coalesce(next_dispatch, now()) <= now() then perform private.advance_pittec_dispatch(search_uuid); end if;
  return jsonb_build_object('outcome','ACTIVE','snapshot',private.pittec_dispatch_snapshot(search_uuid));
end;
$$;

create or replace function public.complete_mobile_appointment(p_appointment_id uuid, p_expected_revision bigint)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare appointment_row public.appointments%rowtype; owned_provider_uuid uuid; dispatch_kind_value text;
begin
  if not private.dispatch_is_provider() then raise exception using errcode = '42501', message = 'PROVIDER_REQUIRED'; end if;
  select provider.id into owned_provider_uuid from public.providers as provider where provider.account_id = auth.uid();
  select appointment.* into appointment_row from public.appointments as appointment where appointment.id=p_appointment_id and appointment.provider_id=owned_provider_uuid for update;
  if appointment_row.id is null then raise exception using errcode = 'P0002', message = 'APPOINTMENT_UNAVAILABLE'; end if;
  select service.dispatch_kind into dispatch_kind_value from public.services as service where service.id=appointment_row.service_id;
  if appointment_row.revision <> p_expected_revision then return jsonb_build_object('outcome','REVISION_CONFLICT','snapshot',private.pittec_dispatch_snapshot(appointment_row.dispatch_search_id)); end if;
  if dispatch_kind_value = 'PITTEC_AUTO_SEQUENTIAL' then
    if appointment_row.status <> 'IN_PROGRESS' then return jsonb_build_object('outcome','INVALID_TRANSITION','snapshot',private.pittec_dispatch_snapshot(appointment_row.dispatch_search_id)); end if;
    update public.appointments set status='AWAITING_CLIENT_COMPLETION_CONFIRMATION', completion_requested_at=now(), revision=revision+1, updated_at=now() where id=appointment_row.id;
    update public.service_searches set status='AWAITING_CLIENT_COMPLETION_CONFIRMATION', revision=revision+1, updated_at=now() where id=appointment_row.dispatch_search_id;
    return jsonb_build_object('outcome','AWAITING_CLIENT_CONFIRMATION','snapshot',private.pittec_dispatch_snapshot(appointment_row.dispatch_search_id));
  end if;
  if appointment_row.status not in ('ACCEPTED','IN_PROGRESS') then return private.dispatch_snapshot(appointment_row.dispatch_search_id); end if;
  update public.appointments set status='COMPLETED', ended_at=now(), revision=revision+1, updated_at=now() where id=appointment_row.id;
  update public.service_searches set status='COMPLETED', search_latitude=null, search_longitude=null, search_point=null, search_accuracy_m=null, request_captured_at=null, revision=revision+1, updated_at=now(), ended_at=now() where id=appointment_row.dispatch_search_id;
  delete from public.appointment_route_snapshots where appointment_id=appointment_row.id; perform private.apply_pending_provider_mode(appointment_row.provider_id);
  return private.dispatch_snapshot(appointment_row.dispatch_search_id);
end;
$$;

create or replace function public.confirm_pittec_completion(p_appointment_id uuid, p_expected_revision bigint)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare appointment_row public.appointments%rowtype;
begin
  if not private.dispatch_is_client() then raise exception using errcode = '42501', message = 'CLIENT_REQUIRED'; end if;
  select appointment.* into appointment_row from public.appointments as appointment where appointment.id=p_appointment_id and appointment.client_id=auth.uid() for update;
  if appointment_row.id is null then raise exception using errcode = 'P0002', message = 'APPOINTMENT_UNAVAILABLE'; end if;
  if appointment_row.revision <> p_expected_revision then return jsonb_build_object('outcome','REVISION_CONFLICT','snapshot',private.pittec_dispatch_snapshot(appointment_row.dispatch_search_id)); end if;
  if appointment_row.status <> 'AWAITING_CLIENT_COMPLETION_CONFIRMATION' then return jsonb_build_object('outcome','INVALID_TRANSITION','snapshot',private.pittec_dispatch_snapshot(appointment_row.dispatch_search_id)); end if;
  update public.appointments set status='COMPLETED', client_completion_confirmed_at=now(), ended_at=now(), revision=revision+1, updated_at=now() where id=appointment_row.id;
  update public.service_searches set status='COMPLETED', search_latitude=null, search_longitude=null, search_point=null, search_accuracy_m=null, request_captured_at=null, revision=revision+1, updated_at=now(), ended_at=now() where id=appointment_row.dispatch_search_id;
  delete from public.appointment_route_snapshots where appointment_id=appointment_row.id; perform private.apply_pending_provider_mode(appointment_row.provider_id);
  return jsonb_build_object('outcome','COMPLETED','snapshot',private.pittec_dispatch_snapshot(appointment_row.dispatch_search_id));
end;
$$;

grant execute on function public.confirm_pittec_completion(uuid,bigint) to authenticated;
commit;
