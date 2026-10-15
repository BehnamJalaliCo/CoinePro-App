import { useEffect, useState } from 'react';
import { usePrefs, useT } from '../state/prefs';
import { usePage } from '../state/page';
import { useSession } from '../data/session';
import { faAgo, faNum } from '../i18n/time';
import { Icon } from '../ui/Icon';
import { State, dateTime, errorText, useL, useLoad } from '../pages/kit';
import type { PageProps } from '../pages/registry';
import { cryptoRules, registration, vipInvite, vipRecheck, type VipEvent, type VipState } from './api';
import { severityOf, usdt, zoneOf } from './logic';
import { useVip } from './store';
import { BalanceGauge, Countdown, Pill } from './ui';
import { TelegramLink } from './TelegramLink';

// «وضعیت VIP»: where the reader stands under the rules TradeYar's monitor enforces — the balance
// against the warning and removal marks, the clock when one is running, the notices it sent (the
// same ones the bot DMs), what to do next, and the activation checklist. Forex membership beside it.
export default function VipStatusPage({ onSignIn }: PageProps) {
  const L = useL();
  const open = usePage((s) => s.open);
  const sessions = useSession((s) => s.sessions);
  const { crypto, forex, cryptoError, refresh, loadedAt } = useVip();
  useEffect(() => { void refresh(); }, [refresh]);
  return (
    <div className="page-inner vip-page">
      {sessions.tradeyar ? (
        crypto ? <CryptoStatus vip={crypto} /> : cryptoError ? <div className="empty fa">{L('وضعیت VIP خوانده نشد.', 'The VIP state could not be read.')} <button className="link" onClick={() => void refresh()}>{L('دوباره', 'Retry')}</button></div> : <div className="empty fa">{loadedAt ? '' : `${L('در حال بارگذاری', 'Loading')}…`}</div>
      ) : (
        <section className="card stack">
          <h3 className="fa">{L('عضویت رمزارز', 'Crypto membership')}</h3>
          <p className="fa">{L('وضعیت عضویت رمزارز روی حساب تریدیار است؛ با ایمیل و رمز وارد شوید.', 'Crypto membership lives on the TradeYar account; sign in with your e-mail.')}</p>
          <div className="row"><button className="btn btn-primary sm" onClick={onSignIn}><span className="fa">{L('ورود', 'Sign in')}</span></button></div>
        </section>
      )}
      <section className="card stack vip-forex-card">
        <div className="row-between"><h3 className="fa">{L('VIP فارکس · فقط سیگنال', 'Forex VIP · signals only')}</h3>{forex ? <Pill tone={forex.entitled ? 'ok' : forex.status === 'rejected' ? 'crit' : forex.status === 'submitted' ? 'info' : 'mute'}>{forex.entitled ? L('فعال', 'Active') : forex.status === 'submitted' ? L('در حال بررسی', 'Under review') : forex.status === 'rejected' ? L('تأیید نشد', 'Not approved') : L('عضو نیستید', 'Not a member')}</Pill> : null}</div>
        {forex ? (
          <>
            <p className="fa">{forex.messageFa}</p>
            {forex.vip ? <p className="muted fa">{forex.vip.lifetime ? L('اشتراک رایگان همیشگی', 'Free lifetime plan') : `${L('تا', 'Until')} ${dateTime(forex.vip.expiresAt)}`}</p> : null}
            <div className="row">
              {forex.entitled ? <button className="btn btn-quiet sm" onClick={() => open('signals')}><span className="fa">{L('سیگنال‌های فارکس', 'Forex signals')}</span></button> : <button className="btn btn-primary sm" onClick={() => open('vip-join')}><span className="fa">{L('عضویت رایگان فارکس', 'Join forex VIP free')}</span></button>}
              {forex.channelInvite ? <a className="btn btn-quiet sm" href={forex.channelInvite} target="_blank" rel="noopener noreferrer"><Icon name="ph-telegram-logo" size="sm" /><span className="fa">{L('کانال VIP', 'VIP channel')}</span></a> : null}
            </div>
            {!forex.telegramLinked ? <TelegramLink /> : null}
          </>
        ) : <p className="fa muted">{L('برای عضویت فارکس از صفحه‌ی عضویت، حساب کوین‌پرو اف‌ایکس را وصل کنید.', 'Connect a CoinePro-FX account from the membership page for forex.')} <button className="link" onClick={() => open('vip-join')}>{L('عضویت', 'Join')}</button></p>}
      </section>
    </div>
  );
}

