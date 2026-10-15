// The TradeYar VIP channel's signal on the chart of its symbol: the entry zone as a box from the
// moment it was posted, the entry line, the stop, and TP1–TP3 with the reached ones ticked; a tag
// with the status and the live profit, and a marker on the bar it was posted on. Every active
// channel signal of the open symbol is drawn for a member, and the one picked in the VIP panel
// even after it closed (with its result).
import { useEffect, useMemo, useRef } from 'react';
import { createPortal } from 'react-dom';
import { LineStyle, type IPriceLine } from 'lightweight-charts';
import { useChartBus } from '../chart/chartBus';
import { usePrefs } from '../state/prefs';
import { useMarket, digitsOf } from '../state/market';
import { formatPrice } from '../chart/format';
import { faAgo } from '../i18n/time';
import { useL } from '../pages/kit';
import type { Signal } from '../data/signals';
import { isOpen, livePct, outcomeOf, statusLabel, useVip } from './vip';
import { chartFrame, frameLoop, paneBox, yOf } from './overlay';
import './trade.css';

const GOLD = '#D4A64A';
const RED = '#F23645';
const GREEN = '#089981';

export function VipOverlay() {
  const api = useChartBus((s) => s.api);
  const series = useChartBus((s) => s.series);
  const symbol = usePrefs((s) => s.symbol);
  const member = useVip((s) => s.member);
  const active = useVip((s) => s.lists.active);
  const focus = useVip((s) => s.focus);
  const onChart = useVip((s) => s.onChart);
  const signals = useMemo(() => {
    const list = member && onChart ? (active ?? []).filter((s) => s.symbol === symbol) : [];
    if (focus && focus.symbol === symbol && !list.some((s) => s.id === focus.id)) list.push(focus);
    return list.slice(0, 4);
  }, [member, onChart, active, focus, symbol]);
  const frame = chartFrame(api);
  if (!api || !series || !frame || !signals.length) return null;
  return createPortal(<VipDrawing signals={signals} symbol={symbol} focusId={focus?.id ?? null} />, frame);
}

