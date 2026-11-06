import { registrationAccess } from './registration-access.ts';
import { createClient } from 'npm:@supabase/supabase-js@2';
import { object } from './mercadopago-http.ts';
import { authenticatedUser, isUuid, json } from './payment-response.ts';
import { serviceClient } from './payment-reconciliation.ts';

// Feature 024: cancellation is authoritative in the database. Before approval it
// converges to CANCELLED; after approval it remains on the refund state machine.

export async function cancellationResponse(req: Request, refundOnly: boolean) {
  if (req.method !== 'POST') return json(405, { code: 'METHOD_NOT_ALLOWED' });
  try {
    const denied = await registrationAccess(req);
    if (denied) return denied;
    const user = await authenticatedUser(serviceClient(), req);
    if (!user) return json(401, { code: 'SESSION_REQUIRED' });
    const input = object(await req.json().catch(() => null));
    if (!isUuid(input.payment_id)) return json(400, { code: 'INVALID_INPUT' });
    const db = createClient(Deno.env.get('SUPABASE_URL')!, Deno.env.get('SUPABASE_ANON_KEY')!, {
      global: { headers: { Authorization: req.headers.get('authorization')! } }, auth: { persistSession: false },
    });
    const { data, error } = await db.rpc('request_payment_cancellation', { p_payment_id: input.payment_id, p_refund_only: refundOnly });
    if (error) return json(error.message === 'PAYMENT_NOT_FOUND' ? 404 : error.message === 'NOT_REFUNDABLE' ? 409 : 503,
      { code: ['PAYMENT_NOT_FOUND','NOT_REFUNDABLE'].includes(error.message) ? error.message : 'SERVICE_UNAVAILABLE' });
    const result = object(data);
    return json(['CANCELLED','REFUNDED','REJECTED','EXPIRED'].includes(String(result.status)) && result.sync_state === 'CURRENT' ? 200 : 202, result);
  } catch { return json(503, { code: 'SERVICE_UNAVAILABLE' }); }
}
