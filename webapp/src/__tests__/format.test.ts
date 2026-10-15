import { REGISTRY } from '../engine/indicators.js';
import { STUDIES } from '../engine/indicators_ext_c.js';
import { describe, expect, it } from 'vitest';
import { formatPrice, formatVolume } from '../chart/format';
import { cleanFa, CATALOG } from '../chart/indicatorCatalog';
import { faAgo, faNum } from '../i18n/time';
import { fa, en } from '../i18n/strings';

describe('figures', () => {
  it('prices are Latin with grouping', () => {
    expect(formatPrice(81594.7, 1)).toBe('81,594.7');
    expect(formatPrice(1.120745, 5)).toBe('1.12075');
    expect(/[۰-۹]/.test(formatPrice(1234.5, 2))).toBe(false);
  });
  it('volumes abbreviate', () => {
    expect(formatVolume(0)).toBe('0');
    expect(formatVolume(1500)).toBe('1.50K');
    expect(formatVolume(2_500_000)).toBe('2.50M');
    expect(formatVolume(3_100_000_000)).toBe('3.10B');
  });
  it('prose counts are Persian', () => {
    expect(faNum(25)).toBe('۲۵');
    expect(faAgo(Date.now() - 5 * 60_000)).toBe('۵ دقیقه پیش');
  });
});

describe('copy', () => {
  it('every key exists in both languages', () => {
    expect(Object.keys(en).sort()).toEqual(Object.keys(fa).sort());
    for (const v of Object.values(en)) expect(v.length).toBeGreaterThan(0);
  });
  it('the indicator names drop kasra and the hamza ezafe', () => {
    for (const e of CATALOG) {
      expect(e.fa).not.toMatch(/ِ/);
      expect(e.fa).not.toMatch(/هٔ|ۀ/);
    }
    expect(cleanFa('حجمِ خالص')).toBe('حجم خالص');
  });
  it('the catalogue has all 108 studies of the old registry, the ones added since, and English names', () => {
    const keys = new Set(CATALOG.map((e) => e.key));
    for (const k of Object.keys(REGISTRY)) expect(keys.has(k), k).toBe(true);
    expect(Object.keys(REGISTRY).length).toBe(108);
    expect(CATALOG.length).toBe(Object.keys(STUDIES).length);
    expect(CATALOG.length).toBeGreaterThan(108 + 70);
    for (const e of CATALOG) expect(e.en.length).toBeGreaterThan(1);
  });
});
