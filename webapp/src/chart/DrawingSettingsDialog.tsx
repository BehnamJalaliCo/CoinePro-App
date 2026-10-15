import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { useChartBus } from './chartBus';
import { usePrefs } from '../state/prefs';
import { useDrawing } from '../state/drawing';
import { Icon } from '../ui/Icon';
import { useL } from '../pages/kit';
import { TEXT_TYPES, type DrawingObject, type DrawingLayer } from '../engine/drawings.js';
import { LEVEL_DEFAULTS, normLevels, type Level } from '../engine/drawtools_ext.js';
import { drawingName, saveAsDefault, styleOf } from './DrawingActions';
import { TF_CLASSES } from './DrawingContextMenu';
import { formatPrice } from './format';
import { EMOJIS } from './tools';
import './drawing.css';

type Tab = 'style' | 'text' | 'coords' | 'visibility' | 'inputs';
type Rec = Record<string, unknown>;
type Pt = { t: number; p: number };

/** Lines and shapes that may carry a short text (TradingView's Text tab on them). */
export const LINE_TEXT_TYPES = ['trend', 'ray', 'extline', 'hline', 'hray', 'vline', 'rect'];
const FILL = ['rect', 'rotrect', 'circle', 'ellipse', 'triangle', 'tripattern'];
const NO_LINE = ['text', 'anchoredtext', 'emoji', 'table', 'comment', 'ghostfeed'];
const EXTEND = ['trend', 'ray', 'rect', 'fib', 'fibext'];
const POSITION = ['longshort', 'short'];
const WRAP = ['text', 'anchoredtext', 'comment'];
const isPt = (v: unknown): v is Pt => !!v && typeof (v as Pt).t === 'number' && typeof (v as Pt).p === 'number';
const num = (v: unknown, f: number) => (typeof v === 'number' && Number.isFinite(v) ? v : f);

/**
 * The text controls a drawing's Text tab and the quick text editor share: the text (or a table's
 * cells), colour, size, bold, italic, alignment, wrap, background and border.
 */
