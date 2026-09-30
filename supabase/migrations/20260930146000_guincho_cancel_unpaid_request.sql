begin;
create function public.cancel_unpaid_guincho_request(p_request_id uuid) returns jsonb
language plpgsql security definer set search_path='' as $$
declare s public.service_searches%rowtype; a public.appointments%rowtype;
begin
 select * into s from public.service_searches where id=p_request_id and client_id=auth.uid() and dispatch_kind='GUINCHO_DIRECT' for update;
 if s.id is null then raise exception using errcode='42501',message='CLIENT_REQUIRED'; end if;
 select * into a from public.appointments where id=s.current_appointment_id for update;
 if a.status='CANCELLED' then return jsonb_build_object('outcome','CANCELLED'); end if;
 if a.status<>'AWAITING_PAYMENT' or exists(select 1 from public.payments where request_id=s.id) then
 raise exception using errcode='22023',message='INVALID_TRANSITION'; end if;
 perform private.cancel_guincho_appointment(a.id,'CLIENT_CANCELLED_BEFORE_PAYMENT');
 return jsonb_build_object('outcome','CANCELLED');
end; $$;
revoke all on function public.cancel_unpaid_guincho_request(uuid) from public,anon;
grant execute on function public.cancel_unpaid_guincho_request(uuid) to authenticated;
commit;
