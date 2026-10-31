// «Bring your own script» — what happens to whatever a reader pastes (the phone's ScriptPaste.kt).
//
// Three different things arrive and the reader is never asked which one they have: NamaScript (what
// an assistant given the prompt kit writes, usually with one or two small, repeated mistakes), Pine
// (what an assistant asked for «a TradingView indicator» writes), and prose («buy when EMA 20 crosses
// EMA 50»), which becomes a template picker.
//
// The fixes are a table of textual substitutions, each one always right in this engine's language:
// a substitution that could change a correct script's meaning does not belong here (that needs
// judgement, which belongs to the error message). They are applied before the sheet opens, listed
// with the lines they touched, and undone in one tap. What Pine carries that this engine has not is
// left as a comment and named — never silently dropped.
import { matchingTemplates, numbersIn, type ScriptTemplate } from './templates';

export type Dialect = 'nama' | 'pine' | 'prose';
export interface Fix { id: string; what: string; whatEn: string; lines: number[] }
export interface Unsupported { line: number; what: string; whatEn: string; source: string }
export interface Paste {
  dialect: Dialect;
  /** The text as pasted, for the undo. */
  raw: string;
  /** The text after every fix (equal to `raw` when nothing applied). */
  fixed: string;
  fixes: Fix[];
  unsupported: Unsupported[];
  /** For prose: the templates that answer it, best first, and the numbers it named. */
  templates: ScriptTemplate[];
  numbers: number[];
}

const MASK = '\u0001';

/**
 * A line with everything inside a string literal or after a `//` blanked out, length for length, so a
 * rule never rewrites a reader's own words («۳ کندل» in a title) or a remark.
 */
