import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import { usePrefs, useT } from '../state/prefs';
import { usePage } from '../state/page';
import { useSession } from '../data/session';
import { faNum } from '../i18n/time';
import { Icon } from '../ui/Icon';
import { Field, State, Tabs, errorText, useL, useLoad } from '../pages/kit';
import type { PageProps } from '../pages/registry';
import { cryptoRules, forexClaim, forexRules, registration, submitUid, vipInvite, vipRecheck, vipState, type CryptoRules, type Registration } from './api';
import { cleanUid, cryptoStep, outcomeOf, readLocal, usdt, writeLocal, type CryptoStep, type Market, type VipLocal } from './logic';
import { useVip } from './store';
import { BotText, Countdown, PartnerLink, Pill, StepRail } from './ui';
import { ForexAccount } from './ForexAccount';

// «عضویت رایگان VIP»: the sign-up funnel TradeYar runs in its bot and on its user site, on Pro
// Chart. Crypto: the Pro Chart account, an exchange account through our referral link (LBank by
// default, Ourbit beside it), the deposit, the UID, and the verdict with its next step — re-checked
// automatically on the server. Forex: OneRoyal through our link, the deposit, the account number for
// the support team's check; signals only. Every number and sentence is the servers'.
export default function VipJoinPage({ onSignIn }: PageProps) {
  const L = useL();
  const [local, setLocal] = useState<VipLocal>(readLocal);
  const save = useCallback((n: Partial<VipLocal>) => setLocal((p) => { const next = { ...p, ...n }; writeLocal(next); return next; }), []);
  return (
    <div className="page-inner vip-page">
      <header className="vip-hero">
        <div className="vip-hero-text">
          <Pill tone="ok">{L('رایگان', 'Free')}</Pill>
          <h2 className="fa">{L('کانال سیگنال VIP و همه‌ی امکانات پرو چارت، رایگان', 'The VIP signal channel and all of Pro Chart, free')}</h2>
          <p className="fa muted">{L('با لینک ما حساب صرافی یا بروکر باز کنید و پول را در حساب خودتان نگه دارید؛ ما از کمیسیون صرافی درآمد داریم، نه از شما.', 'Open an exchange or broker account through our link and keep the money in your own account; we are paid by the exchange, not by you.')}</p>
        </div>
        <Tabs value={local.market} onChange={(m: Market) => save({ market: m })} tabs={[{ id: 'crypto', label: L('رمزارز · LBank', 'Crypto · LBank') }, { id: 'forex', label: L('فارکس · OneRoyal', 'Forex · OneRoyal') }]} />
      </header>
      {local.market === 'crypto' ? <CryptoFunnel local={local} save={save} onSignIn={onSignIn} /> : <ForexFunnel onSignIn={onSignIn} />}
    </div>
  );
}

