// A short message at the bottom of the chart ("Image copied", "Layout saved"), TradingView's toast.
import { useEffect } from 'react';
import { create } from 'zustand';
import { usePrefs } from '../state/prefs';

interface ToastState { text: string | null; seq: number; show(text: string): void; hide(): void }
const useToast = create<ToastState>((set, get) => ({
  text: null,
  seq: 0,
  show: (text) => set({ text, seq: get().seq + 1 }),
  hide: () => set({ text: null }),
}));

export function toast(text: string): void { useToast.getState().show(text); }

export function Toasts() {
  const { text, seq, hide } = useToast();
  const fa = usePrefs((s) => s.lang) === 'fa';
  useEffect(() => {
    if (!text) return;
    const id = setTimeout(hide, 3200);
    return () => clearTimeout(id);
  }, [text, seq, hide]);
  if (!text) return null;
  return <div className={`pc-toast${fa ? ' fa' : ''}`} role="status" aria-live="polite" dir={fa ? 'rtl' : 'ltr'}>{text}</div>;
}
