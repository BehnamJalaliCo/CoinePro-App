import { useState } from 'react';
import { useT } from '../state/prefs';
import { ty, pub, num, str } from '../data/member';
import { Field, State, errorText, useL, useLoad } from './kit';

// Exchange membership (feature/membership, TradeYar): the referral sign-up with LBank or Ourbit,
// then the exchange UID for the platform to check; the status it answers, in its own Persian words.
const STATUS: Record<string, [string, string]> = {
  awaiting_uid: ['در انتظار ثبت UID', 'Waiting for your UID'], verifying: ['در حال بررسی', 'Verifying'], approved: ['تأیید شده', 'Approved'],
  pending_deposit: ['در انتظار واریز', 'Waiting for the deposit'], rejected_referral: ['ثبت‌نام با لینک ما نبوده', 'Not registered with our link'], error: ['خطا', 'Error'], pending: ['در انتظار', 'Pending'],
};

export default function MembershipPage() {
  const L = useL();
  const t = useT();
  const status = useLoad(() => ty<Record<string, unknown>>('api/mobile/v1/membership/status'), []);
  const info = useLoad(() => pub<Record<string, unknown>>('/up/tradeyar/api/v1/public/membership'), []);
  const exchanges = (Array.isArray(info.data?.uid_exchanges) ? info.data!.uid_exchanges : ['lbank', 'ourbit']) as string[];
  const [exchange, setExchange] = useState('lbank');
  const [uid, setUid] = useState('');
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const s = status.data;
  const code = str(s?.status);
  const canSend = !s || code === 'awaiting_uid' || Boolean(s.can_resubmit) || str(s.next_step) === 'uid';

  const submit = async () => {
    const clean = uid.replace(/[۰-۹]/g, (d) => String(d.charCodeAt(0) - 0x06f0)).replace(/\s/g, '');
    if (!/^\d{4,20}$/.test(clean)) { setMsg(L('UID فقط عدد است.', 'The UID is digits only.')); return; }
    setBusy(true); setMsg(null);
    try { await ty('api/mobile/v1/membership/uid', { body: { exchange, uid: clean } }); setUid(''); status.reload(); }
    catch (e) { setMsg(errorText(e, t)); } finally { setBusy(false); }
  };

  return (
    <div className="page-inner narrow">
      <section className="card">
        <h3 className="fa">{L('وضعیت عضویت', 'Membership status')}</h3>
        <State loading={status.loading} error={status.error} onRetry={status.reload}>
          <p className={code === 'approved' ? 'up fa' : 'fa'}><b>{STATUS[code] ? L(...STATUS[code]) : code || L('ثبت نشده', 'Not registered')}</b></p>
          {s?.message_fa ? <p className="fa">{str(s.message_fa)}</p> : null}
          {s?.uid ? <p className="num muted">{str(s.exchange)} · UID {str(s.uid)}</p> : null}
          {s?.note ? <p className="muted fa">{str(s.note)}</p> : null}
        </State>
      </section>
      <section className="card">
        <h3 className="fa">{L('۱. ثبت‌نام در صرافی با لینک ما', '1. Sign up on the exchange with our link')}</h3>
        <State loading={info.loading} error={info.error}>
          {info.data?.notice_fa ? <p className="fa">{str(info.data.notice_fa)}</p> : null}
          <div className="row">
            {info.data?.lbank_referral_url ? <a className="btn btn-quiet sm" href={str(info.data.lbank_referral_url)} target="_blank" rel="noreferrer">LBank</a> : null}
            {info.data?.ourbit_referral_url ? <a className="btn btn-quiet sm" href={str(info.data.ourbit_referral_url)} target="_blank" rel="noreferrer">Ourbit</a> : null}
          </div>
          {num(info.data?.min_deposit_usdt) ? <p className="muted fa">{L('حداقل واریز', 'Minimum deposit')} <b className="num">{num(info.data?.min_deposit_usdt)} USDT</b></p> : null}
        </State>
      </section>
      {canSend ? (
        <form className="card form" onSubmit={(e) => { e.preventDefault(); void submit(); }}>
          <h3 className="fa">{L('۲. ثبت UID صرافی', '2. Send your exchange UID')}</h3>
          <Field label={L('صرافی', 'Exchange')}><select value={exchange} onChange={(e) => setExchange(e.target.value)}>{exchanges.map((x) => <option key={x} value={x}>{x}</option>)}</select></Field>
          <Field label="UID"><input className="num" dir="ltr" inputMode="numeric" value={uid} onChange={(e) => setUid(e.target.value)} /></Field>
          {msg ? <p className="down fa">{msg}</p> : null}
          <button className="btn btn-primary" disabled={busy}>{L('ارسال برای بررسی', 'Send for review')}</button>
        </form>
      ) : null}
    </div>
  );
}
