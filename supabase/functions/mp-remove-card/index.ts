// Removes a saved card: deletes the Customer Card at Mercado Pago, then the local row. Existing
// payments made with it are untouched (they keep their own denormalized card_brand/last_four —
// see specs/018-mercadopago-payments/data-model.md).
import { createClient } from "npm:@supabase/supabase-js@2";

const json = (status: number, body: unknown) => new Response(JSON.stringify(body), {
  status,
  headers: { "content-type": "application/json; charset=utf-8" },
});

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
  const savedCardId = payload.saved_card_id;
  if (typeof savedCardId !== "string" || savedCardId.length === 0) {
    return json(400, { code: "INVALID_INPUT" });
  }

  const admin = createClient(supabaseUrl, serviceKey, { auth: { persistSession: false } });
  const { data: card } = await admin.from("saved_cards").select("*")
    .eq("id", savedCardId).eq("client_id", userData.user.id).maybeSingle();
  if (!card) return json(404, { code: "CARD_NOT_FOUND" });

  const upstream = await fetch(
    `https://api.mercadopago.com/v1/customers/${card.mp_customer_id}/cards/${card.mp_card_id}`,
    { method: "DELETE", headers: { Authorization: `Bearer ${accessToken}` } },
  );
  if (!upstream.ok && upstream.status !== 404) return json(502, { code: "PROVIDER_UNAVAILABLE" });

  // Uses the caller's own session (RLS-scoped remove_saved_card RPC) rather than the admin
  // client directly, so ownership is re-checked at the same layer every other write goes through.
  const { error: rpcError } = await userClient.rpc("remove_saved_card", { p_saved_card_id: savedCardId });
  if (rpcError) return json(502, { code: "PROVIDER_UNAVAILABLE" });

  return new Response(null, { status: 204 });
});
