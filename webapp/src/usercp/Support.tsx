import { useEffect, useState } from 'react';
import {
  ATTACHMENTS_PER_MESSAGE, ATTACHMENT_LIMIT, TICKET_CATEGORIES, closeTicket, getTicket, getTickets, openTicket, panelBlob, panelError, replyTicket,
  type NewAttachment, type TicketAttachment,
} from '../data/panel';
import { State, errorText, useL, useLoad } from '../pages/kit';
import { useT } from '../state/prefs';
import { Icon } from '../ui/Icon';
import { usePanel } from './PanelPage';
import { Card, Note, Pill, Section, stamp, useAgo } from './ui';
import { categoryLabel, ticketStatus } from './labels';

/** A picture made small enough to send: at most 1600 px on its long side, JPEG, under the limit. */
export async function shrinkImage(file: File, limit = ATTACHMENT_LIMIT): Promise<NewAttachment> {
  const bitmap = await createImageBitmap(file);
  const long = Math.max(bitmap.width, bitmap.height);
  let side = Math.min(1600, long);
  for (let attempt = 0; attempt < 8; attempt++) {
    const scale = side / long;
    const canvas = document.createElement('canvas');
    canvas.width = Math.max(1, Math.round(bitmap.width * scale));
    canvas.height = Math.max(1, Math.round(bitmap.height * scale));
    canvas.getContext('2d')!.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
    for (const quality of [0.86, 0.72, 0.6]) {
      const data = canvas.toDataURL('image/jpeg', quality);
      if (Math.floor((data.length - data.indexOf(',') - 1) * 0.75) <= limit) return { name: file.name.replace(/\.[^.]+$/, '') + '.jpg', data };
    }
    side = Math.round(side * 0.75);
  }
  throw new Error('too-large');
}

function Attachments({ files, onChange }: { files: NewAttachment[]; onChange(f: NewAttachment[]): void }) {
  const L = useL();
  const [err, setErr] = useState<string | null>(null);
  const add = () => {
    const input = document.createElement('input');
    input.type = 'file'; input.accept = 'image/png,image/jpeg,image/webp,image/gif'; input.multiple = true;
    input.onchange = async () => {
      setErr(null);
      const picked = Array.from(input.files ?? []).slice(0, ATTACHMENTS_PER_MESSAGE - files.length);
      const out: NewAttachment[] = [];
      for (const f of picked) {
        try { out.push(await shrinkImage(f)); } catch { setErr(L('این تصویر خوانده نشد یا خیلی بزرگ است.', 'That picture could not be read or is too large.')); }
      }
      onChange([...files, ...out]);
    };
    input.click();
  };
  return (
    <div className="ucp-attach">
      {files.map((f, i) => (
        <span key={i} className="ucp-thumb"><img src={f.data} alt={f.name} /><button type="button" aria-label={L('حذف تصویر', 'Remove picture')} onClick={() => onChange(files.filter((_, j) => j !== i))}><Icon name="ph-x" size="sm" /></button></span>
      ))}
      {files.length < ATTACHMENTS_PER_MESSAGE ? <button type="button" className="btn btn-quiet sm" onClick={add}><Icon name="ph-paperclip" size="sm" />{L('افزودن تصویر', 'Add a picture')}</button> : null}
      <small className="ucp-muted">{L('تا ۳ تصویر، هر کدام حداکثر ۳۰۰ کیلوبایت (خودکار کوچک می‌شود).', 'Up to 3 pictures, 300 KB each (made smaller for you).')}</small>
      {err ? <small className="ucp-bad-text">{err}</small> : null}
    </div>
  );
}

function Shot({ ticket, file }: { ticket: number; file: TicketAttachment }) {
  const [url, setUrl] = useState<string | null>(null);
  useEffect(() => {
    let alive = true; let made: string | null = null;
    void panelBlob(`tickets/${ticket}/attachments/${file.id}`).then((b) => { if (!alive) return; made = URL.createObjectURL(b); setUrl(made); }).catch(() => undefined);
    return () => { alive = false; if (made) URL.revokeObjectURL(made); };
  }, [ticket, file.id]);
  return url ? <a className="ucp-thumb" href={url} target="_blank" rel="noopener noreferrer"><img src={url} alt={file.name} /></a> : <span className="ucp-thumb wait" />;
}

