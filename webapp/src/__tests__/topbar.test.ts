import { beforeEach, describe, expect, it } from 'vitest';
import { backendOf, digitsFor, exprLegs, instrumentOf, isExpression, marketOf, normaliseExpression } from '../data/symbols';
import { canDraw } from '../data/candles';
import { chartPath, readAddress, readSymbol } from '../route';
import { PRESETS, activeChart, applyInterval, applySymbol, cellCount, gridTemplate, presetOf, useGrid } from '../state/grid';
import { usePrefs } from '../state/prefs';
import { useStudies } from '../state/studies';
import { useCompare } from '../state/compare';
import { recentLayouts, useLayouts } from '../state/layouts';
import { logoParts } from '../ui/SymbolLogo';
import { logicalToTime, timeToLogical } from '../chart/LiteChart';

const loc = (path: string, search = '') => ({ pathname: path, search } as Location);

describe('spreads and formulas', () => {
  it('tells an expression from a plain symbol', () => {
    expect(exprLegs('BTCUSDT/ETHUSDT')).toEqual(['BTCUSDT', 'ETHUSDT']);
    expect(exprLegs('(EURUSD+GBPUSD)/2')).toEqual(['EURUSD', 'GBPUSD']);
    expect(exprLegs('XAUUSD*2')).toEqual(['XAUUSD']);
    expect(isExpression('BTCUSDT')).toBe(false);
    expect(isExpression('(BTCUSDT)')).toBe(false);
    expect(isExpression('BTCUSDT/')).toBe(false);
    expect(normaliseExpression(' btcusdt / ethusdt ')).toBe('BTCUSDT/ETHUSDT');
    expect(normaliseExpression('<script>')).toBeNull();
  });
  it('routes, classifies and formats by its first leg and its own size', () => {
    expect(backendOf('BTCUSDT/ETHUSDT')).toBe('tradeyar');
    expect(backendOf('XAUUSD/XAGUSD')).toBe('coineprofx');
    expect(marketOf('XAUUSD*2')).toBe('metals');
    expect(digitsFor('BTCUSDT/ETHUSDT', 33.7)).toBe(4);
    expect(digitsFor('EURUSD/GBPUSD', 0.85)).toBe(6);
    expect(digitsFor('XAUUSD*2', 8280)).toBe(2);
    const inst = instrumentOf('BTCUSDT/ETHUSDT', 33.7);
    expect(inst.symbol).toBe('BTCUSDT/ETHUSDT');
    expect(inst.nameFa ?? inst.nameEn).toBeUndefined();
  });
  it('draws an interval only every leg can draw', () => {
    expect(canDraw('BTCUSDT/ETHUSDT', '1')).toBe(true);
    expect(canDraw('BTCUSDT/XAUUSD', '1')).toBe(false);
    expect(canDraw('BTCUSDT/XAUUSD', '45')).toBe(true);
  });
  it('never has artwork, so never reaches a list', () => {
    expect(logoParts('XAUUSD*2', { 'metal:GOLD': 'gold.svg' })).toEqual({ primary: null, secondary: null });
  });
});

describe('the address', () => {
  it('carries a spread encoded in one segment', () => {
    expect(chartPath('BTCUSDT/ETHUSDT', '60')).toBe('/next/BTCUSDT%2FETHUSDT/1h');
    expect(chartPath('BTCUSDT', '45')).toBe('/next/BTCUSDT/45m');
    expect(readAddress(loc('/next/BTCUSDT%2FETHUSDT/1h'))).toEqual({ symbol: 'BTCUSDT/ETHUSDT', interval: '60' });
    expect(readAddress(loc('/next/%28EURUSD%2BGBPUSD%29%2F2/4h'))).toEqual({ symbol: '(EURUSD+GBPUSD)/2', interval: '240' });
  });
  it('reads derived intervals', () => {
    expect(readAddress(loc('/next/ETHUSDT/45m'))).toEqual({ symbol: 'ETHUSDT', interval: '45' });
    expect(readAddress(loc('/next/XAUUSD/3M'))).toEqual({ symbol: 'XAUUSD', interval: '3M' });
    expect(readAddress(loc('/next/XAUUSD/3D'))).toEqual({ symbol: 'XAUUSD', interval: '3D' });
  });
  it('a pair written with a slash is one instrument', () => {
    expect(readSymbol('EUR/USD')).toBe('EURUSD');
    expect(readSymbol('btc/usdt')).toBe('BTCUSDT');
    expect(readSymbol('XAUUSD/XAGUSD')).toBe('XAUUSD/XAGUSD');
  });
});

