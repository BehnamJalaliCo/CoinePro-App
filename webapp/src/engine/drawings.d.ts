// Types for the vendored drawing engine (drawings.js). The engine is plain JavaScript from the old
// Pro-Chart; only the surface the app calls is described here.
export interface DrawingObject { type: string; locked?: boolean; visible?: boolean; [k: string]: unknown }

/** The keys of a drawing that are its look rather than its place. */
export const STYLE_KEYS: string[];
/** Drawings that carry a text: an editor opens when one is placed and on a double click. */
export const TEXT_TYPES: string[];

export interface PositionStats {
  entry: number; stop: number; target: number; long: boolean;
  account: number; lot: number; riskAmt: number; qty: number; reward: number; rr: number; tpPct: number; slPct: number;
}

export class DrawingLayer {
  constructor(canvas: unknown, chart: unknown);
  tool: string;
  selected: number;
  digits: number;
  lang: string;
  emoji: string;
  color: string;
  eraserCursor: string;
  candles: { t: number; o: number; h: number; l: number; c: number; v?: number }[] | null;
  priceFmt?: (v: number) => string;
  onChange: ((drawings: DrawingObject[]) => void) | null;
  onSelect: ((index: number) => void) | null;
  onToolReset: (() => void) | null;
  onDblEdit: ((index: number) => void) | null;
  onTextRequest?: ((index: number) => void) | null;
  onCreated: ((index: number) => void) | null;
  onZoomBox: ((box: { x0: number; y0: number; x1: number; y1: number }) => void) | null;
  _down?: (e: PointerEvent) => void;
  static label(type: string): string;
  setSeries(series: unknown): void;
  setCandles(candles: unknown[]): void;
  setDigits(digits: number): void;
  setMagnet(on: boolean, mode?: 'strong' | 'weak'): void;
  setTool(tool: string, color?: string): void;
  setStayInMode(on: boolean): void;
  setTimeFmt(fn: (t: number) => string): void;
  setTfClass(cls: string, n?: number | null): void;
  setToolDefaults(map: Record<string, Record<string, unknown>>): void;
  setDrawings(list: DrawingObject[], record?: boolean): void;
  getDrawings(): DrawingObject[];
  getSelected(): DrawingObject | null;
  resetHistory(): void;
  hitTest(x: number, y: number): number;
  selectedIndices(): number[];
  toggleSelect(index: number): void;
  selectAll(): void;
  cancelInteraction(): void;
  nudgeSelected(key: string, large?: boolean): boolean;
  copySelected(): DrawingObject[];
  pasteMany(list: DrawingObject[]): number[];
  removeAt(index: number): void;
  removeSelected(): void;
  abandon(index: number): void;
  amend(index: number, style: Record<string, unknown>): void;
  selectAt(index: number): void;
  toggleLock(index: number): void;
  toggleVisible(index: number): void;
  clone(index: number): number;
  addHLine(price: number, color?: string): number;
  addDrawing(d: DrawingObject): void;
  hideAll(on: boolean): void;
  allHidden(): boolean;
  setStyle(index: number, style: Record<string, unknown>): void;
  setStyleMany(indices: number[], style: Record<string, unknown>): void;
  beginEdit(): DrawingObject[];
  liveStyle(index: number, style: Record<string, unknown>): void;
  endEdit(before: DrawingObject[], commit: boolean): void;
  bringToFront(index: number): void;
  sendToBack(index: number): void;
  bringForward(index: number): void;
  sendBackward(index: number): void;
  move(from: number, to: number): void;
  lockAll(on: boolean, record?: boolean): void;
  clearAll(): void;
  undo(): void;
  redo(): void;
  canUndo(): boolean;
  canRedo(): boolean;
  render(): void;
  positionStats(d: DrawingObject): PositionStats;
  _barInterval(): number | null;
  setScriptPaint(result: unknown): void;
  destroy(): void;
}
