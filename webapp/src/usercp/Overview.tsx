import { useState } from 'react';
import { usePage } from '../state/page';
import { usePrefs } from '../state/prefs';
import { useSession } from '../data/session';
import { getActivity, saveProfile } from '../data/panel';
import { money, useL, useLoad } from '../pages/kit';
import type { Backend } from '../data/symbols';
import { usePanel } from './PanelPage';
import { Card, COUNTRY, Pill, Row, Section, Stat, hasPage, openPage, stamp, useAgo, useCount } from './ui';
import { BACKENDS, alertsCount, closedTrades, entitlement, flat, membership, positions, signedOn, todayPnl } from './sources';
import { eventLabel, planLabel } from './labels';

const PLATFORM: Record<Backend, [string, string]> = { tradeyar: ['تریدیار · رمزارز', 'TradeYar · crypto'], coineprofx: ['کوین‌پرو اف‌ایکس · فارکس', 'CoinePro-FX · forex'] };

function layoutsHere(): number {
  try { const v = JSON.parse(localStorage.getItem('pc.layouts.v1') ?? 'null') as { layouts?: unknown[] } | null; return Array.isArray(v?.layouts) ? v!.layouts!.length : 0; } catch { return 0; }
}

export default function Overview() {
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const ago = useAgo();
  const count = useCount();
  const open = usePage((s) => s.open);
  const { me, reload } = usePanel();
  const sessions = useSession((s) => s.sessions);
  const ents = useLoad(() => Promise.all(BACKENDS.map(entitlement)), []);
  const vip = useLoad(() => membership(), []);
  const pos = useLoad(() => Promise.all(BACKENDS.map(positions)), []);
  const today = useLoad(() => Promise.all(BACKENDS.map((b) => closedTrades(b, 2))), []);
  const alerts = useLoad(() => Promise.all(BACKENDS.map(alertsCount)), []);
  const recent = useLoad(() => (me ? getActivity().then((r) => r.items.slice(0, 6)) : Promise.resolve([])), [me?.account]);
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState('');
  const [tagline, setTagline] = useState('');

  const openPositions = pos.data ? flat(pos.data) : null;
  const pnl = today.data ? todayPnl(flat(today.data)) : null;
  const pnlText = pnl ? (Object.keys(pnl).length ? Object.entries(pnl).map(([c, v]) => `${v >= 0 ? '+' : '−'}${money(Math.abs(v))} ${c}`).join(' · ') : '0.00') : '—';
  const pnlTone = pnl ? (Object.values(pnl).reduce((a, b) => a + b, 0) >= 0 ? 'good' : 'bad') : undefined;
  const alertTotal = alerts.data ? alerts.data.reduce<number>((n, x) => n + (x ?? 0), 0) : null;
  const cloud = me?.workspace.find((w) => w.kind === 'layouts');
  const premium = (ents.data ?? []).find((e) => e?.premium);
  const isVip = Boolean(me?.vip) || vip.data?.status === 'approved';

  const startEdit = () => { setName(me?.profile.display_name ?? ''); setTagline(me?.profile.tagline ?? ''); setEditing(true); };
  const saveEdit = async () => { await saveProfile({ ...me?.profile, display_name: name.trim(), tagline: tagline.trim() }).catch(() => null); setEditing(false); reload(); };

  return (
    <Section title={L('نمای کلی', 'Overview')} sub={L('حساب شما روی هر دو پلتفرم، وضعیت VIP و پریمیوم، و آنچه امروز در جریان است.', 'Your account on both platforms, VIP and Premium, and what is going on today.')}>
      <div className="ucp-stats">
        <Stat icon="ph-chart-bar" label={L('پوزیشن باز', 'Open positions')} value={openPositions ? openPositions.length : '—'} foot={openPositions?.length ? openPositions.slice(0, 3).map((p) => p.symbol).join(' · ') : L('هیچ پوزیشن بازی نیست', 'No open position')} />
        <Stat icon="ph-trend-up" label={L('سود و زیان امروز', 'Today’s P&L')} value={pnlText} tone={pnlTone} foot={L('از معاملات بسته‌شده‌ی امروز', 'From trades closed today')} />
        <Stat icon="ph-bell" label={L('هشدار فعال', 'Active alerts')} value={alertTotal ?? '—'} foot={<button className="ucp-link" onClick={() => open('alerts')}>{L('مرکز هشدار', 'Alert center')}</button>} />
        <Stat icon="ph-layout" label={L('چیدمان', 'Layouts')} value={layoutsHere()} foot={cloud?.version ? L(`نسخه‌ی ابری ${count(cloud.version)}`, `Cloud version ${cloud.version}`) : L('هنوز در ابر نیست', 'Not in the cloud yet')} />
      </div>

      <div className="ucp-grid">
        <Card title={L('هویت', 'Identity')} sub={L('یک حساب برای پنل؛ حساب هر پلتفرم جداست.', 'One panel account; each platform keeps its own.')}
          actions={editing ? null : <button className="btn btn-quiet sm" onClick={startEdit}>{L('ویرایش نام نمایشی', 'Edit display name')}</button>}>
          {editing ? (
            <form className="ucp-form" onSubmit={(e) => { e.preventDefault(); void saveEdit(); }}>
              <label className="field"><span>{L('نام نمایشی', 'Display name')}</span><input value={name} maxLength={40} onChange={(e) => setName(e.target.value)} dir="auto" /></label>
              <label className="field"><span>{L('یک خط درباره‌ی شما', 'A line about you')}</span><input value={tagline} maxLength={80} onChange={(e) => setTagline(e.target.value)} dir="auto" /></label>
              <div className="ucp-actions"><button className="btn btn-primary sm">{L('ذخیره', 'Save')}</button><button type="button" className="btn btn-quiet sm" onClick={() => setEditing(false)}>{L('انصراف', 'Cancel')}</button></div>
            </form>
          ) : null}
          {me?.profile.tagline && !editing ? <p className="ucp-muted" dir="auto">{me.profile.tagline}</p> : null}
          {BACKENDS.map((b) => {
            const alias = me?.aliases.find((a) => a.backend === b);
            const user = sessions[b]?.user;
            const on = signedOn(b);
            return (
              <Row key={b} label={lang === 'fa' ? PLATFORM[b][0] : PLATFORM[b][1]} hint={alias ? <span className="num">{alias.alias}</span> : undefined}>
                {on ? <><span className="num">{alias?.email || user?.email || '—'}</span><Pill tone="good">{L('وارد شده', 'Signed in')}</Pill></> : <Pill>{L('وارد نشده', 'Not signed in')}</Pill>}
              </Row>
            );
          })}
          {me ? (
            <Row label={L('عضو پنل از', 'Member since')} hint={me.previous_sign_in ? L(`ورود پیشین: ${ago(me.previous_sign_in.at * 1000)} · ${me.previous_sign_in.browser ?? ''} ${COUNTRY(me.previous_sign_in.country, lang)}`, `Previous sign-in: ${ago(me.previous_sign_in.at * 1000)} · ${me.previous_sign_in.browser ?? ''} ${COUNTRY(me.previous_sign_in.country, lang)}`) : undefined}>
              <span className="num">{stamp(me.first_seen * 1000)}</span>
            </Row>
          ) : null}
        </Card>

        <Card title={L('VIP و پریمیوم', 'VIP and Premium')} sub={L('عضویت VIP از راه صرافی همکار؛ پریمیوم پرو چارت از راه اشتراک.', 'VIP comes through a partner exchange; Pro Chart Premium through a plan.')}>
          <Row label="VIP" hint={vip.data?.message || undefined}>
            {isVip ? <Pill tone="gold">{L('فعال', 'Active')}</Pill> : vip.data ? <Pill tone="warn">{vipStatus(vip.data.status, L)}</Pill> : <Pill>{signedOn('tradeyar') ? '—' : L('حساب تریدیار لازم است', 'Needs a TradeYar account')}</Pill>}
          </Row>
          {(ents.data ?? []).map((e, i) => e ? (
            <Row key={BACKENDS[i]} label={L(`پریمیوم · ${PLATFORM[BACKENDS[i]!][0]}`, `Premium · ${PLATFORM[BACKENDS[i]!][1]}`)} hint={e.endsAt ? L(`تا ${stamp(e.endsAt)}`, `Until ${stamp(e.endsAt)}`) : undefined}>
              {e.premium ? <Pill tone="gold">{e.plan ? planLabel(e.plan, L) : L('فعال', 'Active')}</Pill> : <Pill>{e.all ? L('همه‌ی ابزارها رایگان', 'Everything free today') : L('رایگان', 'Free')}</Pill>}
            </Row>
          ) : null)}
          <div className="ucp-doors">
            {!isVip ? <button className="btn btn-primary sm" onClick={() => openPage('vip-join', 'membership')}>{L('پیوستن به VIP', 'Join VIP')}</button> : null}
            {hasPage('vip-status') || isVip ? <button className="btn btn-quiet sm" onClick={() => openPage('vip-status', 'membership')}>{L('وضعیت VIP', 'VIP status')}</button> : null}
            {hasPage('performance') ? <button className="btn btn-quiet sm" onClick={() => openPage('performance')}>{L('کارنامه‌ی سیگنال‌ها', 'Signal performance')}</button> : null}
            {hasPage('affiliate') ? <button className="btn btn-quiet sm" onClick={() => openPage('affiliate')}>{L('همکاری در فروش', 'Affiliate')}</button> : null}
            {!premium ? <button className="btn btn-quiet sm" onClick={() => open('panel', 'billing')}>{L('پریمیوم', 'Premium')}</button> : null}
          </div>
        </Card>
      </div>

      <Card title={L('فعالیت اخیر', 'Recent activity')} actions={<button className="btn btn-quiet sm" onClick={() => open('panel', 'security')}>{L('همه', 'All')}</button>}>
        {recent.data?.length ? (
          <ul className="ucp-feed">
            {recent.data.map((e) => (
              <li key={e.id}><span className={`ucp-dot k-${e.kind}`} /><span>{eventLabel(e, L)}</span><span className="ucp-muted">{[e.browser, e.os].filter(Boolean).join(' · ')}</span><span className="ucp-muted">{ago(e.at * 1000)}</span></li>
            ))}
          </ul>
        ) : <p className="ucp-muted">{me ? L('هنوز فعالیتی ثبت نشده.', 'Nothing recorded yet.') : L('فعالیت از سرور پنل می‌آید.', 'Activity comes from the panel server.')}</p>}
      </Card>
    </Section>
  );
}

export function vipStatus(status: string, L: (fa: string, en: string) => string): string {
  return ({
    awaiting_uid: L('منتظر UID', 'Awaiting UID'), verifying: L('در حال بررسی', 'Verifying'), approved: L('فعال', 'Active'),
    pending_deposit: L('منتظر واریز', 'Awaiting deposit'), rejected_referral: L('بدون لینک معرف', 'Not referred'), pending: L('در انتظار', 'Pending'), error: L('خطا', 'Error'),
  } as Record<string, string>)[status] ?? (status || '—');
}
