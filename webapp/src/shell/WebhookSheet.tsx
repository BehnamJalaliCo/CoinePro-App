import { useState } from 'react';
import { usePrefs } from '../state/prefs';
import {
  REFUSALS, attemptSummary, delivered, draftOf, draftValid, failed, newDraft, useWebhooks, validateUrl,
  type WebhookAttempt, type WebhookDraft,
} from '../state/webhooks';
import { Icon } from '../ui/Icon';
import { faNum } from '../i18n/time';
import { dateTime, useL } from '../pages/kit';
import './alerts2.css';

// Where an alert is posted when it fires, and whether it arrived (the phone's WebhookSheet): the
// reader's webhooks, each switchable and openable; one way to make another; the editor in the
// order somebody fills it in (name, address, signing key, on, test, save); and the delivery log.
// The address is refused under the field while it is typed. The key field is a password field,
// opens empty on an existing webhook (empty and untouched keeps the stored key), and nothing on
// this screen ever shows a stored key.
export function WebhookSheet({ onClose }: { onClose(): void }) {
  const L = useL();
  const fa = usePrefs((s) => s.lang) === 'fa';
  const [draft, setDraft] = useState<WebhookDraft | null>(null);
  return (
    <div className="scrim" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div className="dialog settings webhook-dlg" role="dialog" aria-modal="true" aria-label={L('وب‌هوک', 'Webhooks')} dir={fa ? 'rtl' : 'ltr'}
        onKeyDown={(e) => { e.stopPropagation(); if (e.key === 'Escape') { if (draft) setDraft(null); else onClose(); } }}>
        <header className="dialog-head">
          <div>
            <h2 className="fa">{L('وب‌هوک', 'Webhooks')}</h2>
            <p className="muted small fa wh-sub">{L('هر هشداری که اجرا شود، به این نشانی‌ها هم فرستاده می‌شود.', 'Every alert that fires is also posted to these addresses.')}</p>
          </div>
          <button className="ib" onClick={onClose} aria-label={L('بستن', 'close')}><Icon name="ph-x" size="md" /></button>
        </header>
        <div className="settings-body wh-body">
          {draft ? <WebhookEditor draft={draft} setDraft={setDraft} /> : <WebhookList onOpen={setDraft} />}
        </div>
      </div>
    </div>
  );
}

function WebhookList({ onOpen }: { onOpen(d: WebhookDraft): void }) {
  const L = useL();
  const fa = usePrefs((s) => s.lang) === 'fa';
  const { targets, log, setEnabled, remove, clearLog } = useWebhooks();
  return (
    <>
      {!targets.length ? <div className="empty fa">{L('هنوز وب‌هوکی نساخته‌اید', 'You have not made a webhook yet')}</div> : null}
      {targets.map((t) => (
        <div key={t.id} className={`wh-row${t.enabled ? '' : ' off'}`} data-webhook={t.name}>
          <button className="wh-main" onClick={() => onOpen(draftOf(t))}>
            <b dir="auto">{t.name}</b>
            <span className="wh-url num" dir="ltr">{t.url}</span>
          </button>
          <button className="chip sm" data-act="toggle" onClick={() => setEnabled(t.id, !t.enabled)}><span className="fa">{t.enabled ? L('روشن', 'On') : L('خاموش', 'Off')}</span></button>
          <button className="chip sm danger" data-act="delete" onClick={() => { if (window.confirm(L('این وب‌هوک حذف شود؟', 'Delete this webhook?'))) remove(t.id); }}><span className="fa">{L('حذف', 'Delete')}</span></button>
        </div>
      ))}
      <button className="btn btn-primary wh-new" data-act="new" onClick={() => onOpen(newDraft())}><span className="fa">{L('وب‌هوک تازه', 'New webhook')}</span></button>
      {log.length ? (
        <section className="wh-log" data-log>
          <div className="wh-log-head">
            <span className="fa">{L(`سیاهه‌ی ارسال‌ها (${faNum(log.length)} مورد)`, `Delivery log (${log.length})`)}</span>
            <button className="link-btn fa" onClick={() => { if (window.confirm(L('سیاهه‌ی ارسال‌ها پاک شود؟', 'Clear the delivery log?'))) clearLog(); }}>{L('پاک کردن', 'Clear')}</button>
          </div>
          {log.slice(0, 100).map((a, i) => <DeliveryLine key={`${a.at}-${i}`} a={a} fa={fa} showName />)}
        </section>
      ) : null}
    </>
  );
}

/** One delivery: outcome first, then the status and the latency in Latin figures; never the body or the key. */
export function DeliveryLine({ a, fa, showName = false }: { a: WebhookAttempt; fa: boolean; showName?: boolean }) {
  return (
    <div className={`wh-line${failed(a) ? ' bad' : delivered(a) ? ' ok' : ' unknown'}`} data-outcome={a.outcome}>
      {showName ? <span className="wh-name" dir="auto">{a.targetName}</span> : null}
      <span className="wh-sum">{attemptSummary(a, fa)}</span>
      <span className="muted num wh-at">{dateTime(a.at).slice(5)}</span>
    </div>
  );
}

