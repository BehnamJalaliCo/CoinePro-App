import { useState, type FormEvent } from 'react';
import { useT } from '../state/prefs';
import { useSession } from '../data/session';
import { Field, errorText, useL } from '../pages/kit';
import { forexRegisterStart, forexRegisterVerify, forexSignIn } from './forexAuth';

// The forex VIP lives on CoinePro-FX. A reader signed in on TradeYar has no session there, so this
// signs in (or registers with the e-mailed code) on CoinePro-FX alone, beside the TradeYar session.
export function ForexAccount({ onTradeYarSignIn }: { onTradeYarSignIn(): void }) {
  const L = useL();
  const t = useT();
  const user = useSession((s) => s.sessions.tradeyar?.user ?? null);
  const [mode, setMode] = useState<'in' | 'up' | 'code'>('in');
  const [email, setEmail] = useState(user?.email ?? '');
  const [password, setPassword] = useState('');
  const [name, setName] = useState(user?.name ?? '');
  const [code, setCode] = useState('');
  const [token, setToken] = useState('');
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const run = async (e: FormEvent, fn: () => Promise<void>) => {
    e.preventDefault(); setBusy(true); setMsg(null);
    try { await fn(); } catch (err) { setMsg(errorText(err, t)); } finally { setBusy(false); }
  };
  return (
    <div className="stack vip-fx-account">
      <h3 className="fa">{L('حساب فارکس (کوین‌پرو اف‌ایکس)', 'Forex account (CoinePro-FX)')}</h3>
      <p className="muted fa">{L('عضویت فارکس روی حساب کوین‌پرو اف‌ایکس ثبت می‌شود. با همان ایمیل وارد شوید، یا اگر حساب ندارید بسازید.', 'Forex membership lives on the CoinePro-FX account. Sign in with the same e-mail, or create one.')}</p>
      {mode === 'code' ? (
        <form className="form stack" onSubmit={(e) => void run(e, () => forexRegisterVerify(token, code))}>
          <Field label={L('کد ارسال‌شده به ایمیل', 'Code sent to your e-mail')}><input className="num" dir="ltr" inputMode="numeric" autoComplete="one-time-code" value={code} onChange={(e) => setCode(e.target.value)} /></Field>
          {msg ? <p className="down fa" role="alert">{msg}</p> : null}
          <button className="btn btn-primary" disabled={busy}><span className="fa">{L('تأیید کد', 'Verify')}</span></button>
        </form>
      ) : (
        <form className="form stack" onSubmit={(e) => void run(e, mode === 'in' ? () => forexSignIn(email, password) : async () => { const r = await forexRegisterStart(email, password, name); setToken(r.token); setMode('code'); })}>
          {mode === 'up' ? <Field label={L('نام و نام خانوادگی', 'Full name')}><input value={name} onChange={(e) => setName(e.target.value)} dir="auto" required /></Field> : null}
          <Field label={L('ایمیل', 'E-mail')}><input type="email" dir="ltr" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} required /></Field>
          <Field label={L('رمز عبور', 'Password')}><input type="password" dir="ltr" autoComplete={mode === 'in' ? 'current-password' : 'new-password'} value={password} onChange={(e) => setPassword(e.target.value)} required /></Field>
          {msg ? <p className="down fa" role="alert">{msg}</p> : null}
          <button className="btn btn-primary" disabled={busy}><span className="fa">{mode === 'in' ? L('ورود به حساب فارکس', 'Sign in to forex') : L('ساخت حساب فارکس', 'Create the forex account')}</span></button>
          <div className="row">
            <button type="button" className="link-btn" onClick={() => { setMode(mode === 'in' ? 'up' : 'in'); setMsg(null); }}><span className="fa">{mode === 'in' ? L('حساب فارکس ندارم', 'I have no forex account') : L('حساب دارم؛ ورود', 'I have one; sign in')}</span></button>
            {!user ? <button type="button" className="link-btn" onClick={onTradeYarSignIn}><span className="fa">{L('ورود به پرو چارت', 'Sign in to Pro Chart')}</span></button> : null}
          </div>
        </form>
      )}
    </div>
  );
}
