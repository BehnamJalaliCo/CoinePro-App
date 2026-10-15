// «پنل کاربری» — the reader's own panel on the web (/next/panel/<section>): who they are on both
// platforms, their trading and what the chart sent, their VIP copy trading, security and live web
// sessions, the cloud workspace, the inbox, support, billing, connections, and their data. Laid out
// as TradingView's account settings are: the sections at the side, one at a time beside them.
import { createContext, lazy, Suspense, useContext, useEffect, useRef, type ComponentType } from 'react';
import { usePage } from '../state/page';
import { usePrefs } from '../state/prefs';
import { useSession } from '../data/session';
import { events, getMe, telemetryOn, type PanelMe } from '../data/panel';
import { useL, useLoad } from '../pages/kit';
import type { PageProps } from '../pages/registry';
import { Icon } from '../ui/Icon';
import { Note } from './ui';
import './usercp.css';

export type SectionId = 'overview' | 'trading' | 'copytrade' | 'security' | 'workspace' | 'inbox' | 'support' | 'billing' | 'connections' | 'privacy';

interface SectionDef { id: SectionId; fa: string; en: string; icon: string; group: 'account' | 'trade' | 'help'; component: ComponentType }
const S = (loader: () => Promise<{ default: ComponentType }>) => lazy(loader);

export const SECTIONS: SectionDef[] = [
  { id: 'overview', fa: 'نمای کلی', en: 'Overview', icon: 'ph-squares-four', group: 'account', component: S(() => import('./Overview')) },
  { id: 'security', fa: 'امنیت و نشست‌ها', en: 'Security and sessions', icon: 'ph-shield-check', group: 'account', component: S(() => import('./Security')) },
  { id: 'workspace', fa: 'فضای کار ابری', en: 'Cloud workspace', icon: 'ph-cloud-check', group: 'account', component: S(() => import('./Workspace')) },
  { id: 'inbox', fa: 'اعلان‌ها', en: 'Notifications', icon: 'ph-bell', group: 'account', component: S(() => import('./Inbox')) },
  { id: 'trading', fa: 'معاملات', en: 'Trading', icon: 'ph-chart-line-up', group: 'trade', component: S(() => import('./Trading')) },
  { id: 'copytrade', fa: 'کپی‌ترید VIP', en: 'VIP copy trading', icon: 'ph-crown', group: 'trade', component: S(() => import('./CopyTrade')) },
  { id: 'connections', fa: 'اتصال صرافی', en: 'Exchange connections', icon: 'ph-plugs-connected', group: 'trade', component: S(() => import('./Connections')) },
  { id: 'billing', fa: 'اشتراک و پرداخت', en: 'Plans and billing', icon: 'ph-credit-card', group: 'trade', component: S(() => import('./Billing')) },
  { id: 'support', fa: 'پشتیبانی', en: 'Support', icon: 'ph-lifebuoy', group: 'help', component: S(() => import('./Support')) },
  { id: 'privacy', fa: 'داده‌ها و حریم خصوصی', en: 'Data and privacy', icon: 'ph-database', group: 'help', component: S(() => import('./Privacy')) },
];

const GROUPS: { id: SectionDef['group']; fa: string; en: string }[] = [
  { id: 'account', fa: 'حساب', en: 'Account' },
  { id: 'trade', fa: 'معامله و اشتراک', en: 'Trading and plans' },
  { id: 'help', fa: 'پشتیبانی و داده', en: 'Help and data' },
];

export const sectionOf = (param: string | null): SectionId => (SECTIONS.some((s) => s.id === param) ? (param as SectionId) : 'overview');

/** The panel's own account (from the panel server) for every section, and whether it answered. */
interface PanelContext { me: PanelMe | null; loading: boolean; down: string | null; reload(): void }
const Ctx = createContext<PanelContext>({ me: null, loading: true, down: null, reload: () => undefined });
export const usePanel = () => useContext(Ctx);

