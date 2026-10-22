// Exercise the real HTTP handler with authenticated requests and a mocked router, without
// opening a listener or contacting Supabase/OSRM.
let handler: (request: Request) => Promise<Response>;
const serve = Deno.serve;
Deno.serve = ((callback: unknown) => { handler = callback as typeof handler; return {}; }) as typeof Deno.serve;
try { await import("./index.ts"); } finally { Deno.serve = serve; }

const geometry = { type: "LineString", coordinates: [[0, 0], [0.01, 0], [0.01, 0.01]] };
const route = { code: "Ok", routes: [{ distance: 2000, duration: 600.1, geometry }] };
const payload = { origin: { latitude: 0, longitude: 0 }, destination: { latitude: 0.01, longitude: 0.01 } };
const assert = (condition: boolean, message: string) => { if (!condition) throw new Error(message); };

async function requestWithMocks(body: unknown, upstream: unknown, authenticated = true, method = "POST") {
  const names = ["SUPABASE_URL", "SUPABASE_ANON_KEY", "PITTEC_ROUTING_BASE_URL", "PITTEC_ROUTING_API_KEY"];
  const old = names.map(name => Deno.env.get(name));
  const fetch = globalThis.fetch;
  const calls: string[] = [];
  try {
    Deno.env.set(names[0], "https://project.example");
    Deno.env.set(names[1], "test-anon-key");
    Deno.env.set(names[2], "https://router.example/route/v1/driving");
    Deno.env.delete(names[3]);
    globalThis.fetch = async (input) => {
      const url = input instanceof Request ? input.url : String(input);
      calls.push(url);
      if (url.startsWith("https://project.example/auth/v1/user")) {
        return new Response(JSON.stringify(authenticated ? { id: "test-user" } : { message: "Unauthorized" }), {
          status: authenticated ? 200 : 401, headers: { "content-type": "application/json" },
        });
      }
      assert(url.startsWith("https://router.example/route/v1/driving/"), "Unexpected outgoing request");
      return new Response(JSON.stringify(upstream), { headers: { "content-type": "application/json" } });
    };
    const response = await handler(new Request("https://edge.example/journey-route", {
      method, headers: { authorization: "Bearer test-token", "content-type": "application/json" },
      ...(method === "POST" ? { body: JSON.stringify(body) } : {}),
    }));
    return { status: response.status, body: await response.json(), calls };
  } finally {
    globalThis.fetch = fetch;
    names.forEach((name, index) => old[index] === undefined ? Deno.env.delete(name) : Deno.env.set(name, old[index]!));
  }
}

Deno.test("road distance, duration and geometry are returned together", async () => {
  const result = await requestWithMocks(payload, route);
  assert(result.status === 200 && result.body.distance_meters === 2000, "Expected road distance");
  assert(result.body.geometry.duration_seconds === 601, "Expected rounded upstream travel duration");
  assert(JSON.stringify(result.body.geometry.coordinates) === JSON.stringify(geometry.coordinates), "Expected complete road geometry");
  assert(result.calls[1].includes("0,0;0.01,0.01") && result.calls[1].includes("overview=full"), "Expected requested origin/destination and full geometry");
});
Deno.test("missing router duration cannot create an ETA", async () => {
  const result = await requestWithMocks(payload, { code: "Ok", routes: [{ distance: 2000, geometry }] });
  assert(result.status === 502 && result.body.code === "ROUTER_INVALID_RESPONSE", "Missing duration must fail");
});
Deno.test("invalid road geometry is rejected", async () => {
  const result = await requestWithMocks(payload, { code: "Ok", routes: [{ distance: 2000, duration: 600, geometry: { type: "LineString", coordinates: [[0, 0], [181, 0]] } }] });
  assert(result.status === 502, "Out-of-range geometry must fail");
});
Deno.test("invalid coordinates do not reach the router", async () => {
  const result = await requestWithMocks({ ...payload, origin: { latitude: 91, longitude: 0 } }, route);
  assert(result.status === 400 && result.calls.length === 1, "Invalid location must not route");
});
Deno.test("unauthenticated caller cannot reach the router", async () => {
  const result = await requestWithMocks(payload, route, false);
  assert(result.status === 401 && result.calls.length === 1, "Authentication must precede routing");
});
Deno.test("unsupported method does not make outgoing requests", async () => {
  const result = await requestWithMocks(null, route, true, "GET");
  assert(result.status === 405 && result.calls.length === 0, "Unsupported method must fail immediately");
});
