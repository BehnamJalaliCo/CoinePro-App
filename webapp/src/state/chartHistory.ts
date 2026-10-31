// The chart's one undo and redo (OLD feature/chart ChartHistory.kt, ChartController.kt:1533):
// the last change to an indicator, the interval or range, the chart type, or a drawing — not the
// drawings alone. Undo after switching an indicator on by mistake takes the indicator off, the
// way a reader means it, and the drawing tools keep their steps in the same stack.
//
// A change to the chart is kept as a snapshot of what the chart was (the interval, the marked
// range, the chart type and the indicators with their inputs and style), not as a command that
// knows how to invert itself: a snapshot cannot be wrong about what the chart was. The bars are
// not in it, so an undo shows today's bars on yesterday's apparatus. The drawing layer keeps its
// own snapshots (engine/drawing_history.js); this stack holds a marker for each of its steps and
// hands undo to the layer when the marker comes up, so drag, paste and the text editor keep the
// rules they already have. The symbol is not in it: a new symbol starts a new history, and pan
// and zoom are not steps.
import { create } from 'zustand';
import type { ChartType } from '../chart/chartTypes';
import { useChartBus } from '../chart/chartBus';
import { canDraw } from '../data/candles';
import { usePrefs } from './prefs';
import { SCRIPT, useStudies, type Study } from './studies';

/** What the chart was before a change (OLD ChartStep). */
export interface ChartStep { interval: string; range: string | null; chartType: ChartType; studies: Study[] }

/** One step back: a drawing-layer step (the layer undoes it), or the chart as it was. */
export type HistoryEntry<S> = { kind: 'drawing' } | { kind: 'chart'; step: S };

/** How many steps are kept (OLD ChartHistory.LIMIT). */
export const HISTORY_LIMIT = 50;
/** The window the frames of one continuous edit collapse into (OLD ChartHistory.COALESCE_MS). */
export const COALESCE_MS = 600;

const sameJson = (a: unknown, b: unknown) => JSON.stringify(a) === JSON.stringify(b);

/**
 * Two stacks and the rules between them, pure so the rules hold in a unit test (OLD ChartHistory):
 * a recorded step clears the redo stack; a step identical to the top is not a step; a change that
 * is one frame of a continuous edit is recorded at most once per COALESCE_MS; fifty steps kept.
 */
export class ChartHistory<S> {
  private back: Array<HistoryEntry<S>> = [];
  private forward: Array<HistoryEntry<S>> = [];
  private lastAt = 0;

  constructor(private readonly limit = HISTORY_LIMIT, private readonly now: () => number = Date.now, private readonly same: (a: S, b: S) => boolean = sameJson) {}

  get canUndo(): boolean { return this.back.length > 0; }
  get canRedo(): boolean { return this.forward.length > 0; }
  /** The kinds on the undo stack, oldest first (for tests and the e2e hooks). */
  kinds(): Array<'drawing' | 'chart'> { return this.back.map((e) => e.kind); }

  /** Pushes the chart as it was before a change. */
  record(step: S, coalescable = false): boolean {
    const at = this.now();
    if (coalescable && this.back.length && at - this.lastAt < COALESCE_MS) return false;
    const top = this.back[this.back.length - 1];
    if (top?.kind === 'chart' && this.same(top.step, step)) return false;
    this.push({ kind: 'chart', step }, at);
    return true;
  }

  /** The drawing layer recorded a step of its own. */
  recordDrawing(): void { this.push({ kind: 'drawing' }, this.now()); }

  /** The drawing layer took its last step back without an undo (a new text left empty). */
  dropDrawing(): void {
    if (this.back[this.back.length - 1]?.kind === 'drawing') this.back.pop();
  }

  /** One step back, given the chart as it is now; null when there is nothing to take back. */
  undo(current: S): HistoryEntry<S> | null {
    const e = this.back.pop();
    if (!e) return null;
    this.forward.push(e.kind === 'chart' ? { kind: 'chart', step: current } : e);
    return e;
  }

  /** The mirror of undo. */
  redo(current: S): HistoryEntry<S> | null {
    const e = this.forward.pop();
    if (!e) return null;
    this.back.push(e.kind === 'chart' ? { kind: 'chart', step: current } : e);
    return e;
  }

  /** Takes back the newest chart step when the action it was recorded for changed nothing. */
  dropChart(step: S): void {
    const top = this.back[this.back.length - 1];
    if (top?.kind === 'chart' && this.same(top.step, step)) this.back.pop();
  }

  /** Forgets everything (a new symbol). */
  clear(): void { this.back = []; this.forward = []; this.lastAt = 0; }

  private push(e: HistoryEntry<S>, at: number): void {
    this.back.push(e);
    this.forward = [];
    this.lastAt = at;
    while (this.back.length > this.limit) this.back.shift();
  }
}

// ── The live chart's history ────────────────────────────────────────────────────────────────────

interface ChartHistoryState {
  canUndo: boolean;
  canRedo: boolean;
  /** The time bar's marked range (its button id), or null. */
  range: string | null;
  setRange(range: string | null): void;
}

export const useChartHistory = create<ChartHistoryState>((set) => ({
  canUndo: false,
  canRedo: false,
  range: null,
  setRange: (range) => set({ range }),
}));

const history = new ChartHistory<ChartStep>();
/** True while a step is put back, so the changes it makes are not recorded as new ones. */
let restoring = false;
/** Inside chartAction: the action recorded once, its parts are not steps of their own. */
let quiet = 0;
/** The rest of the tick a symbol changed in: a layout or a link setting the interval with it. */
let settling = false;
/** The time bar's range button, registered while the time bar is on screen. */
let rangeApplier: ((id: string) => void) | null = null;

