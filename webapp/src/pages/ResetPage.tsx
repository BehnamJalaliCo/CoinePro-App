import { useState } from 'react';
import { useT } from '../state/prefs';
import { usePage } from '../state/page';
import { resetPassword, forgotPassword } from '../data/auth';
import { Field, errorText, useL } from './kit';

// Reset the password: the emailed link opens /next/reset?token=…, or the code from the email is
// typed here; the new password is set on whichever platform holds the account.
export default function ResetPage() {
  const L = useL();
  const t = useT();
  const close = usePage((s) => s.close);
  const [token, setToken] = useState(() => new URLSearchParams(window.location.search).get('token') ?? '');
  const [email, setEmail] = useState('');
  const [pass, setPass] = useState('');
  const [again, setAgain] = useState('');
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);
  const done = msg?.ok && !email;

  const send = async () => {
    if (!/^\S+@\S+\.\S+$/.test(email.trim())) return;
    setBusy(true);
    try { await forgotPassword(email); setMsg({ ok: true, text: L('اگر این ایمیل حساب داشته باشد، کد بازیابی فرستاده شد.', 'If this email has an account, a reset code is on its way.') }); }
    catch (e) { setMsg({ ok: false, text: errorText(e, t) }); } finally { setBusy(false); }
  };
  const reset = async () => {
    if (pass.length < 8) { setMsg({ ok: false, text: L('رمز دست‌کم ۸ نویسه است.', 'The password has at least 8 characters.') }); return; }
    if (pass !== again) { setMsg({ ok: false, text: L('دو رمز یکی نیستند.', 'The two passwords differ.') }); return; }
    setBusy(true); setMsg(null);
    try { await resetPassword(token, pass); setEmail(''); setMsg({ ok: true, text: L('رمز تازه ثبت شد؛ حالا وارد شوید.', 'The new password is set; sign in now.') }); window.history.replaceState(null, '', window.location.pathname); }
    catch (e) { setMsg({ ok: false, text: errorText(e, t) }); } finally { setBusy(false); }
  };

  return (
    <div className="page-inner narrow">
      {!token ? (
        <form className="card form" onSubmit={(e) => { e.preventDefault(); void send(); }}>
          <Field label={L('ایمیل حساب', 'Account email')}><input type="email" dir="ltr" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" /></Field>
          <button className="btn btn-quiet" disabled={busy}>{L('فرستادن کد بازیابی', 'Send the reset code')}</button>
          <Field label={L('کد بازیابی از ایمیل', 'Reset code from the email')}><input className="num" dir="ltr" value={token} onChange={(e) => setToken(e.target.value)} /></Field>
        </form>
      ) : null}
      {token && !done ? (
        <form className="card form" onSubmit={(e) => { e.preventDefault(); void reset(); }}>
          <Field label={L('رمز تازه', 'New password')}><input type="password" value={pass} onChange={(e) => setPass(e.target.value)} autoComplete="new-password" /></Field>
          <Field label={L('تکرار رمز تازه', 'New password again')}><input type="password" value={again} onChange={(e) => setAgain(e.target.value)} autoComplete="new-password" /></Field>
          <button className="btn btn-primary" disabled={busy}>{L('ثبت رمز تازه', 'Set the new password')}</button>
        </form>
      ) : null}
      {msg ? <p className={`fa ${msg.ok ? 'up' : 'down'}`}>{msg.text}</p> : null}
      {done ? <button className="btn btn-primary sm" onClick={close}>{L('بازگشت به چارت', 'Back to the chart')}</button> : null}
    </div>
  );
}
