// «گرفتن اسکریپت از هوش مصنوعی» — the prompt a reader hands to an assistant (the phone's
// ScriptPromptKit.kt). A reader is going to ask an assistant anyway, and «write me a NamaScript
// indicator» comes back as Pine or an invented language. The difference between that and something
// that runs on the first try is a few hundred characters of context: what differs from Pine, the
// function names, the drawing family and three examples. The app has them, so it hands them over.
//
// The function lists are generated from the reference the editor itself shows (engine/scriptlib.js
// REFERENCE), not written beside it, so the prompt cannot teach a name the engine does not answer.
// The ask is in the reader's language; the specification is English in both, because the model is
// the one reading it and it reads English best.
import { REFERENCE } from '../engine/scriptlib.js';

/** Bumped whenever a change here could change an assistant's answer. */
export const PROMPT_VERSION = '2';
export const SPEC_MARK = '--- NamaScript specification';
/** The web front doors: a link opens whatever the reader is already signed into. */
export const CHATGPT = 'https://chatgpt.com/';
export const CLAUDE = 'https://claude.ai/new';
const PER_GROUP = 6;

/** The groups' English names, by the reference's Persian ones. */
const GROUP_EN: Record<string, string> = {
  'میانگین': 'Averages', 'نوسان‌گرها': 'Oscillators', 'نوسان/روند': 'Volatility and trend', 'سیگنال': 'Signals',
  'ورودی': 'Inputs', 'استراتژی': 'Strategy', 'میانگین‌های پیشرفته': 'More averages', 'اسیلاتورهای پیشرفته': 'More oscillators',
  'روند/جهت/ابر': 'Trend, direction, cloud', 'حجم/ساختار': 'Volume and structure', 'ریاضی': 'Math',
};

