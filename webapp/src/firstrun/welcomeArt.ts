// The welcome slides' five scenes (OLD app WelcomeSlides.kt), drawn rather than loaded: three layers
// each — a soft radial glow in the brand gold behind, a mid-layer carrying the subject, a foreground
// element saying what the subject is for — in two colours, the gold and the ink. [phase] runs 0 to 1
// once per slide and stops; at 1 every scene is its finished picture, which is what a reader with
// animations off sees from the first frame. The glow is a gradient inside the drawing (the Kotlin
// file is allow-listed for exactly this), never a blur or a coloured shadow.
export type WelcomeSlideId = 'chart' | 'tools' | 'alerts' | 'practice' | 'free';

const GLOW_ALPHA = 0.22;
const CANDLES = 9;
const GRID = 4;
const BARS = 11;
const HAIRLINE = 0.006;
const WICK = 0.008;
const MIN_BODY = 0.012;
const CARD_AT = 0.62;
const SHACKLE_OPEN = 42;
const CANDLE_OPENS = [0.62, 0.58, 0.52, 0.55, 0.5, 0.52, 0.49, 0.45, 0.41];
const CANDLE_CLOSES = [0.58, 0.52, 0.55, 0.5, 0.52, 0.49, 0.45, 0.41, 0.3];
const CANDLE_HIGHS = [0.55, 0.5, 0.49, 0.48, 0.47, 0.47, 0.43, 0.39, 0.26];
const CANDLE_LOWS = [0.66, 0.61, 0.58, 0.58, 0.55, 0.54, 0.51, 0.47, 0.43];
const REPLAY_HEIGHTS = [0.2, 0.3, 0.26, 0.4, 0.34, 0.46, 0.38, 0.5, 0.42, 0.54, 0.47];

export interface ArtColours { gold: string; ink: string; muted: string }

