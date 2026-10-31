// The markets list's arithmetic (OLD feature/search MarketArrangement.kt, MarketFilterSheet.kt,
// core/marketdata MarketPulse.kt, MarketsHeader.kt): the «داغ» lens, the filter sheet's four
// questions, the pulse's figures and the headline row. Pure, so the decisions that go wrong
// silently — which rows a lens drops, what a filter does with a missing figure — are testable.
import { marketOf } from '../data/symbols';
import type { Market, Ticker } from '../data/types';
import type { NewsItem } from '../data/intel';

/** Money through a market today, in the quote currency: the comparable figure across a mixed list. */
export const turnoverOf = (tk: Ticker): number | null => {
  const v = (tk.volume ?? 0) * (marketOf(tk.symbol) === 'crypto' ? tk.last : 1);
  return Number.isFinite(v) && v > 0 ? v : null;
};
const changeOf = (tk: Ticker): number | null => (Number.isFinite(tk.changePct) ? tk.changePct : null);

/**
 * «داغ» — the busiest markets among the ones that actually moved today. A market qualifies when its
 * absolute move is at least the middle of today's board (the upper of the two middles on an even
 * count); the survivors are ordered by turnover. A market missing either figure is unknown, not
 * hot, and is left out. No blended score: a gate plus an order can be read off the row's columns.
 */
export function hottest(rows: Ticker[]): Ticker[] {
  const scored = rows.flatMap((tk) => {
    const turnover = turnoverOf(tk);
    const move = changeOf(tk);
    return turnover == null || move == null ? [] : [{ tk, turnover, move: Math.abs(move) }];
  });
  if (!scored.length) return [];
  const gate = scored.map((x) => x.move).sort((a, b) => a - b)[Math.floor(scored.length / 2)]!;
  return scored.filter((x) => x.move >= gate).sort((a, b) => b.turnover - a.turnover).map((x) => x.tk);
}

// ── The filter sheet (F2) ─────────────────────────────────────────────────────────────────────────
export type ChangeBand = 'up5' | 'up' | 'down' | 'down5';
export type TurnoverFloor = 'm1' | 'm10' | 'm100';
export const CHANGE_BANDS: { id: ChangeBand; from: number | null; to: number | null; fa: string; en: string }[] = [
  { id: 'up5', from: 5, to: null, fa: 'رشد ۵٪ به بالا', en: 'Up 5%+' },
  { id: 'up', from: 0, to: null, fa: 'صعودی', en: 'Up' },
  { id: 'down', from: null, to: 0, fa: 'نزولی', en: 'Down' },
  { id: 'down5', from: null, to: -5, fa: 'افت ۵٪ به بالا', en: 'Down 5%+' },
];
export const TURNOVER_FLOORS: { id: TurnoverFloor; atLeast: number; fa: string; en: string }[] = [
  { id: 'm1', atLeast: 1_000_000, fa: 'بالای ۱ میلیون دلار', en: 'Over $1M' },
  { id: 'm10', atLeast: 10_000_000, fa: 'بالای ۱۰ میلیون دلار', en: 'Over $10M' },
  { id: 'm100', atLeast: 100_000_000, fa: 'بالای ۱۰۰ میلیون دلار', en: 'Over $100M' },
];
/** A market family's name on a chip: the category tabs' own words. */
export const TYPE_NAMES: Record<Market, { fa: string; en: string }> = {
  crypto: { fa: 'کریپتو', en: 'Crypto' },
  forex: { fa: 'فارکس', en: 'Forex' },
  metals: { fa: 'فلزات', en: 'Metals' },
  indices: { fa: 'شاخص‌ها', en: 'Indices' },
  energy: { fa: 'انرژی', en: 'Energy' },
};

export interface MarketFilter { types: Market[]; venues: string[]; change: ChangeBand | null; turnover: TurnoverFloor | null }
export const NO_FILTER: MarketFilter = { types: [], venues: [], change: null, turnover: null };
export const filterIsEmpty = (f: MarketFilter) => !f.types.length && !f.venues.length && f.change == null && f.turnover == null;
/** How many of the four questions have been answered — the number on the button's badge. */
export const filterCount = (f: MarketFilter) => f.types.length + f.venues.length + (f.change ? 1 : 0) + (f.turnover ? 1 : 0);

