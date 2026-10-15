// Every screen of the app that is not the chart, grouped as the app's menu groups them
// (feature/menu MenuCatalogue.kt), each loaded only when opened.
import { lazy, type ComponentType, type LazyExoticComponent } from 'react';

export type PageId =
  | 'markets' | 'heatmap' | 'screener' | 'news' | 'calendar'
  | 'portfolio' | 'paper' | 'journal' | 'connections' | 'trade'
  | 'backtest' | 'script' | 'alerts' | 'tools' | 'vision' | 'ai' | 'signals' | 'signal' | 'assistant'
  | 'academy' | 'lesson' | 'community' | 'thread' | 'help' | 'safety' | 'terms' | 'privacy'
  | 'home' | 'profile' | 'membership' | 'verify' | 'activity' | 'notifications' | 'delete' | 'pro' | 'reset';

/** What every page receives: the address's extra segment, and a way to ask the reader to sign in. */
export interface PageProps { param: string | null; onSignIn(): void }

export type MenuGroup = 'market' | 'trade' | 'analysis' | 'learn' | 'account';

export interface PageDef {
  id: PageId;
  /** The address segment: /next/<path>[/<param>]. */
  path: string;
  fa: string;
  en: string;
  icon: string;
  group: MenuGroup | null;
  member?: boolean;
  component: LazyExoticComponent<ComponentType<PageProps>>;
}

const P = (loader: () => Promise<{ default: ComponentType<PageProps> }>) => lazy(loader);

export const PAGES: PageDef[] = [
  { id: 'markets', path: 'markets', fa: 'بازارها', en: 'Markets', icon: 'ph-squares-four', group: 'market', component: P(() => import('./MarketsPage')) },
  { id: 'heatmap', path: 'heatmap', fa: 'هیت‌مپ بازار', en: 'Heat map', icon: 'ph-columns', group: 'market', component: P(() => import('./HeatmapPage')) },
  { id: 'screener', path: 'screener', fa: 'اسکرینر', en: 'Screener', icon: 'ph-funnel', group: 'market', component: P(() => import('./ScreenerPage')) },
  { id: 'news', path: 'market/news', fa: 'اخبار بازار', en: 'News', icon: 'ph-newspaper', group: 'market', component: P(() => import('./NewsPage')) },
  { id: 'calendar', path: 'market/calendar', fa: 'تقویم اقتصادی', en: 'Economic calendar', icon: 'ph-calendar-blank', group: 'market', component: P(() => import('./CalendarPage')) },

  { id: 'portfolio', path: 'portfolio', fa: 'پرتفوی', en: 'Portfolio', icon: 'ph-chart-bar', group: 'trade', member: true, component: P(() => import('./PortfolioPage')) },
  { id: 'paper', path: 'paper-trade', fa: 'معامله‌ی آزمایشی', en: 'Paper trading', icon: 'ph-flask', group: 'trade', component: P(() => import('./PaperPage')) },
  { id: 'journal', path: 'journal', fa: 'ژورنال معاملات', en: 'Trade journal', icon: 'ph-pencil-simple', group: 'trade', component: P(() => import('./JournalPage')) },
  { id: 'trade', path: 'trade', fa: 'معامله‌ی زنده', en: 'Live trading', icon: 'ph-lightning', group: 'trade', member: true, component: P(() => import('./TradePage')) },
  { id: 'connections', path: 'connections', fa: 'اتصال حساب', en: 'Connections', icon: 'ph-share-network', group: 'trade', member: true, component: P(() => import('./ConnectionsPage')) },

  { id: 'backtest', path: 'backtest', fa: 'بک‌تست', en: 'Backtest', icon: 'ph-rewind', group: 'analysis', component: P(() => import('./BacktestPage')) },
  { id: 'script', path: 'script', fa: 'اسکریپت‌نویسی (نمااسکریپت)', en: 'NamaScript', icon: 'ph-code', group: 'analysis', component: P(() => import('./ScriptPage')) },
  { id: 'alerts', path: 'alerts', fa: 'مرکز هشدار', en: 'Alert center', icon: 'ph-bell', group: 'analysis', component: P(() => import('./AlertsPage')) },
  { id: 'tools', path: 'tools', fa: 'جعبه‌ابزار تریدر', en: 'Trader tools', icon: 'ph-target', group: 'analysis', component: P(() => import('./ToolsPage')) },
  { id: 'vision', path: 'ai/vision', fa: 'تحلیل تصویر چارت', en: 'Chart vision', icon: 'ph-camera', group: 'analysis', member: true, component: P(() => import('./VisionPage')) },
  { id: 'ai', path: 'ai', fa: 'ستاپ‌ساز هوشمند', en: 'AI setup builder', icon: 'ph-brain', group: 'analysis', member: true, component: P(() => import('./AiSetupPage')) },
  { id: 'signals', path: 'signals', fa: 'سیگنال‌ها', en: 'Signals', icon: 'ph-lightning', group: 'analysis', component: P(() => import('./SignalsPage')) },
  { id: 'signal', path: 'signal', fa: 'سیگنال', en: 'Signal', icon: 'ph-lightning', group: null, component: P(() => import('./SignalPage')) },
  { id: 'assistant', path: 'ai/assistant', fa: 'دستیار', en: 'Assistant', icon: 'ph-robot', group: 'analysis', member: true, component: P(() => import('./AssistantPage')) },

  { id: 'academy', path: 'academy', fa: 'آکادمی', en: 'Academy', icon: 'ph-crown', group: 'learn', member: true, component: P(() => import('./AcademyPage')) },
  { id: 'lesson', path: 'academy/lesson', fa: 'درس', en: 'Lesson', icon: 'ph-crown', group: null, member: true, component: P(() => import('./LessonPage')) },
  { id: 'community', path: 'community', fa: 'انجمن', en: 'Community', icon: 'ph-chat-circle', group: 'learn', component: P(() => import('./CommunityPage')) },
  { id: 'thread', path: 'community/post', fa: 'گفتگو', en: 'Thread', icon: 'ph-chat-circle', group: null, component: P(() => import('./ThreadPage')) },
  { id: 'help', path: 'tutorials', fa: 'آموزش‌ها', en: 'Tutorials', icon: 'ph-question', group: 'learn', component: P(() => import('./HelpPage')) },
  { id: 'safety', path: 'launch-readiness', fa: 'ایمنی و نسخه', en: 'Safety and version', icon: 'ph-info', group: 'learn', component: P(() => import('./SafetyPage')) },
  { id: 'terms', path: 'legal/terms', fa: 'قوانین استفاده', en: 'Terms of use', icon: 'ph-info', group: null, component: P(() => import('./LegalPage')) },
  { id: 'privacy', path: 'legal/privacy', fa: 'حریم خصوصی', en: 'Privacy', icon: 'ph-info', group: null, component: P(() => import('./LegalPage')) },

  { id: 'home', path: 'home', fa: 'خانه و خلاصه‌ی حساب', en: 'Home', icon: 'ph-user-circle', group: 'account', member: true, component: P(() => import('./HomePage')) },
  { id: 'profile', path: 'profile', fa: 'پروفایل و تنظیمات', en: 'Profile and settings', icon: 'ph-user-circle', group: 'account', component: P(() => import('./ProfilePage')) },
  { id: 'membership', path: 'membership', fa: 'عضویت صرافی', en: 'Exchange membership', icon: 'ph-crown', group: 'account', member: true, component: P(() => import('./MembershipPage')) },
  { id: 'verify', path: 'account/verify', fa: 'احراز هویت', en: 'Verification', icon: 'ph-check', group: 'account', member: true, component: P(() => import('./VerifyPage')) },
  { id: 'activity', path: 'activity', fa: 'فعالیت', en: 'Activity', icon: 'ph-bell-ringing', group: 'account', member: true, component: P(() => import('./ActivityPage')) },
  { id: 'notifications', path: 'notifications', fa: 'تنظیمات اعلان', en: 'Notification settings', icon: 'ph-bell', group: 'account', component: P(() => import('./NotificationsPage')) },
  { id: 'pro', path: 'pro', fa: 'پرو چارت پرمیوم', en: 'Pro Chart Premium', icon: 'ph-crown', group: 'account', component: P(() => import('./ProPage')) },
  { id: 'delete', path: 'account/delete', fa: 'حذف حساب', en: 'Delete account', icon: 'ph-trash', group: 'account', member: true, component: P(() => import('./DeleteAccountPage')) },
  { id: 'reset', path: 'reset', fa: 'بازیابی رمز', en: 'Reset password', icon: 'ph-sign-in', group: null, component: P(() => import('./ResetPage')) },
];

