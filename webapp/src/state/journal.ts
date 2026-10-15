// The trade journal (feature/journal, core/journal JournalStore): each trade with its side, prices,
// size, result, the feeling behind it, the lesson and tags, and optionally a picture of the chart.
// Kept in this browser; exported as CSV or JSON, imported back from JSON.
import { create } from 'zustand';
import { uuid } from '../data/http';

export const EMOTIONS = ['آرام', 'ترس', 'طمع', 'انتقام', 'بی‌صبری', 'مطمئن'] as const;
export const EMOTIONS_EN: Record<string, string> = { 'آرام': 'Calm', 'ترس': 'Fear', 'طمع': 'Greed', 'انتقام': 'Revenge', 'بی‌صبری': 'Impatience', 'مطمئن': 'Confident' };
export const TAGS = ['بریک‌اوت', 'ریتست', 'پولبک', 'رنج', 'ترند', 'حمایت', 'مقاومت', 'دایورجنس', 'نیوز', 'اسکالپ', 'سوینگ', 'اوردرفلو'];

export interface JournalEntry {
  id: string; symbol: string; buy: boolean; entry: number; exit: number | null; size: number;
  pnl: number | null; emotion: string; note: string; lesson: string; tags: string[]; createdAt: number; screenshot?: string;
}

const KEY = 'pc.journal.v1';
const read = (): JournalEntry[] => { try { const v = JSON.parse(localStorage.getItem(KEY) ?? '[]') as unknown; return Array.isArray(v) ? (v as JournalEntry[]) : []; } catch { return []; } };

/** The result a closed trade carries when the reader did not type one. */
export const pnlFor = (e: Pick<JournalEntry, 'buy' | 'entry' | 'exit' | 'size'>) => (e.exit == null ? null : (e.buy ? e.exit - e.entry : e.entry - e.exit) * e.size);

interface JournalState {
  entries: JournalEntry[];
  error: string | null;
  upsert(e: Omit<JournalEntry, 'id' | 'createdAt'> & { id?: string; createdAt?: number }): void;
  remove(id: string): void;
  importAll(list: unknown): number;
}

export const useJournal = create<JournalState>((set, get) => {
  const save = () => {
    try { localStorage.setItem(KEY, JSON.stringify(get().entries)); set({ error: null }); }
    catch { set({ error: 'storage' }); }
  };
  return {
    entries: read(),
    error: null,
    upsert: (e) => {
      const id = e.id ?? uuid();
      const entry: JournalEntry = { ...e, id, createdAt: e.createdAt ?? Date.now(), pnl: e.pnl ?? pnlFor(e) };
      const rest = get().entries.filter((x) => x.id !== id);
      set({ entries: [entry, ...rest].sort((a, b) => b.createdAt - a.createdAt) });
      save();
    },
    remove: (id) => { set({ entries: get().entries.filter((x) => x.id !== id) }); save(); },
    importAll: (list) => {
      if (!Array.isArray(list)) return 0;
      const ok = list.filter((x): x is JournalEntry => Boolean(x) && typeof (x as JournalEntry).symbol === 'string' && typeof (x as JournalEntry).entry === 'number')
        .map((x) => ({ ...x, id: x.id || uuid(), tags: Array.isArray(x.tags) ? x.tags : [], createdAt: x.createdAt || Date.now() }));
      const have = new Set(get().entries.map((x) => x.id));
      set({ entries: [...ok.filter((x) => !have.has(x.id)), ...get().entries].sort((a, b) => b.createdAt - a.createdAt) });
      save();
      return ok.length;
    },
  };
});
