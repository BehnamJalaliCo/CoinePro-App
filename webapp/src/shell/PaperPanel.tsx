import { useEffect, useState } from 'react';
import { usePaper, pnlOf } from '../state/paper';
import { useMarket, digitsOf } from '../state/market';
import { usePrefs, useT } from '../state/prefs';
import { instrumentOf } from '../data/symbols';
import { formatPrice } from '../chart/format';
import { SymbolLogo } from '../ui/SymbolLogo';
import { Icon } from '../ui/Icon';

// A practice account under the chart: buy or sell the open symbol at the live price, watch the
// positions move, close them into the history. Figures in USDT, Latin digits.
export function PaperPanel() {
  const t = useT();
  const symbol = usePrefs((s) => s.symbol);
  const tickers = useMarket((s) => s.tickers);
  const { balance, positions, history, open, close, reset } = usePaper();
  const tk = tickers[symbol];
  const digits = instrumentOf(symbol, tk?.last).digits;
  const [qty, setQty] = useState('');
  useEffect(() => { if (tk && !qty) setQty(String(Number((1000 / tk.last).toPrecision(2)))); }, [tk, qty]);
  const unrealized = positions.reduce((n, p) => n + (tickers[p.symbol] ? pnlOf(p, tickers[p.symbol]!.last) : 0), 0);
  const money = (v: number) => `${v < 0 ? '−' : ''}${Math.abs(v).toLocaleString('en-US', { maximumFractionDigits: 2, minimumFractionDigits: 2 })}`;

  return (
    <div className="paper">
      <div className="paper-account">
        <div className="bt-stat"><span className="bt-k">Balance</span><span className="bt-v num">{money(balance)} USDT</span></div>
        <div className="bt-stat"><span className="bt-k">Unrealized</span><span className={`bt-v num ${unrealized >= 0 ? 'up' : 'down'}`}>{money(unrealized)}</span></div>
        <div className="bt-stat"><span className="bt-k">Equity</span><span className="bt-v num">{money(balance + unrealized)}</span></div>
        <span className="ws-spacer" />
        <div className="paper-ticket">
          <SymbolLogo symbol={symbol} size={18} /><span className="sym">{symbol}</span>
          <span className="num muted">{tk ? formatPrice(tk.last, digits) : '—'}</span>
          <input className="num" type="number" min="0" step="any" value={qty} onChange={(e) => setQty(e.target.value)} aria-label="quantity" />
          <button className="btn buy sm" disabled={!tk} onClick={() => tk && open(symbol, 'long', Number(qty), tk.last)}>Buy</button>
          <button className="btn sell sm" disabled={!tk} onClick={() => tk && open(symbol, 'short', Number(qty), tk.last)}>Sell</button>
          <button className="lt" title="reset" onClick={reset}><Icon name="ph-arrow-counter-clockwise" size="sm" /></button>
        </div>
      </div>
      <div className="paper-lists">
        <div className="bt-trades">
          <div className="bt-row bt-head p5"><span>Symbol</span><span>Side</span><span>Qty</span><span>Entry</span><span>P&amp;L</span></div>
          {!positions.length ? <div className="empty fa">{t('noResults')}</div> : null}
          {positions.map((p) => {
            const px = tickers[p.symbol]?.last;
            const pnl = px ? pnlOf(p, px) : 0;
            const d = instrumentOf(p.symbol, px).digits;
            return (
              <div key={p.id} className="bt-row p5">
                <span className="sym">{p.symbol}</span>
                <span className={p.side === 'long' ? 'up' : 'down'}>{p.side === 'long' ? 'Long' : 'Short'}</span>
                <span className="num">{p.qty}</span>
                <span className="num">{formatPrice(p.entry, d)}</span>
                <span className={`num ${pnl >= 0 ? 'up' : 'down'}`}>{money(pnl)}
                  <button className="lt" title="close" disabled={!px} onClick={() => px && close(p.id, px)}><Icon name="ph-x" size="sm" /></button>
                </span>
              </div>
            );
          })}
        </div>
        <div className="bt-trades">
          <div className="bt-row bt-head p5"><span>Symbol</span><span>Side</span><span>Entry</span><span>Exit</span><span>P&amp;L</span></div>
          {history.map((h) => {
            const d = digitsOf(h.symbol);
            return (
              <div key={h.id} className="bt-row p5">
                <span className="sym">{h.symbol}</span>
                <span className={h.side === 'long' ? 'up' : 'down'}>{h.side === 'long' ? 'Long' : 'Short'}</span>
                <span className="num">{formatPrice(h.entry, d)}</span>
                <span className="num">{formatPrice(h.exit, d)}</span>
                <span className={`num ${h.pnl >= 0 ? 'up' : 'down'}`}>{money(h.pnl)}</span>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
