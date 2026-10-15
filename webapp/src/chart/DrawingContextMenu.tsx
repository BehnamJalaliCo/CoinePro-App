import { useEffect, useLayoutEffect, useRef, useState, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { useChartBus } from './chartBus';
import { usePrefs } from '../state/prefs';
import { useDrawing } from '../state/drawing';
import { Icon } from '../ui/Icon';
import { useL } from '../pages/kit';
import { toolIcon } from './tools';
import { TEXT_TYPES } from '../engine/drawings.js';
import { addAlertFor, alertDetail, applyStyle, bumpDrawings, copySelection, drawingName, saveAsDefault } from './DrawingActions';
import './drawing.css';

/** The timeframe classes «visibility on intervals» switches, in TradingView's order. */
export const TF_CLASSES: { id: string; fa: string; en: string; min: number; max: number }[] = [
  { id: 'seconds', fa: 'ثانیه', en: 'Seconds', min: 1, max: 59 },
  { id: 'minutes', fa: 'دقیقه', en: 'Minutes', min: 1, max: 59 },
  { id: 'hours', fa: 'ساعت', en: 'Hours', min: 1, max: 24 },
  { id: 'days', fa: 'روز', en: 'Days', min: 1, max: 366 },
  { id: 'weeks', fa: 'هفته', en: 'Weeks', min: 1, max: 52 },
  { id: 'months', fa: 'ماه', en: 'Months', min: 1, max: 12 },
];

/** Whether a drawing shows on a timeframe class (its tfVis entry: false, or {on}). */
export const tfOn = (tfVis: unknown, cls: string): boolean => {
  const v = tfVis && typeof tfVis === 'object' ? (tfVis as Record<string, unknown>)[cls] : undefined;
  if (v === false) return false;
  if (v && typeof v === 'object' && (v as { on?: boolean }).on === false) return false;
  return true;
};

// The right-click menu of a drawing (contract 2: opened by `pc:drawing-menu` with the pointer and the
// drawing's index): the tool's name, settings, an alert from it, its text, clone and copy, the
// visual order, visibility on intervals, templates, lock, hide, delete — TradingView's items.
export function DrawingContextMenu() {
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const layer = useChartBus((s) => s.drawingLayer);
  useChartBus((s) => s.drawingVersion);
  const templates = useDrawing((s) => s.templates);
  const [at, setAt] = useState<{ x: number; y: number; index: number } | null>(null);
  const [sub, setSub] = useState<string | null>(null);
  const ref = useRef<HTMLDivElement>(null);
  const [pos, setPos] = useState<{ left: number; top: number; flip: boolean }>({ left: -9999, top: 0, flip: false });

  useEffect(() => {
    const on = (e: Event) => {
      const det = (e as CustomEvent<{ clientX: number; clientY: number; index: number }>).detail;
      const l = useChartBus.getState().drawingLayer;
      if (!det || !l || !l.getDrawings()[det.index]) return;
      // The drawing under the pointer becomes the selection, as on TradingView.
      if (!l.selectedIndices().includes(det.index)) l.selectAt(det.index);
      setSub(null);
      setAt({ x: det.clientX, y: det.clientY, index: det.index });
    };
    window.addEventListener('pc:drawing-menu', on);
    return () => window.removeEventListener('pc:drawing-menu', on);
  }, []);

  useEffect(() => {
    if (!at) return;
    const close = (e: PointerEvent) => { if (!ref.current?.contains(e.target as Node)) setAt(null); };
    const esc = (e: KeyboardEvent) => { if (e.key === 'Escape') setAt(null); };
    const blur = () => setAt(null);
    document.addEventListener('pointerdown', close, true);
    document.addEventListener('keydown', esc);
    window.addEventListener('blur', blur);
    return () => { document.removeEventListener('pointerdown', close, true); document.removeEventListener('keydown', esc); window.removeEventListener('blur', blur); };
  }, [at]);

  useLayoutEffect(() => {
    if (!at || !ref.current) return;
    const w = ref.current.offsetWidth, h = ref.current.offsetHeight;
    const left = Math.max(8, Math.min(at.x, window.innerWidth - w - 8));
    const top = Math.max(8, Math.min(at.y, window.innerHeight - h - 8));
    setPos({ left, top, flip: left + w + 230 > window.innerWidth });
  }, [at]);

  const d = at && layer ? layer.getDrawings()[at.index] : null;
  if (!at || !layer || !d) return null;
  const i = at.index;
  const done = (fn: () => void) => () => { fn(); layer.render(); bumpDrawings(); setAt(null); };
  const row = (icon: string, label: string, onClick: () => void, opts: { kbd?: string; danger?: boolean; disabled?: boolean; act?: string } = {}): ReactNode => (
    <button role="menuitem" className={`dfly-row${opts.danger ? ' danger' : ''}`} disabled={opts.disabled} data-act={opts.act} onClick={onClick} onMouseEnter={() => setSub(null)}>
      <Icon name={icon} size="sm" /><span className="dfly-label">{label}</span>{opts.kbd ? <kbd className="num">{opts.kbd}</kbd> : null}
    </button>
  );
  const subRow = (id: string, icon: string, label: string, body: ReactNode) => (
    <div className="dctx-sub" onMouseEnter={() => setSub(id)}>
      <button role="menuitem" aria-haspopup="menu" aria-expanded={sub === id} className="dfly-row" data-act={id} onClick={() => setSub(sub === id ? null : id)}>
        <Icon name={icon} size="sm" /><span className="dfly-label">{label}</span><Icon name={lang === 'fa' ? 'ph-caret-left' : 'ph-caret-right'} size="sm" className="caret" />
      </button>
      {sub === id ? <div className={`dctx dctx-subm${pos.flip ? ' flip' : ''}`} role="menu" dir={lang === 'fa' ? 'rtl' : 'ltr'}>{body}</div> : null}
    </div>
  );
  const tfVis = d.tfVis;
  const setTf = (cls: string) => {
    const cur = { ...((tfVis && typeof tfVis === 'object') ? tfVis as Record<string, unknown> : {}) };
    const was = cur[cls];
    const on = !tfOn(tfVis, cls);
    cur[cls] = was && typeof was === 'object' ? { ...(was as object), on } : on ? true : false;
    layer.setStyle(i, { tfVis: cur }); layer.render(); bumpDrawings();
  };
  const tpls = templates[d.type] ?? [];

  return createPortal(
    <div ref={ref} className="dctx" role="menu" dir={lang === 'fa' ? 'rtl' : 'ltr'} aria-label={drawingName(d.type, lang)}
      style={{ left: pos.left, top: pos.top }} onContextMenu={(e) => e.preventDefault()}>
      <div className="dctx-head"><Icon name={toolIcon(d.type)} size="sm" /><span>{typeof d.name === 'string' && d.name ? d.name : drawingName(d.type, lang)}</span></div>
      {row('ph-gear', L('تنظیمات…', 'Settings…'), done(() => useDrawing.getState().openSettings(i)), { act: 'settings' })}
      {alertDetail(d) ? row('ph-bell', L('افزودن آلارم روی این ترسیم', 'Add alert on this drawing'), done(() => { addAlertFor(d); }), { act: 'alert' }) : null}
      {TEXT_TYPES.includes(d.type) ? row('ph-pencil-simple', L('ویرایش متن', 'Edit text'), done(() => useChartBus.getState().setTextEdit({ index: i, fresh: false })), { act: 'text' }) : null}
      <div className="dfly-sep" />
      {row('ph-copy', L('تکثیر', 'Clone'), done(() => { layer.clone(i); }), { kbd: 'Ctrl+D', act: 'clone' })}
      {row('ph-copy-simple', L('کپی', 'Copy'), done(() => { copySelection(); }), { kbd: 'Ctrl+C', act: 'copy' })}
      <div className="dfly-sep" />
      {subRow('order', 'ph-stack', L('ترتیب نمایش', 'Visual order'), (
        <>
          {row('ph-selection-foreground', L('آوردن به جلو', 'Bring to front'), done(() => layer.bringToFront(i)), { act: 'front' })}
          {row('ph-caret-up', L('یک لایه جلوتر', 'Bring forward'), done(() => layer.bringForward(i)), { disabled: i >= layer.getDrawings().length - 1, act: 'forward' })}
          {row('ph-caret-down', L('یک لایه عقب‌تر', 'Send backward'), done(() => layer.sendBackward(i)), { disabled: i <= 0, act: 'backward' })}
          {row('ph-selection-background', L('بردن به عقب', 'Send to back'), done(() => layer.sendToBack(i)), { act: 'back' })}
        </>
      ))}
      {subRow('visibility', 'ph-clock', L('نمایش روی تایم‌فریم‌ها', 'Visibility on intervals'), (
        <>
          {TF_CLASSES.map((c) => (
            <button key={c.id} role="menuitemcheckbox" aria-checked={tfOn(tfVis, c.id)} className="dfly-row" data-tf={c.id} onClick={() => setTf(c.id)}>
              {tfOn(tfVis, c.id) ? <Icon name="ph-check" size="sm" /> : <span className="dfly-nocheck" />}<span className="dfly-label">{L(c.fa, c.en)}</span>
            </button>
          ))}
          <div className="dfly-sep" />
          {row('ph-sliders-horizontal', L('بازه‌ها…', 'Ranges…'), done(() => useDrawing.getState().openSettings(i, 'visibility')))}
        </>
      ))}
      {subRow('template', 'ph-squares-four', L('قالب', 'Template'), (
        <>
          {tpls.map((x) => row('ph-squares-four', x.name, done(() => applyStyle(i, x.style))))}
          {tpls.length ? <div className="dfly-sep" /> : null}
          {row('ph-bookmark-simple', L('ذخیره به‌عنوان پیش‌فرض', 'Save as default'), done(() => saveAsDefault(d)), { act: 'default' })}
          {row('ph-floppy-disk', L('ذخیره‌ی قالب…', 'Save template as…'), done(() => useDrawing.getState().openSettings(i, 'template')))}
        </>
      ))}
      <div className="dfly-sep" />
      {row(d.locked ? 'unlock' : 'lock', d.locked ? L('بازکردن قفل', 'Unlock') : L('قفل', 'Lock'), done(() => layer.toggleLock(i)), { kbd: 'Ctrl+L', act: 'lock' })}
      {row(d.visible === false ? 'ph-eye' : 'eye_off', d.visible === false ? L('نمایش', 'Show') : L('پنهان', 'Hide'), done(() => layer.toggleVisible(i)), { act: 'hide' })}
      <div className="dfly-sep" />
      {row('ph-trash', L('حذف', 'Remove'), done(() => { if (layer.selectedIndices().length > 1 && layer.selectedIndices().includes(i)) layer.removeSelected(); else layer.removeAt(i); }), { kbd: 'Del', danger: true, act: 'remove' })}
    </div>,
    document.body,
  );
}
