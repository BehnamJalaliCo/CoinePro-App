// The first run (OLD web WebApp.kt:335-363): the welcome slides, then the starter preferences, then
// the one question about how much to show (OLD core/datastore ReaderMode.kt). Each is shown once and
// remembered in this browser. A returning reader — one this browser already knows from before this
// screen existed — is never asked: they have effectively made these choices already.
import { create } from 'zustand';
import { firstRunAllowed } from '../coach/store';

/**
 * How much of the app a reader asked to see. Simple hides from the chrome what a first chart has not
 * asked for (replay, the order book, NamaScript, multi-chart layouts); nothing is removed from the
 * app and nothing is gated. Trader is the default and what «بعداً» gives; on the web it shows the
 * whole surface, as Pro does.
 */
export type ReaderMode = 'simple' | 'trader' | 'pro';
export type Rise = 'green' | 'red';

const KEY = 'pc.firstrun.v1';

interface Stored { welcome: boolean; starter: boolean; reader: ReaderMode | null; rise: Rise }

function read(): Stored {
  try {
    const v = JSON.parse(localStorage.getItem(KEY) ?? 'null') as Partial<Stored> | null;
    if (v) return { welcome: v.welcome === true, starter: v.starter === true, reader: v.reader === 'simple' || v.reader === 'trader' || v.reader === 'pro' ? v.reader : null, rise: v.rise === 'red' ? 'red' : 'green' };
  } catch { /* storage blocked */ }
  return { welcome: false, starter: false, reader: null, rise: 'green' };
}

/** A browser that has used the app before (its preferences were saved before this module loaded). */
const returning = (() => { try { return localStorage.getItem('pc.prefs.v1') !== null && localStorage.getItem(KEY) === null; } catch { return true; } })();

export type Step = 'welcome' | 'starter' | 'question' | null;

/** Which first-run screen is due, given what was stored and where the reader is. */
export function stepOf(s: Stored, o: { allowed: boolean; returning: boolean; pageOpen: boolean }): Step {
  if (!o.allowed || o.returning || o.pageOpen) return null;
  if (!s.welcome) return 'welcome';
  if (!s.starter) return 'starter';
  if (!s.reader) return 'question';
  return null;
}

interface FirstRunState extends Stored {
  /** Whether a first-run screen covers the app now (the coach waits while it does). */
  active: boolean;
  setActive(on: boolean): void;
  seeWelcome(): void;
  finishStarter(): void;
  setReader(mode: ReaderMode): void;
  setRise(rise: Rise): void;
}

export const isReturning = returning;
export const firstRunPermitted = firstRunAllowed;

export const useFirstRun = create<FirstRunState>((set, get) => {
  const save = () => { const { welcome, starter, reader, rise } = get(); try { localStorage.setItem(KEY, JSON.stringify({ welcome, starter, reader, rise })); } catch { /* for this visit */ } };
  return {
    ...read(),
    active: false,
    // Stored the moment a first-run screen shows, so a reload half-way is not taken for a returning
    // reader (the app saves its own preferences from the first frame).
    setActive: (active) => { if (get().active !== active) { set({ active }); if (active) save(); } },
    seeWelcome: () => { set({ welcome: true }); save(); },
    finishStarter: () => { set({ starter: true }); save(); },
    setReader: (reader) => { set({ reader }); save(); applyReader(reader); },
    setRise: (rise) => { set({ rise }); save(); applyRise(rise); },
  };
});

export function applyReader(mode: ReaderMode | null): void {
  const root = document.documentElement;
  if (mode === 'simple') root.setAttribute('data-reader', 'simple');
  else root.removeAttribute('data-reader');
}

export function applyRise(rise: Rise): void {
  const root = document.documentElement;
  if (rise === 'red') root.setAttribute('data-rise', 'red');
  else root.removeAttribute('data-rise');
}

applyReader(useFirstRun.getState().reader);
applyRise(useFirstRun.getState().rise);
