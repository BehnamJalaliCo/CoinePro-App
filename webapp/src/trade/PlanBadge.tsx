// How the reader's plan is named wherever the app shows it: «VIP» for a member of the TradeYar VIP
// channel or of CoinePro-FX's forex VIP (either one opens everything), «پرمیوم» for a bought period.
import { useL } from '../pages/kit';
import { usePrefs } from '../state/prefs';
import { Icon } from '../ui/Icon';
import { usePremium } from './entitlements';
import './trade.css';

export function PlanBadge() {
  const L = useL();
  const p = usePremium();
  if (!p.premium) return null;
  const vip = p.vip || p.forexVip;
  const title = p.vip ? L('عضو کانال VIP تریدیار', 'TradeYar VIP channel member') : p.forexVip ? L('عضو VIP فارکس (CoinePro-FX)', 'CoinePro-FX forex VIP member') : undefined;
  return <b className={`plan-badge${vip ? ' vip' : ''}`} title={title}>{vip ? 'VIP' : L('پرمیوم', 'Premium')}</b>;
}

/** «VIP» when the plan is a VIP membership, else null (the caller keeps its own premium wording). */
export function useVipName(): string | null {
  const p = usePremium();
  return p.vip || p.forexVip ? 'VIP' : null;
}

/** The premium dialog's answer to a channel member: nothing to buy, everything is open. */
export function VipCard() {
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const p = usePremium();
  if (p.vip || p.forexVip) {
    return (
      <div className="vip-card" dir={lang === 'fa' ? 'rtl' : 'ltr'}>
        <Icon name="ph-crown-simple" />
        <div>
          <b className="fa">{p.vip ? L('شما عضو کانال VIP تریدیار هستید', 'You are a TradeYar VIP channel member') : L('شما عضو VIP فارکس (CoinePro-FX) هستید', 'You are a CoinePro-FX forex VIP member')}</b>
          <p className="fa">{p.vip
            ? L('تا وقتی در کانال هستید، همه‌ی امکانات پرو چارت برایتان رایگان و بی‌سقف است؛ خریدی لازم نیست.', 'While you are in the channel, all of Pro Chart is free and unlimited for you; there is nothing to buy.')
            : L('تا وقتی عضویت VIP فارکس فعال است، همه‌ی امکانات پرو چارت برایتان رایگان و بی‌سقف است؛ خریدی لازم نیست.', 'While your forex VIP is active, all of Pro Chart is free and unlimited for you; there is nothing to buy.')}</p>
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
