// «میدان» — the daily replay challenge (OLD chart/core Arena.kt, feature/chart ArenaSession.kt,
// core/datastore ArenaStore.kt). The same market and the same window for everybody on a given day,
// picked from the date (no backend serves «today's challenge»), five minutes on the clock, the
// replay's practice account, and a score: sixty points for how the trades were taken and forty for
// what they made, printed apart so a profitable, undisciplined session cannot hide in a good total.
// The reader's own history — one row per day, a second play of a day replacing the first — gives
// the streak (counted back from today or yesterday) and the best day: the league of one.
import { create } from 'zustand';
import type { Candle } from '../data/types';
import { useReplay, exitReplayUncounted, type ReplayTrade } from './replay';
import { localEpochDay } from './duel';

export const TIMER_SECONDS = 300;
export const MINIMUM_FORWARD_BARS = 60;
export const MINIMUM_CONTEXT_BARS = 120;
export const DISCIPLINE_POINTS = 60;
export const PROFIT_POINTS = 40;
export const TARGET_R = 3;
export const REVENGE_PENALTY = 15;
/** How soon after a loss a new trade counts as revenge, in the replay's own clock. */
export const REVENGE_WINDOW_SECONDS = 90;
/** When the clock turns to the market's red: enough to close a position, not to open one. */
export const URGENT_SECONDS = 60;
const MAX_ROWS = 730;

export interface ArenaChallenge { symbol: string; startBar: number; epochDay: number }
export interface ArenaTrade { hadStop: boolean; rMultiple: number | null; revenge?: boolean }
export interface ArenaScore { discipline: number; profit: number; trades: number; withStop: number; revenge: number; earnedR: number }
export interface ArenaResult { epochDay: number; symbol: string; total: number; discipline: number; profit: number }

export const total = (s: ArenaScore) => s.discipline + s.profit;
export const played = (s: ArenaScore) => s.trades > 0;
export const UNPLAYED: ArenaScore = { discipline: 0, profit: 0, trades: 0, withStop: 0, revenge: 0, earnedR: 0 };

const A = 6_364_136_223_846_793_005n;
const B = 1_442_695_040_888_963_407n;

/**
 * Today's challenge, or null where there is not enough history to play on. Deterministic in the day
 * and the symbols (sorted by the caller), with the Kotlin Long arithmetic written out.
 */
export function challengeFor(epochDay: number, symbols: string[], historyBars: number): ArenaChallenge | null {
  if (!symbols.length) return null;
  if (historyBars < MINIMUM_CONTEXT_BARS + MINIMUM_FORWARD_BARS) return null;
  let seed = BigInt.asIntN(64, BigInt(epochDay) * A + B);
  const next = (): number => {
    seed = BigInt.asIntN(64, seed * A + B);
    return Number((BigInt.asUintN(64, seed) >> 17n) & 0x7fff_ffffn);
  };
  const symbol = symbols[next() % symbols.length]!;
  const span = historyBars - MINIMUM_CONTEXT_BARS - MINIMUM_FORWARD_BARS;
  return { symbol, startBar: MINIMUM_CONTEXT_BARS + (next() % (span + 1)), epochDay };
}

/** Scores a finished session (OLD Arena.score). */
export function score(trades: ArenaTrade[]): ArenaScore {
  if (!trades.length) return UNPLAYED;
  const withStop = trades.filter((t) => t.hadStop).length;
  const revenge = trades.filter((t) => t.revenge).length;
  // Kotlin's integer division: 60 × withStop / n, truncated.
  const discipline = Math.max(0, Math.min(DISCIPLINE_POINTS, Math.trunc((DISCIPLINE_POINTS * withStop) / trades.length) - REVENGE_PENALTY * revenge));
  const earned = trades.reduce((n, t) => n + (t.rMultiple ?? 0), 0);
  const profit = Math.round(PROFIT_POINTS * Math.max(0, Math.min(1, earned / TARGET_R)));
  return { discipline, profit, trades: trades.length, withStop, revenge, earnedR: earned };
}

/** Marks the trades opened within the revenge window of a losing close (OLD markRevengeTrades). */
export function markRevenge(trades: ArenaTrade[], opened: number[], closed: number[], results: number[]): ArenaTrade[] {
  if (trades.length !== opened.length || trades.length !== closed.length || trades.length !== results.length) return trades;
  return trades.map((t, i) => {
    let revenge = false;
    for (let e = 0; e < i; e++) if (results[e]! < 0 && opened[i]! >= closed[e]! && opened[i]! - closed[e]! <= REVENGE_WINDOW_SECONDS) revenge = true;
    return { ...t, revenge };
  });
}

/** The replay's closed practice trades, in the order they closed, as the Arena reads them. */
export function scoreReplay(trades: ReplayTrade[]): ArenaScore {
  const closed = trades.filter((t) => t.exit != null).sort((a, b) => (a.exitAt ?? 0) - (b.exitAt ?? 0));
  const marked = markRevenge(
    closed.map((t) => {
      const hadStop = t.risk > 0 || t.sl != null;
      return { hadStop, rMultiple: hadStop && t.r != null && Number.isFinite(t.r) ? t.r : null };
    }),
    closed.map((t) => t.at),
    closed.map((t) => t.exitAt ?? t.at),
    closed.map((t) => t.pnl ?? 0),
  );
  return score(marked);
}

