// The reader's profile on this device (OLD core/datastore ProfileStore): the name and line others
// see here, the avatar and its ring, and whether balances are hidden. Never sent anywhere.
import { readRing, type AvatarRing } from './Avatar';

export const PROFILE_KEY = 'pc.profile.v1';
export interface Profile { name: string; tagline: string; avatar: string; ring: AvatarRing; hideBalance: boolean }
const BLANK: Profile = { name: '', tagline: '', avatar: '', ring: 'gold', hideBalance: false };
export const readProfile = (): Profile => { try { const v = { ...BLANK, ...(JSON.parse(localStorage.getItem(PROFILE_KEY) ?? '{}') as Partial<Profile>) }; return { ...v, ring: readRing(v.ring) }; } catch { return BLANK; } };
