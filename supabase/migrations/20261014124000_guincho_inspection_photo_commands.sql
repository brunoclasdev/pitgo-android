begin;
create function private.inspection_require_draft(p_id uuid,p_version uuid) returns void language plpgsql security definer set search_path='' as $$
begin
 if not private.inspection_writable(p_id) then raise exception using errcode='22023',message='APPOINTMENT_CLOSED'; end if;
 if not exists(select 1 from public.guincho_inspection_versions v where v.id=p_version and v.appointment_id=p_id and v.state='DRAFT'
 and v.version_number=(select max(version_number) from public.guincho_inspection_versions where appointment_id=p_id)) then raise exception using errcode='22023',message='VERSION_CONFLICT'; end if;
end $$;
create function public.prepare_guincho_inspection_photo(p_appointment_id uuid,p_expected_revision bigint,p_operation_id uuid,p_version_id uuid,p_point text,p_captured_at timestamptz,p_byte_size bigint,p_caption text default null,p_replaces_photo_id uuid default null)
returns jsonb language plpgsql security definer set search_path='' as $$
#variable_conflict use_column
declare a public.appointments%rowtype; f public.guincho_inspection_photos%rowtype; photo_uuid uuid:=gen_random_uuid(); caption_value text:=nullif(btrim(p_caption),'');
begin
 a:=private.inspection_lock(p_appointment_id,'PROVIDER');
 if p_operation_id is null or p_point is null or p_point not in ('FRONT','REAR','LEFT','RIGHT','DETAIL') or p_captured_at is null or p_byte_size is null or p_byte_size not between 1 and 5242880 or char_length(caption_value)>1000 then raise exception using errcode='22023',message='INVALID_INPUT'; end if;
 select * into f from public.guincho_inspection_photos where appointment_id=a.id and reserve_operation_id=p_operation_id;
 if f.id is not null then
  if f.reserved_for_version_id<>p_version_id or f.point<>p_point or f.caption is distinct from caption_value or f.captured_at<>p_captured_at or f.byte_size<>p_byte_size or f.replaces_photo_id is distinct from p_replaces_photo_id then raise exception using errcode='22023',message='OPERATION_CONFLICT'; end if;
  return private.inspection_result(a.id,'EXISTING')||jsonb_build_object('photo',to_jsonb(f));
 end if;
 if a.revision is distinct from p_expected_revision then return private.inspection_result(a.id,'REVISION_CONFLICT'); end if;
 perform private.inspection_require_draft(a.id,p_version_id);
 if p_replaces_photo_id is not null and not exists(select 1 from public.guincho_inspection_version_photos m where m.version_id=p_version_id and m.photo_id=p_replaces_photo_id and m.appointment_id=a.id and m.point=p_point and m.selected) then raise exception using errcode='22023',message='VERSION_CONFLICT'; end if;
 if p_point<>'DETAIL' and exists(select 1 from public.guincho_inspection_version_photos where version_id=p_version_id and point=p_point and selected and photo_id is distinct from p_replaces_photo_id) then raise exception using errcode='22023',message='VERSION_CONFLICT'; end if;
 if exists(select 1 from public.guincho_inspection_photos where reserved_for_version_id=p_version_id and state='RESERVED' and abandoned_at is null and (point=p_point and p_point<>'DETAIL' or replaces_photo_id=p_replaces_photo_id)) then raise exception using errcode='22023',message='UPLOAD_PENDING'; end if;
 insert into public.guincho_inspection_photos(id,appointment_id,reserved_for_version_id,point,caption,captured_by,captured_by_name,captured_at,byte_size,reserve_operation_id,replaces_photo_id,object_path)
 values(photo_uuid,a.id,p_version_id,p_point,caption_value,auth.uid(),(select name from public.providers where id=a.provider_id),p_captured_at,p_byte_size,p_operation_id,p_replaces_photo_id,a.id::text||'/'||photo_uuid::text||'.jpg') returning * into f;
 perform private.inspection_bump(a.id);
 return private.inspection_result(a.id,'APPLIED')||jsonb_build_object('photo',to_jsonb(f));
