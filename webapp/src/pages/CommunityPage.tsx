import { useEffect, useRef, useState } from 'react';
import { usePrefs, useT } from '../state/prefs';
import { usePage } from '../state/page';
import { CATEGORIES, CATEGORY_EN, REACTIONS, community, communityImage, listOf, readPost, type Post } from '../data/community';
import { faAgo } from '../i18n/time';
import { State, Tabs, dateTime, errorText, useL, useLoad } from './kit';
import type { PageProps } from './registry';
import { takePendingPost } from '../shell/publish';
import { create } from 'zustand';
import { splitPost } from '../script/doc';
import { writeClipboard } from '../script/clipboard';
import { isFounding } from '../data/founding';
import { faNum } from '../i18n/time';
import { Dropdown } from '../ui/Menu';
import { Icon } from '../ui/Icon';
import './extras.css';
import '../script/script2.css';

// The authors this reader follows (OLD core/community CommunityController.toggleFollow): kept in this
// browser, by display name, and a «دنبال‌شده‌ها» chip that narrows whichever topic is chosen to them.
const FOLLOW_KEY = 'pc.community.follows.v1';
const readFollows = (): string[] => { try { const v = JSON.parse(localStorage.getItem(FOLLOW_KEY) ?? '[]') as unknown; return Array.isArray(v) ? v.filter((x): x is string => typeof x === 'string') : []; } catch { return []; } };
export const useFollows = create<{ follows: string[]; toggle(author: string): void }>((set, get) => ({
  follows: readFollows(),
  toggle: (author) => {
    const follows = get().follows.includes(author) ? get().follows.filter((x) => x !== author) : [...get().follows, author];
    set({ follows });
    try { localStorage.setItem(FOLLOW_KEY, JSON.stringify(follows)); } catch { /* blocked */ }
  },
}));

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

/**
 * A post is prose, and sometimes a file (a shared script): the two are read in opposite directions, so
 * the file is a left-to-right code block that does not wrap. In the feed the file is a promise — four
 * lines — and whole in the thread, where the reader chose it.
 */
export function PostBody({ content, expanded = false }: { content: string; expanded?: boolean }) {
  const { prose, code } = splitPost(content);
  const text = expanded || prose.length <= 600 ? prose : `${prose.slice(0, 600)}…`;
  return (
    <>
      {text.trim() ? <p className="fa post-body" dir="auto">{text}</p> : null}
      {code ? <pre className={`sx-code post-code${expanded ? '' : ' feed'}`} dir="ltr">{expanded ? code : code.split('\n').slice(0, 4).join('\n')}</pre> : null}
    </>
  );
}

/** «گزینه‌های این نوشته»: copy the text (the follow stays on the card, where it was). */
export function PostMenu({ post, onNote }: { post: Post; onNote(text: string): void }) {
  const L = useL();
  const btn = useRef<HTMLButtonElement>(null);
  const [open, setOpen] = useState(false);
  const copy = async () => { setOpen(false); if (await writeClipboard(post.content)) onNote(L('متن رونوشت شد.', 'Copied.')); else window.prompt('', post.content); };
  return (
    <>
      <button ref={btn} className="lt post-menu-btn" aria-label={L('گزینه‌های این نوشته', 'Post options')} title={L('گزینه‌های این نوشته', 'Post options')} aria-haspopup="menu" onClick={(e) => { e.stopPropagation(); setOpen(!open); }}><Icon name="ph-dots-three" size="sm" /></button>
      {open ? (
        <Dropdown anchor={btn.current} onClose={() => setOpen(false)}>
          <button className="menu-item post-copy" onClick={(e) => { e.stopPropagation(); void copy(); }}><Icon name="ph-copy" size="sm" /><span className="fa">{L('رونوشت متن', 'Copy text')}</span></button>
        </Dropdown>
      ) : null}
    </>
  );
}

