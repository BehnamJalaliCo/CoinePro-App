// The layer that draws the coach over everything else (OLD CoineProCoach.kt CoineProCoachLayer,
// CoachSpotlight, CoachCard). Two looks:
//
// * Full, on a touch screen and for a tip played from the library: the app dimmed to a deep indigo,
//   the light closing in on the control like an iris on a slow spring, its edge feathered rather than
//   cut, a pastel ring turning slowly round it, and the card — the film, a title, one sentence —
//   rising under the light. A tap anywhere is «فهمیدم».
// * Quiet, with a mouse (5.33.0): a dark glass and a card in the middle of the chart read as a modal
//   the reader had to answer before they could trade. So there a tip is a small card in the
//   bottom-left corner without the film, the ring round its control and nothing dimmed, and every
//   click outside the card reaches the page as if it were not there.
//
// Never over an open dialog, page or menu; put away when its control goes; by itself after a few
// loops. Without motion (prefers-reduced-motion) nothing moves: the light is simply there and each
// film is its middle frame.
import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { usePrefs } from '../state/prefs';
import { useL } from '../pages/kit';
import { useCoach, coachAllowed, coachDone } from './store';
import { AUTO_CLOSE_LINE_MS, AUTO_CLOSE_SCENE_MS, LOST_TARGET_MS, MOUSE_SESSION_CAP, SETTLE_DELAY_MS, URGENT_DELAY_MS, cardTop, pickTip, tipById, tipText, tipTitle, type Box, type CoachTip } from './tips';
import { anchorOf, blockedNow, collectRequests, reducedMotion, touchFirst } from './anchors';
import { drawFilm, SCENE_MS, STILL_FRAME, type SceneId } from './films';
import { useFirstRun } from '../firstrun/store';
import './coach.css';

const SCRIM = [13, 15, 34];
const SCRIM_ALPHA = 0.8;
const SCRIM_FADE_MS = 420;
const CARD_DELAY_MS = 260;
const RING_PERIOD_MS = 3600;
const FEATHER_STEPS = 16;
const FEATHER_ALPHA = 0.085;
const FEATHER_STEP = 0.6;
const HOLE_PAD = 8;
const HOLE_RADIUS = 18;
const RING_HALO = 5;
const HALO_STEPS = 8;
const RING_HALO_ALPHA = 0.5;
const RING_LINE = 1.5;
const CARD_MAX_WIDTH = 380;
const QUIET_CARD_WIDTH = 320;
const QUIET_CARD_INSET = 64;
const CARD_GAP = 16;
const CARD_RISE = 36;
/** Lavender, sky, mint, peach and back: the ring and the play discs. */
const RING_COLOURS = [[0xB9, 0xB3, 0xFF], [0xA9, 0xDC, 0xFF], [0xA8, 0xED, 0xD0], [0xFF, 0xD3, 0xBE], [0xB9, 0xB3, 0xFF]];
const RING_SAMPLES = 12;

/** The ring's palette turned by [phase] of a revolution, sampled into thirteen stops. */
function ringStops(phase: number): string[] {
  const span = RING_COLOURS.length - 1;
  return Array.from({ length: RING_SAMPLES + 1 }, (_, i) => {
    const at = ((((i / RING_SAMPLES - phase) % 1) + 1) % 1) * span;
    const from = Math.min(Math.floor(at), span - 1);
    const f = at - from; const a = RING_COLOURS[from]!; const b = RING_COLOURS[from + 1]!;
    return `rgb(${Math.round(a[0]! + (b[0]! - a[0]!) * f)},${Math.round(a[1]! + (b[1]! - a[1]!) * f)},${Math.round(a[2]! + (b[2]! - a[2]!) * f)})`;
  });
}

/** A critically-ish damped spring towards [target], as Compose's spring(dampingRatio, stiffness). */
function springStep(s: { x: number; v: number }, target: number, damping: number, stiffness: number, dt: number): void {
  const c = 2 * damping * Math.sqrt(stiffness);
  for (let left = dt; left > 0; left -= 1 / 240) {
    const h = Math.min(1 / 240, left);
    const a = -stiffness * (s.x - target) - c * s.v;
    s.v += a * h; s.x += s.v * h;
  }
}

