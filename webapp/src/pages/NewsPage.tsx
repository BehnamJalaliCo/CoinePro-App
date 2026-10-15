import { useEffect, useMemo, useState } from 'react';
import { useT } from '../state/prefs';
import { usePage } from '../state/page';
import { useSession } from '../data/session';
import { articleBody, cryptoNews, forexNews, type NewsItem } from '../data/intel';
import { member, str, ms } from '../data/member';
import { faAgo } from '../i18n/time';
import { Tabs, useLoad, State } from './kit';
import { Icon } from '../ui/Icon';

// The app's news (feature/news): crypto and forex feeds, high-impact filter and search, saved
// stories, the platform's announcements for members, the full story in place, share and source.
type Tab = 'crypto' | 'forex' | 'saved' | 'announcements';
const SAVED = 'pc.news.saved.v1';
const readSaved = (): NewsItem[] => { try { return JSON.parse(localStorage.getItem(SAVED) ?? '[]') as NewsItem[]; } catch { return []; } };

export default function NewsPage({ param }: { param: string | null }) {
  const t = useT();
  const signed = useSession((s) => Boolean(s.sessions.tradeyar || s.sessions.coineprofx));
  const open = usePage((s) => s.open);
  const [tab, setTab] = useState<Tab>('crypto');
  const [onlyHot, setOnlyHot] = useState(false);
  const [q, setQ] = useState('');
  const [saved, setSaved] = useState<NewsItem[]>(readSaved);
  const [story, setStory] = useState<NewsItem | null>(null);
  const [body, setBody] = useState<string | null>(null);
  const feed = useLoad(async () => {
    if (tab === 'crypto') return cryptoNews(50);
    if (tab === 'forex') return forexNews();
    if (tab === 'saved') return readSaved();
    const r = await member<Record<string, unknown>>('announcements?limit=30');
    const list = (r.announcements ?? r.items ?? r.news ?? []) as Record<string, unknown>[];
    return list.map((x): NewsItem => ({ id: `a${str(x.id)}`, market: 'crypto', title: str(x.title), summary: str(x.summary), source: str(x.source), time: ms(x.published_at) ?? 0, image: null, url: x.url ? str(x.url) : undefined, importance: Number(x.importance ?? 0) }));
  }, [tab]);

  useEffect(() => { if (param && feed.data) { const s = feed.data.find((x) => x.slug === param || x.id === param); if (s) setStory(s); } }, [param, feed.data]);
  useEffect(() => { setBody(null); if (story) void articleBody(story).then(setBody).catch(() => setBody(null)); }, [story]);

  const list = useMemo(() => (feed.data ?? []).filter((n) => (!onlyHot || n.importance >= 7) && (!q.trim() || n.title.includes(q.trim()) || n.summary.includes(q.trim()))), [feed.data, onlyHot, q]);
  const toggleSave = (n: NewsItem) => {
    const next = saved.some((x) => x.id === n.id) ? saved.filter((x) => x.id !== n.id) : [n, ...saved];
    setSaved(next);
    try { localStorage.setItem(SAVED, JSON.stringify(next.slice(0, 200))); } catch { /* ignore */ }
  };
  const share = (n: NewsItem) => {
    const url = n.url ?? window.location.href;
    if (navigator.share) void navigator.share({ title: n.title, url }).catch(() => undefined);
    else void navigator.clipboard?.writeText(`${n.title}\n${url}`);
  };

  if (story) {
    return (
      <div className="page-inner narrow">
        <button className="btn btn-quiet sm" onClick={() => setStory(null)}><Icon name="ph-caret-right" size="sm" /><span className="fa">{t('back')}</span></button>
        <article className="article" dir="rtl">
          {story.image ? <img src={story.image} alt="" className="article-img" /> : null}
          <h2>{story.title}</h2>
          <div className="news-meta"><span>{story.source}</span> · <span>{faAgo(story.time)}</span></div>
          <p className="article-text">{body ?? story.summary}</p>
          <div className="row">
            <button className="btn btn-quiet sm" onClick={() => toggleSave(story)}><Icon name={saved.some((x) => x.id === story.id) ? 'ph-star-fill' : 'ph-star'} size="sm" /><span className="fa">{t('newsSave')}</span></button>
            <button className="btn btn-quiet sm" onClick={() => share(story)}><Icon name="ph-share-network" size="sm" /><span className="fa">{t('share')}</span></button>
            {story.url ? <a className="btn btn-quiet sm" href={story.url} target="_blank" rel="noopener noreferrer">{story.source}</a> : null}
          </div>
        </article>
      </div>
    );
  }

  return (
    <div className="page-inner narrow">
      <Tabs value={tab} onChange={setTab} tabs={[
        { id: 'crypto', label: t('crypto') }, { id: 'forex', label: t('forex') }, { id: 'saved', label: t('newsSaved') },
        ...(signed ? [{ id: 'announcements' as Tab, label: t('announcements') }] : []),
      ]} />
      <div className="row">
        <button className={`chip${onlyHot ? ' on' : ''}`} onClick={() => setOnlyHot(!onlyHot)}><span className="fa">{t('highImpact')}</span></button>
        <input className="scr-search" value={q} onChange={(e) => setQ(e.target.value)} placeholder={t('search')} dir="auto" />
        <span className="ws-spacer" />
        <button className="link-btn" onClick={() => open('calendar')}>{t('calendar')}</button>
      </div>
      <State loading={feed.loading} error={feed.error} empty={!list.length} onRetry={feed.reload}>
        {list.map((n) => (
          <div key={n.id} className={`news-card${n.importance >= 7 ? ' hot' : ''}`} dir="rtl">
            {n.image ? <img src={n.image} alt="" loading="lazy" /> : null}
            <button className="news-text" onClick={() => setStory(n)}>
              <b>{n.title}</b>
              <span className="news-sum">{n.summary}</span>
              <span className="news-meta">{n.source} · {faAgo(n.time)}</span>
            </button>
            <button className="lt" onClick={() => toggleSave(n)} aria-label={t('newsSave')}><Icon name={saved.some((x) => x.id === n.id) ? 'ph-star-fill' : 'ph-star'} size="sm" /></button>
          </div>
        ))}
      </State>
    </div>
  );
}