// ── Crypto (TradeYar) ────────────────────────────────────────────────────────────────────────────
function CryptoFunnel({ local, save, onSignIn }: { local: VipLocal; save(n: Partial<VipLocal>): void; onSignIn(): void }) {
  const L = useL();
  const t = useT();
  const open = usePage((s) => s.open);
  const signed = useSession((s) => Boolean(s.sessions.tradeyar));
  const anySigned = useSession((s) => Boolean(s.sessions.tradeyar || s.sessions.coineprofx));
  const rules = useLoad(cryptoRules, []);
  const vip = useVip((s) => s.crypto);
  const setVip = useVip((s) => s.setCrypto);
  const [reg, setReg] = useState<Registration | null>(null);
  const [regError, setRegError] = useState<string | null>(null);
  const [editing, setEditing] = useState(false);
  const [view, setView] = useState<CryptoStep | null>(null);

  const load = useCallback(async () => {
    if (!signed) { setReg(null); return; }
    try {
      const [r, v] = await Promise.all([registration(), vipState().catch(() => null)]);
      setReg(r); setRegError(null);
      if (v) setVip(v);
    } catch (e) { setRegError(errorText(e, t)); }
  }, [signed, setVip, t]);
  useEffect(() => { void load(); }, [load]);

  const r = rules.data;
  const exchange = local.exchange || r?.defaultExchange || 'lbank';
  const ex = r?.exchanges.find((x) => x.id === exchange) ?? r?.exchanges[0] ?? null;
  const step = editing ? 'uid' : cryptoStep(signed, reg, local);
  const outcome = outcomeOf(reg, vip);
  const shown = view ?? step;

  // The verdict is re-read while there is something to wait for, as the user site's funnel does (8 s).
  useEffect(() => {
    if (step !== 'result' || outcome === 'vip' || outcome === 'error') return;
    const id = setInterval(() => void load(), 8000);
    return () => clearInterval(id);
  }, [step, outcome, load]);

  const order: CryptoStep[] = ['account', 'exchange', 'deposit', 'uid', 'result'];
  const labels: Record<CryptoStep, string> = {
    account: L('حساب پرو چارت', 'Pro Chart account'), exchange: L('حساب صرافی با لینک ما', 'Exchange account via our link'),
    deposit: L('شارژ حساب', 'Fund the account'), uid: L('ارسال UID', 'Send the UID'), result: L('بررسی و ورود', 'Check and join'),
  };
  const at = order.indexOf(step);
  const steps = order.map((id, i) => ({
    id, label: labels[id], state: (i < at ? 'done' : i === at ? 'now' : 'todo') as 'done' | 'now' | 'todo',
    onClick: i <= at && id !== 'account' && signed ? () => setView(id === step ? null : id) : undefined,
  }));

  return (
    <div className="vip-funnel">
      <aside className="vip-side">
        <StepRail steps={steps} label={L('مراحل عضویت', 'Steps')} />
        <State loading={rules.loading} error={rules.error} onRetry={rules.reload}>
          {r ? <RulesCard rules={r} /> : null}
        </State>
      </aside>
      <section className="vip-main card">
        {shown === 'account' ? (
          <div className="stack">
            <h3 className="fa">{L('۱. وارد حساب پرو چارت شوید', '1. Sign in to Pro Chart')}</h3>
            <p className="fa">{anySigned && !signed
              ? L('عضویت رمزارز روی حساب تریدیار ثبت می‌شود؛ یک بار با ایمیل و رمز وارد شوید تا حساب رمزارز هم وصل شود.', 'Crypto membership lives on the TradeYar account; sign in once with your e-mail so it connects.')
              : L('عضویت VIP روی همین حساب ثبت می‌شود و هشدارهای موجودی هم همین‌جا و در اعلان‌ها می‌آید.', 'Membership is kept on this account, and balance warnings arrive here and in your notifications.')}</p>
            <div className="row"><button className="btn btn-primary sm" onClick={onSignIn}><Icon name="ph-sign-in" size="sm" /><span className="fa">{L('ورود یا ثبت‌نام', 'Sign in or register')}</span></button></div>
          </div>
        ) : null}
        {shown === 'exchange' && r ? (
          <div className="stack">
            <h3 className="fa">{L('۲. حساب صرافی را با لینک ما بسازید', '2. Open the exchange account through our link')}</h3>
            <div className="row vip-exchanges" role="radiogroup" aria-label={L('صرافی', 'Exchange')}>
              {r.exchanges.map((x) => (
                <button key={x.id} role="radio" aria-checked={x.id === exchange} className={`vip-exchange${x.id === exchange ? ' on' : ''}`} onClick={() => save({ exchange: x.id })}>
                  <b>{x.label}</b>
                  <span className="muted fa">{x.id === r.defaultExchange ? L('پیشنهاد ما', 'Recommended') : L('گزینه‌ی دوم', 'Alternative')}{x.copyTrade ? ` · ${L('کپی‌ترید', 'copy trading')}` : ''}</span>
                </button>
              ))}
            </div>
            {r.noticeFa ? <p className="fa vip-note">{r.noticeFa}</p> : null}
            {r.texts.have_account ? <details className="vip-details"><summary className="fa">{L('از قبل در این صرافی حساب دارم', 'I already have an account there')}</summary><BotText html={r.texts.have_account} /></details> : null}
            {ex ? (
              <div className="row">
                <PartnerLink href={ex.referralUrl} primary>{L(`ثبت‌نام در ${ex.label}`, `Sign up on ${ex.label}`)}</PartnerLink>
                <button className="btn btn-quiet sm" onClick={() => { save({ registered: true }); setView(null); }}><span className="fa">{L('حساب را ساختم', 'Done, account opened')}</span></button>
              </div>
            ) : null}
            {r.texts.fresh_signup ? <BotText html={r.texts.fresh_signup} className="muted" /> : null}
          </div>
        ) : null}
        {shown === 'deposit' && r ? (
          <div className="stack">
            <h3 className="fa">{L('۳. حساب را شارژ کنید', '3. Fund the account')}</h3>
            <p className="fa lead">{L('حداقل', 'At least')} <b className="num">{usdt(r.minDeposit)} USDT</b> {L('در حساب صرافی خودتان؛ پول دست خودتان می‌ماند و هر وقت بخواهید برداشت می‌کنید.', 'in your own exchange account; the money stays yours to withdraw at any time.')}</p>
            {r.texts.stay_rule ? <BotText html={r.texts.stay_rule} /> : null}
            <div className="row">
              {ex ? <PartnerLink href={ex.referralUrl}>{L(`ورود به ${ex.label}`, `Open ${ex.label}`)}</PartnerLink> : null}
              <button className="btn btn-primary sm" onClick={() => { save({ deposited: true }); setView(null); }}><span className="fa">{L('شارژ کردم', 'Done, funded')}</span></button>
            </div>
          </div>
        ) : null}
        {shown === 'uid' && r ? <UidStep rules={r} exchange={exchange} onExchange={(id) => save({ exchange: id })} onSent={(x) => { setReg(x); setEditing(false); setView(null); void load(); }} /> : null}
        {shown === 'result' ? (
          <Result reg={reg} rules={r} error={regError} onResubmit={() => { setEditing(true); setView(null); }} onStatus={() => open('vip-status')} onReload={load} />
        ) : null}
      </section>
    </div>
  );
}

