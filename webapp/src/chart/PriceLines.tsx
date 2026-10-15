// The horizontal lines and their axis labels that the settings ask for, kept in step with the
// price series: previous day close, previous day high and low, the visible range's high, low and
// average close, bid and ask, the price alerts (dragged to change their price), the paper
// positions with their stop and target (dragged too) and the pending orders, and a trade setup.
// Also the marks on bars: the visible range's highest high and lowest low, and executions.
import { memo, useEffect, useMemo, useRef, useState } from 'react';
import { LineStyle, createSeriesMarkers, type IChartApi, type IPriceLine, type ISeriesApi, type ISeriesMarkersPluginApi, type SeriesMarker, type SeriesType, type Time } from 'lightweight-charts';
import type { Candle } from '../data/types';
import { usePrefs } from '../state/prefs';
import { useChartSettings, showsLine, showsValue, type LineVis } from '../state/chartSettings';
import { useMarket } from '../state/market';
import { useQuote } from '../data/quote';
import { useAlerts, type PriceAlert } from '../state/alerts';
import { usePaper } from '../state/paper';
import { useOverlay } from '../state/overlay';
import { useChartBus } from './chartBus';
import { barsFor } from '../data/symbolStats';
import { formatPrice } from './format';
import { useL } from '../pages/kit';
import { Icon } from '../ui/Icon';
import { barIndexAt } from './EventMarks';
import './chart-x.css';

export interface LineSpec {
  id: string; price: number; color: string; title: string; style?: LineStyle; width?: 1 | 2 | 3 | 4;
  vis: LineVis;
  /** A drag handle on the line: what to do with the price it is dropped at. */
  drag?: (price: number) => void;
}

/** The visible range's high, low and average close, and the bars that made the high and the low. */
export function visibleStats(list: Candle[], from: number, to: number): { high: number; low: number; avg: number; hiT: number; loT: number } | null {
  const a = Math.max(0, Math.floor(from)); const b = Math.min(list.length - 1, Math.ceil(to));
  if (b < a) return null;
  let hi = -Infinity; let lo = Infinity; let sum = 0; let n = 0; let hiT = 0; let loT = 0;
  for (let i = a; i <= b; i++) {
    const x = list[i]!;
    if (x.h > hi) { hi = x.h; hiT = x.t; }
    if (x.l < lo) { lo = x.l; loT = x.t; }
    sum += x.c; n++;
  }
  return n ? { high: hi, low: lo, avg: sum / n, hiT, loT } : null;
}

/** An alert keeps its id where the store can change a price; otherwise it is replaced by an equal one. */
export function moveAlert(a: PriceAlert, price: number): void {
  const st = useAlerts.getState() as unknown as { update?(id: string, p: Partial<PriceAlert>): void; remove(id: string): void; add(symbol: string, condition: PriceAlert['condition'], value: number): void };
  if (typeof st.update === 'function') { st.update(a.id, { value: price }); return; }
  st.remove(a.id);
  st.add(a.symbol, a.condition, price);
}

