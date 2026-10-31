// Records and reports, as the phone computes them: the portfolio's full report (feature/portfolio
// PortfolioMetrics.kt), the month-by-month buckets in the Solar Hijri calendar (core/portfolio
// PortfolioMath.byMonth, core/common JalaliDate.kt), the files a reader takes away (core/export
// Csv.kt / Workbook.kt, feature/portfolio TradeExport.kt, core/backtest BacktestExport.kt), the
// journal's filter and figures (feature/journal JournalFilter.kt, core/journal JournalStats.kt) and
// the signal record's evidence (core/signals SignalPerformance.kt). Pure functions only: every figure
// here can be added up on paper, which is what the tests do.
import { faNum } from '../i18n/time';
import type { PaperTrade } from '../state/paper';
import type { JournalEntry } from '../state/journal';
import type { BtResult } from './backtestEngine';

// ── Solar Hijri dates (JalaliDate.kt) ───────────────────────────────────────────────────────────
export const JALALI_MONTHS = ['فروردین', 'اردیبهشت', 'خرداد', 'تیر', 'مرداد', 'شهریور', 'مهر', 'آبان', 'آذر', 'دی', 'بهمن', 'اسفند'];
export const JALALI_MONTHS_EN = ['Farvardin', 'Ordibehesht', 'Khordad', 'Tir', 'Mordad', 'Shahrivar', 'Mehr', 'Aban', 'Azar', 'Dey', 'Bahman', 'Esfand'];
const BREAKS = [-61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210, 1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178];
export interface Jalali { year: number; month: number; day: number }

const idiv = (a: number, b: number) => Math.trunc(a / b);
function calculate(jy: number): { leap: number; gy: number; march: number } | null {
  if (!(jy >= BREAKS[0]! && jy < BREAKS[BREAKS.length - 1]!)) return null;
  const gy = jy + 621;
  let leapJ = -14; let prev = BREAKS[0]!; let jump = 0;
  for (let i = 1; i < BREAKS.length; i++) {
    const cur = BREAKS[i]!;
    jump = cur - prev;
    if (jy < cur) break;
    leapJ += idiv(jump, 33) * 8 + idiv(jump % 33, 4);
    prev = cur;
  }
  let n = jy - prev;
  leapJ += idiv(n, 33) * 8 + idiv((n % 33) + 3, 4);
  if (jump % 33 === 4 && jump - n === 4) leapJ += 1;
  const leapG = idiv(gy, 4) - idiv((idiv(gy, 100) + 1) * 3, 4) - 150;
  const march = 20 + leapJ - leapG;
  if (jump - n < 6) n = n - jump + idiv(jump + 4, 33) * 33;
  let leap = (((n + 1) % 33) - 1) % 4;
  if (leap === -1) leap = 4;
  return { leap, gy, march };
}
const epochDay = (y: number, m: number, d: number) => Math.round(Date.UTC(y, m - 1, d) / 86_400_000);

/** The Solar Hijri date of a Gregorian calendar day; null outside the table (refused, not guessed). */
export function jalaliOf(gy: number, gm: number, gd: number): Jalali | null {
  let jy = gy - 621;
  const c = calculate(jy);
  if (!c) return null;
  let offset = epochDay(gy, gm, gd) - epochDay(c.gy, 3, c.march);
  if (offset >= 0) {
    if (offset <= 185) return { year: jy, month: 1 + idiv(offset, 31), day: (offset % 31) + 1 };
    offset -= 186;
  } else {
    jy -= 1; offset += 179;
    if (c.leap === 1) offset += 1;
  }
  return { year: jy, month: 7 + idiv(offset, 30), day: (offset % 30) + 1 };
}
/** The reader's own calendar day of an instant (local time zone), in Solar Hijri. */
export function jalaliAt(ms: number): Jalali | null {
  const d = new Date(ms);
  return Number.isFinite(d.getTime()) ? jalaliOf(d.getFullYear(), d.getMonth() + 1, d.getDate()) : null;
}
/** «۱۱ دی ۱۴۰۴» — prose, so Persian digits. */
export const jalaliLong = (j: Jalali) => `${faNum(j.day)} ${JALALI_MONTHS[j.month - 1]} ${faNum(j.year)}`;
/** «۱۱ دی». */
export const jalaliShort = (j: Jalali) => `${faNum(j.day)} ${JALALI_MONTHS[j.month - 1]}`;
/** `1404/10/11`: Latin digits, zero-padded — the one Jalali form that sorts (TradeExport.jalaliDate). */
export const jalaliSlash = (j: Jalali) => `${String(j.year).padStart(4, '0')}/${String(j.month).padStart(2, '0')}/${String(j.day).padStart(2, '0')}`;

// ── The full report (PortfolioMetrics.kt) ───────────────────────────────────────────────────────
/** One closed trade as the report reads it. Times in unix seconds. */
export interface ClosedTrade {
  id: string; symbol: string; side: 'buy' | 'sell';
  volume: number | null; entry: number | null; exit: number | null;
  openedAt: number | null; closedAt: number;
  gross: number | null; commission: number | null; swap: number | null; net: number | null;
  pips: number | null; reason: string; balanceAfter: number | null; liquidated: boolean; currency: string;
}
export interface EquityPoint { time: number; equity: number }
export interface DrawdownSpan { peakIndex: number; troughIndex: number; peakAt: number; troughAt: number; peakEquity: number; troughEquity: number; depth: number; depthPercent: number | null }
export interface RunUpSpan { troughIndex: number; peakIndex: number; troughAt: number; peakAt: number; troughEquity: number; peakEquity: number; height: number; heightPercent: number | null }
export interface DrawdownRun { startIndex: number; endIndex: number; startAt: number; endAt: number; trades: number; seconds: number; recovered: boolean }
export interface TradeMetrics {
  trades: number; wins: number; losses: number; scratches: number; net: number; grossWin: number; grossLoss: number;
  largestWin: number | null; largestLoss: number | null; averageHoldingSeconds: number | null; holdingSample: number;
  longestWinStreak: number; longestLossStreak: number; sharpe: number | null; sortino: number | null;
  equity: EquityPoint[]; equityIsBalance: boolean; drawdown: DrawdownSpan | null; runUp: RunUpSpan | null; longestDrawdown: DrawdownRun | null;
  winRate: number | null; profitFactor: number | null; expectancy: number | null; averageWin: number | null; averageLoss: number | null; winLossRatio: number | null;
}

