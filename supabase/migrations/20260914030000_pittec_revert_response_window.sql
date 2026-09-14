-- Reverte 20260914020000_pittec_test_longer_response_window.sql — o teste confirmou que o gargalo
-- real não era o prazo de 60s (o app Android nunca chegava a mostrar a oferta pendente em tela
-- alguma, independente da duração da janela), então não há motivo pra manter os 5 minutos. Volta
-- private.advance_pittec_dispatch ao valor original de produção (`020260913010000`).
begin;

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

commit;