function Thread({ id, onBack, onChange }: { id: number; onBack(): void; onChange(): void }) {
  const L = useL();
  const t = useT();
  const ago = useAgo();
  const thread = useLoad(() => getTicket(id), [id]);
  const [body, setBody] = useState('');
  const [files, setFiles] = useState<NewAttachment[]>([]);
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const send = async () => {
    if (!body.trim()) return;
    setBusy(true); setMsg(null);
    try { await replyTicket(id, { body: body.trim(), attachments: files }); setBody(''); setFiles([]); thread.reload(); onChange(); }
    catch (e) { setMsg(panelError(e) === 'slow_down' ? L('کمی صبر کنید و دوباره بفرستید.', 'Wait a little and send again.') : errorText(e, t)); } finally { setBusy(false); }
  };
  const close = async () => { await closeTicket(id).catch(() => null); thread.reload(); onChange(); };
  const tk = thread.data?.ticket;
  const [label, tone] = tk ? ticketStatus(tk.status, L) : ['', 'muted' as const];
  return (
    <Card title={tk?.subject ?? '…'} sub={tk ? `#${tk.id} · ${categoryLabel(tk.category, L)} · ${stamp(tk.created_at * 1000)}` : undefined}
      actions={<><button className="btn btn-quiet sm" onClick={onBack}>{L('همه‌ی تیکت‌ها', 'All tickets')}</button>{tk ? <Pill tone={tone}>{label}</Pill> : null}</>}>
      <State loading={thread.loading} error={thread.error} onRetry={thread.reload}>
        <div className="ucp-thread">
          {(thread.data?.messages ?? []).map((m) => (
            <div key={m.id} className={`ucp-bubble ${m.author}`}>
              <div className="ucp-bubble-head"><b>{m.author === 'owner' ? L('پشتیبانی پرو چارت', 'Pro Chart support') : L('شما', 'You')}</b><span className="ucp-muted">{ago(m.at * 1000)}</span></div>
              <p dir="auto">{m.body}</p>
              {m.attachments.length ? <div className="ucp-attach">{m.attachments.map((a) => <Shot key={a.id} ticket={id} file={a} />)}</div> : null}
            </div>
          ))}
        </div>
        <form className="ucp-form" onSubmit={(e) => { e.preventDefault(); void send(); }}>
          <label className="field"><span>{tk?.status === 'closed' ? L('پاسخ تازه این تیکت را دوباره باز می‌کند', 'A new reply reopens this ticket') : L('پاسخ شما', 'Your reply')}</span>
            <textarea rows={4} maxLength={4000} value={body} onChange={(e) => setBody(e.target.value)} dir="auto" /></label>
          <Attachments files={files} onChange={setFiles} />
          {msg ? <Note tone="bad" icon="ph-warning">{msg}</Note> : null}
          <div className="ucp-actions">
            <button className="btn btn-primary sm" disabled={busy || !body.trim()}>{L('ارسال', 'Send')}</button>
            {tk && tk.status !== 'closed' ? <button type="button" className="btn btn-quiet sm" onClick={() => void close()}>{L('بستن تیکت', 'Close the ticket')}</button> : null}
          </div>
        </form>
      </State>
    </Card>
  );
}

