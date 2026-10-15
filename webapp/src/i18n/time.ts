// Times in prose: «۵ دقیقه پیش» with Persian digits (a count in a sentence), and clock times in
// Latin digits (a figure), in the reader's own time zone.
const FA_DIGITS = '۰۱۲۳۴۵۶۷۸۹';
export const faNum = (n: number | string) => String(n).replace(/\d/g, (d) => FA_DIGITS[Number(d)]!);

export function faAgo(ms: number, now: number = Date.now()): string {
  const s = Math.max(0, Math.round((now - ms) / 1000));
  if (s < 60) return 'همین حالا';
  const m = Math.round(s / 60);
  if (m < 60) return `${faNum(m)} دقیقه پیش`;
  const h = Math.round(m / 60);
  if (h < 24) return `${faNum(h)} ساعت پیش`;
  const d = Math.round(h / 24);
  return `${faNum(d)} روز پیش`;
}

export function clock(ms: number): string {
  const d = new Date(ms);
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}

const FA_WEEKDAYS = ['یکشنبه', 'دوشنبه', 'سه‌شنبه', 'چهارشنبه', 'پنجشنبه', 'جمعه', 'شنبه'];
export function faDay(ms: number): string {
  const d = new Date(ms);
  const date = new Intl.DateTimeFormat('fa-IR-u-ca-persian', { day: 'numeric', month: 'long' }).format(d);
  return `${FA_WEEKDAYS[d.getDay()]} ${date}`;
}
