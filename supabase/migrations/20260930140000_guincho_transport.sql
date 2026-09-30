begin;
create function private.guincho_provider_transition(p_appointment_id uuid,p_expected_revision bigint,p_action text)
returns jsonb language plpgsql security definer set search_path='' as $$
declare a public.appointments%rowtype; search_uuid uuid;
begin
 select a.dispatch_search_id into search_uuid from public.appointments a join public.providers p on p.id=a.provider_id
 where a.id=p_appointment_id and a.guincho_quote_id is not null and p.account_id=auth.uid();
 if search_uuid is null then raise exception using errcode='42501',message='PROVIDER_REQUIRED'; end if;
 perform 1 from public.service_searches where id=search_uuid for update;
 select * into a from public.appointments where id=p_appointment_id for update;
 if (p_action='ARRIVE' and a.arrived_at is not null) or (p_action='START' and a.service_started_at is not null)
 or (p_action='COMPLETE' and a.completion_requested_at is not null) then
 return jsonb_build_object('outcome','EXISTING','snapshot',private.guincho_snapshot(a.id),'server_now',now()); end if;
 if a.revision is distinct from p_expected_revision then
 return jsonb_build_object('outcome','REVISION_CONFLICT','snapshot',private.guincho_snapshot(a.id),'server_now',now()); end if;
 if not exists(select 1 from public.payments p where p.request_id=search_uuid and p.appointment_id=a.id
 and p.quote_version=a.quote_version and p.amount=a.customer_total_amount and private.payment_can_release(p,a.service_id)) then
 raise exception using errcode='22023',message='PAYMENT_REQUIRED'; end if;
 if p_action='ARRIVE' and a.status='ACCEPTED' then
  update public.appointments set arrived_at=now(),revision=revision+1,updated_at=now() where id=a.id;
 elsif p_action='START' and a.status='ACCEPTED' and a.arrived_at is not null then
  update public.appointments set status='IN_PROGRESS',service_started_at=now(),revision=revision+1,updated_at=now() where id=a.id;
  update public.service_searches set status='IN_PROGRESS' where id=search_uuid;
 elsif p_action='COMPLETE' and a.status='IN_PROGRESS' then
  update public.appointments set status='AWAITING_CLIENT_COMPLETION_CONFIRMATION',completion_requested_at=now(),
   revision=revision+1,updated_at=now() where id=a.id;
  update public.service_searches set status='AWAITING_CLIENT_COMPLETION_CONFIRMATION' where id=search_uuid;
  delete from public.provider_current_locations where appointment_id=a.id;
  delete from public.client_current_locations where appointment_id=a.id;
 else raise exception using errcode='22023',message='INVALID_TRANSITION'; end if;
 update public.service_searches set revision=revision+1,updated_at=now() where id=search_uuid;
 return jsonb_build_object('outcome','APPLIED','snapshot',private.guincho_snapshot(a.id),'server_now',now());
end; $$;
revoke all on function private.guincho_provider_transition(uuid,bigint,text) from public,anon,authenticated;
create function public.arrive_guincho_appointment(p_appointment_id uuid,p_expected_revision bigint,p_operation_id uuid)
returns jsonb language plpgsql security definer set search_path='' as $$
begin
 if p_operation_id is null then raise exception using errcode='22023',message='INVALID_INPUT'; end if;
 return private.guincho_provider_transition(p_appointment_id,p_expected_revision,'ARRIVE');
end; $$;
create function public.start_guincho_transport(p_appointment_id uuid,p_expected_revision bigint,p_operation_id uuid)
returns jsonb language plpgsql security definer set search_path='' as $$
begin
 if p_operation_id is null then raise exception using errcode='22023',message='INVALID_INPUT'; end if;
 return private.guincho_provider_transition(p_appointment_id,p_expected_revision,'START');
end; $$;
revoke all on function public.arrive_guincho_appointment(uuid,bigint,uuid),public.start_guincho_transport(uuid,bigint,uuid) from public,anon;
grant execute on function public.arrive_guincho_appointment(uuid,bigint,uuid),public.start_guincho_transport(uuid,bigint,uuid) to authenticated;
commit;