export const GROUPS: { id: MenuGroup; fa: string; en: string }[] = [
  { id: 'market', fa: 'بازار و دیده‌بان', en: 'Market and watchlist' },
  { id: 'trade', fa: 'معامله و سبد', en: 'Trade and portfolio' },
  { id: 'analysis', fa: 'تحلیل و ابزار', en: 'Analysis and tools' },
  { id: 'learn', fa: 'آموزش و برنامه', en: 'Learn and the app' },
  { id: 'account', fa: 'حساب من', en: 'My account' },
];

export const pageById = (id: PageId) => PAGES.find((p) => p.id === id)!;

/** The page an address names, longest path first: /next/academy/lesson/<slug> before /next/academy. */
export function pageFromPath(rest: string): { id: PageId; param: string | null } | null {
  const clean = rest.replace(/^\/+|\/+$/g, '');
  const sorted = [...PAGES].sort((a, b) => b.path.length - a.path.length);
  for (const p of sorted) {
    if (clean === p.path) return { id: p.id, param: null };
    if (clean.startsWith(`${p.path}/`)) {
      const param = clean.slice(p.path.length + 1);
      if (/^[A-Za-z0-9._~-]{1,128}$/.test(param)) return { id: p.id, param };
    }
  }
  // The terminal's old shared-script form /s/<id>.
  const s = /^s\/([A-Za-z0-9_-]{1,64})$/.exec(clean);
  if (s) return { id: 'script', param: s[1]! };
  return null;
}
