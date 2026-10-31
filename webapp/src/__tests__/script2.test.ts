import { beforeEach, describe, expect, it } from 'vitest';
import engineSource from '../engine/namascript.js?raw';
import { readPaste, repair, dialectOf, masked, FIX_IDS } from '../script/paste';
import { TEMPLATES, matchingTemplates, numbersIn, templateSource } from '../script/templates';
import { LESSONS } from '../script/lessons';
import { PROMPT_EXAMPLES, PROMPT_VERSION, SPEC_MARK, promptParts, scriptPrompt } from '../script/promptKit';
import { idFor, readScriptFile, scriptIn, sharePost, shareRefusals, splitPost, writeScriptFile, installedScripts, noteInstalled, type ScriptDocument } from '../script/doc';
import { readSnapshot, swingPosition, readContext } from '../pages/aiEvidence';
import { isSafeLink, partnerList, PARTNER_FALLBACK } from '../trade/partners';
import { noteFounding, isFounding } from '../data/founding';
import { withMeta, ENGINE_PLOT_COLOUR } from '../state/script';
import type { ScriptResult } from '../engine/namascript.js';
import { REFERENCE } from '../engine/scriptlib.js';

// The engine's worker, run in-process: its source is a template literal in namascript.js.
const WORKER = (() => {
  const start = engineSource.indexOf('const WORKER_SRC = `') + 'const WORKER_SRC = `'.length;
  const end = engineSource.indexOf('`;', start);
  return new Function(`return \`${engineSource.slice(start, end)}\`;`)() as string;
})();
function run(source: string): ScriptResult {
  const n = 300;
  const close = Array.from({ length: n }, (_, i) => 100 + Math.sin(i / 9) * 8 + i * 0.05 + (((i * 7919) % 13) - 6) * 0.4);
  const data = {
    source, inputs: {}, time: close.map((_, i) => 1_700_000_000 + i * 3600), open: close.map((c, i) => (i ? close[i - 1]! : c)),
    high: close.map((c) => c + 1.2), low: close.map((c) => c - 1.2), close, volume: close.map((_, i) => 100 + (i % 7) * 40),
  };
  let out: ScriptResult | null = null;
  const self: { onmessage?: (e: { data: unknown }) => void; postMessage(m: ScriptResult): void } = { postMessage: (m) => { out = m; } };
  new Function('self', WORKER)(self);
  self.onmessage!({ data });
  return out!;
}
const draws = (r: ScriptResult) => (r.plots?.length ?? 0) + (r.shapes?.length ?? 0) + (r.hlines?.length ?? 0) + (r.zones?.length ?? 0);

describe('script2: templates', () => {
  it('there are twelve, and every one runs, draws and declares its alert conditions', () => {
    expect(TEMPLATES).toHaveLength(12);
    for (const t of TEMPLATES) {
      const r = run(templateSource(t));
      expect(r.ok, `${t.id}: ${r.error}`).toBe(true);
      expect(draws(r), t.id).toBeGreaterThan(0);
      expect((r.alerts ?? []).length, t.id).toBeGreaterThanOrEqual(1);
    }
  });
  it('a sentence fills the holes in order and keeps the defaults for the rest', () => {
    const t = TEMPLATES.find((x) => x.id === 'ma-cross')!;
    expect(templateSource(t, [9])).toContain('input.int(9, "دوره‌ی تند"');
    expect(templateSource(t, [9])).toContain('input.int(50, "دوره‌ی کند"');
    expect(numbersIn('وقتی میانگین ۲۰ از 50 رد شد')).toEqual([20, 50]);
  });
  it('matches keywords best first, and offers the first three when nothing matched', () => {
    expect(matchingTemplates('buy when RSI is oversold')[0]!.id).toBe('rsi-zones');
    expect(matchingTemplates('qwerty').map((t) => t.id)).toEqual(TEMPLATES.slice(0, 3).map((t) => t.id));
  });
});