function WebhookEditor({ draft, setDraft }: { draft: WebhookDraft; setDraft(d: WebhookDraft | null): void }) {
  const L = useL();
  const fa = usePrefs((s) => s.lang) === 'fa';
  const { save, test } = useWebhooks();
  const [result, setResult] = useState<WebhookAttempt | null>(null);
  const [testing, setTesting] = useState(false);
  // Any change to the form makes an earlier test answer about something else.
  const edit = (patch: Partial<WebhookDraft>) => { setDraft({ ...draft, ...patch }); setResult(null); };
  // Shown only once something is typed: a form that opens complaining reads as broken.
  const refusal = draft.url ? validateUrl(draft.url) : null;
  const valid = draftValid(draft);
  return (
    <div className="wh-editor" data-editor>
      <label className="al-col">
        <span className="fa">{L('نام', 'Name')}</span>
        <input value={draft.name} onChange={(e) => edit({ name: e.target.value })} dir="auto" data-field="name" autoFocus />
        <small className="muted fa">{L('نامی که در سیاهه‌ی ارسال‌ها می‌بینید؛ مثلاً ربات تلگرام.', 'The name you will see in the delivery log — «Telegram bot», say.')}</small>
      </label>
      <label className="al-col">
        <span className="fa">{L('نشانی', 'Address')}</span>
        <input value={draft.url} onChange={(e) => edit({ url: e.target.value })} dir="ltr" inputMode="url" className={`num${refusal ? ' invalid' : ''}`} aria-invalid={Boolean(refusal)} data-field="url" placeholder="https://" />
        <small className={`fa ${refusal ? 'down' : 'muted'}`} data-url-hint>{refusal ? REFUSALS[refusal][fa ? 'fa' : 'en'] : L('فقط https، روی نام دامنه، با پورت ۸۰ یا ۴۴۳.', 'HTTPS only, on a domain name, port 80 or 443.')}</small>
      </label>
      <label className="al-col">
        <span className="fa">{L('کلید امضا', 'Signing key')}</span>
        <input type="password" value={draft.secret} onChange={(e) => edit({ secret: e.target.value, secretTouched: true })} dir="ltr" autoComplete="new-password" spellCheck={false} data-field="secret" />
        <small className="muted fa">{draft.editingId ? L('اگر خالی بماند، کلید قبلی دست‌نخورده می‌ماند. کلید ذخیره‌شده هیچ‌جا نشان داده نمی‌شود.', 'Left empty, the stored key is kept. A stored key is never displayed.') : L('اختیاری. با آن، بدنه‌ی درخواست امضا می‌شود تا گیرنده مطمئن شود از این برنامه آمده است.', 'Optional. The request body is signed with it so the receiver can prove the call came from this app.')}</small>
        <small className="muted fa">{L('کلید فقط در همین مرورگر نگه داشته می‌شود و خودش هیچ‌جا فرستاده نمی‌شود؛ فقط امضای بدنه در سرآیند X-CoinePro-Signature به نشانی شما می‌رود.', 'The key is kept in this browser only and is never sent anywhere itself; only the body’s signature goes to your address, in the X-CoinePro-Signature header.')}</small>
      </label>
      <div className="wh-switch">
        <span className="fa">{L('فعال', 'Enabled')}</span>
        <button type="button" className="chip sm" data-act="enabled" onClick={() => edit({ enabled: !draft.enabled })}><span className="fa">{draft.enabled ? L('روشن', 'On') : L('خاموش', 'Off')}</span></button>
      </div>
      <div className="wh-test">
        {valid ? <button type="button" className="chip sm" data-act="test" disabled={testing} onClick={() => { setTesting(true); setResult(null); void test(draft).then((r) => { setResult(r); setTesting(false); }); }}><span className="fa">{testing ? L('در حال ارسال…', 'Sending…') : L('آزمایش', 'Test')}</span></button> : null}
        {result ? <span className={`wh-result fa${failed(result) ? ' down' : ''}`} data-test-result={result.outcome}>{attemptSummary(result, fa)}</span> : null}
      </div>
      <p className="muted small fa">{L('نشانی همین حالا بررسی می‌شود، نه هنگام اجرای هشدار؛ تا اگر ایراد دارد الان بدانید.', 'The address is checked now rather than when an alert fires, so you find out about a problem here.')}</p>
      <p className="muted small fa">{L('مرورگر فقط به نشانی‌ای می‌تواند بفرستد و پاسخش را بخواند که CORS را اجازه دهد. اگر نشانی اجازه ندهد، هشدار باز هم فرستاده می‌شود، اما به‌صورت متن ساده، بدون امضا، و سیاهه نمی‌تواند پاسخ را بخواند.', 'A browser can post to an address and read its answer only where the address allows CORS. Where it does not, the alert is still sent, but as plain text, without the signature, and the log cannot read the answer.')}</p>
      <button className="btn btn-primary" data-act="save" disabled={!valid} onClick={() => { if (save(draft)) setDraft(null); }}><span className="fa">{L('ذخیره‌ی وب‌هوک', 'Save webhook')}</span></button>
      <button type="button" className="chip sm wh-back" data-act="back" onClick={() => setDraft(null)}><span className="fa">{L('بازگشت', 'Back')}</span></button>
    </div>
  );
}
