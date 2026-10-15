// The web user panel's server half (web/relay/relay.py → web/admin/admin.py, «کاربران وب»).
//
// Every call goes to /up/panel/<path> on this origin with one of the reader's own `pch_…` handles:
// the relay checks the handle against this browser's HttpOnly cookie, asks the reader's backend who
// they are, and passes the call on with that verified identity. The page never names its account.
// The sessions list, revoke and this browser's sign-out are answered by the relay itself.
//
// Also here, as plain functions the tests can reach: the cloud workspace (which `pc.*` keys belong
// to which part, the blob, its hash, what «sync now» should do) and the small event queue that
// carries the page's own errors and feature use, batched, signed-in only.
import { accessFor } from './auth';
import { HttpError, request, type RequestOptions } from './http';
import { useSession } from './session';
import type { Backend } from './symbols';

// ── Calls ────────────────────────────────────────────────────────────────────────────────────────

const ROOT = '/up/panel/';

/** The backend whose handle opens the panel: TradeYar first, as the relay ranks accounts. */
export function panelBackend(): Backend | null {
  const s = useSession.getState().sessions;
  return s.tradeyar ? 'tradeyar' : s.coineprofx ? 'coineprofx' : null;
}

export async function panel<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const backend = panelBackend();
  if (!backend) throw new Error('signed-out');
  const token = await accessFor(backend);
  if (!token) throw new HttpError(401, null, null);
  return request<T>(ROOT + path, { ...opts, token });
}

/** A binary answer (a ticket's image, the export) as a Blob; an <img> cannot send the handle. */
export async function panelBlob(path: string): Promise<Blob> {
  const backend = panelBackend();
  const token = backend ? await accessFor(backend) : null;
  if (!token) throw new HttpError(401, null, null);
  const res = await fetch(ROOT + path, { headers: { Authorization: `Bearer ${token}` }, credentials: 'same-origin', cache: 'no-store' });
  if (!res.ok) throw new HttpError(res.status, null, null);
  return res.blob();
}

/** The panel server's own refusal word (`too_large`, `newer_version`…), when there is one. */
export function panelError(e: unknown): string | null {
  if (!(e instanceof HttpError)) return null;
  const b = e.body as { error?: unknown } | null;
  return typeof b?.error === 'string' ? b.error : null;
}

// ── Shapes ───────────────────────────────────────────────────────────────────────────────────────

export interface PanelAlias { alias: string; backend: Backend; email: string | null; name: string | null; vip: number; paid: number; plan: string | null; last_seen: number | null }
export interface WorkspacePart { kind: Kind; version: number; size: number; updated_at: number | null; device: string | null; versions: number; limit: number }
export interface PanelMe {
  account: string; aliases: PanelAlias[]; first_seen: number; last_seen: number; sign_ins: number;
  vip: boolean; paid: boolean; plan: string | null; premium_until: string | null;
  profile: { display_name?: string; tagline?: string; avatar?: string }; prefs: PanelPrefs;
  unread: number; open_tickets: number; answered_tickets: number; trades_30d: number; workspace: WorkspacePart[];
  previous_sign_in: { at: number; device: string | null; browser: string | null; os: string | null; country: string | null } | null;
  session: { sid: string; device: string; browser: string; os: string; country: string | null };
}
export interface PanelPrefs { autosync?: boolean; sync_kinds?: Kind[]; digest?: boolean; lang?: string; theme?: string; sounds?: boolean }
export interface WebSession { sid: string; created: number; last_seen: number; browser: string; os: string; device: string; country: string; accounts: string[]; current: boolean }
export interface PanelEvent { id: number; at: number; kind: string; name: string; source: string; backend: string | null; device: string | null; browser: string | null; os: string | null; country: string | null; detail: Record<string, unknown> | null }
export interface SecurityLog { items: PanelEvent[]; devices: { browser: string | null; os: string | null; device: string | null; last_seen: number; sign_ins: number; country: string | null }[]; failed_30d: number }
export interface AuditTrade { id: number; at: number; backend: string | null; method: string; path: string; action: string; symbol: string | null; side: string | null; quantity: number | null; type: string | null; price: number | null; venue: string | null; status: number; ok: boolean; order_id: string | null; state: string | null; detail: string | null; device?: string | null }
export interface InboxItem { id: number; kind: string; title: string; body: string; link: string; ticket_id: number | null; created_at: number; read: boolean; broadcast: boolean }
export type TicketStatus = 'open' | 'answered' | 'closed';
export interface Ticket { id: number; subject: string; category: string; status: TicketStatus; created_at: number; updated_at: number; unread_user: boolean }
export interface TicketAttachment { id: number; name: string; type: string; size: number }
export interface TicketMessage { id: number; at: number; author: 'user' | 'owner'; body: string; attachments: TicketAttachment[] }
export interface NewAttachment { name: string; data: string }

