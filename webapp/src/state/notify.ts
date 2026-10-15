// This browser's own notification settings (the notifications page): the alert sound and quiet
// hours, read by the alert watcher before it rings.
export const NOTIFY_KEY = 'pc.notify.v1';
export interface NotifyLocal { sound: boolean; quietFrom: string; quietTo: string; quiet: boolean }
const DEFAULTS: NotifyLocal = { sound: true, quiet: false, quietFrom: '23:00', quietTo: '07:00' };

export function readNotify(): NotifyLocal {
  try { return { ...DEFAULTS, ...(JSON.parse(localStorage.getItem(NOTIFY_KEY) ?? '{}') as Partial<NotifyLocal>) }; } catch { return DEFAULTS; }
}

export function writeNotify(n: NotifyLocal): void { try { localStorage.setItem(NOTIFY_KEY, JSON.stringify(n)); } catch { /* ignore */ } }

/** Whether an alert fired now should stay silent, by the quiet hours. */
export function inQuietHours(now = new Date(), l: NotifyLocal = readNotify()): boolean {
  if (!l.quiet) return false;
  const m = now.getHours() * 60 + now.getMinutes();
  const [a, b] = [l.quietFrom, l.quietTo].map((x) => { const [h, mm] = x.split(':').map(Number); return (h ?? 0) * 60 + (mm ?? 0); }) as [number, number];
  return a <= b ? m >= a && m < b : m >= a || m < b;
}

/** A short two-tone chime, made in the page (no audio file). */
export function chime(): void {
  try {
    const Ctx = window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
    if (!Ctx) return;
    const ctx = new Ctx();
    [880, 1320].forEach((f, i) => {
      const o = ctx.createOscillator(); const g = ctx.createGain();
      o.frequency.value = f; o.type = 'sine';
      g.gain.setValueAtTime(0.0001, ctx.currentTime + i * 0.16);
      g.gain.exponentialRampToValueAtTime(0.2, ctx.currentTime + i * 0.16 + 0.02);
      g.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + i * 0.16 + 0.3);
      o.connect(g).connect(ctx.destination); o.start(ctx.currentTime + i * 0.16); o.stop(ctx.currentTime + i * 0.16 + 0.32);
    });
    setTimeout(() => void ctx.close(), 1000);
  } catch { /* audio unavailable */ }
}
