import { registrationAccess } from '../_shared/registration-access.ts';
import { createClient } from "npm:@supabase/supabase-js@2";

// Public-road directions between points the app already knows. This endpoint never looks up
// participant positions or identities; appointment location authorization stays in the tracking RPCs.
const json = (status: number, body: unknown) => new Response(JSON.stringify(body), {
  status, headers: { "content-type": "application/json; charset=utf-8" },
});
const fail = (status: number, code: string) => json(status, { status: "UNAVAILABLE", code });
const validPoint = (point: unknown): point is { latitude: number; longitude: number } => {
  if (!point || typeof point !== "object") return false;
  const p = point as Record<string, unknown>;
  return typeof p.latitude === "number" && Number.isFinite(p.latitude) && Math.abs(p.latitude) <= 90 &&
    typeof p.longitude === "number" && Number.isFinite(p.longitude) && Math.abs(p.longitude) <= 180;
};

Deno.serve(async (request) => {
  const registrationDenied = await registrationAccess(request, 'ANY');
  if (registrationDenied) return registrationDenied;

  if (request.method !== "POST") return fail(405, "METHOD_NOT_ALLOWED");
  const url = Deno.env.get("SUPABASE_URL"), key = Deno.env.get("SUPABASE_ANON_KEY");
  const base = Deno.env.get("PITTEC_ROUTING_BASE_URL");
  if (!url || !key || !base) return fail(503, "SERVICE_UNAVAILABLE");
  const client = createClient(url, key, {
    global: { headers: { Authorization: request.headers.get("authorization") ?? "" } },
    auth: { persistSession: false },
  });
  const { data, error } = await client.auth.getUser();
  if (error || !data.user) return fail(401, "AUTH_REQUIRED");
  const body = await request.json().catch(() => null);
  if (!validPoint(body?.origin) || !validPoint(body?.destination)) return fail(400, "INVALID_REQUEST");
  const { origin, destination } = body;
  const target = new URL(`${base.replace(/\/$/, "")}/${origin.longitude},${origin.latitude};${destination.longitude},${destination.latitude}`);
  target.searchParams.set("overview", "full");
  target.searchParams.set("geometries", "geojson");
  target.searchParams.set("steps", "false");
  target.searchParams.set("alternatives", "false");
  const apiKey = Deno.env.get("PITTEC_ROUTING_API_KEY");
  if (apiKey) target.searchParams.set("key", apiKey);
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 8_000);
  try {
    const response = await fetch(target, { signal: controller.signal });
    if (!response.ok) return fail(502, "ROUTER_FAILURE");
    const result = await response.json();
    const route = result?.routes?.[0];
    const geometry = route?.geometry;
    const distance = route?.distance, duration = route?.duration;
    if (result?.code !== "Ok" || typeof distance !== "number" || !Number.isFinite(distance) || distance < 0 || distance > 2_000_000 ||
        typeof duration !== "number" || !Number.isFinite(duration) || duration < 0 || duration > 604_800 ||
        geometry?.type !== "LineString" || !Array.isArray(geometry.coordinates) || geometry.coordinates.length < 2 || geometry.coordinates.length > 10_000 ||
        !geometry.coordinates.every((p: unknown) => Array.isArray(p) && p.length === 2 && validPoint({ longitude: p[0], latitude: p[1] }))) {
      return fail(502, "ROUTER_INVALID_RESPONSE");
    }
    return json(200, {
      status: "ROUTE", distance_meters: Math.round(distance),
      geometry: { type: "LineString", coordinates: geometry.coordinates, duration_seconds: Math.ceil(duration) },
    });
  } catch {
    return fail(controller.signal.aborted ? 504 : 502, controller.signal.aborted ? "ROUTER_TIMEOUT" : "ROUTER_FAILURE");
  } finally { clearTimeout(timer); }
});
