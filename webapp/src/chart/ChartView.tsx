// The main chart: lightweight-charts (TradingView's own open-source renderer) set to TradingView's
// desktop measurements — 6px bars, 10 bars of breathing room on the right, dotted grid, dashed
// crosshair — with the legend, the volume and the live last bar.
import { useEffect, useRef, useState } from 'react';
import {
  CandlestickSeries, ColorType, CrosshairMode, HistogramSeries, LineStyle, createChart,
  PriceScaleMode, type IChartApi, type ISeriesApi, type MouseEventParams, type Time, type UTCTimestamp,
} from 'lightweight-charts';
import { showSpan, useChartBus } from './chartBus';
import type { Candle } from '../data/types';
import { formatPrice, formatVolume } from './format';
import { useT, usePrefs, isDark } from '../state/prefs';
import { intervalLabel } from '../data/intervals';
import { SymbolLogo } from '../ui/SymbolLogo';

export interface ChartFeed {
  /** Bars ending now (or before `before`, a bar time in seconds), oldest first. */
  history(symbol: string, interval: string, before?: number): Promise<Candle[]>;
  /** Live updates of the forming bar, starting from the last bar of history; returns the unsubscribe. */
  live(symbol: string, interval: string, from: Candle | undefined, onBar: (bar: Candle) => void): () => void;
}

interface Palette { bg: string; text: string; line: string; grid: string; cross: string; crossLabel: string; up: string; down: string }

function palette(dark: boolean): Palette {
  return dark
    ? { bg: '#0F0F0F', text: '#B8B8B8', line: '#2E2E2E', grid: 'rgba(242,242,242,0.06)', cross: '#9C9C9C', crossLabel: '#4A4A4A', up: '#089981', down: '#F23645' }
    : { bg: '#FFFFFF', text: '#0F0F0F', line: '#EBEBEB', grid: 'rgba(46,46,46,0.06)', cross: '#9C9C9C', crossLabel: '#131722', up: '#089981', down: '#F23645' };
}

const toBar = (c: Candle) => ({ time: c.t as UTCTimestamp, open: c.o, high: c.h, low: c.l, close: c.c });

