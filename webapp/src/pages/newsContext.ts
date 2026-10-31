// What a story bears on, the chart it bears on, and the next stories about the same market
// (OLD feature/news NewsArticleScreen.kt MarketContext / RelatedStories, NewsScreen.kt relatedTo,
// core/marketintel AcademyPublicNews.relevance, MarketIntelGateway.parseRelevance,
// core/chartevents ChartEventSymbols).
import type { NewsItem } from '../data/intel';

export type Relevance = 'gold' | 'silver' | 'crypto';
export type Story = NewsItem & { relevance?: Relevance[] };

const SILVER_WORDS = ['نقره', 'XAG', 'Silver', 'silver'];
const GOLD_WORDS = ['طلا', 'XAU', 'Gold', 'gold'];

function classify(text: string): Relevance[] | null {
  const silver = SILVER_WORDS.some((w) => text.includes(w));
  const gold = GOLD_WORDS.some((w) => text.includes(w));
  if (silver && !gold) return ['silver'];
  if (gold && !silver) return ['gold'];
  return null;
}

/** The feed's own tags where it sent them: gold / xau / xauusd, silver / xag / xagusd, crypto / digital_assets. */
export function parseRelevance(values: unknown): Relevance[] {
  const list = Array.isArray(values) ? values : typeof values === 'string' ? values.split(/[,\s]+/) : [];
  const out = new Set<Relevance>();
  for (const v of list) {
    const s = String(v).trim().toLowerCase();
    if (s === 'gold' || s === 'xau' || s === 'xauusd') out.add('gold');
    else if (s === 'silver' || s === 'xag' || s === 'xagusd') out.add('silver');
    else if (s === 'crypto' || s === 'digital_assets') out.add('crypto');
  }
  return [...out];
}

/**
 * A story's markets: its own tags when it has them; a crypto story is crypto; a forex-feed story is
 * gold or silver by its title (the title decides), then its summary, and a story naming neither — a
 * rate decision, the dollar — moves both.
 */
export function relevanceOf(s: Story): Relevance[] {
  if (s.relevance) return s.relevance;
  if (s.market === 'crypto') return ['crypto'];
  return classify(s.title) ?? classify(s.summary ?? '') ?? ['gold', 'silver'];
}

/** The instrument to open: gold before silver before crypto, so a story always opens the same chart. */
export function symbolFor(rel: Relevance[]): string | null {
  if (rel.includes('gold')) return 'XAUUSD';
  if (rel.includes('silver')) return 'XAGUSD';
  if (rel.includes('crypto')) return 'BTCUSDT';
  return null;
}

export const RELEVANCE_LABEL: Record<Relevance, [string, string]> = { gold: ['طلا', 'Gold'], silver: ['نقره', 'Silver'], crypto: ['کریپتو', 'Crypto'] };

/** Four. Enough that the page continues, few enough that it is a suggestion and not a second feed. */
export const MAX_RELATED = 4;

/** The next few stories about the same market, newest first; an untagged story gets the rest of the feed. */
export function relatedTo(story: Story, feed: Story[]): Story[] {
  const mine = relevanceOf(story);
  const others = feed.filter((x) => x.id !== story.id);
  const matched = mine.length ? others.filter((x) => relevanceOf(x).some((r) => mine.includes(r))) : others;
  return [...matched].sort((a, b) => (b.time || 0) - (a.time || 0)).slice(0, MAX_RELATED);
}

/** «این خبر روی طلا و نقره اثر دارد.» — the sentence under «این خبر به چه مربوط است». */
export function contextSentence(rel: Relevance[], fa: boolean): string {
  if (!rel.length) return fa ? 'این خبر به بازار خاصی بسته نشده و عمومی است.' : 'This story is not tied to one market.';
  const names = rel.map((r) => RELEVANCE_LABEL[r][fa ? 0 : 1]).join(fa ? ' و ' : ' and ');
  return fa ? `این خبر روی ${names} اثر دارد.` : `This story bears on ${names}.`;
}
