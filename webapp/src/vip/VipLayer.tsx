import { useEffect, useState } from 'react';
import { usePrefs } from '../state/prefs';
import { usePage } from '../state/page';
import { Icon } from '../ui/Icon';
import { useL } from '../pages/kit';
import { readLocal, severityOf, usdt, writeLocal } from './logic';
import { startVipPoller, useVip } from './store';
import { Countdown } from './ui';
import { InstallPrompt } from './InstallPrompt';

// Everything the VIP area puts over the workspace: the poller, the banner for a low, critical or
// removed membership (as the bot's DMs say it, but visible wherever the reader is), and the
// install prompt. One mount in App.tsx.
export function VipLayer() {
  useEffect(() => startVipPoller(), []);
  return (
    <>
      <VipBanner />
      <InstallPrompt />
    </>
  );
}

/** How long a dismissed banner stays away, by severity. A different state shows at once. */
const QUIET_MS: Record<string, number> = { low: 24 * 3_600_000, critical: 6 * 3_600_000, removed: 24 * 3_600_000 };

export function VipBanner() {
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const open = usePage((s) => s.open);
  const page = usePage((s) => s.page);
  const vip = useVip((s) => s.crypto);
  const [, force] = useState(0);
  const sev = severityOf(vip);
  if (!vip || (sev !== 'low' && sev !== 'critical' && sev !== 'removed') || page === 'vip-status' || page === 'vip-join') return null;
  const key = `${sev}`;
  const local = readLocal();
  const at = local.dismissed[key];
  if (at && Date.now() - at < (QUIET_MS[sev] ?? 0)) return null;
  const dismiss = () => { writeLocal({ ...local, dismissed: { ...local.dismissed, [key]: Date.now() } }); force((n) => n + 1); };
  const th = vip.thresholds;
  const text = sev === 'low'
    ? L(`موجودی حساب صرافی ${usdt(vip.balance)} USDT است؛ برای حفظ VIP بالای ${usdt(th.warn)} USDT نگه دارید.`, `Exchange balance is ${usdt(vip.balance)} USDT; keep it above ${usdt(th.warn)} USDT to stay VIP.`)
    : sev === 'critical'
      ? L(`اخطار نهایی: موجودی ${usdt(vip.balance)} USDT است و دسترسی VIP لغو می‌شود مگر به بالای ${usdt(th.critical)} USDT برسد.`, `Final warning: balance ${usdt(vip.balance)} USDT; VIP is removed unless it is back above ${usdt(th.critical)} USDT.`)
      : L(`دسترسی VIP و امکانات رایگان لغو شد. با رسیدن موجودی به ${usdt(th.minDeposit)} USDT خودکار برمی‌گردد.`, `VIP and the free features were removed. They return automatically at ${usdt(th.minDeposit)} USDT.`);
  return (
    <div className={`vip-banner ${sev}`} role={sev === 'low' ? 'status' : 'alert'} dir={lang === 'fa' ? 'rtl' : 'ltr'} data-vip-banner={sev}>
      <Icon name={sev === 'removed' ? 'ph-x-circle' : 'ph-warning'} size="sm" />
      <span className="vip-banner-text fa">{text}{sev === 'critical' && vip.removalDueAt ? <> <b><Countdown at={vip.removalDueAt} /></b></> : null}</span>
      <button className="vip-banner-cta" onClick={() => open('vip-status')}><span className="fa">{sev === 'removed' ? L('بازگرداندن دسترسی', 'Restore access') : L('وضعیت VIP', 'VIP status')}</span></button>
      <button className="ib vip-banner-x" aria-label={L('بستن', 'Close')} onClick={dismiss}><Icon name="ph-x" size="sm" /></button>
    </div>
  );
}
