// The chart area: the main chart and the other charts of the multi-chart layout, arranged by the
// chosen preset, the active one outlined (the top bar drives it), Alt+Enter to maximise it. A
// phone shows the main chart alone, as the old Pro-Chart did.
import { useEffect, type ReactNode } from 'react';
import type { ChartFeed } from '../chart/ChartView';
import { GridSync, LiteChart } from '../chart/LiteChart';
import { CELL_AREAS, cellCount, gridTemplate, useGrid } from '../state/grid';
import { usePrefs } from '../state/prefs';
import { SessionShading } from './SessionShading';
import './topbar.css';

export function ChartGrid({ feed, children, onCellSearch }: { feed: ChartFeed; children: ReactNode; onCellSearch(i: number): void }) {
  const { grid, cells, active, maximized, setActive, setMaximized, setCell } = useGrid();
  const n = cellCount(grid);
  const multi = n > 1;

  // Alt+Enter: the active chart alone, and back.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (!e.altKey || e.ctrlKey || e.metaKey || e.shiftKey || e.key !== 'Enter') return;
      const el = e.target as HTMLElement | null;
      if (el?.closest('input, textarea, select, [contenteditable]')) return;
      const g = useGrid.getState();
      if (cellCount(g.grid) < 2) return;
      e.preventDefault();
      g.setMaximized(!g.maximized);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  // On a phone only the main chart is shown; the top bar drives it.
  useEffect(() => {
    const mq = window.matchMedia?.('(max-width: 760px)');
    if (!mq) return;
    const apply = () => { if (mq.matches && useGrid.getState().active !== 0) useGrid.getState().setActive(0); };
    apply();
    mq.addEventListener?.('change', apply);
    return () => mq.removeEventListener?.('change', apply);
  }, []);

  const promote = (i: number) => {
    const p = usePrefs.getState();
    const c = cells[i]!;
    const main = { symbol: p.symbol, interval: p.interval, chartType: p.chartType };
    p.setSymbol(c.symbol); p.setInterval(c.interval);
    if (c.chartType) p.setChartType(c.chartType);
    setCell(i, main);
    setActive(0);
  };

  return (
    <div className={`chart-grid g-${grid}${multi ? ' multi' : ''}${maximized && multi ? ' maxed' : ''}`} style={gridTemplate(grid)}>
      <div className={`cell cell-main${multi && active === 0 ? ' active' : ''}`} style={{ gridArea: 'a' }} data-cell={0}
        onMouseDownCapture={() => { if (useGrid.getState().active !== 0) setActive(0); }}>
        {children}
        <SessionShading />
      </div>
      {cells.slice(0, n - 1).map((c, i) => (
        <div key={i} className={`cell-slot${active === i + 1 ? ' active' : ''}`} style={{ gridArea: CELL_AREAS[i + 1] }}>
          <LiteChart index={i} feed={feed} cell={c} active={active === i + 1}
            onSymbol={() => { setActive(i + 1); onCellSearch(i); }}
            onActivate={() => { if (useGrid.getState().active !== i + 1) setActive(i + 1); }}
            onMain={() => promote(i)} />
        </div>
      ))}
      {multi ? <GridSync /> : null}
      {maximized && multi ? (
        <button className="cell-restore ib" title="Alt+Enter" onClick={() => setMaximized(false)}>
          <svg className="icon sm" aria-hidden><use href="#ph-arrows-in" /></svg>
        </button>
      ) : null}
    </div>
  );
}
