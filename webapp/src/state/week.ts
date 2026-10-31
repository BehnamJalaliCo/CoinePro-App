// «هفته‌ی من» — what the reader did with their own week (OLD core/common MyWeek.kt, WeekStart.kt).
//
// The week opens on the reader's own first day — Saturday in Iran and across the Gulf, Monday
// elsewhere — at midnight in the browser's zone, and runs seven days. Every number is a thing the
// reader did that this browser recorded: trades closed (the journal and the paper account), alerts
// that fired and those still waiting, replay sessions finished. Below MINIMUM_TRADES closed trades
// there is no win rate at all, only the count: a percentage over three trades is three coin flips
// in a confident font.

/** How many closed trades a win rate needs before it is printed. */
export const MINIMUM_TRADES = 5;

/** The zones whose week begins on Saturday (Iran, Afghanistan and the Gulf states). */
export const SATURDAY_ZONES = ['Asia/Tehran', 'Asia/Kabul', 'Asia/Baghdad', 'Asia/Bahrain', 'Asia/Dubai', 'Asia/Kuwait', 'Asia/Muscat', 'Asia/Qatar', 'Asia/Riyadh'];

/** The browser's own zone, the closest thing to a region the page has. */
export function readerZone(): string {
  try { return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'; } catch { return 'UTC'; }
}

/** 6 (Saturday) or 1 (Monday), in `Date.getDay()` numbering. */
export const weekStartDay = (zone: string) => (SATURDAY_ZONES.includes(zone) ? 6 : 1);

/** The instant the week holding `now` opened, in milliseconds (the browser's midnight). */
export function weekStart(now: number, zone: string = readerZone()): number {
  const d = new Date(now);
  d.setHours(0, 0, 0, 0);
  const back = (d.getDay() - weekStartDay(zone) + 7) % 7;
  d.setDate(d.getDate() - back);
  return d.getTime();
}

export interface WeekTrade { closedAt: number; won: boolean }

export interface WeekSummary {
  from: number;
  to: number;
  trades: number;
  won: number;
  /** Null below MINIMUM_TRADES: the count is the claim, and no percentage is shown anywhere. */
  winPercent: number | null;
  alertsFired: number;
  alertsArmed: number;
  practiceSessions: number;
  /** Nothing happened: the card says a sentence instead of four zeros. */
  empty: boolean;
}

export function myWeek(input: { now: number; zone?: string; trades?: WeekTrade[]; alertFiredAt?: number[]; alertsArmed?: number; practiceFinishedAt?: number[] }): WeekSummary {
  const from = weekStart(input.now, input.zone);
  const end = new Date(from);
  end.setDate(end.getDate() + 7);
  const to = end.getTime();
  const inside = (t: number) => t >= from && t < to;
  const trades = (input.trades ?? []).filter((x) => inside(x.closedAt));
  const won = trades.filter((x) => x.won).length;
  const alertsFired = (input.alertFiredAt ?? []).filter(inside).length;
  const practiceSessions = (input.practiceFinishedAt ?? []).filter(inside).length;
  return {
    from, to, trades: trades.length, won,
    winPercent: trades.length >= MINIMUM_TRADES ? (won * 100) / trades.length : null,
    alertsFired, alertsArmed: input.alertsArmed ?? 0, practiceSessions,
    empty: trades.length === 0 && alertsFired === 0 && practiceSessions === 0,
  };
}

/**
 * The week's trades from the journal and the paper account. A journal row with no result, or a
 * break-even one, is not counted (the app does not decide a trade the reader has not closed the
 * books on); a paper trade already copied into the journal (`paper-<id>`) is counted once.
 */
export function weekTrades(journal: { id: string; pnl: number | null; createdAt: number }[], paper: { id: string; pnl: number; closedAt: number }[]): WeekTrade[] {
  const out: WeekTrade[] = [];
  const copied = new Set<string>();
  for (const e of journal) {
    if (e.id.startsWith('paper-')) copied.add(e.id.slice(6));
    if (e.pnl == null || e.pnl === 0 || !Number.isFinite(e.pnl)) continue;
    out.push({ closedAt: e.createdAt, won: e.pnl > 0 });
  }
  for (const h of paper) {
    if (copied.has(h.id) || !Number.isFinite(h.pnl) || h.pnl === 0) continue;
    out.push({ closedAt: h.closedAt, won: h.pnl > 0 });
  }
  return out;
}
