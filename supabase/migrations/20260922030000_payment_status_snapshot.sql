begin;

-- Keep the status polling contract identical to private.payment_snapshot().
-- The Android fallback needs the complete payment row to leave Verifying and
-- render Pix details after a delayed Edge Function response.
create or replace function public.get_payment_status(p_request_id uuid) returns jsonb
language sql stable security definer set search_path = '' as $$
  select coalesce(
    (
      select private.payment_snapshot(p)
      from public.payments p
      where p.request_id = p_request_id
        and p.client_id = auth.uid()
      order by
        (p.sync_state <> 'CURRENT' or p.status in ('PENDING','APPROVED','REFUND_PENDING','REFUND_FAILED')) desc,
        p.is_compensation,
        p.created_at desc,
        p.id desc
      limit 1
    ),
    jsonb_build_object('outcome', 'NOT_FOUND')
  );
$$;

revoke all on function public.get_payment_status(uuid) from public, anon, authenticated;
grant execute on function public.get_payment_status(uuid) to authenticated;

commit;
