// How the reader's plan is named wherever the app shows it: «VIP» for a member of the TradeYar VIP
// channel (everything free), «پرمیوم» for a bought period.
import { useL } from '../pages/kit';
import { usePrefs } from '../state/prefs';
import { Icon } from '../ui/Icon';
import { usePremium } from './entitlements';
import './trade.css';

export function PlanBadge() {
  const L = useL();
  const p = usePremium();
  if (!p.premium) return null;
  return <b className={`plan-badge${p.vip ? ' vip' : ''}`} title={p.vip ? L('عضو کانال VIP تریدیار', 'TradeYar VIP channel member') : undefined}>{p.vip ? 'VIP' : L('پرمیوم', 'Premium')}</b>;
}

/** «VIP» when the plan is the channel's, else null (the caller keeps its own premium wording). */
export function useVipName(): string | null {
  return usePremium().vip ? 'VIP' : null;
}

/** The premium dialog's answer to a channel member: nothing to buy, everything is open. */
export function VipCard() {
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const p = usePremium();
  if (p.vip) {
    return (
      <div className="vip-card" dir={lang === 'fa' ? 'rtl' : 'ltr'}>
        <Icon name="ph-crown-simple" />
        <div>
          <b className="fa">{L('شما عضو کانال VIP تریدیار هستید', 'You are a TradeYar VIP channel member')}</b>
          <p className="fa">{L('تا وقتی در کانال هستید، همه‌ی امکانات پرو چارت برایتان رایگان و بی‌سقف است؛ خریدی لازم نیست.', 'While you are in the channel, all of Pro Chart is free and unlimited for you; there is nothing to buy.')}</p>
        </div>
      </div>
    );
  }
  if (!p.premium) return null;
  const until = p.endsAt ? new Intl.DateTimeFormat(lang === 'fa' ? 'fa-IR-u-ca-persian' : 'en-GB', { dateStyle: 'long' }).format(new Date(p.endsAt)) : null;
  return (
    <div className="vip-card premium" dir={lang === 'fa' ? 'rtl' : 'ltr'}>
      <Icon name="ph-crown" />
      <div><b className="fa">{L('پرمیوم فعال است', 'Premium is active')}</b>{until ? <p className="fa">{L(`تا ${until}`, `Until ${until}`)}</p> : null}</div>
    </div>
  );
}