export default function Support() {
  const L = useL();
  const t = useT();
  const ago = useAgo();
  const { reload: reloadMe } = usePanel();
  const list = useLoad(() => getTickets().then((r) => r.items), []);
  const [view, setView] = useState<'list' | 'new' | number>('list');
  const [subject, setSubject] = useState('');
  const [category, setCategory] = useState<string>('bug');
  const [body, setBody] = useState('');
  const [files, setFiles] = useState<NewAttachment[]>([]);
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);

  const create = async () => {
    if (subject.trim().length < 3 || body.trim().length < 5) { setMsg(L('موضوع و شرح را کامل بنویسید.', 'Write a subject and a description.')); return; }
    setBusy(true); setMsg(null);
    try {
      const r = await openTicket({ subject: subject.trim(), category, body: body.trim(), attachments: files });
      setSubject(''); setBody(''); setFiles([]); list.reload(); reloadMe(); setView(r.id);
    } catch (e) {
      const code = panelError(e);
      setMsg(code === 'slow_down' || code === 'too_many_open' ? L('تیکت‌های باز زیادی دارید یا زیاد فرستاده‌اید؛ کمی بعد دوباره امتحان کنید.', 'You have many open tickets or sent many; try again a little later.')
        : code === 'attachment_type' ? L('فقط تصویر PNG، JPEG، WebP یا GIF.', 'Only PNG, JPEG, WebP or GIF pictures.') : errorText(e, t));
    } finally { setBusy(false); }
  };

  if (typeof view === 'number') return <Section title={L('پشتیبانی', 'Support')}><Thread id={view} onBack={() => setView('list')} onChange={() => { list.reload(); reloadMe(); }} /></Section>;

  return (
    <Section title={L('پشتیبانی', 'Support')} sub={L('سؤال، مشکل یا پیشنهاد را بنویسید؛ پاسخ همین‌جا و در اعلان‌ها می‌آید.', 'Write a question, a problem or a suggestion; the answer comes here and in your notifications.')}
      actions={view === 'list' ? <button className="btn btn-primary sm" onClick={() => setView('new')}>{L('تیکت تازه', 'New ticket')}</button> : null}>
      {view === 'new' ? (
        <Card title={L('تیکت تازه', 'New ticket')}>
          <form className="ucp-form" onSubmit={(e) => { e.preventDefault(); void create(); }}>
            <label className="field"><span>{L('موضوع', 'Subject')}</span><input value={subject} maxLength={120} onChange={(e) => setSubject(e.target.value)} dir="auto" /></label>
            <label className="field"><span>{L('دسته', 'Category')}</span>
              <select value={category} onChange={(e) => setCategory(e.target.value)}>{TICKET_CATEGORIES.map((c) => <option key={c} value={c}>{categoryLabel(c, L)}</option>)}</select></label>
            <label className="field"><span>{L('شرح', 'Description')}</span><textarea rows={6} maxLength={4000} value={body} onChange={(e) => setBody(e.target.value)} dir="auto"
              placeholder={L('چه اتفاقی افتاد، روی کدام نماد و تایم‌فریم، و انتظار چه چیزی داشتید؟', 'What happened, on which symbol and timeframe, and what did you expect?')} /></label>
            <Attachments files={files} onChange={setFiles} />
            {msg ? <Note tone="bad" icon="ph-warning">{msg}</Note> : null}
            <div className="ucp-actions">
              <button className="btn btn-primary sm" disabled={busy}>{L('ارسال تیکت', 'Send the ticket')}</button>
              <button type="button" className="btn btn-quiet sm" onClick={() => setView('list')}>{L('انصراف', 'Cancel')}</button>
            </div>
          </form>
        </Card>
      ) : null}
      <Card title={L('تیکت‌های شما', 'Your tickets')}>
        <State loading={list.loading} error={list.error} onRetry={list.reload} empty={!list.data?.length}>
          <ul className="ucp-tickets">
            {(list.data ?? []).map((tk) => {
              const [label, tone] = ticketStatus(tk.status, L);
              return (
                <li key={tk.id}>
                  <button onClick={() => setView(tk.id)}>
                    <span className="num ucp-muted">#{tk.id}</span>
                    <b dir="auto">{tk.subject}</b>
                    {tk.unread_user ? <Pill tone="info">{L('پاسخ تازه', 'New reply')}</Pill> : null}
                    <span className="ucp-muted">{categoryLabel(tk.category, L)}</span>
                    <Pill tone={tone}>{label}</Pill>
                    <span className="ucp-muted">{ago(tk.updated_at * 1000)}</span>
                  </button>
                </li>
              );
            })}
          </ul>
        </State>
      </Card>
    </Section>
  );
}
