import { memo, useEffect, useRef } from 'react';
import type { IChartApi } from 'lightweight-charts';
import type { Candle } from '../data/types';
import { zoneOf } from './time';

// TradingView's «Session breaks»: a faint vertical line where a new trading day starts, on intraday
// intervals, in the chart's own time zone, in the colour, width and style the settings choose.
// Drawn on a canvas inside the chart (below the drawings), so a snapshot carries it.
export const SessionBreaks = memo(function SessionBreaks({ api, bars, intraday, timezone, version, color, width = 1, style = 'dashed' }: { api: IChartApi | null; bars: Candle[]; intraday: boolean; timezone: string; version: number; color: string; width?: number; style?: 'solid' | 'dotted' | 'dashed' }) {
  const canvas = useRef<HTMLCanvasElement>(null);
  useEffect(() => {
    const el = canvas.current;
    if (!el || !api) return;
    let fmt: Intl.DateTimeFormat;
    try { fmt = new Intl.DateTimeFormat('en-CA', { timeZone: zoneOf(timezone), year: 'numeric', month: '2-digit', day: '2-digit' }); }
    catch { fmt = new Intl.DateTimeFormat('en-CA', { timeZone: 'UTC', year: 'numeric', month: '2-digit', day: '2-digit' }); }
    // The day each bar belongs to, computed once per set of bars.
    const starts: number[] = [];
    let prev = '';
    for (const b of bars) { const d = fmt.format(new Date(b.t * 1000)); if (prev && d !== prev) starts.push(b.t); prev = d; }
    const paint = () => {
      const parent = el.parentElement;
      if (!parent) return;
      const dpr = window.devicePixelRatio || 1;
      const w = parent.clientWidth; const pane = api.panes()[0];
      const h = pane ? pane.getHeight() : parent.clientHeight;
      el.style.width = `${w}px`; el.style.height = `${h}px`;
      el.width = Math.round(w * dpr); el.height = Math.round(h * dpr);
      const ctx = el.getContext('2d')!;
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      ctx.clearRect(0, 0, w, h);
      if (!intraday) return;
      const ts = api.timeScale();
      ctx.strokeStyle = color; ctx.lineWidth = width; ctx.setLineDash(style === 'solid' ? [] : style === 'dotted' ? [1, 3] : [4, 4]);
      const right = w - ts.width() > 0 ? ts.width() : w;
      for (const t of starts) {
        const x = ts.timeToCoordinate(t as never);
        if (x == null || x < 0 || x > right) continue;
        const px = Math.round(x) + (width % 2 ? 0.5 : 0);
        ctx.beginPath(); ctx.moveTo(px, 0); ctx.lineTo(px, h); ctx.stroke();
      }
    };
    paint();
    const ts = api.timeScale();
    ts.subscribeVisibleLogicalRangeChange(paint);
    const ro = new ResizeObserver(paint);
    if (el.parentElement) ro.observe(el.parentElement);
    return () => { ts.unsubscribeVisibleLogicalRangeChange(paint); ro.disconnect(); };
  }, [api, bars, intraday, timezone, version, color, width, style]);
  return <canvas ref={canvas} className="session-breaks" aria-hidden />;
});
