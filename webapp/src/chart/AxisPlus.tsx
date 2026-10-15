// The price axis' own buttons, as on TradingView: the «+» beside the axis at the crosshair's price
// (alert, a paper order, a horizontal line, a note, a long or short position there), the quote
// currency at the top of the axis (on mouse-over, always or never), and the settings gear in the
// corner under it.
import { memo, useEffect, useRef, useState } from 'react';
import type { IChartApi, ISeriesApi, MouseEventParams, SeriesType, Time } from 'lightweight-charts';
import { usePrefs } from '../state/prefs';
import { useChartSettings } from '../state/chartSettings';
import { useMarket } from '../state/market';
import { instrumentOf } from '../data/symbols';
import { usePaper } from '../state/paper';
import { usePanels } from '../state/panels';
import { useL } from '../pages/kit';
import { Icon } from '../ui/Icon';
import { formatPrice } from './format';
import { MI, MSep, PopMenu } from './menuKit';
import { addHorizontalLine, addPosition, addTextNote, offerOrderTicket, openAlertDialog } from './actions';
import { openChartSettings } from '../shell/SettingsDialog';
import './chart-x.css';

export const AxisPlus = memo(function AxisPlus({ api, series, digits, onAlertAt }: { api: IChartApi | null; series: ISeriesApi<SeriesType> | null; digits: number; onAlertAt?(price: number | null): void }) {
  const L = useL();
  const on = useChartSettings((s) => s.plusButton);
  const leftSide = useChartSettings((s) => s.scalePlacement === 'left');
  const symbol = usePrefs((s) => s.symbol);
  const [at, setAt] = useState<{ y: number; price: number } | null>(null);
  const [menu, setMenu] = useState<{ x: number; y: number; price: number } | null>(null);
  const over = useRef(false);
  void onAlertAt;
  useEffect(() => {
    if (!api || !series || !on) { setAt(null); return; }
    const move = (p: MouseEventParams<Time>) => {
      if (over.current) return;
      if (!p.point || (p.paneIndex ?? 0) !== 0) { setAt(null); return; }
      const price = series.coordinateToPrice(p.point.y);
      setAt(typeof price === 'number' ? { y: p.point.y, price } : null);
    };
    api.subscribeCrosshairMove(move);
    return () => api.unsubscribeCrosshairMove(move);
  }, [api, series, on]);
  if (!api || !on || (!at && !menu)) return null;
  const leftW = api.priceScale('left').width();
  // Beside the price axis: at the pane's right edge, or its left edge when the scale is on the left.
  const x = leftSide ? leftW + 4 : leftW + api.timeScale().width() - 22;
  const y = menu ? null : at?.y;
  const px = menu?.price ?? at?.price ?? 0;
  const label = formatPrice(px, digits);
  const last = useMarket.getState().tickers[symbol]?.last ?? px;
  const qty = Number((1000 / (px || 1)).toPrecision(2));
  const order = (side: 'long' | 'short') => {
    if (offerOrderTicket({ symbol, side: side === 'long' ? 'buy' : 'sell', price: px, source: 'axis-plus', ...(px !== last ? { type: 'limit' as const } : {}) })) return;
    const type = (side === 'long') === (px < last) ? 'limit' : 'stop';
    usePaper.getState().place({ symbol, side, type, qty, ...(type === 'limit' ? { limitPrice: px } : { stopPrice: px }) }, last);
    if (usePanels.getState().bottom !== 'paper') usePanels.getState().setBottom('paper');
  };
  const kind = (side: 'long' | 'short') => ((side === 'long') === (px < last) ? L('محدود', 'limit') : L('توقف', 'stop'));
  return (
    <>
      {y != null ? (
        <button type="button" className="axis-plus" style={{ left: x, top: y - 9 }} title={L('افزودن', 'Add')} aria-label={L('افزودن در این قیمت', 'Add at this price')}
          onMouseEnter={() => { over.current = true; }} onMouseLeave={() => { over.current = false; }}
          onClick={(e) => { const r = e.currentTarget.getBoundingClientRect(); over.current = false; setMenu({ x: leftSide ? r.left : r.right, y: r.bottom + 2, price: at!.price }); }}>
          <Icon name="ph-plus" size="sm" />
        </button>
      ) : null}
      {menu ? (
        <PopMenu x={menu.x} y={menu.y} anchorRight={!leftSide} onClose={() => setMenu(null)} className="axis-plus-menu" label={label}>
          <MI icon="ph-bell" act="plus-alert" label={<>{L('هشدار روی', 'Add alert on')} <span className="sym">{symbol}</span> {L('در', 'at')} <span className="num">{label}</span></>} hint="Alt+A" onClick={() => openAlertDialog({ symbol, price: menu.price })} />
          <MI icon="ph-arrow-down" act="plus-sell" label={<>{L('فروش', 'Sell')} <span className="num">{qty}</span> <span className="sym">{symbol}</span> @ <span className="num">{label}</span> {kind('short')}</>} onClick={() => order('short')} />
          <MI icon="ph-arrow-up" act="plus-buy" label={<>{L('خرید', 'Buy')} <span className="num">{qty}</span> <span className="sym">{symbol}</span> @ <span className="num">{label}</span> {kind('long')}</>} onClick={() => order('long')} />
          <MSep />
          <MI icon="tool_hline" act="plus-hline" label={<>{L('خط افقی در', 'Draw horizontal line at')} <span className="num">{label}</span></>} hint="Alt+H" onClick={() => addHorizontalLine(menu.price)} />
          <MI icon="ph-note" act="plus-note" label={<>{L('یادداشت متنی در', 'Add text note at')} <span className="num">{label}</span></>} onClick={() => addTextNote(menu.price, null)} />
          <MI icon="tool_longshort" act="plus-long" label={L('پوزیشن خرید از این قیمت', 'Long position here')} onClick={() => addPosition('long', menu.price, null)} />
          <MI icon="tool_short" act="plus-short" label={L('پوزیشن فروش از این قیمت', 'Short position here')} onClick={() => addPosition('short', menu.price, null)} />
        </PopMenu>
      ) : null}
    </>
  );
});