describe('script2: paste', () => {
  it('tells NamaScript, Pine and prose apart', () => {
    expect(dialectOf('a = ta.ema(close, 20)\nplot(a, "x", color.blue)')).toBe('nama');
    expect(dialectOf('//@version=5\nindicator("x")\nplot(close)')).toBe('pine');
    expect(dialectOf('x = close > open and volume > 0\nplot(x)')).toBe('pine');
    expect(dialectOf('وقتی میانگین ۲۰ از ۵۰ رد شد بخر')).toBe('prose');
    expect(dialectOf('')).toBe('prose');
  });
  it('masks strings and comments, length for length', () => {
    const line = 'plot(x, "۳ کندل") // ۵';
    expect(masked(line)).toHaveLength(line.length);
    expect(masked(line)).not.toContain('۳');
  });
  it('repairs what is always wrong here, says which lines, and never touches the reader\'s words', () => {
    const src = 'len = input.int(۲۰, “طول”)\nfloat avg = sma(close, len)\nprev = close(1)\nflat = avg == na\nplot(avg, "۳ کندل", color.grey)';
    const { fixed, fixes } = repair(src);
    expect(fixes.map((f) => f.id)).toEqual(['smart-quotes', 'persian-digits', 'typed-declaration', 'bare-ta-names', 'na-comparison', 'colour-names', 'close-index']);
    expect(fixed).toContain('input.int(20, "طول")');
    expect(fixed).toContain('avg = ta.sma(close, len)');
    expect(fixed).toContain('prev = close[1]');
    expect(fixed).toContain('flat = na(avg)');
    expect(fixed).toContain('"۳ کندل", color.gray');
    expect(fixes.find((f) => f.id === 'persian-digits')!.lines).toEqual([1]);
    expect(run(fixed).ok).toBe(true);
  });
  it('is idempotent and leaves a correct script byte for byte', () => {
    const { fixed } = repair('len = input.int(۲۰, "طول")\nplot(ta.sma(close, len))');
    expect(repair(fixed).fixes).toEqual([]);
    for (const t of TEMPLATES) expect(repair(templateSource(t)).fixes, t.id).toEqual([]);
    for (const l of LESSONS) if (l.example) expect(repair(l.example).fixes, l.id).toEqual([]);
  });
  it('adds a plot to a script that computes and draws nothing', () => {
    const { fixed, fixes } = repair('fast = ta.ema(close, 9)');
    expect(fixes.map((f) => f.id)).toEqual(['add-plot']);
    expect(fixed.trim().endsWith('plot(fast)')).toBe(true);
  });
  it('keeps Pine it cannot run as named comments, and reads prose as a picker', () => {
    const p = readPaste('//@version=5\nindicator("x")\nbull = close > open and volume > 0\nif bull\n    x := 1\nplot(close)');
    expect(p.dialect).toBe('pine');
    expect(p.unsupported.map((u) => u.line)).toEqual([3, 4, 5]);
    expect(p.fixed).toContain('// [نمااسکریپت');
    expect(p.raw).toContain('if bull');
    const prose = readPaste('buy on the MACD signal line, 12 26 9');
    expect(prose.dialect).toBe('prose');
    expect(prose.templates[0]!.id).toBe('macd-signal');
    expect(prose.numbers).toEqual([12, 26, 9]);
    expect(FIX_IDS).toContain('smart-quotes');
  });
});

