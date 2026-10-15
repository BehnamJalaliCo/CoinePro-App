import { describe, expect, it } from 'vitest';
import { parseInterval, readAddress } from '../route';

const loc = (path: string, search = '') => ({ pathname: path, search } as Location);

describe('parseInterval', () => {
  it.each([
    ['4h', '240'], ['240', '240'], ['H4', '240'], ['15m', '15'], ['M15', '15'], ['1D', '1D'], ['D', '1D'],
    ['d1', '1D'], ['1W', '1W'], ['W1', '1W'], ['1M', '1M'], ['1m', '1'], ['1h', '60'], ['2h', '120'],
  ])('%s → %s', (raw, id) => expect(parseInterval(raw)).toBe(id));
  it('rejects nonsense', () => {
    expect(parseInterval('7x')).toBeNull();
    expect(parseInterval('')).toBeNull();
    expect(parseInterval(null)).toBeNull();
  });
});

describe('readAddress', () => {
  it('reads /next/SYMBOL/INTERVAL', () => {
    expect(readAddress(loc('/next/BTCUSDT/4h'))).toEqual({ symbol: 'BTCUSDT', interval: '240' });
  });
  it('reads the terminal query form', () => {
    expect(readAddress(loc('/next/', '?symbol=xauusd&tf=H1'))).toEqual({ symbol: 'XAUUSD', interval: '60' });
  });
  it('refuses unsafe symbols', () => {
    expect(readAddress(loc('/next/%3Cscript%3E')).symbol).toBeNull();
    expect(readAddress(loc('/next/A')).symbol).toBeNull();
  });
});
