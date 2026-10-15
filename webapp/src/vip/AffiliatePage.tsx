import { useState } from 'react';
import { usePrefs } from '../state/prefs';
import { ty, num, str } from '../data/member';
import { faNum } from '../i18n/time';
import { Icon } from '../ui/Icon';
import { State, useL, useLoad } from '../pages/kit';
import { CopyField } from './ui';

// «معرفی»: the user site's affiliate screen — the reader's own referral link (from the exchange
// account's referral code), the sign-ups it brought in the last 30 days as the exchange's referral
// service reports them, and nothing where the service has no number: a failed read is an error,
// never «0 referrals», and no commission figure is invented.
interface Summary { configured: boolean; total: number | null; active: number | null; monthly: number | null; members: { id: string; traded: boolean }[]; error: boolean }
interface Link { link: string | null; code: string; uid: string }

export default function AffiliatePage() {
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const link = useLoad(() => ty<Record<string, unknown>>('api/user/v1/affiliate/link').then((r): Link => ({ link: r.referralLink ? str(r.referralLink) : null, code: str(r.referralCode), uid: str(r.lbankUid) })), []);
  const summary = useLoad(() => ty<Record<string, unknown>>('api/user/v1/affiliate/summary').then((r): Summary => ({
    configured: Boolean(r.configured), total: num(r.totalReferrals), active: num(r.activeReferrals), monthly: num(r.monthlyReferrals), error: Boolean(r.error),
    members: (Array.isArray(r.members) ? (r.members as Record<string, unknown>[]) : []).map((m) => ({ id: str(m.openId), traded: Boolean(m.transaction) })),
  })), []);
  const [shared, setShared] = useState(false);
  const n = (v: number) => (lang === 'fa' ? faNum(v) : String(v));
  const share = async (url: string) => {
    const text = L(`با این لینک در صرافی ثبت‌نام کن و سیگنال VIP و پرو چارت را رایگان بگیر:\n${url}`, `Sign up with this link and get the VIP signals and Pro Chart free:\n${url}`);
    try { if ('share' in navigator) { await navigator.share({ text }); return; } } catch { /* cancelled */ }
    await navigator.clipboard?.writeText(text).then(() => { setShared(true); setTimeout(() => setShared(false), 1500); }).catch(() => undefined);
  };
  const s = summary.data;
  return (
    <div className="page-inner narrow vip-page">
      <section className="card stack">
        <h3 className="fa">{L('لینک معرفی شما', 'Your referral link')}</h3>
        <State loading={link.loading} error={link.error} onRetry={link.reload}>
          {link.data?.link ? (
            <>
              <CopyField text={link.data.link} label={L('کپی لینک', 'Copy link')} />
              <div className="row">
                <button className="btn btn-primary sm" onClick={() => void share(link.data!.link!)}><Icon name={shared ? 'ph-check' : 'ph-share-network'} size="sm" /><span className="fa">{shared ? L('متن دعوت کپی شد', 'Invite copied') : L('اشتراک‌گذاری', 'Share')}</span></button>
              </div>
              {link.data.code ? <p className="muted"><span className="fa">{L('کد معرف', 'Referral code')}</span> <span className="num">{link.data.code}</span></p> : null}
            </>
          ) : <p className="fa">{L('کد معرف روی حساب صرافی شما ثبت نشده است؛ تا آن زمان لینکی برای اشتراک‌گذاری نیست.', 'No referral code is set on your exchange account yet, so there is no link to share.')}</p>}
        </State>
      </section>
      <State loading={summary.loading} error={summary.error} onRetry={summary.reload}>
        {s ? (s.error ? (
          <div className="card warn stack"><h3 className="fa">{L('آمار زیرمجموعه‌ها دریافت نشد', 'Referral numbers unavailable')}</h3><p className="fa">{L('سرویس معرف صرافی پاسخ نداد. این «صفر زیرمجموعه» نیست؛ عددی جایگزین نمایش داده نمی‌شود.', 'The exchange referral service did not answer. This is not «zero referrals»; no number is shown in its place.')}</p></div>
        ) : !s.configured ? (
          <div className="card stack"><h3 className="fa">{L('برنامه‌ی معرفی برای حساب شما فعال نیست', 'Referrals are not enabled for your account')}</h3><p className="fa muted">{L('برای فعال‌سازی با پشتیبانی تماس بگیرید.', 'Contact support to enable it.')}</p></div>
        ) : (
          <>
            <section className="card vip-tiles-card">
              <div className="vip-tiles">
                <div className="vip-tile"><span className="muted fa">{L('زیرمجموعه‌ها · ۳۰ روز', 'Referrals · 30 days')}</span><b className="num">{s.total ?? '—'}</b></div>
                {s.active != null && s.monthly ? <div className="vip-tile"><span className="muted fa">{L('معامله‌کرده', 'Traded')}</span><b className="num">{s.active} / {s.monthly}</b></div> : null}
              </div>
              <p className="muted fa">{L('مبلغ کمیسیون این‌جا نوشته نمی‌شود، چون سرویس صرافی آن را برنمی‌گرداند.', 'No commission figure is shown: the exchange service does not return one.')}</p>
            </section>
            <section className="card stack">
              <h3 className="fa">{L('زیرمجموعه‌های اخیر', 'Recent referrals')}</h3>
              {s.members.length ? (
                <ul className="vip-members">{s.members.map((m, i) => <li key={m.id || i}><span className="num">{m.id || '—'}</span><span className={`fa ${m.traded ? 'up' : 'muted'}`}>{m.traded ? L('معامله کرده', 'Traded') : L('بدون معامله', 'No trades')}</span></li>)}</ul>
              ) : <div className="empty fa">{L(`در ${n(30)} روز گذشته کسی با لینک شما ثبت‌نام نکرده است.`, 'Nobody signed up with your link in the last 30 days.')}</div>}
            </section>
          </>
        )) : null}
      </State>
    </div>
  );
}
