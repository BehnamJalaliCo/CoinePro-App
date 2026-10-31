// Webhooks (the phone's core/webhook and feature/alerts WebhookSheet): a fired alert is also posted
// to the reader's own HTTPS addresses — a bot, a sheet, a desk system. Free, no second factor.
//
//   · The address is judged while it is typed: HTTPS only, at a domain name, on port 80 or 443
//     (WebhookUrl.validate, the same five refusals in the same order).
//   · The body is the reader's own message (JSON when it parses as JSON, else plain text), or a
//     small JSON envelope {alertId, symbol, firedAt, price, timeframe} when they wrote none.
//   · With a signing key, `X-CoinePro-Signature: sha256=<hex>` carries an HMAC-SHA256 of the exact
//     body (WebCrypto). The key itself never travels: it is kept in this browser only, under a key
//     outside the `pc.` namespace, so neither the archive export nor the cloud workspace can carry
//     it, nothing renders it back, and no delivery record has a field it could fit in.
//   · Three seconds, no redirects, no cookies, no referrer; every attempt is a line in the log.
//
// A browser cannot post to a server that does not allow it (CORS) and read the answer. Such an
// endpoint is posted to with `mode: 'no-cors'` instead: the request leaves, but a browser may then
// send only plain text with no custom header (so no signature), and the answer is unreadable — the
// log says exactly that rather than claiming a delivery. Which mode an address needs is learned on
// its first send (the test button probes again), so a real firing is never posted twice.
import { create } from 'zustand';
import { uuid } from '../data/http';

// ── Addresses ───────────────────────────────────────────────────────────────────────────────────
export type UrlRefusal = 'empty' | 'malformed' | 'not_https' | 'no_host' | 'not_a_domain' | 'port';
export const REFUSALS: Record<UrlRefusal, { fa: string; en: string }> = {
  empty: { fa: 'نشانی وارد نشده است', en: 'No address entered' },
  malformed: { fa: 'نشانی خوانده نمی‌شود', en: 'The address cannot be read' },
  not_https: { fa: 'نشانی باید با https شروع شود', en: 'The address must start with https' },
  not_a_domain: { fa: 'نشانی باید نام دامنه باشد، نه نشانی عددی', en: 'The address must be a domain name, not a numeric address' },
  port: { fa: 'فقط پورت‌های ۸۰ و ۴۴۳ پذیرفته می‌شوند', en: 'Only ports 80 and 443 are accepted' },
  no_host: { fa: 'نشانی میزبان ندارد', en: 'The address has no host' },
};
export const ALLOWED_PORTS = [80, 443];
const IPV4 = /^\d{1,3}(\.\d{1,3}){3}$/;

function isDomainName(host: string): boolean {
  if (host.startsWith('[')) return false;
  if (IPV4.test(host)) return false;
  const labels = host.replace(/^\.+|\.+$/g, '').split('.');
  if (labels.length < 2) return false;
  return labels.every((l) => l.length > 0 && /^[\p{L}\p{N}-]+$/u.test(l));
}

