// The signal layer on the chart (OLD chart/core SignalMarkers.kt, feature/chart ChartSignals.kt
// markersFor): each study's six newest signals as triangles under the low for a buy and over the high
// for a sell, in the study's own colour, sized by how far past the line the signal went; «خرید» /
// «فروش» under them where there is room (12px a bar), bare triangles below that, one mark per ten
// bars (the strongest) once a triangle is wider than its candle. Per study: labels, triangles or
// off (the Explain sheet). Also the host that reads the layer whenever the studies or the bars
// change, and the foot under the plot. Drawn on a canvas inside the chart, so a snapshot carries it.
import { useEffect, useMemo, useRef, type RefObject } from 'react';
import type { IChartApi, ISeriesApi, SeriesType } from 'lightweight-charts';
import type { Candle } from '../data/types';
import { usePrefs } from '../state/prefs';
import { useStudies, VOLUME } from '../state/studies';
import { useScript } from '../state/script';
import { useChartSettings } from '../state/chartSettings';
import { exprLegs } from '../data/symbols';
import { planFor } from '../data/candles';
import { useChartBus } from './chartBus';
import { entryOf } from './indicatorCatalog';
import { evaluateLayer, ownTimeframe, registryColour, useSignals } from './signalStore';
import { SignalFoot } from './SignalStrip';
import { ExplainSheetHost } from './ExplainSheet';
import { AUTO_CLAIMS, chartExclusions, detailFor, markersFor, repaintOf, sizeFor, thinMarks, TRIANGLE_SPACING, type MarkerStyle } from './signals';
import './signals.css';

/** How often a live bar may re-read the studies. */
const LIVE_THROTTLE_MS = 1000;

