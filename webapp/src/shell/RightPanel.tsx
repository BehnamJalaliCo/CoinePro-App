import { usePanels, type PanelId } from '../state/panels';
import { useT } from '../state/prefs';
import { Watchlist } from './Watchlist';
import { SymbolDetails } from './SymbolDetails';
import { DataWindow } from './DataWindow';
import { NewsPanel } from './NewsPanel';
import { CalendarPanel } from './CalendarPanel';
import { AlertsPanel } from './AlertsPanel';
import { ObjectTree } from './ObjectTree';
import { Screener } from './Screener';
import { SignalsPanel } from './SignalsPanel';
import { AiPanel } from './AiPanel';
import type { StringKey } from '../i18n/strings';

const TITLES: Record<PanelId, StringKey> = {
  watchlist: 'watchlist', alerts: 'alerts', signals: 'signals', ai: 'aiTitle', objects: 'objectTree', data: 'dataWindow',
  news: 'news', calendar: 'calendar', screener: 'screener',
};

// The right panel: whichever widget is open in the widget bar. The watchlist carries the symbol's
// details under it, split by a draggable line in TradingView; here a fixed split for now.
export function RightPanel({ onNewAlert, onAddSymbol, onSignIn }: { onNewAlert(): void; onAddSymbol(): void; onSignIn(): void }) {
  const t = useT();
  const open = usePanels((s) => s.open);
  if (!open) return <aside className="ws-panel" hidden />;
  return (
    <aside className="ws-panel" aria-label={t(TITLES[open])}>
      {open === 'watchlist' ? (
        <>
          <Watchlist onAdd={onAddSymbol} />
          <SymbolDetails />
        </>
      ) : open === 'data' ? <DataWindow />
        : open === 'news' ? <NewsPanel />
        : open === 'calendar' ? <CalendarPanel />
        : open === 'alerts' ? <AlertsPanel onNew={onNewAlert} />
        : open === 'objects' ? <ObjectTree />
        : open === 'signals' ? <SignalsPanel onSignIn={onSignIn} />
        : open === 'ai' ? <AiPanel onSignIn={onSignIn} />
        : (
          <section className="panel-generic">
            <header className="panel-head"><span className="fa">{t(TITLES[open])}</span></header>
            <Screener compact />
          </section>
        )}
    </aside>
  );
}
