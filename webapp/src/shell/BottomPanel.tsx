import { useEffect, useRef, useState } from 'react';
import { usePanels, type BottomTab } from '../state/panels';
import { useT } from '../state/prefs';
import { Icon } from '../ui/Icon';
import type { StringKey } from '../i18n/strings';
import { Screener } from './Screener';
import { NamaScriptPanel } from './NamaScriptPanel';
import { BacktestPanel } from './BacktestPanel';
import { PaperPanel } from './PaperPanel';
import { NotesTab } from './NotesTab';
import { useL } from '../pages/kit';
import './studies.css';

// Under the chart, where TradingView keeps Pine Editor, the Strategy Tester and the Trading Panel:
// the screener, NamaScript (our Pine), the strategy tester, the trading panel (paper) and notes.
// A tab opens the panel; the open tab closes it. The top edge drags to resize (kept; a double
// click puts the default height back); the buttons minimise, maximise (the panel takes the
// chart's place) and close it.
const TABS: { id: BottomTab; label?: StringKey; fa?: string; en?: string }[] = [
  { id: 'screener', label: 'screener' },
  { id: 'namascript', label: 'namascript' },
  { id: 'backtest', label: 'backtest' },
  { id: 'paper', label: 'paperTrading' },
  { id: 'notes', fa: 'یادداشت‌ها', en: 'Notes' },
];
const KEY = 'pc.dock.v1';
const DEFAULT_H = 300;
const readDock = (): { h: number; max: boolean } => { try { const v = JSON.parse(localStorage.getItem(KEY) ?? 'null') as { h?: number; max?: boolean } | null; return { h: v?.h && v.h >= 120 ? v.h : DEFAULT_H, max: Boolean(v?.max) }; } catch { return { h: DEFAULT_H, max: false }; } };

export function BottomPanel() {
  const t = useT();
  const L = useL();
  const bottom = usePanels((s) => s.bottom);
  const setBottom = usePanels((s) => s.setBottom);
  const [dock, setDock] = useState(readDock);
  const [room, setRoom] = useState(0);
  const section = useRef<HTMLElement>(null);
  const save = (d: { h: number; max: boolean }) => { setDock(d); try { localStorage.setItem(KEY, JSON.stringify(d)); } catch { /* ignore */ } };

  // The room the panel may take: the main column less the tab strip and the time bar.
  useEffect(() => {
    const main = section.current?.parentElement;
    if (!main) return;
    const measure = () => setRoom(Math.max(160, main.clientHeight - 34 - 31));
    measure();
    const ro = new ResizeObserver(measure);
    ro.observe(main);
    return () => ro.disconnect();
  }, []);

  const drag = (e: React.PointerEvent) => {
    if (!bottom) return;
    e.preventDefault();
    const y0 = e.clientY; const h0 = dock.max ? room : dock.h;
    let h = h0;
    const move = (ev: PointerEvent) => { h = Math.max(120, Math.min(room, h0 + (y0 - ev.clientY))); setDock({ h, max: false }); };
    const up = () => { window.removeEventListener('pointermove', move); window.removeEventListener('pointerup', up); save({ h, max: false }); };
    window.addEventListener('pointermove', move);
    window.addEventListener('pointerup', up);
  };
  const height = dock.max ? room : Math.min(dock.h, room || dock.h);
  const label = (x: (typeof TABS)[number]) => (x.label ? t(x.label) : L(x.fa!, x.en!));

  return (
    <section ref={section} className={`ws-bottom${dock.max && bottom ? ' maxed' : ''}`}>
      {bottom ? <div className="dock-grip" onPointerDown={drag} onDoubleClick={() => save({ h: DEFAULT_H, max: false })} title={L('کشیدن برای تغییر اندازه؛ دوبار کلیک برای اندازه‌ی پیش‌فرض', 'Drag to resize; double-click for the default height')} role="separator" aria-orientation="horizontal" /> : null}
      <nav className="ws-bottom-tabs" role="tablist">
        {TABS.map((x) => (
          <button key={x.id} role="tab" aria-selected={bottom === x.id} className={`tab${bottom === x.id ? ' on' : ''}`} onClick={() => setBottom(x.id)}>
            <span className="fa">{label(x)}</span>
          </button>
        ))}
        <span className="ws-spacer" />
        {bottom ? (
          <>
            <button className="ib" onClick={() => save({ ...dock, max: !dock.max })} aria-label={dock.max ? L('بازگرداندن اندازه', 'Restore panel') : L('بزرگ‌ترین اندازه', 'Maximize panel')} title={dock.max ? L('بازگرداندن اندازه', 'Restore panel') : L('بزرگ‌ترین اندازه', 'Maximize panel')}><Icon name={dock.max ? 'ph-arrows-in' : 'ph-arrows-out'} size="sm" /></button>
            <button className="ib" onClick={() => setBottom(bottom)} aria-label="collapse" title={L('کوچک کردن', 'Minimize panel')}><Icon name="ph-caret-down" size="sm" /></button>
            <button className="ib" onClick={() => { save({ ...dock, max: false }); setBottom(bottom); }} aria-label={L('بستن', 'Close panel')} title={L('بستن', 'Close panel')}><Icon name="ph-x" size="sm" /></button>
          </>
        ) : (
          <button className="ib" onClick={() => setBottom('namascript')} aria-label="expand" title={L('باز کردن', 'Open panel')}><Icon name="ph-caret-up" size="sm" /></button>
        )}
      </nav>
      {bottom ? (
        <div className="ws-bottom-body" role="tabpanel" style={{ height }}>
          {bottom === 'screener' ? <Screener /> : bottom === 'namascript' ? <NamaScriptPanel /> : bottom === 'backtest' ? <BacktestPanel /> : bottom === 'notes' ? <NotesTab /> : <PaperPanel />}
        </div>
      ) : null}
    </section>
  );
}
