import { useEffect, useMemo, useState } from 'react';
import { usePage } from '../state/page';
import { useSession } from '../data/session';
import { activeBackend, member, ty, num, ms, str, usePlatform } from '../data/member';
import { useMarket } from '../state/market';
import { useWatchlist } from '../state/watchlist';
import { useAlerts } from '../state/alerts';
import { usePanels } from '../state/panels';
import { usePrefs } from '../state/prefs';
import { useReplay } from '../state/replay';
import { useSaved } from '../data/saved';
import { SAVED_TICKERS } from '../data/prices';
import { activeSignals, type Signal } from '../data/signals';
import { formatPrice } from '../chart/format';
import { digitsOf } from '../state/market';
import { useOnline } from '../shell/Offline';
import { SymbolLogo, hasLogo } from '../ui/SymbolLogo';
import { Icon } from '../ui/Icon';
import { faNum } from '../i18n/time';
import { previousVisit } from '../state/visits';
import { Markdown } from './Markdown';
import { State, Tabs, dateTime, money, pct, useL, useLoad } from './kit';
import { VipCard } from '../vip/VipCard';
import { AvatarView, decodeAvatar } from './Avatar';
import { readProfile, PROFILE_KEY } from './profileStore';
import {
  FEED_LABEL, awayDays, awayHours, challengeFor, epochDay, feedState, homeMarkets, leanOf, localEpochDay, marketMood, moodIsEmpty,
  readArenaDays, arenaStreak, sinceLastVisit, type ChallengeSurface, type MarketMood,
} from './homeLoop';
import './home2.css';

