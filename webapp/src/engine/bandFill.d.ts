export interface BandPoint { time: number; upper: number | null; lower: number | null }
export class BandFillPrimitive {
  constructor(data: BandPoint[], color: string, colorDown?: string | null);
  setData(data: BandPoint[]): void;
}