export function TextFields({ d, set, autoFocus }: { d: Rec & { type: string }; set(st: Rec): void; autoFocus?: boolean }) {
  const L = useL();
  const area = useRef<HTMLTextAreaElement>(null);
  useEffect(() => { if (autoFocus) requestAnimationFrame(() => area.current?.focus()); }, [autoFocus]);
  const isTable = d.type === 'table';
  const colourKey = d.type === 'text' || d.type === 'anchoredtext' || LINE_TEXT_TYPES.includes(d.type) ? (LINE_TEXT_TYPES.includes(d.type) ? 'textColor' : 'color') : 'textColor';
  const colour = typeof d[colourKey] === 'string' ? (d[colourKey] as string) : (colourKey === 'textColor' && !LINE_TEXT_TYPES.includes(d.type) ? '#ffffff' : (typeof d.color === 'string' ? d.color : '#2962FF'));
  const cells: string[][] = Array.isArray(d.cells) ? (d.cells as unknown[][]).map((r) => (Array.isArray(r) ? r.map((c) => String(c ?? '')) : [''])) : [['', ''], ['', '']];
  const cols = Math.max(1, ...cells.map((r) => r.length));
  const setCell = (r: number, c: number, v: string) => set({ cells: cells.map((row, ri) => Array.from({ length: cols }, (_, ci) => (ri === r && ci === c ? v : row[ci] ?? ''))) });
  const boxed = TEXT_TYPES.includes(d.type);
  const align = d.align === 'center' || d.align === 'right' ? d.align : d.align === 'left' ? 'left' : (LINE_TEXT_TYPES.includes(d.type) ? 'center' : 'left');
  return (
    <>
      {isTable ? (
        <>
          <div className="dtable" style={{ gridTemplateColumns: `repeat(${cols}, minmax(72px, 1fr))` }}>
            {cells.map((row, r) => Array.from({ length: cols }, (_, c) => (
              <input key={`${r}-${c}`} dir="auto" value={row[c] ?? ''} aria-label={`${r + 1}:${c + 1}`} autoFocus={autoFocus && r === 0 && c === 0} onChange={(e) => setCell(r, c, e.target.value)} />
            )))}
          </div>
          <div className="dtable-ops">
            <button className="btn btn-quiet" onClick={() => set({ cells: [...cells.map((r) => Array.from({ length: cols }, (_, i) => r[i] ?? '')), Array.from({ length: cols }, () => '')] })}>{L('+ ردیف', '+ Row')}</button>
            <button className="btn btn-quiet" onClick={() => set({ cells: cells.map((r) => [...Array.from({ length: cols }, (_, i) => r[i] ?? ''), '']) })}>{L('+ ستون', '+ Column')}</button>
            <button className="btn btn-quiet" disabled={cells.length <= 1} onClick={() => set({ cells: cells.slice(0, -1) })}>{L('− ردیف', '− Row')}</button>
            <button className="btn btn-quiet" disabled={cols <= 1} onClick={() => set({ cells: cells.map((r) => r.slice(0, cols - 1)) })}>{L('− ستون', '− Column')}</button>
            <label className="dtext-tools"><input type="checkbox" checked={d.header !== false} onChange={(e) => set({ header: e.target.checked })} /><span>{L('ردیف عنوان', 'Header row')}</span></label>
          </div>
        </>
      ) : (
        <textarea ref={area} className="text-edit" dir="auto" rows={4} value={String(d.text ?? '')} onChange={(e) => set({ text: e.target.value })} aria-label={L('متن', 'Text')} />
      )}
      <div className="dtext-tools">
        <input type="color" value={colour} onChange={(e) => set({ [colourKey]: e.target.value })} aria-label={L('رنگ متن', 'Text colour')} title={L('رنگ متن', 'Text colour')} />
        <select className="num" value={num(d.fontSize, 12)} onChange={(e) => set({ fontSize: Number(e.target.value) })} aria-label={L('اندازه', 'Size')}>
          {[10, 11, 12, 14, 16, 18, 20, 24, 28, 32, 40, 48].map((n) => <option key={n} value={n}>{n}</option>)}
        </select>
        <button className={`ib${d.bold ? ' on' : ''}`} aria-pressed={Boolean(d.bold)} title={L('پررنگ', 'Bold')} onClick={() => set({ bold: !d.bold })}><Icon name="ph-text-b" size="sm" /></button>
        <button className={`ib${d.italic ? ' on' : ''}`} aria-pressed={Boolean(d.italic)} title={L('کج', 'Italic')} onClick={() => set({ italic: !d.italic })}><Icon name="ph-text-italic" size="sm" /></button>
        {!isTable ? (
          <span className="dsd-seg" role="group" aria-label={L('ترازبندی', 'Alignment')}>
            {(['left', 'center', 'right'] as const).map((a) => <button key={a} className={align === a ? 'on' : ''} title={a === 'left' ? L('چپ', 'Left') : a === 'center' ? L('وسط', 'Centre') : L('راست', 'Right')} onClick={() => set({ align: a })}><Icon name={`ph-text-align-${a}`} size="sm" /></button>)}
          </span>
        ) : null}
        {LINE_TEXT_TYPES.includes(d.type) ? (
          <span className="dsd-seg" role="group" aria-label={L('جای متن', 'Text position')}>
            {(['top', 'middle', 'bottom'] as const).map((a) => <button key={a} className={(d.valign ?? 'top') === a ? 'on' : ''} onClick={() => set({ valign: a })}>{a === 'top' ? L('بالا', 'Top') : a === 'middle' ? L('روی خط', 'Middle') : L('پایین', 'Bottom')}</button>)}
          </span>
        ) : null}
      </div>
      {WRAP.includes(d.type) ? (
        <div className="dtext-tools">
          <label><input type="checkbox" checked={Boolean(d.wrap)} onChange={(e) => set({ wrap: e.target.checked })} />{L('شکستن خط', 'Text wrap')}</label>
          {d.wrap ? <label>{L('پهنا', 'Width')}<input type="number" min={40} max={800} step={10} className="num dsd-in" value={num(d.wrapWidth, 240)} onChange={(e) => set({ wrapWidth: Number(e.target.value) })} /></label> : null}
        </div>
      ) : null}
      {boxed ? (
        <div className="dtext-tools">
          <label><input type="checkbox" checked={Boolean(d.bg)} onChange={(e) => set({ bg: e.target.checked ? '#1E222D' : undefined })} />{L('پس‌زمینه', 'Background')}</label>
          {d.bg ? <input type="color" value={String(d.bg)} onChange={(e) => set({ bg: e.target.value })} aria-label={L('رنگ پس‌زمینه', 'Background colour')} /> : null}
          {d.bg ? <input type="range" min={0} max={1} step={0.05} value={num(d.bgOpacity, 0.9)} onChange={(e) => set({ bgOpacity: Number(e.target.value) })} aria-label={L('شفافیت', 'Opacity')} /> : null}
          <label><input type="checkbox" checked={Boolean(d.border)} onChange={(e) => set({ border: e.target.checked ? (typeof d.color === 'string' ? d.color : '#2962FF') : undefined })} />{L('حاشیه', 'Border')}</label>
          {d.border ? <input type="color" value={String(d.border)} onChange={(e) => set({ border: e.target.value })} aria-label={L('رنگ حاشیه', 'Border colour')} /> : null}
        </div>
      ) : null}
    </>
  );
}

