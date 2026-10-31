// The coach's films (OLD core/designsystem CoachScenes.kt, 5.21.0): small looping pictures, each one
// gesture, drawn on a canvas rather than played from a file. «پاستیلی و سینمایی»: a dawn-coloured
// stage, mint and rose candles, a lavender finger; a camera that pushes in over the loop, a band of
// light crossing the stage as it opens, two soft out-of-focus glows behind and a vignette. The loop
// dissolves through the stage colour at both ends. Without motion a film is its middle frame, which
// still shows the gesture. No blur and no shadows of colour: the depth is gradients inside the
// drawing (the same allow-listed case as the Kotlin films).

export type SceneId = 'trackpad' | 'toolbar' | 'pinch' | 'axis' | 'hold' | 'handle' | 'plus' | 'alertLine' | 'reorder';

/** One loop of a film. Long enough to see the gesture land and its result settle. */
export const SCENE_MS = 3400;
/** The frame a reduced-motion reader is shown: the gesture under way, its result visible. */
export const STILL_FRAME = 0.5;
const LOOP_FADE = 0.06;
const PUSH_IN = 0.035;
/** The stage's height in design units. */
const STAGE_UNITS = 180;

const STAGE_TOP = '#EFEBFF';
const STAGE_BOTTOM = '#FFF1EA';
const STAGE_MID = '#F7EEF5';
const BOKEH_LAVENDER = 'rgba(201,193,255,0.4)';
const BOKEH_MINT = 'rgba(180,240,214,0.333)';
const VIGNETTE = 'rgba(42,33,96,0.12)';
const GRID = 'rgba(108,99,255,0.12)';
const UP = '#8FDDBE';
const UP_EDGE = '#4CBF95';
const DOWN = '#F7AFC0';
const DOWN_EDGE = '#E47C96';
const INK = '#2B2E55';
const INK_SOFT = '#6A6F95';
const INK_FAINT = '#D9D7EE';
const ACCENT = '#8A8FF8';
const ACCENT_SOFT = '#E6E5FF';
const CURSOR_BLUE = '#5B7CFA';
const LINE_BLUE = '#7A86F7';
const HALO = '#B9B3FF';
const FINGER_SHADE = '#E9E5FF';
const FINGER_RING = '#A6A9F6';
const GRIP = '#B8BAD0';
const BELL = '#F6A98B';
const WHITE = '#FFFFFF';

interface P { x: number; y: number }
interface R { left: number; top: number; right: number; bottom: number }
interface Bar { open: number; high: number; low: number; close: number }

export const seg = (t: number, from: number, to: number) => Math.min(1, Math.max(0, (t - from) / (to - from)));
const lerp = (a: number, b: number, f: number) => a + (b - a) * f;
const lerpP = (a: P, b: P, f: number): P => ({ x: lerp(a.x, b.x, f), y: lerp(a.y, b.y, f) });
const add = (a: P, b: P): P => ({ x: a.x + b.x, y: a.y + b.y });
const sub = (a: P, b: P): P => ({ x: a.x - b.x, y: a.y - b.y });
const mul = (a: P, k: number): P => ({ x: a.x * k, y: a.y * k });
export const easeOut = (x: number) => 1 - (1 - x) ** 3;
export const easeInOut = (x: number) => (x < 0.5 ? 4 * x * x * x : 1 - (-2 * x + 2) ** 3 / 2);
export function easeOutBack(x: number): number {
  if (x <= 0) return 0;
  const c1 = 1.70158; const c3 = c1 + 1;
  return 1 + c3 * (x - 1) ** 3 + c1 * (x - 1) ** 2;
}

function bars(moves: number[]): Bar[] {
  let close = 100;
  return moves.map((move, i) => {
    const open = close; close = open + move;
    const wick = 0.6 + (i % 3) * 0.35;
    return { open, high: Math.max(open, close) + wick, low: Math.min(open, close) - wick, close };
  });
}
const BARS = bars([1.2, -0.8, 1.6, 0.9, -1.4, -0.6, 1.1, 2.2, -0.9, 1.4, 0.7, -1.8, -0.7, 1.3, 1.9, -0.5, 1.2, 2.4, -1.1, 0.8, 1.5, -0.6, 1.7, 1.1]);

/** «85,240» — a market figure, so Latin, with the thousands grouped. */
export function filmPrice(value: number): string {
  const s = String(Math.abs(Math.trunc(value))).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  return value < 0 ? `-${s}` : s;
}

