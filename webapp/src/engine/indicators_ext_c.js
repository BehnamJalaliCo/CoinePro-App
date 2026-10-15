// Indicators the web app adds to the old Pro-Chart registry for TradingView parity: built-ins neither
// app had, the volume profiles of the Profiles tab, and the single candlestick patterns of the
// Patterns tab. Same contract as the registry: pure functions of the bars' columns returning the
// output shape the renderer knows, plus four the renderer learned for them:
//   candles   [{ time, open, high, low, close, color }]  — re-coloured bars (Bollinger Bars)
//   table     { title, rows: [[label, value, color?]] }   — a small table over the chart (Performance)
//   profile   { segments: [{ from, to, rows, poc, vah, val }], place: 'right' | 'inside' } — volume profiles
//   flags     visibleRange (inputs __from/__to are the bars on screen), other (inputs __other is the
//             second symbol's closes on these bars, by time)
// `STUDIES` is the whole catalogue: the vendored REGISTRY (108, unchanged) plus these.
import { REGISTRY, sma, ema, bollinger, hasVolume, resolveSrc } from './indicators.js';
import { TV_PATTERNS, detectPattern } from './candlePatterns.js';
import { technicalRating } from './techRating.js';

const nulls = (n) => new Array(n).fill(null);
const UP = '#089981', DN = '#F23645';

// ── Helpers ────────────────────────────────────────────────────────────────────────────────────
const rollSum = (v, p) => { const o = nulls(v.length); let s = 0; for (let i = 0; i < v.length; i++) { s += v[i] || 0; if (i >= p) s -= v[i - p] || 0; if (i >= p - 1) o[i] = s; } return o; };
const sd = (v, p) => { const m = sma(v, p); return v.map((_, i) => { if (m[i] == null) return null; let s = 0; for (let j = 0; j < p; j++) { const d = v[i - j] - m[i]; s += d * d; } return Math.sqrt(s / p); }); };
const highest = (v, p, i) => { let m = -Infinity; for (let j = Math.max(0, i - p + 1); j <= i; j++) m = Math.max(m, v[j]); return m; };
const lowest = (v, p, i) => { let m = Infinity; for (let j = Math.max(0, i - p + 1); j <= i; j++) m = Math.min(m, v[j]); return m; };
/** Period key of a bar time (UTC): day, ISO week (Monday) or month. */
export const periodKey = (t, anchor) => {
  if (anchor === 'W') return Math.floor((t / 86400 + 3) / 7);
  if (anchor === 'M') { const d = new Date(t * 1000); return d.getUTCFullYear() * 12 + d.getUTCMonth(); }
  return Math.floor(t / 86400);
};
const clv = (h, l, c) => (h > l ? ((c - l) - (h - c)) / (h - l) : 0);
/** Swing highs and lows (pivot length on each side) as [{ i, price, high }] in time order. */
export const swings = (h, l, len) => {
  const out = [];
  for (let i = len; i < h.length - len; i++) {
    let hi = true, lo = true;
    for (let j = 1; j <= len; j++) { if (h[i - j] >= h[i] || h[i + j] > h[i]) hi = false; if (l[i - j] <= l[i] || l[i + j] < l[i]) lo = false; }
    if (hi) out.push({ i, price: h[i], high: true });
    if (lo) out.push({ i, price: l[i], high: false });
  }
  return out;
};
/** Alternating swings (a high, then a low, …), keeping the more extreme of two in a row. */
const zig = (s) => { const out = []; for (const p of s) { const last = out[out.length - 1]; if (last && last.high === p.high) { if ((p.high && p.price > last.price) || (!p.high && p.price < last.price)) out[out.length - 1] = p; } else out.push(p); } return out; };

// ── Volume profile ─────────────────────────────────────────────────────────────────────────────
/** Rows of a profile over bars [a, b]: up and down volume per price row, the point of control and the value area. */
export function profileRows(c, a, b, rows = 24, vaPct = 70) {
  let lo = Infinity, hi = -Infinity;
  for (let i = a; i <= b; i++) { lo = Math.min(lo, c.low[i]); hi = Math.max(hi, c.high[i]); }
  if (!Number.isFinite(lo) || !Number.isFinite(hi) || hi <= lo) return null;
  const step = (hi - lo) / rows;
  const out = Array.from({ length: rows }, (_, k) => ({ lo: lo + k * step, hi: lo + (k + 1) * step, up: 0, down: 0 }));
  const withVol = hasVolume(c.volume.slice(a, b + 1));
  for (let i = a; i <= b; i++) {
    const v = withVol ? (c.volume[i] || 0) : 1;
    const r0 = Math.max(0, Math.min(rows - 1, Math.floor((c.low[i] - lo) / step)));
    const r1 = Math.max(0, Math.min(rows - 1, Math.floor((c.high[i] - lo) / step)));
    const per = v / (r1 - r0 + 1);
    const up = c.close[i] >= c.open[i];
    for (let r = r0; r <= r1; r++) { if (up) out[r].up += per; else out[r].down += per; }
  }
  let poc = 0;
  for (let r = 1; r < rows; r++) if (out[r].up + out[r].down > out[poc].up + out[poc].down) poc = r;
  const total = out.reduce((s, r) => s + r.up + r.down, 0);
  let a1 = poc, b1 = poc, acc = out[poc].up + out[poc].down;
  while (acc < total * vaPct / 100 && (a1 > 0 || b1 < rows - 1)) {
    const below = a1 > 0 ? out[a1 - 1].up + out[a1 - 1].down : -1;
    const above = b1 < rows - 1 ? out[b1 + 1].up + out[b1 + 1].down : -1;
    if (above >= below) { b1++; acc += above; } else { a1--; acc += below; }
  }
  return { rows: out.map((r, k) => ({ ...r, va: k >= a1 && k <= b1, poc: k === poc })), poc: (out[poc].lo + out[poc].hi) / 2, vah: out[b1].hi, val: out[a1].lo };
}

