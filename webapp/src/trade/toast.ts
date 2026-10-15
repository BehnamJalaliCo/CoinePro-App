// Short notices from trading (an order sent, a position closed, a stop moved): one at a time,
// over the chart, gone after a few seconds.
import { create } from 'zustand';

export interface Notice { id: number; text: string; tone: 'ok' | 'error' | 'info' }

interface NoticeState { notice: Notice | null; show(text: string, tone?: Notice['tone']): void; clear(): void }

let seq = 0;
let timer: ReturnType<typeof setTimeout> | null = null;

export const useNotice = create<NoticeState>((set) => ({
  notice: null,
  show: (text, tone = 'ok') => {
    const id = ++seq;
    set({ notice: { id, text, tone } });
    if (timer) clearTimeout(timer);
    timer = setTimeout(() => set((s) => (s.notice?.id === id ? { notice: null } : s)), tone === 'error' ? 7000 : 4000);
  },
  clear: () => set({ notice: null }),
}));

export const notify = (text: string, tone: Notice['tone'] = 'ok') => useNotice.getState().show(text, tone);
