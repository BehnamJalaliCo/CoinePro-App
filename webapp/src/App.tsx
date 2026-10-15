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
import { AlertDialog, AlertHost, AlertToast } from './shell/AlertsPanel';
import { attachAppHotkeys } from './chart/hotkeys';
import { useReplay } from './state/replay';
import { useWatchlist } from './state/watchlist';
import { SettingsDialog } from './shell/SettingsDialog';
import { PremiumDialog } from './shell/PremiumDialog';
import { applySymbol, useGrid } from './state/grid';
import { ChartGrid } from './shell/ChartGrid';
import { MainMenu } from './shell/MainMenu';
import { PageHost } from './pages/PageHost';
import { usePage } from './state/page';
import { ChartView } from './chart/ChartView';
import { feedFor } from './data/feed';
import { canDraw, intervalsFor } from './data/candles';
import { instrumentOf } from './data/symbols';
import { useMarket } from './state/market';
import { usePrefs, applyTheme } from './state/prefs';
import { formatPrice } from './chart/format';
import { useRoute } from './route';
import { startPanel } from './usercp/telemetry';
import { intervalBox } from './shell/IntervalMenu';
import { StudiesHost } from './shell/StudiesHost';
import { TradeHost } from './trade/TradeHost';

export function App() {
  const symbol = usePrefs((s) => s.symbol);
  const interval = usePrefs((s) => s.interval);
  const setInterval = usePrefs((s) => s.setInterval);
  const theme = usePrefs((s) => s.theme);
  const lang = usePrefs((s) => s.lang);
  const session = usePrefs((s) => s.session);
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
  const [menu, setMenu] = useState(false);
  const page = usePage((s) => s.page);
  const [cellSearch, setCellSearch] = useState<number | null>(null);
  const addToWatchlist = useWatchlist((s) => s.add);
  const [alertAt, setAlertAt] = useState<number | null | undefined>(undefined);
  const addCompare = useCompare((s) => s.add);
  useRoute();

  useEffect(() => startPanel(), []);
  useEffect(() => { applyTheme(theme); }, [theme]);
  useEffect(() => { document.documentElement.lang = lang; }, [lang]);

  // An interval the symbol's backend cannot draw falls back to the nearest one it can.
  const supported = intervalsFor(symbol);
  useEffect(() => {
    if (!canDraw(symbol, interval)) setInterval(supported.includes('5') ? '5' : supported[0]!);
  }, [symbol, interval, supported, setInterval]);

  // The tab title follows the price, as on TradingView.
  useEffect(() => {
    const pct = tk ? ` ${tk.changePct >= 0 ? '▲' : '▼'} ${tk.changePct >= 0 ? '+' : '−'}${Math.abs(tk.changePct).toFixed(2)}%` : '';
    document.title = tk ? `${symbol} ${formatPrice(tk.last, inst.digits)}${pct}` : `${symbol} · Pro Chart`;
  }, [symbol, tk, inst.digits]);

  useEffect(() => attachAppHotkeys({
    indicators: () => setIndicators(true),
    // Ctrl+K: TradingView's Quick Search (the top bar's command palette).
    symbolSearch: () => window.dispatchEvent(new CustomEvent('pc:quick-search')),
    newAlert: () => setAlertAt(null),
    settings: () => setSettings(true),
  }), []);

  // An indicator's settings asked for from outside the chart (the object tree's gear, the
  // indicators dialog) open in StudiesHost, which listens for pc:study-settings itself.

  // Replay ends when the symbol or the interval changes.
  useEffect(() => { useReplay.getState().exit(); }, [symbol, interval]);

  // Typing a letter anywhere opens the symbol search with it, and a digit the interval box, as on
  // TradingView.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (search !== null || signIn || indicators || studyUid || comparing || adding || settings || premium || menu || page || cellSearch !== null || alertAt !== undefined || e.ctrlKey || e.metaKey || e.altKey) return;
      const el = e.target as HTMLElement;
      if (el.closest('input, textarea, [contenteditable]')) return;
      // The interval box is open but not focused yet: what is typed goes into it, letters too (4h).
      if ((intervalBox.open || document.querySelector('.iv-box')) && e.key.length === 1) { e.preventDefault(); window.dispatchEvent(new CustomEvent('pc:interval-box', { detail: e.key })); return; }
      if (document.querySelector('.scrim, [role=dialog]')) return;
      if (/^[0-9]$/.test(e.key)) { e.preventDefault(); window.dispatchEvent(new CustomEvent('pc:interval-box', { detail: e.key })); }
      else if (/^[a-zA-Z]$/.test(e.key)) { e.preventDefault(); setSearch(e.key.toUpperCase()); }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [search, signIn, indicators, studyUid, comparing, adding, settings, premium, menu, page, cellSearch, alertAt]);

  const displayName = (lang === 'fa' ? inst.nameFa : inst.nameEn) ?? symbol;

  return (
    <div className="ws">
      <TopBar onSymbolSearch={() => setSearch('')} onIndicators={() => setIndicators(true)} onSignIn={() => setSignIn(true)} onCompare={() => setComparing(true)} onAlert={() => setAlertAt(null)} onSettings={() => setSettings(true)} onPremium={() => setPremium(true)} onMenu={() => setMenu(true)} onReplay={() => { const r = useReplay.getState(); if (r.state?.on || r.selecting) r.exit(); else r.select(); }} />
      <ToolRail />
      <main className="ws-main">
        <ChartGrid feed={feedFor(session)} onCellSearch={setCellSearch}>
          <ChartView feed={feedFor(session)} digits={inst.digits} displayName={displayName} onStudySettings={setStudyUid} onAlertAt={(p) => setAlertAt(p)} />
        </ChartGrid>
        <TimeBar supported={(iv) => supported.includes(iv)} />
        <BottomPanel />
      </main>
      <RightPanel onNewAlert={() => setAlertAt(null)} onAddSymbol={() => setAdding(true)} onSignIn={() => setSignIn(true)} onCompare={() => setComparing(true)} onSearch={() => setSearch('')} />
      <WidgetBar />
      <PageHost onSignIn={() => setSignIn(true)} />
      {menu ? <MainMenu onClose={() => setMenu(false)} onSignIn={() => setSignIn(true)} /> : null}
      {search !== null ? <SymbolSearch initial={search} allowExpr onClose={() => setSearch(null)} /> : null}
      {signIn ? <SignIn onClose={() => setSignIn(false)} /> : null}
      {indicators ? <IndicatorsDialog onClose={() => setIndicators(false)} /> : null}
      {studyUid ? <StudySettings uid={studyUid} onClose={() => setStudyUid(null)} /> : null}
      {alertAt !== undefined ? <AlertDialog price={alertAt ?? undefined} onClose={() => setAlertAt(undefined)} /> : null}
      <AlertToast />
      <AlertHost />
      <StudiesHost />
      <TradeHost onSignIn={() => setSignIn(true)} />
      {premium ? <PremiumDialog onClose={() => setPremium(false)} onSignIn={() => { setPremium(false); setSignIn(true); }} /> : null}
      {settings ? <SettingsDialog onClose={() => setSettings(false)} /> : null}
      {cellSearch !== null ? <SymbolSearch allowExpr current={useGrid.getState().cells[cellSearch]?.symbol} onClose={() => setCellSearch(null)} onPick={(sym) => { useGrid.getState().setActive(cellSearch + 1); applySymbol(sym); }} /> : null}
      {adding ? <SymbolSearch onClose={() => setAdding(false)} onPick={(s) => addToWatchlist(s)} /> : null}
      {comparing ? <SymbolSearch mode="compare" onClose={() => setComparing(false)} onPick={(s) => addCompare(s)} /> : null}
    </div>
  );
}