const durationOf = (t: ClosedTrade) => (t.openedAt == null ? null : t.closedAt - t.openedAt);
const avg = (xs: number[]) => xs.reduce((n, x) => n + x, 0) / xs.length;

/** Per-trade Sharpe; the population deviation, like Sortino's divisor. Null below two returns or with no spread. */
export function sharpe(returns: number[], target = 0): number | null {
  if (returns.length < 2) return null;
  const mean = avg(returns);
  const dev = Math.sqrt(returns.reduce((n, r) => n + (r - mean) * (r - mean), 0) / returns.length);
  return dev <= 0 ? null : (mean - target) / dev;
}
/** Mean excess over the downside deviation, whose mean is over EVERY return (the trap PortfolioMetrics names). */
export function sortino(returns: number[], target = 0): number | null {
  if (returns.length < 2) return null;
  const mean = avg(returns);
  const shortfall = returns.reduce((n, r) => { const b = Math.min(r - target, 0); return n + b * b; }, 0);
  const down = Math.sqrt(shortfall / returns.length);
  return down <= 0 ? null : (mean - target) / down;
}
/** Peak to trough on the curve, never the largest losing trade. The percentage only on a balance curve. */
export function deepestDrawdown(curve: EquityPoint[], isBalance: boolean): DrawdownSpan | null {
  if (curve.length < 2) return null;
  let peakIndex = 0; let peak = curve[0]!.equity; let deepest: DrawdownSpan | null = null;
  curve.forEach((p, i) => {
    if (p.equity > peak) { peak = p.equity; peakIndex = i; }
    const fall = peak - p.equity;
    if (fall > 0 && fall > (deepest?.depth ?? 0)) {
      deepest = { peakIndex, troughIndex: i, peakAt: curve[peakIndex]!.time, troughAt: p.time, peakEquity: peak, troughEquity: p.equity, depth: fall, depthPercent: isBalance && Math.abs(peak) > 0 ? (fall / Math.abs(peak)) * 100 : null };
    }
  });
  return deepest;
}
/** The mirror: trough to peak, the percentage against the trough, on a balance curve only. */
export function highestRunUp(curve: EquityPoint[], isBalance: boolean): RunUpSpan | null {
  if (curve.length < 2) return null;
  let troughIndex = 0; let trough = curve[0]!.equity; let best: RunUpSpan | null = null;
  curve.forEach((p, i) => {
    if (p.equity < trough) { trough = p.equity; troughIndex = i; }
    const rise = p.equity - trough;
    if (rise > 0 && rise > (best?.height ?? 0)) {
      best = { troughIndex, peakIndex: i, troughAt: curve[troughIndex]!.time, peakAt: p.time, troughEquity: trough, peakEquity: p.equity, height: rise, heightPercent: isBalance && trough > 0 ? (rise / trough) * 100 : null };
    }
  });
  return best;
}
/** The longest stretch under a previous high, in trades; a run still open at the end counts to the last trade. */
export function longestDrawdown(curve: EquityPoint[]): DrawdownRun | null {
  if (curve.length < 2) return null;
  let peakIndex = 0; let peak = curve[0]!.equity; let under = false; let longest: DrawdownRun | null = null;
  const close = (end: number, recovered: boolean) => {
    const run = { startIndex: peakIndex, endIndex: end, startAt: curve[peakIndex]!.time, endAt: curve[end]!.time, trades: end - peakIndex, seconds: curve[end]!.time - curve[peakIndex]!.time, recovered };
    if (run.trades > (longest?.trades ?? 0)) longest = run;
  };
  for (let i = 1; i < curve.length; i++) {
    if (curve[i]!.equity >= peak) { if (under) close(i, true); peak = curve[i]!.equity; peakIndex = i; under = false; } else under = true;
  }
  if (under) close(curve.length - 1, false);
  return longest;
}

/** The curve a report draws: the broker's balance when every trade carries one, else profit from zero. */
export function equityCurve(ordered: ClosedTrade[]): { curve: EquityPoint[]; balanced: boolean } {
  const balanced = ordered.length > 0 && ordered.every((t) => t.balanceAfter != null);
  if (balanced) return { curve: ordered.map((t) => ({ time: t.closedAt, equity: t.balanceAfter! })), balanced };
  let run = 0;
  return { curve: ordered.map((t) => { run += t.net ?? 0; return { time: t.closedAt, equity: run }; }), balanced: false };
}

const EMPTY_METRICS: TradeMetrics = {
  trades: 0, wins: 0, losses: 0, scratches: 0, net: 0, grossWin: 0, grossLoss: 0, largestWin: null, largestLoss: null, averageHoldingSeconds: null, holdingSample: 0,
  longestWinStreak: 0, longestLossStreak: 0, sharpe: null, sortino: null, equity: [], equityIsBalance: false, drawdown: null, runUp: null, longestDrawdown: null,
  winRate: null, profitFactor: null, expectancy: null, averageWin: null, averageLoss: null, winLossRatio: null,
};

