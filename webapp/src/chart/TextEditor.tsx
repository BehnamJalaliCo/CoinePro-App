import { useEffect, useRef, useState } from 'react';
import { useChartBus } from './chartBus';
import { useT } from '../state/prefs';
import { Icon } from '../ui/Icon';

const COLORS = ['#2962FF', '#089981', '#F23645', '#FF9800', '#9C27B0', '#0F0F0F', '#787B86'];

// The text of a text drawing (text, note, callout, signpost…), written in place of the browser's
// prompt: several lines, a colour, a size. An empty new text is removed.
export function TextEditor() {
  const t = useT();
  const edit = useChartBus((s) => s.textEdit);
  const layer = useChartBus((s) => s.drawingLayer);
  const setEdit = useChartBus((s) => s.setTextEdit);
  const d = edit && layer ? layer.getDrawings()[edit.index] : undefined;
  const [text, setText] = useState('');
  const [color, setColor] = useState('#2962FF');
  const [size, setSize] = useState(14);
  const area = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    if (!d) return;
    setText(String(d.text ?? ''));
    setColor(String(d.color ?? '#2962FF'));
    setSize(Number(d.fontSize ?? 14));
    requestAnimationFrame(() => area.current?.focus());
  }, [edit]); // eslint-disable-line react-hooks/exhaustive-deps

  if (!edit || !layer || !d) return null;
  const close = (save: boolean) => {
    if (save && text.trim()) layer.setStyle(edit.index, { text, color, fontSize: size });
    else if (edit.fresh) layer.removeAt(edit.index);
    setEdit(null);
  };
  return (
    <div className="scrim" onMouseDown={(e) => { if (e.target === e.currentTarget) close(true); }}>
      <div className="dialog settings" role="dialog" aria-modal="true" aria-label={t('drawText')}
        onKeyDown={(e) => { if (e.key === 'Escape') close(false); if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) close(true); }}>
        <header className="dialog-head">
          <h2 className="fa">{t('drawText')}</h2>
          <button className="ib" onClick={() => close(false)} aria-label="close"><Icon name="ph-x" size="md" /></button>
        </header>
        <div className="settings-body">
          <textarea ref={area} className="text-edit" dir="auto" rows={4} value={text} onChange={(e) => setText(e.target.value)} />
          <div className="setting-row">
            <span className="swatches">{COLORS.map((c) => <button key={c} className={`swatch${c === color ? ' on' : ''}`} style={{ background: c }} onClick={() => setColor(c)} aria-label={c} />)}</span>
            <select value={size} onChange={(e) => setSize(Number(e.target.value))} className="num">
              {[10, 12, 14, 16, 20, 24, 28, 32].map((n) => <option key={n} value={n}>{n}</option>)}
            </select>
          </div>
        </div>
        <footer className="dialog-foot">
          <span className="ws-spacer" />
          <button className="btn btn-quiet" onClick={() => close(false)}>{t('back')}</button>
          <button className="btn btn-primary" onClick={() => close(true)}>Ok</button>
        </footer>
      </div>
    </div>
  );
}
