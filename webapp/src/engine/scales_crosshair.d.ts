export const TIMEZONES: { id: string; label: string }[];
export interface SessionDef { id: string; label: string; startUtc: number; endUtc: number; color: string; edge: string }
export const SESSIONS: SessionDef[];
export interface SessionBand { id: string; label: string; from: number; to: number; color: string; edge: string }
export function sessionBands(fromSec: number, toSec: number, tz?: string, opt?: { sessions?: string[]; overlap?: boolean }): SessionBand[];
export function secondsToClose(tf: string, nowSec?: number, tz?: string): number;
export function formatCountdown(rem: number): string;
export function countdownTint(rem: number): string | null;
export function tzOffsetSec(unixSec: number, tz: string): number;
