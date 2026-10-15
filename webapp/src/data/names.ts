// The phone's instrument names (core/symbols SymbolNames.kt), copied to generated/names.json at
// build time. The row subtitle follows SymbolModels.listDescription: a pair is «دلار/ین», a coin
// «بیت‌کوین/تتر», an index its own name; a listing the phone has never named shows no subtitle.
import type { Market } from './types';

type NameMaps = Record<string, Record<string, string>>;
let maps: NameMaps = {};

export function loadNames(base: string): Promise<void> {
  return fetch(`${base}generated/names.json`).then((r) => r.json()).then((m: NameMaps) => { maps = m; }).catch(() => undefined);
}

const pick = (map: string, code: string, en: boolean) => maps[en ? `${map}_EN` : map]?.[code];

/** The canonical name the phone files an instrument under (NAS100 → US100, XTIUSD → USOIL). */
export function canonical(symbol: string): string {
  return maps.ALIASES?.[symbol] ?? symbol;
}

export function describe(symbol: string, market: Market, base: string, quote: string, en: boolean): string | undefined {
  const c = canonical(symbol);
  if (market === 'indices') return pick('INDEX', c, en);
  if (market === 'energy') return pick('ENERGY', c, en);
  if (market === 'forex' || market === 'metals') {
    const short = (code: string) => pick('CURRENCY_SHORT', code, en) ?? pick('CURRENCY', code, en) ?? pick('METAL', code, en);
    const a = short(base);
    const b = short(quote);
    return a && b && a !== b ? `${a}/${b}` : a;
  }
  const coin = pick('CRYPTO', maps.BASE_ALIASES?.[base] ?? base, en);
  const q = pick('CRYPTO', quote, en) ?? pick('CURRENCY_SHORT', quote, en);
  return coin && q && coin !== q ? `${coin}/${q}` : undefined;
}

/** Text a search should match: the ticker plus every name the phone gives it. */
export function searchText(symbol: string, market: Market, base: string, quote: string): string {
  return [symbol, describe(symbol, market, base, quote, false), describe(symbol, market, base, quote, true),
    pick('CRYPTO', base, false), pick('CURRENCY', base, false), pick('CURRENCY', quote, false)].filter(Boolean).join(' ').toLowerCase();
}
