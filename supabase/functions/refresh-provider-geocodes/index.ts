import { createClient } from "npm:@supabase/supabase-js@2";

Deno.serve(async (request) => {
  const secret = Deno.env.get("GEOCODE_REFRESH_SECRET");
  if (!secret || request.headers.get("x-refresh-secret") !== secret) {
    return new Response("Unauthorized", { status: 401 });
  }
  const url = Deno.env.get("SUPABASE_URL");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const mapTilerKey = Deno.env.get("MAPTILER_API_KEY");
  if (!url || !serviceKey || !mapTilerKey) return new Response("Unavailable", { status: 503 });
  const admin = createClient(url, serviceKey, { auth: { persistSession: false } });
  const cutoff = new Date(Date.now() + 5 * 24 * 60 * 60_000).toISOString();
  const { data: addresses, error } = await admin.from("addresses")
    .select("id,street,number,neighborhood,zip_code,city,state,geocode_place_id,latitude,longitude")
    .lte("geocode_expires_at", cutoff).limit(200);
  if (error) return new Response("Unavailable", { status: 503 });
  let refreshed = 0;
  for (const address of addresses ?? []) {
    const parts = [address.street, address.number, address.neighborhood,
      address.city, address.state, address.zip_code, "Brasil"]
      .map((value) => String(value ?? "").trim());
    if (parts.slice(0, 6).some((value) => !value)) continue;
    const endpoint = new URL(
      `https://api.maptiler.com/geocoding/${encodeURIComponent(parts.join(", "))}.json`,
    );
    endpoint.searchParams.set("country", "br");
    endpoint.searchParams.set("language", "pt");
    endpoint.searchParams.set("limit", "1");
    endpoint.searchParams.set("autocomplete", "false");
    endpoint.searchParams.set("key", mapTilerKey);
    const response = await fetch(endpoint);
    if (!response.ok) continue;
    const body = await response.json();
    const match = body.features?.[0];
    if (!match) continue;
    const coordinates = match.geometry?.type === "Point" ? match.geometry.coordinates : match.center;
    const longitude = Number(coordinates?.[0]);
    const latitude = Number(coordinates?.[1]);
    if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) continue;
    const materialChange = Math.abs(latitude - Number(address.latitude)) > 0.001 ||
      Math.abs(longitude - Number(address.longitude)) > 0.001;
    await admin.from("addresses").update({
      latitude,
      longitude,
      geocode_place_id: String(match.id ?? ""),
      address_point: `POINT(${longitude} ${latitude})`,
      geocoded_at: new Date().toISOString(),
      geocode_expires_at: new Date(Date.now() + 30 * 24 * 60 * 60_000).toISOString(),
      location_verified_at: materialChange ? null : undefined,
    }).eq("id", address.id);
    refreshed += 1;
  }
  return Response.json({ refreshed });
});
