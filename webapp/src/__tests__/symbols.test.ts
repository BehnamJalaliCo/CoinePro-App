import { describe, expect, it } from 'vitest';
import { backendOf, digitsFor, instrumentOf, marketOf } from '../data/symbols';
import { intervalOfTimeframe } from '../data/signals';
import { marketStatus } from '../data/sessions';

describe('symbols', () => {
  it('routes by backend', () => {
    expect(backendOf('BTCUSDT')).toBe('tradeyar');
    expect(backendOf('XAUUSD')).toBe('coineprofx');
    expect(backendOf('EURUSD')).toBe('coineprofx');
  });
  it('classifies markets', () => {
    expect(marketOf('XAUUSD')).toBe('metals');
    expect(marketOf('XAGUSD')).toBe('metals');
    expect(marketOf('US30')).toBe('indices');
    expect(marketOf('XTIUSD')).toBe('energy');
    expect(marketOf('GBPJPY')).toBe('forex');
    expect(marketOf('SOLUSDT')).toBe('crypto');
  });
  it('uses the instrument precision rules (forex 5, JPY 3, XAG 3, gold 2)', () => {
    expect(digitsFor('EURUSD')).toBe(5);
    expect(digitsFor('USDJPY')).toBe(3);
    expect(digitsFor('XAGUSD')).toBe(3);
    expect(digitsFor('XAUUSD')).toBe(2);
    expect(digitsFor('BTCUSDT', 81_000)).toBe(1);
    expect(digitsFor('DOGEUSDT', 0.08)).toBe(5);
    expect(digitsFor('PEPEUSDT', 0.0000041)).toBe(8);
  });
  it('splits base and quote', () => {
    expect(instrumentOf('ETHUSDT')).toMatchObject({ base: 'ETH', quote: 'USDT', market: 'crypto' });
    expect(instrumentOf('EURUSD')).toMatchObject({ base: 'EUR', quote: 'USD', market: 'forex' });
  });
});

describe('signal timeframes', () => {
  it.each([['15m', '15'], ['M15', '15'], ['H4', '240'], ['1h', '60'], ['D1', '1D'], ['1d', '1D'], ['W1', '1W']])('%s → %s', (tf, id) => {
    expect(intervalOfTimeframe(tf)).toBe(id);
  });
  it('unknown → null', () => expect(intervalOfTimeframe('weird')).toBeNull());
});

describe('market hours', () => {
  it('crypto never closes', () => expect(marketStatus('crypto', new Date(Date.UTC(2026, 9, 17, 12)))).toBe(true));
  it('forex is closed on Saturday and Sunday before 22:00 UTC', () => {
    expect(marketStatus('forex', new Date(Date.UTC(2026, 9, 17, 12)))).toBe(false); // Saturday
    expect(marketStatus('forex', new Date(Date.UTC(2026, 9, 18, 21)))).toBe(false); // Sunday 21:00
    expect(marketStatus('forex', new Date(Date.UTC(2026, 9, 18, 22, 30)))).toBe(true);
    expect(marketStatus('forex', new Date(Date.UTC(2026, 9, 16, 22, 30)))).toBe(false); // Friday after close
  });
  it('metals pause at 21:00 UTC on weekdays', () => {
    expect(marketStatus('metals', new Date(Date.UTC(2026, 9, 14, 21, 15)))).toBe(false);
    expect(marketStatus('forex', new Date(Date.UTC(2026, 9, 14, 21, 15)))).toBe(true);
  });
});
