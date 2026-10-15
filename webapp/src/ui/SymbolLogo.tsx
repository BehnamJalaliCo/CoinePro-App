// The instrument's artwork, from the same sources the phone uses (design/asset-logos). A pair of
// currencies is two overlapping flags, as on TradingView. A symbol with no artwork never reaches a
// list (SymbolArtwork.covers on the phone; `hasLogo` here), so the fallback below is for the brief
// moment before the map has loaded, never a lettered disc left on screen.
import { useEffect, useState } from 'react';

const BASE = import.meta.env.BASE_URL;
let mapPromise: Promise<Record<string, string>> | null = null;
let map: Record<string, string> | null = null;

export function loadLogoMap(): Promise<Record<string, string>> {
  if (!mapPromise) {
    mapPromise = fetch(`${BASE}generated/logos.json`).then((r) => r.json()).then((m) => (map = m)).catch(() => (map = {}));
  }
  return mapPromise;
}

const CURRENCY_COUNTRY: Record<string, string> = {
  USD: 'US', EUR: 'EU', GBP: 'GB', JPY: 'JP', CHF: 'CH', AUD: 'AU', NZD: 'NZ', CAD: 'CA', CNY: 'CN', CNH: 'CN',
  HKD: 'CN', SGD: 'SG', SEK: 'SE', NOK: 'NO', DKK: 'DK', PLN: 'PL', CZK: 'CZ', HUF: 'HU', TRY: 'TR', ZAR: 'ZA',
  MXN: 'MX', BRL: 'BR', INR: 'IN', KRW: 'KR', RUB: 'RU', ILS: 'IL', SAR: 'SA', AED: 'AE', THB: 'TH',
};
const METAL: Record<string, string> = { XAU: 'GOLD', XAG: 'SILVER', XPT: 'PLATINUM', XPD: 'PALLADIUM' };
const CRYPTO_QUOTES = ['USDT', 'USDC', 'FDUSD', 'BUSD', 'BTC', 'ETH', 'USD'];

export interface LogoParts { primary: string | null; secondary: string | null }

export function logoParts(symbol: string, m: Record<string, string> | null = map): LogoParts {
  if (!m) return { primary: null, secondary: null };
  const s = symbol.toUpperCase();
  const metal = METAL[s.slice(0, 3)];
  if (metal && m[`metal:${metal}`]) return { primary: m[`metal:${metal}`] ?? null, secondary: null };
  if (/^[A-Z]{6}$/.test(s) && CURRENCY_COUNTRY[s.slice(0, 3)] && CURRENCY_COUNTRY[s.slice(3)]) {
    return {
      primary: m[`country:${CURRENCY_COUNTRY[s.slice(0, 3)]}`] ?? null,
      secondary: m[`country:${CURRENCY_COUNTRY[s.slice(3)]}`] ?? null,
    };
  }
  for (const q of CRYPTO_QUOTES) {
    if (s.endsWith(q) && s.length > q.length) {
      const base = s.slice(0, -q.length);
      if (m[base]) return { primary: m[base] ?? null, secondary: null };
    }
  }
  return { primary: m[s] ?? null, secondary: null };
}

export function hasLogo(symbol: string): boolean {
  return logoParts(symbol).primary !== null;
}

export function SymbolLogo({ symbol, size = 20 }: { symbol: string; size?: number }) {
  const [, setReady] = useState(map !== null);
  useEffect(() => { if (!map) loadLogoMap().then(() => setReady(true)); }, []);
  const { primary, secondary } = logoParts(symbol);
  if (!primary) return <span className="logo logo-empty" style={{ width: size, height: size }} aria-hidden />;
  if (secondary) {
    const small = Math.round(size * 0.72);
    return (
      <span className="logo logo-pair" style={{ width: size, height: size }} aria-hidden>
        <img src={`${BASE}generated/logos/${primary}`} width={small} height={small} alt="" className="a" />
        <img src={`${BASE}generated/logos/${secondary}`} width={small} height={small} alt="" className="b" />
      </span>
    );
  }
  return <img className="logo" src={`${BASE}generated/logos/${primary}`} width={size} height={size} alt="" />;
}
