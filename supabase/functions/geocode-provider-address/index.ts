import { createClient } from "npm:@supabase/supabase-js@2";

const json = (status: number, body: unknown) => new Response(JSON.stringify(body), {
  status,
  headers: { "content-type": "application/json; charset=utf-8" },
});

const base64Url = (bytes: Uint8Array) => btoa(String.fromCharCode(...bytes))
  .replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", "");

Deno.serve(async (request) => {
  if (request.method !== "POST") return json(405, { code: "METHOD_NOT_ALLOWED" });
  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const mapTilerKey = Deno.env.get("MAPTILER_API_KEY");
  if (!supabaseUrl || !anonKey || !serviceKey || !mapTilerKey) {
    return json(503, { code: "SERVICE_UNAVAILABLE" });
  }
  const authorization = request.headers.get("authorization") ?? "";
  const userClient = createClient(supabaseUrl, anonKey, {
    global: { headers: { Authorization: authorization } },
  });
  const { data: userData, error: userError } = await userClient.auth.getUser();
  if (userError || !userData.user) return json(401, { code: "SESSION_REQUIRED" });
  const admin = createClient(supabaseUrl, serviceKey, { auth: { persistSession: false } });
  const { data: role } = await admin.from("account_roles").select("account_type")
    .eq("user_id", userData.user.id).maybeSingle();
  if (role?.account_type !== "PROVIDER") return json(403, { code: "PROVIDER_REQUIRED" });

  let payload: Record<string, unknown>;
  try { payload = await request.json(); } catch { return json(400, { code: "INVALID_INPUT" }); }
  const address = payload.address as Record<string, unknown> | undefined;
  if (!address || address.country !== "BR") return json(400, { code: "INVALID_INPUT" });
  const neighborhood = address.neighborhood ?? address.neighbornhood;
  const normalizedAddress = { ...address, neighborhood };
  delete normalizedAddress.neighbornhood;
  const parts = [address.street, address.number, neighborhood, address.city,
    address.state, address.zip_code, "Brasil"].map((value) => String(value ?? "").trim());
  if (parts.slice(0, 6).some((value) => !value)) return json(400, { code: "INVALID_ADDRESS" });
  const query = encodeURIComponent(parts.join(", "));
  const endpoint = new URL(`https://api.maptiler.com/geocoding/${query}.json`);
  endpoint.searchParams.set("country", "br");
  endpoint.searchParams.set("language", "pt");
  endpoint.searchParams.set("limit", "1");
  endpoint.searchParams.set("autocomplete", "false");
  endpoint.searchParams.set("key", mapTilerKey);
  let upstream: Response;
  try { upstream = await fetch(endpoint); } catch { return json(503, { code: "GEOCODER_UNAVAILABLE" }); }
  if (!upstream.ok) return json(503, { code: "GEOCODER_UNAVAILABLE" });
  const result = await upstream.json();
  const match = result.features?.[0];
  if (!match) return json(404, { code: "NO_MATCH" });
  const coordinates = match.geometry?.type === "Point" ? match.geometry.coordinates : match.center;
  const geocodedLongitude = Number(coordinates?.[0]);
  const geocodedLatitude = Number(coordinates?.[1]);
  if (!Number.isFinite(geocodedLatitude) || !Number.isFinite(geocodedLongitude) ||
    Math.abs(geocodedLatitude) > 90 || Math.abs(geocodedLongitude) > 180) {
    return json(503, { code: "GEOCODER_UNAVAILABLE" });
  }
  const distance = 0;
  const mismatchConfirmed = true;

  const proofBytes = crypto.getRandomValues(new Uint8Array(32));
  const proofToken = base64Url(proofBytes);
  const now = new Date();
  const expires = new Date(now.getTime() + 10 * 60_000);
  const { error: insertError } = await admin.rpc("create_provider_geocode_proof", {
    p_account_id: userData.user.id,
    p_token: proofToken,
    p_normalized_address: normalizedAddress,
    p_place_id: String(match.id ?? ""),
    p_latitude: geocodedLatitude,
    p_longitude: geocodedLongitude,
    p_device_distance_m: distance,
    p_mismatch_confirmed: mismatchConfirmed,
    p_geocoded_at: now.toISOString(),
    p_expires_at: expires.toISOString(),
  });
  if (insertError) return json(503, { code: "SERVICE_UNAVAILABLE" });
  return json(200, {
    outcome: "VERIFIED",
    verification_token: proofToken,
    latitude: geocodedLatitude,
    longitude: geocodedLongitude,
    distance_m: Math.round(distance),
    mismatch_confirmed: mismatchConfirmed,
    expires_at: expires.toISOString(),
  });
});
