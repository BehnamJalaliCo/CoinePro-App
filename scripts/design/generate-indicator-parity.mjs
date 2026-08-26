#!/usr/bin/env node
// Regenerate core/chart/src/test/resources/indicator-parity.txt.
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
//   node scripts/design/generate-indicator-parity.mjs <path-to-Pro-Chart-App>
//
// Pro-Chart is a separate repository and is not vendored here; clone it and pass its root.

import { mkdtempSync, readFileSync, writeFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';
import { join, resolve } from 'node:path';
import { tmpdir } from 'node:os';

const FIXTURE = 'core/chart/src/test/resources/indicator-parity.txt';

const prochart = process.argv[2];
if (!prochart) {
  console.error('usage: node scripts/design/generate-indicator-parity.mjs <path-to-Pro-Chart-App>');
  process.exit(2);
}

// Pro-Chart is bundled by Vite, so its imports are extensionless (`from './indicators_ext_a'`).
// Node's ESM loader will not resolve those. Rather than edit the other repository, the three files
// are copied to a scratch directory with `.js` appended to their relative imports — the source is
// otherwise untouched, which is the point: what runs here has to be what ships there.
const stage = mkdtempSync(join(tmpdir(), 'parity-'));
const FILES = ['indicators.js', 'indicators_ext_a.js', 'indicators_ext_b.js'];
for (const file of FILES) {
  const source = readFileSync(resolve(prochart, 'src/bazaarnama', file), 'utf8');
  writeFileSync(join(stage, file), source.replace(/(from\s+'\.\/[\w.-]+)'/g, "$1.js'"));
}

const load = async (file) => import(pathToFileURL(join(stage, file)).href);

const [, extA, extB] = await Promise.all(FILES.map(load));

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

if (missing.length) {
  console.error(`not found in the registries: ${missing.join(', ')}`);
  process.exit(1);
}

// Keep everything already in the fixture — the twenty original indicators are still checked by it —
// and replace only the series this script owns.
const existing = text.slice(text.indexOf('SERIES ')).trimEnd().split(/\n(?=SERIES )/);
const owned = new Set(RECORD.map(([name]) => name));
const kept = existing.filter((block) => !owned.has(block.slice(7, block.indexOf('\n'))));
const header = text.slice(0, text.indexOf('SERIES '));

writeFileSync(FIXTURE, `${header}${[...kept, ...recorded].join('\n')}\n`);
console.log(`wrote ${recorded.length} series (${kept.length} kept) to ${FIXTURE}`);
