// The bars on the chart, as a file the reader owns (OLD feature/chart ChartExport.kt, core/export
// Csv.kt and Numbers.cell). Every timestamp gets two columns: the ISO instant a spreadsheet sorts and
// subtracts, and the Solar Hijri date (zero-padded, Latin digits — the one Jalali form whose text
// order is its calendar order) a Persian trader reconciles against their notes, with the clock in its
// own column. The first row says what the numbers are; the byte-order mark is what Persian Excel
// needs; volume is blank, not nought, on a feed that carries none. What is exported is what is on
// the chart: a replay exports the bars the reader can see, never the answer sheet.

/** Gregorian → Solar Hijri (the jalaali arithmetic: 33-year cycles, valid for -61 … 3177 AH). */
export function toJalali(gy: number, gm: number, gd: number): { jy: number; jm: number; jd: number } {
  const breaks = [-61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210, 1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178];
  /** Leap information and the Gregorian date of Farvardin 1 for Jalali year jy. */
  const jalCal = (jy: number) => {
    const gyr = jy + 621;
    let leapJ = -14;
    let jp = breaks[0]!;
    let jump = 0;
    for (let i = 1; i < breaks.length; i++) {
      const jm = breaks[i]!;
      jump = jm - jp;
      if (jy < jm) break;
      leapJ += Math.trunc(jump / 33) * 8 + Math.trunc((jump % 33) / 4);
      jp = jm;
    }
    let n = jy - jp;
    leapJ += Math.trunc(n / 33) * 8 + Math.trunc(((n % 33) + 3) / 4);
    if (jump % 33 === 4 && jump - n === 4) leapJ += 1;
    const leapG = Math.trunc(gyr / 4) - Math.trunc((Math.trunc(gyr / 100) + 1) * 3 / 4) - 150;
    const march = 20 + leapJ - leapG;
    if (jump - n < 6) n = n - jump + Math.trunc((jump + 4) / 33) * 33;
    let leap = (((n + 1) % 33) - 1) % 4;
    if (leap === -1) leap = 4;
    return { leap, gy: gyr, march };
  };
  const dayOf = (y: number, m: number, d: number) => {
    const a = Math.trunc((14 - m) / 12);
    const yy = y + 4800 - a;
    const mm = m + 12 * a - 3;
    return d + Math.trunc((153 * mm + 2) / 5) + 365 * yy + Math.trunc(yy / 4) - Math.trunc(yy / 100) + Math.trunc(yy / 400) - 32045;
  };
  const jdn = dayOf(gy, gm, gd);
  let jy = gy - 621;
  const r = jalCal(jy);
  const jdn1f = dayOf(r.gy, 3, r.march);
  let k = jdn - jdn1f;
  if (k >= 0) {
    if (k <= 185) return { jy, jm: 1 + Math.trunc(k / 31), jd: (k % 31) + 1 };
    k -= 186;
  } else {
    jy -= 1;
    k += 179;
    if (r.leap === 1) k += 1;
  }
  return { jy, jm: 7 + Math.trunc(k / 30), jd: (k % 30) + 1 };
}

const pad = (n: number, w = 2) => String(n).padStart(w, '0');

/** The local calendar date and clock of an instant in a time zone (the reader's own). */
export function localParts(epochSeconds: number, timeZone: string): { y: number; m: number; d: number; hh: string; mm: string } {
  try {
    const f = new Intl.DateTimeFormat('en-GB', { timeZone, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' });
    const p = Object.fromEntries(f.formatToParts(new Date(epochSeconds * 1000)).map((x) => [x.type, x.value]));
    return { y: Number(p.year), m: Number(p.month), d: Number(p.day), hh: p.hour ?? '00', mm: p.minute ?? '00' };
  } catch {
    const d = new Date(epochSeconds * 1000);
    return { y: d.getUTCFullYear(), m: d.getUTCMonth() + 1, d: d.getUTCDate(), hh: pad(d.getUTCHours()), mm: pad(d.getUTCMinutes()) };
  }
}

/** `1404/10/11`, zero-padded, Latin digits. */
export function jalaliDate(epochSeconds: number, timeZone: string): string {
  const p = localParts(epochSeconds, timeZone);
  const j = toJalali(p.y, p.m, p.d);
  return `${pad(j.jy, 4)}/${pad(j.jm)}/${pad(j.jd)}`;
}

/** A number as a cell: up to eight decimals, no grouping, no trailing zeros; blank when absent. */
export function cell(v: number | null | undefined): string {
  if (v == null || !Number.isFinite(v)) return '';
  return v.toFixed(8).replace(/\.?0+$/, '') || '0';
}

const field = (v: string) => `"${v.replace(/"/g, '""')}"`;
const line = (vs: string[]) => vs.map(field).join(',');

export const CSV_HEADERS = {
  fa: ['زمان (ISO)', 'تاریخ (شمسی)', 'ساعت', 'باز', 'بیشترین', 'کمترین', 'بسته', 'حجم'],
  en: ['Time (ISO)', 'Date (Solar Hijri)', 'Clock', 'Open', 'High', 'Low', 'Close', 'Volume'],
};

export interface CsvBar { t: number; o: number; h: number; l: number; c: number; v: number }

/** The bars as CSV text: the provenance row, the headings, one row per bar oldest first, CRLF, with the BOM. */
export function candleCsv(bars: CsvBar[], o: { symbol: string; interval: string; source: string; timeZone: string; en: boolean }): string {
  const noVolume = !bars.some((b) => Number.isFinite(b.v) && b.v > 0);
  const rows = [...bars].sort((a, b) => a.t - b.t).map((b) => {
    const p = localParts(b.t, o.timeZone);
    const j = toJalali(p.y, p.m, p.d);
    return [new Date(b.t * 1000).toISOString().replace('.000Z', 'Z'), `${pad(j.jy, 4)}/${pad(j.jm)}/${pad(j.jd)}`, `${p.hh}:${p.mm}`, cell(b.o), cell(b.h), cell(b.l), cell(b.c), noVolume ? '' : cell(b.v)];
  });
  const provenance = `# ${o.symbol} · ${o.interval} · ${o.source || (o.en ? 'no source' : 'بدون منبع')}`;
  return '﻿' + [line([provenance]), line(o.en ? CSV_HEADERS.en : CSV_HEADERS.fa), ...rows.map(line)].join('\r\n') + '\r\n';
}

/** `BTCUSDT-1h-1405-08-09.csv`: Latin digits and hyphens, today's Solar Hijri date. */
export function csvFileName(symbol: string, interval: string, timeZone: string, now = Date.now()): string {
  const safe = (s: string) => s.replace(/[^A-Za-z0-9.-]/g, '-');
  const p = localParts(Math.floor(now / 1000), timeZone);
  const j = toJalali(p.y, p.m, p.d);
  return `${safe(symbol)}-${safe(interval)}-${pad(j.jy, 4)}-${pad(j.jm)}-${pad(j.jd)}.csv`;
}
