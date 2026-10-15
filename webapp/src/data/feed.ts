// The chart's feed: history from candles.ts, the forming bar built from live prices and corrected
// against the backend's own last bars every so often (the phone's live edge, CandleBudget-aware).
import type { ChartFeed } from '../chart/ChartView';
import type { Candle } from './types';
import { bucketOf, loadEdge, loadHistory } from './candles';
import { intervalSeconds } from './intervals';
import { onPrice, setFocus } from './prices';
import { backendOf } from './symbols';
import { pageVisible } from './http';

const monthLike = (iv: string) => iv === '1W' || iv === '1M';

export const feed: ChartFeed = {
  async history(symbol, interval, before) {
    if (!before) setFocus(symbol);
    return loadHistory(symbol, interval, before, before ? 'bulk' : 'reader');
  },

  live(symbol, interval, from, onBar) {
    let bar: Candle | null = from ? { ...from } : null;
    let alive = true;
    const fold: 'week' | 'month' | 'minutes' = interval === '1W' ? 'week' : interval === '1M' ? 'month' : 'minutes';

    const seed = (bars: Candle[]) => {
      const tail = bars[bars.length - 1];
      if (!tail) return;
      if (!bar || tail.t >= bar.t) { bar = { ...tail }; onBar(bar); }
    };

    const offPrice = onPrice(symbol, (price, tsMs) => {
      if (!bar) return;
      const t = bucketOf(Math.floor(tsMs / 1000), interval, fold);
      if (t > bar.t) bar = { t, o: bar.c, h: price, l: price, c: price, v: 0 };
      else { bar = { ...bar, h: Math.max(bar.h, price), l: Math.min(bar.l, price), c: price }; }
      onBar(bar);
    });

    // The edge: crypto every interval/12 (3–30 s); forex at most every 30 s to stay inside the
    // guest budget's background share.
    const crypto = backendOf(symbol) === 'tradeyar';
    const every = monthLike(interval) ? 60_000 : Math.min(30_000, Math.max(crypto ? 3000 : 30_000, (intervalSeconds(interval) * 1000) / 12));
    const tick = async () => {
      if (!alive) return;
      if (pageVisible()) {
        try { seed(await loadEdge(symbol, interval)); } catch { /* the next tick tries again */ }
      }
      if (alive) timer = setTimeout(tick, every);
    };
    let timer = setTimeout(tick, every);

    return () => { alive = false; clearTimeout(timer); offPrice(); };
  },
};
