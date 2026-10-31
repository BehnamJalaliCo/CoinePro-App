import { useEffect, useState } from 'react';
import { useSession } from '../data/session';
import { ty, fx } from '../data/member';
import { State, useL, useLoad } from './kit';
import type { Backend } from '../data/symbols';
import { readNotify, writeNotify, type NotifyLocal } from '../state/notify';
import { usePulse, type PulseSettings } from '../state/pulse';
import { useScanWatch } from '../state/scanWatch';
import { faNum } from '../i18n/time';
import { intervalLabel } from '../data/intervals';
import './scan.css';

/** The market notifications worked out in this browser (OLD MarketPulse, 5.27.0) and the daily brief. */
function MarketNotifications({ granted }: { granted: boolean }) {
  const L = useL();
  const p = usePulse();
  const watches = useScanWatch((s) => s.watches);
  const removeWatch = useScanWatch((s) => s.remove);
  const rows: [keyof PulseSettings, string, string][] = [
    ['move', L('حرکت تند بازار', 'Sharp market moves'), L('بیت‌کوین، طلا و جفت‌ارزهای اصلی، وقتی تند حرکت می‌کنند', 'Bitcoin, gold and the major pairs, when they move fast')],
    ['watchlist', L('نوسان بازارهای ستاره‌دار', 'Starred market moves'), L('حرکت غیرعادی در بازارهایی که ستاره زده‌اید — بدون تعیین عدد', 'Unusual moves in markets you starred — no number to choose')],
    ['digest', L('خلاصه‌ی روزانه‌ی بازار', 'Daily market summary'), L('هر صبح: بازارهای اصلی و پرتحرک‌ترین‌های اسکرینر', 'Each morning: the majors and the screener’s biggest movers')],
    ['calendar', L('تقویم اقتصادی', 'Economic calendar'), L('پیش از رویدادهای پراهمیت', 'Before high-importance releases')],
    ['scan', L('اسکن‌های رشد', 'Growth scans'), L('وقتی نمادی وارد اسکنی می‌شود که از اسکرینر خواسته‌اید', 'When a market enters a scan you asked the screener to watch')],
  ];
  const hh = String(Math.floor(p.briefMinute / 60)).padStart(2, '0');
  const mm = String(p.briefMinute % 60).padStart(2, '0');
  return (
    <section className="card pulse-card">
      <h3 className="fa">{L('اعلان‌های بازار در این مرورگر', 'Market notifications in this browser')}</h3>
      <p className="muted small fa pulse-note">{L('اعلان مرورگر فقط وقتی می‌رسد که یک زبانه‌ی پرو چارت باز باشد؛ با بستن همه‌ی زبانه‌ها چیزی نمی‌آید. برای اعلان بدون زبانه‌ی باز، اپ گوشی یا تلگرام را فعال کنید.', 'Browser notifications arrive only while a Pro Chart tab is open; with every tab closed nothing comes. For notifications without an open tab, use the phone app or Telegram.')}</p>
      <p className="muted small fa pulse-note">{L('حداکثر شش اعلان در روز، و از 23:00 تا 08:00 هیچ اعلانی نمی‌آید. هر نوع کلید جدا دارد.', 'At most six a day, and nothing between 23:00 and 08:00. Each kind has its own switch.')}</p>
      {!granted ? <p className="down small fa">{L('تا اجازه‌ی اعلان این مرورگر داده نشود، هیچ‌کدام از این‌ها نمی‌رسد.', 'None of these arrive until this browser allows notifications.')}</p> : null}
      {rows.map(([k, label, note]) => (
        <label key={k} className="check row-between" data-pulse={k}><span className="fa">{label}<br /><small className="muted">{note}</small></span><input type="checkbox" role="switch" checked={Boolean(p[k])} onChange={() => p.set({ [k]: !p[k] } as Partial<PulseSettings>)} /></label>
      ))}
      <label className="check row-between" data-pulse="brief"><span className="fa">{L('رصد صبح', 'Morning brief')}<br /><small className="muted">{L('بازارهای خودتان، روزی یک‌بار، ساعتی که خودتان انتخاب می‌کنید', 'Your own markets, once a day, at an hour you choose')}</small></span><input type="checkbox" role="switch" checked={p.brief} onChange={() => p.set({ brief: !p.brief })} /></label>
      {p.brief ? <div className="pulse-hour"><span className="fa">{L('ساعت', 'Hour')}</span><input type="time" aria-label="brief-hour" value={`${hh}:${mm}`} onChange={(e) => { const [h, m] = e.target.value.split(':').map(Number); if (Number.isFinite(h) && Number.isFinite(m)) p.set({ briefMinute: (h ?? 0) * 60 + (m ?? 0) }); }} /></div> : null}
      {watches.length ? (
        <div className="stack">
          <span className="muted small fa">{L(`اسکن‌های زیر نظر: ${faNum(watches.length)}`, `Watched scans: ${watches.length}`)}</span>
          {watches.map((w) => <div key={w.id} className="row-between"><span className="fa small">{L(`${faNum(w.symbols.length)} نماد · ${intervalLabel(w.tf)}`, `${w.symbols.length} markets · ${intervalLabel(w.tf)}`)}{w.minGrowth != null ? ` · ≥ ${w.minGrowth}` : ''}</span><button className="btn btn-quiet sm" onClick={() => removeWatch(w.id)}>{L('حذف', 'Remove')}</button></div>)}
        </div>
      ) : null}
    </section>
  );
}

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
        <p className="fa">{perm === 'granted' ? L('اعلان مرورگر فعال است.', 'Browser notifications are on.') : perm === 'denied' ? L('اعلان در تنظیمات مرورگر بسته شده است.', 'Notifications are blocked in the browser settings.') : perm === 'unsupported' ? L('این مرورگر اعلان ندارد.', 'This browser has no notifications.') : L('برای هشدارهای قیمت و اعلان‌های بازار در همین صفحه، اجازه‌ی اعلان بدهید.', 'Allow notifications for price alerts and market notifications on this page.')}</p>
        {perm === 'default' ? <button className="btn btn-primary sm" onClick={() => void Notification.requestPermission().then(setPerm)}>{L('اجازه‌ی اعلان', 'Allow notifications')}</button> : null}
        <label className="check row-between"><span className="fa">{L('صدای هشدار', 'Alert sound')}</span><input type="checkbox" role="switch" checked={local.sound} onChange={() => save({ ...local, sound: !local.sound })} /></label>
        <label className="check row-between"><span className="fa">{L('ساعت‌های سکوت', 'Quiet hours')}</span><input type="checkbox" role="switch" checked={local.quiet} onChange={() => save({ ...local, quiet: !local.quiet })} /></label>
        {local.quiet ? <div className="row"><input type="time" value={local.quietFrom} onChange={(e) => save({ ...local, quietFrom: e.target.value })} /><span>→</span><input type="time" value={local.quietTo} onChange={(e) => save({ ...local, quietTo: e.target.value })} /></div> : null}
      </section>
      <MarketNotifications granted={perm === 'granted'} />
      {sessions.tradeyar ? <PlatformPrefs backend="tradeyar" title={L('اعلان‌های رمزارز (تریدیار)', 'Crypto notifications (TradeYar)')} /> : null}
      {sessions.coineprofx ? <PlatformPrefs backend="coineprofx" title={L('اعلان‌های فارکس (کوین‌پرو اف‌ایکس)', 'Forex notifications (CoinePro-FX)')} /> : null}
      {!sessions.tradeyar && !sessions.coineprofx ? <p className="muted fa">{L('برای تنظیم اعلان‌های گوشی و تلگرام وارد شوید.', 'Sign in to set what the phone and Telegram receive.')}</p> : null}
    </div>
  );
}
