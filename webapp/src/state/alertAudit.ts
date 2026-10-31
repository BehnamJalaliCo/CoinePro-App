// What one alert actually did (the phone's AlertAuditStore, AlertAuditTrail and AlertAuditSheet):
// made, changed, paused, re-armed, deleted, its condition met, the notification arriving or not —
// and why not — and its webhooks that did not answer. Kept in this browser, five hundred lines,
// newest last; an alert's own history is filtered from the one list.
import { create } from 'zustand';
import type { Outcome, Reason } from './webhooks';

export type AuditEvent = 'created' | 'edited' | 'armed' | 'fired' | 'delivered' | 'delivery_failed' | 'snoozed' | 'expired' | 'deleted';
/** What a save changed, named as the editor names it (AlertAuditTrail.changes). */
export type ChangeKey = 'symbol' | 'scope' | 'condition' | 'frequency' | 'channels' | 'loudness' | 'message';
/** Why a notification did not arrive (AndroidAlertDeliverer's refusals, and the page's own). */
export type DeliveryReason = 'no_channels' | 'no_permission' | 'suppressed' | 'post_failed' | 'tab_hidden';

export interface AuditEntry {
  alertId: string;
  event: AuditEvent;
  at: number;
  symbol: string;
  /** The market's price at the moment, for the events that read one (never for the reader's own). */
  price?: number;
  timeframe?: string;
  changed?: ChangeKey[];
  reasons?: DeliveryReason[];
  /** A webhook that did not deliver: its name then, the outcome and the reason. */
  hook?: { name: string; outcome: Outcome; reason?: Reason };
}

export const AUDIT_EVENTS: Record<AuditEvent, { fa: string; en: string }> = {
  created: { fa: 'ساخته شد', en: 'Created' },
  edited: { fa: 'ویرایش شد', en: 'Edited' },
  armed: { fa: 'پایش آغاز شد', en: 'Watching started' },
  fired: { fa: 'شرط برقرار شد', en: 'Condition met' },
  delivered: { fa: 'اعلان رسید', en: 'Notification arrived' },
  delivery_failed: { fa: 'اعلان نرسید', en: 'Notification did not arrive' },
  snoozed: { fa: 'به تعویق افتاد', en: 'Paused' },
  expired: { fa: 'منقضی شد', en: 'Expired' },
  deleted: { fa: 'حذف شد', en: 'Deleted' },
};
export const CHANGE_WORDS: Record<ChangeKey, { fa: string; en: string }> = {
  symbol: { fa: 'نماد', en: 'symbol' },
  scope: { fa: 'دامنه', en: 'scope' },
  condition: { fa: 'شرط', en: 'condition' },
  frequency: { fa: 'تکرار', en: 'repeat' },
  channels: { fa: 'کانال‌ها', en: 'channels' },
  loudness: { fa: 'صدا', en: 'sound' },
  message: { fa: 'پیام', en: 'message' },
};
export const DELIVERY_REASONS: Record<DeliveryReason, { fa: string; en: string }> = {
  no_channels: { fa: 'هیچ روشی برای این هشدار روشن نیست', en: 'No way of telling you is switched on for this alert' },
  no_permission: { fa: 'اجازه‌ی اعلان داده نشده است', en: 'Notification permission has not been given' },
  suppressed: { fa: 'اعلان‌ها خاموش یا در ساعات سکوت بود', en: 'Notifications were off or in quiet hours' },
  post_failed: { fa: 'سیستم اعلان را نپذیرفت', en: 'The system did not accept the notification' },
  tab_hidden: { fa: 'صفحه در پس‌زمینه بود', en: 'The page was in the background' },
};

/**
 * «شرط و تکرار تغییر کرد»: the changed things as one clause (AlertAuditTrail.phrase) — «، » between
 * them and « و » before the last, a singular verb, and no count.
 */
export function changePhrase(changed: ChangeKey[], fa: boolean): string {
  const w = changed.map((c) => CHANGE_WORDS[c][fa ? 'fa' : 'en']);
  if (!w.length) return '';
  const subject = w.length === 1 ? w[0]! : `${w.slice(0, -1).join(fa ? '، ' : ', ')}${fa ? ' و ' : ' and '}${w.at(-1)}`;
  return fa ? `${subject} تغییر کرد` : `${subject[0]!.toUpperCase()}${subject.slice(1)} changed`;
}

/** The note under an entry, in the reader's language. */
export function entryNote(e: AuditEntry, fa: boolean, hookText?: (h: NonNullable<AuditEntry['hook']>) => string): string | null {
  if (e.changed?.length) return changePhrase(e.changed, fa);
  if (e.hook) return hookText ? hookText(e.hook) : e.hook.name;
  if (e.reasons?.length) return e.reasons.map((r) => DELIVERY_REASONS[r][fa ? 'fa' : 'en']).join(fa ? '، ' : ', ');
  return null;
}

/** What the editor shows of an alert, for comparing two saves as the reader sees them. */
export interface AuditView { symbol: string; scope: string; condition: string; frequency: string; channels: string; loudness: string; message: string; active: boolean; spent: boolean }

/** The things a save changed, in the sheet's order. Empty: the same alert as far as anybody can see. */
export function auditChanges(prev: AuditView, next: AuditView): ChangeKey[] {
  const keys: ChangeKey[] = ['symbol', 'scope', 'condition', 'frequency', 'channels', 'loudness', 'message'];
  return keys.filter((k) => (k === 'message' ? prev.message.trim() !== next.message.trim() : prev[k] !== next[k]));
}

/**
 * The line a save deserves (AlertAuditTrail.save): a new alert is «ساخته شد»; a change is «ویرایش
 * شد» naming what changed; an unchanged save that puts a paused or spent alert back on watch is
 * «پایش آغاز شد»; an identical save writes nothing.
 */
export function saveLine(prev: AuditView | null, next: AuditView): { event: AuditEvent; changed?: ChangeKey[] } | null {
  if (!prev) return { event: 'created' };
  const changed = auditChanges(prev, next);
  if (changed.length) return { event: 'edited', changed };
  if (next.active && (!prev.active || prev.spent)) return { event: 'armed' };
  return null;
}

const KEY = 'pc.alerts.audit.v1';
export const MAX_AUDIT = 500;

interface AuditState {
  entries: AuditEntry[];
  record(e: AuditEntry | AuditEntry[]): void;
}

export const useAlertAudit = create<AuditState>((set, get) => ({
  entries: (() => { try { const v = JSON.parse(localStorage.getItem(KEY) ?? '[]') as unknown; return Array.isArray(v) ? (v as AuditEntry[]).slice(-MAX_AUDIT) : []; } catch { return []; } })(),
  record: (e) => {
    const add = (Array.isArray(e) ? e : [e]).map((x) => { const o = { ...x }; for (const k of Object.keys(o) as (keyof AuditEntry)[]) if (o[k] === undefined) delete o[k]; return o; });
    if (!add.length) return;
    const entries = [...get().entries, ...add].slice(-MAX_AUDIT);
    set({ entries });
    try { localStorage.setItem(KEY, JSON.stringify(entries)); } catch { /* full */ }
  },
}));

/** One alert's history, oldest first: a firing and its delivery read as a pair downwards. */
export const historyOf = (entries: AuditEntry[], alertId: string) => entries.filter((x) => x.alertId === alertId).sort((a, b) => a.at - b.at);
