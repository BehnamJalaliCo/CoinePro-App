import { useState } from 'react';
import { usePrefs, useT } from '../state/prefs';
import { usePage } from '../state/page';
import { community, readPost, readReply, type Post } from '../data/community';
import { faAgo } from '../i18n/time';
import { PostImage, Reactions, useMe } from './CommunityPage';
import { State, dateTime, errorText, useL, useLoad } from './kit';
import type { PageProps } from './registry';

// One conversation (feature/community): the post, its replies threaded under their parents, the
// best answer chosen by the author, reply, report.
export default function ThreadPage({ param }: PageProps) {
  const L = useL();
  const t = useT();
  const lang = usePrefs((s) => s.lang);
  const open = usePage((s) => s.open);
  const me = useMe();
  const id = param ?? '';
  const thread = useLoad(() => community<Record<string, unknown>>(`posts/${encodeURIComponent(id)}`).then((r) => ({ post: readPost((r.post ?? r) as Record<string, unknown>), replies: ((r.replies ?? []) as Record<string, unknown>[]).map(readReply) })), [id]);
  const [post, setPost] = useState<Post | null>(null);
  const [text, setText] = useState('');
  const [parent, setParent] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const p = post ?? thread.data?.post ?? null;
  const mine = Boolean(p && me.data?.name && p.author === me.data.name);
  const when = (ms: number | null) => (ms ? (lang === 'fa' ? faAgo(ms) : dateTime(ms)) : '');

  const reply = async () => {
    if (text.trim().length < 2) return;
    setBusy(true); setMsg(null);
    try { await community(`posts/${encodeURIComponent(id)}/reply`, { body: { content: text.trim(), parent_id: parent } }); setText(''); setParent(null); thread.reload(); }
    catch (e) { setMsg(errorText(e, t)); } finally { setBusy(false); }
  };
  const report = async () => { if (window.confirm(L('این گفتگو گزارش شود؟', 'Report this post?'))) { await community(`posts/${encodeURIComponent(id)}/report`, { method: 'POST' }).catch(() => null); setMsg(L('گزارش شد؛ ممنون.', 'Reported; thank you.')); } };
  const best = async (rid: string) => { await community(`posts/${encodeURIComponent(id)}/best-reply/${encodeURIComponent(rid)}`, { method: 'POST' }).catch(() => null); thread.reload(); };
  const replies = thread.data?.replies ?? [];
  const roots = replies.filter((r) => !r.parent || !replies.some((x) => x.id === r.parent));
  const children = (pid: string) => replies.filter((r) => r.parent === pid);
  const node = (r: (typeof replies)[number], depth = 0): JSX.Element => (
    <div key={r.id} className={`reply${r.best || p?.best === r.id ? ' best' : ''}`} style={{ marginInlineStart: depth * 18 }}>
      <div className="row"><b dir="auto">{r.author}</b>{r.best || p?.best === r.id ? <span className="up fa small">✓ {L('بهترین پاسخ', 'Best answer')}</span> : null}<span className="ws-spacer" /><span className="muted small">{when(r.createdAt)}</span></div>
      <p className="fa" dir="auto">{r.content}</p>
      <div className="row"><button className="link-btn" onClick={() => setParent(r.id)}>{L('پاسخ', 'Reply')}</button>{mine && !p?.best ? <button className="link-btn" onClick={() => void best(r.id)}>{L('بهترین پاسخ', 'Best answer')}</button> : null}</div>
      {children(r.id).map((c) => node(c, Math.min(depth + 1, 4)))}
    </div>
  );

  return (
    <div className="page-inner narrow community">
      <button className="link-btn" onClick={() => open('community')}>← {L('انجمن', 'Community')}</button>
      <State loading={thread.loading} error={thread.error} onRetry={thread.reload}>
        {p ? (
          <article className="card post">
            <div className="row"><b dir="auto">{p.author}</b><span className="chip"><span className="fa">{p.category}</span></span><span className="ws-spacer" /><span className="muted small">{when(p.createdAt)}</span><button className="link-btn" onClick={() => void report()}>{L('گزارش', 'Report')}</button></div>
            <p className="fa post-body" dir="auto">{p.content}</p>
            {p.image ? <PostImage url={p.image} /> : null}
            <Reactions post={p} onChange={setPost} />
          </article>
        ) : null}
        <div className="stack">{roots.map((r) => node(r))}</div>
      </State>
      {me.data?.registered ? (
        <form className="card form" onSubmit={(e) => { e.preventDefault(); void reply(); }}>
          {parent ? <p className="muted small fa">{L('در پاسخ به یک پاسخ', 'Replying to a reply')} <button type="button" className="link-btn" onClick={() => setParent(null)}>×</button></p> : null}
          <textarea rows={3} value={text} onChange={(e) => setText(e.target.value)} dir="auto" placeholder={L('پاسخ شما', 'Your reply')} />
          <button className="btn btn-primary sm" disabled={busy || text.trim().length < 2}>{L('ارسال پاسخ', 'Send the reply')}</button>
        </form>
      ) : <p className="muted fa">{L('برای پاسخ، اول در «نوشتن» یک نام انتخاب کنید.', 'To reply, first choose a name under Write.')} <button className="link-btn" onClick={() => open('community')}>{L('انجمن', 'Community')}</button></p>}
      {msg ? <p className="fa">{msg}</p> : null}
    </div>
  );
}