function VipDrawing({ signals, symbol, focusId }: { signals: Signal[]; symbol: string; focusId: string | null }) {
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const api = useChartBus((s) => s.api)!;
  const series = useChartBus((s) => s.series)!;
  const last = useMarket((s) => s.tickers[symbol]?.last ?? null);
  const digits = digitsOf(symbol);
  const root = useRef<HTMLDivElement>(null);
  const els = useRef(new Map<string, HTMLElement>());

  // Entry, stop and targets as the series' own lines, labelled on the axis.
  useEffect(() => {
    const made: IPriceLine[] = [];
    const add = (price: number | null | undefined, color: string, title: string, style: LineStyle, width: 1 | 2 = 1) => {
      if (price == null || !Number.isFinite(price)) return;
      made.push(series.createPriceLine({ price, color, lineWidth: width, lineStyle: style, axisLabelVisible: true, title }));
    };
    for (const s of signals) {
      const faded = !isOpen(s) && s.id !== focusId;
      if (faded) continue;
      add(s.entry, GOLD, 'VIP', LineStyle.Solid, 2);
      add(s.sl, RED, 'SL', LineStyle.Dashed);
      s.targets.slice(0, 3).forEach((tp, i) => add(tp, GREEN, `TP${i + 1}${s.hits?.[i] ? ' ✓' : ''}`, s.hits?.[i] ? LineStyle.Dotted : LineStyle.Dashed));
    }
    return () => { for (const l of made) { try { series.removePriceLine(l); } catch { /* series replaced */ } } };
  }, [signals, series, focusId]);

  // The box, the marker and the tag follow the chart every frame.
  useEffect(() => frameLoop(() => {
    const frame = root.current?.parentElement;
    const box = frame ? paneBox(api, frame) : null;
    if (!root.current || !box) return;
    root.current.style.cssText = `left:${box.left}px;top:${box.top}px;width:${box.width}px;height:${box.height}px`;
    const bars = useChartBus.getState().bars;
    for (const s of signals) {
      const t = s.createdAt / 1000;
      let i = -1;
      for (let k = bars.length - 1; k >= 0; k--) { if (bars[k]!.t <= t) { i = k; break; } }
      const x = i >= 0 ? api.timeScale().logicalToCoordinate(i as never) : bars.length ? 0 : null;
      const xs = x == null ? null : Math.max(0, Math.min(box.width, Number(x)));
      const lo = s.entryLow ?? null; const hi = s.entryHigh ?? null;
      const zone = els.current.get(`${s.id}:zone`);
      if (zone) {
        const y1 = hi != null ? yOf(series, hi) : null; const y2 = lo != null ? yOf(series, lo) : null;
        if (xs == null || y1 == null || y2 == null) zone.style.display = 'none';
        else { zone.style.display = ''; zone.style.left = `${xs}px`; zone.style.width = `${Math.max(2, box.width - xs)}px`; zone.style.top = `${Math.min(y1, y2)}px`; zone.style.height = `${Math.max(2, Math.abs(y2 - y1))}px`; }
      }
      const mark = els.current.get(`${s.id}:mark`);
      if (mark) { if (xs == null || i < 0) mark.style.display = 'none'; else { mark.style.display = ''; mark.style.left = `${xs}px`; } }
      const tag = els.current.get(`${s.id}:tag`);
      if (tag) {
        const ye = s.entry != null ? yOf(series, s.entry) : null;
        // Only while the entry is on screen: a tag pinned to an edge would point at nothing.
        if (xs == null || ye == null || ye < 4 || ye > box.height - 4) tag.style.display = 'none';
        else { tag.style.display = ''; tag.style.left = `${Math.max(0, Math.min(xs + 6, box.width - tag.offsetWidth - 4))}px`; tag.style.top = `${ye - 26 < 0 ? ye + 4 : ye - 26}px`; }
      }
    }
  }), [api, series, signals]);

  const reg = (key: string) => (el: HTMLElement | null) => { if (el) els.current.set(key, el); else els.current.delete(key); };

  return (
    <div className="vip-ov" ref={root} dir="ltr">
      {signals.map((s) => {
        const open = isOpen(s);
        const pct = open ? livePct(s, last ?? s.current) : s.pctGain;
        const outcome = outcomeOf(s);
        return (
          <div key={s.id} className={`vip-sig ${s.direction}${open ? '' : ' closed'}${s.id === focusId ? ' focus' : ''}`}>
            {s.entryLow != null && s.entryHigh != null ? <div className="vip-zone" ref={reg(`${s.id}:zone`)} /> : null}
            <div className="vip-mark" ref={reg(`${s.id}:mark`)}><span>VIP</span></div>
            <div className="vip-tag" ref={reg(`${s.id}:tag`)} title={s.rationale ?? ''}>
              <b className="vip-badge">VIP</b>
              <span className={`vip-dir ${s.direction === 'long' ? 'up' : 'down'}`}>{s.direction === 'long' ? 'LONG' : 'SHORT'}</span>
              <span>{s.timeframe}</span>
              {s.entry != null ? <span>@ {formatPrice(s.entry, digits)}</span> : null}
              <span className="fa" dir={lang === 'fa' ? 'rtl' : 'ltr'}>{L(...statusLabel(s))}</span>
              {pct != null ? <b className={pct >= 0 ? 'up' : 'down'}>{pct >= 0 ? '+' : '−'}{Math.abs(pct).toFixed(2)}%</b> : null}
              {!open && s.pnlUsd != null ? <b className={outcome === 'win' ? 'up' : 'down'}>{s.pnlUsd >= 0 ? '+' : '−'}{Math.abs(s.pnlUsd).toFixed(2)}$</b> : null}
              <span className="muted fa" dir={lang === 'fa' ? 'rtl' : 'ltr'}>{lang === 'fa' ? faAgo(s.createdAt) : timeAgoEn(s.createdAt)}</span>
            </div>
          </div>
        );
      })}
    </div>
  );
}

export function timeAgoEn(ms: number, now = Date.now()): string {
  const m = Math.max(0, Math.round((now - ms) / 60000));
  if (m < 1) return 'just now';
  if (m < 60) return `${m} min ago`;
  const h = Math.round(m / 60);
  if (h < 24) return `${h} h ago`;
  return `${Math.round(h / 24)} d ago`;
}
