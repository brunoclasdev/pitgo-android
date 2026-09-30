begin;
alter table public.payments drop constraint payments_guincho_context_check;
alter table public.payments add constraint payments_guincho_context_check check(
 dispatch_kind<>'GUINCHO_DIRECT' or (appointment_id is not null and quote_version is not null and quote_version>0 and currency='BRL'));

-- Preserve audited legacy bodies and inject an explicit branch at their entry point.
-- Public APIs cannot use the old mobile transitions to bypass payment/arrival/client approval.
do $$
declare item record; definition text;
begin
 for item in select * from (values
  ('private.dispatch_snapshot(uuid)', $guard$if exists(select 1 from public.service_searches where id=p_search_id and dispatch_kind='GUINCHO_DIRECT') then return private.guincho_snapshot((select current_appointment_id from public.service_searches where id=p_search_id)); end if;$guard$),
  ('public.get_my_active_mobile_dispatch()', $guard$declare guincho_result jsonb; begin guincho_result:=public.get_my_active_guincho_request(); if guincho_result->>'outcome'='ACTIVE' then return guincho_result; end if; end;$guard$),
  ('public.get_pittec_appointment_snapshot(uuid)', $guard$if exists(select 1 from public.appointments where id=p_appointment_id and guincho_quote_id is not null) then return private.guincho_snapshot(p_appointment_id); end if;$guard$),
  ('public.respond_to_mobile_dispatch(uuid,boolean,bigint)', $guard$if exists(select 1 from public.appointments where id=p_appointment_id and guincho_quote_id is not null) then return public.respond_guincho_request(p_appointment_id,p_expected_revision,gen_random_uuid(),p_accept); end if;$guard$),
  ('public.start_mobile_appointment(uuid,bigint)', $guard$if exists(select 1 from public.appointments where id=p_appointment_id and guincho_quote_id is not null) then return public.start_guincho_transport(p_appointment_id,p_expected_revision,gen_random_uuid()); end if;$guard$),
  ('public.complete_mobile_appointment(uuid,bigint)', $guard$if exists(select 1 from public.appointments where id=p_appointment_id and guincho_quote_id is not null) then return public.request_guincho_completion(p_appointment_id,p_expected_revision,gen_random_uuid()); end if;$guard$),
  ('public.confirm_pittec_completion(uuid,bigint)', $guard$if exists(select 1 from public.appointments where id=p_appointment_id and guincho_quote_id is not null) then return public.confirm_guincho_completion(p_appointment_id,p_expected_revision,gen_random_uuid()); end if;$guard$),
  ('public.cancel_mobile_appointment(uuid,text,bigint)', $guard$if exists(select 1 from public.appointments where id=p_appointment_id and guincho_quote_id is not null) then
    if not exists(select 1 from public.appointments a join public.providers p on p.id=a.provider_id where a.id=p_appointment_id and (a.client_id=auth.uid() or p.account_id=auth.uid())) then raise exception using errcode='42501',message='PARTICIPANT_REQUIRED'; end if;
    if p_reason is null or char_length(btrim(p_reason)) not between 1 and 1000 then raise exception using errcode='22023',message='REASON_REQUIRED'; end if;
    perform 1 from public.service_searches where id=(select dispatch_search_id from public.appointments where id=p_appointment_id) for update;
    perform 1 from public.appointments where id=p_appointment_id for update;
    if exists(select 1 from public.appointments where id=p_appointment_id and status in ('DISPUTED','AWAITING_CLIENT_COMPLETION_CONFIRMATION','COMPLETED')) then raise exception using errcode='22023',message='INVALID_TRANSITION'; end if;
    if exists(select 1 from public.appointments where id=p_appointment_id and revision is distinct from p_expected_revision) then return jsonb_build_object('outcome','REVISION_CONFLICT','snapshot',private.guincho_snapshot(p_appointment_id)); end if;
    perform private.cancel_guincho_appointment(p_appointment_id,btrim(p_reason)); return jsonb_build_object('outcome','CANCELLED','snapshot',private.guincho_snapshot(p_appointment_id)); end if;$guard$),
  ('private.advance_mobile_dispatch(uuid)', $guard$if exists(select 1 from public.service_searches where id=p_search_id and dispatch_kind='GUINCHO_DIRECT') then return jsonb_build_object('outcome','DIRECT_REQUEST'); end if;$guard$),
  ('public.request_payment_cancellation(uuid,boolean)', $guard$if exists(select 1 from public.payments p join public.appointments a on a.id=p.appointment_id where p.id=p_payment_id and p.dispatch_kind='GUINCHO_DIRECT' and a.status in ('DISPUTED','AWAITING_CLIENT_COMPLETION_CONFIRMATION','COMPLETED')) then raise exception using errcode='22023',message='NOT_REFUNDABLE'; end if;$guard$)
 ) as guards(signature,guard)
 loop
  select pg_get_functiondef(item.signature::regprocedure) into definition;
  if definition !~* '\mbegin\M' then raise exception 'Unexpected function body: %',item.signature; end if;
  definition:=regexp_replace(definition,'\mbegin\M','begin'||chr(10)||item.guard,'i');
  execute definition;
 end loop;
end; $$;
notify pgrst,'reload schema';
commit;