/** The whole report over a window of closed trades, read in close order. */
export function tradeMetrics(trades: ClosedTrade[], target = 0): TradeMetrics {
  if (!trades.length) return EMPTY_METRICS;
  const ordered = [...trades].sort((a, b) => a.closedAt - b.closedAt);
  let wins = 0; let losses = 0; let scratches = 0; let grossWin = 0; let grossLoss = 0;
  let largestWin: number | null = null; let largestLoss: number | null = null;
  let held = 0; let heldN = 0; let cw = 0; let cl = 0; let lw = 0; let ll = 0;
  for (const t of ordered) {
    const p = t.net ?? 0;
    if (p > 0) { wins++; grossWin += p; if (largestWin == null || p > largestWin) largestWin = p; cw++; cl = 0; }
    else if (p < 0) { losses++; grossLoss += -p; if (largestLoss == null || p < largestLoss) largestLoss = p; cl++; cw = 0; }
    // A scratch breaks both runs.
    else { scratches++; cw = 0; cl = 0; }
    lw = Math.max(lw, cw); ll = Math.max(ll, cl);
    const s = durationOf(t);
    if (s != null && s >= 0) { held += s; heldN++; }
  }
  const returns = ordered.map((t) => t.net ?? 0);
  const net = returns.reduce((n, x) => n + x, 0);
  const { curve, balanced } = equityCurve(ordered);
  const n = ordered.length;
  const averageWin = wins ? grossWin / wins : null;
  const averageLoss = losses ? grossLoss / losses : null;
  return {
    trades: n, wins, losses, scratches, net, grossWin, grossLoss, largestWin, largestLoss,
    averageHoldingSeconds: heldN ? Math.trunc(held / heldN) : null, holdingSample: heldN, longestWinStreak: lw, longestLossStreak: ll,
    sharpe: sharpe(returns, target), sortino: sortino(returns, target), equity: curve, equityIsBalance: balanced,
    drawdown: deepestDrawdown(curve, balanced), runUp: highestRunUp(curve, balanced), longestDrawdown: longestDrawdown(curve),
    winRate: wins + losses > 0 ? (wins / (wins + losses)) * 100 : null, profitFactor: grossLoss > 0 ? grossWin / grossLoss : null,
    expectancy: net / n, averageWin, averageLoss, winLossRatio: averageWin != null && averageLoss != null && averageLoss > 0 ? averageWin / averageLoss : null,
  };
}

export interface SymbolAttribution { symbol: string; trades: number; wins: number; losses: number; net: number; share: number | null; winRate: number | null }
/** Which instruments moved the result, largest mover first; the share is of the pool each drew from. */
export function attribution(trades: ClosedTrade[]): SymbolAttribution[] {
  if (!trades.length) return [];
  const gw = trades.reduce((n, t) => n + Math.max(t.net ?? 0, 0), 0);
  const gl = trades.reduce((n, t) => n - Math.min(t.net ?? 0, 0), 0);
  const by = new Map<string, ClosedTrade[]>();
  for (const t of trades) by.set(t.symbol, [...(by.get(t.symbol) ?? []), t]);
  return [...by.entries()].map(([symbol, rows]) => {
    const net = rows.reduce((n, t) => n + (t.net ?? 0), 0);
    const wins = rows.filter((t) => (t.net ?? 0) > 0).length; const losses = rows.filter((t) => (t.net ?? 0) < 0).length;
    return { symbol, trades: rows.length, wins, losses, net, share: net > 0 && gw > 0 ? (net / gw) * 100 : net < 0 && gl > 0 ? (-net / gl) * 100 : null, winRate: wins + losses > 0 ? (wins / (wins + losses)) * 100 : null };
  }).sort((a, b) => Math.abs(b.net) - Math.abs(a.net) || a.symbol.localeCompare(b.symbol));
}

export interface MonthlyPerformance { year: number; month: number; trades: number; net: number }
/** Solar Hijri months from the first to the last, quiet months included as zero-trade rows. */
export function byMonth(trades: ClosedTrade[]): MonthlyPerformance[] {
  const keyed = new Map<string, ClosedTrade[]>();
  for (const t of trades) { const j = jalaliAt(t.closedAt * 1000); if (!j) continue; const k = `${j.year}-${j.month}`; keyed.set(k, [...(keyed.get(k) ?? []), t]); }
  if (!keyed.size) return [];
  const keys = [...keyed.keys()].map((k) => k.split('-').map(Number) as [number, number]).sort((a, b) => a[0] - b[0] || a[1] - b[1]);
  const [fy, fm] = keys[0]!; const [ly, lm] = keys[keys.length - 1]!;
  const out: MonthlyPerformance[] = [];
  for (let y = fy, m = fm; y < ly || (y === ly && m <= lm);) {
    const rows = keyed.get(`${y}-${m}`) ?? [];
    out.push({ year: y, month: m, trades: rows.length, net: rows.reduce((n, t) => n + (t.net ?? 0), 0) });
    m++; if (m > 12) { m = 1; y++; }
  }
  return out;
}

