// Whether a study can change after it is drawn, said on the study itself (OLD feature/chart
// RepaintClaim.kt): «repaint نمی‌کند», «با تأخیر قطعی می‌شود», «تا بسته‌شدن کندل تغییر می‌کند» or
// «عقب‌تر دوباره نوشته می‌شود», with its sentence on hover — in the legend and in the study's settings.
// A study this build has not read the arithmetic of gets no mark, never a guessed one.
import { usePrefs } from '../state/prefs';
import type { Study } from '../state/studies';
import { ownTimeframe } from './signalStore';
import { repaintLabel, repaintNote, repaintOf } from './signals';
import './signals.css';

export function claimOf(study: Study | undefined, interval: string) {
  return study ? repaintOf(study.key, { ownTimeframe: ownTimeframe(study, interval), waitClose: study.waitClose }) : null;
}

export function RepaintBadge({ study, withNote = false }: { study: Study | undefined; withNote?: boolean }) {
  const lang = usePrefs((s) => s.lang);
  const interval = usePrefs((s) => s.interval);
  const claim = claimOf(study, interval);
  if (!claim) return null;
  const en = lang !== 'fa';
  const badge = <span className={`repaint-badge${en ? '' : ' fa'}`} data-claim={claim} title={repaintNote(claim, en)} dir={en ? 'ltr' : 'rtl'}>{repaintLabel(claim, en)}</span>;
  if (!withNote) return badge;
  return <div className={`st-repaint${en ? '' : ' fa'}`} data-claim={claim}>{badge}<span>{repaintNote(claim, en)}</span></div>;
}
