// TradingView's cursors over the chart: Cross (the crosshair), Dot (a small dot with the
// crosshair), Arrow (the plain pointer, no crosshair), Demonstration (a red laser dot that leaves a
// fading trail, for presenting; no crosshair) and Eraser. The look comes from drawing.css through
// `data-cursor` on the chart; the laser trail is painted on a canvas of its own.
import type { CursorMode } from '../state/drawing';

/** The eraser glyph as a CSS cursor (also handed to the drawing layer for the canvas). */
export const ERASER_CURSOR = `url("data:image/svg+xml;utf8,${encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="22" height="22" viewBox="0 0 256 256"><path fill="white" stroke="black" stroke-width="12" d="M225 80 176 31a16 16 0 0 0-22 0L31 154a16 16 0 0 0 0 22l42 42h143v-12h-90l99-99a16 16 0 0 0 0-22Z"/></svg>')}") 4 18, pointer`;

const LIFE = 650; // ms a point of the laser trail stays

export interface CursorOverlay { set(mode: CursorMode | 'draw'): void; dispose(): void }

export function attachCursor(host: HTMLElement): CursorOverlay {
  let mode: CursorMode | 'draw' = 'cursor';
  let canvas: HTMLCanvasElement | null = null;
  let raf = 0;
  const trail: { x: number; y: number; t: number }[] = [];
  const reduced = () => { try { return window.matchMedia('(prefers-reduced-motion: reduce)').matches; } catch { return false; } };

  const paint = () => {
    raf = 0;
    if (!canvas) return;
    const dpr = window.devicePixelRatio || 1;
    const w = host.clientWidth, h = host.clientHeight;
    if (canvas.width !== Math.round(w * dpr) || canvas.height !== Math.round(h * dpr)) {
      canvas.width = Math.round(w * dpr); canvas.height = Math.round(h * dpr);
      canvas.style.width = `${w}px`; canvas.style.height = `${h}px`;
    }
    const ctx = canvas.getContext('2d');
    if (!ctx) return;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, w, h);
    const now = performance.now();
    while (trail.length && now - trail[0]!.t > LIFE) trail.shift();
    if (!trail.length) return;
    ctx.lineCap = 'round'; ctx.lineJoin = 'round';
    for (let i = 1; i < trail.length; i++) {
      const a = trail[i - 1]!, b = trail[i]!;
      const k = 1 - (now - b.t) / LIFE;
      ctx.strokeStyle = `rgba(242,54,69,${(0.55 * k).toFixed(3)})`;
      ctx.lineWidth = 2 + 4 * k;
      ctx.beginPath(); ctx.moveTo(a.x, a.y); ctx.lineTo(b.x, b.y); ctx.stroke();
    }
    const head = trail[trail.length - 1]!;
    ctx.fillStyle = 'rgba(242,54,69,0.25)'; ctx.beginPath(); ctx.arc(head.x, head.y, 10, 0, Math.PI * 2); ctx.fill();
    ctx.fillStyle = '#F23645'; ctx.beginPath(); ctx.arc(head.x, head.y, 5, 0, Math.PI * 2); ctx.fill();
    if (trail.length > 1 || now - head.t < LIFE) raf = requestAnimationFrame(paint);
  };

  const onMove = (e: PointerEvent) => {
    if (mode !== 'demonstration' || !canvas) return;
    const r = host.getBoundingClientRect();
    const p = { x: e.clientX - r.left, y: e.clientY - r.top, t: performance.now() };
    if (reduced()) trail.length = 0; // no trail when motion is reduced: the dot alone
    trail.push(p);
    if (!raf) raf = requestAnimationFrame(paint);
  };
  const onLeave = () => { trail.length = 0; if (!raf) raf = requestAnimationFrame(paint); };
  host.addEventListener('pointermove', onMove);
  host.addEventListener('pointerleave', onLeave);

  return {
    set(next) {
      mode = next;
      host.setAttribute('data-cursor', next);
      if (next === 'demonstration' && !canvas) {
        canvas = document.createElement('canvas');
        canvas.className = 'draw-demo';
        host.appendChild(canvas);
      } else if (next !== 'demonstration' && canvas) {
        if (raf) cancelAnimationFrame(raf);
        raf = 0; trail.length = 0; canvas.remove(); canvas = null;
      }
    },
    dispose() {
      host.removeEventListener('pointermove', onMove);
      host.removeEventListener('pointerleave', onLeave);
      if (raf) cancelAnimationFrame(raf);
      canvas?.remove();
      host.removeAttribute('data-cursor');
    },
  };
}
