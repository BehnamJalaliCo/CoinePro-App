// NamaScript, our Pine: the reader's code, whether it is on the chart, and the last run's result.
// The code is kept in this browser; running happens in the engine's sandboxed worker.
import { create } from 'zustand';
import { EXAMPLES } from '../engine/scriptlib.js';
import type { ScriptResult } from '../engine/namascript.js';

const KEY = 'pc.namascript.v1';
interface Stored { source: string; applied: boolean; inputs: Record<string, unknown> }

function read(): Stored {
  try {
    const v = JSON.parse(localStorage.getItem(KEY) ?? 'null') as Stored | null;
    if (v && typeof v.source === 'string') return { source: v.source, applied: Boolean(v.applied), inputs: v.inputs ?? {} };
  } catch { /* fall through */ }
  return { source: EXAMPLES[1]?.code ?? '', applied: false, inputs: {} };
}

interface ScriptState extends Stored {
  result: ScriptResult | null;
  running: boolean;
  /** Bumped to ask the chart to run the script again on its bars. */
  runSeq: number;
  setSource(s: string): void;
  setInputs(i: Record<string, unknown>): void;
  apply(): void;
  remove(): void;
  setResult(r: ScriptResult | null): void;
  setRunning(on: boolean): void;
}

export const useScript = create<ScriptState>((set, get) => {
  const save = () => { const { source, applied, inputs } = get(); try { localStorage.setItem(KEY, JSON.stringify({ source, applied, inputs })); } catch { /* ignore */ } };
  return {
    ...read(),
    result: null,
    running: false,
    runSeq: 0,
    setSource: (source) => { set({ source }); save(); },
    setInputs: (inputs) => { set((s) => ({ inputs, runSeq: s.runSeq + 1 })); save(); },
    apply: () => { set((s) => ({ applied: true, runSeq: s.runSeq + 1 })); save(); },
    remove: () => { set({ applied: false, result: null }); save(); },
    setResult: (result) => set({ result }),
    setRunning: (running) => set({ running }),
  };
});
