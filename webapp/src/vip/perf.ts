// The performance record's arithmetic, kept honest the way the user site keeps it: a win rate only
// over graded trades (wins + losses), never a 0% for «nothing graded», the outcome walk (+1 a win,
// −1 a loss, in time order), and the month bars. Pure; the pages draw it.

export type Outcome = 'WIN' | 'LOSS' | 'BREAKEVEN' | 'UNKNOWN' | 'OPEN';

export interface Graded { outcome: Outcome; at: number }

/** +1 per win, −1 per loss, oldest first, starting at 0 (two points at least to be worth drawing). */
export function outcomeWalk(trades: Graded[]): number[] {
  const g = trades.filter((t) => t.outcome === 'WIN' || t.outcome === 'LOSS').sort((a, b) => a.at - b.at);
  if (!g.length) return [];
  const walk = [0];
  let run = 0;
  for (const t of g) { run += t.outcome === 'WIN' ? 1 : -1; walk.push(run); }
  return walk;
}

export interface MonthRow { month: string; wins: number; losses: number; graded: number; winRate: number | null; total: number }

/** Month buckets (YYYY-MM, oldest first) from graded trades; the rate is null when nothing graded. */
export function monthRows(trades: Graded[], tz: 'utc' | 'local' = 'local'): MonthRow[] {
  const by = new Map<string, MonthRow>();
  for (const t of trades) {
    const d = new Date(t.at);
    const key = tz === 'utc' ? `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}` : `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
    const r = by.get(key) ?? { month: key, wins: 0, losses: 0, graded: 0, winRate: null, total: 0 };
    r.total++;
    if (t.outcome === 'WIN') r.wins++;
    if (t.outcome === 'LOSS') r.losses++;
    by.set(key, r);
  }
  return [...by.values()].sort((a, b) => a.month.localeCompare(b.month)).map((r) => {
    const graded = r.wins + r.losses;
    return { ...r, graded, winRate: graded ? Math.round((r.wins / graded) * 1000) / 10 : null };
  });
}

/** Wins over graded, 0…100 with one decimal; null over zero graded trades. */
export function winRate(trades: Graded[]): { rate: number | null; wins: number; losses: number; graded: number } {
  const wins = trades.filter((t) => t.outcome === 'WIN').length;
  const losses = trades.filter((t) => t.outcome === 'LOSS').length;
  const graded = wins + losses;
  return { rate: graded ? Math.round((wins / graded) * 1000) / 10 : null, wins, losses, graded };
}

/** A published signal's outcome: its own flag first, else the sign of its result. */
export function signalOutcome(s: { isWin: boolean | null; pctGain: number | null; closedAt: number | null }): Outcome {
  if (s.closedAt == null && s.isWin == null) return 'OPEN';
  if (s.isWin === true) return 'WIN';
  if (s.isWin === false) return s.pctGain === 0 ? 'BREAKEVEN' : 'LOSS';
  if (s.pctGain == null) return 'UNKNOWN';
  return s.pctGain > 0 ? 'WIN' : s.pctGain < 0 ? 'LOSS' : 'BREAKEVEN';
}

const FA_MONTHS = ['ژانویه', 'فوریه', 'مارس', 'آوریل', 'مه', 'ژوئن', 'ژوئیه', 'اوت', 'سپتامبر', 'اکتبر', 'نوامبر', 'دسامبر'];
const EN_MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
export function monthLabel(key: string, lang: 'fa' | 'en'): string {
  const [y, m] = key.split('-');
  const i = Number(m) - 1;
  if (!y || !(i >= 0 && i < 12)) return key;
  return lang === 'fa' ? `${FA_MONTHS[i]} ${y}` : `${EN_MONTHS[i]} ${y}`;
}
