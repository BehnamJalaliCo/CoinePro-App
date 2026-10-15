import { usePage } from '../state/page';
import { useSession } from '../data/session';
import { Icon } from '../ui/Icon';
import { useL } from '../pages/kit';
import { severityOf, usdt } from './logic';
import { useVip } from './store';
import { Pill } from './ui';

// The user site's conversion block, for the home page: where the reader stands on the VIP ladder
// and the one next step — join, fund, keep the balance up, or come back after a removal.
export function VipCard() {
  const L = useL();
  const open = usePage((s) => s.open);
  const vip = useVip((s) => s.crypto);
  const forex = useVip((s) => s.forex);
  const signed = useSession((s) => Boolean(s.sessions.tradeyar || s.sessions.coineprofx));
  const sev = severityOf(vip);
  const inside = Boolean(vip?.entitled || forex?.entitled);
  const tone = sev === 'critical' || sev === 'removed' ? 'crit' : sev === 'low' ? 'warn' : inside ? 'ok' : 'info';
  const head = sev === 'removed' ? L('دسترسی VIP لغو شده', 'VIP access removed')
    : sev === 'critical' ? L('اخطار نهایی موجودی', 'Final balance warning')
      : sev === 'low' ? L('موجودی کم است', 'Low balance')
        : inside ? L('عضو VIP هستید', 'You are a VIP member')
          : sev === 'pending' ? L('یک قدم تا VIP', 'One step to VIP')
            : L('کانال VIP و پرو چارت، رایگان', 'VIP channel and Pro Chart, free');
  const body = vip && sev !== 'none' ? vip.messageFa : forex?.entitled ? forex.messageFa
    : L('با لینک ما در LBank یا OneRoyal حساب باز کنید و پول را در حساب خودتان نگه دارید.', 'Open an LBank or OneRoyal account through our link and keep the money in your own account.');
  return (
    <section className={`card vip-card ${tone}`} data-vip-card={sev}>
      <div className="row-between"><h3 className="fa">{head}</h3><Pill tone={tone}>{inside ? L('فعال', 'Active') : L('رایگان', 'Free')}</Pill></div>
      <p className="fa">{body}</p>
      {vip?.balance != null && sev !== 'none' ? <p className="muted"><span className="fa">{L('موجودی صرافی', 'Exchange balance')}</span> <b className="num">{usdt(vip.balance)} USDT</b></p> : null}
      <div className="row">
        {inside || sev === 'low' || sev === 'critical' || sev === 'removed' || sev === 'pending'
          ? <button className="btn btn-primary sm" onClick={() => open('vip-status')}><Icon name="ph-seal-check" size="sm" /><span className="fa">{L('وضعیت VIP', 'VIP status')}</span></button>
          : <button className="btn btn-primary sm" onClick={() => open('vip-join')}><Icon name="ph-crown" size="sm" /><span className="fa">{signed ? L('عضویت رایگان', 'Join free') : L('شروع عضویت رایگان', 'Start for free')}</span></button>}
      </div>
    </section>
  );
}

/** A one-line invitation on the signals page for a reader outside the VIP channel. */
export function VipNudge() {
  const L = useL();
  const open = usePage((s) => s.open);
  const vip = useVip((s) => s.crypto);
  const forex = useVip((s) => s.forex);
  if (vip?.entitled || forex?.entitled) return null;
  const removed = vip?.status === 'removed';
  return (
    <div className="vip-nudge" data-vip-nudge={removed ? 'removed' : 'join'}>
      <Icon name="ph-crown" size="sm" />
      <span className="fa">{removed
        ? L('سیگنال‌های VIP برای این حساب بسته شده؛ با شارژ حساب صرافی خودکار برمی‌گردد.', 'VIP signals are closed for this account; they return when the exchange account is funded.')
        : L('همه‌ی سیگنال‌ها و کانال VIP رایگان است: با لینک ما حساب صرافی باز کنید.', 'Every signal and the VIP channel are free: open an exchange account through our link.')}</span>
      <button className="btn btn-quiet sm" onClick={() => open(removed ? 'vip-status' : 'vip-join')}><span className="fa">{removed ? L('بازگرداندن', 'Restore') : L('عضویت رایگان', 'Join free')}</span></button>
    </div>
  );
}
