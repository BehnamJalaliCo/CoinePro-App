// «آموزش‌ها» in the menu (OLD CoineProCoach.kt CoachLibrary): every tip the coach teaches once,
// playable on demand, and the way to have them all again.
import { useState } from 'react';
import { usePrefs } from '../state/prefs';
import { usePage } from '../state/page';
import { useL } from '../pages/kit';
import { COACH_GROUPS, COACH_TIPS, tipTitle } from './tips';
import { useCoach } from './store';
import { touchFirst } from './anchors';
import './coach.css';

export default function CoachLibrary() {
  const L = useL();
  const fa = usePrefs((s) => s.lang) === 'fa';
  const play = useCoach((s) => s.play);
  const replayAll = useCoach((s) => s.replayAll);
  const [replayed, setReplayed] = useState(false);
  const [touch] = useState(touchFirst);
  return (
    <div className="page-inner coach-lib">
      <div className="coach-lead">
        <p className={fa ? 'fa' : ''}>{L('هر آموزش فقط یک بار و همان لحظه‌ای نشان داده می‌شود که اولین بار به آن قسمت می‌رسید. روی هر کدام بزنید تا دوباره ببینید، یا همه را برگردانید.', 'Each tip appears once, at the moment you first reach that part of the app. Tap one to watch it again, or bring them all back.')}</p>
        <button type="button" className={`coach-replay${replayed ? ' done' : ''}`} disabled={replayed} data-act="coach-replay-all"
          onClick={() => { replayAll(); setReplayed(true); }}>
          {replayed ? L('آموزش‌ها دوباره سر جایشان نشان داده می‌شوند.', 'The tips will show again as you reach each place.') : L('نمایش دوباره‌ی همه‌ی آموزش‌ها', 'Show all tips again')}
        </button>
      </div>
      <button type="button" className="coach-academy" onClick={() => usePage.getState().open('academy')}>
        <span className={fa ? 'fa' : ''}>{L('درس‌های آکادمی', 'Academy lessons')}</span>
      </button>
      {COACH_GROUPS.map((g) => {
        const tips = COACH_TIPS.filter((t) => t.group === g.id);
        if (!tips.length) return null;
        return (
          <section key={g.id}>
            <h3 className={`coach-group${fa ? ' fa' : ''}`}>{fa ? g.title.fa : g.title.en}</h3>
            {tips.map((t) => {
              const title = tipTitle(t, touch);
              return (
                <button key={t.id} type="button" className="coach-row" data-tip={t.id} onClick={() => play(t.id)} style={{ marginBottom: 8 }}>
                  {/* A play mark for an animation, a dot for a sentence. */}
                  <span className={`coach-play${t.scene ? '' : ' line'}`} aria-hidden><i /></span>
                  <span className="coach-row-text">
                    <b className={fa ? 'fa' : ''}>{fa ? title.fa : title.en}</b>
                    <span className={fa ? 'fa' : ''}>{t.scene ? L('انیمیشن', 'Animation') : L('توضیح کوتاه', 'Short tip')}</span>
                  </span>
                </button>
              );
            })}
          </section>
        );
      })}
    </div>
  );
}