function RulesCard({ rules: r }: { rules: CryptoRules }) {
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const n = (v: number | null) => (v == null ? '—' : lang === 'fa' ? faNum(v) : String(v));
  return (
    <div className="card vip-rules">
      <h3 className="fa">{L('شرایط کانال VIP', 'VIP channel rules')}</h3>
      <dl className="vip-kv">
        <dt className="fa">{L('ورود', 'Entry')}</dt><dd><span className="num">≥ {usdt(r.minDeposit)} USDT</span></dd>
        {r.warnBelow != null ? <><dt className="fa">{L('هشدار', 'Warning')}</dt><dd><span className="num">&lt; {usdt(r.warnBelow)} USDT</span></dd></> : null}
        {r.criticalBelow != null ? <><dt className="fa">{L('اخطار نهایی', 'Final warning')}</dt><dd><span className="num">&lt; {usdt(r.criticalBelow)} USDT</span></dd></> : null}
        {r.graceHours != null ? <><dt className="fa">{L('مهلت پیش از حذف', 'Grace before removal')}</dt><dd className="fa">{n(r.graceHours)} {L('ساعت', 'h')}</dd></> : null}
        {r.rejoinAt != null ? <><dt className="fa">{L('بازگشت خودکار', 'Automatic return')}</dt><dd><span className="num">≥ {usdt(r.rejoinAt)} USDT</span></dd></> : null}
        <dt className="fa">{L('هزینه', 'Price')}</dt><dd className="fa up">{L('رایگان', 'Free')}</dd>
      </dl>
      {r.texts.deal ? <BotText html={r.texts.deal} className="muted" /> : null}
    </div>
  );
}

