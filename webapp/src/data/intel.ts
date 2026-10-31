// News and the economic calendar, from the same public routes the phone reads (core/marketintel):
//   crypto news   GET /api/news?limit=            → {data:[{id, slug, source, titleFa, summaryFa, publishedAt, importance, sourceImageUrl}]}
//   forex news    GET /up/coineprofx/api/academy/bn/news → {items:[{id, title, summary, url, image, source, published_at}]}
//   article       GET /up/tradeyar/api/v1/news/{slug} → {data:{bodyFa}}
//   calendar      GET /up/tradeyar/api/v1/public/calendar/week → [{title, country, date, impact, forecast, previous}]
//                 else GET /up/coineprofx/api/academy/bn/calendar → {items:[…]}
import { request } from './http';
import { withSaved } from './saved';

export interface NewsItem {
  id: string;
  market: 'crypto' | 'forex';
  title: string;
  summary: string;
  source: string;
  time: number; // ms
  image: string | null;
  slug?: string;
  url?: string;
  importance: number;
  /** The feed's own classification where it sent one (named apart from a calendar event's `impact`) (OLD MarketImpact / NewsSentiment); never guessed. */
  newsImpact?: NewsImpact;
  sentiment?: NewsSentiment;
}

export type NewsImpact = 'high' | 'medium' | 'low';
export type NewsSentiment = 'bullish' | 'bearish' | 'neutral';
const first = (x: Record<string, unknown>, keys: string[]): string | null => {
  for (const k of keys) { const v = x[k]; if (v != null && String(v).trim()) return String(v).trim().toLowerCase(); }
  return null;
};
/** OLD MarketIntelGateway parseImpact: words and 1–3 levels, in either language. */
export function parseImpact(raw: string | null): NewsImpact | undefined {
  if (raw == null) return undefined;
  if (['low', '1', 'کم', 'پایین'].includes(raw)) return 'low';
  if (['medium', 'moderate', '2', 'متوسط', 'میانه'].includes(raw)) return 'medium';
  if (['high', '3', 'زیاد', 'بالا', 'پرتأثیر', 'پرتاثیر'].includes(raw)) return 'high';
  return undefined;
}
export function parseSentiment(raw: string | null): NewsSentiment | undefined {
  if (raw === 'bullish' || raw === 'positive') return 'bullish';
  if (raw === 'bearish' || raw === 'negative') return 'bearish';
  if (raw === 'neutral') return 'neutral';
  return undefined;
}
/** TradeYar's 0–10 importance as three levels (OLD TradeYarPublicNews: 7 and up high, 4 and up medium). */
export function impactOfScore(score: unknown): NewsImpact | undefined {
  if (score == null || score === '') return undefined;
  const n = Number(score);
  if (!Number.isFinite(n)) return undefined;
  return n >= 7 ? 'high' : n >= 4 ? 'medium' : 'low';
}
const IMPACT_KEYS = ['impact', 'impact_level', 'impactLevel', 'severity'];
const SENTIMENT_KEYS = ['sentiment', 'tone'];

export interface CalendarEvent {
  title: string;
  currency: string;
  /** The country the feed names, where it names one apart from the currency (search reads it). */
  country?: string;
  time: number; // ms
  impact: 'high' | 'medium' | 'low' | 'holiday';
  forecast: string;
  previous: string;
  actual: string;
  /** The markets it moves (gold, silver, crypto), as the feed tags them (MarketRelevance). */
  relevance?: ('gold' | 'silver' | 'crypto')[];
}

/** The feed's market tags (MarketIntelGateway.parseRelevance); null where a row carries none. */
export function parseRelevance(x: Record<string, unknown>): ('gold' | 'silver' | 'crypto')[] | null {
  const raw = x.relevance ?? x.symbols ?? x.markets ?? x.tags;
  if (raw == null) return null;
  const list = Array.isArray(raw) ? raw.map(String) : String(raw).split(',');
  const out = new Set<'gold' | 'silver' | 'crypto'>();
  for (const v of list) {
    const k = v.trim().toLowerCase();
    if (['gold', 'xau', 'xauusd'].includes(k)) out.add('gold');
    else if (['silver', 'xag', 'xagusd'].includes(k)) out.add('silver');
    else if (['crypto', 'digital_assets'].includes(k)) out.add('crypto');
  }
  return [...out];
}

