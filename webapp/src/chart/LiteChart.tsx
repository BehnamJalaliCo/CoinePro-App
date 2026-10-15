// A second, third or fourth chart in a multi-chart layout: its own symbol and interval, candles
// and volume, the legend, live bars, and the crosshair following the main chart's time.
import { useEffect, useRef, useState } from 'react';
import {
  CandlestickSeries, ColorType, CrosshairMode, HistogramSeries, LineStyle, createChart,
  type IChartApi, type ISeriesApi, type UTCTimestamp,
} from 'lightweight-charts';
import type { ChartFeed } from './ChartView';
import type { Candle } from '../data/types';
import { isDark, usePrefs } from '../state/prefs';
import { useChartBus } from './chartBus';
import { useChartSettings } from '../state/chartSettings';
import { digitsOf } from '../state/market';
import { formatPrice } from './format';
import { intervalLabel } from '../data/intervals';
import { intervalsFor } from '../data/candles';
import { SymbolLogo } from '../ui/SymbolLogo';
import { Icon } from '../ui/Icon';
import { timeOptions } from './time';

export function LiteChart({ feed, symbol, interval, onSymbol, onInterval, onFocus, onMain }: {
  feed: ChartFeed; symbol: string; interval: string;
  onSymbol(): void; onInterval(iv: string): void; onFocus(): void; onMain(): void;
}) {
  const host = useRef<HTMLDivElement>(null);
  const chart = useRef<IChartApi | null>(null);
  const price = useRef<ISeriesApi<'Candlestick'> | null>(null);
  const vol = useRef<ISeriesApi<'Histogram'> | null>(null);
  const bars = useRef<Candle[]>([]);
  const [last, setLast] = useState<Candle | null>(null);
  const theme = usePrefs((s) => s.theme);
  const dark = isDark(theme);
  const { up, down } = useChartSettings();
  const digits = digitsOf(symbol);

  useEffect(() => {
    if (!host.current) return;
    const c = createChart(host.current, {
      autoSize: true,
      layout: { background: { type: ColorType.Solid, color: dark ? '#0F0F0F' : '#FFFFFF' }, textColor: dark ? '#B8B8B8' : '#0F0F0F', fontSize: 11, fontFamily: "'IRANYekanX', system-ui, sans-serif", attributionLogo: false },
      grid: { vertLines: { color: dark ? 'rgba(242,242,242,0.06)' : 'rgba(46,46,46,0.06)', style: LineStyle.Dotted }, horzLines: { color: dark ? 'rgba(242,242,242,0.06)' : 'rgba(46,46,46,0.06)', style: LineStyle.Dotted } },
      crosshair: { mode: CrosshairMode.Normal },
      rightPriceScale: { borderColor: dark ? '#2E2E2E' : '#EBEBEB' },
      timeScale: { borderColor: dark ? '#2E2E2E' : '#EBEBEB', barSpacing: 6, rightOffset: 10, timeVisible: true },
      localization: { locale: 'en-US', priceFormatter: (v: number) => formatPrice(v, digitsOf(symbol)) },
    });
    price.current = c.addSeries(CandlestickSeries, { upColor: up, downColor: down, borderUpColor: up, borderDownColor: down, wickUpColor: up, wickDownColor: down });
    vol.current = c.addSeries(HistogramSeries, { priceScaleId: 'v', priceFormat: { type: 'volume' }, lastValueVisible: false, priceLineVisible: false });
    c.priceScale('v').applyOptions({ scaleMargins: { top: 0.82, bottom: 0 } });
    chart.current = c;
    return () => { c.remove(); chart.current = null; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [dark, up, down]);

  useEffect(() => {
    let alive = true;
    let stop: (() => void) | null = null;
    const ok = intervalsFor(symbol);
    const iv = ok.includes(interval) ? interval : ok.includes('60') ? '60' : ok[0]!;
    const vc = (b: Candle) => (b.c >= b.o ? up : down) + (dark ? '80' : '66');
    feed.history(symbol, iv).then(({ bars: data }) => {
      if (!alive || !price.current) return;
      bars.current = data;
      price.current.setData(data.map((b) => ({ time: b.t as UTCTimestamp, open: b.o, high: b.h, low: b.l, close: b.c })));
      vol.current?.setData(data.map((b) => ({ time: b.t as UTCTimestamp, value: b.v, color: vc(b) })));
      chart.current?.timeScale().scrollToRealTime();
      setLast(data[data.length - 1] ?? null);
      stop = feed.live(symbol, iv, data[data.length - 1], (bar) => {
        const list = bars.current;
        const tail = list[list.length - 1];
        if (tail && bar.t < tail.t) return;
        if (tail && bar.t === tail.t) list[list.length - 1] = bar; else list.push(bar);
        price.current?.update({ time: bar.t as UTCTimestamp, open: bar.o, high: bar.h, low: bar.l, close: bar.c });
        vol.current?.update({ time: bar.t as UTCTimestamp, value: bar.v, color: vc(bar) });
        setLast({ ...bar });
      });
    }).catch(() => undefined);
    return () => { alive = false; stop?.(); };
  }, [feed, symbol, interval, dark, up, down]);

  const timezone = usePrefs((s) => s.timezone);
  useEffect(() => {
    const c = chart.current;
    if (c) { c.applyOptions(timeOptions(timezone, interval)); c.applyOptions({ localization: { locale: 'en-US', priceFormatter: (v: number) => formatPrice(v, digitsOf(symbol)) } }); }
  }, [timezone, interval, symbol, dark, up, down]);

  // The main chart's crosshair, mirrored by time.
  const hover = useChartBus((b) => b.hover);
  useEffect(() => {
    const c = chart.current;
    const s = price.current;
    if (!c || !s) return;
    if (!hover) { c.clearCrosshairPosition(); return; }
    const list = bars.current;
    let best: Candle | undefined;
    for (let i = list.length - 1; i >= 0; i--) { if (list[i]!.t <= hover.t) { best = list[i]; break; } }
    if (best) c.setCrosshairPosition(best.c, best.t as UTCTimestamp, s);
  }, [hover]);

  const tone = last && last.c >= last.o ? 'up' : 'down';
  return (
    <div className="lite" onMouseDown={onFocus}>
      <div className="lite-head">
        <button className="ib" onClick={onSymbol}><SymbolLogo symbol={symbol} size={16} /><span className="sym">{symbol}</span></button>
        <select className="lite-iv num" value={interval} onChange={(e) => onInterval(e.target.value)}>
          {intervalsFor(symbol).map((iv) => <option key={iv} value={iv}>{intervalLabel(iv)}</option>)}
        </select>
        {last ? <span className={`num ${tone}`}>{formatPrice(last.c, digits)}</span> : null}
        <span className="ws-spacer" />
        <button className="lt" title="main" onClick={onMain}><Icon name="ph-arrows-out" size="sm" /></button>
      </div>
      <div ref={host} className="lite-host" />
    </div>
  );
}
