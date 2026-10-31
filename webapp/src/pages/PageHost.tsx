import { Suspense, useEffect } from 'react';
import { usePage } from '../state/page';
import { usePrefs, useT } from '../state/prefs';
import { useSession } from '../data/session';
import { pageById } from './registry';
import { Icon } from '../ui/Icon';
import './home2.css';
import { TEACHING_DISMISS, TEACHING_WHAT, teachingFor, useTeaching, type TeachingSurface } from './teaching';

// A page over the workspace, as the app's screens sit over its chart: a header with the way back
// and the title, then the page. A member page asks a guest to sign in first.
export function PageHost({ onSignIn }: { onSignIn(): void }) {
  const t = useT();
  const lang = usePrefs((s) => s.lang);
  const { page, param, close } = usePage();
  const signed = useSession((s) => Boolean(s.sessions.tradeyar || s.sessions.coineprofx));
  useEffect(() => {
    if (!page) return;
    const esc = (e: KeyboardEvent) => { if (e.key === 'Escape' && !(e.target as HTMLElement).closest('input, textarea, select, .dialog')) close(); };
    window.addEventListener('keydown', esc);
    return () => window.removeEventListener('keydown', esc);
  }, [page, close]);
  const teach = teachingFor(page);
  const restore = useTeaching((s) => s.restore);
  if (!page) return null;
  const def = pageById(page);
  const Body = def.component;
  const gated = Boolean(def.member && !signed);
  return (
    <div className="page" role="dialog" dir={lang === 'fa' ? 'rtl' : 'ltr'} aria-label={lang === 'fa' ? def.fa : def.en}>
      <header className="page-head">
        <button className="ib" onClick={close} aria-label={t('back')}><Icon name={lang === 'fa' ? 'ph-arrow-right' : 'ph-arrow-left'} size="md" /></button>
        <Icon name={def.icon} size="md" />
        <h1 className={lang === 'fa' ? 'fa' : ''}>{lang === 'fa' ? def.fa : def.en}</h1>
        <span className="ws-spacer" />
        {teach ? <button className="ib teach-what" onClick={() => restore(teach.key)} aria-label={lang === 'fa' ? TEACHING_WHAT[0] : TEACHING_WHAT[1]} title={lang === 'fa' ? TEACHING_WHAT[0] : TEACHING_WHAT[1]}><Icon name="ph-question" size="md" /></button> : null}
        <button className="ib" onClick={close} aria-label="close"><Icon name="ph-x" size="md" /></button>
      </header>
      <div className="page-body">
        {teach && !gated ? <TeachingStrip surface={teach} /> : null}
        {gated ? (
          <div className="gate">
            <Icon name="ph-sign-in" size="lg" />
            <p className="fa">{t('memberOnly')}</p>
            <button className="btn btn-primary" onClick={onSignIn}>{t('signIn')}</button>
          </div>
        ) : (
          <Suspense fallback={<div className="empty fa">{t('loading')}…</div>}>
            <Body param={param} onSignIn={onSignIn} />
          </Suspense>
        )}
      </div>
    </div>
  );
}

/**
 * The page's own explanation: what it does, and the one thing a first reader gets wrong (OLD
 * CoineProTeachingBanner). In the page's flow above everything else, so it pushes the page down and
 * never covers a control; × puts it away for good, the header's «؟» brings it back.
 */
function TeachingStrip({ surface }: { surface: TeachingSurface }) {
  const lang = usePrefs((s) => s.lang);
  const hidden = useTeaching((s) => s.dismissed.includes(surface.key));
  const dismiss = useTeaching((s) => s.dismiss);
  if (hidden) return null;
  const pick = (x: [string, string]) => (lang === 'fa' ? x[0] : x[1]);
  return (
    <aside className="teach-strip" data-teach={surface.key} dir={lang === 'fa' ? 'rtl' : 'ltr'}>
      <Icon name="ph-info" size="sm" />
      <div className="teach-text">
        <p className="teach-lead">{pick(surface.lead)}</p>
        {surface.pitfall ? <p className="teach-pitfall">{pick(surface.pitfall)}</p> : null}
      </div>
      <button type="button" className="ib teach-close" onClick={() => dismiss(surface.key)} aria-label={pick(TEACHING_DISMISS)} title={pick(TEACHING_DISMISS)}><Icon name="ph-x" size="sm" /></button>
    </aside>
  );
}
