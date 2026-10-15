// Every request to the backends goes through the pro-chart.com relay (web/relay/relay.py):
//   /up/tradeyar/…, /up/coineprofx/…  the two platforms, byte for byte
//   /api/crypto/…, /api/fx/…, /api/news  cached public reads
// Same origin, so the relay's `pc_session` cookie travels with them and its `pch_…` token handles
// work. The headers are the phone's (NetworkFactory.kt), with the platform set to web.

const CLIENT_KEY = 'pc_client_id';
const INSTALL_KEY = 'install_id_web';

/** A v4 UUID; crypto.randomUUID exists only in secure contexts, so build one from random bytes. */
export function uuid(): string {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID();
  const b = crypto.getRandomValues(new Uint8Array(16));
  b[6] = (b[6]! & 0x0f) | 0x40;
  b[8] = (b[8]! & 0x3f) | 0x80;
  const h = Array.from(b, (x) => x.toString(16).padStart(2, '0')).join('');
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
}

function stableId(key: string): string {
  try {
    const have = localStorage.getItem(key);
    if (have) return have;
    const id = uuid();
    localStorage.setItem(key, id);
    return id;
  } catch {
    return uuid();
  }
}

let headersCache: Record<string, string> | null = null;
function baseHeaders(): Record<string, string> {
  if (!headersCache) {
    headersCache = {
      Accept: 'application/json',
      'X-Client-Id': stableId(CLIENT_KEY),
      'X-Install-Id': stableId(INSTALL_KEY),
      'X-App-Platform': 'web',
      'X-Client-Platform': 'web',
      'X-Client-Flavor': 'web',
      'X-App-Version': __APP_VERSION__,
    };
  }
  return headersCache;
}

export class HttpError extends Error {
  constructor(readonly status: number, readonly body: unknown, readonly retryAfterMs: number | null) {
    super(`HTTP ${status}`);
  }
  /** The backend's own words, only when they are Persian (ApiError.kt's rule). */
  get readerMessage(): string | null {
    const b = this.body as { detail?: unknown; message?: unknown } | null;
    const d = b?.detail;
    const text = typeof d === 'string' ? d : (d as { message?: unknown } | undefined)?.message ?? b?.message;
    return typeof text === 'string' && /[؀-ۿ]/.test(text) ? text : null;
  }
}

/** Reads how long a 429 asks us to wait: Retry-After, else `retry_after` in the body, else 60 s. */
function retryAfter(res: Response, body: unknown): number | null {
  if (res.status !== 429 && res.status !== 503) return null;
  const header = Number(res.headers.get('Retry-After'));
  if (Number.isFinite(header) && header > 0) return Math.min(header, 3600) * 1000;
  const d = (body as { detail?: { retry_after?: unknown } } | null)?.detail;
  const inBody = Number(d?.retry_after);
  if (Number.isFinite(inBody) && inBody > 0) return Math.min(inBody, 3600) * 1000;
  return 60_000;
}

// A 429 silences that route family until the wait is over: the backend asked, we listen.
const quietUntil = new Map<string, number>();
const family = (path: string) => path.split('/').slice(0, 4).join('/');

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  token?: string | null;
  signal?: AbortSignal;
  timeoutMs?: number;
  /** Extra headers (the community's X-Community-Key). */
  headers?: Record<string, string>;
}

export async function request<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const fam = family(path);
  const quiet = quietUntil.get(fam) ?? 0;
  if (quiet > Date.now()) throw new HttpError(429, null, quiet - Date.now());

  const headers: Record<string, string> = { ...baseHeaders(), ...(opts.headers ?? {}) };
  if (opts.token) headers.Authorization = `Bearer ${opts.token}`;
  const hasBody = opts.body !== undefined;
  const isForm = typeof FormData !== 'undefined' && opts.body instanceof FormData;
  if (hasBody && !isForm) headers['Content-Type'] = 'application/json; charset=UTF-8';
  const method = opts.method ?? (hasBody ? 'POST' : 'GET');

  const timeout = new AbortController();
  const timer = setTimeout(() => timeout.abort(), opts.timeoutMs ?? 30_000);
  const onAbort = () => timeout.abort();
  opts.signal?.addEventListener('abort', onAbort);
  let res: Response;
  try {
    res = await fetch(path, {
      method, headers, cache: 'no-store', credentials: 'same-origin',
      body: isForm ? (opts.body as FormData) : hasBody ? JSON.stringify(opts.body) : method === 'GET' ? undefined : '',
      signal: timeout.signal,
    });
  } catch (e) {
    throw new HttpError(-1, String(e), null);
  } finally {
    clearTimeout(timer);
    opts.signal?.removeEventListener('abort', onAbort);
  }
  const text = await res.text();
  let body: unknown = null;
  try { body = text ? JSON.parse(text) : null; } catch { body = text; }
  if (!res.ok) {
    const wait = retryAfter(res, body);
    if (res.status === 429 && wait) quietUntil.set(fam, Date.now() + wait);
    throw new HttpError(res.status, body, wait);
  }
  return body as T;
}

/** True while the reader can see the page; pollers stop when the tab is hidden. */
export const pageVisible = () => typeof document === 'undefined' || document.visibilityState !== 'hidden';
