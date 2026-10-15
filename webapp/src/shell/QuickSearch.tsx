// TradingView's Quick Search (Ctrl+K, the magnifier): one field over everything the app can do —
// actions, settings switches, intervals and chart types, indicators, drawing tools, symbols and
// pages. Enter runs the highlighted one; a switch flips in place.
import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { Icon } from '../ui/Icon';
import { SymbolLogo, hasLogo } from '../ui/SymbolLogo';
import { useL } from '../pages/kit';
import { usePrefs } from '../state/prefs';
import { useMarket } from '../state/market';
import { useChartSettings, type ChartSettings } from '../state/chartSettings';
import { useDrawing } from '../state/drawing';
import { useStudies } from '../state/studies';
import { useLayouts } from '../state/layouts';
import { usePanels, type PanelId } from '../state/panels';
import { usePage } from '../state/page';
import { useChartBus } from '../chart/chartBus';
import { useGrid, applyChartType, applyInterval, applySymbol, PRESETS, type Sync } from '../state/grid';
import { CATALOG } from '../chart/indicatorCatalog';
import { TOOL_GROUPS } from '../chart/tools';
import { CHART_TYPES } from '../chart/chartTypes';
import { INTERVALS, intervalLongLabel } from '../data/intervals';
import { PAGES } from '../pages/registry';
import { copyChartLink, copySnapshot, downloadSnapshot } from '../chart/snapshot';
import { exportBarsCsv } from '../chart/actions';
import { highlightPositions } from '../engine/fuzzy.js';
import { toast } from './toast';
import { faNum } from '../i18n/time';
import { SESSIONS } from './SessionShading';
import { VOLUME } from '../state/studies';

type Section = 'recent' | 'actions' | 'settings' | 'intervals' | 'types' | 'indicators' | 'drawings' | 'symbols' | 'pages' | 'layouts';
interface Cmd {
  id: string;
  section: Section;
  fa: string;
  en: string;
  /** Other words it answers to (an indicator's abbreviation, a symbol's names). */
  alt?: string;
  icon?: string;
  logo?: string;
  key?: string;
  /** A switch: its state now. */
  on?: () => boolean;
  run(): void;
}

const SECTIONS: { id: Section; fa: string; en: string }[] = [
  { id: 'recent', fa: 'اخیر', en: 'Recent' },
  { id: 'actions', fa: 'کارها', en: 'Actions' },
  { id: 'settings', fa: 'تنظیمات', en: 'Settings' },
  { id: 'intervals', fa: 'بازه‌های زمانی', en: 'Intervals' },
  { id: 'types', fa: 'نوع چارت', en: 'Chart types' },
  { id: 'layouts', fa: 'چیدمان چند چارت', en: 'Multi-chart layouts' },
  { id: 'indicators', fa: 'اندیکاتورها', en: 'Indicators' },
  { id: 'drawings', fa: 'ابزارهای ترسیم', en: 'Drawing tools' },
  { id: 'symbols', fa: 'نمادها', en: 'Symbols' },
  { id: 'pages', fa: 'صفحه‌ها', en: 'Pages' },
];
const RECENT_KEY = 'pc.quicksearch.recent.v1';
const readRecent = (): string[] => { try { const v = JSON.parse(localStorage.getItem(RECENT_KEY) ?? '[]') as unknown; return Array.isArray(v) ? (v as string[]).slice(0, 8) : []; } catch { return []; } };

export interface QuickActions { indicators(): void; compare(): void; alert(): void; replay(): void; settings(): void; symbolSearch(): void }

const fullscreen = () => { if (document.fullscreenElement) void document.exitFullscreen(); else void document.documentElement.requestFullscreen?.(); };
const settingsTab = (tab: string) => window.dispatchEvent(new CustomEvent('pc:settings', { detail: { tab } }));

