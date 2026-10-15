// The signed-in reader's watchlist is the phone's (core/watchlistsync): one JSON document on
// TradeYar, `{version, payload:{schema:1, lists:[{id, name, symbols, …}], deleted}}`. The web shows
// the document's first list and writes back only that list's symbols, leaving every other list and
// field exactly as the phone wrote them. A 409 carries the current document: take it, re-apply.
import { authed } from './auth';
import { HttpError } from './http';
import { useSession } from './session';
import { useWatchlist } from '../state/watchlist';

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

function firstList(p: Payload | null): ListDoc | undefined {
  return p?.lists?.find((l) => Array.isArray(l.symbols));
}

async function pull(): Promise<void> {
  const r = readDoc(await authed('tradeyar', PATH));
  if (!r) return;
  doc = r;
  const list = firstList(r.payload);
  if (list?.symbols.length) {
    applyingRemote = true;
    useWatchlist.getState().set(list.symbols.filter((s) => typeof s === 'string'));
    applyingRemote = false;
  } else {
    await push(useWatchlist.getState().symbols);
  }
}

async function push(symbols: string[], attempt = 0): Promise<void> {
  if (!doc || pushing) return;
  pushing = true;
  try {
    const now = Date.now();
    const payload: Payload = { schema: 1, ...(doc.payload ?? {}), lists: [...(doc.payload?.lists ?? [])] };
    const lists = payload.lists!;
    const i = lists.findIndex((l) => Array.isArray(l.symbols));
    if (i >= 0) lists[i] = { ...lists[i]!, symbols, updated_at_ms: now };
    else lists.unshift({ id: `web-${now.toString(36)}`, name: 'Watchlist', symbols, created_at_ms: now, updated_at_ms: now });
    const r = readDoc(await authed('tradeyar', PATH, { method: 'PUT', body: { version: doc.version, payload } }));
    if (r) doc = r;
  } catch (e) {
    if (e instanceof HttpError && e.status === 409 && attempt < 2) {
      const current = readDoc(e.body);
      if (current) { doc = current; pushing = false; return push(symbols, attempt + 1); }
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
  useWatchlist.subscribe((s, prev) => {
    if (applyingRemote || !doc || s.symbols === prev.symbols) return;
    if (timer) clearTimeout(timer);
    timer = setTimeout(() => { void push(s.symbols); }, 800);
  });
}
