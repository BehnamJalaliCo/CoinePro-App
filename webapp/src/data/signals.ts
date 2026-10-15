// The platforms' trading signals — what TradingView does not have. A signed-in reader sees the
// active ones (TradeYar for crypto, CoinePro-FX for forex when the plan allows); everyone sees the
// public track record of closed signals (GET /api/track-record).
import { request } from './http';
import { authed } from './auth';
import { useSession } from './session';

export interface Signal {
  id: string;
  symbol: string;
  direction: 'long' | 'short';
  timeframe: string;
  status: string;
  entry: number | null;
  sl: number | null;
  targets: number[];
  createdAt: number;
  closedAt: number | null;
  isWin: boolean | null;
  pctGain: number | null;
  rr: number | null;
  confidence: number | null;
  rationale: string | null;
}

const num = (v: unknown): number | null => { const n = Number(v); return v == null || v === '' || !Number.isFinite(n) ? null : n; };
const ms = (v: unknown): number | null => { if (v == null) return null; const t = typeof v === 'number' ? (v < 1e12 ? v * 1000 : v) : Date.parse(String(v)); return Number.isFinite(t) ? t : null; };

function read(x: Record<string, unknown>): Signal {
  const targets = Array.isArray(x.targets)
    ? (x.targets as Record<string, unknown>[]).map((tt) => num(tt.price)).filter((v): v is number => v != null)
    : [x.tp1 ?? x.tp1_price, x.tp2 ?? x.tp2_price, x.tp3 ?? x.tp3_price].map(num).filter((v): v is number => v != null);
  const conf = num(x.confidence ?? x.signal_score);
  return {
    id: String(x.id ?? `${x.symbol}-${x.created_at}`),
    symbol: String(x.symbol ?? '').toUpperCase(),
    direction: String(x.direction ?? '').toLowerCase().startsWith('s') ? 'short' : 'long',
    timeframe: String(x.timeframe ?? ''),
    status: String(x.status ?? ''),
    entry: num(x.entry ?? x.entry_price),
    sl: num(x.stop_loss ?? x.sl ?? x.sl_price),
    targets,
    createdAt: ms(x.created_at) ?? 0,
    closedAt: ms(x.closed_at),
    isWin: typeof x.is_win === 'boolean' ? x.is_win : null,
    pctGain: num(x.pct_gain ?? x.live_pnl_percent),
    rr: num(x.risk_reward ?? x.risk_reward_tp1 ?? x.rr),
    confidence: conf == null ? null : conf <= 1 ? conf * 100 : conf,
    rationale: typeof x.rationale === 'string' ? x.rationale : null,
  };
}

export async function trackRecord(limit = 30): Promise<Signal[]> {
  const r = await request<{ signals?: Record<string, unknown>[] }>(`/api/track-record?limit=${Math.max(5, limit)}`);
  return (r.signals ?? []).map(read).filter((s) => s.symbol);
}

export async function activeSignals(): Promise<Signal[]> {
  const s = useSession.getState().sessions;
  const jobs: Promise<Signal[]>[] = [];
  if (s.tradeyar) {
    jobs.push(authed<{ items?: Record<string, unknown>[] }>('tradeyar', '/up/tradeyar/api/mobile/v1/signals?status=active&limit=50')
      .then((r) => (r.items ?? []).map(read)));
  }
  if (s.coineprofx) {
    jobs.push(authed<{ items?: Record<string, unknown>[] }>('coineprofx', '/up/coineprofx/api/public/signals/active?limit=50')
      .then((r) => (r.items ?? []).map(read)));
  }
  const all = await Promise.allSettled(jobs);
  return all.flatMap((r) => (r.status === 'fulfilled' ? r.value : [])).sort((a, b) => b.createdAt - a.createdAt);
}

/** '15m', 'M15', 'H4', '1h', 'D1' → the app's interval id. */
export function intervalOfTimeframe(tf: string): string | null {
  const s = tf.trim();
  let m = /^(\d+)\s*([mhdw])$/i.exec(s);
  if (m) { const n = Number(m[1]); const u = m[2]!.toLowerCase(); return u === 'm' ? String(n) : u === 'h' ? String(n * 60) : u === 'd' ? '1D' : '1W'; }
  m = /^([MHDW])(\d+)$/i.exec(s);
  if (m) { const n = Number(m[2]); const u = m[1]!.toUpperCase(); return u === 'M' ? String(n) : u === 'H' ? String(n * 60) : u === 'D' ? '1D' : '1W'; }
  return null;
}
