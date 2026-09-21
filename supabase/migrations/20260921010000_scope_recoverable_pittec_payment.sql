begin;

-- Do not resume an orphaned PitTec payment from the home screen. A PitTec
-- payment is recoverable only while its dispatch is actively waiting for the
-- client's payment; legacy mobile dispatch keeps its previous recovery rules.
create or replace function public.get_recoverable_payment() returns jsonb
language sql stable security definer set search_path='' as $$
  select coalesce((select private.payment_snapshot(p) from public.payments p
    where p.client_id=auth.uid()
      and (p.sync_state<>'CURRENT'
        or p.status in ('PENDING','REFUND_PENDING','REFUND_FAILED')
        or (p.status='APPROVED' and p.dispatch_kind='MOBILE_SEQUENTIAL'
          and not exists(select 1 from public.service_searches s where s.id=p.request_id)))
      and (
        p.dispatch_kind<>'PITTEC_AUTO_SEQUENTIAL'
        or exists (
          select 1 from public.service_searches s
          where s.id=p.request_id
            and s.client_id=auth.uid()
            and s.status='AWAITING_PAYMENT'
        )
      )
    order by p.created_at desc,p.id desc limit 1),jsonb_build_object('outcome','NOT_FOUND'));
$$;

revoke all on function public.get_recoverable_payment() from public,anon,authenticated;
grant execute on function public.get_recoverable_payment() to authenticated;

commit;
