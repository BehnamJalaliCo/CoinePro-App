import { beforeEach, describe, expect, it } from 'vitest';
import { DrawingLayer, type DrawingObject } from '../engine/drawings.js';
import { normLevels, levelValues, volumeRows, vwapPath, dashOf, EXT_ONE, EXT_POLY } from '../engine/drawtools_ext.js';
import { appHotkeyHandlers, LAYER_SHORTCUTS } from '../chart/hotkeys';
import drawingsSrc from '../engine/drawings.js?raw';
import extSrc from '../engine/drawtools_ext.js?raw';
import { SHORTCUTS } from '../engine/hotkeys.js';
import { tfClassOf, timeLabel } from '../chart/drawingHost';
import { alertDetail, removableDrawings, styleOf } from '../chart/DrawingActions';
import { drawingStats } from '../chart/DrawingStyleBar';
import { tfOn } from '../chart/DrawingContextMenu';
import { useDrawing } from '../state/drawing';
import { ALL_TOOLS, CURSOR_TOOLS, TOOL_GROUPS, toolIcon } from '../chart/tools';

// A layer on a fake canvas and chart: one pixel is one second on x and one unit of price on y.
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

describe('drawing engine', () => {
  it('hit-tests a horizontal line through the public hitTest', () => {
    const l = mkLayer();
    l.setDrawings([{ type: 'hline', p: 100 }]);
    expect(l.hitTest(300, 101)).toBe(0);
    expect(l.hitTest(300, 160)).toBe(-1);
  });

  it('abandons an empty new text without leaving an undo step', () => {
    const l = mkLayer();
    l.setDrawings([{ type: 'hline', p: 50 }]);
    l.addDrawing({ type: 'hline', p: 60 });          // one step
    (l as unknown as { _pushUndo(): void })._pushUndo();
    l.getDrawings().push({ type: 'text', t: 1, p: 1, text: '' });
    l.abandon(2);
    expect(l.getDrawings()).toHaveLength(2);
    l.undo();                                          // undoes the hline, not a ghost text
    expect(l.getDrawings()).toHaveLength(1);
  });

  it('forgets the history of the previous symbol', () => {
    const l = mkLayer();
    l.addDrawing({ type: 'hline', p: 1 });
    expect(l.canUndo()).toBe(true);
    l.setDrawings([]);
    l.resetHistory();
    expect(l.canUndo()).toBe(false);
  });

  it('nudges every selected drawing, 8px with Shift', () => {
    const l = mkLayer();
    l.setDrawings([{ type: 'hline', p: 100 }, { type: 'hline', p: 200 }]);
    l.selectAll();
    expect(l.selectedIndices()).toEqual([0, 1]);
    expect(l.nudgeSelected('ArrowDown', true)).toBe(true);
    expect(l.getDrawings().map((d) => d.p)).toEqual([108, 208]);
    l.cancelInteraction();
    expect(l.nudgeSelected('ArrowDown')).toBe(false);
  });

  it('copies and pastes the selection as one undo step', () => {
    const l = mkLayer();
    l.setDrawings([{ type: 'trend', p0: { t: 10, p: 10 }, p1: { t: 50, p: 50 } }]);
    l.selectAt(0);
    const clip = l.copySelected();
    expect(clip).toHaveLength(1);
    l.pasteMany(clip);
    expect(l.getDrawings()).toHaveLength(2);
    expect((l.getDrawings()[1]!.p0 as { t: number }).t).toBe(24);
    l.undo();
    expect(l.getDrawings()).toHaveLength(1);
  });

  it('hides a drawing outside its visibility range', () => {
    const l = mkLayer();
    const d: DrawingObject = { type: 'hline', p: 5, tfVis: { minutes: { on: true, min: 1, max: 15 }, hours: false } };
    const hidden = (cls: string, n: number) => { l.setTfClass(cls, n); return (l as unknown as { _tfHidden(d: DrawingObject): boolean })._tfHidden(d); };
    expect(hidden('minutes', 5)).toBe(false);
    expect(hidden('minutes', 30)).toBe(true);
    expect(hidden('hours', 1)).toBe(true);
    expect(hidden('days', 1)).toBe(false);
  });

  it('long position: target from the reader, quantity from the risk', () => {
    const l = mkLayer();
    const d: DrawingObject = { type: 'longshort', p0: { t: 0, p: 100 }, p1: { t: 10, p: 95 }, account: 10000, risk: 1 };
    let s = l.positionStats(d);
    expect(s.target).toBe(110);                // two times the risk until set
    expect(s.riskAmt).toBe(100);
    expect(s.qty).toBe(20);
    expect(s.rr).toBe(2);
    s = l.positionStats({ ...d, tp: 115, riskUnit: 'amount', risk: 50 });
    expect(s.target).toBe(115);
    expect(s.qty).toBe(10);
    expect(s.reward).toBe(150);
    expect(s.rr).toBe(3);
  });

  it('long position has independent stop and target handles', () => {
    const l = mkLayer();
    const d: DrawingObject = { type: 'longshort', p0: { t: 100, p: 100 }, p1: { t: 200, p: 90 } };
    const hs = (l as unknown as { _handlePoints(d: DrawingObject): { set(t: number, p: number): void }[] })._handlePoints(d);
    expect(hs).toHaveLength(4);
    hs[2]!.set(0, 130);
    expect(d.tp).toBe(130);
    hs[1]!.set(0, 95);
    expect((d.p1 as { p: number; t: number }).p).toBe(95);
    expect((d.p1 as { p: number; t: number }).t).toBe(200);
  });
});

