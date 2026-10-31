import { useEffect, useMemo, useRef, useState } from 'react';
import { useT } from '../state/prefs';
import { usePage } from '../state/page';
import { useSession } from '../data/session';
import { articleBody, cryptoNews, forexNews, SAVED_KEYS, type NewsItem } from '../data/intel';
import { useSaved } from '../data/saved';
import { SavedLine } from '../shell/Offline';
import { pub, ty, str, ms } from '../data/member';
import { HttpError } from '../data/http';
import { faAgo, faNum } from '../i18n/time';
import { usePrefs } from '../state/prefs';
import { useChartBus } from '../chart/chartBus';
import { goToTime } from '../chart/actions';
import { Tabs, useL, useLoad, State } from './kit';
import { Icon } from '../ui/Icon';
import { contextSentence, parseRelevance, relatedTo, relevanceOf, symbolFor, type Story } from './newsContext';
import './home2.css';

// The app's news (feature/news): crypto and forex feeds, high-impact filter and search, saved
// stories, the platform's announcements (open to guests too, OLD AnnouncementsScreen), the full story
// in place with what it bears on, its chart and more from the same market, share and source.
type Tab = 'crypto' | 'forex' | 'saved' | 'announcements';
const SAVED = 'pc.news.saved.v1';
const readSaved = (): NewsItem[] => { try { return JSON.parse(localStorage.getItem(SAVED) ?? '[]') as NewsItem[]; } catch { return []; } };

/** Announcements from TradeYar: the member's session when there is one, the public read otherwise. */
async function announcements(signedTy: boolean): Promise<Story[]> {
  const path = 'api/mobile/v1/announcements?limit=30';
  const r = signedTy ? await ty<Record<string, unknown>>(path) : await pub<Record<string, unknown>>(`/up/tradeyar/${path}`);
  if (!r || typeof r !== 'object') throw new Error('unreadable');
  const list = (r.announcements ?? r.items ?? r.news ?? []) as Record<string, unknown>[];
  if (!Array.isArray(list)) throw new Error('unreadable');
  // An announcement with no id, title or date is not one (AnnouncementDto.toDomain); a link only on https.
  return list.filter((x) => str(x.id).trim() && str(x.title).trim() && ms(x.published_at ?? x.publishedAt) != null).map((x): Story => {
    const url = str(x.url).trim();
    const imp = str(x.importance).toLowerCase();
    return {
      id: `a${str(x.id)}`, market: 'crypto', title: str(x.title).trim(), summary: str(x.summary).trim(), source: str(x.source), time: ms(x.published_at ?? x.publishedAt) ?? 0, image: null,
      url: /^https:\/\/[^\s/]+/.test(url) ? url : undefined, importance: imp === 'high' || imp === 'critical' ? 8 : Number(x.importance) || 0,
      relevance: parseRelevance(x.relevance ?? x.markets ?? x.tags),
    };
  });
}

/** Opens the chart of `symbol` and scrolls it to `atMs` once its bars have loaded. */
function showOnChart(symbol: string, atMs: number, close: () => void): void {
  usePrefs.getState().setSymbol(symbol);
  close();
  const until = Date.now() + 15_000;
  const tick = () => {
    const { bars, api } = useChartBus.getState();
    if (api && bars.length && usePrefs.getState().symbol === symbol) { void goToTime(Math.floor(atMs / 1000)); return; }
    if (Date.now() < until) setTimeout(tick, 250);
  };
  setTimeout(tick, 300);
}

