import { createClient } from "npm:@supabase/supabase-js@2";

// See contracts/pittec-routing-ui.md. This function never accepts coordinates from the request
// body — it reads both exact participant positions itself, server-side, using the caller's own
// JWT so RLS/RPC participant checks apply exactly as they do everywhere else in the app.

const json = (status: number, body: unknown) => new Response(JSON.stringify(body), {
  status,
  headers: { "content-type": "application/json; charset=utf-8" },
});

const unavailable = (status: number, code: string, retryable: boolean) =>
  json(status, { status: "UNAVAILABLE", code, retryable });

// Per-instance only: a cold isolate or a different instance under load can still race a second
// upstream call for the same appointment. That is safe — `upsert_pittec_route_snapshot` rejects
// an older `provider_location_revision` at the database level — this just avoids paying for two
// upstream calls in the common case of two near-simultaneous requests on the same warm instance.
const inFlight = new Map<string, Promise<Response>>();

const MIN_REFRESH_INTERVAL_MS = 15_000;
const MAX_UPSTREAM_POINTS = 10_000;
const MAX_DISTANCE_METERS = 2_000_000;
const UPSTREAM_TIMEOUT_MS = 8_000;

Deno.serve(async (request) => {
  if (request.method !== "POST") return json(405, { code: "METHOD_NOT_ALLOWED" });

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const routingBaseUrl = Deno.env.get("PITTEC_ROUTING_BASE_URL");
  const routingApiKey = Deno.env.get("PITTEC_ROUTING_API_KEY");
  if (!supabaseUrl || !anonKey || !serviceKey || !routingBaseUrl) {
    return unavailable(503, "SERVICE_UNAVAILABLE", true);
  }

  let payload: Record<string, unknown>;
  try {
    payload = await request.json();
  } catch {
    return json(400, { status: "UNAVAILABLE", code: "INVALID_REQUEST", retryable: false });
  }
  const appointmentId = String(payload.appointment_id ?? "");
  const requestedRevision = Number(payload.provider_location_revision);
  const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
  if (!uuidPattern.test(appointmentId) || !Number.isFinite(requestedRevision) || requestedRevision <= 0) {
    return json(400, { status: "UNAVAILABLE", code: "INVALID_REQUEST", retryable: false });
  }

  const authorization = request.headers.get("authorization") ?? "";
  const userClient = createClient(supabaseUrl, anonKey, {
    global: { headers: { Authorization: authorization } },
  });
  const { data: userData, error: userError } = await userClient.auth.getUser();
  if (userError || !userData.user) return json(401, { status: "UNAVAILABLE", code: "AUTH_REQUIRED", retryable: false });

  const existing = inFlight.get(appointmentId);
  if (existing) return (await existing).clone();

  const work = handle(userClient, supabaseUrl, serviceKey, routingBaseUrl, routingApiKey, appointmentId, requestedRevision);
  inFlight.set(appointmentId, work);
  try {
    return await work;
  } finally {
    inFlight.delete(appointmentId);
  }
});

