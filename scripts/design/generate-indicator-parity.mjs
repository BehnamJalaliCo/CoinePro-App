#!/usr/bin/env node
// Regenerate chart/core/src/jvmTest/resources/indicator-parity.txt.
//
// The Kotlin indicators are a port of Pro-Chart's, and the only honest way to check a port is
// against the thing it was ported from. So this runs the *actual* JavaScript over the *same* bars
// and records what it produces; `IndicatorParityTest` then asserts the Kotlin reproduces it to
// 1e-6. Nothing in the fixture is hand-written, and it must never be hand-edited — an expectation
// somebody adjusted until the test passed is not a check, it is a note of what the bug does.
//
// The bars are read back from the existing fixture rather than regenerated, so every run compares
// against the identical walk and a diff of the fixture is a diff of the *indicators*.
//
//   node scripts/design/generate-indicator-parity.mjs <path-to-Pro-Chart-App> [--refresh]
//
// Pro-Chart is a separate repository and is not vendored here; clone it and pass its root.

import { mkdtempSync, readFileSync, writeFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';
import { join, resolve } from 'node:path';
import { tmpdir } from 'node:os';

const FIXTURE = 'chart/core/src/jvmTest/resources/indicator-parity.txt';

const prochart = process.argv.slice(2).find((arg) => !arg.startsWith('--'));
if (!prochart) {
  console.error('usage: node scripts/design/generate-indicator-parity.mjs <path-to-Pro-Chart-App>');
  process.exit(2);
}

// Pro-Chart is bundled by Vite, so its imports are extensionless (`from './indicators_ext_a'`).
// Node's ESM loader will not resolve those. Rather than edit the other repository, the three files
// are copied to a scratch directory with `.js` appended to their relative imports — the source is
// otherwise untouched, which is the point: what runs here has to be what ships there.
const stage = mkdtempSync(join(tmpdir(), 'parity-'));
const FILES = ['indicators.js', 'indicators_ext_a.js', 'indicators_ext_b.js', 'candlePatterns.js'];
for (const file of FILES) {
  const source = readFileSync(resolve(prochart, 'src/bazaarnama', file), 'utf8');
  writeFileSync(join(stage, file), source.replace(/(from\s+'\.\/[\w.-]+)'/g, "$1.js'"));
}

const load = async (file) => import(pathToFileURL(join(stage, file)).href);

const [core, extA, extB] = await Promise.all(FILES.map(load));

// ── the bars, read back from the fixture so the walk never changes ──────────────────────────────
const text = readFileSync(FIXTURE, 'utf8');
const barLines = text
  .slice(text.indexOf('BARS\n') + 5, text.indexOf('SERIES '))
  .trim()
  .split('\n');
const bars = barLines.map((line) => {
  const [t, o, h, l, c, v] = line.split(',').map(Number);
  return { t, o, h, l, c, v };
});
const candles = {
  time: bars.map((b) => b.t),
  open: bars.map((b) => b.o),
  high: bars.map((b) => b.h),
  low: bars.map((b) => b.l),
  close: bars.map((b) => b.c),
  volume: bars.map((b) => b.v),
};

// ── which registry entries to record, and under what fixture name ───────────────────────────────
// One entry per Kotlin function, named for the Kotlin call rather than the JS key, because the
// fixture is read by the Kotlin test and a name it cannot map back is a name nobody can check.
const REGISTRY = { ...(extA.EXT_REGISTRY_A ?? {}), ...(extB.EXT_REGISTRY_B ?? {}) };
// The core registry, for the studies that live only there (ALMA, the ribbons, STC, Elder-ray, the
// higher-timeframe pair, the anchored VWAP, the standard error bands). Looked up by its own keys,
// so the pack-B entries above keep winning where both define one.
const CORE = core.REGISTRY ?? {};

const RECORD = [
  // Trend and moving averages (pack A).
  ['smma14', 'smma', { period: 14 }, 'line'],
  ['zlema21', 'zlema', { period: 21 }, 'line'],
  ['kama10', 'kama', { period: 10, fast: 2, slow: 30 }, 'line'],
  ['t3_10', 't3', { period: 10, volume: 0.7 }, 'line'],
  ['mcginley14', 'mcginley', { period: 14 }, 'line'],
  ['linreg100', 'linreg', { period: 100 }, 'line'],
  ['lsma25', 'lsma', { period: 25, offset: 0 }, 'line'],

  // Volatility (pack A).
  ['stddev20', 'stddev', { period: 20 }, 'line'],
  ['hv10', 'hv', { period: 10, annual: 365 }, 'line'],
  ['chaikinVol10', 'chaikinVol', { period: 10, roc: 10 }, 'line'],
  ['envelopesUpper', 'envelopes', { period: 20, pct: 1 }, 'upper'],
  ['envelopesBasis', 'envelopes', { period: 20, pct: 1 }, 'basis'],
  ['envelopesLower', 'envelopes', { period: 20, pct: 1 }, 'lower'],

  // Momentum (pack B).
  ['mom10', 'mom', { period: 10 }, 'line'],
  ['roc9', 'roc', { period: 9 }, 'line'],
  ['trix18', 'trix', { period: 18, sig: 9 }, 'line'],
  ['trix18Signal', 'trix', { period: 18, sig: 9 }, 'signal'],
  ['ac', 'ac', {}, 'hist'],
  ['uo', 'uo', { short: 7, mid: 14, long: 28 }, 'line'],
  ['fisher9', 'fisher', { period: 9 }, 'line'],
  ['fisher9Signal', 'fisher', { period: 9 }, 'signal'],
  ['crsi', 'crsi', { rsiLen: 3, streakLen: 2, rankLen: 100 }, 'line'],
  ['smiErgodic', 'smiErgodic', { long: 20, short: 5, sig: 5 }, 'line'],
  ['smiErgodicSignal', 'smiErgodic', { long: 20, short: 5, sig: 5 }, 'signal'],
  ['smi10', 'smi', { period: 10, smoothK: 3, smoothD: 3 }, 'line'],
  ['bop', 'bop', { smooth: 1 }, 'line'],
  ['bbPercent20', 'bbpercent', { period: 20, mult: 2 }, 'line'],
  ['bbWidth20', 'bbw', { period: 20, mult: 2 }, 'line'],

  // Volume (pack B).
  ['adLine', 'adline', {}, 'line'],
  ['chaikinOsc', 'chaikinOsc', { fast: 3, slow: 10 }, 'line'],
  ['eom14', 'eom', { period: 14, scale: 100000000 }, 'line'],
  ['forceIndex13', 'forceIndex', { period: 13 }, 'line'],
  ['klinger', 'klinger', { fast: 34, slow: 55, sig: 13 }, 'line'],
  ['klingerSignal', 'klinger', { fast: 34, slow: 55, sig: 13 }, 'signal'],
  ['pvt', 'pvt', {}, 'line'],

  // The fourth pack (pack B's remaining entries).
  ['aroonOsc14', 'aroonOsc', { period: 14 }, 'line'],
  ['adr14', 'adr', { period: 14 }, 'line'],
  ['medianHl2_3', 'median', { period: 3, source: 'hl2' }, 'line'],
  ['typicalPrice', 'typicalPrice', {}, 'line'],
  ['weightedClose', 'weightedClose', {}, 'line'],
  ['pmo', 'pmo', { len1: 35, len2: 20, sig: 10 }, 'line'],
  ['pmoSignal', 'pmo', { len1: 35, len2: 20, sig: 10 }, 'signal'],
  ['pvi', 'pvi', {}, 'line'],
  ['nvi', 'nvi', {}, 'line'],
  ['rviVol', 'rviVol', { length: 14, stdevLen: 10, source: 'close' }, 'line'],
  ['ulcer14', 'ulcer', { period: 14 }, 'line'],
  ['volumeOsc', 'volumeOsc', { shortLen: 5, longLen: 10 }, 'line'],
  ['linRegChannelUpper', 'linRegChannel', { length: 100, mult: 2, source: 'close' }, 'upper'],
  ['linRegChannelBasis', 'linRegChannel', { length: 100, mult: 2, source: 'close' }, 'basis'],
  ['linRegChannelLower', 'linRegChannel', { length: 100, mult: 2, source: 'close' }, 'lower'],
];

// The fourth pack's core-registry entries, read from `indicators.js`'s own REGISTRY: a bare series
// field, or `lines[i].data` / `hists[i].data` where the entry draws more than one.
const RECORD_CORE = [
  ['alma9', 'alma', { period: 9, offset: 0.85, sigma: 6, source: 'close' }, 'line'],
  ['stc', 'stc', { fast: 23, slow: 50, cycle: 10, source: 'close' }, 'line'],
  ['mtfEma50x4', 'mtfEma', { period: 50, factor: 4 }, 'line'],
  ['mtfRsi14x4', 'mtfRsi', { period: 14, factor: 4 }, 'line'],
  ['ribbon0', 'maRibbon', { base: 20, step: 10, count: 6 }, ['lines', 0]],
  ['ribbon5', 'maRibbon', { base: 20, step: 10, count: 6 }, ['lines', 5]],
  ['gmmaShort3', 'gmma', {}, ['lines', 0]],
  ['gmmaLong60', 'gmma', {}, ['lines', 11]],
  ['maCrossFast10', 'maCross', { fast: 10, slow: 30 }, ['lines', 0]],
  ['maCrossSlow30', 'maCross', { fast: 10, slow: 30 }, ['lines', 1]],
  ['avwap100', 'avwap', { anchorBars: 100, mult: 1 }, ['lines', 0]],
  ['avwap100Upper', 'avwap', { anchorBars: 100, mult: 1 }, ['lines', 1]],
  ['stdErrMid21', 'stdErrBands', { period: 21, mult: 2 }, ['lines', 0]],
  ['stdErrUp21', 'stdErrBands', { period: 21, mult: 2 }, ['lines', 1]],
  ['elderBull13', 'elderRay', { period: 13 }, ['hists', 0]],
  ['elderBear13', 'elderRay', { period: 13 }, ['hists', 1]],
];

// Chandelier's two sides come from pack B as `lines`.
const RECORD_B_LINES = [
  ['chandelierLong22', 'chandelier', { period: 22, mult: 3 }, 0],
  ['chandelierShort22', 'chandelier', { period: 22, mult: 3 }, 1],
];

// The structure studies return `lines`/`levels`/`markers` rather than a bare series, so they are
// recorded separately: one entry names the registry key, the index into its `lines` array, and the
// fixture name. Only the pivot ladder and the zigzag are numeric enough to check this way — the
// clustering studies are checked by their own properties in Kotlin, because a fixture of cluster
// prices would pin an arrangement rather than a calculation.
const RECORD_LINES = [
  ['pivotClassicP', 'pivotsMulti', { pivotType: 'Classic' }, 3],
  ['pivotClassicR1', 'pivotsMulti', { pivotType: 'Classic' }, 2],
  ['pivotClassicS1', 'pivotsMulti', { pivotType: 'Classic' }, 4],
  ['pivotFibR2', 'pivotsMulti', { pivotType: 'Fibonacci' }, 1],
  ['pivotCamarillaR3', 'pivotsMulti', { pivotType: 'Camarilla' }, 0],
  ['pivotWoodieS2', 'pivotsMulti', { pivotType: 'Woodie' }, 5],
  ['pivotDemarkP', 'pivotsMulti', { pivotType: 'DM' }, 3],
  ['zigzag5', 'zigzag', { dev: 5 }, 0],
];

const number = (value) => {
  if (value == null || Number.isNaN(value)) return '';
  if (!Number.isFinite(value)) return '';
  // Eight decimals: past what 1e-6 needs, short of where the two languages' rounding diverges.
  return Number(value.toFixed(8)).toString();
};

const recorded = [];
const missing = [];
for (const [name, key, inputs, field] of RECORD) {
  const entry = REGISTRY[key];
  if (!entry) {
    missing.push(key);
    continue;
  }
  const result = entry.calc(candles, { ...entry.inputs, ...inputs });
  const series = result?.[field];
  if (!Array.isArray(series)) {
    missing.push(`${key}.${field}`);
    continue;
  }
  recorded.push(`SERIES ${name}\n${series.map(number).join(',')}`);
}

for (const [name, key, inputs, position] of RECORD_LINES) {
  const entry = REGISTRY[key];
  if (!entry) {
    missing.push(key);
    continue;
  }
  const series = entry.calc(candles, { ...entry.inputs, ...inputs })?.lines?.[position]?.data;
  if (!Array.isArray(series)) {
    missing.push(`${key}.lines[${position}]`);
    continue;
  }
  recorded.push(`SERIES ${name}\n${series.map(number).join(',')}`);
}

for (const [name, key, inputs, field] of RECORD_CORE) {
  const entry = CORE[key];
  if (!entry) {
    missing.push(`core.${key}`);
    continue;
  }
  const result = entry.calc(candles, { ...entry.inputs, ...inputs });
  const series = Array.isArray(field) ? result?.[field[0]]?.[field[1]]?.data : result?.[field];
  if (!Array.isArray(series)) {
    missing.push(`core.${key}.${field}`);
    continue;
  }
  recorded.push(`SERIES ${name}\n${series.map(number).join(',')}`);
}

for (const [name, key, inputs, position] of RECORD_B_LINES) {
  const entry = REGISTRY[key];
  if (!entry) {
    missing.push(key);
    continue;
  }
  const series = entry.calc(candles, { ...entry.inputs, ...inputs })?.lines?.[position]?.data;
  if (!Array.isArray(series)) {
    missing.push(`${key}.lines[${position}]`);
    continue;
  }
  recorded.push(`SERIES ${name}\n${series.map(number).join(',')}`);
}

if (missing.length) {
  console.error(`not found in the registries: ${missing.join(', ')}`);
  process.exit(1);
}

// Keep everything already in the fixture — the twenty original indicators are still checked by it.
//
// A series already in the fixture is kept as it is unless `--refresh` is passed. Pro-Chart kept
// changing after the port: by 2026-09 thirteen of the recorded series (the TRIX and SMI Ergodic
// signals, SMI, Force Index, Klinger's signal, the accelerator's warm-up and the seven pivot
// lines) no longer match what its JavaScript draws. Those were ported from the revision the fixture
// records and are held to the textbook by `IndicatorReferenceTest`; re-pinning them to a later
// revision is a decision about the indicator, not a side effect of adding a new one.
const refresh = process.argv.includes('--refresh');
const existing = text.slice(text.indexOf('SERIES ')).trimEnd().split(/\n(?=SERIES )/);
const present = new Set(existing.map((block) => block.slice(7, block.indexOf('\n'))));
const owned = new Set([...RECORD, ...RECORD_LINES, ...RECORD_CORE, ...RECORD_B_LINES].map(([name]) => name));
const fresh = refresh ? recorded : recorded.filter((block) => !present.has(block.slice(7, block.indexOf('\n'))));
recorded.length = 0;
recorded.push(...fresh);
const kept = existing.filter((block) => {
  const name = block.slice(7, block.indexOf('\n'));
  return !owned.has(name) || !refresh;
});
const header = text.slice(0, text.indexOf('SERIES '));

writeFileSync(FIXTURE, `${header}${[...kept, ...recorded].join('\n')}\n`);
console.log(`wrote ${recorded.length} series (${kept.length} kept) to ${FIXTURE}`);
