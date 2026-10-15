// Types for the bar-by-bar NamaScript engine (namascript_bar.js): Pine's execution model, one bar at
// a time with persistent `var`s; the same result shape as runScript.
import type { ScriptResult } from './namascript.js';
export function runScriptBar(source: string, candles: unknown[], inputs?: Record<string, unknown>, timeoutMs?: number): Promise<ScriptResult>;
