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
}

export const DEFAULT_SETTINGS: ChartSettings = {
  up: '#089981', down: '#F23645', grid: 'both', crosshair: 'normal', priceLine: true, showOhlc: true, showChange: true, countdown: true,
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
