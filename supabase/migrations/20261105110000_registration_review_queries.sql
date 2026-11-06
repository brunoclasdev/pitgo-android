create function public.list_registration_reviews(p_status text default 'IN_REVIEW',p_account_type text default null,p_after_submitted_at timestamptz default null,p_after_id uuid default null,p_limit integer default 20) returns jsonb language plpgsql stable security definer set search_path='' as $$
declare result jsonb;
begin
 if not private.registration_staff() then raise exception using errcode='42501',message='FORBIDDEN'; end if;
 select coalesce(jsonb_agg(to_jsonb(q)),'[]') into result from (
 select c.id case_id,c.account_type,c.status,c.revision,s.submitted_at,i.first_name,i.last_name from private.registration_cases c
 join private.registration_identities i on i.user_id=c.user_id join private.registration_submissions s on s.id=c.current_submission_id
 where c.user_id<>auth.uid() and (p_status is null or c.status=p_status) and (p_account_type is null or c.account_type=p_account_type)
 and (p_after_submitted_at is null or (s.submitted_at,c.id)>(p_after_submitted_at,p_after_id))
 order by s.submitted_at,c.id limit least(greatest(p_limit,1),100)) q;
 return result;
end $$;
create function public.get_registration_review(p_case_id uuid) returns jsonb language plpgsql stable security definer set search_path='' as $$
declare c private.registration_cases;
begin
 select * into c from private.registration_cases where id=p_case_id;
 if not private.registration_staff() or not found or c.user_id=auth.uid() then raise exception using errcode='42501',message='FORBIDDEN'; end if;
 return jsonb_build_object('case',to_jsonb(c),'identity',(select to_jsonb(i) from private.registration_identities i where i.user_id=c.user_id),
 'submissions',coalesce((select jsonb_agg(to_jsonb(s)||jsonb_build_object('evidence',coalesce((select jsonb_agg(to_jsonb(e)) from private.registration_submission_evidence se join private.registration_evidence e on e.id=se.evidence_id where se.submission_id=s.id),'[]'),'review',(select to_jsonb(v) from private.registration_reviews v where v.submission_id=s.id)) order by s.version desc) from private.registration_submissions s where s.case_id=c.id),'[]'));
end $$;
revoke all on function public.list_registration_reviews(text,text,timestamptz,uuid,integer),public.get_registration_review(uuid) from public,anon;
grant execute on function public.list_registration_reviews(text,text,timestamptz,uuid,integer),public.get_registration_review(uuid) to authenticated;
