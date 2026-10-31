// TradingView's tooltips on the drawing rail and the top bar (OLD ChartDesktopToolbar.kt:433-472,
// CHART-11): a control names itself after half a second under a mouse, in a small dark label that
// follows the pointer and goes when the pointer leaves or presses. Never on keyboard focus, never
// for a finger. The controls keep their `title` (their name for the rest of the app and its tests);
// it is lifted off only while the mouse is over the control, so the browser's own tooltip does not
// appear beside this one, and put back on leave or press.
import { useEffect, useRef } from 'react';
import { createPortal } from 'react-dom';

/** TradingView's pause before a control names itself. */
export const TOOLTIP_DELAY_MS = 500;
const SCOPE = '.ws-rail, .ws-top';
const OFFSET_X = 12;
const OFFSET_Y = 18;

export function ChromeTips() {
  const tip = useRef<HTMLDivElement>(null);
  useEffect(() => {
    let el: HTMLElement | null = null;
    let label = '';
    let timer: ReturnType<typeof setTimeout> | null = null;
    let shown = false;
    let x = 0; let y = 0;
    const place = () => {
      const t = tip.current; if (!t) return;
      const w = t.offsetWidth; const h = t.offsetHeight;
      t.style.left = `${Math.max(4, Math.min(x + OFFSET_X, window.innerWidth - w - 4))}px`;
      t.style.top = `${y + OFFSET_Y + h > window.innerHeight - 4 ? y - h - 8 : y + OFFSET_Y}px`;
    };
    const hide = () => {
      if (timer) { clearTimeout(timer); timer = null; }
      shown = false;
      if (tip.current) tip.current.hidden = true;
    };
    const restore = () => {
      if (el && label && !el.hasAttribute('title')) el.setAttribute('title', label);
      el = null; label = '';
    };
    const over = (e: PointerEvent) => {
      if (e.pointerType !== 'mouse') return;
      const target = (e.target as Element | null)?.closest?.('[title]') as HTMLElement | null;
      if (target === el) return;
      if (el && el.contains(e.target as Node)) return;
      hide(); restore();
      if (!target || !target.closest(SCOPE)) return;
      const text = target.getAttribute('title') ?? '';
      if (!text) return;
      el = target; label = text;
      target.removeAttribute('title');
      if (!target.hasAttribute('aria-label')) target.setAttribute('aria-label', text);
      x = e.clientX; y = e.clientY;
      timer = setTimeout(() => {
        timer = null;
        // A group's flyout already open beside the rail says more than the button's name.
        if (!el || !tip.current || document.querySelector('.dfly')) return;
        tip.current.textContent = label;
        tip.current.hidden = false;
        shown = true;
        place();
      }, TOOLTIP_DELAY_MS);
    };
    const move = (e: PointerEvent) => { if (e.pointerType !== 'mouse' || !el) return; x = e.clientX; y = e.clientY; if (shown) place(); };
    const out = (e: PointerEvent) => {
      if (!el) return;
      const to = e.relatedTarget as Node | null;
      if (to && el.contains(to)) return;
      hide(); restore();
    };
    const press = () => { hide(); restore(); };
    document.addEventListener('pointerover', over, true);
    document.addEventListener('pointermove', move, true);
    document.addEventListener('pointerout', out, true);
    document.addEventListener('pointerdown', press, true);
    window.addEventListener('keydown', press, true);
    window.addEventListener('blur', press);
    window.addEventListener('scroll', hide, true);
    return () => {
      hide(); restore();
      document.removeEventListener('pointerover', over, true);
      document.removeEventListener('pointermove', move, true);
      document.removeEventListener('pointerout', out, true);
      document.removeEventListener('pointerdown', press, true);
      window.removeEventListener('keydown', press, true);
      window.removeEventListener('blur', press);
      window.removeEventListener('scroll', hide, true);
    };
  }, []);
  return createPortal(<div ref={tip} className="chrome-tip" role="tooltip" hidden dir="auto" />, document.body);
}
