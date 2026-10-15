// The signed-in reader's watchlists are the phone's (core/watchlistsync): one JSON document on
// TradeYar, `{version, payload:{schema:1, lists:[{id, name, symbols, flags, …}], deleted}}`. The web
// shows every list of the document and writes back the lists it changed, by id, leaving every
// other field exactly as the phone wrote it; a list deleted here goes into `deleted`. A 409 carries
// the current document: take it, re-apply.
//
// Flags travel in the phone's own per-list `flags` map (`{"XAUUSD": "red"}`, the phone's colour
// ids; cyan and pink are the web's and the phone drops them). Sections and notes, which the phone
// does not have, ride in the list's extra fields `web_sections` and `web_notes`.
import { authed } from './auth';
import { HttpError } from './http';
import { useSession } from './session';
import { FLAG_IDS, useWatchlist, type WList, type WSection } from '../state/watchlist';

const PATH = '/up/tradeyar/api/mobile/v1/watchlists';

interface ListDoc { id: string; name?: string; symbols: string[]; updated_at_ms?: number; flags?: Record<string, string>; web_sections?: unknown; web_notes?: Record<string, string>; [k: string]: unknown }
interface Payload { schema?: number; lists?: ListDoc[]; deleted?: Record<string, number>; [k: string]: unknown }
interface Doc { version: number; payload: Payload | null }

let doc: Doc | null = null;
let pushing = false;
let applyingRemote = false;

function readDoc(raw: unknown): Doc | null {
  const d = (raw as { detail?: unknown })?.detail && typeof (raw as { detail?: unknown }).detail === 'object' ? (raw as { detail: unknown }).detail : raw;
  const v = d as { version?: unknown; payload?: unknown };
  if (typeof v?.version !== 'number') return null;
  return { version: v.version, payload: (v.payload as Payload) ?? null };
}

function sectionsOf(raw: unknown): WSection[] | undefined {
  if (!Array.isArray(raw)) return undefined;
  const out = raw.filter((s): s is Record<string, unknown> => Boolean(s) && typeof s === 'object')
    .map((s) => ({ id: String(s.id ?? ''), name: String(s.name ?? ''), symbols: Array.isArray(s.symbols) ? s.symbols.filter((x): x is string => typeof x === 'string') : [], ...(s.collapsed ? { collapsed: true } : {}) }))
    .filter((s) => s.id);
  return out.length ? out : undefined;
}

export function listsOf(p: Payload | null): WList[] {
  const del = p?.deleted ?? {};
  return (p?.lists ?? []).filter((l) => Array.isArray(l.symbols) && !(l.id in del))
    .map((l) => ({ id: String(l.id), name: String(l.name ?? 'Watchlist'), symbols: l.symbols.filter((s) => typeof s === 'string'), sections: sectionsOf(l.web_sections) }));
}

/** Every list's flags and notes folded into the web's single map (the first list to name a symbol wins). */
export function marksOf(p: Payload | null): { flags: Record<string, number>; notes: Record<string, string> } {
  const flags: Record<string, number> = {};
  const notes: Record<string, string> = {};
  for (const l of p?.lists ?? []) {
    for (const [s, id] of Object.entries(l.flags ?? {})) { const i = FLAG_IDS.indexOf(String(id)); if (i >= 0 && flags[s] == null) flags[s] = i; }
    for (const [s, n] of Object.entries(l.web_notes ?? {})) if (typeof n === 'string' && n && notes[s] == null) notes[s] = n;
  }
  return { flags, notes };
}

/** One list's document entry as the web would write it, from the doc's own entry (or none). */
export function listDoc(d: ListDoc | undefined, l: WList, flags: Record<string, number>, notes: Record<string, string>, now: number): ListDoc {
  const f: Record<string, string> = {};
  const n: Record<string, string> = {};
  for (const s of l.symbols) { if (flags[s] != null && FLAG_IDS[flags[s]!]) f[s] = FLAG_IDS[flags[s]!]!; if (notes[s]) n[s] = notes[s]!; }
  const next: ListDoc = { ...(d ?? { created_at_ms: now }), id: l.id, name: l.name, symbols: l.symbols };
  if (Object.keys(f).length) next.flags = f; else delete next.flags;
  if (l.sections?.length) next.web_sections = l.sections.map((s) => ({ id: s.id, name: s.name, symbols: s.symbols, ...(s.collapsed ? { collapsed: true } : {}) })); else delete next.web_sections;
  if (Object.keys(n).length) next.web_notes = n; else delete next.web_notes;
  const same = d && JSON.stringify({ ...d, updated_at_ms: 0 }) === JSON.stringify({ ...next, updated_at_ms: 0 });
  next.updated_at_ms = same ? d!.updated_at_ms : now;
  return next;
}

async function pull(): Promise<void> {
  const r = readDoc(await authed('tradeyar', PATH));
  if (!r) return;
  doc = r;
  const lists = listsOf(r.payload);
  if (lists.some((l) => l.symbols.length)) {
    applyingRemote = true;
    const st = useWatchlist.getState();
    st.setLists(lists);
    const marks = marksOf(r.payload);
    if (Object.keys(marks.flags).length || Object.keys(marks.notes).length) st.setMarks({ ...st.flags, ...marks.flags }, { ...st.notes, ...marks.notes });
    applyingRemote = false;
  } else {
    await push(useWatchlist.getState().lists, []);
  }
}

async function push(lists: WList[], removed: string[], attempt = 0): Promise<void> {
  if (!doc || pushing) return;
  pushing = true;
  try {
    const now = Date.now();
    const { flags, notes } = useWatchlist.getState();
    const payload: Payload = { schema: 1, ...(doc.payload ?? {}), lists: [...(doc.payload?.lists ?? [])], deleted: { ...(doc.payload?.deleted ?? {}) } };
    const docLists = payload.lists!;
    for (const l of lists) {
      const i = docLists.findIndex((d) => String(d.id) === l.id);
      if (i >= 0) docLists[i] = listDoc(docLists[i], l, flags, notes, now);
      else docLists.push(listDoc(undefined, l, flags, notes, now));
    }
    for (const id of removed) { payload.lists = payload.lists!.filter((d) => String(d.id) !== id); payload.deleted![id] = now; }
    const r = readDoc(await authed('tradeyar', PATH, { method: 'PUT', body: { version: doc.version, payload } }));
    if (r) doc = r;
  } catch (e) {
    if (e instanceof HttpError && e.status === 409 && attempt < 2) {
      const current = readDoc(e.body);
      if (current) { doc = current; pushing = false; return push(lists, removed, attempt + 1); }
    }
  } finally {
    pushing = false;
  }
}

let started = false;
export function startWatchlistSync(): void {
  if (started) return;
  started = true;
  let signed = Boolean(useSession.getState().sessions.tradeyar);
  if (signed) void pull().catch(() => undefined);
  useSession.subscribe((s) => {
    const now = Boolean(s.sessions.tradeyar);
    if (now && !signed) void pull().catch(() => undefined);
    if (!now) doc = null;
    signed = now;
  });
  let timer: ReturnType<typeof setTimeout> | null = null;
  let removed: string[] = [];
  useWatchlist.subscribe((s, prev) => {
    if (applyingRemote || !doc || (s.lists === prev.lists && s.flags === prev.flags && s.notes === prev.notes)) return;
    removed = [...removed, ...prev.lists.filter((l) => !s.lists.some((x) => x.id === l.id)).map((l) => l.id)];
    if (timer) clearTimeout(timer);
    timer = setTimeout(() => { const gone = removed; removed = []; void push(useWatchlist.getState().lists, gone); }, 800);
  });
}