/** A rounded rectangle's path (canvas roundRect is not everywhere yet). */
function rr(ctx: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, r: number): void {
  const k = Math.max(0, Math.min(r, Math.abs(w) / 2, Math.abs(h) / 2));
  ctx.beginPath();
  ctx.moveTo(x + k, y);
  ctx.arcTo(x + w, y, x + w, y + h, k);
  ctx.arcTo(x + w, y + h, x, y + h, k);
  ctx.arcTo(x, y + h, x, y, k);
  ctx.arcTo(x, y, x + w, y, k);
  ctx.closePath();
}

/** Draws one frame of [scene] at [t] (0–1 of its loop) on a [width]×[height] canvas. */
export function drawFilm(ctx: CanvasRenderingContext2D, width: number, height: number, scene: SceneId, t: number, touch: boolean, motion: boolean): void {
  const u = height / STAGE_UNITS;
  const w = width / u;
  const p = (x: number, y: number): P => ({ x: x * u, y: y * u });
  const r = (l: number, t2: number, rt: number, b: number): R => ({ left: l * u, top: t2 * u, right: rt * u, bottom: b * u });
  const plot = r(10, 12, w - 44, 168);
  const gutter = r(w - 42, 12, w - 6, 168);

  const alpha = (a: number, fn: () => void) => { if (a <= 0) return; ctx.save(); ctx.globalAlpha *= Math.min(1, a); fn(); ctx.restore(); };
  const line = (a: P, b: P, color: string, width2: number, al = 1, dash?: number[], cap: CanvasLineCap = 'butt') => alpha(al, () => {
    ctx.strokeStyle = color; ctx.lineWidth = width2; ctx.lineCap = cap; ctx.setLineDash(dash ?? []);
    ctx.beginPath(); ctx.moveTo(a.x, a.y); ctx.lineTo(b.x, b.y); ctx.stroke();
  });
  const disc = (c: P, radius: number, fill: string | CanvasGradient, al = 1) => alpha(al, () => {
    ctx.fillStyle = fill; ctx.beginPath(); ctx.arc(c.x, c.y, Math.max(0, radius), 0, Math.PI * 2); ctx.fill();
  });
  const ringAt = (c: P, radius: number, color: string, width2: number, al = 1) => alpha(al, () => {
    ctx.strokeStyle = color; ctx.lineWidth = width2; ctx.setLineDash([]); ctx.beginPath(); ctx.arc(c.x, c.y, Math.max(0, radius), 0, Math.PI * 2); ctx.stroke();
  });
  const box = (x: number, y: number, bw: number, bh: number, radius: number, fill: string, al = 1) => alpha(al, () => { ctx.fillStyle = fill; rr(ctx, x, y, bw, bh, radius); ctx.fill(); });
  const text = (value: string, at: P, colour: string, sizeUnits = 9, bold = false, al = 1) => alpha(al, () => {
    ctx.fillStyle = colour; ctx.font = `${bold ? 700 : 500} ${sizeUnits * u}px IRANYekanX, system-ui, sans-serif`;
    ctx.textAlign = 'center'; ctx.textBaseline = 'middle'; ctx.fillText(value, at.x, at.y);
  });
  const clip = (area: R, fn: () => void) => { ctx.save(); ctx.beginPath(); ctx.rect(area.left, area.top, area.right - area.left, area.bottom - area.top); ctx.clip(); fn(); ctx.restore(); };

  // ── Pieces ──
  const grid = (area: R, al = 1) => {
    for (let y = area.top + 24 * u; y < area.bottom; y += 30 * u) line({ x: area.left, y }, { x: area.right, y }, GRID, 0.6 * u, al);
    for (let x = area.left + 30 * u; x < area.right; x += 52 * u) line({ x, y: area.top }, { x, y: area.bottom }, GRID, 0.6 * u, al);
  };
  const candles = (area: R, data: Bar[], o: { spacing?: number; shift?: number; stretch?: number; centred?: boolean; alpha?: number } = {}) => {
    const { spacing = 10, shift = 0, stretch = 1, centred = false, alpha: al = 1 } = o;
    const lo = Math.min(...data.map((b) => b.low)); const hi = Math.max(...data.map((b) => b.high));
    const mid = (area.top + area.bottom) / 2; const span = (area.bottom - area.top) * 0.62;
    const y = (price: number) => { const raw = mid + span / 2 - ((price - lo) / (hi - lo)) * span; return mid + (raw - mid) * stretch; };
    const step = spacing * u; const body = step * 0.62;
    const cx = (area.left + area.right) / 2;
    clip(area, () => data.forEach((bar, i) => {
      const x = centred ? cx + (i - (data.length - 1) / 2) * step + shift * u : area.right - 18 * u - (data.length - 1 - i) * step + shift * u;
      if (x < area.left - step || x > area.right + step) return;
      const up = bar.close >= bar.open;
      line({ x, y: y(bar.high) }, { x, y: y(bar.low) }, up ? UP_EDGE : DOWN_EDGE, 1.2 * u, al);
      const top = Math.min(y(bar.open), y(bar.close));
      const bottom = Math.max(Math.max(y(bar.open), y(bar.close)), top + 1.5 * u);
      box(x - body / 2, top, body, bottom - top, 1.6 * u, up ? UP : DOWN, al);
    }));
  };
  const priceColumn = (area: R, o: { stretch?: number; alpha?: number; highlight?: boolean } = {}) => {
    const { stretch = 1, alpha: al = 1, highlight = false } = o;
    box(area.left, area.top, area.right - area.left, area.bottom - area.top, 8 * u, highlight ? ACCENT_SOFT : 'rgba(255,255,255,0.55)', al);
    clip(area, () => {
      const mid = (area.top + area.bottom) / 2; const cx = (area.left + area.right) / 2;
      for (let i = -4; i <= 4; i++) text(filmPrice(86_000 - i * 500), { x: cx, y: mid + i * 24 * u * stretch }, INK_SOFT, 7.5, false, al);
    });
  };
  const pointer = (at: P, press: number, al: number) => {
    if (press > 0) disc(at, 11 * u, HALO, 0.35 * press * al);
    const path = new Path2D();
    path.moveTo(at.x, at.y);
    path.lineTo(at.x, at.y + 17 * u); path.lineTo(at.x + 4.2 * u, at.y + 13 * u); path.lineTo(at.x + 7.4 * u, at.y + 19.5 * u);
    path.lineTo(at.x + 10 * u, at.y + 18.3 * u); path.lineTo(at.x + 6.9 * u, at.y + 12 * u); path.lineTo(at.x + 12.4 * u, at.y + 12 * u);
    path.closePath();
    alpha(al, () => { ctx.fillStyle = WHITE; ctx.fill(path); ctx.strokeStyle = INK; ctx.lineWidth = 1.2 * u; ctx.setLineDash([]); ctx.stroke(path); });
  };
  /** A finger, or a mouse pointer where the reader has one. [press] dents it and lights its halo. */
  const finger = (at: P, press: number, al: number) => {
    if (al <= 0) return;
    if (!touch) { pointer(at, press, al); return; }
    const radius = 13 * u * (1 - 0.12 * press);
    disc(add(at, { x: 0, y: 3.5 * u * (1 - press * 0.6) }), radius * 1.08, 'rgba(0,0,0,0.12)', al);
    disc(at, radius * (1.7 + 0.25 * press), HALO, (0.22 + 0.3 * press) * al);
    const g = ctx.createRadialGradient(at.x - radius * 0.35, at.y - radius * 0.35, 0, at.x - radius * 0.35, at.y - radius * 0.35, radius * 1.6);
    g.addColorStop(0, WHITE); g.addColorStop(1, FINGER_SHADE);
    disc(at, radius, g, al);
    ringAt(at, radius, FINGER_RING, 1.4 * u, al);
  };
  const ripple = (at: P, progress: number) => {
    if (progress <= 0 || progress >= 1) return;
    const e = easeOut(progress);
    ringAt(at, (10 + 22 * e) * u, ACCENT, 2 * u, 0.55 * (1 - progress));
    ringAt(at, (6 + 12 * e) * u, ACCENT, 1.4 * u, 0.35 * (1 - progress));
  };
  const holdRing = (at: P, progress: number) => {
    if (progress <= 0 || progress >= 1) return;
    ctx.save(); ctx.strokeStyle = ACCENT; ctx.lineWidth = 2.6 * u; ctx.lineCap = 'round'; ctx.setLineDash([]);
    ctx.beginPath(); ctx.arc(at.x, at.y, 19 * u, -Math.PI / 2, -Math.PI / 2 + Math.PI * 2 * easeInOut(progress)); ctx.stroke(); ctx.restore();
  };
  const handle = (at: P, al = 1, lit = false) => {
    if (lit) disc(at, 9 * u, HALO, 0.45 * al);
    disc(at, 4.6 * u, WHITE, al);
    ringAt(at, 4.6 * u, ACCENT, 1.6 * u, al);
  };
  const card = (area: R, al = 1, lifted = 0, fill = WHITE) => {
    const bw = area.right - area.left; const bh = area.bottom - area.top;
    box(area.left, area.top + (2 + 5 * lifted) * u, bw, bh, 10 * u, `rgba(0,0,0,${0.06 + 0.08 * lifted})`, al);
    box(area.left, area.top, bw, bh, 10 * u, fill, al);
  };
  const sparkline = (area: R, colour: string, seed: number) => {
    const count = 16;
    ctx.save(); ctx.strokeStyle = colour; ctx.lineWidth = 1.6 * u; ctx.lineCap = 'round'; ctx.setLineDash([]); ctx.beginPath();
    for (let i = 0; i <= count; i++) {
      const f = i / count; const wave = Math.sin((i + seed) * 0.9) * 0.28 + f * 0.5;
      const x = area.left + f * (area.right - area.left);
      const y = area.bottom - Math.min(1, Math.max(0, 0.2 + wave * 0.9)) * (area.bottom - area.top);
      if (i === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
    }
    ctx.stroke(); ctx.restore();
  };
  const tag = (at: P, value: string, al: number) => {
    box(at.x - 19 * u, at.y - 6.5 * u, 38 * u, 13 * u, 4 * u, ACCENT, al);
    text(value, at, WHITE, 7, true, al);
  };
  const bell = (at: P, scale: number, al: number, swing = 0) => alpha(al, () => {
    ctx.save();
    const pivot = sub(at, { x: 0, y: 4 * u });
    ctx.translate(pivot.x, pivot.y); ctx.rotate((swing * Math.PI) / 180); ctx.translate(-pivot.x, -pivot.y);
    const s = 4.2 * u * scale;
    ctx.fillStyle = WHITE; ctx.beginPath();
    ctx.moveTo(at.x - s, at.y + s * 0.6);
    ctx.bezierCurveTo(at.x - s, at.y - s * 1.1, at.x + s, at.y - s * 1.1, at.x + s, at.y + s * 0.6);
    ctx.closePath(); ctx.fill();
    ctx.beginPath(); ctx.arc(at.x, at.y + s * 0.95, s * 0.28, 0, Math.PI * 2); ctx.fill();
    ctx.restore();
  });
  /** The toolbar's five glyphs: a trend line, a channel, a fib, a box and a «T». */
  const glyph = (index: number, at: P, colour: string) => {
    const s = 6 * u; const st = 1.4 * u;
    if (index === 0) line(add(at, { x: -s, y: s }), add(at, { x: s, y: -s }), colour, st, 1, undefined, 'round');
    else if (index === 1) {
      line(add(at, { x: -s, y: s * 0.3 }), add(at, { x: s, y: -s }), colour, st, 1, undefined, 'round');
      line(add(at, { x: -s, y: s }), add(at, { x: s, y: -s * 0.3 }), colour, st, 1, undefined, 'round');
    } else if (index === 2) {
      for (let k = 0; k <= 3; k++) { const y = at.y - s + k * s * 0.66; line({ x: at.x - s, y }, { x: at.x + s, y }, colour, st * 0.8); }
    } else if (index === 3) {
      ctx.save(); ctx.strokeStyle = colour; ctx.lineWidth = st; ctx.setLineDash([]); rr(ctx, at.x - s, at.y - s * 0.8, s * 2, s * 1.6, 2 * u); ctx.stroke(); ctx.restore();
    } else {
      line(add(at, { x: -s * 0.8, y: -s }), add(at, { x: s * 0.8, y: -s }), colour, st);
      line(add(at, { x: 0, y: -s }), add(at, { x: 0, y: s }), colour, st);
    }
  };

  // ── The films ──
  const films: Record<SceneId, () => void> = {
    // 1. The drawing cursor: drag anywhere moves the blue dot, a tap sets the point under it.
    trackpad: () => {
      grid(plot); candles(plot, BARS, { alpha: 0.55 }); priceColumn(gutter, { alpha: 0.7 });
      const start = p(w * 0.5, 70); const a = p(w * 0.3, 112); const b = p(w * 0.68, 42);
      const path = (at: number) => (at < 0.48 ? lerpP(start, a, easeInOut(seg(at, 0.1, 0.34))) : lerpP(a, b, easeInOut(seg(at, 0.48, 0.74))));
      const cursor = path(t);
      const home = p(w * 0.42, 150);
      const fingerAt = t < 0.48 ? add(home, sub(cursor, start)) : add(add(home, sub(a, start)), sub(cursor, a));
      const committed = t >= 0.8;
      if (t >= 0.4) {
        line(a, committed ? b : cursor, LINE_BLUE, committed ? 2.6 * u : 2 * u, 1, committed ? undefined : [6 * u, 4 * u], 'round');
        handle(a);
        if (committed) handle(b, 1, true);
      }
      const ca = 1 - seg(t, 0.86, 0.92);
      line({ x: cursor.x, y: plot.top }, { x: cursor.x, y: plot.bottom }, CURSOR_BLUE, u, 0.7 * ca, [5 * u, 4 * u]);
      line({ x: plot.left, y: cursor.y }, { x: plot.right, y: cursor.y }, CURSOR_BLUE, u, 0.7 * ca, [5 * u, 4 * u]);
      for (let s = 1; s <= 7; s++) { const then = t - s * 0.012; if (then < 0) break; disc(path(then), (3.6 - s * 0.35) * u, CURSOR_BLUE, 0.16 * (1 - s / 8) * ca); }
      disc(cursor, 6.4 * u, WHITE, ca); disc(cursor, 4.8 * u, CURSOR_BLUE, ca);
      const tapOne = seg(t, 0.38, 0.46); const tapTwo = seg(t, 0.78, 0.86);
      ripple(fingerAt, seg(t, 0.39, 0.6)); ripple(fingerAt, seg(t, 0.79, 1));
      const press = t < 0.08 ? 0 : tapOne > 0 && tapOne < 1 ? 1 - Math.abs(tapOne - 0.5) * 2 : tapTwo > 0 && tapTwo < 1 ? 1 - Math.abs(tapTwo - 0.5) * 2
        : (t >= 0.1 && t <= 0.34) || (t >= 0.48 && t <= 0.74) ? 0.6 : 0.2;
      finger(fingerAt, press, seg(t, 0.03, 0.1) * (1 - seg(t, 0.88, 0.95)));
    },
    // 2. The floating toolbar: dragged by its six dots, folded with a tap.
    toolbar: () => {
      grid(plot); candles(plot, BARS, { alpha: 0.4 }); priceColumn(gutter, { alpha: 0.55 });
      const origin = lerpP(p(16, 18), p(54, 30), easeOutBack(seg(t, 0.12, 0.4)));
      const fold = easeOutBack(seg(t, 0.56, 0.76));
      const bar: R = { left: origin.x, top: origin.y, right: origin.x + 26 * u, bottom: origin.y + lerp(136, 46, fold) * u };
      card(bar, 1, t >= 0.12 && t <= 0.42 ? 1 : 0);
      for (let row = 0; row <= 1; row++) for (let col = 0; col <= 2; col++) disc({ x: bar.left + (8 + col * 5) * u, y: bar.top + (7 + row * 4.5) * u }, 1.4 * u, GRIP);
      clip({ left: bar.left, top: bar.top + 15 * u, right: bar.right, bottom: bar.bottom - 2 * u }, () => {
        const cx = (bar.left + bar.right) / 2;
        for (let i = 0; i < 5; i++) {
          const cell = { x: cx, y: bar.top + (28 + i * 22) * u };
          if (i === 0) box(cell.x - 10 * u, cell.y - 9 * u, 20 * u, 18 * u, 5 * u, ACCENT_SOFT);
          glyph(i, cell, i === 0 ? CURSOR_BLUE : INK);
        }
      });
      const grip = { x: (bar.left + bar.right) / 2, y: bar.top + 9 * u };
      const at = add(grip, p(4, 6));
      ripple(at, seg(t, 0.54, 0.74));
      const press = t >= 0.1 && t <= 0.42 ? 0.8 : t >= 0.52 && t <= 0.58 ? 1 : 0.15;
      finger(at, press, seg(t, 0.03, 0.1) * (1 - seg(t, 0.66, 0.74)));
    },
    // 3. Pinch (a turning wheel for a mouse).
    pinch: () => {
      grid(plot);
      const open = easeInOut(seg(t, 0.14, 0.52));
      candles(plot, BARS, { spacing: lerp(8.5, 16, open), centred: true });
      priceColumn(gutter);
      const middle = { x: (plot.left + plot.right) / 2, y: (plot.top + plot.bottom) / 2 };
      const al = seg(t, 0.03, 0.12) * (1 - seg(t, 0.62, 0.72));
      if (touch) {
        const reach = lerp(12, 48, open);
        const press = t >= 0.12 && t <= 0.58 ? 0.8 : 0.2;
        finger(add(middle, p(-reach, reach * 0.55)), press, al);
        finger(add(middle, p(reach, -reach * 0.55)), press, al);
      } else {
        finger(middle, 0, al);
        for (let c = 0; c <= 2; c++) {
          const phase = (t * 6 + c / 3) % 1;
          const at = add(middle, p(20, 10 - phase * 18));
          const fade = (1 - Math.abs(phase - 0.5) * 2) * al * (t >= 0.12 && t <= 0.54 ? 1 : 0);
          line(add(at, { x: -4 * u, y: 3 * u }), at, ACCENT, 1.8 * u, fade, undefined, 'round');
          line(at, add(at, { x: 4 * u, y: 3 * u }), ACCENT, 1.8 * u, fade, undefined, 'round');
        }
      }
    },
    // 4. The price column dragged to stretch the chart, then fitted again with two taps.
    axis: () => {
      grid(plot);
      const drag = easeInOut(seg(t, 0.14, 0.44));
      const back = easeOutBack(seg(t, 0.68, 0.88));
      const stretch = lerp(lerp(1, 1.6, drag), 1, back);
      candles(plot, BARS, { stretch });
      priceColumn(gutter, { stretch, highlight: t >= 0.1 && t <= 0.7 });
      const top = p(w - 24, 66); const bottom = p(w - 24, 112);
      const at = t < 0.5 ? lerpP(top, bottom, drag) : bottom;
      ripple(at, seg(t, 0.58, 0.74)); ripple(at, seg(t, 0.66, 0.82));
      const press = t >= 0.12 && t <= 0.46 ? 0.85 : (t >= 0.57 && t <= 0.6) || (t >= 0.65 && t <= 0.68) ? 1 : 0.15;
      finger(at, press, seg(t, 0.03, 0.1) * (1 - seg(t, 0.84, 0.92)));
    },
    // 5. Hold for the crosshair (a right click and its menu for a mouse).
    hold: () => {
      grid(plot); candles(plot, BARS); priceColumn(gutter);
      const at = p(w * 0.55, 80);
      if (touch) {
        holdRing(at, seg(t, 0.14, 0.42));
        const shown = seg(t, 0.42, 0.5);
        if (shown > 0) {
          line({ x: at.x, y: plot.top }, { x: at.x, y: plot.bottom }, ACCENT, 1.2 * u, shown, [5 * u, 4 * u]);
          line({ x: plot.left, y: at.y }, { x: plot.right, y: at.y }, ACCENT, 1.2 * u, shown, [5 * u, 4 * u]);
          tag({ x: (gutter.left + gutter.right) / 2, y: at.y }, filmPrice(85_240), shown);
          tag({ x: at.x, y: plot.bottom - 8 * u }, '14:30', shown);
          disc(at, 3.4 * u, ACCENT, shown);
        }
        finger(at, t >= 0.12 && t <= 0.55 ? 1 : 0.2, seg(t, 0.05, 0.13) * (1 - seg(t, 0.55, 0.63)));
      } else {
        ripple(at, seg(t, 0.18, 0.4));
        const open = easeOutBack(seg(t, 0.26, 0.4));
        if (open > 0) {
          const m = add(at, p(8, 6));
          const menu: R = { left: m.x, top: m.y, right: m.x + 84 * u * open, bottom: m.y + 58 * u * open };
          card(menu, seg(t, 0.26, 0.32));
          for (let row = 0; row <= 2; row++) {
            const y = menu.top + (12 + row * 16) * u * open;
            disc({ x: menu.left + 10 * u, y }, 3.2 * u * open, row === 0 ? BELL : ACCENT_SOFT);
            box(menu.left + 18 * u, y - 2 * u, (40 - row * 8) * u * open, 4 * u, 2 * u, INK_FAINT);
          }
        }
        finger(at, t >= 0.18 && t <= 0.24 ? 1 : 0, seg(t, 0.05, 0.13));
      }
    },
    // 8. A drawing's handles: a point dragged to reshape it, the middle to move it.
    handle: () => {
      grid(plot); candles(plot, BARS, { alpha: 0.7 }); priceColumn(gutter, { alpha: 0.7 });
      const reshape = easeOutBack(seg(t, 0.18, 0.52));
      const move = easeOutBack(seg(t, 0.7, 0.86));
      const shift = mul(p(0, 18), move);
      const first = add(p(w * 0.22, 124), shift);
      const second = add(lerpP(p(w * 0.58, 84), p(w * 0.74, 40), reshape), shift);
      line(first, second, LINE_BLUE, 2.4 * u, 1, undefined, 'round');
      handle(first); handle(second, 1, t >= 0.14 && t <= 0.56);
      const middle = lerpP(first, second, 0.5);
      const at = t < 0.6 ? second : t < 0.68 ? lerpP(second, lerpP(sub(first, shift), sub(second, shift), 0.5), easeInOut(seg(t, 0.6, 0.68))) : middle;
      const press = (t >= 0.14 && t <= 0.54) || (t >= 0.68 && t <= 0.88) ? 0.85 : 0.2;
      finger(at, press, seg(t, 0.04, 0.12) * (1 - seg(t, 0.88, 0.95)));
    },
    // 9. «+» beside the price: an alert at that price.
    plus: () => {
      grid(plot); candles(plot, BARS); priceColumn(gutter, { highlight: t > 0.18 });
      const y = 84 * u;
      const bubble = { x: gutter.left - 12 * u, y };
      const pop = easeOutBack(seg(t, 0.2, 0.32));
      const ring = seg(t, 0.4, 0.48);
      const run = easeInOut(seg(t, 0.42, 0.66));
      if (run > 0) line({ x: gutter.left - 4 * u, y }, { x: gutter.left - 4 * u - (gutter.left - plot.left) * run, y }, BELL, 1.6 * u, 1, [6 * u, 4 * u]);
      if (pop > 0) {
        disc(bubble, 8 * u * pop, ring > 0 ? BELL : ACCENT);
        if (ring < 0.5) {
          line(add(bubble, { x: -3.6 * u * pop, y: 0 }), add(bubble, { x: 3.6 * u * pop, y: 0 }), WHITE, 1.6 * u, 1 - ring * 2);
          line(add(bubble, { x: 0, y: -3.6 * u * pop }), add(bubble, { x: 0, y: 3.6 * u * pop }), WHITE, 1.6 * u, 1 - ring * 2);
        } else bell(bubble, 1, (ring - 0.5) * 2);
      }
      tag({ x: (gutter.left + gutter.right) / 2, y }, filmPrice(85_500), seg(t, 0.18, 0.24));
      const at = lerpP(p(w * 0.5, 130), add(bubble, p(2, 3)), easeInOut(seg(t, 0.06, 0.2)));
      ripple(bubble, seg(t, 0.36, 0.56));
      finger(at, t >= 0.35 && t <= 0.4 ? 1 : 0.15, seg(t, 0.03, 0.08) * (1 - seg(t, 0.56, 0.64)));
    },
    // 10. An alert line dragged to a new price; the bell swings when it lands.
    alertLine: () => {
      grid(plot); candles(plot, BARS, { alpha: 0.8 }); priceColumn(gutter, { alpha: 0.8 });
      const drag = easeOutBack(seg(t, 0.18, 0.52));
      const y = lerp(116, 52, drag) * u;
      const moving = t >= 0.16 && t <= 0.56;
      line({ x: plot.left, y }, { x: gutter.left, y }, BELL, moving ? 2 * u : 1.5 * u, 1, [6 * u, 4 * u]);
      const bx = gutter.left - 2 * u; const bw = gutter.right - gutter.left + 2 * u;
      box(bx, y - 8 * u, bw, 16 * u, 5 * u, BELL);
      const k = seg(t, 0.6, 0.8);
      const swing = Math.sin(k * Math.PI * 5) * 14 * (1 - k);
      bell({ x: bx + 8 * u, y }, 0.8, 1, swing);
      const price = Math.trunc(Math.trunc(lerp(84_200, 86_950, drag)) / 50) * 50;
      text(filmPrice(price), { x: bx + bw / 2 + 4 * u, y }, WHITE, 6.6, true);
      finger({ x: bx + bw / 2, y: y + 2 * u }, moving ? 0.85 : 0.2, seg(t, 0.06, 0.14) * (1 - seg(t, 0.58, 0.66)));
    },
    // 14. Reorder: a row carried down past two others, which step up with a little spring.
    reorder: () => {
      const rows = ['BTC', 'ETH', 'SOL', 'XRP'];
      const pitch = 38;
      const lift = seg(t, 0.12, 0.2) * (1 - seg(t, 0.66, 0.74));
      const carried = easeInOut(seg(t, 0.2, 0.6)) * pitch * 2;
      const rowAt = (area: R, symbol: string, lifted: number) => {
        card(area, 1, lifted);
        const cy = (area.top + area.bottom) / 2;
        text(symbol, { x: area.left + 24 * u, y: cy }, INK, 9.5, true);
        sparkline({ left: area.left + 48 * u, top: area.top + 9 * u, right: area.left + 110 * u, bottom: area.bottom - 9 * u }, UP_EDGE, symbol.length);
        for (let k = -1; k <= 1; k++) { const yy = cy + k * 3.4 * u; line({ x: area.right - 21 * u, y: yy }, { x: area.right - 11 * u, y: yy }, GRIP, 1.4 * u, 1, undefined, 'round'); }
      };
      rows.forEach((symbol, i) => {
        if (i === 0) return;
        const passed = easeOutBack(Math.min(1, Math.max(0, seg(carried, (i - 0.5) * pitch, (i - 0.5) * pitch + 10))));
        const top = 10 + i * pitch - (i <= 2 ? passed * pitch : 0);
        rowAt(r(12, top, w - 12, top + 32), symbol, 0);
      });
      const top = 10 + carried;
      const row = r(12, top, w - 12, top + 32);
      const c = { x: (row.left + row.right) / 2, y: (row.top + row.bottom) / 2 };
      ctx.save(); ctx.translate(c.x, c.y); ctx.scale(1 + 0.03 * lift, 1 + 0.03 * lift); ctx.translate(-c.x, -c.y);
      rowAt(row, rows[0]!, lift);
      ctx.restore();
      finger(add({ x: row.right - 16 * u, y: c.y }, p(2, 4)), t >= 0.1 && t <= 0.66 ? 0.9 : 0.2, seg(t, 0.03, 0.1) * (1 - seg(t, 0.7, 0.78)));
    },
  };

  // ── The stage ──
  ctx.save();
  ctx.clearRect(0, 0, width, height);
  const bg = ctx.createLinearGradient(0, 0, 0, height);
  bg.addColorStop(0, STAGE_TOP); bg.addColorStop(1, STAGE_BOTTOM);
  ctx.fillStyle = bg; ctx.fillRect(0, 0, width, height);
  const drift = Math.sin(t * 2 * Math.PI);
  const glow = (c: P, radius: number, colour: string) => {
    const g = ctx.createRadialGradient(c.x, c.y, 0, c.x, c.y, radius);
    g.addColorStop(0, colour); g.addColorStop(1, 'rgba(255,255,255,0)');
    disc(c, radius, g);
  };
  glow({ x: width * (0.18 + 0.03 * drift), y: height * 0.2 }, height * 0.7, BOKEH_LAVENDER);
  glow({ x: width * (0.84 - 0.03 * drift), y: height * 0.86 }, height * 0.6, BOKEH_MINT);

  const push = 1 + PUSH_IN * easeInOut(t);
  ctx.save();
  ctx.translate(width / 2, height / 2); ctx.scale(push, push); ctx.translate(-width / 2, -height / 2);
  films[scene]();
  ctx.restore();

  const sweep = seg(t, 0.02, 0.3);
  if (sweep > 0 && sweep < 1) {
    const x = -width * 0.4 + sweep * width * 1.8;
    const g = ctx.createLinearGradient(x - height * 0.6, 0, x + height * 0.2, height);
    g.addColorStop(0, 'rgba(255,255,255,0)'); g.addColorStop(0.5, 'rgba(255,255,255,0.22)'); g.addColorStop(1, 'rgba(255,255,255,0)');
    ctx.fillStyle = g; ctx.fillRect(0, 0, width, height);
  }
  const v = ctx.createRadialGradient(width / 2, height / 2, 0, width / 2, height / 2, Math.max(width, height) * 0.62);
  v.addColorStop(0, 'rgba(42,33,96,0)'); v.addColorStop(0.5, 'rgba(42,33,96,0)'); v.addColorStop(1, VIGNETTE);
  ctx.fillStyle = v; ctx.fillRect(0, 0, width, height);
  if (motion) {
    // Through the stage colour at both ends of the loop: a dissolve, never a cut.
    const shown = seg(t, 0, LOOP_FADE) * (1 - seg(t, 1 - LOOP_FADE, 1));
    alpha(1 - shown, () => { ctx.fillStyle = STAGE_MID; ctx.fillRect(0, 0, width, height); });
  }
  ctx.restore();
}
