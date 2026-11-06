create function public.decide_registration_review(p_submission_id uuid,p_expected_revision bigint,p_operation_id uuid,p_result text,p_checked_evidence_ids uuid[],p_correction_items jsonb default '[]',p_reason text default null) returns jsonb language plpgsql security definer set search_path='' as $$
declare c private.registration_cases; s private.registration_submissions; result jsonb; ids uuid[];
 payload jsonb:=jsonb_build_object('submission',p_submission_id,'result',p_result,'checked',p_checked_evidence_ids,'items',p_correction_items,'reason',p_reason);
begin
 if not private.registration_staff() then raise exception using errcode='42501',message='FORBIDDEN'; end if;
 result:=private.registration_replay(p_operation_id,'decide',payload); if result is not null then return result; end if;
 select * into s from private.registration_submissions where id=p_submission_id;
 select * into c from private.registration_cases where id=s.case_id for update;
 if not found or c.user_id=auth.uid() then raise exception using errcode='42501',message='FORBIDDEN'; end if;
 if p_expected_revision is null or c.revision is distinct from p_expected_revision then raise exception using errcode='40001',message='REVISION_CONFLICT'; end if;
 if c.current_submission_id<>s.id or c.status<>'IN_REVIEW' or s.state<>'SUBMITTED' then raise exception using errcode='22023',message='SUBMISSION_NOT_CURRENT'; end if;
 if p_result='APPROVED' then
 perform private.validate_registration_submission(s.id);
 select array_agg(evidence_id order by evidence_id) into ids from private.registration_submission_evidence where submission_id=s.id;
 if ids is distinct from (select array_agg(distinct x order by x) from unnest(p_checked_evidence_ids) x) then raise exception using errcode='22023',message='INCOMPLETE_REVIEW'; end if;
 elsif p_result='CORRECTION_REQUESTED' then
 if coalesce(btrim(p_reason),'')='' or jsonb_typeof(p_correction_items)<>'array' or jsonb_array_length(p_correction_items)=0 then raise exception using errcode='22023',message='CORRECTION_REASON_REQUIRED'; end if;
 else raise exception using errcode='22023',message='INVALID_RESULT'; end if;
 insert into private.registration_reviews(submission_id,reviewer_user_id,result,checked_evidence_ids,correction_items,reason)
 values(s.id,auth.uid(),p_result,coalesce(p_checked_evidence_ids,'{}'),p_correction_items,p_reason);
 update private.registration_submissions set state=case when p_result='APPROVED' then 'APPROVED' else 'CORRECTION_REQUESTED' end,decided_at=now() where id=s.id;
 update private.registration_cases set status=p_result,revision=revision+1,
 approved_submission_id=case when p_result='APPROVED' then s.id else null end,
 activated_at=case when p_result='APPROVED' then now() else null end where id=c.id;
 if p_result='APPROVED' then perform private.project_registration_approval(c.id); end if;
 perform public.record_dashboard_action('REGISTRATION_'||p_result,'registration_case',c.id);
 result:=jsonb_build_object('case_id',c.id,'status',p_result,'revision',c.revision+1);
 return private.registration_receipt(p_operation_id,'decide',payload,result);
end $$;
revoke all on function public.decide_registration_review(uuid,bigint,uuid,text,uuid[],jsonb,text) from public,anon;
grant execute on function public.decide_registration_review(uuid,bigint,uuid,text,uuid[],jsonb,text) to authenticated;
