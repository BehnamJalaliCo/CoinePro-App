import { useState } from 'react';
import { useT } from '../state/prefs';
import { usePage } from '../state/page';
import { useSession } from '../data/session';
import { authed, signOut } from '../data/auth';
import { ROOT } from '../data/member';
import { errorText, useL } from './kit';
import type { Backend } from '../data/symbols';

// Delete the account (feature/account): the platform deletes it for good; typing the word first,
// then the request on every platform the reader is signed in to, then a local sign-out.
export default function DeleteAccountPage() {
  const L = useL();
  const t = useT();
  const close = usePage((s) => s.close);
  const sessions = useSession((s) => s.sessions);
  const word = L('حذف', 'DELETE');
  const [typed, setTyped] = useState('');
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const go = async () => {
    setBusy(true); setMsg(null);
    try {
      for (const b of ['tradeyar', 'coineprofx'] as Backend[]) if (sessions[b]) await authed(b, `${ROOT[b]}/account`, { method: 'DELETE' });
      signOut(); close();
    } catch (e) { setMsg(errorText(e, t)); } finally { setBusy(false); }
  };
  return (
    <div className="page-inner narrow">
      <section className="card warn">
        <h3 className="fa">{L('حذف همیشگی حساب', 'Delete the account for good')}</h3>
        <p className="fa">{L('حساب، تاریخچه‌ی سیگنال‌ها، هشدارها و تنظیمات روی سرور پاک می‌شوند و برنمی‌گردند. اشتراک پرمیوم فعال هم از بین می‌رود. اتصال صرافی قطع می‌شود ولی سفارش‌های باز روی صرافی می‌مانند.', 'The account, signal history, alerts and settings are erased on the server and cannot come back. An active premium plan ends too. The exchange link is removed, but open orders stay on the exchange.')}</p>
        <p className="fa">{L(`برای تأیید، کلمه‌ی «${word}» را بنویسید.`, `To confirm, type ${word}.`)}</p>
        <input value={typed} onChange={(e) => setTyped(e.target.value)} dir="auto" aria-label="confirm" />
        {msg ? <p className="down fa">{msg}</p> : null}
        <button className="btn sell" disabled={busy || typed.trim() !== word} onClick={() => void go()}>{L('حذف حساب', 'Delete the account')}</button>
      </section>
    </div>
  );
}
