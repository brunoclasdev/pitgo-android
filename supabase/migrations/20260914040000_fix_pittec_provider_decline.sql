begin;

-- A provider who declines is no longer a participant in the search.  Returning a
-- PitTec snapshot after advancing to the next provider therefore fails the
-- participant check and rolls back the decline.  Complete the transition first,
-- then return only the outcome; the client observes the next offer separately.
create or replace function public.respond_to_mobile_dispatch(
  p_appointment_id uuid,
  p_accept boolean,
  p_expected_revision bigint
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare
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
  select appointment.* into appointment_row
  from public.appointments as appointment
  where appointment.id = p_appointment_id and appointment.provider_id = owned_provider_uuid
  for update;
  if appointment_row.id is null then
    raise exception using errcode = 'P0002', message = 'REQUEST_UNAVAILABLE';
  end if;
  select search.* into search_row from public.service_searches as search
  where search.id = appointment_row.dispatch_search_id for update;
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
      last_event = case when dispatch_kind_value = 'PITTEC_AUTO_SEQUENTIAL' then 'PROVIDER_REJECTED' else last_event end,
      last_event_at = case when dispatch_kind_value = 'PITTEC_AUTO_SEQUENTIAL' then now() else last_event_at end,
      revision = revision + 1, updated_at = now()
  where id = appointment_row.dispatch_search_id;
  if dispatch_kind_value = 'PITTEC_AUTO_SEQUENTIAL' then
    perform private.advance_pittec_dispatch(appointment_row.dispatch_search_id);
    return jsonb_build_object('outcome','DECLINED');
  end if;
  perform private.advance_mobile_dispatch(appointment_row.dispatch_search_id);
  return jsonb_build_object('outcome','NONE');
end;
$$;

grant execute on function public.respond_to_mobile_dispatch(uuid,boolean,bigint) to authenticated;
commit;
