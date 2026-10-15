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
import { useLayouts } from './state/layouts';
import { PRESETS } from './state/grid';
import { composeSnapshot } from './chart/snapshot';
import { PAGES } from './pages/registry';
import { events as panelEvents, flushEvents as flushPanelEvents } from './data/panel';
import { REGISTRY } from './engine/indicators.js';
import { STUDIES } from './engine/indicators_ext_c.js';
import { EXAMPLES } from './engine/scriptlib.js';
import { TOOL_GROUPS } from './chart/tools';
import { CHART_TYPES } from './chart/chartTypes';
import { usePlacement } from './chart/studyPlacement';

export function expose(): void {
  (window as unknown as { __pc: unknown }).__pc = {
    usePrefs, useStudies, useDrawing, useScript, useChartBus, useReplay, useAlerts, usePanels, useGrid, useMarket, useCompare, usePage, usePaper, useJournal, useWatchlist, useChartSettings, useWatchView, usePlacement, useOverlay,
    useLayouts, gridPresets: PRESETS.map((p) => ({ id: p.id, cells: p.cells })),
    /** The snapshot canvas's size and how many of its pixels differ from the background. */
    snapshotProbe: async (opts?: Record<string, unknown>) => {
      const c = await composeSnapshot({ format: 'png', hiRes: false, watermark: true, caption: true, ...(opts ?? {}) });
      if (!c) return null;
      const d = c.getContext('2d')!.getImageData(0, 0, c.width, c.height).data;
      const bg = [d[0], d[1], d[2]];
      let ink = 0;
      for (let i = 0; i < d.length; i += 16) if (Math.abs(d[i]! - bg[0]!) + Math.abs(d[i + 1]! - bg[1]!) + Math.abs(d[i + 2]! - bg[2]!) > 30) ink++;
      return { width: c.width, height: c.height, ink };
    },
    snapshotDataUrl: async (opts?: Record<string, unknown>) => (await composeSnapshot({ format: 'png', hiRes: false, watermark: true, caption: true, ...(opts ?? {}) }))?.toDataURL('image/png') ?? null,
    pages: PAGES.map((p) => ({ id: p.id, path: p.path, member: Boolean(p.member) })),
    panel: { flush: () => flushPanelEvents(), waiting: () => panelEvents.size },
    indicators: Object.keys(REGISTRY), studyKeys: Object.keys(STUDIES), examples: EXAMPLES.map((e) => e.name), exampleCode: EXAMPLES.map((e) => e.code),
    tools: TOOL_GROUPS.flatMap((g) => g.tools.map((x) => ({ id: x.id, group: g.id }))), chartTypes: CHART_TYPES.map((c) => c.id),
  };
}
