import { Suspense, useEffect } from 'react';
import { usePage } from '../state/page';
import { usePrefs, useT } from '../state/prefs';
import { useSession } from '../data/session';
import { pageById } from './registry';
import { Icon } from '../ui/Icon';

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
  if (!page) return null;
  const def = pageById(page);
  const Body = def.component;
  return (
    <div className="page" role="dialog" dir={lang === 'fa' ? 'rtl' : 'ltr'} aria-label={lang === 'fa' ? def.fa : def.en}>
      <header className="page-head">
        <button className="ib" onClick={close} aria-label={t('back')}><Icon name={lang === 'fa' ? 'ph-arrow-right' : 'ph-arrow-left'} size="md" /></button>
        <Icon name={def.icon} size="md" />
        <h1 className={lang === 'fa' ? 'fa' : ''}>{lang === 'fa' ? def.fa : def.en}</h1>
        <span className="ws-spacer" />
        <button className="ib" onClick={close} aria-label="close"><Icon name="ph-x" size="md" /></button>
      </header>
      <div className="page-body">
        {def.member && !signed ? (
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
