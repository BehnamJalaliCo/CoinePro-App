import { describe, expect, it } from 'vitest';
import { decodeCookie, encodeCookie, merge } from '../firstrun/store';

describe('first-run answers in a cookie', () => {
  it('round-trips every state', () => {
    const states = [
      { welcome: false, starter: false, reader: null, rise: 'green' as const },
      { welcome: true, starter: false, reader: null, rise: 'green' as const },
      { welcome: true, starter: true, reader: 'trader' as const, rise: 'red' as const },
      { welcome: true, starter: true, reader: 'simple' as const, rise: 'green' as const },
    ];
    for (const s of states) expect(decodeCookie(encodeCookie(s))).toEqual(s);
  });

  it('refuses anything else', () => {
    for (const bad of ['', 'w.s', 'x.s.trader.green', 'w.s.boss.green', 'w.s.pro.blue', null, undefined]) expect(decodeCookie(bad)).toBeNull();
  });

  it('a step done in either record is done', () => {
    const local = { welcome: false, starter: false, reader: null, rise: 'green' as const };
    const cookie = { welcome: true, starter: true, reader: 'pro' as const, rise: 'red' as const };
    expect(merge(local, cookie)).toEqual({ welcome: true, starter: true, reader: 'pro', rise: 'red' });
    expect(merge(null, cookie)).toEqual(cookie);
    expect(merge(local, null)).toEqual(local);
    expect(merge(null, null)).toBeNull();
  });
});
