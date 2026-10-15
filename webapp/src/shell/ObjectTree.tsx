import { useState, type DragEvent, type ReactNode } from 'react';
import { useChartBus } from '../chart/chartBus';
import { usePrefs } from '../state/prefs';
import { useDrawing } from '../state/drawing';
import { useStudies, VOLUME } from '../state/studies';
import { entryOf } from '../chart/indicatorCatalog';
import { toolIcon } from '../chart/tools';
import { formatPrice } from '../chart/format';
import { bumpDrawings, drawingName } from '../chart/DrawingActions';
import { useL } from '../pages/kit';
import { faNum } from '../i18n/time';
import { Icon } from '../ui/Icon';
import type { DrawingObject } from '../engine/drawings.js';
import '../chart/drawing.css';

type Pt = { t: number; p: number };

/** What a row says about a drawing: its own name, its text, or the price of a horizontal line. */
function describe(d: DrawingObject, digits: number): string {
  if (typeof d.text === 'string' && d.text.trim()) return d.text.replace(/\\n|\n/g, ' ').slice(0, 32);
  if (Array.isArray(d.cells)) { const first = (d.cells as unknown[][]).flat().map((c) => String(c ?? '').trim()).find(Boolean); if (first) return first.slice(0, 32); }
  if (d.type === 'hline' && typeof d.p === 'number') return formatPrice(d.p, digits);
  if (['hray', 'pricelabel', 'crossline'].includes(d.type) && d.p0) return formatPrice((d.p0 as Pt).p, digits);
  return '';
}

