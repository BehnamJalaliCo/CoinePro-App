import { useMemo, useState } from 'react';
import { usePrefs, useT, type Theme } from '../state/prefs';
import { usePage } from '../state/page';
import { useSession } from '../data/session';
import { authed, signOut } from '../data/auth';
import { member, ms, num, str } from '../data/member';
import { academy } from '../data/academy';
import { registration } from '../vip/api';
import { usePremium } from '../trade/entitlements';
import { useJournal, pnlFor } from '../state/journal';
import { usePaper } from '../state/paper';
import { Field, Tabs, downloadText, pickText, useL, useLoad } from './kit';
import type { PageProps } from './registry';
import { faNum } from '../i18n/time';
import { AvatarComposer, AvatarView, decodeAvatar, encodeAvatar } from './Avatar';
import { PROFILE_KEY, readProfile, type Profile } from './profileStore';
import { isFounding } from '../data/founding';
import { drawable, foundingFact, kycState, membershipFact, planFact, recordFacts, verificationFact, winRate, type Fact } from './profileFacts';
import './home2.css';

// Profile and settings (feature/profile, ProfileStore): the name and line others see on this
// device, an avatar (letter, picture, coin, pair, metal or mark, in a ring), where the account
// stands and what the reader has done, hiding balances, appearance (with «نیمه‌شب») and language,
// the archive of everything this browser keeps (export and restore), and the account's ways out.
const KEY = PROFILE_KEY;
const read = readProfile;

/** Every key this app keeps in the browser, except the sign-in handles. */
export function archive(): Record<string, string> {
  const out: Record<string, string> = {};
  for (let i = 0; i < localStorage.length; i++) {
    const k = localStorage.key(i);
    if (k && k.startsWith('pc.') && k !== 'pc.sessions.v1') out[k] = localStorage.getItem(k) ?? '';
  }
  return out;
}

/** Keys whose list is merged with what this browser holds instead of replaced: the journal. */
const MERGED = 'pc.journal.v1';

/** The journal in the archive joined with this browser's: every entry kept once by id, this browser's copy winning, newest first. */
export function mergeJournal(mine: string | null, theirs: string): string {
  const list = (raw: string | null): Record<string, unknown>[] => { try { const v = JSON.parse(raw ?? '[]') as unknown; return Array.isArray(v) ? v.filter((x): x is Record<string, unknown> => Boolean(x) && typeof x === 'object') : []; } catch { return []; } };
  const here = list(mine);
  const ids = new Set(here.map((e) => String(e.id ?? '')));
  const added = list(theirs).filter((e) => !ids.has(String(e.id ?? '')) || !e.id);
  return JSON.stringify([...here, ...added].sort((a, b) => Number(b.createdAt ?? 0) - Number(a.createdAt ?? 0)));
}

export function restore(data: unknown): number {
  if (!data || typeof data !== 'object') return 0;
  let n = 0;
  for (const [k, v] of Object.entries(data as Record<string, unknown>)) {
    if (!k.startsWith('pc.') || k === 'pc.sessions.v1' || typeof v !== 'string') continue;
    try { localStorage.setItem(k, k === MERGED ? mergeJournal(localStorage.getItem(k), v) : v); n++; } catch { /* full */ }
  }
  return n;
}

