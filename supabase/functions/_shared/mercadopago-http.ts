export type JsonObject = Record<string, unknown>;

export function object(value: unknown): JsonObject {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
    ? value as JsonObject : {};
}

export interface MpResponse {
  status: number;
  body: JsonObject;
  retryAfter: number;
  code: string;
}

// No automatic mutation retries: a transport failure is not proof that MP did not charge.
export async function mpRequest(path: string, method = 'GET', body?: unknown, key?: string): Promise<MpResponse> {
  const token = Deno.env.get('MP_ACCESS_TOKEN');
  if (!token) throw new Error('SERVICE_UNAVAILABLE');
  const headers: Record<string, string> = { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
  if (key) headers['X-Idempotency-Key'] = key;
  try {
    const response = await fetch(`https://api.mercadopago.com${path}`, {
      method, headers, body: body === undefined ? undefined : JSON.stringify(body),
      signal: AbortSignal.timeout(7000),
    });
    const rawRetry = response.headers.get('retry-after') ?? '';
    const seconds = Number(rawRetry);
    const retryAfter = Math.max(0, Number.isFinite(seconds) ? seconds : (Date.parse(rawRetry) - Date.now()) / 1000);
    const parsed = object(await response.json().catch(() => null));
    const error = Array.isArray(parsed.errors) ? object(parsed.errors[0]) : parsed;
    // Allow code identifiers, never provider messages (which can contain payer input).
    const candidate = String(error.code ?? error.error ?? 'PROVIDER_ERROR');
    return { status: response.status, body: parsed, retryAfter: Number.isFinite(retryAfter) ? retryAfter : 60,
      code: /^[a-zA-Z0-9_]{1,80}$/.test(candidate) ? candidate : 'PROVIDER_ERROR' };
  } catch {
    return { status: 0, body: {}, retryAfter: 60, code: 'PROVIDER_UNCERTAIN' };
  }
}

export function cents(value: unknown): number | null {
  const str = String(value ?? '');
  if (!/^\d{1,8}(\.\d{1,2})?$/.test(str)) return null;
  const [units, decimals = ''] = str.split('.');
  return Number(units) * 100 + Number(decimals.padEnd(2, '0'));
}

export function decimal(value: number): string {
  if (!Number.isSafeInteger(value) || value < 0) throw new Error('INVALID_AMOUNT');
  return `${Math.floor(value / 100)}.${String(value % 100).padStart(2, '0')}`;
}

export async function secureEqual(a: string, b: string): Promise<boolean> {
  const encoder = new TextEncoder();
  const [x, y] = await Promise.all([a,b].map(v => crypto.subtle.digest('SHA-256', encoder.encode(v))));
  const left = new Uint8Array(x), right = new Uint8Array(y);
  let diff = 0;
  for (let i = 0; i < left.length; i++) diff |= left[i] ^ right[i];
  return diff === 0;
}
