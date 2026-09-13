// Creates a Mercado Pago charge (Pix or a previously saved card) for one solicitation.
// See specs/018-mercadopago-payments/contracts/mercadopago-edge-functions.md.
//
// The Access Token never leaves this function. The app only ever sees `payment_id`, `status`
// and, for Pix, the QR data Mercado Pago itself returns.
import { createClient } from "npm:@supabase/supabase-js@2";

const json = (status: number, body: unknown) => new Response(JSON.stringify(body), {
  status,
  headers: { "content-type": "application/json; charset=utf-8" },
});

const DISPATCH_KINDS = new Set(["MOBILE_SEQUENTIAL", "PITTEC_AUTO_SEQUENTIAL"]);
const LIVE_STATUSES = new Set(["PENDING", "APPROVED", "REFUND_PENDING"]);
const PIX_EXPIRATION_MINUTES = 30;

const isUuid = (value: unknown): value is string =>
  typeof value === "string" &&
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);

const mpStatusToPitGo = (mpStatus: string): string => {
  switch (mpStatus) {
    case "approved": return "APPROVED";
    case "rejected": return "REJECTED";
    case "cancelled": return "CANCELLED";
    case "refunded": return "REFUNDED";
    default: return "PENDING"; // pending, in_process, authorized, ...
  }
};

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
  const admin = createClient(supabaseUrl, serviceKey, { auth: { persistSession: false } });

  let payload: Record<string, unknown>;
  try { payload = await request.json(); } catch { return json(400, { code: "INVALID_INPUT" }); }
  const requestId = payload.request_id;
  const dispatchKind = payload.dispatch_kind;
  const amount = Number(payload.amount);
  const method = payload.method;
  const savedCardId = payload.saved_card_id;
  const cardSecurityCodeToken = payload.card_security_code_token;
  if (
    !isUuid(requestId) ||
    typeof dispatchKind !== "string" || !DISPATCH_KINDS.has(dispatchKind) ||
    !Number.isFinite(amount) || amount <= 0 ||
    (method !== "PIX" && method !== "SAVED_CARD") ||
    (method === "SAVED_CARD" && !isUuid(savedCardId))
  ) {
    return json(400, { code: "INVALID_INPUT" });
  }

  // Idempotency (FR-011/SC-006): a live payment for this request already exists — never charge
  // twice for the same solicitation. Refresh it from Mercado Pago instead of creating a new one.
  const { data: existing } = await admin.from("payments").select("*")
    .eq("request_id", requestId).eq("client_id", userData.user.id)
    .order("created_at", { ascending: false }).limit(1).maybeSingle();
  if (existing && LIVE_STATUSES.has(existing.status)) {
    if (existing.status !== "PENDING" || !existing.mp_payment_id) {
      return json(200, { payment_id: existing.id, status: existing.status });
    }
    const refreshed = await fetch(`https://api.mercadopago.com/v1/payments/${existing.mp_payment_id}`, {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    if (!refreshed.ok) return json(502, { code: "PROVIDER_UNAVAILABLE" });
    const mpPayment = await refreshed.json();
    return json(200, buildResponse(existing.id, mpPayment));
  }

  let profile: { cpf: string | null; email: string | null } | null = null;
  {
    const { data } = await admin.from("profiles").select("cpf").eq("id", userData.user.id).maybeSingle();
    profile = { cpf: data?.cpf ?? null, email: userData.user.email ?? null };
  }
  if (method === "PIX" && !profile.cpf) return json(422, { code: "PAYER_DOCUMENT_REQUIRED" });

  const body: Record<string, unknown> = {
    transaction_amount: amount,
    description: "PitGo - solicitação de serviço",
    external_reference: requestId,
    payer: {
      email: profile.email ?? undefined,
      identification: profile.cpf ? { type: "CPF", number: profile.cpf } : undefined,
    },
  };
  let cardBrand: string | null = null;
  let cardLastFour: string | null = null;
  if (method === "PIX") {
    body.payment_method_id = "pix";
    body.date_of_expiration = new Date(Date.now() + PIX_EXPIRATION_MINUTES * 60_000).toISOString();
  } else {
    const { data: card } = await admin.from("saved_cards").select("*")
      .eq("id", savedCardId).eq("client_id", userData.user.id).maybeSingle();
    if (!card) return json(404, { code: "CARD_NOT_FOUND" });
    cardBrand = card.brand;
    cardLastFour = card.last_four_digits;
    // The card's own token identifies it for a stored-card charge; a fresh CVV token (collected
    // by the SDK right before paying, per Mercado Pago's saved-card flow) rides alongside it when
    // the issuer requires the security code again.
    body.token = cardSecurityCodeToken || card.mp_card_id;
    body.installments = 1;
  }

  const attempt = existing ? `${requestId}:${Date.now()}` : requestId;
  let upstream: Response;
  try {
    upstream = await fetch("https://api.mercadopago.com/v1/payments", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/json",
        "X-Idempotency-Key": attempt,
      },
      body: JSON.stringify(body),
    });
  } catch { return json(502, { code: "PROVIDER_UNAVAILABLE" }); }
  if (!upstream.ok && upstream.status !== 201) {
    if (upstream.status === 400 || upstream.status === 422) return json(422, { code: "CARD_REJECTED" });
    return json(502, { code: "PROVIDER_UNAVAILABLE" });
  }
  const mpPayment = await upstream.json();

  const { data: inserted, error: insertError } = await admin.from("payments").insert({
    request_id: requestId,
    client_id: userData.user.id,
    dispatch_kind: dispatchKind,
    method,
    saved_card_id: method === "SAVED_CARD" ? savedCardId : null,
    card_brand: cardBrand,
    card_last_four_digits: cardLastFour,
    amount,
    status: mpStatusToPitGo(mpPayment.status ?? "pending"),
    mp_payment_id: String(mpPayment.id),
    idempotency_key: attempt,
    approved_at: mpPayment.status === "approved" ? new Date().toISOString() : null,
  }).select().single();
  if (insertError || !inserted) return json(502, { code: "PROVIDER_UNAVAILABLE" });

  return json(200, buildResponse(inserted.id, mpPayment));
});

function buildResponse(paymentId: string, mpPayment: Record<string, unknown>) {
  const status = mpStatusToPitGo(String(mpPayment.status ?? "pending"));
  const pixData = (mpPayment as Record<string, any>).point_of_interaction?.transaction_data;
  const response: Record<string, unknown> = { payment_id: paymentId, status };
  if (pixData?.qr_code && pixData?.qr_code_base64) {
    response.pix = {
      qr_code: pixData.qr_code,
      qr_code_base64: pixData.qr_code_base64,
      expires_at: mpPayment.date_of_expiration ?? null,
    };
  }
  return response;
}
