// Home's rules, apart from its layout so they can be tested (OLD core/marketdata MarketMood.kt,
// core/common ReturnLoop.kt, core/datastore ArenaStore.kt `streak`, feature/home HomeScreen.kt).
import type { Ticker } from '../data/types';

// ── «بازار امروز» — the board's mood ────────────────────────────────────────────────────────────
export type MarketLean = 'up' | 'down' | 'mixed' | 'unknown';
export interface MarketMood { breadth: number | null; advancing: number; declining: number; movers: Ticker[]; unusual: Ticker[] }

/** How far past half the board has to be before the strip names a direction: sixty-forty. */
export const LEANING = 60;
/** How many markets each list carries. Three is a strip; six is a screen. */
export const LISTED = 3;
/** How deep into the turnover ranking «busy» reaches: the top twenty. */
export const BUSY_DEPTH = 20;

export const moodIsEmpty = (m: MarketMood) => m.breadth == null && !m.movers.length && !m.unusual.length;

export function leanOf(breadth: number | null): MarketLean {
  if (breadth == null) return 'unknown';
  if (breadth >= LEANING) return 'up';
  if (breadth <= 100 - LEANING) return 'down';
  return 'mixed';
}

/**
 * Reads the board. Breadth is the share of the markets that moved that went up (a flat market is
 * not counted either way); movers are the biggest moves either way; «busy and moving» is the top of
 * the board on turnover intersected with movement. `covered` keeps symbols without artwork out.
 */
export function marketMood(tickers: Ticker[], covered: (symbol: string) => boolean = () => true): MarketMood {
  const list = tickers.filter((t) => covered(t.symbol));
  if (!list.length) return { breadth: null, advancing: 0, declining: 0, movers: [], unusual: [] };
  const moved = list.filter((t) => Number.isFinite(t.changePct));
  const advancing = moved.filter((t) => t.changePct > 0).length;
  const declining = moved.filter((t) => t.changePct < 0).length;
  const decided = advancing + declining;
  const breadth = decided > 0 ? Math.floor((advancing * 100) / decided) : null;
  const bySize = [...moved].sort((a, b) => Math.abs(b.changePct) - Math.abs(a.changePct));
  const turnover = (t: Ticker) => (t.volume != null && Number.isFinite(t.volume) ? t.volume * (t.last || 1) : null);
  const busy = new Set(list.map((t) => [t, turnover(t)] as const).filter((x): x is [Ticker, number] => x[1] != null)
    .sort((a, b) => b[1] - a[1]).slice(0, BUSY_DEPTH).map(([t]) => t.symbol.toUpperCase()));
  return {
    breadth, advancing, declining,
    movers: bySize.slice(0, LISTED),
    unusual: bySize.filter((t) => busy.has(t.symbol.toUpperCase())).slice(0, LISTED),
  };
}

// ── The return loop ─────────────────────────────────────────────────────────────────────────────
/** A move worth mentioning, as a share of the price: two per cent. */
export const WORTH_MENTIONING = 0.02;
export const MOVERS_SHOWN = 3;
/** Below this many hours away there is no «since your last visit»: they were just here. */
export const AWAY_HOURS = 4;

export interface SymbolMove { symbol: string; change: number }
export interface SinceLastVisit { awayMillis: number; movers: SymbolMove[]; signals: number; alerts: number }
export const awayHours = (s: SinceLastVisit) => Math.floor(s.awayMillis / 3_600_000);
export const awayDays = (s: SinceLastVisit) => Math.floor(s.awayMillis / 86_400_000);