function score(c: Cmd, q: string): number {
  const hay = [c.en, c.fa, c.alt ?? ''];
  let best = -1;
  for (const h of hay) {
    const H = h.toLowerCase();
    if (!H) continue;
    if (H === q) best = Math.max(best, 1000);
    else if (H.startsWith(q)) best = Math.max(best, 800 - H.length);
    else if (H.split(/[\s/()·-]+/).some((w) => w.startsWith(q))) best = Math.max(best, 650 - H.length);
    else if (H.includes(q)) best = Math.max(best, 500 - H.indexOf(q));
    else if (q.length >= 4) {
      // Scattered letters only when they sit close together (TradingView ignores loose matches).
      const pos = highlightPositions(H, q);
      if (pos.length === q.length && pos[pos.length - 1]! - pos[0]! < q.length * 2) best = Math.max(best, 200);
    }
  }
  if (best < 0) return best;
  // Actions and settings before catalogue entries of equal match, as TradingView ranks them.
  const weight: Record<Section, number> = { recent: 0, actions: 40, settings: 35, intervals: 20, types: 20, layouts: 10, indicators: 15, drawings: 15, symbols: 10, pages: 5 };
  return best + weight[c.section];
}

export function QuickSearch({ actions, onClose }: { actions: QuickActions; onClose(): void }) {
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const fa = lang === 'fa';
  const instruments = useMarket((s) => s.instruments);
  const [q, setQ] = useState('');
  const [cursor, setCursor] = useState(0);
  const [, force] = useState(0);
  const input = useRef<HTMLInputElement>(null);
  const list = useRef<HTMLDivElement>(null);
  useEffect(() => { input.current?.focus(); }, []);

  const cmds = useMemo<Cmd[]>(() => {
    const out: Cmd[] = [];
    const ALT: Record<string, string> = {
      download: 'snapshot screenshot picture عکس اسکرین‌شات', copyimg: 'snapshot screenshot clipboard عکس', copylink: 'snapshot share link اشتراک',
      csv: 'export data csv داده', reset: 'reset scale fit بازنشانی', alert: 'alarm آلارم', indicators: 'study studies اندیکاتور',
      replay: 'bar replay بازپخش', theme: 'dark light night تم تاریک روشن', maximize: 'maximise fullscreen chart', compare: 'overlay add symbol',
      save: 'layout چیدمان', open: 'load layout چیدمان', new: 'layout چیدمان', shortcuts: 'hotkeys keys کلید',
    };
    const a = (id: string, faL: string, en: string, icon: string, run: () => void, key?: string) => out.push({ id: `a:${id}`, section: 'actions', fa: faL, en, alt: ALT[id], icon, run, key });
    a('save', 'ذخیره‌ی چیدمان', 'Save layout', 'ph-floppy-disk', () => { useLayouts.getState().save(); }, 'Ctrl+S');
    a('open', 'باز کردن چیدمان…', 'Open layout…', 'ph-folder-open', () => window.dispatchEvent(new CustomEvent('pc:layout-dialog', { detail: { mode: 'open' } })));
    a('new', 'چیدمان تازه', 'Create new layout', 'ph-plus', () => useLayouts.getState().newLayout());
    a('saveas', 'ذخیره با نام تازه…', 'Save layout as…', 'ph-file-plus', () => window.dispatchEvent(new CustomEvent('pc:layout-dialog', { detail: { mode: 'saveAs' } })));
    a('download', 'دانلود تصویر چارت', 'Download chart image', 'ph-download-simple', () => { void downloadSnapshot(); }, 'Alt+S');
    a('copyimg', 'کپی تصویر چارت', 'Copy chart image', 'ph-copy', () => { void copySnapshot().then((ok) => toast(ok ? L('تصویر کپی شد', 'Image copied') : L('مرورگر اجازه نداد', 'The browser did not allow it'))); });
    a('copylink', 'کپی لینک چارت', 'Copy chart link', 'ph-link', () => { void copyChartLink().then((u) => toast(u ? L('لینک چارت کپی شد', 'Chart link copied') : L('کپی ممکن نشد', 'Could not copy'))); });
    a('csv', 'دانلود داده‌ی چارت (CSV)', 'Export chart data (CSV)', 'ph-download-simple', () => exportBarsCsv());
    a('fullscreen', 'تمام‌صفحه', 'Fullscreen mode', 'maximize2', fullscreen, 'Ctrl+Alt+F');
    a('undo', 'واگرد', 'Undo', 'ph-arrow-counter-clockwise', () => { const l = useChartBus.getState().drawingLayer; if (l) { l.undo(); useChartBus.getState().bumpDrawings(l.canUndo(), l.canRedo(), l.getDrawings().length); } }, 'Ctrl+Z');
    a('redo', 'ازنو', 'Redo', 'ph-arrow-clockwise', () => { const l = useChartBus.getState().drawingLayer; if (l) { l.redo(); useChartBus.getState().bumpDrawings(l.canUndo(), l.canRedo(), l.getDrawings().length); } }, 'Ctrl+Y');
    a('goto', 'رفتن به تاریخ…', 'Go to date…', 'ph-calendar-blank', () => window.dispatchEvent(new CustomEvent('pc:goto')), 'Alt+G');
    a('reset', 'بازنشانی نمای چارت', 'Reset chart view', 'ph-arrows-clockwise', () => { const api = useChartBus.getState().api; if (api) { api.priceScale('right').applyOptions({ autoScale: true }); api.timeScale().resetTimeScale(); api.timeScale().scrollToRealTime(); } });
    a('alert', 'هشدار تازه', 'Add alert', 'ph-bell', () => actions.alert(), 'Alt+A');
    a('indicators', 'اندیکاتورها…', 'Indicators, metrics and strategies…', 'ph-chart-line-up', () => actions.indicators(), '/');
    a('compare', 'مقایسه یا افزودن نماد…', 'Compare or add symbol…', 'ph-plus', () => actions.compare());
    a('symbol', 'جستجوی نماد…', 'Symbol search…', 'search', () => actions.symbolSearch());
    a('replay', 'بازپخش', 'Bar replay', 'ph-rewind', () => actions.replay());
    a('settings', 'تنظیمات چارت…', 'Chart settings…', 'settings2', () => actions.settings(), 'Ctrl+,');
    for (const [tab, f, e] of [['symbol', 'نماد', 'Symbol'], ['status', 'خط وضعیت', 'Status line'], ['scales', 'مقیاس‌ها', 'Scales'], ['canvas', 'بوم', 'Canvas'], ['trading', 'معامله', 'Trading'], ['alerts', 'هشدارها', 'Alerts'], ['events', 'رویدادها', 'Events']] as const) {
      a(`settings-${tab}`, `تنظیمات چارت: ${f}`, `Chart settings: ${e}`, 'settings2', () => settingsTab(tab));
    }
    a('theme', 'تم تیره / روشن', 'Dark / light theme', 'ph-moon', () => { const p = usePrefs.getState(); p.setTheme(p.theme === 'dark' ? 'light' : 'dark'); }, 'Ctrl+Alt+T');
    a('lang', 'زبان: English', 'Language: فارسی', 'ph-translate', () => { const p = usePrefs.getState(); p.setLang(p.lang === 'fa' ? 'en' : 'fa'); });
    a('maximize', 'بزرگ کردن چارت فعال', 'Maximize the active chart', 'ph-arrows-out', () => { const g = useGrid.getState(); g.setMaximized(!g.maximized); }, 'Alt+Enter');
    a('shortcuts', 'میانبرهای صفحه‌کلید', 'Keyboard shortcuts', 'ph-keyboard', () => usePage.getState().open('help', 'shortcuts'), '?');
    a('help', 'مرکز راهنما', 'Help center', 'ph-question', () => usePage.getState().open('help'));
    for (const [id, f, e, icon] of [['watchlist', 'دیده‌بان', 'Watchlist', 'ph-list'], ['alerts', 'فهرست هشدارها', 'Alerts', 'ph-bell'], ['objects', 'درخت اشیا', 'Object tree', 'ph-rows'], ['data', 'پنجره‌ی داده', 'Data window', 'ph-columns'], ['news', 'اخبار', 'News', 'ph-newspaper'], ['calendar', 'تقویم اقتصادی', 'Economic calendar', 'ph-calendar-blank'], ['screener', 'اسکرینر', 'Screener', 'ph-funnel']] as const) {
      a(`panel-${id}`, `پنل ${f}`, `${e} panel`, icon, () => { const p = usePanels.getState(); if (p.open !== id) p.toggle(id as PanelId); });
    }
    const scale = (mode: 'percent' | 'log') => { const b = useChartBus.getState(); b.setScale(b.scale === mode ? 'normal' : mode); };
    const s = (id: string, faL: string, en: string, on: () => boolean, run: () => void, key?: string) => out.push({ id: `s:${id}`, section: 'settings', fa: faL, en, icon: 'ph-sliders-horizontal', on, run, key });
    const cs = (k: keyof ChartSettings, faL: string, en: string) => s(k, faL, en, () => Boolean(useChartSettings.getState()[k]), () => useChartSettings.getState().set({ [k]: !useChartSettings.getState()[k] } as Partial<ChartSettings>));
    // The two lines are a label-and-line choice in Chart Settings; here they switch the line on and off.
    s('lastPriceLine', 'خط قیمت آخر', 'Last price line', () => useChartSettings.getState().lastPrice === 'value-line',
      () => useChartSettings.getState().set({ lastPrice: useChartSettings.getState().lastPrice === 'value-line' ? 'value' : 'value-line' }));
    s('prevDayCloseLine', 'خط بسته‌شدن روز قبل', 'Previous close line', () => useChartSettings.getState().prevDayClose !== 'hidden',
      () => useChartSettings.getState().set({ prevDayClose: useChartSettings.getState().prevDayClose === 'hidden' ? 'value-line' : 'hidden' }));
    cs('countdown', 'شمارش معکوس بسته‌شدن کندل', 'Countdown to bar close');
    cs('showOhlc', 'مقادیر OHLC در لجند', 'OHLC values in the legend');
    cs('showChange', 'تغییر کندل در لجند', 'Bar change values');
    cs('watermark', 'واترمارک نماد', 'Symbol watermark');
    cs('events', 'رویدادهای اقتصادی روی چارت', 'Economic events on the chart');
    cs('sessionBreaks', 'جداکننده‌ی جلسه‌ها', 'Session breaks');
    cs('autoDivergence', 'واگرایی‌های خودکار RSI', 'Auto RSI divergences');
    cs('autoHarmonic', 'الگوهای هارمونیک خودکار', 'Auto harmonic patterns');
    cs('autoGaps', 'گپ‌های قیمتی خودکار', 'Auto price gaps');
    s('magnet', 'آهنربای ترسیم', 'Magnet mode', () => useDrawing.getState().magnet, () => useDrawing.getState().setMagnet(!useDrawing.getState().magnet), 'Ctrl+Alt+M');
    s('hide', 'پنهان کردن ترسیم‌ها', 'Hide all drawings', () => useDrawing.getState().hidden, () => useDrawing.getState().setHidden(!useDrawing.getState().hidden), 'Ctrl+Alt+H');
    s('lock', 'قفل همه‌ی ترسیم‌ها', 'Lock all drawings', () => useDrawing.getState().locked, () => useDrawing.getState().setLocked(!useDrawing.getState().locked));
    s('percent', 'مقیاس درصدی', 'Percent scale', () => useChartBus.getState().scale === 'percent', () => scale('percent'), 'Alt+P');
    s('log', 'مقیاس لگاریتمی', 'Log scale', () => useChartBus.getState().scale === 'log', () => scale('log'), 'Alt+L');
    s('invert', 'وارونه کردن مقیاس', 'Invert scale', () => useChartBus.getState().invert, () => useChartBus.getState().setInvert(!useChartBus.getState().invert), 'Alt+I');
    s('autoscale', 'مقیاس خودکار', 'Auto scale', () => useChartBus.getState().autoScale, () => useChartBus.getState().setAutoScale(!useChartBus.getState().autoScale));
    s('autosave', 'ذخیره‌ی خودکار چیدمان', 'Autosave layout', () => useLayouts.getState().autosave, () => useLayouts.getState().setAutosave(!useLayouts.getState().autosave));
    s('volume', 'حجم زیر قیمت', 'Volume', () => useStudies.getState().list.some((x) => x.key === VOLUME && !x.hidden), () => {
      const st = useStudies.getState();
      const v = st.list.find((x) => x.key === VOLUME);
      if (!v) useStudies.setState({ list: [{ uid: 'vol', key: VOLUME, inputs: {} }, ...st.list] });
      else st.toggle(v.uid);
    });
    for (const ss of SESSIONS) {
      s(`shade-${ss.id}`, `سایه‌ی جلسه‌ی ${ss.fa}`, `${ss.en} session shading`, () => usePrefs.getState().shadedSessions.includes(ss.id), () => {
        const p = usePrefs.getState();
        p.setShadedSessions(p.shadedSessions.includes(ss.id) ? p.shadedSessions.filter((x) => x !== ss.id) : [...p.shadedSessions, ss.id]);
      });
    }
    for (const [k, f, e] of [['symbol', 'نماد', 'symbol'], ['interval', 'بازه', 'interval'], ['crosshair', 'نشانگر', 'crosshair'], ['time', 'زمان', 'time'], ['dateRange', 'بازه‌ی تاریخ', 'date range']] as const) {
      s(`sync-${k}`, `همگام‌سازی ${f} در چیدمان`, `Sync ${e} in layout`, () => useGrid.getState().sync[k as keyof Sync], () => useGrid.getState().setSync({ [k]: !useGrid.getState().sync[k as keyof Sync] }));
    }
    for (const iv of INTERVALS) out.push({ id: `i:${iv.id}`, section: 'intervals', fa: intervalLongLabel(iv.id, 'fa'), en: intervalLongLabel(iv.id, 'en'), alt: iv.label, icon: 'ph-clock', run: () => applyInterval(iv.id) });
    for (const c of CHART_TYPES) out.push({ id: `t:${c.id}`, section: 'types', fa: c.fa, en: c.en, icon: c.icon, run: () => applyChartType(c.id) });
    for (const n of [1, 2, 3, 4, 6, 8]) {
      const p = PRESETS.find((x) => x.cells === n)!;
      out.push({ id: `g:${p.id}`, section: 'layouts', fa: n === 1 ? 'یک چارت' : `${faNum(n)} چارت`, en: n === 1 ? '1 chart' : `${n} charts`, icon: 'ph-squares-four', run: () => useGrid.getState().setGrid(p.id) });
    }
    for (const e of CATALOG) out.push({ id: `ind:${e.key}`, section: 'indicators', fa: e.fa, en: e.en, alt: e.abbr, icon: 'ph-chart-line-up', run: () => useStudies.getState().add(e.key) });
    for (const g of TOOL_GROUPS) {
      for (const tl of g.tools) {
        if (g.id === 'cursors') continue;
        out.push({ id: `d:${tl.id}`, section: 'drawings', fa: tl.fa, en: tl.en, icon: `tool_${tl.id}`, key: tl.key, run: () => useDrawing.getState().setTool(tl.id, g.id) });
      }
    }
    for (const i of Object.values(instruments)) {
      if (!hasLogo(i.symbol)) continue;
      out.push({ id: `sym:${i.symbol}`, section: 'symbols', fa: i.nameFa ?? i.symbol, en: i.symbol, alt: `${i.nameEn ?? ''} ${i.symbol}`, logo: i.symbol, run: () => applySymbol(i.symbol) });
    }
    for (const p of PAGES) {
      if (!p.group) continue;
      out.push({ id: `p:${p.id}`, section: 'pages', fa: p.fa, en: p.en, icon: p.icon, run: () => usePage.getState().open(p.id) });
    }
    return out;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [instruments, lang]);

  const needle = q.trim().toLowerCase();
  const rows = useMemo(() => {
    if (!needle) {
      const byId = new Map(cmds.map((c) => [c.id, c]));
      return readRecent().map((id) => byId.get(id)).filter((c): c is Cmd => Boolean(c)).map((c) => ({ ...c, section: 'recent' as Section }));
    }
    const scored = cmds.map((c) => ({ c, s: score(c, needle) })).filter((x) => x.s >= 0);
    // At most a handful per section, best sections first.
    const per = new Map<Section, { c: Cmd; s: number }[]>();
    for (const x of scored.sort((a, b) => b.s - a.s)) {
      const list = per.get(x.c.section) ?? [];
      if (list.length < (x.c.section === 'actions' || x.c.section === 'settings' ? 8 : 6)) list.push(x);
      per.set(x.c.section, list);
    }
    const order = [...per.entries()].sort((a, b) => b[1][0]!.s - a[1][0]!.s);
    return order.flatMap(([, list]) => list.map((x) => x.c));
  }, [cmds, needle]);

  useEffect(() => { setCursor(0); }, [needle]);
  useEffect(() => { list.current?.querySelector('.qs-row.on')?.scrollIntoView({ block: 'nearest' }); }, [cursor]);

  const run = (c: Cmd) => {
    try { localStorage.setItem(RECENT_KEY, JSON.stringify([c.id, ...readRecent().filter((x) => x !== c.id)].slice(0, 8))); } catch { /* storage blocked */ }
    if (c.on) { c.run(); force((n) => n + 1); return; }
    onClose();
    c.run();
  };

  const mark = (text: string): ReactNode => {
    const pos = new Set(needle ? highlightPositions(text, needle) : []);
    if (!pos.size) return text;
    return [...text].map((ch, i) => (pos.has(i) ? <b key={i} className="hl">{ch}</b> : ch));
  };

  let lastSection: Section | null = null;
  return createPortal(
    <div className="scrim" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div className="dialog quick" role="dialog" aria-modal="true" aria-label={L('جستجوی ابزار یا کار', 'Search tool or function')} dir={fa ? 'rtl' : 'ltr'}
        onKeyDown={(e) => {
          if (e.key === 'Escape') { e.stopPropagation(); onClose(); }
          else if (e.key === 'ArrowDown') { e.preventDefault(); setCursor((c) => Math.min(rows.length - 1, c + 1)); }
          else if (e.key === 'ArrowUp') { e.preventDefault(); setCursor((c) => Math.max(0, c - 1)); }
          else if (e.key === 'Enter' && rows[cursor]) { e.preventDefault(); run(rows[cursor]!); }
        }}>
        <header className="dialog-head">
          <h2 className={fa ? 'fa' : ''}>{L('جستجوی ابزار یا کار', 'Search tool or function')}</h2>
          <button className="ib" onClick={onClose} aria-label="close"><Icon name="ph-x" size="md" /></button>
        </header>
        <label className="ss-field">
          <Icon name="search" size="md" />
          <input ref={input} value={q} onChange={(e) => setQ(e.target.value)} spellCheck={false} autoComplete="off" dir="auto"
            placeholder={L('مثلاً RSI، عکس، خط روند، طلا', 'e.g. RSI, snapshot, trend line, gold')} aria-label={L('جستجو', 'Search')} />
          {q ? <button className="ss-clear" onClick={() => { setQ(''); input.current?.focus(); }} aria-label="clear"><Icon name="ph-x" size="sm" /></button> : null}
        </label>
        <div className="qs-list" ref={list} role="listbox">
          {!rows.length ? (
            <div className={`qs-empty${fa ? ' fa' : ''}`}>{needle ? L('چیزی پیدا نشد', 'Nothing found') : L('برای پیدا کردن ترسیم‌ها، کارها و تنظیمات بنویسید', 'Type to search for drawings, functions and settings')}</div>
          ) : rows.map((c, i) => {
            const head = c.section !== lastSection ? SECTIONS.find((x) => x.id === c.section)! : null;
            lastSection = c.section;
            const label = fa ? c.fa : c.en;
            return (
              <div key={`${c.section}-${c.id}`}>
                {head ? <div className={`qs-section${fa ? ' fa' : ''}`}>{fa ? head.fa : head.en}</div> : null}
                <button role="option" aria-selected={i === cursor} className={`qs-row${i === cursor ? ' on' : ''}`} onMouseEnter={() => setCursor(i)} onClick={() => run(c)}>
                  {c.logo ? <SymbolLogo symbol={c.logo} size={20} /> : <Icon name={c.icon ?? 'ph-sparkle'} size="sm" />}
                  <span className={`qs-label${fa ? ' fa' : ''}`}>{mark(label)}</span>
                  {c.section === 'symbols' ? <span className="num muted">{c.en}</span> : null}
                  {c.key ? <kbd className="num">{c.key}</kbd> : null}
                  {c.on ? <span className={`switch${c.on() ? ' on' : ''}`} aria-hidden><i /></span> : null}
                </button>
              </div>
            );
          })}
        </div>
      </div>
    </div>,
    document.body,
  );
}
