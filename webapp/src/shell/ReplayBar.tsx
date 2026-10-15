import { useReplay, SPEED_LADDER } from '../state/replay';
import { useT } from '../state/prefs';
import { Icon } from '../ui/Icon';

// The replay controls TradingView floats over the chart's bottom edge: back to selecting a bar,
// step back, play/pause, step forward, speed, and leave.
export function ReplayBar() {
  const t = useT();
  const { selecting, state, controller, select, exit } = useReplay();
  if (!selecting && !state?.on) return null;
  if (selecting) {
    return (
      <div className="replay-bar selecting">
        <Icon name="ph-rewind" size="sm" />
        <span className="fa">{t('replayPick')}</span>
        <button className="lt" onClick={exit} aria-label="close"><Icon name="ph-x" size="sm" /></button>
      </div>
    );
  }
  return (
    <div className="replay-bar" role="toolbar" aria-label={t('replay')}>
      <button className="ib" title="select bar" onClick={select}><Icon name="ph-target" size="sm" /></button>
      <button className="ib" title="step back" onClick={() => controller.stepBack()}><Icon name="ph-skip-back" size="sm" /></button>
      <button className="ib" title="play" onClick={() => controller.toggle()}><Icon name={state?.playing ? 'ph-pause' : 'ph-play'} size="md" /></button>
      <button className="ib" title="step" onClick={() => controller.step()}><Icon name="ph-skip-forward" size="sm" /></button>
      <select className="replay-speed num" value={state?.speed ?? 1} onChange={(e) => controller.setSpeed(Number(e.target.value))}>
        {SPEED_LADDER.map((s) => <option key={s} value={s}>{s}x</option>)}
      </select>
      <span className="num muted">{state ? `${state.idx + 1} / ${state.length}` : ''}</span>
      <button className="ib" title="exit" onClick={exit}><Icon name="ph-x" size="sm" /></button>
    </div>
  );
}