/** Why `url` cannot be used, or null when it can (WebhookUrl.validate). */
export function validateUrl(url: string): UrlRefusal | null {
  const s = url.trim();
  if (!s) return 'empty';
  const scheme = /^([a-z][a-z0-9+.-]*):/i.exec(s)?.[1]?.toLowerCase();
  if (!scheme || /\s/.test(s)) return 'malformed';
  if (scheme !== 'https') {
    try { new URL(s); } catch { return 'malformed'; }
    return 'not_https';
  }
  // `https:///path`: a URL parser would read "path" as the host.
  if (/^https:\/\/([/?#]|$)/i.test(s)) return 'no_host';
  if (!/^https:\/\//i.test(s)) return 'no_host';
  let u: URL;
  try { u = new URL(s); } catch { return 'malformed'; }
  if (!u.hostname) return 'no_host';
  if (!isDomainName(u.hostname)) return 'not_a_domain';
  // An empty port is the scheme's default, 443.
  if (u.port !== '' && !ALLOWED_PORTS.includes(Number(u.port))) return 'port';
  return null;
}

// ── Bodies ──────────────────────────────────────────────────────────────────────────────────────
/**
 * Whether `body` is a well-formed JSON object or array (WebhookBody.looksLikeJson): a scanner, not
 * a parser, bounded at 32 levels; a bare number or string is text, as a receiver would read it.
 */
export function looksLikeJson(body: string): boolean {
  const text = body.trim();
  if (text.length < 2 || (text[0] !== '{' && text[0] !== '[')) return false;
  let at = 0, depth = 0;
  const done = () => at >= text.length;
  const ws = () => { while (at < text.length && /\s/.test(text[at]!)) at++; };
  const isHex = (c: string | undefined) => c != null && /[0-9a-fA-F]/.test(c);
  const str = (): boolean => {
    at++;
    while (at < text.length) {
      const c = text[at]!;
      if (c === '"') { at++; return true; }
      if (c === '\\') {
        at++;
        if (at >= text.length) return false;
        if (text[at] === 'u') {
          if (at + 4 >= text.length) return false;
          for (let k = 1; k <= 4; k++) if (!isHex(text[at + k])) return false;
          at += 4;
        } else if (!'"\\/bfnrt'.includes(text[at]!)) return false;
        at++;
      } else if (c.charCodeAt(0) < 0x20) return false;
      else at++;
    }
    return false;
  };
  const lit = (w: string) => { if (!text.startsWith(w, at)) return false; at += w.length; return true; };
  const num = (): boolean => {
    const start = at;
    if (!done() && text[at] === '-') at++;
    let d = 0; while (at < text.length && /\d/.test(text[at]!)) { at++; d++; }
    if (!d) return false;
    if (text[at] === '.') { at++; let f = 0; while (at < text.length && /\d/.test(text[at]!)) { at++; f++; } if (!f) return false; }
    if (text[at] === 'e' || text[at] === 'E') { at++; if (text[at] === '+' || text[at] === '-') at++; let e = 0; while (at < text.length && /\d/.test(text[at]!)) { at++; e++; } if (!e) return false; }
    return at > start;
  };
  const value = (): boolean => {
    ws();
    if (done()) return false;
    switch (text[at]) {
      case '{': return obj();
      case '[': return arr();
      case '"': return str();
      case 't': return lit('true');
      case 'f': return lit('false');
      case 'n': return lit('null');
      default: return num();
    }
  };
  const obj = (): boolean => {
    if (++depth > 32) return false;
    at++; ws();
    if (!done() && text[at] === '}') { at++; depth--; return true; }
    for (;;) {
      ws();
      if (done() || text[at] !== '"' || !str()) return false;
      ws();
      if (done() || text[at] !== ':') return false;
      at++;
      if (!value()) return false;
      ws();
      if (done()) return false;
      if (text[at] === ',') at++;
      else if (text[at] === '}') { at++; depth--; return true; } else return false;
    }
  };
  const arr = (): boolean => {
    if (++depth > 32) return false;
    at++; ws();
    if (!done() && text[at] === ']') { at++; depth--; return true; }
    for (;;) {
      if (!value()) return false;
      ws();
      if (done()) return false;
      if (text[at] === ',') at++;
      else if (text[at] === ']') { at++; depth--; return true; } else return false;
    }
  };
  if (!value()) return false;
  ws();
  return done();
}

export const contentTypeOf = (body: string) => (looksLikeJson(body) ? 'application/json' : 'text/plain');

/** What one fired alert hands its webhooks (WebhookEvent). `firedAt` is ms, inside the signed body. */
export interface WebhookEvent { alertId: string; symbol: string; firedAt: number; message?: string; price?: number | null; timeframe?: string | null }

/** The envelope sent when the reader wrote no message of their own. */
export function defaultBody(e: WebhookEvent): string {
  let s = `{"alertId":${JSON.stringify(e.alertId)},"symbol":${JSON.stringify(e.symbol)},"firedAt":${Math.round(e.firedAt)}`;
  if (e.price != null && Number.isFinite(e.price)) s += `,"price":${e.price}`;
  if (e.timeframe && e.timeframe.trim()) s += `,"timeframe":${JSON.stringify(e.timeframe)}`;
  return `${s}}`;
}
export const bodyOf = (e: WebhookEvent) => (e.message ?? '').trim() || defaultBody(e);

// ── Signature ───────────────────────────────────────────────────────────────────────────────────
export const SIGNATURE_HEADER = 'X-CoinePro-Signature';
export const TIMESTAMP_HEADER = 'X-CoinePro-Timestamp';

const hex = (mac: Uint8Array) => Array.from(mac, (b) => b.toString(16).padStart(2, '0')).join('');

/** `sha256=<hex>` of `body` under `secret`, or null with no secret (never a hash of an empty key). */
export async function signatureOf(body: string, secret: string | null | undefined): Promise<string | null> {
  if (!secret || !secret.trim()) return null;
  const enc = new TextEncoder();
  const subtle = typeof crypto !== 'undefined' ? crypto.subtle : undefined;
  // WebCrypto exists only in a secure context; a page served over plain HTTP signs in script.
  if (!subtle) return `sha256=${hex(hmacSha256(enc.encode(secret), enc.encode(body)))}`;
  const key = await subtle.importKey('raw', enc.encode(secret), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
  return `sha256=${hex(new Uint8Array(await subtle.sign('HMAC', key, enc.encode(body))))}`;
}

const K256 = new Uint32Array([
  0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5, 0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
  0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da, 0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
  0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85, 0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
  0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3, 0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
]);

/** SHA-256 (FIPS 180-4), for the signature where WebCrypto is unavailable. */
export function sha256(msg: Uint8Array): Uint8Array {
  const h = new Uint32Array([0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19]);
  const len = msg.length;
  const padded = new Uint8Array(((len + 9 + 63) >> 6) << 6);
  padded.set(msg);
  padded[len] = 0x80;
  const bits = len * 8;
  const dv = new DataView(padded.buffer);
  dv.setUint32(padded.length - 8, Math.floor(bits / 0x100000000));
  dv.setUint32(padded.length - 4, bits >>> 0);
  const w = new Uint32Array(64);
  const rotr = (x: number, n: number) => (x >>> n) | (x << (32 - n));
  for (let off = 0; off < padded.length; off += 64) {
    for (let i = 0; i < 16; i++) w[i] = dv.getUint32(off + i * 4);
    for (let i = 16; i < 64; i++) {
      const s0 = rotr(w[i - 15]!, 7) ^ rotr(w[i - 15]!, 18) ^ (w[i - 15]! >>> 3);
      const s1 = rotr(w[i - 2]!, 17) ^ rotr(w[i - 2]!, 19) ^ (w[i - 2]! >>> 10);
      w[i] = (w[i - 16]! + s0 + w[i - 7]! + s1) >>> 0;
    }
    let [a, b, c, d, e, f, g, k] = [h[0]!, h[1]!, h[2]!, h[3]!, h[4]!, h[5]!, h[6]!, h[7]!];
    for (let i = 0; i < 64; i++) {
      const t1 = (k + (rotr(e, 6) ^ rotr(e, 11) ^ rotr(e, 25)) + ((e & f) ^ (~e & g)) + K256[i]! + w[i]!) >>> 0;
      const t2 = ((rotr(a, 2) ^ rotr(a, 13) ^ rotr(a, 22)) + ((a & b) ^ (a & c) ^ (b & c))) >>> 0;
      k = g; g = f; f = e; e = (d + t1) >>> 0; d = c; c = b; b = a; a = (t1 + t2) >>> 0;
    }
    h[0] = (h[0]! + a) >>> 0; h[1] = (h[1]! + b) >>> 0; h[2] = (h[2]! + c) >>> 0; h[3] = (h[3]! + d) >>> 0;
    h[4] = (h[4]! + e) >>> 0; h[5] = (h[5]! + f) >>> 0; h[6] = (h[6]! + g) >>> 0; h[7] = (h[7]! + k) >>> 0;
  }
  const out = new Uint8Array(32);
  const ov = new DataView(out.buffer);
  for (let i = 0; i < 8; i++) ov.setUint32(i * 4, h[i]!);
  return out;
}

/** HMAC-SHA256 (RFC 2104). */
export function hmacSha256(key: Uint8Array, msg: Uint8Array): Uint8Array {
  let k = key.length > 64 ? sha256(key) : key;
  const block = new Uint8Array(64);
  block.set(k);
  k = block;
  const inner = new Uint8Array(64 + msg.length);
  const outer = new Uint8Array(64 + 32);
  for (let i = 0; i < 64; i++) { inner[i] = k[i]! ^ 0x36; outer[i] = k[i]! ^ 0x5c; }
  inner.set(msg, 64);
  outer.set(sha256(inner), 64);
  return sha256(outer);
}

// ── Targets, attempts ───────────────────────────────────────────────────────────────────────────
export interface WebhookTarget {
  id: string;
  /** The reader's own name for it, never derived from the URL. */
  name: string;
  url: string;
  enabled: boolean;
  createdAt: number;
  /** How a browser may post to it, learned on its first send: 'no-cors' where it allows no CORS. */
  mode?: 'cors' | 'no-cors';
}

export type Outcome = 'delivered' | 'rejected' | 'timeout' | 'unreachable' | 'blocked' | 'opaque';
export const OUTCOMES: Record<Outcome, { fa: string; en: string }> = {
  delivered: { fa: 'تحویل شد', en: 'Delivered' },
  rejected: { fa: 'گیرنده نپذیرفت', en: 'Receiver refused' },
  timeout: { fa: 'پاسخی نرسید', en: 'No answer' },
  unreachable: { fa: 'اتصال برقرار نشد', en: 'Could not connect' },
  blocked: { fa: 'نشانی پذیرفته نشد', en: 'Address refused' },
  opaque: { fa: 'فرستاده شد؛ پاسخ خوانده نشد', en: 'Sent; the answer could not be read' },
};
export type Reason = UrlRefusal | 'rejected' | 'timeout' | 'unreachable' | 'opaque';
export const REASONS: Record<Reason, { fa: string; en: string }> = {
  ...REFUSALS,
  rejected: { fa: 'گیرنده درخواست را نپذیرفت', en: 'The receiver did not accept the request' },
  timeout: { fa: 'گیرنده در سه ثانیه پاسخ نداد', en: 'The receiver did not answer within three seconds' },
  unreachable: { fa: 'اتصال به گیرنده برقرار نشد', en: 'Could not connect to the receiver' },
  opaque: { fa: 'این نشانی به مرورگر اجازه‌ی خواندن پاسخ را نمی‌دهد (CORS)؛ متن ساده و بدون امضا فرستاده شد', en: 'This address does not let a browser read its answer (CORS); it was sent as plain text, without the signature' },
};

/** One attempt to deliver one alert to one webhook: never the body, never the key. */
export interface WebhookAttempt {
  targetId: string;
  /** The target's name then; a later rename does not rewrite history. */
  targetName: string;
  alertId: string;
  at: number;
  outcome: Outcome;
  status?: number | null;
  latencyMs?: number;
  reason?: Reason;
}
export const delivered = (a: Pick<WebhookAttempt, 'outcome'>) => a.outcome === 'delivered';
/** A failure the reader must see. An unreadable answer is not known to have failed. */
export const failed = (a: Pick<WebhookAttempt, 'outcome'>) => a.outcome !== 'delivered' && a.outcome !== 'opaque';

/** «تحویل شد · 200 · 143ms»: the outcome, the status and the latency (Latin figures), the reason. */
export function attemptSummary(a: WebhookAttempt, fa: boolean): string {
  const label = OUTCOMES[a.outcome][fa ? 'fa' : 'en'];
  const parts = [label];
  if (a.status != null) parts.push(`⁦${a.status}⁩`);
  if ((a.latencyMs ?? 0) > 0) parts.push(`⁦${a.latencyMs}ms⁩`);
  const why = a.reason ? REASONS[a.reason][fa ? 'fa' : 'en'] : null;
  if (why && why !== label) parts.push(why);
  return parts.join(' · ');
}

export const TIMEOUT_MS = 3000;
export const TEST_ALERT_ID = 'webhook-test';
const TEST_TARGET_ID = 'webhook-test-target';
export const MAX_TARGETS = 50;
export const MAX_LOG = 500;

type Fetch = (url: string, init: RequestInit) => Promise<Response>;

/**
 * Posts `event` to `target` and says what happened; never throws. `probe` re-learns the mode (the
 * test button); otherwise a learned 'no-cors' is used directly so a firing leaves once.
 */
export async function deliver(target: WebhookTarget, event: WebhookEvent, secret: string | null, opts: { fetch?: Fetch; now?: () => number; probe?: boolean } = {}): Promise<{ attempt: WebhookAttempt; mode?: 'cors' | 'no-cors' }> {
  const now = opts.now ?? Date.now;
  const go: Fetch = opts.fetch ?? ((u, i) => fetch(u, i));
  const base = { targetId: target.id, targetName: target.name, alertId: event.alertId };
  const refusal = validateUrl(target.url);
  if (refusal) return { attempt: { ...base, at: now(), outcome: 'blocked', reason: refusal } };
  const body = bodyOf(event);
  // Signed before anything is sent, so a failure here is never mistaken for a refusal by CORS.
  const sig = await signatureOf(body, secret).catch(() => null);
  const started = now();
  const attempt = (outcome: Outcome, extra: Partial<WebhookAttempt> = {}): WebhookAttempt => ({ ...base, at: now(), outcome, latencyMs: Math.max(0, now() - started), ...extra });
  const send = async (mode: 'cors' | 'no-cors'): Promise<Response> => {
    const ctl = new AbortController();
    const timer = setTimeout(() => ctl.abort(), TIMEOUT_MS);
    try {
      const headers: Record<string, string> = {};
      if (mode === 'cors') {
        headers['Content-Type'] = contentTypeOf(body);
        headers[TIMESTAMP_HEADER] = String(started);
        if (sig) headers[SIGNATURE_HEADER] = sig;
      } else headers['Content-Type'] = 'text/plain';
      return await go(target.url.trim(), { method: 'POST', mode, body, headers, redirect: mode === 'cors' ? 'manual' : 'follow', credentials: 'omit', referrerPolicy: 'no-referrer', cache: 'no-store', signal: ctl.signal });
    } finally { clearTimeout(timer); }
  };
  const aborted = (e: unknown) => (e as { name?: string })?.name === 'AbortError';
  const first: 'cors' | 'no-cors' = opts.probe || !target.mode ? 'cors' : target.mode;
  try {
    const r = await send(first);
    if (first === 'no-cors' || r.type === 'opaque') return { attempt: attempt('opaque', { reason: 'opaque' }), mode: 'no-cors' };
    // A redirect is not followed: it would move a signed POST to a host nobody approved.
    if (r.type === 'opaqueredirect') return { attempt: attempt('rejected', { reason: 'rejected', status: r.status || null }), mode: 'cors' };
    return { attempt: r.ok ? attempt('delivered', { status: r.status }) : attempt('rejected', { status: r.status, reason: 'rejected' }), mode: 'cors' };
  } catch (e) {
    if (aborted(e)) return { attempt: attempt('timeout', { reason: 'timeout' }) };
    if (first === 'no-cors') return { attempt: attempt('unreachable', { reason: 'unreachable' }) };
    // A refusal by CORS and a dead host look the same to a page: ask once more without CORS.
    try {
      await send('no-cors');
      return { attempt: attempt('opaque', { reason: 'opaque' }), mode: 'no-cors' };
    } catch (e2) {
      return { attempt: attempt(aborted(e2) ? 'timeout' : 'unreachable', { reason: aborted(e2) ? 'timeout' : 'unreachable' }) };
    }
  }
}

// ── The draft in the editor ─────────────────────────────────────────────────────────────────────
export interface WebhookDraft { editingId: string | null; name: string; url: string; secret: string; secretTouched: boolean; enabled: boolean }
export const newDraft = (): WebhookDraft => ({ editingId: null, name: '', url: '', secret: '', secretTouched: false, enabled: true });
/** An existing target opens with the key field empty: untouched means «keep the stored key». */
export const draftOf = (t: WebhookTarget): WebhookDraft => ({ editingId: t.id, name: t.name, url: t.url, secret: '', secretTouched: false, enabled: t.enabled });
export const draftValid = (d: WebhookDraft) => d.name.trim().length > 0 && validateUrl(d.url) === null;

// ── The store ───────────────────────────────────────────────────────────────────────────────────
const TARGETS_KEY = 'pc.webhooks.v1';
const LOG_KEY = 'pc.webhooks.log.v1';
/** Outside `pc.`: the archive export and the cloud workspace read only `pc.` keys. */
const SECRETS_KEY = 'coinepro.webhook.keys.v1';

function readJson<T>(key: string, fallback: T): T { try { const v = JSON.parse(localStorage.getItem(key) ?? 'null') as unknown; return (v ?? fallback) as T; } catch { return fallback; } }
const readSecrets = (): Record<string, string> => { const v = readJson<Record<string, string>>(SECRETS_KEY, {}); return v && typeof v === 'object' && !Array.isArray(v) ? v : {}; };
function writeSecret(id: string, secret: string | null): void {
  const all = readSecrets();
  if (secret) all[id] = secret; else delete all[id];
  try { localStorage.setItem(SECRETS_KEY, JSON.stringify(all)); } catch { /* storage off */ }
}
const secretOf = (id: string): string | null => readSecrets()[id] ?? null;
/** Whether a target has a key, without ever handing the key out. */
export const hasSecret = (id: string): boolean => Boolean(secretOf(id));

interface WebhookState {
  targets: WebhookTarget[];
  log: WebhookAttempt[];
  /** Saves a draft; false when the URL or the name is refused. */
  save(d: WebhookDraft): string | null;
  remove(id: string): void;
  setEnabled(id: string, on: boolean): void;
  /**
   * Posts a recognisable test event to what the editor holds (saved or not) and records it: the
   * typed key when the field was touched, else the stored one.
   */
  test(d: WebhookDraft): Promise<WebhookAttempt | null>;
  /** Posts a fired alert to every enabled target (or to `only`), records and returns the attempts. */
  dispatch(event: WebhookEvent, only?: string[]): Promise<WebhookAttempt[]>;
  clearLog(): void;
}

/** What the test button learned about an address's CORS, for the save that follows it. */
const probed = new Map<string, 'cors' | 'no-cors'>();

export const useWebhooks = create<WebhookState>((set, get) => {
  const saveTargets = (targets: WebhookTarget[]) => { set({ targets }); try { localStorage.setItem(TARGETS_KEY, JSON.stringify(targets)); } catch { /* ignore */ } };
  const record = (attempts: WebhookAttempt[]) => {
    if (!attempts.length) return;
    const log = [...[...attempts].reverse(), ...get().log].slice(0, MAX_LOG);
    set({ log }); try { localStorage.setItem(LOG_KEY, JSON.stringify(log)); } catch { /* ignore */ }
  };
  const learn = (id: string, mode: 'cors' | 'no-cors' | undefined) => {
    if (!mode) return;
    const t = get().targets.find((x) => x.id === id);
    if (t && t.mode !== mode) saveTargets(get().targets.map((x) => (x.id === id ? { ...x, mode } : x)));
  };
  const post = async (t: WebhookTarget, e: WebhookEvent, probe = false) => {
    const r = await deliver(t, e, secretOf(t.id), { probe });
    learn(t.id, r.mode);
    return r.attempt;
  };
  const targets = readJson<WebhookTarget[]>(TARGETS_KEY, []);
  const log = readJson<WebhookAttempt[]>(LOG_KEY, []);
  return {
    targets: Array.isArray(targets) ? targets.slice(0, MAX_TARGETS) : [],
    log: Array.isArray(log) ? log.slice(0, MAX_LOG) : [],
    save: (d) => {
      if (!draftValid(d)) return null;
      const existing = d.editingId ? get().targets.find((t) => t.id === d.editingId) : undefined;
      const url = d.url.trim();
      const t: WebhookTarget = {
        id: existing?.id ?? uuid(), name: d.name.trim(), url, enabled: d.enabled, createdAt: existing?.createdAt ?? Date.now(),
        // A new address is a new question about CORS, unless the test button just answered it.
        mode: existing && existing.url === url ? existing.mode ?? probed.get(url) : probed.get(url),
      };
      if (d.secretTouched || !existing) writeSecret(t.id, d.secret.trim() || null);
      saveTargets([...get().targets.filter((x) => x.id !== t.id), t].slice(-MAX_TARGETS));
      return t.id;
    },
    remove: (id) => { writeSecret(id, null); saveTargets(get().targets.filter((t) => t.id !== id)); },
    setEnabled: (id, on) => saveTargets(get().targets.map((t) => (t.id === id ? { ...t, enabled: on } : t))),
    test: async (d) => {
      if (!draftValid(d)) return null;
      const existing = d.editingId ? get().targets.find((x) => x.id === d.editingId) : undefined;
      const t: WebhookTarget = { id: existing?.id ?? TEST_TARGET_ID, name: d.name.trim(), url: d.url.trim(), enabled: d.enabled, createdAt: existing?.createdAt ?? Date.now() };
      const secret = d.secretTouched ? d.secret.trim() || null : existing ? secretOf(existing.id) : d.secret.trim() || null;
      // A real symbol and price, so a receiver's parser sees the shape it will actually get; the
      // alert id is not any alert's, so no alert's history gains a firing that never happened.
      const r = await deliver(t, { alertId: TEST_ALERT_ID, symbol: 'BTCUSDT', firedAt: Date.now(), price: 68_500, timeframe: 'H1' }, secret, { probe: true });
      if (r.mode) probed.set(t.url, r.mode);
      if (existing && existing.url === t.url) learn(existing.id, r.mode);
      record([r.attempt]);
      return r.attempt;
    },
    dispatch: async (event, only) => {
      const list = get().targets.filter((t) => t.enabled && (!only || only.includes(t.id)));
      if (!list.length) return [];
      const attempts = await Promise.all(list.map((t) => post(t, event)));
      record(attempts);
      return attempts;
    },
    clearLog: () => { set({ log: [] }); try { localStorage.removeItem(LOG_KEY); } catch { /* ignore */ } },
  };
});

/** One alert's deliveries, newest first. */
export const deliveriesFor = (log: WebhookAttempt[], alertId: string) => log.filter((a) => a.alertId === alertId);

/** The phone's timeframe names (H1, D1) for an interval id, for a receiver's parser. */
export function timeframeName(interval: string | undefined): string | null {
  if (!interval) return null;
  const m: Record<string, string> = { '1': 'M1', '3': 'M3', '5': 'M5', '15': 'M15', '30': 'M30', '45': 'M45', '60': 'H1', '120': 'H2', '180': 'H3', '240': 'H4', '1D': 'D1', 'D': 'D1', '1W': 'W1', 'W': 'W1', '1M': 'MN1' };
  return m[interval] ?? interval;
}
