// The market notifications in this browser (OLD app/pulse MarketPulse.kt + MarketPulseEngine.kt,
// 5.27.0; the daily brief, OLD chart/core RasadBrief.kt + app/brief MorningBriefEngine.kt).
//
// Four kinds, each its own switch on the notifications page: a sharp move in bitcoin (±3 %), gold
// (±1.5 %) or the major pairs (±0.7 %); a starred market's move (±5 %); the morning summary
// (09:00–12:00: the majors and the screener's top risers); and the reminder twenty minutes before a
// high-importance release. Each move is measured inside a four-hour window; one market is not
// announced twice inside three hours; at most six a day across every kind (the screener's scan
// watch counts too); nothing between 23:00 and 08:00, nor inside the reader's own quiet hours.
// The daily brief — the reader's own markets at an hour they choose — is off until switched on.
//
// A browser notification needs a tab: the checks run every quarter of an hour while one is open.
// Under the end-to-end suite (?e2e) nothing runs and nothing is offered unless a test opts in
// (localStorage `pc.e2e.notify` = '1'), and even then the test drives `run` itself.
import { create } from 'zustand';
import { useMarket } from './market';
import { useWatchlist } from './watchlist';
import { usePrefs } from './prefs';
import { usePage } from './page';
import { inQuietHours } from './notify';
import { instrumentOf, marketOf } from '../data/symbols';
import { formatPrice } from '../chart/format';
import { hasLogo } from '../ui/SymbolLogo';
import { faNum } from '../i18n/time';
import { calendarWeek } from '../data/intel';
import type { Ticker } from '../data/types';

// ── The rules (OLD MarketPulse) ─────────────────────────────────────────────────────────────────
export const WINDOW_MS = 4 * 60 * 60 * 1000;
export const COOLDOWN_MS = 3 * 60 * 60 * 1000;
export const DAILY_CAP = 6;
export const QUIET_FROM_MINUTE = 23 * 60;
export const QUIET_TO_MINUTE = 8 * 60;
export const DIGEST_MINUTE = 9 * 60;
export const DIGEST_LAST_MINUTE = 12 * 60;
export const CALENDAR_LEAD_MS = 20 * 60 * 1000;
export const BITCOIN = 'BTCUSDT';
export const MAJORS = [BITCOIN, 'XAUUSD', 'EURUSD'];
export const DIGEST_MAJORS = [BITCOIN, 'ETHUSDT', 'XAUUSD', 'EURUSD'];
const MAX_SAMPLES = 24;
const MAX_STARRED = 12;
const MAX_MOVES_PER_RUN = 2;
const MAX_EVENTS = 20;
const LIQUID = 100;
const CALENDAR_REFRESH_MS = 3 * 60 * 60 * 1000;
export const RUN_EVERY_MS = 15 * 60 * 1000;
/** The brief: a tenth of a percent is inside the spread — not a move worth naming (OLD RasadBrief.QUIET_PERCENT). */
export const QUIET_PERCENT = 0.1;
/** How many of the reader's markets the brief counts (OLD MorningBriefEngine.MAX_MARKETS). */
const BRIEF_MARKETS = 12;
/** Seven in the morning by default (OLD NotificationSettings.DEFAULT_BRIEF_MINUTE). */
export const DEFAULT_BRIEF_MINUTE = 7 * 60;

export interface Sample { at: number; price: number }
export interface Move { symbol: string; percent: number; from: number; to: number; windowMs: number }

/** The move, in percent, that a symbol has to make to be announced. */
export function thresholdFor(symbol: string): number {
  const s = symbol.toUpperCase();
  if (s === BITCOIN) return 3;
  const m = marketOf(s);
  return m === 'metals' ? 1.5 : m === 'forex' ? 0.7 : m === 'indices' || m === 'energy' ? 1.5 : 5;
}

/**
 * Adds a price to the window and says whether it now holds a move past the threshold: from the
 * window's low (a rise) or high (a fall) to now. A move found starts the window again from now, so
 * the same climb is not announced on every run.
 */
export function observe(symbol: string, samples: Sample[], now: number, price: number, threshold: number): [Sample[], Move | null] {
  if (!(price > 0)) return [samples, null];
  const kept = [...samples.filter((s) => now - s.at >= 0 && now - s.at <= WINDOW_MS), { at: now, price }];
  let low = kept[0]!, high = kept[0]!;
  for (const s of kept) { if (s.price < low.price) low = s; if (s.price > high.price) high = s; }
  const rise = ((price - low.price) / low.price) * 100;
  const fall = ((price - high.price) / high.price) * 100;
  const move = rise >= threshold && rise >= -fall ? { symbol, percent: rise, from: low.price, to: price, windowMs: now - low.at }
    : -fall >= threshold ? { symbol, percent: fall, from: high.price, to: price, windowMs: now - high.at } : null;
  return move ? [[{ at: now, price }], move] : [kept.slice(-MAX_SAMPLES), null];
}