/** What changed while the reader was away, or null — which is the ordinary case, not an empty card. */
export function sinceLastVisit(lastVisit: number, now: number, movers: SymbolMove[], signals = 0, alerts = 0): SinceLastVisit | null {
  if (lastVisit <= 0) return null;
  const awayMillis = now - lastVisit;
  if (awayMillis < AWAY_HOURS * 3_600_000) return null;
  const worth = movers.filter((m) => Math.abs(m.change) >= WORTH_MENTIONING).sort((a, b) => Math.abs(b.change) - Math.abs(a.change)).slice(0, MOVERS_SHOWN);
  if (!worth.length && signals === 0 && alerts === 0) return null;
  return { awayMillis, movers: worth, signals, alerts };
}

export type ChallengeSurface = 'chart' | 'script' | 'alerts' | 'journal' | 'paper' | 'arena' | 'watchlist';
export interface DailyChallenge { id: string; title: string; surface: ChallengeSurface }

/** Twelve tasks of about a minute each, walked with a stride coprime with the length. */
const CHALLENGES: { id: string; fa: string; en: string; surface: ChallengeSurface }[] = [
  { id: 'read-a-signal', fa: 'یک سیگنال را باز کنید و دلیلش را ببینید', en: 'Open one signal and see why', surface: 'chart' },
  { id: 'add-a-study', fa: 'یک اندیکاتور تازه به چارت اضافه کنید', en: 'Put one new study on the chart', surface: 'chart' },
  { id: 'explain-sheet', fa: 'برگه‌ی «چرا» را برای یک اندیکاتور بخوانید', en: 'Read the Explain sheet for one study', surface: 'chart' },
  { id: 'paste-a-script', fa: 'یک اسکریپت از هوش مصنوعی بگیرید و اجرا کنید', en: 'Get a script from an AI and run it', surface: 'script' },
  { id: 'save-a-script', fa: 'یک اسکریپت را به نام خودتان ذخیره کنید', en: 'Save one script as your own', surface: 'script' },
  { id: 'library-strategy', fa: 'یک استراتژی از کتابخانه را امتحان کنید', en: 'Try one strategy from the library', surface: 'script' },
  { id: 'set-an-alert', fa: 'یک هشدار روی قیمتی که برایتان مهم است بگذارید', en: 'Set an alert on a price you care about', surface: 'alerts' },
  { id: 'journal-a-trade', fa: 'یک معامله را در دفترچه بنویسید', en: 'Write one trade in the journal', surface: 'journal' },
  { id: 'paper-trade', fa: 'یک معامله‌ی آزمایشی باز کنید', en: 'Open one paper trade', surface: 'paper' },
  { id: 'replay-arena', fa: 'یک دور در میدان بازپخش بازی کنید', en: 'Play one round in the replay arena', surface: 'arena' },
  { id: 'watchlist-tidy', fa: 'دیده‌بانتان را مرتب کنید', en: 'Tidy your watchlist', surface: 'watchlist' },
  { id: 'compare-two', fa: 'دو نماد را کنار هم بگذارید', en: 'Put two symbols side by side', surface: 'chart' },
];
const STRIDE = 7;

/** Today's challenge: the same one for everybody, decided by the (UTC) day and nothing else. */
export function challengeFor(epochDay: number, english = false): DailyChallenge {
  const n = CHALLENGES.length;
  const i = (((epochDay * STRIDE) % n) + n) % n;
  const c = CHALLENGES[i]!;
  return { id: c.id, title: english ? c.en : c.fa, surface: c.surface };
}

export const epochDay = (ms: number) => Math.floor(ms / 86_400_000);
/** The reader's local calendar day as an epoch day (the arena keeps local days, as LocalDate does). */
export const localEpochDay = (ms: number) => { const d = new Date(ms); return Math.floor(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()) / 86_400_000); };

/** Days running the arena has been played, today or yesterday counting as unbroken (ArenaStore.streak). */
export function arenaStreak(days: number[], today: number): number {
  if (!days.length) return 0;
  const set = new Set(days);
  let day = set.has(today) ? today : set.has(today - 1) ? today - 1 : null;
  if (day == null) return 0;
  let n = 0;
  while (set.has(day)) { n++; day--; }
  return n;
}