async function handle(
  userClient: ReturnType<typeof createClient>,
  supabaseUrl: string,
  serviceKey: string,
  routingBaseUrl: string,
  routingApiKey: string | undefined,
  appointmentId: string,
  requestedRevision: number,
): Promise<Response> {
  const started = Date.now();
  const { data: context, error: contextError } = await userClient.rpc("get_pittec_route_context", {
    p_appointment_id: appointmentId,
  });
  if (contextError) {
    const message = contextError.message ?? "";
    if (message.includes("NOT_PARTICIPANT")) {
      return json(403, { status: "UNAVAILABLE", code: "NOT_PARTICIPANT", retryable: false });
    }
    if (message.includes("APPOINTMENT_NOT_TRACKABLE")) {
      return json(409, { status: "UNAVAILABLE", code: "APPOINTMENT_NOT_TRACKABLE", retryable: false });
    }
    console.log(JSON.stringify({ fn: "pittec-route", appointmentId, outcome: "CONTEXT_ERROR" }));
    return unavailable(503, "SERVICE_UNAVAILABLE", true);
  }
  if (context?.outcome === "LOCATION_NOT_READY") {
    return json(409, { status: "UNAVAILABLE", code: "LOCATION_NOT_READY", retryable: true });
  }
  if (context?.outcome !== "READY") {
    return json(409, { status: "UNAVAILABLE", code: "APPOINTMENT_NOT_TRACKABLE", retryable: false });
  }
  const providerRevision = Number(context.provider_location_revision);

  // A service-role client is required both to read/write the route snapshot (RLS restricts direct
  // client writes) and to call the upstream router without exposing its credential to the app.
  const admin = createClient(supabaseUrl, serviceKey, { auth: { persistSession: false } });
  const { data: current } = await admin.rpc("get_pittec_route_snapshot", { p_appointment_id: appointmentId });
  const currentSnapshot = current?.outcome === "FOUND" ? current : null;
  const freshEnough = currentSnapshot &&
    Date.now() - Date.parse(currentSnapshot.generated_at) < MIN_REFRESH_INTERVAL_MS;
  const coversRequested = currentSnapshot &&
    Number(currentSnapshot.provider_location_revision) >= providerRevision;
  if (currentSnapshot && (freshEnough || coversRequested)) {
    return json(200, {
      status: "ROUTE",
      appointment_id: appointmentId,
      provider_location_revision: currentSnapshot.provider_location_revision,
      distance_meters: currentSnapshot.distance_meters,
      geometry: currentSnapshot.geometry,
      generated_at: currentSnapshot.generated_at,
    });
  }

  const provider = context.provider as { latitude: number; longitude: number };
  const client = context.client as { latitude: number; longitude: number };
  const coordinates = `${provider.longitude},${provider.latitude};${client.longitude},${client.latitude}`;
  const endpoint = new URL(`${routingBaseUrl.replace(/\/$/, "")}/${coordinates}`);
  endpoint.searchParams.set("overview", "full");
  endpoint.searchParams.set("geometries", "geojson");
  if (routingApiKey) endpoint.searchParams.set("key", routingApiKey);

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), UPSTREAM_TIMEOUT_MS);
  let upstream: Response;
  try {
    upstream = await fetch(endpoint, { signal: controller.signal });
  } catch (error) {
    clearTimeout(timeout);
    const timedOut = error instanceof DOMException && error.name === "AbortError";
    console.log(JSON.stringify({
      fn: "pittec-route", appointmentId, outcome: timedOut ? "ROUTER_TIMEOUT" : "ROUTER_FAILURE",
      latencyMs: Date.now() - started,
    }));
    if (timedOut) return unavailable(504, "ROUTER_TIMEOUT", true);
    return unavailable(502, "ROUTER_FAILURE", true);
  }
  clearTimeout(timeout);
  if (!upstream.ok) {
    console.log(JSON.stringify({
      fn: "pittec-route", appointmentId, outcome: "ROUTER_FAILURE", httpStatus: upstream.status,
      latencyMs: Date.now() - started,
    }));
    return unavailable(502, "ROUTER_FAILURE", true);
  }
  const body = await upstream.json().catch(() => null);
  const route = body?.routes?.[0];
  const distanceMeters = Math.round(Number(route?.distance));
  const geometry = route?.geometry;
  const validGeometry = geometry?.type === "LineString" &&
    Array.isArray(geometry.coordinates) &&
    geometry.coordinates.length >= 2 &&
    geometry.coordinates.length <= MAX_UPSTREAM_POINTS &&
    geometry.coordinates.every((pair: unknown) =>
      Array.isArray(pair) && pair.length === 2 &&
      Number.isFinite(pair[0]) && Number.isFinite(pair[1]) &&
      pair[0] >= -180 && pair[0] <= 180 && pair[1] >= -90 && pair[1] <= 90
    );
  if (body?.code !== "Ok" || !validGeometry || !Number.isFinite(distanceMeters) ||
    distanceMeters < 0 || distanceMeters > MAX_DISTANCE_METERS
  ) {
    console.log(JSON.stringify({
      fn: "pittec-route", appointmentId, outcome: "ROUTER_INVALID_RESPONSE",
      latencyMs: Date.now() - started,
    }));
    return unavailable(502, "ROUTER_FAILURE", true);
  }

  const { data: upserted, error: upsertError } = await admin.rpc("upsert_pittec_route_snapshot", {
    p_appointment_id: appointmentId,
    p_provider_location_revision: providerRevision,
    p_geometry: { type: "LineString", coordinates: geometry.coordinates },
    p_distance_meters: distanceMeters,
    p_provider: "osrm",
  });
  if (upsertError || !upserted) {
    console.log(JSON.stringify({ fn: "pittec-route", appointmentId, outcome: "UPSERT_FAILURE" }));
    return unavailable(503, "SERVICE_UNAVAILABLE", true);
  }
  console.log(JSON.stringify({
    fn: "pittec-route", appointmentId, outcome: "ROUTE", revision: upserted.provider_location_revision,
    latencyMs: Date.now() - started,
  }));
  return json(200, {
    status: "ROUTE",
    appointment_id: appointmentId,
    provider_location_revision: upserted.provider_location_revision,
    distance_meters: upserted.distance_meters,
    geometry: upserted.geometry,
    generated_at: upserted.generated_at,
  });
}
