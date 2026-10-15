import { useEffect, useMemo, useRef } from 'react';
import { createPortal } from 'react-dom';
import { LineStyle, type IPriceLine, type ISeriesApi, type SeriesType } from 'lightweight-charts';
import { useChartBus } from '../chart/chartBus';
import { hitLine, roundTo, useTradeLines, type TradeLine } from '../chart/tradeLines';

// Draws every group of trading lines (chart/tradeLines.ts) on the chart bus's main series: the
// line and its axis price through lightweight-charts, the tag beside the scale as a small DOM box
// that follows the line every frame. Dragging works on the line itself (±5 px, ±10 px by touch) and
// on its tag; the chart does not pan while a line is held, and Escape puts it back.
export function TradeLinesHost() {
  const groups = useTradeLines((s) => s.groups);
  const api = useChartBus((s) => s.api);
  const series = useChartBus((s) => s.series);
  const lines = useMemo(() => Object.values(groups).flat(), [groups]);
  const host = (api?.chartElement().closest('.ws-chart') as HTMLElement | null | undefined) ?? null;
  const made = useRef(new Map<string, IPriceLine>());
  const owner = useRef<ISeriesApi<SeriesType> | null>(null);
  const tags = useRef(new Map<string, HTMLDivElement>());
  const linesRef = useRef<TradeLine[]>(lines);
  linesRef.current = lines;
  const drag = useRef<{ id: string; price: number; from: number; y0: number; moved: boolean } | null>(null);

  const precision = () => {
    const f = series?.options().priceFormat as { type?: string; precision?: number } | undefined;
    return f?.type === 'price' || f?.type === undefined ? f?.precision ?? 2 : 2;
  };
  const priceNow = (l: TradeLine) => (drag.current?.id === l.id ? drag.current.price : l.price);

  // The price lines themselves, kept in step with the groups.
  useEffect(() => {
    if (owner.current !== series) {
      for (const pl of made.current.values()) { try { owner.current?.removePriceLine(pl); } catch { /* series gone */ } }
      made.current.clear();
      owner.current = series;
    }
    if (!series) return;
    const keep = new Set<string>();
    for (const l of lines) {
      keep.add(l.id);
      const opts = { price: priceNow(l), color: l.color, lineWidth: 1 as const, lineStyle: l.dashed ? LineStyle.Dashed : LineStyle.Solid, axisLabelVisible: true, title: '', axisLabelColor: l.color, axisLabelTextColor: '#FFFFFF' };
      const pl = made.current.get(l.id);
      try { if (pl) pl.applyOptions(opts); else made.current.set(l.id, series.createPriceLine(opts)); } catch { /* series replaced mid-update */ }
    }
    for (const [id, pl] of made.current) if (!keep.has(id)) { try { series.removePriceLine(pl); } catch { /* gone */ } made.current.delete(id); }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [lines, series]);
  useEffect(() => () => { for (const pl of made.current.values()) { try { owner.current?.removePriceLine(pl); } catch { /* gone */ } } made.current.clear(); }, []);

  // Tags follow their lines through scrolls, zooms and scale changes.
  const any = lines.length > 0;
  useEffect(() => {
    if (!host || !api || !series || !any) return;
    let raf = 0;
    const tick = () => {
      const chartEl = api.chartElement();
      const off = chartEl.getBoundingClientRect().top - host.getBoundingClientRect().top;
      const right = (host.getBoundingClientRect().right - chartEl.getBoundingClientRect().right) + api.priceScale('right').width() + 6;
      const h = api.panes()[0]?.getHeight() ?? chartEl.clientHeight;
      for (const l of linesRef.current) {
        const el = tags.current.get(l.id);
        if (!el) continue;
        const y = series.priceToCoordinate(priceNow(l));
        if (y == null || y < 0 || y > h) { el.style.visibility = 'hidden'; continue; }
        el.style.visibility = '';
        el.style.right = `${Math.round(right)}px`;
        el.style.transform = `translateY(${Math.round(off + y - 10)}px)`;
      }
      raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [host, api, series, any]);

  // Dragging: caught on the way down (capture), before the chart starts a pan.
  useEffect(() => {
    if (!host || !api || !series || !any) return;
    const chartEl = api.chartElement();
    const yOf = (clientY: number) => clientY - chartEl.getBoundingClientRect().top;
    const ys = () => linesRef.current.map((l) => ({ y: series.priceToCoordinate(priceNow(l)), drag: Boolean(l.onMove) }));
    const overChart = (t: EventTarget | null) => t instanceof Node && (chartEl.contains(t) || (t as HTMLElement).classList?.contains('draw-layer'));
    const find = (target: EventTarget | null, clientY: number, slop: number): number => {
      const el = target as HTMLElement | null;
      if (el?.closest?.('.tl-x')) return -1;
      const tag = el?.closest?.('.tl-tag') as HTMLElement | null;
      if (tag) return linesRef.current.findIndex((l) => l.id === tag.dataset.id && Boolean(l.onMove));
      if (!overChart(target)) return -1;
      const y = yOf(clientY);
      if (y > (api.panes()[0]?.getHeight() ?? Infinity)) return -1;
      return hitLine(ys(), y, slop);
    };
    const apply = (price: number) => {
      const d = drag.current;
      if (!d) return;
      d.price = price;
      try { made.current.get(d.id)?.applyOptions({ price }); } catch { /* gone */ }
    };
    const moveTo = (clientY: number) => {
      const d = drag.current;
      if (!d) return;
      if (Math.abs(clientY - d.y0) > 2) d.moved = true;
      const p = series.coordinateToPrice(yOf(clientY));
      if (typeof p === 'number' && Number.isFinite(p) && p > 0) apply(roundTo(p, precision()));
    };
    const finish = (commit: boolean) => {
      const d = drag.current;
      if (!d) return;
      const line = linesRef.current.find((l) => l.id === d.id);
      host.classList.remove('tl-dragging');
      if (commit && d.moved && line?.onMove) { drag.current = null; line.onMove(d.price); }
      else { apply(d.from); drag.current = null; }
    };
    const begin = (i: number, clientY: number) => {
      const l = linesRef.current[i]!;
      drag.current = { id: l.id, price: l.price, from: l.price, y0: clientY, moved: false };
      host.classList.add('tl-dragging');
    };

    const down = (e: MouseEvent) => {
      if (e.button !== 0) return;
      const i = find(e.target, e.clientY, 5);
      if (i < 0) return;
      e.preventDefault(); e.stopPropagation();
      begin(i, e.clientY);
      const mv = (ev: MouseEvent) => moveTo(ev.clientY);
      const up = () => { window.removeEventListener('mousemove', mv, true); window.removeEventListener('mouseup', up, true); finish(true); };
      window.addEventListener('mousemove', mv, true);
      window.addEventListener('mouseup', up, true);
    };
    const touch = (e: TouchEvent) => {
      const t = e.touches[0];
      if (!t || e.touches.length > 1) return;
      const i = find(e.target, t.clientY, 10);
      if (i < 0) return;
      e.preventDefault(); e.stopPropagation();
      begin(i, t.clientY);
      const mv = (ev: TouchEvent) => { const p = ev.touches[0]; if (p) { ev.preventDefault(); moveTo(p.clientY); } };
      const end = () => { window.removeEventListener('touchmove', mv, true); window.removeEventListener('touchend', end, true); window.removeEventListener('touchcancel', end, true); finish(true); };
      window.addEventListener('touchmove', mv, { capture: true, passive: false });
      window.addEventListener('touchend', end, true);
      window.addEventListener('touchcancel', end, true);
    };
    const hover = (e: MouseEvent) => {
      if (drag.current || e.buttons) return;
      host.classList.toggle('tl-hover', find(e.target, e.clientY, 5) >= 0 && !(e.target as HTMLElement).closest?.('.tl-tag'));
    };
    const key = (e: KeyboardEvent) => { if (e.key === 'Escape' && drag.current) { e.stopPropagation(); finish(false); } };
    host.addEventListener('mousedown', down, true);
    host.addEventListener('touchstart', touch, { capture: true, passive: false });
    host.addEventListener('mousemove', hover, true);
    window.addEventListener('keydown', key, true);
    return () => {
      host.removeEventListener('mousedown', down, true);
      host.removeEventListener('touchstart', touch, true);
      host.removeEventListener('mousemove', hover, true);
      window.removeEventListener('keydown', key, true);
      host.classList.remove('tl-hover', 'tl-dragging');
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [host, api, series, any]);

  if (!host || !lines.length) return null;
  return createPortal(
    <div className="tl-layer" dir="ltr">
      {lines.map((l) => (
        <div key={l.id} data-id={l.id} className={`tl-tag${l.onMove ? ' movable' : ''}`} style={{ ['--tl' as string]: l.color }}
          ref={(el) => { if (el) tags.current.set(l.id, el); else tags.current.delete(l.id); }}>
          <span className="tl-label" dir="auto">{l.label}</span>
          {l.value ? <span className="tl-val num" style={l.valueColor ? { color: l.valueColor } : undefined}>{l.value}</span> : null}
          {l.onClose ? <button className="tl-x" title={l.closeTitle} aria-label={l.closeTitle} onClick={(e) => { e.stopPropagation(); l.onClose?.(); }}>×</button> : null}
        </div>
      ))}
    </div>,
    host,
  );
}
