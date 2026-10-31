// The browser's half of the owner's console (OLD app/owner OwnerChannel.kt, OwnerInbox.kt): a
// check-in with pro-chart.com (POST /api/app/hello, served by web/admin) at most every
// `hello_every_hours`, and what comes back — campaigns as cards, and two banners (maintenance, and
// the update nudge). What is sent: a random install id made in this browser, «web», the version,
// the language and the time zone's name; nothing about the account, the markets or the activity.
// A browser shows no notifications for these, so every live campaign waits as a card; a card the
// reader opened or dismissed is settled and never shown again. A failed check-in waits half an hour.
import { create } from 'zustand';
import { request, uuid } from './http';
import { usePrefs } from '../state/prefs';

export interface OwnerMessage {
  id: number; kind: string; high: boolean; title: string; body: string; cta: string; link: string; coupon: string; notify: boolean; endsAtMillis: number | null;
}
export interface OwnerBoard { maintenance: string | null; updateNudge: string | null; cards: OwnerMessage[] }
export const EMPTY_BOARD: OwnerBoard = { maintenance: null, updateNudge: null, cards: [] };

const HELLO = '/api/app/hello';
const EVENT = '/api/app/event';
const K = { install: 'pc.owner.install.v1', reply: 'pc.owner.reply.v1', last: 'pc.owner.last.v1', failed: 'pc.owner.failed.v1', every: 'pc.owner.every.v1', settled: 'pc.owner.settled.v1', shown: 'pc.owner.shown.v1' };
const DEFAULT_EVERY_HOURS = 6;
const HOUR = 3_600_000;
const RETRY_AFTER = 30 * 60_000;
const MAX_REMEMBERED = 200;
const MAX_TITLE = 80; const MAX_BODY = 400; const MAX_CTA = 30;
const COUPON = /^[A-Z0-9][A-Z0-9-]{2,39}$/;
export const SCHEME_PREFIX = 'coinepro://';

/** The console allows these two shapes and nothing else; checked again here, where it is acted on. */
export const isSafeLink = (link: string) => link === '' || link.startsWith(SCHEME_PREFIX) || (link.startsWith('https://') && link.length <= 300 && !/\s/.test(link));

/** The phone's versionCode rule from the shared version: MAJOR·10⁷ + MINOR·10⁵ + PATCH·10³. */
export function versionCodeOf(version: string): number {
  const m = /^(\d+)\.(\d+)\.(\d+)/.exec(version);
  return m ? Number(m[1]) * 10_000_000 + Number(m[2]) * 100_000 + Number(m[3]) * 1_000 : 0;
}

/** The update banner: on, and this build older than the oldest version the owner still recommends. */
export const nudge = (on: boolean, message: string, minVersionCode: number, versionCode: number): string | null =>
  on && minVersionCode > 0 && versionCode < minVersionCode ? message : null;

export function messagesFrom(reply: unknown): OwnerMessage[] {
  const list = (reply as { messages?: unknown } | null)?.messages;
  if (!Array.isArray(list)) return [];
  const out: OwnerMessage[] = [];
  for (const raw of list) {
    if (!raw || typeof raw !== 'object') continue;
    const x = raw as Record<string, unknown>;
    const id = Number(x.id);
    if (!Number.isInteger(id) || id <= 0) continue;
    const s = (v: unknown) => (typeof v === 'string' ? v : v == null ? '' : String(v));
    const link = s(x.link);
    const coupon = s(x.coupon);
    out.push({
      id, kind: s(x.kind), high: x.priority === 'high', title: s(x.title).slice(0, MAX_TITLE), body: s(x.body).slice(0, MAX_BODY), cta: s(x.cta).slice(0, MAX_CTA),
      link: isSafeLink(link) ? link : '', coupon: COUPON.test(coupon) ? coupon : '', notify: x.notify !== false,
      endsAtMillis: x.ends_at == null ? null : Number(x.ends_at) * 1000,
    });
  }
  return out;
}

/** The banners and the cards a reply puts on screen now, in the reader's language. */
export function boardFrom(reply: unknown, now: number, settled: Set<number>, english: boolean, versionCode: number): OwnerBoard {
  if (!reply || typeof reply !== 'object') return EMPTY_BOARD;
  const config = ((reply as { config?: unknown }).config ?? null) as Record<string, unknown> | null;
  const banner = (name: string): [boolean, string] => {
    const item = config?.[name] as Record<string, unknown> | null | undefined;
    if (!item || typeof item !== 'object') return [false, ''];
    const fa = typeof item.message_fa === 'string' ? item.message_fa : '';
    const en = typeof item.message_en === 'string' ? item.message_en : '';
    return [item.on === true, (english ? en : fa).trim() || fa];
  };
  const [mOn, m] = banner('maintenance');
  const [nOn, n] = banner('update_nudge');
  const cards = messagesFrom(reply).filter((x) => !settled.has(x.id) && (x.endsAtMillis == null || x.endsAtMillis > now));
  return { maintenance: mOn ? m : null, updateNudge: nudge(nOn, n, Number(config?.min_version_code ?? 0) || 0, versionCode), cards };
}

