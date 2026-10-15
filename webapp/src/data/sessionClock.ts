// When a market next opens or closes, from the same rules as data/sessions.ts (forex, metals,
// energy and the CFD indices trade Sunday 22:00 to Friday 22:00 UTC; all but forex break for the
// 21:00–22:00 UTC rollover; crypto never closes), for the details card's countdown and About card.
import { marketStatus } from './sessions';
import type { Market } from './types';

/** The next change of state (ms) and whether the market is open now; null for a market that never closes. */
export function nextSessionChange(market: Market, now = Date.now()): { open: boolean; at: number } | null {
  if (market === 'crypto') return null;
  const open = marketStatus(market, new Date(now));
  let t = Math.floor(now / 3_600_000) * 3_600_000 + 3_600_000;
  for (let i = 0; i < 24 * 9; i++, t += 3_600_000) {
    if (marketStatus(market, new Date(t)) !== open) return { open, at: t };
  }
  return null;
}

/** "02:15:09", or "3d 04:10" past a day. */
export function countdown(ms: number): string {
  const s = Math.max(0, Math.floor(ms / 1000));
  const d = Math.floor(s / 86400);
  const hh = String(Math.floor((s % 86400) / 3600)).padStart(2, '0');
  const mm = String(Math.floor((s % 3600) / 60)).padStart(2, '0');
  const ss = String(s % 60).padStart(2, '0');
  return d ? `${d}d ${hh}:${mm}` : `${hh}:${mm}:${ss}`;
}

/** The trading hours, in UTC, as the About card states them. */
export function sessionText(market: Market, fa: boolean): string {
  if (market === 'crypto') return fa ? '۲۴ ساعته، هر روز هفته' : '24/7';
  const base = fa ? 'یکشنبه 22:00 تا جمعه 22:00 UTC' : 'Sun 22:00 – Fri 22:00 UTC';
  if (market === 'forex') return base;
  return fa ? `${base}، وقفه‌ی روزانه 21:00–22:00` : `${base}, daily break 21:00–22:00`;
}

/** Contract size per lot, as the brokers quote it (no feed carries it). */
export function contractSize(symbol: string, market: Market, fa: boolean): string {
  if (market === 'crypto') return fa ? '۱ واحد' : '1 unit';
  if (/^XAU/.test(symbol)) return fa ? '100 اونس' : '100 oz';
  if (/^XAG/.test(symbol)) return fa ? '5,000 اونس' : '5,000 oz';
  if (/^XP[TD]/.test(symbol)) return fa ? '100 اونس' : '100 oz';
  if (market === 'energy') return /XNG|NGAS/.test(symbol) ? (fa ? '10,000 MMBtu' : '10,000 MMBtu') : (fa ? '1,000 بشکه' : '1,000 bbl');
  if (market === 'indices') return fa ? '۱ واحد شاخص' : '1 index unit';
  return fa ? `100,000 ${symbol.slice(0, 3)}` : `100,000 ${symbol.slice(0, 3)}`;
}