const vpPeriodic = (anchor) => (c, i) => {
  const n = c.close.length; const segs = [];
  let start = 0;
  for (let k = 1; k <= n; k++) {
    if (k === n || periodKey(c.time[k], anchor) !== periodKey(c.time[start], anchor)) {
      const p = profileRows(c, start, k - 1, i.rows, i.valueArea);
      if (p) segs.push({ from: c.time[start], to: c.time[k - 1], ...p });
      start = k;
    }
  }
  return { profile: { segments: segs.slice(-60), place: 'inside' } };
};

// ── The indicators ─────────────────────────────────────────────────────────────────────────────
const chopZone = (c) => {
  const n = c.close.length; const e = ema(c.close, 34); const ang = nulls(n);
  for (let i = 1; i < n; i++) {
    if (e[i] == null || e[i - 1] == null) continue;
    const avg = (c.high[i] + c.low[i] + c.close[i]) / 3;
    const hh = highest(c.high, 30, i), ll = lowest(c.low, 30, i);
    const span = hh > ll ? 25 / (hh - ll) * ll : 0;
    const y2 = (e[i - 1] - e[i]) / avg * span;
    const len = Math.sqrt(1 + y2 * y2);
    const a = Math.round(180 * Math.acos(1 / len) / Math.PI);
    ang[i] = y2 > 0 ? -a : a;
  }
  const color = (a) => (a == null ? '#787B86' : a >= 5 ? '#26C6DA' : a >= 3.57 ? '#43A047' : a >= 2.14 ? '#A5D6A7' : a >= 0.71 ? '#009688' : a <= -5 ? '#D50000' : a <= -3.57 ? '#E91E63' : a <= -2.14 ? '#FF6D00' : a <= -0.71 ? '#FFB74D' : '#FDD835');
  return { hist: ang.map((a) => (a == null ? null : 1)), palette: (_v, k) => color(ang[k]), range: [0, 1] };
};

const correlation = (a, b, p) => a.map((_, i) => {
  if (i < p - 1) return null;
  let sa = 0, sb = 0, saa = 0, sbb = 0, sab = 0;
  for (let j = i - p + 1; j <= i; j++) { const x = a[j], y = b[j]; if (x == null || y == null) return null; sa += x; sb += y; saa += x * x; sbb += y * y; sab += x * y; }
  const d = Math.sqrt((p * saa - sa * sa) * (p * sbb - sb * sb));
  return d ? (p * sab - sa * sb) / d : null;
});

const rci = (src, p) => src.map((_, i) => {
  if (i < p - 1) return null;
  const w = []; for (let j = 0; j < p; j++) w.push({ v: src[i - j], t: j });
  const byPrice = [...w].sort((x, y) => y.v - x.v);
  const rank = new Map(); byPrice.forEach((x, k) => rank.set(x.t, k + 1));
  let d2 = 0; for (const x of w) { const d = (x.t + 1) - rank.get(x.t); d2 += d * d; }
  return (1 - 6 * d2 / (p * (p * p - 1))) * 100;
});

const delta = (c) => c.close.map((cl, i) => (c.volume[i] || 0) * clv(c.high[i], c.low[i], cl));

