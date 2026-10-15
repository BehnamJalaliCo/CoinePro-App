// A price that moves flashes, as on TradingView and the old Pro-Chart: the cell's background lights
// green on an uptick and red on a downtick, then fades over half a second. The Web Animations API
// restarts the fade on every tick without remounting the cell; `prefers-reduced-motion` turns the
// fade off (the tick direction still colours the figure). `data-flash` records the last direction,
// which the end-to-end suite reads.
import { useEffect, useLayoutEffect, useRef, useState } from 'react';

export type Tick = 1 | -1 | 0;

function reducedMotion(): boolean {
  try { return window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false; } catch { return false; }
}

const UP = 'rgba(8, 153, 129, 0.28)';
const DOWN = 'rgba(242, 54, 69, 0.28)';

/** Lights an element's background for one tick (or a neutral gold for a new item, `dir` 0). */
export function flashElement(el: Element | null, dir: Tick, ms = 600): void {
  if (!el) return;
  (el as HTMLElement).dataset.flash = dir > 0 ? 'up' : dir < 0 ? 'down' : 'new';
  if (reducedMotion() || typeof (el as HTMLElement).animate !== 'function') return;
  const from = dir > 0 ? UP : dir < 0 ? DOWN : 'rgba(212, 166, 74, 0.32)';
  try { (el as HTMLElement).animate([{ backgroundColor: from }, { backgroundColor: 'transparent' }], { duration: ms, easing: 'ease-out' }); } catch { /* animations unavailable */ }
}

/**
 * A ref for the element to flash when `value` moves, and the direction of the last move (which
 * stays until the next one, so a figure can keep the tick's colour as TradingView's Last does).
 */
export function useFlash<T extends HTMLElement = HTMLElement>(value: number | null | undefined): { ref: React.RefObject<T>; tick: Tick } {
  const ref = useRef<T>(null);
  const prev = useRef<number | null | undefined>(value);
  const [tick, setTick] = useState<Tick>(0);
  useLayoutEffect(() => {
    const p = prev.current;
    prev.current = value;
    if (value == null || p == null || value === p || !Number.isFinite(value)) return;
    const dir: Tick = value > p ? 1 : -1;
    setTick(dir);
    flashElement(ref.current, dir);
  }, [value]);
  return { ref, tick };
}

/** Re-renders every `ms` so "5 minutes ago" and countdowns stay true. */
export function useNow(ms = 30_000): number {
  const [now, setNow] = useState(Date.now());
  useEffect(() => { const id = setInterval(() => setNow(Date.now()), ms); return () => clearInterval(id); }, [ms]);
  return now;
}
