create function public.save_my_registration_draft(p_account_type text,p_data jsonb,p_operation_id uuid,p_expected_revision bigint) returns jsonb language plpgsql security definer set search_path='' as $$
declare c private.registration_cases; result jsonb; payload jsonb:=jsonb_build_object('mode',p_account_type,'data',p_data); v jsonb;
begin
 result:=private.registration_replay(p_operation_id,'save',payload); if result is not null then return result; end if;
 select * into c from private.registration_cases where user_id=auth.uid() and account_type=p_account_type;
 c:=private.registration_case_lock(c.id,p_expected_revision);
 if c.status not in ('INCOMPLETE','CORRECTION_REQUESTED') or not exists(select 1 from private.registration_submissions where id=c.current_submission_id and state='DRAFT') then raise exception using errcode='22023',message='INVALID_STATE'; end if;
 if (p_data-'vehicles') is distinct from (select snapshot-'vehicles' from private.registration_submissions where id=c.current_submission_id) then raise exception using errcode='22023',message='INVALID_INPUT'; end if;
 if jsonb_typeof(p_data)<>'object' or exists(select 1 from jsonb_object_keys(p_data) k where k not in ('address','provider','offers','vehicles')) then raise exception using errcode='22023',message='INVALID_INPUT'; end if;
 if p_data ? 'vehicles' and jsonb_typeof(p_data->'vehicles')<>'array' then raise exception using errcode='22023',message='INVALID_INPUT'; end if;
 for v in select value from jsonb_array_elements(coalesce(p_data->'vehicles','[]')) loop
 if nullif(v->>'id','') is null or (v->>'id')::uuid is null then raise exception using errcode='22023',message='INVALID_VEHICLE'; end if;
 end loop;
 if (select count(*)<>count(distinct value->>'id') from jsonb_array_elements(coalesce(p_data->'vehicles','[]'))) then raise exception using errcode='22023',message='INVALID_VEHICLE'; end if;
 delete from private.registration_submission_evidence se where submission_id=c.current_submission_id and kind='VEHICLE_DOCUMENT'
 and not exists(select 1 from jsonb_array_elements(coalesce(p_data->'vehicles','[]')) v where v->>'id'=se.vehicle_reference_id::text);
 delete from private.registration_submission_evidence se where se.submission_id=c.current_submission_id and se.kind='VEHICLE_DOCUMENT'
 and exists(select 1 from private.registration_submissions oldsub,
 jsonb_array_elements(coalesce(oldsub.snapshot->'vehicles','[]')) oldv,
 jsonb_array_elements(coalesce(p_data->'vehicles','[]')) newv
 where oldsub.id=c.current_submission_id and oldv->>'id'=se.vehicle_reference_id::text
 and newv->>'id'=se.vehicle_reference_id::text and oldv is distinct from newv);
 update private.registration_submissions set snapshot=p_data where id=c.current_submission_id;
 update private.registration_cases set revision=revision+1 where id=c.id;
 return private.registration_receipt(p_operation_id,'save',payload,public.get_my_registration(p_account_type));
end $$;
revoke all on function public.save_my_registration_draft(text,jsonb,uuid,bigint) from public,anon;
grant execute on function public.save_my_registration_draft(text,jsonb,uuid,bigint) to authenticated;
-- Reuse existing complementary forms and copy their validated persisted values into the draft.
create function public.sync_my_registration_complement(p_account_type text) returns jsonb language plpgsql security definer set search_path='' as $$
declare c private.registration_cases; data jsonb; pid uuid;
begin
 select * into c from private.registration_cases where user_id=auth.uid() and account_type=p_account_type for update;
 if not found then raise exception using errcode='42501',message='FORBIDDEN'; end if;
 if not c.verification_required or c.status='APPROVED' then return public.get_my_registration(p_account_type); end if;
 if c.status not in ('INCOMPLETE','CORRECTION_REQUESTED') or not exists(select 1 from private.registration_submissions where id=c.current_submission_id and state='DRAFT') then raise exception using errcode='22023',message='INVALID_STATE'; end if;
 select snapshot into data from private.registration_submissions where id=c.current_submission_id;
 if p_account_type='CLIENT' then
 select data||jsonb_build_object('address',to_jsonb(a)-'client_id'-'created_at'-'updated_at') into data from public.client_addresses a where client_id=auth.uid();
 else
 select id into pid from public.providers where account_id=auth.uid();
 select data||jsonb_build_object('provider',jsonb_build_object('name',p.name,'email',p.email,'phone',p.phone,'cnpj',p.cnpj,'description',p.description,'location_mode',p.location_mode),
 'address',(select to_jsonb(a)-'id'-'provider_id'-'address_point' from public.addresses a where provider_id=pid limit 1),
 'offers',coalesce((select jsonb_agg(to_jsonb(o)-'id'-'provider_id'-'created_at'-'updated_at') from public.provider_services o where provider_id=pid),'[]')) into data from public.providers p where id=pid;
 end if;
 if data is null then raise exception using errcode='22023',message='INCOMPLETE_REGISTRATION'; end if;
 update private.registration_submissions set snapshot=data where id=c.current_submission_id;
 update private.registration_cases set revision=revision+1 where id=c.id;
 return public.get_my_registration(p_account_type);
end $$;
revoke all on function public.sync_my_registration_complement(text) from public,anon;
grant execute on function public.sync_my_registration_complement(text) to authenticated;