function UidStep({ rules: r, exchange, onExchange, onSent }: { rules: CryptoRules; exchange: string; onExchange(id: string): void; onSent(reg: Registration): void }) {
  const L = useL();
  const t = useT();
  const [uid, setUid] = useState('');
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const ex = r.exchanges.find((x) => x.id === exchange);
  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const clean = cleanUid(uid, exchange, r);
    if (clean.error) {
      setMsg(clean.error === 'phone' ? L('این شبیه شماره‌ی موبایل است، نه UID صرافی.', 'That looks like a phone number, not an exchange UID.')
        : clean.error === 'email' ? L('این شبیه ایمیل است، نه UID صرافی.', 'That looks like an e-mail, not an exchange UID.')
        : clean.error === 'empty' ? L('UID را وارد کنید.', 'Enter the UID.')
        : L(`UID در ${ex?.label ?? exchange} این شکل را ندارد؛ نمونه: ${ex?.uidExample ?? ''}`, `A ${ex?.label ?? exchange} UID does not look like that; e.g. ${ex?.uidExample ?? ''}`));
      return;
    }
    setBusy(true); setMsg(null);
    try { onSent(await submitUid(exchange, clean.uid)); }
    catch (err) { setMsg(errorText(err, t)); }
    finally { setBusy(false); }
  };
  return (
    <form className="stack form vip-form" onSubmit={(e) => void submit(e)}>
      <h3 className="fa">{L('۴. UID حساب صرافی را بفرستید', '4. Send your exchange UID')}</h3>
      <Field label={L('صرافی', 'Exchange')}>
        <select value={exchange} onChange={(e) => onExchange(e.target.value)}>{r.exchanges.map((x) => <option key={x.id} value={x.id}>{x.label}</option>)}</select>
      </Field>
      <Field label="UID" hint={ex?.uidExample ? L(`نمونه: ${ex.uidExample}`, `e.g. ${ex.uidExample}`) : undefined}>
        <input className="num" dir="ltr" autoComplete="off" spellCheck={false} value={uid} onChange={(e) => setUid(e.target.value)} placeholder={ex?.uidExample ?? ''} />
      </Field>
      {msg ? <p className="down fa" role="alert">{msg}</p> : null}
      <button className="btn btn-primary" disabled={busy}>{busy ? <span className="fa">{L('در حال بررسی…', 'Checking…')}</span> : <span className="fa">{L('ارسال و بررسی', 'Send and check')}</span>}</button>
      <details className="vip-details"><summary className="fa">{L('UID را کجا پیدا کنم؟', 'Where is my UID?')}</summary><BotText html={(exchange === 'ourbit' ? r.texts.ourbit_register : r.texts.lbank_register) ?? ''} /></details>
    </form>
  );
}

