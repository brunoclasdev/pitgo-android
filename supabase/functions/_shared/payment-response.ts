import { JsonObject } from './mercadopago-http.ts';
import { Database } from './payment-reconciliation.ts';

export const json = (status: number, body: unknown) => Response.json(body, { status });
export const isUuid = (v: unknown): v is string => typeof v === 'string'
  && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(v);
export async function authenticatedUser(db: Database, req: Request) {
  const bearer = req.headers.get('authorization') ?? '';
  if (!bearer.startsWith('Bearer ')) return null;
  const { data, error } = await db.auth.getUser(bearer.slice(7));
  return error ? null : data.user;
}
// Explicit allowlist: credentials and operation keys never enter API snapshots.
export function snapshot(p: JsonObject) {
  return {
    outcome: 'FOUND', payment_id: p.id, request_id: p.request_id, service_id: p.service_id,
    dispatch_kind: p.dispatch_kind, appointment_id: p.appointment_id, quote_version: p.quote_version,
    status: p.status, sync_state: p.sync_state, revision: p.revision, method: p.method,
    amount: String(p.amount), currency: p.currency, created_at: p.created_at,
    card_brand: p.card_brand, card_last_four_digits: p.card_last_four_digits,
    cancel_requested: p.cancel_requested_at != null, support_required: p.support_required_at != null,
    pix: p.pix_qr_code && p.pix_qr_code_base64 && p.pix_expires_at ? {
      qr_code: p.pix_qr_code, qr_code_base64: p.pix_qr_code_base64, expires_at: p.pix_expires_at,
    } : null,
  };
}
