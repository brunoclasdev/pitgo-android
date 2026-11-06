insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types) values('registration-documents','registration-documents',false,10485760,array['image/jpeg','image/png']);
create function private.registration_storage_insert(p_path text) returns boolean language sql stable security definer set search_path='' as $$
 select exists(select 1 from private.registration_evidence e join private.registration_submissions s on s.id=e.origin_submission_id
 join private.registration_cases c on c.id=e.case_id join private.registration_submission_evidence se on se.evidence_id=e.id and se.submission_id=s.id
 where e.storage_path=p_path and e.user_id=auth.uid() and e.upload_state='RESERVED' and s.state='DRAFT' and c.current_submission_id=s.id);
$$;
create function private.registration_storage_read(p_path text) returns boolean language sql stable security definer set search_path='' as $$
 select exists(select 1 from private.registration_evidence e where e.storage_path=p_path and (e.user_id=auth.uid() or private.registration_staff()));
$$;
grant execute on function private.registration_storage_insert(text),private.registration_storage_read(text) to authenticated;
revoke all on function private.registration_storage_insert(text),private.registration_storage_read(text) from public,anon;
create policy registration_upload on storage.objects for insert to authenticated with check(bucket_id='registration-documents' and private.registration_storage_insert(name));
create policy registration_read on storage.objects for select to authenticated using(bucket_id='registration-documents' and private.registration_storage_read(name));
create policy registration_insert_boundary on storage.objects as restrictive for insert to public with check(bucket_id<>'registration-documents' or (auth.uid() is not null and private.registration_storage_insert(name)));
create policy registration_select_boundary on storage.objects as restrictive for select to public using(bucket_id<>'registration-documents' or (auth.uid() is not null and private.registration_storage_read(name)));
create policy registration_update_boundary on storage.objects as restrictive for update to public using(bucket_id<>'registration-documents') with check(bucket_id<>'registration-documents');
create policy registration_delete_boundary on storage.objects as restrictive for delete to public using(bucket_id<>'registration-documents');
