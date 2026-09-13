// Refunds an approved payment whose solicitation ended without service (FR-015/FR-017): the app
// calls this right after a cancel/exhausted-search RPC response reports REFUND_PENDING. Retries
// synchronously inside this single invocation (research.md §7) — no external queue needed.
import { createClient } from "npm:@supabase/supabase-js@2";

const json = (status: number, body: unknown) => new Response(JSON.stringify(body), {
  status,
  headers: { "content-type": "application/json; charset=utf-8" },
});

const RETRY_DELAYS_MS = [2_000, 8_000, 20_000];
const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

Deno.serve(async (request) => {
  if (request.method !== "POST") return json(405, { code: "METHOD_NOT_ALLOWED" });
  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const accessToken = Deno.env.get("MP_ACCESS_TOKEN");
  if (!supabaseUrl || !anonKey || !serviceKey || !accessToken) {
    return json(503, { code: "SERVICE_UNAVAILABLE" });
  }

  const authorization = request.headers.get("authorization") ?? "";
  const userClient = createClient(supabaseUrl, anonKey, {
    global: { headers: { Authorization: authorization } },
  });
  const { data: userData, error: userError } = await userClient.auth.getUser();
  if (userError || !userData.user) return json(401, { code: "SESSION_REQUIRED" });

  let payload: Record<string, unknown>;
  try { payload = await request.json(); } catch { return json(400, { code: "INVALID_INPUT" }); }
  const paymentId = payload.payment_id;
  if (typeof paymentId !== "string" || paymentId.length === 0) {
    return json(400, { code: "INVALID_INPUT" });
  }

  const admin = createClient(supabaseUrl, serviceKey, { auth: { persistSession: false } });
  const { data: payment } = await admin.from("payments").select("*")
    .eq("id", paymentId).eq("client_id", userData.user.id).maybeSingle();
  if (!payment) return json(404, { code: "PAYMENT_NOT_FOUND" });
  if (payment.status === "REFUNDED" || payment.status === "REFUND_FAILED") {
    return json(200, { status: payment.status });
  }
  if (payment.status !== "REFUND_PENDING") return json(409, { code: "NOT_REFUNDABLE" });

  let attempts = payment.refund_attempts as number;
  let refunded = false;
  while (attempts < 3 && !refunded) {
    const response = await fetch(
      `https://api.mercadopago.com/v1/payments/${payment.mp_payment_id}/refunds`,
      { method: "POST", headers: { Authorization: `Bearer ${accessToken}` } },
    );
    attempts += 1;
    if (response.ok) {
      refunded = true;
      break;
    }
    if (attempts < 3) await sleep(RETRY_DELAYS_MS[attempts - 1] ?? RETRY_DELAYS_MS.at(-1)!);
  }

  const finalStatus = refunded ? "REFUNDED" : "REFUND_FAILED";
  await admin.from("payments").update({
    status: finalStatus,
    refund_attempts: attempts,
    updated_at: new Date().toISOString(),
  }).eq("id", paymentId);

  return json(200, { status: finalStatus });
});
