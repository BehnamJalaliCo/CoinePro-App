// TradingView's status line over the chart: the symbol row (logo, ticker and description, interval,
// market, chart type, market status, OHLC coloured by the bar, bar change, volume, last day change),
// the Buy/Sell boxes, then one row per indicator, volume and compared symbol — each with its tools on
// hover (always on a touch screen) and a «⋯» menu — and the arrow that folds the rows away.
import { useEffect, useRef, useState } from 'react';
import type { Candle } from '../data/types';
import { formatPct, formatPrice, formatVolume } from './format';
import { usePrefs } from '../state/prefs';
import { intervalLabel } from '../data/intervals';
import { SymbolLogo } from '../ui/SymbolLogo';
import { Icon } from '../ui/Icon';
import { useL } from '../pages/kit';
import { useStudies, VOLUME, type Study } from '../state/studies';
import { useChartSettings } from '../state/chartSettings';
import { useCompare } from '../state/compare';
import type { CompareRow } from './chartBus';
import { useChartBus } from './chartBus';
import type { StudyLegendRow } from './studies';
import { shortName } from './studies';
import { REGISTRY } from '../engine/indicators.js';
import { chartTypeDef, type ChartType } from './chartTypes';
import { useMarket } from '../state/market';
import { marketOf } from '../data/symbols';
import { marketStatus } from '../data/sessions';
import { useQuote } from '../data/quote';
import { useReplay } from '../state/replay';
import { usePaper } from '../state/paper';
import { usePanels } from '../state/panels';
import { usePage } from '../state/page';
import { useWatchlist } from '../state/watchlist';
import { faNum } from '../i18n/time';
import { MI, MSep, MSub, PopMenu } from './menuKit';
import { openChartSettings } from '../shell/SettingsDialog';
import { addTextNote, offerOrderTicket, openAlertDialog, openIndicators } from './actions';
import { usePlacement } from './studyPlacement';
import { useEventsAt, useNewsAt } from './EventMarks';
import './chart-x.css';

const MARKET_NAME: Record<string, [string, string]> = {
  crypto: ['کریپتو', 'Crypto'], forex: ['فارکس', 'Forex'], metals: ['فلزات', 'Metals'], indices: ['شاخص‌ها', 'Indices'], energy: ['انرژی', 'Energy'],
};

/** A study's name and inputs as the legend writes them («RSI» and «14 close»). */
export function studyTitle(st: Pick<Study, 'key' | 'inputs'>): { name: string; inputs: string } {
  const def = REGISTRY[st.key];
  if (!def) return { name: st.key === VOLUME ? 'Vol' : st.key, inputs: '' };
  const vals = Object.values({ ...def.inputs, ...st.inputs }).filter((v) => typeof v === 'number' || (typeof v === 'string' && /^(open|high|low|close|hl2|hlc3|ohlc4|hlcc4|volume)$/.test(v)));
  return { name: shortName(st.key, def.label), inputs: vals.join(' ') };
}

/** What a legend row may also carry when the indicators area provides it: the name and inputs
 *  apart, each plot's name, the values' precision, whether the values are wanted, whether the
 *  study is off on this timeframe. */
export interface RowExtras { name?: string; args?: string; plotNames?: string[]; precision?: number; statusValues?: boolean; tfOff?: boolean }
export const extras = (r: StudyLegendRow) => r as StudyLegendRow & RowExtras;

/** The digits for an indicator's value: its own precision, else the price's on the price pane. */
export function valueDigits(r: StudyLegendRow, v: number, digits: number): number {
  const p = extras(r).precision;
  if (typeof p === 'number' && p >= 0) return p;
  return r.pane === 0 ? digits : Math.abs(v) >= 1000 ? 0 : 2;
}

/** The flash of the close on a new tick, green up and red down (no flash when motion is reduced: the CSS turns animations off). */
function useFlash(value: number | null | undefined, live: boolean): { cls: string; key: number } {
  const prev = useRef(value);
  const [st, setSt] = useState({ cls: '', key: 0 });
  useEffect(() => {
    if (live && value != null && prev.current != null && value !== prev.current) setSt((s) => ({ cls: value > prev.current! ? 'flash-up' : 'flash-down', key: s.key + 1 }));
    prev.current = value;
  }, [value, live]);
  return st;
}

