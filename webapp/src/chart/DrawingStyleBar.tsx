import { useCallback, useEffect, useLayoutEffect, useRef, useState, type ReactNode } from 'react';
import { useChartBus } from './chartBus';
import { useT, usePrefs } from '../state/prefs';
import { useDrawing } from '../state/drawing';
import { Icon } from '../ui/Icon';
import { useL } from '../pages/kit';
import { TEXT_TYPES, type DrawingObject } from '../engine/drawings.js';
import { addAlertFor, alertDetail, applyStyle, bumpDrawings, copySelection, drawingName, saveAsDefault, styleOf } from './DrawingActions';
import { DrawingContextMenu } from './DrawingContextMenu';
import { DrawingSettingsDialog } from './DrawingSettingsDialog';
import './drawing.css';

// TradingView's floating toolbar over a selected drawing: its name, templates, line colour, fill,
// text style, width and line style, the exact price of a horizontal line, the stats of a sloped
// one, then settings, alert, lock, clone, visual order, more, and delete. Every change is one undo
// step in the engine. It sits just above (or below) the selection, never over it, and can be
// dragged by its grip to stay where the reader puts it.
export const COLORS = ['#2962FF', '#F23645', '#089981', '#FF9800', '#9C27B0', '#00BCD4', '#787B86', '#000000'];
const MORE_COLORS = ['#E91E63', '#FFEB3B', '#4CAF50', '#673AB7', '#795548', '#FFFFFF'];
const WIDTHS = [1, 2, 3, 4];
const STYLES: { id: 'solid' | 'dashed' | 'dotted'; dash: string }[] = [{ id: 'solid', dash: '' }, { id: 'dashed', dash: '6 4' }, { id: 'dotted', dash: '2 3' }];
/** Shapes with a fill colour and opacity. */
export const FILL_TYPES = ['rect', 'rotrect', 'circle', 'ellipse', 'triangle', 'tripattern'];
/** Drawings with one exact price the bar can type in. */
const PRICE_TYPES = ['hline', 'hray', 'pricelabel', 'crossline'];
const TEXTLESS_LINE = ['text', 'anchoredtext', 'emoji', 'table', 'comment'];

/** Δprice, %, pips or ticks, bars and duration from a drawing's first to last point (or null). */
export function drawingStats(d: DrawingObject, symbol: string, digits: number, iv: number | null): { dp: number; pct: number; steps: number; unit: 'pips' | 'ticks'; bars: number | null; dur: string } | null {
  const pts = Array.isArray(d.pts) ? (d.pts as { t: number; p: number }[]) : (d.p0 && d.p1 ? [d.p0 as { t: number; p: number }, d.p1 as { t: number; p: number }] : []);
  if (pts.length < 2) return null;
  const a = pts[0]!, b = pts[pts.length - 1]!;
  if (typeof a.p !== 'number' || typeof b.p !== 'number' || a.p === b.p) return null;
  const dp = b.p - a.p, pct = a.p ? (dp / a.p) * 100 : 0;
  const fx = /^[A-Z]{6}$/.test(symbol);
  const step = fx ? 10 ** -(Math.max(1, digits) - 1) : 10 ** -digits;
  const dt = Math.abs((b.t ?? 0) - (a.t ?? 0));
  const days = Math.floor(dt / 86400), hours = Math.floor((dt % 86400) / 3600), mins = Math.floor((dt % 3600) / 60);
  const dur = days ? (hours && days < 7 ? `${days}d ${hours}h` : `${days}d`) : hours ? (mins && hours < 6 ? `${hours}h ${mins}m` : `${hours}h`) : mins ? `${mins}m` : '';
  return { dp, pct, steps: Math.round(dp / step), unit: fx ? 'pips' : 'ticks', bars: iv ? Math.round(dt / iv) : null, dur };
}

