// «عضو بنیان‌گذار»: whether this reader arrived while the whole app was free (the phone's
// ProfileStore.foundingMember, noted from Entitlements.foundingMember, which is `all`). The mark is
// written once, the first time an account's entitlements answer `all: true` in this browser, and never
// written false — somebody who arrives after the free period does not get it, and somebody who has it
// keeps it. An account whose own data says `founding` / `founding_member` has it too. Local, like the
// phone's, so it is shown on the reader's own posts and pictures only: this browser knows it about
// exactly one person.
import { useEntitlements } from '../trade/entitlements';
import { useSession } from './session';

const KEY = 'pc.founding.v1';

function accountSays(): boolean {
  const s = useSession.getState().sessions;
  return Boolean(s.tradeyar?.user?.founding || s.coineprofx?.user?.founding);
}

export function isFounding(): boolean {
  if (accountSays()) return true;
  try { return localStorage.getItem(KEY) === '1'; } catch { return false; }
}

export const isFoundingMember = isFounding;

/** Notes the mark when any signed-in platform says everything is open; idempotent and one-way. */
export function noteFounding(byVenue: Record<string, { all: boolean } | undefined>): boolean {
  try { if (localStorage.getItem(KEY) === '1') return true; } catch { /* storage blocked */ }
  if (!Object.values(byVenue).some((e) => e?.all === true)) return false;
  try { localStorage.setItem(KEY, '1'); } catch { return false; }
  return true;
}

/** The stamp's words in the reader's language, or null for a reader without the mark. */
export function foundingStamp(en: boolean): string | null {
  return isFounding() ? (en ? 'Founding member' : 'عضو بنیان‌گذار') : null;
}

noteFounding(useEntitlements.getState().byVenue);
useEntitlements.subscribe((s) => { noteFounding(s.byVenue); });
