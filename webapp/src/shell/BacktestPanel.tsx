import { useScript } from '../state/script';
import { usePrefs, useT } from '../state/prefs';
import { instrumentOf } from '../data/symbols';
import { formatPrice } from '../chart/format';
import { usePanels } from '../state/panels';

// TradingView's Strategy Tester, for the NamaScript strategy on the chart: the overview figures,
// the equity curve, and the trade list. Results are in price points per one unit, as the engine
// keeps them (no position sizing), and every figure is Latin.
export function BacktestPanel() {
  const t = useT();
  const symbol = usePrefs((s) => s.symbol);
  const result = useScript((s) => s.result);
  const applied = useScript((s) => s.applied);
  const setBottom = usePanels((s) => s.setBottom);
  const st = result?.ok ? result.strategy : null;
  const digits = instrumentOf(symbol).digits;
  const fmt = (v: number) => formatPrice(v, Math.min(digits, 4));

  if (!applied || !st) {
    return (
      <div className="bt-empty fa">
        <p>{t('btNeedsStrategy')}</p>
        <button className="btn btn-primary sm" onClick={() => setBottom('namascript')}><span className="fa">{t('namascript')}</span></button>
      </div>
    );
  }

  const eq = st.equity;
  const min = Math.min(0, ...eq.map((p) => p.e));
  const max = Math.max(0, ...eq.map((p) => p.e));
  const W = 600, H = 120;
  const path = eq.map((p, i) => `${i ? 'L' : 'M'}${(i / Math.max(1, eq.length - 1)) * W},${H - ((p.e - min) / (max - min || 1)) * H}`).join(' ');
  const zeroY = H - ((0 - min) / (max - min || 1)) * H;

  const stats: [string, string, string?][] = [
    ['Net profit', fmt(st.net), st.net >= 0 ? 'up' : 'down'],
    ['Total trades', String(st.trades)],
    ['Win rate', `${st.win}%`],
    ['Profit factor', st.pf.toFixed(2)],
    ['Max drawdown', fmt(st.dd), 'down'],
    ['Avg trade', fmt(st.avgTrade), st.avgTrade >= 0 ? 'up' : 'down'],
    ['Sharpe', st.sharpe.toFixed(2)],
    ['Long / Short', `${st.longN} / ${st.shortN}`],
  ];

  return (
    <div className="bt">
      <div className="bt-stats">
        {stats.map(([k, v, tone]) => (
          <div key={k} className="bt-stat"><span className="bt-k">{k}</span><span className={`bt-v num ${tone ?? ''}`}>{v}</span></div>
        ))}
      </div>
      <div className="bt-body">
        <svg className="bt-equity" viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" aria-label="equity">
          <line x1="0" x2={W} y1={zeroY} y2={zeroY} className="bt-zero" />
          <path d={path} className={st.net >= 0 ? 'up' : 'down'} />
        </svg>
        <div className="bt-trades">
          <div className="bt-row bt-head"><span>#</span><span>Type</span><span>Exit</span><span>P&amp;L</span></div>
          {st.list.slice().reverse().map((tr, i) => (
            <div key={i} className="bt-row">
              <span className="num">{st.list.length - i}</span>
              <span className={tr.dir === 1 ? 'up' : 'down'}>{tr.dir === 1 ? 'Long' : 'Short'}</span>
              <span className="num">{new Date(tr.t * 1000).toISOString().slice(0, 16).replace('T', ' ')}</span>
              <span className={`num ${tr.r >= 0 ? 'up' : 'down'}`}>{tr.r >= 0 ? '+' : '−'}{fmt(Math.abs(tr.r))}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
