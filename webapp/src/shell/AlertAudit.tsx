import { usePrefs } from '../state/prefs';
import { describeAlert, type PriceAlert } from '../state/alerts';
import { AUDIT_EVENTS, entryNote, historyOf, useAlertAudit, type AuditEntry } from '../state/alertAudit';
import { OUTCOMES, REASONS, deliveriesFor, useWebhooks } from '../state/webhooks';
import { digitsOf } from '../state/market';
import { formatPrice } from '../chart/format';
import { intervalLabel } from '../data/intervals';
import { clock, faNum } from '../i18n/time';
import { dateTime, useL } from '../pages/kit';
import { Icon } from '../ui/Icon';
import { DeliveryLine } from './WebhookSheet';
import './alerts2.css';

// What one alert actually did (the phone's AlertAuditSheet): a sentence saying what the sheet is
// for, the alert's webhook deliveries (a failure the loudest line), then its own timeline oldest
// first — a firing and its delivery read as a pair downwards — with the date in Persian and the
// clock, the price and the timeframe in Latin figures.

/** «۵ شهریور · 14:30 · قیمت 68,512.40 · H1»: the state the market was in, as recorded then. */
function factsLine(e: AuditEntry, fa: boolean): string {
  const day = fa ? new Intl.DateTimeFormat('fa-IR-u-ca-persian', { day: 'numeric', month: 'long' }).format(new Date(e.at)) : dateTime(e.at).slice(0, 10);
  const parts = [day, `⁦${clock(e.at)}⁩`];
  if (e.price != null && Number.isFinite(e.price)) parts.push(`${fa ? 'قیمت' : 'Price'} ⁦${formatPrice(e.price, digitsOf(e.symbol))}⁩`);
  if (e.timeframe) parts.push(`⁦${intervalLabel(e.timeframe)}⁩`);
  return parts.join(' · ');
}

export function AlertAuditDialog({ alert, onClose }: { alert: PriceAlert; onClose(): void }) {
  const L = useL();
  const fa = usePrefs((s) => s.lang) === 'fa';
  const all = useAlertAudit((s) => s.entries);
  const log = useWebhooks((s) => s.log);
  const entries = historyOf(all, alert.id);
  const deliveries = deliveriesFor(log, alert.id);
  const hookText = (h: NonNullable<AuditEntry['hook']>) => {
    const label = OUTCOMES[h.outcome][fa ? 'fa' : 'en'];
    const why = h.reason ? REASONS[h.reason][fa ? 'fa' : 'en'] : null;
    return [h.name, label, why && why !== label ? why : null].filter(Boolean).join(fa ? '، ' : ', ');
  };
  return (
    <div className="scrim" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div className="dialog settings audit-dlg" role="dialog" aria-modal="true" aria-label={L('کارنامه‌ی هشدار', 'What this alert did')} dir={fa ? 'rtl' : 'ltr'} onKeyDown={(e) => { e.stopPropagation(); if (e.key === 'Escape') onClose(); }}>
        <header className="dialog-head">
          <div>
            <h2 className="fa">{L('کارنامه‌ی هشدار', 'What this alert did')}</h2>
            <p className="muted small audit-sentence" dir="auto">{alert.name || describeAlert(alert, fa, (v) => formatPrice(v, digitsOf(alert.symbol)))}</p>
          </div>
          <button className="ib" onClick={onClose} aria-label={L('بستن', 'close')}><Icon name="ph-x" size="md" /></button>
        </header>
        <div className="settings-body audit-body">
          <p className="small fa audit-lead">{L('این سیاهه‌ی کاری است که این هشدار واقعاً انجام داده. اگر حرکتی را از دست داده‌اید، اینجا معلوم می‌شود اعلان رسیده یا نرسیده.', 'This is the record of what the alert actually did. If you missed a move, this is where you find out whether the notification arrived or not.')}</p>
          {deliveries.length ? (
            <section className="audit-hooks" data-deliveries>
              <div className="small fa muted">{L(`ارسال به وب‌هوک‌ها (${faNum(deliveries.length)} مورد)`, `Webhook deliveries (${deliveries.length})`)}</div>
              {deliveries.map((a, i) => <DeliveryLine key={`${a.at}-${i}`} a={a} fa={fa} showName />)}
            </section>
          ) : null}
          {!entries.length ? (
            <div className="audit-empty">
              <Icon name="ph-clock" size="md" />
              <b className="fa">{L('هنوز چیزی ثبت نشده', 'Nothing recorded yet')}</b>
              <span className="muted small fa">{L('از لحظه‌ای که هشدار ساخته شود، هر رویدادش اینجا نوشته می‌شود.', 'From the moment an alert is made, every event of its own is written here.')}</span>
            </div>
          ) : (
            <ol className="audit-timeline" data-timeline>
              {entries.map((e, i) => {
                const note = entryNote(e, fa, hookText);
                return (
                  <li key={`${i}-${e.at}-${e.event}`} className={`audit-row ev-${e.event}`} data-event={e.event}>
                    <span className="audit-rail"><i /></span>
                    <div className="audit-text">
                      <b className="fa">{AUDIT_EVENTS[e.event][fa ? 'fa' : 'en']}</b>
                      <span className="muted small">{factsLine(e, fa)}</span>
                      {note ? <span className="small audit-note fa" dir="auto">{note}</span> : null}
                    </div>
                  </li>
                );
              })}
            </ol>
          )}
        </div>
      </div>
    </div>
  );
}
