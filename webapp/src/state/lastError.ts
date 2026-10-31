// «آخرین خطای برنامه» — the last thing that broke in this browser, kept for the reader to copy, send
// or clear (OLD app LaunchReadinessScreen CrashCard). The page's own uncaught errors and unhandled
// rejections, never sent anywhere by this module: when, the error's own line, the first frame from
// this app, and the whole trace for the copy.
import { create } from 'zustand';

export interface LastError { at: number; summary: string; culprit: string | null; trace: string }

const KEY = 'pc.lastError.v1';
const MAX_TRACE = 8_000;

function read(): LastError | null {
  try {
    const v = JSON.parse(localStorage.getItem(KEY) ?? 'null') as Partial<LastError> | null;
    return v && typeof v.at === 'number' && typeof v.summary === 'string' ? { at: v.at, summary: v.summary, culprit: typeof v.culprit === 'string' ? v.culprit : null, trace: typeof v.trace === 'string' ? v.trace : v.summary } : null;
  } catch { return null; }
}

/** The report from an error: its line, the first frame from this app's own scripts, the trace. */
export function describe(error: unknown, where = '', at = Date.now(), origin = typeof location === 'undefined' ? '' : location.origin): LastError {
  const e = error as { name?: unknown; message?: unknown; stack?: unknown } | null;
  const name = typeof e?.name === 'string' && e.name ? e.name : 'Error';
  const message = typeof e?.message === 'string' ? e.message : String(error ?? 'unknown');
  const stack = typeof e?.stack === 'string' ? e.stack : '';
  const frames = stack.split('\n').map((l) => l.trim()).filter((l) => /^at |@/.test(l));
  const own = frames.find((l) => (origin && l.includes(origin)) || /\/assets\/|\.tsx?:|\.js:/.test(l)) ?? (where || null);
  const summary = `${name}: ${message}`.slice(0, 400);
  const version = typeof __APP_VERSION__ === 'string' ? __APP_VERSION__ : '';
  const trace = [`Pro Chart web ${version}`, new Date(at).toISOString(), typeof location === 'undefined' ? '' : location.pathname, summary, stack || where].filter(Boolean).join('\n').slice(0, MAX_TRACE);
  return { at, summary, culprit: own ? own.replace(/^at /, '').slice(0, 200) : null, trace };
}

interface LastErrorState { last: LastError | null; record(e: LastError): void; clear(): void }
export const useLastError = create<LastErrorState>((set) => ({
  last: read(),
  record: (e) => { set({ last: e }); try { localStorage.setItem(KEY, JSON.stringify(e)); } catch { /* full or blocked */ } },
  clear: () => { set({ last: null }); try { localStorage.removeItem(KEY); } catch { /* blocked */ } },
}));

let started = false;
export function startErrorCapture(): void {
  if (started || typeof window === 'undefined') return;
  started = true;
  window.addEventListener('error', (ev: ErrorEvent) => {
    // A resource that failed to load is not the app breaking.
    if (!ev.error && !ev.message) return;
    const file = (ev.filename || '').split('/').pop() ?? '';
    useLastError.getState().record(describe(ev.error ?? new Error(ev.message), file ? `${file}:${ev.lineno}:${ev.colno}` : ''));
  });
  window.addEventListener('unhandledrejection', (ev: PromiseRejectionEvent) => {
    useLastError.getState().record(describe(ev.reason, 'promise'));
  });
}