/** Days, hours, minutes, seconds; negative clamps to zero (ReportFormatting.durationParts). */
export function durationParts(total: number): { days: number; hours: number; minutes: number; seconds: number } {
  const s = Math.max(0, Math.trunc(total));
  return { days: Math.trunc(s / 86_400), hours: Math.trunc((s % 86_400) / 3_600), minutes: Math.trunc((s % 3_600) / 60), seconds: s % 60 };
}
/** Two units at most, in the reader's language; Persian digits in Persian prose. */
export function durationLabel(total: number, fa: boolean): string {
  const p = durationParts(total); const n = (x: number) => (fa ? faNum(x) : String(x));
  if (p.days > 0) return fa ? `${n(p.days)} روز و ${n(p.hours)} ساعت` : `${p.days} d ${p.hours} h`;
  if (p.hours > 0) return fa ? `${n(p.hours)} ساعت و ${n(p.minutes)} دقیقه` : `${p.hours} h ${p.minutes} m`;
  if (p.minutes > 0) return fa ? `${n(p.minutes)} دقیقه` : `${p.minutes} m`;
  return fa ? `${n(p.seconds)} ثانیه` : `${p.seconds} s`;
}
/** A month cell: thousands folded to k, sign always shown, Latin digits. */
export function compactMoney(v: number): string {
  const m = Math.abs(v); const sign = v < 0 ? '-' : '+';
  const body = m >= 1_000 ? `${(m / 1_000).toLocaleString('en-US', { minimumFractionDigits: 1, maximumFractionDigits: 1 })}k` : m.toLocaleString('en-US', { maximumFractionDigits: 0 });
  return sign + body;
}
/** The running high of a series (EquityReportCurve.runningPeaks). */
export function runningPeaks(values: number[]): number[] {
  let peak = values[0] ?? 0;
  return values.map((v) => { if (v > peak) peak = v; return peak; });
}

// ── Files (core/export) ─────────────────────────────────────────────────────────────────────────
/** A number cell: Latin digits, no grouping, up to eight decimals, empty for an absent value (Numbers.cell). */
export function numberCell(v: number | null | undefined): string {
  if (v == null || !Number.isFinite(v)) return '';
  const s = v.toFixed(8).replace(/\.?0+$/, '');
  return s === '-0' ? '0' : s;
}
/** Comma-separated text with a byte-order mark, CRLF, every field quoted (Csv.build). */
export function buildCsv(header: string[], rows: string[][], preamble: string[][] = []): string {
  const line = (r: string[]) => r.map((v) => `"${v.replace(/"/g, '""')}"`).join(',');
  let out = '﻿';
  for (const r of preamble) out += line(r) + '\r\n';
  out += line(header);
  for (const r of rows) out += '\r\n' + line(r);
  return out + '\r\n';
}

const CRC_TABLE = (() => { const t = new Uint32Array(256); for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; } return t; })();
export function crc32(bytes: Uint8Array): number {
  let c = 0xffffffff;
  for (let i = 0; i < bytes.length; i++) c = CRC_TABLE[(c ^ bytes[i]!) & 0xff]! ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}
/** A zip of stored (uncompressed) parts — all an .xlsx needs, and no dependency. */
export function zipStore(parts: Array<{ name: string; data: Uint8Array }>): Uint8Array<ArrayBuffer> {
  const enc = new TextEncoder();
  // 2000-01-01 12:00, the fixed time Workbook.kt stamps on every part so a file is reproducible.
  const TIME = 12 << 11; const DATE = ((2000 - 1980) << 9) | (1 << 5) | 1;
  const chunks: Uint8Array[] = []; const central: Uint8Array[] = []; let offset = 0;
  for (const p of parts) {
    const name = enc.encode(p.name); const crc = crc32(p.data);
    const head = new DataView(new ArrayBuffer(30));
    head.setUint32(0, 0x04034b50, true); head.setUint16(4, 20, true); head.setUint16(6, 0, true); head.setUint16(8, 0, true);
    head.setUint16(10, TIME, true); head.setUint16(12, DATE, true); head.setUint32(14, crc, true);
    head.setUint32(18, p.data.length, true); head.setUint32(22, p.data.length, true); head.setUint16(26, name.length, true); head.setUint16(28, 0, true);
    chunks.push(new Uint8Array(head.buffer), name, p.data);
    const cd = new DataView(new ArrayBuffer(46));
    cd.setUint32(0, 0x02014b50, true); cd.setUint16(4, 20, true); cd.setUint16(6, 20, true); cd.setUint16(8, 0, true); cd.setUint16(10, 0, true);
    cd.setUint16(12, TIME, true); cd.setUint16(14, DATE, true); cd.setUint32(16, crc, true); cd.setUint32(20, p.data.length, true); cd.setUint32(24, p.data.length, true);
    cd.setUint16(28, name.length, true); cd.setUint16(30, 0, true); cd.setUint16(32, 0, true); cd.setUint16(34, 0, true); cd.setUint16(36, 0, true); cd.setUint32(38, 0, true); cd.setUint32(42, offset, true);
    central.push(new Uint8Array(cd.buffer), name);
    offset += 30 + name.length + p.data.length;
  }
  const cdSize = central.reduce((n, c) => n + c.length, 0);
  const end = new DataView(new ArrayBuffer(22));
  end.setUint32(0, 0x06054b50, true); end.setUint16(8, parts.length, true); end.setUint16(10, parts.length, true); end.setUint32(12, cdSize, true); end.setUint32(16, offset, true);
  const all = [...chunks, ...central, new Uint8Array(end.buffer)];
  const out = new Uint8Array(new ArrayBuffer(all.reduce((n, c) => n + c.length, 0)));
  let at = 0; for (const c of all) { out.set(c, at); at += c.length; }
  return out;
}

const XML = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>';
const MAIN_NS = 'http://schemas.openxmlformats.org/spreadsheetml/2006/main';
const REL_NS = 'http://schemas.openxmlformats.org/officeDocument/2006/relationships';
export function xmlEscape(v: string): string {
  let out = '';
  for (const ch of v) {
    const c = ch.codePointAt(0)!;
    if (ch === '&') out += '&amp;'; else if (ch === '<') out += '&lt;'; else if (ch === '>') out += '&gt;'; else if (ch === '"') out += '&quot;'; else if (ch === "'") out += '&apos;';
    else if (ch === '\n' || ch === '\r' || ch === '\t') out += ch; else if (c < 0x20) continue; else out += ch;
  }
  return out;
}
export function columnName(index: number): string {
  let rem = index; let name = '';
  for (;;) { name = String.fromCharCode(65 + (rem % 26)) + name; rem = Math.trunc(rem / 26) - 1; if (rem < 0) break; }
  return name;
}
const textCell = (ref: string, v: string, bold: boolean) => `<c r="${ref}"${bold ? ' s="1"' : ''} t="inlineStr"><is><t xml:space="preserve">${xmlEscape(v)}</t></is></c>`;

