import type { ReactNode } from 'react';
import { usePrefs } from '../state/prefs';

// The old Pro-Chart help's drawn diagrams (bazaarnama/Help.jsx), ported: a schematic in SVG from
// relative coordinates (0–1 of the box, larger numbers are pixels), coloured from the theme. Every
// shape kind the old library uses is drawn; an unknown one is skipped, never a crash.
type Shape = Record<string, unknown> & { kind?: string };
export interface Diagram { ref?: { w?: number; h?: number }; shapes?: Shape[] }

const num = (v: unknown, d = 0) => { const n = Number(v); return Number.isFinite(n) ? n : d; };

function token(name: string, fallback: string): string {
  try { return getComputedStyle(document.documentElement).getPropertyValue(name).trim() || fallback; } catch { return fallback; }
}

export function HelpDiagram({ diagram }: { diagram: Diagram }) {
  usePrefs((s) => s.theme); // repaint with the theme
  if (!diagram || !Array.isArray(diagram.shapes) || !diagram.shapes.length) return null;
  const W = num(diagram.ref?.w, 320);
  const H = num(diagram.ref?.h, 200);
  const px = (x: unknown) => { const n = num(x, 0); return n <= 1 && n >= -1 ? n * W : n; };
  const py = (y: unknown) => { const n = num(y, 0); return n <= 1 && n >= -1 ? n * H : n; };
  const up = '#089981', dn = '#F23645';
  const acc = token('--info', '#2962FF'); const txt = token('--text', '#0F0F0F'); const muted = token('--text-2', '#4A4A4A');
  const panel = token('--bg-sunken', '#F7F7F7'); const grid = token('--line-strong', '#D9D9D9'); const bg = token('--bg', '#FFFFFF');
  const col = (c: unknown, fb?: string) => (c === 'up' ? up : c === 'down' ? dn : c === 'accent' ? acc : c === 'muted' ? muted : (typeof c === 'string' && c) || fb || muted);
  const poly = (pts: unknown) => (Array.isArray(pts) ? (pts as number[][]).map((p) => `${px(p[0])},${py(p[1])}`).join(' ') : '');
  const multi = (text: unknown, x: number, fs: number) => String(text ?? '').split('\n').map((ln, j) => <tspan key={j} x={x} dy={j === 0 ? 0 : fs * 1.25}>{ln}</tspan>);

  const bgCandles = (n: number, opacity: unknown, key: string) => {
    const els: ReactNode[] = []; const slot = W / (n + 1); const cw = Math.max(3, slot * 0.5); let seed = 1234;
    for (let k = 0; k < n; k++) {
      seed = (seed * 9301 + 49297) % 233280; const r = seed / 233280;
      const cx = slot * (k + 1); const mid = H * (0.45 + 0.22 * Math.sin(k * 0.8 + 0.5)); const bh = 6 + r * 20; const c = r > 0.5 ? up : dn;
      els.push(<g key={`${key}-${k}`}><line x1={cx} y1={mid - bh - 7} x2={cx} y2={mid + bh + 7} stroke={c} strokeWidth="1" /><rect x={cx - cw / 2} y={mid - bh} width={cw} height={bh * 2} fill={c} rx="1" /></g>);
    }
    return <g key={key} opacity={opacity == null ? 0.5 : num(opacity, 0.5)}>{els}</g>;
  };

  const node = (s: Shape, i: number): ReactNode => {
    if (!s || typeof s !== 'object') return null;
    switch (s.kind) {
      case 'candles': return bgCandles(num(s.n, 7) || 7, s.opacity, `c${i}`);
      case 'candle': {
        const cx = px(s.x); const w = Math.max(5, num(s.w, 0.1) * W); const c = col(s.color);
        const yt = py(s.bodyTop ?? 0.35), yb = py(s.bodyBot ?? 0.6); const top = Math.min(yt, yb), h = Math.max(2, Math.abs(yb - yt)); const hollow = s.fill === 'hollow';
        return <g key={i}><line x1={cx} y1={py(s.wickTop ?? 0.25)} x2={cx} y2={py(s.wickBot ?? 0.7)} stroke={c} strokeWidth="1.3" /><rect x={cx - w / 2} y={top} width={w} height={h} rx="1" fill={hollow ? 'none' : c} stroke={c} strokeWidth={hollow ? 1.5 : 1} /></g>;
      }
      case 'bar': {
        const cx = px(s.x), c = col(s.color), tick = Math.max(4, 0.03 * W);
        return <g key={i}><line x1={cx} y1={py(s.top)} x2={cx} y2={py(s.bot)} stroke={c} strokeWidth="1.5" />{s.open != null ? <line x1={cx - tick} y1={py(s.open)} x2={cx} y2={py(s.open)} stroke={c} strokeWidth="1.5" /> : null}{s.close != null ? <line x1={cx} y1={py(s.close)} x2={cx + tick} y2={py(s.close)} stroke={c} strokeWidth="1.5" /> : null}</g>;
      }
      case 'line': return <line key={i} x1={px(s.x1)} y1={py(s.y1)} x2={px(s.x2)} y2={py(s.y2)} stroke={s.accent ? acc : col(s.color)} strokeWidth={num(s.w, 2)} strokeDasharray={s.dashed ? '5 4' : undefined} strokeLinecap="round" markerEnd={s.arrow ? 'url(#hd-ah)' : undefined} />;
      case 'ray': return <line key={i} x1={px(s.x1)} y1={py(s.y1)} x2={px(s.x2)} y2={py(s.y2)} stroke={s.accent === false ? col(s.color) : acc} strokeWidth={num(s.w, 2)} strokeDasharray={s.dashed ? '5 4' : undefined} strokeLinecap="round" />;
      case 'hline': return <line key={i} x1="0" y1={py(s.y)} x2={W} y2={py(s.y)} stroke={s.accent ? acc : col(s.color)} strokeWidth={num(s.w, 1.5)} strokeDasharray={s.dashed ? '5 4' : undefined} />;
      case 'vline': return <line key={i} x1={px(s.x)} y1="0" x2={px(s.x)} y2={H} stroke={s.accent ? acc : col(s.color)} strokeWidth={num(s.w, 1.5)} strokeDasharray={s.dashed ? '5 4' : undefined} />;
      case 'rect': case 'zone': {
        const x = px(s.x ?? s.x1), y = py(s.y ?? s.y1);
        const w = s.w != null ? px(s.w) : Math.abs(px(s.x2) - px(s.x1)); const h = s.h != null ? py(s.h) : Math.abs(py(s.y2) - py(s.y1));
        const c = s.accent ? acc : col(s.color, acc);
        return <rect key={i} x={Math.min(x, x + w)} y={Math.min(y, y + h)} width={Math.abs(w)} height={Math.abs(h)} fill={c} fillOpacity={num(s.fill, 0.14)} stroke={c} strokeWidth="1.2" strokeDasharray={s.dashed ? '4 3' : undefined} rx="2" />;
      }
      case 'brick': { const sz = Math.max(8, 0.1 * W); return <rect key={i} x={px(s.x)} y={py(s.y)} width={sz} height={sz} fill={col(s.color)} fillOpacity="0.85" stroke={panel} strokeWidth="1.5" rx="1" />; }
      case 'block': { const w = Math.max(8, 0.11 * W), h = Math.max(8, 0.1 * H); return <rect key={i} x={px(s.x) - w / 2} y={py(s.y)} width={w} height={h} fill={col(s.color)} fillOpacity="0.85" stroke={panel} strokeWidth="1.5" rx="1" />; }
      case 'hlbar': { const bw = Math.max(6, 0.06 * W); return <rect key={i} x={px(s.x) - bw / 2} y={py(s.top)} width={bw} height={Math.max(2, py(s.bot) - py(s.top))} fill={col(s.color)} rx="1.5" />; }
      case 'columns': {
        const baseY = py(s.baseY ?? 0.9); const bars = Array.isArray(s.bars) ? (s.bars as Shape[]) : []; const bw = Math.max(6, (W / (bars.length + 1)) * 0.55);
        return <g key={i}>{bars.map((b, j) => { const h = num(b.h, 0.3) * H; return <rect key={j} x={px(b.x) - bw / 2} y={baseY - h} width={bw} height={Math.max(2, h)} fill={col(b.color)} rx="1" />; })}</g>;
      }
      case 'pnfcol': {
        const cx = px(s.x), c = col(s.color); const a = py(s.from), b = py(s.to), top = Math.min(a, b), bot = Math.max(a, b);
        const r = Math.max(5, 0.035 * W), gap = r * 2.4; const els: ReactNode[] = [];
        for (let yy = top; yy <= bot + 1; yy += gap) {
          if (s.dir === 'O') els.push(<circle key={yy} cx={cx} cy={yy + r} r={r} fill="none" stroke={c} strokeWidth="1.6" />);
          else els.push(<g key={yy}><line x1={cx - r} y1={yy} x2={cx + r} y2={yy + 2 * r} stroke={c} strokeWidth="1.6" /><line x1={cx - r} y1={yy + 2 * r} x2={cx + r} y2={yy} stroke={c} strokeWidth="1.6" /></g>);
        }
        return <g key={i}>{els}</g>;
      }
      case 'kagi': return <g key={i} fill="none" strokeLinejoin="round" strokeLinecap="round">{(Array.isArray(s.segments) ? (s.segments as Shape[]) : []).map((sg, j) => <polyline key={j} points={poly(sg.points)} stroke={sg.thick ? up : dn} strokeWidth={sg.thick ? 3.2 : 1.4} />)}</g>;
      case 'area': {
        const pts = Array.isArray(s.points) ? (s.points as number[][]) : []; if (!pts.length) return null;
        const base = py(s.baseline ?? 1); const c = col(s.color, acc); const d = poly(pts);
        return <g key={i}><polygon points={`${px(pts[0]![0])},${base} ${d} ${px(pts[pts.length - 1]![0])},${base}`} fill={c} fillOpacity="0.16" /><polyline points={d} fill="none" stroke={c} strokeWidth="2" strokeLinejoin="round" /></g>;
      }
      case 'baseline': {
        const pts = Array.isArray(s.points) ? (s.points as number[][]) : []; const base = py(s.baseY ?? 0.5);
        const uc = col(s.upColor ?? 'up'), dc = col(s.downColor ?? 'down'); const u = `hd-bu${i}`, d = `hd-bd${i}`;
        const fill = pts.length ? `${px(pts[0]![0])},${base} ${poly(pts)} ${px(pts[pts.length - 1]![0])},${base}` : '';
        return <g key={i}><defs><clipPath id={u}><rect x="0" y="0" width={W} height={base} /></clipPath><clipPath id={d}><rect x="0" y={base} width={W} height={Math.max(0, H - base)} /></clipPath></defs>
          <line x1="0" y1={base} x2={W} y2={base} stroke={muted} strokeWidth="1" strokeDasharray="4 3" />
          {pts.length ? <><polygon points={fill} fill={uc} fillOpacity="0.16" clipPath={`url(#${u})`} /><polygon points={fill} fill={dc} fillOpacity="0.16" clipPath={`url(#${d})`} /><polyline points={poly(pts)} fill="none" stroke={uc} strokeWidth="2" clipPath={`url(#${u})`} /><polyline points={poly(pts)} fill="none" stroke={dc} strokeWidth="2" clipPath={`url(#${d})`} /></> : null}</g>;
      }
      case 'steps': return <polyline key={i} points={poly(s.points)} fill="none" stroke={col(s.color, acc)} strokeWidth="2" strokeLinejoin="miter" />;
      case 'volbars': {
        const hs = Array.isArray(s.heights) ? (s.heights as number[]) : []; const baseY = py(s.y ?? 0.78), maxH = num(s.h, 0.18) * H; const bw = Math.max(5, (W / (hs.length + 1)) * 0.5);
        return <g key={i}>{hs.map((hh, j) => { const h = num(hh, 0.3) * maxH; return <rect key={j} x={(W / (hs.length + 1)) * (j + 1) - bw / 2} y={baseY - h} width={bw} height={Math.max(2, h)} fill={j % 2 ? dn : up} fillOpacity="0.55" rx="1" />; })}</g>;
      }
      case 'hist': {
        const els: ReactNode[] = []; const bh = H / 10; let seed = 77;
        for (let k = 0; k < 9; k++) { seed = (seed * 9301 + 49297) % 233280; els.push(<rect key={k} x={px(s.x ?? 0.02)} y={bh * (k + 0.6)} width={(0.12 + (seed / 233280) * 0.5) * W} height={bh * 0.6} fill={acc} fillOpacity="0.3" rx="1" />); }
        return <g key={i}>{els}</g>;
      }
      case 'equalbars': {
        const n = num(s.count, 8), h = num(s.height, 0.25) * H, baseY = H * 0.62; const els: ReactNode[] = []; let x = 0.08 * W; let seed = 33;
        for (let k = 0; k < n; k++) { seed = (seed * 9301 + 49297) % 233280; els.push(<rect key={k} x={x} y={baseY - h / 2} width={Math.max(4, 0.035 * W)} height={h} fill={k % 2 ? dn : up} rx="1" />); x += (0.04 + (seed / 233280) * 0.1) * W + 0.035 * W; }
        return <g key={i}>{els}</g>;
      }
      case 'series': {
        const x = px(s.x ?? 0.05), y = py(s.y ?? 0.1), w = px(s.w ?? 0.9), h = py(s.h ?? 0.3); const smooth = s.role === 'ha' || s.role === 'close' || Boolean(s.smooth);
        return <g key={i}><rect x={x} y={y} width={w} height={h} fill={panel} stroke={grid} strokeWidth="1" rx="3" />
          {Array.from({ length: 6 }).map((_, k) => { const cx = x + (w / 7) * (k + 1); const mid = y + h * (0.5 - (smooth ? 0.18 : 0.34) * Math.sin(k * 0.9)); const bh = h * (smooth ? 0.1 : 0.06 + (0.12 * ((k * 73) % 5)) / 5); return <rect key={k} x={cx - 3} y={mid - bh} width="6" height={Math.max(3, bh * 2)} fill={smooth || k % 3 !== 0 ? up : dn} rx="1" />; })}</g>;
      }
      case 'minichart': {
        const cx = px(s.x), wb = 0.22 * W, hb = 0.4 * H, x0 = cx - wb / 2, y0 = 0.25 * H; const upT = s.trend !== 'down';
        const pts = upT ? [[0, 0.9], [0.3, 0.6], [0.6, 0.65], [1, 0.2]] : [[0, 0.2], [0.3, 0.5], [0.6, 0.4], [1, 0.85]];
        return <g key={i}><rect x={x0} y={y0} width={wb} height={hb} fill={panel} stroke={grid} strokeWidth="1" rx="3" /><polyline points={pts.map((p) => `${x0 + p[0]! * wb},${y0 + p[1]! * hb}`).join(' ')} fill="none" stroke={upT ? up : dn} strokeWidth="2" /></g>;
      }
      case 'mirror': return <line key={i} x1={px(s.x)} y1="0" x2={px(s.x)} y2={H} stroke={muted} strokeWidth="1.5" strokeDasharray="2 3" />;
      case 'tfbar': {
        const items = Array.isArray(s.items) ? (s.items as string[]) : []; const padX = 0.06 * W, gap = 0.02 * W; const cw = (W - padX * 2 - gap * (items.length - 1)) / Math.max(1, items.length); const y = py(s.y ?? 0.42), ch = Math.max(22, 0.18 * H);
        return <g key={i}>{items.map((it, j) => { const x = padX + j * (cw + gap), on = it === s.active; return <g key={j}><rect x={x} y={y} width={cw} height={ch} rx="4" fill={on ? acc : panel} stroke={on ? acc : grid} strokeWidth="1" /><text x={x + cw / 2} y={y + ch / 2 + 4} fontSize="11" textAnchor="middle" fill={on ? '#fff' : muted} fontWeight={on ? 700 : 500}>{it}</text></g>; })}</g>;
      }
      case 'axis': {
        const ticks = Array.isArray(s.ticks) ? (s.ticks as string[]) : []; const x = W - 0.16 * W;
        return <g key={i}><line x1={x} y1={0.08 * H} x2={x} y2={0.92 * H} stroke={grid} strokeWidth="1.2" />
          {ticks.map((t, j) => { const y = 0.12 * H + ((0.78 * H) / Math.max(1, ticks.length - 1)) * j; return <g key={j}><line x1={x} y1={y} x2={x + 5} y2={y} stroke={grid} strokeWidth="1" /><text x={x + 9} y={y + 4} fontSize="10" fill={muted}>{t}</text></g>; })}
          {s.mode ? <text x={x - 8} y={0.06 * H} fontSize="10" textAnchor="end" fill={acc} fontWeight="700">{String(s.mode)}</text> : null}</g>;
      }
      case 'axisState': { const cx = px(s.x), w = 0.22 * W, h = 0.16 * H, y = 0.4 * H; return <g key={i}><rect x={cx - w / 2} y={y} width={w} height={h} rx="5" fill={panel} stroke={grid} strokeWidth="1" /><text x={cx} y={y + h / 2 + 4} fontSize="11" textAnchor="middle" fill={s.icon === 'lock' ? acc : muted} fontWeight="600">{String(s.label ?? '')}</text></g>; }
      case 'axisLabel': {
        const isR = s.side === 'right', isB = s.side === 'bottom'; const x = isR ? W - 30 : px(s.x); const y = isB ? H - 8 : py(s.y); const w = 52, h = 16;
        return <g key={i}><rect x={isR ? W - w : x - w / 2} y={y - h / 2} width={w} height={h} rx="3" fill={acc} /><text x={isR ? W - w / 2 : x} y={y + 4} fontSize="10" textAnchor="middle" fill="#fff" fontWeight="600">{String(s.text ?? '')}</text></g>;
      }
      case 'crosshair': {
        const x = px(s.x), y = py(s.y);
        return <g key={i}><line x1="0" y1={y} x2={W} y2={y} stroke={muted} strokeWidth="1" strokeDasharray="4 3" /><line x1={x} y1="0" x2={x} y2={H} stroke={muted} strokeWidth="1" strokeDasharray="4 3" />
          {s.glyph === 'dot' ? <circle cx={x} cy={y} r="4" fill={acc} stroke="#fff" strokeWidth="1.2" /> : null}
          {s.glyph === 'arrow' ? <path d={`M${x},${y} l9,3 l-3,1 l3,4 l-2,1 l-3,-4 l-2,2 Z`} fill={acc} /> : null}</g>;
      }
      case 'snap': {
        const f = (s.from as number[]) ?? [0.6, 0.4], t = (s.to as number[]) ?? [0.5, 0.3];
        return <g key={i}><line x1={px(f[0])} y1={py(f[1])} x2={px(t[0])} y2={py(t[1])} stroke={acc} strokeWidth="1.6" strokeDasharray="3 3" markerEnd="url(#hd-ah)" /><circle cx={px(t[0])} cy={py(t[1])} r="4" fill={acc} stroke="#fff" strokeWidth="1" />{s.target ? <text x={px(t[0]) + 7} y={py(t[1]) - 5} fontSize="10" fill={acc} fontWeight="700">{String(s.target)}</text> : null}</g>;
      }
      case 'dot': case 'point': return <g key={i}><circle cx={px(s.x)} cy={py(s.y)} r={s.accent ? 5 : 4} fill={s.accent ? acc : col(s.color, txt)} stroke={panel} strokeWidth="1.2" />{s.label ? <text x={px(s.x) + 8} y={py(s.y) + 4} fontSize="10" fill={s.accent ? acc : muted} fontWeight={s.accent ? 700 : 500}>{String(s.label)}</text> : null}</g>;
      case 'arrowhead': {
        const x = px(s.x), y = py(s.y); const dirs: Record<string, number> = { 'up-right': -45, 'up-left': -135, 'down-right': 45, 'down-left': 135, right: 0, left: 180, up: -90, down: 90 };
        const a = ((dirs[String(s.dir)] ?? 0) * Math.PI) / 180, L = 9;
        return <g key={i}><path d={`M${x},${y} L${x - L * Math.cos(a - 0.4)},${y - L * Math.sin(a - 0.4)} L${x - L * Math.cos(a + 0.4)},${y - L * Math.sin(a + 0.4)} Z`} fill={acc} />{s.label ? <text x={x} y={y - 8} fontSize="10" textAnchor="middle" fill={acc} fontWeight="700">{String(s.label)}</text> : null}</g>;
      }
      case 'arc': {
        const cx = px(s.cx), cy = py(s.cy), r = num(s.r, 0.15) * W; const a0 = (-num(s.from, 0) * Math.PI) / 180, a1 = (-num(s.to, 45) * Math.PI) / 180;
        const large = Math.abs(num(s.to, 45) - num(s.from, 0)) > 180 ? 1 : 0;
        return <path key={i} d={`M${cx + r * Math.cos(a0)},${cy + r * Math.sin(a0)} A${r},${r} 0 ${large} 0 ${cx + r * Math.cos(a1)},${cy + r * Math.sin(a1)}`} fill="none" stroke={acc} strokeWidth="1.4" />;
      }
      case 'cleanchart': return <g key={i}><rect x={0.05 * W} y={0.1 * H} width={0.9 * W} height={0.75 * H} fill="none" stroke={grid} strokeWidth="1" rx="4" /><polyline points={poly([[0.1, 0.7], [0.3, 0.45], [0.5, 0.55], [0.7, 0.3], [0.9, 0.4]])} fill="none" stroke={acc} strokeWidth="2" /></g>;
      case 'icon': {
        const x = px(s.x), y = py(s.y);
        if (s.text != null) return <text key={i} x={x} y={y} fontSize={num(s.size, 11)} fill={s.accent ? acc : muted} fontWeight="600" textAnchor={(s.anchor as 'start') ?? 'start'}>{String(s.text)}</text>;
        return <g key={i}><circle cx={x} cy={y} r="9" fill={panel} stroke={acc} strokeWidth="1.2" /><text x={x} y={y + 4} fontSize="10" textAnchor="middle" fill={acc} fontWeight="700">{String(s.name ?? '?').slice(0, 1).toUpperCase()}</text></g>;
      }
      case 'label': case 'text': {
        const x = px(s.x), y = py(s.y); const content = s.text != null ? s.text : s.label; const fs = num(s.size, 11);
        if (s.box) {
          const lines = String(content ?? '').split('\n'); const bw = Math.max(...lines.map((l) => l.length)) * fs * 0.56 + 14; const bh = lines.length * fs * 1.25 + 8;
          return <g key={i}><rect x={x - bw / 2} y={y - fs} width={bw} height={bh} rx="4" fill={panel} stroke={acc} strokeWidth="1" opacity="0.96" /><text x={x} y={y} fontSize={fs} fill={s.accent ? acc : txt} fontWeight="600" textAnchor="middle">{multi(content, x, fs)}</text></g>;
        }
        return <text key={i} x={x} y={y} fontSize={fs} fill={s.accent ? acc : muted} fontWeight={s.accent ? 700 : 500} textAnchor={(s.anchor as 'middle') ?? 'middle'}>{multi(content, x, fs)}</text>;
      }
      default: return null;
    }
  };

  return (
    <svg className="help-diagram" viewBox={`0 0 ${W} ${H}`} width="100%" style={{ background: bg, direction: 'ltr' }} role="img">
      <defs><marker id="hd-ah" markerWidth="8" markerHeight="8" refX="6" refY="3" orient="auto"><path d="M0,0 L6,3 L0,6 Z" fill={acc} /></marker></defs>
      {diagram.shapes.map(node)}
    </svg>
  );
}
