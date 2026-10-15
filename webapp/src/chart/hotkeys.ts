// TradingView's keyboard shortcuts, from the old Pro-Chart table (engine/hotkeys.js): drawing tools
// on Alt, intervals and chart types, scale switches, zoom and scroll, replay, snapshot, the drawing
// clipboard and selection. Undo, redo and Delete are the drawing layer's own (drawingHost.ts).
// Every combination the help page lists has its handler here (or in drawingHost.ts).
import { attachHotkeys } from '../engine/hotkeys.js';
import { useDrawing } from '../state/drawing';
import { usePrefs } from '../state/prefs';
import { useChartBus } from './chartBus';
import { useReplay } from '../state/replay';
import { usePanels } from '../state/panels';
import { INTERVALS } from '../data/intervals';
import { intervalsFor } from '../data/candles';
import { takeSnapshot } from './snapshot';
import { useLayouts } from '../state/layouts';
import type { ChartType } from './chartTypes';
import { useGrid, type GridId } from '../state/grid';
import { usePage } from '../state/page';
import { usePaper } from '../state/paper';
import { useMarket } from '../state/market';
import { copyChartImage } from './actions';
import { useScript } from '../state/script';
import { clearChart, copySelection, inDialog, pasteClip } from './DrawingActions';
import { resetChartView } from './DrawingZoom';

export interface HotkeyActions { indicators(): void; symbolSearch(): void; newAlert(): void; settings(): void }

/** Shortcuts the drawing layer handles itself (drawingHost.ts): undo, redo and Delete. */
export const LAYER_SHORTCUTS = ['undo', 'redo', 'deleteSel'];

export function attachAppHotkeys(actions: HotkeyActions): () => void {
  return attachHotkeys(appHotkeyHandlers(actions));
}

