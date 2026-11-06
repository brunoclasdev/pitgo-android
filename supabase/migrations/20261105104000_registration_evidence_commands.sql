create function public.reserve_my_registration_evidence(p_submission_id uuid,p_kind text,p_vehicle_reference_id uuid,p_mime_type text,p_byte_size bigint,p_operation_id uuid,p_expected_revision bigint) returns jsonb language plpgsql security definer set search_path='' as $$
declare s private.registration_submissions; c private.registration_cases; eid uuid:=gen_random_uuid(); path text; result jsonb;
 payload jsonb:=jsonb_build_object('submission',p_submission_id,'kind',p_kind,'vehicle',p_vehicle_reference_id,'mime',p_mime_type,'bytes',p_byte_size);
begin
 result:=private.registration_replay(p_operation_id,'reserve',payload); if result is not null then return result; end if;
 select * into s from private.registration_submissions where id=p_submission_id;
 c:=private.registration_case_lock(s.case_id,p_expected_revision);
 if c.current_submission_id<>s.id or s.state<>'DRAFT' then raise exception using errcode='22023',message='INVALID_STATE'; end if;
 if p_kind='VEHICLE_DOCUMENT' and not exists(select 1 from jsonb_array_elements(coalesce(s.snapshot->'vehicles','[]')) v where v->>'id'=p_vehicle_reference_id::text) then raise exception using errcode='22023',message='INVALID_VEHICLE'; end if;
 path:=auth.uid()::text||'/'||c.id||'/'||s.id||'/'||eid||case when p_mime_type='image/png' then '.png' else '.jpg' end;
 insert into private.registration_evidence(id,user_id,case_id,origin_submission_id,kind,vehicle_reference_id,storage_path,mime_type,byte_size)
 values(eid,auth.uid(),c.id,s.id,p_kind,p_vehicle_reference_id,path,p_mime_type,p_byte_size);
 delete from private.registration_submission_evidence where submission_id=s.id and kind=p_kind and vehicle_reference_id is not distinct from p_vehicle_reference_id;
 insert into private.registration_submission_evidence values(s.id,eid,p_kind,p_vehicle_reference_id);
 update private.registration_cases set revision=revision+1 where id=c.id;
 result:=jsonb_build_object('evidence_id',eid,'bucket','registration-documents','object_path',path,'revision',c.revision+1);
 return private.registration_receipt(p_operation_id,'reserve',payload,result);
end $$;
-- Accessible only to service_role; caller identity is verified by the Edge Function.
create function public.confirm_registration_evidence_internal(p_user_id uuid,p_evidence_id uuid,p_operation_id uuid,p_expected_revision bigint) returns jsonb language plpgsql security definer set search_path='' as $$
declare e private.registration_evidence; c private.registration_cases; result jsonb;
begin
 perform pg_advisory_xact_lock(hashtextextended(p_user_id::text||p_operation_id::text,0));
 select o.result into result from private.registration_operations o where actor_id=p_user_id and operation_id=p_operation_id and command='confirm' and payload=jsonb_build_object('evidence',p_evidence_id);
 if found then return result; end if;
 if exists(select 1 from private.registration_operations where actor_id=p_user_id and operation_id=p_operation_id) then raise exception using errcode='22023',message='OPERATION_KEY_REUSED'; end if;
 select * into e from private.registration_evidence where id=p_evidence_id and user_id=p_user_id;
 if not found then raise exception using errcode='42501',message='FORBIDDEN'; end if;
 select * into c from private.registration_cases where id=e.case_id for update;
 if p_expected_revision is null or c.revision is distinct from p_expected_revision then raise exception using errcode='40001',message='REVISION_CONFLICT'; end if;
 if c.current_submission_id<>e.origin_submission_id or not exists(select 1 from private.registration_submissions where id=e.origin_submission_id and state='DRAFT') or not exists(select 1 from private.registration_submission_evidence where submission_id=e.origin_submission_id and evidence_id=e.id) then raise exception using errcode='22023',message='INVALID_STATE'; end if;
 if not exists(select 1 from storage.objects where bucket_id='registration-documents' and name=e.storage_path and (metadata->>'size')::bigint=e.byte_size and metadata->>'mimetype'=e.mime_type) then raise exception using errcode='22023',message='UPLOAD_NOT_CONFIRMED'; end if;
 update private.registration_evidence set upload_state='RECEIVED',confirmed_at=now() where id=e.id;
 update private.registration_cases set revision=revision+1 where id=c.id;
 result:=jsonb_build_object('revision',c.revision+1,'evidence_id',e.id,'upload_state','RECEIVED');
 insert into private.registration_operations values(p_user_id,p_operation_id,'confirm',jsonb_build_object('evidence',p_evidence_id),result);
 return result;
end $$;
create function public.get_registration_evidence_internal(p_user_id uuid,p_evidence_id uuid) returns jsonb language sql stable security definer set search_path='' as $$
 select jsonb_build_object('storage_path',storage_path,'mime_type',mime_type,'byte_size',byte_size) from private.registration_evidence where id=p_evidence_id and user_id=p_user_id;
$$;
revoke all on function public.reserve_my_registration_evidence(uuid,text,uuid,text,bigint,uuid,bigint) from public,anon;
grant execute on function public.reserve_my_registration_evidence(uuid,text,uuid,text,bigint,uuid,bigint) to authenticated;
revoke all on function public.confirm_registration_evidence_internal(uuid,uuid,uuid,bigint),public.get_registration_evidence_internal(uuid,uuid) from public,anon,authenticated;
grant execute on function public.confirm_registration_evidence_internal(uuid,uuid,uuid,bigint),public.get_registration_evidence_internal(uuid,uuid) to service_role;