/** A real .xlsx (Workbook.kt): right to left, the header frozen, the declared columns as number cells. */
export function buildWorkbook(o: { sheetName: string; header: string[]; rows: string[][]; numericColumns: Iterable<number>; preamble?: string[][] }): Uint8Array<ArrayBuffer> {
  const numeric = new Set(o.numericColumns); const preamble = o.preamble ?? [];
  const headerRow = preamble.length ? preamble.length + 2 : 1;
  let body = `${XML}<worksheet xmlns="${MAIN_NS}"><sheetViews><sheetView rightToLeft="1" workbookViewId="0"><pane ySplit="${headerRow}" topLeftCell="A${headerRow + 1}" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews><sheetData>`;
  preamble.forEach((row, i) => {
    const r = i + 1; body += `<row r="${r}">`;
    row.forEach((v, c) => { if (v) body += textCell(columnName(c) + r, v, c === 0); });
    body += '</row>';
  });
  body += `<row r="${headerRow}">${o.header.map((h, c) => textCell(columnName(c) + headerRow, h, true)).join('')}</row>`;
  o.rows.forEach((row, i) => {
    const r = headerRow + 1 + i; body += `<row r="${r}">`;
    for (let c = 0; c < o.header.length; c++) {
      const v = row[c] ?? ''; const ref = columnName(c) + r;
      const n = numeric.has(c) && v !== '' && Number.isFinite(Number(v)) ? Number(v) : null;
      if (!v) continue;
      body += n != null ? `<c r="${ref}"><v>${numberCell(n)}</v></c>` : textCell(ref, v, false);
    }
    body += '</row>';
  });
  body += '</sheetData></worksheet>';
  const enc = new TextEncoder();
  const part = (name: string, s: string) => ({ name, data: enc.encode(s) });
  return zipStore([
    part('[Content_Types].xml', `${XML}<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>`),
    part('_rels/.rels', `${XML}<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="${REL_NS}/officeDocument" Target="xl/workbook.xml"/></Relationships>`),
    part('xl/workbook.xml', `${XML}<workbook xmlns="${MAIN_NS}" xmlns:r="${REL_NS}"><sheets><sheet name="${xmlEscape([...o.sheetName].slice(0, 31).join(''))}" sheetId="1" r:id="rId1"/></sheets></workbook>`),
    part('xl/_rels/workbook.xml.rels', `${XML}<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="${REL_NS}/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="${REL_NS}/styles" Target="styles.xml"/></Relationships>`),
    // Only a regular and a bold cell style; no typeface is named, so the spreadsheet uses its own.
    part('xl/styles.xml', `${XML}<styleSheet xmlns="${MAIN_NS}"><fonts count="2"><font><sz val="11"/></font><font><b/><sz val="11"/></font></fonts><fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/></cellXfs></styleSheet>`),
    part('xl/worksheets/sheet1.xml', body),
  ]);
}

export const XLSX_MIME = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
/** Hands the reader a file (a Blob through a download link). */
export function downloadFile(name: string, data: BlobPart, type: string): void {
  const a = document.createElement('a');
  a.href = URL.createObjectURL(new Blob([data], { type }));
  a.download = name; a.click();
  setTimeout(() => URL.revokeObjectURL(a.href), 4000);
}
/** `coinepro-trades-1404-10-11.xlsx`: the Jalali day the reader would look for it under, in Latin digits. */
export function tradesFileName(ext: string, now = Date.now()): string {
  const j = jalaliAt(now);
  return j ? `coinepro-trades-${jalaliSlash(j).replace(/\//g, '-')}.${ext}` : `coinepro-trades.${ext}`;
}

/** `2026-01-01T09:30:00Z`: UTC, no milliseconds when there are none (ISO_INSTANT). */
export const isoInstant = (sec: number | null | undefined) => (sec == null || !Number.isFinite(sec) ? '' : new Date(sec * 1000).toISOString().replace('.000Z', 'Z'));
const jalaliCell = (sec: number | null | undefined) => { if (sec == null) return ''; const j = jalaliAt(sec * 1000); return j ? jalaliSlash(j) : ''; };

/** The trade history as a file (TradeExport.kt): Persian headings, two columns for every time. */
export const TRADE_HEADERS = ['شناسه', 'نماد', 'جهت', 'حجم', 'قیمت ورود', 'قیمت خروج', 'زمان باز شدن (ISO)', 'تاریخ باز شدن (شمسی)', 'زمان بسته شدن (ISO)', 'تاریخ بسته شدن (شمسی)', 'مدت نگهداری (ثانیه)', 'سود ناخالص', 'کارمزد', 'سواپ', 'سود خالص', 'پیپ', 'دلیل بسته شدن', 'موجودی پس از معامله', 'لیکویید', 'ارز'];
export const TRADE_NUMERIC = [3, 4, 5, 10, 11, 12, 13, 14, 15, 17];
export function tradeFields(t: ClosedTrade): string[] {
  const d = durationOf(t);
  return [t.id, t.symbol, t.side === 'buy' ? 'خرید' : 'فروش', numberCell(t.volume), numberCell(t.entry), numberCell(t.exit), isoInstant(t.openedAt), jalaliCell(t.openedAt), isoInstant(t.closedAt), jalaliCell(t.closedAt),
    d == null ? '' : String(d), numberCell(t.gross), numberCell(t.commission), numberCell(t.swap), numberCell(t.net), numberCell(t.pips), t.reason, numberCell(t.balanceAfter), t.liquidated ? 'بله' : 'خیر', t.currency];
}
const oldestFirst = (trades: ClosedTrade[]) => [...trades].sort((a, b) => a.closedAt - b.closedAt).map(tradeFields);
export const tradesCsv = (trades: ClosedTrade[]) => buildCsv(TRADE_HEADERS, oldestFirst(trades));
export const tradesXlsx = (trades: ClosedTrade[]) => buildWorkbook({ sheetName: 'معاملات', header: TRADE_HEADERS, rows: oldestFirst(trades), numericColumns: TRADE_NUMERIC });