// Home (feature/home HomeScreen.kt): the account at a glance. What happened while the reader was
// away and today's one task with the arena's streak; the balance with its holdings and the feed's
// state under it; the board's mood «بازار امروز»; the reader's own list first among six markets;
// the open signals; the day's briefing, the announcements and the ways into the rest. A reader
// with both platforms switches between them here. Nothing is invented: a card with nothing to say
// is not drawn.
export default function HomePage() {
  const L = useL();
  const fa = usePrefs((s) => s.lang) === 'fa';
  const n = (v: number) => (fa ? faNum(v) : String(v));
  const open = usePage((s) => s.open);
  const close = usePage((s) => s.close);
  const sessions = useSession((s) => s.sessions);
  const platform = usePlatform((s) => s.platform);
  const setPlatform = usePlatform((s) => s.set);
  const backend = activeBackend();
  const user = (backend && sessions[backend]?.user) || null;
  const [profile, setProfile] = useState(readProfile);
  useEffect(() => {
    const on = (e: StorageEvent) => { if (e.key === PROFILE_KEY) setProfile(readProfile()); };
    window.addEventListener('storage', on);
    return () => window.removeEventListener('storage', on);
  }, []);
  const briefing = useLoad(() => member<{ body?: string; generated_at?: number }>('briefing'), [backend]);
  const folio = useLoad(() => member<Record<string, unknown>>('portfolio'), [backend]);
  const news = useLoad(() => (sessions.tradeyar ? ty<{ announcements?: Record<string, unknown>[]; items?: Record<string, unknown>[] }>('api/mobile/v1/announcements?limit=5').then((r) => r.announcements ?? r.items ?? []) : Promise.resolve([])), [sessions.tradeyar]);
  const signals = useLoad(() => activeSignals().catch(() => [] as Signal[]), [sessions.tradeyar, sessions.coineprofx]);
  const total = folio.data?.total as { amount?: number; currency?: string } | undefined;
  const change = folio.data?.change as { amount?: number; percent?: number; period?: string } | undefined;
  const holdings = Array.isArray(folio.data?.holdings) ? (folio.data!.holdings as unknown[]).length : 0;
  const both = Boolean(sessions.tradeyar && sessions.coineprofx);
  const hidden = profile.hideBalance;
  const toggleHidden = () => { const next = { ...profile, hideBalance: !hidden }; setProfile(next); try { localStorage.setItem(PROFILE_KEY, JSON.stringify(next)); } catch { /* private window */ } };

  // The board, the reader's own list, and the return loop.
  const tickers = useMarket((s) => s.tickers);
  const watchlist = useWatchlist((s) => s.symbols);
  const toggleWatch = (s: string) => { const w = useWatchlist.getState(); if (w.symbols.includes(s)) w.remove(s); else w.add(s); };
  const log = useAlerts((s) => s.log);
  const covered = useMemo(() => Object.values(tickers).filter((t) => hasLogo(t.symbol)), [tickers]);
  const mood = useMemo(() => marketMood(covered), [covered]);
  const cards = useMemo(() => homeMarkets(covered.map((t) => t.symbol), watchlist), [covered, watchlist]);
  const now = useMemo(() => Date.now(), []);
  const last = previousVisit();
  const since = useMemo(() => sinceLastVisit(
    last, now,
    watchlist.flatMap((s) => { const t = tickers[s]; return t && Number.isFinite(t.changePct) ? [{ symbol: s, change: t.changePct / 100 }] : []; }),
    (signals.data ?? []).filter((x) => x.createdAt > last).length,
    log.filter((x) => x.at > last).length,
  ), [last, now, watchlist, tickers, signals.data, log]);
  const challenge = useMemo(() => challengeFor(epochDay(now), !fa), [now, fa]);
  const streak = useMemo(() => arenaStreak(readArenaDays(), localEpochDay(now)), [now]);
  const doChallenge = (s: ChallengeSurface) => {
    if (s === 'script') open('script');
    else if (s === 'alerts') open('alerts');
    else if (s === 'journal') open('journal');
    else if (s === 'paper') open('paper');
    else { close(); if (s === 'watchlist') usePanels.getState().show('watchlist'); if (s === 'arena') useReplay.getState().select(); }
  };
  const openSymbol = (s: string) => { usePrefs.getState().setSymbol(s); close(); };

  return (
    <div className="page-inner home">
      <div className="row home-greeting">
        <h2 className="fa">{L('سلام', 'Hi')}{profile.name || user?.name ? ` ${profile.name || user?.name}` : ''} 👋</h2>
        <span className="ws-spacer" />
        <button type="button" className="avatar-btn" onClick={() => open('profile')} aria-label={L('حساب', 'Account')}>
          <AvatarView base={decodeAvatar(profile.avatar)} ring={profile.ring} initial={(profile.name || user?.name || '?').charAt(0)} size={38} />
        </button>
      </div>
      {since || challenge ? (
        <div className="home-loop">
          {since ? (
            <section className="card home-since" data-card="since">
              <div className="row-between"><b className="fa">{L('وقتی نبودید', 'While you were away')}</b>
                <span className="muted small fa">{awayDays(since) >= 1 ? L(`${n(awayDays(since))} روز`, `${awayDays(since)} days`) : L(`${n(awayHours(since))} ساعت`, `${awayHours(since)} hours`)}</span></div>
              {since.movers.map((m) => (
                <button key={m.symbol} type="button" className="row-between since-row" onClick={() => openSymbol(m.symbol)}>
                  <span className="num">{m.symbol}</span><span className={`num ${m.change >= 0 ? 'up' : 'down'}`} dir="ltr">{pct(m.change * 100)}</span>
                </button>
              ))}
              {since.signals > 0 ? <span className="muted small fa">{L(`${n(since.signals)} سیگنال صادر شد`, `${since.signals} signals fired`)}</span> : null}
              {since.alerts > 0 ? <span className="muted small fa">{L(`${n(since.alerts)} هشدار شما به صدا درآمد`, `${since.alerts} of your alerts went off`)}</span> : null}
            </section>
          ) : null}
          <section className="card home-challenge" data-card="challenge" data-challenge={challenge.id}>
            <div className="row-between"><span className="muted small fa">{L('امروز', 'Today')}</span>
              {streak > 0 ? <span className="streak small fa">{L(`${n(streak)} روز پشت سر هم`, `${streak} days running`)}</span> : null}</div>
            <b className="fa">{challenge.title}</b>
            <button type="button" className="btn btn-quiet sm" onClick={() => doChallenge(challenge.surface)}>{L('انجامش بدهید', 'Do it')}</button>
          </section>
        </div>
      ) : null}
      {both ? <Tabs value={platform} onChange={setPlatform} tabs={[{ id: 'tradeyar', label: L('رمزارز', 'Crypto') }, { id: 'coineprofx', label: L('فارکس', 'Forex') }]} /> : null}
      <div className="split">
        <div className="grow stack">
          <section className="card home-hero" data-card="balance">
            <div className="row"><span className="muted fa">{L('دارایی کل', 'Total balance')}</span>
              <button type="button" className="ib" onClick={toggleHidden} aria-label={hidden ? L('نمایش موجودی', 'Show balance') : L('پنهان کردن موجودی', 'Hide balance')} aria-pressed={hidden}><Icon name={hidden ? 'ph-eye-slash' : 'ph-eye'} size="sm" /></button></div>
            <State loading={folio.loading} error={null}>
              {num(total?.amount) != null ? (
                <>
                  <b className="big num" dir="ltr">{hidden ? '••••••' : `${money(num(total?.amount))} ${total?.currency ?? ''}`}</b>
                  <div className={`num change-pill ${hidden ? 'muted' : (change?.amount ?? 0) >= 0 ? 'up' : 'down'}`} dir="ltr">{hidden ? '••••' : `${money(num(change?.amount))} · ${pct(num(change?.percent))} ${change?.period ?? ''}`}</div>
                </>
              ) : (
                <><b className="big muted">—</b><span className="muted small fa">{L('هنوز موجودی‌ای دریافت نشده.', 'No balance has arrived yet.')}</span></>
              )}
              {holdings > 0 ? <button className="link-btn fa holdings-line" onClick={() => open('portfolio')}>{L(`${n(holdings)} دارایی · پرتفوی`, `${holdings} holdings · portfolio`)}</button> : null}
            </State>
            <FeedRow />
          </section>
          {!moodIsEmpty(mood) ? <MoodStrip mood={mood} onOpen={openSymbol} /> : null}
          <section className="card home-markets" data-card="markets">
            <h3 className="fa">{L('بازار', 'Market')}</h3>
            {cards.rows.length ? cards.rows.map((s) => {
              const t = tickers[s];
              return (
                <div key={s} className="home-mrow" data-symbol={s}>
                  <button type="button" className="lt star" onClick={() => toggleWatch(s)} aria-pressed={watchlist.includes(s)} aria-label={s}><Icon name={watchlist.includes(s) ? 'ph-star-fill' : 'ph-star'} size="sm" /></button>
                  <button type="button" className="home-mrow-main" onClick={() => openSymbol(s)}>
                    <SymbolLogo symbol={s} size={22} /><span className="num sym">{s}</span>
                    <span className="ws-spacer" />
                    <span className="num" dir="ltr">{t ? formatPrice(t.last, digitsOf(s)) : '—'}</span>
                    <span className={`num chg ${t && t.changePct >= 0 ? 'up' : 'down'}`} dir="ltr">{t ? pct(t.changePct) : ''}</span>
                  </button>
                </div>
              );
            }) : <p className="muted fa">{L('هنوز داده‌ای از بازار نرسیده است.', 'No market data has arrived yet.')}</p>}
            {cards.more > 0 ? <button type="button" className="link-btn fa" onClick={() => open('markets')}>{L(`${n(cards.more)} بازار دیگر`, `${cards.more} more markets`)} ›</button> : null}
          </section>
          {signals.data?.length ? (
            <section className="card home-signals" data-card="signals">
              <h3 className="fa">{L('سیگنال‌های باز', 'Open signals')}</h3>
              {signals.data.slice(0, 5).map((x) => (
                <button key={`${x.backend}-${x.id}`} type="button" className="home-signal" onClick={() => open('signal', `${x.backend ?? 'tradeyar'}-${x.id}`)}>
                  <b className="num">{x.symbol} <span className={x.direction === 'long' ? 'up' : 'down'}>{x.direction === 'long' ? L('خرید', 'Buy') : L('فروش', 'Sell')}</span></b>
                  <span className="muted small fa">{L(`ورود ${lvl(x.entry, x.symbol)} · حد ضرر ${lvl(x.sl, x.symbol)} · هدف ${lvl(x.targets[0], x.symbol)}`, `Entry ${lvl(x.entry, x.symbol)} · Stop ${lvl(x.sl, x.symbol)} · Target ${lvl(x.targets[0], x.symbol)}`)}</span>
                </button>
              ))}
            </section>
          ) : null}
          <section className="card">
            <h3 className="fa">{L('خلاصه‌ی امروز', "Today's briefing")}</h3>
            <State loading={briefing.loading} error={briefing.error} onRetry={briefing.reload} empty={!briefing.data?.body}>
              {briefing.data?.body ? <><Markdown source={briefing.data.body} /><p className="muted small num">{dateTime(ms(briefing.data.generated_at))}</p></> : null}
            </State>
          </section>
          <section className="card">
            <h3 className="fa">{L('اطلاعیه‌ها', 'Announcements')}</h3>
            <State loading={news.loading} error={news.error} empty={!news.data?.length}>
              {(news.data ?? []).map((x) => (
                <a key={str(x.id)} className="news-line" href={str(x.url) || undefined} target="_blank" rel="noreferrer">
                  <b className="fa" dir="auto">{str(x.title)}</b><span className="muted fa" dir="auto">{str(x.summary)}</span><span className="muted small num">{dateTime(ms(x.published_at))}</span>
                </a>
              ))}
            </State>
          </section>
        </div>
        <aside className="stack side">
          <VipCard />
          <section className="card doors">
            {([['signals', L('سیگنال‌ها', 'Signals')], ['ai', L('ستاپ‌ساز هوشمند', 'AI setup builder')], ['activity', L('فعالیت', 'Activity')], ['alerts', L('هشدارها', 'Alerts')], ['journal', L('ژورنال', 'Journal')], ['academy', L('آکادمی', 'Academy')], ['news', L('اخبار', 'News')], ['tools', L('جعبه‌ابزار', 'Tools')], ['vip-status', L('وضعیت VIP', 'VIP status')], ['copytrade', L('کپی‌ترید', 'Copy trading')], ['performance', L('عملکرد', 'Performance')], ['affiliate', L('معرفی دوستان', 'Referrals')], ['profile', L('پروفایل', 'Profile')]] as const).map(([id, label]) => (
              <button key={id} className="btn btn-quiet sm" onClick={() => open(id)}><span className="fa">{label}</span></button>
            ))}
          </section>
        </aside>
      </div>
    </div>
  );
}