describe('multi-chart presets', () => {
  it('has every old Pro-Chart arrangement and TradingView’s up to eight', () => {
    const ids = PRESETS.map((p) => p.id);
    expect(new Set(ids).size).toBe(ids.length);
    for (const id of ['1', '2v', '2h', '3', '3c', '3r', '4', '4c', '4r', '6', '8']) expect(ids).toContain(id);
    expect(Math.max(...PRESETS.map((p) => p.cells))).toBe(8);
  });
  it('each preset is a rectangle of rectangles with the right number of charts', () => {
    for (const p of PRESETS) {
      const rows = p.areas.map((r) => r.split(' '));
      expect(new Set(rows.map((r) => r.length)).size, p.id).toBe(1);
      const letters = [...new Set(rows.flat())];
      expect(letters.length, p.id).toBe(p.cells);
      for (const a of letters) {
        const cells: [number, number][] = [];
        rows.forEach((r, i) => r.forEach((x, j) => { if (x === a) cells.push([i, j]); }));
        const r0 = Math.min(...cells.map((c) => c[0])); const r1 = Math.max(...cells.map((c) => c[0]));
        const c0 = Math.min(...cells.map((c) => c[1])); const c1 = Math.max(...cells.map((c) => c[1]));
        expect(cells.length, `${p.id}/${a}`).toBe((r1 - r0 + 1) * (c1 - c0 + 1));
      }
      expect(gridTemplate(p.id).gridTemplateAreas.split("' '").length).toBe(rows.length);
    }
    expect(presetOf('nonsense').id).toBe('1');
    expect(cellCount('6')).toBe(6);
  });
});

describe('the top bar drives the active chart', () => {
  beforeEach(() => {
    usePrefs.setState({ symbol: 'BTCUSDT', interval: '60', chartType: 'candles' });
    useGrid.setState({ grid: '4', active: 0, sync: { symbol: false, interval: false, crosshair: true, time: false, dateRange: false } });
    useGrid.getState().setCells([{ symbol: 'ETHUSDT', interval: '60' }, { symbol: 'XAUUSD', interval: '60' }, { symbol: 'EURUSD', interval: '60' }]);
  });
  it('the main chart by default', () => {
    applySymbol('SOLUSDT');
    expect(usePrefs.getState().symbol).toBe('SOLUSDT');
    expect(useGrid.getState().cells[0]!.symbol).toBe('ETHUSDT');
  });
  it('the chart picked', () => {
    useGrid.getState().setActive(2);
    applySymbol('GBPUSD');
    applyInterval('240');
    expect(useGrid.getState().cells[1]).toMatchObject({ symbol: 'GBPUSD', interval: '240' });
    expect(usePrefs.getState().symbol).toBe('BTCUSDT');
    expect(activeChart()).toMatchObject({ symbol: 'GBPUSD', interval: '240' });
  });
  it('every chart with sync on', () => {
    useGrid.getState().setSync({ symbol: true, interval: true });
    applySymbol('SOLUSDT');
    applyInterval('15');
    expect(usePrefs.getState()).toMatchObject({ symbol: 'SOLUSDT', interval: '15' });
    expect(useGrid.getState().cells.slice(0, 3).every((c) => c.symbol === 'SOLUSDT' && c.interval === '15')).toBe(true);
  });
  it('a cell beyond the layout cannot be active', () => {
    useGrid.getState().setGrid('2v');
    useGrid.getState().setActive(3);
    expect(useGrid.getState().active).toBe(0);
  });
});

describe('time sync between charts', () => {
  const bars = [0, 1, 2, 3].map((i) => ({ t: 1000 + i * 60, o: 1, h: 1, l: 1, c: 1, v: 1 }));
  it('maps a bar index to its time and back, beyond the last bar too', () => {
    expect(logicalToTime(bars, 2, 60)).toBe(1120);
    expect(logicalToTime(bars, 6, 60)).toBe(1360);
    expect(timeToLogical(bars, 1130, 60)).toBe(2);
    expect(timeToLogical(bars, 1360, 60)).toBe(6);
  });
});

