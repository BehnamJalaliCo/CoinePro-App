// «Remove drawings» asks first (OLD ChartScreen confirmClear): how many drawings of this symbol go,
// that they do not come back, and that the indicators and the chart's settings stay. Opened by
// `askClearDrawings()` from any menu; the chart view hosts it.
import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { useDrawing } from '../state/drawing';
import { usePrefs } from '../state/prefs';
import { useL } from '../pages/kit';
import { faNum } from '../i18n/time';
import { Icon } from '../ui/Icon';
import { useChartBus } from './chartBus';

const EVENT = 'pc:confirm-clear-drawings';

/** The drawings on the chart now (what «Remove drawings» would take). */
export function drawingCount(): number {
  const layer = useChartBus.getState().drawingLayer as unknown as { getDrawings?(): unknown[] } | null;
  return layer?.getDrawings?.().length ?? 0;
}

/** Asks before removing every drawing of this symbol. */
export function askClearDrawings(): void {
  window.dispatchEvent(new CustomEvent(EVENT));
}

export function ConfirmClearHost() {
  const L = useL();
  const fa = usePrefs((s) => s.lang) === 'fa';
  const [count, setCount] = useState<number | null>(null);
  useEffect(() => {
    const on = () => { const n = drawingCount(); if (n > 0) setCount(n); };
    window.addEventListener(EVENT, on);
    return () => window.removeEventListener(EVENT, on);
  }, []);
  if (count == null) return null;
  const close = () => setCount(null);
  const confirm = () => { useDrawing.getState().clearAll(); close(); };
  const n = fa ? faNum(count) : String(count);
  return createPortal(
    <div className="scrim center" onMouseDown={(e) => { if (e.target === e.currentTarget) close(); }}>
      <div className="dialog tv-small confirm-clear" role="alertdialog" aria-modal="true" aria-label={L('پاک کردن همه‌ی ترسیم‌ها', 'Remove every drawing')} dir={fa ? 'rtl' : 'ltr'}
        onKeyDown={(e) => { if (e.key === 'Escape') { e.stopPropagation(); close(); } }}>
        <header className="dialog-head"><h2 className={fa ? 'fa' : ''}>{L('پاک کردن همه‌ی ترسیم‌ها', 'Remove every drawing')}</h2>
          <button type="button" className="ib" onClick={close} aria-label={L('بستن', 'Close')}><Icon name="ph-x" size="md" /></button></header>
        <div className="tv-small-body">
          <p className={fa ? 'fa' : ''}>{L(`هر ${n} ترسیم این نماد برداشته می‌شود و برنمی‌گردد. اندیکاتورها و تنظیمات چارت دست‌نخورده می‌مانند.`, `All ${n} drawings on this symbol are removed and do not come back. The indicators and the chart's settings are untouched.`)}</p>
        </div>
        <footer className="dialog-foot end">
          <button type="button" className="btn btn-quiet sm" data-act="keep" autoFocus onClick={close}><span className={fa ? 'fa' : ''}>{L('بماند', 'Keep them')}</span></button>
          <button type="button" className="btn btn-danger sm" data-act="clear" onClick={confirm}><span className={fa ? 'fa' : ''}>{L('پاک کنید', 'Remove them')}</span></button>
        </footer>
      </div>
    </div>,
    document.body,
  );
}