function roundRectPath(ctx: CanvasRenderingContext2D, b: { left: number; top: number; width: number; height: number }, r: number): void {
  const k = Math.max(0, Math.min(r, b.width / 2, b.height / 2));
  const { left: x, top: y, width: w, height: h } = b;
  ctx.beginPath();
  ctx.moveTo(x + k, y); ctx.arcTo(x + w, y, x + w, y + h, k); ctx.arcTo(x + w, y + h, x, y + h, k); ctx.arcTo(x, y + h, x, y, k); ctx.arcTo(x, y, x + w, y, k);
  ctx.closePath();
}
const inflate = (b: Box, d: number): Box => ({ left: b.left - d, top: b.top - d, width: b.width + 2 * d, height: b.height + 2 * d });

/** The film on its stage: a loop, or its middle frame without motion. */
export function CoachFilm({ scene, touch, className }: { scene: SceneId; touch: boolean; className?: string }) {
  const ref = useRef<HTMLCanvasElement>(null);
  useEffect(() => {
    const canvas = ref.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;
    const motion = !reducedMotion();
    let raf = 0;
    const t0 = performance.now();
    const frame = () => {
      const w = canvas.clientWidth; const h = canvas.clientHeight;
      const dpr = window.devicePixelRatio || 1;
      if (w > 0 && h > 0) {
        if (canvas.width !== Math.round(w * dpr) || canvas.height !== Math.round(h * dpr)) { canvas.width = Math.round(w * dpr); canvas.height = Math.round(h * dpr); }
        ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
        const t = motion ? ((performance.now() - t0) % SCENE_MS) / SCENE_MS : STILL_FRAME;
        drawFilm(ctx, w, h, scene, t, touch, motion);
        canvas.dataset.painted = '1';
      }
      if (motion) raf = requestAnimationFrame(frame);
    };
    frame();
    // Without motion one frame is the film; it is drawn again only if the stage changes size.
    const ro = motion ? null : new ResizeObserver(() => frame());
    ro?.observe(canvas);
    return () => { cancelAnimationFrame(raf); ro?.disconnect(); };
  }, [scene, touch]);
  return <canvas ref={ref} className={`coach-film${className ? ` ${className}` : ''}`} data-scene={scene} aria-hidden />;
}

/** The card: the film, a title, one sentence, «فهمیدم» and the way out. Pastel in both themes. */
function CoachCard({ tip, touch, compact, onDone, onNever, style, cardRef }: {
  tip: CoachTip; touch: boolean; compact: boolean; onDone(): void; onNever: (() => void) | null;
  style: React.CSSProperties; cardRef: React.Ref<HTMLDivElement>;
}) {
  const L = useL();
  const fa = usePrefs((s) => s.lang) === 'fa';
  const title = tipTitle(tip, touch); const text = tipText(tip, touch);
  return (
    <div ref={cardRef} className={`coach-card${compact ? ' quiet' : ''}`} style={style} dir={fa ? 'rtl' : 'ltr'} data-tip={tip.id}
      role="status" aria-live="polite" onPointerDown={(e) => e.stopPropagation()} onClick={(e) => e.stopPropagation()}>
      {tip.scene && !compact ? <CoachFilm scene={tip.scene} touch={touch || !tip.mouseText} className="coach-stage" /> : null}
      <div className="coach-head"><i className="coach-dot" aria-hidden /><b className={fa ? 'fa' : ''}>{fa ? title.fa : title.en}</b></div>
      <p className={`coach-text${fa ? ' fa' : ''}`}>{fa ? text.fa : text.en}</p>
      <div className="coach-acts">
        {onNever ? <button type="button" className="coach-never" onClick={onNever}>{L('بلدم، دیگر نشان داده نشود', 'I know these, stop showing them')}</button> : <span />}
        <button type="button" className="coach-ok" onClick={onDone}>{L('فهمیدم', 'Got it')}</button>
      </div>
    </div>
  );
}

