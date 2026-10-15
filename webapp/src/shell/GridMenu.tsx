// TradingView's "Select layout": every arrangement of one to eight charts in rows by count, then
// "Sync in layout" (symbol, interval, crosshair, time, date range), then what the other charts
// take from the main one (its indicators, its horizontal lines), and maximise (Alt+Enter).
import { Dropdown } from '../ui/Menu';
import { Icon } from '../ui/Icon';
import { useL } from '../pages/kit';
import { usePrefs } from '../state/prefs';
import { PRESETS, CELL_AREAS, useGrid, type Mirror, type Sync } from '../state/grid';
import { faNum } from '../i18n/time';

/** The arrangement drawn as TradingView draws it: a rounded frame and the lines between charts. */
export function GridGlyph({ id }: { id: string }) {
  const p = PRESETS.find((x) => x.id === id) ?? PRESETS[0]!;
  const rows = p.areas.map((r) => r.split(' '));
  const W = 20; const H = 16;
  const cw = W / rows[0]!.length; const rh = H / rows.length;
  const cells = CELL_AREAS.slice(0, p.cells).map((a) => {
    let r0 = Infinity; let r1 = -1; let c0 = Infinity; let c1 = -1;
    rows.forEach((row, r) => row.forEach((x, c) => { if (x === a) { r0 = Math.min(r0, r); r1 = Math.max(r1, r); c0 = Math.min(c0, c); c1 = Math.max(c1, c); } }));
    return { x: c0 * cw, y: r0 * rh, w: (c1 - c0 + 1) * cw, h: (r1 - r0 + 1) * rh };
  });
  return (
    <svg className="grid-glyph" viewBox="-1 -1 22 18" aria-hidden>
      {cells.map((c, i) => <rect key={i} x={c.x} y={c.y} width={c.w} height={c.h} rx={i === 0 && p.cells === 1 ? 2.5 : 0} />)}
      <rect x={0} y={0} width={W} height={H} rx={2.5} />
    </svg>
  );
}

export function GridMenu({ anchor, onClose }: { anchor: HTMLElement | null; onClose(): void }) {
  const L = useL();
  const fa = usePrefs((s) => s.lang) === 'fa';
  const { grid, sync, mirror, maximized, setGrid, setSync, setMirror, setMaximized } = useGrid();
  const counts = [...new Set(PRESETS.map((p) => p.cells))];
  const syncRows: { id: keyof Sync; fa: string; en: string; hintFa: string; hintEn: string }[] = [
    { id: 'symbol', fa: 'نماد', en: 'Symbol', hintFa: 'نماد هر چارت را روی همه‌ی چارت‌ها می‌گذارد.', hintEn: 'A symbol change applies to every chart.' },
    { id: 'interval', fa: 'بازه‌ی زمانی', en: 'Interval', hintFa: 'بازه‌ی هر چارت را روی همه می‌گذارد.', hintEn: 'An interval change applies to every chart.' },
    { id: 'crosshair', fa: 'نشانگر', en: 'Crosshair', hintFa: 'نشانگر در همه‌ی چارت‌ها روی همان زمان می‌ایستد.', hintEn: 'The crosshair stands on the same time on every chart.' },
    { id: 'time', fa: 'زمان', en: 'Time', hintFa: 'جابه‌جایی و بزرگ‌نمایی یک چارت، بقیه را هم می‌برد.', hintEn: 'Scrolling or zooming one chart moves the others.' },
    { id: 'dateRange', fa: 'بازه‌ی تاریخ', en: 'Date range', hintFa: 'دکمه‌های 1D تا All روی همه‌ی چارت‌ها اثر می‌کنند.', hintEn: 'The 1D…All range buttons apply to every chart.' },
  ];
  const mirrorRows: { id: keyof Mirror; fa: string; en: string }[] = [
    { id: 'studies', fa: 'اندیکاتورهای چارت اصلی', en: "Main chart's indicators" },
    { id: 'hlines', fa: 'خط‌های افقی', en: 'Horizontal lines' },
  ];
  const toggle = (on: boolean, label: string, onClick: () => void, hint?: string, key?: string) => (
    <button className="menu-item toggle-row" role="menuitemcheckbox" aria-checked={on} onClick={onClick} title={hint}>
      <span className={fa ? 'fa' : ''}>{label}</span>
      {hint ? <span className="hint-i" aria-hidden><Icon name="ph-info" size="sm" /></span> : null}
      {key ? <kbd className="num">{key}</kbd> : null}
      <span className={`switch${on ? ' on' : ''}`} aria-hidden><i /></span>
    </button>
  );
  return (
    <Dropdown anchor={anchor} onClose={onClose} align="right" className="grid-menu tvm">
      <div dir={fa ? 'rtl' : 'ltr'}>
        {counts.map((n) => (
          <div key={n} className="grid-row">
            <span className="grid-n num">{fa ? faNum(n) : n}</span>
            <span className="grid-opts">
              {PRESETS.filter((p) => p.cells === n).map((p) => (
                <button key={p.id} className={`grid-opt${grid === p.id ? ' on' : ''}`} aria-pressed={grid === p.id} aria-label={p.id} data-grid={p.id}
                  onClick={() => setGrid(p.id)}>
                  <GridGlyph id={p.id} />
                </button>
              ))}
            </span>
          </div>
        ))}
        <div className={`menu-title${fa ? ' fa' : ''}`}>{L('همگام در چیدمان', 'Sync in layout')}</div>
        {syncRows.map((r) => toggle(sync[r.id], fa ? r.fa : r.en, () => setSync({ [r.id]: !sync[r.id] }), fa ? r.hintFa : r.hintEn))}
        <div className={`menu-title${fa ? ' fa' : ''}`}>{L('از چارت اصلی', 'From the main chart')}</div>
        {mirrorRows.map((r) => toggle(mirror[r.id], fa ? r.fa : r.en, () => setMirror({ [r.id]: !mirror[r.id] })))}
        <div className="menu-sep" />
        {toggle(maximized, L('بزرگ کردن چارت فعال', 'Maximize the active chart'), () => setMaximized(!maximized), undefined, 'Alt+Enter')}
      </div>
    </Dropdown>
  );
}