end $$;
create function public.confirm_guincho_inspection_photo(p_appointment_id uuid,p_expected_revision bigint,p_operation_id uuid,p_photo_id uuid)
returns jsonb language plpgsql security definer set search_path='' as $$
#variable_conflict use_column
declare a public.appointments%rowtype; f public.guincho_inspection_photos%rowtype; obj storage.objects%rowtype;
begin
 a:=private.inspection_lock(p_appointment_id,'PROVIDER');
 if p_operation_id is null then raise exception using errcode='22023',message='INVALID_INPUT'; end if;
 if exists(select 1 from public.guincho_inspection_photos where appointment_id=a.id and confirm_operation_id=p_operation_id and id<>p_photo_id) then raise exception using errcode='22023',message='OPERATION_CONFLICT'; end if;
 select * into f from public.guincho_inspection_photos where id=p_photo_id and appointment_id=a.id for update;
 if f.id is null then raise exception using errcode='22023',message='PHOTO_NOT_SAVED'; end if;
 if f.state='READY' then
  if f.confirm_operation_id is distinct from p_operation_id then raise exception using errcode='22023',message='OPERATION_CONFLICT'; end if;
  return private.inspection_result(a.id,'EXISTING');
 end if;
 if a.revision is distinct from p_expected_revision then return private.inspection_result(a.id,'REVISION_CONFLICT'); end if;
 perform private.inspection_require_draft(a.id,f.reserved_for_version_id);
 if f.abandoned_at is not null then raise exception using errcode='22023',message='INVALID_TRANSITION'; end if;
 select * into obj from storage.objects where bucket_id='guincho-inspections' and name=f.object_path;
 if obj.id is null then raise exception using errcode='22023',message='PHOTO_NOT_SAVED'; end if;
 if obj.owner_id is distinct from auth.uid()::text or obj.metadata->>'mimetype' is distinct from 'image/jpeg' or (obj.metadata->>'size')::bigint is distinct from f.byte_size then raise exception using errcode='22023',message='INVALID_IMAGE'; end if;
 if f.replaces_photo_id is not null and not exists(select 1 from public.guincho_inspection_version_photos where version_id=f.reserved_for_version_id and photo_id=f.replaces_photo_id and point=f.point and selected) then raise exception using errcode='22023',message='VERSION_CONFLICT'; end if;
 if f.point<>'DETAIL' and exists(select 1 from public.guincho_inspection_version_photos where version_id=f.reserved_for_version_id and point=f.point and selected and photo_id is distinct from f.replaces_photo_id) then raise exception using errcode='22023',message='VERSION_CONFLICT'; end if;
 update public.guincho_inspection_photos set state='READY',saved_at=now(),confirm_operation_id=p_operation_id where id=f.id;
 if f.replaces_photo_id is not null then update public.guincho_inspection_version_photos set selected=false where version_id=f.reserved_for_version_id and photo_id=f.replaces_photo_id; end if;
 insert into public.guincho_inspection_version_photos(appointment_id,version_id,photo_id,point,display_order) values(a.id,f.reserved_for_version_id,f.id,f.point,f.photo_order);
 perform private.inspection_bump(a.id);
 return private.inspection_result(a.id,'APPLIED');
end $$;
create function public.discard_guincho_inspection_photo(p_appointment_id uuid,p_expected_revision bigint,p_operation_id uuid,p_photo_id uuid)
returns jsonb language plpgsql security definer set search_path='' as $$
#variable_conflict use_column
declare a public.appointments%rowtype; f public.guincho_inspection_photos%rowtype;
begin
 a:=private.inspection_lock(p_appointment_id,'PROVIDER');
 if p_operation_id is null then raise exception using errcode='22023',message='INVALID_INPUT'; end if;
 if exists(select 1 from public.guincho_inspection_photos where appointment_id=a.id and discard_operation_id=p_operation_id and id<>p_photo_id) then raise exception using errcode='22023',message='OPERATION_CONFLICT'; end if;
 select * into f from public.guincho_inspection_photos where id=p_photo_id and appointment_id=a.id for update;
 if f.id is null then raise exception using errcode='22023',message='PHOTO_NOT_SAVED'; end if;
 if f.discard_operation_id=p_operation_id then return private.inspection_result(a.id,'EXISTING'); end if;
 if a.revision is distinct from p_expected_revision then return private.inspection_result(a.id,'REVISION_CONFLICT'); end if;
 perform private.inspection_require_draft(a.id,f.reserved_for_version_id);
 if f.state<>'RESERVED' or f.abandoned_at is not null then raise exception using errcode='22023',message='INVALID_TRANSITION'; end if;
 update public.guincho_inspection_photos set abandoned_at=now(),discard_operation_id=p_operation_id where id=f.id;
 perform private.inspection_bump(a.id); return private.inspection_result(a.id,'APPLIED');
end $$;
create function public.unselect_guincho_inspection_photo(p_appointment_id uuid,p_expected_revision bigint,p_operation_id uuid,p_photo_id uuid)
returns jsonb language plpgsql security definer set search_path='' as $$
#variable_conflict use_column
declare a public.appointments%rowtype; v uuid; m public.guincho_inspection_version_photos%rowtype;
begin
 a:=private.inspection_lock(p_appointment_id,'PROVIDER');
 if p_operation_id is null then raise exception using errcode='22023',message='INVALID_INPUT'; end if;
 select * into m from public.guincho_inspection_version_photos where appointment_id=a.id and unselect_operation_id=p_operation_id;
 if m.photo_id is not null then
  if m.photo_id<>p_photo_id then raise exception using errcode='22023',message='OPERATION_CONFLICT'; end if;
  return private.inspection_result(a.id,'EXISTING');
 end if;
 if a.revision is distinct from p_expected_revision then return private.inspection_result(a.id,'REVISION_CONFLICT'); end if;
 select id into v from public.guincho_inspection_versions where appointment_id=a.id order by version_number desc limit 1;
 perform private.inspection_require_draft(a.id,v);
 update public.guincho_inspection_version_photos set selected=false,unselect_operation_id=p_operation_id where version_id=v and photo_id=p_photo_id and selected;
 if not found then raise exception using errcode='22023',message='INVALID_TRANSITION'; end if;
 perform private.inspection_bump(a.id); return private.inspection_result(a.id,'APPLIED');
end $$;
revoke all on function private.inspection_require_draft(uuid,uuid) from public,anon,authenticated;
revoke all on function public.prepare_guincho_inspection_photo(uuid,bigint,uuid,uuid,text,timestamptz,bigint,text,uuid),public.confirm_guincho_inspection_photo(uuid,bigint,uuid,uuid),public.discard_guincho_inspection_photo(uuid,bigint,uuid,uuid),public.unselect_guincho_inspection_photo(uuid,bigint,uuid,uuid) from public,anon;
grant execute on function public.prepare_guincho_inspection_photo(uuid,bigint,uuid,uuid,text,timestamptz,bigint,text,uuid),public.confirm_guincho_inspection_photo(uuid,bigint,uuid,uuid),public.discard_guincho_inspection_photo(uuid,bigint,uuid,uuid),public.unselect_guincho_inspection_photo(uuid,bigint,uuid,uuid) to authenticated;
commit;
