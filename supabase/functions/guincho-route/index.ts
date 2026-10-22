import { createClient } from "npm:@supabase/supabase-js@2";

const json = (http: number, body: unknown) => new Response(JSON.stringify(body), {
  status: http, headers: { "content-type": "application/json; charset=utf-8" },
});
const unavailable = (http: number, code: string, retryable = true, retry = 1000) =>
  json(http, { status: "UNAVAILABLE", code, retryable, retry_after_ms: retry });

Deno.serve(async (request) => {
  if (request.method !== "POST") return unavailable(405, "METHOD_NOT_ALLOWED", false);
  const url = Deno.env.get("SUPABASE_URL"), anon = Deno.env.get("SUPABASE_ANON_KEY"), service = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !anon || !service) return unavailable(503, "SERVICE_UNAVAILABLE");
  const user = createClient(url, anon, { global: { headers: { Authorization: request.headers.get("authorization") ?? "" } }, auth: { persistSession: false } });
  const { data: identity, error: authError } = await user.auth.getUser();
  if (authError || !identity.user) return unavailable(401, "AUTH_REQUIRED", false);
  let body;
  try { body = await request.json(); } catch { return unavailable(400, "INVALID_REQUEST", false); }
  if (!body || typeof body !== "object" || Array.isArray(body) || Object.keys(body).some(k => !["appointment_id", "reason", "known_route_version"].includes(k)) ||
      typeof body.appointment_id !== "string" || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(body.appointment_id) ||
      !["INITIAL", "RESUME", "DEVIATION", "RETRY"].includes(body.reason) || !Number.isSafeInteger(body.known_route_version) || body.known_route_version < 0)
    return unavailable(400, "INVALID_REQUEST", false);

  const admin = createClient(url, service, { auth: { persistSession: false } });
  const appointment = body.appointment_id;
  const { data: claim, error } = await admin.rpc("claim_guincho_route_refresh", {
    p_appointment_id: appointment, p_requesting_user_id: identity.user.id, p_reason: body.reason, p_known_route_version: body.known_route_version,
  });
  if (error) {
    if (error.message.includes("NOT_PARTICIPANT")) return unavailable(403, "NOT_PARTICIPANT", false);
    if (error.message.includes("APPOINTMENT_NOT_TRACKABLE")) return unavailable(409, "APPOINTMENT_NOT_TRACKABLE", false);
    return unavailable(503, "SERVICE_UNAVAILABLE");
  }
  if (claim?.outcome === "REUSED") return json(200, { ...claim, status: "ROUTE" });
  if (claim?.outcome === "BUSY") return json(202, { status: "PENDING", code: "ROUTE_UPDATING", retryable: true, retry_after_ms: 500 });
  if (claim?.outcome === "RETRY_AFTER") return unavailable(429, "RATE_LIMITED", true, claim.retry_after_ms);
  if (["LOCATION_NOT_READY", "LOCATION_UNCERTAIN"].includes(claim?.outcome)) return unavailable(409, claim.outcome);
  if (claim?.outcome !== "CLAIMED") return unavailable(503, "SERVICE_UNAVAILABLE");
  const started = performance.now();
  const fail = async (code: string, http: number) => {
    const { data } = await admin.rpc("fail_guincho_route_refresh", { p_appointment_id: appointment, p_lease_id: claim.lease_id, p_failure_code: code });
    console.log(JSON.stringify({ fn: "guincho-route", outcome: code, latency_ms: Math.round(performance.now() - started) }));
    return unavailable(http, code, true, data?.retry_after_ms ?? 1000);
  };
  const base = Deno.env.get("PITTEC_ROUTING_BASE_URL");
  if (!base) return await fail("SERVICE_UNAVAILABLE", 503);
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 4000);
  try {
    const target = new URL(`${base.replace(/\/$/, "")}/${claim.origin.longitude},${claim.origin.latitude};${claim.destination.longitude},${claim.destination.latitude}`);
    target.searchParams.set("overview", "full"); target.searchParams.set("geometries", "geojson");
    target.searchParams.set("steps", "false"); target.searchParams.set("alternatives", "false");
    const key = Deno.env.get("PITTEC_ROUTING_API_KEY"); if (key) target.searchParams.set("key", key);
    const response = await fetch(target, { signal: controller.signal });
    if (!response.ok || !response.body) return await fail("ROUTER_FAILURE", 502);
    const reader = response.body.getReader(); const chunks: Uint8Array[] = []; let size = 0;
    try {
      while (true) {
        const { value, done } = await reader.read(); if (done) break;
        size += value.byteLength;
        if (size > 1_048_576) { await reader.cancel(); return await fail("ROUTER_INVALID_RESPONSE", 502); }
        chunks.push(value);
      }
    } finally { reader.releaseLock(); }
    const bytes = new Uint8Array(size); let offset = 0;
    for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.length; }
    const result = JSON.parse(new TextDecoder().decode(bytes));
    const route = result?.routes?.[0], geometry = route?.geometry, distance = Math.round(Number(route?.distance));
    const duration = typeof route?.duration === "number" && Number.isFinite(route.duration) && route.duration >= 0 && route.duration <= 604_800
      ? Math.ceil(route.duration) : null;
    if (result?.code !== "Ok" || geometry?.type !== "LineString" || !Array.isArray(geometry.coordinates) || geometry.coordinates.length < 2 || geometry.coordinates.length > 10_000 ||
        !geometry.coordinates.every((p: unknown) => Array.isArray(p) && p.length === 2 && p.every(v => typeof v === "number" && Number.isFinite(v)) && Math.abs(p[0]) <= 180 && Math.abs(p[1]) <= 90) ||
        !Number.isSafeInteger(distance) || distance < 0 || distance > 2_000_000 || duration === null) return await fail("ROUTER_INVALID_RESPONSE", 502);
    clearTimeout(timer);
    const { data: saved, error: saveError } = await admin.rpc("finish_guincho_route_refresh", { p_appointment_id: appointment, p_lease_id: claim.lease_id, p_geometry: { ...geometry, duration_seconds: duration }, p_distance_meters: distance });
    if (saveError) return await fail("SERVICE_UNAVAILABLE", 503);
    if (saved?.outcome === "LEASE_LOST") {
      const { data: latest, error: denied } = await user.rpc("get_guincho_route_snapshot", { p_appointment_id: appointment });
      if (denied) return unavailable(409, "APPOINTMENT_NOT_TRACKABLE", false);
      if (latest?.outcome === "FOUND") return json(200, { ...latest, status: "ROUTE", reused: true });
      return json(202, { status: "PENDING", code: "ROUTE_UPDATING", retryable: true, retry_after_ms: 500 });
    }
    console.log(JSON.stringify({ fn: "guincho-route", outcome: "ROUTE", reason: body.reason, latency_ms: Math.round(performance.now() - started) }));
    return json(200, { ...saved, status: "ROUTE", reused: false });
  } catch (error) {
    return await fail(controller.signal.aborted ? "ROUTER_TIMEOUT" : error instanceof SyntaxError ? "ROUTER_INVALID_RESPONSE" : "ROUTER_FAILURE", controller.signal.aborted ? 504 : 502);
  } finally { clearTimeout(timer); }
});