/** `#RRGGBB` at [a]. */
function withAlpha(hex: string, a: number): string {
  const n = parseInt(hex.slice(1), 16);
  return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${Math.max(0, Math.min(1, a))})`;
}

function rr(ctx: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, r: number): void {
  const k = Math.max(0, Math.min(r, w / 2, h / 2));
  ctx.beginPath();
  ctx.moveTo(x + k, y); ctx.arcTo(x + w, y, x + w, y + h, k); ctx.arcTo(x + w, y + h, x, y + h, k); ctx.arcTo(x, y + h, x, y, k); ctx.arcTo(x, y, x + w, y, k);
  ctx.closePath();
}

export function drawWelcomeArt(ctx: CanvasRenderingContext2D, W: number, H: number, slide: WelcomeSlideId, phase: number, c: ArtColours): void {
  const min = Math.min(W, H);
  const line = (x1: number, y1: number, x2: number, y2: number, colour: string, width: number, cap: CanvasLineCap = 'butt') => {
    ctx.strokeStyle = colour; ctx.lineWidth = width; ctx.lineCap = cap; ctx.beginPath(); ctx.moveTo(x1, y1); ctx.lineTo(x2, y2); ctx.stroke();
  };
  const glow = (x: number, y: number, radius: number, strength = 1) => {
    const g = ctx.createRadialGradient(x, y, 0, x, y, radius);
    g.addColorStop(0, withAlpha(c.gold, GLOW_ALPHA * strength)); g.addColorStop(1, withAlpha(c.gold, 0));
    ctx.fillStyle = g; ctx.beginPath(); ctx.arc(x, y, radius, 0, Math.PI * 2); ctx.fill();
  };
  ctx.clearRect(0, 0, W, H);

  if (slide === 'chart') {
    // A chart that explains itself: candles draw themselves left to right; the card arrives last.
    glow(W * 0.66, H * 0.42, min * 0.52);
    for (const f of [0.32, 0.78]) line(0, H * f, W, H * f, c.muted, min * HAIRLINE);
    const slot = W / CANDLES; const body = slot * 0.46; const drawn = phase * CANDLES;
    CANDLE_OPENS.forEach((o, i) => {
      const appear = Math.min(1, Math.max(0, drawn - i));
      if (appear <= 0) return;
      const x = slot * (i + 0.5);
      const open = H * o; const close = H * CANDLE_CLOSES[i]!;
      const live = i === CANDLE_OPENS.length - 1;
      const paint = withAlpha(live ? c.gold : c.ink, appear * (live ? 1 : 0.9));
      line(x, H * CANDLE_HIGHS[i]!, x, H * CANDLE_LOWS[i]!, paint, min * WICK);
      ctx.fillStyle = paint; rr(ctx, x - body / 2, Math.min(open, close), body, Math.max(Math.abs(close - open), min * MIN_BODY), body * 0.22); ctx.fill();
    });
    const card = Math.min(1, Math.max(0, (phase - CARD_AT) / (1 - CARD_AT)));
    if (card <= 0) return;
    const w = W * 0.44; const h = H * 0.2; const left = W * 0.5; const top = H * 0.08;
    ctx.fillStyle = withAlpha(c.gold, card * 0.14); rr(ctx, left, top, w, h, h * 0.3); ctx.fill();
    ctx.strokeStyle = withAlpha(c.gold, card); ctx.lineWidth = min * HAIRLINE * 2; rr(ctx, left, top, w, h, h * 0.3); ctx.stroke();
    for (const [y, run] of [[0.34, 0.62], [0.62, 0.4]] as const) line(left + w * 0.14, top + h * y, left + w * (0.14 + run), top + h * y, withAlpha(c.ink, card * 0.75), min * 0.012, 'round');
  } else if (slide === 'tools') {
    // 240+ tools, and your own: the grid fills cell by cell; the last cell is the one you write.
    glow(W / 2, H / 2, min * 0.5);
    const gap = W * 0.055; const cell = (W - gap * (GRID + 1)) / GRID; const filled = phase * GRID * GRID;
    for (let row = 0; row < GRID; row++) for (let col = 0; col < GRID; col++) {
      const i = row * GRID + col; const appear = Math.min(1, Math.max(0, filled - i));
      const x = gap + col * (cell + gap); const y = gap + row * (cell + gap); const r = cell * 0.26;
      ctx.strokeStyle = c.muted; ctx.lineWidth = min * HAIRLINE * 2; rr(ctx, x, y, cell, cell, r); ctx.stroke();
      if (appear <= 0) continue;
      ctx.fillStyle = withAlpha(c.gold, appear * 0.2); rr(ctx, x, y, cell, cell, r); ctx.fill();
      ctx.strokeStyle = withAlpha(c.gold, appear); rr(ctx, x, y, cell, cell, r); ctx.stroke();
      if (i === GRID * GRID - 1) {
        const mx = x + cell / 2; const my = y + cell / 2; const arm = cell * 0.24;
        line(mx - arm, my, mx + arm, my, withAlpha(c.ink, appear), min * 0.016, 'round');
        line(mx, my - arm, mx, my + arm, withAlpha(c.ink, appear), min * 0.016, 'round');
      }
    }
  } else if (slide === 'alerts') {
    // Alerts and the order book: one ring leaves the bell, once, and the line it crossed stays.
    glow(W / 2, H / 2, min * 0.46, 1 - phase * 0.4);
    line(0, H * 0.74, W, H * 0.74, c.muted, min * HAIRLINE);
    line(W * 0.08, H * 0.62, W * 0.92, H * 0.62, withAlpha(c.gold, 0.55), min * 0.01, 'round');
    const ring = Math.min(1, Math.max(0, phase));
    if (ring > 0) {
      ctx.strokeStyle = withAlpha(c.gold, (1 - ring) * 0.55); ctx.lineWidth = min * 0.014;
      ctx.beginPath(); ctx.arc(W / 2, H * 0.4, min * (0.16 + ring * 0.3), 0, Math.PI * 2); ctx.stroke();
    }
    const bx = W / 2; const by = H * 0.42; const span = min * 0.16; const sw = min * 0.022;
    ctx.strokeStyle = c.ink; ctx.lineWidth = sw; ctx.lineCap = 'round';
    ctx.beginPath(); ctx.arc(bx, by, span, Math.PI, 2 * Math.PI); ctx.stroke();
    for (const side of [-span, span]) line(bx + side, by, bx + side, by + span * 0.72, c.ink, sw, 'round');
    line(bx - span * 1.25, by + span * 0.72, bx + span * 1.25, by + span * 0.72, c.ink, sw, 'round');
    ctx.fillStyle = c.gold; ctx.beginPath(); ctx.arc(bx, by + span * 1.02, span * 0.2, 0, Math.PI * 2); ctx.fill();
  } else if (slide === 'practice') {
    // Practise without risk: the play head sweeps once and the bars behind it turn live.
    const head = W * (0.08 + phase * 0.84);
    glow(head, H * 0.5, min * 0.42);
    line(0, H * 0.76, W, H * 0.76, c.muted, min * HAIRLINE);
    const slot = W / BARS;
    REPLAY_HEIGHTS.forEach((h, i) => { const x = slot * (i + 0.5); line(x, H * 0.76, x, H * 0.76 - H * h, x <= head ? c.ink : c.muted, slot * 0.4, 'round'); });
    line(head, H * 0.2, head, H * 0.82, c.gold, min * 0.012);
    const tip = min * 0.05;
    ctx.fillStyle = c.gold; ctx.beginPath();
    ctx.moveTo(head - tip * 0.7, H * 0.2 - tip); ctx.lineTo(head + tip * 0.9, H * 0.2); ctx.lineTo(head - tip * 0.7, H * 0.2 + tip); ctx.closePath(); ctx.fill();
  } else {
    // Free, in Persian, no deposit: the shackle swings open once and stays open.
    glow(W / 2, H / 2, min * 0.44, 0.7 + phase * 0.3);
    const bw = min * 0.42; const bh = min * 0.34; const bl = (W - bw) / 2; const bt = H * 0.52;
    const span = bw * 0.56; const hx = bl + bw / 2 + span / 2; const hy = bt;
    ctx.save();
    ctx.translate(hx, hy); ctx.rotate((-SHACKLE_OPEN * phase * Math.PI) / 180); ctx.translate(-hx, -hy);
    ctx.strokeStyle = c.muted; ctx.lineWidth = min * 0.036; ctx.lineCap = 'round';
    ctx.beginPath(); ctx.arc(hx - span / 2, bt - span * 0.9 + span / 2, span / 2, Math.PI, 2 * Math.PI); ctx.stroke();
    for (const x of [hx - span, hx]) line(x, bt - span * 0.4, x, bt, c.muted, min * 0.036, 'round');
    ctx.restore();
    ctx.fillStyle = withAlpha(c.ink, 0.1); rr(ctx, bl, bt, bw, bh, bh * 0.26); ctx.fill();
    ctx.strokeStyle = c.ink; ctx.lineWidth = min * 0.022; rr(ctx, bl, bt, bw, bh, bh * 0.26); ctx.stroke();
    const kx = bl + bw / 2; const ky = bt + bh * 0.44;
    ctx.fillStyle = c.gold; ctx.beginPath(); ctx.arc(kx, ky, bh * 0.13, 0, Math.PI * 2); ctx.fill();
    line(kx, ky, kx, ky + bh * 0.28, c.gold, min * 0.02, 'round');
  }
}
