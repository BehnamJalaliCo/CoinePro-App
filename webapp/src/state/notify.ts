// This browser's own notification settings (the notifications page): the alert sound and quiet
// hours, read by the alert watcher before it rings. The sounds are made in the page (no audio
// files): TradingView's choice of a few tones, played once or repeated for a few seconds.
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

export type SoundId = 'chime' | 'bell' | 'beep' | 'alarm' | 'digital' | 'none';
export const SOUNDS: { id: SoundId; fa: string; en: string }[] = [
  { id: 'chime', fa: 'زنگوله', en: 'Chime' }, { id: 'bell', fa: 'زنگ', en: 'Bell' }, { id: 'beep', fa: 'بوق کوتاه', en: 'Beep' },
  { id: 'alarm', fa: 'آژیر', en: 'Alarm clock' }, { id: 'digital', fa: 'دیجیتال', en: 'Digital' }, { id: 'none', fa: 'بی‌صدا', en: 'No sound' },
];
/** Notes per sound: frequency (Hz), start (s), length (s), wave. */
const TONES: Record<Exclude<SoundId, 'none'>, { f: number; at: number; len: number; type: OscillatorType }[]> = {
  chime: [{ f: 880, at: 0, len: 0.3, type: 'sine' }, { f: 1320, at: 0.16, len: 0.3, type: 'sine' }],
  bell: [{ f: 660, at: 0, len: 0.9, type: 'triangle' }, { f: 1320, at: 0, len: 0.6, type: 'sine' }],
  beep: [{ f: 1000, at: 0, len: 0.12, type: 'square' }, { f: 1000, at: 0.2, len: 0.12, type: 'square' }],
  alarm: [{ f: 990, at: 0, len: 0.1, type: 'square' }, { f: 990, at: 0.15, len: 0.1, type: 'square' }, { f: 990, at: 0.3, len: 0.1, type: 'square' }, { f: 990, at: 0.45, len: 0.1, type: 'square' }],
  digital: [{ f: 1568, at: 0, len: 0.08, type: 'sine' }, { f: 2093, at: 0.1, len: 0.08, type: 'sine' }, { f: 1568, at: 0.2, len: 0.08, type: 'sine' }],
};
const SPAN: Record<Exclude<SoundId, 'none'>, number> = { chime: 0.5, bell: 1, beep: 0.35, alarm: 0.6, digital: 0.3 };

/** Plays a sound once, or repeated until `seconds` have passed. */
export function playSound(id: SoundId = 'chime', seconds = 0): void {
  if (id === 'none') return;
  try {
    const Ctx = window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
    if (!Ctx) return;
    const ctx = new Ctx();
    const tones = TONES[id] ?? TONES.chime;
    const span = SPAN[id] ?? 0.5;
    const rounds = Math.max(1, Math.min(30, Math.floor(seconds / (span + 0.25)) || 1));
    for (let r = 0; r < rounds; r++) {
      const base = ctx.currentTime + r * (span + 0.25);
      for (const tn of tones) {
        const o = ctx.createOscillator(); const g = ctx.createGain();
        o.frequency.value = tn.f; o.type = tn.type;
        g.gain.setValueAtTime(0.0001, base + tn.at);
        g.gain.exponentialRampToValueAtTime(0.18, base + tn.at + 0.02);
        g.gain.exponentialRampToValueAtTime(0.0001, base + tn.at + tn.len);
        o.connect(g).connect(ctx.destination); o.start(base + tn.at); o.stop(base + tn.at + tn.len + 0.02);
      }
    }
    setTimeout(() => void ctx.close(), (rounds * (span + 0.25) + 1) * 1000);
  } catch { /* audio unavailable */ }
}

/** A short two-tone chime, made in the page (no audio file). */
export function chime(): void {
  playSound('chime');
}
