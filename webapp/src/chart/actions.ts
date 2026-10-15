// Chart actions the menus share: go to a date (loading older history until it is on the chart),
// the loaded bars as CSV, and the chart picture copied to the clipboard.
import { useChartBus } from './chartBus';
import { usePrefs } from '../state/prefs';
import { intervalLabel } from '../data/intervals';
import { chartImage } from './snapshot';
import { replayGoTo } from '../state/replay';

const wait = (ms: number) => new Promise((r) => setTimeout(r, ms));

/** Brings `t` (seconds) to the middle of the chart; scrolls back for older history up to 12 times. */
export async function goToTime(t: number): Promise<boolean> {
  // While replaying, the date moves the replay; while picking its first bar, it starts there.
  if (replayGoTo(t, useChartBus.getState().bars)) return true;
  for (let i = 0; i < 12; i++) {
    const { api, bars } = useChartBus.getState();
    if (!api || !bars.length) return false;
    if (bars[0]!.t <= t || i === 11) {
      let idx = bars.findIndex((b) => b.t >= t);
      if (idx < 0) idx = bars.length - 1;
      const span = Math.max(40, Math.round(((api.timeScale().getVisibleLogicalRange()?.to ?? 100) - (api.timeScale().getVisibleLogicalRange()?.from ?? 0))));
      const range = { from: idx - span / 2, to: idx + span / 2 };
      // A load or a live bar landing at the same moment can move the view back; hold it there.
      for (let k = 0; k < 4; k++) {
        api.timeScale().setVisibleLogicalRange(range);
        await wait(120);
        const now = api.timeScale().getVisibleLogicalRange();
        if (now && now.from <= idx && now.to >= idx) break;
      }
      return bars[0]!.t <= t;
    }
    // Showing the oldest bars asks the chart for the page before them.
    api.timeScale().setVisibleLogicalRange({ from: -5, to: 60 });
    const before = bars[0]!.t;
    for (let k = 0; k < 20; k++) { await wait(150); if ((useChartBus.getState().bars[0]?.t ?? before) < before) break; }
    if ((useChartBus.getState().bars[0]?.t ?? before) >= before) {
      const s = useChartBus.getState();
      if (s.api) s.api.timeScale().setVisibleLogicalRange({ from: 0, to: 80 });
      return false;
    }
  }
  return false;
}

/** The loaded bars as a CSV file: time (UTC), open, high, low, close, volume. */
export function exportBarsCsv(): void {
  const { bars } = useChartBus.getState();
  const { symbol, interval } = usePrefs.getState();
  const rows = ['time,open,high,low,close,volume', ...bars.map((b) => `${new Date(b.t * 1000).toISOString()},${b.o},${b.h},${b.l},${b.c},${b.v}`)];
  const a = document.createElement('a');
  a.href = URL.createObjectURL(new Blob([rows.join('\n')], { type: 'text/csv' }));
  a.download = `${symbol}_${intervalLabel(interval)}.csv`;
  a.click();
  setTimeout(() => URL.revokeObjectURL(a.href), 4000);
}

/** The chart picture on the clipboard; false where the browser does not allow it. */
export async function copyChartImage(): Promise<boolean> {
  const blob = await chartImage();
  if (!blob || typeof ClipboardItem === 'undefined' || !navigator.clipboard?.write) return false;
  try { await navigator.clipboard.write([new ClipboardItem({ 'image/png': blob })]); return true; } catch { return false; }
}

// ── Drawing from the menus (the chart's right-click menu, the axis «+», the legend) ─────────────────
type Addable = { addDrawing?(d: Record<string, unknown>): void; addHLine(price: number, color?: string): number; getDrawings(): unknown[]; canUndo(): boolean; canRedo(): boolean };

function layerOf(): Addable | null {
  return useChartBus.getState().drawingLayer as unknown as Addable | null;
}
function bump(l: Addable) { useChartBus.getState().bumpDrawings(l.canUndo(), l.canRedo(), l.getDrawings().length); }

