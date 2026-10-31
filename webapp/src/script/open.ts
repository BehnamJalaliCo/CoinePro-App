// A script document in and out of the studio: the open tab as a document (for a file or a post), and a
// document opened as a tab — or put straight on the chart, the community's one-tap install.
import { useScript } from '../state/script';
import { idFor, noteInstalled, type ScriptDocument } from './doc';

/** The open tab as a document: its saved id when it has one, its «mine» fields, its tuned inputs. */
export function currentDocument(): ScriptDocument {
  const s = useScript.getState();
  const tab = s.tabs.find((t) => t.id === s.active) ?? s.tabs[0]!;
  const saved = tab.savedId ? s.lib.find((x) => x.id === tab.savedId) : undefined;
  const defaults: Record<string, number> = {};
  if (s.on && s.runCode === tab.code) for (const [k, v] of Object.entries(s.inputs)) if (typeof v === 'number' && Number.isFinite(v) && !k.startsWith('__')) defaults[k] = v;
  return {
    id: saved?.id && /^[a-z0-9]{6,32}$/.test(saved.id) ? saved.id : idFor(tab.code, saved?.updatedAt ?? Date.now()),
    name: tab.name, source: tab.code, ...(tab.meta ?? {}),
    ...(Object.keys(defaults).length ? { defaults } : {}),
  };
}

/** Opens a document as a new tab with its name and «mine» fields; on the chart too when asked. */
export function openDocument(doc: ScriptDocument, toChart = false): void {
  const { description, colour, tags, ownPane } = doc;
  const meta = { ...(description ? { description } : {}), ...(colour ? { colour } : {}), ...(tags?.length ? { tags } : {}), ...(ownPane ? { ownPane } : {}) };
  const s = useScript.getState();
  s.newTab(doc.source, doc.name || 'NamaScript', Object.keys(meta).length ? meta : undefined);
  if (toChart) {
    if (doc.defaults) s.setInputs({ ...doc.defaults });
    useScript.getState().apply();
    noteInstalled(doc.id);
  }
}
