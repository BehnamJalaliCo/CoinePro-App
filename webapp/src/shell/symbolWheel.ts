// The phone's symbol wheel (OLD feature/chart SymbolWheel.kt): a vertical drag on the symbol's name
// in the top bar turns to the previous or next symbol of the reader's list. A ring rather than a list
// with ends — a control dead on the first and last entry cannot be told from a broken one — and with
// artwork as the filter, here as everywhere, so no symbol without a logo is ever turned to.
import { useRef } from 'react';
import { hasLogo } from '../ui/SymbolLogo';

export interface SymbolNeighbours { previous: string | null; next: string | null; position: number; total: number }

const ringOf = (symbols: string[], lists: (s: string) => boolean) => {
  const seen = new Set<string>();
  return symbols.filter((s) => { const k = s.toUpperCase(); if (!lists(s) || seen.has(k)) return false; seen.add(k); return true; });
};

/** The neighbours on the ring; two symbols are one neighbour, drawn ahead. */
export function symbolNeighbours(symbols: string[], current: string, lists: (s: string) => boolean = hasLogo): SymbolNeighbours {
  const ring = ringOf(symbols, lists);
  if (!ring.length) return { previous: null, next: null, position: 0, total: 0 };
  const i = ring.findIndex((s) => s.toUpperCase() === current.toUpperCase());
  if (i < 0) return { previous: ring.length > 1 ? ring[ring.length - 1]! : null, next: ring[0]!, position: 0, total: ring.length };
  if (ring.length === 1) return { previous: null, next: null, position: 1, total: 1 };
  return { previous: ring.length > 2 ? ring[(i - 1 + ring.length) % ring.length]! : null, next: ring[(i + 1) % ring.length]!, position: i + 1, total: ring.length };
}

/** The symbol `steps` away on the ring, or null where there is nowhere to turn. */
export function symbolStep(symbols: string[], current: string, steps: number, lists: (s: string) => boolean = hasLogo): string | null {
  const ring = ringOf(symbols, lists);
  if (!ring.length || steps === 0) return null;
  const i = ring.findIndex((s) => s.toUpperCase() === current.toUpperCase());
  if (i < 0) return steps > 0 ? ring[0]! : ring[ring.length - 1]!;
  if (ring.length === 1) return null;
  const moved = (((i + steps) % ring.length) + ring.length) % ring.length;
  return moved !== i ? ring[moved]! : null;
}

/** How far a finger travels before the wheel turns one step (the phone's 18 dp row, at CSS pixels). */
export const WHEEL_STEP_PX = 24;

/**
 * Pointer handlers for the symbol's name: on a touch screen of phone width, an upward drag turns to
 * the next symbol and a downward one to the previous, one step per row of travel; a tap still opens
 * the symbol search (the click after a turn is swallowed).
 */
export function useSymbolWheel(symbols: () => string[], current: string, onSelect: (s: string) => void) {
  const drag = useRef<{ id: number; y: number; at: string; turned: boolean } | null>(null);
  const swallow = useRef(false);
  const phone = () => { try { return window.matchMedia('(max-width: 760px)').matches; } catch { return false; } };
  return {
    onPointerDown: (e: React.PointerEvent) => {
      if (e.pointerType === 'mouse' || !phone()) return;
      drag.current = { id: e.pointerId, y: e.clientY, at: current, turned: false };
    },
    onPointerMove: (e: React.PointerEvent) => {
      const d = drag.current;
      if (!d || d.id !== e.pointerId) return;
      const dy = e.clientY - d.y;
      if (Math.abs(dy) < WHEEL_STEP_PX) return;
      const landed = symbolStep(symbols(), d.at, dy < 0 ? 1 : -1);
      d.y = e.clientY;
      if (!landed) return;
      d.at = landed;
      d.turned = true;
      onSelect(landed);
    },
    onPointerUp: () => { if (drag.current?.turned) swallow.current = true; drag.current = null; },
    onPointerCancel: () => { drag.current = null; },
    /** True once, for the click that ends a turn. */
    swallowClick: () => { const s = swallow.current; swallow.current = false; return s; },
  };
}