function CryptoStatus({ vip }: { vip: VipState }) {
  const L = useL();
  const t = useT();
  const lang = usePrefs((s) => s.lang);
  const open = usePage((s) => s.open);
  const setVip = useVip((s) => s.setCrypto);
  const rules = useLoad(cryptoRules, []);
  const reg = useLoad(() => registration().catch(() => null), []);
  const [busy, setBusy] = useState(false);
  const [note, setNote] = useState<string | null>(null);
  const [invite, setInvite] = useState<string | null>(null);
  const sev = severityOf(vip);
  const th = vip.thresholds;
  const zone = zoneOf(vip.balance, th.warn, th.critical);
  const recheck = async () => {
    setBusy(true); setNote(null);
    try { const v = await vipRecheck(); setVip(v); setNote(v.recheck?.messageFa ?? v.messageFa); }
    catch (e) { setNote(errorText(e, t)); } finally { setBusy(false); }
  };
  const getInvite = async () => {
    setBusy(true); setNote(null);
    try { const x = await vipInvite(); setInvite(x.url); }
    catch (e) { setNote(errorText(e, t)); } finally { setBusy(false); }
  };
  const label: Record<string, [string, string, 'ok' | 'warn' | 'crit' | 'info' | 'mute']> = {
    active: ['فعال', 'Active', 'ok'], low: ['موجودی کم', 'Low balance', 'warn'], critical: ['اخطار نهایی', 'Final warning', 'crit'],
    removed: ['لغو شده', 'Removed', 'crit'], pending_deposit: ['در انتظار شارژ', 'Waiting for the deposit', 'info'],
    awaiting_referral: ['در انتظار تأیید صرافی', 'Waiting for the exchange', 'info'], expired: ['متوقف', 'Stopped', 'mute'], none: ['عضو نیستید', 'Not a member', 'mute'],
  };
  const [lf, le, tone] = label[vip.status] ?? label.none!;
  const n = (v: number | null) => (v == null ? '—' : lang === 'fa' ? faNum(v) : String(v));
  const todo = whatToDo(vip, L, n);
  const regStatus = reg.data?.status ?? null;
  const checklist: [string, boolean, string | null][] = [
    [L('حساب صرافی با لینک ما', 'Exchange account via our link'), Boolean(vip.uid) || (regStatus != null && regStatus !== 'awaiting_uid'), 'vip-join'],
    [L('UID تأییدشده (زیرمجموعه)', 'UID verified (referral)'), vip.status !== 'none' && vip.status !== 'awaiting_referral' && vip.status !== 'expired' || regStatus === 'approved' || regStatus === 'pending_deposit', 'vip-join'],
    [L(`شارژ حداقل ${usdt(th.minDeposit)} USDT`, `At least ${usdt(th.minDeposit)} USDT`), vip.entitled || regStatus === 'approved', 'vip-join'],
    [L('ورود به کانال VIP تلگرام', 'Joined the VIP Telegram channel'), vip.channel.telegramKnown && vip.entitled, null],
    [L('اعلان مرورگر برای هشدار موجودی', 'Browser notifications for balance warnings'), typeof Notification !== 'undefined' && Notification.permission === 'granted', 'notifications'],
    [L('کپی‌ترید خودکار (اختیاری)', 'Automatic copy trading (optional)'), false, 'copytrade'],
  ];
  return (
    <>
      <section className={`card vip-status sev-${sev}`} data-vip-status={vip.status}>
        <div className="row-between">
          <h3 className="fa">{L('VIP رمزارز · تریدیار', 'Crypto VIP · TradeYar')}</h3>
          <Pill tone={tone}>{L(lf, le)}</Pill>
        </div>
        <p className="fa lead">{vip.messageFa}</p>
        <BalanceGauge balance={vip.balance} warn={th.warn} critical={th.critical} entry={th.minDeposit} />
        <div className="vip-facts">
          {vip.exchange ? <span><span className="muted fa">{L('صرافی', 'Exchange')}</span> <b>{vip.exchange.toUpperCase()}</b></span> : null}
          {vip.uid ? <span><span className="muted">UID</span> <b className="num">{vip.uid}</b></span> : null}
          {vip.checkedAt ? <span><span className="muted fa">{L('آخرین بررسی', 'Last checked')}</span> <b className={lang === 'fa' ? 'fa' : 'num'}>{lang === 'fa' ? faAgo(vip.checkedAt) : dateTime(vip.checkedAt)}</b></span> : null}
          {vip.status === 'critical' && vip.removalDueAt ? <span className="down"><span className="fa">{L('زمان باقی‌مانده تا لغو', 'Time left before removal')}</span> <b><Countdown at={vip.removalDueAt} /></b></span> : null}
          {(vip.status === 'pending_deposit' || vip.status === 'awaiting_referral') && vip.pendingUntil ? <span><span className="muted fa">{L('بررسی خودکار تا', 'Automatic checks for')}</span> <b><Countdown at={vip.pendingUntil} /></b></span> : null}
        </div>
        {zone !== 'unknown' ? <p className="muted fa">{vip.ruleFa}</p> : null}
        <div className="row">
          {vip.entitled && vip.channel.configured ? <button className="btn btn-primary sm" disabled={busy} onClick={() => void getInvite()}><Icon name="ph-telegram-logo" size="sm" /><span className="fa">{L('لینک تازه‌ی کانال VIP', 'Fresh VIP channel link')}</span></button> : null}
          {vip.status !== 'none' && vip.status !== 'active' ? <button className="btn btn-quiet sm" disabled={busy} onClick={() => void recheck()}><Icon name="ph-arrows-clockwise" size="sm" /><span className="fa">{L('شارژ کردم — همین الان بررسی کن', 'I deposited — check now')}</span></button> : null}
          {vip.status === 'none' || vip.status === 'expired' ? <button className="btn btn-primary sm" onClick={() => open('vip-join')}><span className="fa">{L('عضویت رایگان', 'Join free')}</span></button> : null}
          {vip.supportUrl ? <a className="btn btn-quiet sm" href={vip.supportUrl} target="_blank" rel="noopener noreferrer"><span className="fa">{L('پشتیبانی', 'Support')}</span></a> : null}
        </div>
        {invite ? <p className="muted fa vip-invite">{L('لینک یک‌بارمصرف شما آماده است:', 'Your single-use link is ready:')} <a className="link num" href={invite} target="_blank" rel="noopener noreferrer">{invite}</a></p> : null}
        {note ? <p className="fa vip-note" role="status">{note}</p> : null}
      </section>

      <div className="vip-grid">
        <section className="card stack">
          <h3 className="fa">{L('چه باید بکنم؟', 'What to do')}</h3>
          <ul className="vip-todo">{todo.map((x, i) => <li key={i} className="fa">{x}</li>)}</ul>
          <State loading={rules.loading} error={null}>
            {rules.data?.balanceEverySeconds ? <p className="muted fa">{L(`موجودی هر ${n(Math.round(rules.data.balanceEverySeconds / 60))} دقیقه خوانده می‌شود؛ زیر حد بحرانی دو بار پشت‌سرهم تأیید می‌شود و بعد مهلت ${n(th.graceHours)} ساعته شروع می‌شود.`, `The balance is read every ${Math.round(rules.data.balanceEverySeconds / 60)} minutes; a critical reading is confirmed twice before the ${th.graceHours ?? '—'}-hour grace starts.`)}</p> : null}
          </State>
        </section>
        <section className="card stack">
          <h3 className="fa">{L('فهرست راه‌اندازی', 'Activation checklist')}</h3>
          <ol className="vip-check">
            {checklist.map(([text, done, page], i) => (
              <li key={i} className={done ? 'done' : ''}>
                <Icon name={done ? 'ph-check-circle' : 'ph-lock-key-open'} size="sm" />
                <span className="fa">{text}</span>
                {!done && page ? <button className="link-btn" onClick={() => open(page as 'vip-join')}><span className="fa">{L('انجام', 'Do it')}</span></button> : null}
              </li>
            ))}
          </ol>
        </section>
      </div>

      <section className="card stack">
        <h3 className="fa">{L('سابقه‌ی اعلان‌های VIP', 'VIP notices')}</h3>
        {vip.events.length ? (
          <ul className="vip-events">
            {vip.events.map((e) => <EventRow key={e.id} e={e} />)}
          </ul>
        ) : <div className="empty fa">{L('هنوز اعلانی نیامده است.', 'No notices yet.')}</div>}
      </section>
    </>
  );
}

