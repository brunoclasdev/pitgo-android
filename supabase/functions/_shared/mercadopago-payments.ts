import { cents, decimal, JsonObject, mpRequest, object } from './mercadopago-http.ts';
import { PaymentEvidence } from './mercadopago-orders.ts';

export const getLegacyPayment = (id: string) => mpRequest(`/v1/payments/${encodeURIComponent(id)}`);
export const cancelLegacyPayment = (id: string, key: string) => mpRequest(`/v1/payments/${encodeURIComponent(id)}`, 'PUT', { status: 'cancelled' }, key);
export const refundLegacyPayment = (id: string, key: string) => mpRequest(`/v1/payments/${encodeURIComponent(id)}/refunds`, 'POST', undefined, key);
export function normalizeLegacyPayment(p: JsonObject): PaymentEvidence | null {
  const amount = cents(p.transaction_amount);
  if (!p.id || amount === null) return null;
  const status = String(p.status ?? ''), detail = String(p.status_detail ?? '');
  const refunded = cents(p.transaction_amount_refunded);
  const paid = cents(object(p.transaction_details).total_paid_amount);
  let result: PaymentEvidence['result'] = 'VERIFYING';
  if (status === 'approved' && detail === 'accredited' && paid === amount && refunded === 0) result = 'APPROVED';
  else if (status === 'refunded' && refunded === amount) result = 'REFUNDED';
  else if (['pending','in_process','authorized'].includes(status) && (paid === null || paid === 0)) result = 'PENDING';
  else if (status === 'rejected' && (paid === null || paid === 0)) result = 'REJECTED';
  else if (status === 'cancelled' && (paid === null || paid === 0)) result = detail === 'expired' ? 'EXPIRED' : 'CANCELLED';
  const pix = object(object(p.point_of_interaction).transaction_data);
  const refunds = Array.isArray(p.refunds) ? p.refunds.map(object) : [];
  return { resource_id: String(p.id), external_reference: String(p.external_reference ?? ''),
    transaction_id: null, reference_id: null, amount: decimal(amount), currency: String(p.currency_id ?? ''),
    status, detail, transaction_status: status, transaction_detail: detail, result,
    settled_amount: paid === null ? null : decimal(paid), refunded_amount: refunded === null ? null : decimal(refunded),
    refund_id: refunds[0]?.id == null ? null : String(refunds[0].id),
    method: p.payment_method_id === 'pix' ? 'PIX' : 'SAVED_CARD',
    pix: typeof pix.qr_code === 'string' && pix.qr_code.length <= 8192
      && typeof pix.qr_code_base64 === 'string' && pix.qr_code_base64.length <= 262144
      && Number.isFinite(Date.parse(String(p.date_of_expiration)))
      ? { qr_code: pix.qr_code, qr_code_base64: pix.qr_code_base64, expires_at: String(p.date_of_expiration) } : null,
  };
}