const lvl = (v: number | null | undefined, symbol: string) => (v == null || !Number.isFinite(v) ? '—' : `⁦${formatPrice(v, digitsOf(symbol))}⁩`);

/** The feed's state under the balance: a dot, a word, and how old a saved picture is (ConnectionRow). */
function FeedRow() {
  const L = useL();
  const liveAt = useMarket((s) => s.liveAt);
  const savedAt = useSaved((s) => s.at[SAVED_TICKERS.crypto] ?? s.at[SAVED_TICKERS.fx] ?? null);
  const online = useOnline();
  const [now, setNow] = useState(Date.now());
  useEffect(() => { const id = window.setInterval(() => setNow(Date.now()), 5000); return () => window.clearInterval(id); }, []);
  const state = feedState({ liveAt, savedAt, online, now });
  const tone = state === 'live' ? 'up' : state === 'offline' ? 'down' : state === 'connecting' ? 'muted' : 'warn';
  const stored = savedAt ? new Intl.DateTimeFormat(L('fa-IR-u-ca-persian', 'en-GB'), { month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit' }).format(new Date(savedAt)) : null;
  return (
    <div className="feed-row" data-feed={state}>
      <span className={`feed-dot ${tone}`} aria-hidden /><span className="muted small fa">{L(...FEED_LABEL[state])}</span>
      {(state === 'cached' || state === 'cachedRefreshing') ? <span className="muted small fa">{stored ? L(`ذخیره‌شده در ${stored} · مقادیر لحظه‌ای نیستند`, `Stored ${stored} · values are not live`) : L('تصویر ذخیره‌شده‌ی بازار · زمان نامشخص', 'Stored market snapshot · age unknown')}</span> : null}
    </div>
  );
}

/** «بازار امروز»: the board's lean, its split bar and counts, the biggest moves, the busy and moving. */
function MoodStrip({ mood, onOpen }: { mood: MarketMood; onOpen(s: string): void }) {
  const L = useL();
  const fa = usePrefs((s) => s.lang) === 'fa';
  const n = (v: number) => (fa ? faNum(v) : String(v));
  const lean = leanOf(mood.breadth);
  const chips = (heading: string, list: MarketMood['movers']) => (
    <div className="mood-row">
      <span className="muted small fa">{heading}</span>
      <div className="mood-chips">
        {list.map((t) => (
          <button key={t.symbol} type="button" className="mood-chip" onClick={() => onOpen(t.symbol)}>
            <span className="num" dir="ltr">{t.symbol}</span>
            {Number.isFinite(t.changePct) ? <span className={`num ${t.changePct >= 0 ? 'up' : 'down'}`} dir="ltr">{pct(t.changePct)}</span> : null}
          </button>
        ))}
      </div>
    </div>
  );
  return (
    <section className="card mood" data-card="mood" aria-label="market-mood">
      {mood.breadth != null ? (
        <div className="mood-breadth">
          <div className="row-between"><span className="muted small fa">{L('بازار امروز', 'The board today')}</span>
            <b className={`fa ${lean === 'up' ? 'up' : lean === 'down' ? 'down' : 'muted'}`}>{lean === 'up' ? L('بیشتر صعودی', 'Mostly up') : lean === 'down' ? L('بیشتر نزولی', 'Mostly down') : L('مختلط', 'Mixed')}</b></div>
          <div className="mood-bar" aria-hidden><span className="up-part" style={{ flexGrow: Math.min(99, Math.max(1, mood.breadth)) }} /><span className="down-part" style={{ flexGrow: Math.min(99, Math.max(1, 100 - mood.breadth)) }} /></div>
          <span className="muted small fa">{L(`${n(mood.advancing)} بازار بالا، ${n(mood.declining)} پایین`, `${mood.advancing} markets up, ${mood.declining} down`)}</span>
        </div>
      ) : null}
      {mood.movers.length ? chips(L('بیشترین حرکت', 'Biggest moves'), mood.movers) : null}
      {mood.unusual.length ? chips(L('شلوغ و در حرکت', 'Busy and moving'), mood.unusual) : null}
    </section>
  );
}
