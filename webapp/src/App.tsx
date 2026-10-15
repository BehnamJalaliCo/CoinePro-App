import { useEffect, useState } from 'react';
import { TopBar } from './shell/TopBar';
import { ToolRail } from './shell/ToolRail';
import { WidgetBar } from './shell/WidgetBar';
import { RightPanel } from './shell/RightPanel';
import { TimeBar } from './shell/TimeBar';
import { BottomPanel } from './shell/BottomPanel';
import { SymbolSearch } from './shell/SymbolSearch';
import { SignIn } from './shell/SignIn';
import { ChartView } from './chart/ChartView';
import { feed } from './data/feed';
import { intervalsFor } from './data/candles';
import { instrumentOf } from './data/symbols';
import { useMarket } from './state/market';
import { usePrefs, applyTheme } from './state/prefs';
import { formatPrice } from './chart/format';
import { useRoute } from './route';

export function App() {
  const symbol = usePrefs((s) => s.symbol);
  const interval = usePrefs((s) => s.interval);
  const setInterval = usePrefs((s) => s.setInterval);
  const theme = usePrefs((s) => s.theme);
  const lang = usePrefs((s) => s.lang);
  const tk = useMarket((s) => s.tickers[symbol]);
  const inst = useMarket((s) => s.instruments[symbol]) ?? instrumentOf(symbol, tk?.last);
  const [search, setSearch] = useState<string | null>(null);
  const [signIn, setSignIn] = useState(false);
  useRoute();

  useEffect(() => { applyTheme(theme); }, [theme]);
  useEffect(() => { document.documentElement.lang = lang; }, [lang]);

  // An interval the symbol's backend cannot draw falls back to the nearest one it can.
  const supported = intervalsFor(symbol);
  useEffect(() => {
    if (!supported.includes(interval)) setInterval(supported.includes('5') ? '5' : supported[0]!);
  }, [symbol, interval, supported, setInterval]);

  // The tab title follows the price, as on TradingView.
  useEffect(() => {
    const pct = tk ? ` ${tk.changePct >= 0 ? '▲' : '▼'} ${tk.changePct >= 0 ? '+' : '−'}${Math.abs(tk.changePct).toFixed(2)}%` : '';
    document.title = tk ? `${symbol} ${formatPrice(tk.last, inst.digits)}${pct}` : `${symbol} · Pro Chart`;
  }, [symbol, tk, inst.digits]);

  // Typing a letter anywhere opens the symbol search with it, as on TradingView.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (search !== null || signIn || e.ctrlKey || e.metaKey || e.altKey) return;
      const el = e.target as HTMLElement;
      if (el.closest('input, textarea, [contenteditable]')) return;
      if (/^[a-zA-Z0-9]$/.test(e.key)) { e.preventDefault(); setSearch(e.key.toUpperCase()); }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [search, signIn]);

  const displayName = (lang === 'fa' ? inst.nameFa : inst.nameEn) ?? symbol;

  return (
    <div className="ws">
      <TopBar onSymbolSearch={() => setSearch('')} onIndicators={() => undefined} onSignIn={() => setSignIn(true)} />
      <ToolRail />
      <main className="ws-main">
        <ChartView feed={feed} digits={inst.digits} displayName={displayName} />
        <TimeBar supported={(iv) => supported.includes(iv)} />
        <BottomPanel />
      </main>
      <RightPanel />
      <WidgetBar />
      {search !== null ? <SymbolSearch initial={search} onClose={() => setSearch(null)} /> : null}
      {signIn ? <SignIn onClose={() => setSignIn(false)} /> : null}
    </div>
  );
}