export const TICKET_CATEGORIES = ['account', 'billing', 'trading', 'bug', 'idea', 'other'] as const;
export const ATTACHMENT_LIMIT = 300 * 1024;
export const ATTACHMENTS_PER_MESSAGE = 3;

export const getMe = () => panel<PanelMe>('me');
export const getSessions = () => panel<{ sessions: WebSession[] }>('sessions');
export const revokeSessions = (body: { sid: string } | { others: true } | { all: true }) => panel<{ revoked: number }>('sessions/revoke', { body });
export const getSecurity = () => panel<SecurityLog>('security');
export const getActivity = (kind = '', before = 0) => panel<{ items: PanelEvent[] }>(`activity?limit=100${kind ? `&kind=${kind}` : ''}${before ? `&before=${before}` : ''}`);
export const getTrades = () => panel<{ items: AuditTrade[]; total: number }>('trades?limit=200');
export const getInbox = () => panel<{ items: InboxItem[]; unread: number }>('inbox');
export const readMessage = (id: number) => panel(`inbox/${id}/read`, { body: {} });
export const readAll = () => panel<{ marked: number }>('inbox/read-all', { body: {} });
export const dismissMessage = (id: number) => panel(`inbox/${id}`, { method: 'DELETE' });
export const getTickets = () => panel<{ items: Ticket[] }>('tickets');
export const getTicket = (id: number) => panel<{ ticket: Ticket; messages: TicketMessage[] }>(`tickets/${id}`);
export const openTicket = (body: { subject: string; category: string; body: string; attachments: NewAttachment[] }) => panel<{ id: number }>('tickets', { body });
export const replyTicket = (id: number, body: { body: string; attachments: NewAttachment[] }) => panel(`tickets/${id}/messages`, { body });
export const closeTicket = (id: number) => panel(`tickets/${id}/close`, { body: {} });
export const saveProfile = (profile: PanelMe['profile']) => panel<{ profile: PanelMe['profile'] }>('profile', { method: 'PUT', body: profile });
export const savePrefs = (prefs: PanelPrefs) => panel<{ prefs: PanelPrefs }>('prefs', { method: 'PUT', body: prefs });
export const eraseData = () => panel<{ erased: Record<string, number> }>('data', { method: 'DELETE', body: { confirm: true } });
export const exportData = () => panelBlob('export');

/** The relay forgets this browser's session; called when the reader signs out. Never throws. */
export function relaySignOut(): void {
  try { void fetch(`${ROOT}signout`, { method: 'POST', credentials: 'same-origin', keepalive: true }).catch(() => undefined); } catch { /* offline */ }
}

// ── The cloud workspace ──────────────────────────────────────────────────────────────────────────

export const KINDS = ['layouts', 'settings', 'drawings', 'templates', 'watchlist'] as const;
export type Kind = (typeof KINDS)[number];

const KIND_KEYS: Record<Kind, (key: string) => boolean> = {
  layouts: (k) => k === 'pc.layouts.v1' || k === 'pc.grid.v1',
  settings: (k) => ['pc.chartsettings.v1', 'pc.prefs.v1', 'pc.panels.v1', 'pc.notify.v1'].includes(k),
  drawings: (k) => k.startsWith('pc.drawings.'),
  templates: (k) => ['pc.studies.v1', 'pc.studies.fav.v1', 'pc.namascript.v1', 'pc.scripts.v1'].includes(k),
  watchlist: (k) => ['pc.watchlists.v2', 'pc.watchlist.v1', 'pc.screens.v1', 'pc.news.saved.v1'].includes(k),
};