/** A paper trade as a closed trade (PaperRecordMath.asClosedTrade), so paper and live read alike. */
export function paperAsClosed(t: PaperTrade): ClosedTrade {
  const fees = t.fees ?? 0;
  return {
    id: t.id, symbol: t.symbol, side: t.side === 'long' ? 'buy' : 'sell', volume: t.qty, entry: t.entry, exit: t.exit,
    openedAt: Math.floor(t.openedAt / 1000), closedAt: Math.floor(t.closedAt / 1000), gross: t.pnl + fees, commission: -fees, swap: null, net: t.pnl,
    pips: null, reason: t.reason ?? 'manual', balanceAfter: t.balanceAfter ?? null, liquidated: t.reason === 'liq', currency: 'USDT',
  };
}

// ── The paper record (PaperRecordTab / PaperRecordMath) ─────────────────────────────────────────
export interface SymbolPerformance { symbol: string; trades: number; wins: number; net: number; winRate: number | null }
export interface PaperRecord {
  trades: number; wins: number; losses: number; net: number; grossWin: number; grossLoss: number; best: number | null; worst: number | null;
  winRate: number | null; profitFactor: number | null; expectancy: number | null; averageWin: number | null; averageLoss: number | null; payoff: number | null;
  maxDrawdown: number; maxDrawdownPercent: number | null; costs: number; equity: EquityPoint[]; equityIsBalance: boolean;
  bySymbol: SymbolPerformance[]; byMonth: MonthlyPerformance[];
}
export function paperRecord(history: PaperTrade[]): PaperRecord {
  const trades = history.map(paperAsClosed).sort((a, b) => a.closedAt - b.closedAt);
  let wins = 0; let losses = 0; let net = 0; let gw = 0; let gl = 0; let best: number | null = null; let worst: number | null = null;
  for (const t of trades) {
    const p = t.net ?? 0; net += p;
    if (p > 0) { wins++; gw += p; } else if (p < 0) { losses++; gl += -p; }
    if (best == null || p > best) best = p; if (worst == null || p < worst) worst = p;
  }
  const { curve, balanced } = equityCurve(trades);
  const dd = deepestDrawdown(curve, balanced);
  const by = new Map<string, SymbolPerformance>();
  for (const t of trades) { const r = by.get(t.symbol) ?? { symbol: t.symbol, trades: 0, wins: 0, net: 0, winRate: null }; r.trades++; if ((t.net ?? 0) > 0) r.wins++; r.net += t.net ?? 0; r.winRate = (r.wins / r.trades) * 100; by.set(t.symbol, r); }
  const averageWin = wins ? gw / wins : null; const averageLoss = losses ? gl / losses : null;
  return {
    trades: trades.length, wins, losses, net, grossWin: gw, grossLoss: gl, best, worst,
    winRate: wins + losses > 0 ? (wins / (wins + losses)) * 100 : null, profitFactor: gl > 0 ? gw / gl : null, expectancy: trades.length ? net / trades.length : null,
    averageWin, averageLoss, payoff: averageWin != null && averageLoss != null && averageLoss > 0 ? averageWin / averageLoss : null,
    maxDrawdown: dd?.depth ?? 0, maxDrawdownPercent: dd?.depthPercent ?? null, costs: history.reduce((n, t) => n + (t.fees ?? 0), 0),
    equity: curve, equityIsBalance: balanced,
    // Worst first: the list is read to find what is losing (PortfolioMath.bySymbol).
    bySymbol: [...by.values()].sort((a, b) => a.net - b.net), byMonth: byMonth(trades),
  };
}

// ── The journal (JournalFilter.kt, JournalStats.kt) ─────────────────────────────────────────────
export type JournalOutcome = 'any' | 'wins' | 'losses' | 'ungraded';
export type JournalShot = 'any' | 'with' | 'without';
export interface JournalQuery { outcome: JournalOutcome; shot: JournalShot; tags: string[]; query: string }
export const foldDigits = (s: string) => s.replace(/[۰-۹]/g, (d) => String(d.charCodeAt(0) - 0x06f0)).replace(/[٠-٩]/g, (d) => String(d.charCodeAt(0) - 0x0660));
const norm = (s: string) => foldDigits(s).toLowerCase().trim();
export function filterJournal(entries: JournalEntry[], f: JournalQuery): JournalEntry[] {
  const needle = norm(f.query);
  return entries.filter((e) => {
    const pnl = e.pnl != null && Number.isFinite(e.pnl) ? e.pnl : null;
    const outcome = f.outcome === 'any' || (f.outcome === 'wins' ? pnl != null && pnl > 0 : f.outcome === 'losses' ? pnl != null && pnl < 0 : pnl == null);
    const shot = f.shot === 'any' || (f.shot === 'with' ? Boolean(e.screenshot) : !e.screenshot);
    const tags = f.tags.every((t) => e.tags.includes(t));
    const text = !needle || [e.symbol, e.note, e.lesson, e.emotion, e.tags.join(',')].some((x) => norm(x ?? '').includes(needle));
    return outcome && shot && tags && text;
  });
}
export interface JournalStats { graded: number; wins: number; losses: number; net: number; grossProfit: number; grossLoss: number; winRate: number | null; profitFactor: number | null; expectancy: number | null; averageWin: number | null; averageLoss: number | null }
export function journalStats(entries: JournalEntry[]): JournalStats {
  const graded = entries.map((e) => e.pnl).filter((p): p is number => p != null && Number.isFinite(p));
  const wins = graded.filter((p) => p > 0); const losses = graded.filter((p) => p < 0);
  const gp = wins.reduce((n, x) => n + x, 0); const gl = -losses.reduce((n, x) => n + x, 0); const net = graded.reduce((n, x) => n + x, 0);
  return {
    graded: graded.length, wins: wins.length, losses: losses.length, net, grossProfit: gp, grossLoss: gl,
    winRate: graded.length ? (wins.length * 100) / graded.length : null, profitFactor: gl === 0 ? null : gp / gl, expectancy: graded.length ? net / graded.length : null,
    averageWin: wins.length ? gp / wins.length : null, averageLoss: losses.length ? gl / losses.length : null,
  };
}

