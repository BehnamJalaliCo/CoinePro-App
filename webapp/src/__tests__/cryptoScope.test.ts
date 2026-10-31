import { beforeEach, describe, expect, it } from 'vitest';
import { isForexOrMetal, isOffScope, isScopeRefusal, learnOffScope } from '../data/cryptoScope';
import { useMarket } from '../state/market';

describe('crypto scope (TradeYar TYR-021)', () => {
  beforeEach(() => { localStorage.clear(); });

  it('mirrors TradeYar’s non-crypto rule', () => {
    expect(isForexOrMetal('GOLDUSDT')).toBe(true);
    expect(isForexOrMetal('NAS100USDT')).toBe(true);
    expect(isForexOrMetal('EURUSD')).toBe(true);
    expect(isForexOrMetal('XAUUSD')).toBe(true);
    expect(isForexOrMetal('TSLAUSDT')).toBe(true);
    expect(isForexOrMetal('BTCUSDT')).toBe(false);
    expect(isForexOrMetal('XAUTUSDT')).toBe(false);
    expect(isForexOrMetal('PAXGUSDT')).toBe(false);
  });

  it('keeps measured refusals and share baskets out, real coins in', () => {
    for (const s of ['GOLDUSDT', 'SILVERUSDT', 'EURUSDT', 'USD1USDT', 'FOXAUSDT', 'HK0700USDT', 'HK9999USDT', 'CSOPSAMSUNG2LUSDT', 'KODEX200USDT']) expect(isOffScope(s)).toBe(true);
    for (const s of ['BTCUSDT', 'ETHUSDT', 'XAUTUSDT', 'PAXGUSDT', '1INCHUSDT', '1000SATSUSDT', 'C98USDT']) expect(isOffScope(s)).toBe(false);
  });

  it('reads only a 422 carrying TYR-021 as the scope gate', () => {
    expect(isScopeRefusal(422, { error: { code: 'TYR-021', message: 'x' } })).toBe(true);
    expect(isScopeRefusal(422, { detail: 'validation' })).toBe(false);
    expect(isScopeRefusal(500, { error: { code: 'TYR-021' } })).toBe(false);
  });

  it('a learned refusal leaves every list and is remembered', () => {
    useMarket.getState().put([{ symbol: 'ABCUSDT', last: 1, change: 0, changePct: 0, ts: 0 } as never]);
    expect(isOffScope('ABCUSDT')).toBe(false);
    learnOffScope('ABCUSDT');
    expect(isOffScope('ABCUSDT')).toBe(true);
    expect('ABCUSDT' in useMarket.getState().tickers).toBe(false);
    expect(JSON.parse(localStorage.getItem('pc.offscope.v1') ?? '[]')).toContain('ABCUSDT');
  });
});
