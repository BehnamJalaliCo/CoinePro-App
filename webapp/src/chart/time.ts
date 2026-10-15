// The time axis and crosshair label in the chart's time zone, Latin digits and English month
// names (figures are Latin, as on TradingView). Daily and longer bars keep their UTC date.
// TradingView's «Scales and lines → Time scale» choices: day of week, date format, 12/24 hours.
import type { DeepPartial, TimeChartOptions } from 'lightweight-charts';
import { intervalSeconds } from '../data/intervals';

export type DateFormat = 'dd MMM \'yy' | 'MMM dd, yyyy' | 'yyyy-MM-dd' | 'dd/MM/yyyy' | 'MM/dd/yyyy';
export interface TimeLook { hour12?: boolean; dayOfWeek?: boolean; dateFormat?: DateFormat }

export const DATE_FORMATS: DateFormat[] = ['dd MMM \'yy', 'MMM dd, yyyy', 'yyyy-MM-dd', 'dd/MM/yyyy', 'MM/dd/yyyy'];

/** 'exchange' is the instruments' own clock: UTC for every market this app draws. */
export const zoneOf = (tz: string) => (!tz || tz === 'exchange' ? 'UTC' : tz);

function safeFormat(tz: string, o: Intl.DateTimeFormatOptions, locale = 'en-US'): Intl.DateTimeFormat {
  try { return new Intl.DateTimeFormat(locale, { timeZone: tz, ...o }); } catch { return new Intl.DateTimeFormat(locale, { timeZone: 'UTC', ...o }); }
}

/** Formats a date (seconds) the way the reader chose, in a zone. */
export function dateText(t: number, tz: string, look: TimeLook = {}): string {
  const d = new Date(t * 1000);
  const parts = safeFormat(zoneOf(tz), { year: 'numeric', month: 'short', day: '2-digit', weekday: 'short' }).formatToParts(d);
  const num = safeFormat(zoneOf(tz), { year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(d);
  const get = (ps: Intl.DateTimeFormatPart[], k: string) => ps.find((p) => p.type === k)?.value ?? '';
  const dd = get(num, 'day'); const mm = get(num, 'month'); const yyyy = get(num, 'year');
  const mon = get(parts, 'month'); const wd = get(parts, 'weekday');
  let s: string;
  switch (look.dateFormat ?? 'dd MMM \'yy') {
    case 'MMM dd, yyyy': s = `${mon} ${dd}, ${yyyy}`; break;
    case 'yyyy-MM-dd': s = `${yyyy}-${mm}-${dd}`; break;
    case 'dd/MM/yyyy': s = `${dd}/${mm}/${yyyy}`; break;
    case 'MM/dd/yyyy': s = `${mm}/${dd}/${yyyy}`; break;
    default: s = `${dd} ${mon} '${yyyy.slice(2)}`;
  }
  return look.dayOfWeek === false ? s : `${wd} ${s}`;
}

/** A clock time (seconds) in a zone, 24-hour or 12-hour. */
export function clockText(t: number, tz: string, hour12 = false): string {
  const f = safeFormat(zoneOf(tz), { hour: '2-digit', minute: '2-digit', hour12 }, hour12 ? 'en-US' : 'en-GB');
  return f.format(new Date(t * 1000));
}

/** The bar's time as the crosshair label writes it: the date, and the clock on intraday bars. */
export function barTimeText(t: number, timezone: string, interval: string, look: TimeLook = {}): string {
  const daily = intervalSeconds(interval) >= 86400;
  const tz = daily ? 'UTC' : timezone;
  return daily ? dateText(t, tz, look) : `${dateText(t, tz, look)}  ${clockText(t, tz, look.hour12)}`;
}

export function timeOptions(timezone: string, interval: string, look: TimeLook = {}): DeepPartial<TimeChartOptions> {
  const daily = intervalSeconds(interval) >= 86400;
  const tz = daily ? 'UTC' : zoneOf(timezone);
  const time = safeFormat(tz, { hour: '2-digit', minute: '2-digit', hour12: Boolean(look.hour12) }, look.hour12 ? 'en-US' : 'en-GB');
  const day = safeFormat(tz, { day: 'numeric' });
  const month = safeFormat(tz, { month: 'short' });
  const year = safeFormat(tz, { year: 'numeric' });
  const at = (t: unknown) => new Date((typeof t === 'number' ? t : 0) * 1000);
  return {
    localization: { timeFormatter: (t: unknown) => barTimeText(typeof t === 'number' ? t : 0, timezone, interval, look) },
    timeScale: {
      tickMarkFormatter: (t: unknown, type: number) => {
        const d = at(t);
        if (type === 0) return year.format(d);
        if (type === 1) return month.format(d);
        if (type === 2) return day.format(d);
        return time.format(d);
      },
    },
  };
}
