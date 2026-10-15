import { useEffect, useMemo, useRef } from 'react';
import type { IChartApi, ISeriesApi, SeriesType } from 'lightweight-charts';
import type { Candle } from '../data/types';
import { detectDivergences } from '../engine/divergence.js';
import { detectHarmonics } from '../engine/harmonic.js';
import { detectGaps } from '../engine/gaps.js';

// The old Pro-Chart's automatic reading, on the chart: RSI divergences (regular and hidden) as a
// line between the two pivots, harmonic patterns (Gartley, Bat, Butterfly, Crab) as their XABCD
// legs, and price gaps as the zone left open. Recomputed with the bars; drawn on its own canvas.
export interface AutoFlags { divergence: boolean; harmonic: boolean; gaps: boolean }

const EN: [RegExp, string][] = [
  [/واگرایی صعودی مخفی/g, 'Hidden bullish divergence'], [/واگرایی نزولی مخفی/g, 'Hidden bearish divergence'],
  [/واگرایی صعودی/g, 'Bullish divergence'], [/واگرایی نزولی/g, 'Bearish divergence'],
  [/گارتلی/g, 'Gartley'], [/خفاش \(Bat\)/g, 'Bat'], [/پروانه \(Butterfly\)/g, 'Butterfly'], [/خرچنگ \(Crab\)/g, 'Crab'],
];
/** A gap worth marking: at least 0.1%, and at least half the median bar's range (quiet markets and
 *  folded intervals open a hair away from the last close all the time). */
function gapThreshold(bars: Candle[]): number {
  const r = bars.slice(-200).map((b) => (b.c ? ((b.h - b.l) / b.c) * 100 : 0)).filter((x) => x > 0).sort((a, b) => a - b);
  const med = r.length ? r[Math.floor(r.length / 2)]! : 0;
  return Math.max(0.1, med * 0.5);
}

/** The gaps price has not gone back into yet, the newest twelve: the ones a trader still watches. */
function openGaps(bars: Candle[], gaps: { t: number; dir: 'up' | 'down'; pct: number }[]) {
  const idx = new Map(bars.map((b, i) => [b.t, i]));
  const open: typeof gaps = [];
  for (const g of gaps) {
    const i = idx.get(g.t);
    if (i == null || i < 1) continue;
    const edge = bars[i - 1]!.c;
    let filled = false;
    for (let j = i; j < bars.length && !filled; j++) filled = g.dir === 'up' ? bars[j]!.l <= edge : bars[j]!.h >= edge;
    if (!filled) open.push(g);
  }
  return open.slice(-12);
}

const english = (s: string) => EN.reduce((x, [re, to]) => x.replace(re, to), s);

