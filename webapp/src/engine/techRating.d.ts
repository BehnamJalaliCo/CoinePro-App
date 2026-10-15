export type RatingKey = 'strongBuy' | 'buy' | 'neutral' | 'sell' | 'strongSell';
export const RATING_FA: Record<RatingKey, { label: string; color: string }>;
export function ratingLabel(score: number): RatingKey;
export interface TechnicalRating {
  overall: number; ma: number; osc: number; label: RatingKey; maLabel: RatingKey; oscLabel: RatingKey;
  buy: number; sell: number; neutral: number; maBuy: number; maSell: number; maNeutral: number; oscBuy: number; oscSell: number; oscNeutral: number;
}
export function technicalRating(candles: { o: number; h: number; l: number; c: number; v?: number }[]): TechnicalRating | null;
