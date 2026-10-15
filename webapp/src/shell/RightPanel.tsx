import { lazy, Suspense, useRef, useState, type ComponentType } from 'react';
import { usePanels, type PanelId } from '../state/panels';
import { usePrefs } from '../state/prefs';
import { Watchlist } from './Watchlist';
import { SymbolDetails } from './SymbolDetails';
import { DataWindow } from './DataWindow';
import { AlertsPanel } from './AlertsPanel';
import { ObjectTree } from './ObjectTree';
import { Screener } from './Screener';
import './panels-x.css';

// The widgets the panel opens less often load when first opened, so the first paint stays light.
const later = <P extends object>(load: () => Promise<Record<string, unknown>>, name: string) =>
  lazy(() => load().then((m) => ({ default: m[name] as ComponentType<P> })));
const NewsPanel = later(() => import('./NewsPanel'), 'NewsPanel');
const CalendarPanel = later(() => import('./CalendarPanel'), 'CalendarPanel');
const SignalsPanel = later<{ onSignIn(): void }>(() => import('./SignalsPanel'), 'SignalsPanel');
const AiPanel = later<{ onSignIn(): void }>(() => import('./AiPanel'), 'AiPanel');
const Hotlists = later(() => import('./Hotlists'), 'Hotlists');
const DomPanel = later(() => import('./DomPanel'), 'DomPanel');
const NotesPanel = later(() => import('./NotesPanel'), 'NotesPanel');
const NotificationLog = later(() => import('./NotificationLog'), 'NotificationLog');
const OrderPanel = later(() => import('./OrderPanel'), 'OrderPanel');

/** Every widget's name, in both languages (the widget bar's tooltips and the panel's label). */
export const PANEL_TITLES: Record<PanelId, { fa: string; en: string }> = {
  watchlist: { fa: 'دیده‌بان، جزئیات و اخبار', en: 'Watchlist, details and news' },
  alerts: { fa: 'هشدارها', en: 'Alerts' },
  signals: { fa: 'سیگنال‌ها', en: 'Signals' },
  ai: { fa: 'تحلیل هوشمند', en: 'AI analysis' },
  objects: { fa: 'درخت اشیا', en: 'Object tree' },
  data: { fa: 'پنجره‌ی داده', en: 'Data window' },
  news: { fa: 'اخبار', en: 'News' },
  calendar: { fa: 'تقویم اقتصادی', en: 'Economic calendar' },
  screener: { fa: 'اسکرینر', en: 'Screener' },
  hotlists: { fa: 'پرتحرک‌ها', en: 'Hotlists' },
  dom: { fa: 'دفتر سفارش (DOM)', en: 'Order book (DOM)' },
  notes: { fa: 'یادداشت‌ها', en: 'Notes' },
  log: { fa: 'گزارش اعلان‌ها', en: 'Notification log' },
  order: { fa: 'پنل سفارش', en: 'Order panel' },
};

// The right panel: whichever widget is open in the widget bar. The watchlist carries the symbol's
// details under it, split by a line the reader drags (kept), and the details fold to their header.
export function RightPanel({ onNewAlert, onAddSymbol, onSignIn, onCompare, onSearch }: { onNewAlert(): void; onAddSymbol(): void; onSignIn(): void; onCompare?(): void; onSearch?(): void }) {
  const lang = usePrefs((s) => s.lang);
  const open = usePanels((s) => s.open);
  const split = usePanels((s) => s.split);
  const folded = usePanels((s) => s.detailsCollapsed);
  const setSplit = usePanels((s) => s.setSplit);
  const aside = useRef<HTMLElement>(null);
  const [dragging, setDragging] = useState(false);
  if (!open) return <aside className="ws-panel" hidden />;
  const title = PANEL_TITLES[open][lang === 'fa' ? 'fa' : 'en'];

  const startDrag = (e: React.PointerEvent) => {
    const box = aside.current?.getBoundingClientRect();
    if (!box) return;
    e.preventDefault();
    (e.target as HTMLElement).setPointerCapture?.(e.pointerId);
    setDragging(true);
    const move = (ev: PointerEvent) => setSplit((ev.clientY - box.top) / box.height);
    const up = () => { setDragging(false); window.removeEventListener('pointermove', move); window.removeEventListener('pointerup', up); };
    window.addEventListener('pointermove', move);
    window.addEventListener('pointerup', up);
  };

  return (
    <aside className="ws-panel" aria-label={title} ref={aside} data-panel={open}>
      {open === 'watchlist' ? (
        <>
          <div className="wl-pane" style={folded ? { flex: '1 1 0', minHeight: 0, display: 'flex', flexDirection: 'column' } : { height: `${split * 100}%`, minHeight: 0, display: 'flex', flexDirection: 'column', flex: 'none' }}>
            <Watchlist onAdd={onAddSymbol} />
          </div>
          {folded ? null : (
            <div className={`wl-split${dragging ? ' dragging' : ''}`} role="separator" aria-orientation="horizontal" aria-valuenow={Math.round(split * 100)} tabIndex={0}
              onPointerDown={startDrag} onDoubleClick={() => setSplit(0.58)}
              onKeyDown={(e) => { if (e.key === 'ArrowUp') setSplit(split - 0.03); if (e.key === 'ArrowDown') setSplit(split + 0.03); }} />
          )}
          <SymbolDetails onCompare={onCompare} onSearch={onSearch} />
        </>
      ) : (<Suspense fallback={<div className="empty fa">…</div>}>{open === 'data' ? <DataWindow />
        : open === 'news' ? <NewsPanel />
        : open === 'calendar' ? <CalendarPanel />
        : open === 'alerts' ? <AlertsPanel onNew={onNewAlert} />
        : open === 'objects' ? <ObjectTree />
        : open === 'signals' ? <SignalsPanel onSignIn={onSignIn} />
        : open === 'ai' ? <AiPanel onSignIn={onSignIn} />
        : open === 'hotlists' ? <Hotlists />
        : open === 'dom' ? <DomPanel />
        : open === 'notes' ? <NotesPanel />
        : open === 'log' ? <NotificationLog />
        : open === 'order' ? <OrderPanel />
        : (
          <section className="panel-generic">
            <header className="panel-head"><span className="fa">{title}</span></header>
            <Screener compact />
          </section>
        )}</Suspense>)}
    </aside>
  );
}