export function masked(line: string, blindToComments = true): string {
  if (!/["']/.test(line) && !(blindToComments && line.includes('//'))) return line;
  let out = '';
  let quote: string | null = null;
  for (let i = 0; i < line.length; i++) {
    const c = line[i]!;
    if (quote) {
      if (c === '\\' && i + 1 < line.length) { out += MASK + MASK; i++; continue; }
      if (c === quote) { quote = null; out += c; } else out += MASK;
    } else if (c === '"' || c === "'") { quote = c; out += c; }
    else if (blindToComments && c === '/' && line[i + 1] === '/') { out += MASK.repeat(line.length - i); break; }
    else out += c;
  }
  return out;
}

/** A pattern replaced in a line where it matched the blanked view, with the real text's groups. */
function rewriteOutsideText(line: string, re: RegExp, to: (m: RegExpExecArray) => string, blind: boolean): string {
  const view = masked(line, blind);
  const g = new RegExp(re.source, re.flags.includes('g') ? re.flags : `${re.flags}g`);
  const allowed = new Set<number>();
  for (let m = g.exec(view); m; m = g.exec(view)) { allowed.add(m.index); if (!m[0].length) g.lastIndex++; }
  if (!allowed.size) return line;
  let out = '';
  let cursor = 0;
  g.lastIndex = 0;
  for (let m = g.exec(line); m; m = g.exec(line)) {
    if (!m[0].length) { g.lastIndex++; continue; }
    if (m.index < cursor || !allowed.has(m.index)) continue;
    out += line.slice(cursor, m.index) + to(m);
    cursor = m.index + m[0].length;
  }
  return out + line.slice(cursor);
}

interface Rule { id: string; what: string; whatEn: string; lines(src: string): number[]; apply(src: string): string }

function lineRule(id: string, what: string, whatEn: string, re: RegExp, to: (m: RegExpExecArray) => string, blind = true): Rule {
  const test = new RegExp(re.source, re.flags.replace('g', ''));
  return {
    id, what, whatEn,
    lines: (src) => src.split('\n').flatMap((l, i) => (test.test(masked(l, blind)) ? [i + 1] : [])),
    apply: (src) => src.split('\n').map((l) => rewriteOutsideText(l, re, to, blind)).join('\n'),
  };
}

const COLOUR_NAMES: Record<string, string> = { grey: 'gray', gold: 'orange', buy: 'green', sell: 'red' };
const DRAW_CALLS = /\b(plot|plotshape|plotchar|plotarrow|plotcandle|plotbar|hline|bgcolor|barcolor|fill|riskreward|alertcondition|alert|label\.new|line\.new|box\.new|table\.new|strategy\.\w+)\s*\(/;
const ASSIGNMENT = /^(\s*)([A-Za-z_]\w*)\s*:?=\s*\S/gm;

const RULES: Rule[] = [
  // First, so the quotes it straightens become string delimiters before any other rule looks inside them.
  lineRule('smart-quotes', 'گیومه‌های تایپوگرافیک به گیومه‌ی ساده تبدیل شدند.', 'Typographic quotation marks became plain ones.', /[“”«»]/g, () => '"'),
  lineRule('persian-digits', 'رقم‌های فارسی داخل کد به لاتین تبدیل شدند.', 'Persian digits inside the code became Latin.', /[۰-۹]+/g,
    (m) => m[0].replace(/[۰-۹]/g, (d) => String(d.charCodeAt(0) - 0x06f0))),
  lineRule('typed-declaration', 'نوع متغیر (float، int، …) حذف شد؛ در نمااسکریپت متغیر نوع ندارد.', 'Dropped the variable’s type (float, int, …); variables carry none here.',
    /^(\s*(?:var\s+|varip\s+)?)(?:float|int|bool|string|color)\s+([A-Za-z_]\w*)\s*(:?=)/g, (m) => `${m[1]}${m[2]} ${m[3]}`),
  lineRule('bare-ta-names', 'نام‌های بدون پیشوند (sma، ema، rsi، …) با «ta.» نوشته شدند.', 'Bare names (sma, ema, rsi, …) were given their “ta.” prefix.',
    /(?<![\w.])(sma|ema|wma|rma|hma|rsi|atr|macd|stdev|highest|lowest|cci|mfi|vwma|change)\s*\(/g, (m) => `ta.${m[1]}(`),
  lineRule('na-comparison', '«== na» به na(...) تبدیل شد؛ مقایسه با غیبت هیچ‌وقت تصمیم نمی‌گیرد.', '“== na” became na(...); a comparison with absence never decides.',
    /([A-Za-z_][\w.]*(?:\[\d+\])?)\s*(==|!=)\s*na\b(?!\s*\()/g, (m) => (m[2] === '==' ? `na(${m[1]})` : `iff(na(${m[1]}), false, true)`)),
  lineRule('colour-spelling', '«colour» به «color» تبدیل شد؛ نام آرگومان در این زبان آمریکایی است.', '“colour” became “color”; the argument’s name is American here.', /\bcolour\b/g, () => 'color'),
  lineRule('colour-names', 'رنگ‌هایی که این زبان ندارد (grey، gold، …) به نزدیک‌ترین رنگ خودش تبدیل شدند.', 'Colours this language does not name (grey, gold, …) became its nearest own.',
    /\bcolor\.(grey|gold|buy|sell)\b/g, (m) => `color.${COLOUR_NAMES[m[1]!]}`),
  lineRule('close-index', 'close(1) به close[1] تبدیل شد.', 'close(1) became close[1].',
    /\b(open|high|low|close|volume|hlc3|ohlc4|hl2|hlcc4)\s*\(\s*(\d+)\s*\)/g, (m) => `${m[1]}[${m[2]}]`),
  lineRule('pine-constants', 'ثابت‌های location.* و size.* پاین حذف شدند؛ این زبان جای نشانه را خودش تعیین می‌کند.', 'Dropped Pine’s location.* and size.* constants; the mark’s place is worked out here.',
    /\s*,\s*(?:(?:location|size|xloc|yloc|extend|display)\s*=\s*)?(?:location|size|xloc|yloc|extend|display)\.\w+/g, () => ''),
  {
    id: 'add-plot', what: 'یک plot اضافه شد؛ اسکریپتی که چیزی نمی‌کشد روی چارت دیده نمی‌شود.', whatEn: 'Added a plot; a script that draws nothing is invisible on the chart.',
    lines: (src) => {
      const view = src.split('\n').map((l) => masked(l)).join('\n');
      const names = [...view.matchAll(ASSIGNMENT)];
      return DRAW_CALLS.test(view) || !names.length ? [] : [src.split('\n').length];
    },
    apply: (src) => {
      const view = src.split('\n').map((l) => masked(l)).join('\n');
      const last = [...view.matchAll(ASSIGNMENT)].at(-1)![2];
      return `${src.trimEnd()}\nplot(${last})\n`;
    },
  },
];

/** Every repair applied in table order (each is idempotent), with what it did and where. */
export function repair(source: string): { fixed: string; fixes: Fix[] } {
  let text = source;
  const fixes: Fix[] = [];
  for (const r of RULES) {
    const hit = r.lines(text);
    if (!hit.length) continue;
    text = r.apply(text);
    fixes.push({ id: r.id, what: r.what, whatEn: r.whatEn, lines: hit });
  }
  return { fixed: text, fixes };
}

export const FIX_IDS = RULES.map((r) => r.id);

// ── What it is ─────────────────────────────────────────────────────────────────────────────────

/** Pine's alone: things this engine's NamaScript never writes. */
const PINE_MARKERS: RegExp[] = [
  /^\s*\/\/@version\s*=/m,
  /^\s*(?:var\s+|varip\s+)?(?:float|int|bool|string|color)\s+[A-Za-z_]\w*\s*:?=/m,
  /\b(?:minval|maxval|linewidth)\s*=/,
  /\b(?:location|size|xloc|syminfo|timeframe)\.\w+/,
  /^\s*[A-Za-z_]\w*\s*\([^)]*\)\s*=>/m,
  /^\s*(?:if|while)\s+(?!\()\S/m,
  /^\s*for\s+\w+\s*=.*\bto\b/m,
];
/** Infix and/or/not — Pine's; this engine spells them and(a, b), or(a, b). */
const INFIX_LOGIC = /\b(?:and|or)\b(?!\s*\()|\bnot\s+(?!\()/;

const CODE_MARKERS: RegExp[] = [
  /^\s*\w+\s*:?=\s*\S/m,
  /\b(?:plot|plotshape|plotchar|hline|bgcolor|fill|alertcondition|riskreward|indicator|study|strategy(?:\.\w+)?|label\.new)\s*\(/,
  /\bta\.\w+\s*\(/,
  /\binput(?:\.\w+)?\s*\(/,
];

export function dialectOf(text: string): Dialect {
  const t = text.trim();
  if (!t) return 'prose';
  const view = t.split('\n').map((l) => masked(l, false)).join('\n');
  const code = t.split('\n').map((l) => masked(l)).join('\n');
  if (/^\s*\/\/@version\s*=/m.test(view) || PINE_MARKERS.slice(1).some((r) => r.test(code))) return 'pine';
  if (!CODE_MARKERS.some((r) => r.test(code))) return 'prose';
  return INFIX_LOGIC.test(code) ? 'pine' : 'nama';
}

/** The Pine lines this engine cannot run, as comments, and why. */
const BLOCKED: [RegExp, string, string][] = [
  [/^\s*(?:if|else|for|while|switch)\b(?!.*\{\s*$)/, 'کنترل جریان در نمااسکریپت نیست؛ با شرط یا iff(...) بنویسید', 'control flow is not in NamaScript − rewrite as a condition or iff(...)'],
  [/^\s*[A-Za-z_]\w*\s*\([^)]*\)\s*=>/, 'تابع کاربر در نمااسکریپت نیست', 'user functions are not in NamaScript'],
  [/\b(?:array|matrix|map)\.\w+/, 'آرایه و ماتریس در نمااسکریپت نیست', 'collections are not in NamaScript'],
  [/\b(?:syminfo|timeframe|session)\.\w+/, 'این فضای نام در نمااسکریپت نیست', 'this namespace is not in NamaScript'],
  [INFIX_LOGIC, 'and/or/not میان سری‌ها در این زبان and(a, b)، or(a, b) و iff(c, false, true) است', 'and/or/not between series are and(a, b), or(a, b) and iff(c, false, true) here'],
];

function translatePine(src: string): { source: string; unsupported: Unsupported[] } {
  const unsupported: Unsupported[] = [];
  let blockIndent = -1;
  const out = src.split('\n').map((raw, i) => {
    const line = raw.replace(/\s+$/, '');
    const indent = /^\s*/.exec(line)![0].length;
    const code = masked(line);
    if (!code.trim()) return line;
    // The body of a blocked block goes with it.
    if (blockIndent >= 0 && indent > blockIndent) {
      unsupported.push({ line: i + 1, what: 'بخشی از بلوک بالا', whatEn: 'part of the block above', source: line.trim() });
      return `// [نمااسکریپت] ${line.trim()}`;
    }
    blockIndent = -1;
    const hit = BLOCKED.find(([re]) => re.test(code));
    if (!hit) return line;
    if (/^\s*(?:if|else|for|while|switch)\b/.test(code) || /=>\s*$/.test(code)) blockIndent = indent;
    unsupported.push({ line: i + 1, what: hit[1], whatEn: hit[2], source: line.trim() });
    return `// [نمااسکریپت: ${hit[2]}] ${line.trim()}`;
  });
  return { source: `${out.join('\n').trimEnd()}\n`, unsupported };
}

/** Reads a paste: what it is, repaired as far as it can be without judgement, and what is left. Nothing runs. */
export function readPaste(text: string): Paste {
  const dialect = dialectOf(text);
  if (dialect === 'prose') return { dialect, raw: text, fixed: text, fixes: [], unsupported: [], templates: matchingTemplates(text), numbers: numbersIn(text) };
  if (dialect === 'pine') {
    const tr = translatePine(text);
    const { fixed, fixes } = repair(tr.source);
    return { dialect, raw: text, fixed, fixes, unsupported: tr.unsupported, templates: [], numbers: [] };
  }
  const { fixed, fixes } = repair(text);
  return { dialect, raw: text, fixed, fixes, unsupported: [], templates: [], numbers: [] };
}
