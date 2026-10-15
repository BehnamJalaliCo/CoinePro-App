import { useEffect, useState } from 'react';
import { usePrefs, useT } from '../state/prefs';
import { usePage } from '../state/page';
import { CATEGORIES, CATEGORY_EN, REACTIONS, community, communityImage, listOf, readPost, type Post } from '../data/community';
import { faAgo } from '../i18n/time';
import { State, Tabs, dateTime, errorText, useL, useLoad } from './kit';
import type { PageProps } from './registry';
import { takePendingPost } from '../shell/publish';

// The community (feature/community): the feed by category with search, posts with likes and
// reactions, a name to post under, the composer with a picture, and the leaderboard. Anonymous:
// the install's key and the chosen name are all the server knows.
export function useMe() {
  return useLoad(() => community<Record<string, unknown>>('me').then((r) => ({ registered: Boolean(r.registered ?? r.display_name ?? r.name), name: String(r.display_name ?? r.name ?? r.username ?? ''), banned: Boolean(r.banned ?? r.is_banned) })), []);
}

export function PostImage({ url }: { url: string }) {
  const [src, setSrc] = useState<string | null>(null);
  useEffect(() => { let u: string | null = null; void communityImage(url).then((x) => { u = x; setSrc(x); }); return () => { if (u) URL.revokeObjectURL(u); }; }, [url]);
  return src ? <img className="post-image" src={src} alt="" loading="lazy" /> : null;
}

export function Reactions({ post, onChange }: { post: Post; onChange(p: Post): void }) {
  const like = async () => { const r = await community<{ likes?: number; liked?: boolean }>(`posts/${post.id}/like`, { method: 'POST' }).catch(() => null); if (r) onChange({ ...post, likes: r.likes ?? post.likes, liked: r.liked ?? !post.liked }); };
  const react = async (emoji: string) => { const r = await community<{ reactions?: Record<string, number>; counts?: Record<string, number> }>(`posts/${post.id}/react`, { body: { emoji } }).catch(() => null); if (r) onChange({ ...post, reactions: r.reactions ?? r.counts ?? post.reactions }); };
  return (
    <div className="row reactions">
      <button className={`chip${post.liked ? ' on' : ''}`} onClick={(e) => { e.stopPropagation(); void like(); }}>♥ <span className="num">{post.likes}</span></button>
      {REACTIONS.map((r) => <button key={r} className="chip" onClick={(e) => { e.stopPropagation(); void react(r); }}>{r} <span className="num">{post.reactions[r] ?? ''}</span></button>)}
      <span className="muted num">💬 {post.replies}</span>
    </div>
  );
}

