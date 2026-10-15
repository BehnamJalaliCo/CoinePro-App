// Words for what the panel server records, in both languages.
import type { Kind, PanelEvent, TicketStatus } from '../data/panel';

type L = (fa: string, en: string) => string;

export const kindLabel = (k: Kind, L: L): string => ({
  layouts: L('چیدمان‌ها و قالب‌های اندیکاتور', 'Layouts and indicator templates'),
  settings: L('تنظیمات چارت و ظاهر', 'Chart settings and appearance'),
  drawings: L('ترسیم‌ها', 'Drawings'),
  templates: L('اندیکاتورها و اسکریپت‌ها', 'Indicators and scripts'),
  watchlist: L('دیده‌بان‌ها و فیلترها', 'Watchlists and screens'),
} as const)[k];

export const actionLabel = (a: string, L: L): string => ({
  order: L('سفارش تازه', 'New order'), amend: L('ویرایش سفارش', 'Order amended'), cancel: L('لغو سفارش', 'Order cancelled'),
  close: L('بستن پوزیشن', 'Position closed'), protection: L('حد سود و ضرر', 'Stop and target'), leverage: L('تغییر اهرم', 'Leverage changed'),
  execution: L('اجرای سیگنال', 'Signal executed'), execution_close: L('بستن اجرای سیگنال', 'Execution closed'),
  venue_connect: L('اتصال صرافی', 'Exchange connected'), venue_disconnect: L('قطع اتصال صرافی', 'Exchange disconnected'),
} as Record<string, string>)[a] ?? a;

export const deviceLabel = (d: string | null | undefined, L: L): string => ({ phone: L('گوشی', 'Phone'), tablet: L('تبلت', 'Tablet'), desktop: L('رایانه', 'Computer') } as Record<string, string>)[d ?? ''] ?? (d || '—');

export const methodLabel = (m: string, L: L): string => ({
  password: L('ایمیل و رمز', 'Email and password'), google: 'Google', telegram: L('تلگرام', 'Telegram'), register: L('ثبت‌نام', 'Registration'),
  link: L('پیوند حساب فارکس', 'Forex account link'), page: L('از همین صفحه', 'From this page'), logout: L('خروج', 'Sign-out'),
  reader: L('توسط خودتان', 'By you'), owner: L('توسط پشتیبانی', 'By support'),
} as Record<string, string>)[m] ?? m;

export function eventLabel(e: PanelEvent, L: L): string {
  switch (e.kind) {
    case 'signin': return `${L('ورود', 'Signed in')} · ${methodLabel(e.name, L)}`;
    case 'signout': return L('خروج', 'Signed out');
    case 'signin_failed': return L('تلاش ناموفق برای ورود', 'Failed sign-in attempt');
    case 'refresh_failed': return L('پایان نشست', 'Session ended');
    case 'session_revoked': return `${L('بستن نشست', 'Session closed')} · ${methodLabel(e.name, L)}`;
    case 'trade': {
      const d = e.detail ?? {};
      return `${actionLabel(e.name, L)}${d.symbol ? ` · ${String(d.symbol)}` : ''}${d.side ? ` ${String(d.side).toUpperCase()}` : ''}`;
    }
    case 'feature': return featureLabel(e.name, L);
    case 'page': return `${L('صفحه', 'Page')} · ${e.name}`;
    default: return e.name || e.kind;
  }
}

export function featureLabel(name: string, L: L): string {
  if (name.startsWith('workspace.save.')) return L('ذخیره در فضای کار ابری', 'Saved to the cloud workspace');
  if (name.startsWith('workspace.restore.')) return L('بازگردانی از فضای کار ابری', 'Restored from the cloud workspace');
  return ({
    'profile.save': L('نمایه ذخیره شد', 'Profile saved'), 'ticket.open': L('تیکت پشتیبانی باز شد', 'Support ticket opened'),
    'data.export': L('خروجی داده‌ها', 'Data exported'),
  } as Record<string, string>)[name] ?? name;
}

export const ticketStatus = (s: TicketStatus, L: L): [string, 'warn' | 'good' | 'muted'] =>
  s === 'open' ? [L('باز', 'Open'), 'warn'] : s === 'answered' ? [L('پاسخ داده شد', 'Answered'), 'good'] : [L('بسته', 'Closed'), 'muted'];

export const categoryLabel = (c: string, L: L): string => ({
  account: L('حساب و ورود', 'Account and sign-in'), billing: L('اشتراک و پرداخت', 'Plans and billing'), trading: L('معامله و صرافی', 'Trading and exchange'),
  bug: L('خطا در برنامه', 'Something is broken'), idea: L('پیشنهاد', 'Suggestion'), other: L('سایر', 'Other'),
} as Record<string, string>)[c] ?? c;

/** A plan id as the platforms send it (monthly, quarterly, yearly, pro…), in words. */
export const planLabel = (plan: string, L: L): string => ({
  monthly: L('ماهانه', 'Monthly'), quarterly: L('سه‌ماهه', 'Quarterly'), yearly: L('سالانه', 'Yearly'), pro: 'Pro', vip: 'VIP', free: L('رایگان', 'Free'),
} as Record<string, string>)[plan.toLowerCase()] ?? plan;
