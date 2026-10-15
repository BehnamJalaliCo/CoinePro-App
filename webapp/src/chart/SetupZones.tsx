// A trade setup (a signal from the platforms, the AI's reading of the chart) as TradingView draws a
// long or short position: from the setup's bar forward, a green box from the entry to each target
// and a red box from the entry to the stop, with the risk-to-reward written on it. The lines with
// their axis labels are PriceLines'; this is the zones, on a canvas inside the chart.
import { memo, useEffect, useRef } from 'react';
import type { IChartApi, ISeriesApi, SeriesType } from 'lightweight-charts';
import type { Candle } from '../data/types';
import { useOverlay } from '../state/overlay';
import { usePrefs } from '../state/prefs';
import { barIndexAt } from './EventMarks';

export const SetupZones = memo(function SetupZones({ api, series, version, bars }: { api: IChartApi | null; series: ISeriesApi<SeriesType> | null; version: number; bars: Candle[] }) {
  const canvas = useRef<HTMLCanvasElement>(null);
  const setup = useOverlay((o) => o.setup);
  const symbol = usePrefs((s) => s.symbol);
  const fa = usePrefs((s) => s.lang) === 'fa';
  const on = Boolean(setup && setup.symbol === symbol && setup.entry != null && (setup.sl != null || setup.targets.length));
  useEffect(() => {
    const el = canvas.current;
    if (!el || !api || !series) return;
    const paint = () => {
      const parent = el.parentElement;
      if (!parent) return;
      const dpr = window.devicePixelRatio || 1;
      const pane = api.panes()[0];
      const w = parent.clientWidth; const h = pane ? pane.getHeight() : parent.clientHeight;
      el.style.width = `${w}px`; el.style.height = `${h}px`;
      el.width = Math.round(w * dpr); el.height = Math.round(h * dpr);
      const ctx = el.getContext('2d')!;
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      ctx.clearRect(0, 0, w, h);
      if (!on || !setup || setup.entry == null || !bars.length) return;
      const ts = api.timeScale();
      const startIdx = setup.time ? Math.max(0, barIndexAt(bars, Math.floor(setup.time > 1e12 ? setup.time / 1000 : setup.time))) : bars.length - 1;
      const x0 = ts.logicalToCoordinate(startIdx as never);
      const x1 = ts.logicalToCoordinate((Math.max(startIdx, bars.length - 1) + 24) as never);
      const yE = series.priceToCoordinate(setup.entry);
      if (x0 == null || x1 == null || yE == null) return;
      const xa = Math.max(0, x0 as number); const xb = Math.min(ts.width(), x1 as number);
      if (xb <= xa) return;
      const box = (p: number | null, color: string) => {
        if (p == null) return;
        const y = series.priceToCoordinate(p);
        if (y == null) return;
        ctx.fillStyle = color;
        ctx.fillRect(xa, Math.min(y, yE), xb - xa, Math.abs(y - yE));
      };
      box(setup.sl, 'rgba(242,54,69,0.14)');
      const far = setup.targets.length ? setup.targets[setup.targets.length - 1]! : null;
      box(far, 'rgba(8,153,129,0.14)');
      const near = setup.targets[0];
      if (near != null && setup.sl != null) {
        const rr = Math.abs(near - setup.entry) / Math.abs(setup.entry - setup.sl || 1);
        ctx.font = "600 11px 'IRANYekanX', sans-serif";
        ctx.textAlign = 'left'; ctx.textBaseline = 'middle';
        const text = `${fa ? 'ریسک به ریوارد' : 'R:R'} ${rr.toFixed(2)}`;
        const tw = ctx.measureText(text).width + 10;
        ctx.fillStyle = setup.direction === 'long' ? '#089981' : '#F23645';
        ctx.fillRect(xa + 6, (yE as number) - 9, tw, 18);
        ctx.fillStyle = '#FFFFFF';
        ctx.fillText(text, xa + 11, yE as number);
      }
    };
    paint();
    const ts = api.timeScale();
    ts.subscribeVisibleLogicalRangeChange(paint);
    api.subscribeCrosshairMove(paint);
    const ro = new ResizeObserver(paint);
    if (el.parentElement) ro.observe(el.parentElement);
    return () => { ts.unsubscribeVisibleLogicalRangeChange(paint); api.unsubscribeCrosshairMove(paint); ro.disconnect(); };
  }, [api, series, version, bars, setup, on, fa]);
  return <canvas ref={canvas} className="setup-zones" aria-hidden data-on={on ? '1' : '0'} />;
});
