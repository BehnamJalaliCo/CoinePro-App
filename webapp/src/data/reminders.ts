// Calendar reminders (the bell on an economic event): kept in this browser, rung at the event's
// time by a check every 15 seconds wherever the reader is in the app — a sound, a browser
// notification, and an entry the notification log shows.
import { playSound, readNotify, inQuietHours } from '../state/notify';
import type { CalendarEvent } from './intel';

const REMIND_KEY = 'pc.cal.reminders.v1';
export const REMIND_LOG_KEY = 'pc.cal.reminded.v1';

export const eventKey = (e: CalendarEvent) => `${e.time}|${e.currency}|${e.title}`;
export interface Reminder { key: string; title: string; currency: string; time: number }
export function readReminders(): Reminder[] { try { const v = JSON.parse(localStorage.getItem(REMIND_KEY) ?? '[]') as unknown; return Array.isArray(v) ? (v as Reminder[]) : []; } catch { return []; } }
export function writeReminders(r: Reminder[]) { try { localStorage.setItem(REMIND_KEY, JSON.stringify(r)); } catch { /* ignore */ } window.dispatchEvent(new Event('pc:reminders')); }
export function readReminded(): Reminder[] { try { const v = JSON.parse(localStorage.getItem(REMIND_LOG_KEY) ?? '[]') as unknown; return Array.isArray(v) ? (v as Reminder[]) : []; } catch { return []; } }

/** Rings the reminders whose time has come, every 15 seconds, wherever the reader is in the app. */
let reminding = false;
export function startCalendarReminders(): void {
  if (reminding || typeof window === 'undefined') return;
  reminding = true;
  const check = () => {
    const now = Date.now();
    const all = readReminders();
    const due = all.filter((r) => r.time <= now);
    if (!due.length) return;
    writeReminders(all.filter((r) => r.time > now));
    const log = [...due.filter((r) => now - r.time < 3_600_000), ...readReminded()].slice(0, 100);
    try { localStorage.setItem(REMIND_LOG_KEY, JSON.stringify(log)); } catch { /* ignore */ }
    for (const r of due) {
      if (now - r.time > 3_600_000) continue;
      const prefs = readNotify();
      if (!inQuietHours(new Date(), prefs) && prefs.sound) playSound('bell');
      try { if ('Notification' in window && Notification.permission === 'granted') new Notification(`${r.currency} · ${r.title}`, { body: new Date(r.time).toLocaleTimeString(), tag: r.key }); } catch { /* ignore */ }
      window.dispatchEvent(new CustomEvent('pc:reminder', { detail: r }));
    }
  };
  check();
  setInterval(check, 15_000);
}