export function bandHolds(band: ChangeBand, percent: number): boolean {
  const b = CHANGE_BANDS.find((x) => x.id === band)!;
  if (b.from != null && percent < b.from) return false;
  if (b.to != null && percent > b.to) return false;
  return true;
}

/**
 * Whether a row survives. **A change band keeps the rows nothing knows the change of** (a band
 * narrows what is on screen; it is not «only the rows you have a figure for»). **A turnover floor
 * does not**: «over ten million a day» is a claim about the market, and a row that said nothing
 * has not made it.
 */
export function keeps(f: MarketFilter, tk: Ticker): boolean {
  if (f.types.length && !f.types.includes(marketOf(tk.symbol))) return false;
  if (f.venues.length && !(tk.source && f.venues.includes(tk.source))) return false;
  const change = changeOf(tk);
  if (f.change && change != null && !bandHolds(f.change, change)) return false;
  if (f.turnover) {
    const floor = TURNOVER_FLOORS.find((x) => x.id === f.turnover)!.atLeast;
    if ((turnoverOf(tk) ?? -1) < floor) return false;
  }
  return true;
}

export const applyFilter = (rows: Ticker[], f: MarketFilter): Ticker[] => (filterIsEmpty(f) ? rows : rows.filter((tk) => keeps(f, tk)));

/** Every market family present, in the list's own order. */
export function typesOf(rows: Ticker[]): Market[] {
  const seen: Market[] = [];
  for (const tk of rows) { const m = marketOf(tk.symbol); if (!seen.includes(m)) seen.push(m); }
  return seen;
}
/** Every venue the rows came from, in the order they first appear. */
export function venuesOf(rows: Ticker[]): string[] {
  const seen: string[] = [];
  for (const tk of rows) if (tk.source && !seen.includes(tk.source)) seen.push(tk.source);
  return seen;
}

// ── The pulse (U3) ────────────────────────────────────────────────────────────────────────────────
/**
 * The day's four figures. Market cap, Bitcoin's dominance and the fear-and-greed reading are left
 * null on purpose: neither backend serves a supply figure or the published mood index, and a number
 * worked out here would carry a familiar name and mean something else. The volume is this venue's
 * own book; the breadth (the share of the board that is up) is what the app can measure of the mood.
 */
export interface MarketPulse { marketCap: number | null; turnover24h: number | null; bitcoinDominance: number | null; fearGreed: number | null; breadth: number | null; markets: number }
export function pulseOf(table: Ticker[]): MarketPulse {
  const empty: MarketPulse = { marketCap: null, turnover24h: null, bitcoinDominance: null, fearGreed: null, breadth: null, markets: 0 };
  if (!table.length) return empty;
  const turnover = table.map(turnoverOf).filter((v): v is number => v != null);
  const changes = table.map(changeOf).filter((v): v is number => v != null);
  return {
    ...empty,
    turnover24h: turnover.length ? turnover.reduce((a, b) => a + b, 0) : null,
    breadth: changes.length ? Math.floor((changes.filter((c) => c > 0).length * 100) / changes.length) : null,
    markets: table.length,
  };
}
export const pulseHasFigure = (p: MarketPulse) => p.marketCap != null || p.turnover24h != null || p.bitcoinDominance != null || p.fearGreed != null || p.breadth != null;

