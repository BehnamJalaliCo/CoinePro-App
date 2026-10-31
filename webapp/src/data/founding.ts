// «عضو بنیان‌گذار»: whether this reader arrived while the whole app was free (the phone's
// ProfileStore.foundingMember, noted from Entitlements.foundingMember). The mark is written once, the
// first time an account's entitlements answer `all: true` in this browser, and never written false —
// somebody who arrives after the free period does not get it, and somebody who has it keeps it.
// Local, like the phone's, so it is shown on the reader's own posts only: this browser knows it about
// exactly one person.
import { useEntitlements } from '../trade/entitlements';

const KEY = 'pc.founding.v1';

export function isFounding(): boolean {
  try { return localStorage.getItem(KEY) === '1'; } catch { return false; }
}

/** Notes the mark when any signed-in platform says everything is open; idempotent and one-way. */
export function noteFounding(byVenue: Record<string, { all: boolean } | undefined>): boolean {
  if (isFounding()) return true;
  if (!Object.values(byVenue).some((e) => e?.all === true)) return false;
  try { localStorage.setItem(KEY, '1'); } catch { return false; }
  return true;
}

noteFounding(useEntitlements.getState().byVenue);
useEntitlements.subscribe((s) => { noteFounding(s.byVenue); });
