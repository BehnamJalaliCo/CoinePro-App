// The old Pro-Chart's trading-session shading (engine/scales_crosshair.js sessionBands and
// paintSessions, ported): Sydney, Tokyo, London and New York as faint bands across the main pane
// on intraday charts, and their overlaps a shade stronger. Picked per session from the time bar.
import { useEffect, useRef } from 'react';
import { useChartBus } from '../chart/chartBus';
import { usePrefs } from '../state/prefs';
import { intervalSeconds } from '../data/intervals';

export interface SessionDef { id: string; fa: string; en: string; start: number; end: number; color: string; edge: string }
// Hours in UTC; an end before the start runs past midnight.
export const SESSIONS: SessionDef[] = [
  { id: 'sydney', fa: 'سیدنی', en: 'Sydney', start: 21, end: 6, color: 'rgba(236,72,153,0.08)', edge: 'rgba(236,72,153,0.4)' },
  { id: 'tokyo', fa: 'توکیو', en: 'Tokyo', start: 0, end: 9, color: 'rgba(99,102,241,0.08)', edge: 'rgba(99,102,241,0.4)' },
  { id: 'london', fa: 'لندن', en: 'London', start: 8, end: 17, color: 'rgba(34,197,94,0.08)', edge: 'rgba(34,197,94,0.4)' },
  { id: 'newyork', fa: 'نیویورک', en: 'New York', start: 13, end: 22, color: 'rgba(234,179,8,0.09)', edge: 'rgba(234,179,8,0.45)' },
];
const OVERLAP = { color: 'rgba(239,68,68,0.08)', edge: 'rgba(239,68,68,0.35)' };

export interface Band { id: string; from: number; to: number; color: string; edge: string }

/** The bands of the chosen sessions between two times (seconds), overlaps included when asked. */
export function sessionBands(from: number, to: number, ids: string[], overlaps: boolean): Band[] {
  if (!(to > from) || !ids.length) return [];
  const DAY = 86400;
  const pick = SESSIONS.filter((s) => ids.includes(s.id));
  const first = Math.floor(from / DAY) * DAY - DAY;
  const last = Math.floor(to / DAY) * DAY + DAY;
  const out: Band[] = [];
  const span = (s: SessionDef, day: number) => [day + s.start * 3600, day + (s.end > s.start ? s.end : s.end + 24) * 3600] as const;
  for (let day = first; day <= last; day += DAY) {
    for (const s of pick) {
      const [a, b] = span(s, day);
      if (Math.min(b, to) > Math.max(a, from)) out.push({ id: s.id, from: Math.max(a, from), to: Math.min(b, to), color: s.color, edge: s.edge });
    }
    if (!overlaps) continue;
    for (let i = 0; i < pick.length; i++) {
      for (let j = i + 1; j < pick.length; j++) {
        // Each pair against the next day's span too, for the sessions that run past midnight.
        for (const shift of [-DAY, 0, DAY]) {
          const [a1, b1] = span(pick[i]!, day);
          const [a2, b2] = span(pick[j]!, day + shift);
          const a = Math.max(a1, a2, from); const b = Math.min(b1, b2, to);
          if (b > a) out.push({ id: 'overlap', from: a, to: b, color: OVERLAP.color, edge: OVERLAP.edge });
        }
      }
    }
  }
  return out;
}

export function SessionShading() {
  const canvas = useRef<HTMLCanvasElement>(null);
  const api = useChartBus((b) => b.api);
  const bars = useChartBus((b) => b.bars);
  const interval = usePrefs((s) => s.interval);
  const ids = usePrefs((s) => s.shadedSessions);
  const overlaps = usePrefs((s) => s.sessionOverlaps);
  const on = ids.length > 0 && intervalSeconds(interval) < 86400;
  useEffect(() => {
    const el = canvas.current;
    if (!el || !api || !on) return;
    const paint = () => {
      const parent = el.parentElement;
      if (!parent) return;
      const dpr = window.devicePixelRatio || 1;
      const ts = api.timeScale();
      const pane = api.panes()[0];
      const w = ts.width() || parent.clientWidth;
      const h = pane ? pane.getHeight() : parent.clientHeight;
      el.style.width = `${w}px`; el.style.height = `${h}px`;
      el.width = Math.round(w * dpr); el.height = Math.round(h * dpr);
      const ctx = el.getContext('2d');
      if (!ctx) return;
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      ctx.clearRect(0, 0, w, h);
      const range = ts.getVisibleRange();
      if (!range) return;
      const step = intervalSeconds(usePrefs.getState().interval);
      const from = Number(range.from); const to = Number(range.to) + step;
      for (const b of sessionBands(from, to, ids, overlaps)) {
        // Times between bars: the bar the edge falls in.
        const x1 = coord(api, b.from, step); const x2 = coord(api, b.to, step);
        if (x1 == null || x2 == null) continue;
        const l = Math.max(0, Math.min(x1, x2)); const r = Math.min(w, Math.max(x1, x2));
        if (r <= l) continue;
        ctx.fillStyle = b.color;
        ctx.fillRect(l, 0, r - l, h);
        ctx.strokeStyle = b.edge; ctx.lineWidth = 1;
        ctx.beginPath(); ctx.moveTo(Math.round(l) + 0.5, 0); ctx.lineTo(Math.round(l) + 0.5, h); ctx.moveTo(Math.round(r) - 0.5, 0); ctx.lineTo(Math.round(r) - 0.5, h); ctx.stroke();
      }
    };
    paint();
    const ts = api.timeScale();
    ts.subscribeVisibleLogicalRangeChange(paint);
    const ro = new ResizeObserver(paint);
    if (el.parentElement) ro.observe(el.parentElement);
    return () => { try { ts.unsubscribeVisibleLogicalRangeChange(paint); } catch { /* chart gone */ } ro.disconnect(); };
  }, [api, bars, on, ids, overlaps]);
  if (!on) return null;
  return <canvas ref={canvas} className="session-shading" aria-hidden />;
}

/** The x of a time, between bars too: the bar it falls in, plus the fraction of the bar. */
function coord(api: NonNullable<ReturnType<typeof useChartBus.getState>['api']>, t: number, step: number): number | null {
  const ts = api.timeScale();
  const base = Math.floor(t / step) * step;
  const x = ts.timeToCoordinate(base as never);
  if (x == null) {
    const bars = useChartBus.getState().bars;
    const last = bars[bars.length - 1];
    if (!last || t <= last.t) return null;
    const xl = ts.timeToCoordinate(last.t as never);
    if (xl == null) return null;
    const lr = ts.getVisibleLogicalRange();
    const spacing = lr ? ts.width() / (lr.to - lr.from) : 6;
    return xl + ((t - last.t) / step) * spacing;
  }
  return x;
}