/** Never in the cloud, whatever a blob claims: the sign-in handles, the community key, this file's own memo. */
export const NEVER_SYNCED = ['pc.sessions.v1', 'pc.community.key.v1', 'pc.fx.guest.v1', 'pc.platform.v1', 'pc.cloud.v1', 'pc.telemetry.v1'];

export function kindOf(key: string): Kind | null {
  if (!key.startsWith('pc.') || NEVER_SYNCED.includes(key)) return null;
  return KINDS.find((k) => KIND_KEYS[k](key)) ?? null;
}

export interface WorkspaceBlob { keys: Record<string, string> }
type KeyStore = Pick<Storage, 'length' | 'key' | 'getItem' | 'setItem' | 'removeItem'>;

function keysOf(store: KeyStore): string[] {
  const out: string[] = [];
  for (let i = 0; i < store.length; i++) { const k = store.key(i); if (k) out.push(k); }
  return out;
}

/** One part of the workspace as it is in this browser now. */
export function collect(store: KeyStore, kind: Kind): WorkspaceBlob {
  const keys: Record<string, string> = {};
  for (const k of keysOf(store).sort()) if (kindOf(k) === kind) keys[k] = store.getItem(k) ?? '';
  return { keys };
}

/** Replaces one part of this browser's workspace with a blob: its keys only, every one checked. */
export function apply(store: KeyStore, kind: Kind, blob: unknown): number {
  const keys = (blob as WorkspaceBlob | null)?.keys;
  if (!keys || typeof keys !== 'object') return 0;
  for (const k of keysOf(store)) if (kindOf(k) === kind && !(k in keys)) store.removeItem(k);
  let n = 0;
  for (const [k, v] of Object.entries(keys)) {
    if (kindOf(k) !== kind || typeof v !== 'string') continue;
    try { store.setItem(k, v); n++; } catch { /* full */ }
  }
  return n;
}

/** A short, stable fingerprint of a blob (FNV-1a), to tell whether this browser changed it. */
export function hashBlob(blob: WorkspaceBlob): string {
  const text = JSON.stringify(Object.keys(blob.keys).sort().map((k) => [k, blob.keys[k]]));
  let h = 0x811c9dc5;
  for (let i = 0; i < text.length; i++) { h ^= text.charCodeAt(i); h = Math.imul(h, 0x01000193); }
  return (h >>> 0).toString(16).padStart(8, '0') + text.length.toString(16);
}

export const isEmpty = (blob: WorkspaceBlob) => Object.keys(blob.keys).length === 0;
export const blobBytes = (blob: WorkspaceBlob) => new TextEncoder().encode(JSON.stringify(blob)).length;

/** What this browser last agreed with the cloud on, per part (`pc.cloud.v1`). */
export interface CloudMemo { auto?: boolean; kinds: Partial<Record<Kind, { version: number; hash: string; at: number }>> }
const MEMO_KEY = 'pc.cloud.v1';
export function readMemo(): CloudMemo {
  try { const v = JSON.parse(localStorage.getItem(MEMO_KEY) ?? 'null') as CloudMemo | null; if (v && typeof v.kinds === 'object') return v; } catch { /* fall through */ }
  return { kinds: {} };
}
export function writeMemo(memo: CloudMemo): void { try { localStorage.setItem(MEMO_KEY, JSON.stringify(memo)); } catch { /* full */ } }

export type SyncAction = 'push' | 'pull' | 'conflict' | 'none';

/**
 * What «sync now» does with one part: send this browser's changes, take another device's, or ask
 * when both changed since the last agreement. An empty browser never overwrites a filled cloud.
 */
export function plan(local: WorkspaceBlob, remoteVersion: number, memo: CloudMemo['kinds'][Kind] | undefined): SyncAction {
  const known = memo?.version ?? 0;
  const localChanged = memo ? hashBlob(local) !== memo.hash : !isEmpty(local);
  const remoteChanged = remoteVersion > known;
  if (!remoteChanged) return localChanged ? 'push' : 'none';
  if (!localChanged || isEmpty(local)) return 'pull';
  return 'conflict';
}

