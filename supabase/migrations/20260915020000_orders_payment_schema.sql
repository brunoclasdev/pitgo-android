begin;

alter table public.payments
  add column service_id uuid references public.services(id),
  add column provider_api text not null default 'PAYMENTS' check (provider_api in ('PAYMENTS','ORDERS')),
  add column mp_order_id text unique,
  add column mp_transaction_id text unique,
  add column mp_reference_id text,
  add column external_reference text,
  add column sync_state text not null default 'CURRENT' check (sync_state in ('CREATING','VERIFYING','CURRENT')),
  add column provider_status text,
  add column provider_status_detail text,
  add column transaction_status text,
  add column transaction_status_detail text,
  add column settled_amount numeric(10,2),
  add column refunded_amount numeric(10,2),
  add column pix_qr_code text check (length(pix_qr_code) <= 8192),
  add column pix_qr_code_base64 text check (length(pix_qr_code_base64) <= 262144),
  add column pix_expires_at timestamptz,
  add column cancel_requested_at timestamptz,
  add column cancel_idempotency_key uuid not null default gen_random_uuid(),
  add column refund_idempotency_key uuid not null default gen_random_uuid(),
  add column mp_refund_id text,
  add column refund_submission_state text not null default 'READY'
    check (refund_submission_state in ('READY','UNCERTAIN','SUBMITTED','FAILED','DONE')),
  add column is_compensation boolean not null default false,
  add column revision bigint not null default 1,
  add column recovery_offset integer not null default 0 check(recovery_offset>=0),
  add column next_action_at timestamptz,
  add column work_action text check (work_action in ('RECONCILE','CANCEL','REFUND')),
  add column lease_token uuid,
  add column lease_until timestamptz,
  add column last_checked_at timestamptz,
  add column reconcile_attempts integer not null default 0,
  add column last_error_code text,
  add column support_required_at timestamptz;

-- Historical payments may predate quote linkage. Enforce it only for new Orders.
alter table public.payments drop constraint if exists payments_pittec_context_check;
alter table public.payments add constraint payments_pittec_context_check check (
  provider_api = 'PAYMENTS' or dispatch_kind <> 'PITTEC_AUTO_SEQUENTIAL'
  or (appointment_id is not null and quote_version is not null and quote_version > 0)
);
-- Backfill identity only. Never infer refunded/settled amounts from local status.
update public.payments p set external_reference = p.request_id::text,
  service_id = s.service_id
from public.service_searches s where s.id = p.request_id and s.client_id = p.client_id;
update public.payments set external_reference = request_id::text where external_reference is null;
alter table public.payments alter column external_reference set not null;
alter table public.payments add constraint payments_orders_context check (
  provider_api <> 'ORDERS' or service_id is not null
);
create unique index payments_refund_operation_unique on public.payments(refund_idempotency_key);
create index payments_due_work on public.payments(next_action_at,id) where next_action_at is not null;
create index payments_external_reference on public.payments(provider_api,external_reference);
create index payments_client_history on public.payments(client_id,created_at desc,id desc);

-- The old live constraint remains applicable to legacy rows. New reservations also check
-- legacy REFUND_FAILED under the same request lock (old databases may already contain both).
drop index public.payments_one_live_per_request;
create unique index payments_one_live_per_request on public.payments(request_id)
where not is_compensation and (
  status in ('PENDING','APPROVED','REFUND_PENDING')
  or (provider_api = 'ORDERS' and (sync_state <> 'CURRENT' or status = 'REFUND_FAILED'))
);

create table private.payment_webhook_inbox (
  id uuid primary key default gen_random_uuid(),
  provider_api text not null check (provider_api in ('PAYMENTS','ORDERS')),
  resource_id text not null check (length(resource_id) between 1 and 128),
  event_type text not null,
  dedupe_key text not null unique,
  received_at timestamptz not null default now(),
  next_action_at timestamptz not null default now(),
  lease_token uuid,
  lease_until timestamptz,
  attempts integer not null default 0,
  processed_at timestamptz,
  last_error_code text
);
create index payment_inbox_due on private.payment_webhook_inbox(next_action_at,id)
  where processed_at is null;
alter table private.payment_webhook_inbox enable row level security;
revoke all on private.payment_webhook_inbox from public,anon,authenticated;
revoke all on public.payments from public,anon,authenticated;

commit;
