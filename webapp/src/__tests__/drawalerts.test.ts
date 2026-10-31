import { beforeEach, describe, expect, it } from 'vitest';
import { DrawingLayer, type DrawingObject } from '../engine/drawings.js';
import { alertKindOf, alertsOn, anchorsOf, boundsAt, levelAt, noteDrawings, orphanIds, storedOrphans, findDrawing } from '../state/drawingLinks';
import { drawingGeometry, followDrawings, startAlertWatch, useAlerts } from '../state/alerts';
import { useMarket } from '../state/market';
import { alertDetail } from '../chart/DrawingActions';
import { guardDrawingDeletes, useDrawingDelete } from '../chart/drawingAlerts';
import { drawStep, placesPoints, pointsOf } from '../chart/PlacementCursor';
import { sketchPath } from '../shell/AlertDrawingMark';
import { FLOAT_GROUPS } from '../shell/PhoneDrawing';
import { usePrefs } from '../state/prefs';

function mkLayer(): DrawingLayer {
  const ctx = new Proxy({}, { get: () => () => undefined, set: () => true });
  const canvas = { getContext: () => ctx, width: 800, height: 400, style: {} as Record<string, string>, getBoundingClientRect: () => ({ left: 0, top: 0, width: 800, height: 400 }), addEventListener() {}, removeEventListener() {} };
  const chart = { timeScale: () => ({ timeToCoordinate: (t: number) => t, coordinateToTime: (x: number) => x, getVisibleLogicalRange: () => null, options: () => ({ barSpacing: 6 }) }), priceScale: () => ({ width: () => 60 }) };
  const l = new DrawingLayer(canvas, chart);
  const any = l as unknown as Record<string, unknown>;
  any.series = { priceToCoordinate: (p: number) => p, coordinateToPrice: (y: number) => y };
  any._x = (t: number) => t; any._y = (p: number) => p; any._t = (x: number) => x; any._p = (y: number) => y;
  l.render = () => undefined;
  return l;
}

beforeEach(() => {
  localStorage.clear();
  useAlerts.setState({ list: [] });
  useDrawingDelete.setState({ pending: null });
});

describe('stable drawing ids', () => {
  it('every drawing gets an id; a copy gets a new one; loaded ones keep theirs', () => {
    const l = mkLayer();
    l.setDrawings([{ type: 'hline', p: 5 }, { type: 'hline', p: 6, id: 'keep' }]);
    expect(l.idsGiven).toBe(true);
    const [a, b] = l.getDrawings();
    expect(typeof a!.id).toBe('string');
    expect(b!.id).toBe('keep');
    const i = l.clone(1);
    expect(l.getDrawings()[i]!.id).not.toBe('keep');
    l.pasteMany([{ type: 'hline', p: 7, id: 'keep' }]);
    const ids = l.getDrawings().map((d) => d.id);
    expect(new Set(ids).size).toBe(ids.length);
    l.setDrawings([{ type: 'hline', p: 1, id: 'x' }]);
    expect(l.idsGiven).toBe(false);
  });
  it('a moved drawing keeps its id, and removeIds is one undo step', () => {
    const l = mkLayer();
    l.setDrawings([{ type: 'hline', p: 5, id: 'a' }, { type: 'hline', p: 6, id: 'b' }]);
    l.bringToFront(0);
    expect(l.indexOfId('a')).toBe(1);
    l.removeIds(['a', 'b']);
    expect(l.getDrawings().length).toBe(0);
    l.undo();
    expect(l.getDrawings().map((d) => d.id)).toEqual(['b', 'a']);
  });
});

