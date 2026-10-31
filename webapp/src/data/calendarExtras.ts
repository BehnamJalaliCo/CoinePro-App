// The economic calendar's search and «افزودن به تقویم» (OLD feature/calendar CalendarFilter,
// EconomicCalendarScreen addToCalendar, and the old web terminal's .ics file).
import type { CalendarEvent } from './intel';

/** A currency's country, in English and Persian, so «آمریکا», «japan» or «UK» find its releases. */
const COUNTRY: Record<string, string[]> = {
  USD: ['united states', 'usa', 'us', 'america', 'آمریکا', 'امریکا', 'ایالات متحده'],
  EUR: ['euro area', 'eurozone', 'europe', 'eu', 'اروپا', 'منطقه‌ی یورو', 'حوزه‌ی یورو'],
  GBP: ['united kingdom', 'uk', 'britain', 'england', 'انگلیس', 'بریتانیا'],
  JPY: ['japan', 'ژاپن'], CHF: ['switzerland', 'سوئیس'], AUD: ['australia', 'استرالیا'],
  NZD: ['new zealand', 'نیوزیلند'], CAD: ['canada', 'کانادا'], CNY: ['china', 'چین'], CNH: ['china', 'چین'],
  DEM: ['germany', 'آلمان'], INR: ['india', 'هند'], TRY: ['turkey', 'ترکیه'], ZAR: ['south africa', 'آفریقای جنوبی'],
};

/** Whether an event matches the typed text: its title, its currency, its country (code or name). */
export function calendarMatches(e: CalendarEvent, query: string): boolean {
  const q = query.trim().toLowerCase();
  if (!q) return true;
  const hay = [e.title, e.currency, e.country ?? '', ...(COUNTRY[e.currency.toUpperCase()] ?? []), ...(e.country ? COUNTRY[e.country.toUpperCase()] ?? [] : [])].join(' | ').toLowerCase();
  return hay.includes(q);
}

/** Half an hour: the window a release actually moves a market in. */
const EVENT_MS = 30 * 60 * 1000;
const stamp = (ms: number) => new Date(ms).toISOString().replace(/[-:]/g, '').replace(/\.\d{3}/, '');
const escape = (v: string) => v.replace(/\\/g, '\\\\').replace(/;/g, '\;').replace(/,/g, '\\,').replace(/\n/g, '\\n');

/** The release as an iCalendar event, every desktop calendar and the phone's own open it. */
export function icsFor(e: CalendarEvent, now: number = Date.now()): string {
  const notes = [e.country, e.currency].filter((x, i, a) => x && a.indexOf(x) === i).join(' · ');
  let h = 0;
  for (const ch of e.title) h = (h * 31 + ch.charCodeAt(0)) | 0;
  return [
    'BEGIN:VCALENDAR', 'VERSION:2.0', 'PRODID:-//CoinePro//Pro Chart//FA',
    'BEGIN:VEVENT', `UID:${e.time}-${h}@pro-chart.com`, `DTSTAMP:${stamp(now)}`,
    `DTSTART:${stamp(e.time)}`, `DTEND:${stamp(e.time + EVENT_MS)}`, `SUMMARY:${escape(e.title)}`, `DESCRIPTION:${escape(notes)}`,
    'END:VEVENT', 'END:VCALENDAR',
  ].join('\r\n');
}

/** Saves the release as `event.ics`. */
export function downloadIcs(e: CalendarEvent): void {
  const a = document.createElement('a');
  a.href = URL.createObjectURL(new Blob([icsFor(e)], { type: 'text/calendar' }));
  a.download = 'event.ics'; a.click();
  setTimeout(() => URL.revokeObjectURL(a.href), 4000);
}
