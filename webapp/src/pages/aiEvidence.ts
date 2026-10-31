// What the AI was given, so its answer can be checked rather than trusted (the phone's
// AiTechnicalSnapshot, AiSignalGateway.mergedSnapshot, and the assistant's AssistantContextItem).
//
// The setup's readings arrive two ways: TradeYar nests them under `snapshot`, CoinePro-FX writes them
// flat beside the levels. Both are read, the flat one preferred; a reading neither sent is missing,
// never zero.

export interface AiSnapshot {
  ema20: number | null; ema50: number | null; ema200: number | null;
  rsi14: number | null; atr14: number | null; macd: number | null;
  bbUpper: number | null; bbLower: number | null;
  swingHigh20: number | null; swingLow20: number | null; change20: number | null;
  priceNow: number | null;
}

const fin = (v: unknown): number | null => {
  if (v == null || v === '') return null;
  const n = Number(v);
  return Number.isFinite(n) ? n : null;
};
const pick = (o: Record<string, unknown> | null | undefined, ...keys: string[]): number | null => {
  if (!o) return null;
  for (const k of keys) { const v = fin(o[k]); if (v != null) return v; }
  return null;
};

const FIELDS: Record<keyof AiSnapshot, string[]> = {
  ema20: ['ema20', 'ema_20'], ema50: ['ema50', 'ema_50'], ema200: ['ema200', 'ema_200'],
  rsi14: ['rsi14', 'rsi_14', 'rsi'], atr14: ['atr14', 'atr_14', 'atr'], macd: ['macd'],
  bbUpper: ['bb_upper', 'bollinger_upper', 'bbUpper'], bbLower: ['bb_lower', 'bollinger_lower', 'bbLower'],
  swingHigh20: ['swing_high_20', 'swing_high20', 'swing_high', 'swingHigh20'], swingLow20: ['swing_low_20', 'swing_low20', 'swing_low', 'swingLow20'],
  change20: ['change_pct_20', 'change_pct20', 'change_pct', 'change_percent_20'],
  priceNow: ['price_now', 'priceNow', 'current_price', 'price'],
};

/** The readings in a setup result, flat first and nested second; null when there are none at all. */
export function readSnapshot(r: Record<string, unknown>): AiSnapshot | null {
  const nested = r.snapshot && typeof r.snapshot === 'object' ? (r.snapshot as Record<string, unknown>) : null;
  const out = {} as AiSnapshot;
  for (const [k, keys] of Object.entries(FIELDS) as [keyof AiSnapshot, string[]][]) out[k] = pick(r, ...keys) ?? pick(nested, ...keys);
  const any = [out.ema20, out.ema50, out.ema200, out.rsi14, out.atr14, out.macd, out.priceNow].some((v) => v != null);
  return any ? out : null;
}

/** Where price sits between the 20-bar low and high (0–1), or null when the range is unusable. */
export function swingPosition(s: AiSnapshot): number | null {
  if (s.swingLow20 == null || s.swingHigh20 == null || s.priceNow == null) return null;
  const span = s.swingHigh20 - s.swingLow20;
  if (span <= 0) return null;
  return Math.min(1, Math.max(0, (s.priceNow - s.swingLow20) / span));
}

// ── The assistant's sources ─────────────────────────────────────────────────────────────────────

export type ContextKind = 'active_signal' | 'market' | 'news' | 'calendar' | 'risk' | 'tool';
export type Freshness = 'fresh' | 'stale' | 'unknown';
export interface ContextItem { kind: ContextKind; title: string; summary: string | null; source: string | null; asOf: string | null; freshness: Freshness; signalId: string | null }

const KINDS: ContextKind[] = ['active_signal', 'market', 'news', 'calendar', 'risk', 'tool'];

/**
 * The structured sources a reply carries, under whichever key the server uses. Today's server cites
 * nothing, and the app does not assemble citations of its own: that would be the app vouching for the
 * model in the service's voice. An item without a title or a known kind is dropped rather than guessed.
 */
export function readContext(r: Record<string, unknown>): ContextItem[] {
  const list = [r.context, r.sources, r.context_items].find(Array.isArray) as unknown[] | undefined;
  if (!list) return [];
  return list.flatMap((x) => {
    if (!x || typeof x !== 'object') return [];
    const o = x as Record<string, unknown>;
    const kind = String(o.kind ?? o.type ?? '').toLowerCase() as ContextKind;
    const title = String(o.title ?? '').trim();
    if (!KINDS.includes(kind) || !title) return [];
    const f = String(o.freshness ?? '').toLowerCase();
    const str = (v: unknown) => (v == null || v === '' ? null : String(v));
    return [{ kind, title, summary: str(o.summary), source: str(o.source), asOf: str(o.as_of ?? o.asOf), freshness: (f === 'fresh' || f === 'stale' ? f : 'unknown') as Freshness, signalId: str(o.signal_id ?? o.signalId) }];
  });
}
