// NamaScript, our Pine: the reader's scripts (open tabs, the saved library with each script's
// versions), whether one is on the chart, its inputs, the run mode (series or bar by bar), the last
// run's result and the strategy tester's properties and report. Kept in this browser; running
// happens in the engine's sandboxed worker, driven by shell/ScriptHost.tsx.
import { create } from 'zustand';
import { EXAMPLES } from '../engine/scriptlib.js';
import type { ScriptResult } from '../engine/namascript.js';
import type { BacktestOptions, BtResult } from '../pages/backtestEngine';
import { SCRIPT, useStudies } from './studies';

const KEY = 'pc.namascript.v1';
const LIB_KEY = 'pc.scripts.v1';
const STRAT_KEY = 'pc.strategies.v1';

export interface ScriptTab { id: string; name: string; code: string; savedId: string | null }
export interface SavedScript { id: string; name: string; code: string; updatedAt: number; versions?: { at: number; code: string }[] }
/** TradingView's strategy Properties, plus which signals to test when there is no strategy. */
export interface StrategyProps extends BacktestOptions { showTrades: boolean }
export interface SavedStrategy { id: string; name: string; script: string; code: string; inputs: Record<string, unknown>; props: StrategyProps; at: number }

export const DEFAULT_PROPS: StrategyProps = {
  direction: 'both', stopPct: 0, targetPct: 0, feePct: 0, capital: 10_000, riskShare: 100, sizeMode: 'percent', qty: 1, cash: 1000,
  pyramiding: 1, slippagePct: 0, feeCash: 0, fillOnClose: false, from: null, to: null, showTrades: true,
};

interface Stored {
  tabs: ScriptTab[]; active: string; on: boolean; inputs: Record<string, unknown>; barMode: boolean;
  /** The code that is on the chart (what "Add to chart" took), apart from the edits since. */
  runCode: string; props: StrategyProps;
}

const id = () => Math.random().toString(36).slice(2, 10);
const read = <T,>(key: string, fallback: T): T => { try { const v = JSON.parse(localStorage.getItem(key) ?? 'null') as T | null; return v ?? fallback; } catch { return fallback; } };
const write = (key: string, v: unknown) => { try { localStorage.setItem(key, JSON.stringify(v)); } catch { /* full or blocked */ } };

