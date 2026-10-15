// Interval ids follow TradingView's own notation: minutes as a number (1, 45, 240), then days,
// weeks and months with their letter (1D, 3D, 1W, 2W, 1M, 3M, 12M). Any count is an interval: the
// data layer builds the ones a backend lacks by folding a finer one (candles.ts), so a custom
// "7m" or "6h" behaves like a native one.
export type IntervalUnit = 'm' | 'D' | 'W' | 'M';
export interface ParsedInterval { n: number; unit: IntervalUnit }
export interface IntervalDef { id: string; seconds: number; label: string }

/** The kinds the interval menu groups by, in TradingView's order. */
export type IntervalGroup = 'minutes' | 'hours' | 'days';

const ID_RE = /^(\d{1,5})([DWM]?)$/;

/** '45' → 45 minutes, '3D' → 3 days, '12M' → 12 months; null for anything else. */
export function parseIntervalId(id: string | null | undefined): ParsedInterval | null {
  const m = ID_RE.exec(String(id ?? '').trim());
  if (!m) return null;
  const n = Number(m[1]);
  const unit = (m[2] || 'm') as IntervalUnit;
  if (!Number.isInteger(n) || n < 1) return null;
  if (unit === 'm' && n > 1440 * 7) return null;
  if (unit !== 'm' && n > 365) return null;
  return { n, unit };
}

export function intervalId(n: number, unit: IntervalUnit): string {
  return unit === 'm' ? String(n) : `${n}${unit}`;
}

/** The standard menu, TradingView's plus the old Pro-Chart's derived ones (6h, 8h, 12h, 3D). */
const STANDARD = ['1', '2', '3', '5', '10', '15', '30', '45', '60', '120', '180', '240', '360', '480', '720', '1D', '3D', '1W', '1M', '3M', '6M', '12M'];

export function intervalSeconds(id: string): number {
  const p = parseIntervalId(id);
  if (!p) return 3600;
  if (p.unit === 'm') return p.n * 60;
  if (p.unit === 'D') return p.n * 86400;
  if (p.unit === 'W') return p.n * 604800;
  return p.n * 2592000;
}

/** The short label the top bar and the address use: 1m, 45m, 4h, 90m, 1D, 2W, 3M. */
export function intervalLabel(id: string): string {
  const p = parseIntervalId(id);
  if (!p) return id;
  if (p.unit !== 'm') return `${p.n}${p.unit}`;
  if (p.n >= 60 && p.n % 60 === 0) return `${p.n / 60}h`;
  return `${p.n}m`;
}

const FA_DIGITS = '۰۱۲۳۴۵۶۷۸۹';
const fa = (n: number) => String(n).replace(/\d/g, (d) => FA_DIGITS[Number(d)]!);

/** The menu's wording: «۴۵ دقیقه» / "45 minutes", «۳ ماه» / "3 months". */
export function intervalLongLabel(id: string, lang: 'fa' | 'en'): string {
  const p = parseIntervalId(id);
  if (!p) return id;
  let n = p.n;
  let unit: 'minute' | 'hour' | 'day' | 'week' | 'month' = p.unit === 'D' ? 'day' : p.unit === 'W' ? 'week' : p.unit === 'M' ? 'month' : 'minute';
  if (unit === 'minute' && n >= 60 && n % 60 === 0) { n /= 60; unit = 'hour'; }
  if (lang === 'fa') {
    const word = { minute: 'دقیقه', hour: 'ساعت', day: 'روز', week: 'هفته', month: 'ماه' }[unit];
    return `${fa(n)} ${word}`;
  }
  return `${n} ${unit}${n === 1 ? '' : 's'}`;
}

export function intervalGroup(id: string): IntervalGroup {
  const p = parseIntervalId(id);
  if (!p || p.unit !== 'm') return 'days';
  return p.n >= 60 && p.n % 60 === 0 ? 'hours' : 'minutes';
}

/** Orders interval ids from the shortest to the longest. */
export function byLength(a: string, b: string): number {
  return intervalSeconds(a) - intervalSeconds(b);
}

export const INTERVALS: IntervalDef[] = STANDARD.map((id) => ({ id, seconds: intervalSeconds(id), label: intervalLabel(id) }));

/** Whether an id is one of the menu's own (a custom one is anything else that parses). */
export const isStandardInterval = (id: string) => STANDARD.includes(id);

/**
 * What a reader types or a link carries → an interval id, or null.
 *   '45' '45m' 'M45' → '45'      '3h' 'H3' → '180'      '1d' 'D' 'D1' '3D' → '1D' / '3D'
 *   '1W' 'W' 'W1' '2w' → '1W' / '2W'      '1M' (capital) 'MN' '3M' '1mo' → months
 * A lower-case 'm' after a number is minutes and a capital 'M' is months, as on TradingView.
 */
export function parseIntervalInput(raw: string | null | undefined): string | null {
  if (raw == null) return null;
  const s = String(raw).trim().replace(/\s+/g, '');
  if (!s) return null;
  if (parseIntervalId(s)) return normalise(s);
  let m = /^(\d{1,4})(m|min|mins|minutes?)$/.exec(s);
  if (m) return ok(Number(m[1]), 'm');
  m = /^(\d{1,3})(h|hr|hrs|hours?|H)$/.exec(s);
  if (m) return ok(Number(m[1]) * 60, 'm');
  m = /^(\d{1,3})(d|days?)$/i.exec(s);
  if (m) return ok(Number(m[1]), 'D');
  m = /^(\d{1,3})(w|wks?|weeks?)$/i.exec(s);
  if (m) return ok(Number(m[1]), 'W');
  m = /^(\d{1,3})(mo|mos|months?|MN)$/i.exec(s);
  if (m) return ok(Number(m[1]), 'M');
  m = /^([MHDW]|MN)(\d{1,3})$/i.exec(s);
  if (m) {
    const n = Number(m[2]);
    const u = m[1]!.toUpperCase();
    if (u === 'M') return ok(n, 'm');
    if (u === 'H') return ok(n * 60, 'm');
    if (u === 'D') return ok(n, 'D');
    if (u === 'W') return ok(n, 'W');
    return ok(n, 'M');
  }
  if (/^d$/i.test(s)) return '1D';
  if (/^w$/i.test(s)) return '1W';
  if (s === 'M' || /^mn$/i.test(s)) return '1M';
  return null;
}

function ok(n: number, unit: IntervalUnit): string | null {
  if (!Number.isInteger(n) || n < 1) return null;
  const id = intervalId(n, unit);
  return parseIntervalId(id) ? id : null;
}

/** '1440' → '1D', '10080' → '1W': a count of minutes that is a whole day or week reads as one. */
function normalise(id: string): string {
  const p = parseIntervalId(id)!;
  if (p.unit === 'm' && p.n >= 1440 && p.n % 10080 === 0) return intervalId(p.n / 10080, 'W');
  if (p.unit === 'm' && p.n >= 1440 && p.n % 1440 === 0) return intervalId(p.n / 1440, 'D');
  return intervalId(p.n, p.unit);
}
