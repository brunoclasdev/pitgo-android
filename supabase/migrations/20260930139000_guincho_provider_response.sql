begin;
create function public.respond_guincho_request(p_appointment_id uuid,p_expected_revision bigint,p_operation_id uuid,p_accept boolean)
returns jsonb language plpgsql security definer set search_path='' as $$
declare a public.appointments%rowtype; search_uuid uuid;
begin
 if p_operation_id is null or p_accept is null then raise exception using errcode='22023',message='INVALID_INPUT'; end if;
 select a.dispatch_search_id into search_uuid from public.appointments a join public.providers p on p.id=a.provider_id
 where a.id=p_appointment_id and a.guincho_quote_id is not null and p.account_id=auth.uid();
 if search_uuid is null then raise exception using errcode='42501',message='PROVIDER_REQUIRED'; end if;
 perform 1 from public.service_searches where id=search_uuid for update;
 select * into a from public.appointments where id=p_appointment_id for update;
 if a.status in ('ACCEPTED','IN_PROGRESS','AWAITING_CLIENT_COMPLETION_CONFIRMATION','DISPUTED','COMPLETED','CANCELLED') then
 return jsonb_build_object('outcome','EXISTING','snapshot',private.guincho_snapshot(a.id),'server_now',now()); end if;
 if a.status<>'PENDING' then raise exception using errcode='22023',message='INVALID_TRANSITION'; end if;
 if a.response_deadline<=now() then
  perform private.cancel_guincho_appointment(a.id,'PROVIDER_RESPONSE_EXPIRED');
  return jsonb_build_object('outcome','OFFER_EXPIRED','snapshot',private.guincho_snapshot(a.id),'server_now',now());
 end if;
 if a.revision is distinct from p_expected_revision then
 return jsonb_build_object('outcome','REVISION_CONFLICT','snapshot',private.guincho_snapshot(a.id),'server_now',now()); end if;
 if not exists(select 1 from public.payments p where p.request_id=search_uuid and p.appointment_id=a.id
 and p.quote_version=a.quote_version and p.amount=a.customer_total_amount and private.payment_can_release(p,a.service_id)) then
 raise exception using errcode='22023',message='PAYMENT_REQUIRED'; end if;
 if p_accept then
  update public.appointments set status='ACCEPTED',accepted_at=now(),service_deadline_at=now()+interval '4 hours',
   revision=revision+1,updated_at=now() where id=a.id;
  update public.service_searches set status='ACCEPTED',revision=revision+1,updated_at=now() where id=search_uuid;
 else perform private.cancel_guincho_appointment(a.id,'PROVIDER_DECLINED'); end if;
 return jsonb_build_object('outcome',case when p_accept then 'ACCEPTED' else 'DECLINED' end,
 'snapshot',private.guincho_snapshot(a.id),'server_now',now());
end; $$;
revoke all on function public.respond_guincho_request(uuid,bigint,uuid,boolean) from public,anon;
grant execute on function public.respond_guincho_request(uuid,bigint,uuid,boolean) to authenticated;

alter function private.expire_mobile_dispatch_appointment(uuid) rename to expire_mobile_dispatch_appointment_before_guincho;
create function private.expire_mobile_dispatch_appointment(p_appointment_id uuid) returns void
language plpgsql security definer set search_path='' as $$
declare a public.appointments%rowtype;
begin
 select * into a from public.appointments where id=p_appointment_id;
 if a.guincho_quote_id is null then perform private.expire_mobile_dispatch_appointment_before_guincho(p_appointment_id);
 elsif a.status='PENDING' and a.response_deadline<=now() then
  perform private.cancel_guincho_appointment(a.id,'PROVIDER_RESPONSE_EXPIRED');
 end if;
end; $$;
revoke all on function private.expire_mobile_dispatch_appointment(uuid),private.expire_mobile_dispatch_appointment_before_guincho(uuid)
from public,anon,authenticated;
commit;