/** Every shortcut's handler; with LAYER_SHORTCUTS this covers every row of the help page. */
export function appHotkeyHandlers(actions: HotkeyActions): Record<string, (e: KeyboardEvent) => void> {
  const tool = (id: string, group: string) => () => useDrawing.getState().setTool(id, group);
  const type = (t: ChartType) => () => usePrefs.getState().setChartType(t);
  const ts = () => useChartBus.getState().api?.timeScale();
  const stepInterval = (dir: 1 | -1) => () => {
    const p = usePrefs.getState();
    const ok = intervalsFor(p.symbol);
    const list = INTERVALS.map((i) => i.id).filter((i) => ok.includes(i));
    const i = list.indexOf(p.interval);
    const next = list[Math.max(0, Math.min(list.length - 1, i + dir))];
    if (next) p.setInterval(next);
  };
  const zoom = (k: number) => () => {
    const s = ts();
    if (!s) return;
    const r = s.getVisibleLogicalRange();
    if (!r) return;
    const mid = r.to;
    const span = (r.to - r.from) * k;
    s.setVisibleLogicalRange({ from: mid - span, to: mid });
  };
  const scroll = (bars: number) => () => { const s = ts(); if (s) s.scrollToPosition(s.scrollPosition() + bars, false); };
  const bus = () => useChartBus.getState();
  // Arrow keys move the selected drawing first (1px, 8px with Shift), as on TradingView; with no
  // drawing selected they scroll, zoom or step the replay as before.
  const nudgeOr = (key: string, large: boolean, fallback?: () => void) => () => {
    const l = bus().drawingLayer;
    if (l && !inDialog() && l.selectedIndices().length && l.nudgeSelected(key, large)) return;
    fallback?.();
  };
  const setIv = (iv: string) => () => { const p = usePrefs.getState(); if (intervalsFor(p.symbol).includes(iv)) p.setInterval(iv); };
  // Shift+B / Shift+S: a paper trade at the live price, sized at about 1,000 USDT.
  const paperAtMarket = (side: 'long' | 'short') => {
    const sym = usePrefs.getState().symbol;
    const px = useMarket.getState().tickers[sym]?.last;
    if (!px) return;
    usePaper.getState().open(sym, side, Number((1000 / px).toPrecision(2)), px);
    if (usePanels.getState().bottom !== 'paper') usePanels.getState().setBottom('paper');
  };

  return {
    // Esc: out of the replay's bar picking, else the drawing in progress is cancelled, the selection
    // cleared and the reader's cursor comes back.
    cursor: () => {
      if (useReplay.getState().selecting) { useReplay.getState().exit(); return; }
      bus().drawingLayer?.cancelInteraction();
      const d = useDrawing.getState();
      if (d.tool !== d.cursor) d.resetTool();
    },
    trend: tool('trend', 'lines'), hline: tool('hline', 'lines'), hray: tool('hray', 'lines'), vline: tool('vline', 'lines'), crossline: tool('crossline', 'lines'),
    rect: tool('rect', 'shapes'), fib: tool('fib', 'fib'), text: tool('text', 'annotations'),
    magnet: () => useDrawing.getState().setMagnet(!useDrawing.getState().magnet),
    stayDraw: () => useDrawing.getState().setStay(!useDrawing.getState().stay),
    copySel: (e) => { if (copySelection()) e.preventDefault(); },
    pasteSel: (e) => { if (pasteClip()) e.preventDefault(); },
    selectAll: (e) => { const l = bus().drawingLayer; if (l && l.getDrawings().length) { e.preventDefault(); l.selectAll(); } },
    clearChart: () => clearChart(),
    resetChart: () => resetChartView(),
    hideAll: () => useDrawing.getState().setHidden(!useDrawing.getState().hidden),
    removeAll: () => useDrawing.getState().clearAll(),
    tfNext: stepInterval(1), tfPrev: stepInterval(-1),
    typeCandles: type('candles'), typeBars: type('bars'), typeLine: type('line'), typeArea: type('area'), typeHeikin: type('heikin'),
    scrollLeft: nudgeOr('ArrowLeft', false, scroll(-10)), scrollRight: nudgeOr('ArrowRight', false, scroll(10)),
    zoomIn: (e) => (e.key === 'ArrowUp' ? nudgeOr('ArrowUp', false, zoom(0.8))() : zoom(0.8)()),
    zoomOut: (e) => (e.key === 'ArrowDown' ? nudgeOr('ArrowDown', false, zoom(1.25))() : zoom(1.25)()),
    nudgeUpFast: nudgeOr('ArrowUp', true), nudgeDownFast: nudgeOr('ArrowDown', true),
    scrollEnd: () => ts()?.scrollToRealTime(), scrollHome: () => { const s = ts(); if (s) s.setVisibleLogicalRange({ from: 0, to: 120 }); },
    fit: () => ts()?.fitContent(),
    resetScale: () => bus().api?.priceScale('right').applyOptions({ autoScale: true }),
    percentScale: () => bus().setScale(bus().scale === 'percent' ? 'normal' : 'percent'),
    logScale: () => bus().setScale(bus().scale === 'log' ? 'normal' : 'log'),
    toggleRight: () => usePanels.getState().toggle(usePanels.getState().open ?? 'watchlist'),
    fullscreen: () => { if (document.fullscreenElement) void document.exitFullscreen(); else void document.documentElement.requestFullscreen?.(); },
    toggleTheme: () => { const p = usePrefs.getState(); p.setTheme(p.theme === 'dark' ? 'light' : 'dark'); },
    indicators: () => actions.indicators(),
    symbolSearch: () => actions.symbolSearch(),
    newAlert: () => actions.newAlert(),
    settings: () => actions.settings(),
    replayToggle: () => { const r = useReplay.getState(); if (r.state?.on || r.selecting) r.exit(); else r.select(); },
    replayPlay: () => { const r = useReplay.getState(); if (r.state?.on) r.controller.toggle(); },
    replayStep: nudgeOr('ArrowRight', true, () => { const r = useReplay.getState(); if (r.state?.on) r.controller.step(); }),
    replayStepBack: nudgeOr('ArrowLeft', true, () => { const r = useReplay.getState(); if (r.state?.on) r.controller.stepBack(); }),
    screenshot: () => { void takeSnapshot(); },
    saveLayout: () => useLayouts.getState().save(),
    invertScale: () => bus().setInvert(!bus().invert),
    help: () => usePage.getState().open('help', 'shortcuts'),
    gotoDate: () => window.dispatchEvent(new CustomEvent('pc:goto')),
    cycleGrid: () => { const order: GridId[] = ['1', '2v', '2h', '3', '4']; const g = useGrid.getState(); g.setGrid(order[(order.indexOf(g.grid) + 1) % order.length]!); },
    tf1: setIv('5'), tf2: setIv('15'), tf3: setIv('60'), tf4: setIv('240'), tf5: setIv('1D'),
    cloneSel: () => { const l = bus().drawingLayer; if (l && l.selected >= 0) l.clone(l.selected); },
    lockSel: () => { const l = bus().drawingLayer; if (l && l.selected >= 0) { l.toggleLock(l.selected); l.render(); } },
    buy: () => paperAtMarket('long'),
    sell: () => paperAtMarket('short'),
    copyImage: () => { void copyChartImage(); },
    // In the script editor Ctrl+Shift+S keeps the script (it is kept on every change; this makes it explicit).
    saveScript: () => { const s = useScript.getState(); s.setSource(s.source); },
  };
}
