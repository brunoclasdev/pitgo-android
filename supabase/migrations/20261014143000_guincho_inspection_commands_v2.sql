begin;
create function private.inspection_assets_ready(f public.guincho_inspection_photos) returns boolean language sql stable security definer set search_path='' as $$
 select exists(select 1 from storage.objects o where o.bucket_id='guincho-inspections' and o.name=f.object_path
 and o.owner_id=f.captured_by::text and o.metadata->>'mimetype'='image/jpeg' and (o.metadata->>'size')::bigint=f.byte_size)
 and (f.thumbnail_object_path is null or exists(select 1 from storage.objects o where o.bucket_id='guincho-inspections' and o.name=f.thumbnail_object_path
 and o.owner_id=f.captured_by::text and o.metadata->>'mimetype'='image/jpeg' and (o.metadata->>'size')::bigint=f.thumbnail_byte_size));
$$;
create function private.inspection_result_v2(p_id uuid,p_outcome text) returns jsonb language sql stable security definer set search_path='' as $$
 select private.inspection_result(p_id,p_outcome)||jsonb_build_object('inspection',public.get_guincho_inspection_v2(p_id));
$$;
revoke all on function private.inspection_assets_ready(public.guincho_inspection_photos),private.inspection_result_v2(uuid,text) from public,anon,authenticated;

create or replace function public.prepare_guincho_inspection_photo_v2(p_appointment_id uuid,p_expected_revision bigint,p_operation_id uuid,p_version_id uuid,p_point text,p_captured_at timestamptz,p_byte_size bigint,p_thumbnail_byte_size bigint,p_caption text default null,p_replaces_photo_id uuid default null,p_detail_point text default null)
returns jsonb language plpgsql security definer set search_path='' as $$
#variable_conflict use_column
declare a public.appointments%rowtype; f public.guincho_inspection_photos%rowtype; photo_uuid uuid:=gen_random_uuid(); caption_value text:=nullif(btrim(p_caption),'');
begin
 a:=private.inspection_lock(p_appointment_id,'PROVIDER');
 if p_operation_id is null or p_point is null or p_point not in ('FRONT','REAR','LEFT','RIGHT','DETAIL') or p_captured_at is null or p_byte_size is null or p_byte_size not between 1 and 5242880 or char_length(caption_value)>1000 then raise exception using errcode='22023',message='INVALID_INPUT'; end if;
 if p_thumbnail_byte_size is null or p_thumbnail_byte_size not between 1 and 262144 or
 (p_detail_point is not null and (p_point<>'DETAIL' or p_detail_point not in ('TRUNK_INTERIOR','INSTRUMENT_PANEL','FRONT_SEATS','REAR_SEATS','FULL_DASHBOARD','TOOL_KIT','SPARE_TIRE','FRONT_LEFT_TIRE','FRONT_RIGHT_TIRE','REAR_RIGHT_TIRE','REAR_LEFT_TIRE','ENGINE','BATTERY'))) then
  raise exception using errcode='22023',message='INVALID_IMAGE';
 end if;
 select * into f from public.guincho_inspection_photos where appointment_id=a.id and reserve_operation_id=p_operation_id;
 if f.id is not null then
  if f.detail_point is distinct from p_detail_point or f.thumbnail_byte_size is distinct from p_thumbnail_byte_size or f.reserved_for_version_id<>p_version_id or f.point<>p_point or f.caption is distinct from caption_value or f.captured_at<>p_captured_at or f.byte_size<>p_byte_size or f.replaces_photo_id is distinct from p_replaces_photo_id then raise exception using errcode='22023',message='OPERATION_CONFLICT'; end if;
  return private.inspection_result_v2(a.id,'EXISTING')||jsonb_build_object('photo',to_jsonb(f));
 end if;
 if a.revision is distinct from p_expected_revision then return private.inspection_result_v2(a.id,'REVISION_CONFLICT'); end if;
 perform private.inspection_require_draft(a.id,p_version_id);
 if p_replaces_photo_id is not null and not exists(select 1 from public.guincho_inspection_version_photos m where m.version_id=p_version_id and m.photo_id=p_replaces_photo_id and m.appointment_id=a.id and m.point=p_point and m.detail_point is not distinct from p_detail_point and m.selected) then raise exception using errcode='22023',message='VERSION_CONFLICT'; end if;
 if (p_point<>'DETAIL' or p_detail_point is not null) and exists(select 1 from public.guincho_inspection_version_photos where version_id=p_version_id and point=p_point and detail_point is not distinct from p_detail_point and selected and photo_id is distinct from p_replaces_photo_id) then raise exception using errcode='22023',message='VERSION_CONFLICT'; end if;
 if exists(select 1 from public.guincho_inspection_photos where reserved_for_version_id=p_version_id and state='RESERVED' and abandoned_at is null and (point=p_point and (p_point<>'DETAIL' or (p_detail_point is not null and detail_point=p_detail_point)) or replaces_photo_id=p_replaces_photo_id)) then raise exception using errcode='22023',message='UPLOAD_PENDING'; end if;
 insert into public.guincho_inspection_photos(id,appointment_id,reserved_for_version_id,point,detail_point,caption,captured_by,captured_by_name,captured_at,byte_size,reserve_operation_id,replaces_photo_id,object_path,thumbnail_object_path,thumbnail_byte_size)
 values(photo_uuid,a.id,p_version_id,p_point,p_detail_point,caption_value,auth.uid(),(select name from public.providers where id=a.provider_id),p_captured_at,p_byte_size,p_operation_id,p_replaces_photo_id,a.id::text||'/'||photo_uuid::text||'.jpg',a.id::text||'/'||photo_uuid::text||'.thumb.jpg',p_thumbnail_byte_size) returning * into f;
 perform private.inspection_bump(a.id);
 return private.inspection_result_v2(a.id,'APPLIED')||jsonb_build_object('photo',to_jsonb(f));