/** The dimmed app, the lit control and the card: the cinematic part. */
function Spotlight({ tip, preview }: { tip: CoachTip; preview: boolean }) {
  const close = useCoach((s) => s.close);
  const never = useCoach((s) => s.never);
  const [touch] = useState(touchFirst);
  const [motion] = useState(() => !reducedMotion());
  const [target, setTarget] = useState<Box | null>(() => (preview ? null : anchorOf(tip.id, touch)));
  const [size, setSize] = useState(() => ({ w: window.innerWidth, h: window.innerHeight }));
  const [measured, setMeasured] = useState(0);
  const quiet = !touch && !preview;
  const glass = useRef<HTMLCanvasElement>(null);
  const card = useRef<HTMLDivElement>(null);
  const targetRef = useRef(target);
  targetRef.current = target;

  useLayoutEffect(() => { if (card.current) setMeasured(card.current.offsetHeight); }, [tip.id, size.w]);
  useEffect(() => {
    const onResize = () => setSize({ w: window.innerWidth, h: window.innerHeight });
    window.addEventListener('resize', onResize);
    // Escape puts the tip away and nothing under it (a page played from stays open).
    const esc = (e: KeyboardEvent) => { if (e.key === 'Escape') { e.stopPropagation(); close(); } };
    window.addEventListener('keydown', esc, true);
    // Put away by itself after a few loops of the film: long enough to watch it twice.
    const auto = setTimeout(close, tip.scene && !quiet ? AUTO_CLOSE_SCENE_MS : AUTO_CLOSE_LINE_MS);
    return () => { window.removeEventListener('resize', onResize); window.removeEventListener('keydown', esc, true); clearTimeout(auto); };
  }, [close, tip.scene, quiet]);

  // The control is followed while the tip is up; gone for a moment (the page changed, a dialog
  // opened) the tip is put away, since a spotlight on nothing is worse than none.
  useEffect(() => {
    if (preview) return;
    let lostSince = 0;
    const id = setInterval(() => {
      if (blockedNow()) { close(); return; }
      const b = anchorOf(tip.id, touch);
      if (!b) { if (!lostSince) lostSince = Date.now(); else if (Date.now() - lostSince >= LOST_TARGET_MS) close(); return; }
      lostSince = 0;
      const t = targetRef.current;
      if (!t || Math.abs(t.left - b.left) + Math.abs(t.top - b.top) + Math.abs(t.width - b.width) + Math.abs(t.height - b.height) > 1) setTarget(b);
    }, 150);
    return () => clearInterval(id);
  }, [preview, tip.id, touch, close]);

  // The glass and the card's rise, one frame at a time.
  useEffect(() => {
    const canvas = glass.current;
    const ctx = canvas?.getContext('2d');
    if (!canvas || !ctx) return;
    const t0 = performance.now();
    const iris = { x: motion ? 0 : 1, v: 0 };
    const rise = { x: motion ? 1 : 0, v: 0 };
    let last = t0; let raf = 0;
    const frame = (now: number) => {
      const dt = Math.min(0.05, (now - last) / 1000); last = now;
      const el = now - t0;
      if (motion) {
        springStep(iris, 1, 0.86, 60, dt);
        if (el > CARD_DELAY_MS) springStep(rise, 0, 0.72, 200, dt);
      }
      const fade = motion ? 1 - (1 - Math.min(1, el / SCRIM_FADE_MS)) ** 3 : 1;
      const breathe = motion ? (el % RING_PERIOD_MS) / RING_PERIOD_MS : 0;
      const W = window.innerWidth; const H = window.innerHeight; const dpr = window.devicePixelRatio || 1;
      if (canvas.width !== Math.round(W * dpr) || canvas.height !== Math.round(H * dpr)) { canvas.width = Math.round(W * dpr); canvas.height = Math.round(H * dpr); }
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      ctx.clearRect(0, 0, W, H);
      const t = targetRef.current;
      const far = (1 - iris.x) * Math.max(W, H);
      if (!quiet) {
        ctx.globalCompositeOperation = 'source-over';
        ctx.fillStyle = `rgba(${SCRIM.join(',')},${SCRIM_ALPHA * fade})`;
        ctx.fillRect(0, 0, W, H);
        if (t) {
          const hole = inflate(t, HOLE_PAD + far); const corner = HOLE_RADIUS + far * 0.5;
          ctx.globalCompositeOperation = 'destination-out';
          // The edge of the light feathered rather than cut: sixteen faint steps outside the hole.
          for (let ring = FEATHER_STEPS; ring >= 1; ring--) {
            ctx.fillStyle = `rgba(0,0,0,${FEATHER_ALPHA})`;
            roundRectPath(ctx, inflate(hole, ring * FEATHER_STEP), corner + ring * FEATHER_STEP); ctx.fill();
          }
          ctx.fillStyle = '#000'; roundRectPath(ctx, hole, corner); ctx.fill();
          ctx.globalCompositeOperation = 'source-over';
        }
      }
      if (t) {
        // The ring: a pastel band travelling round the light, brightest as the iris lands.
        const hole = inflate(t, HOLE_PAD + far); const corner = HOLE_RADIUS + far * 0.5;
        const glow = (0.7 + 0.3 * Math.sin(breathe * 2 * Math.PI)) * fade;
        const cx = hole.left + hole.width / 2; const cy = hole.top + hole.height / 2;
        const stops = ringStops(breathe);
        let brush: CanvasGradient;
        if (typeof ctx.createConicGradient === 'function') brush = ctx.createConicGradient(0, cx, cy);
        else brush = ctx.createLinearGradient(hole.left, hole.top, hole.left + hole.width, hole.top + hole.height);
        stops.forEach((c, i) => brush.addColorStop(i / RING_SAMPLES, c));
        ctx.strokeStyle = brush;
        const haloStep = RING_HALO / HALO_STEPS;
        for (let ring = HALO_STEPS; ring >= 1; ring--) {
          ctx.globalAlpha = (glow * RING_HALO_ALPHA) / HALO_STEPS; ctx.lineWidth = haloStep * ring * 2;
          roundRectPath(ctx, hole, corner); ctx.stroke();
        }
        ctx.globalAlpha = glow; ctx.lineWidth = RING_LINE; roundRectPath(ctx, hole, corner); ctx.stroke();
        ctx.globalAlpha = 1;
      }
      const c = card.current;
      if (c) {
        c.style.opacity = String(Math.min(1, Math.max(0, 1 - rise.x)));
        c.style.transform = `translateY(${rise.x * CARD_RISE}px) scale(${1 - rise.x * 0.06})`;
      }
      canvas.dataset.iris = iris.x.toFixed(2);
      raf = requestAnimationFrame(frame);
    };
    raf = requestAnimationFrame(frame);
    return () => cancelAnimationFrame(raf);
  }, [motion, quiet]);

  const W = size.w; const H = size.h;
  const width = Math.min(W - CARD_GAP * 2, quiet ? QUIET_CARD_WIDTH : CARD_MAX_WIDTH);
  const estimate = measured > 0 ? measured : tip.scene && !quiet ? 340 : 160;
  const top = quiet ? Math.max(CARD_GAP, H - estimate - CARD_GAP) : cardTop(target ? inflate(target, HOLE_PAD) : null, estimate, H, CARD_GAP);
  const left = quiet ? Math.min(QUIET_CARD_INSET, Math.max(CARD_GAP, W - width - CARD_GAP)) : (W - width) / 2;
  const style: React.CSSProperties = { left, top, width, ...(motion ? { opacity: 0 } : {}) };
  return createPortal(
    <div className={`coach-layer${quiet ? ' quiet' : ''}${preview ? ' preview' : ''}`} data-coach-tip={tip.id} onClick={quiet ? undefined : close}>
      <canvas ref={glass} className={`coach-glass${quiet ? '' : ' coach-scrim'}`} aria-hidden />
      <CoachCard tip={tip} touch={touch} compact={quiet} cardRef={card} style={style} onDone={close} onNever={preview ? null : never} />
    </div>,
    document.body,
  );
}

