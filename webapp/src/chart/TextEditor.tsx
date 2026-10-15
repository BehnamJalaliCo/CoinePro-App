import { useEffect, useState } from 'react';
import { useChartBus } from './chartBus';
import { usePrefs } from '../state/prefs';
import { Icon } from '../ui/Icon';
import { useL } from '../pages/kit';
import { STYLE_KEYS } from '../engine/drawings.js';
import { TextFields } from './DrawingSettingsDialog';
import { drawingName } from './DrawingActions';
import './drawing.css';

/** Drawings that mean nothing without their text: cancelling a new one takes it away. */
export const TEXT_REQUIRED = ['text', 'anchoredtext', 'note', 'comment', 'callout', 'table'];

const hasText = (st: Record<string, unknown>) => (Array.isArray(st.cells)
  ? (st.cells as unknown[][]).some((r) => Array.isArray(r) && r.some((c) => String(c ?? '').trim()))
  : String(st.text ?? '').trim().length > 0);

// The text of a text-bearing drawing (text, anchored text, note, callout, comment, table, price
// label, signpost, flag…), written in place of the browser's prompt: several lines, colour, size,
// bold, italic, alignment, wrap, background and border. It opens as soon as such a drawing is placed.
// A new drawing that needs a text and gets none is taken away without leaving an undo step; one
// that reads without a text (a flag, a price label) stays.
export function TextEditor() {
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const edit = useChartBus((s) => s.textEdit);
  const layer = useChartBus((s) => s.drawingLayer);
  const setEdit = useChartBus((s) => s.setTextEdit);
  const d = edit && layer ? layer.getDrawings()[edit.index] : undefined;
  const [st, setSt] = useState<Record<string, unknown>>({});

  useEffect(() => {
    if (!d) return;
    const pick: Record<string, unknown> = { text: d.text ?? '', cells: d.cells };
    for (const k of STYLE_KEYS) if (d[k] !== undefined) pick[k] = JSON.parse(JSON.stringify(d[k]));
    if (d.type === 'text' && pick.fontSize === undefined) pick.fontSize = 14;
    setSt(pick);
  }, [edit]); // eslint-disable-line react-hooks/exhaustive-deps

  const close = (save: boolean) => {
    if (!edit || !layer || !d) { setEdit(null); return; }
    const ok = save && hasText(st);
    const required = TEXT_REQUIRED.includes(d.type);
    if (ok || (save && !required)) {
      const patch = { ...st };
      if (!Array.isArray(patch.cells)) delete patch.cells;
      if (edit.fresh) layer.amend(edit.index, patch); else layer.setStyle(edit.index, patch);
      layer.render();
    } else if (edit.fresh && required) layer.abandon(edit.index);
    else if (!edit.fresh && save && required) layer.removeAt(edit.index);
    setEdit(null);
  };

  // Escape cancels and Ctrl+Enter keeps, wherever the focus is.
  useEffect(() => {
    if (!edit) return;
    const key = (e: KeyboardEvent) => {
      if (e.key === 'Escape') { e.preventDefault(); close(false); }
      else if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) { e.preventDefault(); close(true); }
    };
    window.addEventListener('keydown', key, true);
    return () => window.removeEventListener('keydown', key, true);
  });

  if (!edit || !layer || !d) return null;
  return (
    <div className="scrim" onMouseDown={(e) => { if (e.target === e.currentTarget) close(true); }}>
      <div className="dialog settings dtext" role="dialog" aria-modal="true" aria-label={L('متن', 'Text')} dir={lang === 'fa' ? 'rtl' : 'ltr'}>
        <header className="dialog-head">
          <h2>{drawingName(d.type, lang)}</h2>
          <button className="ib" onClick={() => close(false)} aria-label={L('بستن', 'Close')}><Icon name="ph-x" size="md" /></button>
        </header>
        <div className="settings-body">
          <TextFields d={{ ...st, type: d.type, color: st.color ?? d.color }} set={(p) => setSt((s) => ({ ...s, ...p }))} autoFocus />
        </div>
        <footer className="dialog-foot">
          <span className="ws-spacer" />
          <button className="btn btn-quiet" data-act="cancel" onClick={() => close(false)}>{L('انصراف', 'Cancel')}</button>
          <button className="btn btn-primary" data-act="ok" onClick={() => close(true)}>{L('تأیید', 'Ok')}</button>
        </footer>
      </div>
    </div>
  );
}
