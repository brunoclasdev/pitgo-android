begin;
create function public.dispute_guincho_completion(p_appointment_id uuid,p_expected_revision bigint,p_operation_id uuid,p_reason text)
returns jsonb language plpgsql security definer set search_path='' as $$
declare a public.appointments%rowtype; search_uuid uuid;
begin
 if p_operation_id is null or p_reason is null or char_length(btrim(p_reason)) not between 1 and 1000 then
 raise exception using errcode='22023',message='REASON_REQUIRED'; end if;
 select dispatch_search_id into search_uuid from public.appointments where id=p_appointment_id and client_id=auth.uid() and guincho_quote_id is not null;
 if search_uuid is null then raise exception using errcode='42501',message='CLIENT_REQUIRED'; end if;
 perform 1 from public.service_searches where id=search_uuid for update;
 select * into a from public.appointments where id=p_appointment_id for update;
 if exists(select 1 from public.appointment_disputes where appointment_id=a.id) then
 return jsonb_build_object('outcome','EXISTING','snapshot',private.guincho_snapshot(a.id),'server_now',now()); end if;
 if a.revision is distinct from p_expected_revision then
 return jsonb_build_object('outcome','REVISION_CONFLICT','snapshot',private.guincho_snapshot(a.id),'server_now',now()); end if;
 if a.status<>'AWAITING_CLIENT_COMPLETION_CONFIRMATION' then raise exception using errcode='22023',message='INVALID_TRANSITION'; end if;
 insert into public.appointment_disputes(appointment_id,client_id,reason) values(a.id,a.client_id,btrim(p_reason));
 update public.appointments set status='DISPUTED',revision=revision+1,updated_at=now() where id=a.id;
 update public.service_searches set status='DISPUTED',revision=revision+1,updated_at=now(),next_dispatch_at=null where id=search_uuid;
 delete from public.provider_current_locations where appointment_id=a.id;
 delete from public.client_current_locations where appointment_id=a.id;
 delete from public.appointment_route_snapshots where appointment_id=a.id;
 perform private.apply_pending_provider_mode(a.provider_id);
 return jsonb_build_object('outcome','DISPUTED','snapshot',private.guincho_snapshot(a.id),'server_now',now());
end; $$;
create function public.list_my_guincho_appointments(p_before timestamptz default null,p_before_id uuid default null,p_limit integer default 20)
returns setof jsonb language plpgsql stable security definer set search_path='' as $$
declare appointment_id uuid;
begin
 if auth.uid() is null then raise exception using errcode='42501',message='SESSION_REQUIRED'; end if;
 for appointment_id in select a.id from public.appointments a join public.providers p on p.id=a.provider_id
 where a.guincho_quote_id is not null and (a.client_id=auth.uid() or (p.account_id=auth.uid() and a.status<>'AWAITING_PAYMENT'
 and exists(select 1 from public.payments pay where pay.request_id=a.dispatch_search_id and not pay.is_compensation
 and pay.status in ('APPROVED','REFUND_PENDING','REFUND_FAILED','REFUNDED'))))
 and (p_before is null or (a.created_at,a.id)<(p_before,p_before_id))
 order by a.created_at desc,a.id desc limit greatest(1,least(coalesce(p_limit,20),100)) loop
  return next private.guincho_snapshot(appointment_id)||jsonb_build_object('created_at',(select created_at from public.appointments where id=appointment_id));
 end loop;
end; $$;
revoke all on function public.dispute_guincho_completion(uuid,bigint,uuid,text),public.list_my_guincho_appointments(timestamptz,uuid,integer) from public,anon;
grant execute on function public.dispute_guincho_completion(uuid,bigint,uuid,text),public.list_my_guincho_appointments(timestamptz,uuid,integer) to authenticated;
commit;
