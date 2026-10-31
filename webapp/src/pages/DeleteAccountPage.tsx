import { useState } from 'react';
import { useT } from '../state/prefs';
import { usePage } from '../state/page';
import { useSession } from '../data/session';
import { authed, signOut } from '../data/auth';
import { HttpError } from '../data/http';
import { ROOT } from '../data/member';
import { errorText, useL } from './kit';
import { PROFILE_KEY } from './profileStore';
import type { Backend } from '../data/symbols';

// Delete the account (OLD feature/account DeleteAccountScreen.kt): what is deleted, what is kept,
// what is not deleted, and what stays in this browser — then typing the word, the request on every
// platform the reader is signed in to, and the sign-out, which also clears the name, picture and
// line this browser kept. A route the server does not have hands over to the published page.
const SITE = 'https://pro-chart.com/legal';
const DELETION_URL = `${SITE}/delete-account/`;

export default function DeleteAccountPage() {
  const L = useL();
  const t = useT();
  const close = usePage((s) => s.close);
  const open = usePage((s) => s.open);
  const sessions = useSession((s) => s.sessions);
  const word = L('حذف', 'DELETE');
  const [typed, setTyped] = useState('');
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const [unsupported, setUnsupported] = useState(false);
  const [copied, setCopied] = useState(false);
  const go = async () => {
    setBusy(true); setMsg(null); setUnsupported(false);
    try {
      for (const b of ['tradeyar', 'coineprofx'] as Backend[]) if (sessions[b]) await authed(b, `${ROOT[b]}/account`, { method: 'DELETE' });
      try { localStorage.removeItem(PROFILE_KEY); } catch { /* blocked */ }
      signOut(); close();
    } catch (e) {
      if (e instanceof HttpError && (e.status === 404 || e.status === 405)) setUnsupported(true);
      // The server's own words where it gave them: an open position or an unsettled balance is the one case a reader can act on.
      else setMsg(e instanceof HttpError && e.readerMessage ? e.readerMessage : e instanceof HttpError && e.status >= 400 && e.status < 500 ? L('حساب حذف نشد. دوباره تلاش کنید یا با پشتیبانی تماس بگیرید.', 'The account was not deleted. Try again, or contact support.') : errorText(e, t));
    } finally { setBusy(false); }
  };
  const bullets = (title: string, lines: string[], note?: string, cls = '') => (
    <section className={`card delete-list ${cls}`}>
      <h3 className="fa">{title}</h3>
      <ul className="fa">{lines.map((x) => <li key={x}>{x}</li>)}</ul>
      {note ? <p className="muted small fa">{note}</p> : null}
    </section>
  );
  const copy = () => { void navigator.clipboard?.writeText(DELETION_URL).then(() => setCopied(true), () => setCopied(false)); };
  const outside = (
    <section className="card">
      <h3 className="fa">{L('حذف از بیرون اپ', 'Delete from outside the app')}</h3>
      <p className="fa">{L('صفحه‌ی حذف حساب، مراحل و مهلت‌ها را می‌گوید و برای استفاده از آن لازم نیست وارد حساب شوید.', 'The deletion page sets out the steps and the timescales, and does not require signing in.')}</p>
      <div className="row">
        <a className="btn btn-primary sm" href={DELETION_URL} target="_blank" rel="noopener noreferrer">{L('باز کردن صفحه‌ی حذف حساب', 'Open the deletion page')}</a>
        <button type="button" className="btn btn-quiet sm" onClick={copy}>{L('کپی نشانی صفحه', 'Copy the page link')}</button>
      </div>
      {copied ? <p className="muted small fa">{L('نشانی صفحه کپی شد. می‌توانی آن را در مرورگر رایانه یا هر دستگاه دیگری باز کنی.', 'The link is on your clipboard. Open it in a browser on a computer or any other device.')}</p> : null}
    </section>
  );
  return (
    <div className="page-inner narrow stack delete-account">
      <h2 className="fa">{L('حذف حساب کاربری', 'Delete account')}</h2>
      <p className="down fa">{L('این کار برگشت‌پذیر نیست. پس از تأیید، حساب و داده‌های آن حذف می‌شوند و از اپ خارج می‌شوید.', 'This cannot be undone. Your account and its data are deleted and you are signed out.')}</p>
      {bullets(L('چه چیزی حذف می‌شود', 'What is deleted'), [
        L('حساب، ایمیل، نام و اطلاعات احراز هویتی که خودتان وارد کرده‌اید', 'The account, your e-mail and name, and any identity details you entered'),
        L('حساب آکادمی و پیشرفت درس‌ها، اگر با همین ایمیل ساخته شده باشد', 'Your academy account and lesson progress, where it was created with the same e-mail'),
        L('شناسه‌ی کاربری صرافی (UID) و کلیدهای API صرافی', 'Your exchange UID and exchange API keys'),
        L('تنظیمات و توکن اعلان', 'Notification preferences and token'),
        L('تاریخچه‌ی سیگنال‌های ذخیره‌شده و هشدارهای قیمت', 'Saved signal history and price alerts'),
      ], undefined, 'removed')}
      {bullets(L('چه چیزی نگه داشته می‌شود', 'What is kept'), [
        L('سوابق معاملاتی، تا جایی که قانون یا قرارداد صرافی الزام می‌کند', 'Trading records, as far as law or the exchange contract requires'),
        L('لاگ امنیتی و ضدتقلب، حداکثر ۱۲ ماه', 'Security and anti-fraud logs, for at most 12 months'),
      ], L('هر دو بی‌نام نگه داشته می‌شوند — بدون ایمیل، نام یا UID — و دیگر به شما قابل انتساب نیستند.', 'Both are kept anonymised — no e-mail, name or UID — and are no longer attributable to you.'), 'kept')}
      {bullets(L('چه چیزی حذف نمی‌شود', 'What is not deleted'), [
        L('حساب شما نزد صرافی. آن حساب مال خودتان است؛ پرو چارت نه آن را ساخته و نه می‌تواند ببندد.', 'Your account at the exchange. That account is yours; Pro Chart neither created it nor can close it.'),
      ], undefined, 'not-removed')}
      {bullets(L('چه چیزی روی این مرورگر می‌ماند', 'What stays in this browser'), [
        L('دفترچه‌ی معاملات و معامله‌های آزمایشی شما', 'Your trading journal and your paper trades'),
        L('فهرست‌های دیده‌بان، چیدمان‌های چارت، قالب‌ها و ترسیم‌های روی چارت', 'Watchlists, chart layouts, templates and the drawings on your charts'),
        L('هشدارهای قیمتی که روی خودِ همین مرورگر اجرا می‌شوند', 'Price alerts that run in this browser itself'),
      ], L('این‌ها فقط روی همین مرورگر بوده‌اند، پس حذف حساب آن‌ها را برنمی‌دارد. نام، تصویر و توضیح شما با خروجی که بلافاصله بعد از حذف انجام می‌شود پاک می‌شود. برای پاک‌کردن بقیه، داده‌ی این سایت را از تنظیمات مرورگر پاک کنید.', 'None of this was ever off this browser, so deleting the account does not remove it. Your name, picture and description are cleared by the sign-out that follows deletion. To remove the rest, clear this site’s data in the browser’s settings.'), 'local')}
      <section className="card warn">
        <p className="fa">{L(`برای تأیید، واژه‌ی «${word}» را تایپ کنید.`, `Type “${word}” to confirm.`)}</p>
        <input value={typed} onChange={(e) => setTyped(e.target.value)} dir="auto" aria-label={word} disabled={busy} />
        <button className="btn sell" disabled={busy || typed.trim() !== word} onClick={() => void go()}>{L('حذف حساب کاربری', 'Delete account')}</button>
      </section>
      {unsupported ? <><p className="fa">{L('این سرویس هنوز حذف حساب از داخل اپ را ارائه نمی‌دهد. راه زیر همین امروز کار می‌کند.', 'This service does not offer in-app deletion yet. The route below works today.')}</p>{outside}</> : null}
      {msg ? <p className="down fa" role="alert">{msg}</p> : null}
      <p className="muted small fa">{L('شرح کامل نگهداری و حذف داده در سیاست حریم خصوصی، بند ۵ آمده است.', 'Retention and deletion are set out in full in the privacy policy, section 5.')}</p>
      <button type="button" className="btn btn-quiet sm" onClick={() => open('privacy')}>{L('سیاست حریم خصوصی', 'Privacy policy')}</button>
    </div>
  );
}
