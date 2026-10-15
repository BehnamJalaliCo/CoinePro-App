// The chart's own settings, TradingView's "Settings" sheet in short: candle colours, grid,
// crosshair, price line and legend. Kept in this browser.
import { create } from 'zustand';

export interface ChartSettings {
  up: string;
  down: string;
  grid: 'both' | 'vertical' | 'horizontal' | 'none';
  crosshair: 'normal' | 'magnet';
  priceLine: boolean;
  showOhlc: boolean;
  showChange: boolean;
  countdown: boolean;
  /** A line at the previous session's close. */
  prevClose: boolean;
  /** The symbol and interval written faintly behind the bars. */
  watermark: boolean;
  /** Economic events of the week marked on the bars they fall in. */
  events: boolean;
  /** A line where each trading day starts, on intraday intervals. */
  sessionBreaks: boolean;
  /** The old Pro-Chart's automatic reading: RSI divergences, harmonic patterns, gaps. */
  autoDivergence: boolean;
  autoHarmonic: boolean;
  autoGaps: boolean;
}

export const DEFAULT_SETTINGS: ChartSettings = {
  up: '#089981', down: '#F23645', grid: 'both', crosshair: 'normal', priceLine: true, showOhlc: true, showChange: true, countdown: true,
  prevClose: false, watermark: false, events: true, sessionBreaks: false, autoDivergence: false, autoHarmonic: false, autoGaps: false,
};

const KEY = 'pc.chartsettings.v1';
const read = (): ChartSettings => {
  try { return { ...DEFAULT_SETTINGS, ...(JSON.parse(localStorage.getItem(KEY) ?? '{}') as Partial<ChartSettings>) }; } catch { return DEFAULT_SETTINGS; }
};

interface State extends ChartSettings { set(p: Partial<ChartSettings>): void; reset(): void }

export const useChartSettings = create<State>((set, get) => ({
  ...read(),
  set: (p) => {
    set(p);
    const { set: _s, reset: _r, ...rest } = get();
    void _s; void _r;
    try { localStorage.setItem(KEY, JSON.stringify(rest)); } catch { /* ignore */ }
  },
  reset: () => { set(DEFAULT_SETTINGS); try { localStorage.removeItem(KEY); } catch { /* ignore */ } },
}));