export default function CommunityPage({ param }: Partial<PageProps> = {}) {
  const L = useL();
  const t = useT();
  const lang = usePrefs((s) => s.lang);
  const open = usePage((s) => s.open);
  // «Publish idea» from the top bar opens the composer (community/post… is the thread page).
  const [tab, setTab] = useState<'feed' | 'compose' | 'leaders'>(param === 'compose' ? 'compose' : 'feed');
  const [cat, setCat] = useState('');
  const [q, setQ] = useState('');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(1);
  const [posts, setPosts] = useState<Post[]>([]);
  const me = useMe();
  const feed = useLoad(() => (search.length >= 2 ? community<Record<string, unknown>>(`posts/search?q=${encodeURIComponent(search)}`) : community<Record<string, unknown>>(`posts?page=${page}&category=${encodeURIComponent(cat)}`)).then((r) => listOf(r).map(readPost)), [cat, page, search]);
  useEffect(() => { if (feed.data) setPosts((cur) => (page === 1 || search ? feed.data! : [...cur, ...feed.data!.filter((p) => !cur.some((c) => c.id === p.id))])); }, [feed.data, page, search]);
  const leaders = useLoad(() => (tab === 'leaders' ? community<Record<string, unknown>>('leaderboard') : Promise.resolve(null)), [tab]);
  const catLabel = (c: string) => (lang === 'fa' ? c : CATEGORY_EN[c] ?? c);

  return (
    <div className="page-inner narrow community">
      <Tabs value={tab} onChange={setTab} tabs={[{ id: 'feed', label: L('گفتگوها', 'Posts') }, { id: 'compose', label: L('نوشتن', 'Write') }, { id: 'leaders', label: L('فعال‌ترین‌ها', 'Leaderboard') }]} />
      {tab === 'feed' ? (
        <>
          <div className="row">
            <Tabs value={cat} onChange={(c) => { setCat(c); setPage(1); }} tabs={[{ id: '', label: L('همه', 'All') }, ...CATEGORIES.map((c) => ({ id: c, label: catLabel(c) }))]} />
            <form onSubmit={(e) => { e.preventDefault(); setSearch(q.trim()); setPage(1); }}><input className="scr-search" value={q} onChange={(e) => { setQ(e.target.value); if (!e.target.value) setSearch(''); }} placeholder={L('جستجو', 'Search')} dir="auto" /></form>
          </div>
          <State loading={feed.loading && !posts.length} error={feed.error} onRetry={feed.reload} empty={!posts.length}>
            <div className="stack">
              {posts.map((p) => (
                <article key={p.id} className="card post click" onClick={() => open('thread', p.id)}>
                  <div className="row"><b dir="auto">{p.author}</b><span className="chip"><span className="fa">{catLabel(p.category)}</span></span>{p.pending ? <span className="muted fa small">{L('در انتظار تأیید', 'Awaiting review')}</span> : null}<span className="ws-spacer" /><span className="muted small">{p.createdAt ? (lang === 'fa' ? faAgo(p.createdAt) : dateTime(p.createdAt)) : ''}</span></div>
                  <p className="fa post-body" dir="auto">{p.content.length > 600 ? `${p.content.slice(0, 600)}…` : p.content}</p>
                  {p.image ? <PostImage url={p.image} /> : null}
                  <Reactions post={p} onChange={(n) => setPosts((cur) => cur.map((x) => (x.id === n.id ? n : x)))} />
                </article>
              ))}
              {!search && feed.data && feed.data.length >= 10 ? <button className="btn btn-quiet sm" disabled={feed.loading} onClick={() => setPage(page + 1)}>{L('بیشتر', 'More')}</button> : null}
            </div>
          </State>
        </>
      ) : null}
      {tab === 'compose' ? <Compose me={me} onDone={() => { setTab('feed'); setPage(1); setSearch(''); feed.reload(); }} /> : null}
      {tab === 'leaders' ? (
        <State loading={leaders.loading} error={leaders.error} onRetry={leaders.reload}>
          <table className="table"><tbody>{(((leaders.data?.items ?? leaders.data?.leaders) ?? []) as Record<string, unknown>[]).map((x, i) => <tr key={i} className={x.is_me ? 'me' : ''}><td className="num">{String(x.rank ?? i + 1)}</td><td dir="auto">{String(x.username ?? x.display_name ?? '')}</td><td className="r num">{String(x.xp ?? x.score ?? x.posts ?? '')}</td></tr>)}</tbody></table>
        </State>
      ) : null}
      <p className="muted small fa">{t('appName')} · {L('انجمن ناشناس است؛ اطلاعات حساب یا تماس منتشر نکنید.', 'The community is anonymous; do not post account or contact details.')}</p>
    </div>
  );
}