/** The script's own name: `indicator("…")` or `strategy("…")` in its code. */
export function scriptTitle(code: string): string | null {
  return /\b(?:indicator|study|strategy)\s*\(\s*["']([^"']+)["']/.exec(code)?.[1] ?? null;
}

function initial(): Stored {
  const v = read<Partial<Stored> & { source?: string; applied?: boolean } | null>(KEY, null);
  if (v && Array.isArray(v.tabs) && v.tabs.length) {
    const tabs = v.tabs.filter((t) => typeof t?.code === 'string');
    const active = tabs.some((t) => t.id === v.active) ? v.active! : tabs[0]!.id;
    return { tabs, active, on: Boolean(v.on), inputs: v.inputs ?? {}, barMode: Boolean(v.barMode), runCode: v.runCode ?? tabs[0]!.code, props: { ...DEFAULT_PROPS, ...(v.props ?? {}) } };
  }
  // The first version kept one script: { source, applied, inputs }.
  const code = typeof v?.source === 'string' ? v.source : EXAMPLES[1]?.code ?? '';
  const tab = { id: id(), name: scriptTitle(code) ?? 'NamaScript', code, savedId: null };
  return { tabs: [tab], active: tab.id, on: Boolean(v?.applied), inputs: v?.inputs ?? {}, barMode: false, runCode: code, props: { ...DEFAULT_PROPS } };
}

interface ScriptState extends Stored {
  /** The open tab's code. */
  source: string;
  /** Retired: the chart view's first runner reads this and stays idle; ScriptHost runs the script. */
  applied: false;
  /** Retired with `applied`. */
  runSeq: number;
  /** Bumped to run the script again (on the bars on screen). */
  seq: number;
  result: ScriptResult | null;
  /** Which engine produced the result (bar mode falls back to the series engine). */
  engine: 'series' | 'bar' | null;
  running: boolean;
  backtest: BtResult | null;
  lib: SavedScript[];
  strategies: SavedStrategy[];
  setSource(s: string): void;
  setInputs(i: Record<string, unknown>): void;
  setBarMode(on: boolean): void;
  /** Puts the open tab's code on the chart (TradingView's "Add to chart" / "Update on chart"). */
  apply(): void;
  remove(): void;
  setResult(r: ScriptResult | null, engine?: 'series' | 'bar'): void;
  setRunning(on: boolean): void;
  setBacktest(b: BtResult | null): void;
  setProps(p: Partial<StrategyProps>): void;
  newTab(code?: string, name?: string): void;
  closeTab(tabId: string): void;
  switchTab(tabId: string): void;
  rename(name: string): void;
  /** Saves the open tab into the library (a new version of it); returns the saved id. */
  save(asNew?: boolean, name?: string): string;
  open(savedId: string): void;
  deleteSaved(savedId: string): void;
  restoreVersion(savedId: string, at: number): void;
  saveStrategy(name: string): void;
  loadStrategy(sid: string): void;
  deleteStrategy(sid: string): void;
}

export const useScript = create<ScriptState>((set, get) => {
  const st = initial();
  const tabOf = (s: Stored) => s.tabs.find((t) => t.id === s.active) ?? s.tabs[0]!;
  const persist = () => { const { tabs, active, on, inputs, barMode, runCode, props } = get(); write(KEY, { tabs, active, on, inputs, barMode, runCode, props }); };
  const patchTab = (p: Partial<ScriptTab>) => {
    const tabs = get().tabs.map((t) => (t.id === get().active ? { ...t, ...p } : t));
    set({ tabs, source: tabOf({ ...get(), tabs }).code });
    persist();
  };
  return {
    ...st,
    source: tabOf(st).code,
    applied: false,
    runSeq: 0,
    seq: 0,
    result: null,
    engine: null,
    running: false,
    backtest: null,
    lib: read<SavedScript[]>(LIB_KEY, []).filter((x) => x && typeof x.code === 'string'),
    strategies: read<SavedStrategy[]>(STRAT_KEY, []),
    setSource: (code) => patchTab({ code }),
    setInputs: (inputs) => { set((s) => ({ inputs, seq: s.seq + 1 })); persist(); },
    setBarMode: (barMode) => { set((s) => ({ barMode, seq: s.seq + 1 })); persist(); },
    apply: () => {
      const code = get().source;
      set((s) => ({ on: true, runCode: code, seq: s.seq + 1 }));
      persist();
      useStudies.getState().setScript(true);
    },
    remove: () => { set({ on: false, result: null, backtest: null }); persist(); useStudies.getState().setScript(false); },
    setResult: (result, engine) => set({ result, engine: engine ?? null }),
    setRunning: (running) => set({ running }),
    setBacktest: (backtest) => set({ backtest }),
    setProps: (p) => { set((s) => ({ props: { ...s.props, ...p }, seq: s.seq + 1 })); persist(); },
    newTab: (code, name) => {
      const c = code ?? '// NamaScript\nindicator("My script")\nplot(ta.sma(close, 20), "SMA", color.blue)\n';
      const tab = { id: id(), name: name ?? scriptTitle(c) ?? 'Untitled', code: c, savedId: null };
      set({ tabs: [...get().tabs, tab], active: tab.id, source: c });
      persist();
    },
    closeTab: (tabId) => {
      let tabs = get().tabs.filter((t) => t.id !== tabId);
      if (!tabs.length) tabs = [{ id: id(), name: 'Untitled', code: '', savedId: null }];
      const active = tabs.some((t) => t.id === get().active) ? get().active : tabs[tabs.length - 1]!.id;
      set({ tabs, active, source: tabs.find((t) => t.id === active)!.code });
      persist();
    },
    switchTab: (tabId) => {
      const t = get().tabs.find((x) => x.id === tabId);
      if (!t) return;
      set({ active: tabId, source: t.code });
      persist();
    },
    rename: (name) => patchTab({ name: name.trim() || 'Untitled' }),
    save: (asNew = false, name) => {
      const tab = tabOf(get());
      const lib = get().lib;
      const have = !asNew && tab.savedId ? lib.find((x) => x.id === tab.savedId) : undefined;
      const sid = have?.id ?? id();
      const nm = name ?? (asNew ? `${tab.name} (2)` : tab.name);
      const versions = [...(have?.versions ?? []), ...(have && have.code !== tab.code ? [{ at: have.updatedAt, code: have.code }] : [])].slice(-30);
      const entry: SavedScript = { id: sid, name: nm, code: tab.code, updatedAt: Date.now(), versions };
      const next = [entry, ...lib.filter((x) => x.id !== sid)];
      set({ lib: next });
      write(LIB_KEY, next);
      patchTab({ savedId: sid, name: nm });
      return sid;
    },
    open: (savedId) => {
      const s = get().lib.find((x) => x.id === savedId);
      if (!s) return;
      const already = get().tabs.find((t) => t.savedId === savedId);
      if (already) { get().switchTab(already.id); return; }
      const tab = { id: id(), name: s.name, code: s.code, savedId };
      set({ tabs: [...get().tabs, tab], active: tab.id, source: s.code });
      persist();
    },
    deleteSaved: (savedId) => {
      const next = get().lib.filter((x) => x.id !== savedId);
      set({ lib: next, tabs: get().tabs.map((t) => (t.savedId === savedId ? { ...t, savedId: null } : t)) });
      write(LIB_KEY, next); persist();
    },
    restoreVersion: (savedId, at) => {
      const s = get().lib.find((x) => x.id === savedId);
      const v = s?.versions?.find((x) => x.at === at);
      if (!s || !v) return;
      get().open(savedId);
      patchTab({ code: v.code });
    },
    saveStrategy: (name) => {
      const { props, inputs, runCode } = get();
      const tab = tabOf(get());
      const entry: SavedStrategy = { id: id(), name, script: tab.name, code: runCode || tab.code, inputs: { ...inputs }, props: { ...props }, at: Date.now() };
      const next = [entry, ...get().strategies].slice(0, 50);
      set({ strategies: next }); write(STRAT_KEY, next);
    },
    loadStrategy: (sid) => {
      const s = get().strategies.find((x) => x.id === sid);
      if (!s) return;
      const tab = { id: id(), name: s.script, code: s.code, savedId: null };
      set({ tabs: [...get().tabs, tab], active: tab.id, source: s.code, inputs: { ...s.inputs }, props: { ...DEFAULT_PROPS, ...s.props } });
      persist();
      get().apply();
    },
    deleteStrategy: (sid) => {
      const next = get().strategies.filter((x) => x.id !== sid);
      set({ strategies: next }); write(STRAT_KEY, next);
    },
  };
});

// The chart's list and the script agree: removing the script's row takes it off the chart, and a
// script left on the chart from the last visit gets its row back.
useStudies.subscribe((s, prev) => {
  const had = prev.list.some((x) => x.key === SCRIPT);
  const has = s.list.some((x) => x.key === SCRIPT);
  if (had && !has && useScript.getState().on) { useScript.setState({ on: false, result: null, backtest: null }); try { const v = JSON.parse(localStorage.getItem(KEY) ?? '{}') as Record<string, unknown>; localStorage.setItem(KEY, JSON.stringify({ ...v, on: false })); } catch { /* ignore */ } }
});
{
  const on = useScript.getState().on;
  const has = useStudies.getState().list.some((x) => x.key === SCRIPT);
  if (on !== has) useStudies.getState().setScript(on);
}
