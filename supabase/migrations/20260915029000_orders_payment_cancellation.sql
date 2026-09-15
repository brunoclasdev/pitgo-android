begin;
alter table public.payments add column cancel_submission_state text not null default 'READY'
  check(cancel_submission_state in ('READY','UNCERTAIN','SUBMITTED','FAILED'));

create function private.persist_payment_cancellation() returns trigger
language plpgsql security definer set search_path='' as $$
begin
  if tg_table_name='service_searches' then
    if new.status not in ('CANCELLED','EXHAUSTED') or old.status=new.status or old.status='COMPLETED' then return new; end if;
    update public.payments set cancel_requested_at=coalesce(cancel_requested_at,now()),
      status=case when status='APPROVED' then 'REFUND_PENDING' else status end,
      next_action_at=now(),work_action='RECONCILE',lease_token=null,lease_until=null,revision=revision+1
    where request_id=new.id and (status in ('PENDING','APPROVED','REFUND_PENDING','REFUND_FAILED') or sync_state<>'CURRENT');
  else
    if new.status<>'CANCELLED' or old.status=new.status or old.status='COMPLETED' then return new; end if;
    -- Only the quoted PitTec appointment owns a payment. Mobile provider declines can resume
    -- the same funded search and must NOT refund that obligation.
    update public.payments set cancel_requested_at=coalesce(cancel_requested_at,now()),
      status=case when status='APPROVED' then 'REFUND_PENDING' else status end,
      next_action_at=now(),work_action='RECONCILE',lease_token=null,lease_until=null,revision=revision+1
    where appointment_id=new.id and dispatch_kind='PITTEC_AUTO_SEQUENTIAL'
      and (status in ('PENDING','APPROVED','REFUND_PENDING','REFUND_FAILED') or sync_state<>'CURRENT');
  end if;
  return new;
end;
$$;
create trigger persist_search_payment_cancellation after update of status on public.service_searches
for each row execute function private.persist_payment_cancellation();
create trigger persist_appointment_payment_cancellation after update of status on public.appointments
for each row execute function private.persist_payment_cancellation();

create function public.request_payment_cancellation(p_payment_id uuid,p_refund_only boolean default false)
returns jsonb language plpgsql security definer set search_path='' as $$
declare p public.payments%rowtype; s public.service_searches%rowtype; a public.appointments%rowtype;
begin
  select * into p from public.payments where id=p_payment_id and client_id=auth.uid();
  if p.id is null then raise exception using errcode='P0002',message='PAYMENT_NOT_FOUND'; end if;
  select * into s from public.service_searches where id=p.request_id for update;
  select * into a from public.appointments where id=coalesce(p.appointment_id,s.current_appointment_id) for update;
  select * into p from public.payments where id=p_payment_id for update;
  if p.status='REFUNDED' then return private.payment_snapshot(p); end if;
  if p_refund_only and p.cancel_requested_at is null and p.status not in ('REFUND_PENDING','REFUND_FAILED') then
    raise exception using errcode='22023',message='NOT_REFUNDABLE';
  end if;
  if s.status='COMPLETED' or a.status='COMPLETED' then
    raise exception using errcode='22023',message='NOT_REFUNDABLE';
  end if;
  if p.cancel_requested_at is null and s.id is not null and s.status not in ('CANCELLED','EXHAUSTED') then
    if p.dispatch_kind='PITTEC_AUTO_SEQUENTIAL' then
      perform public.cancel_pittec_search(gen_random_uuid(),s.id,s.revision);
    elsif a.id is not null then
      perform public.cancel_mobile_appointment(a.id,'CLIENT_CANCELLED',a.revision);
    else
      update public.service_searches set status='CANCELLED',ended_at=now(),updated_at=now(),revision=revision+1,
        search_latitude=null,search_longitude=null,search_point=null,search_accuracy_m=null
        where id=s.id and client_id=auth.uid();
      update private.dispatch_candidates set status='INVALIDATED',decided_at=now()
        where search_id=s.id and status not in ('ACCEPTED','INVALIDATED');
    end if;
  end if;
  update public.payments set cancel_requested_at=coalesce(cancel_requested_at,now()),
    status=case when status='APPROVED' then 'REFUND_PENDING' else status end,
    next_action_at=case when status in ('PENDING','APPROVED','REFUND_PENDING','REFUND_FAILED') or sync_state<>'CURRENT' then now() else next_action_at end,
    work_action='RECONCILE',lease_token=null,lease_until=null,revision=revision+1
  where id=p.id and cancel_requested_at is null;
  return (select private.payment_snapshot(current) from public.payments current where id=p.id);
