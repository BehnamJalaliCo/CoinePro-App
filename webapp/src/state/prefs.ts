// What the reader chose and expects to find again: theme, language, the open symbol and interval.
// Kept in localStorage, which may be unavailable (private windows, blocked storage); every access
// is guarded and the app works without it.
import { create } from 'zustand';
import { en, fa, type StringKey } from '../i18n/strings';
import type { ChartType } from '../chart/chartTypes';

export type Theme = 'system' | 'light' | 'dark';
export type Lang = 'fa' | 'en';

const KEY = 'pc.prefs.v1';

interface Stored { theme: Theme; lang: Lang; symbol: string; interval: string; chartType: ChartType }

function read(): Partial<Stored> {
  try {
    const raw = localStorage.getItem(KEY);
    return raw ? (JSON.parse(raw) as Partial<Stored>) : {};
  } catch {
    return {};
  }
}

function write(value: Stored): void {
  try { localStorage.setItem(KEY, JSON.stringify(value)); } catch { /* storage blocked: keep going */ }
}

interface PrefsState extends Stored {
  setTheme(theme: Theme): void;
  setLang(lang: Lang): void;
  setSymbol(symbol: string): void;
  setInterval(interval: string): void;
  setChartType(chartType: ChartType): void;
}

const initial = read();

export const usePrefs = create<PrefsState>((set, get) => {
  const persist = () => {
    const { theme, lang, symbol, interval, chartType } = get();
    write({ theme, lang, symbol, interval, chartType });
  };
  return {
    theme: initial.theme ?? 'system',
    lang: initial.lang ?? 'fa',
    symbol: initial.symbol ?? 'BTCUSDT',
    interval: initial.interval ?? '60',
    chartType: initial.chartType ?? 'candles',
    setTheme: (theme) => { set({ theme }); persist(); },
    setLang: (lang) => { set({ lang }); persist(); },
    setSymbol: (symbol) => { set({ symbol }); persist(); },
    setInterval: (interval) => { set({ interval }); persist(); },
    setChartType: (chartType) => { set({ chartType }); persist(); },
  };
});

export function useT(): (key: StringKey) => string {
  const lang = usePrefs((s) => s.lang);
  const table = lang === 'fa' ? fa : en;
  return (key) => table[key];
}

/** Applies the theme to the document: `data-theme` only for an explicit choice, as the tokens expect. */
export function applyTheme(theme: Theme): void {
  const root = document.documentElement;
  if (theme === 'system') root.removeAttribute('data-theme');
  else root.setAttribute('data-theme', theme);
}

export function isDark(theme: Theme): boolean {
  if (theme === 'dark') return true;
  if (theme === 'light') return false;
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false;
}