const studiesOf = (list: Study[]): Study[] => list.filter((s) => s.key !== SCRIPT);

/** The chart's apparatus as it stands. */
export function currentStep(): ChartStep {
  const p = usePrefs.getState();
  return { interval: p.interval, range: useChartHistory.getState().range, chartType: p.chartType, studies: studiesOf(useStudies.getState().list) };
}

function publish(): void {
  const canUndo = history.canUndo; const canRedo = history.canRedo;
  const h = useChartHistory.getState();
  if (h.canUndo !== canUndo || h.canRedo !== canRedo) useChartHistory.setState({ canUndo, canRedo });
  const bus = useChartBus.getState();
  if (bus.canUndo !== canUndo || bus.canRedo !== canRedo) useChartBus.setState({ canUndo, canRedo });
}

const recording = () => !restoring && quiet === 0 && !settling;

/** The drawing layer's redo steps go when the reader goes a different way on the chart. */
function dropLayerRedo(): void {
  const layer = useChartBus.getState().drawingLayer as unknown as { _history?: { future?: unknown[] } } | null;
  if (layer?._history?.future) layer._history.future.length = 0;
}

function recordStep(step: ChartStep, coalescable = false): boolean {
  if (!recording()) return false;
  const done = history.record(step, coalescable);
  if (done) dropLayerRedo();
  publish();
  return done;
}

/** Runs a change made of several parts (a range: an interval and a span) as one step. */
export function chartAction(fn: () => void): void {
  const before = currentStep();
  const recorded = recordStep(before);
  quiet++;
  try { fn(); } finally { quiet--; }
  // An action that changed nothing (the range already marked) leaves no step behind.
  if (recorded && sameJson(currentStep(), before)) { history.dropChart(before); publish(); }
}

/** The drawing layer recorded a step (called by the drawing host). */
export function noteDrawingRecorded(): void {
  if (restoring) return;
  history.recordDrawing();
  publish();
}

/** The drawing layer dropped its newest step (a cancelled new text). */
export function noteDrawingAbandoned(): void {
  history.dropDrawing();
  publish();
}

/** Forgets every step (a new symbol: undo never crosses markets). */
export function clearChartHistory(): void {
  history.clear();
  publish();
}

/** The time bar registers its range button so an undo can mark the range again. */
export function registerRangeApplier(fn: ((id: string) => void) | null): void { rangeApplier = fn; }

function restore(step: ChartStep): void {
  restoring = true;
  try {
    const p = usePrefs.getState();
    const h = useChartHistory.getState();
    if (step.range && rangeApplier) {
      if (step.range !== h.range || step.interval !== p.interval) rangeApplier(step.range);
    } else {
      if (step.interval !== p.interval) p.setInterval(step.interval);
      if (h.range !== step.range) h.setRange(step.range);
    }
    if (step.chartType !== usePrefs.getState().chartType) usePrefs.getState().setChartType(step.chartType);
    const list = useStudies.getState().list;
    if (!sameJson(studiesOf(list), step.studies)) {
      const script = list.find((s) => s.key === SCRIPT);
      useStudies.getState().replace(script ? [...step.studies, script] : step.studies);
    }
  } finally {
    restoring = false;
  }
}

function layerStep(redo: boolean): void {
  const layer = useChartBus.getState().drawingLayer;
  if (!layer) return;
  restoring = true;
  try { if (redo) layer.redo(); else layer.undo(); } finally { restoring = false; }
  useChartBus.getState().bumpDrawings(layer.canUndo(), layer.canRedo(), layer.getDrawings().length);
}

/** Takes back the last change to the chart: an indicator, the interval or range, the type, a drawing. */
export function undoChart(): void {
  const e = history.undo(currentStep());
  if (!e) return;
  if (e.kind === 'drawing') layerStep(false); else restore(e.step);
  publish();
}

/** Puts back what undo took; gone the moment the reader goes a different way. */
export function redoChart(): void {
  const e = history.redo(currentStep());
  if (!e) return;
  if (e.kind === 'drawing') layerStep(true); else restore(e.step);
  publish();
}

/** The kinds on the undo stack, oldest first (the e2e suite reads it). */
export const historyKinds = () => history.kinds();

// Every reader-made change to the main chart is recorded as it happens, whoever made it: the top
// bar, the hotkeys, the indicator dialog, a legend row, the time bar. Watching the stores instead of
// instrumenting each call site means a new way to change the chart is covered without being told.
usePrefs.subscribe((s, prev) => {
  if (s.symbol !== prev.symbol) {
    clearChartHistory();
    settling = true;
    queueMicrotask(() => { settling = false; });
    return;
  }
  if (s.interval === prev.interval && s.chartType === prev.chartType) return;
  // The app moving off an interval this market cannot draw is not the reader's change.
  if (s.interval !== prev.interval && !canDraw(s.symbol, prev.interval)) return;
  recordStep({ ...currentStep(), interval: prev.interval, chartType: prev.chartType });
});

useStudies.subscribe((s, prev) => {
  if (s.list === prev.list) return;
  const before = studiesOf(prev.list); const after = studiesOf(s.list);
  if (sameJson(before, after)) return;
  // Inputs or style edited live (the settings dialog) are frames of one edit; adding, removing,
  // reordering or hiding a study is a step of its own.
  const shape = (l: Study[]) => l.map((x) => `${x.uid}:${x.hidden ? 1 : 0}`).join(',');
  recordStep({ ...currentStep(), studies: before }, shape(before) === shape(after));
});