function EventRow({ e }: { e: VipEvent }) {
  const lang = usePrefs((s) => s.lang);
  const icon = e.level === 'ok' ? 'ph-check-circle' : e.level === 'error' ? 'ph-warning' : e.level === 'warning' ? 'ph-warning' : 'ph-info';
  return (
    <li className={`vip-event ${e.level}`} data-event={e.event}>
      <Icon name={icon} size="sm" />
      <div className="vip-event-text">
        <b className="fa" dir="rtl">{e.title}</b>
        {e.body ? <span className="fa" dir="rtl">{e.body}</span> : null}
      </div>
      <span className="muted vip-event-time">{e.at ? (lang === 'fa' ? faAgo(e.at) : dateTime(e.at)) : ''}</span>
    </li>
  );
}

function whatToDo(vip: VipState, L: (fa: string, en: string) => string, n: (v: number | null) => string): string[] {
  const th = vip.thresholds;
  switch (vip.status) {
    case 'active': return [
      L(`موجودی را بالای ${usdt(th.warn)} USDT نگه دارید تا هشداری نگیرید.`, `Keep the balance above ${usdt(th.warn)} USDT to get no warnings.`),
      L('از کانال VIP و همه‌ی امکانات رایگان پرو چارت استفاده کنید.', 'Use the VIP channel and every free Pro Chart feature.'),
    ];
    case 'low': return [
      L(`موجودی را به بالای ${usdt(th.warn)} USDT برگردانید.`, `Bring the balance back above ${usdt(th.warn)} USDT.`),
      L(`اگر زیر ${usdt(th.critical)} USDT برود و ${n(th.graceHours)} ساعت بماند، دسترسی لغو می‌شود.`, `Below ${usdt(th.critical)} USDT for ${th.graceHours ?? '—'} hours, access is removed.`),
    ];
    case 'critical': return [
      L(`پیش از پایان مهلت، موجودی را به بالای ${usdt(th.critical)} USDT برسانید.`, `Before the clock runs out, bring the balance above ${usdt(th.critical)} USDT.`),
      L('بعد از واریز، «همین الان بررسی کن» را بزنید.', 'After depositing, press «check now».'),
    ];
    case 'removed': return [
      L(`حساب صرافی را تا ${usdt(th.minDeposit)} USDT شارژ کنید؛ دسترسی خودکار برمی‌گردد.`, `Fund the exchange account to ${usdt(th.minDeposit)} USDT; access returns automatically.`),
      L('تا آن زمان امکانات ویژه برای این حساب بسته است.', 'Until then premium features are closed for this account.'),
    ];
    case 'pending_deposit': return [
      L(`حساب را تا ${usdt(th.minDeposit)} USDT شارژ کنید؛ موجودی هر دقیقه خودکار بررسی می‌شود.`, `Fund the account to ${usdt(th.minDeposit)} USDT; it is checked every minute.`),
    ];
    case 'awaiting_referral': return [
      L('منتظر بمانید؛ صرافی ارتباط زیرمجموعه را ثبت می‌کند و ما خودکار بررسی می‌کنیم.', 'Wait; the exchange registers the referral and we check automatically.'),
      L('اگر حساب را بدون لینک ما ساخته‌اید، با لینک ما حساب تازه بسازید.', 'If the account was not opened through our link, open a new one through it.'),
    ];
    default: return [L('با لینک ما در صرافی ثبت‌نام کنید، حساب را شارژ کنید و UID را بفرستید.', 'Open an exchange account through our link, fund it and send the UID.')];
  }
}