/**
 * The days the reader played the daily arena, from what this browser keeps. Read defensively:
 * a list of rows with `epochDay` (the phone's ArenaStore shape), `day` or a finish time `at`.
 */
export function arenaDays(raw: unknown): number[] {
  const rows = Array.isArray(raw) ? raw : raw && typeof raw === 'object' && Array.isArray((raw as { results?: unknown }).results) ? (raw as { results: unknown[] }).results : [];
  const out: number[] = [];
  for (const r of rows) {
    if (typeof r === 'number' && Number.isFinite(r)) { out.push(r > 1e9 ? localEpochDay(r) : r); continue; }
    if (!r || typeof r !== 'object') continue;
    const x = r as Record<string, unknown>;
    const d = Number(x.epochDay ?? x.day);
    if (Number.isFinite(d) && d > 0 && d < 1e7) out.push(d);
    else if (Number.isFinite(Number(x.at))) out.push(localEpochDay(Number(x.at)));
  }
  return out;
}

export const ARENA_KEYS = ['pc.arena.v1', 'pc.arena.results.v1'];
export function readArenaDays(): number[] {
  const out: number[] = [];
  for (const k of ARENA_KEYS) { try { out.push(...arenaDays(JSON.parse(localStorage.getItem(k) ?? 'null'))); } catch { /* blocked */ } }
  return out;
}

// ── Home's market card: the reader's own list first ─────────────────────────────────────────────
/** How many instruments the home card carries. */
export const HOME_MARKET_ROWS = 6;
const RANK: Record<string, number> = { BTCUSDT: 0, ETHUSDT: 1, SOLUSDT: 2, XAUUSD: 3, XAGUSD: 4 };

/** Starred symbols lead in the order they were starred, then the rest by the app's rank; capped. */
export function homeMarkets(all: string[], watchlist: string[]): { rows: string[]; more: number } {
  const have = new Set(all);
  const starred = watchlist.filter((s) => have.has(s));
  const rest = all.filter((s) => !watchlist.includes(s)).sort((a, b) => (RANK[a] ?? 10) - (RANK[b] ?? 10) || a.localeCompare(b));
  const both = [...starred, ...rest];
  return { rows: both.slice(0, HOME_MARKET_ROWS), more: Math.max(0, both.length - HOME_MARKET_ROWS) };
}

// ── The feed's state, in words ──────────────────────────────────────────────────────────────────
export type FeedState = 'live' | 'connecting' | 'degraded' | 'offline' | 'cached' | 'cachedRefreshing';
/** Live prices older than this are «reconnecting», not live. */
export const LIVE_WINDOW = 20_000;

export function feedState(o: { liveAt: number; savedAt: number | null; online: boolean; now: number }): FeedState {
  const fresh = o.liveAt > 0 && o.now - o.liveAt < LIVE_WINDOW;
  if (fresh) return 'live';
  if (o.savedAt != null && o.liveAt === 0) return o.online ? 'cachedRefreshing' : 'cached';
  if (!o.online) return 'offline';
  if (o.liveAt === 0) return 'connecting';
  return 'degraded';
}

export const FEED_LABEL: Record<FeedState, [string, string]> = {
  live: ['لحظه‌ای', 'Realtime connected'],
  connecting: ['در حال اتصال…', 'Connecting…'],
  degraded: ['اتصال لحظه‌ای در حال بازیابی · قیمت‌ها هر چند ثانیه تازه می‌شوند', 'Realtime reconnecting · prices refresh every few seconds'],
  offline: ['داده‌های بازار آفلاین است', 'Market data offline'],
  cached: ['آخرین داده‌ی ذخیره‌شده · آفلاین', 'Last saved data · offline'],
  cachedRefreshing: ['آخرین داده‌ی ذخیره‌شده · در حال به‌روزرسانی', 'Last saved data · refreshing'],
};
