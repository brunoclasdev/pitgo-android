begin;
create function private.guincho_snapshot(p_appointment_id uuid) returns jsonb
language plpgsql stable security definer set search_path='' as $$
declare a public.appointments%rowtype; s public.service_searches%rowtype;
  p public.providers%rowtype; pay public.payments%rowtype; caller_client boolean;
  caller_provider boolean; actions jsonb; dispute jsonb;
begin
  select * into a from public.appointments where id=p_appointment_id and guincho_quote_id is not null;
  if a.id is null then return null; end if;
  select * into s from public.service_searches where id=a.dispatch_search_id;
  select * into p from public.providers where id=a.provider_id;
  caller_client:=coalesce(auth.uid()=a.client_id,false);
  caller_provider:=coalesce(auth.uid()=p.account_id,false);
  if not caller_client and not caller_provider then
    raise exception using errcode='42501',message='PARTICIPANT_REQUIRED';
  end if;
  select * into pay from public.payments where request_id=s.id and not is_compensation
  order by created_at desc limit 1;
  -- Paid operational/historical data remain available when a refund is being processed.
  if caller_provider and (a.status='AWAITING_PAYMENT' or pay.id is null
    or pay.status not in ('APPROVED','REFUND_PENDING','REFUND_FAILED','REFUNDED')) then
    raise exception using errcode='42501',message='PAYMENT_REQUIRED';
  end if;
  actions:=case
    when caller_client and a.status='AWAITING_PAYMENT' then ' ["OPEN_PAYMENT","CANCEL"]'::jsonb
    when caller_client and a.status='AWAITING_CLIENT_COMPLETION_CONFIRMATION' then '["CONFIRM_COMPLETION","DISPUTE"]'::jsonb
    when caller_provider and a.status='PENDING' then '["ACCEPT","DECLINE"]'::jsonb
    when caller_provider and a.status='ACCEPTED' and a.arrived_at is null then '["OPEN_TRACKING","ARRIVE"]'::jsonb
    when caller_provider and a.status='ACCEPTED' then '["OPEN_TRACKING","START"]'::jsonb
    when caller_provider and a.status='IN_PROGRESS' then '["OPEN_TRACKING","COMPLETE"]'::jsonb
    when a.status in ('ACCEPTED','IN_PROGRESS') then '["OPEN_TRACKING"]'::jsonb
    else '[]'::jsonb end;
  select jsonb_build_object('id',d.id,'reason',d.reason,'status',d.status,'revision',d.revision,
    'opened_at',d.opened_at,'resolution',d.resolution,'justification',d.justification,'resolved_at',d.resolved_at)
  into dispute from public.appointment_disputes d where d.appointment_id=a.id;
  return jsonb_build_object('search_id',s.id,'service_id',a.service_id,
    'service_name',(select name from public.services where id=a.service_id),'dispatch_kind','GUINCHO_DIRECT',
    'status',s.status,'radius_meters',s.current_radius_m,'revision',s.revision,'server_now',now(),
    'allowed_actions',actions,'tracking_authorized',a.status in ('ACCEPTED','IN_PROGRESS'),
    'provider',jsonb_build_object('provider_id',p.id,'display_name',p.name),
    'payment',case when pay.id is null then null else jsonb_build_object('status',pay.status) end,
    'trip',a.guincho_trip_snapshot,'dispute',dispute,
    'appointment',jsonb_build_object('appointment_id',a.id,'status',a.status,'revision',a.revision,
      'quote_version',a.quote_version,'response_deadline',a.response_deadline,'accepted_at',a.accepted_at,
      'arrived_at',a.arrived_at,'service_started_at',a.service_started_at,
      'completion_requested_at',a.completion_requested_at,'client_completion_confirmed_at',a.client_completion_confirmed_at,
      'quote',case when caller_client then jsonb_build_object('customer_total_amount',a.customer_total_amount::text,
        'currency','BRL','version',a.quote_version,'expires_at',a.quote_expires_at)
        else jsonb_build_object('provider_amount',a.provider_amount::text,'currency','BRL',
          'version',a.quote_version,'expires_at',a.quote_expires_at) end));
end; $$;
revoke all on function private.guincho_snapshot(uuid) from public,anon,authenticated;

create function public.get_guincho_appointment_snapshot(p_appointment_id uuid) returns jsonb
language sql stable security definer set search_path='' as $$
 select private.guincho_snapshot(p_appointment_id);
$$;
create function public.get_my_active_guincho_request() returns jsonb
language plpgsql stable security definer set search_path='' as $$
declare appointment_id uuid;
begin
  if auth.uid() is null then raise exception using errcode='42501',message='SESSION_REQUIRED'; end if;
  select a.id into appointment_id from public.appointments a join public.providers p on p.id=a.provider_id
  where a.guincho_quote_id is not null
  and (a.client_id=auth.uid() or (p.account_id=auth.uid() and a.status<>'AWAITING_PAYMENT'))
  and a.status in ('AWAITING_PAYMENT','PENDING','ACCEPTED','IN_PROGRESS','AWAITING_CLIENT_COMPLETION_CONFIRMATION')
  order by a.updated_at desc,a.id limit 1;
  return jsonb_build_object('outcome',case when appointment_id is null then 'NONE' else 'ACTIVE' end,
    'snapshot',case when appointment_id is null then null else private.guincho_snapshot(appointment_id) end);
end; $$;
revoke all on function public.get_guincho_appointment_snapshot(uuid),public.get_my_active_guincho_request() from public,anon;
grant execute on function public.get_guincho_appointment_snapshot(uuid),public.get_my_active_guincho_request() to authenticated;
commit;
