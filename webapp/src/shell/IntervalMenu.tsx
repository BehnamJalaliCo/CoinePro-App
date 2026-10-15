// TradingView's interval control: the starred intervals as buttons in the top bar, the menu under
// the caret (Add custom interval…, then Minutes, Hours and Days, each group foldable, a star on
// every row that adds or removes it from the bar, a × on the reader's own), the custom-interval
// dialog, and the "Change interval" box that a digit typed anywhere on the chart opens ("45",
// Enter → 45 minutes; "4h", "3D", "2W", "3M" work too). Every interval drives the active chart;
// those a backend lacks are built by folding a finer one in the data layer.
import { useEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { Icon } from '../ui/Icon';
import { Dropdown } from '../ui/Menu';
import { usePrefs, useT } from '../state/prefs';
import { useL } from '../pages/kit';
import {
  INTERVALS, byLength, intervalGroup, intervalId, intervalLabel, intervalLongLabel, isStandardInterval,
  parseIntervalInput, type IntervalGroup, type IntervalUnit,
} from '../data/intervals';
import { canDraw } from '../data/candles';

/** True from the first key that opens the interval box until it closes — before React has drawn it,
 *  so keys typed in between still go to the box (App's key handler reads it). */
export const intervalBox = { open: false };
import { activeChart, applyInterval, useGrid } from '../state/grid';

const GROUPS: { id: IntervalGroup; fa: string; en: string }[] = [
  { id: 'minutes', fa: 'دقیقه', en: 'Minutes' },
  { id: 'hours', fa: 'ساعت', en: 'Hours' },
  { id: 'days', fa: 'روز', en: 'Days' },
];
const FOLD_KEY = 'pc.ivmenu.folded.v1';
const readFolded = (): IntervalGroup[] => { try { const v = JSON.parse(localStorage.getItem(FOLD_KEY) ?? '[]') as unknown; return Array.isArray(v) ? (v as IntervalGroup[]) : []; } catch { return []; } };

/** The interval the top bar shows: the active chart's. */
function useActiveInterval(): { symbol: string; interval: string } {
  const prefSymbol = usePrefs((s) => s.symbol);
  const prefInterval = usePrefs((s) => s.interval);
  const active = useGrid((g) => g.active);
  const cell = useGrid((g) => (g.active > 0 ? g.cells[g.active - 1] : undefined));
  return active > 0 && cell ? { symbol: cell.symbol, interval: cell.interval } : { symbol: prefSymbol, interval: prefInterval };
}

export function IntervalControl() {
  const t = useT();
  const lang = usePrefs((s) => s.lang);
  const favs = usePrefs((s) => s.favIntervals);
  const custom = usePrefs((s) => s.customIntervals);
  const { symbol, interval } = useActiveInterval();
  const [open, setOpen] = useState(false);
  const [adding, setAdding] = useState(false);
  const [box, setBox] = useState<string | null>(null);
  const caret = useRef<HTMLButtonElement>(null);

  // A digit typed on the chart (App) opens the box with it.
  useEffect(() => {
    // Digits typed before the box has the focus still land in it.
    const on = (e: Event) => { intervalBox.open = true; setBox((b) => (b ?? '') + String((e as CustomEvent<string>).detail ?? '')); };
    window.addEventListener('pc:interval-box', on);
    return () => window.removeEventListener('pc:interval-box', on);
  }, []);

  const bar = favs.filter((iv) => canDraw(symbol, iv)).sort(byLength);
  return (
    <>
      <span className="iv-group" role="group" aria-label={t('interval')}>
        {bar.map((iv) => (
          <button key={iv} className={`ib iv${iv === interval ? ' on' : ''}`} aria-pressed={iv === interval}
            onClick={() => applyInterval(iv)} title={intervalLongLabel(iv, lang)}>
            <span className="label num">{intervalLabel(iv)}</span>
          </button>
        ))}
        {!bar.includes(interval) ? (
          <button className="ib iv on" aria-pressed title={intervalLongLabel(interval, lang)} onClick={() => setOpen(!open)}>
            <span className="label num">{intervalLabel(interval)}</span>
          </button>
        ) : null}
        <button ref={caret} className={`ib iv-caret${open ? ' open' : ''}`} title={t('interval')} aria-haspopup="menu" aria-expanded={open} onClick={() => setOpen(!open)}>
          <Icon name="ph-caret-down" size="sm" />
        </button>
      </span>
      {open ? (
        <IntervalMenu anchor={caret.current} symbol={symbol} interval={interval} favs={favs} custom={custom}
          onClose={() => setOpen(false)} onAdd={() => { setOpen(false); setAdding(true); }} />
      ) : null}
      {adding ? <CustomIntervalDialog onClose={() => setAdding(false)} /> : null}
      {box !== null ? <ChangeIntervalBox value={box} onChange={setBox} onAppend={(k) => setBox((b) => (b ?? '') + k)} onClose={() => { intervalBox.open = false; setBox(null); }} /> : null}
    </>
  );
}

function IntervalMenu({ anchor, symbol, interval, favs, custom, onClose, onAdd }: {
  anchor: HTMLElement | null; symbol: string; interval: string; favs: string[]; custom: string[]; onClose(): void; onAdd(): void;
}) {
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const fa = lang === 'fa';
  const [folded, setFolded] = useState<IntervalGroup[]>(readFolded);
  // The old Pro-Chart's search over the menu: a filter, and any interval typed (45, 4h, 3D, 2W).
  const [q, setQ] = useState('');
  const needle = q.trim().toLowerCase();
  const typed = needle ? parseIntervalInput(q) : null;
  const match = (id: string) => !needle || id === typed || intervalLabel(id).toLowerCase().includes(needle)
    || intervalLongLabel(id, 'en').includes(needle) || intervalLongLabel(id, 'fa').includes(q.trim());
  const all = useMemo(() => [...new Set([...INTERVALS.map((i) => i.id), ...custom])].sort(byLength), [custom]);
  const fold = (g: IntervalGroup) => {
    const next = folded.includes(g) ? folded.filter((x) => x !== g) : [...folded, g];
    setFolded(next);
    try { localStorage.setItem(FOLD_KEY, JSON.stringify(next)); } catch { /* storage blocked */ }
  };
  const star = (id: string) => {
    const p = usePrefs.getState();
    p.setFavIntervals(p.favIntervals.includes(id) ? p.favIntervals.filter((x) => x !== id) : [...p.favIntervals, id].sort(byLength));
  };
  const removeCustom = (id: string) => {
    const p = usePrefs.getState();
    p.setCustomIntervals(p.customIntervals.filter((x) => x !== id));
    p.setFavIntervals(p.favIntervals.filter((x) => x !== id));
  };
  return (
    <Dropdown anchor={anchor} onClose={onClose} className="iv-menu tvm">
      <div dir={fa ? 'rtl' : 'ltr'}>
        <button className="menu-item iv-add" onClick={onAdd}>
          <Icon name="ph-plus" size="sm" /><span className={fa ? 'fa' : ''}>{L('افزودن بازه‌ی دلخواه…', 'Add custom interval…')}</span>
        </button>
        <label className="iv-search">
          <Icon name="search" size="sm" />
          <input value={q} onChange={(e) => setQ(e.target.value)} placeholder={L('جستجو یا 45، 4h، 3D', 'Search or type 45, 4h, 3D')} dir="auto" spellCheck={false} aria-label={L('جستجوی بازه', 'Search intervals')}
            onKeyDown={(e) => {
              if (e.key !== 'Enter') return;
              const pick = typed && canDraw(symbol, typed) ? typed : all.find((id) => match(id) && canDraw(symbol, id));
              if (pick) { applyInterval(pick); onClose(); }
            }} />
        </label>
        {typed && !all.includes(typed) ? (
          <div role="menuitem" tabIndex={-1} className={`menu-item iv-row${canDraw(symbol, typed) ? '' : ' off'}`} onClick={() => { if (canDraw(symbol, typed)) { applyInterval(typed); onClose(); } }}>
            <span className={`iv-name${fa ? ' fa' : ''}`}>{intervalLongLabel(typed, lang)}</span>
          </div>
        ) : null}
        {GROUPS.map((g) => {
          const ids = all.filter((id) => intervalGroup(id) === g.id && match(id));
          if (!ids.length) return null;
          const shut = folded.includes(g.id) && !needle;
          return (
            <div key={g.id} className="menu-group iv-group-sec">
              <button className="menu-title iv-head" aria-expanded={!shut} onClick={() => fold(g.id)}>
                <span className={fa ? 'fa' : ''}>{fa ? g.fa : g.en}</span>
                <Icon name={shut ? 'ph-caret-down' : 'ph-caret-up'} size="sm" />
              </button>
              {shut ? null : ids.map((id) => {
                const ok = canDraw(symbol, id);
                const fav = favs.includes(id);
                const mine = !isStandardInterval(id);
                return (
                  <div key={id} role="menuitemradio" aria-checked={id === interval} aria-disabled={!ok} tabIndex={-1}
                    className={`menu-item iv-row${id === interval ? ' on' : ''}${ok ? '' : ' off'}`}
                    onClick={() => { if (!ok) return; applyInterval(id); onClose(); }}>
                    <span className={`iv-name${fa ? ' fa' : ''}`}>{intervalLongLabel(id, lang)}</span>
                    {mine ? (
                      <button className="lt iv-x" title={L('حذف', 'Remove')} aria-label="remove" onClick={(e) => { e.stopPropagation(); removeCustom(id); }}><Icon name="ph-x" size="sm" /></button>
                    ) : null}
                    <button className={`lt iv-star${fav ? ' fav' : ''}`} aria-pressed={fav} title={fav ? L('برداشتن از نوار', 'Remove from favorites') : L('افزودن به نوار', 'Add to favorites')}
                      onClick={(e) => { e.stopPropagation(); star(id); }}>
                      <Icon name={fav ? 'ph-star-fill' : 'ph-star'} size="sm" />
                    </button>
                  </div>
                );
              })}
            </div>
          );
        })}
      </div>
    </Dropdown>
  );
}

const UNITS: { id: IntervalUnit | 'h'; fa: string; en: string }[] = [
  { id: 'm', fa: 'دقیقه', en: 'minutes' }, { id: 'h', fa: 'ساعت', en: 'hours' }, { id: 'D', fa: 'روز', en: 'days' },
  { id: 'W', fa: 'هفته', en: 'weeks' }, { id: 'M', fa: 'ماه', en: 'months' },
];

/** TradingView's "Add custom interval": a type and a count; the new interval opens at once. */
function CustomIntervalDialog({ onClose }: { onClose(): void }) {
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const fa = lang === 'fa';
  const [unit, setUnit] = useState<IntervalUnit | 'h'>('m');
  const [count, setCount] = useState('');
  const n = Number(count);
  const id = Number.isInteger(n) && n > 0 ? (unit === 'h' ? intervalId(n * 60, 'm') : intervalId(n, unit)) : null;
  const parsed = id ? parseIntervalInput(id) : null;
  const ok = parsed && canDraw(activeChart().symbol, parsed);
  const add = () => {
    if (!parsed || !ok) return;
    const p = usePrefs.getState();
    if (!isStandardInterval(parsed) && !p.customIntervals.includes(parsed)) p.setCustomIntervals([...p.customIntervals, parsed].sort(byLength));
    applyInterval(parsed);
    onClose();
  };
  return createPortal(
    <div className="scrim center" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <form className="dialog tv-small" role="dialog" aria-modal="true" dir={fa ? 'rtl' : 'ltr'} onSubmit={(e) => { e.preventDefault(); add(); }}
        onKeyDown={(e) => { if (e.key === 'Escape') { e.stopPropagation(); onClose(); } }}>
        <header className="dialog-head"><h2 className={fa ? 'fa' : ''}>{L('افزودن بازه‌ی دلخواه', 'Add custom interval')}</h2>
          <button type="button" className="ib" onClick={onClose} aria-label="close"><Icon name="ph-x" size="md" /></button></header>
        <div className="tv-small-body">
          <label className="tv-field"><span className={fa ? 'fa' : ''}>{L('نوع', 'Type')}</span>
            <select value={unit} onChange={(e) => setUnit(e.target.value as IntervalUnit | 'h')}>
              {UNITS.map((u) => <option key={u.id} value={u.id}>{fa ? u.fa : u.en}</option>)}
            </select>
          </label>
          <label className="tv-field"><span className={fa ? 'fa' : ''}>{L('بازه', 'Interval')}</span>
            <input autoFocus inputMode="numeric" value={count} onChange={(e) => setCount(e.target.value.replace(/[^\d۰-۹]/g, '').replace(/[۰-۹]/g, (d) => String('۰۱۲۳۴۵۶۷۸۹'.indexOf(d))))} className="num" dir="ltr" aria-label={L('بازه', 'Interval')} />
          </label>
          {count && !ok ? <p className="down small fa">{parsed ? L('این بازه برای این نماد ساخته نمی‌شود.', 'This symbol cannot be drawn at that interval.') : L('عدد درستی نیست.', 'Not a valid number.')}</p> : null}
          {parsed && ok ? <p className={`muted small${fa ? ' fa' : ''}`}>{intervalLongLabel(parsed, lang)}</p> : null}
        </div>
        <footer className="dialog-foot end">
          <button type="button" className="btn btn-quiet" onClick={onClose}><span className={fa ? 'fa' : ''}>{L('انصراف', 'Cancel')}</span></button>
          <button type="submit" className="btn btn-primary" disabled={!ok}><span className={fa ? 'fa' : ''}>{L('افزودن', 'Add')}</span></button>
        </footer>
      </form>
    </div>,
    document.body,
  );
}

/** TradingView's "Change interval": what was typed, what it means, Enter applies. */
function ChangeIntervalBox({ value, onChange: setValue, onAppend, onClose }: { value: string; onChange(v: string): void; onAppend(key: string): void; onClose(): void }) {
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const fa = lang === 'fa';
  const input = useRef<HTMLInputElement>(null);
  useEffect(() => { const el = input.current; if (el) { el.focus(); el.setSelectionRange(el.value.length, el.value.length); } }, []);
  const id = parseIntervalInput(value);
  const ok = id && canDraw(activeChart().symbol, id);
  // What has been typed, ahead of the render that shows it.
  const latest = useRef(value);
  latest.current = value;
  const apply = () => {
    const pick = parseIntervalInput(latest.current);
    if (!pick || !canDraw(activeChart().symbol, pick)) return;
    applyInterval(pick);
    onClose();
  };
  // Keys typed in the instant before the field has the focus still count, Enter and Esc too.
  const live = useRef({ apply, onClose, onAppend });
  live.current = { apply, onClose, onAppend };
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.target === input.current || e.ctrlKey || e.metaKey || e.altKey) return;
      const l = live.current;
      if (e.key === 'Enter') { e.preventDefault(); l.apply(); }
      else if (e.key === 'Escape') { e.preventDefault(); l.onClose(); }
      else if (/^[0-9a-zA-Z]$/.test(e.key)) { e.preventDefault(); e.stopPropagation(); latest.current += e.key; l.onAppend(e.key); input.current?.focus(); }
    };
    window.addEventListener('keydown', onKey, true);
    return () => window.removeEventListener('keydown', onKey, true);
  }, []);
  return createPortal(
    <div className="scrim clear" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div className="iv-box" role="dialog" aria-label={L('تغییر بازه‌ی زمانی', 'Change interval')} dir={fa ? 'rtl' : 'ltr'}>
        <h3 className={fa ? 'fa' : ''}>{L('تغییر بازه‌ی زمانی', 'Change interval')}</h3>
        <input ref={input} className="num" dir="ltr" value={value} spellCheck={false} autoComplete="off" aria-label={L('بازه', 'Interval')}
          onChange={(e) => setValue(e.target.value)}
          onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); apply(); } else if (e.key === 'Escape') { e.stopPropagation(); onClose(); } }}
          onBlur={() => setTimeout(onClose, 120)} />
        <p className={`iv-box-hint${ok ? '' : ' bad'}${fa ? ' fa' : ''}`}>{!value ? ' ' : id ? (ok ? intervalLongLabel(id, lang) : L('برای این نماد نیست', 'Not applicable')) : L('بازه‌ی نادرست', 'Invalid interval')}</p>
      </div>
    </div>,
    document.body,
  );
}
