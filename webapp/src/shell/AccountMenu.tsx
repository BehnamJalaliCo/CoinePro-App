import { useEffect, useRef, useState } from 'react';
import { usePrefs, useT, type Theme } from '../state/prefs';
import { useSession } from '../data/session';
import { signOut } from '../data/auth';
import { Icon } from '../ui/Icon';

// The round button at the top right, as on TradingView: the reader's initial (or a person glyph for
// a guest) opening the menu with the account, theme and language.
export function AccountMenu({ onSignIn, onPremium }: { onSignIn(): void; onPremium(): void }) {
  const t = useT();
  const sessions = useSession((s) => s.sessions);
  const { theme, setTheme, lang, setLang } = usePrefs();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const user = sessions.tradeyar?.user ?? sessions.coineprofx?.user ?? null;
  const signed = Boolean(sessions.tradeyar || sessions.coineprofx);
  const initial = (user?.name ?? user?.email ?? '').trim().charAt(0).toUpperCase();

  useEffect(() => {
    if (!open) return;
    const close = (e: MouseEvent) => { if (!ref.current?.contains(e.target as Node)) setOpen(false); };
    const esc = (e: KeyboardEvent) => { if (e.key === 'Escape') setOpen(false); };
    document.addEventListener('mousedown', close);
    document.addEventListener('keydown', esc);
    return () => { document.removeEventListener('mousedown', close); document.removeEventListener('keydown', esc); };
  }, [open]);

  const themes: { id: Theme; label: 'light' | 'dark' | 'system' }[] = [{ id: 'light', label: 'light' }, { id: 'dark', label: 'dark' }, { id: 'system', label: 'system' }];

  return (
    <div className="account" ref={ref}>
      <button className={`avatar${signed ? ' signed' : ''}`} aria-haspopup="menu" aria-expanded={open} title={t('account')} onClick={() => setOpen(!open)}>
        {signed && initial ? <span>{initial}</span> : <Icon name="ph-user-circle" size="md" />}
      </button>
      {open ? (
        <div className="menu account-menu" role="menu">
          <div className="menu-user">
            <span className="fa">{user?.name ?? (signed ? t('account') : t('guest'))}</span>
            {user?.email ? <span className="num muted">{user.email}</span> : null}
          </div>
          <div className="menu-sep" />
          <div className="menu-row">
            <span className="fa">{t('theme')}</span>
            <div className="seg">
              {themes.map((x) => (
                <button key={x.id} className={theme === x.id ? 'on' : ''} aria-pressed={theme === x.id} onClick={() => setTheme(x.id)}>
                  <span className="fa">{t(x.label)}</span>
                </button>
              ))}
            </div>
          </div>
          <div className="menu-row">
            <span className="fa">{t('language')}</span>
            <div className="seg">
              <button className={lang === 'fa' ? 'on' : ''} aria-pressed={lang === 'fa'} onClick={() => setLang('fa')}>فارسی</button>
              <button className={lang === 'en' ? 'on' : ''} aria-pressed={lang === 'en'} onClick={() => setLang('en')}>English</button>
            </div>
          </div>
          <button role="menuitem" className="menu-item premium-item" onClick={() => { setOpen(false); onPremium(); }}>
            <Icon name="ph-crown" size="sm" /><span className="fa">{t('premium')}</span>
          </button>
          <div className="menu-sep" />
          {signed ? (
            <button role="menuitem" className="menu-item" onClick={() => { signOut(); setOpen(false); }}>
              <Icon name="ph-sign-out" size="sm" /><span className="fa">{t('signOut')}</span>
            </button>
          ) : (
            <button role="menuitem" className="menu-item" onClick={() => { setOpen(false); onSignIn(); }}>
              <Icon name="ph-sign-in" size="sm" /><span className="fa">{t('signIn')}</span>
            </button>
          )}
        </div>
      ) : null}
    </div>
  );
}