function Tools({ children }: { children: React.ReactNode }) {
  return <span className="legend-tools">{children}</span>;
}

export function Legend({ symbol, displayName, interval, chartType, digits, shown, prev, rows, compareRows, onStudySettings, hovering }: {
  symbol: string; displayName: string; interval: string; chartType: ChartType; digits: number; shown: Candle | null; prev: Candle | undefined;
  rows: StudyLegendRow[]; compareRows: CompareRow[]; onStudySettings?(uid: string): void; hovering: boolean;
}) {
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const fa = lang === 'fa';
  const cs = useChartSettings();
  const studies = useStudies((s) => s.list);
  const { toggle: toggleStudy, remove: removeStudy, clear: clearStudies } = useStudies();
  const tk = useMarket((m) => m.tickers[symbol]);
  const market = marketOf(symbol);
  const open = marketStatus(market);
  const quote = useQuote(symbol);
  const replayOn = useReplay((r) => Boolean(r.state?.on));
  const [menu, setMenu] = useState<{ kind: 'symbol' | 'study' | 'volume' | 'compare'; uid?: string; x: number; y: number } | null>(null);
  const [confirm, setConfirm] = useState<{ side: 'long' | 'short'; px: number } | null>(null);
  const flash = useFlash(shown?.c, !hovering);
  const volStudy = studies.find((s) => s.key === VOLUME);
  const tone = shown && shown.c >= shown.o ? 'up' : 'down';
  const base = prev?.c ?? shown?.o;
  const chg = shown && base != null ? shown.c - base : 0;
  const chgPct = shown && base ? (chg / base) * 100 : 0;
  const chgTone = chg >= 0 ? 'up' : 'down';
  const others = studies.filter((s) => s.key !== VOLUME);
  const typeDef = chartTypeDef(chartType);
  const hoverEvents = useEventsAt(shown?.t ?? null, hovering);
  const hoverNews = useNewsAt(shown?.t ?? null, hovering);

  const title = cs.titleMode === 'ticker' || displayName === symbol ? symbol : cs.titleMode === 'description' ? displayName : null;
  const at = (e: React.MouseEvent, kind: 'symbol' | 'study' | 'volume' | 'compare', uid?: string) => {
    const r = (e.currentTarget as HTMLElement).getBoundingClientRect();
    setMenu({ kind, uid, x: fa ? r.right : r.left, y: r.bottom + 4 });
  };
  const paperTrade = (side: 'long' | 'short', px: number) => {
    usePaper.getState().open(symbol, side, Number((1000 / px).toPrecision(2)), px);
    if (usePanels.getState().bottom !== 'paper') usePanels.getState().setBottom('paper');
  };
  const trade = (side: 'long' | 'short', px: number) => {
    if (offerOrderTicket({ symbol, side: side === 'long' ? 'buy' : 'sell', price: px, source: 'legend' })) return;
    if (cs.instantOrders) paperTrade(side, px); else setConfirm({ side, px });
  };
  const bg = cs.legendBg ? { ['--legend-bg' as string]: `color-mix(in srgb, var(--bg) ${cs.legendBgOpacity}%, transparent)` } : undefined;
  const count = others.length + (volStudy ? 1 : 0);

  const studyRow = (r: StudyLegendRow) => (
    <StudyRow key={r.uid} row={r} digits={digits} onStudySettings={onStudySettings} onMenu={(e) => at(e, 'study', r.uid)} />
  );

  return (
    <div className={`legend${cs.legendBg ? ' with-bg' : ''}${cs.legendCompact ? ' compact' : ''}`} style={bg} dir="ltr">
      <div className="legend-row legend-main">
        <span className="legend-head" onDoubleClick={() => openChartSettings('symbol')}>
          {cs.showLogo ? <SymbolLogo symbol={symbol} size={18} /> : null}
          {cs.showTitle ? (
            title != null
              ? <span className={`legend-title${cs.titleMode === 'description' && fa ? ' fa' : ' sym'}`}>{title}</span>
              : <><span className="legend-title sym">{symbol}</span><span className={`legend-desc${fa ? ' fa' : ''}`}>{displayName}</span></>
          ) : null}
          <span className="legend-dot">·</span>
          <span className="num legend-iv">{intervalLabel(interval)}</span>
          <span className="legend-dot">·</span>
          <span className={`legend-mkt${fa ? ' fa' : ''}`}>{MARKET_NAME[market]?.[fa ? 0 : 1] ?? market}</span>
          <span className="legend-type" title={fa ? typeDef.fa : typeDef.en}><Icon name={typeDef.icon} size="sm" /></span>
          {cs.showMarketStatus ? (
            <span className={`legend-status ${open ? 'open' : 'closed'}`} title={open ? L('بازار باز است', 'Market open') : L('بازار بسته است', 'Market closed')} aria-label={open ? L('بازار باز است', 'Market open') : L('بازار بسته است', 'Market closed')}><i /></span>
          ) : null}
          <Tools>
            <button className="lt" title={L('تنظیمات چارت', 'Chart settings')} aria-label={L('تنظیمات چارت', 'Chart settings')} onClick={() => openChartSettings('symbol')}><Icon name="ph-gear" size="sm" /></button>
            <button className="lt" title={L('بیشتر', 'More')} aria-label={L('بیشتر', 'More')} onClick={(e) => at(e, 'symbol')}><Icon name="ph-dots-three" size="sm" /></button>
          </Tools>
        </span>
        {shown ? (
          <span className="legend-values num">
            {cs.showOhlc ? (
              <span className={`legend-ohlc ${tone}`}>
                <i>O</i>{formatPrice(shown.o, digits)} <i>H</i>{formatPrice(shown.h, digits)} <i>L</i>{formatPrice(shown.l, digits)} <i>C</i><b key={flash.key} className={flash.cls}>{formatPrice(shown.c, digits)}</b>
              </span>
            ) : null}
            {cs.showChange ? <span className={`legend-chg ${chgTone}`}>{chg >= 0 ? '+' : '−'}{formatPrice(Math.abs(chg), digits)} ({chg >= 0 ? '+' : '−'}{Math.abs(chgPct).toFixed(2)}%)</span> : null}
            {cs.showVolume ? <span className="legend-vol"><i>Vol</i><span className={tone}>{formatVolume(shown.v)}</span></span> : null}
            {cs.showLastDay && tk ? <span className={`legend-day ${tk.change >= 0 ? 'up' : 'down'}`} title={L('تغییر روز گذشته', 'Last day change')}><i>D</i>{tk.change >= 0 ? '+' : '−'}{formatPrice(Math.abs(tk.change), digits)} ({formatPct(tk.changePct)})</span> : null}
          </span>
        ) : null}
      </div>
      {hoverEvents.length ? (
        <div className="legend-events">
          {hoverEvents.map((e, i) => <span key={i} className="num"><i className={`ev-dot ${e.impact}`} />{e.currency} · {e.title}{e.actual ? ` · ${e.actual}` : e.forecast ? ` · f ${e.forecast}` : ''}</span>)}
        </div>
      ) : null}
      {hoverNews.length ? (
        <div className="legend-events news">
          {hoverNews.map((n) => <span key={n.id} className={n.market === 'crypto' || /[؀-ۿ]/.test(n.title) ? 'fa' : ''}><i className="ev-dot news" />{n.title}</span>)}
        </div>
      ) : null}
      {quote && !replayOn && cs.tradeButtons ? (
        <div className="legend-trade" role="group" aria-label={L('خرید و فروش', 'Trade')}>
          <button className="lt-sell" title={L('فروش (معامله‌ی آزمایشی)', 'Sell (paper)')} onClick={() => trade('short', (quote.bid + quote.ask) / 2)}><b className="num">{formatPrice(quote.bid, digits)}</b><span>{L('فروش', 'SELL')}</span></button>
          <span className="lt-spread num">{formatPrice(quote.spread, digits)}</span>
          <button className="lt-buy" title={L('خرید (معامله‌ی آزمایشی)', 'Buy (paper)')} onClick={() => trade('long', (quote.bid + quote.ask) / 2)}><b className="num">{formatPrice(quote.ask, digits)}</b><span>{L('خرید', 'BUY')}</span></button>
        </div>
      ) : null}
      {confirm ? (
        <div className="legend-confirm" role="alertdialog" dir={fa ? 'rtl' : 'ltr'}>
          <span>{confirm.side === 'long' ? L('خرید', 'Buy') : L('فروش', 'Sell')} <b className="num">{Number((1000 / confirm.px).toPrecision(2))}</b> <span className="sym">{symbol}</span> {L('به قیمت بازار؟', 'at market?')}</span>
          <button className="btn btn-primary sm" onClick={() => { paperTrade(confirm.side, confirm.px); setConfirm(null); }}>{L('تأیید', 'Confirm')}</button>
          <button className="btn btn-quiet sm" onClick={() => setConfirm(null)}>{L('انصراف', 'Cancel')}</button>
        </div>
      ) : null}
      {!cs.legendCollapsed ? (
        <div className="legend-rows">
          {volStudy && shown && (BRICKS_OR_TIME(chartType)) ? (
            <div className={`legend-study${volStudy.hidden ? ' off' : ''}`}>
              <span className="legend-study-name">Vol</span>
              {!volStudy.hidden && cs.indValues ? <span className={`num ${tone}`}>{formatVolume(shown.v)}</span> : null}
              <Tools>
                <button className="lt" title={volStudy.hidden ? L('نمایش', 'Show') : L('پنهان کردن', 'Hide')} onClick={() => toggleStudy(volStudy.uid)}><Icon name={volStudy.hidden ? 'eye_off' : 'ph-eye'} size="sm" /></button>
                <button className="lt" title={L('تنظیمات', 'Settings')} onClick={(e) => at(e, 'volume')}><Icon name="ph-gear" size="sm" /></button>
                <button className="lt" title={L('حذف', 'Remove')} onClick={() => removeStudy(volStudy.uid)}><Icon name="ph-trash" size="sm" /></button>
              </Tools>
            </div>
          ) : null}
          {compareRows.map((c) => <CompareLegendRow key={c.symbol} row={c} digits={digits} onMenu={(e) => at(e, 'compare', c.symbol)} />)}
          {rows.map(studyRow)}
        </div>
      ) : null}
      {count + compareRows.length > 0 ? (
        <div className="legend-fold">
          <button className="lt fold" title={cs.legendCollapsed ? L('نمایش اندیکاتورها', 'Show indicators') : L('پنهان کردن اندیکاتورها', 'Hide indicators')} aria-expanded={!cs.legendCollapsed}
            onClick={() => cs.set({ legendCollapsed: !cs.legendCollapsed })}>
            <Icon name={cs.legendCollapsed ? 'ph-caret-down' : 'ph-caret-up'} size="sm" />
          </button>
          {cs.legendCollapsed ? <span className={`legend-summary${fa ? ' fa' : ''}`}>{fa ? `${faNum(count)} اندیکاتور` : `${count} indicator${count === 1 ? '' : 's'}`}</span> : null}
          <Tools>
            <button className={`lt${cs.legendCompact ? ' on' : ''}`} title={cs.legendCompact ? L('نمای کامل', 'Normal view') : L('نمای فشرده', 'Compact view')} aria-pressed={cs.legendCompact} onClick={() => cs.set({ legendCompact: !cs.legendCompact })}><Icon name="ph-rows" size="sm" /></button>
            {others.length ? (
              <button className="lt" title={L('حذف همه‌ی اندیکاتورها', 'Remove all indicators')} onClick={() => {
                if (window.confirm(fa ? `${faNum(others.length)} اندیکاتور حذف شود؟` : `Remove ${others.length} indicator${others.length === 1 ? '' : 's'}?`)) clearStudies();
              }}><Icon name="ph-trash" size="sm" /></button>
            ) : null}
          </Tools>
        </div>
      ) : null}
      {menu?.kind === 'study' && menu.uid ? <StudyMenu uid={menu.uid} at={menu} onClose={() => setMenu(null)} onStudySettings={onStudySettings} /> : null}
      {menu?.kind === 'symbol' ? <SymbolMenu symbol={symbol} digits={digits} last={shown?.c ?? null} at={menu} onClose={() => setMenu(null)} /> : null}
      {menu?.kind === 'volume' ? <VolumeMenu at={menu} onClose={() => setMenu(null)} /> : null}
      {menu?.kind === 'compare' && menu.uid ? <CompareMenu symbol={menu.uid} at={menu} onClose={() => setMenu(null)} /> : null}
    </div>
  );
}