describe('drawing tools', () => {
  it('reads a level table, old ratios or new rows', () => {
    expect(levelValues({ type: 'fib' })).toEqual([0, 0.236, 0.382, 0.5, 0.618, 0.786, 1]);
    expect(levelValues({ type: 'fib', levels: [0, 0.5, 1] })).toEqual([0, 0.5, 1]);
    const rows = normLevels({ type: 'fib', levels: [{ v: 0.5, on: false, color: '#ff0000' }, { v: 1 }] });
    expect(rows[0]).toEqual({ v: 0.5, on: false, color: '#ff0000' });
    expect(levelValues({ type: 'fib', levels: rows })).toEqual([1]);
  });

  it('anchored VWAP is the volume-weighted typical price from the anchor', () => {
    const cs = [{ t: 1, o: 1, h: 1, l: 1, c: 1, v: 100 }, { t: 2, o: 2, h: 2, l: 2, c: 2, v: 100 }, { t: 3, o: 4, h: 4, l: 4, c: 4, v: 200 }];
    const path = vwapPath(cs, 2);
    expect(path.map((q) => q.t)).toEqual([2, 3]);
    expect(path[1]!.v).toBeCloseTo((2 * 100 + 4 * 200) / 300);
  });

  it('volume profile splits rising and falling volume and finds the POC and value area', () => {
    const cs = Array.from({ length: 20 }, (_, i) => ({ t: i, o: 100, h: 101 + (i % 3), l: 99, c: i % 2 ? 102 : 99.5, v: i === 5 ? 1000 : 10 }));
    const p = volumeRows(cs, 0, 19, 12, 0.7)!;
    expect(p.rows).toHaveLength(12);
    const up = p.rows.reduce((s, r) => s + r.up, 0), down = p.rows.reduce((s, r) => s + r.down, 0);
    expect(Math.round(up + down)).toBe(1190);
    expect(p.rows.filter((r) => r.poc)).toHaveLength(1);
    expect(p.rows.some((r) => r.va)).toBe(true);
    expect(p.val).toBeLessThanOrEqual(p.vah);
  });

  it('every rail tool has an icon and a name in both languages; cursors and new tools are known', () => {
    for (const t of [...ALL_TOOLS, ...CURSOR_TOOLS]) { expect(t.fa).toBeTruthy(); expect(t.en).toBeTruthy(); expect(toolIcon(t.id)).toBeTruthy(); }
    for (const id of ['avwap', 'anchoredvp', 'anchoredtext', 'pin', 'comment', 'table', 'emoji', 'hray', 'crossline']) expect(EXT_ONE).toContain(id);
    expect(EXT_POLY).toContain('ghostfeed');
    expect(TOOL_GROUPS.map((g) => g.id)).toEqual(['cursors', 'lines', 'fib', 'patterns', 'forecast', 'shapes', 'annotations', 'icons']);
  });
});