// ── The signal record (SignalPerformance.kt) ────────────────────────────────────────────────────
export type ResultFilter = 'all' | 'win' | 'loss' | 'breakeven' | 'unknown';
export type MarketKind = 'crypto' | 'forex';
/** A closed signal as the record reads it — `hits` keeps «not said» apart from «not hit». */
export interface SignalRecord { id: string; symbol: string; market: MarketKind; direction: string; timeframe: string; entry: number | null; pnlUsd: number | null; hits: Array<boolean | null>; closeReason: string | null; rr: number | null; source: string | null; closedAt: number | null }
const n = (v: unknown): number | null => { const x = Number(v); return v == null || v === '' || !Number.isFinite(x) ? null : x; };
export function readSignalRecord(x: Record<string, unknown>, fallback: MarketKind): SignalRecord {
  const m = String(x.market ?? '').toLowerCase();
  const result = (x.result ?? null) as Record<string, unknown> | null;
  const closed = x.closed_at; const t = typeof closed === 'number' ? (closed < 1e12 ? closed * 1000 : closed) : closed ? Date.parse(String(closed)) : NaN;
  return {
    id: String(x.id ?? ''), symbol: String(x.symbol ?? '').toUpperCase(), market: m === 'forex' ? 'forex' : m === 'crypto' ? 'crypto' : fallback,
    direction: String(x.direction ?? ''), timeframe: String(x.timeframe ?? ''), entry: n(x.entry ?? x.entry_price),
    pnlUsd: n(result?.pnl_usd), hits: Array.isArray(x.targets) ? (x.targets as Record<string, unknown>[]).map((tt) => (typeof tt.hit === 'boolean' ? tt.hit : null)) : [],
    closeReason: typeof x.close_reason === 'string' ? x.close_reason : null, rr: n(x.risk_reward_tp1 ?? x.risk_reward), source: typeof result?.source === 'string' ? result.source : null,
    closedAt: Number.isFinite(t) ? t : null,
  };
}
export function performanceResult(s: SignalRecord): Exclude<ResultFilter, 'all'> {
  if (s.pnlUsd == null) return 'unknown';
  return s.pnlUsd > 0 ? 'win' : s.pnlUsd < 0 ? 'loss' : 'breakeven';
}
export function filterHistory(list: SignalRecord[], f: { market?: MarketKind | null; symbol?: string; result?: ResultFilter }): SignalRecord[] {
  const sym = f.symbol?.trim().toUpperCase() || null;
  return list.filter((s) => (!f.market || s.market === f.market) && (!sym || s.symbol === sym) && (!f.result || f.result === 'all' || performanceResult(s) === f.result));
}
export function isExplicitStopLossReason(reason: string | null | undefined): boolean {
  if (!reason) return false;
  return ['SL', 'STOP_LOSS', 'STOPLOSS'].includes(reason.trim().toUpperCase().replace(/-/g, '_').replace(/ /g, '_'));
}
export interface RateMetric { hits: number; denominator: number; percent: number | null }
const rate = (hits: number, d: number): RateMetric => ({ hits, denominator: d, percent: d === 0 ? null : (hits / d) * 100 });
export interface SignalSummary { total: number; wins: number; losses: number; breakeven: number; unknownPnl: number; winRate: RateMetric; tpHitRate: RateMetric; stopLossRate: RateMetric; averageRiskReward: number | null; riskRewardDenominator: number }
export function summarizeSignals(list: SignalRecord[]): SignalSummary {
  const pnl = list.map((s) => s.pnlUsd).filter((p): p is number => p != null);
  const targetEvidence = list.filter((s) => s.hits.some((h) => h != null));
  const reasons = list.filter((s) => s.closeReason && s.closeReason.trim());
  const rrs = list.map((s) => s.rr).filter((r): r is number => r != null && r > 0);
  const wins = pnl.filter((p) => p > 0).length;
  return {
    total: list.length, wins, losses: pnl.filter((p) => p < 0).length, breakeven: pnl.filter((p) => p === 0).length, unknownPnl: list.length - pnl.length,
    winRate: rate(wins, pnl.length), tpHitRate: rate(targetEvidence.filter((s) => s.hits.some((h) => h === true)).length, targetEvidence.length),
    stopLossRate: rate(reasons.filter((s) => isExplicitStopLossReason(s.closeReason)).length, reasons.length),
    averageRiskReward: rrs.length ? avg(rrs) : null, riskRewardDenominator: rrs.length,
  };
}
/** A queued request may be cancelled; MT5 may close what it submitted or opened; LBank only before it leaves (ExecutionModels.canRequestClose). */
export function canRequestClose(status: string, venue: string): boolean {
  const s = status.toLowerCase();
  if (s === 'queued') return true;
  return venue.toLowerCase() === 'mt5' && (s === 'submitted' || s === 'open');
}

