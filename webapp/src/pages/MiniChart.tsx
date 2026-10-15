import { useEffect, useRef } from 'react';
import { CandlestickSeries, ColorType, LineStyle, createChart, type UTCTimestamp } from 'lightweight-charts';
import { isDark, usePrefs } from '../state/prefs';
import { useChartSettings } from '../state/chartSettings';
import { loadHistory } from '../data/candles';
import { digitsOf } from '../state/market';
import { timeOptions } from '../chart/time';

// A small chart for a page: the symbol's recent bars and, when given, a setup's levels as lines.
export function MiniChart({ symbol, interval = '60', levels = [], height = 260 }: { symbol: string; interval?: string; levels?: { price: number; color: string; title: string }[]; height?: number }) {
  const host = useRef<HTMLDivElement>(null);
  const theme = usePrefs((s) => s.theme);
  const timezone = usePrefs((s) => s.timezone);
  const dark = isDark(theme);
  const { up, down } = useChartSettings();
  const key = levels.map((l) => `${l.title}${l.price}`).join('|');
  useEffect(() => {
    if (!host.current) return;
    const c = createChart(host.current, {
      autoSize: true,
      layout: { background: { type: ColorType.Solid, color: 'transparent' }, textColor: dark ? '#B8B8B8' : '#0F0F0F', fontSize: 11, fontFamily: "'IRANYekanX', system-ui, sans-serif", attributionLogo: false },
      grid: { vertLines: { visible: false }, horzLines: { color: dark ? '#2A2A2A' : '#F0F3FA' } },
      rightPriceScale: { borderVisible: false }, timeScale: { borderVisible: false, timeVisible: true },
      handleScroll: false, handleScale: false,
      ...timeOptions(timezone, interval),
    });
    const digits = digitsOf(symbol);
    const s = c.addSeries(CandlestickSeries, { upColor: up, downColor: down, wickUpColor: up, wickDownColor: down, borderVisible: false, priceFormat: { type: 'price', precision: digits, minMove: 1 / 10 ** digits } });
    for (const l of levels) s.createPriceLine({ price: l.price, color: l.color, lineWidth: 1, lineStyle: LineStyle.Dashed, axisLabelVisible: true, title: l.title });
    let alive = true;
    loadHistory(symbol, interval, undefined, 'background').then(({ bars }) => {
      if (!alive) return;
      s.setData(bars.slice(-150).map((b) => ({ time: b.t as UTCTimestamp, open: b.o, high: b.h, low: b.l, close: b.c })));
      c.timeScale().fitContent();
    }).catch(() => undefined);
    return () => { alive = false; c.remove(); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [symbol, interval, dark, up, down, key, timezone]);
  return <div ref={host} className="mini-chart" style={{ height }} />;
}