/** Watches for the next tip's moment, and draws the one on screen. Mounted once, by the app. */
export function CoachLayer() {
  const showing = useCoach((s) => s.showing);
  const preview = useCoach((s) => s.preview);
  useEffect(() => {
    if (!coachAllowed) return;
    let candidate: { id: string; since: number } | null = null;
    const id = setInterval(() => {
      const s = useCoach.getState();
      if (s.showing || s.preview || coachDone(s)) { candidate = null; return; }
      const touch = touchFirst();
      // With a mouse nothing is urgent: past the visit's one tip, or inside the quiet after it, there
      // is nothing to look for.
      if (!touch && (s.shown >= MOUSE_SESSION_CAP || Date.now() < s.coolingUntil)) { candidate = null; return; }
      const blocked = useFirstRun.getState().active || blockedNow();
      const tip = pickTip({
        requests: blocked ? new Map() : collectRequests(touch), dismissed: new Set(s.dismissed), off: s.off, touch, shown: s.shown,
        cooling: Date.now() < s.coolingUntil, windowHeight: window.innerHeight, windowWidth: window.innerWidth, blocked,
      });
      if (!tip) { candidate = null; return; }
      const now = Date.now();
      // Settled first: a control still sliding in, or a page the reader is only passing through,
      // does not get a tip.
      if (candidate?.id !== tip.id) candidate = { id: tip.id, since: now };
      else if (now - candidate.since >= (tip.urgent ? URGENT_DELAY_MS : SETTLE_DELAY_MS)) { candidate = null; s.show(tip.id); }
    }, 200);
    return () => clearInterval(id);
  }, []);
  const tip = tipById(preview ?? showing);
  if (!tip) return null;
  return <Spotlight key={`${preview ? 'p' : 's'}:${tip.id}`} tip={tip} preview={Boolean(preview)} />;
}
