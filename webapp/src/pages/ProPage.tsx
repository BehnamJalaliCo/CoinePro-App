import { PremiumBody } from '../shell/PremiumDialog';
import { usePrefs } from '../state/prefs';
import { faNum } from '../i18n/time';
import { useL } from './kit';
import type { PageProps } from './registry';

// «پرو چارت پرمیوم» (app ProScreen): the limits free and premium will have, then the plans and the
// USDT checkout. Everything is free today; buying now changes nothing the reader already has.
export default function ProPage({ onSignIn }: PageProps) {
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const n = (v: number) => (lang === 'fa' ? faNum(v) : String(v));
  const rows: [string, string, string][] = [
    [L('چارت کنار هم', 'Charts side by side'), n(2), n(16)],
    [L('اندیکاتور روی هر چارت', 'Indicators per chart'), n(5), n(25)],
    [L('هشدار فعال', 'Active alerts'), n(5), n(100)],
    [L('چیدمان ذخیره‌شده', 'Saved layouts'), n(1), L('نامحدود', 'Unlimited')],
  ];
  return (
    <div className="page-inner narrow pro-page">
      <p className="lead fa">{L('همه‌ی ابزارها الان رایگان‌اند. طرحی که امروز بخرید، وقتی این سقف‌ها بیایند از قبل در جریان است و چیزی از آنچه دارید کم نمی‌شود.', 'Every tool is free today. A plan bought now is already running when these limits arrive, and nothing you have today is taken away.')}</p>
      <table className="table card">
        <thead><tr><th className="fa">{L('امکان', 'Feature')}</th><th className="r fa">{L('رایگان', 'Free')}</th><th className="r fa">{L('پرمیوم', 'Premium')}</th></tr></thead>
        <tbody>{rows.map(([a, b, c]) => <tr key={a}><td className="fa">{a}</td><td className="r">{b}</td><td className="r"><b>{c}</b></td></tr>)}</tbody>
      </table>
      <div className="card"><PremiumBody onSignIn={onSignIn} /></div>
      <p className="muted small fa">{L('یک حساب: پرمیومی که اینجا بخرید، هر جا وارد شوید کار می‌کند.', 'One account: premium bought here works wherever you sign in.')}</p>
    </div>
  );
}
