import { useEffect, useMemo, useState } from 'react';
import { createPortal } from 'react-dom';
import { GROUPS, PAGES } from '../pages/registry';
import { usePage } from '../state/page';
import { isDark, usePrefs, useT } from '../state/prefs';
import { useDrawing } from '../state/drawing';
import { useSession } from '../data/session';
import { Icon } from '../ui/Icon';
import { useL } from '../pages/kit';
import './topbar.css';

// The menu behind the button at the top left, TradingView's: the help centre and the keyboard
// shortcuts, the dark theme and drawings-toolbar switches and the language, then every screen of
// the app in the app's own groups, with a search across them.
type RailState = { railHidden?: boolean; setRailHidden?(on: boolean): void };

export function MainMenu({ onClose, onSignIn }: { onClose(): void; onSignIn(): void }) {
  const t = useT();
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const theme = usePrefs((s) => s.theme);
  const setTheme = usePrefs((s) => s.setTheme);
  const setLang = usePrefs((s) => s.setLang);
  const open = usePage((s) => s.open);
  const rail = useDrawing((s) => s as unknown as RailState);
  const sessions = useSession((s) => s.sessions);
  const signed = Boolean(sessions.tradeyar || sessions.coineprofx);
  const user = sessions.tradeyar?.user ?? sessions.coineprofx?.user ?? null;
  const [q, setQ] = useState('');
  const fa = lang === 'fa';
  useEffect(() => {
    const esc = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', esc);
    return () => window.removeEventListener('keydown', esc);
  }, [onClose]);
  const needle = q.trim().toLowerCase();
  const pages = useMemo(() => PAGES.filter((p) => p.group && (!needle || p.fa.includes(q.trim()) || p.en.toLowerCase().includes(needle))), [needle, q]);
  const dark = isDark(theme);
  const go = (fn: () => void) => () => { fn(); onClose(); };
  return createPortal(
    <div className="scrim drawer-scrim" dir={fa ? 'rtl' : 'ltr'} onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
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
          {!needle ? (
            <section className="drawer-tv">
              <button className="drawer-row" onClick={go(() => open('markets'))}>
                <Icon name="ph-squares-four" size="sm" /><span className={fa ? 'fa' : ''}>{L('خانه‌ی بازار', 'Home')}</span>
              </button>
              <button className="drawer-row" onClick={go(() => open('help'))}>
                <Icon name="ph-question" size="sm" /><span className={fa ? 'fa' : ''}>{L('مرکز راهنما', 'Help Center')}</span>
              </button>
              <button className="drawer-row" onClick={go(() => open('help', 'shortcuts'))}>
                <Icon name="ph-keyboard" size="sm" /><span className={fa ? 'fa' : ''}>{L('میانبرهای صفحه‌کلید', 'Keyboard shortcuts')}</span><kbd className="num">?</kbd>
              </button>
              <div className="menu-sep" />
              <button className="drawer-row" role="switch" aria-checked={dark} onClick={() => setTheme(dark ? 'light' : 'dark')}>
                <Icon name="ph-moon" size="sm" /><span className={fa ? 'fa' : ''}>{L('تم تیره', 'Dark theme')}</span>
                <span className={`switch${dark ? ' on' : ''}`} aria-hidden><i /></span>
              </button>
              {rail.setRailHidden ? (
                <button className="drawer-row" role="switch" aria-checked={!rail.railHidden} onClick={() => rail.setRailHidden?.(!rail.railHidden)}>
                  <Icon name="ph-pencil-simple" size="sm" /><span className={fa ? 'fa' : ''}>{L('نوار ابزار ترسیم', 'Drawings panel')}</span>
                  <span className={`switch${!rail.railHidden ? ' on' : ''}`} aria-hidden><i /></span>
                </button>
              ) : null}
              <div className="drawer-row lang-row">
                <Icon name="ph-translate" size="sm" /><span className={fa ? 'fa' : ''}>{L('زبان', 'Language')}</span>
                <div className="seg">
                  <button className={lang === 'fa' ? 'on' : ''} aria-pressed={lang === 'fa'} onClick={() => setLang('fa')}>فارسی</button>
                  <button className={lang === 'en' ? 'on' : ''} aria-pressed={lang === 'en'} onClick={() => setLang('en')}>English</button>
                </div>
              </div>
            </section>
          ) : null}
          {GROUPS.map((g) => {
            const rows = pages.filter((p) => p.group === g.id);
            if (!rows.length) return null;
            return (
              <section key={g.id}>
                <h3 className="drawer-group fa">{fa ? g.fa : g.en}</h3>
                {rows.map((p) => (
                  <button key={p.id} className="drawer-item" onClick={() => { open(p.id); onClose(); }}>
                    <Icon name={p.icon} size="sm" />
                    <span className={fa ? 'fa' : ''}>{fa ? p.fa : p.en}</span>
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