export const quiet = (minute: number) => minute >= QUIET_FROM_MINUTE || minute < QUIET_TO_MINUTE;
export const digestDue = (minute: number, today: string, lastDigestDay: string | null) =>
  minute >= DIGEST_MINUTE && minute < DIGEST_LAST_MINUTE && !quiet(minute) && lastDigestDay !== today;
/** On opening, once a day, until notifications are on — never again for a reader who said so. */
export const shouldOffer = (enabled: boolean, never: boolean, lastOfferDay: string | null, today: string) => !enabled && !never && lastOfferDay !== today;

export const dayOf = (ms: number) => { const d = new Date(ms); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`; };
export const minuteOf = (ms: number) => { const d = new Date(ms); return d.getHours() * 60 + d.getMinutes(); };

// ── Wording (OLD app strings pulse_*) ───────────────────────────────────────────────────────────
const nameOf = (symbol: string, en: boolean) => { const i = instrumentOf(symbol); return (en ? i.nameEn : i.nameFa) ?? symbol; };
export const signed = (pct: number) => `${pct < 0 ? '−' : '+'}${Math.abs(pct).toFixed(2)}%`;
const priceText = (v: number, symbol: string) => formatPrice(v, instrumentOf(symbol, v).digits);

export function spanText(windowMs: number, en: boolean): string {
  const hours = Math.round(windowMs / 3_600_000);
  if (hours >= 1) return en ? `over the last ${hours} hours` : `در ${faNum(hours)} ساعت گذشته`;
  const minutes = Math.max(15, Math.floor(windowMs / 60_000));
  return en ? `over the last ${minutes} minutes` : `در ${faNum(minutes)} دقیقه‌ی گذشته`;
}

export function moveText(m: Move, en: boolean): { title: string; body: string } {
  const name = nameOf(m.symbol, en);
  const pct = signed(m.percent);
  const title = m.percent >= 0 ? (en ? `${name} up ${pct}` : `${name} ${pct} بالا رفت`) : (en ? `${name} down ${pct}` : `${name} ${pct} پایین آمد`);
  const price = priceText(m.to, m.symbol);
  return { title, body: en ? `Now ${price}, ${spanText(m.windowMs, en)}` : `اکنون ${price}، ${spanText(m.windowMs, en)}` };
}

/** The screener's three biggest risers among the hundred most traded coins with a mark. */
export function moversOf(tickers: Ticker[]): { symbol: string; change: number }[] {
  return tickers
    .filter((t) => t.symbol.endsWith('USDT') && hasLogo(t.symbol) && Number.isFinite(t.changePct))
    .sort((a, b) => (b.volume ?? 0) * b.last - (a.volume ?? 0) * a.last)
    .slice(0, LIQUID)
    .sort((a, b) => b.changePct - a.changePct)
    .slice(0, 3)
    .filter((t) => t.changePct > 0)
    .map((t) => ({ symbol: t.symbol, change: t.changePct }));
}

export function digestText(tickers: Record<string, Ticker>, en: boolean): { title: string; body: string } | null {
  const tiles = DIGEST_MAJORS.flatMap((s) => { const q = tickers[s]; return q ? [`${nameOf(s, en)} ${Number.isFinite(q.changePct) ? signed(q.changePct) : '—'}`] : []; });
  if (!tiles.length) return null;
  const line = tiles.join(' · ');
  const movers = moversOf(Object.values(tickers)).map((m) => `${m.symbol.replace(/USDT$/, '')} ${signed(m.change)}`).join('، ');
  const label = en ? 'Top risers:' : 'بیشترین رشد:';
  return { title: en ? 'Today’s market summary' : 'خلاصه‌ی بازار امروز', body: movers ? `${line}\n${label} ${movers}` : line };
}

export interface PulseEvent { id: string; title: string; currency: string; at: number; forecast: string; previous: string }
export function calendarText(e: PulseEvent, now: number, en: boolean): { title: string; body: string } {
  const minutes = Math.max(1, Math.floor((e.at - now) / 60_000));
  const details = [en ? 'High importance' : 'اهمیت بالا', e.currency || null, e.forecast ? (en ? `forecast ${e.forecast}` : `پیش‌بینی ${e.forecast}`) : null, e.previous ? (en ? `previous ${e.previous}` : `قبلی ${e.previous}`) : null].filter(Boolean).join(' · ');
  return { title: en ? `In ${minutes} min: ${e.title}` : `${faNum(minutes)} دقیقه‌ی دیگر: ${e.title}`, body: details };
}

// ── The daily brief (OLD RasadBrief) ────────────────────────────────────────────────────────────
export interface BriefMarket { symbol: string; changePercent: number | null }
export interface Brief { headline: string; mover: string | null; lines: string[] }

/** The market the brief is about: the largest move either way; ties go to the reader's own order. */
export function moverOf(markets: BriefMarket[]): BriefMarket | null {
  let best: BriefMarket | null = null;
  for (const m of markets) if (m.changePercent != null && Number.isFinite(m.changePercent) && (!best || Math.abs(m.changePercent) > Math.abs(best.changePercent!))) best = m;
  return best;
}

/** The brief, or null where there is nothing honest to say (no market's change could be read). */
export function briefOf(markets: BriefMarket[], en: boolean): Brief | null {
  const measured = markets.filter((m) => m.changePercent != null && Number.isFinite(m.changePercent));
  if (!measured.length) return null;
  const up = measured.filter((m) => m.changePercent! > 0).length;
  const down = measured.filter((m) => m.changePercent! < 0).length;
  const quietHeadline = (n: number) => (en ? `A quiet night across your ${n} markets` : `شب آرامی برای ${faNum(n)} بازار شما`);
  const mover = moverOf(markets);
  const move = mover?.changePercent ?? 0;
  if (!mover || Math.abs(move) < QUIET_PERCENT) {
    return { headline: quietHeadline(measured.length), mover: null, lines: [en ? 'Nothing on your list moved enough to be worth naming.' : 'هیچ‌کدام از بازارهای فهرست شما آن‌قدر تکان نخورد که ارزش نام‌بردن داشته باشد.'] };
  }
  const headline = up > 0 && down > 0 ? (en ? `${up} up, ${down} down` : `${faNum(up)} بالا، ${faNum(down)} پایین`)
    : up > 0 ? (en ? `${up} up` : `${faNum(up)} بالا`)
      : down > 0 ? (en ? `${down} down` : `${faNum(down)} پایین`) : quietHeadline(up + down);
  const figure = Math.abs(move).toFixed(2);
  const line = en ? `${mover.symbol} ${move > 0 ? 'rose' : 'fell'} ${figure}%.` : `${mover.symbol} ${figure}٪ ${move > 0 ? 'بالا رفت' : 'پایین آمد'}.`;
  return { headline, mover: mover.symbol, lines: [line] };
}

// ── Settings and the record (this browser) ──────────────────────────────────────────────────────
export interface PulseSettings { move: boolean; watchlist: boolean; digest: boolean; calendar: boolean; scan: boolean; brief: boolean; briefMinute: number }
export const DEFAULT_PULSE: PulseSettings = { move: true, watchlist: true, digest: true, calendar: true, scan: true, brief: false, briefMinute: DEFAULT_BRIEF_MINUTE };
const SETTINGS_KEY = 'pc.pulse.v1';
const RECORD_KEY = 'pc.pulse.record.v1';
interface PulseRecord {
  samples: Record<string, Sample[]>; fired: Record<string, number>; sentDay: string | null; sent: number;
  digestDay: string | null; briefDay: string | null; offerDay: string | null; never: boolean;
  announced: string[]; events: PulseEvent[]; eventsAt: number;
}
const EMPTY: PulseRecord = { samples: {}, fired: {}, sentDay: null, sent: 0, digestDay: null, briefDay: null, offerDay: null, never: false, announced: [], events: [], eventsAt: 0 };
const read = <T,>(key: string, base: T): T => { try { return { ...base, ...(JSON.parse(localStorage.getItem(key) ?? '{}') as Partial<T>) }; } catch { return base; } };
const write = (key: string, v: unknown) => { try { localStorage.setItem(key, JSON.stringify(v)); } catch { /* ignore */ } };
let record: PulseRecord = read(RECORD_KEY, EMPTY);
const saveRecord = () => write(RECORD_KEY, record);

interface PulseState extends PulseSettings { set(p: Partial<PulseSettings>): void }
export const usePulse = create<PulseState>((set, get) => ({
  ...read(SETTINGS_KEY, DEFAULT_PULSE),
  set: (p) => { set(p); const { set: _s, ...rest } = get(); void _s; write(SETTINGS_KEY, rest); },
}));

// ── Posting, behind the gates ───────────────────────────────────────────────────────────────────
// Read once, when the module loads: the address loses its query once the route is applied.
const E2E = (() => { try { return new URLSearchParams(window.location.search).has('e2e'); } catch { return false; } })();
export const isE2E = () => E2E;
const optedIn = () => { try { return localStorage.getItem('pc.e2e.notify') === '1'; } catch { return false; } };
/** Whether this page may notify or offer at all: never under the e2e suite unless a test opted in. */
export const allowed = () => !E2E || optedIn();
const realPermission = (): NotificationPermission | 'unsupported' => (typeof Notification === 'undefined' ? 'unsupported' : Notification.permission);
/**
 * The browser's answer. An opted-in e2e test may state it (localStorage `pc.e2e.permission`),
 * because the suite's page is not a secure context and the browser there always says «denied».
 */
export const permission = (): NotificationPermission | 'unsupported' => {
  if (E2E && optedIn()) { try { const v = localStorage.getItem('pc.e2e.permission'); if (v === 'granted' || v === 'default' || v === 'denied') return v; } catch { /* ignore */ } }
  return realPermission();
};
/** What was posted, newest last (read by the e2e suite). */
export const posted: { kind: string; title: string; body: string; at: number }[] = [];

/** Whether a notification may go out now: not in the night, not in the reader's quiet hours, under the day's cap. */
export function canSpend(now: number): boolean {
  if (quiet(minuteOf(now)) || inQuietHours(new Date(now))) return false;
  return sentToday(now) < DAILY_CAP;
}
export function sentToday(now: number): number { return record.sentDay === dayOf(now) ? record.sent : 0; }
function countOne(now: number) { const d = dayOf(now); record = { ...record, sentDay: d, sent: (record.sentDay === d ? record.sent : 0) + 1 }; saveRecord(); }

/** Shows one notification if this page may; true when it went out. Counted against the day's cap when `counted`. */
export function post(kind: string, title: string, body: string, opts: { tag?: string; onClick?: () => void; now?: number; counted?: boolean } = {}): boolean {
  if (!allowed() || permission() !== 'granted') return false;
  // Under an opted-in e2e test the browser cannot really show one: it is recorded in `posted` only.
  if (realPermission() === 'granted') {
    try {
      const n = new Notification(title, { body, tag: opts.tag ?? kind, icon: `${import.meta.env.BASE_URL}generated/app-icons/favicon-32.png` });
      n.onclick = () => { try { window.focus(); } catch { /* ignore */ } opts.onClick?.(); n.close(); };
    } catch { return false; }
  } else if (!E2E) return false;
  const now = opts.now ?? Date.now();
  posted.push({ kind, title, body, at: now });
  if (opts.counted !== false) countOne(now);
  return true;
}

const openChart = (symbol: string) => () => { usePrefs.getState().setSymbol(symbol); usePage.getState().close(); };
const openPage = (id: 'screener' | 'calendar') => () => usePage.getState().open(id);

/** The reader's first list (the default one), upper-cased and de-duplicated, at most `cap`. */
function firstList(cap: number): string[] {
  const lists = useWatchlist.getState().lists;
  const list = lists.find((l) => l.id === 'default') ?? lists[0];
  return [...new Set((list?.symbols ?? []).map((s) => s.trim().toUpperCase()).filter(Boolean))].slice(0, cap);
}

/** One pass of the market notifications (OLD MarketPulseEngine.run). */
export async function run(now = Date.now()): Promise<void> {
  if (!allowed() || permission() !== 'granted') return;
  const s = usePulse.getState();
  const en = usePrefs.getState().lang === 'en';
  const minute = minuteOf(now);
  const today = dayOf(now);
  const silent = quiet(minute) || inQuietHours(new Date(now));
  const tickers = useMarket.getState().tickers;
  const majors = s.move ? MAJORS : [];
  const starred = s.watchlist ? firstList(MAX_STARRED) : [];
  const due = s.digest && digestDue(minute, today, record.digestDay);

  // The samples are kept up to date in the quiet night too, so the morning does not open with a
  // stale window; only the notification waits.
  const moves: { move: Move; kind: 'market_move' | 'watchlist_move' }[] = [];
  const samples = { ...record.samples };
  for (const symbol of [...new Set([...majors, ...starred])]) {
    const q = tickers[symbol];
    if (!q) continue;
    const [kept, move] = observe(symbol, samples[symbol] ?? [], now, q.last, thresholdFor(symbol));
    samples[symbol] = kept;
    if (move && now - (record.fired[symbol] ?? 0) >= COOLDOWN_MS) moves.push({ move, kind: majors.includes(symbol) ? 'market_move' : 'watchlist_move' });
  }
  record = { ...record, samples };
  saveRecord();
  if (silent) return;

  let budget = DAILY_CAP - sentToday(now);
  // Bitcoin first, then the largest move: the cap should spend itself on what matters most.
  moves.sort((a, b) => Number(b.move.symbol === BITCOIN) - Number(a.move.symbol === BITCOIN) || Math.abs(b.move.percent) - Math.abs(a.move.percent));
  for (const { move, kind } of moves.slice(0, MAX_MOVES_PER_RUN)) {
    if (budget <= 0) break;
    const t = moveText(move, en);
    if (post(kind, t.title, t.body, { tag: `move:${move.symbol}`, onClick: openChart(move.symbol), now })) {
      record = { ...record, fired: { ...record.fired, [move.symbol]: now } }; saveRecord();
      budget--;
    }
  }

  // Not while the reader is looking at the page: a summary of the prices in front of them is
  // noise. The day counts as summarised — they have seen it (OLD 5.29.0, the app in the foreground).
  if (due && typeof document !== 'undefined' && document.visibilityState === 'visible') {
    record = { ...record, digestDay: today }; saveRecord();
  } else if (due && budget > 0) {
    const d = digestText(tickers, en);
    if (d && post('market_digest', d.title, d.body, { tag: 'digest', onClick: openPage('screener'), now })) {
      record = { ...record, digestDay: today }; saveRecord();
      budget--;
    }
  }

  if (s.calendar && budget > 0) await calendar(now, en, budget);
}

async function calendar(now: number, en: boolean, budget: number): Promise<void> {
  if (now - record.eventsAt > CALENDAR_REFRESH_MS) {
    try {
      const list = (await calendarWeek()).filter((e) => e.impact === 'high' && e.time > now).sort((a, b) => a.time - b.time).slice(0, MAX_EVENTS)
        .map((e) => ({ id: `${e.time}:${e.currency}:${e.title}`, title: e.title, currency: e.currency, at: e.time, forecast: e.forecast, previous: e.previous }));
      record = { ...record, events: list, eventsAt: now }; saveRecord();
    } catch { /* the next pass asks again */ }
  }
  const announced = new Set(record.announced);
  let left = budget;
  for (const e of record.events.filter((x) => !announced.has(x.id) && x.at - now >= 0 && x.at - now <= CALENDAR_LEAD_MS).slice(0, 2)) {
    if (left <= 0) break;
    const t = calendarText(e, now, en);
    if (post('calendar', t.title, t.body, { tag: `cal:${e.id}`, onClick: openPage('calendar'), now })) { announced.add(e.id); left--; }
  }
  record = { ...record, announced: [...announced].slice(-200) }; saveRecord();
}

/** The daily brief, once a day at or after the reader's hour (OLD MorningBriefEngine.run). */
export function runBrief(now = Date.now()): boolean {
  const s = usePulse.getState();
  if (!s.brief || !allowed() || permission() !== 'granted') return false;
  const today = dayOf(now);
  if (record.briefDay === today || minuteOf(now) < s.briefMinute) return false;
  const symbols = firstList(BRIEF_MARKETS);
  if (!symbols.length) return false;
  const tickers = useMarket.getState().tickers;
  const brief = briefOf(symbols.map((symbol) => ({ symbol, changePercent: tickers[symbol]?.changePct ?? null })), usePrefs.getState().lang === 'en');
  if (!brief) return false;
  // Once a day, and outside the six: it is the reader's own appointment, not the market's news.
  if (!post('morning_brief', brief.headline, brief.lines.join(' '), { tag: 'brief', onClick: brief.mover ? openChart(brief.mover) : undefined, now, counted: false })) return false;
  record = { ...record, briefDay: today }; saveRecord();
  return true;
}

// ── The offer (OLD NotificationOffer) ───────────────────────────────────────────────────────────
/** Whether to offer notifications on opening: once a day while the browser can still ask, never under e2e unless opted in. */
export function offerDue(now = Date.now()): boolean {
  return allowed() && permission() === 'default' && shouldOffer(false, record.never, record.offerDay, dayOf(now));
}
export function offerSeen(never = false, now = Date.now()): void { record = { ...record, offerDay: dayOf(now), never: record.never || never }; saveRecord(); }

/** Test and settings helpers. */
export const pulseRecord = () => record;
export function resetPulseRecord(): void { record = { ...EMPTY }; saveRecord(); posted.length = 0; }

let started = false;
/** Runs the checks while a tab is open: the brief each minute, the market every quarter hour. */
export function startPulse(): void {
  if (started || !allowed() || isE2E()) return;
  started = true;
  let tick = 0;
  const loop = () => {
    runBrief();
    if (tick % 15 === 1) void run();
    tick++;
  };
  window.setInterval(loop, 60_000);
}
