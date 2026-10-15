// Types for the old Pro-Chart's resampler (resample.js).
export interface ResampleBar { t: number; o: number; h: number; l: number; c: number; v: number }
export function resampleCandles<T extends ResampleBar>(bars: T[], factor: number): ResampleBar[];
export default resampleCandles;
