import { useEffect, useRef } from 'react';
import { createPortal } from 'react-dom';
import { useChartBus } from './chartBus';
import { usePrefs, useT } from '../state/prefs';
import { useStudies } from '../state/studies';
import { useDrawing } from '../state/drawing';
import { formatPrice } from './format';
import { Icon } from '../ui/Icon';
import { digitsOf } from '../state/market';
import { copyChartImage, exportBarsCsv } from './actions';
import { useChartSettings } from '../state/chartSettings';
import { usePage } from '../state/page';

// The chart's right-click menu, TradingView's first items: reset the view, an alert or a horizontal
// line at the price under the pointer, copy that price, clear drawings or indicators.
export function ChartContextMenu({ at, onClose, onAlertAt }: { at: { x: number; y: number; price: number | null }; onClose(): void; onAlertAt?(price: number | null): void }) {
  const t = useT();
  const ref = useRef<HTMLDivElement>(null);
  const symbol = usePrefs((s) => s.symbol);
  const { api, drawingLayer: layer, invert, setInvert, scale, setScale } = useChartBus();
  const settings = useChartSettings();
  const openPage = usePage((s) => s.open);
  const clearStudies = useStudies((s) => s.clear);
  const clearDrawings = useDrawing((s) => s.clearAll);
  const digits = digitsOf(symbol);
  const price = at.price != null ? Number(at.price.toFixed(digits)) : null;
  const label = price != null ? formatPrice(price, digits) : '';

  useEffect(() => {
    const close = (e: MouseEvent) => { if (!ref.current?.contains(e.target as Node)) onClose(); };
    const esc = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    document.addEventListener('mousedown', close);
    document.addEventListener('keydown', esc);
    return () => { document.removeEventListener('mousedown', close); document.removeEventListener('keydown', esc); };
  }, [onClose]);

  const run = (fn: () => void) => () => { fn(); onClose(); };
  const left = Math.min(at.x, window.innerWidth - 270);
  const top = Math.max(8, Math.min(at.y, window.innerHeight - 520));

  return createPortal(
    <div ref={ref} className="menu dropdown ctx" role="menu" style={{ position: 'fixed', left, top }}>
      <button className="menu-item" onClick={run(() => { api?.timeScale().applyOptions({ barSpacing: 6, rightOffset: 10 }); api?.timeScale().scrollToRealTime(); api?.priceScale('right').applyOptions({ autoScale: true }); })}>
        <Icon name="ph-arrow-counter-clockwise" size="sm" /><span className="fa">{t('contextReset')}</span><kbd className="num">Alt+R</kbd>
      </button>
      <div className="menu-sep" />
      {price != null ? (
        <>
          <button className="menu-item" onClick={run(() => onAlertAt?.(price))}>
            <Icon name="ph-bell" size="sm" /><span className="fa">{t('contextAlert')}</span><span className="num muted">{label}</span>
          </button>
          <button className="menu-item" onClick={run(() => {
            if (!layer) return;
            const list = [...layer.getDrawings(), { type: 'hline', p: price, color: '#2962FF', width: 1 }];
            layer.setDrawings(list, true);
            layer.onChange?.(list);
          })}>
            <Icon name="tool_hline" size="sm" /><span className="fa">{t('contextHline')}</span><span className="num muted">{label}</span>
          </button>
          <button className="menu-item" onClick={run(() => { void navigator.clipboard?.writeText(label.replace(/,/g, '')); })}>
            <Icon name="ph-copy" size="sm" /><span className="fa">{t('contextCopy')}</span><span className="num muted">{label}</span>
          </button>
          <div className="menu-sep" />
        </>
      ) : null}
      <button className="menu-item" onClick={run(() => setInvert(!invert))}><Icon name="ph-arrows-down-up" size="sm" /><span>Invert scale</span>{invert ? <Icon name="ph-check" size="sm" /> : <kbd className="num">Alt+I</kbd>}</button>
      <button className="menu-item" onClick={run(() => setScale(scale === 'indexed' ? 'normal' : 'indexed'))}><Icon name="ph-percent" size="sm" /><span>Indexed to 100</span>{scale === 'indexed' ? <Icon name="ph-check" size="sm" /> : null}</button>
      <button className="menu-item" onClick={run(() => settings.set({ prevClose: !settings.prevClose }))}><Icon name="tool_hline" size="sm" /><span>Previous close line</span>{settings.prevClose ? <Icon name="ph-check" size="sm" /> : null}</button>
      <button className="menu-item" onClick={run(() => settings.set({ events: !settings.events }))}><Icon name="ph-calendar-blank" size="sm" /><span>Economic events</span>{settings.events ? <Icon name="ph-check" size="sm" /> : null}</button>
      <div className="menu-sep" />
      <button className="menu-item" onClick={run(() => { void copyChartImage(); })}><Icon name="ph-camera" size="sm" /><span>Copy chart image</span></button>
      <button className="menu-item" onClick={run(exportBarsCsv)}><Icon name="ph-download-simple" size="sm" /><span>Export chart data (CSV)</span></button>
      <button className="menu-item" onClick={run(() => openPage('help', 'shortcuts'))}><Icon name="ph-keyboard" size="sm" /><span>Keyboard shortcuts</span><kbd className="num">?</kbd></button>
      <div className="menu-sep" />
      <button className="menu-item" onClick={run(clearDrawings)}><Icon name="trash2" size="sm" /><span className="fa">{t('contextRemoveDrawings')}</span></button>
      <button className="menu-item" onClick={run(clearStudies)}><Icon name="ph-trash" size="sm" /><span className="fa">{t('contextRemoveStudies')}</span></button>
    </div>,
    document.body,
  );
}
