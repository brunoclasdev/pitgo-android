import { object, secureEqual } from '../_shared/mercadopago-http.ts';
import { defer, reconcile, rpc, serviceClient } from '../_shared/payment-reconciliation.ts';

Deno.serve(async req => {
  const secret = Deno.env.get('MP_RECONCILE_SECRET');
  if (req.method !== 'POST') return new Response(null, { status: 405 });
  if (!secret || !await secureEqual(req.headers.get('x-reconcile-secret') ?? '', secret)) {
    return new Response(null, { status: 401 });
  }
  const start = Date.now(), db = serviceClient();
  let completed = 0, failed = 0, released = 0;
  try {
    const raw = await rpc(db, 'mp_claim_work', { p_limit: 20 });
    const work = Array.isArray(raw) ? raw.map(object) : [];
    let cursor = 0;
    await Promise.all(Array.from({ length: 2 }, async () => {
      while (cursor < work.length) {
        const entry = work[cursor++], item = object(entry.item), kind = String(entry.kind);
        // Leave enough budget for one bounded provider request and persistence.
        if (Date.now() - start > 22_000) {
          await defer(db, kind, item, 'BUDGET_DEFERRED', 60);
          released++;
          continue;
        }
        try { await reconcile(db, kind, item); completed++; }
        catch {
          failed++;
          // If persistence is unavailable the 90-second lease still recovers the work.
          await defer(db, kind, item, 'WORKER_RETRY').catch(() => {});
        }
      }
    }));
    console.info(JSON.stringify({ event: 'payment_reconcile_batch', completed, failed, released, duration_ms: Date.now() - start }));
    return Response.json({ completed, failed, released });
  } catch {
    return Response.json({ error: 'RECONCILIATION_UNAVAILABLE' }, { status: 503 });
  }
});
