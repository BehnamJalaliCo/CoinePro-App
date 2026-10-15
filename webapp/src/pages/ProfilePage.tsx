import { useState } from 'react';
import { usePrefs, type Theme } from '../state/prefs';
import { usePage } from '../state/page';
import { useSession } from '../data/session';
import { signOut } from '../data/auth';
import { Field, Tabs, downloadText, pickText, useL } from './kit';
import type { PageProps } from './registry';

// Profile and settings (feature/profile, ProfileStore): the name and line others see on this
// device, an avatar, hiding balances, appearance and language, the archive of everything this
// browser keeps (export and restore), and the account's ways out.
const KEY = 'pc.profile.v1';
interface Profile { name: string; tagline: string; avatar: string; hideBalance: boolean }
const AVATARS = ['🦁', '🐺', '🦅', '🐂', '🐻', '🦊', '🐯', '🐬', '🚀', '💎', '📈', '🧠'];
const read = (): Profile => { try { return { name: '', tagline: '', avatar: '', hideBalance: false, ...(JSON.parse(localStorage.getItem(KEY) ?? '{}') as Partial<Profile>) }; } catch { return { name: '', tagline: '', avatar: '', hideBalance: false }; } };

/** Every key this app keeps in the browser, except the sign-in handles. */
export function archive(): Record<string, string> {
  const out: Record<string, string> = {};
  for (let i = 0; i < localStorage.length; i++) {
    const k = localStorage.key(i);
    if (k && k.startsWith('pc.') && k !== 'pc.sessions.v1') out[k] = localStorage.getItem(k) ?? '';
  }
  return out;
}

export function restore(data: unknown): number {
  if (!data || typeof data !== 'object') return 0;
  let n = 0;
  for (const [k, v] of Object.entries(data as Record<string, unknown>)) {
    if (!k.startsWith('pc.') || k === 'pc.sessions.v1' || typeof v !== 'string') continue;
    try { localStorage.setItem(k, v); n++; } catch { /* full */ }
  }
  return n;
}

