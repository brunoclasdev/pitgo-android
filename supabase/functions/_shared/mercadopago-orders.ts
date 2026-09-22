import { cents, decimal, JsonObject, mpRequest, object } from './mercadopago-http.ts';

export interface PaymentEvidence {
  external_reference: string;
  resource_id: string;
  transaction_id: string | null;
  reference_id: string | null;
  amount: string;
  currency: string;
  status: string;
  detail: string;
  transaction_status: string;
  transaction_detail: string;
  result: 'PENDING' | 'APPROVED' | 'REJECTED' | 'EXPIRED' | 'CANCELLED' | 'REFUNDED' | 'VERIFYING';
  settled_amount: string | null;
  refunded_amount: string | null;
  refund_id: string | null;
  method: 'PIX' | 'SAVED_CARD';
  pix: { qr_code: string; qr_code_base64: string; expires_at: string } | null;
}

export function normalizeOrder(order: JsonObject): PaymentEvidence | null {
  const tx = object(order.transactions);
  const payments = Array.isArray(tx.payments) ? tx.payments : [];
  if (payments.length !== 1 || order.type !== 'online') return null;
  const payment = object(payments[0]), method = object(payment.payment_method);
  const amount = cents(order.total_amount);
  if (!order.id || !payment.id || amount === null || cents(payment.amount) !== amount) return null;
  const status = String(order.status ?? ''), detail = String(order.status_detail ?? '');
  const ps = String(payment.status ?? ''), pd = String(payment.status_detail ?? '');
  const refunds = Array.isArray(tx.refunds) ? tx.refunds.map(object) : [];
  const confirmed = refunds.filter(r => r.status === 'processed' && r.transaction_id === payment.id);
  const values = confirmed.map(r => cents(r.amount));
  const refunded = values.every(v => v !== null) ? values.reduce<number>((sum,v) => sum + (v ?? 0), 0) : null;
  const paid = cents(order.total_paid_amount ?? payment.paid_amount);
  let result: PaymentEvidence['result'] = 'VERIFYING';
  if (status === 'processed' && detail === 'accredited' && ps === 'processed' && pd === 'accredited'
    && paid === amount && refunded === 0) result = 'APPROVED';
  else if (['created','processing','action_required'].includes(status)
    && ['created','processing','action_required'].includes(ps) && (paid === null || paid === 0)) result = 'PENDING';
  else if (status === 'failed' && ps === 'failed' && (paid === null || paid === 0)) result = 'REJECTED';
  else if (['canceled','expired'].includes(status) && ['canceled','expired'].includes(ps)
    && (paid === null || paid === 0)) result = status === 'expired' ? 'EXPIRED' : 'CANCELLED';
  if ((status === 'refunded' || detail === 'refunded') && refunded === amount) result = 'REFUNDED';
  if ((Array.isArray(tx.chargebacks) && tx.chargebacks.length) || detail === 'partially_refunded') result = 'VERIFYING';
  const rawExpiry = payment.date_of_expiration;
  const created = Date.parse(String(order.created_date ?? ''));
  const expiry = rawExpiry ? Date.parse(String(rawExpiry)) : created + 30 * 60_000;
  const pix = method.id === 'pix' && typeof method.qr_code === 'string'
    && typeof method.qr_code_base64 === 'string' && Number.isFinite(expiry)
    && method.qr_code.length <= 8192 && method.qr_code_base64.length <= 262144
    ? { qr_code: method.qr_code, qr_code_base64: method.qr_code_base64, expires_at: new Date(expiry).toISOString() } : null;
  return {
    external_reference: String(order.external_reference ?? ''), resource_id: String(order.id),
    transaction_id: String(payment.id), reference_id: payment.reference_id == null ? null : String(payment.reference_id),
    amount: decimal(amount),
    // Mercado Pago returns both `BR` and `BRA` for Brazilian Orders depending
    // on the API response/version. Both represent BRL for this integration.
    currency: ['BR', 'BRA'].includes(String(order.country_code ?? '').toUpperCase())
      ? 'BRL' : String(order.currency_id ?? ''),
    status, detail, transaction_status: ps, transaction_detail: pd, result,
    settled_amount: paid === null ? null : decimal(paid),
    refunded_amount: refunded === null ? null : decimal(refunded),
    refund_id: confirmed[0]?.id == null ? null : String(confirmed[0].id),
    method: method.id === 'pix' ? 'PIX' : 'SAVED_CARD', pix,
  };
}

export async function getOrder(id: string) {
  return await mpRequest(`/v1/orders/${encodeURIComponent(id)}`);
}
export const cancelOrder = (id: string, key: string) => mpRequest(`/v1/orders/${encodeURIComponent(id)}/cancel`, 'POST', undefined, key);
export const refundOrder = (id: string, key: string) => mpRequest(`/v1/orders/${encodeURIComponent(id)}/refund`, 'POST', undefined, key);

// One bounded page per work item; the database persists the cursor across leases.
export async function searchOrders(reference: string, createdAt: string, offset = 0) {
  const begin = new Date(Date.parse(createdAt) - 300_000).toISOString();
  const end = new Date(Date.parse(createdAt) + 86_400_000).toISOString();
  const query = new URLSearchParams({ external_reference: reference, begin_date: begin,
    end_date: end, type: 'online', limit: '50', offset: String(offset) });
  const response = await mpRequest(`/v1/orders?${query}`);
  const orders = Array.isArray(response.body.data) ? response.body.data.map(object)
    .filter(o => o.external_reference === reference && o.id) : [];
  const paging = object(response.body.paging);
  const total = Number(paging.total ?? 0);
  return { response, orders, nextOffset: offset + 50 < total ? offset + 50 : 0 };
}