function Result({ reg, rules: r, error, onResubmit, onStatus, onReload }: { reg: Registration | null; rules: CryptoRules | null; error: string | null; onResubmit(): void; onStatus(): void; onReload(): Promise<void> }) {
  const L = useL();
  const t = useT();
  const vip = useVip((s) => s.crypto);
  const setVip = useVip((s) => s.setCrypto);
  const [busy, setBusy] = useState(false);
  const [note, setNote] = useState<string | null>(null);
  const [invite, setInvite] = useState<string | null>(null);
  const outcome = outcomeOf(reg, vip);
  const ex = useMemo(() => r?.exchanges.find((x) => x.id === (vip?.exchange ?? reg?.exchange ?? r.defaultExchange)) ?? r?.exchanges[0] ?? null, [r, vip, reg]);
  const recheck = async () => {
    setBusy(true); setNote(null);
    try { const v = await vipRecheck(); setVip(v); setNote(v.recheck?.messageFa ?? v.messageFa); await onReload(); }
    catch (e) { setNote(errorText(e, t)); } finally { setBusy(false); }
  };
  const getInvite = async () => {
    setBusy(true); setNote(null);
    try { const x = await vipInvite(); setInvite(x.url); }
    catch (e) { setNote(errorText(e, t)); } finally { setBusy(false); }
  };
  if (error) return <div className="empty fa">{error}</div>;
  const message = (outcome === 'deposit' || outcome === 'not_indexed') && vip?.messageFa ? vip.messageFa : reg?.messageFa || r?.registrationStatusFa[reg?.status ?? ''] || '';
  return (
    <div className={`stack vip-result ${outcome}`} data-outcome={outcome}>
      {outcome === 'vip' ? (
        <>
          <div className="vip-result-head ok"><Icon name="ph-seal-check" size="md" /><h3 className="fa">{L('دسترسی VIP شما فعال است', 'Your VIP access is on')}</h3></div>
          <p className="fa">{vip?.messageFa}</p>
          <div className="row">
            {vip?.channel.configured ? <button className="btn btn-primary sm" disabled={busy} onClick={() => void getInvite()}><Icon name="ph-telegram-logo" size="sm" /><span className="fa">{L('ورود به کانال VIP تلگرام', 'Join the VIP Telegram channel')}</span></button> : null}
            <button className="btn btn-quiet sm" onClick={onStatus}><span className="fa">{L('وضعیت و شرایط ماندن', 'Status and rules')}</span></button>
          </div>
          {invite ? <p className="muted fa vip-invite">{L('لینک یک‌بارمصرف شما آماده است:', 'Your single-use link is ready:')} <a className="link num" href={invite} target="_blank" rel="noopener noreferrer">{invite}</a></p> : null}
          {vip?.ruleFa ? <p className="fa muted">{vip.ruleFa}</p> : null}
        </>
      ) : outcome === 'deposit' ? (
        <>
          <div className="vip-result-head warn"><Icon name="ph-wallet" size="md" /><h3 className="fa">{L('حساب تأیید شد؛ موجودی به حد لازم نرسیده', 'Verified; the balance is not there yet')}</h3></div>
          <p className="fa">{message}</p>
          {vip?.balance != null ? <p className="fa">{L('موجودی فعلی', 'Current balance')} <b className="num">{usdt(vip.balance)} USDT</b> · {L('حداقل', 'minimum')} <b className="num">{usdt(vip.thresholds.minDeposit ?? r?.minDeposit ?? null)} USDT</b></p> : null}
          {vip?.pendingUntil ? <p className="muted fa">{L('بررسی خودکار تا', 'Automatic checks for another')} <Countdown at={vip.pendingUntil} /> {L('دیگر ادامه دارد.', '')}</p> : null}
          <div className="row">
            <button className="btn btn-primary sm" disabled={busy} onClick={() => void recheck()}><Icon name="ph-arrows-clockwise" size="sm" /><span className="fa">{L('واریز کردم — همین الان بررسی کن', 'I deposited — check now')}</span></button>
            {ex ? <PartnerLink href={ex.referralUrl}>{L(`ورود به ${ex.label}`, `Open ${ex.label}`)}</PartnerLink> : null}
          </div>
        </>
      ) : outcome === 'not_indexed' ? (
        <>
          <div className="vip-result-head info"><Icon name="ph-hourglass" size="md" /><h3 className="fa">{L('هنوز زیرمجموعه‌ی ما شناسایی نشده‌اید', 'Not recognised as our referral yet')}</h3></div>
          <p className="fa">{message}</p>
          {r?.exchangeLagFa ? <p className="muted fa">{L(`ثبت ارتباط زیرمجموعه از سمت صرافی ${r.exchangeLagFa} طول می‌کشد؛ ما خودکار بررسی می‌کنیم.`, 'The exchange takes a while to register the referral; we keep checking automatically.')}</p> : null}
          <div className="row">
            <button className="btn btn-quiet sm" disabled={busy} onClick={() => void recheck()}><Icon name="ph-arrows-clockwise" size="sm" /><span className="fa">{L('همین الان بررسی کن', 'Check now')}</span></button>
            <button className="btn btn-quiet sm" onClick={onResubmit}><span className="fa">{L('UID دیگری بفرستم', 'Send another UID')}</span></button>
            {ex ? <PartnerLink href={ex.referralUrl}>{L('ساخت حساب با لینک ما', 'Open an account via our link')}</PartnerLink> : null}
          </div>
        </>
      ) : outcome === 'error' ? (
        <>
          <div className="vip-result-head crit"><Icon name="ph-warning" size="md" /><h3 className="fa">{L('بررسی انجام نشد', 'The check did not run')}</h3></div>
          <p className="fa">{message}</p>
          <div className="row"><button className="btn btn-primary sm" onClick={onResubmit}><span className="fa">{L('ارسال دوباره', 'Send again')}</span></button></div>
        </>
      ) : (
        <>
          <div className="vip-result-head info"><Icon name="ph-hourglass" size="md" /><h3 className="fa">{outcome === 'approved' ? L('حساب تأیید شد؛ دسترسی VIP در حال صدور است', 'Approved; VIP access is being issued') : L('در حال بررسی', 'Checking')}</h3></div>
          <p className="fa">{message}</p>
          <p className="muted fa">{L('این صفحه هر چند ثانیه خودش به‌روز می‌شود.', 'This page refreshes itself every few seconds.')}</p>
        </>
      )}
      {reg?.uid ? <p className="muted"><span className="fa">{L('UID ثبت‌شده', 'UID on record')}</span> <span className="num">{reg.uid}</span>{reg.canResubmit && outcome !== 'vip' && outcome !== 'not_indexed' && outcome !== 'error' ? <> · <button className="link-btn" onClick={onResubmit}><span className="fa">{L('اصلاح UID', 'Change UID')}</span></button></> : null}</p> : null}
      {note ? <p className="fa vip-note" role="status">{note}</p> : null}
    </div>
  );
}

