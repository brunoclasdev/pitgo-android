import { createClient } from 'npm:@supabase/supabase-js@2';

const json = (status: number, body: unknown) => new Response(JSON.stringify(body), {
  status, headers: { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' },
});
const coordinate = (value: unknown, maximum: number): value is number =>
  typeof value === 'number' && Number.isFinite(value) && Math.abs(value) <= maximum;

Deno.serve(async req => {
  if (req.method !== 'POST') return json(405, { code: 'METHOD_NOT_ALLOWED' });
  const url = Deno.env.get('SUPABASE_URL');
  const anon = Deno.env.get('SUPABASE_ANON_KEY');
  const key = Deno.env.get('MAPTILER_API_KEY');
  if (!url || !anon || !key) return json(503, { code: 'SEARCH_UNAVAILABLE' });
  try {
    // Bound input before forwarding a single search to the geocoder.
    const body = await req.text();
    if (body.length > 2048) return json(413, { code: 'INVALID_INPUT' });
    const input = JSON.parse(body);
    if (!input || typeof input !== 'object' || Array.isArray(input) ||
      Object.keys(input).some(name => !['query', 'latitude', 'longitude', 'reverse'].includes(name)) ||
      (input.reverse !== undefined && typeof input.reverse !== 'boolean') ||
      !coordinate(input.latitude, 90) || !coordinate(input.longitude, 180)) {
      return json(400, { code: 'INVALID_INPUT' });
    }
    const query = typeof input.query === 'string' ? input.query.trim() : '';
    if (!input.reverse && (query.length < 3 || query.length > 200 || /[;\u0000-\u001f]/.test(query))) {
      return json(400, { code: 'INVALID_INPUT' });
    }
    const caller = createClient(url, anon, {
      global: { headers: { Authorization: req.headers.get('authorization') ?? '' } },
      auth: { persistSession: false, autoRefreshToken: false },
    });
    const { data: session, error: authError } = await caller.auth.getUser();
    if (authError || !session.user) return json(401, { code: 'SESSION_REQUIRED' });
    const { data: account, error: accountError } = await caller.rpc('get_my_account_context');
    if (accountError) return json(503, { code: 'SEARCH_UNAVAILABLE' });
    if (account?.account_type !== 'CLIENT' || account?.client_state !== 'READY') {
      return json(403, { code: 'CLIENT_REQUIRED' });
    }
    const term = input.reverse ? `${input.longitude},${input.latitude}` : encodeURIComponent(query);
    const endpoint = new URL(`https://api.maptiler.com/geocoding/${term}.json`);
    endpoint.searchParams.set('key', key);
    endpoint.searchParams.set('country', 'br');
    endpoint.searchParams.set('language', 'pt');
    endpoint.searchParams.set('limit', input.reverse ? '1' : '5');
    endpoint.searchParams.set('autocomplete', input.reverse ? 'false' : 'true');
    if (!input.reverse) endpoint.searchParams.set('proximity', `${input.longitude},${input.latitude}`);
    const upstream = await fetch(endpoint, { signal: AbortSignal.timeout(8000) });
    if (!upstream.ok) return json(503, { code: 'SEARCH_UNAVAILABLE' });
    const result = await upstream.json();
    const seen = new Set<string>();
    const suggestions = [];
    for (const feature of Array.isArray(result.features) ? result.features : []) {
      const point = feature.center ?? (feature.geometry?.type === 'Point' ? feature.geometry.coordinates : null);
      const label = typeof feature.place_name === 'string' ? feature.place_name.trim().slice(0, 500) : '';
      if (!label || !coordinate(point?.[0], 180) || !coordinate(point?.[1], 90)) continue;
      const identity = `${label}:${point[0]}:${point[1]}`;
      if (seen.has(identity)) continue;
      seen.add(identity);
      suggestions.push({ id: String(feature.id ?? identity), label, latitude: point[1], longitude: point[0] });
      if (suggestions.length >= (input.reverse ? 1 : 5)) break;
    }
    return json(200, { suggestions });
  } catch (error) {
    if (error instanceof SyntaxError) return json(400, { code: 'INVALID_INPUT' });
    return json(503, { code: 'SEARCH_UNAVAILABLE' });
  }
});
