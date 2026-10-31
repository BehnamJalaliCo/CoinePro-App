import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ChartHistory, COALESCE_MS, HISTORY_LIMIT, clearChartHistory, chartAction, historyKinds, noteDrawingAbandoned, noteDrawingRecorded, redoChart, undoChart, useChartHistory } from '../state/chartHistory';
import { usePrefs } from '../state/prefs';
import { useStudies } from '../state/studies';
import { useChartBus } from '../chart/chartBus';
import { usePaper, DEFAULT_RULES } from '../state/paper';
import { comparePlan, compareInGrid, maxPanesFor, presetOf, useGrid } from '../state/grid';

describe('chart history: the rules (OLD ChartHistory)', () => {
  it('records, undoes and redoes snapshots; a new step clears redo', () => {
    const h = new ChartHistory<number>();
    h.record(1); h.record(2);
    expect(h.undo(3)).toEqual({ kind: 'chart', step: 2 });
    expect(h.canRedo).toBe(true);
    expect(h.redo(2)).toEqual({ kind: 'chart', step: 3 });
    h.undo(3);
    h.record(7);
    expect(h.canRedo).toBe(false);
  });

  it('drops a step identical to the top and keeps fifty', () => {
    const h = new ChartHistory<number>();
    h.record(1); h.record(1);
    expect(h.kinds()).toEqual(['chart']);
    for (let i = 0; i < 80; i++) h.record(i + 10);
    expect(h.kinds()).toHaveLength(HISTORY_LIMIT);
  });

  it('collapses the frames of one continuous edit into one step', () => {
    let t = 1000;
    const h = new ChartHistory<number>(50, () => t);
    h.record(1, true);
    t += 100; h.record(2, true);
    t += 100; h.record(3, true);
    expect(h.kinds()).toHaveLength(1);
    t += COALESCE_MS; h.record(4, true);
    expect(h.kinds()).toHaveLength(2);
    t += 1; h.record(5);
    expect(h.kinds()).toHaveLength(3);
  });

  it('keeps drawing steps in the same order, and forgets a cancelled one', () => {
    const h = new ChartHistory<number>();
    h.record(1); h.recordDrawing(); h.recordDrawing();
    h.dropDrawing();
    expect(h.kinds()).toEqual(['chart', 'drawing']);
    expect(h.undo(2)).toEqual({ kind: 'drawing' });
    expect(h.undo(2)).toEqual({ kind: 'chart', step: 1 });
    expect(h.redo(1)).toEqual({ kind: 'chart', step: 2 });
    expect(h.redo(2)).toEqual({ kind: 'drawing' });
  });
});

describe('chart history: the live chart', () => {
  const layer = { undo: vi.fn(), redo: vi.fn(), canUndo: () => true, canRedo: () => false, getDrawings: () => [] };
  beforeEach(async () => {
    usePrefs.setState({ symbol: 'BTCUSDT', interval: '60', chartType: 'candles' });
    useStudies.setState({ list: [{ uid: 'vol', key: '__volume', inputs: {} }] });
    useChartHistory.setState({ range: null });
    useChartBus.setState({ drawingLayer: layer as never });
    await Promise.resolve();
    clearChartHistory();
    layer.undo.mockClear(); layer.redo.mockClear();
  });

  it('takes back an interval, a chart type and an indicator in turn, and puts them back', () => {
    usePrefs.getState().setInterval('15');
    usePrefs.getState().setChartType('line');
    const uid = useStudies.getState().add('rsi');
    expect(uid).toBeTruthy();
    expect(useChartBus.getState().canUndo).toBe(true);
    undoChart();
    expect(useStudies.getState().list.some((s) => s.uid === uid)).toBe(false);
    undoChart();
    expect(usePrefs.getState().chartType).toBe('candles');
    undoChart();
    expect(usePrefs.getState().interval).toBe('60');
    expect(useChartBus.getState().canUndo).toBe(false);
    redoChart(); redoChart(); redoChart();
    expect(usePrefs.getState().interval).toBe('15');
    expect(usePrefs.getState().chartType).toBe('line');
    expect(useStudies.getState().list.some((s) => s.uid === uid)).toBe(true);
  });

  it('hands a drawing step to the drawing layer, in the order it happened', () => {
    usePrefs.getState().setInterval('15');
    noteDrawingRecorded();
    expect(historyKinds()).toEqual(['chart', 'drawing']);
    undoChart();
    expect(layer.undo).toHaveBeenCalledTimes(1);
    expect(usePrefs.getState().interval).toBe('15');
    undoChart();
    expect(usePrefs.getState().interval).toBe('60');
    redoChart(); redoChart();
    expect(layer.redo).toHaveBeenCalledTimes(1);
    noteDrawingRecorded(); noteDrawingAbandoned();
    expect(historyKinds()).toEqual(['chart', 'drawing']);
  });

  it('records a range as one step, and nothing when the action changed nothing', () => {
    chartAction(() => { useChartHistory.getState().setRange('1D'); usePrefs.getState().setInterval('1'); });
    expect(historyKinds()).toEqual(['chart']);
    chartAction(() => {});
    expect(historyKinds()).toEqual(['chart']);
    undoChart();
    expect([usePrefs.getState().interval, useChartHistory.getState().range]).toEqual(['60', null]);
  });

  it('starts again on a new symbol, and the switch itself is not a step', async () => {
    usePrefs.getState().setInterval('15');
    usePrefs.getState().setSymbol('ETHUSDT');
    usePrefs.getState().setInterval('240');
    expect(useChartBus.getState().canUndo).toBe(false);
    await Promise.resolve();
    usePrefs.getState().setInterval('60');
    expect(historyKinds()).toEqual(['chart']);
  });
});