describe('drawing geometry (OLD AlertEvaluation.boundsAt)', () => {
  it('a rectangle is its top and bottom', () => {
    expect(boundsAt({ type: 'rect', p0: { t: 0, p: 110 }, p1: { t: 10, p: 100 } }, 5)).toEqual([100, 110]);
  });
  it('a parallel channel is its two rails, sliding with time', () => {
    const ch: DrawingObject = { type: 'channel', pts: [{ t: 0, p: 100 }, { t: 10, p: 110 }, { t: 0, p: 120 }] };
    expect(boundsAt(ch, 20)).toEqual([120, 140]);
  });
  it('flat top/bottom and disjoint channels', () => {
    expect(boundsAt({ type: 'flatchannel', pts: [{ t: 0, p: 100 }, { t: 10, p: 110 }, { t: 5, p: 90 }] }, 10)).toEqual([90, 110]);
    expect(boundsAt({ type: 'disjointchannel', pts: [{ t: 0, p: 100 }, { t: 10, p: 100 }, { t: 0, p: 120 }, { t: 10, p: 130 }] }, 10)).toEqual([100, 130]);
  });
  it('a line extends past its anchors; a time-only drawing has no level', () => {
    expect(levelAt({ type: 'trend', p0: { t: 0, p: 10 }, p1: { t: 10, p: 20 } }, 20)).toBe(30);
    expect(boundsAt({ type: 'vline', t: 5 }, 5)).toEqual([]);
    expect(alertKindOf({ type: 'vline', t: 5 })).toBeNull();
  });
  it('kinds: band, line, price', () => {
    expect(alertKindOf({ type: 'rect', p0: { t: 0, p: 1 }, p1: { t: 1, p: 2 } })).toBe('band');
    expect(alertKindOf({ type: 'trend', p0: { t: 0, p: 1 }, p1: { t: 1, p: 2 } })).toBe('line');
    expect(alertKindOf({ type: 'hline', p: 3 })).toBe('price');
  });
  it('one anchor for a horizontal mark', () => {
    expect(anchorsOf({ type: 'hline', p: 3 })).toEqual([{ t: 0, p: 3 }]);
    expect(anchorsOf({ type: 'hray', p0: { t: 1, p: 9 }, p1: { t: 1, p: 9 } })).toEqual([{ t: 1, p: 9 }]);
    expect(anchorsOf({ type: 'channel', pts: [{ t: 0, p: 1 }, { t: 1, p: 2 }, { t: 0, p: 3 }] }).length).toBe(3);
  });
});

describe('alert ↔ drawing links (OLD AlertDrawingLinks)', () => {
  const alerts = [
    { id: '1', symbol: 'BTCUSDT', drawingId: 'a' },
    { id: '2', symbol: 'btcusdt', drawingId: 'b' },
    { id: '3', symbol: 'ETHUSDT', drawingId: 'a' },
    { id: '4', symbol: 'BTCUSDT' },
  ];
  it('finds the alerts on a drawing, symbol compared case-insensitively', () => {
    expect(alertsOn(alerts, 'BTCUSDT', ['a', 'b']).map((a) => a.id)).toEqual(['1', '2']);
  });
  it('an unread symbol gets no verdict; a missing drawing orphans', () => {
    expect([...orphanIds(alerts, new Map())]).toEqual([]);
    expect([...orphanIds(alerts, new Map([['BTCUSDT', new Set(['a'])]]))]).toEqual([]);
    expect([...orphanIds(alerts, new Map([['ETHUSDT', new Set(['z'])]]))]).toEqual(['3']);
  });
  it('reads stored drawings: present, gone, orphaned', () => {
    localStorage.setItem('pc.drawings.v1.SOLUSDT', JSON.stringify([{ type: 'hline', p: 1, id: 'h' }]));
    noteDrawings('SOLUSDT', [{ type: 'hline', p: 1, id: 'h' }]);
    expect(findDrawing('SOLUSDT', 'h')?.p).toBe(1);
    expect(findDrawing('SOLUSDT', 'gone')).toBeUndefined();
    expect([...storedOrphans([{ id: 'x', symbol: 'SOLUSDT', drawingId: 'gone' }, { id: 'y', symbol: 'SOLUSDT', drawingId: 'h' }])]).toEqual(['x']);
  });
});

