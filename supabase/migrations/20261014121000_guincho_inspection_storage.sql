begin;
insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types) values('guincho-inspections','guincho-inspections',false,5242880,array['image/jpeg']);
create function private.inspection_object_read(p_path text) returns boolean language sql stable security definer set search_path='' as $$
 select exists(select 1 from public.guincho_inspection_photos f where f.object_path=p_path and private.inspection_participant(f.appointment_id));
$$;
create function private.inspection_object_insert(p_path text) returns boolean language sql stable security definer set search_path='' as $$
 select exists(select 1 from public.guincho_inspection_photos f join public.guincho_inspection_versions v on v.id=f.reserved_for_version_id
 where f.object_path=p_path and f.captured_by=auth.uid() and f.state='RESERVED' and f.abandoned_at is null and v.state='DRAFT'
 and private.inspection_writable(f.appointment_id));
$$;
revoke all on function private.inspection_object_read(text),private.inspection_object_insert(text) from public,anon;
grant usage on schema private to authenticated;
grant execute on function private.inspection_object_read(text),private.inspection_object_insert(text) to authenticated;
create policy guincho_inspection_read on storage.objects for select to authenticated using(bucket_id='guincho-inspections' and private.inspection_object_read(name));
create policy guincho_inspection_insert on storage.objects for insert to authenticated with check(bucket_id='guincho-inspections' and private.inspection_object_insert(name));
-- Restrictive policies also constrain any broad pre-existing permissive policy.
create policy guincho_inspection_read_restrict on storage.objects as restrictive for select to authenticated using(bucket_id<>'guincho-inspections' or (auth.uid() is not null and private.inspection_object_read(name)));
create policy guincho_inspection_insert_restrict on storage.objects as restrictive for insert to authenticated with check(bucket_id<>'guincho-inspections' or (auth.uid() is not null and private.inspection_object_insert(name)));
create policy guincho_inspection_no_update on storage.objects as restrictive for update to anon,authenticated using(bucket_id<>'guincho-inspections') with check(bucket_id<>'guincho-inspections');
create policy guincho_inspection_no_delete on storage.objects as restrictive for delete to anon,authenticated using(bucket_id<>'guincho-inspections');
create policy guincho_inspection_anon_no_read on storage.objects as restrictive for select to anon using(bucket_id<>'guincho-inspections');
create policy guincho_inspection_anon_no_insert on storage.objects as restrictive for insert to anon with check(bucket_id<>'guincho-inspections');
commit;
