import { useState } from 'react';
import { usePage } from '../state/page';
import { usePrefs, useT } from '../state/prefs';
import { useSession } from '../data/session';
import { forgotPassword, signOut } from '../data/auth';
import { getSecurity, getSessions, revokeSessions, type WebSession } from '../data/panel';
import { State, errorText, useL, useLoad } from '../pages/kit';
import { Icon } from '../ui/Icon';
import { Card, COUNTRY, Note, Pill, Row, Section, stamp, useAgo, useCount } from './ui';
import { deviceLabel, eventLabel } from './labels';

const deviceIcon = (d: string | null | undefined) => (d === 'phone' ? 'ph-device-mobile' : d === 'tablet' ? 'ph-device-tablet' : 'ph-desktop');

export default function Security() {
  const L = useL();
  const t = useT();
  const lang = usePrefs((s) => s.lang);
  const ago = useAgo();
  const count = useCount();
  const open = usePage((s) => s.open);
  const sessions = useSession((s) => s.sessions);
  const live = useLoad(() => getSessions().then((r) => r.sessions), []);
  const log = useLoad(() => getSecurity(), []);
  const [busy, setBusy] = useState<string | null>(null);
  const [msg, setMsg] = useState<string | null>(null);
  const email = sessions.tradeyar?.user?.email ?? sessions.coineprofx?.user?.email ?? '';

  const revoke = async (key: string, body: Parameters<typeof revokeSessions>[0], ask: string, endsHere = false) => {
    if (!window.confirm(ask)) return;
    setBusy(key); setMsg(null);
    try {
      const r = await revokeSessions(body);
      if (endsHere) { signOut(); return; }
      setMsg(L(`${count(r.revoked)} نشست بسته شد.`, `${r.revoked} session(s) closed.`));
      live.reload(); log.reload();
    } catch (e) { setMsg(errorText(e, t)); } finally { setBusy(null); }
  };

  const reset = async () => {
    if (!email) return;
    setBusy('reset'); setMsg(null);
    try { await forgotPassword(email); setMsg(L(`کد بازیابی به ${email} فرستاده شد. آن را در صفحه‌ی بازیابی رمز وارد کنید.`, `A reset code went to ${email}. Enter it on the reset page.`)); }
    catch (e) { setMsg(errorText(e, t)); } finally { setBusy(null); }
  };

  const others = (live.data ?? []).filter((s) => !s.current);
  return (
    <Section title={L('امنیت و نشست‌ها', 'Security and sessions')} sub={L('مرورگرهایی که الان با حساب شما وارد وب‌اند، تاریخچه‌ی ورود و دستگاه‌ها. نشست‌های اپ اندروید جدا مدیریت می‌شوند.', 'The browsers signed in to the web with your account right now, your sign-in history and devices. Android app sessions are managed separately.')}>
      {msg ? <Note tone="info">{msg}</Note> : null}
      {log.data?.failed_30d ? <Note tone="warn" icon="ph-warning">{L(`${count(log.data.failed_30d)} تلاش ناموفق برای ورود به حساب شما در ۳۰ روز اخیر ثبت شده. اگر کار شما نبوده، رمز را عوض کنید.`, `${log.data.failed_30d} failed sign-in attempt(s) on your account in the last 30 days. If that was not you, change your password.`)}</Note> : null}

      <Card title={L('نشست‌های وب', 'Web sessions')} sub={L('بستن یک نشست، آن مرورگر را همین حالا از حساب خارج می‌کند.', 'Closing a session signs that browser out right away.')}
        actions={<>
          <button className="btn btn-quiet sm" disabled={!others.length || busy !== null} onClick={() => void revoke('others', { others: true }, L('همه‌ی مرورگرهای دیگر خارج شوند؟', 'Sign out every other browser?'))}>{L('خروج از دیگر مرورگرها', 'Sign out other browsers')}</button>
          <button className="btn btn-quiet sm down" disabled={busy !== null} onClick={() => void revoke('all', { all: true }, L('از همه‌ی مرورگرها، از جمله همین یکی، خارج می‌شوید. ادامه می‌دهید؟', 'You will be signed out of every browser, this one included. Continue?'), true)}>{L('خروج از همه‌جا', 'Sign out everywhere')}</button>
        </>}>
        <State loading={live.loading} error={live.error} onRetry={live.reload} empty={!live.data?.length}>
          <ul className="ucp-sessions">
            {(live.data ?? []).map((s: WebSession) => (
              <li key={s.sid} className={s.current ? 'current' : ''}>
                <Icon name={deviceIcon(s.device)} size="md" />
                <div>
                  <b>{[s.browser, s.os].filter(Boolean).join(' · ')} <span className="ucp-muted">{deviceLabel(s.device, L)}</span></b>
                  <span className="ucp-muted">{COUNTRY(s.country, lang)} · {L('از', 'since')} <span className="num">{stamp(s.created * 1000)}</span> · {L('آخرین فعالیت', 'last active')} {ago(s.last_seen * 1000)}</span>
                </div>
                {s.current ? <Pill tone="good">{L('همین مرورگر', 'This browser')}</Pill> : <button className="btn btn-quiet sm" disabled={busy !== null} onClick={() => void revoke(s.sid, { sid: s.sid }, L('این نشست بسته شود؟', 'Close this session?'))}>{L('بستن', 'Close')}</button>}
              </li>
            ))}
          </ul>
        </State>
      </Card>

      <div className="ucp-grid">
        <Card title={L('رمز عبور', 'Password')}>
          <Row label={L('بازیابی یا تغییر رمز', 'Reset or change the password')} hint={email ? <span className="num">{email}</span> : L('ایمیلی برای این حساب ثبت نیست', 'No email on this account')}>
            <button className="btn btn-quiet sm" disabled={!email || busy !== null} onClick={() => void reset()}>{L('ارسال کد بازیابی', 'Send a reset code')}</button>
          </Row>
          <Row label={L('کد را دارید؟', 'Have a code?')}>
            <button className="btn btn-quiet sm" onClick={() => open('reset')}>{L('صفحه‌ی بازیابی رمز', 'Reset page')}</button>
          </Row>
        </Card>
        <Card title={L('دستگاه‌ها', 'Devices')} sub={L('مرورگرهایی که از آن‌ها وارد وب شده‌اید.', 'The browsers you have signed in to the web from.')}>
          <State loading={log.loading} error={log.error} onRetry={log.reload} empty={!log.data?.devices.length}>
            <ul className="ucp-devices">
              {(log.data?.devices ?? []).map((d, i) => (
                <li key={i}><Icon name={deviceIcon(d.device)} size="sm" /><span>{[d.browser, d.os].filter(Boolean).join(' · ')}</span><span className="ucp-muted">{L(`${count(d.sign_ins)} ورود`, `${d.sign_ins} sign-ins`)}</span><span className="ucp-muted">{ago(d.last_seen * 1000)}</span></li>
              ))}
            </ul>
          </State>
        </Card>
      </div>

      <Card title={L('تاریخچه‌ی ورود و امنیت', 'Sign-in and security history')}>
        <State loading={log.loading} error={log.error} onRetry={log.reload} empty={!log.data?.items.length}>
          <div className="ucp-table-wrap">
            <table className="table ucp-table">
              <thead><tr><th>{L('رویداد', 'Event')}</th><th>{L('مرورگر', 'Browser')}</th><th>{L('کشور', 'Country')}</th><th className="r">{L('زمان', 'Time')}</th></tr></thead>
              <tbody>{(log.data?.items ?? []).map((e) => (
                <tr key={e.id} className={e.kind === 'signin_failed' ? 'ucp-bad' : ''}><td>{eventLabel(e, L)}</td><td>{[e.browser, e.os].filter(Boolean).join(' · ') || '—'}</td><td>{COUNTRY(e.country, lang)}</td><td className="r num">{stamp(e.at * 1000)}</td></tr>
              ))}</tbody>
            </table>
          </div>
        </State>
      </Card>
    </Section>
  );
}
