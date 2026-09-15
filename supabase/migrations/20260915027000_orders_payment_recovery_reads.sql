begin;
create function public.get_recoverable_payment() returns jsonb
language sql stable security definer set search_path='' as $$
  select coalesce((select private.payment_snapshot(p) from public.payments p
    where p.client_id=auth.uid() and (p.sync_state<>'CURRENT'
      or p.status in ('PENDING','REFUND_PENDING','REFUND_FAILED')
      or (p.status='APPROVED' and p.dispatch_kind='MOBILE_SEQUENTIAL'
        and not exists(select 1 from public.service_searches s where s.id=p.request_id)))
    order by p.created_at desc,p.id desc limit 1),jsonb_build_object('outcome','NOT_FOUND'));
$$;
create function public.get_payment_history(p_before timestamptz default null,p_before_id uuid default null,p_limit integer default 20)
returns setof jsonb language sql stable security definer set search_path='' as $$
  select private.payment_snapshot(p) from public.payments p where p.client_id=auth.uid()
    and (p_before is null or (p.created_at,p.id)<(p_before,p_before_id))
  order by p.created_at desc,p.id desc limit least(greatest(p_limit,1),50);
$$;
-- Pending legacy work remains on Payments. No external operation is performed during migration.
update public.payments set next_action_at=now(),work_action='RECONCILE',
  cancel_requested_at=case when status in ('REFUND_PENDING','REFUND_FAILED') then coalesce(cancel_requested_at,now()) else cancel_requested_at end,
  refund_submission_state=case when status in ('REFUND_PENDING','REFUND_FAILED') and refund_attempts>0 then 'UNCERTAIN' else refund_submission_state end
where provider_api='PAYMENTS' and status in ('PENDING','REFUND_PENDING','REFUND_FAILED');
revoke all on function public.get_recoverable_payment(),public.get_payment_history(timestamptz,uuid,integer) from public,anon,authenticated;
grant execute on function public.get_recoverable_payment(),public.get_payment_history(timestamptz,uuid,integer) to authenticated;
commit;