end;
$$;

create function public.mp_begin_operation(p_id uuid,p_revision bigint,p_lease uuid,p_operation text)
returns jsonb language plpgsql security definer set search_path='' as $$
declare p public.payments%rowtype;
begin
  select * into p from public.payments where id=p_id for update;
  if p.id is null or p.revision is distinct from p_revision or p.lease_token is distinct from p_lease
    or p_lease is null or p.lease_until<now() or p.cancel_requested_at is null or p.sync_state<>'CURRENT' then return null; end if;
  if p_operation='REFUND' then
    if p.status<>'REFUND_PENDING' or p.refund_submission_state<>'READY' or p.refund_attempts>=3
      or p.settled_amount-coalesce(p.refunded_amount,0) is distinct from p.amount then return null; end if;
    update public.payments set refund_attempts=refund_attempts+1,refund_submission_state='UNCERTAIN',revision=revision+1
      where id=p.id returning * into p;
  elsif p_operation='CANCEL' then
    if p.status<>'PENDING' or p.cancel_submission_state<>'READY' then return null; end if;
    update public.payments set cancel_submission_state='UNCERTAIN',revision=revision+1 where id=p.id returning * into p;
  else return null; end if;
  return to_jsonb(p);
end;
$$;

create function public.mp_record_operation(p_id uuid,p_lease uuid,p_operation text,p_http integer,p_code text,p_retry_seconds integer)
returns void language plpgsql security definer set search_path='' as $$
declare p public.payments%rowtype; result text; permanent boolean;
begin
  select * into p from public.payments where id=p_id and lease_token=p_lease for update;
  if p.id is null then return; end if;
  permanent:=p_http in (400,401,403,404,422);
  result:=case when p_http between 200 and 299 then 'SUBMITTED' when p_http=429 then 'READY'
    when permanent then 'FAILED' else 'UNCERTAIN' end;
  if p_operation='REFUND' and p.refund_attempts>=3 and result='READY' then result:='FAILED'; end if;
  update public.payments set
    refund_submission_state=case when p_operation='REFUND' then result else refund_submission_state end,
    cancel_submission_state=case when p_operation='CANCEL' then result else cancel_submission_state end,
    status=case when p_operation='REFUND' and result='FAILED' then 'REFUND_FAILED' else status end,
    support_required_at=case when result='FAILED' then coalesce(support_required_at,now()) else support_required_at end,
    last_error_code=left(p_code,80),next_action_at=now()+make_interval(secs=>greatest(p_retry_seconds,
      case when result='FAILED' then 3600 when p.refund_attempts>=2 then 300 else 60 end)),
    work_action='RECONCILE',lease_token=null,lease_until=null,revision=revision+1 where id=p.id;
end;
$$;
revoke all on function private.persist_payment_cancellation(),public.request_payment_cancellation(uuid,boolean),
  public.mp_begin_operation(uuid,bigint,uuid,text),public.mp_record_operation(uuid,uuid,text,integer,text,integer) from public,anon,authenticated;
grant execute on function public.request_payment_cancellation(uuid,boolean) to authenticated;
grant execute on function public.mp_begin_operation(uuid,bigint,uuid,text),public.mp_record_operation(uuid,uuid,text,integer,text,integer) to service_role;
commit;
