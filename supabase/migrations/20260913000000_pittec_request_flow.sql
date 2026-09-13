-- specs/019-pittec-request-flow: corrige o fluxo de solicitação do PitTec.
--
-- (a) private.pittec_dispatch_snapshot ganha o campo `candidate` (nome do prestador + distância
--     aproximada), visível ao CLIENTE enquanto o appointment atual está PENDING — hoje esse mesmo
--     dado só é exposto ao prestador via `offer`. Mutuamente exclusivo com `provider` (que só
--     aparece a partir de ACCEPTED).
-- (b) public.start_pittec_dispatch perde a guarda PAYMENT_REQUIRED — a busca passa a ser gratuita
--     para iniciar.
-- (c) public.arrive_pittec_appointment ganha a guarda PAYMENT_REQUIRED — a transição
--     ACCEPTED -> IN_PROGRESS (o prestador marcando "cheguei") passa a exigir
--     payments.request_id = <search_id> APPROVED. É o novo ponto real de "o serviço começa" (o
--     cliente já paga bem antes disso, ao confirmar o card de valores em ACCEPTED — este gate é a
--     rede de segurança server-side, nunca a fonte da experiência percebida).
--
-- Nenhuma tabela nova. Nenhuma outra linha das três funções muda.

-- (a) ---------------------------------------------------------------------------------------------

create or replace function private.pittec_dispatch_snapshot(p_search_id uuid)
 returns jsonb
 language plpgsql
 stable security definer
 set search_path to ''
as $function$
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
    -- (019) Candidato ainda não aceito, visível só ao cliente — espelha `offer` acima, mas do lado
    -- do outro participante. Nunca coexiste com `provider` (mutuamente exclusivos por status).
    'candidate', case
      when caller_is_client and appointment_row.status = 'PENDING' then jsonb_build_object(
        'provider_name', provider_display_name,
        'approximate_distance_meters', round(candidate_row.distance_meters / 500) * 500
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
$function$;

-- (b) ---------------------------------------------------------------------------------------------

create or replace function public.start_pittec_dispatch(p_request_id uuid, p_service_id uuid, p_latitude double precision, p_longitude double precision, p_accuracy_meters double precision, p_captured_at timestamp with time zone)
 returns jsonb
 language plpgsql
 security definer
 set search_path to ''
as $function$
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

  -- (019) Guarda PAYMENT_REQUIRED removida daqui — a busca agora é gratuita para iniciar. O
  -- pagamento real passa a ser exigido em arrive_pittec_appointment (ver migration).

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
$function$;

-- (c) ---------------------------------------------------------------------------------------------

create or replace function public.arrive_pittec_appointment(p_request_id uuid, p_appointment_id uuid, p_expected_revision bigint)
 returns jsonb
 language plpgsql
 security definer
 set search_path to ''
as $function$
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

  -- (019) Nova guarda: o pagamento (cobrado quando o cliente confirma o card de valores, logo após
  -- ACCEPTED) precisa estar aprovado antes de o prestador poder marcar a chegada. Rede de segurança
  -- server-side — no fluxo feliz o pagamento já foi aprovado bem antes da chegada física.
  if not exists (
    select 1 from public.payments
     where request_id = appointment_row.dispatch_search_id
       and status = 'APPROVED'
  ) then
    raise exception using errcode = 'P0001', message = 'PAYMENT_REQUIRED';
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
$function$;
