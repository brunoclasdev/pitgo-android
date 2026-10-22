begin;
create or replace function private.inspection_immutable() returns trigger language plpgsql set search_path='' as $$
begin
 if tg_op='DELETE' then raise exception using errcode='22023',message='INSPECTION_IMMUTABLE'; end if;
 if tg_table_name='guincho_inspection_versions' then
  if old.state in ('APPROVED','CORRECTION_REQUESTED') or (old.state='SUBMITTED' and (new.state not in ('APPROVED','CORRECTION_REQUESTED')
   or (to_jsonb(new)-array['state','decided_by','decided_by_name','decided_at','decision_operation_id','correction_reason'])<>(to_jsonb(old)-array['state','decided_by','decided_by_name','decided_at','decision_operation_id','correction_reason']))) then
   raise exception using errcode='22023',message='INSPECTION_IMMUTABLE'; end if;
 elsif tg_table_name='guincho_inspection_photos' then
  if old.state='READY' or old.abandoned_at is not null or
   (to_jsonb(new)-array['state','saved_at','confirm_operation_id','abandoned_at','discard_operation_id'])
   is distinct from (to_jsonb(old)-array['state','saved_at','confirm_operation_id','abandoned_at','discard_operation_id']) then raise exception using errcode='22023',message='INSPECTION_IMMUTABLE'; end if;
 end if;
 return new;
end $$;
create or replace function private.inspection_manifest_guard() returns trigger language plpgsql set search_path='' as $$
begin
 if tg_op='DELETE' then raise exception using errcode='22023',message='INSPECTION_IMMUTABLE'; end if;
 if not exists(select 1 from public.guincho_inspection_versions v join public.guincho_inspection_photos p on p.id=new.photo_id and p.appointment_id=v.appointment_id
 where v.id=new.version_id and v.appointment_id=new.appointment_id and v.state='DRAFT' and p.state='READY' and p.point=new.point and p.detail_point is not distinct from new.detail_point) then
  raise exception using errcode='22023',message='INSPECTION_IMMUTABLE'; end if;
 if tg_op='UPDATE' and (to_jsonb(new)-array['selected','unselect_operation_id'])
 is distinct from (to_jsonb(old)-array['selected','unselect_operation_id']) then
  raise exception using errcode='22023',message='INSPECTION_IMMUTABLE';
 end if;
 return new;
end $$;
create or replace function public.begin_guincho_inspection_correction(p_appointment_id uuid,p_expected_revision bigint,p_parent_version_id uuid) returns jsonb language plpgsql security definer set search_path='' as $$
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
 insert into public.guincho_inspection_version_photos(appointment_id,version_id,photo_id,point,detail_point,display_order)
 select a.id,new_id,photo_id,point,detail_point,display_order from public.guincho_inspection_version_photos where version_id=v.id and selected;
 perform private.inspection_bump(a.id); return private.inspection_result(a.id,'APPLIED');
end $$;
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
  return private.inspection_result(a.id,'EXISTING')||jsonb_build_object('photo',to_jsonb(f));
 end if;
 if a.revision is distinct from p_expected_revision then return private.inspection_result(a.id,'REVISION_CONFLICT'); end if;
 perform private.inspection_require_draft(a.id,p_version_id);
 if p_replaces_photo_id is not null and not exists(select 1 from public.guincho_inspection_version_photos m where m.version_id=p_version_id and m.photo_id=p_replaces_photo_id and m.appointment_id=a.id and m.point=p_point and m.selected) then raise exception using errcode='22023',message='VERSION_CONFLICT'; end if;
 if p_point<>'DETAIL' and exists(select 1 from public.guincho_inspection_version_photos where version_id=p_version_id and point=p_point and selected and photo_id is distinct from p_replaces_photo_id) then raise exception using errcode='22023',message='VERSION_CONFLICT'; end if;
 if exists(select 1 from public.guincho_inspection_photos where reserved_for_version_id=p_version_id and state='RESERVED' and abandoned_at is null and (point=p_point and (p_point<>'DETAIL' or (category is not null and detail_point=category)) or replaces_photo_id=p_replaces_photo_id)) then raise exception using errcode='22023',message='UPLOAD_PENDING'; end if;
 insert into public.guincho_inspection_photos(id,appointment_id,reserved_for_version_id,point,detail_point,caption,captured_by,captured_by_name,captured_at,byte_size,reserve_operation_id,replaces_photo_id,object_path)
 values(photo_uuid,a.id,p_version_id,p_point,category,caption_value,auth.uid(),(select name from public.providers where id=a.provider_id),p_captured_at,p_byte_size,p_operation_id,p_replaces_photo_id,a.id::text||'/'||photo_uuid::text||'.jpg') returning * into f;
 perform private.inspection_bump(a.id);
 return private.inspection_result(a.id,'APPLIED')||jsonb_build_object('photo',to_jsonb(f));
end $$;
notify pgrst,'reload schema';
commit;