const tables = {
  performance: (c) => {
    const last = c.close[c.close.length - 1]; const tNow = c.time[c.time.length - 1];
    const back = (days) => { const t = tNow - days * 86400; let k = 0; while (k < c.time.length - 1 && c.time[k] < t) k++; return c.time[k] <= t + 86400 * 2 ? c.close[k] : null; };
    const ytdStart = Date.UTC(new Date(tNow * 1000).getUTCFullYear(), 0, 1) / 1000;
    const ytd = (() => { const k = c.time.findIndex((t) => t >= ytdStart); return k >= 0 ? c.close[k] : null; })();
    const pct = (b) => (b ? (last - b) / b * 100 : null);
    const rows = [['1W', pct(back(7))], ['1M', pct(back(30))], ['3M', pct(back(91))], ['6M', pct(back(182))], ['YTD', pct(ytd)], ['1Y', pct(back(365))]]
      .map(([k, v]) => [k, v == null ? '—' : `${v >= 0 ? '+' : '−'}${Math.abs(v).toFixed(2)}%`, v == null ? null : v >= 0 ? UP : DN]);
    return { table: { title: 'Performance', rows } };
  },
  seasonality: (c) => {
    // Average monthly return over the years in the data, and this year's.
    const byMonth = Array.from({ length: 12 }, () => []); const thisYear = new Array(12).fill(null);
    const year = new Date(c.time[c.time.length - 1] * 1000).getUTCFullYear();
    let k = 0;
    while (k < c.time.length) {
      const d = new Date(c.time[k] * 1000); const y = d.getUTCFullYear(), m = d.getUTCMonth();
      let e = k; while (e + 1 < c.time.length) { const d2 = new Date(c.time[e + 1] * 1000); if (d2.getUTCFullYear() !== y || d2.getUTCMonth() !== m) break; e++; }
      const r = c.open[k] ? (c.close[e] - c.open[k]) / c.open[k] * 100 : null;
      if (r != null) { byMonth[m].push(r); if (y === year) thisYear[m] = r; }
      k = e + 1;
    }
    const M = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    const f = (v) => (v == null ? '—' : `${v >= 0 ? '+' : '−'}${Math.abs(v).toFixed(2)}%`);
    const rows = M.map((name, m) => { const a = byMonth[m].length ? byMonth[m].reduce((s, x) => s + x, 0) / byMonth[m].length : null; return [name, `${f(a)} · ${f(thisYear[m])}`, a == null ? null : a >= 0 ? UP : DN]; });
    return { table: { title: 'Seasonality (avg · this year)', rows } };
  },
};

// Moon phases: new and full moons from the mean synodic month, counted from the new moon of
// 2000-01-06 18:14 UTC (accurate to within a day, as TradingView's are).
const SYNODIC = 29.530588853 * 86400;
const NEW_MOON = Date.UTC(2000, 0, 6, 18, 14) / 1000;
const moon = (c) => {
  const n = c.time.length; if (!n) return { markers: [] };
  const markers = [];
  const first = c.time[0], last = c.time[n - 1];
  let k = Math.floor((first - NEW_MOON) / SYNODIC) - 1;
  for (; ; k++) {
    for (const [phase, off] of [['new', 0], ['full', 0.5]]) {
      const t = NEW_MOON + (k + off) * SYNODIC;
      if (t < first) continue;
      if (t > last) return { markers };
      let i = 0; while (i < n - 1 && c.time[i + 1] <= t) i++;
      markers.push({ time: c.time[i], position: phase === 'new' ? 'belowBar' : 'aboveBar', shape: 'circle', color: phase === 'new' ? '#787B86' : '#FFB300', text: phase === 'new' ? '●' : '○' });
    }
  }
};

const autoTrend = (c, i) => {
  const n = c.close.length; const s = swings(c.high, c.low, i.depth || 10);
  const highs = s.filter((x) => x.high).slice(-2), lows = s.filter((x) => !x.high).slice(-2);
  const line = (pts) => { if (pts.length < 2) return nulls(n); const [a, b] = pts; const slope = (b.price - a.price) / (b.i - a.i); return c.close.map((_, k) => (k >= a.i ? a.price + slope * (k - a.i) : null)); };
  return { lines: [{ data: line(highs), color: DN, name: 'Resistance' }, { data: line(lows), color: UP, name: 'Support' }] };
};

const autoPitchfork = (c, i) => {
  const n = c.close.length; const z = zig(swings(c.high, c.low, i.depth || 10)).slice(-3);
  if (z.length < 3) return { lines: [] };
  const [p0, p1, p2] = z; const mx = (p1.i + p2.i) / 2, my = (p1.price + p2.price) / 2;
  const slope = (my - p0.price) / (mx - p0.i || 1);
  const along = (x0, y0) => c.close.map((_, k) => (k >= x0 ? y0 + slope * (k - x0) : null));
  return { lines: [{ data: along(p0.i, p0.price), color: '#F23645', name: 'Median' }, { data: along(p1.i, p1.price), color: '#2962FF', name: 'Upper' }, { data: along(p2.i, p2.price), color: '#2962FF', name: 'Lower' }] };
};

const autoFibExt = (c, i) => {
  const z = zig(swings(c.high, c.low, i.depth || 10)).slice(-3);
  if (z.length < 3) return { levels: [] };
  const [a, b, d] = z; const move = b.price - a.price;
  const RATIOS = [[0, '#787B86'], [0.618, '#089981'], [1, '#2962FF'], [1.272, '#FF9800'], [1.618, '#F23645'], [2.618, '#9C27B0']];
  return { levels: RATIOS.map(([r, color]) => ({ price: d.price + move * r, color, label: `${r}` })) };
};