/** The quote currency at the top of the price axis, and the settings gear in the axes' corner. */
export const AxisChips = memo(function AxisChips({ api, symbol, paneHeight }: { api: IChartApi | null; symbol: string; paneHeight: number }) {
  void paneHeight;
  const L = useL();
  const mode = useChartSettings((s) => s.currencyLabel);
  const left = useChartSettings((s) => s.scalePlacement === 'left');
  const inst = useMarket((m) => m.instruments[symbol]) ?? instrumentOf(symbol);
  const ref = useRef<HTMLDivElement>(null);
  const [hover, setHover] = useState(false);
  useEffect(() => {
    const host = ref.current?.parentElement;
    if (!host || !api || mode !== 'hover') return;
    const move = (e: MouseEvent) => {
      const r = host.getBoundingClientRect();
      const w = api.priceScale(left ? 'left' : 'right').width();
      const x = e.clientX - r.left;
      setHover(left ? x < w : x > r.width - w);
    };
    const leave = () => setHover(false);
    host.addEventListener('mousemove', move);
    host.addEventListener('mouseleave', leave);
    return () => { host.removeEventListener('mousemove', move); host.removeEventListener('mouseleave', leave); };
  }, [api, mode, left]);
  if (!api) return <div ref={ref} hidden />;
  const w = api.priceScale(left ? 'left' : 'right').width();
  const show = mode === 'always' || (mode === 'hover' && hover);
  return (
    <div ref={ref} className="axis-chips">
      {show && w ? <span className={`axis-ccy num${left ? ' left' : ''}`} style={left ? { left: 0, width: w } : { right: 0, width: w }}>{inst.quote}</span> : null}
      {w ? (
        <button type="button" className={`axis-gear${left ? ' left' : ''}`} style={{ [left ? 'left' : 'right']: 0, bottom: 0, width: w, height: api.timeScale().height() }}
          title={L('تنظیمات', 'Settings')} aria-label={L('تنظیمات', 'Settings')} onClick={() => openChartSettings('scales')}>
          <Icon name="ph-gear" size="sm" />
        </button>
      ) : null}
    </div>
  );
});
