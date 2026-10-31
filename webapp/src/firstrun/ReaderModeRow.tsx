// The main menu's row for the first run's one question (OLD ReaderMode): how much the chrome shows.
// Changing it is one tap, either way, with nothing lost.
import { usePrefs } from '../state/prefs';
import { useL } from '../pages/kit';
import { Icon } from '../ui/Icon';
import { useFirstRun, type ReaderMode } from './store';

export function ReaderModeRow() {
  const L = useL();
  const fa = usePrefs((s) => s.lang) === 'fa';
  const mode = useFirstRun((s) => s.reader) ?? 'trader';
  const set = useFirstRun((s) => s.setReader);
  const opts: { id: ReaderMode; label: string }[] = [
    { id: 'simple', label: L('ساده', 'Simple') },
    { id: 'trader', label: L('معامله‌گر', 'Trader') },
    { id: 'pro', label: L('حرفه‌ای', 'Pro') },
  ];
  return (
    <div className="drawer-row lang-row reader-row">
      <Icon name="ph-sliders-horizontal" size="sm" /><span className={fa ? 'fa' : ''}>{L('نمایش', 'Show')}</span>
      <div className="seg">
        {opts.map((o) => <button key={o.id} className={mode === o.id ? 'on' : ''} aria-pressed={mode === o.id} data-mode={o.id} onClick={() => set(o.id)}>{o.label}</button>)}
      </div>
    </div>
  );
}
