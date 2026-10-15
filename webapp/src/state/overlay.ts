// A trade setup drawn on the chart: a signal from the platforms or the AI's reading of the chart.
// Entry, stop and targets become price lines; the chart clears it when the symbol changes.
import { create } from 'zustand';

export interface Setup { symbol: string; direction: 'long' | 'short'; entry: number | null; sl: number | null; targets: number[]; label: string; time?: number }

interface OverlayState { setup: Setup | null; show(s: Setup): void; clear(): void }

export const useOverlay = create<OverlayState>((set) => ({
  setup: null,
  show: (setup) => set({ setup }),
  clear: () => set({ setup: null }),
}));
