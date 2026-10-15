// Where an indicator sits, as TradingView's legend «⋯ → Move to / Pin to scale» puts it: its own
// pane or the main one, and the right scale, the left scale or no scale. Kept per indicator.
import { create } from 'zustand';

export interface Placement { pane?: 'main' | 'new'; scale?: 'left' | 'right' | 'none' }

const KEY = 'pc.studyplace.v1';
const read = (): Record<string, Placement> => {
  try { const v = JSON.parse(localStorage.getItem(KEY) ?? '{}') as unknown; return v && typeof v === 'object' ? (v as Record<string, Placement>) : {}; } catch { return {}; }
};

interface PlacementState {
  byUid: Record<string, Placement>;
  set(uid: string, p: Placement): void;
  forget(uid: string): void;
}

export const usePlacement = create<PlacementState>((set, get) => {
  const save = () => { try { localStorage.setItem(KEY, JSON.stringify(get().byUid)); } catch { /* storage blocked */ } };
  return {
    byUid: read(),
    set: (uid, p) => { set({ byUid: { ...get().byUid, [uid]: { ...get().byUid[uid], ...p } } }); save(); },
    forget: (uid) => { const { [uid]: _x, ...rest } = get().byUid; void _x; set({ byUid: rest }); save(); },
  };
});