/** A bar's number in the loaded history (1 = the oldest), extrapolated past the last bar. */
function barOf(layer: DrawingLayer, t: number): number {
  const cs = layer.candles ?? [];
  if (!cs.length) return 0;
  const iv = layer._barInterval() ?? 60;
  const last = cs[cs.length - 1]!;
  if (t > last.t) return cs.length + Math.round((t - last.t) / iv);
  if (t < cs[0]!.t) return 1 - Math.round((cs[0]!.t - t) / iv);
  let lo = 0, hi = cs.length - 1;
  while (lo < hi) { const m = (lo + hi) >> 1; if (cs[m]!.t < t) lo = m + 1; else hi = m; }
  return lo + 1;
}
function timeOfBar(layer: DrawingLayer, n: number): number | null {
  const cs = layer.candles ?? [];
  if (!cs.length || !Number.isFinite(n)) return null;
  const iv = layer._barInterval() ?? 60;
  if (n >= 1 && n <= cs.length) return cs[Math.round(n) - 1]!.t;
  if (n > cs.length) return cs[cs.length - 1]!.t + Math.round(n - cs.length) * iv;
  return cs[0]!.t - Math.round(1 - n) * iv;
}

// TradingView's drawing settings: Style (line, extensions, middle point, price labels, stats,
// background, the level table), Text, Coordinates (price and bar of each point), Visibility (which
// intervals show it, with ranges), and Inputs for a position (account, risk, quantity). Changes
// show at once; OK keeps them as one undo step, Cancel puts everything back.
export function DrawingSettingsDialog() {
  const settings = useDrawing((s) => s.settings);
  const layer = useChartBus((s) => s.drawingLayer);
  if (!settings || !layer || !layer.getDrawings()[settings.index]) return null;
  return <Dialog key={`${settings.index}`} index={settings.index} startTab={settings.tab} layer={layer} />;
}