export default function ProfilePage({ onSignIn }: PageProps) {
  const L = useL();
  const open = usePage((s) => s.open);
  const { theme, setTheme, lang, setLang } = usePrefs();
  const sessions = useSession((s) => s.sessions);
  const user = sessions.tradeyar?.user ?? sessions.coineprofx?.user ?? null;
  const signed = Boolean(sessions.tradeyar || sessions.coineprofx);
  const [p, setP] = useState<Profile>(read);
  const [msg, setMsg] = useState<string | null>(null);
  const save = (n: Profile) => { setP(n); try { localStorage.setItem(KEY, JSON.stringify(n)); } catch { /* ignore */ } };

  const upload = () => {
    const input = document.createElement('input');
    input.type = 'file'; input.accept = 'image/*';
    input.onchange = async () => {
      const f = input.files?.[0];
      if (!f) return;
      try {
        const bmp = await createImageBitmap(f);
        const c = document.createElement('canvas'); c.width = 128; c.height = 128;
        const s = Math.min(bmp.width, bmp.height);
        c.getContext('2d')!.drawImage(bmp, (bmp.width - s) / 2, (bmp.height - s) / 2, s, s, 0, 0, 128, 128);
        save({ ...p, avatar: c.toDataURL('image/jpeg', 0.85) });
      } catch { /* not an image */ }
    };
    input.click();
  };

  return (
    <div className="page-inner narrow stack profile">
      <section className="card">
        <div className="row">
          <span className="avatar big">{p.avatar.startsWith('data:') ? <img src={p.avatar} alt="" /> : p.avatar || (p.name || user?.name || '?').charAt(0).toUpperCase()}</span>
          <div className="stack tight">
            <b className="fa" dir="auto">{p.name || user?.name || L('مهمان', 'Guest')}</b>
            {user?.email ? <span className="num muted">{user.email}</span> : null}
            {p.tagline ? <span className="muted fa" dir="auto">{p.tagline}</span> : null}
          </div>
        </div>
        <div className="chips flat">{AVATARS.map((a) => <button key={a} className={`chip${p.avatar === a ? ' on' : ''}`} onClick={() => save({ ...p, avatar: a })}>{a}</button>)}
          <button className="chip" onClick={upload}><span className="fa">{L('تصویر', 'Picture')}</span></button>
          {p.avatar ? <button className="chip" onClick={() => save({ ...p, avatar: '' })}>×</button> : null}
        </div>
        <form className="form" onSubmit={(e) => e.preventDefault()}>
          <Field label={L('نام نمایشی', 'Display name')}><input value={p.name} maxLength={24} onChange={(e) => save({ ...p, name: e.target.value })} dir="auto" /></Field>
          <Field label={L('یک خط درباره‌ی شما', 'A line about you')}><input value={p.tagline} maxLength={60} onChange={(e) => save({ ...p, tagline: e.target.value })} dir="auto" /></Field>
          <label className="check row-between"><span className="fa">{L('پنهان کردن موجودی‌ها', 'Hide balances')}</span><input type="checkbox" role="switch" checked={p.hideBalance} onChange={() => save({ ...p, hideBalance: !p.hideBalance })} /></label>
        </form>
      </section>
      <section className="card">
        <h3 className="fa">{L('ظاهر و زبان', 'Appearance and language')}</h3>
        <Tabs value={theme} onChange={(x: Theme) => setTheme(x)} tabs={[{ id: 'light', label: L('روشن', 'Light') }, { id: 'dark', label: L('تیره', 'Dark') }, { id: 'system', label: L('مثل سیستم', 'System') }]} />
        <Tabs value={lang} onChange={setLang} tabs={[{ id: 'fa', label: 'فارسی' }, { id: 'en', label: 'English' }]} />
      </section>
      <section className="card">
        <h3 className="fa">{L('بایگانی این مرورگر', 'This browser’s archive')}</h3>
        <p className="muted fa small">{L('دیده‌بان، ترسیم‌ها، چیدمان‌ها، اسکریپت‌ها، ژورنال، معامله‌ی آزمایشی و تنظیمات؛ بدون اطلاعات ورود.', 'Watchlists, drawings, layouts, scripts, journal, paper account and settings; never the sign-in.')}</p>
        <div className="row">
          <button className="btn btn-quiet sm" onClick={() => downloadText(`pro-chart-archive-${new Date().toISOString().slice(0, 10)}.json`, JSON.stringify(archive()))}>{L('خروجی بایگانی', 'Export the archive')}</button>
          <button className="btn btn-quiet sm" onClick={() => void pickText().then((t) => { if (!t) return; try { const n = restore(JSON.parse(t)); setMsg(L(`${n} مورد بازگردانده شد؛ صفحه دوباره بارگذاری می‌شود.`, `${n} items restored; reloading.`)); setTimeout(() => window.location.reload(), 900); } catch { setMsg(L('این فایل بایگانی پرو چارت نیست.', 'That is not a Pro Chart archive.')); } })}>{L('بازگردانی', 'Restore')}</button>
        </div>
        {msg ? <p className="fa">{msg}</p> : null}
      </section>
      <section className="card doors">
        <button className="btn btn-quiet sm" onClick={() => open('notifications')}>{L('تنظیمات اعلان', 'Notification settings')}</button>
        <button className="btn btn-quiet sm" onClick={() => open('safety')}>{L('ایمنی و نسخه', 'Safety and version')}</button>
        <button className="btn btn-quiet sm" onClick={() => open('terms')}>{L('قوانین', 'Terms')}</button>
        <button className="btn btn-quiet sm" onClick={() => open('privacy')}>{L('حریم خصوصی', 'Privacy')}</button>
        {signed ? (
          <>
            <button className="btn btn-quiet sm" onClick={() => open('vip-status')}>{L('وضعیت VIP', 'VIP status')}</button>
            <button className="btn btn-quiet sm" onClick={() => open('copytrade')}>{L('کپی‌ترید', 'Copy trading')}</button>
            <button className="btn btn-quiet sm" onClick={() => open('affiliate')}>{L('معرفی دوستان', 'Referrals')}</button>
            <button className="btn btn-quiet sm" onClick={() => open('verify')}>{L('احراز هویت', 'Verification')}</button>
            <button className="btn btn-quiet sm" onClick={() => signOut()}>{L('خروج از حساب', 'Sign out')}</button>
            <button className="btn btn-quiet sm down" onClick={() => open('delete')}>{L('حذف حساب', 'Delete the account')}</button>
          </>
        ) : <button className="btn btn-primary sm" onClick={onSignIn}>{L('ورود', 'Sign in')}</button>}
      </section>
    </div>
  );
}
