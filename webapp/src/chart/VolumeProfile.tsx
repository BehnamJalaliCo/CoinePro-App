// TradingView's «Volume profile visible range» (VPVR): the volume traded at each price over the
// bars on screen, as rows growing from the right edge of the pane — buying and selling volume in
// their colours, the value area (70% of the volume around the busiest price) stronger than the
// rest, and the point of control (the busiest row) as a line across. Recomputed as the reader
// scrolls; drawn on a canvas inside the chart so a snapshot carries it.
import { memo, useEffect, useRef } from 'react';
import type { IChartApi, ISeriesApi, SeriesType } from 'lightweight-charts';
import type { Candle } from '../data/types';
import { computeProfile } from '../engine/volumeProfile.js';
import { tint } from './chartTypes';

export const VolumeProfile = memo(function VolumeProfile({ api, series, bars, version, rows, valueArea, up, down, dark }: { api: IChartApi | null; series: ISeriesApi<SeriesType> | null; bars: Candle[]; version: number; rows: number; valueArea: number; up: string; down: string; dark: boolean }) {
  const canvas = useRef<HTMLCanvasElement>(null);
  useEffect(() => {
    const el = canvas.current;
    if (!el || !api || !series) return;
    let raf = 0;
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
      const r = api.timeScale().getVisibleLogicalRange();
      if (!r) return;
      const vis = bars.slice(Math.max(0, Math.floor(r.from)), Math.min(bars.length, Math.ceil(r.to) + 1));
      const prof = computeProfile(vis, Math.max(6, Math.min(120, Math.round(rows))), Math.max(0.1, Math.min(0.95, valueArea / 100)));
      if (!prof) { el.dataset.rows = '0'; return; }
      const left = api.priceScale('left').width();
      const right = left + api.timeScale().width();
      const max = Math.max(...prof.map((b) => b.vol)) || 1;
      const span = (right - left) * 0.3;
      let poc: { y: number } | null = null;
      for (const b of prof) {
        const y1 = series.priceToCoordinate(b.hi); const y2 = series.priceToCoordinate(b.lo);
        if (y1 == null || y2 == null) continue;
        const top = Math.min(y1, y2) + 0.5; const hh = Math.max(1, Math.abs(y2 - y1) - 1);
        const len = (b.vol / max) * span;
        const upLen = b.vol ? (b.up / b.vol) * len : 0;
        const a = b.va ? 0.34 : 0.14;
        ctx.fillStyle = tint(up, a);
        ctx.fillRect(right - len, top, upLen, hh);
        ctx.fillStyle = tint(down, a);
        ctx.fillRect(right - len + upLen, top, len - upLen, hh);
        if (b.poc) poc = { y: (y1 + y2) / 2 };
      }
      if (poc) {
        ctx.strokeStyle = dark ? '#FFB74D' : '#F57C00';
        ctx.lineWidth = 1.5; ctx.setLineDash([]);
        ctx.beginPath(); ctx.moveTo(left, Math.round(poc.y) + 0.5); ctx.lineTo(right, Math.round(poc.y) + 0.5); ctx.stroke();
      }
      el.dataset.rows = String(prof.length);
    };
    const later = () => { cancelAnimationFrame(raf); raf = requestAnimationFrame(paint); };
    paint();
    const ts = api.timeScale();
    ts.subscribeVisibleLogicalRangeChange(later);
    const ro = new ResizeObserver(later);
    if (el.parentElement) ro.observe(el.parentElement);
    const id = setInterval(later, 1500);
    return () => { cancelAnimationFrame(raf); clearInterval(id); ts.unsubscribeVisibleLogicalRangeChange(later); ro.disconnect(); };
  }, [api, series, bars, version, rows, valueArea, up, down, dark]);
  return <canvas ref={canvas} className="volume-profile" aria-hidden />;
});
