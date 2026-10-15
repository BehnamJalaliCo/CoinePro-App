import { useEffect, useMemo, useState } from 'react';
import { usePrefs, useT } from '../state/prefs';
import { articleBody, cryptoNews, forexNews, type NewsItem } from '../data/intel';
import { backendOf } from '../data/symbols';
import { Icon } from '../ui/Icon';
import { faAgo } from '../i18n/time';

// TradingView's news panel: newest first, the open symbol's market on top, one line of source and
// time under each headline. A crypto story opens in place with its full Persian text.
export function NewsPanel() {
  const t = useT();
  const symbol = usePrefs((s) => s.symbol);
  const [items, setItems] = useState<NewsItem[] | null>(null);
  const [failed, setFailed] = useState(false);
  const [tab, setTab] = useState<'market' | 'all'>('market');
  const [open, setOpen] = useState<NewsItem | null>(null);
  const [body, setBody] = useState<string | null>(null);
  const market = backendOf(symbol) === 'tradeyar' ? 'crypto' : 'forex';

  useEffect(() => {
    let alive = true;
    const load = () => Promise.allSettled([cryptoNews(40), forexNews()]).then((rs) => {
      if (!alive) return;
      const all = rs.flatMap((r) => (r.status === 'fulfilled' ? r.value : []));
      setFailed(rs.every((r) => r.status === 'rejected'));
      setItems(all.sort((a, b) => b.time - a.time));
    });
    void load();
    const id = setInterval(load, 120_000);
    return () => { alive = false; clearInterval(id); };
  }, []);

  useEffect(() => {
    setBody(null);
    if (open) void articleBody(open).then(setBody).catch(() => setBody(null));
  }, [open]);

  const shown = useMemo(() => (items ?? []).filter((n) => tab === 'all' || n.market === market), [items, tab, market]);

  if (open) {
    return (
      <section className="panel-generic news">
        <header className="panel-head">
          <button className="ib" onClick={() => setOpen(null)} aria-label={t('back')}><Icon name="ph-caret-left" size="sm" /></button>
          <span className="fa">{t('news')}</span>
        </header>
        <article className="panel-body article" dir="rtl">
          {open.image ? <img src={open.image} alt="" className="article-img" /> : null}
          <h3>{open.title}</h3>
          <div className="news-meta"><span>{open.source}</span> · <span>{faAgo(open.time)}</span></div>
          <p className="article-text">{body ?? open.summary}</p>
          {open.url ? <a className="link" href={open.url} target="_blank" rel="noopener noreferrer">{open.source}</a> : null}
        </article>
      </section>
    );
  }

  return (
    <section className="panel-generic news">
      <header className="panel-head"><span className="fa">{t('news')}</span></header>
      <nav className="chips panel-chips">
        <button className={`chip${tab === 'market' ? ' on' : ''}`} onClick={() => setTab('market')}><span className="fa">{t(market)}</span></button>
        <button className={`chip${tab === 'all' ? ' on' : ''}`} onClick={() => setTab('all')}><span className="fa">{t('all')}</span></button>
      </nav>
      <div className="panel-body">
        {items === null ? <div className="empty fa">{t('loading')}…</div> : null}
        {items !== null && !shown.length ? <div className="empty fa">{failed ? t('networkError') : t('noResults')}</div> : null}
        {shown.map((n) => (
          <button key={n.id} className={`news-row${n.importance >= 7 ? ' hot' : ''}`} onClick={() => setOpen(n)} dir="rtl">
            <span className="news-title">{n.title}</span>
            <span className="news-meta"><span>{n.source}</span> · <span>{faAgo(n.time)}</span></span>
          </button>
        ))}
      </div>
    </section>
  );
}
