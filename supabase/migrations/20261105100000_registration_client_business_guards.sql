create table private.registration_guard_targets(name text primary key, mode text not null);
revoke all on private.registration_guard_targets from public,anon,authenticated;
insert into private.registration_guard_targets values
('acknowledge_pittec_start','PROVIDER'),
('arrive_guincho_appointment','PROVIDER'),
('arrive_pittec_appointment','PROVIDER'),
('begin_guincho_inspection_correction','ANY'),
('cancel_mobile_appointment','ANY'),
('cancel_pittec_search','CLIENT'),
('cancel_unpaid_guincho_request','CLIENT'),
('complete_mobile_appointment','PROVIDER'),
('confirm_guincho_completion','CLIENT'),
('confirm_guincho_inspection_photo','ANY'),
('confirm_guincho_inspection_photo_v2','ANY'),
('confirm_pittec_completion','CLIENT'),
('create_guincho_request','CLIENT'),
('create_vehicle','CLIENT'),
('decide_guincho_inspection','ANY'),
('discard_guincho_inspection_photo','ANY'),
('discover_guincho_providers','CLIENT'),
('discover_service_providers','CLIENT'),
('dispute_guincho_completion','CLIENT'),
('get_appointment_client_location','ANY'),
('get_appointment_provider_location','ANY'),
('get_guincho_appointment_snapshot','ANY'),
('get_guincho_inspection','ANY'),
('get_guincho_inspection_v2','ANY'),
('get_guincho_route_snapshot','ANY'),
('get_my_active_guincho_request','ANY'),
('get_my_active_mobile_dispatch','ANY'),
('get_my_active_pittec_dispatch','ANY'),
('get_my_guincho_inspection_alert','ANY'),
('get_my_pending_mobile_dispatch','PROVIDER'),
('get_payment_history','CLIENT'),
('get_payment_status','CLIENT'),
('get_pittec_appointment_snapshot','ANY'),
('get_pittec_route_context','ANY'),
('get_pittec_route_snapshot','ANY'),
('get_provider_rating_summary','ANY'),
('get_recoverable_payment','CLIENT'),
('get_saved_card_tokenization_context','CLIENT'),
('get_service_rating','ANY'),
('list_guincho_inspection_photos','ANY'),
('list_guincho_inspection_photos_v2','ANY'),
('list_guincho_inspection_versions','ANY'),
('list_home_services','CLIENT'),
('list_my_guincho_appointments','ANY'),
('list_my_pending_guincho_inspection_approvals','ANY'),
('list_my_provider_ratings','PROVIDER'),
('list_my_service_appointments','CLIENT'),
('list_nearby_pittecs','CLIENT'),
('list_saved_cards','CLIENT'),
('list_service_provider_locations','CLIENT'),
('next_mobile_dispatch_candidate','CLIENT'),
('prepare_guincho_inspection_photo','ANY'),
('prepare_guincho_inspection_photo_v2','ANY'),
('publish_client_location','CLIENT'),
('publish_mobile_provider_location','PROVIDER'),
('publish_provider_location','PROVIDER'),
('register_my_guincho_inspection_push','ANY'),
('remove_saved_card','CLIENT'),
('request_guincho_completion','PROVIDER'),
('request_mobile_dispatch_candidate','CLIENT'),
('request_payment_cancellation','CLIENT'),
('reserve_homol_order_payment','CLIENT'),
('reserve_order_payment','CLIENT'),
('respond_guincho_request','PROVIDER'),
('respond_to_mobile_dispatch','PROVIDER'),
('respond_to_pittec_quote','PROVIDER'),
('revoke_my_guincho_inspection_push','ANY'),
('save_guincho_trip_draft','CLIENT'),
('set_primary_vehicle','CLIENT'),
('set_provider_availability','PROVIDER'),
('simulate_guincho_payment','CLIENT'),
('start_guincho_transport','PROVIDER'),
('start_mobile_appointment','PROVIDER'),
('start_mobile_dispatch','CLIENT'),
('start_pittec_dispatch','CLIENT'),
('stop_mobile_provider_location','PROVIDER'),
('stop_provider_location','PROVIDER'),
('submit_guincho_inspection','ANY'),
('submit_service_rating','ANY'),
('unselect_guincho_inspection_photo','ANY');
create function private.install_registration_business_guards() returns void language plpgsql security definer set search_path='' as $guard$
declare f record; definition text; cloned text; args text; callargs text; rettype text; body text; guard text; original text;
begin
 for f in select p.*,coalesce(t.mode,'ANY') mode from pg_proc p join pg_namespace n on n.oid=p.pronamespace left join private.registration_guard_targets t on t.name=p.proname
 where n.nspname='public' and p.prokind='f' and has_function_privilege('authenticated',p.oid,'EXECUTE')
 and not exists(select 1 from pg_depend dep where dep.classid='pg_proc'::regclass and dep.objid=p.oid and dep.deptype='e')
 and p.proname not like 'dashboard_%' and p.proname not like 'mp_%'
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
revoke all on function private.install_registration_business_guards() from public,anon,authenticated;
