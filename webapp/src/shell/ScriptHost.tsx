import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { useScript } from '../state/script';
import { useStudies } from '../state/studies';
import { useReplay } from '../state/replay';
import { useChartBus } from '../chart/chartBus';
import { runScript } from '../engine/namascript.js';
import { runScriptBar } from '../engine/namascript_bar.js';
import { fromAlerts, fromIndices, simulate, type SignalSet } from '../pages/backtestEngine';
import type { Candle } from '../data/types';

/** Runs the script on the chart over the bars on screen: once on "Add to chart", again on new bars, on an
 *  input or property change, and on every replay step (so a replayed script never sees its future). */
export async function runOnChart(view: Candle[]): Promise<void> {
  const st = useScript.getState();
  if (!st.on || !view.length) return;
  st.setRunning(true);
  const inputs = { ...st.inputs };
  let engine: 'series' | 'bar' = st.barMode ? 'bar' : 'series';
  let r = await (st.barMode ? runScriptBar : runScript)(st.runCode, view, inputs);
  // Bar by bar does not know every ta.* function yet; the series engine takes over when it is missing one.
  if (st.barMode && !r.ok && /is not a function|is not defined/i.test(r.error ?? '')) { r = await runScript(st.runCode, view, inputs); engine = 'series'; }
  if (!useScript.getState().on) { useScript.getState().setRunning(false); return; }
  let sig: SignalSet | null = null;
  if (r.ok && r.signals) sig = fromIndices(view.length, r.signals);
  else if (r.ok) {
    // No strategy: two alert conditions are a long entry and its exit (the old app's tester rule).
    const al = (r.alerts ?? []).filter((a) => !a.dynamic);
    if (al.length >= 2) sig = fromAlerts(view, al[0]!.bars, al[1]!.bars);
  }
  const s = useScript.getState();
  s.setResult(r, engine);
  s.setBacktest(sig ? simulate(view, sig, s.props) : null);
  s.setRunning(false);
  useStudies.getState().touch('script');
}

const viewNow = () => useReplay.getState().slice ?? useChartBus.getState().bars;

export function ScriptHost() {
  const on = useScript((s) => s.on);
  const seq = useScript((s) => s.seq);
  const slice = useReplay((s) => s.slice);
  const bars = useChartBus((s) => s.bars);
  const [tick, setTick] = useState(0);

  useEffect(() => {
    if (!on) return;
    const id = setTimeout(() => { void runOnChart(viewNow()); }, slice ? 60 : 0);
    return () => clearTimeout(id);
  }, [on, seq, slice, bars, tick]);

  // A new bar closing on the live chart runs the script again (the forming bar's ticks do not).
  useEffect(() => {
    if (!on) return;
    let seen = `${bars.length}:${bars[bars.length - 1]?.t ?? 0}`;
    const id = setInterval(() => {
      if (useReplay.getState().slice) return;
      const b = useChartBus.getState().bars;
      const now = `${b.length}:${b[b.length - 1]?.t ?? 0}`;
      if (now !== seen) { seen = now; setTick((x) => x + 1); }
    }, 3000);
    return () => clearInterval(id);
  }, [on, bars]);

  return <ScriptTables />;
}

/** `table.new` tables over the chart's top-right corner, as TradingView puts a script's table. */
function ScriptTables() {
  const result = useScript((s) => s.result);
  const on = useScript((s) => s.on);
  const hidden = useStudies((s) => s.list.find((x) => x.key === '__script')?.hidden);
  const api = useChartBus((s) => s.api);
  const host = api?.chartElement().closest('.ws-chart') as HTMLElement | null | undefined;
  const tables = on && !hidden && result?.ok ? (result.tables ?? []).filter((t) => t.cells?.length) : [];
  if (!host || !tables.length) return null;
  return createPortal(
    <div className="ns-tables" dir="auto">
      {tables.map((t, i) => {
        const rows = Math.max(...t.cells.map((c) => c.row)) + 1;
        const cols = Math.max(...t.cells.map((c) => c.col)) + 1;
        const grid: (typeof t.cells[number] | undefined)[][] = Array.from({ length: rows }, () => Array.from({ length: cols }, () => undefined));
        for (const c of t.cells) grid[c.row]![c.col] = c;
        return (
          <table key={i} className="ns-table">
            <tbody>{grid.map((r, ri) => <tr key={ri}>{r.map((c, ci) => <td key={ci} style={c?.color ? { color: c.color } : undefined}>{c?.text ?? ''}</td>)}</tr>)}</tbody>
          </table>
        );
      })}
    </div>,
    host,
  );
}