const prevHLC = (c, i) => {
  const n = c.close.length; const anchor = i.anchor || 'D';
  const H = nulls(n), L = nulls(n), C = nulls(n);
  let ph = null, pl = null, pc = null, ch = -Infinity, cl = Infinity, cc = null, key = null;
  for (let k = 0; k < n; k++) {
    const p = periodKey(c.time[k], anchor);
    if (p !== key) { if (key != null) { ph = ch; pl = cl; pc = cc; } key = p; ch = -Infinity; cl = Infinity; }
    ch = Math.max(ch, c.high[k]); cl = Math.min(cl, c.low[k]); cc = c.close[k];
    H[k] = ph; L[k] = pl; C[k] = pc;
  }
  return { lines: [{ data: H, color: '#089981', gaps: true, name: 'High' }, { data: L, color: '#F23645', gaps: true, name: 'Low' }, { data: C, color: '#2962FF', dashed: true, gaps: true, name: 'Close' }] };
};

const anchored = (c, anchor, f) => { const n = c.close.length; const out = nulls(n); let key = null, acc = null; for (let k = 0; k < n; k++) { const p = periodKey(c.time[k], anchor); if (p !== key) { key = p; acc = null; } acc = f(acc, k); out[k] = acc.v; } return out; };

const ratingLine = (c) => {
  const n = c.close.length; const out = nulls(n);
  const cs = c.close.map((cl, k) => ({ o: c.open[k], h: c.high[k], l: c.low[k], c: cl, v: c.volume[k] }));
  // The rating needs about 200 bars of history; computing it for the last 300 bars keeps it quick.
  for (let k = Math.max(60, n - 300); k < n; k++) { const r = technicalRating(cs.slice(Math.max(0, k - 220), k + 1)); out[k] = r ? r.overall : null; }
  return out;
};

const pattern = (p) => ({
  label: `${p.fa} (${p.en.replace(/ - .*/, '')})`, pane: 'main', inputs: { trend: 'SMA50' }, color: p.dir === 'bull' ? '#2962FF' : p.dir === 'bear' ? DN : '#787B86',
  calc: (c, i) => {
    const cs = c.close.map((cl, k) => ({ t: c.time[k], o: c.open[k], h: c.high[k], l: c.low[k], c: cl }));
    const at = detectPattern(p.id, cs, i.trend);
    const color = p.dir === 'bull' ? '#2962FF' : p.dir === 'bear' ? DN : '#787B86';
    return { markers: at.map((k) => ({ time: c.time[k], position: p.dir === 'bull' ? 'belowBar' : 'aboveBar', shape: p.dir === 'bull' ? 'arrowUp' : p.dir === 'bear' ? 'arrowDown' : 'circle', color, text: p.abbr })) };
  },
});


// ── Auto chart patterns (TradingView's "Auto Chart Patterns") ───────────────────────────────────
// From the alternating swing highs and lows: head and shoulders (and inverse), double and triple
// tops and bottoms, ascending, descending and symmetrical triangles, rising and falling wedges, and
// bull and bear flags. The latest three that do not overlap are drawn: their outline through the
// swings, and their neckline or the two boundary lines, carried ten bars past the last swing. The
// tolerance for "level" and "equal" is half the average true range.
export const CHART_PATTERNS = {
  hs: ['سر و شانه', 'Head and Shoulders', -1], ihs: ['سر و شانه‌ی معکوس', 'Inverse Head and Shoulders', 1],
  dt: ['سقف دوقلو', 'Double Top', -1], db: ['کف دوقلو', 'Double Bottom', 1],
  tt: ['سقف سه‌قلو', 'Triple Top', -1], tb: ['کف سه‌قلو', 'Triple Bottom', 1],
  at: ['مثلث افزایشی', 'Ascending Triangle', 1], dsc: ['مثلث کاهشی', 'Descending Triangle', -1], st: ['مثلث متقارن', 'Symmetrical Triangle', 0],
  rw: ['گوه‌ی صعودی', 'Rising Wedge', -1], fw: ['گوه‌ی نزولی', 'Falling Wedge', 1],
  bf: ['پرچم صعودی', 'Bull Flag', 1], brf: ['پرچم نزولی', 'Bear Flag', -1],
};

