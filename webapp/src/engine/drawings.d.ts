// Types for the vendored drawing engine (drawings.js). The engine is plain JavaScript from the old
// Pro-Chart; only the surface the app calls is described here.
export interface DrawingObject { type: string; locked?: boolean; visible?: boolean; [k: string]: unknown }

export class DrawingLayer {
  constructor(canvas: unknown, chart: unknown);
  tool: string;
  selected: number;
  onChange: ((drawings: DrawingObject[]) => void) | null;
  onSelect: ((index: number) => void) | null;
  onToolReset: (() => void) | null;
  onDblEdit: ((index: number) => void) | null;
  onTextRequest?: ((index: number) => void) | null;
  static label(type: string): string;
  setSeries(series: unknown): void;
  setCandles(candles: unknown[]): void;
  setDigits(digits: number): void;
  setMagnet(on: boolean, mode?: 'strong' | 'weak'): void;
  setTool(tool: string, color?: string): void;
  setStayInMode(on: boolean): void;
  setTimeFmt(fn: (t: number) => string): void;
  setDrawings(list: DrawingObject[], record?: boolean): void;
  getDrawings(): DrawingObject[];
  getSelected(): DrawingObject | null;
  removeAt(index: number): void;
  setStyle(index: number, style: Record<string, unknown>): void;
  lockAll(on: boolean): void;
  clearAll(): void;
  undo(): void;
  redo(): void;
  canUndo(): boolean;
  canRedo(): boolean;
  render(): void;
  setScriptPaint(result: unknown): void;
}