/** Days in a row, counted back from today or yesterday — a gap of two days ends it. */
export function streak(rows: ArenaResult[], today: number): number {
  if (!rows.length) return 0;
  const days = new Set(rows.map((r) => r.epochDay));
  let day = days.has(today) ? today : days.has(today - 1) ? today - 1 : null;
  if (day == null) return 0;
  let n = 0;
  while (days.has(day)) { n++; day--; }
  return n;
}
export const best = (rows: ArenaResult[]): ArenaResult | null => rows.reduce<ArenaResult | null>((a, r) => (!a || r.total > a.total ? r : a), null);

/** One row per day: a second play of the same day replaces the first. */
export function recordResult(rows: ArenaResult[], r: ArenaResult): ArenaResult[] {
  return [...rows.filter((x) => x.epochDay !== r.epochDay), r].sort((a, b) => a.epochDay - b.epochDay).slice(-MAX_ROWS);
}

/** `m:ss`. */
export function clockOf(seconds: number): string {
  const s = Math.max(0, Math.floor(seconds));
  return `${Math.floor(s / 60)}:${s % 60 < 10 ? '0' : ''}${s % 60}`;
}

const KEY = 'pc.arena.v1';
const readRows = (): ArenaResult[] => {
  try {
    const v = JSON.parse(localStorage.getItem(KEY) ?? '[]') as unknown;
    return Array.isArray(v) ? (v as ArenaResult[]).filter((r) => r && typeof r.epochDay === 'number' && typeof r.total === 'number').sort((a, b) => a.epochDay - b.epochDay) : [];
  } catch { return []; }
};

export interface ArenaSession { challenge: ArenaChallenge; remaining: number; running: boolean; score: ArenaScore | null }

interface ArenaState {
  rows: ArenaResult[];
  session: ArenaSession | null;
  /** Whether the result sheet is showing. */
  result: boolean;
  /** Starts the replay at the challenge's bar (the challenge's market's whole series as loaded). */
  begin(challenge: ArenaChallenge, bars: Candle[]): boolean;
  start(): void;
  tick(): void;
  /** Ends the five minutes with the score of the trades taken so far (the clock or «تمام»). */
  finish(): void;
  closeResult(): void;
  leave(): void;
  clear(): void;
}

let timer: ReturnType<typeof setInterval> | null = null;
const stopTimer = () => { if (timer) { clearInterval(timer); timer = null; } };

export const useArena = create<ArenaState>((set, get) => {
  const save = () => { try { localStorage.setItem(KEY, JSON.stringify(get().rows)); } catch { /* blocked */ } };
  return {
    rows: readRows(),
    session: null,
    result: false,
    begin: (challenge, bars) => {
      const at = bars[challenge.startBar];
      if (!at) return false;
      const r = useReplay.getState();
      if (r.state?.on) exitReplayUncounted();
      r.start(bars, at.t, { keepView: false });
      if (!useReplay.getState().state?.on) return false;
      stopTimer();
      set({ session: { challenge, remaining: TIMER_SECONDS, running: false, score: null }, result: false });
      return true;
    },
    start: () => {
      const s = get().session;
      if (!s || s.score || s.running) return;
      set({ session: { ...s, running: true } });
      stopTimer();
      timer = setInterval(() => get().tick(), 1000);
    },
    tick: () => {
      const s = get().session;
      if (!s?.running) return;
      const remaining = Math.max(0, s.remaining - 1);
      set({ session: { ...s, remaining } });
      if (remaining === 0) get().finish();
    },
    finish: () => {
      const s = get().session;
      if (!s || s.score) return;
      stopTimer();
      const result = scoreReplay(useReplay.getState().trades);
      // Recorded whatever it came to, as the phone does: the day was played.
      const rows = recordResult(get().rows, { epochDay: s.challenge.epochDay, symbol: s.challenge.symbol, total: total(result), discipline: result.discipline, profit: result.profit });
      set({ session: { ...s, running: false, remaining: 0, score: result }, rows, result: true });
      save();
    },
    closeResult: () => set({ result: false }),
    leave: () => { stopTimer(); set({ session: null, result: false }); if (useReplay.getState().state?.on) exitReplayUncounted(); },
    clear: () => { set({ rows: [] }); save(); },
  };
});

export const arenaToday = () => localEpochDay();

// Leaving the replay any other way ends the challenge's clock (the result stays readable).
useReplay.subscribe((r, p) => {
  if (p.state?.on && !r.state?.on) {
    const a = useArena.getState();
    if (a.session && !a.session.score) { stopTimer(); useArena.setState({ session: null, result: false }); }
    else if (a.session && !a.result) useArena.setState({ session: null });
  }
});
