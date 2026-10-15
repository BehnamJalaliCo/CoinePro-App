import { useEffect, useState } from 'react';
import { useChartBus } from './chartBus';
import { useT } from '../state/prefs';
import { Icon } from '../ui/Icon';

// TradingView's floating toolbar over a selected drawing: colour, line width, line style, lock,
// clone, hide and delete — every change one undo step in the engine.
const COLORS = ['#2962FF', '#F23645', '#089981', '#FF9800', '#9C27B0', '#00BCD4', '#787B86', '#000000'];
const WIDTHS = [1, 2, 3, 4];
const STYLES: { id: 'solid' | 'dashed' | 'dotted'; dash: string }[] = [{ id: 'solid', dash: '' }, { id: 'dashed', dash: '6 4' }, { id: 'dotted', dash: '2 3' }];

export function DrawingStyleBar() {
  const t = useT();
  const layer = useChartBus((s) => s.drawingLayer);
  const index = useChartBus((s) => s.selectedDrawing);
  useChartBus((s) => s.drawingVersion);
  const [open, setOpen] = useState<'color' | 'width' | 'style' | null>(null);
  useEffect(() => { setOpen(null); }, [index]);
  const d = layer && index >= 0 ? layer.getDrawings()[index] : null;
  if (!layer || !d) return null;
  const color = typeof d.color === 'string' ? d.color : '#2962FF';
  const width = typeof d.width === 'number' ? d.width : 2;
  const style = d.lineStyle === 'dotted' ? 'dotted' : d.lineStyle === 'dashed' || d.dashed ? 'dashed' : 'solid';
  const set = (st: Record<string, unknown>) => { layer.setStyle(index, st); layer.render(); setOpen(null); };
  return (
    <div className="draw-style-bar" role="toolbar" aria-label="drawing style" onMouseDown={(e) => e.stopPropagation()}>
      <span className="dsb-name">{(layer.constructor as unknown as { label?(t: string): string }).label?.(d.type) ?? d.type}</span>
      <div className="dsb-group">
        <button className="ib" title="color" onClick={() => setOpen(open === 'color' ? null : 'color')}><span className="dsb-swatch" style={{ background: color }} /></button>
        {open === 'color' ? <div className="dsb-pop">{COLORS.map((c) => <button key={c} className={`swatch${c === color ? ' on' : ''}`} style={{ background: c }} onClick={() => set({ color: c })} aria-label={c} />)}<input type="color" value={color} onChange={(e) => set({ color: e.target.value })} aria-label="custom" /></div> : null}
      </div>
      <div className="dsb-group">
        <button className="ib num" title="width" onClick={() => setOpen(open === 'width' ? null : 'width')}>{width}px</button>
        {open === 'width' ? <div className="dsb-pop col">{WIDTHS.map((w) => <button key={w} className={`dsb-line${w === width ? ' on' : ''}`} onClick={() => set({ width: w })}><svg width="40" height="10"><line x1="2" x2="38" y1="5" y2="5" stroke="currentColor" strokeWidth={w} /></svg></button>)}</div> : null}
      </div>
      <div className="dsb-group">
        <button className="ib" title="style" onClick={() => setOpen(open === 'style' ? null : 'style')}><svg width="24" height="10"><line x1="2" x2="22" y1="5" y2="5" stroke="currentColor" strokeWidth="2" strokeDasharray={STYLES.find((x) => x.id === style)!.dash} /></svg></button>
        {open === 'style' ? <div className="dsb-pop col">{STYLES.map((x) => <button key={x.id} className={`dsb-line${x.id === style ? ' on' : ''}`} onClick={() => set({ lineStyle: x.id, dashed: x.id === 'dashed' })}><svg width="40" height="10"><line x1="2" x2="38" y1="5" y2="5" stroke="currentColor" strokeWidth="2" strokeDasharray={x.dash} /></svg></button>)}</div> : null}
      </div>
      <span className="sep" />
      <button className={`ib${d.locked ? ' on' : ''}`} title={t('lockDrawings')} onClick={() => { layer.toggleLock(index); layer.render(); }}><Icon name={d.locked ? 'lock' : 'unlock'} size="sm" /></button>
      <button className="ib" title="clone" onClick={() => { layer.clone(index); layer.render(); }}><Icon name="ph-copy" size="sm" /></button>
      <button className="ib" title="hide" onClick={() => { layer.toggleVisible(index); layer.render(); }}><Icon name={d.visible === false ? 'eye_off' : 'ph-eye'} size="sm" /></button>
      <button className="ib" title={t('remove')} onClick={() => layer.removeAt(index)}><Icon name="ph-trash" size="sm" /></button>
    </div>
  );
}
