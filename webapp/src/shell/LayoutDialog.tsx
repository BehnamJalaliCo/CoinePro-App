// The layout menu's dialogs: a name for Save / Save as / Rename / Make a copy, and TradingView's
// "Open layout" — every saved layout with a search, sorting by name or by when it was modified,
// and delete.
import { useEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { Icon } from '../ui/Icon';
import { useL } from '../pages/kit';
import { usePrefs } from '../state/prefs';
import { useLayouts } from '../state/layouts';
import { intervalLabel } from '../data/intervals';
import { cellCount } from '../state/grid';
import { faNum } from '../i18n/time';
import { toast } from './toast';
import type { LayoutDialogMode } from './SaveMenus';

export function LayoutDialog({ mode, onClose }: { mode: LayoutDialogMode; onClose(): void }) {
  return mode === 'open' ? <OpenLayoutDialog onClose={onClose} /> : <NameDialog mode={mode} onClose={onClose} />;
}

function NameDialog({ mode, onClose }: { mode: Exclude<LayoutDialogMode, 'open'>; onClose(): void }) {
  const L = useL();
  const fa = usePrefs((s) => s.lang) === 'fa';
  const { current, layouts, save, rename, copy } = useLayouts();
  const initial = mode === 'rename' ? current ?? '' : mode === 'copy' ? (current ? `${current} ${L('(رونوشت)', '(copy)')}` : '') : mode === 'save' ? '' : '';
  const [name, setName] = useState(initial);
  const input = useRef<HTMLInputElement>(null);
  useEffect(() => { input.current?.select(); }, []);
  const n = name.trim();
  const clash = n && n !== current && layouts.some((l) => l.name === n);
  const title = mode === 'rename' ? L('تغییر نام چیدمان', 'Rename chart layout') : mode === 'copy' ? L('ساخت رونوشت از چیدمان', 'Copy chart layout') : L('ذخیره‌ی چیدمان', 'Save chart layout');
  const submit = () => {
    if (!n) return;
    if (mode === 'rename' && current) rename(current, n);
    else if (mode === 'copy') copy(n);
    else save(n);
    toast(mode === 'rename' ? L('نام چیدمان عوض شد', 'Layout renamed') : L('چیدمان ذخیره شد', 'Layout saved'));
    onClose();
  };
  return createPortal(
    <div className="scrim center" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <form className="dialog tv-small" role="dialog" aria-modal="true" aria-label={title} dir={fa ? 'rtl' : 'ltr'}
        onSubmit={(e) => { e.preventDefault(); submit(); }} onKeyDown={(e) => { if (e.key === 'Escape') { e.stopPropagation(); onClose(); } }}>
        <header className="dialog-head"><h2 className={fa ? 'fa' : ''}>{title}</h2>
          <button type="button" className="ib" onClick={onClose} aria-label="close"><Icon name="ph-x" size="md" /></button></header>
        <div className="tv-small-body">
          <label className="tv-field wide"><span className={fa ? 'fa' : ''}>{L('نام چیدمان', 'Layout name')}</span>
            <input ref={input} autoFocus value={name} onChange={(e) => setName(e.target.value)} maxLength={60} dir="auto" placeholder={L('مثلاً تحلیل روزانه‌ی طلا', 'e.g. Gold daily')} />
          </label>
          {clash ? <p className={`muted small${fa ? ' fa' : ''}`}>{L('چیدمانی با این نام هست و جایگزین می‌شود.', 'A layout with this name exists and will be replaced.')}</p> : null}
        </div>
        <footer className="dialog-foot end">
          <button type="button" className="btn btn-quiet" onClick={onClose}><span className={fa ? 'fa' : ''}>{L('انصراف', 'Cancel')}</span></button>
          <button type="submit" className="btn btn-primary" disabled={!n}><span className={fa ? 'fa' : ''}>{mode === 'rename' ? L('تغییر نام', 'Rename') : L('ذخیره', 'Save')}</span></button>
        </footer>
      </form>
    </div>,
    document.body,
  );
}

function OpenLayoutDialog({ onClose }: { onClose(): void }) {
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const fa = lang === 'fa';
  const { layouts, current, load, remove } = useLayouts();
  const [q, setQ] = useState('');
  const [sort, setSort] = useState<'modified' | 'name'>('modified');
  const [cursor, setCursor] = useState(0);
  const rows = useMemo(() => {
    const needle = q.trim().toLowerCase();
    const hit = layouts.filter((l) => !needle || l.name.toLowerCase().includes(needle) || l.symbol.toLowerCase().includes(needle));
    return hit.sort((a, b) => (sort === 'name' ? a.name.localeCompare(b.name, lang) : b.savedAt - a.savedAt));
  }, [layouts, q, sort, lang]);
  useEffect(() => { setCursor(0); }, [q, sort]);
  const when = (ms: number) => {
    try { return new Intl.DateTimeFormat('en-GB', { year: 'numeric', month: 'short', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date(ms)); } catch { return ''; }
  };
  const open = (name: string) => { load(name); onClose(); };
  return createPortal(
    <div className="scrim" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div className="dialog open-layouts" role="dialog" aria-modal="true" aria-label={L('باز کردن چیدمان', 'Open layout')} dir={fa ? 'rtl' : 'ltr'}
        onKeyDown={(e) => {
          if (e.key === 'Escape') { e.stopPropagation(); onClose(); }
          else if (e.key === 'ArrowDown') { e.preventDefault(); setCursor((c) => Math.min(rows.length - 1, c + 1)); }
          else if (e.key === 'ArrowUp') { e.preventDefault(); setCursor((c) => Math.max(0, c - 1)); }
          else if (e.key === 'Enter' && rows[cursor]) { e.preventDefault(); open(rows[cursor]!.name); }
        }}>
        <header className="dialog-head"><h2 className={fa ? 'fa' : ''}>{L('باز کردن چیدمان', 'Open layout')}</h2>
          <button className="ib" onClick={onClose} aria-label="close"><Icon name="ph-x" size="md" /></button></header>
        <label className="ss-field">
          <Icon name="search" size="md" />
          <input autoFocus value={q} onChange={(e) => setQ(e.target.value)} placeholder={L('جستجو', 'Search')} dir="auto" aria-label={L('جستجو', 'Search')} />
        </label>
        <div className="ol-head">
          <button className={`ol-sort${sort === 'name' ? ' on' : ''}`} onClick={() => setSort('name')}><span className={fa ? 'fa' : ''}>{L('نام چیدمان', 'Layout name')}</span>{sort === 'name' ? <Icon name="ph-caret-down" size="sm" /> : null}</button>
          <span className={fa ? 'fa' : ''}>{L('نماد', 'Symbol')}</span>
          <button className={`ol-sort${sort === 'modified' ? ' on' : ''}`} onClick={() => setSort('modified')}><span className={fa ? 'fa' : ''}>{L('آخرین تغییر', 'Modified')}</span>{sort === 'modified' ? <Icon name="ph-caret-down" size="sm" /> : null}</button>
          <span />
        </div>
        <div className="ol-list" role="listbox">
          {!rows.length ? <div className="empty fa">{layouts.length ? L('چیزی پیدا نشد', 'Nothing matches') : L('هنوز چیدمانی ذخیره نشده است. از منوی ذخیره، «ذخیره‌ی چیدمان» را بزنید.', 'No saved layouts yet. Use Save layout in the save menu.')}</div> : null}
          {rows.map((l, i) => (
            <div key={l.name} role="option" aria-selected={i === cursor} className={`ol-row${i === cursor ? ' on' : ''}${l.name === current ? ' current' : ''}`}
              onMouseEnter={() => setCursor(i)} onClick={() => open(l.name)}>
              <span className="ol-name" dir="auto">{l.name}{l.name === current ? <Icon name="ph-check" size="sm" /> : null}</span>
              <span className="ol-sym"><span className="num">{l.symbol} · {intervalLabel(l.interval)}</span>{l.grid && cellCount(l.grid) > 1 ? <em className={fa ? 'fa' : ''} dir={fa ? 'rtl' : 'ltr'}>{fa ? `${faNum(cellCount(l.grid))} چارت` : `${cellCount(l.grid)} charts`}</em> : null}</span>
              <span className="num ol-when">{when(l.savedAt)}</span>
              <button className="lt" title={L('حذف', 'Delete')} aria-label="delete" onClick={(e) => {
                e.stopPropagation();
                if (window.confirm(L(`چیدمان «${l.name}» حذف شود؟`, `Delete layout "${l.name}"?`))) remove(l.name);
              }}><Icon name="trash2" size="sm" /></button>
            </div>
          ))}
        </div>
      </div>
    </div>,
    document.body,
  );
}