export const PriceLines = memo(function PriceLines({ series, api, version, bars, barsVersion, digits }: { series: ISeriesApi<SeriesType> | null; api: IChartApi | null; version: number; bars: Candle[]; barsVersion: number; digits: number }) {
  const L = useL();
  const symbol = usePrefs((s) => s.symbol);
  const lang = usePrefs((s) => s.lang);
  const cs = useChartSettings();
  const shown = useChartBus((b) => b.shown);
  const quote = useQuote(cs.bidAsk !== 'hidden' ? symbol : '');
  const alerts = useAlerts((a) => a.list);
  const positions = usePaper((p) => p.positions);
  const orders = usePaper((p) => p.orders);
  const history = usePaper((p) => p.history);
  const setup = useOverlay((o) => o.setup);
  const prevClose = useMarket((m) => { const tk = m.tickers[symbol]; if (!tk) return null; const v = tk.prevClose ?? tk.last - tk.change; return Number.isFinite(v) && v > 0 ? Number(v.toPrecision(10)) : null; });
  const chartType = usePrefs((s) => s.chartType);
  const [daily, setDaily] = useState<Candle[]>([]);
  const [base, setBase] = useState<number | null>(null);

  // Baseline: the base sits at a share of the visible price range (TradingView's 50% unless
  // changed), follows the scale as it moves, and can be dragged to a new level.
  useEffect(() => {
    if (!api || !series || chartType !== 'baseline') { setBase(null); return; }
    const level = () => {
      const h = api.panes()[0]?.getHeight() ?? 0;
      const top = series.coordinateToPrice(0); const bot = series.coordinateToPrice(h);
      if (!h || typeof top !== 'number' || typeof bot !== 'number') return null;
      return { top, bot };
    };
    const apply = () => {
      const r = level();
      if (!r) return;
      const price = r.bot + (r.top - r.bot) * (useChartSettings.getState().baselinePct / 100);
      series.applyOptions({ baseValue: { type: 'price', price } });
      setBase((prev) => (prev != null && Math.abs(prev - price) < Math.abs(r.top - r.bot) * 1e-4 ? prev : price));
    };
    apply();
    api.timeScale().subscribeVisibleLogicalRangeChange(apply);
    const id = setInterval(apply, 1000);
    return () => { clearInterval(id); try { api.timeScale().unsubscribeVisibleLogicalRangeChange(apply); } catch { /* gone */ } };
  }, [api, series, chartType, cs.baselinePct, version]);
  const dropBase = (price: number) => {
    if (!api || !series) return;
    const h = api.panes()[0]?.getHeight() ?? 0;
    const top = series.coordinateToPrice(0); const bot = series.coordinateToPrice(h);
    if (!h || typeof top !== 'number' || typeof bot !== 'number' || top === bot) return;
    useChartSettings.getState().set({ baselinePct: Math.round(Math.max(0, Math.min(100, ((price - bot) / (top - bot)) * 100))) });
  };
  const [stats, setStats] = useState<ReturnType<typeof visibleStats>>(null);

  // The previous day's bar, for its high and low (and its close when the ticker has none).
  useEffect(() => {
    if (cs.pdhl === 'hidden' && (cs.prevDayClose === 'hidden' || prevClose != null)) return;
    let alive = true;
    void barsFor(symbol, '1D').then((d) => { if (alive) setDaily(d); });
    return () => { alive = false; };
  }, [symbol, cs.pdhl, cs.prevDayClose, prevClose]);
  const prevDay = useMemo(() => {
    if (daily.length < 2) return null;
    const today = Math.floor(Date.now() / 86_400_000) * 86400;
    const idx = daily[daily.length - 1]!.t >= today ? daily.length - 2 : daily.length - 1;
    return daily[idx] ?? null;
  }, [daily]);

  // The visible range's statistics, recomputed as the reader scrolls or zooms.
  const wantStats = cs.highLow !== 'hidden' || cs.avgClose !== 'hidden' || cs.hhll;
  useEffect(() => {
    if (!api || !wantStats) { setStats(null); return; }
    let raf = 0;
    const update = () => {
      cancelAnimationFrame(raf);
      raf = requestAnimationFrame(() => {
        const r = api.timeScale().getVisibleLogicalRange();
        const list = useChartBus.getState().shown;
        const next = r ? visibleStats(list, r.from, r.to) : null;
        setStats((prev) => (prev && next && prev.high === next.high && prev.low === next.low && Math.abs(prev.avg - next.avg) < 1e-12 ? prev : next));
      });
    };
    update();
    api.timeScale().subscribeVisibleLogicalRangeChange(update);
    const id = setInterval(update, 2000);
    return () => { cancelAnimationFrame(raf); clearInterval(id); try { api.timeScale().unsubscribeVisibleLogicalRangeChange(update); } catch { /* chart gone */ } };
  }, [api, wantStats, shown, barsVersion]);

  const specs = useMemo<LineSpec[]>(() => {
    const out: LineSpec[] = [];
    if (base != null) out.push({ id: 'baseline', price: base, color: '#787B86', title: L('مبنا', 'Base'), style: LineStyle.Dotted, vis: 'line', drag: dropBase });
    const pc = prevClose ?? (prevDay ? prevDay.c : null);
    if (cs.prevDayClose !== 'hidden' && pc != null) out.push({ id: 'prevclose', price: pc, color: '#787B86', title: L('پایانی قبلی', 'Prev close'), style: LineStyle.Dotted, vis: cs.prevDayClose });
    if (cs.pdhl !== 'hidden' && prevDay) {
      out.push({ id: 'pdh', price: prevDay.h, color: '#089981', title: 'PDH', style: LineStyle.Dashed, vis: cs.pdhl });
      out.push({ id: 'pdl', price: prevDay.l, color: '#F23645', title: 'PDL', style: LineStyle.Dashed, vis: cs.pdhl });
    }
    if (stats && cs.highLow !== 'hidden') {
      out.push({ id: 'vhigh', price: stats.high, color: '#26A69A', title: L('سقف', 'High'), style: LineStyle.Dashed, vis: cs.highLow });
      out.push({ id: 'vlow', price: stats.low, color: '#EF5350', title: L('کف', 'Low'), style: LineStyle.Dashed, vis: cs.highLow });
    }
    if (stats && cs.avgClose !== 'hidden') out.push({ id: 'avg', price: stats.avg, color: '#FF9800', title: L('میانگین', 'Avg'), style: LineStyle.Dotted, vis: cs.avgClose });
    if (quote && cs.bidAsk !== 'hidden') {
      out.push({ id: 'bid', price: quote.bid, color: '#F23645', title: L('خرید', 'Bid'), style: LineStyle.Dotted, vis: cs.bidAsk });
      out.push({ id: 'ask', price: quote.ask, color: '#2962FF', title: L('فروش', 'Ask'), style: LineStyle.Dotted, vis: cs.bidAsk });
    }
    if (cs.alertLines) {
      for (const a of alerts) {
        if (a.symbol !== symbol || (cs.alertLinesActiveOnly && !a.active)) continue;
        const extra = a as unknown as { value2?: number; upper?: number };
        out.push({ id: `alert-${a.id}`, price: a.value, color: cs.alertLineColor, title: L('هشدار', 'Alert'), style: LineStyle.Dashed, vis: 'value-line', drag: (p) => moveAlert(a, p) });
        const second = extra.value2 ?? extra.upper;
        if (typeof second === 'number' && Number.isFinite(second)) out.push({ id: `alert2-${a.id}`, price: second, color: cs.alertLineColor, title: L('هشدار', 'Alert'), style: LineStyle.Dashed, vis: 'value-line' });
      }
    }
    if (cs.positionLines) {
      for (const p of positions) {
        if (p.symbol !== symbol) continue;
        out.push({ id: `pos-${p.id}`, price: p.entry, color: '#2962FF', title: `${p.side === 'long' ? L('خرید', 'Long') : L('فروش', 'Short')} ${p.qty}`, width: 2, vis: 'value-line' });
        const st = usePaper.getState();
        out.push({ id: `sl-${p.id}`, price: p.sl ?? (p.side === 'long' ? p.entry * 0.99 : p.entry * 1.01), color: '#F23645', title: p.sl != null ? L('حد ضرر', 'SL') : L('حد ضرر (بکشید)', 'SL (drag)'), style: LineStyle.Dashed, vis: p.sl != null ? 'value-line' : 'line', drag: (x) => st.protect(p.id, x, p.tp) });
        out.push({ id: `tp-${p.id}`, price: p.tp ?? (p.side === 'long' ? p.entry * 1.02 : p.entry * 0.98), color: '#089981', title: p.tp != null ? L('حد سود', 'TP') : L('حد سود (بکشید)', 'TP (drag)'), style: LineStyle.Dashed, vis: p.tp != null ? 'value-line' : 'line', drag: (x) => st.protect(p.id, p.sl, x) });
      }
    }
    if (cs.orderLines) {
      for (const o of orders) {
        if (o.symbol !== symbol) continue;
        const px = o.limitPrice ?? o.stopPrice;
        if (px == null) continue;
        out.push({ id: `ord-${o.id}`, price: px, color: o.side === 'long' ? '#089981' : '#F23645', title: `${o.side === 'long' ? L('خرید', 'Buy') : L('فروش', 'Sell')} ${o.type} ${o.qty}`, style: LineStyle.LargeDashed, vis: 'value-line' });
      }
    }
    if (setup && setup.symbol === symbol) {
      if (setup.entry != null) out.push({ id: 'setup-entry', price: setup.entry, color: '#2962FF', title: `${setup.label} · ${L('ورود', 'Entry')}`, width: 2, vis: 'value-line' });
      if (setup.sl != null) out.push({ id: 'setup-sl', price: setup.sl, color: '#F23645', title: 'SL', width: 2, style: LineStyle.Dashed, vis: 'value-line' });
      setup.targets.forEach((tp, i) => out.push({ id: `setup-tp${i}`, price: tp, color: '#089981', title: `TP${i + 1}`, width: 2, style: LineStyle.Dashed, vis: 'value-line' }));
    }
    return out;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cs.prevDayClose, cs.pdhl, cs.highLow, cs.avgClose, cs.bidAsk, cs.alertLines, cs.alertLinesActiveOnly, cs.alertLineColor, cs.positionLines, cs.orderLines, prevClose, prevDay, stats, quote, alerts, positions, orders, setup, symbol, lang, base]);

  // The lines themselves: made, changed or removed to match the list.
  const made = useRef<Map<string, IPriceLine>>(new Map());
  const owner = useRef<ISeriesApi<SeriesType> | null>(null);
  useEffect(() => {
    if (!series) return;
    if (owner.current !== series) { made.current = new Map(); owner.current = series; }
    const seen = new Set<string>();
    for (const sp of specs) {
      seen.add(sp.id);
      const opts = { price: sp.price, color: sp.color, title: showsValue(sp.vis) || sp.vis === 'line' ? sp.title : '', lineStyle: sp.style ?? LineStyle.Solid, lineWidth: sp.width ?? 1, lineVisible: showsLine(sp.vis), axisLabelVisible: showsValue(sp.vis) };
      const line = made.current.get(sp.id);
      if (line) line.applyOptions(opts);
      else made.current.set(sp.id, series.createPriceLine(opts));
    }
    for (const [id, line] of made.current) {
      if (seen.has(id)) continue;
      try { series.removePriceLine(line); } catch { /* series replaced */ }
      made.current.delete(id);
    }
  }, [series, specs, version]);
  useEffect(() => () => { const s = owner.current; if (s) for (const l of made.current.values()) { try { s.removePriceLine(l); } catch { /* gone */ } } made.current = new Map(); }, []);

  // Marks on bars: the visible range's highest high and lowest low; the paper trades' entries and exits.
  const markers = useRef<ISeriesMarkersPluginApi<Time> | null>(null);
  const markerOwner = useRef<ISeriesApi<SeriesType> | null>(null);
  useEffect(() => {
    if (!series) return;
    if (markerOwner.current !== series) { markers.current = null; markerOwner.current = series; }
    const list: SeriesMarker<Time>[] = [];
    if (cs.hhll && stats) {
      list.push({ time: stats.hiT as Time, position: 'aboveBar', color: '#26A69A', shape: 'arrowDown', text: `H ${formatPrice(stats.high, digits)}`, size: 0.8 });
      list.push({ time: stats.loT as Time, position: 'belowBar', color: '#EF5350', shape: 'arrowUp', text: `L ${formatPrice(stats.low, digits)}`, size: 0.8 });
    }
    if (cs.executionMarks && bars.length && !['renko', 'linebreak', 'kagi', 'pnf', 'range'].includes(usePrefs.getState().chartType)) {
      const trades = [...history.filter((h) => h.symbol === symbol), ...positions.filter((p) => p.symbol === symbol)];
      for (const tr of trades.slice(0, 60)) {
        const i = barIndexAt(bars, Math.floor(tr.openedAt / 1000));
        if (i >= 0) list.push({ time: bars[i]!.t as Time, position: tr.side === 'long' ? 'belowBar' : 'aboveBar', color: tr.side === 'long' ? '#2962FF' : '#F23645', shape: tr.side === 'long' ? 'arrowUp' : 'arrowDown', text: tr.side === 'long' ? L('خرید', 'Buy') : L('فروش', 'Sell') });
        const closed = (tr as { closedAt?: number }).closedAt;
        if (closed) {
          const j = barIndexAt(bars, Math.floor(closed / 1000));
          if (j >= 0) list.push({ time: bars[j]!.t as Time, position: tr.side === 'long' ? 'aboveBar' : 'belowBar', color: '#787B86', shape: 'circle', text: L('بستن', 'Close') });
        }
      }
    }
    list.sort((a, b) => (a.time as number) - (b.time as number));
    if (!markers.current) { if (!list.length) return; markers.current = createSeriesMarkers(series, list); }
    else markers.current.setMarkers(list);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [series, version, cs.hhll, cs.executionMarks, stats, history, positions, bars, barsVersion, symbol, digits, lang]);

  return <DragHandles api={api} series={series} specs={specs.filter((s) => s.drag)} digits={digits} />;
});

/** A strip along each draggable line; dragging moves the line and drops it at a new price. The drag
 *  follows the pointer anywhere on the page and is kept inside the pane. */
function DragHandles({ api, series, specs, digits }: { api: IChartApi | null; series: ISeriesApi<SeriesType> | null; specs: LineSpec[]; digits: number }) {
  const L = useL();
  const [, setTick] = useState(0);
  const [drag, setDrag] = useState<{ id: string; price: number } | null>(null);
  const dragRef = useRef<{ id: string; price: number; drop?(p: number): void } | null>(null);
  useEffect(() => {
    if (!api || !specs.length) return;
    const bump = () => setTick((n) => n + 1);
    api.timeScale().subscribeVisibleLogicalRangeChange(bump);
    const id = setInterval(bump, 500);
    return () => { clearInterval(id); try { api.timeScale().unsubscribeVisibleLogicalRangeChange(bump); } catch { /* gone */ } };
  }, [api, specs.length]);
  if (!api || !series || !specs.length) return null;
  const width = api.timeScale().width();
  const paneH = api.panes()[0]?.getHeight() ?? 0;
  const left = api.priceScale('left').width();
  const start = (sp: LineSpec, el: HTMLElement) => {
    const host = (el.parentElement as HTMLElement).getBoundingClientRect();
    dragRef.current = { id: sp.id, price: sp.price, drop: sp.drag };
    setDrag({ id: sp.id, price: sp.price });
    const move = (e: PointerEvent) => {
      const y = Math.max(0, Math.min(paneH, e.clientY - host.top));
      const p = series.coordinateToPrice(y);
      if (typeof p !== 'number' || !dragRef.current) return;
      dragRef.current.price = Number(p.toFixed(digits));
      setDrag({ id: sp.id, price: dragRef.current.price });
    };
    const up = () => {
      window.removeEventListener('pointermove', move);
      window.removeEventListener('pointerup', up);
      window.removeEventListener('pointercancel', up);
      const d = dragRef.current;
      dragRef.current = null;
      setDrag(null);
      if (d && d.price !== sp.price) d.drop?.(d.price);
    };
    window.addEventListener('pointermove', move);
    window.addEventListener('pointerup', up);
    window.addEventListener('pointercancel', up);
  };
  return (
    <>
      {specs.map((sp) => {
        const price = drag?.id === sp.id ? drag.price : sp.price;
        const y = series.priceToCoordinate(price);
        if (y == null || ((y < 0 || y > paneH) && drag?.id !== sp.id)) return null;
        return (
          <div key={sp.id} className={`alert-handle${drag?.id === sp.id ? ' dragging' : ''}`} data-line={sp.id} style={{ top: Math.max(0, Math.min(paneH, y as number)) - 4, left, width }}
            title={L('برای جابه‌جایی بکشید', 'Drag to move')}
            onPointerDown={(e) => { e.preventDefault(); e.stopPropagation(); start(sp, e.currentTarget); }}>
            <span className="alert-grip" style={{ borderColor: sp.color, color: sp.color }}><Icon name={sp.id.startsWith('alert') ? 'ph-bell' : 'ph-arrows-vertical'} size="sm" />{drag?.id === sp.id ? <b className="num">{formatPrice(drag.price, digits)}</b> : null}</span>
          </div>
        );
      })}
    </>
  );
}
