import { useEffect, useState } from 'react';
import { usePrefs } from '../state/prefs';
import { offerDue, offerSeen } from '../state/pulse';
import { SymbolLogo } from '../ui/SymbolLogo';
import { Icon } from '../ui/Icon';
import '../pages/scan.css';

// The offer to turn notifications on (OLD app NotificationOffer, 5.27.0): on opening, once a day,
// until they are on — and never again for a reader who says so. A sample of what arrives (a bitcoin
// move, as the system would show it), the four kinds, the limits, and one button. Offered only
// while the browser can still ask; never under the e2e suite unless a test opts in.
export function NotificationOffer() {
  const lang = usePrefs((s) => s.lang);
  const en = lang === 'en';
  const L = (fa: string, e: string) => (en ? e : fa);
  const [open, setOpen] = useState(false);
  useEffect(() => {
    // After the first paint has settled, so the offer is not the first thing that moves.
    const t = window.setTimeout(() => { if (offerDue()) { offerSeen(); setOpen(true); } }, 2500);
    return () => window.clearTimeout(t);
  }, []);
  if (!open) return null;
  const enable = () => { setOpen(false); void Notification.requestPermission().catch(() => undefined); };
  return (
    <div className="scrim" onClick={() => setOpen(false)}>
      <div className="dialog notify-offer" role="dialog" aria-label={L('اعلان‌ها', 'Notifications')} onClick={(e) => e.stopPropagation()}>
        <div className="dialog-head"><h2 className="fa">{L('از حرکت‌های مهم بازار جا نمانید', 'Keep up with the market’s big moves')}</h2><button className="lt" aria-label={L('بستن', 'Close')} onClick={() => setOpen(false)}><Icon name="ph-x" size="sm" /></button></div>
        <div className="offer-sample">
          <SymbolLogo symbol="BTCUSDT" size={36} />
          <div className="grow"><b className="fa">{L('بیت‌کوین +3.24% بالا رفت', 'Bitcoin up +3.24%')}</b><span className="muted small fa">{L('اکنون 64,210، در ۲ ساعت گذشته', 'Now 64,210, over the last 2 hours')}</span></div>
        </div>
        <ul>
          <li><Icon name="ph-trend-up" size="sm" /><span className="fa">{L('حرکت تند بیت‌کوین، طلا و جفت‌ارزهای اصلی', 'Sharp moves in bitcoin, gold and the major pairs')}</span></li>
          <li><Icon name="ph-star" size="sm" /><span className="fa">{L('نوسان غیرعادی بازارهایی که ستاره زده‌اید', 'Unusual moves in the markets you starred')}</span></li>
          <li><Icon name="ph-newspaper" size="sm" /><span className="fa">{L('خلاصه‌ی صبحگاهی بازار با پررشدترین‌های اسکرینر', 'A morning summary with the screener’s top risers')}</span></li>
          <li><Icon name="ph-calendar-blank" size="sm" /><span className="fa">{L('یادآوری پیش از اخبار اقتصادی پراهمیت', 'A reminder before high-importance economic releases')}</span></li>
        </ul>
        <p className="muted small fa">{L('حداکثر شش اعلان در روز، و از 23:00 تا 08:00 هیچ اعلانی نمی‌آید. هر نوع در تنظیمات کلید جدا دارد.', 'At most six a day, and nothing between 23:00 and 08:00. Each kind has its own switch in the settings.')}</p>
        <p className="muted small fa">{L('اعلان مرورگر فقط وقتی می‌رسد که یک زبانه‌ی پرو چارت باز باشد.', 'Browser notifications arrive only while a Pro Chart tab is open.')}</p>
        <button className="btn btn-primary" onClick={enable}><Icon name="ph-bell" size="sm" /><span className="fa">{L('فعال کردن اعلان‌ها', 'Turn on notifications')}</span></button>
        <div className="offer-actions">
          <button className="btn btn-quiet sm" onClick={() => setOpen(false)}><span className="fa">{L('بعداً', 'Later')}</span></button>
          <button className="btn btn-quiet sm" onClick={() => { offerSeen(true); setOpen(false); }}><span className="fa">{L('دیگر پرسیده نشود', 'Do not ask again')}</span></button>
        </div>
      </div>
    </div>
  );
}
