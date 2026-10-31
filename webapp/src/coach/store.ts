// The coach's state: what has been put away (kept in this browser) and, for this launch only, what is
// on screen, how many have been shown and the quiet after the last one.
import { create } from 'zustand';
import { COACH_TIPS, COOLDOWN_MS } from './tips';

const KEY = 'pc.coach.v1';

// The end-to-end suite opens every page with ?e2e=1; the coach and the first run stay out of those
// pages unless a test asks for them (&coach=1, &firstrun=1, or the matching localStorage flag), so no
// other suite ever meets a tip or a welcome screen. Read once, before the address is rewritten.
const params = (() => { try { return new URLSearchParams(window.location.search); } catch { return new URLSearchParams(); } })();
const flag = (k: string) => { try { return localStorage.getItem(k) === '1'; } catch { return false; } };
export const E2E = params.has('e2e');
export const coachAllowed = !E2E || params.has('coach') || flag('pc.coach.e2e');
export const firstRunAllowed = !E2E || params.has('firstrun') || flag('pc.firstrun.e2e');

interface Stored { dismissed: string[]; off: boolean }

function read(): Stored {
  try {
    const v = JSON.parse(localStorage.getItem(KEY) ?? '{}') as Partial<Stored>;
    return { dismissed: Array.isArray(v.dismissed) ? v.dismissed.filter((x) => typeof x === 'string') : [], off: v.off === true };
  } catch { return { dismissed: [], off: false }; }
}

interface CoachState extends Stored {
  /** The tip on screen, spotlit on its control. */
  showing: string | null;
  /** A tip played from the library: no spotlight, the card in the middle of the glass. */
  preview: string | null;
  shown: number;
  coolingUntil: number;
  /** Shows [id] on its control now and puts it away for good. */
  show(id: string): void;
  /** Plays one tip now, from the library. */
  play(id: string): void;
  close(): void;
  /** «بلدم، دیگر نشان داده نشود»: the whole coach put away. */
  never(): void;
  /** «همه را دوباره نشان بده»: every tip back, the session cap and the cooldown forgotten. */
  replayAll(): void;
}

export const useCoach = create<CoachState>((set, get) => {
  const save = () => { const { dismissed, off } = get(); try { localStorage.setItem(KEY, JSON.stringify({ dismissed, off })); } catch { /* storage blocked: for this visit only */ } };
  return {
    ...read(),
    showing: null,
    preview: null,
    shown: 0,
    coolingUntil: 0,
    show: (id) => {
      const s = get();
      set({ showing: id, shown: s.shown + 1, dismissed: s.dismissed.includes(id) ? s.dismissed : [...s.dismissed, id] });
      save();
    },
    play: (id) => set({ preview: id }),
    close: () => set({ showing: null, preview: null, coolingUntil: Date.now() + COOLDOWN_MS }),
    never: () => { set({ off: true, showing: null, preview: null, coolingUntil: Date.now() + COOLDOWN_MS }); save(); },
    replayAll: () => { set({ dismissed: [], off: false, shown: 0, coolingUntil: 0 }); save(); },
  };
});

/** Every tip already put away (or the whole coach), so nothing is left to watch for. */
export const coachDone = (s: Stored) => s.off || COACH_TIPS.every((t) => s.dismissed.includes(t.id));
