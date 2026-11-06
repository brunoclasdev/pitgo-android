create function private.validate_registration_submission(p_submission uuid) returns void language plpgsql security definer set search_path='' as $$
declare s private.registration_submissions; c private.registration_cases; k text; v jsonb; a jsonb;
begin
 select * into s from private.registration_submissions where id=p_submission;
 select * into c from private.registration_cases where id=s.case_id;
 a:=s.snapshot->'address';
 foreach k in array array['street','number','neighborhood','zip_code','city','state'] loop
 if coalesce(btrim(a->>k),'')='' then raise exception using errcode='22023',message='INCOMPLETE_REGISTRATION'; end if;
 end loop;
 foreach k in array array['CNH_FRONT','CNH_BACK','SELFIE','SELFIE_WITH_DOCUMENT'] loop
 if not exists(select 1 from private.registration_submission_evidence se join private.registration_evidence e on e.id=se.evidence_id
 where se.submission_id=s.id and se.kind=k and e.case_id=c.id and e.upload_state='RECEIVED') then raise exception using errcode='22023',message='INCOMPLETE_REGISTRATION'; end if;
 end loop;
 if c.account_type='PROVIDER' then
 foreach k in array array['name','cnpj','email','phone','location_mode'] loop
 if coalesce(btrim(s.snapshot->'provider'->>k),'')='' then raise exception using errcode='22023',message='INCOMPLETE_REGISTRATION'; end if;
 end loop;
 if not coalesce(private.is_valid_cnpj(s.snapshot->'provider'->>'cnpj'),false) or jsonb_array_length(coalesce(s.snapshot->'offers','[]'))<1 then raise exception using errcode='22023',message='INCOMPLETE_REGISTRATION'; end if;
 if exists(select 1 from jsonb_array_elements(s.snapshot->'offers') item
 left join public.services service on service.id=(item->>'service_id')::uuid
 left join public.service_categories category on category.id=service.category_id
 where service.id is null or not service.active or category.id is null or not category.active) then
 raise exception using errcode='22023',message='CATALOG_CHANGED'; end if;
 for v in select value from jsonb_array_elements(coalesce(s.snapshot->'vehicles','[]')) loop
 if coalesce(v->>'plate','') !~ '^[A-Z]{3}[0-9][A-Z0-9][0-9]{2}$' then raise exception using errcode='22023',message='INVALID_VEHICLE'; end if;
 foreach k in array array['id','plate','make','model','color'] loop
 if coalesce(btrim(v->>k),'')='' then raise exception using errcode='22023',message='INCOMPLETE_REGISTRATION'; end if;
 end loop;
 if not exists(select 1 from private.registration_submission_evidence se join private.registration_evidence e on e.id=se.evidence_id where se.submission_id=s.id and se.kind='VEHICLE_DOCUMENT' and se.vehicle_reference_id=(v->>'id')::uuid and e.case_id=c.id and e.upload_state='RECEIVED') then raise exception using errcode='22023',message='INCOMPLETE_REGISTRATION'; end if;
 end loop;
 end if;
 if exists(select 1 from private.registration_submission_evidence se join private.registration_evidence e on e.id=se.evidence_id where se.submission_id=s.id and (e.case_id<>c.id or e.upload_state<>'RECEIVED')) then raise exception using errcode='22023',message='UPLOAD_NOT_CONFIRMED'; end if;
end $$;
create function public.submit_my_registration(p_submission_id uuid,p_operation_id uuid,p_expected_revision bigint) returns jsonb language plpgsql security definer set search_path='' as $$
declare s private.registration_submissions; c private.registration_cases; result jsonb; payload jsonb:=jsonb_build_object('submission',p_submission_id);
begin
 result:=private.registration_replay(p_operation_id,'submit',payload); if result is not null then return result; end if;
 select * into s from private.registration_submissions where id=p_submission_id;
 c:=private.registration_case_lock(s.case_id,p_expected_revision);
 if c.current_submission_id<>s.id or s.state<>'DRAFT' then raise exception using errcode='22023',message='INVALID_STATE'; end if;
 perform private.validate_registration_submission(s.id);
 update private.registration_submissions set state='SUBMITTED',submitted_at=now() where id=s.id;
 update private.registration_cases set status='IN_REVIEW',revision=revision+1 where id=c.id;
 return private.registration_receipt(p_operation_id,'submit',payload,public.get_my_registration(c.account_type));
end $$;
revoke all on function private.validate_registration_submission(uuid) from public,anon,authenticated;
revoke all on function public.submit_my_registration(uuid,uuid,bigint) from public,anon;
grant execute on function public.submit_my_registration(uuid,uuid,bigint) to authenticated;
create function private.registration_pending_items(p_submission uuid) returns text[] language plpgsql stable security definer set search_path='' as $$
declare s private.registration_submissions; c private.registration_cases; pending text[]:='{}'; k text; v jsonb;
begin
 select * into s from private.registration_submissions where id=p_submission;
 if not found then return pending; end if;
 select * into c from private.registration_cases where id=s.case_id;
 foreach k in array array['street','number','neighborhood','zip_code','city','state'] loop
 if coalesce(btrim(s.snapshot->'address'->>k),'')='' then pending:=array_append(pending,'ADDRESS'); exit; end if;
 end loop;
 foreach k in array array['CNH_FRONT','CNH_BACK','SELFIE','SELFIE_WITH_DOCUMENT'] loop
 if not exists(select 1 from private.registration_submission_evidence se join private.registration_evidence e on e.id=se.evidence_id where se.submission_id=s.id and se.kind=k and e.upload_state='RECEIVED') then pending:=array_append(pending,k); end if;
 end loop;
 if c.account_type='PROVIDER' then
 if not s.snapshot ? 'provider' or jsonb_array_length(coalesce(s.snapshot->'offers','[]'))=0 then pending:=array_append(pending,'PROVIDER'); end if;
 for v in select value from jsonb_array_elements(coalesce(s.snapshot->'vehicles','[]')) loop
 if not exists(select 1 from private.registration_submission_evidence se join private.registration_evidence e on e.id=se.evidence_id where se.submission_id=s.id and se.kind='VEHICLE_DOCUMENT' and se.vehicle_reference_id=(v->>'id')::uuid and e.upload_state='RECEIVED') then pending:=array_append(pending,'VEHICLE_DOCUMENT'); end if;
 end loop;
 end if;
 return pending;
end $$;
revoke all on function private.registration_pending_items(uuid) from public,anon,authenticated;