describe('layouts', () => {
  beforeEach(() => {
    localStorage.clear();
    useLayouts.setState({ layouts: [], current: null, autosave: false, dirty: false });
    usePrefs.setState({ symbol: 'BTCUSDT', interval: '60', chartType: 'candles' });
    useGrid.getState().setGrid('1');
  });
  it('keeps the grid, the charts in it, studies, compares and drawings, and brings them back', () => {
    useGrid.getState().setGrid('3');
    useGrid.getState().setCells([{ symbol: 'XAUUSD', interval: '240', chartType: 'line' }, { symbol: 'EURUSD', interval: '15' }]);
    useStudies.setState({ list: [{ uid: 'vol', key: '__volume', inputs: {} }, { uid: 'r', key: 'rsi', inputs: { period: 21 } }] });
    useCompare.setState({ symbols: ['ETHUSDT'] });
    localStorage.setItem('pc.drawings.v1.XAUUSD', JSON.stringify([{ type: 'hline', p: 4000 }]));
    expect(useLayouts.getState().save('Gold desk')).toBe(true);

    useGrid.getState().setGrid('1');
    usePrefs.setState({ symbol: 'SOLUSDT', interval: '5' });
    useStudies.setState({ list: [] });
    useCompare.setState({ symbols: [] });
    localStorage.removeItem('pc.drawings.v1.XAUUSD');

    useLayouts.getState().load('Gold desk');
    expect(usePrefs.getState()).toMatchObject({ symbol: 'BTCUSDT', interval: '60' });
    expect(useGrid.getState().grid).toBe('3');
    expect(useGrid.getState().cells[0]).toMatchObject({ symbol: 'XAUUSD', interval: '240', chartType: 'line' });
    expect(useStudies.getState().list.map((s) => s.key)).toEqual(['__volume', 'rsi']);
    expect(useCompare.getState().symbols).toEqual(['ETHUSDT']);
    expect(JSON.parse(localStorage.getItem('pc.drawings.v1.XAUUSD') ?? '[]')).toEqual([{ type: 'hline', p: 4000 }]);
  });
  it('rename, copy, recently used, delete', () => {
    const st = useLayouts.getState();
    st.save('A');
    st.save('B');
    useLayouts.getState().rename('B', 'C');
    expect(useLayouts.getState().current).toBe('C');
    useLayouts.getState().copy('C (copy)');
    expect(useLayouts.getState().layouts.map((l) => l.name).sort()).toEqual(['A', 'C', 'C (copy)']);
    expect(recentLayouts().map((l) => l.name)).not.toContain('C (copy)');
    useLayouts.getState().remove('A');
    expect(useLayouts.getState().layouts.some((l) => l.name === 'A')).toBe(false);
  });
  it('saving with no name asks for one', () => {
    let asked = false;
    const on = () => { asked = true; };
    window.addEventListener('pc:layout-dialog', on);
    expect(useLayouts.getState().save()).toBe(false);
    window.removeEventListener('pc:layout-dialog', on);
    expect(asked).toBe(true);
  });
  it('a new layout is one chart with volume, unnamed', () => {
    useGrid.getState().setGrid('4');
    useLayouts.getState().save('X');
    useLayouts.getState().newLayout();
    expect(useLayouts.getState().current).toBeNull();
    expect(useGrid.getState().grid).toBe('1');
    expect(useStudies.getState().list.map((s) => s.key)).toEqual(['__volume']);
  });
});

describe('trading-session shading', () => {
  it('bands the chosen sessions and their overlaps, Sydney across midnight', async () => {
    const { sessionBands } = await import('../shell/SessionShading');
    const day = Date.UTC(2026, 9, 14) / 1000;
    const london = sessionBands(day, day + 86400, ['london'], true);
    expect(london).toEqual([{ id: 'london', from: day + 8 * 3600, to: day + 17 * 3600, color: expect.any(String), edge: expect.any(String) }]);
    const ln = sessionBands(day, day + 86400, ['london', 'newyork'], true).filter((b) => b.id === 'overlap');
    expect(ln.map((b) => [(b.from - day) / 3600, (b.to - day) / 3600])).toEqual([[13, 17]]);
    const st = sessionBands(day, day + 86400, ['sydney', 'tokyo'], true).filter((b) => b.id === 'overlap');
    expect(st.map((b) => [(b.from - day) / 3600, (b.to - day) / 3600])).toEqual([[0, 6]]);
    expect(sessionBands(day, day + 86400, [], true)).toEqual([]);
  });
});
