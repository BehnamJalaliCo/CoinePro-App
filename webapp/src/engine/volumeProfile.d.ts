export interface ProfileBucket { lo: number; hi: number; vol: number; up: number; down: number; poc: boolean; va: boolean }
export function computeProfile(candles: { l: number; h: number; o?: number; c?: number; v?: number }[] | null, buckets?: number, valueArea?: number): ProfileBucket[] | null;
export default computeProfile;
