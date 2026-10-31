// Economic events and the latest news on the chart, in a lane at the foot of the price pane just
// above the dates (OLD CoineProChart.kt drawEventMarks): a story is TradingView's purple lightning
// bolt, a calendar release a disc in its importance colour behind a small white calendar; where a
// group holds both, the bolt stands one glyph above the release in the same column. Marks closer
// than a glyph's width fold into one group drawn as a stack, so no two glyphs ever touch. A click
// opens the group's every event, earliest first; a hover shows them; the legend lists the bar's
// events while the crosshair is on it. Under each unusually large bar a small dot opens «چرا این
// حرکت؟». Drawn on a canvas inside the chart, so a snapshot carries them.
import { useEffect, useMemo, useRef, useState } from 'react';
import { create } from 'zustand';
import type { IChartApi, ISeriesApi, MouseEventParams, SeriesType, Time } from 'lightweight-charts';
import type { Candle } from '../data/types';
import { calendarWeek, cryptoNews, forexNews, type CalendarEvent, type NewsItem } from '../data/intel';
import { marketOf } from '../data/symbols';
import { intervalSeconds } from '../data/intervals';
import { isDark, usePrefs } from '../state/prefs';
import { useChartSettings } from '../state/chartSettings';
import { isCursorMode, useDrawing } from '../state/drawing';
import { useReplay } from '../state/replay';
import { clockText, dateText } from './time';
import {
  DOT, GLYPH, IMPORTANCE_COLOR, MOUSE_RADIUS, NEWS_PURPLE, REACH, STACK, TOUCH_RADIUS,
  drawnAt, groupItems, hasNews, isCluster, laneCentre, laneGroups, laneReach, markImportance, markKind, nearest, newsBelongs, notableBars, notableReading,
  type LaneItem, type LaneMark, type NotableReading, type Placed,
} from './eventLane';
import { EventSheet, NotableSheet } from './EventSheets';
import './events.css';

interface MarksState { events: Map<number, CalendarEvent[]>; news: Map<number, NewsItem[]>; set(p: Partial<Pick<MarksState, 'events' | 'news'>>): void }
export const useMarks = create<MarksState>((set) => ({ events: new Map(), news: new Map(), set: (p) => set(p) }));

/** The events of the bar under the crosshair (for the legend). */
export function useEventsAt(t: number | null, hovering: boolean): CalendarEvent[] {
  const m = useMarks((s) => s.events);
  return hovering && t != null ? m.get(t) ?? [] : [];
}
export function useNewsAt(t: number | null, hovering: boolean): NewsItem[] {
  const m = useMarks((s) => s.news);
  return hovering && t != null ? m.get(t) ?? [] : [];
}

export const IMPACT_COLOR = IMPORTANCE_COLOR;
export const NEWS_COLOR = NEWS_PURPLE;

// Fetched once a visit (the calendar) or every few minutes (news).
let eventsCache: Promise<CalendarEvent[]> | null = null;
function loadEvents(): Promise<CalendarEvent[]> {
  eventsCache ??= calendarWeek().catch(() => { eventsCache = null; return []; });
  return eventsCache;
}
const newsCache = new Map<string, { at: number; p: Promise<NewsItem[]> }>();
function loadNews(market: 'crypto' | 'forex'): Promise<NewsItem[]> {
  const hit = newsCache.get(market);
  if (hit && Date.now() - hit.at < 5 * 60_000) return hit.p;
  const p = (market === 'crypto' ? cryptoNews(30) : forexNews()).catch(() => { newsCache.delete(market); return [] as NewsItem[]; });
  newsCache.set(market, { at: Date.now(), p });
  return p;
}
const newsMarket = (symbol: string): 'crypto' | 'forex' => (marketOf(symbol) === 'crypto' ? 'crypto' : 'forex');

/** The currencies an event must be in to touch a symbol. */
export function currenciesOf(symbol: string): string[] {
  if (marketOf(symbol) === 'crypto') return ['USD'];
  return [symbol.slice(0, 3), symbol.slice(3, 6)].map((c) => (c === 'XAU' || c === 'XAG' ? 'USD' : c));
}

/** Whether an event passes the importance filter. */
export function importanceOk(impact: CalendarEvent['impact'], filter: 'high' | 'medium' | 'all'): boolean {
  if (filter === 'all') return true;
  if (filter === 'medium') return impact === 'high' || impact === 'medium';
  return impact === 'high';
}

