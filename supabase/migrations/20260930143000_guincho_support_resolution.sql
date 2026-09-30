begin;
create function public.dashboard_get_guincho_dispute(p_dispute_id uuid) returns jsonb
language plpgsql stable security definer set search_path='' as $$
declare d public.appointment_disputes%rowtype; a public.appointments%rowtype; pay jsonb;
begin
 if not coalesce(private.dashboard_access_at_least('MANAGER'),false) then raise exception using errcode='42501',message='STAFF_REQUIRED'; end if;
 select * into d from public.appointment_disputes where id=p_dispute_id;
 if d.id is null then raise exception using errcode='P0002',message='DISPUTE_NOT_FOUND'; end if;
 select * into a from public.appointments where id=d.appointment_id;
 select jsonb_build_object('status',p.status,'amount',p.amount::text,'support_required',p.support_required)
 into pay from public.payments p where p.request_id=a.dispatch_search_id and not p.is_compensation order by p.created_at desc limit 1;
 return to_jsonb(d)||jsonb_build_object('appointment_status',a.status,'provider_name',(select name from public.providers where id=a.provider_id),
 'trip',a.guincho_trip_snapshot,'payment',pay);
end; $$;
create function public.dashboard_list_guincho_disputes(p_status text default 'OPEN',p_limit integer default 20,p_offset integer default 0)
returns setof jsonb language plpgsql stable security definer set search_path='' as $$
declare dispute_id uuid;
begin
 if not coalesce(private.dashboard_access_at_least('MANAGER'),false) then raise exception using errcode='42501',message='STAFF_REQUIRED'; end if;
 if p_status is not null and p_status not in ('OPEN','RESOLVED') then raise exception using errcode='22023',message='INVALID_INPUT'; end if;
 for dispute_id in select id from public.appointment_disputes where p_status is null or status=p_status
 order by opened_at desc,id desc limit greatest(1,least(coalesce(p_limit,20),100)) offset greatest(0,coalesce(p_offset,0)) loop
 return next public.dashboard_get_guincho_dispute(dispute_id); end loop;
end; $$;
create function public.dashboard_resolve_guincho_dispute(p_dispute_id uuid,p_expected_revision bigint,p_operation_id uuid,p_decision text,p_justification text)
returns jsonb language plpgsql security definer set search_path='' as $$
declare d public.appointment_disputes%rowtype; a public.appointments%rowtype; search_uuid uuid;
begin
 if not coalesce(private.dashboard_access_at_least('MANAGER'),false) then raise exception using errcode='42501',message='STAFF_REQUIRED'; end if;
 if p_operation_id is null or p_decision is null or p_decision not in ('COMPLETE','CANCEL_FULL_REFUND')
 or p_justification is null or char_length(btrim(p_justification)) not between 1 and 1000 then
 raise exception using errcode='22023',message='JUSTIFICATION_REQUIRED'; end if;
 select a.dispatch_search_id into search_uuid from public.appointment_disputes d join public.appointments a on a.id=d.appointment_id where d.id=p_dispute_id;
 if search_uuid is null then raise exception using errcode='P0002',message='DISPUTE_NOT_FOUND'; end if;
 perform 1 from public.service_searches where id=search_uuid for update;
 select a.* into a from public.appointments a join public.appointment_disputes d on d.appointment_id=a.id where d.id=p_dispute_id for update of a;
 select * into d from public.appointment_disputes where id=p_dispute_id for update;
 if d.status='RESOLVED' then return jsonb_build_object('outcome','EXISTING','dispute',public.dashboard_get_guincho_dispute(d.id)); end if;
 if d.revision is distinct from p_expected_revision then return jsonb_build_object('outcome','REVISION_CONFLICT','dispute',public.dashboard_get_guincho_dispute(d.id)); end if;
 if a.status<>'DISPUTED' then raise exception using errcode='22023',message='INVALID_TRANSITION'; end if;
 update public.appointment_disputes set status='RESOLVED',resolution=p_decision,justification=btrim(p_justification),
 resolved_by=auth.uid(),resolved_at=now(),revision=revision+1 where id=d.id;
 update public.appointments set status=case when p_decision='COMPLETE' then 'COMPLETED' else 'CANCELLED' end,
 ended_at=now(),cancelled_at=case when p_decision='CANCEL_FULL_REFUND' then now() else cancelled_at end,
 cancelled_by=case when p_decision='CANCEL_FULL_REFUND' then 'SYSTEM' else cancelled_by end,
 cancellation_reason=case when p_decision='CANCEL_FULL_REFUND' then 'SUPPORT_FULL_REFUND' else cancellation_reason end,
 revision=revision+1,updated_at=now() where id=a.id;
 -- Only this search changes. Cancellation trigger queues the full payment amount for refund.
 update public.service_searches set status=case when p_decision='COMPLETE' then 'COMPLETED' else 'CANCELLED' end,
 ended_at=now(),revision=revision+1,updated_at=now(),search_latitude=null,search_longitude=null,search_point=null,search_accuracy_m=null
 where id=search_uuid;
 perform public.record_dashboard_action('GUINCHO_DISPUTE_'||p_decision,'appointment_dispute',d.id);
 return jsonb_build_object('outcome','RESOLVED','dispute',public.dashboard_get_guincho_dispute(d.id));
end; $$;
revoke all on function public.dashboard_get_guincho_dispute(uuid),public.dashboard_list_guincho_disputes(text,integer,integer),
public.dashboard_resolve_guincho_dispute(uuid,bigint,uuid,text,text) from public,anon;
grant execute on function public.dashboard_get_guincho_dispute(uuid),public.dashboard_list_guincho_disputes(text,integer,integer),
public.dashboard_resolve_guincho_dispute(uuid,bigint,uuid,text,text) to authenticated;
commit;