export default function ProfilePage({ onSignIn }: PageProps) {
  const L = useL();
  const t = useT();
  const open = usePage((s) => s.open);
  const { theme, setTheme, lang, setLang } = usePrefs();
  const sessions = useSession((s) => s.sessions);
  const user = sessions.tradeyar?.user ?? sessions.coineprofx?.user ?? null;
  const signed = Boolean(sessions.tradeyar || sessions.coineprofx);
  const fa = lang === 'fa';
  const n = (v: number) => (fa ? faNum(v) : String(v));
  const [p, setP] = useState<Profile>(read);
  const [msg, setMsg] = useState<string | null>(null);
  const [composing, setComposing] = useState(false);
  const [photo, setPhoto] = useState<string | null>(() => (p.avatar.startsWith('data:') ? p.avatar : null));
  const save = (next: Profile) => { setP(next); try { localStorage.setItem(KEY, JSON.stringify(next)); } catch { /* ignore */ } };
  const shown = p.name || user?.name || '';
  const initial = (shown || L('مهمان', 'Guest')).trim().charAt(0);

  // ── Where the account stands: only the servers' answers, unknown when they gave none ──
  const reg = useLoad(() => (sessions.tradeyar ? registration().catch(() => null) : Promise.resolve(null)), [Boolean(sessions.tradeyar)]);
  const kyc = useLoad(() => (signed ? member<Record<string, unknown>>('kyc').catch(() => null) : Promise.resolve(null)), [signed]);
  const tyEnt = useLoad(() => (sessions.tradeyar ? authed<Record<string, unknown>>('tradeyar', '/up/tradeyar/api/mobile/v1/entitlements').catch(() => null) : Promise.resolve(null)), [Boolean(sessions.tradeyar)]);
  const me = useLoad(() => (sessions.coineprofx ? academy<Record<string, unknown>>('me').catch(() => null) : Promise.resolve(null)), [Boolean(sessions.coineprofx)]);
  const premium = usePremium();
  // The founding mark: the account's own data, or the one-way mark noted while everything was open (data/founding.ts).
  const founding = [sessions.tradeyar?.user, sessions.coineprofx?.user].some((u) => Boolean((u as Record<string, unknown> | null | undefined)?.founding || (u as Record<string, unknown> | null | undefined)?.founding_member))
    || tyEnt.data?.founding_member === true || tyEnt.data?.founding === true || isFounding();
  const planLabel = useMemo(() => {
    if (premium.vip || premium.plan === 'vip') return 'VIP';
    if (!premium.premium) return null;
    if (fa && user?.planFa) return user.planFa;
    const key = { monthly: 'planMonthly', quarterly: 'planQuarterly', yearly: 'planYearly' }[premium.plan ?? ''] as 'planMonthly' | 'planQuarterly' | 'planYearly' | undefined;
    return key ? t(key) : premium.plan;
  }, [premium, fa, user?.planFa, t]);
  const ends = premium.endsAt ? Date.parse(premium.endsAt) : NaN;
  const daysLeft = Number.isFinite(ends) ? Math.max(0, Math.ceil((ends - Date.now()) / 86_400_000)) : null;
  const k = kyc.data;
  const standing: Fact[] = signed ? [
    ...(sessions.tradeyar ? [membershipFact(reg.loading ? null : reg.data?.status ?? null, reg.data?.messageFa ?? null, L)] : []),
    verificationFact(k ? { state: kycState(k.status), reason: str(k.reason ?? k.rejection_reason) || null, submittedDay: ms(k.submitted_at) ? new Intl.DateTimeFormat(fa ? 'fa-IR-u-ca-persian' : 'en-GB', { dateStyle: 'medium' }).format(new Date(ms(k.submitted_at)!)) : null, level: num(k.level) } : null, L, n),
    planFact({ label: planLabel, expires: Number.isFinite(ends) ? new Intl.DateTimeFormat(fa ? 'fa-IR-u-ca-persian' : 'en-GB', { dateStyle: 'medium' }).format(new Date(ends)) : null, daysLeft, endingSoon: daysLeft != null && daysLeft <= 7 }, L, n),
  ] : [];
  const fFact = foundingFact(founding, L);
  if (fFact) standing.push(fFact);

  // ── What the reader has done ──
  const entries = useJournal((s) => s.entries);
  const history = usePaper((s) => s.history);
  const m = me.data;
  const record = recordFacts({
    journal: { count: entries.length, win: winRate(entries.map((e) => e.pnl ?? pnlFor(e))) },
    paper: { count: history.length, win: winRate(history.map((x) => x.pnl)) },
    academy: m ? { done: num(m.completed), total: num(m.total_lessons), streak: num((m.streak as Record<string, unknown> | undefined)?.current) } : null,
  }, L, n);

  const upload = () => {
    const input = document.createElement('input');
    input.type = 'file'; input.accept = 'image/*';
    input.onchange = async () => {
      const f = input.files?.[0];
      if (!f) return;
      try {
        const bmp = await createImageBitmap(f);
        const c = document.createElement('canvas'); c.width = 128; c.height = 128;
        const side = Math.min(bmp.width, bmp.height);
        c.getContext('2d')!.drawImage(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side, 0, 0, 128, 128);
        setPhoto(c.toDataURL('image/jpeg', 0.85));
      } catch { /* not an image */ }
    };
    input.click();
  };

  const factCard = (title: string, facts: Fact[], cls: string) => {
    const rows = drawable(facts);
    if (!rows.length) return null;
    return (
      <section className={`card facts ${cls}`}>
        <h3 className="fa">{title}</h3>
        {rows.map((f) => {
          const inner = (
            <>
              <span className="fact-label"><span className="fa">{f.label}</span>{f.detail ? <small className="muted fa" dir="auto">{f.detail}</small> : null}</span>
              <b className={`fact-value ${f.value == null ? 'unknown' : f.tone}`} dir="auto">{f.value ?? L('نامشخص', 'Not known')}</b>
            </>
          );
          return f.open ? <button key={f.id} type="button" className="fact-row" data-fact={f.id} onClick={() => open(f.open as Parameters<typeof open>[0])}>{inner}<span className="muted" aria-hidden>›</span></button>
            : <div key={f.id} className="fact-row" data-fact={f.id}>{inner}</div>;
        })}
      </section>
    );
  };

  return (
    <div className="page-inner narrow stack profile">
      <section className="card profile-hero">
        <div className="row">
          <button type="button" className="avatar-btn" onClick={() => setComposing(!composing)} aria-label={L('تصویر نیمرخ شما', 'Your profile picture')}>
            <AvatarView base={decodeAvatar(p.avatar)} ring={p.ring} initial={initial} size={80} />
            <span className="avatar-edit" aria-hidden>✎</span>
          </button>
          <div className="stack tight">
            <b className="fa" dir="auto">{shown || L('مهمان', 'Guest')}</b>
            {p.tagline ? <span className="muted fa" dir="auto">{p.tagline}</span> : user?.email ? <span className="num muted">{user.email}</span> : null}
            <div className="row badges">
              {planLabel ? <span className="badge premium fa">{planLabel}</span> : null}
              {signed ? <span className="badge analysis fa">{sessions.tradeyar && sessions.coineprofx ? L('رمزارز و فارکس', 'Crypto and forex') : sessions.tradeyar ? L('رمزارز', 'Crypto') : L('فارکس', 'Forex')}</span> : null}
            </div>
          </div>
        </div>
        {composing ? (
          <AvatarComposer base={decodeAvatar(p.avatar)} ring={p.ring} initial={initial} photo={photo} onPickPhoto={upload}
            onCancel={() => setComposing(false)} onSave={(b, r) => { save({ ...p, avatar: encodeAvatar(b), ring: r }); setComposing(false); }} />
        ) : null}
      </section>
      {factCard(L('جایگاه شما', 'Where you stand'), standing, 'standing')}
      {factCard(L('کارنامه‌ی شما', 'Your record'), record, 'record')}
      <section className="card">
        <form className="form" onSubmit={(e) => e.preventDefault()}>
          <Field label={L('نام نمایشی', 'Display name')}><input value={p.name} maxLength={24} onChange={(e) => save({ ...p, name: e.target.value })} dir="auto" /></Field>
          <Field label={L('یک خط درباره‌ی شما', 'A line about you')}><input value={p.tagline} maxLength={60} onChange={(e) => save({ ...p, tagline: e.target.value })} dir="auto" /></Field>
          <label className="check row-between"><span className="fa">{L('پنهان کردن موجودی‌ها', 'Hide balances')}</span><input type="checkbox" role="switch" checked={p.hideBalance} onChange={() => save({ ...p, hideBalance: !p.hideBalance })} /></label>
        </form>
      </section>
      <section className="card">
        <h3 className="fa">{L('ظاهر و زبان', 'Appearance and language')}</h3>
        <Tabs value={theme} onChange={(x: Theme) => setTheme(x)} tabs={[{ id: 'light', label: L('روشن', 'Light') }, { id: 'dark', label: L('تیره', 'Dark') }, { id: 'midnight', label: L('نیمه‌شب', 'Midnight') }, { id: 'system', label: L('مثل سیستم', 'System') }]} />
        {theme === 'midnight' ? <p className="muted small fa">{L('تاریک روی سیاه کامل. برای نمایشگر OLED ملایم‌تر است.', 'Dark on true black. Kinder to an OLED screen.')}</p> : null}
        <Tabs value={lang} onChange={setLang} tabs={[{ id: 'fa', label: 'فارسی' }, { id: 'en', label: 'English' }]} />
      </section>
      <section className="card">
        <h3 className="fa">{L('بایگانی این مرورگر', 'This browser’s archive')}</h3>
        <p className="muted fa small">{L('دیده‌بان، ترسیم‌ها، چیدمان‌ها، اسکریپت‌ها، ژورنال، معامله‌ی آزمایشی و تنظیمات؛ بدون اطلاعات ورود.', 'Watchlists, drawings, layouts, scripts, journal, paper account and settings; never the sign-in.')}</p>
        <div className="row">
          <button className="btn btn-quiet sm" onClick={() => downloadText(`pro-chart-archive-${new Date().toISOString().slice(0, 10)}.json`, JSON.stringify(archive()))}>{L('خروجی بایگانی', 'Export the archive')}</button>
          <button className="btn btn-quiet sm" onClick={() => void pickText().then((txt) => { if (!txt) return; try { const c = restore(JSON.parse(txt)); setMsg(L(`${faNum(c)} مورد بازگردانده شد؛ صفحه دوباره بارگذاری می‌شود.`, `${c} items restored; reloading.`)); setTimeout(() => window.location.reload(), 900); } catch { setMsg(L('این فایل بایگانی پرو چارت نیست.', 'That is not a Pro Chart archive.')); } })}>{L('بازگردانی', 'Restore')}</button>
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
