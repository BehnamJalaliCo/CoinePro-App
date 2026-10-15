// The reader's live trading on the chart, as TradingView draws a broker's: each open position as a
// solid line at its entry with the side and size, the open profit and a close button; working
// orders as dashed lines with a cancel button, dragged to a new price where the platform can amend
// them; stop loss and take profit as lines dragged to move the protection, or pulled out of the
// position's SL / TP buttons. Every change to real money asks once more, on the line itself.
import { useEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { LineStyle, type IPriceLine } from 'lightweight-charts';
import { useChartBus } from '../chart/chartBus';
import { usePrefs } from '../state/prefs';
import { useMarket, digitsOf } from '../state/market';
import { formatPrice } from '../chart/format';
import { uuid } from '../data/http';
import { useL } from '../pages/kit';
import { amendOrder, cancelOrder, closePosition, protectPosition } from './api';
import { protectionOf, useTrading, workingOrders } from './store';
import { venueOf, type Venue } from './venue';
import { pnlAt } from './math';
import { tradeErrorText, type Order, type Position } from './wire';
import { chartFrame, frameLoop, paneBox, snap, yOf } from './overlay';
import { notify } from './toast';
import './trade.css';

const BUY = '#2962FF';
const SELL = '#F23645';
const TP = '#089981';

type Tone = 'buy' | 'sell' | 'sl' | 'tp' | 'bracket';
interface Item {
  key: string;
  price: number;
  tone: Tone;
  dashed: boolean;
  /** The first box: side and size, LMT, SL, TP. */
  tag: string;
  qty?: string;
  pnl?: number | null;
  title: string;
  move?(price: number): Promise<void>;
  remove?: { ask: boolean; run(): Promise<void>; label: string };
  spawn?: { sl: boolean; tp: boolean; position: Position };
}

/** Which labels keep the right-hand place: the position, then its stop and target, then orders. */
const rank = (key: string) => (key.startsWith('pos:') ? 0 : key.startsWith('sl:') || key.startsWith('tp:') || key === 'ghost' ? 1 : key.startsWith('ord:') ? 2 : 3);

const color = (t: Tone) => (t === 'buy' ? BUY : t === 'sell' || t === 'sl' ? SELL : t === 'tp' ? TP : '#787B86');

export function ChartTrading() {
  const api = useChartBus((s) => s.api);
  const series = useChartBus((s) => s.series);
  const symbol = usePrefs((s) => s.symbol);
  const venue = venueOf(symbol);
  const book = useTrading((s) => (venue ? s.books[venue] : null));
  const frame = chartFrame(api);
  if (!api || !series || !frame || !venue || !book || book.status !== 'live') return null;
  return createPortal(<TradeLines venue={venue} symbol={symbol} />, frame);
}

function TradeLines({ venue, symbol }: { venue: Venue; symbol: string }) {
  const L = useL();
  const api = useChartBus((s) => s.api)!;
  const series = useChartBus((s) => s.series)!;
  const book = useTrading((s) => s.books[venue]);
  const memory = useTrading((s) => s.protections);
  const last = useMarket((s) => s.tickers[symbol]?.last ?? null);
  const digits = digitsOf(symbol);
  const [drag, setDrag] = useState<{ key: string; price: number } | null>(null);
  const [pending, setPending] = useState<{ key: string; price: number | null } | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [ghost, setGhost] = useState<{ kind: 'sl' | 'tp'; position: Position; price: number } | null>(null);
  const root = useRef<HTMLDivElement>(null);
  const labels = useRef(new Map<string, { el: HTMLElement; price: number }>());
  const lines = useRef(new Map<string, IPriceLine>());
  const fmt = (v: number) => formatPrice(v, digits);
  const refresh = () => void useTrading.getState().refresh(venue, 'book');

  const run = async (key: string, job: () => Promise<void>, ok: string) => {
    setBusy(key);
    try { await job(); notify(ok); } catch (e) { notify(tradeErrorText(e, L), 'error'); } finally { setBusy(null); setPending(null); setGhost(null); refresh(); }
  };

  const protect = (p: Position, sl: number | null, tp: number | null, key: string) => run(key, async () => {
    await protectPosition(venue, { symbol, side: p.side, stop_loss: sl, take_profit: tp });
    useTrading.getState().rememberProtection(venue, symbol, p.side, { sl, tp });
  }, L(`حد ضرر و سود ${symbol} ثبت شد`, `${symbol} protection set`));

  const items = useMemo<Item[]>(() => {
    const out: Item[] = [];
    for (const p of book.positions.filter((x) => x.symbol === symbol)) {
      if (p.entry == null) continue;
      const prot = protectionOf(venue, p, book.orders, memory);
      const side = p.side === 'long' ? 'buy' : 'sell';
      const pnl = last != null ? pnlAt(side, p.entry, last, p.quantity) : p.upnl;
      const key = `pos:${p.side}`;
      out.push({
        key, price: p.entry, tone: side, dashed: false, tag: p.side === 'long' ? '▲' : '▼', qty: String(p.quantity), pnl,
        title: L(`موقعیت ${p.side === 'long' ? 'خرید' : 'فروش'} ${p.quantity} @ ${fmt(p.entry)}`, `${p.side === 'long' ? 'Long' : 'Short'} ${p.quantity} @ ${fmt(p.entry)}`),
        remove: { ask: true, label: L('بستن موقعیت؟', 'Close the position?'), run: () => run(key, async () => { await closePosition(venue, { client_request_id: uuid(), symbol, side: p.side, quantity: null }); }, L(`دستور بستن ${symbol} فرستاده شد`, `${symbol} close sent`)) },
        spawn: { sl: prot.sl == null, tp: prot.tp == null, position: p },
      });
      if (prot.sl != null) {
        const k = `sl:${p.side}`;
        out.push({ key: k, price: prot.sl, tone: 'sl', dashed: true, tag: 'SL', pnl: pnlAt(side, p.entry, prot.sl, p.quantity), title: L('حد ضرر — بکشید تا جابه‌جا شود', 'Stop loss — drag to move'), move: (px) => protect(p, px, prot.tp, k) });
      }
      if (prot.tp != null) {
        const k = `tp:${p.side}`;
        out.push({ key: k, price: prot.tp, tone: 'tp', dashed: true, tag: 'TP', pnl: pnlAt(side, p.entry, prot.tp, p.quantity), title: L('حد سود — بکشید تا جابه‌جا شود', 'Take profit — drag to move'), move: (px) => protect(p, prot.sl, px, k) });
      }
    }
    for (const o of workingOrders(book, symbol)) {
      if (o.price == null) continue;
      const key = `ord:${o.id}`;
      out.push({
        key, price: o.price, tone: o.side, dashed: true, tag: o.type === 'stop' ? 'STP' : 'LMT', qty: o.quantity != null ? String(o.quantity) : undefined,
        title: L(`سفارش ${o.side === 'buy' ? 'خرید' : 'فروش'} ${o.type === 'stop' ? 'استاپ' : 'محدود'}${o.canAmend ? ' — بکشید تا قیمت عوض شود' : ''}`, `${o.side === 'buy' ? 'Buy' : 'Sell'} ${o.type}${o.canAmend ? ' — drag to change the price' : ''}`),
        move: o.canAmend ? (px) => run(key, async () => { await amendOrder(venue, o.id, px); }, L(`قیمت سفارش به ${fmt(px)} رفت`, `Order moved to ${fmt(px)}`)) : undefined,
        remove: o.canCancel ? { ask: false, label: '', run: () => run(key, async () => { await cancelOrder(venue, o.id); }, L('سفارش لغو شد', 'Order cancelled')) } : undefined,
      });
      for (const [lvl, tag] of [[o.stopLoss, 'SL'], [o.takeProfit, 'TP']] as [number | null, string][]) {
        if (lvl != null) out.push({ key: `${tag}:${o.id}`, price: lvl, tone: 'bracket', dashed: true, tag, qty: undefined, title: L(`${tag} سفارش در انتظار`, `${tag} of the working order`) });
      }
    }
    if (ghost) {
      const side = ghost.position.side === 'long' ? 'buy' : 'sell';
      out.push({ key: 'ghost', price: ghost.price, tone: ghost.kind, dashed: true, tag: ghost.kind.toUpperCase(), pnl: ghost.position.entry != null ? pnlAt(side, ghost.position.entry, ghost.price, ghost.position.quantity) : null, title: '' });
    }
    return out;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [book, memory, last, symbol, venue, ghost, digits]);

  const shown = (it: Item) => (drag?.key === it.key ? drag.price : pending?.key === it.key && pending.price != null ? pending.price : it.price);

  // The lines themselves (and their price-axis labels) are the series' own price lines.
  useEffect(() => {
    const map = lines.current;
    const keep = new Set<string>();
    for (const it of items) {
      keep.add(it.key);
      const price = shown(it);
      const opts = { price, color: color(it.tone), lineWidth: 1 as const, lineStyle: it.dashed ? LineStyle.Dashed : LineStyle.Solid, axisLabelVisible: true, title: '', lineVisible: true };
      const have = map.get(it.key);
      if (have) have.applyOptions(opts); else map.set(it.key, series.createPriceLine(opts));
    }
    for (const [k, l] of map) if (!keep.has(k)) { try { series.removePriceLine(l); } catch { /* series replaced */ } map.delete(k); }
  });
  useEffect(() => () => { for (const l of lines.current.values()) { try { series.removePriceLine(l); } catch { /* gone */ } } lines.current.clear(); }, [series]);

  // Labels follow their prices every frame.
  useEffect(() => frameLoop(() => {
    const frame = root.current?.parentElement;
    const box = frame ? paneBox(api, frame) : null;
    if (!root.current || !box) return;
    root.current.style.cssText = `left:${box.left}px;top:${box.top}px;width:${box.width}px;height:${box.height}px`;
    // Labels on nearly the same price step aside instead of covering each other: the position's
    // (and its stop and target) keep the right-hand place, an order's moves left of them.
    const base = box.width < 500 ? 8 : 64;
    const placed: { y: number; right: number; width: number }[] = [];
    const order = [...labels.current.entries()].sort(([a], [b]) => rank(a) - rank(b));
    for (const [, { el, price }] of order) {
      const y = yOf(series, price);
      if (y == null || y < -10 || y > box.height + 10) { el.style.visibility = 'hidden'; continue; }
      el.style.visibility = 'visible';
      el.style.transform = `translateY(${Math.round(y)}px)`;
      const label = el.querySelector<HTMLElement>('.ltl-label');
      const width = label?.offsetWidth ?? 0;
      let right = base;
      for (let guard = 0; guard < 6; guard++) {
        const hit = placed.find((p) => Math.abs(p.y - y) < 23 && right < p.right + p.width + 6 && right + width + 6 > p.right);
        if (!hit) break;
        right = hit.right + hit.width + 6;
      }
      if (label) label.style.setProperty('--ltl-right', `${right}px`);
      placed.push({ y, right, width });
    }
  }), [api, series]);

  const priceAt = (clientY: number): number | null => {
    const frame = root.current?.parentElement;
    const box = frame ? paneBox(api, frame) : null;
    if (!frame || !box) return null;
    const y = clientY - frame.getBoundingClientRect().top - box.top;
    try { const p = series.coordinateToPrice(y); return p == null ? null : snap(Number(p), digits); } catch { return null; }
  };

  const startDrag = (e: React.PointerEvent, key: string, from: number, onDone: (price: number, moved: boolean) => void) => {
    if (e.button !== 0) return;
    e.preventDefault(); e.stopPropagation();
    const y0 = e.clientY;
    let price = from; let moved = false;
    setDrag({ key, price });
    const move = (ev: PointerEvent) => {
      if (Math.abs(ev.clientY - y0) > 2) moved = true;
      const p = priceAt(ev.clientY);
      if (p != null && p > 0) { price = p; setDrag({ key, price: p }); }
    };
    const up = () => { window.removeEventListener('pointermove', move); window.removeEventListener('pointerup', up); setDrag(null); onDone(price, moved); };
    window.addEventListener('pointermove', move);
    window.addEventListener('pointerup', up);
  };

  return (
    <div className="ltl-root" ref={root} dir="ltr">
      {items.map((it) => {
        const price = shown(it);
        const ask = pending?.key === it.key;
        const side = it.tone === 'buy' || it.tone === 'sell';
        return (
          <div
            key={it.key}
            className={`ltl ${it.tone}${it.move ? ' movable' : ''}${drag?.key === it.key ? ' dragging' : ''}${busy === it.key ? ' busy' : ''}`}
            data-key={it.key}
            title={it.title}
            ref={(el) => { if (el) labels.current.set(it.key, { el, price }); else labels.current.delete(it.key); }}
          >
            <span className="ltl-hit" onPointerDown={it.move && !ask && busy !== it.key ? (e) => startDrag(e, it.key, it.price, (p, moved) => { if (moved && p !== it.price) setPending({ key: it.key, price: p }); }) : undefined} />
            <div className="ltl-label" onPointerDown={it.move && !ask && busy !== it.key ? (e) => { if ((e.target as HTMLElement).closest('button')) return; startDrag(e, it.key, it.price, (p, moved) => { if (moved && p !== it.price) setPending({ key: it.key, price: p }); }); } : undefined}>
              <span className="ltl-tag">{it.tag}{it.qty ? <b>{it.qty}</b> : null}</span>
              {it.pnl != null ? <span className={`ltl-pnl ${it.pnl >= 0 ? 'up' : 'down'}`}>{it.pnl >= 0 ? '+' : '−'}{Math.abs(it.pnl).toFixed(2)}</span> : null}
              {drag?.key === it.key || ask ? <span className="ltl-price">{fmt(price)}</span> : null}
              {it.spawn && !ask ? (
                <>
                  {it.spawn.tp ? <button className="ltl-spawn tp" title={L('بکشید تا حد سود بگذارید', 'Drag to set a take profit')} onPointerDown={(e) => { const pos = it.spawn!.position; setGhost({ kind: 'tp', position: pos, price: it.price }); startDrag(e, 'ghost', it.price, (p, moved) => { if (moved) { setGhost({ kind: 'tp', position: pos, price: p }); setPending({ key: 'ghost', price: p }); } else setGhost(null); }); }}>TP</button> : null}
                  {it.spawn.sl ? <button className="ltl-spawn sl" title={L('بکشید تا حد ضرر بگذارید', 'Drag to set a stop loss')} onPointerDown={(e) => { const pos = it.spawn!.position; setGhost({ kind: 'sl', position: pos, price: it.price }); startDrag(e, 'ghost', it.price, (p, moved) => { if (moved) { setGhost({ kind: 'sl', position: pos, price: p }); setPending({ key: 'ghost', price: p }); } else setGhost(null); }); }}>SL</button> : null}
                </>
              ) : null}
              {ask ? (
                <>
                  <span className="ltl-ask fa">{pending!.price == null ? it.remove?.label : L('اینجا؟', 'Here?')}</span>
                  <button className="ltl-ok" aria-label={L('تأیید', 'Confirm')} onClick={() => {
                    if (pending!.price == null) { void it.remove?.run(); return; }
                    if (it.key === 'ghost' && ghost) {
                      const prot = protectionOf(venue, ghost.position, book.orders, memory);
                      void protect(ghost.position, ghost.kind === 'sl' ? pending!.price : prot.sl, ghost.kind === 'tp' ? pending!.price : prot.tp, 'ghost');
                    } else void it.move?.(pending!.price);
                  }}>✓</button>
                  <button className="ltl-no" aria-label={L('انصراف', 'Cancel')} onClick={() => { setPending(null); if (it.key === 'ghost') setGhost(null); }}>✕</button>
                </>
              ) : it.remove ? (
                <button className="ltl-x" title={it.remove.ask ? L('بستن موقعیت', 'Close position') : L('لغو سفارش', 'Cancel order')} aria-label="×" onClick={() => (it.remove!.ask ? setPending({ key: it.key, price: null }) : void it.remove!.run())}>×</button>
              ) : null}
              {side && busy === it.key ? <span className="ltl-spin" aria-hidden /> : null}
            </div>
          </div>
        );
      })}
    </div>
  );
}

/** For the account panel: the stop and target of a position, by the same rule the chart uses. */
export function useProtection(venue: Venue, p: Position, orders: Order[]): { sl: number | null; tp: number | null } {
  const memory = useTrading((s) => s.protections);
  return protectionOf(venue, p, orders, memory);
}
