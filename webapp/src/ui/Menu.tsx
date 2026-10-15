import { useEffect, useLayoutEffect, useRef, useState, type ReactNode } from 'react';
import { createPortal } from 'react-dom';

// A drop-down anchored under a button, rendered in a portal so no toolbar's overflow clips it.
// Closes on outside click, Escape, or a pick.
export function Dropdown({ anchor, onClose, children, align = 'left', className = '' }: { anchor: HTMLElement | null; onClose(): void; children: ReactNode; align?: 'left' | 'right'; className?: string }) {
  const ref = useRef<HTMLDivElement>(null);
  const [pos, setPos] = useState<{ left?: number; right?: number; top?: number; bottom?: number }>({ top: 0 });

  // Under the anchor, or above it when the anchor sits in the lower part of the window.
  useLayoutEffect(() => {
    if (!anchor) return;
    const r = anchor.getBoundingClientRect();
    const v = r.bottom > window.innerHeight * 0.6 ? { bottom: window.innerHeight - r.top + 4 } : { top: r.bottom + 4 };
    setPos(align === 'left' ? { left: r.left, ...v } : { right: window.innerWidth - r.right, ...v });
  }, [anchor, align]);

  useEffect(() => {
    const close = (e: MouseEvent) => {
      if (ref.current?.contains(e.target as Node) || anchor?.contains(e.target as Node)) return;
      onClose();
    };
    const esc = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    document.addEventListener('mousedown', close);
    document.addEventListener('keydown', esc);
    return () => { document.removeEventListener('mousedown', close); document.removeEventListener('keydown', esc); };
  }, [anchor, onClose]);

  return createPortal(
    <div ref={ref} className={`menu dropdown ${className}`} role="menu" style={{ position: 'fixed', ...pos }}>{children}</div>,
    document.body,
  );
}
