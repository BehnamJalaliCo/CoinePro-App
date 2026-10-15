// Economic events and the latest news on the chart, as TradingView marks them: an event as a small
// disc on the bottom edge of the price pane at its bar (upcoming ones out in the space right of the
// last bar), coloured by importance; a story as a purple disc above its bar. A hover shows what it
// is; the legend lists the bar's events while the crosshair is on it. Drawn on a canvas inside the
// chart, so a snapshot carries them.
import { useEffect, useMemo, useRef, useState } from 'react';
import { create } from 'zustand';
import type { IChartApi, ISeriesApi, MouseEventParams, SeriesType, Time } from 'lightweight-charts';
import type { Candle } from '../data/types';
import { calendarWeek, cryptoNews, forexNews, type CalendarEvent, type NewsItem } from '../data/intel';
import { marketOf } from '../data/symbols';
import { intervalSeconds } from '../data/intervals';
import { usePrefs } from '../state/prefs';
import { useChartSettings } from '../state/chartSettings';
import { clockText, dateText } from './time';

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

export const IMPACT_COLOR: Record<CalendarEvent['impact'], string> = { high: '#F23645', medium: '#FF9800', low: '#B2B5BE', holiday: '#787B86' };
export const NEWS_COLOR = '#9C27B0';

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

interface Mark { kind: 'event' | 'news'; logical: number; barT: number | null; color: string; items: (CalendarEvent | NewsItem)[]; high: number | null }