export function DrawingStyleBar() {
  return (
    <>
      <StyleBar />
      <DrawingContextMenu />
      <DrawingSettingsDialog />
    </>
  );
}

type Pop = 'tpl' | 'color' | 'fill' | 'width' | 'style' | 'text' | 'bg' | 'order' | 'more' | 'dir' | null;

function StyleBar() {
  const t = useT();
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const symbol = usePrefs((s) => s.symbol);
  const layer = useChartBus((s) => s.drawingLayer);
  const api = useChartBus((s) => s.api);
  const index = useChartBus((s) => s.selectedDrawing);
  const version = useChartBus((s) => s.drawingVersion);
  const barPos = useDrawing((s) => s.barPos);
  const templates = useDrawing((s) => s.templates);
  const defaults = useDrawing((s) => s.defaults);
  const [open, setOpen] = useState<Pop>(null);
  const [tplName, setTplName] = useState('');
  const [, force] = useState(0);
  const ref = useRef<HTMLDivElement>(null);
  const [auto, setAuto] = useState<{ x: number; y: number; up: boolean } | null>(null);
  const [drag, setDrag] = useState<{ dx: number; dy: number; x: number; y: number } | null>(null);
  useEffect(() => { setOpen(null); }, [index]);

  const d = layer && index >= 0 ? layer.getDrawings()[index] : null;

  // Where the bar goes: centred over the drawing's points, above them when there is room, else
  // below — never on top of the drawing.
  const place = useCallback(() => {
    const bar = ref.current, host = bar?.parentElement;
    if (!bar || !host || !layer || !d) return;
    const W = host.clientWidth, H = host.clientHeight, bw = bar.offsetWidth, bh = bar.offsetHeight;
    const hp = (layer as unknown as { _handlePoints(d: DrawingObject): { x: number | null; y: number | null }[] })._handlePoints(d).filter((p) => p.x != null && p.y != null) as { x: number; y: number }[];
    let x0 = W / 2, x1 = W / 2, y0 = H / 3, y1 = H / 3;
    if (hp.length) { x0 = Math.min(...hp.map((p) => p.x)); x1 = Math.max(...hp.map((p) => p.x)); y0 = Math.min(...hp.map((p) => p.y)); y1 = Math.max(...hp.map((p) => p.y)); }
    if (d.type === 'hline' || d.type === 'pricelabel' || d.type === 'crossline') { x0 = W * 0.3; x1 = W * 0.6; }
    if (d.type === 'vline') { y0 = H * 0.25; y1 = H * 0.25; }
    let top = y0 - bh - 16;
    let up = false;
    if (top < 8) { top = y1 + 16; up = top + bh + 260 > H; }
    if (top + bh > H - 8) { top = 8; }
    const left = Math.max(8, Math.min(W - bw - 8, (x0 + x1) / 2 - bw / 2));
    setAuto((p) => (p && Math.abs(p.x - left) < 1 && Math.abs(p.y - top) < 1 && p.up === up ? p : { x: left, y: top, up }));
  }, [layer, d]);

  useLayoutEffect(() => { place(); }, [place, version, index, open]);
  useEffect(() => {
    if (!api || !d) return;
    let raf = 0;
    const on = () => { if (!raf) raf = requestAnimationFrame(() => { raf = 0; place(); }); };
    api.timeScale().subscribeVisibleLogicalRangeChange(on);
    window.addEventListener('resize', on);
    return () => { api.timeScale().unsubscribeVisibleLogicalRangeChange(on); window.removeEventListener('resize', on); if (raf) cancelAnimationFrame(raf); };
  }, [api, d, place]);

  if (!layer || !d) return null;
  const host = ref.current?.parentElement;
  const clampPos = (p: { x: number; y: number }) => {
    const W = host?.clientWidth ?? 800, H = host?.clientHeight ?? 600, bw = ref.current?.offsetWidth ?? 300, bh = ref.current?.offsetHeight ?? 40;
    return { x: Math.max(0, Math.min(W - bw, p.x)), y: Math.max(0, Math.min(H - bh, p.y)) };
  };
  const pos = drag ? { x: drag.x, y: drag.y } : barPos ? clampPos(barPos) : auto ?? { x: -9999, y: 8 };
  const up = barPos ? (host ? pos.y > host.clientHeight / 2 : false) : Boolean(auto?.up);

  const color = typeof d.color === 'string' ? d.color : '#2962FF';
  const width = typeof d.width === 'number' ? d.width : 2;
  const style = d.lineStyle === 'dotted' ? 'dotted' : d.lineStyle === 'dashed' || d.dashed ? 'dashed' : 'solid';
  const sel = layer.selectedIndices();
  const targets = sel.includes(index) ? sel : [index];
  const set = (st: Record<string, unknown>, close = true) => { layer.setStyle(index, st); layer.render(); if (close) setOpen(null); force((n) => n + 1); };
  const tog = (p: Pop) => setOpen(open === p ? null : p);
  const isText = TEXT_TYPES.includes(d.type);
  const hasLine = !['text', 'anchoredtext', 'emoji'].includes(d.type);
  const digits = layer.digits;
  const stats = drawingStats(d, symbol, digits, layer._barInterval());
  const price = d.type === 'hline' ? (d.p as number) : PRICE_TYPES.includes(d.type) && d.p0 ? (d.p0 as { p: number }).p : null;
  const tpls = templates[d.type] ?? [];
  const popCls = (extra = '') => `dsb-pop${extra ? ` ${extra}` : ''}${up ? ' up' : ''}`;
  const run = (fn: () => void) => () => { fn(); setOpen(null); bumpDrawings(); };

  const setPrice = (v: number) => {
    if (!Number.isFinite(v)) return;
    if (d.type === 'hline') set({ p: v }, false);
    else if (d.p0) { const p0 = d.p0 as { t: number; p: number }; set({ p0: { t: p0.t, p: v }, ...(d.p1 ? { p1: { t: (d.p1 as { t: number }).t, p: v } } : {}) }, false); }
  };

  const item = (icon: string, label: string, onClick: () => void, extra?: ReactNode, disabled = false): ReactNode => (
    <button className="dfly-row" onClick={onClick} disabled={disabled}><Icon name={icon} size="sm" /><span className="dfly-label">{label}</span>{extra}</button>
  );

  return (
    <div ref={ref} className="draw-style-bar dsb" role="toolbar" aria-label={L('سبک ترسیم', 'Drawing style')} dir={lang === 'fa' ? 'rtl' : 'ltr'}
      style={{ left: pos.x, top: pos.y }} onMouseDown={(e) => e.stopPropagation()} onPointerDown={(e) => e.stopPropagation()}>
      <span className="dsb-grip" title={L('جابه‌جایی نوار (دوبار کلیک: کنار ترسیم)', 'Move the toolbar (double click: back beside the drawing)')}
        onPointerDown={(e) => { (e.target as HTMLElement).setPointerCapture?.(e.pointerId); setDrag({ dx: e.clientX - pos.x, dy: e.clientY - pos.y, x: pos.x, y: pos.y }); }}
        onPointerMove={(e) => { if (drag) setDrag({ ...drag, ...clampPos({ x: e.clientX - drag.dx, y: e.clientY - drag.dy }) }); }}
        onPointerUp={() => { if (drag) { useDrawing.getState().setBarPos({ x: drag.x, y: drag.y }); setDrag(null); } }}
        onDoubleClick={() => useDrawing.getState().setBarPos(null)}>
        <Icon name="ph-dots-six-vertical" size="sm" />
      </span>
      <span className="dsb-name" title={drawingName(d.type, lang)}>{typeof d.name === 'string' && d.name ? d.name : drawingName(d.type, lang)}</span>
      <div className="dsb-tpl">
        <button className="ib" title={L('قالب‌ها', 'Templates')} aria-expanded={open === 'tpl'} onClick={() => tog('tpl')}><Icon name="ph-squares-four" size="sm" /></button>
        {open === 'tpl' ? (
          <div className={popCls('menuish')}>
            {tpls.length ? <div className="dfly-sec">{L('قالب‌های ذخیره‌شده', 'Saved templates')}</div> : null}
            {tpls.map((x) => (
              <div key={x.name} className="dsb-tpl-row">
                {item('ph-squares-four', x.name, () => { set(x.style); bumpDrawings(); })}
                <button className="dfly-mini" title={L('حذف قالب', 'Delete template')} aria-label={L('حذف قالب', 'Delete template')} onClick={() => useDrawing.getState().deleteTemplate(d.type, x.name)}><Icon name="ph-x" size="sm" /></button>
              </div>
            ))}
            <form className="dsb-form" onSubmit={(e) => { e.preventDefault(); const n = tplName.trim(); if (!n) return; useDrawing.getState().saveTemplate(d.type, n, styleOf(d)); setTplName(''); }}>
              <input value={tplName} onChange={(e) => setTplName(e.target.value)} placeholder={L('نام قالب تازه', 'New template name')} aria-label={L('نام قالب', 'Template name')} />
              <button className="ib" type="submit" title={L('ذخیره‌ی قالب', 'Save template')} aria-label={L('ذخیره‌ی قالب', 'Save template')}><Icon name="ph-floppy-disk" size="sm" /></button>
            </form>
            <div className="dfly-sep" />
            {item('ph-bookmark-simple', L('ذخیره به‌عنوان پیش‌فرض این ابزار', 'Save as default'), run(() => saveAsDefault(d)))}
            {item('ph-arrow-counter-clockwise', L('برگرداندن به پیش‌فرض', 'Apply default'), run(() => applyStyle(index, defaults[d.type] ?? { color: useDrawing.getState().color, width: 2, lineStyle: 'solid', dashed: false })))}
          </div>
        ) : null}
      </div>
      <span className="sep" />
      <div className="dsb-group">
        <button className="ib" title={hasLine ? L('رنگ خط', 'Line colour') : L('رنگ', 'Colour')} aria-expanded={open === 'color'} onClick={() => tog('color')}>
          <span className="dsb-color"><Icon name={isText ? 'ph-text-t' : 'ph-pencil-simple'} size="sm" /><i style={{ background: color }} /></span>
        </button>
        {open === 'color' ? <div className={popCls()}>{[...COLORS, ...MORE_COLORS].map((c) => <button key={c} className={`swatch${c === color ? ' on' : ''}`} style={{ background: c }} onClick={() => set({ color: c })} aria-label={c} />)}<input type="color" value={color} onChange={(e) => set({ color: e.target.value }, false)} aria-label={L('رنگ دلخواه', 'Custom colour')} /></div> : null}
      </div>
      {FILL_TYPES.includes(d.type) ? (
        <div className="dsb-fill">
          <button className="ib" title={L('رنگ پس‌زمینه', 'Fill colour')} aria-expanded={open === 'fill'} onClick={() => tog('fill')}>
            <span className="dsb-color"><Icon name="ph-paint-bucket" size="sm" /><i style={{ background: typeof d.fill === 'string' ? d.fill : color, opacity: typeof d.fillOpacity === 'number' ? Math.max(0.25, d.fillOpacity) : 0.5 }} /></span>
          </button>
          {open === 'fill' ? (
            <div className={popCls()}>
              {[...COLORS, ...MORE_COLORS].map((c) => <button key={c} className={`swatch${c === d.fill ? ' on' : ''}`} style={{ background: c }} onClick={() => set({ fill: c }, false)} aria-label={c} />)}
              <label className="dsb-op">{L('شفافیت', 'Opacity')}<input type="range" min={0} max={1} step={0.05} value={typeof d.fillOpacity === 'number' ? d.fillOpacity : d.type === 'rect' ? 0.18 : 0.1} onChange={(e) => set({ fillOpacity: Number(e.target.value) }, false)} /><span className="num">{Math.round((typeof d.fillOpacity === 'number' ? d.fillOpacity : d.type === 'rect' ? 0.18 : 0.1) * 100)}%</span></label>
            </div>
          ) : null}
        </div>
      ) : null}
      {isText || d.type === 'emoji' ? (
        <>
          <div className="dsb-txt">
            <button className="ib num" title={L('اندازه‌ی متن', 'Font size')} aria-expanded={open === 'text'} onClick={() => tog('text')}>{Number(d.fontSize ?? (d.type === 'emoji' ? 28 : 12))}</button>
            {open === 'text' ? <div className={popCls('col')}>{(d.type === 'emoji' ? [16, 20, 28, 36, 48, 64] : [10, 11, 12, 14, 16, 20, 24, 28, 32, 40]).map((n) => <button key={n} className={`dsb-line num${Number(d.fontSize ?? 12) === n ? ' on' : ''}`} onClick={() => set({ fontSize: n })}>{n}</button>)}</div> : null}
          </div>
          {d.type !== 'emoji' ? (
            <>
              <button className={`ib txt-b${d.bold ? ' on' : ''}`} title={L('پررنگ', 'Bold')} aria-pressed={Boolean(d.bold)} onClick={() => set({ bold: !d.bold })}><Icon name="ph-text-b" size="sm" /></button>
              <button className={`ib txt-i${d.italic ? ' on' : ''}`} title={L('کج', 'Italic')} aria-pressed={Boolean(d.italic)} onClick={() => set({ italic: !d.italic })}><Icon name="ph-text-italic" size="sm" /></button>
              <div className="dsb-bg">
                <button className={`ib${d.bg || d.border ? ' on' : ''}`} title={L('پس‌زمینه و حاشیه', 'Background and border')} aria-expanded={open === 'bg'} onClick={() => tog('bg')}><Icon name="ph-square" size="sm" /></button>
                {open === 'bg' ? (
                  <div className={popCls('menuish')}>
                    <label className="dfly-row dfly-check"><input type="checkbox" checked={Boolean(d.bg)} onChange={(e) => set({ bg: e.target.checked ? '#1E222D' : undefined }, false)} /><span className="dfly-label">{L('پس‌زمینه', 'Background')}</span>{d.bg ? <input type="color" value={String(d.bg)} onChange={(e) => set({ bg: e.target.value }, false)} aria-label={L('رنگ پس‌زمینه', 'Background colour')} /> : null}</label>
                    {d.bg ? <label className="dfly-row dfly-check"><span className="dfly-label">{L('شفافیت پس‌زمینه', 'Background opacity')}</span><input type="range" min={0} max={1} step={0.05} value={typeof d.bgOpacity === 'number' ? d.bgOpacity : 0.9} onChange={(e) => set({ bgOpacity: Number(e.target.value) }, false)} /></label> : null}
                    <label className="dfly-row dfly-check"><input type="checkbox" checked={Boolean(d.border)} onChange={(e) => set({ border: e.target.checked ? color : undefined }, false)} /><span className="dfly-label">{L('حاشیه', 'Border')}</span>{d.border ? <input type="color" value={String(d.border)} onChange={(e) => set({ border: e.target.value }, false)} aria-label={L('رنگ حاشیه', 'Border colour')} /> : null}</label>
                    {d.type !== 'text' && d.type !== 'anchoredtext' ? <label className="dfly-row dfly-check"><span className="dfly-label">{L('رنگ متن', 'Text colour')}</span><input type="color" value={typeof d.textColor === 'string' ? d.textColor : '#ffffff'} onChange={(e) => set({ textColor: e.target.value }, false)} /></label> : null}
                  </div>
                ) : null}
              </div>
              <button className="ib" title={L('ویرایش متن', 'Edit text')} onClick={() => useChartBus.getState().setTextEdit({ index, fresh: false })}><Icon name="ph-pencil-simple" size="sm" /></button>
            </>
          ) : null}
        </>
      ) : null}
      {!TEXTLESS_LINE.includes(d.type) && !(isText && d.type !== 'callout' && d.type !== 'pricenote') ? (
        <>
          <div className="dsb-group">
            <button className="ib num" title={L('ضخامت خط', 'Line width')} aria-expanded={open === 'width'} onClick={() => tog('width')}>{width}px</button>
            {open === 'width' ? <div className={popCls('col')}>{WIDTHS.map((w) => <button key={w} className={`dsb-line${w === width ? ' on' : ''}`} onClick={() => set({ width: w })}><svg width="40" height="10"><line x1="2" x2="38" y1="5" y2="5" stroke="currentColor" strokeWidth={w} /></svg></button>)}</div> : null}
          </div>
          <div className="dsb-group">
            <button className="ib" title={L('سبک خط', 'Line style')} aria-expanded={open === 'style'} onClick={() => tog('style')}><svg width="24" height="10"><line x1="2" x2="22" y1="5" y2="5" stroke="currentColor" strokeWidth="2" strokeDasharray={STYLES.find((x) => x.id === style)!.dash} /></svg></button>
            {open === 'style' ? <div className={popCls('col')}>{STYLES.map((x) => <button key={x.id} className={`dsb-line${x.id === style ? ' on' : ''}`} title={x.id === 'solid' ? L('توپر', 'Solid') : x.id === 'dashed' ? L('خط‌چین', 'Dashed') : L('نقطه‌چین', 'Dotted')} onClick={() => set({ lineStyle: x.id, dashed: x.id === 'dashed' })}><svg width="40" height="10"><line x1="2" x2="38" y1="5" y2="5" stroke="currentColor" strokeWidth="2" strokeDasharray={x.dash} /></svg></button>)}</div> : null}
          </div>
        </>
      ) : null}
      {d.type === 'arrowdir' ? (
        <div className="dsb-dir">
          <button className="ib" title={L('جهت پیکان', 'Arrow direction')} aria-expanded={open === 'dir'} onClick={() => tog('dir')}><Icon name={`ph-arrow-${String(d.dir ?? 'up')}`} size="sm" /></button>
          {open === 'dir' ? <div className={popCls()}>{(['up', 'down', 'left', 'right'] as const).map((k) => <button key={k} className={`ib${(d.dir ?? 'up') === k ? ' on' : ''}`} onClick={() => set({ dir: k })} aria-label={k}><Icon name={`ph-arrow-${k}`} size="sm" /></button>)}</div> : null}
        </div>
      ) : null}
      {price != null ? (
        <input key={`${index}-${price}`} className="dsb-price num" type="text" inputMode="decimal" defaultValue={price.toFixed(digits)} title={L('قیمت دقیق (Enter)', 'Exact price (Enter)')} aria-label={L('قیمت', 'Price')}
          onKeyDown={(e) => { if (e.key === 'Enter') { setPrice(parseFloat((e.target as HTMLInputElement).value.replace(/,/g, ''))); (e.target as HTMLInputElement).blur(); } e.stopPropagation(); }}
          onBlur={(e) => setPrice(parseFloat(e.target.value.replace(/,/g, '')))} />
      ) : null}
      {stats ? (
        <span className={`dsb-stats ${stats.dp >= 0 ? 'up' : 'down'}`} title={L('آمار: تغییر قیمت · درصد · پیپ/تیک · بار · مدت', 'Stats: price change · % · pips/ticks · bars · time')}>
          <span>{stats.dp >= 0 ? '▲' : '▼'}{Math.abs(stats.dp).toFixed(digits)}</span>
          <span>{stats.pct >= 0 ? '+' : '−'}{Math.abs(stats.pct).toFixed(2)}%</span>
          <span>{Math.abs(stats.steps).toLocaleString('en-US')} {stats.unit}</span>
          {stats.bars != null ? <span className="muted">{Math.abs(stats.bars)} bars</span> : null}
          {stats.dur ? <span className="muted">{stats.dur}</span> : null}
        </span>
      ) : null}
      <span className="sep" />
      <button className="ib" title={L('تنظیمات', 'Settings')} data-act="settings" onClick={() => useDrawing.getState().openSettings(index)}><Icon name="ph-gear" size="sm" /></button>
      {alertDetail(d) ? <button className="ib" title={L('افزودن آلارم روی این ترسیم', 'Add alert on this drawing')} data-act="alert" onClick={() => addAlertFor(d)}><Icon name="ph-bell" size="sm" /></button> : null}
      <button className={`ib${d.locked ? ' on' : ''}`} title={d.locked ? L('بازکردن قفل', 'Unlock') : L('قفل', 'Lock')} data-act="lock" onClick={() => { layer.toggleLock(index); layer.render(); }}><Icon name={d.locked ? 'lock' : 'unlock'} size="sm" /></button>
      <button className="ib" title={`${L('تکثیر', 'Clone')} · Ctrl+D`} data-act="clone" onClick={() => { layer.clone(index); layer.render(); }}><Icon name="ph-copy" size="sm" /></button>
      <div className="dsb-order">
        <button className="ib" title={L('ترتیب نمایش', 'Visual order')} aria-expanded={open === 'order'} data-act="order" onClick={() => tog('order')}><Icon name="ph-stack" size="sm" /></button>
        {open === 'order' ? (
          <div className={popCls('menuish')}>
            {item('ph-selection-foreground', L('آوردن به جلو', 'Bring to front'), run(() => layer.bringToFront(index)))}
            {item('ph-caret-up', L('یک لایه جلوتر', 'Bring forward'), run(() => layer.bringForward(index)), undefined, index >= layer.getDrawings().length - 1)}
            {item('ph-caret-down', L('یک لایه عقب‌تر', 'Send backward'), run(() => layer.sendBackward(index)), undefined, index <= 0)}
            {item('ph-selection-background', L('بردن به عقب', 'Send to back'), run(() => layer.sendToBack(index)))}
          </div>
        ) : null}
      </div>
      <div className="dsb-more">
        <button className="ib" title={L('بیشتر', 'More')} aria-expanded={open === 'more'} data-act="more" onClick={() => tog('more')}><Icon name="ph-dots-three" size="sm" /></button>
        {open === 'more' ? (
          <div className={popCls('menuish')}>
            {item('ph-copy-simple', L('کپی', 'Copy'), run(() => { copySelection(); }), <kbd className="num">Ctrl+C</kbd>)}
            {item(d.visible === false ? 'ph-eye' : 'eye_off', d.visible === false ? L('نمایش', 'Show') : L('پنهان', 'Hide'), run(() => { layer.toggleVisible(index); layer.render(); }))}
            {item('ph-bookmark-simple', L('ذخیره به‌عنوان پیش‌فرض', 'Save as default'), run(() => saveAsDefault(d)))}
            {item('ph-clock', L('نمایش روی تایم‌فریم‌ها…', 'Visibility on intervals…'), run(() => useDrawing.getState().openSettings(index, 'visibility')))}
            {isText ? item('ph-pencil-simple', L('ویرایش متن', 'Edit text'), run(() => useChartBus.getState().setTextEdit({ index, fresh: false }))) : null}
          </div>
        ) : null}
      </div>
      <button className="ib" title={t('remove')} data-act="remove" onClick={() => { if (targets.length > 1) layer.removeSelected(); else layer.removeAt(index); }}><Icon name="ph-trash" size="sm" /></button>
    </div>
  );
}
