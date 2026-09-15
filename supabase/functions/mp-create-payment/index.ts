import { cents, decimal, mpRequest, object } from '../_shared/mercadopago-http.ts';
import { normalizeOrder } from '../_shared/mercadopago-orders.ts';
import { applyEvidence, defer, serviceClient } from '../_shared/payment-reconciliation.ts';
import { authenticatedUser, isUuid, json, snapshot } from '../_shared/payment-response.ts';

Deno.serve(async req => {
  if (req.method !== 'POST') return json(405, { code: 'METHOD_NOT_ALLOWED' });
  try {
    const db = serviceClient(), user = await authenticatedUser(db, req);
    if (!user) return json(401, { code: 'SESSION_REQUIRED' });
    const input = object(await req.json().catch(() => null));
    if (!isUuid(input.request_id) || !['PIX','SAVED_CARD'].includes(String(input.method))
      || !['MOBILE_SEQUENTIAL','PITTEC_AUTO_SEQUENTIAL'].includes(String(input.dispatch_kind))
      || (input.dispatch_kind === 'MOBILE_SEQUENTIAL' && !isUuid(input.service_id))
      || (input.dispatch_kind === 'PITTEC_AUTO_SEQUENTIAL' && (!isUuid(input.appointment_id)
        || !Number.isSafeInteger(input.quote_version) || Number(input.quote_version) <= 0))
      || (input.amount != null && cents(input.amount) === null)) return json(400, { code: 'INVALID_INPUT' });
    let card = null;
    if (input.method === 'SAVED_CARD') {
      if (!isUuid(input.saved_card_id)) return json(400, { code: 'INVALID_INPUT' });
      if (typeof input.card_security_code_token !== 'string' || !/^[a-zA-Z0-9_-]{1,256}$/.test(input.card_security_code_token)) {
        return json(422, { code: 'CARD_TOKEN_REQUIRED' });
      }
      const { data } = await db.from('saved_cards').select('*').eq('id', input.saved_card_id).eq('client_id', user.id).maybeSingle();
      if (!data) return json(404, { code: 'CARD_NOT_FOUND' });
      card = data;
      if (!card.payment_method_id || !card.payment_type_id) {
        const external = await mpRequest(`/v1/customers/${encodeURIComponent(card.mp_customer_id)}/cards/${encodeURIComponent(card.mp_card_id)}`);
        const method = object(external.body.payment_method);
        if (external.status === 404) return json(404, { code: 'CARD_NOT_FOUND' });
        if (external.status !== 200 || String(external.body.id) !== card.mp_card_id || !method.id
          || !['credit_card','debit_card'].includes(String(method.payment_type_id))) return json(503, { code: 'SERVICE_UNAVAILABLE' });
        card.payment_method_id = method.id; card.payment_type_id = method.payment_type_id;
        const { error } = await db.from('saved_cards').update({ payment_method_id: method.id, payment_type_id: method.payment_type_id })
          .eq('id', card.id).eq('client_id', user.id);
        if (error) return json(503, { code: 'SERVICE_UNAVAILABLE' });
      }
    }
    if (!user.email) return json(422, { code: 'PAYER_DOCUMENT_REQUIRED' });
    const { data, error } = await db.rpc('reserve_order_payment', {
      p_client_id: user.id, p_request_id: input.request_id, p_dispatch_kind: input.dispatch_kind,
      p_service_id: input.service_id ?? null, p_appointment_id: input.appointment_id ?? null,
      p_quote_version: input.quote_version ?? null, p_method: input.method, p_card_id: input.saved_card_id ?? null,
      p_expected_amount: input.amount ?? null, p_creation_enabled: Deno.env.get('MP_ORDERS_ENABLED') === 'true',
    });
    if (error) {
      const code = ['INVALID_INPUT','INVALID_PAYMENT_CONTEXT','PRICE_CHANGED','CARD_NOT_FOUND',
        'SERVICE_NOT_FOUND','SERVICE_UNAVAILABLE'].includes(error.message) ? error.message : 'SERVICE_UNAVAILABLE';
      return json(code === 'SERVICE_UNAVAILABLE' ? 503 : code.endsWith('NOT_FOUND') ? 404 : 409, { code });
    }
    const reservation = object(data);
    let payment = object(reservation.payment);
    if (reservation.created !== true) return json(payment.sync_state === 'CURRENT' ? 200 : 202, snapshot(payment));
    const amount = decimal(cents(payment.amount)!);
    const response = await mpRequest('/v1/orders', 'POST', {
      type: 'online', processing_mode: 'automatic', total_amount: amount,
      external_reference: payment.external_reference, payer: { email: user.email, ...(card ? { customer_id: card.mp_customer_id } : {}) },
      transactions: { payments: [{ amount, ...(card ? {} : { expiration_time: 'PT30M' }),
        payment_method: card ? { id: card.payment_method_id, type: card.payment_type_id,
          token: input.card_security_code_token, installments: 1 } : { id: 'pix', type: 'bank_transfer' } }] },
    }, String(payment.idempotency_key));
    const evidence = [200,201,402].includes(response.status) ? normalizeOrder(response.body) : null;
    if (evidence) payment = await applyEvidence(db, payment, evidence);
    else {
      await defer(db, 'payment', payment, response.code, Math.max(60, response.retryAfter));
      const { data: current, error: readError } = await db.from('payments').select('*').eq('id', payment.id).single();
      if (readError) return json(503, { code: 'SERVICE_UNAVAILABLE' });
      payment = object(current);
    }
    return json(payment.sync_state === 'CURRENT' ? 200 : 202, snapshot(payment));
  } catch {
    return json(503, { code: 'SERVICE_UNAVAILABLE' });
  }
});
