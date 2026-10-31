// A backtest of the reader rather than of a rule (OLD core/backtest ReplayReport.kt, feature/chart
// BacktestSheet ReplaySessionCard): the replay's closed practice trades, how each one ended — at the
// stop, at the target, by hand, or with the session («Flatten», the ledger's close-everything) — and
// the rates withheld below thirty trades, where a win rate is an anecdote with decimals.
import { tradeMoney, type ReplayTrade } from './replay';

/** Where a rehearsal stops being an anecdote (OLD BacktestFormat.CONFIDENT_TRADES). */
export const CONFIDENT_TRADES = 30;

export type ReplayExit = 'stop' | 'target' | 'manual' | 'session';
export const exitOf = (t: ReplayTrade): ReplayExit =>
  t.how === 'sl' ? 'stop' : t.how === 'tp' ? 'target' : t.how === 'flatten' ? 'session' : 'manual';

export interface ReplayReport {
  net: number;
  trades: number;
  wins: number;
  losses: number;
  open: number;
  unrealised: number;
  exits: Record<ReplayExit, number>;
  /** Null under CONFIDENT_TRADES: a dash, not a number. */
  winRate: number | null;
  profitFactor: number | null;
  /** The candles revealed in this session. */
  bars: number;
  sampled: boolean;
}

export function replayReport(trades: ReplayTrade[], price: number, bars: number): ReplayReport {
  const closed = trades.filter((t) => t.exit != null);
  const open = trades.filter((t) => t.exit == null && !t.pending);
  const pnl = (t: ReplayTrade) => t.pnl ?? 0;
  const wins = closed.filter((t) => pnl(t) > 0);
  const losses = closed.filter((t) => pnl(t) < 0);
  const exits: Record<ReplayExit, number> = { stop: 0, target: 0, manual: 0, session: 0 };
  for (const t of closed) exits[exitOf(t)]++;
  const sampled = closed.length >= CONFIDENT_TRADES;
  const grossWin = wins.reduce((n, t) => n + pnl(t), 0);
  const grossLoss = -losses.reduce((n, t) => n + pnl(t), 0);
  return {
    net: closed.reduce((n, t) => n + pnl(t), 0),
    trades: closed.length,
    wins: wins.length,
    losses: losses.length,
    open: open.length,
    unrealised: open.reduce((n, t) => n + tradeMoney(t, price) - t.realized, 0),
    exits,
    winRate: sampled ? (wins.length / closed.length) * 100 : null,
    profitFactor: sampled && grossLoss > 0 ? grossWin / grossLoss : null,
    bars,
    sampled,
  };
}
