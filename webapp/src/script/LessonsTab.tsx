import { useState } from 'react';
import { usePrefs } from '../state/prefs';
import { useL } from '../pages/kit';
import { LESSONS } from './lessons';
import { useCount } from './ScriptSheets';

// «آموزش»: the course, the first lesson open on arrival (a wall of twelve collapsed titles is a table
// of contents, not a course). Each example goes straight into the editor.
export function LessonsTab({ onTryExample }: { onTryExample(code: string, title: string): void }) {
  const L = useL();
  const n = useCount();
  const fa = usePrefs((s) => s.lang) === 'fa';
  const [open, setOpen] = useState(LESSONS[0]!.id);
  return (
    <div className="stack sx-lessons">
      {LESSONS.map((l, i) => {
        const on = open === l.id;
        const body = fa ? l.body : l.bodyEn;
        return (
          <article key={l.id} className={`card sx-lesson${on ? ' on' : ''}`} data-lesson={l.id}>
            <button className="sx-lesson-head" aria-expanded={on} onClick={() => setOpen(on ? '' : l.id)}>
              <b className="fa">{n(i + 1)}. {fa ? l.title : l.titleEn}</b>
            </button>
            {!on ? <p className="muted fa sx-clamp">{body[0]}</p> : (
              <>
                {body.map((p, k) => <p key={k} className="fa" dir="auto">{p}</p>)}
                {l.example ? (
                  <>
                    <pre className="sx-code" dir="ltr">{l.example}</pre>
                    <button className="btn btn-quiet sm sx-try" onClick={() => onTryExample(l.example!, fa ? l.title : l.titleEn)}>{L('اجرا در ویرایشگر', 'Open in the editor')}</button>
                  </>
                ) : null}
                <p className="sx-takeaway fa">{fa ? l.takeaway : l.takeawayEn}</p>
              </>
            )}
          </article>
        );
      })}
    </div>
  );
}