/** The patterns among the swings `z` (alternating), latest first: [{ kind, pts, lines: [[a, b]...] }]. */
export function findChartPatterns(z, tol, max = 3) {
  const found = [];
  const taken = (a, b) => found.some((f) => !(b < f.from || a > f.to));
  const near = (a, b) => Math.abs(a - b) <= tol;
  const lineAt = (a, b, i) => a.price + ((b.price - a.price) / ((b.i - a.i) || 1)) * (i - a.i);
  for (let j = z.length - 1; j >= 2 && found.length < max; j--) {
    let hit = null;
    if (j >= 4) {
      const [p0, p1, p2, p3, p4] = z.slice(j - 4, j + 1);
      const top = p0.high; const s = top ? 1 : -1;
      // Head and shoulders: the middle swing beyond both shoulders, the shoulders level, the neck under them.
      if (s * (p2.price - p0.price) > tol && s * (p2.price - p4.price) > tol && near(p0.price, p4.price) && s * (p0.price - Math.max(s * p1.price, s * p3.price) * s) > 0)
        hit = { kind: top ? 'hs' : 'ihs', pts: [p0, p1, p2, p3, p4], lines: [[p1, p3]] };
      else if (near(p0.price, p2.price) && near(p2.price, p4.price) && Math.abs(p1.price - p3.price) <= 2 * tol && s * (Math.min(s * p0.price, s * p2.price, s * p4.price) * s - Math.max(s * p1.price, s * p3.price) * s) > 2 * tol)
        hit = { kind: top ? 'tt' : 'tb', pts: [p0, p1, p2, p3, p4], lines: [[p1, p3]] };
      else {
        // Two boundaries: the outer swings (p0, p2, p4) and the inner ones (p1, p3).
        const [hiA, hiB, loA, loB] = top ? [p0, p4, p1, p3] : [p1, p3, p0, p4];
        const okMid = Math.abs(p2.price - (top ? lineAt(hiA, hiB, p2.i) : lineAt(loA, loB, p2.i))) <= tol * 1.5;
        const ru = hiB.price - hiA.price, rl = loB.price - loA.price;
        const w0 = lineAt(hiA, hiB, p0.i) - lineAt(loA, loB, p0.i), w1 = lineAt(hiA, hiB, p4.i) - lineAt(loA, loB, p4.i);
        if (okMid && w0 > 0 && w1 > 0) {
          const conv = w1 < w0 * 0.8;
          const flatU = Math.abs(ru) <= tol, flatL = Math.abs(rl) <= tol;
          let kind = null;
          if (conv && flatU && rl > tol) kind = 'at';
          else if (conv && flatL && ru < -tol) kind = 'dsc';
          else if (conv && ru < -tol && rl > tol) kind = 'st';
          else if (conv && ru > tol && rl > ru) kind = 'rw';
          else if (conv && rl < -tol && ru < rl) kind = 'fw';
          else if (!conv && Math.abs(w1 - w0) <= w0 * 0.35 && j >= 5) {
            // A flag: a pole at least three channel-widths long into the first swing, then a channel against it.
            const pole = z[j - 5];
            const len = p0.price - pole.price;
            if (Math.abs(len) > 3 * w0 && Math.sign(len) * Math.sign(ru + rl) < 0) kind = len > 0 ? 'bf' : 'brf';
          }
          if (kind) hit = { kind, pts: [p0, p1, p2, p3, p4], lines: [[hiA, hiB], [loA, loB]] };
        }
      }
      if (hit && taken(p0.i, p4.i)) hit = null;
      if (hit) { hit.from = p0.i; hit.to = p4.i; }
    }
    if (!hit) {
      const [p0, p1, p2] = z.slice(j - 2, j + 1);
      const s = p0.high ? 1 : -1;
      if (near(p0.price, p2.price) && s * (Math.min(s * p0.price, s * p2.price) * s - p1.price) > 3 * tol && !taken(p0.i, p2.i))
        hit = { kind: p0.high ? 'dt' : 'db', pts: [p0, p1, p2], lines: [[{ i: p0.i, price: p1.price }, { i: p2.i, price: p1.price }]], from: p0.i, to: p2.i };
    }
    if (hit) { found.push(hit); j -= hit.pts.length - 2; }
  }
  return found;
}

const autoChartPatterns = (c, i) => {
  const n = c.close.length;
  const SLOTS = 3;
  const empty = () => ({ lines: Array.from({ length: SLOTS * 3 }, (_, k) => ({ data: nulls(n), color: k % 3 ? '#FF9800' : '#2962FF', dashed: k % 3 !== 0, connectNulls: true, name: `${['Pattern', 'Line A', 'Line B'][k % 3]} ${Math.floor(k / 3) + 1}` })), markers: [] });
  if (n < 30) return empty();
  let tr = 0; for (let k = 1; k < n; k++) tr += Math.max(c.high[k] - c.low[k], Math.abs(c.high[k] - c.close[k - 1]), Math.abs(c.low[k] - c.close[k - 1]));
  const tol = (tr / (n - 1)) * 0.5;
  const z = zig(swings(c.high, c.low, Math.max(2, i.depth | 0 || 5)));
  const out = empty();
  findChartPatterns(z, tol, SLOTS).forEach((p, k) => {
    const outline = out.lines[k * 3].data;
    for (const q of p.pts) outline[q.i] = q.price;
    p.lines.forEach(([a, b], m) => {
      const d = out.lines[k * 3 + 1 + m].data;
      const end = Math.min(n - 1, b.i + 10);
      const slope = (b.price - a.price) / ((b.i - a.i) || 1);
      d[a.i] = a.price; d[end] = a.price + slope * (end - a.i);
    });
    const [fa, , dir] = CHART_PATTERNS[p.kind];
    const last = p.pts[p.pts.length - 1];
    out.markers.push({ time: c.time[last.i], position: dir > 0 ? 'belowBar' : 'aboveBar', shape: dir > 0 ? 'arrowUp' : dir < 0 ? 'arrowDown' : 'circle', color: dir > 0 ? UP : dir < 0 ? DN : '#2962FF', text: fa });
  });
  out.markers.sort((a, b) => a.time - b.time);
  return out;
};

