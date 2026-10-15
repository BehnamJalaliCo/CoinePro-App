// The platforms' AI, on the chart itself — the reading TradingView does not offer:
//   chart vision  POST multipart …/jobs (one part `image`, ≤ 6 MB), then GET …/jobs/{id} every 2 s
//                 TradeYar api/mobile/v1/ai/vision, CoinePro-FX user/ai-vision (core/aivision)
//   assistant     POST /up/coineprofx/api/user/ai/chat {message ≤ 4000} → {answer, remaining}
import { authed } from './auth';
import { HttpError } from './http';
import { backendOf, type Backend } from './symbols';
import { useSession } from './session';

export interface VisionResult {
  assessment: string;
  direction: 'long' | 'short' | null;
  confidence: number | null;
  trendBias: string | null;
  structure: string | null;
  setup: string | null;
  entryLow: number | null;
  entryHigh: number | null;
  sl: number | null;
  targets: number[];
  reasoning: string | null;
  risk: string | null;
}

const PATHS: Record<Backend, string> = {
  tradeyar: '/up/tradeyar/api/mobile/v1/ai/vision/jobs',
  coineprofx: '/up/coineprofx/api/user/ai-vision/jobs',
};

/** The backend that reads this symbol's chart for a signed-in reader, or null. */
export function visionBackend(symbol: string): Backend | null {
  const s = useSession.getState().sessions;
  const own = backendOf(symbol);
  if (s[own]) return own;
  if (s.tradeyar) return 'tradeyar';
  if (s.coineprofx) return 'coineprofx';
  return null;
}

const num = (v: unknown): number | null => { const n = Number(v); return v == null || !Number.isFinite(n) ? null : n; };

function readResult(r: Record<string, unknown>): VisionResult {
  const zone = (r.entry_zone ?? {}) as Record<string, unknown>;
  const targets = Array.isArray(r.targets) ? (r.targets as Record<string, unknown>[]).map((x) => num(x.price)).filter((v): v is number => v != null) : [];
  const dir = String(r.direction ?? '').toLowerCase();
  const conf = num(r.confidence);
  return {
    assessment: String(r.assessment ?? ''),
    direction: dir.startsWith('l') || dir === 'buy' ? 'long' : dir.startsWith('s') || dir === 'sell' ? 'short' : null,
    confidence: conf == null ? null : conf <= 1 ? Math.round(conf * 100) : Math.round(conf),
    trendBias: (r.trend_bias as string) ?? null,
    structure: (r.market_structure as string) ?? null,
    setup: (r.setup as string) ?? null,
    entryLow: num(zone.low), entryHigh: num(zone.high),
    sl: num(r.stop_loss),
    targets,
    reasoning: (r.reasoning as string) ?? null,
    risk: (r.risk as string) ?? null,
  };
}

export async function readChart(symbol: string, image: Blob, onStatus?: (s: string) => void): Promise<VisionResult> {
  const backend = visionBackend(symbol);
  if (!backend) throw new HttpError(401, null, null);
  const form = new FormData();
  form.append('image', image, `${symbol}.png`);
  const job = await authed<{ id?: string | number; job_id?: string | number; status?: string }>(backend, PATHS[backend], { body: form, timeoutMs: 60_000 });
  const id = job.id ?? job.job_id;
  if (id == null) throw new HttpError(500, null, null);
  const started = Date.now();
  for (;;) {
    await new Promise((r) => setTimeout(r, 2000));
    const j = await authed<{ status?: string; result?: Record<string, unknown>; error_message?: string }>(backend, `${PATHS[backend]}/${encodeURIComponent(String(id))}`);
    const st = String(j.status ?? '').toLowerCase();
    onStatus?.(st);
    if (st === 'done' || st === 'completed' || st === 'succeeded') return readResult(j.result ?? {});
    if (['failed', 'error', 'expired', 'rejected'].includes(st)) throw new HttpError(422, { detail: j.error_message ?? st }, null);
    if (Date.now() - started > 120_000) throw new HttpError(504, null, null);
  }
}

export async function askAssistant(message: string): Promise<{ answer: string; remaining: number | null }> {
  const r = await authed<{ answer?: string; remaining?: number }>('coineprofx', '/up/coineprofx/api/user/ai/chat', { body: { message: message.slice(0, 4000) }, timeoutMs: 90_000 });
  return { answer: r.answer ?? '', remaining: r.remaining ?? null };
}
