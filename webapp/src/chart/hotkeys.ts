// TradingView's keyboard shortcuts, from the old Pro-Chart table (engine/hotkeys.js): drawing tools
// on Alt, intervals and chart types, scale switches, zoom and scroll, replay, snapshot. Undo, redo
// and Delete are the drawing layer's own (drawingHost.ts).
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

export interface HotkeyActions { indicators(): void; symbolSearch(): void; newAlert(): void; settings(): void }

export function attachAppHotkeys(actions: HotkeyActions): () => void {
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
  const setIv = (iv: string) => () => { const p = usePrefs.getState(); if (intervalsFor(p.symbol).includes(iv)) p.setInterval(iv); };
  // Shift+B / Shift+S: a paper trade at the live price, sized at about 1,000 USDT.
  const paperAtMarket = (side: 'long' | 'short') => {
    const sym = usePrefs.getState().symbol;
    const px = useMarket.getState().tickers[sym]?.last;
    if (!px) return;
    usePaper.getState().open(sym, side, Number((1000 / px).toPrecision(2)), px);
    if (usePanels.getState().bottom !== 'paper') usePanels.getState().setBottom('paper');
  };

  return attachHotkeys({
    cursor: () => { if (useReplay.getState().selecting) useReplay.getState().exit(); else useDrawing.getState().setTool('cursor', 'cursors'); },
    trend: tool('trend', 'lines'), hline: tool('hline', 'lines'), vline: tool('vline', 'lines'), ray: tool('ray', 'lines'),
    rect: tool('rect', 'shapes'), fib: tool('fib', 'fib'), text: tool('text', 'annotations'),
    magnet: () => useDrawing.getState().setMagnet(!useDrawing.getState().magnet),
    hideAll: () => useDrawing.getState().setHidden(!useDrawing.getState().hidden),
    removeAll: () => useDrawing.getState().clearAll(),
    tfNext: stepInterval(1), tfPrev: stepInterval(-1),
    typeCandles: type('candles'), typeBars: type('bars'), typeLine: type('line'), typeArea: type('area'), typeHeikin: type('heikin'),
    scrollLeft: scroll(-10), scrollRight: scroll(10), zoomIn: zoom(0.8), zoomOut: zoom(1.25),
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
    replayStep: () => { const r = useReplay.getState(); if (r.state?.on) r.controller.step(); },
    replayStepBack: () => { const r = useReplay.getState(); if (r.state?.on) r.controller.stepBack(); },
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
  });
}
