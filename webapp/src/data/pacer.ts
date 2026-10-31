// Bulk candle reads, paced (OLD core/marketdata PacedCandleGateway.kt, 5.24.3).
//
// The heat map reads up to two hundred and twenty markets' bars and the screener a few hundred,
// four at a time each. Opened one after the other they put some four hundred candle requests on one
// address inside a minute, and both servers cap an address per minute: the browser check of 5.24.2
// counted 219 refusals with 429, and the tiles and columns those answers were for read «–».
//
// So every bulk reader — the screener's scans, the growth scan, the scan watch, the screener
// panel's rating column and the heat map — takes its turn from one pacer per server: one start
// every 60 000 / 45 ms. Never a chart, which is one request a reader is waiting on.
import { loadHistory, type HistoryPage } from './candles';
import { backendOf } from './symbols';
import type { Candle } from './types';

/** The bulk readers' share of each server's per-address minute. */
export const BULK_PER_MINUTE = 45;
/** One start every this many ms (the OLD Long division: 60 000 / 45 = 1333). */
export const BULK_INTERVAL_MS = Math.floor(60_000 / BULK_PER_MINUTE);

/** One start every `intervalMs`, shared by everybody holding the same pacer. */
export class RequestPacer {
  private next = 0;
  constructor(private readonly intervalMs: () => number, private readonly now: () => number = () => Date.now()) {}

  /** Books this caller's turn and says how long to wait for it (ms). */
  reserve(): number {
    const now = this.now();
    const start = Math.max(now, this.next);
    this.next = start + Math.max(0, this.intervalMs());
    return start - now;
  }

  /** Resolves at this caller's turn. */
  async awaitTurn(): Promise<void> {
    const wait = this.reserve();
    if (wait > 0) await new Promise((r) => setTimeout(r, wait));
  }
}

// Under the end-to-end suite (?e2e=1) a scan of sixty fixtures would take eighty seconds, so the
// pace is off there unless a test sets it (localStorage pc.e2e.pace = ms) to watch it work.
const E2E = (() => { try { return new URLSearchParams(window.location.search).has('e2e'); } catch { return false; } })();
function paceMs(): number {
  if (!E2E) return BULK_INTERVAL_MS;
  try { const v = Number(localStorage.getItem('pc.e2e.pace')); return Number.isFinite(v) && v > 0 ? v : 0; } catch { return 0; }
}

/** TradeYar, signed in or not: one address, one cap. */
export const cryptoPacer = new RequestPacer(paceMs);
/** CoinePro-FX. */
export const forexPacer = new RequestPacer(paceMs);

/** The pacer of the server that owns the symbol. */
export function pacerFor(symbol: string): RequestPacer {
  return backendOf(symbol) === 'tradeyar' ? cryptoPacer : forexPacer;
}

/** A bulk read of the newest bars: waits its turn on the symbol's server, then asks at bulk urgency. */
export async function pacedHistory(symbol: string, interval: string): Promise<HistoryPage> {
  await pacerFor(symbol).awaitTurn();
  return loadHistory(symbol, interval, undefined, 'bulk');
}

/** The same, as bars; a failure is no bars. */
export function pacedBars(symbol: string, interval: string): Promise<Candle[]> {
  return pacedHistory(symbol, interval).then((r) => r.bars).catch(() => [] as Candle[]);
}