/** The bar a time falls in (the last bar opening at or before it), or −1 before the first. */
export function barIndexAt(bars: Candle[], t: number): number {
  let lo = 0; let hi = bars.length - 1; let at = -1;
  while (lo <= hi) { const mid = (lo + hi) >> 1; if (bars[mid]!.t <= t) { at = mid; lo = mid + 1; } else hi = mid - 1; }
  return at;
}

const eventItem = (e: CalendarEvent): LaneItem => ({ kind: 'event', at: Math.floor(e.time / 1000), event: e });
const newsItem = (n: NewsItem): LaneItem => ({ kind: 'news', at: Math.floor(n.time / 1000), news: n });

/** A symbol's events and stories, unfiltered by the reader's switches (the «why» sheet's answer). */
async function everythingFor(symbol: string): Promise<LaneItem[]> {
  const ccy = currenciesOf(symbol);
  const [events, news] = await Promise.all([loadEvents(), loadNews(newsMarket(symbol))]);
  return [
    ...events.filter((e) => ccy.includes(e.currency.toUpperCase().slice(0, 3))).map(eventItem),
    ...news.filter((n) => newsBelongs(symbol, n)).map(newsItem),
  ];
}

/** A white lightning bolt on a purple disc, centred on (x, y), `side` tall. */
function drawBolt(ctx: CanvasRenderingContext2D, x: number, y: number, side: number) {
  ctx.fillStyle = NEWS_PURPLE;
  ctx.beginPath(); ctx.arc(x, y, side / 2, 0, Math.PI * 2); ctx.fill();
  const h = side * 0.62; const w = side * 0.42;
  ctx.fillStyle = '#FFFFFF';
  ctx.beginPath();
  ctx.moveTo(x + w * 0.15, y - h / 2);
  ctx.lineTo(x - w / 2, y + h * 0.08);
  ctx.lineTo(x - w * 0.02, y + h * 0.08);
  ctx.lineTo(x - w * 0.15, y + h / 2);
  ctx.lineTo(x + w / 2, y - h * 0.1);
  ctx.lineTo(x + w * 0.02, y - h * 0.1);
  ctx.closePath(); ctx.fill();
}

/** A disc in the importance colour behind a small white calendar: the page, its header band, two rings. */
function drawCalendar(ctx: CanvasRenderingContext2D, x: number, y: number, side: number, colour: string) {
  ctx.fillStyle = colour;
  ctx.beginPath(); ctx.arc(x, y, side / 2, 0, Math.PI * 2); ctx.fill();
  const w = side * 0.52; const h = side * 0.46;
  const left = x - w / 2; const top = y - h / 2 + side * 0.04;
  const corner = side * 0.08; const line = Math.max(1, side * 0.075);
  ctx.strokeStyle = '#FFFFFF'; ctx.fillStyle = '#FFFFFF'; ctx.lineWidth = line;
  ctx.beginPath(); ctx.roundRect(left, top, w, h, corner); ctx.stroke();
  ctx.beginPath(); ctx.roundRect(left, top, w, h * 0.34, corner); ctx.fill();
  const ring = side * 0.12;
  ctx.lineCap = 'round';
  for (const at of [left + w * 0.28, left + w * 0.72]) { ctx.beginPath(); ctx.moveTo(at, top - ring * 0.7); ctx.lineTo(at, top + ring * 0.5); ctx.stroke(); }
  ctx.lineCap = 'butt';
}

type Opened = { kind: 'events'; items: LaneItem[]; bars: number } | { kind: 'notable'; reading: NotableReading };