describe('paper: reverse and reduce only', () => {
  beforeEach(() => { usePaper.setState({ balance: 10_000, positions: [], history: [], orders: [], fills: [], lastError: null, rules: { ...DEFAULT_RULES, spreadPercent: 0, slippagePercent: 0, takerFeePercent: 0 } }); });

  it('closes and opens the same size on the other side, at one price', () => {
    const id = usePaper.getState().open('BTCUSDT', 'long', 2, 100)!;
    const next = usePaper.getState().reverse(id, 110);
    const st = usePaper.getState();
    expect(next).toBeTruthy();
    expect(st.positions).toHaveLength(1);
    expect(st.positions[0]).toMatchObject({ side: 'short', qty: 2, entry: 110 });
    expect(st.history[0]).toMatchObject({ reason: 'rev', exit: 110 });
    expect(st.history[0]!.pnl).toBeCloseTo(20);
    expect(st.fills).toHaveLength(3);
  });

  it('refuses a reduce-only order with nothing on the other side', () => {
    expect(usePaper.getState().place({ symbol: 'BTCUSDT', side: 'short', type: 'market', qty: 1, reduceOnly: true }, 100)).toBeNull();
    expect(usePaper.getState().lastError).toBe('reduce');
    usePaper.getState().open('BTCUSDT', 'short', 1, 100);
    expect(usePaper.getState().place({ symbol: 'BTCUSDT', side: 'short', type: 'market', qty: 1, reduceOnly: true }, 100)).toBeNull();
  });

  it('only reduces, capped at the position, and never opens the other way', () => {
    usePaper.getState().open('BTCUSDT', 'long', 2, 100);
    expect(usePaper.getState().place({ symbol: 'BTCUSDT', side: 'short', type: 'market', qty: 0.5, reduceOnly: true }, 110)).toBeTruthy();
    expect(usePaper.getState().positions[0]).toMatchObject({ side: 'long', qty: 1.5 });
    usePaper.getState().place({ symbol: 'BTCUSDT', side: 'short', type: 'market', qty: 5, reduceOnly: true }, 110);
    expect(usePaper.getState().positions).toHaveLength(0);
    expect(usePaper.getState().history).toHaveLength(2);
  });

  it('a resting reduce-only order is capped when placed and opens nothing when it fills', () => {
    usePaper.getState().open('ETHUSDT', 'long', 1, 100);
    usePaper.getState().place({ symbol: 'ETHUSDT', side: 'short', type: 'limit', qty: 3, limitPrice: 120, reduceOnly: true, sl: 90 }, 100);
    const o = usePaper.getState().orders[0]!;
    expect(o).toMatchObject({ qty: 1, reduceOnly: true, sl: undefined });
    usePaper.getState().mark('ETHUSDT', 121);
    expect(usePaper.getState().positions).toHaveLength(0);
    expect(usePaper.getState().orders).toHaveLength(0);
    expect(usePaper.getState().history[0]!.exit).toBe(120);
  });
});

describe('watchlist «تحلیل»: the list side by side', () => {
  it('caps the panes as the window does', () => {
    expect([maxPanesFor(390), maxPanesFor(1024), maxPanesFor(1600)]).toEqual([2, 8, 16]);
  });

  it('picks a layout that fits the count and cuts to one the layouts offer', () => {
    expect(comparePlan(['BTCUSDT'], 1600)).toBeNull();
    expect(comparePlan(['BTCUSDT', 'BTCUSDT'], 1600)).toBeNull();
    expect(comparePlan(['A', 'B', 'C'], 1600)).toEqual({ grid: '3c', symbols: ['A', 'B', 'C'] });
    expect(comparePlan(['A', 'B', 'C', 'D'], 390)).toEqual({ grid: '2h', symbols: ['A', 'B'] });
    const eleven = 'ABCDEFGHIJK'.split('');
    const plan = comparePlan(eleven, 1600)!;
    expect(plan.symbols).toHaveLength(10);
    expect(presetOf(plan.grid).cells).toBe(10);
    for (let n = 2; n <= 16; n++) {
      const p = comparePlan(Array.from({ length: n }, (_, i) => `S${i}`), 1600)!;
      expect(presetOf(p.grid).cells).toBe(p.symbols.length);
    }
  });

  it('puts the first market on the main chart and the rest in the next charts', () => {
    usePrefs.setState({ symbol: 'XAUUSD', interval: '240' });
    expect(compareInGrid(['BTCUSDT', 'ETHUSDT', 'SOLUSDT', 'EURUSD'], 1600)).toBe(4);
    const g = useGrid.getState();
    expect(g.grid).toBe('4');
    expect(usePrefs.getState().symbol).toBe('BTCUSDT');
    expect(g.cells.slice(0, 3).map((c) => [c.symbol, c.interval])).toEqual([['ETHUSDT', '240'], ['SOLUSDT', '240'], ['EURUSD', '240']]);
    expect(g.active).toBe(0);
  });
});