end $$;
create or replace function public.confirm_guincho_inspection_photo(p_appointment_id uuid,p_expected_revision bigint,p_operation_id uuid,p_photo_id uuid)
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
 if not private.inspection_assets_ready(f) then raise exception using errcode='22023',message='PHOTO_NOT_SAVED'; end if;
 if f.replaces_photo_id is not null and not exists(select 1 from public.guincho_inspection_version_photos where version_id=f.reserved_for_version_id and photo_id=f.replaces_photo_id and point=f.point and detail_point is not distinct from f.detail_point and selected) then raise exception using errcode='22023',message='VERSION_CONFLICT'; end if;
 if (f.point<>'DETAIL' or f.detail_point is not null) and exists(select 1 from public.guincho_inspection_version_photos where version_id=f.reserved_for_version_id and point=f.point and detail_point is not distinct from f.detail_point and selected and photo_id is distinct from f.replaces_photo_id) then raise exception using errcode='22023',message='VERSION_CONFLICT'; end if;
 update public.guincho_inspection_photos set state='READY',saved_at=now(),confirm_operation_id=p_operation_id where id=f.id;
 if f.replaces_photo_id is not null then update public.guincho_inspection_version_photos set selected=false where version_id=f.reserved_for_version_id and photo_id=f.replaces_photo_id; end if;
 insert into public.guincho_inspection_version_photos(appointment_id,version_id,photo_id,point,detail_point,display_order) values(a.id,f.reserved_for_version_id,f.id,f.point,f.detail_point,f.photo_order);
 perform private.inspection_bump(a.id);
 return private.inspection_result(a.id,'APPLIED');
end $$;
create or replace function public.submit_guincho_inspection(p_appointment_id uuid,p_expected_revision bigint,p_operation_id uuid,p_version_id uuid) returns jsonb language plpgsql security definer set search_path='' as $$
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
 if (select count(distinct point) from public.guincho_inspection_version_photos where version_id=p_version_id and selected and point in ('FRONT','REAR','LEFT','RIGHT'))<>4 then raise exception using errcode='22023',message='REQUIRED_POINTS_MISSING'; end if;
 if exists(select 1 from public.guincho_inspection_photos where reserved_for_version_id=p_version_id and state='RESERVED' and abandoned_at is null) then raise exception using errcode='22023',message='UPLOAD_PENDING'; end if;
 if exists(select 1 from public.guincho_inspection_version_photos m join public.guincho_inspection_photos f on f.id=m.photo_id left join storage.objects o on o.bucket_id='guincho-inspections' and o.name=f.object_path where m.version_id=p_version_id and m.selected and (f.state<>'READY' or not private.inspection_assets_ready(f))) then raise exception using errcode='22023',message='PHOTO_NOT_SAVED'; end if;
 update public.guincho_inspection_versions set state='SUBMITTED',submitted_at=now(),submission_operation_id=p_operation_id where id=p_version_id;
 perform private.inspection_bump(a.id); return private.inspection_result(a.id,'APPLIED');
end $$;

create function public.confirm_guincho_inspection_photo_v2(p_appointment_id uuid,p_expected_revision bigint,p_operation_id uuid,p_photo_id uuid)
returns jsonb language plpgsql security definer set search_path='' as $$
declare result jsonb;
begin
 result:=public.confirm_guincho_inspection_photo(p_appointment_id,p_expected_revision,p_operation_id,p_photo_id);
 return result||jsonb_build_object('inspection',public.get_guincho_inspection_v2(p_appointment_id));
end $$;
revoke all on function public.prepare_guincho_inspection_photo_v2(uuid,bigint,uuid,uuid,text,timestamptz,bigint,bigint,text,uuid,text),public.confirm_guincho_inspection_photo_v2(uuid,bigint,uuid,uuid) from public,anon;
grant execute on function public.prepare_guincho_inspection_photo_v2(uuid,bigint,uuid,uuid,text,timestamptz,bigint,bigint,text,uuid,text),public.confirm_guincho_inspection_photo_v2(uuid,bigint,uuid,uuid) to authenticated;
notify pgrst,'reload schema';
commit;