export default function NewsPage({ param }: { param: string | null }) {
  const t = useT();
  const L = useL();
  const fa = usePrefs((s) => s.lang) === 'fa';
  const signedTy = useSession((s) => Boolean(s.sessions.tradeyar));
  const open = usePage((s) => s.open);
  const closePage = usePage((s) => s.close);
  const [tab, setTab] = useState<Tab>('crypto');
  const [onlyHot, setOnlyHot] = useState(false);
  const [q, setQ] = useState('');
  const [saved, setSaved] = useState<NewsItem[]>(readSaved);
  const [story, setStory] = useState<Story | null>(null);
  const annFail = useRef<{ offline: boolean; detail: string | null } | null>(null);
  const [body, setBody] = useState<string | null>(null);
  const savedAt = useSaved((s) => (tab === 'crypto' ? s.at[SAVED_KEYS.cryptoNews] : tab === 'forex' ? s.at[SAVED_KEYS.forexNews] : undefined) ?? null);
  const feed = useLoad(async () => {
    if (tab === 'crypto') return cryptoNews(50);
    if (tab === 'forex') return forexNews();
    if (tab === 'saved') return readSaved() as Story[];
    try { annFail.current = null; return await announcements(signedTy); }
    catch (e) { annFail.current = { offline: e instanceof HttpError && e.status === -1, detail: e instanceof HttpError ? e.readerMessage : null }; throw e; }
  }, [tab, signedTy]);
  // Ours first, the server's own words under it — never instead of them (AnnouncementsScreen FAILED).
  const annError = tab === 'announcements' && feed.error && annFail.current
    ? `${annFail.current.offline ? L('اتصال برقرار نشد. اطلاعیه‌ها بعداً به‌روز می‌شود.', 'Could not connect. Announcements will update later.') : L('سرویس اطلاعیه پاسخ نداد.', 'The announcements service did not answer.')}${annFail.current.detail ? ` ${annFail.current.detail}` : ''}`
    : null;

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
    const rel = relevanceOf(story);
    const chartSymbol = symbolFor(rel);
    const related = relatedTo(story, (feed.data ?? []) as Story[]);
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
          <section className="card news-context" dir={fa ? 'rtl' : 'ltr'}>
            <span className="eyebrow">{L('این خبر به چه مربوط است', 'What this bears on')}</span>
            <p>{contextSentence(rel, fa)}</p>
            {chartSymbol && story.time ? <button type="button" className="btn btn-quiet sm news-chart" onClick={() => showOnChart(chartSymbol, story.time, closePage)}><Icon name="ph-chart-line-up" size="sm" /><span>{L('نمایش روی چارت', 'Show on the chart')}</span></button> : null}
          </section>
          {related.length ? (
            <section className="news-related" dir={fa ? 'rtl' : 'ltr'}>
              <h3>{L('بیشتر از همین بازار', 'More on this market')}</h3>
              {related.map((n) => (
                <button key={n.id} type="button" className="card news-related-row" onClick={() => { setStory(n); window.scrollTo?.(0, 0); }}>
                  <b dir="auto">{n.title}</b><span className="news-meta">{n.source}{n.source ? ' · ' : ''}{faAgo(n.time)}</span>
                </button>
              ))}
            </section>
          ) : null}
        </article>
      </div>
    );
  }

  return (
    <div className="page-inner narrow">
      <Tabs value={tab} onChange={setTab} tabs={[
        { id: 'crypto', label: t('crypto') }, { id: 'forex', label: t('forex') }, { id: 'saved', label: t('newsSaved') },
        { id: 'announcements' as Tab, label: t('announcements') },
      ]} />
      {tab === 'announcements' ? <p className="muted small fa">{feed.data?.length ? L(`${faNum(feed.data.length)} اطلاعیه`, `${feed.data.length} announcements`) : L('آنچه سرویس رسماً اعلام کرده', 'What the service has said officially')}</p> : null}
      <div className="row">
        <button className={`chip${onlyHot ? ' on' : ''}`} onClick={() => setOnlyHot(!onlyHot)}><span className="fa">{t('highImpact')}</span></button>
        <input className="scr-search" value={q} onChange={(e) => setQ(e.target.value)} placeholder={t('search')} dir="auto" />
        <span className="ws-spacer" />
        <button className="link-btn" onClick={() => open('calendar')}>{t('calendar')}</button>
      </div>
      <SavedLine at={savedAt} className="on-page" />
      {tab === 'announcements' && !feed.loading && !feed.error && !list.length && !q.trim() && !onlyHot ? (
        <div className="empty fa ann-empty"><Icon name="ph-info" size="md" /><p>{L('تا امروز اطلاعیه‌ای منتشر نشده.', 'Nothing has been announced yet.')}</p><p className="muted small">{L('این فهرست خبر نیست و خودش پر نمی‌شود؛ وقتی پر می‌شود که ما چیزی برای گفتن داشته باشیم — قطعیِ سرویس، نسخه‌ی تازه، بازارِ تازه، تغییر در قواعد اشتراک. هرچه اینجا نوشته شود می‌ماند، حتی اگر اعلانش را از دست بدهید. خالی بودنش یعنی چیزی گفته نشده، نه اینکه چیزی خراب است.', 'This is not a feed and it does not fill itself; it fills when we have something to say — a service outage, a new version, a new market, a change to how membership works. Whatever is written here stays, even if you miss the notification. Its being empty means nothing has been said, not that something is broken.')}</p></div>
      ) : (
      <State loading={feed.loading} error={annError ?? feed.error} empty={!list.length} onRetry={feed.reload}>
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
      )}
    </div>
  );
}
