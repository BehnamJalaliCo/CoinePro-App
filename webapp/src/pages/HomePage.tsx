import { usePage } from '../state/page';
import { useSession } from '../data/session';
import { activeBackend, member, ty, num, ms, str, usePlatform } from '../data/member';
import { Markdown } from './Markdown';
import { State, Tabs, dateTime, money, pct, useL, useLoad } from './kit';
import { VipCard } from '../vip/VipCard';

// Home (feature/home): the account at a glance — the day's briefing the platform writes, the
// portfolio's value and change, the announcements, and the ways into the rest. A reader with both
// platforms switches between them here, as in the app.
export default function HomePage() {
  const L = useL();
  const open = usePage((s) => s.open);
  const sessions = useSession((s) => s.sessions);
  const platform = usePlatform((s) => s.platform);
  const setPlatform = usePlatform((s) => s.set);
  const backend = activeBackend();
  const user = (backend && sessions[backend]?.user) || null;
  const briefing = useLoad(() => member<{ body?: string; generated_at?: number }>('briefing'), [backend]);
  const folio = useLoad(() => member<Record<string, unknown>>('portfolio'), [backend]);
  const news = useLoad(() => (sessions.tradeyar ? ty<{ announcements?: Record<string, unknown>[]; items?: Record<string, unknown>[] }>('api/mobile/v1/announcements?limit=5').then((r) => r.announcements ?? r.items ?? []) : Promise.resolve([])), [sessions.tradeyar]);
  const total = folio.data?.total as { amount?: number; currency?: string } | undefined;
  const change = folio.data?.change as { amount?: number; percent?: number; period?: string } | undefined;
  const both = Boolean(sessions.tradeyar && sessions.coineprofx);

  return (
    <div className="page-inner home">
      <div className="row">
        <h2 className="fa">{L('سلام', 'Hello')}{user?.name ? ` ${user.name}` : ''}</h2>
        <span className="ws-spacer" />
        {both ? <Tabs value={platform} onChange={setPlatform} tabs={[{ id: 'tradeyar', label: L('رمزارز', 'Crypto') }, { id: 'coineprofx', label: L('فارکس', 'Forex') }]} /> : null}
      </div>
      <div className="split">
        <div className="grow stack">
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
          <section className="card">
            <h3 className="fa">{L('پرتفوی', 'Portfolio')}</h3>
            <State loading={folio.loading} error={folio.error} onRetry={folio.reload}>
              <b className="big num">{money(num(total?.amount))} {total?.currency ?? ''}</b>
              <div className={`num ${(change?.amount ?? 0) >= 0 ? 'up' : 'down'}`}>{money(num(change?.amount))} · {pct(num(change?.percent))} {change?.period ?? ''}</div>
              <button className="link-btn" onClick={() => open('portfolio')}>{L('جزئیات پرتفوی', 'Portfolio details')}</button>
            </State>
          </section>
          <section className="card doors">
            {([['signals', L('سیگنال‌ها', 'Signals')], ['ai', L('ستاپ‌ساز هوشمند', 'AI setup builder')], ['activity', L('فعالیت', 'Activity')], ['alerts', L('هشدارها', 'Alerts')], ['journal', L('ژورنال', 'Journal')], ['academy', L('آکادمی', 'Academy')], ['vip-status', L('وضعیت VIP', 'VIP status')], ['copytrade', L('کپی‌ترید', 'Copy trading')], ['performance', L('عملکرد', 'Performance')], ['affiliate', L('معرفی دوستان', 'Referrals')], ['profile', L('پروفایل', 'Profile')]] as const).map(([id, label]) => (
              <button key={id} className="btn btn-quiet sm" onClick={() => open(id)}><span className="fa">{label}</span></button>
            ))}
          </section>
        </aside>
      </div>
    </div>
  );
}