const imageUrl = (raw: unknown): string | null => {
  if (typeof raw !== 'string' || !raw || raw === 'None' || !/^https:\/\//.test(raw)) return null;
  return `/api/img?url=${encodeURIComponent(raw)}`;
};

const ms = (raw: unknown): number => {
  const t = typeof raw === 'number' ? (raw < 1e12 ? raw * 1000 : raw) : Date.parse(String(raw ?? ''));
  return Number.isFinite(t) ? t : 0;
};

const noTags = (s: unknown): string => (typeof s === 'string' ? s.replace(/<[^>]*>/g, '').trim() : '');

async function fetchCryptoNews(limit: number): Promise<NewsItem[]> {
  // The relay accepts at most 50.
  const r = await request<{ data?: Record<string, unknown>[] }>(`/api/news?limit=${Math.min(50, Math.max(1, limit))}`);
  return (r.data ?? []).map((x) => ({
    id: `c${String(x.id)}`, market: 'crypto' as const,
    title: noTags(x.titleFa ?? x.originalTitleEn), summary: noTags(x.summaryFa),
    source: String(x.source ?? ''), time: ms(x.publishedAt), image: imageUrl(x.sourceImageUrl),
    slug: typeof x.slug === 'string' ? x.slug : undefined, url: typeof x.sourceUrl === 'string' ? x.sourceUrl : undefined,
    importance: Number(x.importance ?? 0),
    newsImpact: parseImpact(first(x, IMPACT_KEYS)) ?? impactOfScore(x.importance), sentiment: parseSentiment(first(x, SENTIMENT_KEYS)),
  })).filter((n) => n.title);
}

async function fetchForexNews(): Promise<NewsItem[]> {
  const r = await request<{ items?: Record<string, unknown>[] }>('/up/coineprofx/api/academy/bn/news');
  return (r.items ?? []).map((x) => ({
    id: `f${String(x.id)}`, market: 'forex' as const,
    title: noTags(x.title), summary: noTags(x.summary), source: String(x.source ?? ''),
    time: ms(x.published_at), image: imageUrl(x.image), url: typeof x.url === 'string' ? x.url : undefined, importance: 0,
    newsImpact: parseImpact(first(x, IMPACT_KEYS)), sentiment: parseSentiment(first(x, SENTIMENT_KEYS)),
  })).filter((n) => n.title);
}

/** The full text of a crypto story, or null (forex stories link out). */
export async function articleBody(item: NewsItem): Promise<string | null> {
  if (item.market !== 'crypto' || !item.slug) return null;
  const r = await request<{ data?: { bodyFa?: string } }>(`/up/tradeyar/api/v1/news/${encodeURIComponent(item.slug)}`);
  const body = r.data?.bodyFa ?? '';
  return body && !/<[a-z][^>]*>/i.test(body) ? body : null;
}

const impactOf = (raw: unknown): CalendarEvent['impact'] => {
  const s = String(raw ?? '').toLowerCase();
  if (s.startsWith('high')) return 'high';
  if (s.startsWith('med')) return 'medium';
  if (s.startsWith('hol')) return 'holiday';
  return 'low';
};
const str = (v: unknown) => (v == null ? '' : String(v));

async function fetchCalendarWeek(): Promise<CalendarEvent[]> {
  let rows: Record<string, unknown>[] = [];
  // The week's public file is about the dollar: a release in it moves both metals and never a coin
  // (PublicCalendarFeed); the academy's rows say which markets they move themselves.
  let untagged: ('gold' | 'silver' | 'crypto')[] = ['gold', 'silver'];
  try {
    const r = await request<unknown>('/up/tradeyar/api/v1/public/calendar/week');
    if (Array.isArray(r)) rows = r as Record<string, unknown>[];
  } catch { /* try the academy feed */ }
  if (!rows.length) {
    const r = await request<{ items?: Record<string, unknown>[] }>('/up/coineprofx/api/academy/bn/calendar');
    rows = r.items ?? [];
    untagged = [];
  }
  return rows.map((x) => ({
    title: str(x.title ?? x.event ?? x.name),
    currency: str(x.currency ?? x.country),
    country: str(x.country ?? x.country_code ?? x.countryCode ?? x.region) || undefined,
    time: ms(x.event_time ?? x.date ?? x.scheduled_at ?? x.datetime),
    impact: impactOf(x.impact_label ?? x.impact ?? x.importance),
    forecast: str(x.forecast), previous: str(x.previous), actual: str(x.actual),
    relevance: parseRelevance(x) ?? untagged,
  })).filter((e) => e.title && e.time).sort((a, b) => a.time - b.time);
}

// ── Saved copies ────────────────────────────────────────────────────────────────────────────────
// Each list is kept when it arrives and shown, with its age, when the network does not answer
// (data/saved.ts; the surfaces read `useSaved` under these keys).
export const SAVED_KEYS = { cryptoNews: 'news:crypto', forexNews: 'news:forex', calendar: 'calendar' } as const;
const some = <T>(x: T[]) => x.length > 0;

export async function cryptoNews(limit = 30): Promise<NewsItem[]> {
  return (await withSaved(SAVED_KEYS.cryptoNews, () => fetchCryptoNews(limit), some)).data;
}

export async function forexNews(): Promise<NewsItem[]> {
  return (await withSaved(SAVED_KEYS.forexNews, fetchForexNews, some)).data;
}

export async function calendarWeek(): Promise<CalendarEvent[]> {
  return (await withSaved(SAVED_KEYS.calendar, fetchCalendarWeek, some)).data;
}
