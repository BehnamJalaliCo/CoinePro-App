// The help library (the phone's «؟» catalogue plus the web's own entries, public/generated/help/
// content.json), loaded once and shared by the Tutorials page and the indicator dialog's
// descriptions, and the alias map that lets every id the app uses — a drawing tool's
// (chart/tools.ts) or an indicator's (indicatorCatalog) — open its entry (contract 5).
export interface Bi { fa?: string; en?: string }
export interface BiList { fa?: string[]; en?: string[] }
export interface HelpSection { h: string; body: string | string[] }
export interface HelpEntry {
  title?: Bi; useCase?: Bi; what?: Bi; how?: BiList; tips?: BiList; example?: Bi; pitfall?: Bi;
  images?: { file: string; alt?: Bi }[];
  /** Encyclopedic sections (Persian), from the old Pro-Chart library. */
  sections?: HelpSection[];
  /** A drawn diagram (the old library's schematic), rendered by HelpDiagram. */
  diagram?: unknown;
  /** Where the entry is filed on the Tutorials page. */
  group?: 'timeframes' | 'types' | 'indicators' | 'drawings' | 'features';
}

const BASE = `${import.meta.env.BASE_URL}generated/help/`;
let cache: Promise<Record<string, HelpEntry>> | null = null;

export function loadHelp(): Promise<Record<string, HelpEntry>> {
  cache ??= fetch(`${BASE}content.json`).then((r) => { if (!r.ok) throw new Error(String(r.status)); return r.json() as Promise<Record<string, HelpEntry>>; }).catch((e: unknown) => { cache = null; throw e; });
  return cache;
}
export const helpImage = (file: string) => `${BASE}images/${file}`;

/** App ids whose help entry has another name. */
export const HELP_ALIASES: Record<string, string> = {
  // Drawing tools (chart/tools.ts).
  fixedvp: 'volumeprofile', anchoredvp: 'avolumeprofile', anchoredtext: 'text',
  cursor: 'cross', select: 'arrowcursor', regchannel: 'regression', flatchannel: 'flattop', disjointchannel: 'disjoint',
  schiff: 'pitchfork_schiff', modschiff: 'pitchfork_schiffmod', insidepitchfork: 'pitchfork_inside', gannfixed: 'gannsquarefixed',
  ell_wxy: 'ell_double', ell_wxyxz: 'ell_triple', short: 'longshort', long: 'longshort', arrowup: 'arrowmarks', arrowdown: 'arrowmarks',
  measure: 'ruler', zoom: 'ruler',
  // Indicators (registry keys).
  volatilityStop: 'volstop', chandeKroll: 'chandekroll', massIndex: 'massindex', netVolume: 'netvolume', woodiesCci: 'woodiescci',
  chopZone: 'chopzone', techRatings: 'techRating', prevHLC: 'prevLevels', vpVisible: 'volumeprofile', vpSession: 'svp',
  vpAnchored: 'avolumeprofile', vpFixed: 'volumeprofile_ind', volume: 'volume',
  // Pages and panels.
  backtest: 'strategyTester', script: 'namascript', 'data-window': 'dataWindow',
};

/** The library key for an app id, or null when there is no entry. */
export function helpKey(lib: Record<string, HelpEntry>, id: string | null | undefined): string | null {
  if (!id) return null;
  if (lib[id]) return id;
  const alias = HELP_ALIASES[id];
  if (alias && lib[alias]) return alias;
  const low = id.toLowerCase();
  const found = Object.keys(lib).find((k) => k.toLowerCase() === low);
  return found ?? null;
}