// ── Storage ────────────────────────────────────────────────────────────────────────────────────
const get = (k: string) => { try { return localStorage.getItem(k); } catch { return null; } };
const put = (k: string, v: string) => { try { localStorage.setItem(k, v); } catch { /* private window */ } };
const ids = (k: string): number[] => { try { const v = JSON.parse(get(k) ?? '[]') as unknown; return Array.isArray(v) ? v.filter((x): x is number => Number.isInteger(x)) : []; } catch { return []; } };
const remember = (k: string, id: number) => { const now = ids(k); if (!now.includes(id)) put(k, JSON.stringify([...now, id].slice(-MAX_REMEMBERED))); };
function installId(): string {
  const have = get(K.install);
  if (have && have.length >= 8) return have;
  const made = uuid();
  put(K.install, made);
  return made;
}
const english = () => usePrefs.getState().lang === 'en';
const versionCode = () => versionCodeOf(__APP_VERSION__);
const lastReply = (): unknown => { try { return JSON.parse(get(K.reply) ?? 'null'); } catch { return null; } };

interface OwnerState { board: OwnerBoard; publish(b: OwnerBoard): void }
export const useOwner = create<OwnerState>((set) => ({ board: EMPTY_BOARD, publish: (board) => set({ board }) }));

const refresh = () => useOwner.getState().publish(boardFrom(lastReply(), Date.now(), new Set(ids(K.settled)), english(), versionCode()));

function event(id: number, kind: 'delivered' | 'opened' | 'dismissed'): void {
  void request(EVENT, { body: { install_id: installId(), message_id: id, kind }, timeoutMs: 10_000 }).catch(() => undefined);
}

/** One check-in, unless the last was recent; `force` skips the wait. Never throws. */
export async function checkIn(force = false): Promise<void> {
  const now = Date.now();
  const every = Math.min(24, Math.max(1, Number(get(K.every)) || DEFAULT_EVERY_HOURS)) * HOUR;
  const last = Number(get(K.last)) || 0;
  const failed = Number(get(K.failed)) || 0;
  if (!force && (now - last < every || now - failed < RETRY_AFTER)) { refresh(); return; }
  try {
    const reply = await request<Record<string, unknown>>(HELLO, {
      body: { install_id: installId(), platform: 'web', flavour: 'direct', lang: english() ? 'en' : 'fa', version: __APP_VERSION__, version_code: versionCode(), sdk: 0, tz: (() => { try { return Intl.DateTimeFormat().resolvedOptions().timeZone; } catch { return ''; } })() },
      timeoutMs: 10_000,
    });
    if (!reply || typeof reply !== 'object') throw new Error('unreadable');
    const hours = Number((reply.config as Record<string, unknown> | undefined)?.hello_every_hours) || DEFAULT_EVERY_HOURS;
    put(K.last, String(now)); put(K.failed, '0'); put(K.every, String(hours)); put(K.reply, JSON.stringify(reply));
  } catch {
    put(K.failed, String(now));
  }
  refresh();
}

let started = false;
export function startOwnerChannel(): void {
  if (started || typeof window === 'undefined') return;
  started = true;
  refresh();
  void checkIn();
  document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'visible') void checkIn(); });
  usePrefs.subscribe((s, prev) => { if (s.lang !== prev.lang) refresh(); });
}

/** The reader saw a card: counted once as delivered. */
export function ownerShown(m: OwnerMessage): void {
  if (ids(K.shown).includes(m.id)) return;
  remember(K.shown, m.id);
  event(m.id, 'delivered');
}

export function ownerDismiss(m: OwnerMessage): void {
  remember(K.settled, m.id);
  event(m.id, 'dismissed');
  refresh();
}

/** Opens a card: a web address in a new tab, an app link (coinepro://…) as the page it names. */
export function ownerOpen(m: OwnerMessage, openApp: (path: string) => void): void {
  remember(K.settled, m.id);
  event(m.id, 'opened');
  refresh();
  if (m.link.startsWith('https://')) window.open(m.link, '_blank', 'noopener,noreferrer');
  else if (m.link.startsWith(SCHEME_PREFIX)) openApp(m.link.slice(SCHEME_PREFIX.length));
}
