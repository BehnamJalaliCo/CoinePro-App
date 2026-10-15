import { useMarket } from '../state/market';
import { usePrefs, useT } from '../state/prefs';
import { SymbolLogo } from '../ui/SymbolLogo';
import { formatPrice, formatVolume } from '../chart/format';
import { instrumentOf } from '../data/symbols';
import { marketStatus } from '../data/sessions';

// Under the watchlist, as on TradingView: the open symbol's name, its last price large, the day's
// change, whether the market is open, then the day's range and volume.
export function SymbolDetails() {
  const t = useT();
  const lang = usePrefs((s) => s.lang);
  const symbol = usePrefs((s) => s.symbol);
  const tk = useMarket((s) => s.tickers[symbol]);
  const inst = useMarket((s) => s.instruments[symbol]) ?? instrumentOf(symbol, tk?.last);
  const digits = inst.digits;
  const name = lang === 'fa' ? inst.nameFa : inst.nameEn;
  const tone = tk ? (tk.change >= 0 ? 'up' : 'down') : '';
  const open = marketStatus(inst.market);
  const range = tk?.high != null && tk.low != null && tk.high > tk.low ? (tk.last - tk.low) / (tk.high - tk.low) : null;

  return (
    <section className="details" aria-label={symbol}>
      <div className="details-head">
        <SymbolLogo symbol={symbol} size={28} />
        <div className="details-names">
          <span className="sym details-sym">{symbol}</span>
          {name ? <span className={`details-name${lang === 'fa' ? ' fa' : ''}`}>{name}</span> : null}
        </div>
      </div>
      <div className="details-price">
        <span className="num details-last">{tk ? formatPrice(tk.last, digits) : '—'}</span>
        <span className="details-quote num">{inst.quote}</span>
      </div>
      {tk ? (
        <div className={`details-change num ${tone}`}>
          {tk.change >= 0 ? '+' : '−'}{formatPrice(Math.abs(tk.change), digits)}{'  '}
          {tk.changePct >= 0 ? '+' : '−'}{Math.abs(tk.changePct).toFixed(2)}%
        </div>
      ) : null}
      <div className={`details-status ${open ? 'open' : 'closed'}`}>
        <span className="dot" /><span className="fa">{t(open ? 'marketOpen' : 'marketClosed')}</span>
      </div>
      <h3 className="details-title fa">{t('keyStats')}</h3>
      {tk?.high != null && tk.low != null ? (
        <div className="details-range">
          <div className="row"><span className="fa">{t('dayRange')}</span></div>
          <div className="bar"><span className="fill" style={{ left: `${Math.max(0, Math.min(1, range ?? 0)) * 100}%` }} /></div>
          <div className="row num"><span>{formatPrice(tk.low, digits)}</span><span>{formatPrice(tk.high, digits)}</span></div>
        </div>
      ) : null}
      <dl className="details-stats">
        {tk?.volume != null ? (<><dt className="fa">{t('volume24h')}</dt><dd className="num">{formatVolume(tk.volume)}</dd></>) : null}
        {tk?.prevClose != null ? (<><dt className="fa">{t('prevClose')}</dt><dd className="num">{formatPrice(tk.prevClose, digits)}</dd></>) : null}
      </dl>
    </section>
  );
}
