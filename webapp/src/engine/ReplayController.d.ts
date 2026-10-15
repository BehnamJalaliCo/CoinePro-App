export const SPEED_LADDER: number[];
export interface ReplayState { on: boolean; playing: boolean; speed: number; idx: number; length: number; progress: number; atStart: boolean; atEnd: boolean }
export class ReplayController {
  constructor(opts?: { speed?: number; onSlice?(slice: unknown[]): void; onStep?(bar: unknown, slice: unknown[], idx: number): void; onChange?(s: ReplayState): void; onEnd?(): void });
  static canEnter(candles: unknown[]): boolean;
  readonly isOn: boolean;
  idx: number;
  full: unknown[];
  playing: boolean;
  speed: number;
  enter(candles: unknown[], opts?: { startIndex?: number; startTime?: number; startPct?: number }): boolean;
  slice(): unknown[];
  step(): boolean;
  stepBack(): boolean;
  seek(o: { index?: number; time?: number }): boolean;
  play(): void;
  pause(): void;
  toggle(): void;
  setSpeed(speed: number): void;
  speedUp(): void;
  speedDown(): void;
  exit(): void;
  destroy(): void;
}