describe('script2: lessons and the prompt kit', () => {
  it('twelve lessons in both languages, every example runs, and the Persian says چارت', () => {
    expect(LESSONS).toHaveLength(12);
    for (const l of LESSONS) {
      expect(l.body.length, l.id).toBe(l.bodyEn.length);
      expect(JSON.stringify(l)).not.toMatch(/نمودار|هٔ/);
      if (l.example) { const r = run(l.example); expect(r.ok, `${l.id}: ${r.error}`).toBe(true); expect(draws(r), l.id).toBeGreaterThan(0); }
    }
  });
  it('the prompt names the chart, carries its version, and splits into the ask and the specification', () => {
    const fa = scriptPrompt('XAUUSD', '1h');
    expect(fa).toContain('XAUUSD on the 1h timeframe');
    expect(fa).toContain(`(نسخه‌ی راهنما: ${PROMPT_VERSION})`);
    const [ask, spec] = promptParts(fa);
    expect(ask).toContain('نمااسکریپت');
    expect(spec!.startsWith(SPEC_MARK)).toBe(true);
    expect(promptParts('just text')[1]).toBeNull();
    expect(scriptPrompt('BTCUSDT', '4h', true)).toContain('(kit version: 2)');
  });
  it('teaches only names the reference documents, and its examples run', () => {
    const known = new Set(REFERENCE.flatMap((g) => g.items.flatMap(([sig]) => [...sig.matchAll(/([A-Za-z_][\w.]*)\s*\(/g)].map((m) => m[1]))));
    const listed = scriptPrompt('X', '1h', true).split('\n').filter((l) => /^- (Averages|Oscillators|Signals|Volatility and trend):/.test(l));
    expect(listed.length).toBeGreaterThan(2);
    for (const l of listed) for (const name of l.replace(/^- [^:]+: /, '').replace(' and a few more', '').split(', ')) expect(known.has(name), name).toBe(true);
    for (const ex of PROMPT_EXAMPLES) { const r = run(ex.source); expect(r.ok, ex.titleEn).toBe(true); }
  });
});

describe('script2: documents and the board', () => {
  const doc: ScriptDocument = { id: 'abc234defgh', name: 'Mine', description: 'One line', source: 'plot(close)', colour: '#22C55E', tags: ['trend', 'ema'], ownPane: true, defaults: { 'طول': 14 } };
  it('a .nama file reads back as it was written, and is the phone\'s format', () => {
    const text = writeScriptFile(doc);
    expect(text.startsWith('// nama 1\n// id: abc234defgh\n')).toBe(true);
    expect(text).toContain('// colour: FF22C55E');
    expect(text).toContain('// pane: own');
    expect(readScriptFile(text)).toEqual(doc);
    expect(readScriptFile('plot(close)')).toBeNull();
    expect(readScriptFile('// nama 1\n// id: x\nplot(close)')).toBeNull();
    // The phone's gold is "nobody chose": as drawn here.
    expect(readScriptFile('// nama 1\n// id: k\n// colour: FFD8A848\n// ---\nplot(close)')!.colour).toBeUndefined();
  });
  it('a share post opens with a sentence, carries the file, and splits back into its halves', () => {
    const body = sharePost(doc);
    expect(body.startsWith('یک اسکریپت از من: Mine\nOne line\n')).toBe(true);
    expect(scriptIn(body)!.source).toBe('plot(close)');
    expect(splitPost(body).prose).toBe('یک اسکریپت از من: Mine\nOne line');
    expect(splitPost('just words').code).toBeNull();
    expect(sharePost(doc, true).startsWith('A script of mine: Mine')).toBe(true);
  });
  it('knows what the board refuses', () => {
    expect(shareRefusals('see https://x.io')).toEqual(['link']);
    expect(shareRefusals('call 09121234567')).toEqual(['number']);
    expect(shareRefusals('ask @somebody')).toEqual(['handle']);
    expect(shareRefusals('//@version=5 plot(close)')).toEqual([]);
  });
  it('ids are the phone\'s shape and stable', () => {
    expect(idFor('plot(close)', 1000)).toMatch(/^[a-km-np-z2-9]{11}$/);
    expect(idFor('plot(close)', 1000)).toBe(idFor('plot(close)', 1000));
    expect(idFor('plot(close)', 1001)).not.toBe(idFor('plot(close)', 1000));
  });
  beforeEach(() => localStorage.clear());
  it('remembers what this browser installed, once each', () => {
    noteInstalled('a'); noteInstalled('a'); noteInstalled('b');
    expect(installedScripts()).toEqual(['a', 'b']);
  });
  it('the founding mark is noted once, from an account that says everything is open, and never undone', () => {
    expect(noteFounding({})).toBe(false);
    expect(noteFounding({ tradeyar: { all: false } })).toBe(false);
    expect(noteFounding({ tradeyar: { all: true } })).toBe(true);
    expect(noteFounding({ tradeyar: { all: false } })).toBe(true);
    expect(isFounding()).toBe(true);
  });
  it('the script\'s colour recolours only the plots left in the engine\'s default', () => {
    const r = { ok: true, plots: [{ name: 'a', color: ENGINE_PLOT_COLOUR, width: 1, style: 0, data: [] }, { name: 'b', color: '#ef4444', width: 1, style: 0, data: [] }] } as ScriptResult;
    expect(withMeta(r, { colour: '#22c55e' }).plots!.map((p) => p.color)).toEqual(['#22c55e', '#ef4444']);
    expect(withMeta(r, null)).toBe(r);
  });
});

describe('script2: AI evidence and sources', () => {
  it('reads the readings flat first and nested second, missing never zero', () => {
    const s = readSnapshot({ ema20: 101.5, snapshot: { ema_20: 99, rsi_14: 55.2, swing_high_20: 110, swing_low_20: 90 }, price_now: 105 })!;
    expect(s.ema20).toBe(101.5);
    expect(s.rsi14).toBe(55.2);
    expect(s.macd).toBeNull();
    expect(swingPosition(s)).toBeCloseTo(0.75);
    expect(readSnapshot({ direction: 'long' })).toBeNull();
    expect(swingPosition({ ...s, swingHigh20: 90 })).toBeNull();
  });
  it('reads the reply\'s sources and drops what it cannot name', () => {
    const c = readContext({ context: [{ kind: 'active_signal', title: 'BTC long', freshness: 'fresh', signal_id: 11, as_of: '10:00' }, { kind: 'weird', title: 'x' }, { kind: 'news', title: '' }] });
    expect(c).toEqual([{ kind: 'active_signal', title: 'BTC long', summary: null, source: null, asOf: '10:00', freshness: 'fresh', signalId: '11' }]);
    expect(readContext({ answer: 'x' })).toEqual([]);
  });
});

describe('script2: trade partners', () => {
  it('broker first, then the exchanges, with the server\'s links when they are https and the phone\'s otherwise', () => {
    const list = partnerList({ lbank: 'https://lbank.com/ref/e2e', ourbit: 'javascript:alert(1)' });
    expect(list.map((p) => p.id)).toEqual(['oneroyal', 'lbank', 'ourbit']);
    expect(list[1]!.url).toBe('https://lbank.com/ref/e2e');
    expect(list[2]!.url).toBe(PARTNER_FALLBACK.ourbit);
    expect(list[0]!.url).toBe(PARTNER_FALLBACK.oneroyal);
    expect(isSafeLink('http://x.com')).toBe(false);
    expect(isSafeLink('intent://x')).toBe(false);
  });
});