describe('a drawing alert follows its drawing', () => {
  it('stores band / line / price geometry and takes the new one after an edit', () => {
    expect(drawingGeometry({ type: 'rect', p0: { t: 0, p: 10 }, p1: { t: 5, p: 20 } })).toEqual({ value: 10, high: 20 });
    expect(drawingGeometry({ type: 'hline', p: 7 })).toEqual({ value: 7 });
    const id = useAlerts.getState().create({ symbol: 'ZZZ', condition: 'enter_channel', value: 10, high: 20, source: 'line', drawingId: 'r', drawingType: 'rect' });
    followDrawings('ZZZ', [{ type: 'rect', id: 'r', p0: { t: 0, p: 30 }, p1: { t: 5, p: 50 } }]);
    const a = useAlerts.getState().list.find((x) => x.id === id)!;
    expect([a.value, a.high]).toEqual([30, 50]);
    const lid = useAlerts.getState().create({ symbol: 'ZZZ', condition: 'cross', value: 1, source: 'line', line: { t1: 0, p1: 1, t2: 1, p2: 2 }, drawingId: 't', drawingType: 'trend' });
    followDrawings('ZZZ', [{ type: 'trend', id: 't', p0: { t: 0, p: 5 }, p1: { t: 1, p: 6 } }]);
    expect(useAlerts.getState().list.find((x) => x.id === lid)!.line).toEqual({ t1: 0, p1: 5, t2: 1, p2: 6 });
  });
  it('alertDetail carries the id and a shape\'s band', () => {
    usePrefs.setState({ symbol: 'BTCUSDT' });
    const r = alertDetail({ type: 'rect', id: 'r1', p0: { t: 0, p: 10 }, p1: { t: 5, p: 20 } });
    expect(r).toMatchObject({ band: { low: 10, high: 20 }, drawingId: 'r1', drawingType: 'rect' });
    const c = alertDetail({ type: 'channel', id: 'c1', pts: [{ t: 0, p: 1 }, { t: 1, p: 1 }, { t: 0, p: 3 }] });
    expect(c?.band).toEqual({ low: 1, high: 3 });
    expect(alertDetail({ type: 'hline', id: 'h1', p: 5 })).toMatchObject({ price: 5, drawingId: 'h1' });
  });
});

describe('deleting a drawing that carries alerts asks first', () => {
  it('no alert: deletes at once; with alerts: asks, and each answer does what it says', () => {
    const l = mkLayer();
    l.setDrawings([{ type: 'hline', p: 5, id: 'a' }, { type: 'hline', p: 6, id: 'b' }]);
    guardDrawingDeletes(l, () => 'BTCUSDT');
    l.removeAt(1);
    expect(l.getDrawings().length).toBe(1);
    const alertId = useAlerts.getState().create({ symbol: 'BTCUSDT', condition: 'cross', value: 5, drawingId: 'a', drawingType: 'hline' });
    l.removeAt(0);
    expect(l.getDrawings().length).toBe(1);
    expect(useDrawingDelete.getState().pending?.alertIds).toEqual([alertId]);
    useDrawingDelete.getState().answer(null);
    expect(l.getDrawings().length).toBe(1);
    l.selectAt(0); l.removeSelected();
    useDrawingDelete.getState().answer(false);
    expect(l.getDrawings().length).toBe(0);
    expect(useAlerts.getState().list.length).toBe(1);
    l.undo();
    l.removeAt(0);
    useDrawingDelete.getState().answer(true);
    expect(l.getDrawings().length).toBe(0);
    expect(useAlerts.getState().list.length).toBe(0);
  });
});

