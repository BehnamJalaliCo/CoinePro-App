// The best bid and ask of the open symbol, as TradingView's Buy/Sell boxes and the Quote card show
// them. Crypto: the top of TradeYar's public order book (the relay, every 2 s while shown). The
// forex feed sends one price as both bid and ask, so a forex quote is left out rather than faked.
// Also the deeper book for the DOM panel.
import { useEffect, useState } from 'react';
import { request, pageVisible } from './http';
import { backendOf } from './symbols';

export interface Quote { bid: number; ask: number; spread: number; ts: number }

export async function topOfBook(symbol: string): Promise<Quote | null> {
  if (backendOf(symbol) !== 'tradeyar') return null;
  const r = await request<{ bids?: number[][]; asks?: number[][] }>(`/up/tradeyar/api/v1/public/market/depth?symbol=${encodeURIComponent(symbol)}&depth=1`);
  const bid = Number(r.bids?.[0]?.[0]); const ask = Number(r.asks?.[0]?.[0]);
  if (!(bid > 0) || !(ask > 0) || ask < bid) return null;
  return { bid, ask, spread: ask - bid, ts: Date.now() };
}

export interface Book { bids: [number, number][]; asks: [number, number][]; ts: number }

/** The public order book, `depth` levels a side (crypto only). */
export async function orderBook(symbol: string, depth = 20): Promise<Book | null> {
  if (backendOf(symbol) !== 'tradeyar') return null;
  const r = await request<{ bids?: number[][]; asks?: number[][] }>(`/up/tradeyar/api/v1/public/market/depth?symbol=${encodeURIComponent(symbol)}&depth=${depth}`);
  const side = (l?: number[][]) => (l ?? []).map((x) => [Number(x[0]), Number(x[1])] as [number, number]).filter(([p, v]) => p > 0 && v >= 0);
  return { bids: side(r.bids).slice(0, depth), asks: side(r.asks).slice(0, depth), ts: Date.now() };
}

// One poller per symbol, however many places show the quote.
const polls = new Map<string, { listeners: Set<(q: Quote | null) => void>; timer: ReturnType<typeof setInterval>; last: Quote | null }>();
function subscribe(symbol: string, everyMs: number, fn: (q: Quote | null) => void): () => void {
  let p = polls.get(symbol);
  if (!p) {
    const entry = { listeners: new Set<(q: Quote | null) => void>(), timer: 0 as unknown as ReturnType<typeof setInterval>, last: null as Quote | null };
    const tick = () => { if (pageVisible()) void topOfBook(symbol).then((x) => { entry.last = x; entry.listeners.forEach((l) => l(x)); }).catch(() => undefined); };
    entry.timer = setInterval(tick, everyMs);
    tick();
    polls.set(symbol, entry);
    p = entry;
  }
  p.listeners.add(fn);
  if (p.last) fn(p.last);
  return () => {
    const e = polls.get(symbol);
    if (!e) return;
    e.listeners.delete(fn);
    if (!e.listeners.size) { clearInterval(e.timer); polls.delete(symbol); }
  };
}

/** Keeps a symbol's quote polled outside React (the alert watcher's bid / ask); returns the stop. */
export function watchQuote(symbol: string, fn: (q: Quote | null) => void = () => undefined): () => void {
  if (backendOf(symbol) !== 'tradeyar') return () => undefined;
  return subscribe(symbol, 2000, fn);
}

/** The newest quote any poller has for the symbol, or null. */
export function lastQuote(symbol: string): Quote | null {
  return polls.get(symbol)?.last ?? null;
}

/** The live quote while a component shows it; null for forex or before the first answer. */
export function useQuote(symbol: string, everyMs = 2000): Quote | null {
  const [q, setQ] = useState<Quote | null>(null);
  useEffect(() => {
    setQ(null);
    if (backendOf(symbol) !== 'tradeyar') return;
    return subscribe(symbol, everyMs, setQ);
  }, [symbol, everyMs]);
  return q;
}
