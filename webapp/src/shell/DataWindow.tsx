import { useChartBus } from '../chart/chartBus';
import { usePrefs, useT } from '../state/prefs';
import { useMarket } from '../state/market';
import { formatPrice, formatVolume } from '../chart/format';
import { instrumentOf } from '../data/symbols';

// TradingView's data window: the values of the bar under the crosshair (the last bar otherwise).
export function DataWindow() {
  const t = useT();
  const symbol = usePrefs((s) => s.symbol);
  const hover = useChartBus((b) => b.hover);
  const bars = useChartBus((b) => b.bars);
  const inst = useMarket((s) => s.instruments[symbol]) ?? instrumentOf(symbol);
  const bar = hover ?? bars[bars.length - 1];
  const d = new Date((bar?.t ?? 0) * 1000);
  const date = bar ? `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}-${String(d.getUTCDate()).padStart(2, '0')}` : '';
  const time = bar ? `${String(d.getUTCHours()).padStart(2, '0')}:${String(d.getUTCMinutes()).padStart(2, '0')}` : '';
  const rows: [string, string][] = bar ? [
    ['Date', date], ['Time', time],
    ['Open', formatPrice(bar.o, inst.digits)], ['High', formatPrice(bar.h, inst.digits)],
    ['Low', formatPrice(bar.l, inst.digits)], ['Close', formatPrice(bar.c, inst.digits)],
    ['Change', `${bar.c - bar.o >= 0 ? '+' : '−'}${formatPrice(Math.abs(bar.c - bar.o), inst.digits)}`],
    ['Vol', formatVolume(bar.v)],
  ] : [];
  return (
    <section className="panel-generic">
      <header className="panel-head"><span className="fa">{t('dataWindow')}</span></header>
      <div className="panel-body">
        <div className="dw-title sym">{symbol}</div>
        <dl className="dw">
          {rows.map(([k, v]) => (<div key={k} className="dw-row"><dt>{k}</dt><dd className="num">{v}</dd></div>))}
        </dl>
      </div>
    </section>
  );
}
