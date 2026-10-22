begin;
create or replace function public.prepare_guincho_inspection_photo(p_appointment_id uuid,p_expected_revision bigint,p_operation_id uuid,p_version_id uuid,p_point text,p_captured_at timestamptz,p_byte_size bigint,p_caption text default null,p_replaces_photo_id uuid default null)
returns jsonb language plpgsql security definer set search_path='' as $$
#variable_conflict use_column
declare a public.appointments%rowtype; f public.guincho_inspection_photos%rowtype; photo_uuid uuid:=gen_random_uuid(); caption_value text:=nullif(btrim(p_caption),''); category text;
begin
 a:=private.inspection_lock(p_appointment_id,'PROVIDER');
 if p_operation_id is null or p_point is null or p_point not in ('FRONT','REAR','LEFT','RIGHT','DETAIL') or p_captured_at is null or p_byte_size is null or p_byte_size not between 1 and 5242880 or char_length(caption_value)>1000 then raise exception using errcode='22023',message='INVALID_INPUT'; end if;
 if p_replaces_photo_id is not null then
  select detail_point into category from public.guincho_inspection_photos where id=p_replaces_photo_id and appointment_id=a.id;
 end if;
 select * into f from public.guincho_inspection_photos where appointment_id=a.id and reserve_operation_id=p_operation_id;
 if f.id is not null then
  if f.thumbnail_object_path is not null or f.detail_point is distinct from category or f.reserved_for_version_id<>p_version_id or f.point<>p_point or f.caption is distinct from caption_value or f.captured_at<>p_captured_at or f.byte_size<>p_byte_size or f.replaces_photo_id is distinct from p_replaces_photo_id then raise exception using errcode='22023',message='OPERATION_CONFLICT'; end if;
  return private.inspection_result(a.id,'EXISTING')||jsonb_build_object('photo',to_jsonb(f)-array['detail_point','thumbnail_object_path','thumbnail_byte_size']);
 end if;
 if a.revision is distinct from p_expected_revision then return private.inspection_result(a.id,'REVISION_CONFLICT'); end if;
 perform private.inspection_require_draft(a.id,p_version_id);
 if p_replaces_photo_id is not null and not exists(select 1 from public.guincho_inspection_version_photos m where m.version_id=p_version_id and m.photo_id=p_replaces_photo_id and m.appointment_id=a.id and m.point=p_point and m.selected) then raise exception using errcode='22023',message='VERSION_CONFLICT'; end if;
 if p_point<>'DETAIL' and exists(select 1 from public.guincho_inspection_version_photos where version_id=p_version_id and point=p_point and selected and photo_id is distinct from p_replaces_photo_id) then raise exception using errcode='22023',message='VERSION_CONFLICT'; end if;
 if exists(select 1 from public.guincho_inspection_photos where reserved_for_version_id=p_version_id and state='RESERVED' and abandoned_at is null and (point=p_point and (p_point<>'DETAIL' or (category is not null and detail_point=category)) or replaces_photo_id=p_replaces_photo_id)) then raise exception using errcode='22023',message='UPLOAD_PENDING'; end if;
 insert into public.guincho_inspection_photos(id,appointment_id,reserved_for_version_id,point,detail_point,caption,captured_by,captured_by_name,captured_at,byte_size,reserve_operation_id,replaces_photo_id,object_path)
 values(photo_uuid,a.id,p_version_id,p_point,category,caption_value,auth.uid(),(select name from public.providers where id=a.provider_id),p_captured_at,p_byte_size,p_operation_id,p_replaces_photo_id,a.id::text||'/'||photo_uuid::text||'.jpg') returning * into f;
 perform private.inspection_bump(a.id);
 return private.inspection_result(a.id,'APPLIED')||jsonb_build_object('photo',to_jsonb(f)-array['detail_point','thumbnail_object_path','thumbnail_byte_size']);
end $$;
notify pgrst,'reload schema';
commit;
