// «ذخیره‌شده · ۲ ساعت پیش» — the last good picture of each feed, kept with its time, and how old it is
// (core/common SavedAge.kt, core/designsystem CoineProSavedBar.kt).
//
// Bars, news, the calendar and the tickers are saved whenever they arrive. When a request fails,
// the saved copy is shown instead and the surface says how old it is in the muted ink — saved is
// not a fault, and it is just as true on a good connection that has not refreshed yet. The line
// leaves by itself the moment a fresh picture lands.
import { create } from 'zustand';

export type SavedAgeUnit = 'moments' | 'minutes' | 'hours' | 'days';
/** A count and its unit; `count` is 0 for `moments`. */
export interface SavedAge { unit: SavedAgeUnit; count: number }

/** Under this many seconds the picture is «just now» rather than a number of minutes. */
export const MOMENTS_SECONDS = 60;

/**
 * How old a saved picture is, or null where there is nothing to say: no stored time, or a time
 * ahead of the clock (the clock moved; clamping it to «just now» could hide a picture days old).
 * Floored, never rounded: fifty-nine minutes is «۵۹ دقیقه», not «یک ساعت».
 */
export function savedAgeOf(savedAt: number | null | undefined, now: number): SavedAge | null {
  if (savedAt == null || !Number.isFinite(savedAt) || savedAt <= 0) return null;
  const s = Math.floor((now - savedAt) / 1000);
  if (s < 0) return null;
  if (s < MOMENTS_SECONDS) return { unit: 'moments', count: 0 };
  if (s < 3600) return { unit: 'minutes', count: Math.floor(s / 60) };
  if (s < 86_400) return { unit: 'hours', count: Math.floor(s / 3600) };
  return { unit: 'days', count: Math.floor(s / 86_400) };
}

const FA_DIGITS = '۰۱۲۳۴۵۶۷۸۹';
const fa = (n: number) => String(n).replace(/\d/g, (d) => FA_DIGITS[Number(d)]!);

/** «۲ ساعت پیش» / "2 hours ago" — the count in Persian digits in Persian, because it is prose. */
export function savedAgeSentence(age: SavedAge, lang: 'fa' | 'en'): string {
  if (age.unit === 'moments') return lang === 'fa' ? 'همین حالا' : 'just now';
  if (lang === 'fa') return `${fa(age.count)} ${age.unit === 'minutes' ? 'دقیقه' : age.unit === 'hours' ? 'ساعت' : 'روز'} پیش`;
  const word = age.unit === 'minutes' ? 'minute' : age.unit === 'hours' ? 'hour' : 'day';
  return `${age.count} ${word}${age.count === 1 ? '' : 's'} ago`;
}

/** «ذخیره‌شده · ۲ ساعت پیش» / "Saved · 2 hours ago". */
export function savedAgo(age: SavedAge, lang: 'fa' | 'en'): string {
  return `${lang === 'fa' ? 'ذخیره‌شده' : 'Saved'} · ${savedAgeSentence(age, lang)}`;
}

// ── The saved copies ────────────────────────────────────────────────────────────────────────────
const PREFIX = 'pc.saved.v1:';
/** Bars are the big ones: at most this many symbol/interval pictures are kept, the oldest go first. */
const MAX_BARS = 12;

interface Snapshot<T> { at: number; data: T }

function keysWith(prefix: string): { key: string; at: number }[] {
  const out: { key: string; at: number }[] = [];
  try {
    for (let i = 0; i < localStorage.length; i++) {
      const k = localStorage.key(i);
      if (!k || !k.startsWith(PREFIX + prefix)) continue;
      const at = Number(/"at":(\d+)/.exec(localStorage.getItem(k) ?? '')?.[1] ?? 0);
      out.push({ key: k, at });
    }
  } catch { /* storage off */ }
  return out.sort((a, b) => a.at - b.at);
}

function evictOldestBars(keep: number): void {
  const bars = keysWith('bars:');
  for (const x of bars.slice(0, Math.max(0, bars.length - keep))) { try { localStorage.removeItem(x.key); } catch { /* ignore */ } }
}

/** Keeps `data` as the last good picture for `key`, with its time. */
export function saveSnapshot<T>(key: string, data: T, at: number = Date.now()): void {
  let text: string;
  try { text = JSON.stringify({ at, data }); } catch { return; }
  const put = () => localStorage.setItem(PREFIX + key, text);
  try {
    put();
    if (key.startsWith('bars:')) evictOldestBars(MAX_BARS);
  } catch {
    // Full: make room among the bars and try once more.
    try { evictOldestBars(Math.floor(MAX_BARS / 2)); put(); } catch { /* give up quietly */ }
  }
}

/** The last good picture for `key`, or null. */
export function readSnapshot<T>(key: string): Snapshot<T> | null {
  try {
    const raw = localStorage.getItem(PREFIX + key);
    if (!raw) return null;
    const v = JSON.parse(raw) as Partial<Snapshot<T>>;
    return typeof v.at === 'number' && v.data !== undefined ? { at: v.at, data: v.data as T } : null;
  } catch { return null; }
}

/** Which feeds are showing a saved picture right now, and since when it was saved. */
interface SavedState { at: Record<string, number>; mark(key: string, savedAt: number | null): void }

export const useSaved = create<SavedState>((set, get) => ({
  at: {},
  mark: (key, savedAt) => {
    const cur = get().at[key];
    if (savedAt == null ? cur === undefined : cur === savedAt) return;
    const next = { ...get().at };
    if (savedAt == null) delete next[key]; else next[key] = savedAt;
    set({ at: next });
  },
}));

/**
 * The fetch, saved when it succeeds; when it fails, the saved copy and its time (and the feed is
 * marked as showing it), else the failure. `fresh` decides whether a result is worth saving (an
 * empty list is not a picture).
 */
export async function withSaved<T>(key: string, fetcher: () => Promise<T>, fresh: (data: T) => boolean = () => true): Promise<{ data: T; savedAt: number | null }> {
  try {
    const data = await fetcher();
    if (fresh(data)) { saveSnapshot(key, data); useSaved.getState().mark(key, null); }
    return { data, savedAt: null };
  } catch (e) {
    const snap = readSnapshot<T>(key);
    if (!snap) throw e;
    useSaved.getState().mark(key, snap.at);
    return { data: snap.data, savedAt: snap.at };
  }
}

/** The oldest saved time among `keys` that are showing a saved picture, or null. */
export function oldestSaved(at: Record<string, number>, keys: string[]): number | null {
  let out: number | null = null;
  for (const k of keys) { const v = at[k]; if (v != null && (out == null || v < out)) out = v; }
  return out;
}
