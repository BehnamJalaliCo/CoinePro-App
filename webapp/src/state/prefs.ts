// What the reader chose and expects to find again: theme, language, the open symbol and interval.
// Kept in localStorage, which may be unavailable (private windows, blocked storage); every access
// is guarded and the app works without it.
import { create } from 'zustand';
import { en, fa, type StringKey } from '../i18n/strings';
import type { ChartType } from '../chart/chartTypes';

export type Theme = 'system' | 'light' | 'dark';
export type Lang = 'fa' | 'en';

const KEY = 'pc.prefs.v1';

interface Stored {
  theme: Theme; lang: Lang; symbol: string; interval: string; chartType: ChartType; timezone: string;
  /** The intervals shown as buttons in the top bar (TradingView's starred ones). */
  favIntervals: string[];
  /** Intervals the reader added (TradingView's "Add custom interval"). */
  customIntervals: string[];
  /** Chart types shown as buttons beside the type menu. */
  favChartTypes: ChartType[];
  /** The time zone follows the symbol's exchange (TradingView's "Exchange"). */
  tzExchange: boolean;
  /** Regular trading hours only, where a symbol has them (US indices). */
  session: 'regular' | 'extended';
  /** Trading sessions shaded on intraday charts (sydney, tokyo, london, newyork), and their overlaps. */
  shadedSessions: string[];
  sessionOverlaps: boolean;
}

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
  setTimezone(tz: string): void;
  setFavIntervals(list: string[]): void;
  setCustomIntervals(list: string[]): void;
  setFavChartTypes(list: ChartType[]): void;
  setTzExchange(on: boolean): void;
  setSession(s: 'regular' | 'extended'): void;
  setShadedSessions(list: string[]): void;
  setSessionOverlaps(on: boolean): void;
}

const initial = read();

function localZone(): string {
  try { return Intl.DateTimeFormat().resolvedOptions().timeZone || 'Asia/Tehran'; } catch { return 'Asia/Tehran'; }
}

export const usePrefs = create<PrefsState>((set, get) => {
  const persist = () => {
    const { theme, lang, symbol, interval, chartType, timezone, favIntervals, customIntervals, favChartTypes, tzExchange, session, shadedSessions, sessionOverlaps } = get();
    write({ theme, lang, symbol, interval, chartType, timezone, favIntervals, customIntervals, favChartTypes, tzExchange, session, shadedSessions, sessionOverlaps });
  };
  return {
    theme: initial.theme ?? 'system',
    lang: initial.lang ?? 'fa',
    symbol: initial.symbol ?? 'BTCUSDT',
    interval: initial.interval ?? '60',
    chartType: initial.chartType ?? 'candles',
    timezone: initial.timezone ?? localZone(),
    favIntervals: Array.isArray(initial.favIntervals) ? initial.favIntervals : ['1', '5', '15', '60', '240', '1D'],
    customIntervals: Array.isArray(initial.customIntervals) ? initial.customIntervals : [],
    favChartTypes: Array.isArray(initial.favChartTypes) ? initial.favChartTypes : [],
    tzExchange: initial.tzExchange ?? false,
    session: initial.session === 'regular' ? 'regular' : 'extended',
    shadedSessions: Array.isArray(initial.shadedSessions) ? initial.shadedSessions : [],
    sessionOverlaps: initial.sessionOverlaps ?? true,
    setTheme: (theme) => { set({ theme }); persist(); },
    setLang: (lang) => { set({ lang }); persist(); },
    setSymbol: (symbol) => { set({ symbol }); persist(); },
    setInterval: (interval) => { set({ interval }); persist(); },
    setChartType: (chartType) => { set({ chartType }); persist(); },
    setTimezone: (timezone) => { set({ timezone }); persist(); },
    setFavIntervals: (favIntervals) => { set({ favIntervals }); persist(); },
    setCustomIntervals: (customIntervals) => { set({ customIntervals }); persist(); },
    setFavChartTypes: (favChartTypes) => { set({ favChartTypes }); persist(); },
    setTzExchange: (tzExchange) => { set({ tzExchange }); persist(); },
    setSession: (session) => { set({ session }); persist(); },
    setShadedSessions: (shadedSessions) => { set({ shadedSessions }); persist(); },
    setSessionOverlaps: (sessionOverlaps) => { set({ sessionOverlaps }); persist(); },
  };
});

// One function per language, so a hook that lists `t` among its dependencies runs once per language
// change, not on every render (the VIP funnel re-read the server in a loop when it changed).
const T_FA = (key: StringKey): string => fa[key];
const T_EN = (key: StringKey): string => en[key];

export function useT(): (key: StringKey) => string {
  return usePrefs((s) => s.lang) === 'fa' ? T_FA : T_EN;
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
