create function public.begin_my_registration_correction(p_case_id uuid,p_operation_id uuid,p_expected_revision bigint) returns jsonb language plpgsql security definer set search_path='' as $$
declare c private.registration_cases; s private.registration_submissions; sid uuid; result jsonb; payload jsonb:=jsonb_build_object('case',p_case_id);
begin
 result:=private.registration_replay(p_operation_id,'correct',payload); if result is not null then return result; end if;
 c:=private.registration_case_lock(p_case_id,p_expected_revision);
 if c.status<>'CORRECTION_REQUESTED' then raise exception using errcode='22023',message='INVALID_STATE'; end if;
 select * into s from private.registration_submissions where id=c.current_submission_id;
 if s.state='DRAFT' then return private.registration_receipt(p_operation_id,'correct',payload,public.get_my_registration(c.account_type)); end if;
 insert into private.registration_submissions(case_id,version,snapshot) values(c.id,s.version+1,s.snapshot) returning id into sid;
 insert into private.registration_submission_evidence select sid,evidence_id,kind,vehicle_reference_id from private.registration_submission_evidence where submission_id=s.id;
 update private.registration_cases set current_submission_id=sid,revision=revision+1 where id=c.id;
 return private.registration_receipt(p_operation_id,'correct',payload,public.get_my_registration(c.account_type));
end $$;
revoke all on function public.begin_my_registration_correction(uuid,uuid,bigint) from public,anon;
grant execute on function public.begin_my_registration_correction(uuid,uuid,bigint) to authenticated;
