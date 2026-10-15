import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { usePrefs } from '../state/prefs';
import { useStudies, VOLUME } from '../state/studies';
import { useScript } from '../state/script';
import { usePanels } from '../state/panels';
import { usePage } from '../state/page';
import { useChartSettings } from '../state/chartSettings';
import { CATALOG, CATEGORIES, CURATED, fold, rank, type CatalogEntry, type Category, type DialogTab } from '../chart/indicatorCatalog';
import { EXAMPLES } from '../engine/scriptlib.js';
import { helpKey, loadHelp, type HelpEntry } from '../pages/helpLib';
import { faNum } from '../i18n/time';
import { useL } from '../pages/kit';
import { Icon } from '../ui/Icon';
import './studies.css';

// TradingView's "Indicators, metrics & strategies": search on top; Personal (favourites, recently
// used, my scripts), Built-in (technicals and the categories) and Community (editors' picks, top,
// trending) on the side; the Indicators / Strategies / Profiles / Patterns tabs over the list; one
// row per study with its star, badge, where it draws and its help. A click adds the study and keeps
// the dialog open, as TradingView does. ↑↓, PageUp/PageDown, Home/End and Enter work the list.
type Side = 'favourites' | 'recent' | 'scripts' | 'technicals' | Category | 'editors' | 'top' | 'trending';

type Row =
  | { kind: 'study'; id: string; e: CatalogEntry }
  | { kind: 'strategy'; id: string; name: string; code: string }
  | { kind: 'script'; id: string; name: string }
  | { kind: 'toggle'; id: 'autoDivergence' | 'autoHarmonic' | 'autoGaps' | 'volumeOverlay'; fa: string; en: string; help: string };

const TOGGLES: Extract<Row, { kind: 'toggle' }>[] = [
  { kind: 'toggle', id: 'autoHarmonic', fa: 'الگوهای هارمونیک خودکار', en: 'Auto harmonic patterns', help: 'harmonics' },
  { kind: 'toggle', id: 'autoDivergence', fa: 'واگرایی‌های خودکار RSI', en: 'Auto RSI divergences', help: 'divergence' },
  { kind: 'toggle', id: 'autoGaps', fa: 'گپ‌های قیمتی', en: 'Price gaps', help: 'gaps' },
];
/** The volume histogram under the price (the chart's own, not the separate-pane study): once taken off, it comes back from here. */
const VOLUME_ROW: Extract<Row, { kind: 'toggle' }> = { kind: 'toggle', id: 'volumeOverlay', fa: 'حجم (روی چارت)', en: 'Volume (on the chart)', help: 'volume' };
const STRATEGIES = EXAMPLES.map((x, i) => ({ ...x, i })).filter((x) => x.kind === 'strategy');

/** The matched part of a name, highlighted as TradingView marks it. */
function Marked({ text, q }: { text: string; q: string }): ReactNode {
  const f = fold(q);
  if (!f) return text;
  const i = text.toLowerCase().indexOf(f);
  if (i < 0) return text;
  return <>{text.slice(0, i)}<mark className="ind-hl">{text.slice(i, i + f.length)}</mark>{text.slice(i + f.length)}</>;
}

