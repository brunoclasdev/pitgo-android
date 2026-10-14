begin;
create function public.submit_guincho_inspection(p_appointment_id uuid,p_expected_revision bigint,p_operation_id uuid,p_version_id uuid) returns jsonb language plpgsql security definer set search_path='' as $$
#variable_conflict use_column
declare a public.appointments%rowtype; v public.guincho_inspection_versions%rowtype;
begin
 a:=private.inspection_lock(p_appointment_id,'PROVIDER');
 if p_operation_id is null then raise exception using errcode='22023',message='INVALID_INPUT'; end if;
 select * into v from public.guincho_inspection_versions where appointment_id=a.id and submission_operation_id=p_operation_id;
 if v.id is not null then
  if v.id<>p_version_id then raise exception using errcode='22023',message='OPERATION_CONFLICT'; end if;
  return private.inspection_result(a.id,'EXISTING');
 end if;
 if a.revision is distinct from p_expected_revision then return private.inspection_result(a.id,'REVISION_CONFLICT'); end if;
 perform private.inspection_require_draft(a.id,p_version_id);
 if (select count(distinct point) from public.guincho_inspection_version_photos where version_id=p_version_id and selected and point<>'DETAIL')<>4 then raise exception using errcode='22023',message='REQUIRED_POINTS_MISSING'; end if;
 if exists(select 1 from public.guincho_inspection_photos where reserved_for_version_id=p_version_id and state='RESERVED' and abandoned_at is null) then raise exception using errcode='22023',message='UPLOAD_PENDING'; end if;
 if exists(select 1 from public.guincho_inspection_version_photos m join public.guincho_inspection_photos f on f.id=m.photo_id left join storage.objects o on o.bucket_id='guincho-inspections' and o.name=f.object_path where m.version_id=p_version_id and m.selected and (f.state<>'READY' or o.id is null)) then raise exception using errcode='22023',message='PHOTO_NOT_SAVED'; end if;
 update public.guincho_inspection_versions set state='SUBMITTED',submitted_at=now(),submission_operation_id=p_operation_id where id=p_version_id;
 perform private.inspection_bump(a.id); return private.inspection_result(a.id,'APPLIED');
end $$;
revoke all on function public.submit_guincho_inspection(uuid,bigint,uuid,uuid) from public,anon;
grant execute on function public.submit_guincho_inspection(uuid,bigint,uuid,uuid) to authenticated;
commit;