/** The function names of a reference row: `ta.dema(src,p) ta.tema(src,p)` → ta.dema, ta.tema. */
function namesOf(sig: string): string[] {
  return [...sig.matchAll(/([A-Za-z_][\w.]*)\s*\(/g)].map((m) => m[1]!);
}

export interface PromptExample { title: string; titleEn: string; source: string }

/** Three shapes between them: a line on the price, a study in its own panel with levels, a verdict. */
export const PROMPT_EXAMPLES: PromptExample[] = [
  {
    title: 'یک میانگین روی قیمت', titleEn: 'One average on the price',
    source: 'length = input.int(20, "دوره", 2, 200)\naverage = ta.ema(close, length)\nplot(average, "میانگین", color.orange)',
  },
  {
    title: 'یک نوسان‌گر در پنل خودش، با سطح', titleEn: 'An oscillator in its own panel, with levels',
    source: 'r = ta.rsi(close, 14)\nplot(r, "RSI", color.purple)\nhline(30, "کف")\nhline(70, "سقف")',
  },
  {
    title: 'یک سیگنال با جمله‌ی خودش', titleEn: 'A signal with its own sentence',
    source: 'fast = ta.ema(close, 9)\nslow = ta.ema(close, 21)\nplot(fast, "تند", color.orange)\nplot(slow, "کند", color.blue)\nbuy = ta.crossover(fast, slow)\nsell = ta.crossunder(fast, slow)\nplotshape(buy, "میانگین تند از کند رد شد", shape.up, color.green)\nplotshape(sell, "میانگین تند زیر کند رفت", shape.down, color.red)\nalertcondition(buy, "میانگین تند از کند رد شد")\nalertcondition(sell, "میانگین تند زیر کند رفت")',
  },
];

function spec(symbol: string, timeframe: string): string {
  const out: string[] = [];
  out.push('--- NamaScript specification (read this in English) ---', '');
  out.push('NamaScript is the Pro Chart web app\'s indicator language. It looks like Pine and differs:');
  out.push('- Every name is a whole series (an array over all the bars), computed at once. There is no per-bar loop.');
  out.push('- Operators do not work on series. Write add(a, b), sub(a, b), mul(a, b), div(a, b) for + − × ÷,');
  out.push('  gt(a, b), lt(a, b), ge(a, b), le(a, b) for > < >= <=, and(a, b), or(a, b) for and/or, iff(c, a, b) for c ? a : b.');
  out.push('  Plain operators are fine between two plain numbers (inputs and constants): length * 2.');
  out.push('- The previous bar is close[1] (or ref(close, 1)), never close(1).');
  out.push('- Arguments are positional: plot(series, "title", color.blue), input.int(20, "title", min, max). No name = value.');
  out.push('- One statement per line. No if / for / while, no function definitions, no arrays, no syminfo or timeframe.');
  out.push('- An indicator("title") or strategy("title") first line is allowed, not required.');
  out.push('');
  out.push('What is available:');
  out.push('- Series: open, high, low, close, volume, hl2, hlc3, ohlc4, bar_index');
  for (const g of REFERENCE) {
    const en = GROUP_EN[g.g];
    if (!en) continue;
    const names = [...new Set(g.items.flatMap(([sig]) => namesOf(sig)))];
    if (!names.length) continue;
    out.push(`- ${en}: ${names.slice(0, PER_GROUP).join(', ')}${names.length > PER_GROUP ? ' and a few more' : ''}`);
  }
  out.push('- Series helpers: add, sub, mul, div, gt, lt, ge, le, and, or, iff, nz, na, ref, crossover, crossunder, cross');
  out.push('');
  out.push('What draws:');
  out.push('- plot(series, "name", color.orange) − a line. A series near the price sits on the candles; one that is not gets its own panel.');
  out.push('- hline(60, "ceiling") − a fixed horizontal line');
  out.push('- plotshape(condition, "name", shape.up, color.green) − a mark on every bar the condition holds; shape.up, shape.down or shape.circle');
  out.push('- input.int(20, "length", 2, 200) and input.float(1.5, "factor", 0.1, 10) − numbers the reader can change');
  out.push('- The colours: color.blue, color.red, color.green, color.orange, color.purple, color.gray, color.yellow, color.aqua, color.teal, color.white');
  out.push('');
  out.push('And the important one:');
  out.push('- alertcondition(condition, "what happened") − this is what turns a script into a *signal*.');
  out.push('  The app turns it into alerts and tests it: the first two conditions are an entry and its exit.');
  out.push('  Write one for each direction, each with a plotshape for the mark.');
  out.push('  Titles and messages may be written in Persian − the reader reads them.');
  out.push('');
  out.push('Three examples:', '');
  for (const ex of PROMPT_EXAMPLES) out.push(`// ${ex.titleEn}`, ex.source, '');
  out.push(`The reader's chart: ${symbol} on the ${timeframe} timeframe. Choose lengths that suit it.`, '');
  out.push('--- end of specification ---');
  return out.join('\n') + '\n';
}

/** The whole prompt, ready to copy. */
export function scriptPrompt(symbol: string, timeframe: string, english = false): string {
  const head = english
    ? ['Write an indicator in NamaScript for me. Code only, no commentary.', 'The language\'s specification is below; follow it exactly.']
    : ['یک اندیکاتور به زبان «نمااسکریپت» برای من بنویس. فقط کد بده، بدون توضیح.', 'مشخصات زبان در بخش انگلیسی زیر آمده است؛ دقیقاً از همان پیروی کن.', 'جواب را که گرفتی، در همان صفحه‌ی «گرفتن اسکریپت از هوش مصنوعی» دکمه‌ی «چسباندن جواب» را بزن.'];
  return [...head, '', spec(symbol, timeframe), english ? `(kit version: ${PROMPT_VERSION})` : `(نسخه‌ی راهنما: ${PROMPT_VERSION})`].join('\n') + '\n';
}

/** A prompt split into the half the reader reads and the half the model reads (null for other text). */
export function promptParts(prompt: string): [string, string | null] {
  const at = prompt.indexOf(SPEC_MARK);
  return at < 0 ? [prompt, null] : [prompt.slice(0, at).trimEnd(), prompt.slice(at)];
}
