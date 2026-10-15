import { useEffect, useMemo, useState } from 'react';
import { createPortal } from 'react-dom';
import { GROUPS, PAGES } from '../pages/registry';
import { usePage } from '../state/page';
import { usePrefs, useT } from '../state/prefs';
import { useSession } from '../data/session';
import { Icon } from '../ui/Icon';

// The menu behind the button at the top left — TradingView's place for it — holding every screen
// of the app in the app's own groups, with a search across them.
export function MainMenu({ onClose, onSignIn }: { onClose(): void; onSignIn(): void }) {
  const t = useT();
  const lang = usePrefs((s) => s.lang);
  const open = usePage((s) => s.open);
  const sessions = useSession((s) => s.sessions);
  const signed = Boolean(sessions.tradeyar || sessions.coineprofx);
  const user = sessions.tradeyar?.user ?? sessions.coineprofx?.user ?? null;
  const [q, setQ] = useState('');
  useEffect(() => {
    const esc = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', esc);
    return () => window.removeEventListener('keydown', esc);
  }, [onClose]);
  const needle = q.trim().toLowerCase();
  const pages = useMemo(() => PAGES.filter((p) => p.group && (!needle || p.fa.includes(q.trim()) || p.en.toLowerCase().includes(needle))), [needle, q]);
  return createPortal(
    <div className="scrim drawer-scrim" dir={lang === 'fa' ? 'rtl' : 'ltr'} onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <nav className="drawer" aria-label="menu">
        <div className="drawer-user">
          <span className={`avatar${signed ? ' signed' : ''}`}>{signed ? (user?.name ?? user?.email ?? '?').charAt(0).toUpperCase() : <Icon name="ph-user-circle" size="md" />}</span>
          <div className="drawer-names">
            <b className="fa">{user?.name ?? (signed ? t('account') : t('guest'))}</b>
            {user?.email ? <span className="muted num">{user.email}</span> : null}
          </div>
          {!signed ? <button className="btn btn-primary sm" onClick={() => { onClose(); onSignIn(); }}>{t('signIn')}</button> : null}
        </div>
        <label className="search-field drawer-search">
          <Icon name="search" size="sm" />
          <input value={q} onChange={(e) => setQ(e.target.value)} placeholder={t('search')} autoFocus dir="auto" />
        </label>
        <div className="drawer-body">
          {GROUPS.map((g) => {
            const rows = pages.filter((p) => p.group === g.id);
            if (!rows.length) return null;
            return (
              <section key={g.id}>
                <h3 className="drawer-group fa">{lang === 'fa' ? g.fa : g.en}</h3>
                {rows.map((p) => (
                  <button key={p.id} className="drawer-item" onClick={() => { open(p.id); onClose(); }}>
                    <Icon name={p.icon} size="sm" />
                    <span className={lang === 'fa' ? 'fa' : ''}>{lang === 'fa' ? p.fa : p.en}</span>
                    {p.member && !signed ? <Icon name="lock" size="sm" className="muted" /> : null}
                  </button>
                ))}
              </section>
            );
          })}
        </div>
      </nav>
    </div>,
    document.body,
  );
}