// ── Forex (CoinePro-FX, signals only) ────────────────────────────────────────────────────────────
function ForexFunnel({ onSignIn }: { onSignIn(): void }) {
  const L = useL();
  const t = useT();
  const lang = usePrefs((s) => s.lang);
  const open = usePage((s) => s.open);
  const signed = useSession((s) => Boolean(s.sessions.coineprofx));
  const rules = useLoad(forexRules, []);
  const state = useVip((s) => s.forex);
  const setState = useVip((s) => s.setForex);
  const refresh = useVip((s) => s.refresh);
  const [login, setLogin] = useState('');
  const [email, setEmail] = useState('');
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  useEffect(() => { if (signed) void refresh(); }, [signed, refresh]);
  const r = rules.data;
  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true); setMsg(null);
    try { setState(await forexClaim(login, email)); setLogin(''); }
    catch (err) { setMsg(errorText(err, t)); } finally { setBusy(false); }
  };
  const n = (v: number | null) => (v == null ? '—' : lang === 'fa' ? faNum(v) : String(v));
  return (
    <div className="vip-funnel">
      <aside className="vip-side">
        <State loading={rules.loading} error={rules.error} onRetry={rules.reload}>
          {r ? (
            <div className="card vip-rules">
              <h3 className="fa">{L('شرایط VIP فارکس', 'Forex VIP rules')}</h3>
              <dl className="vip-kv">
                <dt className="fa">{L('بروکر', 'Broker')}</dt><dd>{r.brokerName}</dd>
                <dt className="fa">{L('حداقل واریز', 'Minimum deposit')}</dt><dd><span className="num">{usdt(r.minDeposit)} {r.currency}</span></dd>
                <dt className="fa">{L('تأیید', 'Verification')}</dt><dd className="fa">{r.verification === 'manual' ? L('بررسی واریز توسط پشتیبانی', 'Deposit checked by support') : r.verification}</dd>
                <dt className="fa">{L('مدت', 'Term')}</dt><dd className="fa">{r.lifetime ? L('همیشگی', 'Lifetime') : '—'}</dd>
                <dt className="fa">{L('دسترسی', 'Access')}</dt><dd className="fa">{L('فقط سیگنال', 'Signals only')}</dd>
              </dl>
              <p className="muted fa">{L('اکسپرت و معامله‌ی خودکار فارکس فعال نیست؛ این عضویت سیگنال‌های فارکس و کانال VIP را باز می‌کند.', 'The forex expert and automated trading are off; this membership opens the forex signals and the VIP channel.')}</p>
              {r.trialHours ? <p className="muted fa">{L(`راه دیگر: ${n(r.trialHours)} ساعت آزمایشی یا اشتراک ماهانه در ربات.`, `Alternatively: a ${r.trialHours}-hour trial or a monthly plan in the bot.`)}</p> : null}
            </div>
          ) : null}
        </State>
      </aside>
      <section className="vip-main card">
        <State loading={rules.loading} error={rules.error}>
          {r ? (
            <div className="stack">
              <ol className="vip-steps">
                {r.steps.map((s, i) => <li key={i} className="fa"><span className="vip-steps-n num">{lang === 'fa' ? faNum(i + 1) : i + 1}</span>{s}</li>)}
              </ol>
              <div className="row">
                <PartnerLink href={r.referralUrl} primary>{L(`ثبت‌نام در ${r.brokerName}`, `Sign up on ${r.brokerName}`)}</PartnerLink>
                {r.supportUrl ? <a className="btn btn-quiet sm" href={r.supportUrl} target="_blank" rel="noopener noreferrer"><Icon name="ph-telegram-logo" size="sm" /><span className="fa">{L('پشتیبانی', 'Support')}</span></a> : null}
              </div>
              <details className="vip-details"><summary className="fa">{L('راهنمای ثبت‌نام و احراز هویت در بروکر', 'Broker sign-up and verification guide')}</summary><BotText html={r.texts.broker_guide ?? ''} /></details>
              <details className="vip-details"><summary className="fa">{L('واریز و برداشت', 'Deposits and withdrawals')}</summary><BotText html={r.texts.broker_funding ?? ''} /></details>
              <hr className="vip-hr" />
              {!signed ? <ForexAccount onTradeYarSignIn={onSignIn} /> : state ? (
                <div className="stack" data-forex-status={state.status}>
                  <div className={`vip-result-head ${state.status === 'approved' ? 'ok' : state.status === 'rejected' ? 'crit' : 'info'}`}>
                    <Icon name={state.status === 'approved' ? 'ph-seal-check' : state.status === 'rejected' ? 'ph-x-circle' : 'ph-hourglass'} size="md" />
                    <h3 className="fa">{state.messageFa}</h3>
                  </div>
                  {state.claim ? <p className="muted fa">{L('حساب', 'Account')} <span className="num">{state.claim.accountLogin}</span> · <span className="num">{state.claim.email}</span>{state.claim.reason ? <> · {L('دلیل', 'Reason')}: {state.claim.reason}</> : null}</p> : null}
                  {state.status === 'approved' ? (
                    <div className="row">
                      <button className="btn btn-primary sm" onClick={() => open('signals')}><span className="fa">{L('سیگنال‌های فارکس', 'Forex signals')}</span></button>
                      {state.channelInvite ? <a className="btn btn-quiet sm" href={state.channelInvite} target="_blank" rel="noopener noreferrer"><Icon name="ph-telegram-logo" size="sm" /><span className="fa">{L('کانال VIP', 'VIP channel')}</span></a> : null}
                    </div>
                  ) : null}
                  {state.canSubmit ? (
                    <form className="form stack" onSubmit={(e) => void submit(e)}>
                      <h3 className="fa">{state.claim ? L('اصلاح یا ارسال دوباره', 'Edit or send again') : L(`شماره‌ی حساب ${r.brokerName} را بفرستید`, `Send your ${r.brokerName} account number`)}</h3>
                      <Field label={L('شماره‌ی حساب معاملاتی (لاگین)', 'Trading account number (login)')}><input className="num" dir="ltr" inputMode="numeric" value={login} onChange={(e) => setLogin(e.target.value)} required /></Field>
                      <Field label={L(`ایمیل ثبت‌نام در ${r.brokerName}`, `E-mail used on ${r.brokerName}`)}><input type="email" dir="ltr" value={email} onChange={(e) => setEmail(e.target.value)} required /></Field>
                      {msg ? <p className="down fa" role="alert">{msg}</p> : null}
                      <button className="btn btn-primary" disabled={busy}><span className="fa">{L('ارسال برای بررسی', 'Send for review')}</span></button>
                    </form>
                  ) : null}
                </div>
              ) : <div className="empty fa">{t('loading')}…</div>}
            </div>
          ) : null}
        </State>
      </section>
    </div>
  );
}
