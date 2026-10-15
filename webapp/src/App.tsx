import { useEffect, useState } from 'react';
import { TopBar } from './shell/TopBar';
import { ToolRail } from './shell/ToolRail';
import { WidgetBar } from './shell/WidgetBar';
import { RightPanel } from './shell/RightPanel';
import { TimeBar } from './shell/TimeBar';
import { BottomPanel } from './shell/BottomPanel';
import { SymbolSearch } from './shell/SymbolSearch';
import { SignIn } from './shell/SignIn';
import { IndicatorsDialog } from './shell/IndicatorsDialog';
import { StudySettings } from './shell/StudySettings';
import { useCompare } from './state/compare';
import { AlertDialog, AlertToast } from './shell/AlertsPanel';
import { attachAppHotkeys } from './chart/hotkeys';
import { useReplay } from './state/replay';
import { useWatchlist } from './state/watchlist';
import { SettingsDialog } from './shell/SettingsDialog';
import { PremiumDialog } from './shell/PremiumDialog';
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
  const [indicators, setIndicators] = useState(false);
  const [studyUid, setStudyUid] = useState<string | null>(null);
  const [comparing, setComparing] = useState(false);
  const [adding, setAdding] = useState(false);
  const [settings, setSettings] = useState(false);
  const [premium, setPremium] = useState(false);
  const addToWatchlist = useWatchlist((s) => s.add);
  const [alertAt, setAlertAt] = useState<number | null | undefined>(undefined);
  const addCompare = useCompare((s) => s.add);
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

  useEffect(() => attachAppHotkeys({
    indicators: () => setIndicators(true),
    symbolSearch: () => setSearch(''),
    newAlert: () => setAlertAt(null),
    settings: () => setSettings(true),
  }), []);

  // Replay ends when the symbol or the interval changes.
  useEffect(() => { useReplay.getState().exit(); }, [symbol, interval]);

  // Typing a letter anywhere opens the symbol search with it, as on TradingView.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (search !== null || signIn || indicators || studyUid || comparing || adding || settings || premium || alertAt !== undefined || e.ctrlKey || e.metaKey || e.altKey) return;
      const el = e.target as HTMLElement;
      if (el.closest('input, textarea, [contenteditable]')) return;
      if (/^[a-zA-Z0-9]$/.test(e.key)) { e.preventDefault(); setSearch(e.key.toUpperCase()); }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [search, signIn, indicators, studyUid, comparing, adding, settings, premium, alertAt]);

  const displayName = (lang === 'fa' ? inst.nameFa : inst.nameEn) ?? symbol;

  return (
    <div className="ws">
      <TopBar onSymbolSearch={() => setSearch('')} onIndicators={() => setIndicators(true)} onSignIn={() => setSignIn(true)} onCompare={() => setComparing(true)} onAlert={() => setAlertAt(null)} onSettings={() => setSettings(true)} onPremium={() => setPremium(true)} onReplay={() => { const r = useReplay.getState(); if (r.state?.on || r.selecting) r.exit(); else r.select(); }} />
      <ToolRail />
      <main className="ws-main">
        <ChartView feed={feed} digits={inst.digits} displayName={displayName} onStudySettings={setStudyUid} onAlertAt={(p) => setAlertAt(p)} />
        <TimeBar supported={(iv) => supported.includes(iv)} />
        <BottomPanel />
      </main>
      <RightPanel onNewAlert={() => setAlertAt(null)} onAddSymbol={() => setAdding(true)} onSignIn={() => setSignIn(true)} />
      <WidgetBar />
      {search !== null ? <SymbolSearch initial={search} onClose={() => setSearch(null)} /> : null}
      {signIn ? <SignIn onClose={() => setSignIn(false)} /> : null}
      {indicators ? <IndicatorsDialog onClose={() => setIndicators(false)} /> : null}
      {studyUid ? <StudySettings uid={studyUid} onClose={() => setStudyUid(null)} /> : null}
      {alertAt !== undefined ? <AlertDialog price={alertAt ?? undefined} onClose={() => setAlertAt(undefined)} /> : null}
      <AlertToast />
      {premium ? <PremiumDialog onClose={() => setPremium(false)} onSignIn={() => { setPremium(false); setSignIn(true); }} /> : null}
      {settings ? <SettingsDialog onClose={() => setSettings(false)} /> : null}
      {adding ? <SymbolSearch onClose={() => setAdding(false)} onPick={(s) => addToWatchlist(s)} /> : null}
      {comparing ? <SymbolSearch onClose={() => setComparing(false)} onPick={(s) => addCompare(s)} /> : null}
    </div>
  );
}