describe('phone placement', () => {
  it('counts points per tool', () => {
    expect(pointsOf('trend')).toBe(2);
    expect(pointsOf('hline')).toBe(1);
    expect(pointsOf('channel')).toBe(3);
    expect(placesPoints('cursor')).toBe(false);
    expect(placesPoints('brush')).toBe(false);
    expect(placesPoints('trend')).toBe(true);
  });
  it('the banner steps (OLD DrawCursor.step)', () => {
    expect(drawStep(2, 0, false)).toEqual({ index: 1, total: 4, text: 'moveStart' });
    expect(drawStep(2, 0, true)).toEqual({ index: 2, total: 4, text: 'tapFirst' });
    expect(drawStep(2, 1, false)).toEqual({ index: 3, total: 4, text: 'moveNext' });
    expect(drawStep(2, 1, true)).toEqual({ index: 4, total: 4, text: 'tapFinish' });
    expect(drawStep(3, 1, true)).toEqual({ index: 4, total: 6, text: 'tapNext' });
    expect(drawStep(1, 0, true)).toEqual({ index: 2, total: 2, text: 'tapFinish' });
    expect(drawStep(0, 2, true)).toEqual({ index: 0, total: 0, text: 'tapNext' });
  });
  it('the floating toolbar shows every drawing group, not the cursors', () => {
    expect(FLOAT_GROUPS.some((g) => g.id === 'cursors')).toBe(false);
    expect(FLOAT_GROUPS.length).toBeGreaterThan(5);
  });
});

describe('the alert row sketch (OLD AlertDrawingSketch)', () => {
  it('one anchor is a level across the middle; price goes up', () => {
    expect(sketchPath([{ t: 0, p: 5 }])).toBe('M8 17L26 17');
    const p = sketchPath([{ t: 0, p: 1 }, { t: 10, p: 2 }]);
    expect(p).toBe('M8.0 26.0L26.0 8.0');
    expect(sketchPath([])).toBe('');
  });
});

describe('the watcher reads the drawing as it is now', () => {
  const tick = (symbol: string, last: number) => useMarket.getState().put([{ symbol, last, change: 0, changePct: 0, ts: Date.now() }]);
  it('a moved line: crossing the old level does nothing, crossing the new one fires', () => {
    startAlertWatch();
    noteDrawings('AAAUSDT', [{ type: 'hline', p: 100, id: 'h' }]);
    useAlerts.getState().create({ symbol: 'AAAUSDT', condition: 'cross', value: 100, drawingId: 'h', drawingType: 'hline' });
    noteDrawings('AAAUSDT', [{ type: 'hline', p: 200, id: 'h' }]);
    tick('AAAUSDT', 150);
    tick('AAAUSDT', 99);
    expect(useAlerts.getState().list[0]!.active).toBe(true);
    tick('AAAUSDT', 201);
    expect(useAlerts.getState().list[0]!.active).toBe(false);
  });
  it('a rectangle: entering it fires', () => {
    startAlertWatch();
    noteDrawings('BBBUSDT', [{ type: 'rect', id: 'r', p0: { t: 0, p: 100 }, p1: { t: 10, p: 110 } }]);
    useAlerts.getState().create({ symbol: 'BBBUSDT', condition: 'enter_channel', value: 100, high: 110, source: 'line', drawingId: 'r', drawingType: 'rect' });
    tick('BBBUSDT', 95);
    expect(useAlerts.getState().list[0]!.active).toBe(true);
    tick('BBBUSDT', 105);
    expect(useAlerts.getState().list[0]!.active).toBe(false);
  });
  it('a channel: leaving it fires', () => {
    startAlertWatch();
    const now = Date.now() / 1000;
    noteDrawings('CCCUSDT', [{ type: 'channel', id: 'c', pts: [{ t: now - 100, p: 100 }, { t: now, p: 100 }, { t: now - 100, p: 120 }] }]);
    useAlerts.getState().create({ symbol: 'CCCUSDT', condition: 'exit_channel', value: 100, high: 120, source: 'line', drawingId: 'c', drawingType: 'channel' });
    tick('CCCUSDT', 110);
    tick('CCCUSDT', 115);
    expect(useAlerts.getState().list[0]!.active).toBe(true);
    tick('CCCUSDT', 125);
    expect(useAlerts.getState().list[0]!.active).toBe(false);
  });
  it('a drawing that is gone never fires', () => {
    startAlertWatch();
    noteDrawings('DDDUSDT', []);
    useAlerts.getState().create({ symbol: 'DDDUSDT', condition: 'cross', value: 100, drawingId: 'h', drawingType: 'hline' });
    tick('DDDUSDT', 90);
    tick('DDDUSDT', 110);
    expect(useAlerts.getState().list[0]!.active).toBe(true);
  });
});
