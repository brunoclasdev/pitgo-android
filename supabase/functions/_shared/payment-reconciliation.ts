import { createClient } from 'npm:@supabase/supabase-js@2';
import { JsonObject, object } from './mercadopago-http.ts';
import { cancelOrder, refundOrder, getOrder, normalizeOrder, PaymentEvidence, searchOrders } from './mercadopago-orders.ts';
import { cancelLegacyPayment, refundLegacyPayment, getLegacyPayment, normalizeLegacyPayment } from './mercadopago-payments.ts';

export function serviceClient() {
  return createClient(Deno.env.get('SUPABASE_URL')!, Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
}
export type Database = ReturnType<typeof serviceClient>;
export async function rpc(db: Database, name: string, args: JsonObject): Promise<unknown> {
  const { data, error } = await db.rpc(name, args);
  if (error) throw new Error('DATABASE_OPERATION_FAILED');
  return data;
}
export function backoff(item: JsonObject): number {
  const age = Date.now() - Date.parse(String(item.created_at));
  const tries = Number(item.reconcile_attempts ?? item.attempts ?? 0);
  const base = age > 86_400_000 ? 3600 : tries <= 1 ? 60 : tries <= 3 ? 300 : 900;
  return base + Math.floor(Math.random() * Math.min(60, base / 5));
}
export async function defer(db: Database, kind: string, item: JsonObject, code: string, seconds = backoff(item), done = false) {
  await rpc(db, 'mp_defer_work', { p_kind: kind, p_id: item.id, p_lease: item.lease_token,
    p_seconds: Math.ceil(seconds), p_code: code, p_done: done });
}
export async function applyEvidence(db: Database, payment: JsonObject, evidence: PaymentEvidence) {
  return object(await rpc(db, 'mp_apply_evidence', { p_id: payment.id, p_revision: payment.revision,
    p_lease: payment.lease_token, p_evidence: evidence }));
}

// Every entry point converges here; webhook bodies never become financial evidence.
export async function reconcile(db: Database, kind: string, item: JsonObject) {
  const api = item.provider_api;
  let id = kind === 'inbox' ? item.resource_id : api === 'ORDERS' ? item.mp_order_id : item.mp_payment_id;
  if (kind === 'payment' && api === 'ORDERS' && (!id || Number(item.recovery_offset ?? 0) > 0)) {
    const found = await searchOrders(String(item.external_reference), String(item.created_at), Number(item.recovery_offset ?? 0));
    if (found.response.status !== 200) {
      await defer(db, kind, item, found.response.code, Math.max(backoff(item), found.response.retryAfter)); return;
    }
    // Additional identities are registered as compensations by the recovery transaction.
    for (const order of found.orders) {
      await rpc(db, 'mp_register_order_identity', { p_id: item.id, p_lease: item.lease_token, p_order: order.id,
        p_reference: order.external_reference });
    }
    const { error } = await db.from('payments').update({ recovery_offset: found.nextOffset })
      .eq('id', item.id).eq('lease_token', item.lease_token);
    if (error) throw new Error('DATABASE_OPERATION_FAILED');
    id = id ?? found.orders[0]?.id;
  }
  if (!id) {
    await defer(db, kind, item, 'RESOURCE_LOOKUP_PENDING');
    return;
  }
  const response = api === 'ORDERS' ? await getOrder(String(id)) : await getLegacyPayment(String(id));
  const evidence = response.status === 200 ? (api === 'ORDERS' ? normalizeOrder(response.body) : normalizeLegacyPayment(response.body)) : null;
  if (!evidence) {
    await defer(db, kind, item, response.status === 200 ? 'UNRECOGNIZED_ORDER' : response.code,
      Math.max(backoff(item), response.retryAfter));
    return;
  }
  const payment = kind === 'payment' ? item : object(await rpc(db, 'mp_claim_payment', {
    p_api: api, p_resource: id, p_reference: evidence.external_reference,
  }));
  if (!payment.id) {
    await defer(db, kind, item, 'PAYMENT_NOT_ASSOCIATED_OR_BUSY');
    return;
  }
  const applied = await applyEvidence(db, payment, evidence);
  if (kind === 'inbox') await defer(db, kind, item, '', 60, true);
  // A recovery search already used two HTTP requests; mutation waits for its next lease.
  if (kind === 'payment' && (item.mp_order_id || item.mp_payment_id)) await performOperation(db, applied);
}

async function performOperation(db: Database, payment: JsonObject) {
  if (!payment.cancel_requested_at || payment.sync_state !== 'CURRENT') return;
  const refund = payment.status === 'REFUND_PENDING' && payment.refund_submission_state === 'READY';
  const cancel = payment.status === 'PENDING' && payment.cancel_submission_state === 'READY'
    && (payment.provider_api === 'ORDERS' ? ['created','action_required'].includes(String(payment.provider_status))
      : ['pending','in_process','authorized'].includes(String(payment.provider_status)));
  if (!refund && !cancel) return;
  const orders = payment.provider_api === 'ORDERS', id = String(orders ? payment.mp_order_id : payment.mp_payment_id);
  const claimed = object(await rpc(db, 'mp_claim_payment', { p_api: payment.provider_api, p_resource: id,
    p_reference: payment.external_reference }));
  if (!claimed.id) return;
  const operation = refund ? 'REFUND' : 'CANCEL';
  const begun = object(await rpc(db, 'mp_begin_operation', { p_id: claimed.id, p_revision: claimed.revision,
    p_lease: claimed.lease_token, p_operation: operation }));
  if (!begun.id) { await defer(db, 'payment', claimed, 'OPERATION_NOT_READY'); return; }
  const key = String(refund ? begun.refund_idempotency_key : begun.cancel_idempotency_key);
  const response = refund ? await (orders ? refundOrder(id, key) : refundLegacyPayment(id, key))
    : await (orders ? cancelOrder(id, key) : cancelLegacyPayment(id, key));
  await rpc(db, 'mp_record_operation', { p_id: begun.id, p_lease: begun.lease_token, p_operation: operation,
    p_http: response.status, p_code: response.code, p_retry_seconds: Math.ceil(response.retryAfter) });
  // Success means submitted, never refunded. A later authoritative GET confirms the money.
}
