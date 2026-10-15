// The full-page screens beside the chart (markets, portfolio, academy…), as the app's routes are.
// One open at a time, over the workspace; the address names it so a link opens it.
import { create } from 'zustand';
import type { PageId } from '../pages/registry';

interface PageState { page: PageId | null; param: string | null; open(page: PageId, param?: string | null): void; close(): void }

export const usePage = create<PageState>((set) => ({
  page: null,
  param: null,
  open: (page, param = null) => set({ page, param }),
  close: () => set({ page: null, param: null }),
}));