export default function PanelPage({ param, onSignIn }: PageProps) {
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const open = usePage((s) => s.open);
  const sessions = useSession((s) => s.sessions);
  const signed = Boolean(sessions.tradeyar || sessions.coineprofx);
  const section = sectionOf(param);
  const me = useLoad(() => (signed ? getMe() : Promise.resolve(null)), [signed]);
  const def = SECTIONS.find((s) => s.id === section)!;
  const Body = def.component;
  const user = sessions.tradeyar?.user ?? sessions.coineprofx?.user ?? null;
  const display = me.data?.profile.display_name || user?.name || me.data?.aliases[0]?.name || user?.email || L('حساب من', 'My account');
  const email = user?.email || me.data?.aliases.find((a) => a.email)?.email || '';
  const avatar = me.data?.profile.avatar ?? '';

  // Which sections the reader opens, as one of the page's feature lines.
  useEffect(() => { if (signed && telemetryOn()) events.push('page', `panel/${section}`); }, [section, signed]);
  // On a phone the sections are a row that scrolls; the open one is kept in view.
  const nav = useRef<HTMLElement>(null);
  useEffect(() => { nav.current?.querySelector<HTMLElement>('.ucp-nav-item.on')?.scrollIntoView?.({ block: 'nearest', inline: 'nearest' }); }, [section, signed]);

  const down = signed && !me.loading && !me.data
    ? L('سرور پنل الان در دسترس نیست؛ بخش‌هایی که از خود پلتفرم‌ها می‌آیند کار می‌کنند.', 'The panel server is not reachable now; the parts that come from the platforms themselves still work.')
    : null;

  if (!signed) {
    return (
      <div className="ucp-gate">
        <Icon name="ph-user-circle" size="lg" />
        <h2>{L('پنل کاربری', 'Your panel')}</h2>
        <p>{L('برای دیدن حساب، معاملات، نشست‌ها و فضای کار ابری وارد شوید.', 'Sign in to see your account, trading, sessions and cloud workspace.')}</p>
        <button className="btn btn-primary" onClick={onSignIn}>{L('ورود', 'Sign in')}</button>
      </div>
    );
  }

  return (
    <Ctx.Provider value={{ me: me.data, loading: me.loading, down: me.data ? null : me.error, reload: me.reload }}>
      <div className="ucp">
        <aside className="ucp-nav" ref={nav} aria-label={L('بخش‌های پنل', 'Panel sections')}>
          <div className="ucp-me">
            <span className="ucp-avatar">{avatar.startsWith('data:') ? <img src={avatar} alt="" /> : avatar || display.trim().charAt(0).toUpperCase()}</span>
            <div><b dir="auto">{display}</b>{email ? <span className="num">{email}</span> : null}</div>
          </div>
          {GROUPS.map((g) => (
            <div key={g.id} className="ucp-nav-group">
              <span className="ucp-nav-title">{lang === 'fa' ? g.fa : g.en}</span>
              {SECTIONS.filter((s) => s.group === g.id).map((s) => (
                <button key={s.id} className={`ucp-nav-item${s.id === section ? ' on' : ''}`} aria-current={s.id === section ? 'page' : undefined} onClick={() => open('panel', s.id === 'overview' ? null : s.id)}>
                  <Icon name={s.icon} size="sm" />
                  <span>{lang === 'fa' ? s.fa : s.en}</span>
                  {s.id === 'inbox' && me.data?.unread ? <em className="ucp-badge num">{me.data.unread}</em> : null}
                  {s.id === 'support' && me.data?.answered_tickets ? <em className="ucp-badge num">{me.data.answered_tickets}</em> : null}
                </button>
              ))}
            </div>
          ))}
        </aside>
        <main className="ucp-main" data-section={section}>
          {down ? <Note tone="warn" icon="ph-warning">{down}</Note> : null}
          <Suspense fallback={<div className="ucp-blank">{L('در حال بارگذاری', 'Loading')}…</div>}>
            <Body />
          </Suspense>
        </main>
      </div>
    </Ctx.Provider>
  );
}