describe('shortcuts', () => {
  it('every shortcut on the help page has a handler', () => {
    const handled = new Set([...Object.keys(appHotkeyHandlers({ indicators() {}, symbolSearch() {}, newAlert() {}, settings() {} })), ...LAYER_SHORTCUTS]);
    const missing = SHORTCUTS.filter((s) => !handled.has(s.id)).map((s) => s.id);
    expect(missing).toEqual([]);
  });
  it('canvas text is in the one typeface and every tool honours the dotted style', () => {
    for (const src of [drawingsSrc, extSrc]) expect(src).not.toMatch(/['"]\d+px sans-serif/);
    expect(dashOf({ lineStyle: 'dotted' })).toEqual([2, 3]);
    expect(dashOf({ lineStyle: 'dashed' })).toEqual([6, 4]);
    expect(dashOf({ dashed: true })).toEqual([6, 4]);
    expect(dashOf({})).toEqual([]);
  });
  const combo = (id: string) => SHORTCUTS.find((s) => s.id === id)?.combo;
  it('every shortcut has an English label too', () => {
    for (const s of SHORTCUTS) { expect(s.en).toBeTruthy(); expect(s.groupEn).toBeTruthy(); expect(/[\u0600-\u06FF]/.test(s.en)).toBe(false); }
  });
  it('Alt+R resets the chart; the ray has no key; Alt+J and Alt+C draw', () => {
    expect(combo('resetChart')).toMatch(/R$/);
    expect(SHORTCUTS.some((s) => s.id === 'ray')).toBe(false);
    expect(combo('hray')).toMatch(/J$/);
    expect(combo('crossline')).toMatch(/C$/);
    expect(combo('copyImage')).toBeTruthy();
  });
});

describe('drawing host helpers', () => {
  it('reads the interval class for visibility', () => {
    expect(tfClassOf('15')).toEqual({ cls: 'minutes', n: 15 });
    expect(tfClassOf('240')).toEqual({ cls: 'hours', n: 4 });
    expect(tfClassOf('1D')).toEqual({ cls: 'days', n: 1 });
    expect(tfClassOf('1W')).toEqual({ cls: 'weeks', n: 1 });
    expect(tfClassOf('1M')).toEqual({ cls: 'months', n: 1 });
  });
  it('writes a vertical line\'s date in the reader\'s time zone', () => {
    const t = Date.UTC(2026, 9, 13, 15, 0) / 1000;
    expect(timeLabel('Asia/Tehran', '60')(t)).toBe("Tue 13 Oct '26  18:30");
    expect(timeLabel('UTC', '60')(t)).toBe("Tue 13 Oct '26  15:00");
    expect(timeLabel('Asia/Tehran', '1D')(t)).toBe("Tue 13 Oct '26");
  });
});

describe('drawing actions', () => {
  it('an alert from a drawing: a price for horizontals, the line for trend lines', () => {
    expect(alertDetail({ type: 'hline', p: 5 })?.price).toBe(5);
    expect(alertDetail({ type: 'trend', p0: { t: 1, p: 2 }, p1: { t: 3, p: 4 } })?.line).toEqual({ t1: 1, p1: 2, t2: 3, p2: 4 });
    expect(alertDetail({ type: 'hray', p0: { t: 1, p: 9 }, p1: { t: 1, p: 9 } })?.price).toBe(9);
  });
  it('keeps locked drawings unless asked to remove them too', () => {
    const list: DrawingObject[] = [{ type: 'hline', p: 1, locked: true }, { type: 'hline', p: 2 }];
    expect(removableDrawings(list, false)).toHaveLength(1);
    expect(removableDrawings(list, true)).toHaveLength(2);
  });
  it('a style is the look without the place', () => {
    expect(styleOf({ type: 'trend', color: '#f00', width: 3, p0: { t: 1, p: 1 } })).toEqual({ color: '#f00', width: 3 });
  });
  it('stats: change, percent, ticks or pips, bars and time', () => {
    const s = drawingStats({ type: 'trend', p0: { t: 0, p: 100 }, p1: { t: 7200, p: 110 } }, 'BTCUSDT', 2, 3600)!;
    expect(s.dp).toBe(10); expect(s.pct).toBe(10); expect(s.unit).toBe('ticks'); expect(s.steps).toBe(1000); expect(s.bars).toBe(2); expect(s.dur).toBe('2h');
    expect(drawingStats({ type: 'trend', p0: { t: 0, p: 1.1 }, p1: { t: 60, p: 1.1010 } }, 'EURUSD', 5, 60)!.steps).toBe(10);
  });
  it('visibility switches read old and new forms', () => {
    expect(tfOn(undefined, 'hours')).toBe(true);
    expect(tfOn({ hours: false }, 'hours')).toBe(false);
    expect(tfOn({ hours: { on: false } }, 'hours')).toBe(false);
  });
});

describe('drawing state', () => {
  beforeEach(() => { localStorage.clear(); });
  it('remembers the cursor mode and goes back to it', () => {
    const s = useDrawing.getState();
    s.setTool('dot', 'cursors');
    expect(useDrawing.getState().cursor).toBe('dot');
    s.setTool('trend', 'lines');
    useDrawing.getState().resetTool();
    expect(useDrawing.getState().tool).toBe('dot');
    expect(JSON.parse(localStorage.getItem('pc.drawing.v1') ?? '{}').cursor).toBe('dot');
    s.setTool('cursor', 'cursors');
  });
  it('keeps favourites, magnet mode and the hidden rail', () => {
    const s = useDrawing.getState();
    s.toggleFavorite('trend');
    s.setMagnetMode('weak');
    s.setRailHidden(true);
    const saved = JSON.parse(localStorage.getItem('pc.drawing.v1') ?? '{}');
    expect(saved.favorites).toContain('trend');
    expect(saved.magnetMode).toBe('weak');
    expect(saved.magnet).toBe(true);
    expect(saved.railHidden).toBe(true);
    s.toggleFavorite('trend'); s.setMagnet(false); s.setMagnetMode('strong'); s.setMagnet(false); s.setRailHidden(false);
  });
});
