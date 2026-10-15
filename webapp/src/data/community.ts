// The app community (core/community, TradeYar api/v1/public/app-community): anonymous by design,
// each install known only by its X-Community-Key — 32 random bytes as 64 hex characters, minted
// once in this browser — and the display name it registers.
import { request, type RequestOptions } from './http';

const KEY = 'pc.community.key.v1';
export function communityKey(): string {
  try {
    const have = localStorage.getItem(KEY);
    if (have && /^[0-9a-f]{64}$/.test(have)) return have;
    const k = Array.from(crypto.getRandomValues(new Uint8Array(32)), (b) => b.toString(16).padStart(2, '0')).join('');
    localStorage.setItem(KEY, k);
    return k;
  } catch {
    return Array.from(crypto.getRandomValues(new Uint8Array(32)), (b) => b.toString(16).padStart(2, '0')).join('');
  }
}

export const CATEGORIES = ['عمومی', 'تحلیل', 'سوال', 'تجربه', 'اخبار'] as const;
export const CATEGORY_EN: Record<string, string> = { 'عمومی': 'General', 'تحلیل': 'Analysis', 'سوال': 'Question', 'تجربه': 'Experience', 'اخبار': 'News' };
export const REACTIONS = ['👍', '🔥', '🤔', '❤️', '💡'];

export function community<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  return request<T>(`/up/tradeyar/api/v1/public/app-community/${path}`, { ...opts, headers: { ...(opts.headers ?? {}), 'X-Community-Key': communityKey() } });
}

export interface Post { id: string; content: string; author: string; category: string; likes: number; liked: boolean; replies: number; reactions: Record<string, number>; best: string | null; createdAt: number | null; pending: boolean; image: string | null }
export interface Reply { id: string; content: string; author: string; parent: string | null; best: boolean; createdAt: number | null }

const s = (v: unknown) => (v == null ? '' : String(v));
const n = (v: unknown) => { const x = Number(v); return Number.isFinite(x) ? x : 0; };
const t = (v: unknown) => { if (v == null || v === '') return null; const x = typeof v === 'number' ? (v < 1e12 ? v * 1000 : v) : Date.parse(String(v)); return Number.isFinite(x) ? x : null; };

export function readPost(x: Record<string, unknown>): Post {
  return {
    id: s(x.id), content: s(x.content ?? x.body), author: s(x.author ?? x.username), category: s(x.category) || 'عمومی',
    likes: n(x.likes ?? x.like_count), liked: Boolean(x.liked ?? x.is_liked), replies: n(x.replies_count),
    reactions: (x.reactions && typeof x.reactions === 'object' ? x.reactions : {}) as Record<string, number>,
    best: x.best_reply_id != null ? s(x.best_reply_id) : null, createdAt: t(x.created_at), pending: x.status != null && s(x.status) !== 'published',
    image: x.image_url ? s(x.image_url) : null,
  };
}

export function readReply(x: Record<string, unknown>): Reply {
  return { id: s(x.id), content: s(x.content), author: s(x.author), parent: x.parent_id != null ? s(x.parent_id) : null, best: Boolean(x.is_best), createdAt: t(x.created_at) };
}

/** The list under whichever key this deployment uses. */
export function listOf(r: Record<string, unknown>): Record<string, unknown>[] {
  for (const k of ['posts', 'items', 'results', 'data', 'rows']) if (Array.isArray(r[k])) return r[k] as Record<string, unknown>[];
  return [];
}

/** An image the community serves needs the key too; fetched as a blob URL. */
export async function communityImage(url: string): Promise<string | null> {
  try {
    const path = url.replace(/^https?:\/\/[^/]+\//, '/up/tradeyar/');
    const res = await fetch(path, { headers: { 'X-Community-Key': communityKey() } });
    if (!res.ok) return null;
    return URL.createObjectURL(await res.blob());
  } catch { return null; }
}
