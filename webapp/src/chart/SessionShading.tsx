// The forex trading sessions shaded behind the bars (the old Pro-Chart's «sessions»; TradingView's
// «Trading sessions» indicator): Sydney, Tokyo, London and New York, each in its colour, with the
// London–New York and Sydney–Tokyo overlaps deeper, and each session's name at its start. Only
// on intraday intervals; drawn on a canvas inside the chart so a snapshot carries it.
import { memo, useEffect, useRef } from 'react';
import type { IChartApi } from 'lightweight-charts';
import type { Candle } from '../data/types';
import { sessionBands } from '../engine/scales_crosshair.js';
import { intervalSeconds } from '../data/intervals';
import { usePrefs } from '../state/prefs';
import type { SessionId } from '../state/chartSettings';
import { sessionName } from '../shell/SettingsDialog';

/** The x of any time, between bars too: the bar it falls in plus the fraction of the bar elapsed. */
export function xOfTime(api: IChartApi, bars: Candle[], t: number, step: number): number | null {
  if (!bars.length) return null;
  let lo = 0; let hi = bars.length - 1; let at = -1;
  while (lo <= hi) { const mid = (lo + hi) >> 1; if (bars[mid]!.t <= t) { at = mid; lo = mid + 1; } else hi = mid - 1; }
  const logical = at < 0 ? (t - bars[0]!.t) / step : at + Math.min(1, (t - bars[at]!.t) / step);
  const x = api.timeScale().logicalToCoordinate((logical - 0.5) as never);
  return x == null ? null : (x as number);
}

export const SessionShading = memo(function SessionShading({ api, bars, sessions, overlaps, version, fa, dark }: { api: IChartApi | null; bars: Candle[]; sessions: SessionId[]; overlaps: boolean; version: number; fa: boolean; dark: boolean }) {
  const canvas = useRef<HTMLCanvasElement>(null);
  const interval = usePrefs((s) => s.interval);
  const key = sessions.join(',');
  useEffect(() => {
    const el = canvas.current;
    if (!el || !api) return;
    const step = intervalSeconds(interval);
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
      const range = api.timeScale().getVisibleLogicalRange();
      if (!range || !bars.length || !sessions.length) return;
      const from = bars[Math.max(0, Math.floor(range.from))]?.t ?? bars[0]!.t;
      const lastIdx = bars.length - 1;
      const to = (bars[Math.min(lastIdx, Math.ceil(range.to))]?.t ?? bars[lastIdx]!.t) + step * Math.max(1, Math.ceil(range.to) - lastIdx);
      const bands = sessionBands(from, to, 'UTC', { sessions, overlap: overlaps });
      const right = api.timeScale().width();
      ctx.font = "11px 'IRANYekanX', sans-serif";
      ctx.textBaseline = 'top';
      for (const b of bands) {
        const x1 = xOfTime(api, bars, b.from, step); const x2 = xOfTime(api, bars, b.to, step);
        if (x1 == null || x2 == null || x2 < 0 || x1 > right) continue;
        const a = Math.max(0, x1); const z = Math.min(right, x2);
        ctx.fillStyle = dark ? b.color.replace(/0\.1\)$/, '0.08)') : b.color;
        ctx.fillRect(a, 0, Math.max(1, z - a), h);
        if (b.id === 'overlap') continue;
        ctx.fillStyle = b.edge;
        ctx.fillRect(a, 0, Math.max(1, z - a), 2);
        if (z - a > 40 && x1 >= 0) {
          ctx.fillStyle = b.edge.replace(/0\.45\)$/, '0.9)');
          ctx.textAlign = 'left';
          ctx.fillText(sessionName(b.id as SessionId, fa), a + 4, 4);
        }
      }
    };
    paint();
    const ts = api.timeScale();
    ts.subscribeVisibleLogicalRangeChange(paint);
    const ro = new ResizeObserver(paint);
    if (el.parentElement) ro.observe(el.parentElement);
    return () => { ts.unsubscribeVisibleLogicalRangeChange(paint); ro.disconnect(); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [api, bars, key, overlaps, version, fa, dark, interval]);
  return <canvas ref={canvas} className="session-shading" aria-hidden data-sessions={key} />;
});