export const EXT_REGISTRY_C = {
  chopZone: { label: 'منطقه‌ی چاپ (Chop Zone)', pane: 'sub', inputs: {}, color: '#FDD835', calc: chopZone },
  correlation: { label: 'ضریب همبستگی (Correlation Coefficient)', pane: 'sub', inputs: { symbol: 'ETHUSDT', length: 20 }, color: '#2962FF', other: true, calc: (c, i) => ({ line: correlation(c.close, i.__other ?? nulls(c.close.length), i.length), guides: [0], range: [-1, 1] }) },
  ratio: { label: 'نسبت (Ratio)', pane: 'sub', inputs: { symbol: 'ETHUSDT' }, color: '#2962FF', other: true, calc: (c, i) => ({ line: c.close.map((v, k) => (i.__other?.[k] ? v / i.__other[k] : null)) }) },
  spread: { label: 'اسپرد دو نماد (Spread)', pane: 'sub', inputs: { symbol: 'ETHUSDT' }, color: '#2962FF', other: true, calc: (c, i) => ({ line: c.close.map((v, k) => (i.__other?.[k] != null ? v - i.__other[k] : null)), guides: [0] }) },
  volumeDelta: { label: 'دلتای حجم (Volume Delta)', pane: 'sub', inputs: {}, color: UP, calc: (c) => (hasVolume(c.volume) ? { hist: delta(c), guides: [0] } : { hist: nulls(c.close.length) }) },
  cvd: { label: 'دلتای حجم تجمعی (CVD)', pane: 'sub', inputs: { anchor: 'D' }, color: '#2962FF', calc: (c, i) => { if (!hasVolume(c.volume)) return { line: nulls(c.close.length) }; const d = delta(c); return { line: anchored(c, i.anchor, (a, k) => ({ v: (a?.v ?? 0) + d[k] })), guides: [0] }; } },
  upDownVolume: { label: 'حجم صعودی و نزولی (Up/Down Volume)', pane: 'sub', inputs: {}, color: UP, calc: (c) => {
    if (!hasVolume(c.volume)) return { hist: nulls(c.close.length) };
    const up = c.close.map((cl, k) => (c.volume[k] || 0) * (c.high[k] > c.low[k] ? (cl - c.low[k]) / (c.high[k] - c.low[k]) : 0.5));
    const dn = c.close.map((cl, k) => -((c.volume[k] || 0) - up[k]));
    return { hists: [{ data: up, color: UP }, { data: dn, color: DN }], lines: [{ data: up.map((u, k) => u + dn[k]), color: '#2962FF', name: 'Delta' }], guides: [0] };
  } },
  rollingVwap: { label: 'VWAP غلتان (Rolling VWAP)', pane: 'main', inputs: { window: 50, mult: 1 }, color: '#2962FF', calc: (c, i) => {
    if (!hasVolume(c.volume)) return { line: nulls(c.close.length) };
    const tp = c.close.map((cl, k) => (c.high[k] + c.low[k] + cl) / 3);
    const pv = rollSum(tp.map((x, k) => x * (c.volume[k] || 0)), i.window), vv = rollSum(c.volume, i.window);
    const vw = pv.map((x, k) => (x != null && vv[k] ? x / vv[k] : null));
    const dev = sd(tp, i.window);
    return { lines: [{ data: vw, color: '#2962FF', name: 'VWAP' }, { data: vw.map((x, k) => (x != null && dev[k] != null ? x + i.mult * dev[k] : null)), color: '#089981', name: 'Upper' }, { data: vw.map((x, k) => (x != null && dev[k] != null ? x - i.mult * dev[k] : null)), color: '#089981', name: 'Lower' }], cloud: [1, 2], cloudColors: ['rgba(8,153,129,0.06)'] };
  } },
  twap: { label: 'میانگین وزنی زمانی (TWAP)', pane: 'main', inputs: { anchor: 'D', source: 'ohlc4' }, color: '#FF6D00', calc: (c, i) => { const s = resolveSrc(c, i.source); return { line: anchored(c, i.anchor, (a, k) => { const sum = (a?.sum ?? 0) + s[k], n = (a?.n ?? 0) + 1; return { sum, n, v: sum / n }; }) }; } },
  visibleAvgPrice: { label: 'میانگین قیمت ناحیه‌ی دیده‌شده (Visible Average Price)', pane: 'main', inputs: { source: 'close' }, color: '#FF6D00', visibleRange: true, calc: (c, i) => {
    const n = c.close.length; const a = Math.max(0, i.__from ?? 0), b = Math.min(n - 1, i.__to ?? n - 1); const s = resolveSrc(c, i.source);
    let sum = 0, k = 0; for (let x = a; x <= b; x++) { sum += s[x]; k++; }
    const avg = k ? sum / k : null; return { line: c.close.map((_, x) => (x >= a && x <= b ? avg : null)) };
  } },
  relVolAtTime: { label: 'حجم نسبی در زمان (Relative Volume at Time)', pane: 'sub', inputs: { length: 10, anchor: 'D' }, color: '#2962FF', calc: (c, i) => {
    const n = c.close.length; if (!hasVolume(c.volume)) return { hist: nulls(n) };
    const cum = nulls(n); const offset = nulls(n); const sessions = [];
    let key = null, acc = 0, start = 0;
    for (let k = 0; k < n; k++) { const p = periodKey(c.time[k], i.anchor); if (p !== key) { key = p; acc = 0; start = k; sessions.push(new Map()); } acc += c.volume[k] || 0; cum[k] = acc; offset[k] = k - start; sessions[sessions.length - 1].set(k - start, acc); }
    const out = nulls(n); let s = -1; key = null;
    for (let k = 0; k < n; k++) {
      const p = periodKey(c.time[k], i.anchor); if (p !== key) { key = p; s++; }
      const past = sessions.slice(Math.max(0, s - i.length), s).map((m) => m.get(offset[k])).filter((v) => v != null);
      if (past.length) { const avg = past.reduce((x, y) => x + y, 0) / past.length; out[k] = avg ? cum[k] / avg : null; }
    }
    return { hist: out, palette: (v) => ((v ?? 0) >= 1 ? UP : '#787B86'), guides: [1] };
  } },
  rci: { label: 'شاخص همبستگی رتبه‌ای (RCI)', pane: 'sub', inputs: { length: 10, source: 'close' }, color: '#2962FF', calc: (c, i) => ({ line: rci(resolveSrc(c, i.source), i.length), guides: [80, -80], range: [-100, 100] }) },
  trendStrength: { label: 'شاخص قدرت روند (Trend Strength Index)', pane: 'sub', inputs: { length: 14, source: 'close' }, color: '#2962FF', calc: (c, i) => ({ line: correlation(resolveSrc(c, i.source), c.close.map((_, k) => k), i.length), guides: [0], range: [-1, 1] }) },
  bbTrend: { label: 'روند بولینگر (BBTrend)', pane: 'sub', inputs: { short: 20, long: 50, mult: 2 }, color: UP, calc: (c, i) => {
    const s = bollinger(c.close, i.short, i.mult), l = bollinger(c.close, i.long, i.mult);
    const v = c.close.map((_, k) => (s.lower[k] != null && l.lower[k] != null && s.basis[k] ? (Math.abs(s.lower[k] - l.lower[k]) - Math.abs(s.upper[k] - l.upper[k])) / s.basis[k] * 100 : null));
    return { hist: v, palette: (x, k) => { const prev = k > 0 ? v[k - 1] : null; const rising = prev == null || (x ?? 0) >= prev; return (x ?? 0) >= 0 ? (rising ? '#089981' : '#ACE5DC') : (rising ? '#FCCBCD' : '#F23645'); }, guides: [0] };
  } },
  bollingerBars: { label: 'کندل‌های بولینگر (Bollinger Bars)', pane: 'main', inputs: { period: 20, mult: 2 }, color: '#2962FF', calc: (c, i) => {
    const b = bollinger(c.close, i.period, i.mult);
    return { candles: c.close.map((cl, k) => {
      const up = b.upper[k], lo = b.lower[k];
      const pb = up != null && lo != null && up > lo ? (cl - lo) / (up - lo) : 0.5;
      const color = pb > 1 ? '#089981' : pb >= 0.5 ? '#ACE5DC' : pb >= 0 ? '#FCCBCD' : '#F23645';
      return { time: c.time[k], open: c.open[k], high: c.high[k], low: c.low[k], close: cl, color };
    }) };
  } },
  smiErgodicOsc: { label: 'نوسان‌گر ارگودیک SMI', pane: 'sub', inputs: { long: 20, short: 5, sig: 5 }, color: UP, calc: (c, i) => {
    const m = c.close.map((x, k) => (k ? x - c.close[k - 1] : 0)); const am = m.map(Math.abs);
    const a = ema(ema(m, i.long), i.short), b = ema(ema(am, i.long), i.short);
    const smi = a.map((x, k) => (x != null && b[k] ? x / b[k] : null)); const sig = ema(smi.map((x) => x ?? 0), i.sig);
    return { hist: smi.map((x, k) => (x != null && sig[k] != null ? x - sig[k] : null)), guides: [0] };
  } },
  bullBearPower: { label: 'قدرت گاو و خرس (Bull Bear Power)', pane: 'sub', inputs: { length: 13 }, color: UP, calc: (c, i) => { const e = ema(c.close, i.length); return { hist: c.close.map((_, k) => (e[k] == null ? null : (c.high[k] - e[k]) + (c.low[k] - e[k]))), guides: [0] }; } },
  vol24h: { label: 'حجم ۲۴ ساعته (24-hour Volume)', pane: 'sub', inputs: {}, color: '#2962FF', calc: (c) => {
    const n = c.close.length; const out = nulls(n); let a = 0, s = 0;
    for (let k = 0; k < n; k++) { s += c.volume[k] || 0; while (c.time[a] <= c.time[k] - 86400) { s -= c.volume[a] || 0; a++; } out[k] = hasVolume(c.volume) ? s : null; }
    return { line: out };
  } },
  techRatings: { label: 'رتبه‌بندی تکنیکال (Technical Ratings)', pane: 'sub', inputs: {}, color: '#2962FF', calc: (c) => { const r = ratingLine(c); return { hist: r, palette: (v) => ((v ?? 0) > 0.5 ? '#2962FF' : (v ?? 0) > 0.1 ? '#90BFF9' : (v ?? 0) < -0.5 ? '#F23645' : (v ?? 0) < -0.1 ? '#FAA1A4' : '#B2B5BE'), guides: [0.5, 0.1, -0.1, -0.5], range: [-1, 1] }; } },
  autoFibExt: { label: 'گسترش فیبوناچی خودکار (Auto Fib Extension)', pane: 'main', inputs: { depth: 10 }, color: '#2962FF', calc: autoFibExt },
  autoTrendlines: { label: 'خطوط روند خودکار (Auto Trendlines)', pane: 'main', inputs: { depth: 10 }, color: '#2962FF', calc: autoTrend },
  autoPitchfork: { label: 'چنگال خودکار (Auto Pitchfork)', pane: 'main', inputs: { depth: 10 }, color: '#F23645', calc: autoPitchfork },
  autoChartPatterns: { label: 'الگوهای کلاسیک خودکار (Auto Chart Patterns)', pane: 'main', inputs: { depth: 5 }, color: '#2962FF', calc: autoChartPatterns },
  prevHLC: { label: 'سقف، کف و بسته‌ی دوره‌ی قبل (Previous High/Low/Close)', pane: 'main', inputs: { anchor: 'D' }, color: '#2962FF', calc: prevHLC },
  performance: { label: 'بازدهی (Performance)', pane: 'main', inputs: {}, color: '#2962FF', calc: tables.performance },
  seasonality: { label: 'فصلی بودن (Seasonality)', pane: 'main', inputs: {}, color: '#2962FF', calc: tables.seasonality },
  moonPhases: { label: 'فازهای ماه (Moon Phases)', pane: 'main', inputs: {}, color: '#FFB300', calc: moon },
  // Profiles.
  vpVisible: { label: 'پروفایل حجم ناحیه‌ی دیده‌شده (VPVR)', pane: 'main', inputs: { rows: 24, valueArea: 70 }, color: '#2196F3', visibleRange: true, calc: (c, i) => { const n = c.close.length; const a = Math.max(0, i.__from ?? 0), b = Math.min(n - 1, i.__to ?? n - 1); const p = b > a ? profileRows(c, a, b, i.rows, i.valueArea) : null; return { profile: { segments: p ? [{ from: c.time[a], to: c.time[b], ...p }] : [], place: 'right' } }; } },
  vpSession: { label: 'پروفایل حجم جلسه (Session Volume Profile)', pane: 'main', inputs: { rows: 24, valueArea: 70 }, color: '#2196F3', calc: vpPeriodic('D') },
  vpPeriodic: { label: 'پروفایل حجم دوره‌ای (Periodic Volume Profile)', pane: 'main', inputs: { anchor: 'W', rows: 24, valueArea: 70 }, color: '#2196F3', calc: (c, i) => vpPeriodic(i.anchor || 'W')(c, i) },
  vpFixed: { label: 'پروفایل حجم بازه‌ی ثابت (Fixed Range Volume Profile)', pane: 'main', inputs: { bars: 150, end: 0, rows: 24, valueArea: 70 }, color: '#2196F3', calc: (c, i) => { const n = c.close.length; const b = Math.max(0, n - 1 - (i.end | 0)); const a = Math.max(0, b - Math.max(2, i.bars | 0) + 1); const p = b > a ? profileRows(c, a, b, i.rows, i.valueArea) : null; return { profile: { segments: p ? [{ from: c.time[a], to: c.time[b], ...p }] : [], place: 'inside' } }; } },
  vpAnchored: { label: 'پروفایل حجم لنگری (Anchored Volume Profile)', pane: 'main', inputs: { anchorBars: 100, rows: 24, valueArea: 70 }, color: '#2196F3', calc: (c, i) => { const n = c.close.length; const a = Math.max(0, n - Math.max(2, i.anchorBars | 0)); const p = profileRows(c, a, n - 1, i.rows, i.valueArea); return { profile: { segments: p ? [{ from: c.time[a], to: c.time[n - 1], ...p }] : [], place: 'inside' } }; } },
  // Candlestick patterns, one each (keys cp_<id>).
  ...Object.fromEntries(TV_PATTERNS.map((p) => [`cp_${p.id}`, pattern(p)])),
};

export const STUDIES = { ...REGISTRY, ...EXT_REGISTRY_C };
