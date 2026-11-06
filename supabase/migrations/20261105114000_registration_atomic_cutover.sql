begin;
lock table auth.users,public.account_roles,public.profiles,public.providers in share row exclusive mode;
create or replace function private.install_registration_business_guards() returns void language plpgsql security definer set search_path='' as $guard$
declare f record; definition text; cloned text; args text; callargs text; rettype text; body text; guard text; original text;
begin
 for f in select p.*,coalesce(t.mode,'ANY') mode from pg_proc p join pg_namespace n on n.oid=p.pronamespace left join private.registration_guard_targets t on t.name=p.proname
 where n.nspname='public' and p.prokind='f' and has_function_privilege('authenticated',p.oid,'EXECUTE')
 and not exists(select 1 from pg_depend dep where dep.classid='pg_proc'::regclass and dep.objid=p.oid and dep.deptype='e')
 and p.proname not like '%dashboard%' and p.proname not like 'admin_%' and p.proname not like 'mp_%'
 and p.proname not in ('get_my_account_context','get_my_account_context_v2','get_my_registration','start_my_registration_mode','save_my_registration_draft','reserve_my_registration_evidence','submit_my_registration','begin_my_registration_correction','sync_my_registration_complement','assert_my_registration_access','list_registration_reviews','get_registration_review','decide_registration_review','create_provider_registration','create_provider_geocode_proof','get_my_client_address','upsert_my_client_address','begin_provider_ownership_migration','claim_provider_ownership_migration','create_dashboard_staff','set_dashboard_staff_active','record_dashboard_action','list_dashboard_audit_log','create_service_category','create_service','update_service_catalog_item','list_transport_categories','list_guincho_sizes','get_guincho_transport_catalog') loop
 if f.proname in ('create_guincho_request_before_payment','cancel_guincho_search','classify_my_vehicle_transport') then f.mode:='CLIENT'; end if;
 original:='registration_original_'||f.oid;
 definition:=pg_get_functiondef(f.oid);
 cloned:=replace(definition,'FUNCTION public.'||quote_ident(f.proname)||'(', 'FUNCTION private.'||original||'(');
 if cloned=definition then raise exception 'Unable to protect function %',f.proname; end if;
 execute cloned;
 execute format('revoke all on function private.%I(%s) from public,anon,authenticated',original,pg_get_function_identity_arguments(f.oid));
 select string_agg(format('$%s',i),',' order by i) into callargs from generate_series(1,f.pronargs) i;
 callargs:=coalesce(callargs,'');
 if f.mode='ANY' then guard:='if private.registration_enabled() and not (private.can_use_account_mode(auth.uid(),''CLIENT'') or private.can_use_account_mode(auth.uid(),''PROVIDER'')) then raise exception using errcode=''42501'',message=''REGISTRATION_REQUIRED''; end if;';
 else guard:=format('perform private.require_registration_access(%L);',f.mode); end if;
 -- Service workers keep existing auth checks in their original implementation.
 guard:='if auth.role() is distinct from ''service_role'' then '||guard||' end if;';
 if f.proretset then body:=format('return query select * from private.%I(%s);',original,callargs);
 elsif f.prorettype='void'::regtype then body:=format('perform private.%I(%s); return;',original,callargs);
 else body:=format('return private.%I(%s);',original,callargs); end if;
 execute format('create or replace function public.%I(%s) returns %s language plpgsql security definer set search_path='''' as %L',
 f.proname,pg_get_function_arguments(f.oid),pg_get_function_result(f.oid),'begin '||guard||body||' end');
 end loop;
end $guard$;
create or replace function public.get_my_account_context_v2(p_account_type text default null) returns jsonb language plpgsql stable security definer set search_path='' as $$
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
 'provider_state',case when mode='PROVIDER' then case when pid is null then 'ONBOARDING' when (select count(*) from public.addresses where provider_id=pid)=1 and (exists(select 1 from public.provider_services where provider_id=pid) or exists(select 1 from public.providers where id=pid and provider_kind='STORE')) then 'READY' else 'INCOMPLETE' end end,
 'provider_id',pid,'legacy_provider_migration_available',false);
end $$;
select private.install_registration_accounts();
drop trigger if exists on_auth_user_created_create_account on auth.users;
create trigger on_auth_user_created_create_account after insert on auth.users for each row execute function private.handle_verified_registration_signup();
select private.install_registration_business_guards();
-- v1 always keeps the original mode; v2 clients explicitly select their active mode.
create or replace function public.get_my_account_context() returns jsonb language sql stable security definer set search_path='' as $$
 select public.get_my_account_context_v2((select account_type from public.account_roles where user_id=auth.uid() and registration_primary));
$$;
update private.registration_runtime set enabled=true where singleton;
commit;