export function IndicatorsDialog({ onClose }: { onClose(): void }) {
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const fa = lang === 'fa';
  const add = useStudies((s) => s.add);
  const favourites = useStudies((s) => s.favourites);
  const recent = useStudies((s) => s.recent);
  const toggleFavourite = useStudies((s) => s.toggleFavourite);
  const lib = useScript((s) => s.lib);
  const chart = useChartSettings();
  const volumeOn = useStudies((s) => s.list.some((x) => x.key === VOLUME && !x.hidden));
  const isOn = (id: Extract<Row, { kind: 'toggle' }>['id']) => (id === 'volumeOverlay' ? volumeOn : Boolean(chart[id]));
  const [q, setQ] = useState('');
  const [side, setSide] = useState<Side>('technicals');
  const [tab, setTab] = useState<DialogTab>('indicators');
  const [cursor, setCursor] = useState(0);
  const [added, setAdded] = useState<string | null>(null);
  const [help, setHelp] = useState<Record<string, HelpEntry> | null>(null);
  const input = useRef<HTMLInputElement>(null);
  const list = useRef<HTMLDivElement>(null);
  useEffect(() => { input.current?.focus(); }, []);
  useEffect(() => { let alive = true; loadHelp().then((h) => { if (alive) setHelp(h); }).catch(() => undefined); return () => { alive = false; }; }, []);

  const studyRows = (keys: string[]) => keys.map((k) => CATALOG.find((c) => c.key === k)).filter((e): e is CatalogEntry => Boolean(e)).map((e): Row => ({ kind: 'study', id: e.key, e }));
  const inTab = (rows: Row[]): Row[] => {
    if (tab === 'strategies') return STRATEGIES.map((s) => ({ kind: 'strategy', id: `ex${s.i}`, name: s.name, code: s.code }));
    const studies = rows.filter((r) => r.kind === 'study' && r.e.tab === tab);
    if (tab === 'indicators' && (side === 'technicals' || side === 'volume')) return [VOLUME_ROW, ...studies];
    return tab === 'patterns' ? [...studies, ...TOGGLES] : studies;
  };

  // Search runs through everything; without it, the side picks the list and the tab filters it.
  const { rows, groups } = useMemo(() => {
    if (q.trim()) {
      const hits = CATALOG.map((e) => ({ e, r: rank(e, q) })).filter((x) => x.r != null).sort((a, b) => a.r! - b.r! || a.e.en.length - b.e.en.length).map(({ e }): Row => ({ kind: 'study', id: e.key, e }));
      const needle = fold(q);
      const strat = STRATEGIES.filter((s) => fold(s.name).includes(needle)).map((s): Row => ({ kind: 'strategy', id: `ex${s.i}`, name: s.name, code: s.code }));
      const mine = lib.filter((s) => fold(s.name).includes(needle)).map((s): Row => ({ kind: 'script', id: s.id, name: s.name }));
      const togg = [VOLUME_ROW, ...TOGGLES].filter((t) => fold(t.en).includes(needle) || fold(t.fa).includes(needle));
      const g: { title: string; rows: Row[] }[] = [];
      if (hits.length || togg.length) g.push({ title: L('تکنیکال', 'Technicals'), rows: [...hits, ...togg] });
      if (strat.length) g.push({ title: L('استراتژی‌ها', 'Strategies'), rows: strat });
      if (mine.length) g.push({ title: L('اسکریپت‌های من', 'My scripts'), rows: mine });
      return { rows: g.flatMap((x) => x.rows), groups: g };
    }
    let r: Row[];
    if (side === 'scripts') r = lib.map((s) => ({ kind: 'script', id: s.id, name: s.name }));
    else if (side === 'favourites') r = inTab(studyRows(favourites));
    else if (side === 'recent') r = inTab(studyRows(recent));
    else if (side === 'technicals') r = inTab(CATALOG.map((e) => ({ kind: 'study', id: e.key, e })));
    else if (side === 'editors' || side === 'top' || side === 'trending') r = inTab(studyRows(CURATED[side]));
    else r = inTab(CATALOG.filter((c) => c.category === side).map((e) => ({ kind: 'study', id: e.key, e })));
    return { rows: r, groups: [{ title: '', rows: r }] };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [q, side, tab, favourites, recent, lib, lang, volumeOn]);

  useEffect(() => { setCursor(0); list.current?.scrollTo({ top: 0 }); }, [q, side, tab]);
  // The highlighted row stays in view as the keys move it.
  useEffect(() => { list.current?.querySelector('.ind-row.on')?.scrollIntoView({ block: 'nearest' }); }, [cursor]);

  const flash = (id: string) => { setAdded(id); setTimeout(() => setAdded((k) => (k === id ? null : k)), 900); };
  const pick = (r: Row) => {
    if (r.kind === 'study') { add(r.id); flash(r.id); return; }
    if (r.kind === 'toggle') {
      if (r.id === 'volumeOverlay') useStudies.getState().setVolume(!volumeOn);
      else chart.set({ [r.id]: !chart[r.id] });
      flash(r.id);
      return;
    }
    const s = useScript.getState();
    if (r.kind === 'strategy') s.newTab(r.code, r.name.replace(/^[^\p{L}\p{N}]+/u, '').trim());
    else s.open(r.id);
    useScript.getState().apply();
    const want = r.kind === 'strategy' ? 'backtest' : 'namascript';
    if (usePanels.getState().bottom !== want) usePanels.getState().setBottom(want);
    flash(r.id);
  };
  const openHelp = (id: string) => { onClose(); usePage.getState().open('help', id); };

  const onKey = (e: React.KeyboardEvent) => {
    const last = rows.length - 1;
    if (e.key === 'Escape') onClose();
    else if (e.key === 'ArrowDown') { e.preventDefault(); setCursor((c) => Math.min(last, c + 1)); }
    else if (e.key === 'ArrowUp') { e.preventDefault(); setCursor((c) => Math.max(0, c - 1)); }
    else if (e.key === 'PageDown') { e.preventDefault(); setCursor((c) => Math.min(last, c + 10)); }
    else if (e.key === 'PageUp') { e.preventDefault(); setCursor((c) => Math.max(0, c - 10)); }
    else if (e.key === 'Home' && (e.ctrlKey || !q)) { e.preventDefault(); setCursor(0); }
    else if (e.key === 'End' && (e.ctrlKey || !q)) { e.preventDefault(); setCursor(Math.max(0, last)); }
    else if (e.key === 'Enter' && rows[cursor]) pick(rows[cursor]!);
  };

  const count = (n: number) => (fa ? faNum(n) : String(n));
  const sideItems: { group: string; items: { id: Side; fa: string; en: string; icon: string; n: number }[] }[] = [
    { group: L('شخصی', 'Personal'), items: [
      { id: 'favourites', fa: 'منتخب‌ها', en: 'Favorites', icon: 'ph-star', n: favourites.length },
      { id: 'recent', fa: 'اخیراً استفاده‌شده', en: 'Recently used', icon: 'ph-clock', n: recent.length },
      { id: 'scripts', fa: 'اسکریپت‌های من', en: 'My scripts', icon: 'ph-code', n: lib.length },
    ] },
    { group: L('داخلی', 'Built-in'), items: [
      { id: 'technicals', fa: 'تکنیکال', en: 'Technicals', icon: 'ph-chart-line-up', n: CATALOG.length },
      ...CATEGORIES.map((c) => ({ id: c.id as Side, fa: c.fa, en: c.en, icon: c.icon, n: CATALOG.filter((x) => x.category === c.id).length })),
    ] },
    { group: L('جامعه', 'Community'), items: [
      { id: 'editors', fa: 'انتخاب سردبیر', en: "Editors' picks", icon: 'ph-crown', n: CURATED.editors.length },
      { id: 'top', fa: 'برترین‌ها', en: 'Top', icon: 'ph-trend-up', n: CURATED.top.length },
      { id: 'trending', fa: 'پرطرفدار', en: 'Trending', icon: 'ph-lightning', n: CURATED.trending.length },
    ] },
  ];
  const tabs: { id: DialogTab; fa: string; en: string }[] = [
    { id: 'indicators', fa: 'اندیکاتورها', en: 'Indicators' }, { id: 'strategies', fa: 'استراتژی‌ها', en: 'Strategies' },
    { id: 'profiles', fa: 'پروفایل‌ها', en: 'Profiles' }, { id: 'patterns', fa: 'الگوها', en: 'Patterns' },
  ];

  const current = rows[cursor];
  const desc = (() => {
    if (!current) return null;
    const pick2 = (b?: { fa?: string; en?: string }) => (b ? (fa ? b.fa || b.en : b.en || b.fa) ?? '' : '');
    if (current.kind === 'study') { const k = help ? helpKey(help, current.id) : null; return { text: k ? pick2(help![k]!.useCase) : '', where: current.e.overlay ? L('روی چارت', 'On the chart') : L('در پنجره‌ی جدا', 'In a new pane') }; }
    if (current.kind === 'toggle') { const k = help ? helpKey(help, current.help) : null; return { text: k ? pick2(help![k]!.useCase) : '', where: L('روی چارت', 'On the chart') }; }
    if (current.kind === 'strategy') return { text: L('نمونه‌ی استراتژی نمااسکریپت؛ روی چارت اجرا و در آزمون استراتژی سنجیده می‌شود.', 'A NamaScript strategy example: it runs on the chart and the Strategy Tester measures it.'), where: L('آزمون استراتژی', 'Strategy Tester') };
    return { text: L('اسکریپت ذخیره‌شده‌ی شما؛ در ویرایشگر باز و روی چارت اجرا می‌شود.', 'Your saved script: it opens in the editor and runs on the chart.'), where: L('نمااسکریپت', 'NamaScript') };
  })();
  const helpOf = (r: Row) => (r.kind === 'study' ? r.id : r.kind === 'toggle' ? r.help : r.kind === 'strategy' ? 'strategyTester' : 'namascript');

  let index = -1;
  const rowView = (r: Row) => {
    index++;
    const i = index;
    const on = i === cursor;
    const fav = r.kind === 'study' && favourites.includes(r.id);
    const name = r.kind === 'study' ? r.e.en : r.kind === 'toggle' ? (fa ? r.fa : r.en) : r.name;
    const sub = r.kind === 'study' ? (fa ? r.e.fa : r.e.short && r.e.short !== r.e.en ? r.e.short : '') : '';
    const toggled = r.kind === 'toggle' && isOn(r.id);
    return (
      <div key={`${r.kind}:${r.id}`} role="option" aria-selected={on} className={`ind-row${on ? ' on' : ''}`} onMouseEnter={() => setCursor(i)} onClick={() => pick(r)}>
        {r.kind === 'study' ? (
          <button className={`star${fav ? ' on' : ''}`} onClick={(e) => { e.stopPropagation(); toggleFavourite(r.id); }} aria-label={L('منتخب', 'Favourite')}>
            <Icon name={fav ? 'ph-star-fill' : 'ph-star'} size="sm" />
          </button>
        ) : <span className="ind-kind"><Icon name={r.kind === 'toggle' ? (toggled ? 'ph-check' : r.id === 'volumeOverlay' ? 'ph-chart-bar' : 'tool_hns') : r.kind === 'strategy' ? 'ph-flask' : 'ph-code'} size="sm" /></span>}
        <span className={`ind-name${r.kind !== 'study' && fa ? ' fa' : ''}`} dir="auto"><Marked text={name} q={q} /></span>
        {r.kind === 'study' && r.e.badge ? <span className={`ind-badge ${r.e.badge.toLowerCase()}`}>{r.e.badge}</span> : null}
        {sub ? <span className={`ind-fa${fa ? ' fa' : ' num'}`}><Marked text={sub} q={q} /></span> : <span className="ws-spacer" />}
        {added === r.id ? <span className="ind-added"><Icon name="ph-check" size="sm" /></span> : null}
        {r.kind === 'study' ? <span className="ind-where" title={r.e.overlay ? L('روی چارت', 'On the chart') : L('در پنجره‌ی جدا', 'In a new pane')}><Icon name={r.e.overlay ? 'chart_candles' : 'ph-rows'} size="sm" /></span> : null}
        <button className="ind-help" title={L('راهنما', 'Help')} aria-label={L('راهنما', 'Help')} onClick={(e) => { e.stopPropagation(); openHelp(helpOf(r)); }}><Icon name="ph-question" size="sm" /></button>
      </div>
    );
  };

  const total = rows.length;
  return (
    <div className="scrim" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div className="dialog indicators tvd" dir={fa ? 'rtl' : 'ltr'} role="dialog" aria-modal="true" aria-label={L('اندیکاتورها', 'Indicators')} onKeyDown={onKey}>
        <header className="dialog-head tvd-head">
          <h2 className="fa">{L('اندیکاتورها، معیارها و استراتژی‌ها', 'Indicators, metrics & strategies')}</h2>
          <button className="ib" onClick={onClose} aria-label={L('بستن', 'Close')}><Icon name="ph-x" size="md" /></button>
        </header>
        <label className="search-field tvd-search">
          <Icon name="search" size="md" />
          <input ref={input} value={q} onChange={(e) => setQ(e.target.value)} placeholder={L(`جستجو در ${faNum(CATALOG.length)} اندیکاتور`, `Search ${CATALOG.length} indicators`)} spellCheck={false} autoComplete="off" dir="auto" />
          {q ? <button className="lt" onClick={() => { setQ(''); input.current?.focus(); }} aria-label={L('پاک کردن', 'Clear')}><Icon name="ph-x" size="sm" /></button> : null}
        </label>
        <div className="ind-body tvd-body">
          <nav className="ind-cats tvd-side">
            {sideItems.map((g) => (
              <div key={g.group} className="tvd-group">
                <div className="tvd-gtitle fa">{g.group}</div>
                {g.items.map((s) => (
                  <button key={s.id} className={`ind-cat${side === s.id && !q ? ' on' : ''}`} onClick={() => { setSide(s.id); setQ(''); if (s.id !== 'scripts' && tab === 'strategies' && s.id !== 'technicals') setTab('indicators'); }}>
                    <Icon name={s.icon} size="sm" /><span className="fa">{fa ? s.fa : s.en}</span><span className="tvd-n num">{s.n ? count(s.n) : ''}</span>
                  </button>
                ))}
              </div>
            ))}
          </nav>
          <div className="tvd-main">
            {!q && side !== 'scripts' ? (
              <div className="tvd-tabs" role="tablist">
                {tabs.map((x) => <button key={x.id} role="tab" aria-selected={tab === x.id} className={`chip${tab === x.id ? ' on' : ''}`} onClick={() => setTab(x.id)}><span className="fa">{fa ? x.fa : x.en}</span></button>)}
              </div>
            ) : null}
            <div className="ind-list tvd-list" role="listbox" ref={list}>
              {total === 0 ? (
                <div className="empty fa">{q ? L('اندیکاتوری با این نام پیدا نشد.', 'No indicators match your search.') : side === 'scripts' ? L('هنوز اسکریپتی ذخیره نکرده‌اید؛ در نمااسکریپت بنویسید و ذخیره کنید.', 'No saved scripts yet: write one in NamaScript and save it.') : side === 'favourites' ? L('هنوز منتخبی ندارید؛ ستاره‌ی کنار هر اندیکاتور را بزنید.', 'No favourites yet: star an indicator to keep it here.') : side === 'recent' ? L('هنوز اندیکاتوری اضافه نکرده‌اید.', 'Nothing added yet.') : L('موردی نیست.', 'Nothing here.')}</div>
              ) : groups.map((g) => (
                <div key={g.title || 'all'}>
                  <div className="tvd-head-row"><span className="fa">{g.title || L('نام اسکریپت', 'Script name')}</span><span className="num">{count(g.rows.length)}</span></div>
                  {g.rows.map(rowView)}
                </div>
              ))}
            </div>
          </div>
        </div>
        <footer className="tvd-foot">
          <span className="tvd-desc fa" dir="auto">{desc ? <>{desc.where ? <b>{desc.where}</b> : null}{desc.text ? ` · ${desc.text}` : ''}</> : null}</span>
          <span className="tvd-keys fa"><kbd>↑</kbd><kbd>↓</kbd> {L('حرکت', 'move')} · <kbd>Enter</kbd> {L('افزودن', 'add')} · <kbd>Esc</kbd> {L('بستن', 'close')}</span>
        </footer>
      </div>
    </div>
  );
}