// ── The backtest file (BacktestExport.kt) ───────────────────────────────────────────────────────
/** Thirty is where the metrics stop being anecdotes (BacktestFormat.CONFIDENT_TRADES). */
export const CONFIDENT_TRADES = 30;
export const BT_HEADERS = ['شماره', 'جهت', 'کندل ورود', 'زمان ورود (ISO)', 'تاریخ ورود (شمسی)', 'قیمت ورود', 'کندل خروج', 'زمان خروج (ISO)', 'تاریخ خروج (شمسی)', 'قیمت خروج', 'حجم', 'کارمزد', 'سود ناخالص', 'سود خالص', 'درصد سود', 'کندل نگهداری', 'بیشترین سود میان‌راه', 'بیشترین زیان میان‌راه', 'موجودی پس از معامله'];
export const BT_NUMERIC = [0, 2, 5, 6, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18];
const jalaliProse = (sec: number) => { const j = sec > 0 ? jalaliAt(sec * 1000) : null; return j ? jalaliLong(j) : ''; };
export interface BtRun { symbol: string; strategy: string; direction: 'long' | 'short' | 'both'; feePct: number; capital: number; bars: number; from: number; to: number; result: BtResult }
export function backtestPreamble(r: BtRun): string[][] {
  const m = r.result;
  const range = r.from > 0 && r.to > 0 ? `${jalaliProse(r.from)} تا ${jalaliProse(r.to)}` : '';
  const rows: Array<[string, string]> = [
    ['گزارش بک‌تست', r.symbol],
    ['راهبرد', r.strategy],
    ['جهت', r.direction === 'both' ? 'خرید و فروش' : r.direction === 'long' ? 'فقط خرید' : 'فقط فروش'],
    ['هزینه‌ی رفت و برگشت (درصد)', numberCell(r.feePct * 2)],
    ['تعداد کندل', numberCell(r.bars)],
    ['بازه‌ی زمانی', range],
    ['سرمایه‌ی اولیه', numberCell(r.capital)],
    ['سود خالص', numberCell(m.net)],
    ['سود خالص (درصد)', numberCell(m.netPct)],
    ['سود ناخالص', numberCell(m.grossProfit)],
    ['زیان ناخالص', numberCell(m.grossLoss)],
    ['مجموع کارمزد', numberCell(m.fees)],
    ['ضریب سود', numberCell(m.pf)],
    ['تعداد معامله', numberCell(m.trades.length)],
    ['معاملات برنده', numberCell(m.wins ?? m.trades.filter((t) => t.pnl > 0).length)],
    ['معاملات بازنده', numberCell(m.losses ?? m.trades.filter((t) => t.pnl <= 0).length)],
    ['درصد برد', numberCell(m.win)],
    ['میانگین سود هر معامله', numberCell(m.avg)],
    ['میانگین برد', numberCell(m.avgWin)],
    ['میانگین باخت', numberCell(m.avgLoss)],
    ['نسبت برد به باخت', numberCell(m.ratio)],
    ['بزرگ‌ترین برد', numberCell(m.largestWin)],
    ['بزرگ‌ترین باخت', numberCell(m.largestLoss)],
    ['میانگین کندل در معامله', numberCell(m.avgBars)],
    ['میانگین کندل در برنده‌ها', numberCell(m.avgBarsWin)],
    ['میانگین کندل در بازنده‌ها', numberCell(m.avgBarsLoss)],
    ['بیشترین رشد سرمایه', numberCell(m.maxRunUp)],
    ['بیشترین رشد سرمایه (درصد)', numberCell(m.maxRunUpPct)],
    ['بیشترین افت سرمایه', numberCell(m.dd)],
    ['بیشترین افت سرمایه (درصد)', numberCell(m.ddPct)],
    ['شارپ', numberCell(m.sharpe)],
    ['سورتینو', numberCell(m.sortino)],
    ['امید ریاضی هر معامله', numberCell(m.expectancy)],
    ['بازده خرید و نگهداری (درصد)', numberCell(m.buyHold)],
  ];
  return rows.map(([k, v]) => [k, v]);
}
export function backtestRows(r: BtRun): string[][] {
  let running = r.capital;
  return r.result.trades.map((t, i) => {
    running += t.pnl;
    return [numberCell(i + 1), t.dir === 1 ? 'خرید' : 'فروش', numberCell(t.entryIdx), isoInstant(t.entryT), jalaliProse(t.entryT), numberCell(t.entry), numberCell(t.exitIdx), isoInstant(t.exitT), jalaliProse(t.exitT), numberCell(t.exit),
      numberCell(t.qty), numberCell(t.fee), numberCell(t.fee != null ? t.pnl + t.fee : null), numberCell(t.pnl), numberCell(t.pct), numberCell(t.bars), numberCell(t.runUp), numberCell(t.drawdown), numberCell(running)];
  });
}
export const backtestCsv = (r: BtRun) => buildCsv(BT_HEADERS, backtestRows(r), [...backtestPreamble(r), []]);
export const backtestXlsx = (r: BtRun) => buildWorkbook({ sheetName: 'بک‌تست', preamble: backtestPreamble(r), header: BT_HEADERS, rows: backtestRows(r), numericColumns: BT_NUMERIC });
export const backtestFileName = (symbol: string, ext: string) => `coinepro-backtest-${symbol.toLowerCase().replace(/[^a-z0-9]/g, '')}.${ext}`;
