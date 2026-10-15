// What the page tells the panel server about itself, only while someone is signed in and only when
// the reader has not turned it off (Data and privacy): its uncaught errors, and which features are
// used — pages opened, indicators added, drawing tools picked, chart types and intervals, alerts.
// Batched every 30 s, the rest when the tab is hidden. Also: the relay forgets this browser's
// session when the reader signs out, and the cloud workspace is pushed when auto-sync is on.
import { usePage } from '../state/page';
import { useStudies } from '../state/studies';
import { useDrawing } from '../state/drawing';
import { useAlerts } from '../state/alerts';
import { usePrefs } from '../state/prefs';
import { useSession } from '../data/session';
import { dropGuestToken } from '../data/forexAccess';
import { describeError, events, flushEvents, panelBackend, readMemo, relaySignOut, syncWorkspace, telemetryOn } from '../data/panel';

const FLUSH_MS = 30_000;
const SYNC_MS = 120_000;

let started = false;

export function startPanel(): () => void {
  if (started) return () => undefined;
  started = true;
  const note = (kind: 'error' | 'feature' | 'page', name: string, detail?: Parameters<typeof events.push>[2]) => {
    if (panelBackend() && telemetryOn()) events.push(kind, name, detail);
  };

  const onError = (e: ErrorEvent) => {
    const file = (e.filename || '').split('/').pop() ?? '';
    const d = describeError(e.error ?? new Error(e.message), file ? `${file}:${e.lineno}:${e.colno}` : '');
    note('error', d.name, d.detail);
  };
  const onRejection = (e: PromiseRejectionEvent) => { const d = describeError(e.reason, 'promise'); note('error', d.name, d.detail); };
  window.addEventListener('error', onError);
  window.addEventListener('unhandledrejection', onRejection);

  const unsubs = [
    usePage.subscribe((s, prev) => { if (s.page && s.page !== prev.page && s.page !== 'panel') note('page', `page/${s.page}`); }),
    useStudies.subscribe((s, prev) => {
      const before = new Set(prev.list.map((x) => x.uid));
      for (const x of s.list) if (!before.has(x.uid)) note('feature', `indicator.add.${x.key}`);
    }),
    useDrawing.subscribe((s, prev) => { if (s.tool !== prev.tool && s.tool !== 'cursor') note('feature', `tool.${s.tool}`); }),
    useAlerts.subscribe((s, prev) => { if (s.list.length > prev.list.length) note('feature', 'alert.create'); }),
    usePrefs.subscribe((s, prev) => {
      if (s.chartType !== prev.chartType) note('feature', `chart.type.${s.chartType}`);
      if (s.interval !== prev.interval) note('feature', `chart.interval.${s.interval}`);
      if (s.lang !== prev.lang) note('feature', `lang.${s.lang}`);
    }),
    useSession.subscribe((s, prev) => {
      const was = Boolean(prev.sessions.tradeyar || prev.sessions.coineprofx);
      const now = Boolean(s.sessions.tradeyar || s.sessions.coineprofx);
      if (was && !now) {
        events.clear();
        relaySignOut();
        // The guest chart token lived in the same relay session; the next chart call mints a new one.
        dropGuestToken();
      }
    }),
  ];

  const push = () => { if (panelBackend() && readMemo().auto) void syncWorkspace({ pushOnly: true }).catch(() => undefined); };
  const flush = window.setInterval(() => { void flushEvents(); }, FLUSH_MS);
  const sync = window.setInterval(push, SYNC_MS);
  const onHide = () => { if (document.visibilityState === 'hidden') { void flushEvents(true); push(); } };
  document.addEventListener('visibilitychange', onHide);

  return () => {
    started = false;
    window.removeEventListener('error', onError);
    window.removeEventListener('unhandledrejection', onRejection);
    document.removeEventListener('visibilitychange', onHide);
    window.clearInterval(flush);
    window.clearInterval(sync);
    unsubs.forEach((u) => u());
  };
}
