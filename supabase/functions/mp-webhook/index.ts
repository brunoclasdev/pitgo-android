import { object, secureEqual } from '../_shared/mercadopago-http.ts';
import { rpc, serviceClient } from '../_shared/payment-reconciliation.ts';
import { json } from '../_shared/payment-response.ts';

async function limitedBody(req: Request): Promise<string> {
  if (Number(req.headers.get('content-length') ?? 0) > 65536) throw new Error('BODY_TOO_LARGE');
  const reader = req.body?.getReader();
  if (!reader) return '';
  const chunks: Uint8Array[] = []; let size = 0;
  for (;;) {
    const { value, done } = await reader.read();
    if (done) break;
    size += value.byteLength;
    if (size > 65536) { await reader.cancel(); throw new Error('BODY_TOO_LARGE'); }
    chunks.push(value);
  }
  const all = new Uint8Array(size); let offset = 0;
  for (const chunk of chunks) { all.set(chunk, offset); offset += chunk.byteLength; }
  return new TextDecoder().decode(all);
}
Deno.serve(async req => {
  if (req.method !== 'POST') return json(405, { code: 'METHOD_NOT_ALLOWED' });
  const secret = Deno.env.get('MP_WEBHOOK_SECRET');
  if (!secret) return json(503, { code: 'SERVICE_UNAVAILABLE' });
  let body;
  try { body = object(JSON.parse(await limitedBody(req))); }
  catch (error) { return json(error instanceof Error && error.message === 'BODY_TOO_LARGE' ? 413 : 400, { code: 'INVALID_INPUT' }); }
  const url = new URL(req.url), id = url.searchParams.get('data.id');
  const type = String(body.type ?? url.searchParams.get('type') ?? '');
  const requestId = req.headers.get('x-request-id');
  const parts = Object.fromEntries((req.headers.get('x-signature') ?? '').split(',').map(x => x.trim().split('=')));
  if (!id || !/^[a-zA-Z0-9_-]{1,128}$/.test(id) || !parts.ts || !/^\d+$/.test(parts.ts)
    || !/^[a-f0-9]{64}$/i.test(parts.v1 ?? '') || (type === 'order' && !requestId)) {
    return json(401, { code: 'INVALID_SIGNATURE' });
  }
  const manifest = 'id:' + id.toLowerCase() + ';' + (requestId ? 'request-id:' + requestId + ';' : '') + 'ts:' + parts.ts + ';';
  const key = await crypto.subtle.importKey('raw', new TextEncoder().encode(secret), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
  const signature = await crypto.subtle.sign('HMAC', key, new TextEncoder().encode(manifest));
  const expected = Array.from(new Uint8Array(signature), b => b.toString(16).padStart(2,'0')).join('');
  if (!await secureEqual(expected, parts.v1.toLowerCase())
    || String(object(body.data).id ?? '').toLowerCase() !== id.toLowerCase()
    || (url.searchParams.has('type') && url.searchParams.get('type') !== type)) {
    return json(401, { code: 'INVALID_SIGNATURE' });
  }
  if (!['order','payment'].includes(type)) return json(200, { code: 'IGNORED' });
  try {
    await rpc(serviceClient(), 'mp_store_webhook', { p_api: type === 'order' ? 'ORDERS' : 'PAYMENTS',
      p_resource: id, p_event: String(body.action ?? type),
      p_key: type + ':' + id + ':' + parts.ts + ':' + expected });
    return json(200, { code: 'RECEIVED' });
  } catch { return json(503, { code: 'PERSIST_FAILED' }); }
});
