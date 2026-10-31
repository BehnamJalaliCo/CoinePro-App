// «دوئل با گذشته» (OLD chart/core Duel.kt, core/datastore DuelStore.kt, feature/chart DuelSession.kt):
// once a day, a moment out of the reader's own markets with the next twenty bars hidden, and one
// question — which way did it go. Up or down; a move under half a percent is «too close», neither
// right nor wrong; no hit rate until five rounds were judged. It rides on the bar replay: the chart
// is handed the 120 bars behind the moment and the moment itself, and the twenty after it appear
// only once the call is written down.
import { create } from 'zustand';
import type { Candle } from '../data/types';
import { useReplay, exitReplayUncounted } from './replay';

/** How far ahead the call is judged. */
export const FORWARD_BARS = 20;
/** Bars of history behind the moment. */
export const CONTEXT_BARS = 120;
/** Below this move (per cent), the market did not move enough for a call to have been right. */
export const FLAT_PERCENT = 0.5;
/** Judged rounds a hit rate needs. */
export const MINIMUM_ROUNDS = 5;

export type DuelCall = 'up' | 'down';
export type DuelVerdict = 'right' | 'wrong' | 'too_close';
export interface DuelRound { symbol: string; atBar: number; resolveBar: number; epochDay: number }
export interface DuelOutcome { call: DuelCall; verdict: DuelVerdict; movePercent: number }
export interface DuelRecord { played: number; right: number; tooClose: number }

export const judged = (r: DuelRecord) => r.played - r.tooClose;
/** The hit rate, or null below MINIMUM_ROUNDS judged rounds. */
export const rightPercent = (r: DuelRecord): number | null => (judged(r) >= MINIMUM_ROUNDS ? (r.right * 100) / judged(r) : null);

/** The reader's local day as days since 1970-01-01 (Kotlin's LocalDate.toEpochDay). */
export function localEpochDay(now: number = Date.now()): number {
  const d = new Date(now);
  return Math.round(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()) / 86_400_000);
}

const A = 2_862_933_555_777_941_757n;
const B = 3_037_000_493n;

/**
 * Today's round, or null where there is not enough history. Deterministic in the day and the
 * symbols (sorted by the caller), with the Kotlin Long arithmetic written out so the phone, the
 * JVM tests and this page pick the same moment.
 */
export function roundFor(epochDay: number, symbols: string[], historyBars: number): DuelRound | null {
  if (!symbols.length) return null;
  if (historyBars < CONTEXT_BARS + FORWARD_BARS + 1) return null;
  let seed = BigInt.asIntN(64, BigInt(epochDay) * A + B);
  const next = (): number => {
    seed = BigInt.asIntN(64, seed * A + B);
    return Number((BigInt.asUintN(64, seed) >> 17n) & 0x7fff_ffffn);
  };
  const symbol = symbols[next() % symbols.length]!;
  const span = historyBars - CONTEXT_BARS - FORWARD_BARS - 1;
  const at = CONTEXT_BARS + (next() % (span + 1));
  return { symbol, atBar: at, resolveBar: at + FORWARD_BARS, epochDay };
}

/** What the call came to, or null where the series cannot answer (judged on the whole series). */
export function judge(bars: Candle[], round: DuelRound, call: DuelCall): DuelOutcome | null {
  if (round.atBar < 0 || round.resolveBar >= bars.length) return null;
  const from = bars[round.atBar]!.c;
  const to = bars[round.resolveBar]!.c;
  if (!Number.isFinite(from) || !Number.isFinite(to) || from === 0) return null;
  const move = ((to - from) / from) * 100;
  const verdict: DuelVerdict = Math.abs(move) < FLAT_PERCENT ? 'too_close' : (move > 0) === (call === 'up') ? 'right' : 'wrong';
  return { call, verdict, movePercent: move };
}

export const record = (r: DuelRecord, o: DuelOutcome): DuelRecord => ({
  played: r.played + 1, right: r.right + (o.verdict === 'right' ? 1 : 0), tooClose: r.tooClose + (o.verdict === 'too_close' ? 1 : 0),
});

const KEY = 'pc.duel.v1';
interface Stored { tally: DuelRecord; answeredDay: number | null }
const read = (): Stored => {
  try {
    const v = JSON.parse(localStorage.getItem(KEY) ?? 'null') as Partial<Stored> | null;
    if (v && v.tally) return { tally: { played: v.tally.played | 0, right: v.tally.right | 0, tooClose: v.tally.tooClose | 0 }, answeredDay: typeof v.answeredDay === 'number' ? v.answeredDay : null };
  } catch { /* fresh */ }
  return { tally: { played: 0, right: 0, tooClose: 0 }, answeredDay: null };
};

export interface DuelSession {
  round: DuelRound;
  localDay: number;
  /** The window the replay was handed: 120 bars, the moment, and the 20 that answer it. */
  bars: Candle[];
  outcome: DuelOutcome | null;
  /** The day had already been answered, so this round was not recorded (said, not swallowed). */
  alreadyAnswered: boolean;
}

interface DuelState extends Stored {
  session: DuelSession | null;
  /** Writes one answered round; false (and nothing written) when the day was already answered. */
  answerRecord(o: DuelOutcome, localDay: number): boolean;
  /** Starts the round on these bars (the round's symbol's whole series as loaded). */
  begin(round: DuelRound, bars: Candle[]): boolean;
  call(c: DuelCall): void;
  finish(): void;
  clear(): void;
}

export const useDuel = create<DuelState>((set, get) => {
  const save = () => { const { tally, answeredDay } = get(); try { localStorage.setItem(KEY, JSON.stringify({ tally, answeredDay })); } catch { /* blocked */ } };
  return {
    ...read(),
    session: null,
    answerRecord: (o, localDay) => {
      if (get().answeredDay === localDay) return false;
      set({ tally: record(get().tally, o), answeredDay: localDay });
      save();
      return true;
    },
    begin: (round, bars) => {
      const from = round.atBar - CONTEXT_BARS;
      if (from < 0 || round.resolveBar >= bars.length) return false;
      const window = bars.slice(from, round.resolveBar + 1);
      const local: DuelRound = { ...round, atBar: CONTEXT_BARS, resolveBar: CONTEXT_BARS + FORWARD_BARS };
      const r = useReplay.getState();
      if (r.state?.on) exitReplayUncounted();
      r.start(window, window[CONTEXT_BARS]!.t, { keepView: false });
      if (!useReplay.getState().state?.on) return false;
      set({ session: { round: local, localDay: round.epochDay, bars: window, outcome: null, alreadyAnswered: false } });
      return true;
    },
    call: (c) => {
      const s = get().session;
      if (!s || s.outcome) return;
      const outcome = judge(s.bars, s.round, c);
      if (!outcome) return;
      const recorded = get().answerRecord(outcome, s.localDay);
      set({ session: { ...s, outcome, alreadyAnswered: !recorded } });
      // Only now do the next twenty bars appear.
      useReplay.getState().controller.seek({ index: s.round.resolveBar });
    },
    finish: () => { set({ session: null }); if (useReplay.getState().state?.on) exitReplayUncounted(); },
    clear: () => { set({ tally: { played: 0, right: 0, tooClose: 0 }, answeredDay: null }); save(); },
  };
});

/** While a round is unanswered, nothing may step the replay into the hidden bars. */
export const duelHidesFuture = () => { const s = useDuel.getState().session; return Boolean(s && !s.outcome); };

// Leaving the replay any other way ends the round too.
useReplay.subscribe((r, p) => { if (p.state?.on && !r.state?.on && useDuel.getState().session) useDuel.setState({ session: null }); });
