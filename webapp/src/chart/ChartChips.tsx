// Two buttons the old Pro-Chart laid over the plot, each with its switch in Settings → Trading:
// TradingView's trade ring under the live bar (OLD CoineProChart.drawTradeRing: a 20px purple ring,
// 1.5px stroke, a bolt inside, its foot 3px above the time axis, centred on the newest bar, shown
// only while that bar is on screen and the ring has room) and the quote currency chip in the
// plot's top corner (OLD ChartScreen.QuoteChip: 26px tall in a hairline, 4px in from the corner,
// inside the plot and never over the price axis), which opens the price scale menu.
import { memo, useEffect, useState } from 'react';
import type { IChartApi, Time } from 'lightweight-charts';
import { useChartSettings } from '../state/chartSettings';
import { useMarket } from '../state/market';
import { instrumentOf } from '../data/symbols';
import { useL } from '../pages/kit';
import { Icon } from '../ui/Icon';
import { openTrade } from '../shell/publish';

/** TradingView's trade ink (OLD TradingViewPalette.TRADE). */
export const TRADE_INK = '#8D32A9';
const RING = 20;
const LIFT = 3;

export const TradeRing = memo(function TradeRing({ api, lastTime, paneHeight, version }: { api: IChartApi | null; lastTime: number | null; paneHeight: number; version: number }) {
  const L = useL();
  const on = useChartSettings((s) => s.tradeRing);
  const left = useChartSettings((s) => s.scalePlacement === 'left');
  const [x, setX] = useState<number | null>(null);
  useEffect(() => {
    if (!api || !on || lastTime == null) { setX(null); return; }
    const ts = api.timeScale();
    const place = () => {
      const cx = ts.timeToCoordinate(lastTime as Time);
      const offset = left ? api.priceScale('left').width() : 0;
      const w = ts.width();
      setX(cx == null || cx - RING / 2 < 0 || cx + RING / 2 > w ? null : cx + offset);
    };
    place();
    ts.subscribeVisibleLogicalRangeChange(place);
    ts.subscribeSizeChange(place);
    return () => { ts.unsubscribeVisibleLogicalRangeChange(place); ts.unsubscribeSizeChange(place); };
  }, [api, on, lastTime, left, version]);
  if (!on || x == null || paneHeight < RING * 3) return null;
  return (
    <button type="button" className="trade-ring" style={{ left: x - RING / 2, top: paneHeight - LIFT - RING }}
      title={L('معامله', 'Trade')} aria-label={L('معامله', 'Trade')} onClick={() => openTrade()}>
      <svg viewBox="0 0 20 20" aria-hidden>
        {/* The bolt: six points, 55 % of the ring's height and 60 % as wide as tall (OLD TRADE_BOLT_*). */}
        <path d="M11.32 4.5 6.7 11.1H9.87L8.68 15.5 13.3 8.9H10.13Z" />
      </svg>
    </button>
  );
});

export const QuoteChip = memo(function QuoteChip({ api, symbol, below = 0, onOpen }: { api: IChartApi | null; symbol: string; /** Room kept clear above it (the pane's own buttons). */ below?: number; onOpen(x: number, y: number): void }) {
  const L = useL();
  const on = useChartSettings((s) => s.quoteChip);
  const left = useChartSettings((s) => s.scalePlacement === 'left');
  const inst = useMarket((m) => m.instruments[symbol]) ?? instrumentOf(symbol);
  const [gutter, setGutter] = useState(0);
  useEffect(() => {
    if (!api) return;
    const measure = () => { try { setGutter(api.priceScale(left ? 'left' : 'right').width()); } catch { /* not laid out */ } };
    measure();
    const ts = api.timeScale();
    ts.subscribeSizeChange(measure);
    const id = setInterval(measure, 1500);
    return () => { ts.unsubscribeSizeChange(measure); clearInterval(id); };
  }, [api, left]);
  if (!on || !api || !inst.quote || !gutter) return null;
  return (
    <button type="button" className={`quote-chip num${left ? ' left' : ''}`} style={{ top: 4 + below, ...(left ? { left: gutter + 4 } : { right: gutter + 4 }) }} dir="ltr"
      title={L('مقیاس قیمت', 'Price scale')} aria-label={`${inst.quote} · ${L('مقیاس قیمت', 'Price scale')}`}
      onClick={(e) => { const r = e.currentTarget.getBoundingClientRect(); onOpen(left ? r.left : r.right, r.bottom + 4); }}>
      <span>{inst.quote}</span><Icon name="ph-caret-down" size="sm" />
    </button>
  );
});
