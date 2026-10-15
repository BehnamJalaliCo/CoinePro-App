import { useState } from 'react';
import { usePage } from '../state/page';
import { useT } from '../state/prefs';
import { eraseData, exportData, setTelemetry, telemetryOn } from '../data/panel';
import { archive } from '../pages/ProfilePage';
import { downloadText, errorText, useL } from '../pages/kit';
import { usePanel } from './PanelPage';
import { Card, Note, Row, Section, Switch } from './ui';

export default function Privacy() {
  const L = useL();
  const t = useT();
  const open = usePage((s) => s.open);
  const { reload, down } = usePanel();
  const [reports, setReports] = useState(telemetryOn);
  const [typed, setTyped] = useState('');
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);
  const word = L('پاک کن', 'ERASE');

  const download = async () => {
    setBusy(true); setMsg(null);
    try {
      const blob = await exportData();
      const a = document.createElement('a');
      a.href = URL.createObjectURL(blob); a.download = `pro-chart-panel-data-${new Date().toISOString().slice(0, 10)}.json`; a.click();
      setTimeout(() => URL.revokeObjectURL(a.href), 4000);
    } catch (e) { setMsg({ ok: false, text: errorText(e, t) }); } finally { setBusy(false); }
  };
  const erase = async () => {
    setBusy(true); setMsg(null);
    try { await eraseData(); setTyped(''); setMsg({ ok: true, text: L('داده‌های پنل شما پاک شد.', 'Your panel data was erased.') }); reload(); }
    catch (e) { setMsg({ ok: false, text: errorText(e, t) }); } finally { setBusy(false); }
  };

  return (
    <Section title={L('داده‌ها و حریم خصوصی', 'Data and privacy')} sub={L('آنچه پرو چارت برای پنل وب شما نگه می‌دارد، گرفتن یک نسخه از همه‌ی آن، و پاک کردنش.', 'What Pro Chart keeps for your web panel, getting a copy of all of it, and erasing it.')}>
      {msg ? <Note tone={msg.ok ? 'good' : 'bad'} icon={msg.ok ? 'ph-check' : 'ph-warning'}>{msg.text}</Note> : null}
      <Card title={L('آنچه نگه داشته می‌شود', 'What is kept')}>
        <ul className="ucp-bullets">
          <li>{L('ورود و خروج از وب، با خانواده‌ی مرورگر و دستگاه، کشور و چکیده‌ای نمک‌زده از نشانی (هرگز خود نشانی IP).', 'Web sign-ins and sign-outs, with the browser and device family, the country and a salted hash of the address (never the IP address itself).')}</li>
          <li>{L('هر سفارشی که از چارت وب فرستاده می‌شود: نماد، جهت، مقدار، نوع و پاسخ صرافی؛ هرگز کلید یا رمز.', 'Every order sent from the web chart: symbol, side, quantity, type and the exchange’s answer; never a key or a secret.')}</li>
          <li>{L('فضای کار ابری، اعلان‌ها و تیکت‌های پشتیبانی که خودتان می‌سازید.', 'The cloud workspace, notifications and support tickets you create.')}</li>
          <li>{L('گزارش خطاهای همین صفحه و قابلیت‌هایی که استفاده می‌کنید، فقط وقتی وارد شده‌اید و فقط به سرور پرو چارت.', 'Reports of this page’s errors and the features you use, only while signed in and only to Pro Chart’s own server.')}</li>
        </ul>
        <Row label={L('ارسال گزارش خطا و استفاده', 'Send error and usage reports')} hint={L('خاموش کنید تا این مرورگر چیزی از این دست نفرستد.', 'Turn off and this browser sends none of these.')}>
          <Switch on={reports} label={L('ارسال گزارش خطا و استفاده', 'Send error and usage reports')} onChange={(v) => { setReports(v); setTelemetry(v); }} />
        </Row>
        <Row label={L('سیاست حریم خصوصی', 'Privacy policy')}><button className="btn btn-quiet sm" onClick={() => open('privacy')}>{L('خواندن', 'Read')}</button></Row>
      </Card>
      <Card title={L('گرفتن نسخه', 'Get a copy')}>
        <Row label={L('داده‌های پنل', 'Panel data')} hint={L('یک فایل JSON با همه‌ی آنچه سرور پنل برای حساب شما نگه می‌دارد.', 'One JSON file with everything the panel server keeps for your account.')}>
          <button className="btn btn-quiet sm" disabled={busy || Boolean(down)} onClick={() => void download()}>{L('دریافت فایل', 'Download')}</button>
        </Row>
        <Row label={L('بایگانی این مرورگر', 'This browser’s archive')} hint={L('دیده‌بان، ترسیم‌ها، چیدمان‌ها و تنظیمات که فقط در همین مرورگر است؛ بدون اطلاعات ورود.', 'Watchlists, drawings, layouts and settings kept only in this browser; never the sign-in.')}>
          <button className="btn btn-quiet sm" onClick={() => downloadText(`pro-chart-archive-${new Date().toISOString().slice(0, 10)}.json`, JSON.stringify(archive()))}>{L('دریافت فایل', 'Download')}</button>
        </Row>
      </Card>
      <Card title={L('پاک کردن', 'Erase')} className="ucp-danger">
        <p>{L('همه‌ی داده‌های پنل وب شما — فضای کار ابری و نسخه‌هایش، اعلان‌ها، تیکت‌ها، تاریخچه‌ی فعالیت — همین حالا پاک می‌شود. فهرست سفارش‌ها بدون نام شما برای بازبینی می‌ماند. حساب شما روی پلتفرم‌ها دست نمی‌خورد.', 'All your web panel data — the cloud workspace and its versions, notifications, tickets, activity history — is erased now. The order log stays, without your name, for review. Your accounts on the platforms are untouched.')}</p>
        <label className="field"><span>{L(`برای تأیید «${word}» را بنویسید`, `Type ${word} to confirm`)}</span><input value={typed} onChange={(e) => setTyped(e.target.value)} dir="auto" aria-label="confirm" /></label>
        <div className="ucp-actions">
          <button className="btn btn-quiet sm down" disabled={busy || typed.trim() !== word || Boolean(down)} onClick={() => void erase()}>{L('پاک کردن داده‌های پنل', 'Erase the panel data')}</button>
          <button className="btn btn-quiet sm down" onClick={() => open('delete')}>{L('حذف حساب', 'Delete the account')}</button>
        </div>
      </Card>
    </Section>
  );
}