export function EventMarks({ api, series, bars, version, replaying = false }: { api: IChartApi | null; series: ISeriesApi<SeriesType> | null; bars: Candle[]; version: number; replaying?: boolean }) {
  const canvas = useRef<HTMLCanvasElement>(null);
  const symbol = usePrefs((s) => s.symbol);
  const interval = usePrefs((s) => s.interval);
  const lang = usePrefs((s) => s.lang);
  const timezone = usePrefs((s) => s.timezone);
  const chartType = usePrefs((s) => s.chartType);
  const dark = isDark(usePrefs((s) => s.theme));
  const cs = useChartSettings();
  const [events, setEvents] = useState<CalendarEvent[]>([]);
  const [news, setNews] = useState<NewsItem[]>([]);
  const [tip, setTip] = useState<{ x: number; y: number; items: LaneItem[] } | null>(null);
  const [opened, setOpened] = useState<Opened | null>(null);
  const timeless = ['renko', 'linebreak', 'kagi', 'pnf', 'range'].includes(chartType);

  useEffect(() => {
    if (!cs.events) { setEvents([]); return; }
    let alive = true;
    void loadEvents().then((e) => { if (alive) setEvents(e); });
    return () => { alive = false; };
  }, [cs.events]);
  useEffect(() => {
    if (!cs.news) { setNews([]); return; }
    let alive = true;
    const go = () => void loadNews(newsMarket(symbol)).then((n) => { if (alive) setNews(n.filter((x) => newsBelongs(symbol, x))); });
    go();
    const id = setInterval(go, 5 * 60_000);
    return () => { alive = false; clearInterval(id); };
  }, [cs.news, symbol]);
  useEffect(() => { setOpened(null); }, [symbol, interval]);

  // Each event and story on the bar it fell in — one mark a bar, whatever its kinds — and, ahead of
  // the bars, upcoming releases by how many bars away they are. A replay shows nothing after the
  // bar it has reached.
  const placed = useMemo<{ marks: LaneMark[]; eventsAt: Map<number, CalendarEvent[]>; newsAt: Map<number, NewsItem[]> }>(() => {
    if (!bars.length || timeless) return { marks: [], eventsAt: new Map(), newsAt: new Map() };
    const step = intervalSeconds(interval);
    const lastIdx = bars.length - 1; const lastT = bars[lastIdx]!.t;
    const end = lastT + step;
    const ccy = currenciesOf(symbol);
    const now = Date.now();
    const bySlot = new Map<number, LaneMark>();
    const eventsAt = new Map<number, CalendarEvent[]>();
    const newsAt = new Map<number, NewsItem[]>();
    const slot = (logical: number, barT: number | null): LaneMark => {
      let m = bySlot.get(logical);
      if (!m) { m = { logical, barT, items: [] }; bySlot.set(logical, m); }
      return m;
    };
    for (const e of events) {
      const t = Math.floor(e.time / 1000);
      if (!importanceOk(e.impact, cs.eventImportance) || !ccy.includes(e.currency.toUpperCase().slice(0, 3))) continue;
      if (cs.eventsFutureOnly && e.time < now) continue;
      if (t < bars[0]!.t || (replaying && t >= end)) continue;
      const i = t >= end ? -1 : barIndexAt(bars, t);
      const logical = i >= 0 ? i : Math.round(lastIdx + (t - lastT) / step);
      const barT = i >= 0 ? bars[i]!.t : null;
      if (barT != null) eventsAt.set(barT, [...(eventsAt.get(barT) ?? []), e]);
      slot(logical, barT).items.push(eventItem(e));
    }
    for (const n of news) {
      const t = Math.floor(n.time / 1000);
      if (replaying && t >= end) continue;
      // A story newer than the last bar (its bar has not come in yet) goes on the last bar.
      const i = t >= lastT ? lastIdx : barIndexAt(bars, t);
      if (i < 0) continue;
      const b = bars[i]!;
      newsAt.set(b.t, [...(newsAt.get(b.t) ?? []), n]);
      slot(i, b.t).items.push(newsItem(n));
    }
    const marks = [...bySlot.values()];
    marks.forEach((m) => m.items.sort((a, b) => a.at - b.at));
    return { marks, eventsAt, newsAt };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [events, news, bars, version, symbol, interval, cs.eventImportance, cs.eventsFutureOnly, timeless, replaying]);
  const marks = placed.marks;
  useEffect(() => { useMarks.getState().set({ events: placed.eventsAt, news: placed.newsAt }); }, [placed]);
  useEffect(() => () => useMarks.getState().set({ events: new Map(), news: new Map() }), []);

  // Bars more than twice their 20-bar baseline; none on a bar an event glyph already holds.
  const notable = useMemo(() => (timeless ? [] : notableBars(bars)),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [bars, version, timeless]);
  const taken = useMemo(() => new Set(marks.filter((m) => m.barT != null).map((m) => m.logical)), [marks]);

  // The latest geometry, for the click and the hover.
  const geo = useRef<{ placed: Placed<LaneMark>[]; groups: Placed<LaneMark>[][]; h: number }>({ placed: [], groups: [], h: 0 });
  const touch = useRef(false);

  useEffect(() => {
    const el = canvas.current;
    if (!el || !api || !series) return;
    const isNewsMark = (m: LaneMark) => markKind(m) === 'news';
    const layout = () => {
      const pane = api.panes()[0];
      const h = pane ? pane.getHeight() : el.parentElement?.clientHeight ?? 0;
      const right = api.timeScale().width();
      const ps: Placed<LaneMark>[] = [];
      for (const m of marks) {
        const x = api.timeScale().logicalToCoordinate(m.logical as never);
        if (x != null) ps.push({ mark: m, x: x as number });
      }
      const groups = laneGroups(ps, isNewsMark, right);
      geo.current = { placed: ps.filter((p) => p.x >= 0 && p.x <= right), groups, h };
      return { h, right, groups };
    };
    const paint = () => {
      const parent = el.parentElement;
      if (!parent) return;
      const dpr = window.devicePixelRatio || 1;
      const { h, right, groups } = layout();
      const w = parent.clientWidth;
      el.style.width = `${w}px`; el.style.height = `${h}px`;
      el.width = Math.round(w * dpr); el.height = Math.round(h * dpr);
      const ctx = el.getContext('2d')!;
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      ctx.clearRect(0, 0, w, h);
      const cy = laneCentre(h);
      const glyphs: string[] = [];
      for (const group of groups) {
        const { mark, x } = drawnAt(group, isNewsMark);
        const stacked = group.length > 1 || isCluster(mark);
        if (markKind(mark) === 'news') {
          drawBolt(ctx, x, cy, GLYPH);
          glyphs.push(`news:${x.toFixed(1)}:${cy.toFixed(1)}`);
          continue;
        }
        // A release does not hide the news beside it: the bolt stands one glyph above, same column.
        if (group.some((p) => hasNews(p.mark))) { drawBolt(ctx, x, cy - REACH, GLYPH); glyphs.push(`news:${x.toFixed(1)}:${(cy - REACH).toFixed(1)}`); }
        const colour = IMPORTANCE_COLOR[markImportance(mark)];
        if (stacked) {
          ctx.strokeStyle = colour; ctx.lineWidth = 1;
          ctx.beginPath(); ctx.arc(x + STACK, cy - STACK, GLYPH / 2, 0, Math.PI * 2); ctx.stroke();
        }
        drawCalendar(ctx, x, cy, GLYPH, colour);
        glyphs.push(`event:${x.toFixed(1)}:${cy.toFixed(1)}:${markImportance(mark)}${stacked ? ':stack' : ''}`);
      }
      // The notable-bar dots, on the glyphs' centre line, smaller and in one muted colour.
      const dots: string[] = [];
      ctx.fillStyle = dark ? '#848E9C' : '#5F6875';
      for (const i of notable) {
        if (taken.has(i)) continue;
        const x = api.timeScale().logicalToCoordinate(i as never) as number | null;
        if (x == null || x < 0 || x > right) continue;
        ctx.beginPath(); ctx.arc(x, cy, DOT / 2, 0, Math.PI * 2); ctx.fill();
        dots.push(`${i}:${x.toFixed(1)}:${cy.toFixed(1)}`);
      }
      el.dataset.glyphs = glyphs.join(' ');
      el.dataset.groups = String(groups.length);
      el.dataset.dots = dots.join(' ');
    };

    /** The group or the notable bar a point in the lane lands on. */
    const hitAt = (pt: { x: number; y: number }): Opened | null => {
      const { placed: ps, groups, h } = geo.current;
      if (!h || pt.y < h - laneReach() || pt.y > h) return null;
      const radius = touch.current ? TOUCH_RADIUS : MOUSE_RADIUS;
      const hit = nearest(ps, (p) => p.x, pt.x, radius);
      if (hit) {
        const group = groups.find((g) => g.includes(hit));
        if (group) { const { items, bars: n } = groupItems(group); return { kind: 'events', items, bars: group.length > 1 ? n : 1 }; }
      }
      const right = api.timeScale().width();
      const bar = nearest(notable.filter((i) => !taken.has(i)), (i) => {
        const x = api.timeScale().logicalToCoordinate(i as never) as number | null;
        return x == null || x < 0 || x > right ? null : x;
      }, pt.x, radius);
      if (bar != null) {
        const reading = notableReading(bars, bar, []);
        if (reading) return { kind: 'notable', reading };
      }
      return null;
    };
    paint();
    const ts = api.timeScale();
    const onMove = (param: MouseEventParams<Time>) => {
      const pt = param.point;
      if (!pt || (param.paneIndex ?? 0) !== 0) { setTip(null); return; }
      const hit = hitAt(pt);
      setTip(hit?.kind === 'events' ? { x: pt.x, y: pt.y, items: hit.items } : null);
    };
    // A click (or a tap) on the lane, read from the page rather than from the chart's own click,
    // which swallows a second click within half a second as a would-be double click.
    const host = el.parentElement;
    let down: { x: number; y: number } | null = null;
    const onDown = (e: PointerEvent) => { touch.current = e.pointerType === 'touch' || e.pointerType === 'pen'; down = { x: e.clientX, y: e.clientY }; };
    const onClick = (e: MouseEvent) => {
      const target = e.target as HTMLElement | null;
      if (!target || target.tagName !== 'CANVAS' || e.button !== 0) return;
      if (down && Math.abs(down.x - e.clientX) + Math.abs(down.y - e.clientY) >= 5) return;
      if (useReplay.getState().selecting) return;
      const tool = useDrawing.getState().tool;
      if (!((isCursorMode(tool) && tool !== 'eraser') || tool === 'select')) return;
      const r = el.getBoundingClientRect();
      const hit = hitAt({ x: e.clientX - r.left, y: e.clientY - r.top });
      if (!hit) return;
      setTip(null);
      if (hit.kind === 'events') { setOpened(hit); return; }
      // The «why» sheet answers with everything this app has inside the bar, whatever the switches.
      const at = hit.reading.index;
      const snapshot = bars;
      void everythingFor(symbol).then((items) => {
        const reading = notableReading(snapshot, at, items);
        if (reading) setOpened({ kind: 'notable', reading });
      });
    };
    host?.addEventListener('pointerdown', onDown, true);
    host?.addEventListener('click', onClick, true);
    ts.subscribeVisibleLogicalRangeChange(paint);
    api.subscribeCrosshairMove(onMove);
    api.subscribeCrosshairMove(paint);
    const ro = new ResizeObserver(paint);
    if (host) ro.observe(host);
    return () => {
      host?.removeEventListener('pointerdown', onDown, true);
      host?.removeEventListener('click', onClick, true);
      ts.unsubscribeVisibleLogicalRangeChange(paint); api.unsubscribeCrosshairMove(onMove); api.unsubscribeCrosshairMove(paint);
      ro.disconnect();
    };
  }, [api, series, marks, notable, taken, bars, dark, symbol]);

  const fa = lang === 'fa';
  return (
    <>
      <canvas ref={canvas} className="event-marks" aria-hidden data-marks={marks.length} data-events={marks.filter((m) => m.items.some((i) => i.kind === 'event')).length} data-news={marks.filter(hasNews).length} data-notable={notable.length} />
      {tip ? (
        <div className="event-tip" style={{ left: tip.x, top: tip.y }} dir={fa ? 'rtl' : 'ltr'} role="tooltip">
          {tip.items.slice(0, 6).map((it, i) => (it.kind === 'event' ? (
            <div key={i} className="event-tip-row">
              <i className="ev-dot" style={{ background: IMPORTANCE_COLOR[it.event.impact] }} />
              <b className="num">{it.event.currency}</b> <span>{it.event.title}</span>
              <span className="num muted"> {dateText(it.at, timezone, { dayOfWeek: false })} {clockText(it.at, timezone)}</span>
              {it.event.actual || it.event.forecast || it.event.previous ? <span className="num muted"> · {it.event.actual ? `${fa ? 'واقعی' : 'Actual'} ${it.event.actual}` : ''}{it.event.forecast ? ` ${fa ? 'پیش‌بینی' : 'Forecast'} ${it.event.forecast}` : ''}{it.event.previous ? ` ${fa ? 'قبلی' : 'Prior'} ${it.event.previous}` : ''}</span> : null}
            </div>
          ) : (
            <div key={i} className="event-tip-row">
              <i className="ev-dot" style={{ background: NEWS_PURPLE }} />
              <span className={/[؀-ۿ]/.test(it.news.title) ? 'fa' : ''}>{it.news.title}</span>
              <span className="num muted"> {clockText(it.at, timezone)}</span>
            </div>
          )))}
          {tip.items.length > 6 ? <div className={`event-tip-row muted${fa ? ' fa' : ''}`}>{fa ? 'برای فهرست کامل کلیک کنید' : 'Click for the full list'}</div> : null}
        </div>
      ) : null}
      {opened?.kind === 'events' ? <EventSheet items={opened.items} bars={opened.bars} onClose={() => setOpened(null)} /> : null}
      {opened?.kind === 'notable' ? <NotableSheet reading={opened.reading} onClose={() => setOpened(null)} /> : null}
    </>
  );
}

