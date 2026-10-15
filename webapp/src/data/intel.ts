// News and the economic calendar, from the same public routes the phone reads (core/marketintel):
//   crypto news   GET /api/news?limit=            → {data:[{id, slug, source, titleFa, summaryFa, publishedAt, importance, sourceImageUrl}]}
//   forex news    GET /up/coineprofx/api/academy/bn/news → {items:[{id, title, summary, url, image, source, published_at}]}
//   article       GET /up/tradeyar/api/v1/news/{slug} → {data:{bodyFa}}
//   calendar      GET /up/tradeyar/api/v1/public/calendar/week → [{title, country, date, impact, forecast, previous}]
//                 else GET /up/coineprofx/api/academy/bn/calendar → {items:[…]}
import { request } from './http';

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
}

export interface CalendarEvent {
  title: string;
  currency: string;
  time: number; // ms
  impact: 'high' | 'medium' | 'low' | 'holiday';
  forecast: string;
  previous: string;
  actual: string;
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

export async function cryptoNews(limit = 30): Promise<NewsItem[]> {
  // The relay accepts at most 50.
  const r = await request<{ data?: Record<string, unknown>[] }>(`/api/news?limit=${Math.min(50, Math.max(1, limit))}`);
  return (r.data ?? []).map((x) => ({
    id: `c${String(x.id)}`, market: 'crypto' as const,
    title: noTags(x.titleFa ?? x.originalTitleEn), summary: noTags(x.summaryFa),
    source: String(x.source ?? ''), time: ms(x.publishedAt), image: imageUrl(x.sourceImageUrl),
    slug: typeof x.slug === 'string' ? x.slug : undefined, url: typeof x.sourceUrl === 'string' ? x.sourceUrl : undefined,
    importance: Number(x.importance ?? 0),
  })).filter((n) => n.title);
}

export async function forexNews(): Promise<NewsItem[]> {
  const r = await request<{ items?: Record<string, unknown>[] }>('/up/coineprofx/api/academy/bn/news');
  return (r.items ?? []).map((x) => ({
    id: `f${String(x.id)}`, market: 'forex' as const,
    title: noTags(x.title), summary: noTags(x.summary), source: String(x.source ?? ''),
    time: ms(x.published_at), image: imageUrl(x.image), url: typeof x.url === 'string' ? x.url : undefined, importance: 0,
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

export async function calendarWeek(): Promise<CalendarEvent[]> {
  let rows: Record<string, unknown>[] = [];
  try {
    const r = await request<unknown>('/up/tradeyar/api/v1/public/calendar/week');
    if (Array.isArray(r)) rows = r as Record<string, unknown>[];
  } catch { /* try the academy feed */ }
  if (!rows.length) {
    const r = await request<{ items?: Record<string, unknown>[] }>('/up/coineprofx/api/academy/bn/calendar');
    rows = r.items ?? [];
  }
  return rows.map((x) => ({
    title: str(x.title ?? x.event ?? x.name),
    currency: str(x.currency ?? x.country),
    time: ms(x.event_time ?? x.date ?? x.scheduled_at ?? x.datetime),
    impact: impactOf(x.impact_label ?? x.impact ?? x.importance),
    forecast: str(x.forecast), previous: str(x.previous), actual: str(x.actual),
  })).filter((e) => e.title && e.time).sort((a, b) => a.time - b.time);
}
