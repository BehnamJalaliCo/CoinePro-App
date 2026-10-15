import { useReplay, SPEED_LADDER, replayPnl } from '../state/replay';
import { useChartBus } from '../chart/chartBus';
import { useT } from '../state/prefs';
import { Icon } from '../ui/Icon';

// The replay controls TradingView floats over the chart's bottom edge: back to selecting a bar,
// step back, play/pause, step forward, speed, and leave.
export function ReplayBar() {
  const t = useT();
  const { selecting, state, controller, select, exit, slice, trades, trade, flatten, start } = useReplay();
  const bars = useChartBus((b) => b.bars);
  const randomBar = () => {
    if (bars.length < 60) return;
    const i = Math.floor(bars.length * (0.2 + Math.random() * 0.6));
    start(bars, bars[i]!.t);
  };
  const jump = (n: number) => { const idx = (state?.idx ?? 0) + n; controller.seek({ index: Math.max(0, Math.min((state?.length ?? 1) - 1, idx)) }); };
  const price = slice?.[slice.length - 1]?.c ?? 0;
  const result = trades.reduce((n, t) => n + replayPnl(t, price), 0);
  const open = trades.find((t) => t.exit == null);
  if (!selecting && !state?.on) return null;
  if (selecting) {
    return (
      <div className="replay-bar selecting">
        <Icon name="ph-rewind" size="sm" />
        <span className="fa">{t('replayPick')}</span>
        <button className="btn btn-quiet sm" onClick={randomBar} disabled={bars.length < 60}><Icon name="ph-shuffle" size="sm" /><span>Random bar</span></button>
        <button className="lt" onClick={exit} aria-label="close"><Icon name="ph-x" size="sm" /></button>
      </div>
    );
  }
  return (
    <div className="replay-bar" role="toolbar" aria-label={t('replay')}>
      <button className="ib" title="select bar" onClick={select}><Icon name="ph-target" size="sm" /></button>
      <button className="ib num" title="−10 bars" onClick={() => jump(-10)}>−10</button>
      <button className="ib" title="step back" onClick={() => controller.stepBack()}><Icon name="ph-skip-back" size="sm" /></button>
      <button className="ib" title="play" onClick={() => controller.toggle()}><Icon name={state?.playing ? 'ph-pause' : 'ph-play'} size="md" /></button>
      <button className="ib" title="step" onClick={() => controller.step()}><Icon name="ph-skip-forward" size="sm" /></button>
      <button className="ib num" title="+10 bars" onClick={() => jump(10)}>+10</button>
      <select className="replay-speed num" value={state?.speed ?? 1} onChange={(e) => controller.setSpeed(Number(e.target.value))}>
        {SPEED_LADDER.map((s) => <option key={s} value={s}>{s}x</option>)}
      </select>
      <span className="num muted">{state ? `${state.idx + 1} / ${state.length}` : ''}</span>
      <span className="sep" />
      <button className="btn buy sm" title="Buy (practice)" onClick={() => trade('long')} disabled={open?.side === 'long'}>Buy</button>
      <button className="btn sell sm" title="Sell (practice)" onClick={() => trade('short')} disabled={open?.side === 'short'}>Sell</button>
      {open ? <button className="btn btn-quiet sm" onClick={flatten}>Flatten</button> : null}
      {trades.length ? <span className={`num ${result >= 0 ? 'up' : 'down'}`} title={`${trades.length} trades`}>{result >= 0 ? '+' : '−'}{Math.abs(result).toFixed(2)}%</span> : null}
      <button className="ib" title="jump to real time" onClick={exit}><Icon name="ph-fast-forward" size="sm" /></button>
      <button className="ib" title="exit" onClick={exit}><Icon name="ph-x" size="sm" /></button>
    </div>
  );
}
