create function public.get_my_account_context_v2(p_account_type text default null) returns jsonb language plpgsql stable security definer set search_path='' as $$
declare uid uuid:=auth.uid(); modes jsonb; mode text; pid uuid; legacy jsonb;
begin
 if uid is null then raise exception using errcode='42501',message='AUTH_REQUIRED'; end if;
 select coalesce(jsonb_agg(jsonb_build_object('account_type',r.account_type,'verification_status',c.status,
 'verification_required',coalesce(c.verification_required,true),'legacy_exempt',c.legacy_exempt_at is not null,
 'revision',c.revision,'current_submission_id',c.current_submission_id,'can_use_marketplace',
 case when private.registration_enabled() then private.can_use_account_mode(uid,r.account_type) else true end)), '[]') into modes
 from public.account_roles r left join private.registration_cases c on c.user_id=r.user_id and c.account_type=r.account_type where r.user_id=uid;
 select r.account_type into mode from public.account_roles r where r.user_id=uid
 order by (r.account_type=p_account_type) desc nulls last,
 private.can_use_account_mode(uid,r.account_type) desc, r.registration_primary desc limit 1;
 if mode is null then raise exception using errcode='42501',message='FORBIDDEN'; end if;
 select id into pid from public.providers where account_id=uid;
 return jsonb_build_object('user_id',uid,'memberships',modes,'account_type',mode,
 'client_state',case when mode='CLIENT' then case when exists(select 1 from public.profiles where id=uid and active) then 'READY' else 'INACTIVE' end end,
 'provider_state',case when mode='PROVIDER' then case when pid is null then 'ONBOARDING' else 'READY' end end,
 'provider_id',pid,'legacy_provider_migration_available',false);
end $$;
create function public.get_my_registration(p_account_type text) returns jsonb language plpgsql stable security definer set search_path='' as $$
declare c private.registration_cases; s private.registration_submissions; identity jsonb;
begin
 select * into c from private.registration_cases where user_id=auth.uid() and account_type=p_account_type;
 if not found then raise exception using errcode='42501',message='FORBIDDEN'; end if;
 select * into s from private.registration_submissions where id=c.current_submission_id;
 select to_jsonb(i)-'created_at'-'updated_at' into identity from private.registration_identities i where i.user_id=c.user_id;
 return jsonb_build_object('case_id',c.id,'account_type',c.account_type,'status',c.status,'revision',c.revision,
 'submission_id',s.id,'submission_state',s.state,'data',coalesce(s.snapshot,'{}'),'identity',identity,
 'pending_items',coalesce(private.registration_pending_items(s.id),'{}'::text[]),
 'evidence',coalesce((select jsonb_agg(jsonb_build_object('id',e.id,'kind',e.kind,'vehicle_reference_id',e.vehicle_reference_id,
 'storage_path',e.storage_path,'upload_state',e.upload_state)) from private.registration_submission_evidence se
 join private.registration_evidence e on e.id=se.evidence_id where se.submission_id=s.id),'[]'),
 'reviews',coalesce((select jsonb_agg(to_jsonb(v)-'reviewer_user_id' order by v.decided_at desc) from private.registration_reviews v
 join private.registration_submissions sub on sub.id=v.submission_id where sub.case_id=c.id),'[]'));
end $$;
create function public.start_my_registration_mode(p_account_type text,p_operation_id uuid) returns jsonb language plpgsql security definer set search_path='' as $$
declare cid uuid; sid uuid; result jsonb; payload jsonb:=jsonb_build_object('mode',p_account_type);
begin
 if not private.registration_enabled() then raise exception using errcode='55000',message='FEATURE_NOT_ENABLED'; end if;
 result:=private.registration_replay(p_operation_id,'start',payload); if result is not null then return result; end if;
 if p_account_type not in ('CLIENT','PROVIDER') then raise exception using errcode='22023',message='INVALID_INPUT'; end if;
 perform pg_advisory_xact_lock(hashtextextended(auth.uid()::text||p_account_type,1));
 insert into public.account_roles(user_id,account_type,registration_primary) values(auth.uid(),p_account_type,false) on conflict(user_id,account_type) do nothing;
 insert into private.registration_cases(user_id,account_type) values(auth.uid(),p_account_type) on conflict(user_id,account_type) do nothing;
 select id,current_submission_id into cid,sid from private.registration_cases where user_id=auth.uid() and account_type=p_account_type for update;
 if sid is null then
 insert into private.registration_submissions(case_id,version) values(cid,1) returning id into sid;
 update private.registration_cases set current_submission_id=sid where id=cid;
 end if;
 return private.registration_receipt(p_operation_id,'start',payload,public.get_my_registration(p_account_type));
end $$;
revoke all on function public.get_my_account_context_v2(text),public.get_my_registration(text),public.start_my_registration_mode(text,uuid) from public,anon;
grant execute on function public.get_my_account_context_v2(text),public.get_my_registration(text),public.start_my_registration_mode(text,uuid) to authenticated;