/** Volume shows on time charts and on the bricks of Renko, Range and Line break. */
const BRICKS_OR_TIME = (t: ChartType) => !['kagi', 'pnf'].includes(t);

function CompareLegendRow({ row, digits, onMenu }: { row: CompareRow; digits: number; onMenu(e: React.MouseEvent): void }) {
  const L = useL();
  const { toggle, remove, setColor } = useCompare();
  return (
    <div className={`legend-study cmp${row.hidden ? ' off' : ''}`}>
      <label className="legend-swatch" title={L('رنگ', 'Colour')} style={{ background: row.color }}>
        <input type="color" aria-label={L('رنگ', 'Colour')} value={/^#[0-9a-f]{6}$/i.test(row.color) ? row.color : '#ff9800'} onChange={(e) => setColor(row.symbol, e.target.value)} />
      </label>
      <span className="legend-study-name sym" style={{ color: row.color }}>{row.symbol}</span>
      {!row.hidden && row.value != null ? <span className="num">{formatPrice(row.value, Math.min(8, Math.max(2, digits)))}</span> : null}
      {!row.hidden && row.change != null ? <span className={`num ${row.change >= 0 ? 'up' : 'down'}`}>{formatPct(row.change)}</span> : null}
      <Tools>
        <button className="lt" title={row.hidden ? L('نمایش', 'Show') : L('پنهان کردن', 'Hide')} onClick={() => toggle(row.symbol)}><Icon name={row.hidden ? 'eye_off' : 'ph-eye'} size="sm" /></button>
        <button className="lt" title={L('حذف', 'Remove')} onClick={() => remove(row.symbol)}><Icon name="ph-trash" size="sm" /></button>
        <button className="lt" title={L('بیشتر', 'More')} onClick={onMenu}><Icon name="ph-dots-three" size="sm" /></button>
      </Tools>
    </div>
  );
}

/** One indicator's row: colour dot, name, inputs, values; eye, gear, trash and «⋯» on hover. */
export function StudyRow({ row, digits, onStudySettings, onMenu }: { row: StudyLegendRow; digits: number; onStudySettings?(uid: string): void; onMenu(e: React.MouseEvent): void }) {
  const L = useL();
  const cs = useChartSettings();
  const study = useStudies((s) => s.list.find((x) => x.uid === row.uid));
  const { toggle, remove } = useStudies();
  const x = extras(row);
  const t = x.name != null ? { name: x.name, inputs: x.args ?? '' } : study ? studyTitle(study) : { name: row.label, inputs: '' };
  const color = row.colors.find(Boolean) ?? 'var(--text-2)';
  const values = !row.hidden && cs.indValues && x.statusValues !== false && !x.tfOff ? row.values.map((v, i) => (
    <span key={i} className="num" style={{ color: row.colors[i] }}>{v == null ? '∅' : formatPrice(v, valueDigits(row, v, digits))}</span>
  )) : null;
  if (cs.legendCompact) {
    return (
      <button type="button" className={`legend-chip${row.hidden ? ' off' : ''}`} title={row.label} onClick={() => onStudySettings?.(row.uid)}>
        <i style={{ background: color }} />{cs.indTitles ? <span>{t.name}</span> : null}
      </button>
    );
  }
  return (
    <div className={`legend-study${row.hidden || x.tfOff ? ' off' : ''}`} data-uid={row.uid}>
      <button type="button" className="legend-swatch" style={{ background: color }} title={L('تنظیمات', 'Settings')} aria-label={L('تنظیمات', 'Settings')} onClick={() => onStudySettings?.(row.uid)} />
      {cs.indTitles ? <span className="legend-study-name num" onDoubleClick={() => onStudySettings?.(row.uid)}>{t.name}{cs.indInputs && t.inputs ? <span className="legend-inputs"> {t.inputs}</span> : null}</span> : null}
      {values}
      <Tools>
        <button className="lt" title={row.hidden ? L('نمایش', 'Show') : L('پنهان کردن', 'Hide')} onClick={() => toggle(row.uid)}><Icon name={row.hidden ? 'eye_off' : 'ph-eye'} size="sm" /></button>
        <button className="lt" title={L('تنظیمات', 'Settings')} onClick={() => onStudySettings?.(row.uid)}><Icon name="ph-gear" size="sm" /></button>
        <button className="lt" title={L('حذف اندیکاتور', 'Remove indicator')} aria-label={L('حذف اندیکاتور', 'Remove indicator')} onClick={() => remove(row.uid)}><Icon name="ph-trash" size="sm" /></button>
        <button className="lt" title={L('بیشتر', 'More')} aria-label={L('بیشتر', 'More')} onClick={onMenu}><Icon name="ph-dots-three" size="sm" /></button>
      </Tools>
    </div>
  );
}

/** The legend of a lower pane: its indicator row. */
export function PaneLegend({ row, top, digits, onStudySettings, paneCount, maximised, collapsed }: { row: StudyLegendRow; top: number; digits: number; onStudySettings?(uid: string): void; paneCount: number; maximised: boolean; collapsed: boolean }) {
  const [menu, setMenu] = useState<{ x: number; y: number } | null>(null);
  const fa = usePrefs((s) => s.lang) === 'fa';
  void paneCount; void maximised; void collapsed;
  return (
    <div className="legend pane-legend" style={{ top }} dir="ltr">
      <StudyRow row={row} digits={digits} onStudySettings={onStudySettings} onMenu={(e) => { const r = (e.currentTarget as HTMLElement).getBoundingClientRect(); setMenu({ x: fa ? r.right : r.left, y: r.bottom + 4 }); }} />
      {menu ? <StudyMenu uid={row.uid} at={menu} onClose={() => setMenu(null)} onStudySettings={onStudySettings} /> : null}
    </div>
  );
}

/** An indicator's «⋯» menu: alert, settings, duplicate, move, pin to scale, panes, help, remove. */
export function StudyMenu({ uid, at, onClose, onStudySettings }: { uid: string; at: { x: number; y: number }; onClose(): void; onStudySettings?(uid: string): void }) {
  const L = useL();
  const symbol = usePrefs((s) => s.symbol);
  const studies = useStudies((s) => s.list);
  const st = studies.find((s) => s.uid === uid);
  const rows = useChartBus((b) => b.studyRows);
  const row = rows.find((r) => r.uid === uid);
  const place = usePlacement((p) => p.byUid[uid]) ?? {};
  if (!st) return null;
  const { name } = studyTitle(st);
  const ownPane = (row?.pane ?? (REGISTRY[st.key]?.pane === 'main' ? 0 : 1)) > 0;
  const panes = useChartBus.getState().api?.panes().length ?? 1;
  const paneIdx = row?.pane ?? 0;
  const s = useStudies.getState();
  // The pin goes in this chart's placement, and in the study's own style when the indicators area keeps one.
  const pin = (scale: 'left' | 'right' | 'none') => {
    usePlacement.getState().set(uid, { scale });
    const patch = (useStudies.getState() as unknown as { patch?(uid: string, p: Record<string, unknown>): void }).patch;
    const style = (st as unknown as { style?: Record<string, unknown> }).style;
    if (typeof patch === 'function' && style) patch(uid, { style: { ...style, scale: scale === 'right' ? undefined : scale } });
  };
  const pane = (op: 'max' | 'collapse') => window.dispatchEvent(new CustomEvent('pc:pane', { detail: { op, pane: paneIdx } }));
  return (
    <PopMenu x={at.x} y={at.y} onClose={onClose} label={name}>
      <MI icon="ph-bell" act="study-alert" label={L(`افزودن هشدار روی ${name}`, `Add alert on ${name}`)} onClick={() => openAlertDialog({ symbol, study: { uid, key: st.key, plot: 0 } })} />
      <MSep />
      <MI icon="ph-gear" act="study-settings" label={L('تنظیمات…', 'Settings…')} onClick={() => onStudySettings?.(uid)} />
      <MI icon="ph-copy" act="study-duplicate" label={L('تکثیر', 'Duplicate')} onClick={() => s.duplicate(uid)} />
      <MI icon={st.hidden ? 'ph-eye' : 'ph-eye-slash'} act="study-hide" label={st.hidden ? L('نمایش', 'Show') : L('پنهان کردن', 'Hide')} onClick={() => s.toggle(uid)} />
      <MSep />
      <MSub icon="ph-stack" act="study-move" label={L('انتقال به', 'Move to')}>
        {ownPane ? <MI act="move-main" label={L('پنل اصلی (روی قیمت)', 'Main pane (over the price)')} onClick={() => usePlacement.getState().set(uid, { pane: 'main' })} /> : null}
        {!ownPane || place.pane === 'main' ? <MI act="move-new" label={L('پنل تازه در پایین', 'New pane below')} onClick={() => usePlacement.getState().set(uid, { pane: place.pane === 'main' ? undefined : 'new' })} /> : null}
        {ownPane && place.pane !== 'main' ? (
          <>
            <MI icon="ph-arrow-up" act="pane-up" label={L('یک پنل بالاتر', 'Pane up')} disabled={paneIdx <= 1} onClick={() => s.move(uid, -1)} />
            <MI icon="ph-arrow-down" act="pane-down" label={L('یک پنل پایین‌تر', 'Pane down')} disabled={paneIdx >= panes - 1} onClick={() => s.move(uid, 1)} />
          </>
        ) : null}
      </MSub>
      <MSub icon="ph-scales" act="study-pin" label={L('اتصال به مقیاس', 'Pin to scale')} hint={place.scale === 'left' ? L('چپ', 'left') : place.scale === 'none' ? L('بدون', 'none') : L('راست', 'right')}>
        <MI act="pin-right" checked={!place.scale || place.scale === 'right'} label={L('مقیاس راست', 'Right scale')} onClick={() => pin('right')} />
        <MI act="pin-left" checked={place.scale === 'left'} label={L('مقیاس چپ', 'Left scale')} onClick={() => pin('left')} />
        <MI act="pin-none" checked={place.scale === 'none'} label={L('بدون مقیاس', 'No scale')} onClick={() => pin('none')} />
      </MSub>
      {ownPane ? (
        <>
          <MI icon="ph-corners-out" act="pane-max" label={L('بزرگ کردن پنل', 'Maximize pane')} onClick={() => pane('max')} />
          <MI icon="ph-arrows-in-line-vertical" act="pane-collapse" label={L('جمع کردن پنل', 'Collapse pane')} onClick={() => pane('collapse')} />
        </>
      ) : null}
      <MSep />
      <MI icon="ph-question" act="study-help" label={L('راهنما', 'Help')} onClick={() => usePage.getState().open('help', st.key)} />
      <MI icon="ph-trash" act="study-remove" danger label={L('حذف', 'Remove')} hint="Del" onClick={() => { s.remove(uid); usePlacement.getState().forget(uid); }} />
    </PopMenu>
  );
}

/** The symbol row's «⋯»: alert, indicator, symbol info, copy price, scale side, watchlist, note, settings. */
function SymbolMenu({ symbol, digits, last, at, onClose }: { symbol: string; digits: number; last: number | null; at: { x: number; y: number }; onClose(): void }) {
  const L = useL();
  const cs = useChartSettings();
  const label = last != null ? formatPrice(last, digits) : '';
  return (
    <PopMenu x={at.x} y={at.y} onClose={onClose} label={symbol}>
      {last != null ? <MI icon="ph-bell" act="sym-alert" label={<>{L('افزودن هشدار روی', 'Add alert on')} <span className="sym">{symbol}</span> {L('در', 'at')} <span className="num">{label}</span></>} hint="Alt+A" onClick={() => openAlertDialog({ symbol, price: last })} /> : null}
      <MI icon="ph-chart-line-up" act="sym-indicator" label={L('افزودن اندیکاتور…', 'Add indicator…')} hint="/" onClick={openIndicators} />
      <MSep />
      <MI icon="ph-info" act="sym-info" label={L('اطلاعات نماد…', 'Symbol info…')} onClick={() => { const p = usePanels.getState(); if (p.open !== 'watchlist') p.toggle('watchlist'); }} />
      {last != null ? <MI icon="ph-copy" act="sym-copy" label={<>{L('کپی قیمت', 'Copy price')} <span className="num">{label}</span></>} onClick={() => { void navigator.clipboard?.writeText(label.replace(/,/g, '')); }} /> : null}
      <MSub icon="ph-scales" act="sym-pin" label={L('اتصال به مقیاس', 'Pin to scale')} hint={cs.scalePlacement === 'left' ? L('چپ', 'left') : L('راست', 'right')}>
        <MI act="sym-pin-right" checked={cs.scalePlacement !== 'left'} label={L('مقیاس راست', 'Right scale')} onClick={() => cs.set({ scalePlacement: 'right' })} />
        <MI act="sym-pin-left" checked={cs.scalePlacement === 'left'} label={L('مقیاس چپ', 'Left scale')} onClick={() => cs.set({ scalePlacement: 'left' })} />
      </MSub>
      <MSep />
      <MI icon="ph-star" act="sym-watch" label={<>{L('افزودن', 'Add')} <span className="sym">{symbol}</span> {L('به فهرست دیده‌بانی', 'to watchlist')}</>} onClick={() => useWatchlist.getState().add(symbol)} />
      {last != null ? <MI icon="ph-note" act="sym-note" label={<>{L('یادداشت متنی برای', 'Add text note for')} <span className="sym">{symbol}</span></>} hint="Alt+N" onClick={() => addTextNote(last, null)} /> : null}
      <MSep />
      <MI icon="ph-gear" act="sym-settings" label={L('تنظیمات…', 'Settings…')} onClick={() => openChartSettings('symbol')} />
    </PopMenu>
  );
}

function VolumeMenu({ at, onClose }: { at: { x: number; y: number }; onClose(): void }) {
  const L = useL();
  const cs = useChartSettings();
  return (
    <PopMenu x={at.x} y={at.y} onClose={onClose} label={L('حجم', 'Volume')}>
      <MI act="vol-ma" checked={cs.volumeMa} keep label={<>{L('میانگین متحرک', 'Moving average')} <span className="num muted">{cs.volumeMaLength}</span></>} onClick={() => cs.set({ volumeMa: !cs.volumeMa })} />
      <MI act="vol-prev" checked={cs.colorByPrevClose} keep label={L('رنگ بر پایه‌ی پایانی قبلی', 'Colour by previous close')} onClick={() => cs.set({ colorByPrevClose: !cs.colorByPrevClose })} />
      <MI act="vol-profile" checked={cs.volumeProfile} keep label={L('پروفایل حجم بازه‌ی دیده‌شده', 'Volume profile (visible range)')} onClick={() => cs.set({ volumeProfile: !cs.volumeProfile })} />
      <MSep />
      <MI icon="ph-gear" act="vol-settings" label={L('تنظیمات…', 'Settings…')} onClick={() => openChartSettings('canvas')} />
    </PopMenu>
  );
}

function CompareMenu({ symbol, at, onClose }: { symbol: string; at: { x: number; y: number }; onClose(): void }) {
  const L = useL();
  const cs = useChartSettings();
  const st = useCompare();
  return (
    <PopMenu x={at.x} y={at.y} onClose={onClose} label={symbol}>
      <MI act="cmp-show" icon={st.hidden[symbol] ? 'ph-eye' : 'ph-eye-slash'} label={st.hidden[symbol] ? L('نمایش', 'Show') : L('پنهان کردن', 'Hide')} onClick={() => st.toggle(symbol)} />
      <MI act="cmp-own" checked={cs.compareMode === 'own'} label={L('مقیاس جداگانه', 'Own scale')} onClick={() => cs.set({ compareMode: 'own' })} />
      <MI act="cmp-pct" checked={cs.compareMode === 'percent'} label={L('مقیاس درصدی', 'Percent scale')} onClick={() => cs.set({ compareMode: 'percent' })} />
      <MSep />
      <MI act="cmp-remove" icon="ph-trash" danger label={L('حذف', 'Remove')} onClick={() => st.remove(symbol)} />
    </PopMenu>
  );
}
