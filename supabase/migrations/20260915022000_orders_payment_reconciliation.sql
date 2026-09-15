begin;

-- Updating reconciliation metadata must not re-accept an already accepted appointment.
drop trigger if exists reconcile_pittec_payment on public.payments;
create trigger reconcile_pittec_payment_insert after insert on public.payments
for each row when (new.status='APPROVED') execute function private.reconcile_pittec_payment_change();
create trigger reconcile_pittec_payment_update after update of status on public.payments
for each row when (old.status is distinct from new.status and new.status='APPROVED')
execute function private.reconcile_pittec_payment_change();

create function public.mp_apply_evidence(p_id uuid,p_revision bigint,p_lease uuid,p_evidence jsonb)
returns jsonb language plpgsql security definer set search_path='' as $$
declare
  row_data public.payments%rowtype;
  result_status text := p_evidence->>'result';
  next_status text;
  next_sync text := 'CURRENT';
  settled numeric;
  refunded numeric;
  conflicting boolean := false;
begin
  select * into row_data from public.payments where id=p_id for update;
  if row_data.id is null then return null; end if;
  if p_lease is null or row_data.revision is distinct from p_revision or row_data.lease_token is distinct from p_lease
    or row_data.lease_until is null or row_data.lease_until < now() then return to_jsonb(row_data); end if;
  if p_evidence->>'external_reference' is distinct from row_data.external_reference
    or p_evidence->>'currency' is distinct from row_data.currency
    or (p_evidence->>'amount')::numeric is distinct from row_data.amount
    or p_evidence->>'method' is distinct from row_data.method
    or coalesce(p_evidence->>'resource_id','')=''
    or (row_data.provider_api='ORDERS' and row_data.mp_transaction_id is not null
      and row_data.mp_transaction_id is distinct from p_evidence->>'transaction_id')
    or (row_data.provider_api='ORDERS' and row_data.mp_order_id is not null
      and row_data.mp_order_id<>p_evidence->>'resource_id')
    or (row_data.provider_api='PAYMENTS' and row_data.mp_payment_id is not null
      and row_data.mp_payment_id<>p_evidence->>'resource_id') then
    update public.payments set sync_state='VERIFYING', support_required_at=coalesce(support_required_at,now()),
      last_error_code='PAYMENT_CONTEXT_MISMATCH',next_action_at=now()+interval '1 hour',
      work_action='RECONCILE',lease_token=null,lease_until=null,revision=revision+1 where id=p_id;
    return (select to_jsonb(p) from public.payments p where id=p_id);
  end if;
  settled := (p_evidence->>'settled_amount')::numeric;
  refunded := (p_evidence->>'refunded_amount')::numeric;
  next_status := row_data.status;
  if result_status='APPROVED' and settled-refunded=row_data.amount then
    next_status := 'APPROVED';
    if row_data.cancel_requested_at is not null or row_data.status in ('REFUND_PENDING','REFUND_FAILED')
      or row_data.is_compensation then next_status := 'REFUND_PENDING'; end if;
    if row_data.status='REFUND_FAILED' then next_status := 'REFUND_FAILED'; end if;
    conflicting := exists(select 1 from public.payments other where other.request_id=row_data.request_id
      and other.id<>p_id and not other.is_compensation and (
        other.sync_state<>'CURRENT' or other.status in ('PENDING','APPROVED','REFUND_PENDING','REFUND_FAILED')));
    if conflicting then next_status := 'REFUND_PENDING'; end if;
  elsif result_status='REFUNDED' and refunded=row_data.amount then
    next_status := 'REFUNDED';
  elsif result_status in ('REJECTED','EXPIRED','CANCELLED')
    and row_data.status not in ('APPROVED','REFUND_PENDING','REFUND_FAILED','REFUNDED') then
    next_status := result_status;
  elsif result_status='PENDING' and row_data.status='PENDING' then
    next_status := 'PENDING';
  else
    next_sync := 'VERIFYING';
  end if;
  -- A stale event cannot reverse a confirmed refund or erase a known settlement.
  if row_data.status='REFUNDED' then next_status:='REFUNDED'; next_sync:='CURRENT'; end if;
  if row_data.status='APPROVED' and result_status='PENDING' then next_status:='APPROVED'; next_sync:='VERIFYING'; end if;
  update public.payments set
    mp_order_id=case when provider_api='ORDERS' then p_evidence->>'resource_id' else mp_order_id end,
    mp_payment_id=case when provider_api='PAYMENTS' then p_evidence->>'resource_id' else mp_payment_id end,
    mp_transaction_id=case when provider_api='ORDERS' then p_evidence->>'transaction_id' else null end,
    mp_reference_id=p_evidence->>'reference_id',provider_status=p_evidence->>'status',
    provider_status_detail=p_evidence->>'detail',transaction_status=p_evidence->>'transaction_status',
    transaction_status_detail=p_evidence->>'transaction_detail',
    settled_amount=greatest(settled_amount,settled),refunded_amount=greatest(refunded_amount,refunded),
    mp_refund_id=coalesce(p_evidence->>'refund_id',mp_refund_id),
    is_compensation=is_compensation or conflicting,status=next_status,sync_state=next_sync,
    pix_qr_code=case when next_status='PENDING' then p_evidence#>>'{pix,qr_code}' end,
    pix_qr_code_base64=case when next_status='PENDING' then p_evidence#>>'{pix,qr_code_base64}' end,
    pix_expires_at=coalesce((p_evidence#>>'{pix,expires_at}')::timestamptz,pix_expires_at),
    approved_at=case when result_status='APPROVED' then coalesce(approved_at,now()) else approved_at end,
    support_required_at=case when conflicting or next_sync='VERIFYING'
      or (created_at<now()-interval '24 hours' and refund_submission_state in ('UNCERTAIN','SUBMITTED'))
      then coalesce(support_required_at,now()) else support_required_at end,
    refund_submission_state=case when next_status='REFUNDED' then 'DONE' else refund_submission_state end,
    next_action_at=case when next_status in ('PENDING','REFUND_PENDING','REFUND_FAILED') or next_sync='VERIFYING' or recovery_offset>0
      then now()+case when created_at<now()-interval '24 hours' or next_status='REFUND_FAILED' then interval '1 hour'
        when reconcile_attempts>3 then interval '15 minutes' when reconcile_attempts>1 then interval '5 minutes'
        else interval '1 minute' end else null end,
    work_action=case when next_status='REFUND_PENDING' then 'REFUND'
      when cancel_requested_at is not null and next_status='PENDING' then 'CANCEL'
      when next_status in ('PENDING','REFUND_FAILED') or next_sync='VERIFYING' then 'RECONCILE' else null end,
    last_checked_at=now(),last_error_code=null,lease_token=null,lease_until=null,
    revision=revision+1,updated_at=now()
  where id=p_id;
  return (select to_jsonb(p) from public.payments p where id=p_id);
end;
$$;

create function public.mp_claim_work(p_limit integer default 20)
returns setof jsonb language plpgsql security definer set search_path='' as $$
declare
  item record;
  count_claimed integer:=0;
  token uuid;
begin
  -- Alternate the sources with bounded quotas so an inbox flood cannot starve refunds.
  for item in select id from public.payments where next_action_at<=now()
    and (lease_until is null or lease_until<now()) order by next_action_at,id
    limit least(greatest(p_limit,1),20)/2 for update skip locked loop
    token:=gen_random_uuid();
    return query update public.payments set lease_token=token,lease_until=now()+interval '90 seconds',
      reconcile_attempts=reconcile_attempts+1 where id=item.id
      returning jsonb_build_object('kind','payment','item',to_jsonb(payments));
    count_claimed:=count_claimed+1;
  end loop;
  for item in select id from private.payment_webhook_inbox where processed_at is null and next_action_at<=now()
    and (lease_until is null or lease_until<now()) order by next_action_at,id
    limit least(greatest(p_limit,1),20)-count_claimed for update skip locked loop
    token:=gen_random_uuid();
    return query update private.payment_webhook_inbox set lease_token=token,lease_until=now()+interval '90 seconds',
      attempts=attempts+1 where id=item.id returning jsonb_build_object('kind','inbox','item',to_jsonb(payment_webhook_inbox));
  end loop;
end;
$$;

create function public.mp_defer_work(p_kind text,p_id uuid,p_lease uuid,p_seconds integer,p_code text,p_done boolean default false)
returns void language plpgsql security definer set search_path='' as $$
begin
  if p_kind='inbox' then
    update private.payment_webhook_inbox set next_action_at=now()+make_interval(secs=>greatest(60,p_seconds)),
      processed_at=case when p_done then now() else null end,lease_token=null,lease_until=null,
      last_error_code=left(p_code,80) where id=p_id and lease_token=p_lease;
  else
    update public.payments set next_action_at=now()+make_interval(secs=>greatest(60,p_seconds)),
      lease_token=null,lease_until=null,last_error_code=left(p_code,80),
      sync_state=case when sync_state='CREATING' then 'VERIFYING' else sync_state end,
      support_required_at=case when created_at<now()-interval '24 hours' then coalesce(support_required_at,now()) else support_required_at end,
      revision=revision+1 where id=p_id and lease_token=p_lease;
  end if;
end;
$$;

create function public.mp_store_webhook(p_api text,p_resource text,p_event text,p_key text)
returns void language sql security definer set search_path='' as $$
  insert into private.payment_webhook_inbox(provider_api,resource_id,event_type,dedupe_key)
  values(p_api,p_resource,left(p_event,100),p_key) on conflict(dedupe_key) do nothing;
$$;

-- Inbox reconciliation must claim the same payment lease as the scheduled path.
create function public.mp_claim_payment(p_api text,p_resource text,p_reference text)
returns jsonb language plpgsql security definer set search_path='' as $$
declare row_data public.payments%rowtype;
begin
  select * into row_data from public.payments where provider_api=p_api and (
    (p_api='ORDERS' and mp_order_id=p_resource) or (p_api='PAYMENTS' and mp_payment_id=p_resource)
    or (p_api='ORDERS' and mp_order_id is null and external_reference=p_reference)
  ) order by created_at desc limit 1 for update;
  if row_data.id is null or row_data.lease_until>now() then return null; end if;
  update public.payments set lease_token=gen_random_uuid(),lease_until=now()+interval '90 seconds'
    where id=row_data.id returning * into row_data;
  return to_jsonb(row_data);
end;
$$;

revoke all on function public.mp_apply_evidence(uuid,bigint,uuid,jsonb),public.mp_claim_work(integer),
  public.mp_defer_work(text,uuid,uuid,integer,text,boolean),public.mp_store_webhook(text,text,text,text),
  public.mp_claim_payment(text,text,text) from public,anon,authenticated;
grant execute on function public.mp_apply_evidence(uuid,bigint,uuid,jsonb),public.mp_claim_work(integer),
  public.mp_defer_work(text,uuid,uuid,integer,text,boolean),public.mp_store_webhook(text,text,text,text),
  public.mp_claim_payment(text,text,text) to service_role;

commit;
