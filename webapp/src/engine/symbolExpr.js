// symbolExpr — تجزیه و ارزیابیِ عبارت‌های نمادِ ریاضی/اسپرد (سبکِ TradingView).
//   مثال‌ها: "EURUSD/GBPUSD" (نسبت)، "XAUUSD*2"، "US30-US500"، "(BTCUSDT+ETHUSDT)/2".
//   عملگرها: + − * / و پرانتز و منفیِ یک‌جمله‌ای؛ نمادها [A-Za-z][A-Za-z0-9]*، اعداد اعشاری.
// خالص و بدونِ importِ خارجی ⇒ مستقیماً در Node قابلِ تست (test/symbolExpr.test.mjs).

const tokenize = (s) => {
  const toks = []; let i = 0;
  while (i < s.length) {
    const ch = s[i];
    if (ch === ' ') { i++; continue; }
    if ('+-*/()'.includes(ch)) { toks.push({ t: ch }); i++; continue; }
    if (/[0-9.]/.test(ch)) { let j = i; while (j < s.length && /[0-9.]/.test(s[j])) j++; const v = parseFloat(s.slice(i, j)); if (!Number.isFinite(v)) return null; toks.push({ t: 'num', v }); i = j; continue; }
    if (/[A-Za-z]/.test(ch)) { let j = i; while (j < s.length && /[A-Za-z0-9]/.test(s[j])) j++; toks.push({ t: 'sym', v: s.slice(i, j).toUpperCase() }); i = j; continue; }
    return null; // کاراکترِ نامعتبر
  }
  return toks;
};

const parse = (toks) => {
  let pos = 0;
  const peek = () => toks[pos];
  const next = () => toks[pos++];
  const parseExpr = () => {
    let node = parseTerm(); if (!node) return null;
    while (peek() && (peek().t === '+' || peek().t === '-')) { const op = next().t; const right = parseTerm(); if (!right) return null; node = { op, left: node, right }; }
    return node;
  };
  const parseTerm = () => {
    let node = parseFactor(); if (!node) return null;
    while (peek() && (peek().t === '*' || peek().t === '/')) { const op = next().t; const right = parseFactor(); if (!right) return null; node = { op, left: node, right }; }
    return node;
  };
  const parseFactor = () => {
    const tk = peek(); if (!tk) return null;
    if (tk.t === 'num') { next(); return { num: tk.v }; }
    if (tk.t === 'sym') { next(); return { sym: tk.v }; }
    if (tk.t === '(') { next(); const e = parseExpr(); if (!e || !peek() || peek().t !== ')') return null; next(); return e; }
    if (tk.t === '-') { next(); const f = parseFactor(); if (!f) return null; return { op: '-', left: { num: 0 }, right: f }; }
    return null;
  };
  const ast = parseExpr();
  if (!ast || pos !== toks.length) return null; // توکنِ اضافی = نامعتبر
  return ast;
};

const collectSyms = (node, set) => {
  set = set || new Set();
  if (!node) return set;
  if (node.sym) set.add(node.sym);
  if (node.left) collectSyms(node.left, set);
  if (node.right) collectSyms(node.right, set);
  return set;
};
const hasOp = (node) => !!(node && (node.op || (node.left && hasOp(node.left)) || (node.right && hasOp(node.right))));

// ارزیابیِ ASTِ با مقادیرِ عددیِ هر نماد؛ null اگر مقداری غایب/نامتناهی یا تقسیم‌بر‌صفر.
export const evalAst = (node, bySym) => {
  if (!node) return null;
  if (node.num != null) return node.num;
  if (node.sym) { const v = bySym[node.sym]; return (v == null || !Number.isFinite(v)) ? null : v; }
  const l = evalAst(node.left, bySym), r = evalAst(node.right, bySym);
  if (l == null || r == null) return null;
  let out;
  switch (node.op) {
    case '+': out = l + r; break;
    case '-': out = l - r; break;
    case '*': out = l * r; break;
    case '/': if (r === 0) return null; out = l / r; break;
    default: return null;
  }
  return Number.isFinite(out) ? out : null;
};

// تجزیهٔ رشته → { isExpr, symbols, ast, eval } یا null (نامعتبر).
//   isExpr=false ⇒ نمادِ سادهٔ تک (اسپرد نیست؛ مسیرِ عادیِ نماد را برو).
export const parseSymbolExpr = (str) => {
  if (!str || typeof str !== 'string') return null;
  const toks = tokenize(str.trim());
  if (!toks || !toks.length) return null;
  const ast = parse(toks);
  if (!ast) return null;
  const symbols = [...collectSyms(ast)];
  return { isExpr: hasOp(ast), symbols, ast, eval: (bySym) => evalAst(ast, bySym) };
};

// محاسبهٔ کندل‌های اسپرد از کندل‌های هر نماد (هم‌ترازی بر اساسِ زمانِ مشترک).
//   candlesBySym: { SYM: [{t,o,h,l,c,v}] }. عبارت روی هر یک از o/h/l/c جدا ارزیابی می‌شود؛
//   high/low = بیشینه/کمینهٔ چهار گوشهٔ محاسبه‌شده تا همواره high≥low (نسبت می‌تواند O/H/L/C را وارونه کند).
export const computeSpread = (candlesBySym, ast) => {
  const syms = [...collectSyms(ast)];
  if (!syms.length) return [];
  const maps = {};
  for (const s of syms) { const m = new Map(); for (const c of (candlesBySym[s] || [])) m.set(c.t, c); maps[s] = m; }
  let times = [...maps[syms[0]].keys()];
  for (let k = 1; k < syms.length; k++) { const mk = maps[syms[k]]; times = times.filter((t) => mk.has(t)); }
  times.sort((a, b) => a - b);
  const out = [];
  for (const t of times) {
    const field = (f) => { const by = {}; for (const s of syms) by[s] = maps[s].get(t)[f]; return evalAst(ast, by); };
    const o = field('o'), h = field('h'), l = field('l'), c = field('c');
    if (o == null || c == null) continue;
    const corners = [o, c, h, l].filter((v) => v != null && Number.isFinite(v));
    if (!corners.length) continue;
    out.push({ t, o, h: Math.max(...corners), l: Math.min(...corners), c, v: 0 });
  }
  return out;
};

export default parseSymbolExpr;
