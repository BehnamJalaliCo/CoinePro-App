// Multi-chart layouts, TradingView's "Select layout": one chart, two side by side or stacked, three,
// or four. The first cell is the main chart; the others keep their own symbol and interval.
import { create } from 'zustand';

export type GridId = '1' | '2v' | '2h' | '3' | '4';
export interface Cell { symbol: string; interval: string }

const KEY = 'pc.grid.v1';
interface Stored { grid: GridId; cells: Cell[] }
const DEFAULT_CELLS: Cell[] = [{ symbol: 'ETHUSDT', interval: '60' }, { symbol: 'XAUUSD', interval: '60' }, { symbol: 'EURUSD', interval: '60' }];
const read = (): Stored => {
  try { const v = JSON.parse(localStorage.getItem(KEY) ?? 'null') as Stored | null; if (v && v.grid) return { grid: v.grid, cells: v.cells?.length === 3 ? v.cells : DEFAULT_CELLS }; } catch { /* fall through */ }
  return { grid: '1', cells: DEFAULT_CELLS };
};

interface GridState extends Stored { setGrid(g: GridId): void; setCell(i: number, c: Partial<Cell>): void }

export const useGrid = create<GridState>((set, get) => {
  const save = () => { const { grid, cells } = get(); try { localStorage.setItem(KEY, JSON.stringify({ grid, cells })); } catch { /* ignore */ } };
  return {
    ...read(),
    setGrid: (grid) => { set({ grid }); save(); },
    setCell: (i, c) => { set({ cells: get().cells.map((x, k) => (k === i ? { ...x, ...c } : x)) }); save(); },
  };
});

export const cellCount = (g: GridId) => (g === '1' ? 1 : g === '3' ? 3 : g === '4' ? 4 : 2);
