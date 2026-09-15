begin;
create function private.payment_snapshot(p public.payments) returns jsonb
language sql stable set search_path='' as $$
  select jsonb_build_object('outcome','FOUND','payment_id',p.id,'request_id',p.request_id,
    'service_id',p.service_id,'dispatch_kind',p.dispatch_kind,'appointment_id',p.appointment_id,
    'quote_version',p.quote_version,'status',p.status,'sync_state',p.sync_state,'revision',p.revision,
    'method',p.method,'amount',p.amount::text,'currency',p.currency,'created_at',p.created_at,
    'card_brand',p.card_brand,'card_last_four_digits',p.card_last_four_digits,
    'cancel_requested',p.cancel_requested_at is not null,'support_required',p.support_required_at is not null,
    'pix',case when p.pix_qr_code is not null and p.pix_qr_code_base64 is not null and p.pix_expires_at is not null
      then jsonb_build_object('qr_code',p.pix_qr_code,'qr_code_base64',p.pix_qr_code_base64,'expires_at',p.pix_expires_at) end);
$$;
revoke all on function private.payment_snapshot(public.payments) from public,anon,authenticated;

create or replace function public.get_payment_status(p_request_id uuid) returns jsonb
language sql stable security definer set search_path='' as $$
  select coalesce((select private.payment_snapshot(p) from public.payments p
    where p.request_id=p_request_id and p.client_id=auth.uid()
    order by (p.sync_state<>'CURRENT' or p.status in ('PENDING','APPROVED','REFUND_PENDING','REFUND_FAILED')) desc,
      p.is_compensation,p.created_at desc,p.id desc limit 1),jsonb_build_object('outcome','NOT_FOUND'));
$$;
create or replace function private.broadcast_payment_change() returns trigger
language plpgsql security definer set search_path='' as $$
begin
  if tg_op='UPDATE' and (private.payment_snapshot(old)-'revision')=(private.payment_snapshot(new)-'revision') then
    return new;
  end if;
  perform realtime.send(jsonb_build_object('request_id',new.request_id,'payment_id',new.id,'status',new.status,
    'revision',new.revision,'sync_state',new.sync_state),'CHANGED','payment:'||new.request_id::text,true);
  return new;
end;
$$;

-- The policy must not query a table whose direct privileges are intentionally revoked.
create function private.can_receive_payment(p_topic text) returns boolean
language sql stable security definer set search_path='' as $$
  select exists(select 1 from public.payments where request_id::text=split_part(p_topic,':',2) and client_id=auth.uid());
$$;
revoke all on function private.can_receive_payment(text) from public,anon,authenticated;
grant execute on function private.can_receive_payment(text) to authenticated;
drop policy if exists payment_receive on realtime.messages;
create policy payment_receive on realtime.messages for select to authenticated using (
  extension='broadcast' and split_part(realtime.topic(),':',1)='payment'
  and private.can_receive_payment(realtime.topic())
);
commit;
