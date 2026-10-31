// «معامله با کارگزار»: the venues this app hands a reader to (the phone's TradePartners.kt) — the
// broker first, then the two exchanges — each through the referral link the VIP funnel already uses
// (TradeYar's VIP rules for LBank and Ourbit, CoinePro-FX's forex-VIP rules for OneRoyal), with the
// phone's own addresses when a server says nothing. Every link leaves the app, and only over https.
import { LOGO_LBANK, LOGO_ONEROYAL, LOGO_OURBIT, type PartnerLogo } from './partnerLogos';

export type PartnerId = 'oneroyal' | 'lbank' | 'ourbit';
export interface Partner { id: PartnerId; name: string; kind: 'broker' | 'exchange'; url: string; logo: PartnerLogo; carriesName: boolean }

/** The phone's addresses: OneRoyal's referral link, and the exchanges' own sign-up pages. */
export const PARTNER_FALLBACK: Record<PartnerId, string> = {
  oneroyal: 'https://vc.cabinet.oneroyal.com/fa/links/go/16669',
  lbank: 'https://www.lbank.com/register/',
  ourbit: 'https://www.ourbit.com/register',
};

/** An address a link may open: https with a host, nothing else (no javascript:, no intent://). */
export function isSafeLink(url: string | null | undefined): url is string {
  if (!url) return false;
  try { const u = new URL(url); return u.protocol === 'https:' && Boolean(u.hostname); } catch { return false; }
}

/** The three venues in their order, each with the server's referral link when it is a safe one. */
export function partnerList(links: Partial<Record<PartnerId, string | null>> = {}): Partner[] {
  const url = (id: PartnerId) => (isSafeLink(links[id]) ? links[id]! : PARTNER_FALLBACK[id]);
  return [
    { id: 'oneroyal', name: 'OneRoyal', kind: 'broker', url: url('oneroyal'), logo: LOGO_ONEROYAL, carriesName: true },
    { id: 'lbank', name: 'LBank', kind: 'exchange', url: url('lbank'), logo: LOGO_LBANK, carriesName: false },
    { id: 'ourbit', name: 'Ourbit', kind: 'exchange', url: url('ourbit'), logo: LOGO_OURBIT, carriesName: false },
  ];
}
