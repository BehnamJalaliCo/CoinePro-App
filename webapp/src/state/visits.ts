// When the reader was last here, for Home's «وقتی نبودید» (OLD core/datastore LastVisitStore and
// the shell's DisposableEffect). The visit is stamped on the way *out* — the tab hidden or the page
// left — never on arrival: stamped on arrival, «since your last visit» would measure a window of
// zero. The value read at start is the previous visit, kept for the whole session.
const KEY = 'pc.lastVisit.v1';

const readStamp = (): number => { try { const n = Number(localStorage.getItem(KEY)); return Number.isFinite(n) && n > 0 ? n : 0; } catch { return 0; } };
let previous = readStamp();
let started = false;

/** The visit before this one (ms), 0 on a first visit. */
export function previousVisit(): number { return previous; }

export function stampVisit(at = Date.now()): void { try { localStorage.setItem(KEY, String(at)); } catch { /* private window */ } }

export function startVisitClock(): void {
  if (started || typeof window === 'undefined') return;
  started = true;
  previous = readStamp();
  const out = () => stampVisit();
  window.addEventListener('pagehide', out);
  document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'hidden') out(); });
}