export type PulseCell = 'cap' | 'volume' | 'dominance' | 'mood';
export const PULSE_CELLS: { id: PulseCell; fa: string; en: string; whyFa: string; whyEn: string }[] = [
  { id: 'cap', fa: 'ارزش کل بازار', en: 'Market cap', whyFa: 'ارزش کل بازار یعنی قیمت ضرب در عرضه‌ی در گردش، و هیچ‌کدام از دو سرویس عرضه را نمی‌فرستند. عددی که اینجا از حجم معاملات ساخته شود، نامی آشنا دارد و چیز دیگری است.', whyEn: 'Market cap is price times circulating supply, and neither feed sends a supply figure. A number here worked out from turnover would carry a familiar name and mean something else.' },
  { id: 'volume', fa: 'حجم ۲۴ ساعت', en: '24h volume', whyFa: 'دفتر همین بازارگاه است، نه کل بازار.', whyEn: 'This venue’s own book, not the whole market.' },
  { id: 'dominance', fa: 'سهم بیت‌کوین', en: 'BTC dominance', whyFa: 'سهم بیت‌کوین یعنی یک ارزش بازار تقسیم بر مجموع همه، پس به همان عرضه‌ای نیاز دارد که ارزش کل بازار می‌خواهد.', whyEn: 'Bitcoin’s share is one market cap divided by all of them, so it needs the same supply figures the total does.' },
  { id: 'mood', fa: 'ترس و طمع', en: 'Fear & greed', whyFa: 'شاخص منتشرشده‌ی ترس و طمع مال جای دیگری است و هیچ‌کدام از دو سرویس آن را نمی‌فرستند. عددی که اینجا ساخته شود، با همان نام، با چیزی که مردم دیده‌اند نمی‌خواند.', whyEn: 'The published fear-and-greed reading belongs to somebody else and neither feed carries it. A reading worked out here would disagree with the one people already know, under the same name.' },
];
export function pulseValue(cell: PulseCell, p: MarketPulse): string | null {
  switch (cell) {
    case 'cap': return p.marketCap != null ? compactFigure(p.marketCap) : null;
    case 'volume': return p.turnover24h != null ? compactFigure(p.turnover24h) : null;
    case 'dominance': return p.bitcoinDominance != null ? `${Math.trunc(p.bitcoinDominance)}%` : null;
    case 'mood': return p.fearGreed != null ? String(p.fearGreed) : null;
  }
}

/** «$1.4B», «$340M»: one decimal under a hundred, none above it. */
export function compactFigure(value: number): string {
  const m = Math.abs(value);
  const [scaled, suffix] = m >= 1e12 ? [value / 1e12, 'T'] : m >= 1e9 ? [value / 1e9, 'B'] : m >= 1e6 ? [value / 1e6, 'M'] : m >= 1e3 ? [value / 1e3, 'K'] : [value, ''];
  const decimals = Math.abs(scaled) < 100 && suffix ? 1 : 0;
  return `$${scaled.toLocaleString('en-US', { minimumFractionDigits: decimals, maximumFractionDigits: decimals })}${suffix}`;
}

// ── Headlines (U4) and Explore's top stories ──────────────────────────────────────────────────────
export interface MarketHeadline { id: string; title: string; important: boolean }
/** Six, because a seventh is a scroll nobody makes. */
export const TICKER_HEADLINES = 6;
/** Explore's «سرخط خبرها». */
export const STORY_LIMIT = 5;
export function headlinesOf(news: NewsItem[]): MarketHeadline[] {
  return news.slice(0, TICKER_HEADLINES).map((n) => ({ id: n.slug ?? n.id, title: n.title, important: n.newsImpact === 'high' }));
}

// ── Open signals (the strip at the foot of the list) ──────────────────────────────────────────────
export interface SignalLike { symbol: string; direction: 'long' | 'short' }
/** The strip's second line: the first two signals, «BTCUSDT خرید · ETHUSDT فروش». */
export function signalSummary(open: SignalLike[], en: boolean): string {
  return open.slice(0, 2).map((s) => `${s.symbol} ${s.direction === 'long' ? (en ? 'Buy' : 'خرید') : (en ? 'Sell' : 'فروش')}`).join(' · ');
}
/** Open signals per symbol, for the count beside each row. */
export function signalCounts(open: SignalLike[]): Record<string, number> {
  const out: Record<string, number> = {};
  for (const s of open) out[s.symbol] = (out[s.symbol] ?? 0) + 1;
  return out;
}
