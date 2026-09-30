import { createClient } from 'npm:@supabase/supabase-js@2';

const json = (status: number, body: unknown) => new Response(JSON.stringify(body), {
  status, headers: { 'content-type': 'application/json; charset=utf-8' },
});
const uuid = (value: unknown): value is string => typeof value === 'string' &&
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);

Deno.serve(async req => {
  if (req.method !== 'POST') return json(405, { code: 'METHOD_NOT_ALLOWED' });
  const url = Deno.env.get('SUPABASE_URL');
  const anon = Deno.env.get('SUPABASE_ANON_KEY');
  const service = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY');
  const router = Deno.env.get('PITTEC_ROUTING_BASE_URL');
  if (!url || !anon || !service || !router) return json(503, { code: 'SERVICE_UNAVAILABLE' });
  try {
    const input = await req.json();
    if (!input || typeof input !== 'object' || Array.isArray(input) ||
      Object.keys(input).some(key => !['draft_id', 'input_revision', 'provider_id'].includes(key)) ||
      !uuid(input.draft_id) || !uuid(input.provider_id) ||
      !Number.isSafeInteger(input.input_revision) || input.input_revision <= 0) {
      return json(400, { code: 'INVALID_INPUT' });
    }
    const caller = createClient(url, anon, {
      global: { headers: { Authorization: req.headers.get('authorization') ?? '' } },
      auth: { persistSession: false },
    });
    const { data: session, error: authError } = await caller.auth.getUser();
    if (authError || !session.user) return json(401, { code: 'SESSION_REQUIRED' });
    const { data: draft, error } = await caller.from('guincho_trip_drafts').select('*')
      .eq('id', input.draft_id).eq('client_id', session.user.id).maybeSingle();
    if (error) return json(503, { code: 'SERVICE_UNAVAILABLE' });
    if (!draft) return json(403, { code: 'DRAFT_UNAVAILABLE' });
    if (Number(draft.revision) !== input.input_revision) return json(409, { code: 'QUOTE_CONFLICT' });
    let distance = Number(draft.route_distance_meters);
    const cached = draft.route_distance_meters != null &&
      Number(draft.route_input_revision) === input.input_revision;
    if (!cached) {
      const coordinates = `${draft.pickup_longitude},${draft.pickup_latitude};${draft.destination_longitude},${draft.destination_latitude}`;
      const endpoint = new URL(`${router.replace(/\/$/, '')}/${coordinates}`);
      endpoint.searchParams.set('overview', 'false');
      const apiKey = Deno.env.get('PITTEC_ROUTING_API_KEY');
      if (apiKey) endpoint.searchParams.set('key', apiKey);
      const response = await fetch(endpoint, { signal: AbortSignal.timeout(8000) });
      if (!response.ok) return json(502, { code: 'ROUTE_UNAVAILABLE' });
      const body = await response.json();
      const route = body?.routes?.[0];
      if (body?.code !== 'Ok' || typeof route?.distance !== 'number') {
        return json(502, { code: 'ROUTE_UNAVAILABLE' });
      }
      distance = route.distance;
    }
    if (!Number.isFinite(distance) || distance < 0 || distance > 2000000) {
      return json(502, { code: 'ROUTE_UNAVAILABLE' });
    }
    const admin = createClient(url, service, { auth: { persistSession: false } });
    const { data: quote, error: storeError } = await admin.rpc('store_guincho_quote', {
      p_client_id: session.user.id, p_draft_id: input.draft_id,
      p_input_revision: input.input_revision, p_provider_id: input.provider_id,
      p_distance_meters: distance,
    });
    if (storeError) {
      const codes = ['QUOTE_CONFLICT', 'PROVIDER_UNAVAILABLE', 'PRICING_UNAVAILABLE', 'ROUTE_UNAVAILABLE'];
      const code = codes.includes(storeError.message) ? storeError.message : 'SERVICE_UNAVAILABLE';
      return json(code === 'SERVICE_UNAVAILABLE' ? 503 : 409, { code });
    }
    return json(200, quote);
  } catch (error) {
    if (error instanceof SyntaxError) return json(400, { code: 'INVALID_INPUT' });
    if (error instanceof DOMException && ['TimeoutError', 'AbortError'].includes(error.name)) {
      return json(504, { code: 'ROUTE_UNAVAILABLE' });
    }
    return json(503, { code: 'SERVICE_UNAVAILABLE' });
  }
});