export function SignalHost({ api, series, bars, version, lastClose, digits, replaying, wrap }: {
  api: IChartApi | null; series: ISeriesApi<SeriesType> | null; bars: Candle[]; version: number; lastClose: number | null; digits: number; replaying: boolean; wrap: RefObject<HTMLDivElement | null>;
}) {
  const studies = useStudies((s) => s.list);
  const lang = usePrefs((s) => s.lang);
  const symbol = usePrefs((s) => s.symbol);
  const interval = usePrefs((s) => s.interval);
  const horizon = useSignals((s) => s.horizon);
  const script = useScript((s) => s.result);
  const cs = useChartSettings();
  const english = lang !== 'fa';

  // Read the layer: at once when the studies, the horizon, the language or the bars change; at most
  // once a second while the forming bar ticks.
  const latest = useRef({ studies, bars, english, horizon, script });
  latest.current = { studies, bars, english, horizon, script };
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const lastRun = useRef(0);
  const run = () => {
    timer.current = null;
    lastRun.current = Date.now();
    const l = latest.current;
    const list = l.studies.filter((s) => s.key !== VOLUME);
    useSignals.getState().setLayer(evaluateLayer(list, l.bars.slice(), { english: l.english, horizon: l.horizon, script: l.script }));
  };
  useEffect(() => {
    if (timer.current) clearTimeout(timer.current);
    timer.current = setTimeout(run, 0);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [studies, version, english, horizon, script, bars.length, bars[0]?.t, bars[bars.length - 1]?.t]);
  useEffect(() => {
    if (timer.current) return;
    timer.current = setTimeout(run, Math.max(0, LIVE_THROTTLE_MS - (Date.now() - lastRun.current)));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [lastClose]);
  useEffect(() => () => { if (timer.current) clearTimeout(timer.current); useSignals.getState().setLayer({ ...useSignals.getState().layer, reads: [], confidence: {}, bars: [] }); }, []);

  // What the picture is not showing.
  const exclusions = useMemo(() => {
    const plan = exprLegs(symbol) ? null : planFor(symbol, interval);
    const repainting: string[] = [];
    for (const st of studies) {
      if (st.key === VOLUME) continue;
      if (repaintOf(st.key, { ownTimeframe: ownTimeframe(st, interval), waitClose: st.waitClose }) === 'repaints') {
        const e = entryOf(st.key);
        repainting.push(e ? (english ? e.en : e.fa) : st.key);
      }
    }
    if (cs.autoHarmonic && AUTO_CLAIMS.harmonic === 'repaints') repainting.push(english ? 'Harmonic patterns' : 'الگوهای هارمونیک');
    return chartExclusions({ foldedFrom: plan && plan.factor > 1 ? plan.tf : null, bars, replaying, repainting: [...new Set(repainting)] });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [symbol, interval, studies, english, replaying, cs.autoHarmonic, bars.length, version]);

  useEffect(() => { useSignals.getState().setExclusions(exclusions); }, [exclusions]);
  useEffect(() => () => useSignals.getState().setExclusions([]), []);

  return (
    <>
      <SignalMarks api={api} series={series} />
      <SignalFoot wrap={wrap} />
      <ExplainSheetHost digits={digits} />
    </>
  );
}

function SignalMarks({ api, series }: { api: IChartApi | null; series: ISeriesApi<SeriesType> | null }) {
  const canvas = useRef<HTMLCanvasElement>(null);
  const layer = useSignals((s) => s.layer);
  const styles = useSignals((s) => s.markerStyles);
  const studies = useStudies((s) => s.list);
  const rows = useChartBus((b) => b.studyRows);

  const marks = useMemo(() => {
    const hidden = new Set(studies.filter((s) => s.hidden).map((s) => s.uid));
    const byUid: Record<string, MarkerStyle> = {};
    const colours: Record<string, string> = {};
    for (const r of layer.reads) {
      const key = layer.keys[r.id] ?? '';
      if (styles[key]) byUid[r.id] = styles[key]!;
      const row = rows.find((x) => x.uid === r.id);
      const c = row?.colors.find(Boolean) ?? registryColour(key);
      if (c && key !== '__script') colours[r.id] = c;
    }
    return markersFor(layer.reads, layer.bars, { hidden, styles: byUid, colours, english: layer.english });
  }, [layer, styles, studies, rows]);

  useEffect(() => {
    const el = canvas.current;
    if (!el || !api || !series) return;
    const paint = () => {
      const parent = el.parentElement;
      if (!parent) return;
      const pane = api.panes()[0];
      const h = pane ? pane.getHeight() : 0;
      const w = api.timeScale().width();
      const dpr = window.devicePixelRatio || 1;
      el.style.width = `${w}px`; el.style.height = `${h}px`;
      el.width = Math.round(w * dpr); el.height = Math.round(h * dpr);
      const ctx = el.getContext('2d')!;
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      ctx.clearRect(0, 0, w, h);
      const spacing = api.timeScale().options().barSpacing;
      const visible = marks.filter((m) => { const x = api.timeScale().timeToCoordinate(m.time as never); return x != null && x >= 0 && x <= w; });
      const drawn = spacing < TRIANGLE_SPACING ? thinMarks(visible) : visible;
      let labels = 0;
      ctx.font = "11px 'IRANYekanX', system-ui, sans-serif";
      ctx.textAlign = 'center';
      for (const m of drawn) {
        const x = api.timeScale().timeToCoordinate(m.time as never) as number | null;
        const y = series.priceToCoordinate(m.price) as number | null;
        if (x == null || y == null) continue;
        const size = sizeFor(m.strength);
        const gap = 4;
        const dir = m.above ? -1 : 1;
        const tip = y + dir * gap;
        const base = tip + dir * size;
        ctx.fillStyle = m.colour;
        ctx.beginPath();
        ctx.moveTo(x, tip); ctx.lineTo(x - size / 2, base); ctx.lineTo(x + size / 2, base); ctx.closePath();
        ctx.fill();
        if (m.label && detailFor(spacing, 'labels') === 'label') {
          ctx.textBaseline = m.above ? 'bottom' : 'top';
          ctx.fillText(m.label, x, base + dir * 2);
          labels++;
        }
      }
      el.dataset.drawn = String(drawn.length);
      el.dataset.labels = String(labels);
      el.dataset.detail = detailFor(spacing);
    };
    paint();
    const ts = api.timeScale();
    ts.subscribeVisibleLogicalRangeChange(paint);
    api.subscribeCrosshairMove(paint);
    const ro = new ResizeObserver(paint);
    if (el.parentElement) ro.observe(el.parentElement);
    return () => { ts.unsubscribeVisibleLogicalRangeChange(paint); api.unsubscribeCrosshairMove(paint); ro.disconnect(); };
  }, [api, series, marks]);

  return <canvas ref={canvas} className="signal-marks" aria-hidden data-marks={marks.length} data-labelled={marks.filter((m) => m.label).length} />;
}