/** A time on the chart for a click, or the last bar's when the click fell beyond the bars. */
export function timeOr(t: number | null | undefined): number {
  const { bars } = useChartBus.getState();
  return typeof t === 'number' ? t : bars[bars.length - 1]?.t ?? Math.floor(Date.now() / 1000);
}

/** TradingView's «Draw horizontal line at …», undoable. */
export function addHorizontalLine(price: number): void {
  const l = layerOf();
  if (!l || !Number.isFinite(price)) return;
  l.addHLine(price, '#2962FF');
  bump(l);
}

/** «Add vertical line at …»: on the bar under the pointer. */
export function addVerticalLine(time: number | null): void {
  const l = layerOf();
  if (!l?.addDrawing) return;
  l.addDrawing({ type: 'vline', t: timeOr(time), color: '#2962FF', width: 2 });
  bump(l);
}

/** «Add text note»: a text drawing at the price and time, opened in the text editor. */
export function addTextNote(price: number, time: number | null): void {
  const l = layerOf();
  if (!l?.addDrawing || !Number.isFinite(price)) return;
  l.addDrawing({ type: 'text', t: timeOr(time), p: price, text: '', color: '#2962FF' });
  bump(l);
  useChartBus.getState().setTextEdit({ index: l.getDrawings().length - 1, fresh: true });
}

/** A long or short position drawn forward from the point: entry there, a stop half a percent away,
 *  the target at twice the risk (the drawing layer works the target out), over a quarter of the view. */
export function addPosition(side: 'long' | 'short', price: number, time: number | null): void {
  const l = layerOf();
  const api = useChartBus.getState().api;
  if (!l?.addDrawing || !Number.isFinite(price)) return;
  const t0 = timeOr(time);
  const vr = api?.timeScale().getVisibleRange();
  const span = vr && typeof vr.to === 'number' && typeof vr.from === 'number' ? vr.to - vr.from : 86400;
  const t1 = t0 + Math.round(span * 0.28);
  const risk = price * 0.005;
  l.addDrawing(side === 'long'
    ? { type: 'longshort', p0: { t: t0, p: price }, p1: { t: t1, p: price - risk }, color: '#089981', width: 1.5 }
    : { type: 'short', p0: { t: t0, p: price }, p1: { t: t1, p: price + risk }, color: '#F23645', width: 1.5 });
  bump(l);
}

export interface AlertDialogDetail { symbol?: string; price?: number; line?: { t1: number; p1: number; t2: number; p2: number }; study?: { uid: string; key: string; plot?: number } }

/** Opens the alert dialog prefilled (contract 1; the alerts area listens). */
export function openAlertDialog(detail: AlertDialogDetail): void {
  window.dispatchEvent(new CustomEvent('pc:alert-dialog', { detail }));
}

/** Resets the view as TradingView's «Reset chart view»: spacing, right margin, the latest bars, auto-scale. */
export function resetChartView(rightOffset = 10): void {
  const { api } = useChartBus.getState();
  if (!api) return;
  api.timeScale().applyOptions({ barSpacing: 6, rightOffset });
  api.timeScale().scrollToRealTime();
  useChartBus.getState().setAutoScale(true);
}

/** Opens the indicators dialog, as the «/» key does. */
export function openIndicators(): void {
  window.dispatchEvent(new KeyboardEvent('keydown', { key: '/', bubbles: true }));
}

export interface OrderTicketDetail { symbol: string; side: 'buy' | 'sell'; price: number; source: 'legend' | 'axis-plus' | 'context-menu'; type?: 'limit' }

/** Offers an order to whoever trades from the chart (`pc:order-ticket`, cancelable). True when
 *  someone took it (called preventDefault); otherwise the caller falls back to paper trading. */
export function offerOrderTicket(detail: OrderTicketDetail): boolean {
  const ev = new CustomEvent('pc:order-ticket', { cancelable: true, detail });
  window.dispatchEvent(ev);
  return ev.defaultPrevented;
}
