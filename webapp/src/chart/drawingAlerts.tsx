// The one question the chart asks before deleting something (OLD feature/chart
// DrawingAlertDeleteDialog.kt, ChartController.deleteDrawings / confirmDrawingDelete).
//
// Asked only where the drawings about to go carry alerts: everything else still deletes on the
// spot with undo behind it. Deleting the line an alert hangs on would silently kill the alert —
// it could never resolve its level again — so the reader chooses:
//   * both: the drawing and its alerts (the destructive row, not the reflex one);
//   * the drawing, keeping the alerts: for somebody about to redraw it; the alert lists then mark
//     each kept alert as having lost its drawing;
//   * neither: the bottom row, Escape and a click outside. Nothing is written.
// A selection collects into one question, not one per drawing.
import { useEffect } from 'react';
import { create } from 'zustand';
import { useAlerts } from '../state/alerts';
import { alertsOn } from '../state/drawingLinks';
import { usePrefs } from '../state/prefs';
import { faNum } from '../i18n/time';
import { useL } from '../pages/kit';
import type { DrawingLayer } from '../engine/drawings.js';

interface Pending { layer: DrawingLayer; symbol: string; drawingIds: string[]; alertIds: string[] }

interface DeleteState {
  pending: Pending | null;
  ask(p: Pending): void;
  /** `alsoAlerts`: delete the alerts too; null: keep everything. */
  answer(alsoAlerts: boolean | null): void;
}

export const useDrawingDelete = create<DeleteState>((set, get) => ({
  pending: null,
  ask: (p) => {
    const cur = get().pending;
    // A second delete while the question is open joins it (the same chart only).
    if (cur && cur.layer === p.layer) set({ pending: { ...p, drawingIds: [...new Set([...cur.drawingIds, ...p.drawingIds])], alertIds: [...new Set([...cur.alertIds, ...p.alertIds])] } });
    else set({ pending: p });
  },
  answer: (alsoAlerts) => {
    const p = get().pending;
    set({ pending: null });
    if (!p || alsoAlerts == null) return;
    p.layer.removeIds(p.drawingIds);
    if (alsoAlerts) { const st = useAlerts.getState(); p.alertIds.forEach((id) => st.remove(id)); }
  },
}));

/**
 * Wraps a layer's deletes (removeAt: the eraser, the menus, the object tree; removeSelected: the
 * Delete key and the style bar) so a drawing that carries alerts asks first. `symbol` is read at
 * the moment of the delete.
 */
export function guardDrawingDeletes(layer: DrawingLayer, symbol: () => string): void {
  const rawAt = layer.removeAt.bind(layer);
  const rawSelected = layer.removeSelected.bind(layer);
  const watching = (ids: string[]) => alertsOn(useAlerts.getState().list, symbol(), ids);
  layer.removeAt = (i: number) => {
    const d = layer.getDrawings()[i];
    const ids = d?.id ? [d.id] : [];
    const alerts = watching(ids);
    if (!alerts.length) { rawAt(i); return; }
    useDrawingDelete.getState().ask({ layer, symbol: symbol(), drawingIds: ids, alertIds: alerts.map((a) => a.id) });
  };
  layer.removeSelected = () => {
    const ids = layer.selectedIndices().map((i) => layer.getDrawings()[i]?.id).filter((x): x is string => typeof x === 'string');
    const alerts = watching(ids);
    if (!alerts.length) { rawSelected(); return; }
    useDrawingDelete.getState().ask({ layer, symbol: symbol(), drawingIds: ids, alertIds: alerts.map((a) => a.id) });
  };
}

/** The dialog itself, mounted once beside the alert dialog. */
export function DrawingAlertDeleteDialog() {
  const L = useL();
  const fa = usePrefs((s) => s.lang) === 'fa';
  const pending = useDrawingDelete((s) => s.pending);
  const answer = useDrawingDelete((s) => s.answer);
  useEffect(() => {
    if (!pending) return;
    const esc = (e: KeyboardEvent) => { if (e.key === 'Escape') { e.stopPropagation(); answer(null); } };
    window.addEventListener('keydown', esc, true);
    return () => window.removeEventListener('keydown', esc, true);
  }, [pending, answer]);
  if (!pending) return null;
  const many = pending.drawingIds.length > 1;
  const n = (x: number) => (fa ? faNum(x) : String(x));
  const alerts = pending.alertIds.length;
  const message = many
    ? L(`${n(alerts)} هشدار این ${n(pending.drawingIds.length)} ترسیم را می‌پاید. اگر ترسیم‌ها بروند، دیگر هرگز به صدا درنمی‌آیند.`,
      `${n(alerts)} alerts watch these ${n(pending.drawingIds.length)} drawings. Delete the drawings and they can never go off again.`)
    : L(`${n(alerts)} هشدار این خط را می‌پاید. اگر خط برود، دیگر هرگز به صدا درنمی‌آیند.`,
      `${n(alerts)} alerts watch this line. Delete the line and they can never go off again.`);
  return (
    <div className="scrim" onMouseDown={(e) => { if (e.target === e.currentTarget) answer(null); }}>
      <div className="dialog dad" role="dialog" aria-modal="true" aria-label={L('روی این ترسیم هشدار هست', 'This drawing carries alerts')} dir={fa ? 'rtl' : 'ltr'}>
        <header className="dialog-head"><h2 className={fa ? 'fa' : ''}>{L('روی این ترسیم هشدار هست', 'This drawing carries alerts')}</h2></header>
        <div className="dad-body">
          <p className={`dad-msg${fa ? ' fa' : ''}`}>{message}</p>
          <button className="dad-choice danger" data-act="both" onClick={() => answer(true)}>
            <span className={fa ? 'fa' : ''}>{many ? L('ترسیم‌ها و هشدارهایشان پاک شوند', 'Delete the drawings and their alerts') : L('ترسیم و هشدارهایش پاک شوند', 'Delete the drawing and its alerts')}</span>
          </button>
          <button className="dad-choice" data-act="keep" onClick={() => answer(false)}>
            <span className={fa ? 'fa' : ''}>{many ? L('ترسیم‌ها بروند، هشدارها بمانند', 'Delete the drawings, keep the alerts') : L('ترسیم برود، هشدارها بمانند', 'Delete the drawing, keep the alerts')}</span>
            <small className={`muted${fa ? ' fa' : ''}`}>{L('هشداری که می‌ماند، در مرکز هشدارها نشان‌دار می‌شود که ترسیمش رفته، و تا وقتی خودتان پاکش کنید یا خط را دوباره بکشید همان‌جا می‌ماند.', 'A kept alert is marked in the alert centre as having lost its drawing, and it stays there until you delete it or draw the line again.')}</small>
          </button>
        </div>
        <footer className="dialog-foot">
          <button className="btn btn-primary dad-cancel" data-act="cancel" autoFocus onClick={() => answer(null)}><span className={fa ? 'fa' : ''}>{L('ترسیم بماند', 'Keep the drawing')}</span></button>
        </footer>
      </div>
    </div>
  );
}