export interface SyncResult { kind: Kind; action: SyncAction | 'too_large' | 'failed'; version?: number }

/**
 * Brings each part in step with the cloud. `resolve` settles the conflicts a previous run reported:
 * «this browser» sends it over the cloud's, «cloud» takes the cloud's. `pushOnly` (the automatic
 * run) never changes this browser. Returns what happened per part; `pulled` means reload.
 */
export async function syncWorkspace(opts: { kinds?: Kind[]; resolve?: Partial<Record<Kind, 'local' | 'cloud'>>; pushOnly?: boolean; store?: KeyStore } = {}): Promise<{ results: SyncResult[]; pulled: boolean }> {
  const store = opts.store ?? localStorage;
  const memo = readMemo();
  const remote = await panel<{ kinds: WorkspacePart[] }>('workspace');
  const results: SyncResult[] = [];
  let pulled = false;
  for (const part of remote.kinds) {
    const kind = part.kind;
    if (opts.kinds && !opts.kinds.includes(kind)) continue;
    const local = collect(store, kind);
    let action: SyncAction = plan(local, part.version, memo.kinds[kind]);
    const choice = opts.resolve?.[kind];
    if (action === 'conflict' && choice) action = choice === 'local' ? 'push' : 'pull';
    if (opts.pushOnly && action !== 'push') { results.push({ kind, action: action === 'pull' ? 'none' : action }); continue; }
    try {
      if (action === 'push') {
        if (blobBytes(local) > part.limit) { results.push({ kind, action: 'too_large' }); continue; }
        const r = await panel<{ version: number }>(`workspace/${kind}`, { method: 'PUT', body: { data: local, base_version: part.version, force: choice === 'local' } });
        memo.kinds[kind] = { version: r.version, hash: hashBlob(local), at: Date.now() };
        results.push({ kind, action, version: r.version });
      } else if (action === 'pull') {
        const r = await panel<{ version: number; data: WorkspaceBlob | null }>(`workspace/${kind}`);
        if (r.data) { apply(store, kind, r.data); pulled = true; }
        memo.kinds[kind] = { version: r.version, hash: hashBlob(collect(store, kind)), at: Date.now() };
        results.push({ kind, action, version: r.version });
      } else {
        results.push({ kind, action });
      }
    } catch (e) {
      results.push({ kind, action: panelError(e) === 'newer_version' ? 'conflict' : 'failed' });
    }
  }
  writeMemo(memo);
  return { results, pulled };
}

/** Takes one saved version of one part into this browser (after the server made it current). */
export async function restoreVersion(kind: Kind, version: number, store: KeyStore = localStorage): Promise<number> {
  const r = await panel<{ version: number }>(`workspace/${kind}/restore`, { body: { version } });
  const now = await panel<{ version: number; data: WorkspaceBlob | null }>(`workspace/${kind}`);
  if (now.data) apply(store, kind, now.data);
  const memo = readMemo();
  memo.kinds[kind] = { version: now.version, hash: hashBlob(collect(store, kind)), at: Date.now() };
  writeMemo(memo);
  return r.version;
}

/** A file with every part of this browser's workspace, and the way back in. */
export function exportWorkspace(store: KeyStore = localStorage): string {
  return JSON.stringify({ format: 'pro-chart-workspace', version: 1, at: new Date().toISOString(), parts: Object.fromEntries(KINDS.map((k) => [k, collect(store, k)])) });
}
export function importWorkspace(text: string, store: KeyStore = localStorage): number {
  const data = JSON.parse(text) as { format?: string; parts?: Record<string, unknown> };
  if (data?.format !== 'pro-chart-workspace' || !data.parts) throw new Error('not-a-workspace');
  return KINDS.reduce((n, k) => n + (data.parts![k] ? apply(store, k, data.parts![k]) : 0), 0);
}

// ── The page's own events ────────────────────────────────────────────────────────────────────────

export interface ClientEvent { kind: 'error' | 'feature' | 'page'; name: string; detail?: Record<string, string | number | boolean | null>; at: number }

