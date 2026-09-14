// Public endpoint called by Mercado Pago itself (no Supabase session — see
// supabase/config.toml's `verify_jwt = false` for this function). Authenticity comes from the
// x-signature header instead. See specs/018-mercadopago-payments/research.md §5 and
// contracts/mercadopago-edge-functions.md.
import { createClient } from "npm:@supabase/supabase-js@2";

const json = (status: number, body: unknown) => new Response(JSON.stringify(body), {
  status,
  headers: { "content-type": "application/json; charset=utf-8" },
});

const mpStatusToPitGo = (mpStatus: string): string => {
  switch (mpStatus) {
    case "approved": return "APPROVED";
    case "rejected": return "REJECTED";
    case "cancelled": return "CANCELLED";
    case "refunded": return "REFUNDED";
    default: return "PENDING";
  }
};

async function hmacSha256Hex(secret: string, message: string): Promise<string> {
  const key = await crypto.subtle.importKey(
    "raw", new TextEncoder().encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"],
  );
  const signature = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(message));
  return Array.from(new Uint8Array(signature)).map((b) => b.toString(16).padStart(2, "0")).join("");
}

/** Mercado Pago's documented `x-signature: ts=...,v1=...` manifest format. */
async function isValidSignature(
  secret: string,
  signatureHeader: string | null,
  requestId: string | null,
  dataId: string | null,
): Promise<boolean> {
  if (!signatureHeader || !dataId) return false;
  const parts = Object.fromEntries(
    signatureHeader.split(",").map((part) => part.trim().split("=").map((s) => s.trim())),
  );
  const ts = parts.ts;
  const v1 = parts.v1;
  if (!ts || !v1) return false;
  const manifest = `id:${dataId.toLowerCase()};request-id:${requestId ?? ""};ts:${ts};`;
  const expected = await hmacSha256Hex(secret, manifest);
  return expected === v1;
}

Deno.serve(async (request) => {
  if (request.method !== "POST") return json(405, { code: "METHOD_NOT_ALLOWED" });
  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const accessToken = Deno.env.get("MP_ACCESS_TOKEN");
  const webhookSecret = Deno.env.get("MP_WEBHOOK_SECRET");
  if (!supabaseUrl || !serviceKey || !accessToken || !webhookSecret) {
    return json(503, { code: "SERVICE_UNAVAILABLE" });
  }

  let payload: Record<string, unknown>;
  try { payload = await request.json(); } catch { return json(400, { code: "INVALID_INPUT" }); }
  const url = new URL(request.url);
  const dataId = (payload.data as Record<string, unknown> | undefined)?.id as string | undefined
    ?? url.searchParams.get("data.id")
    ?? url.searchParams.get("id");
  const type = (payload.type as string | undefined) ?? url.searchParams.get("type");
  if (type !== "payment" || !dataId) return json(200, { code: "IGNORED" });

  const validSignature = await isValidSignature(
    webhookSecret,
    request.headers.get("x-signature"),
    request.headers.get("x-request-id"),
    String(dataId),
  );
  if (!validSignature) return json(401, { code: "INVALID_SIGNATURE" });

  // Never trust the notification body for the actual status/amount — always refetch.
  const upstream = await fetch(`https://api.mercadopago.com/v1/payments/${dataId}`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  if (!upstream.ok) return json(200, { code: "PAYMENT_LOOKUP_FAILED" });
  const mpPayment = await upstream.json();
  const status = mpStatusToPitGo(String(mpPayment.status ?? ""));

  const admin = createClient(supabaseUrl, serviceKey, { auth: { persistSession: false } });
  const { data: current } = await admin.from("payments").select("id,status,approved_at,request_id,client_id,dispatch_kind,appointment_id,quote_version,amount,currency")
    .eq("mp_payment_id", String(dataId)).maybeSingle();
  if (!current) return json(200, { code: "PAYMENT_NOT_FOUND" });

  const externalReference = String(mpPayment.external_reference ?? "");
  const providerAmount = Number(mpPayment.transaction_amount);
  if (externalReference !== current.request_id || !Number.isFinite(providerAmount) ||
      Math.abs(providerAmount - Number(current.amount)) > 0.01 || current.currency !== "BRL") {
    return json(200, { code: "PAYMENT_CONTEXT_MISMATCH" });
  }

  // Our own refund flow (mp-refund-payment) already moved this out of the live states — never
  // let a stale/duplicate notification revive it back to APPROVED.
  const refundStates = new Set(["REFUND_PENDING", "REFUNDED", "REFUND_FAILED"]);
  if (refundStates.has(current.status) && !refundStates.has(status)) {
    return json(200, { code: "IGNORED_AFTER_REFUND" });
  }

  const { error: updateError } = await admin.from("payments").update({
    status,
    approved_at: status === "APPROVED" ? (current.approved_at ?? new Date().toISOString()) : current.approved_at,
    updated_at: new Date().toISOString(),
  }).eq("id", current.id).eq("mp_payment_id", String(dataId));
  if (updateError) return json(200, { code: "PERSIST_FAILED" });

  return json(200, { code: "PROCESSED" });
});
