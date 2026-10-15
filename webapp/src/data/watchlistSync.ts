// The signed-in reader's watchlists are the phone's (core/watchlistsync): one JSON document on
// TradeYar, `{version, payload:{schema:1, lists:[{id, name, symbols, …}], deleted}}`. The web shows
// every list of the document and writes back the lists it changed, by id, leaving every other
// field exactly as the phone wrote it; a list deleted here goes into `deleted`. A 409 carries the
// current document: take it, re-apply.
import { authed } from './auth';
import { HttpError } from './http';
import { useSession } from './session';
import { useWatchlist, type WList } from '../state/watchlist';

const PATH = '/up/tradeyar/api/mobile/v1/watchlists';

interface ListDoc { id: string; name?: string; symbols: string[]; updated_at_ms?: number; [k: string]: unknown }
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

function listsOf(p: Payload | null): WList[] {
  const del = p?.deleted ?? {};
  return (p?.lists ?? []).filter((l) => Array.isArray(l.symbols) && !(l.id in del))
    .map((l) => ({ id: String(l.id), name: String(l.name ?? 'Watchlist'), symbols: l.symbols.filter((s) => typeof s === 'string') }));
}

async function pull(): Promise<void> {
  const r = readDoc(await authed('tradeyar', PATH));
  if (!r) return;
  doc = r;
  const lists = listsOf(r.payload);
  if (lists.some((l) => l.symbols.length)) {
    applyingRemote = true;
    useWatchlist.getState().setLists(lists);
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
    const payload: Payload = { schema: 1, ...(doc.payload ?? {}), lists: [...(doc.payload?.lists ?? [])], deleted: { ...(doc.payload?.deleted ?? {}) } };
    const docLists = payload.lists!;
    for (const l of lists) {
      const i = docLists.findIndex((d) => String(d.id) === l.id);
      if (i >= 0) {
        const d = docLists[i]!;
        if (d.name !== l.name || JSON.stringify(d.symbols) !== JSON.stringify(l.symbols)) docLists[i] = { ...d, name: l.name, symbols: l.symbols, updated_at_ms: now };
      } else docLists.push({ id: l.id, name: l.name, symbols: l.symbols, created_at_ms: now, updated_at_ms: now });
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
    if (applyingRemote || !doc || s.lists === prev.lists) return;
    removed = [...removed, ...prev.lists.filter((l) => !s.lists.some((x) => x.id === l.id)).map((l) => l.id)];
    if (timer) clearTimeout(timer);
    timer = setTimeout(() => { const gone = removed; removed = []; void push(useWatchlist.getState().lists, gone); }, 800);
  });
}
