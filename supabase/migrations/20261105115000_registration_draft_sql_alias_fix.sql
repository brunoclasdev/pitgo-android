-- Avoid a collision between the PL/pgSQL vehicle variable and JSON SQL aliases.
create or replace function public.save_my_registration_draft(p_account_type text,p_data jsonb,p_operation_id uuid,p_expected_revision bigint) returns jsonb language plpgsql security definer set search_path='' as $$
declare c private.registration_cases; result jsonb; payload jsonb:=jsonb_build_object('mode',p_account_type,'data',p_data); draft_vehicle jsonb;
begin
 result:=private.registration_replay(p_operation_id,'save',payload); if result is not null then return result; end if;
 select * into c from private.registration_cases where user_id=auth.uid() and account_type=p_account_type;
 c:=private.registration_case_lock(c.id,p_expected_revision);
 if c.status not in ('INCOMPLETE','CORRECTION_REQUESTED') or not exists(select 1 from private.registration_submissions where id=c.current_submission_id and state='DRAFT') then raise exception using errcode='22023',message='INVALID_STATE'; end if;
 if (p_data-'vehicles') is distinct from (select snapshot-'vehicles' from private.registration_submissions where id=c.current_submission_id) then raise exception using errcode='22023',message='INVALID_INPUT'; end if;
 if jsonb_typeof(p_data)<>'object' or exists(select 1 from jsonb_object_keys(p_data) k where k not in ('address','provider','offers','vehicles')) then raise exception using errcode='22023',message='INVALID_INPUT'; end if;
 if p_data ? 'vehicles' and jsonb_typeof(p_data->'vehicles')<>'array' then raise exception using errcode='22023',message='INVALID_INPUT'; end if;
 for draft_vehicle in select value from jsonb_array_elements(coalesce(p_data->'vehicles','[]')) loop
 if nullif(draft_vehicle->>'id','') is null or (draft_vehicle->>'id')::uuid is null then raise exception using errcode='22023',message='INVALID_VEHICLE'; end if;
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