export function EventMarks({ api, series, bars, version }: { api: IChartApi | null; series: ISeriesApi<SeriesType> | null; bars: Candle[]; version: number }) {
  const canvas = useRef<HTMLCanvasElement>(null);
  const symbol = usePrefs((s) => s.symbol);
  const interval = usePrefs((s) => s.interval);
  const lang = usePrefs((s) => s.lang);
  const timezone = usePrefs((s) => s.timezone);
  const chartType = usePrefs((s) => s.chartType);
  const cs = useChartSettings();
  const [events, setEvents] = useState<CalendarEvent[]>([]);
  const [news, setNews] = useState<NewsItem[]>([]);
  const [tip, setTip] = useState<{ x: number; y: number; mark: Mark } | null>(null);
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
    const market = marketOf(symbol) === 'crypto' ? 'crypto' : 'forex';
    const go = () => void loadNews(market).then((n) => { if (alive) setNews(n); });
    go();
    const id = setInterval(go, 5 * 60_000);
    return () => { alive = false; clearInterval(id); };
  }, [cs.news, symbol]);

  // Each event and story placed on its bar (or, ahead of the bars, by how many bars away it is).
  const placed = useMemo<{ marks: Mark[]; eventsAt: Map<number, CalendarEvent[]>; newsAt: Map<number, NewsItem[]> }>(() => {
    if (!bars.length || timeless) return { marks: [], eventsAt: new Map(), newsAt: new Map() };
    const step = intervalSeconds(interval);
    const lastIdx = bars.length - 1; const lastT = bars[lastIdx]!.t;
    const ccy = currenciesOf(symbol);
    const now = Date.now();
    const out: Mark[] = [];
    const eventsAt = new Map<number, CalendarEvent[]>();
    const groups = new Map<string, Mark>();
    for (const e of events) {
      const t = Math.floor(e.time / 1000);
      if (!importanceOk(e.impact, cs.eventImportance) || !ccy.includes(e.currency.toUpperCase().slice(0, 3))) continue;
      if (cs.eventsFutureOnly && e.time < now) continue;
      if (t < bars[0]!.t) continue;
      const i = t >= lastT + step ? -1 : barIndexAt(bars, t);
      const logical = i >= 0 ? i : lastIdx + (t - lastT) / step;
      const barT = i >= 0 ? bars[i]!.t : null;
      if (barT != null) eventsAt.set(barT, [...(eventsAt.get(barT) ?? []), e]);
      const key = `e${Math.round(logical)}`;
      const g = groups.get(key);
      const rank = (x: CalendarEvent['impact']) => ['holiday', 'low', 'medium', 'high'].indexOf(x);
      if (g) { g.items.push(e); if (rank(e.impact) > rank((g.items[0] as CalendarEvent).impact)) g.color = IMPACT_COLOR[e.impact]; }
      else { const m: Mark = { kind: 'event', logical: Math.round(logical), barT, color: IMPACT_COLOR[e.impact], items: [e], high: null }; groups.set(key, m); out.push(m); }
    }
    const newsAt = new Map<number, NewsItem[]>();
    for (const n of news) {
      const t = Math.floor(n.time / 1000);
      // A story newer than the last bar (its bar has not come in yet) goes on the last bar.
      const i = t >= lastT ? lastIdx : barIndexAt(bars, t);
      if (i < 0) continue;
      const b = bars[i]!;
      newsAt.set(b.t, [...(newsAt.get(b.t) ?? []), n]);
      const key = `n${i}`;
      const g = groups.get(key);
      if (g) g.items.push(n);
      else { const m: Mark = { kind: 'news', logical: i, barT: b.t, color: NEWS_COLOR, items: [n], high: b.h }; groups.set(key, m); out.push(m); }
    }
    return { marks: out, eventsAt, newsAt };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [events, news, bars, version, symbol, interval, cs.eventImportance, cs.eventsFutureOnly, timeless]);
  const marks = placed.marks;
  useEffect(() => { useMarks.getState().set({ events: placed.eventsAt, news: placed.newsAt }); }, [placed]);
  useEffect(() => () => useMarks.getState().set({ events: new Map(), news: new Map() }), []);

  useEffect(() => {
    const el = canvas.current;
    if (!el || !api || !series) return;
    const R = 7;
    const place = (m: Mark, h: number) => {
      const x = api.timeScale().logicalToCoordinate(m.logical as never);
      if (x == null) return null;
      if (m.kind === 'event') return { x: x as number, y: h - R - 4 };
      const y = m.high != null ? series.priceToCoordinate(m.high) : null;
      return y == null ? null : { x: x as number, y: Math.max(R + 2, (y as number) - R - 6) };
    };
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
      const right = api.timeScale().width();
      ctx.font = "600 9px 'IRANYekanX', sans-serif";
      ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
      for (const m of marks) {
        const p = place(m, h);
        if (!p || p.x < 0 || p.x > right) continue;
        ctx.fillStyle = m.color;
        ctx.beginPath(); ctx.arc(p.x, p.y, R, 0, Math.PI * 2); ctx.fill();
        ctx.fillStyle = '#FFFFFF';
        const label = m.items.length > 1 ? String(m.items.length) : m.kind === 'news' ? 'N' : (m.items[0] as CalendarEvent).currency.slice(0, 1).toUpperCase();
        ctx.fillText(label, p.x, p.y + 0.5);
      }
    };
    paint();
    const ts = api.timeScale();
    const onMove = (param: MouseEventParams<Time>) => {
      const pt = param.point;
      if (!pt || (param.paneIndex ?? 0) !== 0) { setTip(null); return; }
      const h = api.panes()[0]?.getHeight() ?? 0;
      const hit = marks.find((m) => { const p = place(m, h); return p && Math.abs(p.x - pt.x) <= R + 2 && Math.abs(p.y - pt.y) <= R + 2; });
      setTip(hit ? { x: pt.x, y: pt.y, mark: hit } : null);
    };
    ts.subscribeVisibleLogicalRangeChange(paint);
    api.subscribeCrosshairMove(onMove);
    api.subscribeCrosshairMove(paint);
    const ro = new ResizeObserver(paint);
    if (el.parentElement) ro.observe(el.parentElement);
    return () => { ts.unsubscribeVisibleLogicalRangeChange(paint); api.unsubscribeCrosshairMove(onMove); api.unsubscribeCrosshairMove(paint); ro.disconnect(); };
  }, [api, series, marks]);

  const fa = lang === 'fa';
  return (
    <>
      <canvas ref={canvas} className="event-marks" aria-hidden data-marks={marks.length} data-events={marks.filter((m) => m.kind === 'event').length} data-news={marks.filter((m) => m.kind === 'news').length} />
      {tip ? (
        <div className="event-tip" style={{ left: tip.x, top: tip.y }} dir={fa ? 'rtl' : 'ltr'} role="tooltip">
          {tip.mark.items.slice(0, 6).map((it, i) => ('impact' in it ? (
            <div key={i} className="event-tip-row">
              <i className="ev-dot" style={{ background: IMPACT_COLOR[it.impact] }} />
              <b className="num">{it.currency}</b> <span>{it.title}</span>
              <span className="num muted"> {dateText(Math.floor(it.time / 1000), timezone, { dayOfWeek: false })} {clockText(Math.floor(it.time / 1000), timezone)}</span>
              {it.actual || it.forecast || it.previous ? <span className="num muted"> · {it.actual ? `${fa ? 'واقعی' : 'Actual'} ${it.actual}` : ''}{it.forecast ? ` ${fa ? 'پیش‌بینی' : 'Forecast'} ${it.forecast}` : ''}{it.previous ? ` ${fa ? 'قبلی' : 'Prior'} ${it.previous}` : ''}</span> : null}
            </div>
          ) : (
            <div key={i} className="event-tip-row">
              <i className="ev-dot" style={{ background: NEWS_COLOR }} />
              <span className={/[؀-ۿ]/.test(it.title) ? 'fa' : ''}>{it.title}</span>
              <span className="num muted"> {clockText(Math.floor(it.time / 1000), timezone)}</span>
            </div>
          )))}
        </div>
      ) : null}
    </>
  );
}