function Compose({ me, onDone }: { me: ReturnType<typeof useMe>; onDone(): void }) {
  const L = useL();
  const t = useT();
  const lang = usePrefs((s) => s.lang);
  const [name, setName] = useState('');
  const [pending] = useState(takePendingPost);
  const [content, setContent] = useState(pending?.text ?? '');
  const [category, setCategory] = useState<string>(pending ? 'تحلیل' : 'عمومی');
  const [image, setImage] = useState<string | null>(pending?.image ?? null);
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);

  const register = async () => {
    if (name.trim().length < 2 || name.trim().length > 24) { setMsg(L('نام ۲ تا ۲۴ نویسه است.', 'The name is 2–24 characters.')); return; }
    setBusy(true); setMsg(null);
    try { await community('me', { body: { display_name: name.trim() } }); me.reload(); }
    catch (e) { setMsg((e as { status?: number }).status === 409 ? L('این نام گرفته شده است.', 'That name is taken.') : errorText(e, t)); } finally { setBusy(false); }
  };
  const pickImage = () => {
    const input = document.createElement('input'); input.type = 'file'; input.accept = 'image/*';
    input.onchange = async () => {
      const f = input.files?.[0]; if (!f) return;
      try {
        const bmp = await createImageBitmap(f); const k = Math.min(1, 1280 / Math.max(bmp.width, bmp.height));
        const c = document.createElement('canvas'); c.width = Math.round(bmp.width * k); c.height = Math.round(bmp.height * k);
        c.getContext('2d')!.drawImage(bmp, 0, 0, c.width, c.height);
        setImage(c.toDataURL('image/jpeg', 0.8));
      } catch { /* not an image */ }
    };
    input.click();
  };
  const post = async () => {
    if (content.trim().length < 5) { setMsg(L('دست‌کم ۵ نویسه بنویسید.', 'Write at least 5 characters.')); return; }
    setBusy(true); setMsg(null);
    try {
      const r = await community<{ status?: string; message?: string }>('posts', { body: { content: content.trim().slice(0, 8000), category, ...(image ? { image: image.split(',')[1] } : {}) } });
      setContent(''); setImage(null);
      if (r.status === 'pending') window.alert(r.message || L('پست شما پس از بررسی منتشر می‌شود.', 'Your post appears after review.'));
      onDone();
    } catch (e) { setMsg(errorText(e, t)); } finally { setBusy(false); }
  };

  if (me.loading) return <div className="empty fa">{t('loading')}…</div>;
  if (me.data?.banned) return <div className="empty fa">{L('امکان نوشتن در انجمن برای این نصب بسته شده است.', 'Posting is closed for this install.')}</div>;
  if (!me.data?.registered) {
    return (
      <form className="card form" onSubmit={(e) => { e.preventDefault(); void register(); }}>
        <h3 className="fa">{L('یک نام برای انجمن انتخاب کنید', 'Choose a community name')}</h3>
        <input value={name} onChange={(e) => setName(e.target.value)} maxLength={24} dir="auto" />
        {msg ? <p className="down fa">{msg}</p> : null}
        <button className="btn btn-primary" disabled={busy}>{L('ثبت نام', 'Save the name')}</button>
      </form>
    );
  }
  return (
    <form className="card form" onSubmit={(e) => { e.preventDefault(); void post(); }}>
      <p className="muted fa">{L('با نام', 'Posting as')} <b dir="auto">{me.data.name}</b></p>
      <Tabs value={category} onChange={setCategory} tabs={CATEGORIES.map((c) => ({ id: c, label: lang === 'fa' ? c : CATEGORY_EN[c] ?? c }))} />
      <textarea rows={6} value={content} onChange={(e) => setContent(e.target.value)} maxLength={8000} dir="auto" placeholder={L('چه در ذهن دارید؟', 'What is on your mind?')} />
      <div className="row"><button type="button" className="btn btn-quiet sm" onClick={pickImage}>{L('افزودن تصویر', 'Add a picture')}</button>{image ? <button type="button" className="btn btn-quiet sm" onClick={() => setImage(null)}>×</button> : null}<span className="muted num small">{content.length}/8000</span></div>
      {image ? <img className="post-image" src={image} alt="" /> : null}
      {msg ? <p className="down fa">{msg}</p> : null}
      <button className="btn btn-primary" disabled={busy}>{L('انتشار', 'Publish')}</button>
    </form>
  );
}
