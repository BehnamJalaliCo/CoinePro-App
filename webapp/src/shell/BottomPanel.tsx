import { usePanels, type BottomTab } from '../state/panels';
import { useT } from '../state/prefs';
import { Icon } from '../ui/Icon';
import type { StringKey } from '../i18n/strings';
import { Screener } from './Screener';
import { NamaScriptPanel } from './NamaScriptPanel';
import { BacktestPanel } from './BacktestPanel';
import { PaperPanel } from './PaperPanel';

// Under the chart, where TradingView keeps Pine Editor and the Strategy Tester: the screener,
// NamaScript (our Pine), the backtest and paper trading. A tab opens the panel; the open tab
// closes it; the caret folds it.
const TABS: { id: BottomTab; label: StringKey }[] = [
  { id: 'screener', label: 'screener' },
  { id: 'namascript', label: 'namascript' },
  { id: 'backtest', label: 'backtest' },
  { id: 'paper', label: 'paperTrading' },
];

export function BottomPanel() {
  const t = useT();
  const bottom = usePanels((s) => s.bottom);
  const setBottom = usePanels((s) => s.setBottom);
  return (
    <section className="ws-bottom">
      <nav className="ws-bottom-tabs" role="tablist">
        {TABS.map((x) => (
          <button key={x.id} role="tab" aria-selected={bottom === x.id} className={`tab${bottom === x.id ? ' on' : ''}`} onClick={() => setBottom(x.id)}>
            <span className="fa">{t(x.label)}</span>
          </button>
        ))}
        <span className="ws-spacer" />
        {bottom ? (
          <button className="ib" onClick={() => setBottom(bottom)} aria-label="collapse"><Icon name="ph-caret-down" size="sm" /></button>
        ) : (
          <button className="ib" onClick={() => setBottom('namascript')} aria-label="expand"><Icon name="ph-caret-up" size="sm" /></button>
        )}
      </nav>
      {bottom ? (
        <div className="ws-bottom-body" role="tabpanel">
          {bottom === 'screener' ? <Screener /> : bottom === 'namascript' ? <NamaScriptPanel /> : bottom === 'backtest' ? <BacktestPanel /> : <PaperPanel />}
        </div>
      ) : null}
    </section>
  );
}
