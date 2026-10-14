begin;
create function public.decide_guincho_inspection(p_appointment_id uuid,p_expected_revision bigint,p_operation_id uuid,p_version_id uuid,p_decision text,p_reason text default null) returns jsonb language plpgsql security definer set search_path='' as $$
#variable_conflict use_column
declare a public.appointments%rowtype; v public.guincho_inspection_versions%rowtype; reason_value text:=nullif(btrim(p_reason),''); target text;
begin
 a:=private.inspection_lock(p_appointment_id,'CLIENT');
 if p_operation_id is null or p_decision is null or p_decision not in ('APPROVE','REQUEST_CORRECTION') then raise exception using errcode='22023',message='INVALID_INPUT'; end if;
 if p_decision='REQUEST_CORRECTION' and (reason_value is null or char_length(reason_value)>1000) then raise exception using errcode='22023',message='REASON_REQUIRED'; end if;
 target:=case when p_decision='APPROVE' then 'APPROVED' else 'CORRECTION_REQUESTED' end;
 select * into v from public.guincho_inspection_versions where appointment_id=a.id and decision_operation_id=p_operation_id;
 if v.id is not null then
  if v.id<>p_version_id or v.state<>target or v.correction_reason is distinct from (case when target='CORRECTION_REQUESTED' then reason_value end) then raise exception using errcode='22023',message='OPERATION_CONFLICT'; end if;
  return private.inspection_result(a.id,'EXISTING');
 end if;
 if a.revision is distinct from p_expected_revision then return private.inspection_result(a.id,'REVISION_CONFLICT'); end if;
 if not private.inspection_writable(a.id) then raise exception using errcode='22023',message='APPOINTMENT_CLOSED'; end if;
 select * into v from public.guincho_inspection_versions where appointment_id=a.id order by version_number desc limit 1 for update;
 if v.id is distinct from p_version_id or v.state<>'SUBMITTED' then raise exception using errcode='22023',message='VERSION_CONFLICT'; end if;
 update public.guincho_inspection_versions set state=target,decided_by=auth.uid(),decided_by_name=coalesce(nullif(a.guincho_trip_snapshot->>'client_name',''),'Cliente responsável'),decided_at=now(),decision_operation_id=p_operation_id,
 correction_reason=case when target='CORRECTION_REQUESTED' then reason_value end where id=v.id;
 perform private.inspection_bump(a.id); return private.inspection_result(a.id,'APPLIED');
end $$;
create function public.begin_guincho_inspection_correction(p_appointment_id uuid,p_expected_revision bigint,p_parent_version_id uuid) returns jsonb language plpgsql security definer set search_path='' as $$
#variable_conflict use_column
declare a public.appointments%rowtype; v public.guincho_inspection_versions%rowtype; new_id uuid;
begin
 a:=private.inspection_lock(p_appointment_id,'PROVIDER');
 if exists(select 1 from public.guincho_inspection_versions where appointment_id=a.id and parent_version_id=p_parent_version_id) then return private.inspection_result(a.id,'EXISTING'); end if;
 if a.revision is distinct from p_expected_revision then return private.inspection_result(a.id,'REVISION_CONFLICT'); end if;
 if not private.inspection_writable(a.id) then raise exception using errcode='22023',message='APPOINTMENT_CLOSED'; end if;
 select * into v from public.guincho_inspection_versions where appointment_id=a.id order by version_number desc limit 1 for update;
 if v.id is distinct from p_parent_version_id or v.state<>'CORRECTION_REQUESTED' then raise exception using errcode='22023',message='VERSION_CONFLICT'; end if;
 insert into public.guincho_inspection_versions(appointment_id,version_number,parent_version_id,created_by) values(a.id,v.version_number+1,v.id,auth.uid()) returning id into new_id;
 insert into public.guincho_inspection_version_photos(appointment_id,version_id,photo_id,point,display_order)
 select a.id,new_id,photo_id,point,display_order from public.guincho_inspection_version_photos where version_id=v.id and selected;
 perform private.inspection_bump(a.id); return private.inspection_result(a.id,'APPLIED');
end $$;
revoke all on function public.decide_guincho_inspection(uuid,bigint,uuid,uuid,text,text),public.begin_guincho_inspection_correction(uuid,bigint,uuid) from public,anon;
grant execute on function public.decide_guincho_inspection(uuid,bigint,uuid,uuid,text,text),public.begin_guincho_inspection_correction(uuid,bigint,uuid) to authenticated;
commit;