export function ChartView({ feed, digits, displayName }: { feed: ChartFeed; digits: number; displayName: string }) {
  const t = useT();
  const symbol = usePrefs((s) => s.symbol);
  const interval = usePrefs((s) => s.interval);
  const theme = usePrefs((s) => s.theme);
  const host = useRef<HTMLDivElement>(null);
  const chart = useRef<IChartApi | null>(null);
  const price = useRef<ISeriesApi<'Candlestick'> | null>(null);
  const volume = useRef<ISeriesApi<'Histogram'> | null>(null);
  const bars = useRef<Candle[]>([]);
  const [hover, setHover] = useState<Candle | null>(null);
  const [last, setLast] = useState<Candle | null>(null);
  const [state, setState] = useState<'loading' | 'ready' | 'error'>('loading');
  const [attempt, setAttempt] = useState(0);
  const dark = isDark(theme);

  // Create once.
  useEffect(() => {
    if (!host.current) return;
    const p = palette(dark);
    const c = createChart(host.current, {
      autoSize: true,
      layout: {
        background: { type: ColorType.Solid, color: p.bg }, textColor: p.text, fontSize: 12,
        fontFamily: "'IRANYekanX', system-ui, sans-serif", attributionLogo: false,
        panes: { separatorColor: p.line, separatorHoverColor: p.line, enableResize: true },
      },
      grid: { vertLines: { color: p.grid, style: LineStyle.Dotted }, horzLines: { color: p.grid, style: LineStyle.Dotted } },
      crosshair: {
        mode: CrosshairMode.Normal,
        vertLine: { color: p.cross, style: LineStyle.Dashed, width: 1, labelBackgroundColor: p.crossLabel },
        horzLine: { color: p.cross, style: LineStyle.Dashed, width: 1, labelBackgroundColor: p.crossLabel },
      },
      rightPriceScale: { borderColor: p.line, scaleMargins: { top: 0.1, bottom: 0.08 } },
      timeScale: { borderColor: p.line, barSpacing: 6, rightOffset: 10, timeVisible: true, secondsVisible: false },
      // Latin digits and English month names on the axes, whatever the browser's locale: market
      // figures are always Latin (owner's rule), and TradingView writes dates the same way.
      localization: { locale: 'en-US', dateFormat: "dd MMM ''yy", priceFormatter: (v: number) => formatPrice(v, digitsRef.current) },
    });
    const s = c.addSeries(CandlestickSeries, {
      upColor: p.up, downColor: p.down, borderUpColor: p.up, borderDownColor: p.down, wickUpColor: p.up, wickDownColor: p.down,
      priceLineStyle: LineStyle.Dotted,
    });
    const v = c.addSeries(HistogramSeries, { priceScaleId: 'vol', priceFormat: { type: 'volume' }, lastValueVisible: false, priceLineVisible: false });
    c.priceScale('vol').applyOptions({ scaleMargins: { top: 0.82, bottom: 0 } });
    c.subscribeCrosshairMove((param: MouseEventParams<Time>) => {
      if (!param.time) { setHover(null); useChartBus.getState().setHover(null); return; }
      const bar = bars.current.find((b) => b.t === param.time);
      setHover(bar ?? null);
      useChartBus.getState().setHover(bar ?? null);
    });
    chart.current = c; price.current = s; volume.current = v;
    useChartBus.getState().setApi(c);
    return () => { useChartBus.getState().setApi(null); c.remove(); chart.current = null; price.current = null; volume.current = null; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const digitsRef = useRef(digits);
  digitsRef.current = digits;

  // Theme changes recolour in place; nothing is refetched.
  useEffect(() => {
    const p = palette(dark);
    chart.current?.applyOptions({
      layout: { background: { type: ColorType.Solid, color: p.bg }, textColor: p.text, panes: { separatorColor: p.line, separatorHoverColor: p.line } },
      grid: { vertLines: { color: p.grid }, horzLines: { color: p.grid } },
      crosshair: { vertLine: { labelBackgroundColor: p.crossLabel }, horzLine: { labelBackgroundColor: p.crossLabel } },
      rightPriceScale: { borderColor: p.line }, timeScale: { borderColor: p.line },
    });
    paintVolume(dark);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [dark]);

  function paintVolume(isDarkNow: boolean) {
    const p = palette(isDarkNow);
    const alpha = isDarkNow ? '80' : '66';
    volume.current?.setData(bars.current.map((b) => ({ time: b.t as UTCTimestamp, value: b.v, color: (b.c >= b.o ? p.up : p.down) + alpha })));
  }

  // The time bar's scale switches.
  const scale = useChartBus((b) => b.scale);
  const autoScale = useChartBus((b) => b.autoScale);
  useEffect(() => {
    const mode = scale === 'log' ? PriceScaleMode.Logarithmic : scale === 'percent' ? PriceScaleMode.Percentage : PriceScaleMode.Normal;
    chart.current?.priceScale('right').applyOptions({ mode, autoScale });
  }, [scale, autoScale]);

  // Load on symbol or interval change; live updates after.
  useEffect(() => {
    let alive = true;
    let stop: (() => void) | null = null;
    setState('loading'); setHover(null);
    feed.history(symbol, interval).then((data) => {
      if (!alive) return;
      bars.current = data;
      price.current?.setData(data.map(toBar));
      paintVolume(dark);
      chart.current?.timeScale().applyOptions({ barSpacing: 6, rightOffset: 10 });
      chart.current?.timeScale().scrollToRealTime();
      const bus = useChartBus.getState();
      bus.setBars(data);
      if (bus.pendingSpan && chart.current) { showSpan(chart.current, data, bus.pendingSpan); bus.setPendingSpan(null); }
      setLast(data[data.length - 1] ?? null);
      setState(data.length ? 'ready' : 'error');
      stop = feed.live(symbol, interval, data[data.length - 1], (bar) => {
        const list = bars.current;
        const tail = list[list.length - 1];
        if (tail && bar.t < tail.t) return;
        if (tail && bar.t === tail.t) list[list.length - 1] = bar; else list.push(bar);
        price.current?.update(toBar(bar));
        const p = palette(dark);
        volume.current?.update({ time: bar.t as UTCTimestamp, value: bar.v, color: (bar.c >= bar.o ? p.up : p.down) + (dark ? '80' : '66') });
        setLast(bar);
      });
    }).catch(() => { if (alive) setState('error'); });
    return () => { alive = false; stop?.(); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [symbol, interval, attempt, feed]);

  const shown = hover ?? last;
  const prev = shown ? bars.current[bars.current.indexOf(shown) - 1] : undefined;
  const chg = shown ? shown.c - (prev?.c ?? shown.o) : 0;
  const chgPct = shown && (prev?.c ?? shown.o) ? (chg / (prev?.c ?? shown.o)) * 100 : 0;
  const tone = shown && shown.c >= shown.o ? 'up' : 'down';

  return (
    <div className="ws-chart">
      <div ref={host} className="chart-host" />
      <div className="legend">
        <div className="legend-row">
          <SymbolLogo symbol={symbol} size={18} />
          <span className="legend-title fa">{displayName}</span>
          <span className="legend-dot">·</span>
          <span className="num">{intervalLabel(interval)}</span>
          {shown ? (
            <span className={`legend-ohlc num ${tone}`}>
              <i>O</i>{formatPrice(shown.o, digits)} <i>H</i>{formatPrice(shown.h, digits)} <i>L</i>{formatPrice(shown.l, digits)} <i>C</i>{formatPrice(shown.c, digits)}
              {' '}{chg >= 0 ? '+' : '−'}{formatPrice(Math.abs(chg), digits)} ({chg >= 0 ? '+' : '−'}{Math.abs(chgPct).toFixed(2)}%)
            </span>
          ) : null}
        </div>
        {shown ? <div className="legend-row sub num"><i>Vol</i> {formatVolume(shown.v)}</div> : null}
      </div>
      {state === 'loading' ? <div className="chart-state fa">{t('loading')}…</div> : null}
      {state === 'error' ? (
        <div className="chart-state fa">{t('chartFailed')} <button className="link" onClick={() => setAttempt((a) => a + 1)}>{t('retry')}</button></div>
      ) : null}
    </div>
  );
}
