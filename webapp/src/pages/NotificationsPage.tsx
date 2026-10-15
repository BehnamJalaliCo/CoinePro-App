import { useEffect, useState } from 'react';
import { useSession } from '../data/session';
import { ty, fx } from '../data/member';
import { State, useL, useLoad } from './kit';
import type { Backend } from '../data/symbols';
import { readNotify, writeNotify, type NotifyLocal } from '../state/notify';

// Notification settings (feature/notifications): what each platform may send — new signals,
// signal updates, price alerts, announcements — kept on the server so the phone and Telegram follow
// it; and this browser's own permission, quiet hours and the alert sound for alerts fired here.
interface Prefs { new_signals: boolean; signal_updates: boolean; price_alerts: boolean; announcements: boolean }
const path = (b: Backend) => (b === 'tradeyar' ? 'api/mobile/v1/push/preferences' : 'user/mobile/push/preferences');
const call = <T,>(b: Backend, o = {}) => (b === 'tradeyar' ? ty<T>(path(b), o) : fx<T>(path(b), o));

function PlatformPrefs({ backend, title }: { backend: Backend; title: string }) {
  const L = useL();
  const prefs = useLoad(() => call<{ preferences?: Prefs }>(backend).then((r) => r.preferences ?? { new_signals: true, signal_updates: true, price_alerts: true, announcements: true }), [backend]);
  const [p, setP] = useState<Prefs | null>(null);
  useEffect(() => { if (prefs.data) setP(prefs.data); }, [prefs.data]);
  const toggle = async (k: keyof Prefs) => {
    if (!p) return;
    const next = { ...p, [k]: !p[k] };
    setP(next);
    try { const r = await call<{ preferences?: Prefs }>(backend, { method: 'PATCH', body: next }); if (r.preferences) setP(r.preferences); }
    catch { setP(p); }
  };
  const rows: [keyof Prefs, string][] = [['new_signals', L('سیگنال تازه', 'New signals')], ['signal_updates', L('به‌روزرسانی سیگنال', 'Signal updates')], ['price_alerts', L('هشدار قیمت', 'Price alerts')], ['announcements', L('اطلاعیه‌ها و وضعیت سرویس', 'Announcements and service status')]];
  return (
    <section className="card">
      <h3 className="fa">{title}</h3>
      <State loading={prefs.loading} error={prefs.error} onRetry={prefs.reload}>
        {p ? rows.map(([k, label]) => <label key={k} className="check row-between"><span className="fa">{label}</span><input type="checkbox" role="switch" checked={p[k]} onChange={() => void toggle(k)} /></label>) : null}
      </State>
    </section>
  );
}

export default function NotificationsPage() {
  const L = useL();
  const sessions = useSession((s) => s.sessions);
  const [local, setLocal] = useState<NotifyLocal>(readNotify);
  const [perm, setPerm] = useState(typeof Notification === 'undefined' ? 'unsupported' : Notification.permission);
  const save = (n: NotifyLocal) => { setLocal(n); writeNotify(n); };
  return (
    <div className="page-inner narrow stack">
      <section className="card">
        <h3 className="fa">{L('این مرورگر', 'This browser')}</h3>
        <p className="fa">{perm === 'granted' ? L('اعلان مرورگر فعال است.', 'Browser notifications are on.') : perm === 'denied' ? L('اعلان در تنظیمات مرورگر بسته شده است.', 'Notifications are blocked in the browser settings.') : perm === 'unsupported' ? L('این مرورگر اعلان ندارد.', 'This browser has no notifications.') : L('برای هشدارهای قیمت در همین صفحه، اجازه‌ی اعلان بدهید.', 'Allow notifications for price alerts on this page.')}</p>
        {perm === 'default' ? <button className="btn btn-primary sm" onClick={() => void Notification.requestPermission().then(setPerm)}>{L('اجازه‌ی اعلان', 'Allow notifications')}</button> : null}
        <label className="check row-between"><span className="fa">{L('صدای هشدار', 'Alert sound')}</span><input type="checkbox" role="switch" checked={local.sound} onChange={() => save({ ...local, sound: !local.sound })} /></label>
        <label className="check row-between"><span className="fa">{L('ساعت‌های سکوت', 'Quiet hours')}</span><input type="checkbox" role="switch" checked={local.quiet} onChange={() => save({ ...local, quiet: !local.quiet })} /></label>
        {local.quiet ? <div className="row"><input type="time" value={local.quietFrom} onChange={(e) => save({ ...local, quietFrom: e.target.value })} /><span>→</span><input type="time" value={local.quietTo} onChange={(e) => save({ ...local, quietTo: e.target.value })} /></div> : null}
      </section>
      {sessions.tradeyar ? <PlatformPrefs backend="tradeyar" title={L('اعلان‌های رمزارز (تریدیار)', 'Crypto notifications (TradeYar)')} /> : null}
      {sessions.coineprofx ? <PlatformPrefs backend="coineprofx" title={L('اعلان‌های فارکس (کوین‌پرو اف‌ایکس)', 'Forex notifications (CoinePro-FX)')} /> : null}
      {!sessions.tradeyar && !sessions.coineprofx ? <p className="muted fa">{L('برای تنظیم اعلان‌های گوشی و تلگرام وارد شوید.', 'Sign in to set what the phone and Telegram receive.')}</p> : null}
    </div>
  );
}