/** «عضو بنیان‌گذار», on the reader's own posts only: the mark lives in this browser and is about this reader. */
export function FoundingMark({ author, me }: { author: string; me: string | undefined }) {
  const L = useL();
  return me && author === me && isFounding() ? <span className="founding fa">{L('عضو بنیان‌گذار', 'Founding member')}</span> : null;
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
  const follows = useFollows((f) => f.follows);
  const toggleFollow = useFollows((f) => f.toggle);
  const [followingOnly, setFollowingOnly] = useState(false);
  const [note, setNote] = useState<string | null>(null);
  const feed = useLoad(() => (search.length >= 2 ? community<Record<string, unknown>>(`posts/search?q=${encodeURIComponent(search)}`) : community<Record<string, unknown>>(`posts?page=${page}&category=${encodeURIComponent(cat)}`)).then((r) => listOf(r).map(readPost)), [cat, page, search]);
  useEffect(() => { if (feed.data) setPosts((cur) => (page === 1 || search ? feed.data! : [...cur, ...feed.data!.filter((p) => !cur.some((c) => c.id === p.id))])); }, [feed.data, page, search]);
  const leaders = useLoad(() => (tab === 'leaders' ? community<Record<string, unknown>>('leaderboard') : Promise.resolve(null)), [tab]);
  const catLabel = (c: string) => (lang === 'fa' ? c : CATEGORY_EN[c] ?? c);

  return (
    <div className="page-inner narrow community">
      <Tabs value={tab} onChange={setTab} tabs={[{ id: 'feed', label: L('گفتگوها', 'Posts') }, { id: 'compose', label: L('نوشتن', 'Write') }, { id: 'leaders', label: L('فعال‌ترین‌ها', 'Leaderboard') }]} />
      {note && tab === 'feed' ? <p className="muted fa community-note" role="status">{note}</p> : null}
      {tab === 'feed' ? (
        <>
          <div className="row">
            <Tabs value={cat} onChange={(c) => { setCat(c); setPage(1); }} tabs={[{ id: '', label: L('همه', 'All') }, ...CATEGORIES.map((c) => ({ id: c, label: catLabel(c) }))]} />
            {!search ? <button className={`chip following-chip${followingOnly ? ' on' : ''}`} aria-pressed={followingOnly} onClick={() => setFollowingOnly(!followingOnly)}><span className="fa">{L('دنبال‌شده‌ها', 'Following')}</span></button> : null}
            <form onSubmit={(e) => { e.preventDefault(); setSearch(q.trim()); setPage(1); }}><input className="scr-search" value={q} onChange={(e) => { setQ(e.target.value); if (!e.target.value) setSearch(''); }} placeholder={L('جستجو', 'Search')} dir="auto" /></form>
          </div>
          <State loading={feed.loading && !posts.length} error={feed.error} onRetry={feed.reload} empty={!posts.length}>
            <div className="stack">
              {followingOnly && !search && !posts.some((p) => follows.includes(p.author)) ? (
                <div className="empty fa following-empty">{L('هنوز پستی از نویسنده‌هایی که دنبال می‌کنید اینجا نیست.', 'No posts from the authors you follow here yet.')} <button className="btn btn-quiet sm" onClick={() => setFollowingOnly(false)}>{L('نمایش همه‌ی پست‌ها', 'Show all posts')}</button></div>
              ) : null}
              {posts.filter((p) => !followingOnly || search || follows.includes(p.author)).map((p) => (
                <article key={p.id} className="card post click" onClick={() => open('thread', p.id)}>
                  <div className="row"><b dir="auto">{p.author}</b><FoundingMark author={p.author} me={me.data?.name} /><span className="chip"><span className="fa">{catLabel(p.category)}</span></span>{p.pending ? <span className="muted fa small">{L('در انتظار تأیید', 'Awaiting review')}</span> : null}<span className="ws-spacer" />{p.author && p.author !== me.data?.name ? <button className="link-btn follow-btn" data-author={p.author} aria-pressed={follows.includes(p.author)} onClick={(e) => { e.stopPropagation(); toggleFollow(p.author); }}>{follows.includes(p.author) ? L('لغو دنبال کردن', 'Unfollow author') : L('دنبال کردن نویسنده', 'Follow author')}</button> : null}<span className="muted small">{p.createdAt ? (lang === 'fa' ? faAgo(p.createdAt) : dateTime(p.createdAt)) : ''}</span><PostMenu post={p} onNote={setNote} /></div>
                  <PostBody content={p.content} />
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

/** The server's bounds on a display name (app_community.py). */
const NAME_MIN = 2;
const NAME_MAX = 24;

function Compose({ me, onDone }: { me: ReturnType<typeof useMe>; onDone(): void }) {
  const L = useL();
  const t = useT();
  const lang = usePrefs((s) => s.lang);
  const [name, setName] = useState('');
  // «تغییر نام»: the same card as the first time, with the current name in it.
  const [naming, setNaming] = useState(false);
  const [pending] = useState(takePendingPost);
  const [content, setContent] = useState(pending?.text ?? '');
  const [category, setCategory] = useState<string>(pending ? 'تحلیل' : 'عمومی');
  const [image, setImage] = useState<string | null>(pending?.image ?? null);
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);

  const register = async () => {
    if (name.trim().length < NAME_MIN || name.trim().length > NAME_MAX) { setMsg(L('نام ۲ تا ۲۴ نویسه است.', 'The name is 2–24 characters.')); return; }
    setBusy(true); setMsg(null);
    try { await community('me', { body: { display_name: name.trim() } }); setNaming(false); me.reload(); }
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
  if (!me.data?.registered || naming) {
    const trimmed = name.trim();
    const sendable = trimmed.length >= NAME_MIN && trimmed.length <= NAME_MAX && !busy && trimmed !== me.data?.name;
    return (
      <form className="card form name-card" onSubmit={(e) => { e.preventDefault(); void register(); }}>
        <h3 className="fa">{L('یک نام برای خودتان انتخاب کنید', 'Pick a name for yourself')}</h3>
        <p className="muted fa">{L('انجمن مستقل از حساب‌های شماست. فقط همین نام دیده می‌شود؛ نه شماره، نه ایمیل.', 'The board is independent of your accounts. Only this name is shown — no phone, no email.')}</p>
        <input value={name} onChange={(e) => setName(e.target.value)} maxLength={NAME_MAX} dir="auto" aria-label={L('نام نمایشی', 'Display name')} placeholder={L('نام نمایشی', 'Display name')} />
        <small className={msg ? 'down fa' : 'muted fa'}>{msg ?? L(`بین ${faNum(NAME_MIN)} تا ${faNum(NAME_MAX)} نویسه: حرف، عدد، فاصله یا زیرخط.`, `${NAME_MIN} to ${NAME_MAX} characters: letters, digits, spaces or underscores.`)}</small>
        <div className="row">
          <button className="btn btn-primary" disabled={!sendable}>{L('ثبت نام', 'Save name')}</button>
          {naming ? <button type="button" className="btn btn-quiet" onClick={() => { setNaming(false); setMsg(null); }}>{L('بستن', 'Close')}</button> : null}
        </div>
      </form>
    );
  }
  return (
    <form className="card form" onSubmit={(e) => { e.preventDefault(); void post(); }}>
      <p className="muted fa">{L(`شما: ${me.data.name}`, `You: ${me.data.name}`)} <button type="button" className="link-btn change-name" onClick={() => { setName(me.data?.name ?? ''); setMsg(null); setNaming(true); }}>{L('تغییر نام', 'Change name')}</button></p>
      <Tabs value={category} onChange={setCategory} tabs={CATEGORIES.map((c) => ({ id: c, label: lang === 'fa' ? c : CATEGORY_EN[c] ?? c }))} />
      <textarea rows={6} value={content} onChange={(e) => setContent(e.target.value)} maxLength={8000} dir="auto" placeholder={L('چه در ذهن دارید؟', 'What is on your mind?')} />
      <div className="row"><button type="button" className="btn btn-quiet sm" onClick={pickImage}>{L('افزودن تصویر', 'Add a picture')}</button>{image ? <button type="button" className="btn btn-quiet sm" onClick={() => setImage(null)}>×</button> : null}<span className="muted num small">{content.length}/8000</span></div>
      {image ? <img className="post-image" src={image} alt="" /> : null}
      {msg ? <p className="down fa">{msg}</p> : null}
      <button className="btn btn-primary" disabled={busy}>{L('انتشار', 'Publish')}</button>
    </form>
  );
}
