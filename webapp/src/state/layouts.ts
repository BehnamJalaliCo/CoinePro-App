// Saved chart layouts and indicator templates, as TradingView's "Save" and "Indicator templates":
// a layout keeps the symbol, interval, chart type, studies and compared symbols; a template keeps
// the studies alone. Kept in this browser.
import { create } from 'zustand';
import { usePrefs } from './prefs';
import { useStudies, type Study } from './studies';
import { useCompare } from './compare';
import type { ChartType } from '../chart/chartTypes';

export interface Layout { name: string; symbol: string; interval: string; chartType: ChartType; studies: Study[]; compare: string[]; savedAt: number }
export interface Template { name: string; studies: Study[] }

const KEY = 'pc.layouts.v1';
interface Stored { layouts: Layout[]; templates: Template[]; current: string | null }
const read = (): Stored => {
  try { const v = JSON.parse(localStorage.getItem(KEY) ?? 'null') as Stored | null; if (v && Array.isArray(v.layouts)) return { layouts: v.layouts, templates: v.templates ?? [], current: v.current ?? null }; } catch { /* fall through */ }
  return { layouts: [], templates: [], current: null };
};

interface LayoutState extends Stored {
  save(name?: string): void;
  load(name: string): void;
  remove(name: string): void;
  saveTemplate(name: string): void;
  applyTemplate(name: string): void;
  removeTemplate(name: string): void;
}

const fresh = (list: Study[]) => list.map((s) => ({ ...s, uid: `${s.key}-${Math.random().toString(36).slice(2, 8)}` }));

export const useLayouts = create<LayoutState>((set, get) => {
  const persist = () => { const { layouts, templates, current } = get(); try { localStorage.setItem(KEY, JSON.stringify({ layouts, templates, current })); } catch { /* ignore */ } };
  return {
    ...read(),
    save: (name) => {
      const n = (name ?? get().current ?? 'Layout').trim() || 'Layout';
      const p = usePrefs.getState();
      const l: Layout = { name: n, symbol: p.symbol, interval: p.interval, chartType: p.chartType, studies: useStudies.getState().list, compare: useCompare.getState().symbols, savedAt: Date.now() };
      set({ layouts: [l, ...get().layouts.filter((x) => x.name !== n)], current: n }); persist();
    },
    load: (name) => {
      const l = get().layouts.find((x) => x.name === name);
      if (!l) return;
      const p = usePrefs.getState();
      p.setSymbol(l.symbol); p.setInterval(l.interval); p.setChartType(l.chartType);
      useStudies.setState({ list: fresh(l.studies) });
      try { localStorage.setItem('pc.studies.v1', JSON.stringify(useStudies.getState().list)); } catch { /* ignore */ }
      useCompare.setState({ symbols: l.compare });
      set({ current: name }); persist();
    },
    remove: (name) => { set({ layouts: get().layouts.filter((x) => x.name !== name), current: get().current === name ? null : get().current }); persist(); },
    saveTemplate: (name) => {
      const n = name.trim();
      if (!n) return;
      set({ templates: [{ name: n, studies: useStudies.getState().list }, ...get().templates.filter((x) => x.name !== n)] }); persist();
    },
    applyTemplate: (name) => {
      const tpl = get().templates.find((x) => x.name === name);
      if (!tpl) return;
      useStudies.setState({ list: fresh(tpl.studies) });
      try { localStorage.setItem('pc.studies.v1', JSON.stringify(useStudies.getState().list)); } catch { /* ignore */ }
    },
    removeTemplate: (name) => { set({ templates: get().templates.filter((x) => x.name !== name) }); persist(); },
  };
});
