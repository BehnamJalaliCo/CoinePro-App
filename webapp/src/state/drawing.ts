// Which drawing tool is armed and the global drawing switches. The chart's DrawingLayer reads this;
// the rail writes it. Kept apart from the layer so the rail never needs the chart to exist.
import { create } from 'zustand';

interface DrawingState {
  tool: string;
  lastInGroup: Record<string, string>;
  magnet: boolean;
  locked: boolean;
  hidden: boolean;
  /** Bumped to ask the chart to remove every drawing on the current symbol. */
  clearSeq: number;
  setTool(id: string, group: string): void;
  setMagnet(on: boolean): void;
  setLocked(on: boolean): void;
  setHidden(on: boolean): void;
  clearAll(): void;
}

export const useDrawing = create<DrawingState>((set) => ({
  tool: 'cursor',
  lastInGroup: {},
  magnet: false,
  locked: false,
  hidden: false,
  clearSeq: 0,
  setTool: (id, group) => set((s) => ({ tool: id, lastInGroup: { ...s.lastInGroup, [group]: id } })),
  setMagnet: (magnet) => set({ magnet }),
  setLocked: (locked) => set({ locked }),
  setHidden: (hidden) => set({ hidden }),
  clearAll: () => set((s) => ({ clearSeq: s.clearSeq + 1 })),
}));