export function AutoOverlay({ api, series, bars, version, flags, fa }: { api: IChartApi | null; series: ISeriesApi<SeriesType> | null; bars: Candle[]; version: number; flags: AutoFlags; fa: boolean }) {
  const canvas = useRef<HTMLCanvasElement>(null);
  const found = useMemo(() => ({
    div: flags.divergence ? detectDivergences(bars) : [],
    harm: flags.harmonic ? detectHarmonics(bars) : [],
    gaps: flags.gaps ? openGaps(bars, detectGaps(bars, gapThreshold(bars))) : [],
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }), [bars, version, flags.divergence, flags.harmonic, flags.gaps]);

  useEffect(() => {
    const el = canvas.current;
    if (!el || !api || !series) return;
    const byT = new Map(bars.map((b, i) => [b.t, i]));
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
      const ts = api.timeScale();
      const xy = (t: number, p: number) => { const x = ts.timeToCoordinate(t as never); const y = series.priceToCoordinate(p); return x == null || y == null ? null : { x: x as number, y: y as number }; };
      ctx.font = "11px 'IRANYekanX', sans-serif";
      const label = (text: string, x: number, y: number, color: string, below: boolean) => {
        const s = fa ? text : english(text);
        const tw = ctx.measureText(s).width + 8;
        const by = below ? y + 6 : y - 22;
        ctx.fillStyle = color; ctx.globalAlpha = 0.9; ctx.fillRect(x - tw / 2, by, tw, 16); ctx.globalAlpha = 1;
        ctx.fillStyle = '#fff'; ctx.textBaseline = 'middle'; ctx.textAlign = 'center'; ctx.fillText(s, x, by + 8);
      };
      // Gaps still open: the zone from the gap bar to the newest bar.
      for (const g of found.gaps) {
        const i = byT.get(g.t); if (i == null || i < 1) continue;
        const prev = bars[i - 1]!; const cur = bars[i]!;
        const a = xy(cur.t, prev.c); const b = xy(bars[bars.length - 1]!.t, cur.o);
        if (!a || !b) continue;
        ctx.fillStyle = g.dir === 'up' ? 'rgba(8,153,129,0.14)' : 'rgba(242,54,69,0.14)';
        ctx.fillRect(a.x, Math.min(a.y, b.y), Math.max(4, b.x - a.x), Math.abs(b.y - a.y) || 2);
      }
      // Divergences: the line between the two price pivots.
      for (const d of found.div) {
        const pts = d.points.map((p) => xy(p.t, p.price)).filter((p): p is { x: number; y: number } => Boolean(p));
        if (pts.length < 2) continue;
        const color = d.dir === 'bull' ? '#089981' : d.dir === 'bear' ? '#F23645' : '#787B86';
        ctx.strokeStyle = color; ctx.lineWidth = 2; ctx.setLineDash([]);
        ctx.beginPath(); ctx.moveTo(pts[0]!.x, pts[0]!.y); ctx.lineTo(pts[1]!.x, pts[1]!.y); ctx.stroke();
        label(d.label, pts[1]!.x, pts[1]!.y, color, d.dir === 'bull');
      }
      // Harmonics: the XABCD legs, D labelled.
      for (const hp of found.harm) {
        const pts = hp.points.map((p) => xy(p.t, p.price)).filter((p): p is { x: number; y: number } => Boolean(p));
        if (pts.length < 5) continue;
        const color = hp.dir === 'bull' ? '#2962FF' : '#9C27B0';
        ctx.strokeStyle = color; ctx.lineWidth = 1.5; ctx.setLineDash([]);
        ctx.beginPath(); pts.forEach((p, k) => (k ? ctx.lineTo(p.x, p.y) : ctx.moveTo(p.x, p.y))); ctx.stroke();
        ctx.fillStyle = color; ctx.globalAlpha = 0.08;
        ctx.beginPath(); ctx.moveTo(pts[0]!.x, pts[0]!.y); ctx.lineTo(pts[1]!.x, pts[1]!.y); ctx.lineTo(pts[2]!.x, pts[2]!.y); ctx.closePath(); ctx.fill();
        ctx.beginPath(); ctx.moveTo(pts[2]!.x, pts[2]!.y); ctx.lineTo(pts[3]!.x, pts[3]!.y); ctx.lineTo(pts[4]!.x, pts[4]!.y); ctx.closePath(); ctx.fill();
        ctx.globalAlpha = 1;
        ['X', 'A', 'B', 'C', 'D'].forEach((n, k) => { ctx.fillStyle = color; ctx.textAlign = 'center'; ctx.fillText(n, pts[k]!.x, pts[k]!.y + (k % 2 ? -8 : 12)); });
        label(hp.label, pts[4]!.x, pts[4]!.y, color, hp.dir === 'bull');
      }
    };
    paint();
    const ts = api.timeScale();
    ts.subscribeVisibleLogicalRangeChange(paint);
    api.subscribeCrosshairMove(paint);
    const ro = new ResizeObserver(paint);
    if (el.parentElement) ro.observe(el.parentElement);
    return () => { ts.unsubscribeVisibleLogicalRangeChange(paint); api.unsubscribeCrosshairMove(paint); ro.disconnect(); };
  }, [api, series, bars, found, fa]);

  return <canvas ref={canvas} className="auto-overlay" aria-hidden data-found={found.div.length + found.harm.length + found.gaps.length} />;
}