// TradingView's object tree: a header with the count and hide-all / lock-all / remove-all /
// new-group buttons; the indicators (each with its settings), then the drawings, top of the stack
// first, each with its colour, a name you can change, show, lock and remove. A click selects a
// drawing on the chart (and opens its style bar), Ctrl or Shift adds to the selection, dragging a
// row changes the stacking order or moves it into a group, a group hides and locks together.
export function ObjectTree() {
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const layer = useChartBus((s) => s.drawingLayer);
  useChartBus((s) => s.drawingVersion);
  useChartBus((s) => s.selectedDrawing);
  const studies = useStudies((s) => s.list);
  const toggleStudy = useStudies((s) => s.toggle);
  const removeStudy = useStudies((s) => s.remove);
  const st = useDrawing();
  const [renaming, setRenaming] = useState<string | null>(null);
  const [draft, setDraft] = useState('');
  const [closed, setClosed] = useState<Set<string>>(new Set());
  const [dragFrom, setDragFrom] = useState<number | null>(null);
  const [over, setOver] = useState<{ key: string; where: 'before' | 'after' | 'into' } | null>(null);
  const drawings = layer?.getDrawings() ?? [];
  const selected = new Set(layer?.selectedIndices() ?? []);
  const digits = layer?.digits ?? 2;
  const n = (v: number) => (lang === 'fa' ? faNum(v) : String(v));
  const allHidden = drawings.length > 0 && drawings.every((d) => d.visible === false);
  const allLocked = drawings.length > 0 && drawings.every((d) => d.locked);
  const groupNames = [...new Set(drawings.map((d) => (typeof d.group === 'string' ? d.group : '')).filter(Boolean))];

  const commitRename = (apply: (name: string) => void) => { const v = draft.trim(); apply(v); setRenaming(null); bumpDrawings(); };
  const membersOf = (g: string) => drawings.map((d, i) => (d.group === g ? i : -1)).filter((i) => i >= 0);

  const newGroup = () => {
    if (!layer) return;
    const sel = layer.selectedIndices();
    if (!sel.length) return;
    let k = groupNames.length + 1;
    const base = L('گروه', 'Group');
    while (groupNames.includes(`${base} ${n(k)}`)) k++;
    layer.setStyleMany(sel, { group: `${base} ${n(k)}` });
    bumpDrawings();
  };

  // Drag and drop: onto a row takes that row's place in the stack (and its group); onto a group's
  // header joins the group.
  const onDragStart = (i: number) => (e: DragEvent) => { setDragFrom(i); e.dataTransfer.effectAllowed = 'move'; try { e.dataTransfer.setData('text/plain', String(i)); } catch { /* old browsers */ } };
  const onDragEnd = () => { setDragFrom(null); setOver(null); };
  const dropOnRow = (to: number) => (e: DragEvent) => {
    e.preventDefault();
    const from = dragFrom;
    setDragFrom(null); setOver(null);
    if (!layer || from == null || from === to) return;
    const target = drawings[to];
    const grp = target && typeof target.group === 'string' ? target.group : undefined;
    const d = drawings[from];
    if (d && d.group !== grp) layer.setStyle(from, { group: grp });
    layer.move(from, to);
    bumpDrawings();
  };
  const dropOnGroup = (g: string) => (e: DragEvent) => {
    e.preventDefault();
    const from = dragFrom;
    setDragFrom(null); setOver(null);
    if (!layer || from == null) return;
    layer.setStyle(from, { group: g });
    bumpDrawings();
  };

  const row = (d: DrawingObject, i: number, inGroup: boolean): ReactNode => {
    const key = `d${i}`;
    const sub = describe(d, digits);
    const name = typeof d.name === 'string' && d.name ? d.name : drawingName(d.type, lang);
    const cls = ['obj-row', d.visible === false ? 'off' : '', selected.has(i) ? 'on' : '', inGroup ? 'in-group' : '', dragFrom === i ? 'dragging' : '', over?.key === key ? `drop-${over.where}` : ''].filter(Boolean).join(' ');
    return (
      <div key={key} className={cls} draggable={renaming !== key} data-index={i}
        onDragStart={onDragStart(i)} onDragEnd={onDragEnd}
        onDragOver={(e) => { if (dragFrom == null) return; e.preventDefault(); const r = e.currentTarget.getBoundingClientRect(); setOver({ key, where: e.clientY < r.top + r.height / 2 ? 'before' : 'after' }); }}
        onDragLeave={() => setOver((o) => (o?.key === key ? null : o))}
        onDrop={dropOnRow(i)}
        onClick={(e) => { if (!layer) return; if (e.ctrlKey || e.metaKey || e.shiftKey) layer.toggleSelect(i); else layer.selectAt(i); bumpDrawings(); }}>
        <span className="obj-dot" style={{ background: typeof d.color === 'string' ? d.color : 'var(--text-3)' }} />
        <Icon name={toolIcon(d.type)} size="sm" />
        {renaming === key ? (
          <input className="obj-rename" autoFocus value={draft} aria-label={L('نام', 'Name')} onClick={(e) => e.stopPropagation()}
            onChange={(e) => setDraft(e.target.value)} onBlur={() => commitRename((v) => layer?.setStyle(i, { name: v || undefined }))}
            onKeyDown={(e) => { e.stopPropagation(); if (e.key === 'Enter') commitRename((v) => layer?.setStyle(i, { name: v || undefined })); if (e.key === 'Escape') setRenaming(null); }} />
        ) : (
          <span className="obj-name" title={L('دوبار کلیک: تغییر نام', 'Double click to rename')} onDoubleClick={(e) => { e.stopPropagation(); setDraft(typeof d.name === 'string' ? d.name : ''); setRenaming(key); }}>
            {name}{sub ? <span className="obj-sub">{sub}</span> : null}
          </span>
        )}
        <span className="obj-tools">
          <button className="lt" title={L('تغییر نام', 'Rename')} aria-label={L('تغییر نام', 'Rename')} onClick={(e) => { e.stopPropagation(); setDraft(typeof d.name === 'string' ? d.name : ''); setRenaming(key); }}><Icon name="ph-pencil-simple" size="sm" /></button>
          <button className="lt" title={d.visible === false ? L('نمایش', 'Show') : L('پنهان', 'Hide')} aria-label={L('نمایش', 'Show')} onClick={(e) => { e.stopPropagation(); layer?.setStyle(i, { visible: d.visible === false }); bumpDrawings(); }}><Icon name={d.visible === false ? 'eye_off' : 'ph-eye'} size="sm" /></button>
          <button className={`lt${d.locked ? ' on' : ''}`} title={d.locked ? L('بازکردن قفل', 'Unlock') : L('قفل', 'Lock')} aria-label={L('قفل', 'Lock')} onClick={(e) => { e.stopPropagation(); layer?.setStyle(i, { locked: !d.locked }); bumpDrawings(); }}><Icon name={d.locked ? 'lock' : 'unlock'} size="sm" /></button>
          <button className="lt" title={L('حذف', 'Remove')} aria-label={L('حذف', 'Remove')} onClick={(e) => { e.stopPropagation(); layer?.removeAt(i); bumpDrawings(); }}><Icon name="ph-trash" size="sm" /></button>
        </span>
      </div>
    );
  };

  const groupRow = (g: string, idxs: number[]): ReactNode => {
    const key = `g${g}`;
    const hidden = idxs.every((i) => drawings[i]?.visible === false);
    const locked = idxs.every((i) => drawings[i]?.locked);
    const isClosed = closed.has(g);
    return (
      <div key={key} className={`obj-row grp${hidden ? ' off' : ''}${over?.key === key ? ' drop-into' : ''}`} data-group={g}
        onDragOver={(e) => { if (dragFrom == null) return; e.preventDefault(); setOver({ key, where: 'into' }); }}
        onDragLeave={() => setOver((o) => (o?.key === key ? null : o))}
        onDrop={dropOnGroup(g)}
        onClick={() => setClosed((s) => { const x = new Set(s); if (x.has(g)) x.delete(g); else x.add(g); return x; })}>
        <span className={`obj-caret${isClosed ? ' closed' : ''}`}><Icon name="ph-caret-down" size="sm" /></span>
        <Icon name={isClosed ? 'ph-folder' : 'ph-folder-open'} size="sm" />
        {renaming === key ? (
          <input className="obj-rename" autoFocus value={draft} aria-label={L('نام گروه', 'Group name')} onClick={(e) => e.stopPropagation()}
            onChange={(e) => setDraft(e.target.value)} onBlur={() => commitRename((v) => { if (v && layer) layer.setStyleMany(idxs, { group: v }); })}
            onKeyDown={(e) => { e.stopPropagation(); if (e.key === 'Enter') commitRename((v) => { if (v && layer) layer.setStyleMany(idxs, { group: v }); }); if (e.key === 'Escape') setRenaming(null); }} />
        ) : (
          <span className="obj-name" onDoubleClick={(e) => { e.stopPropagation(); setDraft(g); setRenaming(key); }}>{g}<span className="obj-count">{n(idxs.length)}</span></span>
        )}
        <span className="obj-tools">
          {selected.size && [...selected].some((i) => drawings[i]?.group !== g) ? (
            <button className="lt" title={L('افزودن انتخاب‌ها به این گروه', 'Move the selected drawings into this group')} aria-label={L('افزودن به گروه', 'Move into group')} data-act="into-group" onClick={(e) => { e.stopPropagation(); layer?.setStyleMany([...selected], { group: g }); bumpDrawings(); }}><Icon name="ph-folder-plus" size="sm" /></button>
          ) : null}
          <button className="lt" title={L('تغییر نام گروه', 'Rename group')} aria-label={L('تغییر نام گروه', 'Rename group')} onClick={(e) => { e.stopPropagation(); setDraft(g); setRenaming(key); }}><Icon name="ph-pencil-simple" size="sm" /></button>
          <button className="lt" title={hidden ? L('نمایش گروه', 'Show group') : L('پنهان‌کردن گروه', 'Hide group')} aria-label={L('نمایش گروه', 'Show group')} onClick={(e) => { e.stopPropagation(); layer?.setStyleMany(idxs, { visible: hidden }); bumpDrawings(); }}><Icon name={hidden ? 'eye_off' : 'ph-eye'} size="sm" /></button>
          <button className={`lt${locked ? ' on' : ''}`} title={locked ? L('بازکردن قفل گروه', 'Unlock group') : L('قفل گروه', 'Lock group')} aria-label={L('قفل گروه', 'Lock group')} onClick={(e) => { e.stopPropagation(); layer?.setStyleMany(idxs, { locked: !locked }); bumpDrawings(); }}><Icon name={locked ? 'lock' : 'unlock'} size="sm" /></button>
          <button className="lt" title={L('برداشتن گروه', 'Ungroup')} aria-label={L('برداشتن گروه', 'Ungroup')} onClick={(e) => { e.stopPropagation(); layer?.setStyleMany(idxs, { group: undefined }); bumpDrawings(); }}><Icon name="ph-x" size="sm" /></button>
        </span>
      </div>
    );
  };

  // Top of the stack first; a group gathers its members where its topmost member stands.
  const out: ReactNode[] = [];
  const seen = new Set<string>();
  for (let i = drawings.length - 1; i >= 0; i--) {
    const d = drawings[i]!;
    const g = typeof d.group === 'string' && d.group ? d.group : '';
    if (!g) { out.push(row(d, i, false)); continue; }
    if (seen.has(g)) continue;
    seen.add(g);
    const idxs = membersOf(g);
    out.push(groupRow(g, idxs));
    if (!closed.has(g)) for (let k = idxs.length - 1; k >= 0; k--) out.push(row(drawings[idxs[k]!]!, idxs[k]!, true));
  }

  const studyName = (key: string) => (key === VOLUME ? L('حجم', 'Volume') : (lang === 'fa' ? entryOf(key)?.fa : entryOf(key)?.en) ?? key);
  return (
    <section className="panel-generic objects" dir={lang === 'fa' ? 'rtl' : 'ltr'}>
      <header className="panel-head">
        <span>{L('درخت اشیا', 'Object tree')}</span>
        <span className="otree-count num">{n(drawings.length + studies.length)}</span>
        <span className="otree-tools">
          <button className="lt" title={L('گروه تازه از انتخاب‌ها', 'Create a group of the selected drawings')} aria-label={L('گروه تازه', 'New group')} disabled={!selected.size} onClick={newGroup}><Icon name="ph-folder-plus" size="sm" /></button>
          <button className={`lt${allHidden ? ' on' : ''}`} title={allHidden ? L('نمایش همه', 'Show all') : L('پنهان‌کردن همه', 'Hide all')} aria-label={L('پنهان‌کردن همه', 'Hide all')} disabled={!drawings.length} onClick={() => { layer?.hideAll(!allHidden); bumpDrawings(); }}><Icon name={allHidden ? 'eye_off' : 'ph-eye'} size="sm" /></button>
          <button className={`lt${allLocked ? ' on' : ''}`} title={allLocked ? L('بازکردن قفل همه', 'Unlock all') : L('قفل همه', 'Lock all')} aria-label={L('قفل همه', 'Lock all')} disabled={!drawings.length} onClick={() => { st.setLocked(!allLocked); if (st.locked === !allLocked) { layer?.lockAll(!allLocked); } bumpDrawings(); }}><Icon name={allLocked ? 'lock' : 'unlock'} size="sm" /></button>
          <button className="lt" title={L('حذف همه‌ی ترسیم‌ها', 'Remove all drawings')} aria-label={L('حذف همه', 'Remove all')} disabled={!drawings.length} onClick={() => { layer?.clearAll(); bumpDrawings(); }}><Icon name="trash2" size="sm" /></button>
        </span>
      </header>
      <div className="panel-body">
        {studies.length ? <div className="otree-h">{L('اندیکاتورها', 'Indicators')}</div> : null}
        {studies.map((s) => (
          <div key={s.uid} className={`obj-row${s.hidden ? ' off' : ''}`} data-study={s.uid}>
            <Icon name="ph-chart-line-up" size="sm" />
            <span className="obj-name">{studyName(s.key)}</span>
            <span className="obj-tools">
              {s.key !== VOLUME ? <button className="lt" title={L('تنظیمات', 'Settings')} aria-label={L('تنظیمات', 'Settings')} data-act="study-settings" onClick={() => window.dispatchEvent(new CustomEvent('pc:study-settings', { detail: { uid: s.uid } }))}><Icon name="ph-gear" size="sm" /></button> : null}
              <button className="lt" onClick={() => toggleStudy(s.uid)} title={s.hidden ? L('نمایش', 'Show') : L('پنهان', 'Hide')} aria-label={L('نمایش', 'Show')}><Icon name={s.hidden ? 'eye_off' : 'ph-eye'} size="sm" /></button>
              <button className="lt" onClick={() => removeStudy(s.uid)} title={L('حذف', 'Remove')} aria-label={L('حذف', 'Remove')}><Icon name="ph-trash" size="sm" /></button>
            </span>
          </div>
        ))}
        <div className="otree-h">{L('ترسیم‌ها', 'Drawings')}<span className="num">{drawings.length ? n(drawings.length) : ''}</span></div>
        {!drawings.length ? <div className="empty">{L('روی این نماد ترسیمی نیست.', 'No drawings on this symbol.')}</div> : null}
        {out}
      </div>
    </section>
  );
}
