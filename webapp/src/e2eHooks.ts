// Handles for the end-to-end suite only (loaded on ?e2e=1): the stores and the engine catalogues.
import { usePrefs } from './state/prefs';
import { useStudies } from './state/studies';
import { useDrawing } from './state/drawing';
import { useScript } from './state/script';
import { useChartBus } from './chart/chartBus';
import { useReplay } from './state/replay';
import { useAlerts } from './state/alerts';
import { usePanels } from './state/panels';
import { useGrid } from './state/grid';
import { useMarket } from './state/market';
import { useCompare } from './state/compare';
import { usePage } from './state/page';
import { useWatchlist, useWatchView } from './state/watchlist';
import { useOverlay } from './state/overlay';
import { useChartSettings } from './state/chartSettings';
import { usePaper } from './state/paper';
import { useJournal } from './state/journal';
import { PAGES } from './pages/registry';
import { REGISTRY } from './engine/indicators.js';
import { EXAMPLES } from './engine/scriptlib.js';
import { TOOL_GROUPS } from './chart/tools';
import { CHART_TYPES } from './chart/chartTypes';

export function expose(): void {
  (window as unknown as { __pc: unknown }).__pc = {
    usePrefs, useStudies, useDrawing, useScript, useChartBus, useReplay, useAlerts, usePanels, useGrid, useMarket, useCompare, usePage, usePaper, useJournal, useWatchlist, useChartSettings, useWatchView, useOverlay,
    pages: PAGES.map((p) => ({ id: p.id, path: p.path, member: Boolean(p.member) })),
    indicators: Object.keys(REGISTRY), examples: EXAMPLES.map((e) => e.name), exampleCode: EXAMPLES.map((e) => e.code),
    tools: TOOL_GROUPS.flatMap((g) => g.tools.map((x) => ({ id: x.id, group: g.id }))), chartTypes: CHART_TYPES.map((c) => c.id),
  };
}
