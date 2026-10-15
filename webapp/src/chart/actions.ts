// Chart actions the menus share: go to a date (loading older history until it is on the chart),
// the loaded bars as CSV, and the chart picture copied to the clipboard.
import { useChartBus } from './chartBus';
import { usePrefs } from '../state/prefs';
import { intervalLabel } from '../data/intervals';
import { chartImage } from './snapshot';

const wait = (ms: number) => new Promise((r) => setTimeout(r, ms));

/** Brings `t` (seconds) to the middle of the chart; scrolls back for older history up to 12 times. */
export async function goToTime(t: number): Promise<boolean> {
  for (let i = 0; i < 12; i++) {
    const { api, bars } = useChartBus.getState();
    if (!api || !bars.length) return false;
    if (bars[0]!.t <= t || i === 11) {
      let idx = bars.findIndex((b) => b.t >= t);
      if (idx < 0) idx = bars.length - 1;
      const span = Math.max(40, Math.round(((api.timeScale().getVisibleLogicalRange()?.to ?? 100) - (api.timeScale().getVisibleLogicalRange()?.from ?? 0))));
      api.timeScale().setVisibleLogicalRange({ from: idx - span / 2, to: idx + span / 2 });
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
