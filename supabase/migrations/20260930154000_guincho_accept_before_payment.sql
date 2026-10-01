begin;

-- Change only future reservations; existing financial records are not rewritten.
do $$
declare definition text;
begin
 definition:=pg_get_functiondef('public.create_guincho_request(uuid,uuid)'::regprocedure);
 if position('''GUINCHO_DIRECT'',''AWAITING_PAYMENT''' in definition)=0 then
  raise exception 'Unexpected reservation contract';
 end if;
 -- Preserve the RPC used by older released clients until their apps are updated.
 definition:=replace(definition,'public.create_guincho_request(', 'public.create_guincho_request_before_payment(');
 definition:=replace(definition,'''GUINCHO_DIRECT'',''AWAITING_PAYMENT''','''GUINCHO_DIRECT'',''AWAITING_PROVIDER''');
 definition:=replace(definition,'status,guincho_quote_id','status,response_deadline,guincho_quote_id');
 definition:=replace(definition,'o.id,''AWAITING_PAYMENT'',q.id','o.id,''PENDING'',now()+interval ''60 seconds'',q.id');
 execute definition;

 definition:=pg_get_functiondef('private.guincho_snapshot(uuid)'::regprocedure);
 definition:=replace(definition,'if caller_provider and (a.status=''AWAITING_PAYMENT'' or pay.id is null',
  'if caller_provider and a.status not in (''PENDING'',''AWAITING_PAYMENT'',''CANCELLED'') and (pay.id is null');
 definition:=replace(definition,'''tracking_authorized'',a.status in (''ACCEPTED'',''IN_PROGRESS'')',
  '''tracking_authorized'',coalesce(a.status in (''ACCEPTED'',''IN_PROGRESS'') and pay.status=''APPROVED'' and private.payment_can_release(pay,a.service_id),false)');
 definition:=replace(definition,'when caller_client and a.status=''AWAITING_PAYMENT'' then',
  'when caller_client and a.status=''PENDING'' then ''["CANCEL"]''::jsonb'||chr(10)||' when caller_client and a.status=''AWAITING_PAYMENT'' then');
 -- The invitation contains the provider's fee, not the client's personal trip.
 definition:=replace(definition,'''trip'',a.guincho_trip_snapshot',
  '''trip'',case when caller_provider and (pay.id is null or pay.status not in
   (''APPROVED'',''REFUND_PENDING'',''REFUND_FAILED'',''REFUNDED'')) then null else a.guincho_trip_snapshot end');
 execute definition;

 definition:=pg_get_functiondef('public.get_my_active_guincho_request()'::regprocedure);
 definition:=replace(definition,'p.account_id=auth.uid() and a.status<>''AWAITING_PAYMENT''','p.account_id=auth.uid()');
 execute definition;
end $$;
revoke all on function public.create_guincho_request_before_payment(uuid,uuid) from public,anon;
grant execute on function public.create_guincho_request_before_payment(uuid,uuid) to authenticated;

create or replace function public.respond_guincho_request(
 p_appointment_id uuid,p_expected_revision bigint,p_operation_id uuid,p_accept boolean
) returns jsonb language plpgsql security definer set search_path='' as $$
#variable_conflict use_column
declare a public.appointments%rowtype; search_uuid uuid; paid boolean;
begin
 if p_operation_id is null or p_accept is null then raise exception using errcode='22023',message='INVALID_INPUT'; end if;
 select a.dispatch_search_id into search_uuid from public.appointments a join public.providers p on p.id=a.provider_id
 where a.id=p_appointment_id and a.guincho_quote_id is not null and p.account_id=auth.uid();
 if search_uuid is null then raise exception using errcode='42501',message='PROVIDER_REQUIRED'; end if;
 perform 1 from public.service_searches where id=search_uuid for update;
 select * into a from public.appointments where id=p_appointment_id for update;
 if a.status in ('AWAITING_PAYMENT','ACCEPTED','IN_PROGRESS','AWAITING_CLIENT_COMPLETION_CONFIRMATION','DISPUTED','COMPLETED','CANCELLED') then
  return jsonb_build_object('outcome','EXISTING','snapshot',private.guincho_snapshot(a.id),'server_now',now());
 end if;
 if a.status<>'PENDING' then raise exception using errcode='22023',message='INVALID_TRANSITION'; end if;
 if a.response_deadline<=now() then
  perform private.cancel_guincho_appointment(a.id,'PROVIDER_RESPONSE_EXPIRED');
  return jsonb_build_object('outcome','OFFER_EXPIRED','snapshot',private.guincho_snapshot(a.id),'server_now',now());
 end if;
 if a.revision is distinct from p_expected_revision then
  return jsonb_build_object('outcome','REVISION_CONFLICT','snapshot',private.guincho_snapshot(a.id),'server_now',now());
 end if;
 select exists(select 1 from public.payments p where p.request_id=search_uuid and p.appointment_id=a.id
  and p.quote_version=a.quote_version and p.amount=coalesce(a.payment_amount,a.customer_total_amount)
  and private.payment_can_release(p,a.service_id)) into paid;
 if p_accept then
  -- Already paid legacy offers keep their original acceptance path.
  update public.appointments set status=case when paid then 'ACCEPTED' else 'AWAITING_PAYMENT' end,
   accepted_at=now(),response_deadline=null,quote_expires_at=case when paid then quote_expires_at else now()+interval '30 minutes' end,
   service_deadline_at=case when paid then now()+interval '4 hours' else null end,
   revision=revision+1,updated_at=now() where id=a.id;
  update public.service_searches set status=case when paid then 'ACCEPTED' else 'AWAITING_PAYMENT' end,
   revision=revision+1,updated_at=now(),next_dispatch_at=null where id=search_uuid;
 else perform private.cancel_guincho_appointment(a.id,'PROVIDER_DECLINED'); end if;
 return jsonb_build_object('outcome',case when p_accept then 'ACCEPTED' else 'DECLINED' end,
  'snapshot',private.guincho_snapshot(a.id),'server_now',now());
end $$;

create or replace function private.reconcile_guincho_payment_change() returns trigger
language plpgsql security definer set search_path='' as $$
#variable_conflict use_column
declare a public.appointments%rowtype; s public.service_searches%rowtype;
begin
 if new.dispatch_kind<>'GUINCHO_DIRECT' then return new; end if;
 if tg_op='UPDATE' and old.status=new.status and old.sync_state=new.sync_state then return new; end if;
 select * into s from public.service_searches where id=new.request_id for update;
 select * into a from public.appointments where id=new.appointment_id and dispatch_search_id=s.id for update;
 if new.status='APPROVED' and new.sync_state='CURRENT' then
  if a.id is not null and a.status in ('PENDING','ACCEPTED','IN_PROGRESS','AWAITING_CLIENT_COMPLETION_CONFIRMATION','DISPUTED','COMPLETED')
   and new.client_id=a.client_id and new.quote_version=a.quote_version
   and new.amount=coalesce(a.payment_amount,a.customer_total_amount)
   and private.payment_can_release(new,a.service_id) then return new; end if;
  if a.id is null or s.dispatch_kind is distinct from 'GUINCHO_DIRECT' or new.client_id is distinct from a.client_id
   or new.quote_version is distinct from a.quote_version or new.amount is distinct from coalesce(a.payment_amount,a.customer_total_amount)
   or new.currency<>'BRL' or a.status<>'AWAITING_PAYMENT' or s.status<>'AWAITING_PAYMENT'
   or a.quote_expires_at<=now() or not private.payment_can_release(new,a.service_id) then
   update public.payments set status='REFUND_PENDING',cancel_requested_at=coalesce(cancel_requested_at,now()),
    next_action_at=now(),work_action='REFUND',lease_token=null,lease_until=null,revision=revision+1,updated_at=now()
    where id=new.id and status='APPROVED';
   if a.id is not null then perform private.cancel_guincho_appointment(a.id,'PAYMENT_NOT_RELEASABLE'); end if;
   return new;
  end if;
  update public.appointments set status=case when a.accepted_at is not null then 'ACCEPTED' else 'PENDING' end,
   response_deadline=case when a.accepted_at is null then now()+interval '60 seconds' else null end,
   service_deadline_at=case when a.accepted_at is not null then now()+interval '4 hours' else null end,
   revision=revision+1,updated_at=now() where id=a.id;
  update public.service_searches set status=case when a.accepted_at is not null then 'ACCEPTED' else 'AWAITING_PROVIDER' end,
   revision=revision+1,updated_at=now(),next_dispatch_at=null where id=s.id;
 elsif new.status in ('REJECTED','EXPIRED','CANCELLED') and new.sync_state='CURRENT' and a.status='AWAITING_PAYMENT' then
  perform private.cancel_guincho_appointment(a.id,'PAYMENT_NOT_APPROVED');
 end if;
 return new;
end $$;

create or replace function private.expire_guincho_reservations() returns void
language plpgsql security definer set search_path='' as $$
declare appointment_uuid uuid;
begin
 for appointment_uuid in select a.id from public.appointments a where a.guincho_quote_id is not null
  and ((a.status='AWAITING_PAYMENT' and a.quote_expires_at<=now())
   or (a.status='PENDING' and a.response_deadline<=now()))
  order by coalesce(a.response_deadline,a.quote_expires_at) limit 100 loop
  perform private.cancel_guincho_appointment(appointment_uuid,'RESERVATION_EXPIRED');
 end loop;
end $$;

do $$
declare definition text;
begin
 definition:=pg_get_functiondef('private.guincho_provider_transition(uuid,bigint,text)'::regprocedure);
 definition:=replace(definition,'set arrived_at=now(),revision=revision+1,updated_at=now()',
  'set arrived_at=now(),service_started_at=now(),status=''IN_PROGRESS'',revision=revision+1,updated_at=now()');
 definition:=replace(definition,'elsif p_action=''START''',
  'update public.service_searches set status=''IN_PROGRESS'',revision=revision+1,updated_at=now() where id=search_uuid;'||chr(10)||' elsif p_action=''START''');
 execute definition;
end $$;

notify pgrst,'reload schema';
commit;
