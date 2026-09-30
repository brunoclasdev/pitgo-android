begin;
create function public.request_guincho_completion(p_appointment_id uuid,p_expected_revision bigint,p_operation_id uuid)
returns jsonb language plpgsql security definer set search_path='' as $$
begin
 if p_operation_id is null then raise exception using errcode='22023',message='INVALID_INPUT'; end if;
 return private.guincho_provider_transition(p_appointment_id,p_expected_revision,'COMPLETE');
end; $$;
create function public.confirm_guincho_completion(p_appointment_id uuid,p_expected_revision bigint,p_operation_id uuid)
returns jsonb language plpgsql security definer set search_path='' as $$
declare a public.appointments%rowtype; search_uuid uuid;
begin
 if p_operation_id is null then raise exception using errcode='22023',message='INVALID_INPUT'; end if;
 select dispatch_search_id into search_uuid from public.appointments where id=p_appointment_id
 and client_id=auth.uid() and guincho_quote_id is not null;
 if search_uuid is null then raise exception using errcode='42501',message='CLIENT_REQUIRED'; end if;
 perform 1 from public.service_searches where id=search_uuid for update;
 select * into a from public.appointments where id=p_appointment_id for update;
 if a.status='COMPLETED' then return jsonb_build_object('outcome','EXISTING','snapshot',private.guincho_snapshot(a.id),'server_now',now()); end if;
 if a.revision is distinct from p_expected_revision then
 return jsonb_build_object('outcome','REVISION_CONFLICT','snapshot',private.guincho_snapshot(a.id),'server_now',now()); end if;
 if a.status<>'AWAITING_CLIENT_COMPLETION_CONFIRMATION' or exists(select 1 from public.appointment_disputes where appointment_id=a.id) then
 raise exception using errcode='22023',message='INVALID_TRANSITION'; end if;
 update public.appointments set status='COMPLETED',client_completion_confirmed_at=now(),ended_at=now(),revision=revision+1,updated_at=now() where id=a.id;
 update public.service_searches set status='COMPLETED',ended_at=now(),revision=revision+1,updated_at=now(),
 search_latitude=null,search_longitude=null,search_point=null,search_accuracy_m=null where id=search_uuid;
 delete from public.appointment_route_snapshots where appointment_id=a.id;
 delete from public.provider_current_locations where appointment_id=a.id;
 delete from public.client_current_locations where appointment_id=a.id;
 perform private.apply_pending_provider_mode(a.provider_id);
 return jsonb_build_object('outcome','COMPLETED','snapshot',private.guincho_snapshot(a.id),'server_now',now());
end; $$;
revoke all on function public.request_guincho_completion(uuid,bigint,uuid),public.confirm_guincho_completion(uuid,bigint,uuid) from public,anon;
grant execute on function public.request_guincho_completion(uuid,bigint,uuid),public.confirm_guincho_completion(uuid,bigint,uuid) to authenticated;
commit;