/**
 * Holds the page's events until they go, in batches of at most 50. Small on purpose: a name of 80
 * characters, a detail of a few fields, the same error at most five times a session, and never more
 * than 200 waiting — a page in a loop of errors must not become a flood.
 */
export class EventQueue {
  private items: ClientEvent[] = [];
  private seen = new Map<string, number>();
  constructor(private readonly max = 200, private readonly repeat = 5) {}

  push(kind: ClientEvent['kind'], name: string, detail?: ClientEvent['detail']): boolean {
    const clean = name.replace(/\s+/g, ' ').trim().slice(0, 80);
    if (!clean) return false;
    const key = `${kind}|${clean}`;
    const n = (this.seen.get(key) ?? 0) + 1;
    this.seen.set(key, n);
    if (n > this.repeat || this.items.length >= this.max) return false;
    this.items.push({ kind, name: clean, detail: detail ? trimDetail(detail) : undefined, at: Math.floor(Date.now() / 1000) });
    return true;
  }

  get size(): number { return this.items.length; }
  take(limit = 50): ClientEvent[] { return this.items.splice(0, limit); }
  putBack(batch: ClientEvent[]): void { this.items = [...batch, ...this.items].slice(0, this.max); }
  clear(): void { this.items = []; }
}

function trimDetail(d: NonNullable<ClientEvent['detail']>): NonNullable<ClientEvent['detail']> {
  const out: NonNullable<ClientEvent['detail']> = {};
  for (const [k, v] of Object.entries(d).slice(0, 10)) out[k.slice(0, 24)] = typeof v === 'string' ? scrub(v).slice(0, k === 'stack' ? 1500 : 300) : v;
  return out;
}

/** No handle, token, email or query string leaves the page inside an error's text. */
export function scrub(text: string): string {
  return text
    .replace(/pch_[A-Za-z0-9_-]+/g, 'pch_…')
    .replace(/Bearer\s+[A-Za-z0-9._~+/=-]+/gi, 'Bearer …')
    .replace(/[\w.+-]+@[\w-]+\.[\w.-]+/g, '…@…')
    .replace(/\?[^\s)'"]*/g, '');
}

/** An error event's name and detail from whatever was thrown. */
export function describeError(error: unknown, source?: string): { name: string; detail: NonNullable<ClientEvent['detail']> } {
  const e = error as { name?: unknown; message?: unknown; stack?: unknown } | null;
  const type = typeof e?.name === 'string' ? e.name : 'Error';
  const message = typeof e?.message === 'string' ? e.message : String(error ?? 'unknown');
  return {
    name: scrub(`${type}: ${message}`),
    detail: {
      message: message.slice(0, 300), stack: typeof e?.stack === 'string' ? e.stack : '', source: source ?? '',
      path: typeof location === 'undefined' ? '' : location.pathname, version: __APP_VERSION__,
    },
  };
}

export const events = new EventQueue();

/** The reader's own switch for error reports and feature use (Data and privacy); on unless turned off. */
const TELEMETRY_KEY = 'pc.telemetry.v1';
export function telemetryOn(): boolean { try { return localStorage.getItem(TELEMETRY_KEY) !== 'off'; } catch { return true; } }
export function setTelemetry(on: boolean): void {
  try { localStorage.setItem(TELEMETRY_KEY, on ? 'on' : 'off'); } catch { /* private window */ }
  if (!on) events.clear();
}

/** Sends what is waiting, when someone is signed in; otherwise forgets it. */
export async function flushEvents(keepalive = false): Promise<number> {
  if (!panelBackend() || !telemetryOn()) { events.clear(); return 0; }
  if (!events.size) return 0;
  const batch = events.take(50);
  try {
    if (keepalive) {
      const backend = panelBackend()!;
      const token = useSession.getState().sessions[backend]?.access;
      if (token) await fetch(`${ROOT}event`, { method: 'POST', keepalive: true, credentials: 'same-origin', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` }, body: JSON.stringify({ events: batch }) });
    } else {
      await panel('event', { body: { events: batch } });
    }
    return batch.length;
  } catch (e) {
    // A panel that is not deployed (404/503) or refuses (429) does not get the same lines again.
    if (e instanceof HttpError && e.status < 0) events.putBack(batch);
    return 0;
  }
}
