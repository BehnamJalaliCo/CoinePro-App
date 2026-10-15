import { useEffect, useState } from 'react';
import { usePrefs, useT } from '../state/prefs';
import { request, HttpError } from '../data/http';
import { authed } from '../data/auth';
import { useSession } from '../data/session';
import { Icon } from '../ui/Icon';
import { faNum } from '../i18n/time';

// «پرو چارت پرمیوم» on the web: the plans TradeYar publishes, paid in USDT on BEP20 to the wallet
// it names, confirmed by the transaction hash (POST payments/usdt). The same checkout the phone's
// web build had; Bazaar is Android-only.
interface Plan { id: string; days: number; price_toman: number; price_usdt: string }
interface Plans { plans: Plan[]; usdt?: { network: string; wallet: string }; paywall?: boolean }
interface Entitlement { premium?: boolean; plan?: string; ends_at?: string }

const PLAN_KEY = { monthly: 'planMonthly', quarterly: 'planQuarterly', yearly: 'planYearly' } as const;

export function PremiumDialog({ onClose, onSignIn }: { onClose(): void; onSignIn(): void }) {
  const t = useT();
  return (
    <div className="scrim" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div className="dialog premium" role="dialog" aria-modal="true" aria-label={t('premium')} onKeyDown={(e) => { if (e.key === 'Escape') onClose(); }}>
        <header className="dialog-head">
          <h2 className="fa">{t('premiumTitle')}</h2>
          <button className="ib" onClick={onClose} aria-label="close"><Icon name="ph-x" size="md" /></button>
        </header>
        <PremiumBody onSignIn={onSignIn} />
      </div>
    </div>
  );
}

/** The plans, the USDT checkout and the reader's current entitlement; also the Pro page's body. */
export function PremiumBody({ onSignIn }: { onSignIn(): void }) {
  const t = useT();
  const lang = usePrefs((s) => s.lang);
  const signed = useSession((s) => Boolean(s.sessions.tradeyar));
  const [plans, setPlans] = useState<Plans | null>(null);
  const [ent, setEnt] = useState<Entitlement | null>(null);
  const [pick, setPick] = useState<Plan | null>(null);
  const [hash, setHash] = useState('');
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    void request<Plans>('/up/tradeyar/api/mobile/v1/payments/plans').then(setPlans).catch(() => setPlans({ plans: [] }));
    if (signed) void authed<Entitlement>('tradeyar', '/up/tradeyar/api/mobile/v1/entitlements').then(setEnt).catch(() => undefined);
  }, [signed]);

  const submit = async () => {
    if (!pick || !/^0x[0-9a-fA-F]{64}$/.test(hash.trim())) { setMsg({ ok: false, text: t('premiumTxInvalid') }); return; }
    setBusy(true); setMsg(null);
    try {
      const r = await authed<Entitlement>('tradeyar', '/up/tradeyar/api/mobile/v1/payments/usdt', { body: { plan: pick.id, tx_hash: hash.trim() } });
      setEnt(r);
      setMsg({ ok: true, text: t('premiumOk') });
    } catch (e) {
      setMsg({ ok: false, text: (e instanceof HttpError && e.readerMessage) || t('networkError') });
    } finally { setBusy(false); }
  };

  return (
  <div className="premium-body" dir={lang === 'fa' ? 'rtl' : 'ltr'}>
    {ent?.premium ? (
      <p className="hint">{`${t('premiumActive')}${ent.ends_at ? ` ${t('premiumUntil')} ${new Intl.DateTimeFormat(lang === 'fa' ? 'fa-IR-u-ca-persian' : 'en-GB', { dateStyle: 'long' }).format(new Date(ent.ends_at))}` : ''}.`}</p>
    ) : null}
    {plans === null ? <p className="hint">{t('loading')}…</p> : null}
    <div className="plans">
      {(plans?.plans ?? []).map((p) => (
        <button key={p.id} className={`plan${pick?.id === p.id ? ' on' : ''}`} onClick={() => setPick(p)}>
          <span className="plan-name">{p.id in PLAN_KEY ? t(PLAN_KEY[p.id as keyof typeof PLAN_KEY]) : p.id}</span>
          <span className="plan-price num">{p.price_usdt} USDT</span>
          <span className="plan-days">{`${lang === 'fa' ? faNum(p.days) : p.days} ${t('days')}`}</span>
        </button>
      ))}
    </div>
    {pick && plans?.usdt ? (
      <div className="pay">
        <p className="hint">{t('premiumPay')} <b className="num">{pick.price_usdt} USDT · {plans.usdt.network}</b></p>
        <div className="wallet num" dir="ltr">
          <code>{plans.usdt.wallet}</code>
          <button className="lt" onClick={() => { void navigator.clipboard?.writeText(plans.usdt!.wallet); setCopied(true); setTimeout(() => setCopied(false), 1500); }} aria-label="copy">
            <Icon name={copied ? 'ph-check' : 'ph-copy'} size="sm" />
          </button>
        </div>
        {signed ? (
          <>
            <input className="num tx" dir="ltr" placeholder="0x…" value={hash} onChange={(e) => setHash(e.target.value)} spellCheck={false} />
            <button className="btn btn-primary" disabled={busy} onClick={() => void submit()}>{t('premiumConfirm')}</button>
          </>
        ) : (
          <button className="btn btn-primary" onClick={onSignIn}>{t('signIn')}</button>
        )}
      </div>
    ) : null}
    {msg ? <p className={msg.ok ? 'ok' : 'error'}>{msg.text}</p> : null}
  </div>
  );
}