function Dialog({ index, startTab, layer }: { index: number; startTab?: string; layer: DrawingLayer }) {
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const symbol = usePrefs((s) => s.symbol);
  const templates = useDrawing((s) => s.templates);
  const [, force] = useState(0);
  const before = useMemo(() => layer.beginEdit(), [layer]);
  const done = useRef(false);
  const openedOn = useRef(symbol);
  const d = layer.getDrawings()[index] as (DrawingObject & Rec) | undefined;
  const type = d?.type ?? '';
  const tabs: { id: Tab; label: string }[] = [
    { id: 'style', label: L('سبک', 'Style') },
    ...(POSITION.includes(type) ? [{ id: 'inputs' as Tab, label: L('ورودی‌ها', 'Inputs') }] : []),
    ...(TEXT_TYPES.includes(type) || LINE_TEXT_TYPES.includes(type) ? [{ id: 'text' as Tab, label: L('متن', 'Text') }] : []),
    { id: 'coords', label: L('مختصات', 'Coordinates') },
    { id: 'visibility', label: L('نمایش', 'Visibility') },
  ];
  const [tab, setTab] = useState<Tab>(() => (tabs.some((x) => x.id === startTab) ? (startTab as Tab) : 'style'));
  const [tplOpen, setTplOpen] = useState(startTab === 'template');
  const [tplName, setTplName] = useState('');
  const [pos, setPos] = useState<{ x: number; y: number } | null>(null);
  const [drag, setDrag] = useState<{ dx: number; dy: number } | null>(null);
  const box = useRef<HTMLDivElement>(null);

  const finish = (commit: boolean) => {
    if (done.current) return;
    done.current = true;
    // A symbol switch while the dialog was open replaced the drawings: nothing to put back then.
    if (openedOn.current === usePrefs.getState().symbol) layer.endEdit(before, commit);
    useDrawing.getState().closeSettings();
  };
  // Closed from outside (another symbol, the drawing removed): keep what is on the chart.
  useEffect(() => { done.current = false; return () => { done.current = true; }; }, []);
  useEffect(() => {
    const esc = (e: KeyboardEvent) => { if (e.key === 'Escape') { e.stopPropagation(); finish(false); } };
    window.addEventListener('keydown', esc);
    return () => window.removeEventListener('keydown', esc);
  });
  useEffect(() => { if (symbol !== openedOn.current) { done.current = true; useDrawing.getState().closeSettings(); } }, [symbol]);

  if (!d) return null;
  const set = (st: Rec) => { layer.liveStyle(index, st); force((n) => n + 1); };
  const row = (label: string, ctl: ReactNode, key?: string) => <label key={key} className="dsd-row"><span className="dsd-k">{label}</span>{ctl}</label>;
  const check = (k: string, label: string, def = false) => row(label, <input type="checkbox" checked={d[k] === undefined ? def : Boolean(d[k])} onChange={(e) => set({ [k]: e.target.checked })} />, k);
  const colourIn = (k: string, fallback: string) => <input type="color" value={typeof d[k] === 'string' ? (d[k] as string) : fallback} onChange={(e) => set({ [k]: e.target.value })} />;
  const style = d.lineStyle === 'dotted' ? 'dotted' : d.lineStyle === 'dashed' || d.dashed ? 'dashed' : 'solid';
  const digits = layer.digits;
  const fmtT = (t: number) => { try { return (layer as unknown as { timeFmt?: (t: number) => string }).timeFmt?.(t) ?? ''; } catch { return ''; } };

  // ── Style ──
  const levelTypes = Object.keys(LEVEL_DEFAULTS);
  const levels: Level[] = levelTypes.includes(type) ? normLevels(d, type) : [];
  const setLevel = (i: number, patch: Partial<Level>) => set({ levels: levels.map((l, k) => (k === i ? { ...l, ...patch } : l)) });
  const styleTab = (
    <>
      {!NO_LINE.includes(type) ? (
        <>
          <div className="dsd-h">{L('خط', 'Line')}</div>
          {row(L('رنگ', 'Colour'), colourIn('color', '#2962FF'))}
          {row(L('ضخامت', 'Width'), <select className="num" value={num(d.width, 2)} onChange={(e) => set({ width: Number(e.target.value) })}>{[1, 2, 3, 4].map((w) => <option key={w} value={w}>{w}px</option>)}</select>)}
          {row(L('سبک', 'Style'), <select value={style} onChange={(e) => set({ lineStyle: e.target.value, dashed: e.target.value === 'dashed' })}><option value="solid">{L('توپر', 'Solid')}</option><option value="dashed">{L('خط‌چین', 'Dashed')}</option><option value="dotted">{L('نقطه‌چین', 'Dotted')}</option></select>)}
        </>
      ) : type === 'emoji' || type === 'text' || type === 'anchoredtext' || type === 'table' || type === 'comment' ? row(L('رنگ', 'Colour'), colourIn('color', '#2962FF')) : null}
      {EXTEND.includes(type) ? (
        <>
          {type !== 'ray' ? check('extendRight', L('امتداد به راست', 'Extend right')) : null}
          {check('extendLeft', L('امتداد به چپ', 'Extend left'))}
        </>
      ) : null}
      {['trend', 'ray', 'rect'].includes(type) ? check('middle', type === 'rect' ? L('خط میانه', 'Middle line') : L('نقطه‌ی میانی', 'Middle point')) : null}
      {['trend', 'ray'].includes(type) ? check('priceLabels', L('برچسب قیمت‌ها', 'Price labels')) : null}
      {type === 'trend' ? row(L('آمار', 'Stats'), (
        <select value={typeof d.stats === 'string' ? d.stats : 'selected'} onChange={(e) => set({ stats: e.target.value })}>
          <option value="hidden">{L('پنهان', 'Hidden')}</option><option value="selected">{L('هنگام انتخاب', 'When selected')}</option><option value="always">{L('همیشه', 'Always')}</option>
        </select>
      )) : null}
      {FILL.includes(type) ? (
        <>
          <div className="dsd-h">{L('پس‌زمینه', 'Background')}</div>
          {row(L('رنگ', 'Colour'), colourIn('fill', typeof d.color === 'string' ? d.color : '#2962FF'))}
          {row(L('شفافیت', 'Opacity'), <input type="range" min={0} max={1} step={0.05} value={num(d.fillOpacity, type === 'rect' ? 0.18 : 0.1)} onChange={(e) => set({ fillOpacity: Number(e.target.value) })} />)}
        </>
      ) : null}
      {type === 'fib' ? check('fibBg', L('پس‌زمینه‌ی سطوح', 'Background'), true) : null}
      {type === 'avwap' ? check('bands', L('باندهای انحراف معیار', 'Standard deviation bands')) : null}
      {type === 'fixedvp' || type === 'anchoredvp' ? (
        <>
          {row(L('تعداد ردیف‌ها', 'Rows'), <input type="number" min={4} max={200} value={num(d.rows, 24)} onChange={(e) => set({ rows: Math.max(4, Math.min(200, Number(e.target.value) || 24)) })} />)}
          {row(L('ناحیه‌ی ارزش ٪', 'Value area %'), <input type="number" min={10} max={100} value={Math.round(num(d.va, 0.7) * 100)} onChange={(e) => set({ va: Math.max(0.1, Math.min(1, (Number(e.target.value) || 70) / 100)) })} />)}
          {row(L('پهنا ٪', 'Width %'), <input type="number" min={5} max={100} value={Math.round(num(d.widthPct, 0.3) * 100)} onChange={(e) => set({ widthPct: Math.max(0.05, Math.min(1, (Number(e.target.value) || 30) / 100)) })} />)}
          {row(L('حجم صعودی', 'Up volume'), colourIn('upColor', '#089981'))}
          {row(L('حجم نزولی', 'Down volume'), colourIn('downColor', '#f23645'))}
        </>
      ) : null}
      {type === 'ghostfeed' ? (
        <>
          {row(L('کندل صعودی', 'Up bars'), colourIn('upColor', '#089981'))}
          {row(L('کندل نزولی', 'Down bars'), colourIn('downColor', '#f23645'))}
        </>
      ) : null}
      {type === 'barspattern' ? (
        <>
          {row(L('نوع', 'Mode'), <select value={typeof d.mode === 'string' ? d.mode : 'candles'} onChange={(e) => set({ mode: e.target.value })}><option value="candles">{L('کندل', 'Candles')}</option><option value="hl">{L('میله‌ی سقف و کف', 'HL bars')}</option><option value="line">{L('خط بسته‌شدن', 'Close line')}</option></select>)}
          {check('mirror', L('آینه‌ای (وارونه در زمان)', 'Mirrored'))}
          {check('flip', L('وارونه (در قیمت)', 'Flipped'))}
        </>
      ) : null}
      {type === 'arrowdir' ? row(L('جهت', 'Direction'), <select value={typeof d.dir === 'string' ? d.dir : 'up'} onChange={(e) => set({ dir: e.target.value })}><option value="up">{L('بالا', 'Up')}</option><option value="down">{L('پایین', 'Down')}</option><option value="left">{L('چپ', 'Left')}</option><option value="right">{L('راست', 'Right')}</option></select>) : null}
      {type === 'emoji' ? (
        <>
          {row(L('اندازه', 'Size'), <input type="number" min={12} max={96} value={num(d.fontSize, 28)} onChange={(e) => set({ fontSize: Number(e.target.value) || 28 })} />)}
          <div className="dfly-emoji">{EMOJIS.map((e) => <button key={e} className={`dfly-emo${d.text === e ? ' on' : ''}`} onClick={() => set({ text: e })} aria-label={e}>{e}</button>)}</div>
        </>
      ) : null}
      {levels.length ? (
        <>
          <div className="dsd-h">{L('سطوح', 'Levels')}</div>
          <div className="dsd-lv">
            {levels.map((l, i) => (
              <div key={i} className="dsd-lv-row">
                <input type="checkbox" checked={l.on} onChange={(e) => setLevel(i, { on: e.target.checked })} aria-label={L('نمایش سطح', 'Show level')} />
                <input type="number" step={0.001} value={l.v} onChange={(e) => setLevel(i, { v: Number(e.target.value) })} aria-label={L('مقدار سطح', 'Level value')} />
                <input type="color" value={l.color} onChange={(e) => setLevel(i, { color: e.target.value })} aria-label={L('رنگ سطح', 'Level colour')} />
                <button className="dfly-mini" title={L('حذف سطح', 'Remove level')} aria-label={L('حذف سطح', 'Remove level')} onClick={() => set({ levels: levels.filter((_, k) => k !== i) })}><Icon name="ph-x" size="sm" /></button>
              </div>
            ))}
          </div>
          <div className="dtable-ops">
            <button className="btn btn-quiet" onClick={() => set({ levels: [...levels, { v: (levels[levels.length - 1]?.v ?? 1) + 0.5, on: true, color: '#2962FF' }] })}>{L('+ سطح', '+ Level')}</button>
            <button className="btn btn-quiet" onClick={() => set({ levels: undefined })}>{L('سطوح پیش‌فرض', 'Default levels')}</button>
          </div>
        </>
      ) : null}
    </>
  );

  // ── Inputs (long / short position) ──
  const ps = POSITION.includes(type) ? layer.positionStats(d) : null;
  const priceIn = (v: number, on: (n: number) => void) => <input type="number" step={10 ** -digits} value={Number(v.toFixed(digits))} onChange={(e) => { const n = Number(e.target.value); if (Number.isFinite(n)) on(n); }} />;
  const inputsTab = ps ? (
    <>
      {row(L('اندازه‌ی حساب', 'Account size'), <input type="number" min={0} value={ps.account} onChange={(e) => set({ account: Number(e.target.value) || 0 })} />)}
      {row(L('اندازه‌ی لات', 'Lot size'), <input type="number" min={0} step="any" value={ps.lot} onChange={(e) => set({ lot: Number(e.target.value) || 1 })} />)}
      {row(L('ریسک', 'Risk'), (
        <span className="dtext-tools">
          <input type="number" min={0} step="any" className="dsd-in num" style={{ width: 80 }} value={num(d.risk, 1)} onChange={(e) => set({ risk: Number(e.target.value) || 0 })} />
          <select value={d.riskUnit === 'amount' ? 'amount' : 'pct'} onChange={(e) => set({ riskUnit: e.target.value })}><option value="pct">%</option><option value="amount">{L('مبلغ', 'Amount')}</option></select>
        </span>
      ))}
      <div className="dsd-h">{L('قیمت‌ها', 'Prices')}</div>
      {row(L('ورود', 'Entry price'), priceIn(ps.entry, (n) => { const p0 = d.p0 as Pt; set({ p0: { t: p0.t, p: n } }); }))}
      {row(L('هدف', 'Profit level'), priceIn(ps.target, (n) => set({ tp: n })))}
      {row(L('حد ضرر', 'Stop level'), priceIn(ps.stop, (n) => { const p1 = d.p1 as Pt; set({ p1: { t: p1.t, p: n } }); }))}
      <div className="dsd-h">{L('نتیجه', 'Result')}</div>
      <div className="dsd-row"><span className="dsd-k">{L('مبلغ ریسک', 'Risk amount')}</span><span className="num">{ps.riskAmt.toFixed(2)}</span></div>
      <div className="dsd-row"><span className="dsd-k">{L('حجم', 'Qty')}</span><span className="num">{Number(ps.qty.toPrecision(4))}</span></div>
      <div className="dsd-row"><span className="dsd-k">{L('سود در هدف', 'Reward')}</span><span className="num">{ps.reward.toFixed(2)}</span></div>
      <div className="dsd-row"><span className="dsd-k">{L('نسبت سود به ریسک', 'Risk/reward ratio')}</span><span className="num">{ps.rr.toFixed(2)}</span></div>
    </>
  ) : null;

  // ── Coordinates ──
  const coord = (label: string, pt: { t?: number; p?: number }, apply: (pt: { t?: number; p?: number }) => void, key: string) => (
    <div key={key} className="dsd-coord">
      <span>{label}</span>
      {pt.p != null ? <input type="number" step={10 ** -digits} aria-label={L('قیمت', 'Price')} value={Number(pt.p.toFixed(digits))} onChange={(e) => { const n = Number(e.target.value); if (Number.isFinite(n)) apply({ ...pt, p: n }); }} /> : <span />}
      {pt.t != null ? <input type="number" step={1} aria-label={L('شماره‌ی کندل', 'Bar')} title={L('شماره‌ی کندل', 'Bar')} value={barOf(layer, pt.t)} onChange={(e) => { const t = timeOfBar(layer, Number(e.target.value)); if (t != null) apply({ ...pt, t }); }} /> : <span />}
      {pt.t != null ? <span className="muted num">{fmtT(pt.t)}</span> : null}
    </div>
  );
  const coordRows: ReactNode[] = [];
  if (type === 'hline') coordRows.push(coord('#1', { p: d.p as number }, (q) => set({ p: q.p }), 'h'));
  else if (type === 'vline') coordRows.push(coord('#1', { t: d.t as number }, (q) => set({ t: q.t }), 'v'));
  else if (type === 'text') coordRows.push(coord('#1', { t: d.t as number, p: d.p as number }, (q) => set({ t: q.t, p: q.p }), 't'));
  else if (type === 'anchoredtext') {
    coordRows.push(
      <div key="a" className="dsd-coord"><span>X %</span><input type="number" min={0} max={100} value={Math.round(num(d.ax, 0.5) * 100)} onChange={(e) => set({ ax: Math.max(0, Math.min(1, Number(e.target.value) / 100)) })} /><span /></div>,
      <div key="b" className="dsd-coord"><span>Y %</span><input type="number" min={0} max={100} value={Math.round(num(d.ay, 0.5) * 100)} onChange={(e) => set({ ay: Math.max(0, Math.min(1, Number(e.target.value) / 100)) })} /><span /></div>,
    );
  } else if (type === 'barspattern' && isPt(d.dst)) coordRows.push(coord(L('جای کپی', 'Copy at'), d.dst, (q) => set({ dst: { t: q.t, p: q.p } }), 'dst'));
  else if (Array.isArray(d.pts)) (d.pts as Pt[]).forEach((q, k) => coordRows.push(coord(`#${k + 1}`, q, (n) => set({ pts: (d.pts as Pt[]).map((x, j) => (j === k ? { t: n.t ?? x.t, p: n.p ?? x.p } : x)) }), `p${k}`)));
  else {
    if (isPt(d.p0)) coordRows.push(coord('#1', d.p0, (q) => set({ p0: { t: q.t ?? (d.p0 as Pt).t, p: q.p ?? (d.p0 as Pt).p } }), 'p0'));
    if (isPt(d.p1) && !(['hray', 'crossline', 'pricelabel', 'note', 'arrowdir', 'flag', 'signpost', 'arrowup', 'arrowdown', 'avwap', 'anchoredvp', 'pin', 'comment', 'table', 'emoji'].includes(type))) coordRows.push(coord('#2', d.p1, (q) => set({ p1: { t: q.t ?? (d.p1 as Pt).t, p: q.p ?? (d.p1 as Pt).p } }), 'p1'));
  }

  // ── Visibility ──
  const vis = (d.tfVis && typeof d.tfVis === 'object' ? d.tfVis : {}) as Rec;
  const visOf = (c: (typeof TF_CLASSES)[number]) => {
    const v = vis[c.id];
    if (v === false) return { on: false, min: c.min, max: c.max };
    if (v && typeof v === 'object') { const o = v as { on?: boolean; min?: number; max?: number }; return { on: o.on !== false, min: num(o.min, c.min), max: num(o.max, c.max) }; }
    return { on: true, min: c.min, max: c.max };
  };
  const setVis = (c: (typeof TF_CLASSES)[number], patch: Partial<{ on: boolean; min: number; max: number }>) => {
    const cur = { ...visOf(c), ...patch };
    cur.min = Math.max(c.min, Math.min(c.max, Math.round(cur.min)));
    cur.max = Math.max(cur.min, Math.min(c.max, Math.round(cur.max)));
    set({ tfVis: { ...vis, [c.id]: cur } });
  };
  const visTab = (
    <>
      {TF_CLASSES.map((c) => {
        const v = visOf(c);
        const span = c.max - c.min || 1;
        return (
          <div key={c.id} className="dsd-vis" data-tf={c.id}>
            <input type="checkbox" checked={v.on} onChange={(e) => setVis(c, { on: e.target.checked })} aria-label={L(c.fa, c.en)} />
            <span>{L(c.fa, c.en)}</span>
            <input type="number" min={c.min} max={c.max} value={v.min} disabled={!v.on} onChange={(e) => setVis(c, { min: Number(e.target.value) })} aria-label={L('از', 'From')} />
            <span className="bar"><i style={{ left: `${((v.min - c.min) / span) * 100}%`, right: `${100 - ((v.max - c.min) / span) * 100}%`, opacity: v.on ? 1 : 0.3 }} /></span>
            <input type="number" min={c.min} max={c.max} value={v.max} disabled={!v.on} onChange={(e) => setVis(c, { max: Number(e.target.value) })} aria-label={L('تا', 'To')} />
          </div>
        );
      })}
    </>
  );

  const tpls = templates[type] ?? [];
  const at = pos ?? { x: Math.max(12, (window.innerWidth - 480) / 2), y: Math.max(12, window.innerHeight * 0.1) };
  return createPortal(
    <div className="scrim clear dsd-scrim">
      <div ref={box} className="dialog dsd" role="dialog" aria-modal="false" aria-label={drawingName(type, lang)} dir={lang === 'fa' ? 'rtl' : 'ltr'} style={{ left: at.x, top: at.y }}>
        <header className="dialog-head"
          onPointerDown={(e) => { if ((e.target as HTMLElement).closest('button')) return; (e.currentTarget as HTMLElement).setPointerCapture?.(e.pointerId); setDrag({ dx: e.clientX - at.x, dy: e.clientY - at.y }); }}
          onPointerMove={(e) => { if (drag) setPos({ x: Math.max(0, Math.min(window.innerWidth - 120, e.clientX - drag.dx)), y: Math.max(0, Math.min(window.innerHeight - 60, e.clientY - drag.dy)) }); }}
          onPointerUp={() => setDrag(null)}>
          <h2>{typeof d.name === 'string' && d.name ? d.name : drawingName(type, lang)}</h2>
          <button className="ib" onClick={() => finish(false)} aria-label={L('بستن', 'Close')}><Icon name="ph-x" size="md" /></button>
        </header>
        <nav className="dsd-tabs" role="tablist">
          {tabs.map((x) => <button key={x.id} role="tab" aria-selected={tab === x.id} className={`dsd-tab${tab === x.id ? ' on' : ''}`} data-tab={x.id} onClick={() => setTab(x.id)}>{x.label}</button>)}
        </nav>
        <div className="dsd-body">
          {tab === 'style' ? styleTab : null}
          {tab === 'inputs' ? inputsTab : null}
          {tab === 'text' ? <TextFields d={d} set={set} /> : null}
          {tab === 'coords' ? (coordRows.length ? coordRows : <p className="dsd-hint">{L('این ترسیم مختصات قابل‌ویرایش ندارد.', 'This drawing has no editable coordinates.')}</p>) : null}
          {tab === 'visibility' ? visTab : null}
        </div>
        <footer className="dialog-foot">
          <div className="dsd-tpl">
            <button className="btn btn-quiet" aria-expanded={tplOpen} onClick={() => setTplOpen(!tplOpen)}>{L('قالب', 'Template')}<Icon name="ph-caret-down" size="sm" /></button>
            {tplOpen ? (
              <div className="dsb-pop menuish">
                {tpls.map((x) => <button key={x.name} className="dfly-row" onClick={() => { set(x.style); setTplOpen(false); }}><Icon name="ph-squares-four" size="sm" /><span className="dfly-label">{x.name}</span></button>)}
                <form className="dsb-form" onSubmit={(e) => { e.preventDefault(); const n = tplName.trim(); if (!n) return; useDrawing.getState().saveTemplate(type, n, styleOf(d)); setTplName(''); setTplOpen(false); }}>
                  <input value={tplName} autoFocus={startTab === 'template'} onChange={(e) => setTplName(e.target.value)} placeholder={L('ذخیره به نام…', 'Save as…')} aria-label={L('نام قالب', 'Template name')} />
                  <button className="ib" type="submit" aria-label={L('ذخیره', 'Save')}><Icon name="ph-floppy-disk" size="sm" /></button>
                </form>
                <div className="dfly-sep" />
                <button className="dfly-row" onClick={() => { saveAsDefault(d); setTplOpen(false); }}><Icon name="ph-bookmark-simple" size="sm" /><span className="dfly-label">{L('ذخیره به‌عنوان پیش‌فرض', 'Save as default')}</span></button>
                <button className="dfly-row" onClick={() => { const def = useDrawing.getState().defaults[type]; set(def ?? { color: useDrawing.getState().color, width: 2, lineStyle: 'solid', dashed: false }); setTplOpen(false); }}><Icon name="ph-arrow-counter-clockwise" size="sm" /><span className="dfly-label">{L('اعمال پیش‌فرض', 'Apply default')}</span></button>
              </div>
            ) : null}
          </div>
          <span className="ws-spacer" />
          <button className="btn btn-quiet" data-act="cancel" onClick={() => finish(false)}>{L('انصراف', 'Cancel')}</button>
          <button className="btn btn-primary" data-act="ok" onClick={() => finish(true)}>{L('تأیید', 'Ok')}</button>
        </footer>
      </div>
    </div>,
    document.body,
  );
}

/** A price as the chart writes it (for labels in this dialog's callers). */
export const fmtPrice = (v: number, digits: number) => formatPrice(v, digits);
