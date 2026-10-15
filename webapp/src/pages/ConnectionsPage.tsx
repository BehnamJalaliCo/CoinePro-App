import { useState } from 'react';
import { useT } from '../state/prefs';
import { useSession } from '../data/session';
import { ty, str } from '../data/member';
import { Field, State, Tabs, errorText, useL, useLoad } from './kit';

// Exchange connections (feature/connections, core/execution ExecutionGateway): the LBank keys a
// TradeYar account trades and executes signals with. Keys go to the platform once and are never
// shown again; only a hint comes back. Only the server's word that the link is live enables trading.
interface Venue { configured: boolean; connected: boolean; status: string; permission: string; keyHint: string }

export default function ConnectionsPage() {
  const L = useL();
  const t = useT();
  const signed = useSession((s) => Boolean(s.sessions.tradeyar));
  const venue = useLoad(() => ty<Record<string, unknown>>('api/mobile/v1/venues/lbank').then((x): Venue => ({
    configured: Boolean(x.configured), connected: Boolean(x.connected), status: str(x.status), permission: str(x.permission), keyHint: str(x.key_hint),
  })), [signed]);
  const [key, setKey] = useState('');
  const [secret, setSecret] = useState('');
  const [perm, setPerm] = useState<'spot' | 'futures'>('futures');
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);

  if (!signed) return <div className="empty fa">{L('اتصال صرافی با حساب تریدیار است؛ وارد شوید.', 'Exchange connections need a TradeYar account; sign in.')}</div>;
  const v = venue.data;
  const connect = async () => {
    if (key.trim().length < 8 || secret.trim().length < 8) { setMsg(L('کلید و رمز API را کامل وارد کنید.', 'Enter the full API key and secret.')); return; }
    setBusy(true); setMsg(null);
    try { await ty('api/mobile/v1/venues/lbank', { body: { api_key: key.trim(), api_secret: secret.trim(), permission: perm } }); setKey(''); setSecret(''); venue.reload(); }
    catch (e) { setMsg(errorText(e, t)); } finally { setBusy(false); }
  };
  const disconnect = async () => {
    if (!window.confirm(L('اتصال LBank قطع شود؟ سفارش‌های باز روی صرافی می‌مانند.', 'Disconnect LBank? Open orders stay on the exchange.'))) return;
    setBusy(true);
    try { await ty('api/mobile/v1/venues/lbank', { method: 'DELETE' }); venue.reload(); } catch (e) { setMsg(errorText(e, t)); } finally { setBusy(false); }
  };

  return (
    <div className="page-inner narrow">
      <section className="card">
        <h3>LBank</h3>
        <State loading={venue.loading} error={venue.error} onRetry={venue.reload}>
          {v ? (
            <dl className="kv">
              <dt className="fa">{L('وضعیت', 'Status')}</dt><dd className={v.connected ? 'up' : v.configured ? 'down' : 'muted'}>{v.connected ? L('متصل و تأییدشده', 'Connected and verified') : v.configured ? L('ذخیره شده ولی تأیید نشده', 'Saved but not verified') : L('متصل نیست', 'Not connected')}{v.status ? ` · ${v.status}` : ''}</dd>
              {v.permission ? <><dt className="fa">{L('دسترسی', 'Permission')}</dt><dd>{v.permission}</dd></> : null}
              {v.keyHint ? <><dt className="fa">{L('کلید', 'Key')}</dt><dd className="num">{v.keyHint}</dd></> : null}
            </dl>
          ) : null}
        </State>
        {v?.configured ? <button className="btn btn-quiet sm" disabled={busy} onClick={() => void disconnect()}>{L('قطع اتصال', 'Disconnect')}</button> : null}
      </section>
      <form className="card form" onSubmit={(e) => { e.preventDefault(); void connect(); }} autoComplete="off">
        <h3 className="fa">{v?.configured ? L('جایگزینی کلیدها', 'Replace the keys') : L('اتصال LBank', 'Connect LBank')}</h3>
        <Tabs value={perm} onChange={setPerm} tabs={[{ id: 'futures', label: L('فیوچرز', 'Futures') }, { id: 'spot', label: L('اسپات', 'Spot') }]} />
        <Field label="API key"><input dir="ltr" value={key} onChange={(e) => setKey(e.target.value)} autoComplete="off" spellCheck={false} /></Field>
        <Field label="API secret"><input dir="ltr" type="password" value={secret} onChange={(e) => setSecret(e.target.value)} autoComplete="new-password" spellCheck={false} /></Field>
        <p className="muted small fa">{L('کلید را بدون دسترسی برداشت بسازید. کلیدها فقط یک بار به سرور تریدیار فرستاده می‌شوند و در این مرورگر نمی‌مانند.', 'Create the key without withdrawal permission. The keys are sent once to the TradeYar server and are not kept in this browser.')}</p>
        {msg ? <p className="down fa">{msg}</p> : null}
        <button className="btn btn-primary" disabled={busy}>{L('ذخیره و بررسی اتصال', 'Save and verify')}</button>
      </form>
      <p className="muted small fa">{L('متاتریدر از اینجا وصل نمی‌شود: کوین‌پرو اف‌ایکس حساب کارگزار را از راه کپی‌تریدینگ خودش وصل می‌کند.', 'MetaTrader is not linked from here: CoinePro-FX links a broker account through its own copy trading.')}</p>
    </div>
  );
}
