begin;
create or replace function private.inspection_object_read(p_path text) returns boolean language sql stable security definer set search_path='' as $$
 select exists(select 1 from public.guincho_inspection_photos f where (f.object_path=p_path or f.thumbnail_object_path=p_path) and private.inspection_participant(f.appointment_id));
$$;
create or replace function private.inspection_object_insert(p_path text) returns boolean language sql stable security definer set search_path='' as $$
 select exists(select 1 from public.guincho_inspection_photos f join public.guincho_inspection_versions v on v.id=f.reserved_for_version_id
 where (f.object_path=p_path or f.thumbnail_object_path=p_path) and f.captured_by=auth.uid() and f.state='RESERVED' and f.abandoned_at is null and v.state='DRAFT'
 and private.inspection_writable(f.appointment_id));
$$;
notify pgrst,'reload schema';
commit;
