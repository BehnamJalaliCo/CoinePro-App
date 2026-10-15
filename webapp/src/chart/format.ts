// Figures are always Latin digits, grouped with commas, at the instrument's own precision.
const cache = new Map<number, Intl.NumberFormat>();

export function formatPrice(v: number, digits: number): string {
  if (!Number.isFinite(v)) return '—';
  if (v === 0) return '0';
  let f = cache.get(digits);
  if (!f) {
    f = new Intl.NumberFormat('en-US', { minimumFractionDigits: digits, maximumFractionDigits: digits });
    cache.set(digits, f);
  }
  return f.format(v);
}

export function formatVolume(v: number): string {
  if (!Number.isFinite(v)) return '—';
  if (v === 0) return '0';
  const a = Math.abs(v);
  if (a >= 1e9) return (v / 1e9).toFixed(2) + 'B';
  if (a >= 1e6) return (v / 1e6).toFixed(2) + 'M';
  if (a >= 1e3) return (v / 1e3).toFixed(2) + 'K';
  return v.toFixed(a < 10 ? 2 : 0);
}

export function formatPct(v: number): string {
  if (!Number.isFinite(v)) return '—';
  if (v === 0) return '0';
  return `${v >= 0 ? '+' : '−'}${Math.abs(v).toFixed(2)}%`;
}

/** Price precision when the server does not say: what the phone uses (S1, 5.32.0). */
export function guessDigits(symbol: string, price?: number): number {
  const s = symbol.toUpperCase();
  if (/JPY/.test(s) && !/USDT$/.test(s)) return 3;
  if (/^XAG/.test(s)) return 3;
  if (/^XAU/.test(s)) return 2;
  if (/^[A-Z]{6}$/.test(s)) return 5;
  if (price === undefined) return 2;
  if (price >= 1000) return 2;
  if (price >= 1) return 4;
  if (price >= 0.01) return 5;
  return 8;
}
