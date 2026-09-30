begin;
-- PitTec is accepted before payment; directed Guincho is accepted only after payment.
alter table public.appointments drop constraint appointments_confirmation_state_check;
alter table public.appointments add constraint appointments_confirmation_state_check check (
 (guincho_quote_id is not null and status='AWAITING_PAYMENT' and quote_expires_at is not null
   and client_confirmed_quote_version=quote_version and customer_total_amount>0)
 or (status not in ('AWAITING_CLIENT_CONFIRMATION','AWAITING_PAYMENT'))
 or (guincho_quote_id is null and accepted_at is not null and client_confirmation_deadline is not null
   and quoted_price is not null and quoted_price>0)
);
commit;
