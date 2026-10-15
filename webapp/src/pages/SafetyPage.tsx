import { usePage } from '../state/page';
import { pub } from '../data/member';
import { Markdown } from './Markdown';
import { State, useL, useLoad } from './kit';

// Safety and version (the app's LaunchReadinessScreen): how the product behaves where it matters,
// the risk, privacy on the web, support, and the latest release of the Android app.
interface Latest { version_code?: number; version_name?: string; url?: string; notes_fa?: string; notes_en?: string; mandatory?: boolean }
const SUPPORT = 'https://t.me/ProChart_Sup';

export default function SafetyPage() {
  const L = useL();
  const open = usePage((s) => s.open);
  const latest = useLoad(() => pub<Latest>('/api/app/latest'), []);
  const card = (title: string, body: string, warn = false) => (
    <section className={`card${warn ? ' warn' : ''}`}><h3 className="fa">{title}</h3><p className="fa">{body}</p></section>
  );
  const notes = latest.data ? L(latest.data.notes_fa ?? '', latest.data.notes_en ?? latest.data.notes_fa ?? '') : '';
  return (
    <div className="page-inner narrow safety">
      <p className="muted fa">{L('پیش از استفاده از قابلیت‌های پرریسک، ببینید پرو چارت با سیگنال، دسترسی‌ها، کارگزار و صرافی‌های بیرونی و پشتیبانی چطور رفتار می‌کند.', 'Before using anything with consequences, see how Pro Chart handles signals, permissions, outside brokers and exchanges, and support.')}</p>
      <div className="stack">
        {card(L('پرو چارت چطور کار می‌کند', 'How Pro Chart works'), L('سیگنال ← تحلیل ← ورود / حد ضرر / اهداف ← اجرا ← پایش ← بستن یا خوردن هدف ← نتیجه. اجرا همیشه مسیر تأیید صریح دارد و پرو چارت هرگز نتیجه‌ی کارگزار یا صرافی را از خودش نمی‌سازد.', 'Signal → analysis → entry / stop / targets → execute → monitor → close or target hit → result. Execution always requires an explicit confirmation, and Pro Chart never invents a broker or exchange outcome.'))}
        {card(L('ریسک معامله و هوش مصنوعی', 'Trading and AI risk'), L('معامله و سرمایه‌گذاری ریسک زیان دارد. سیگنال، تحلیل و خروجی هوش مصنوعی تضمین نتیجه نیستند. اجرا به کارگزار یا صرافی، دسترسی‌های حساب، شرایط بازار و تأیید سرور بستگی دارد. نتایج گذشته یا نمایش‌داده‌شده تضمینی برای آینده نیست. هر سفارش را پیش از تأیید بازبینی کنید.', 'Trading and investment carry the risk of loss. Signals, analysis and AI output are not guaranteed outcomes. Execution depends on the broker or exchange, account permissions, market conditions and server confirmation. Past or displayed results do not guarantee future performance. Review every order before confirming it.'), true)}
        {card(L('وضعیت واقعی کارگزار و صرافی', 'Broker and exchange status'), L('اتصالی که ذخیره شده با اتصال زنده‌ی تأییدشده یکی نیست. پرو چارت کارهایی را که به کارگزار یا صرافی وابسته‌اند فقط وقتی فعال می‌کند که سرور اتصال را تأیید کرده باشد؛ اتصال ناموجود، قدیمی یا ناموفق همیشه دیده می‌شود.', 'A saved connection is not the same as a live, verified one. Pro Chart enables actions that depend on a broker or exchange only once the server has confirmed the connection; a missing, stale or failed connection always stays visible.'), true)}
        {card(L('حریم خصوصی', 'Privacy'), L('این نسخه‌ی وب هیچ ابزار تحلیلی، تبلیغاتی یا ردیابی ندارد. نشست ورود شما در کوکی امن رله‌ی pro-chart.com نگه داشته می‌شود و صفحه فقط یک شناسه‌ی کوتاه از آن می‌بیند؛ خروج از حساب آن را فراموش می‌کند. دیده‌بان، ترسیم‌ها، ژورنال و معامله‌ی آزمایشی فقط در همین مرورگر می‌مانند. تصویر چارت فقط وقتی خودتان بفرستید فرستاده می‌شود.', 'This web app ships no analytics, advertising or tracking tool. Your sign-in lives in a secure cookie of the pro-chart.com relay and the page only sees a short handle to it; signing out forgets it. Your watchlist, drawings, journal and paper account stay in this browser. A chart image is sent only when you send one.'))}
        <section className="card">
          <h3 className="fa">{L('پشتیبانی و بازخورد', 'Support and feedback')}</h3>
          <p className="fa">{L('برای پرسش، در تلگرام با پشتیبانی گفت‌وگو کنید و نظرها، پیشنهادها و انتقادهایتان را برای ما بفرستید.', 'To ask something, open the support chat on Telegram and send us your thoughts, suggestions and complaints.')}</p>
          <div className="row">
            <a className="btn btn-primary sm" href={SUPPORT} target="_blank" rel="noreferrer"><span className="fa">{L('گفت‌وگو با پشتیبانی در تلگرام', 'Open support on Telegram')}</span></a>
            <button className="btn btn-quiet sm" onClick={() => open('terms')}><span className="fa">{L('قوانین استفاده', 'Terms of use')}</span></button>
            <button className="btn btn-quiet sm" onClick={() => open('privacy')}><span className="fa">{L('حریم خصوصی', 'Privacy')}</span></button>
          </div>
        </section>
        <section className="card">
          <h3 className="fa">{L('نسخه', 'Version')}</h3>
          <dl className="kv"><dt className="fa">{L('نسخه‌ی وب', 'Web app')}</dt><dd className="num">{__APP_VERSION__}</dd></dl>
          <State loading={latest.loading} error={latest.error} onRetry={latest.reload}>
            {latest.data?.version_name ? (
              <>
                <dl className="kv"><dt className="fa">{L('آخرین نسخه‌ی اندروید', 'Latest Android release')}</dt><dd className="num">{latest.data.version_name}</dd></dl>
                {notes ? <Markdown source={notes} /> : null}
                {latest.data.url ? <a className="btn btn-quiet sm" href={latest.data.url} target="_blank" rel="noreferrer"><span className="fa">{L('دریافت اپ اندروید', 'Get the Android app')}</span></a> : null}
              </>
            ) : null}
          </State>
        </section>
        <p className="muted small fa">{L('اتصال محیط عملیاتی، تأیید کارگزار و صرافی و آمادگی اجرای زنده جداگانه بررسی می‌شوند و این صفحه آن‌ها را تضمین نمی‌کند.', 'Live connectivity, trusted brokers and exchanges, and readiness for real execution are checked separately; this page does not promise them.')}</p>
      </div>
    </div>
  );
}
