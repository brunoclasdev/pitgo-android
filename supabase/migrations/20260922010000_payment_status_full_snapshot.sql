begin;

-- The Android payment poller needs the same complete, sanitized shape returned
-- by the Edge Functions. The previous projection only returned id/status/method,
-- which made every poll fail client-side contract validation.
create or replace function public.get_payment_status(p_request_id uuid)
returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare row_data public.payments%rowtype;
begin
  select * into row_data
  from public.payments
  where request_id = p_request_id and client_id = auth.uid()
  order by created_at desc
  limit 1;

  if row_data.id is null then
    return jsonb_build_object('outcome', 'NOT_FOUND');
  end if;

  return jsonb_strip_nulls(jsonb_build_object(
    'outcome', 'FOUND',
    'payment_id', row_data.id,
    'request_id', row_data.request_id,
    'status', row_data.status,
    'method', row_data.method,
    'service_id', row_data.service_id,
    'dispatch_kind', row_data.dispatch_kind,
    'appointment_id', row_data.appointment_id,
    'quote_version', row_data.quote_version,
    'sync_state', row_data.sync_state,
    'revision', row_data.revision,
    'amount', row_data.amount::text,
    'currency', row_data.currency,
    'cancel_requested', row_data.cancel_requested_at is not null,
    'support_required', row_data.support_required_at is not null,
    'created_at', row_data.created_at,
    'card_brand', row_data.card_brand,
    'card_last_four_digits', row_data.card_last_four_digits,
    'pix', case when row_data.pix_qr_code is not null
      and row_data.pix_qr_code_base64 is not null
      and row_data.pix_expires_at is not null
      then jsonb_build_object(
        'qr_code', row_data.pix_qr_code,
        'qr_code_base64', row_data.pix_qr_code_base64,
        'expires_at', row_data.pix_expires_at
      ) end
  ));
end;
$$;

notify pgrst, 'reload schema';

commit;
