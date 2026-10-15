// Copies the assets the web app shares with the phone into public/generated/, so there is one
// source for each: the typeface from core/designsystem, the interface icons and the symbol logos
// from design/. Nothing here is committed twice; public/generated/ is ignored by git.
//
//   fonts/iranyekanx_*.ttf   the product's only typeface (owner's rule)
//   icons.svg                every design/ui-icons/tradingview/*.svg as a <symbol id="…">
//   logos/<base>.svg         one file per instrument the phone can draw, plus logos.json
//   names.json               the phone's instrument names (core/symbols SymbolNames.kt, SymbolAliases.kt)
import { mkdirSync, readFileSync, readdirSync, copyFileSync, writeFileSync, existsSync, rmSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const app = join(here, '..');
const repo = join(app, '..');
const out = join(app, 'public', 'generated');

rmSync(out, { recursive: true, force: true });
mkdirSync(join(out, 'fonts'), { recursive: true });
mkdirSync(join(out, 'logos'), { recursive: true });

// ── Typeface ─────────────────────────────────────────────────────────────────────────────────────
const fontDir = join(repo, 'core/designsystem/src/main/res/font');
const fonts = readdirSync(fontDir).filter((f) => /^iranyekanx_.*\.ttf$/.test(f));
for (const f of fonts) copyFileSync(join(fontDir, f), join(out, 'fonts', f));

// ── Interface icons → one sprite ─────────────────────────────────────────────────────────────────
// Each source is `<svg viewBox="…">…</svg>` drawn in currentColor; the sprite keeps the viewBox
// per symbol so a `<use>` scales it like the original.
// The TradingView set whole; Phosphor only by the list in phosphor-icons.txt (fill variants are
// named `<name>-fill` in that list).
const wanted = new Set(readFileSync(join(here, 'phosphor-icons.txt'), 'utf8').split('\n')
  .map((l) => l.trim()).filter((l) => l && !l.startsWith('#')));
const iconDirs = [
  ['', join(repo, 'design/ui-icons/tradingview'), null],
  ['ph-', join(repo, 'design/ui-icons/phosphor-regular'), wanted],
  ['ph-', join(repo, 'design/ui-icons/phosphor-fill'), wanted],
];
const symbols = [];
for (const [prefix, dir, only] of iconDirs) {
  if (!existsSync(dir)) continue;
  for (const f of readdirSync(dir).filter((n) => n.endsWith('.svg') && (!only || only.has(n.replace(/\.svg$/, '')))).sort()) {
    const svg = readFileSync(join(dir, f), 'utf8');
    const viewBox = /viewBox="([^"]+)"/.exec(svg)?.[1] ?? '0 0 24 24';
    const inner = svg.replace(/^[\s\S]*?<svg[^>]*>/, '').replace(/<\/svg>\s*$/, '');
    symbols.push(`<symbol id="${prefix}${f.replace(/\.svg$/, '')}" viewBox="${viewBox}">${inner}</symbol>`);
  }
}
writeFileSync(join(out, 'icons.svg'),
  `<svg xmlns="http://www.w3.org/2000/svg" style="display:none">${symbols.join('')}</svg>`);

// ── Symbol logos ─────────────────────────────────────────────────────────────────────────────────
// The manifest is the phone's own (scripts/design/build-symbol-logos.py): crypto base → archive.
// A base the manifest does not list has no artwork, and the app never shows a symbol without one
// (SymbolArtwork.covers on the phone; logoFor() here).
const logoRoot = join(repo, 'design/asset-logos');
const manifest = readFileSync(join(logoRoot, 'SYMBOL-LOGO-MANIFEST.txt'), 'utf8');
const logos = {};
for (const line of manifest.split('\n')) {
  if (!line.trim() || line.startsWith('#')) continue;
  const [base, source] = line.trim().split(/\s+/);
  if (!base || !source) continue;
  const svg = join(logoRoot, source, `${base}.svg`);
  if (!existsSync(svg)) continue;
  const name = base.replace(/[^a-z0-9]/gi, '_');
  copyFileSync(svg, join(out, 'logos', `${name}.svg`));
  logos[base.toUpperCase()] = `${name}.svg`;
}
// Countries (for currency pairs) and metals, whole.
for (const [kind, dir] of [['country', 'tv-logos/country'], ['metal', 'tv-logos/metal'], ['country', 'authored']]) {
  const full = join(logoRoot, dir);
  if (!existsSync(full)) continue;
  for (const f of readdirSync(full).filter((n) => n.endsWith('.svg'))) {
    const code = f.replace(/\.svg$/, '').toUpperCase();
    const name = `${kind}_${code.toLowerCase().replace(/[^a-z0-9]/g, '_')}.svg`;
    copyFileSync(join(full, f), join(out, 'logos', name));
    logos[`${kind}:${code}`] = name;
  }
}
writeFileSync(join(out, 'logos.json'), JSON.stringify(logos));

// ── App icons ────────────────────────────────────────────────────────────────────────────────────
// The launcher icon the phone and the current web build already ship (scripts/design/build-web-icons.py).
const appIcons = join(repo, 'web/src/wasmJsMain/resources/icons');
mkdirSync(join(out, 'app-icons'), { recursive: true });
for (const f of readdirSync(appIcons)) copyFileSync(join(appIcons, f), join(out, 'app-icons', f));

// ── Instrument names ─────────────────────────────────────────────────────────────────────────────
// Every `val NAME: Map<String, String> = mapOf("A" to "B", …)` in SymbolNames.kt, and the alias map,
// read as data so a name changed for the phone changes on the web too.
const symbolsDir = join(repo, 'core/symbols/src/main/kotlin/com/coinepro/core/symbols');
function kotlinMaps(file) {
  const src = readFileSync(join(symbolsDir, file), 'utf8');
  const maps = {};
  for (const m of src.matchAll(/val\s+([A-Z_]+)\s*:\s*Map<String,\s*String>\s*=\s*mapOf\(([\s\S]*?)\n\s*\)/g)) {
    const entries = {};
    for (const e of m[2].matchAll(/"([^"]+)"\s+to\s+"([^"]*)"/g)) entries[e[1]] = e[2];
    maps[m[1]] = entries;
  }
  return maps;
}
const names = kotlinMaps('SymbolNames.kt');
const aliasMaps = kotlinMaps('SymbolAliases.kt');
const aliases = aliasMaps.SYMBOLS ?? {};
writeFileSync(join(out, 'names.json'), JSON.stringify({ ...names, ALIASES: aliases, BASE_ALIASES: aliasMaps.BASES ?? {} }));

// ── Help library and legal texts ─────────────────────────────────────────────────────────────────
// The phone's «؟» catalogue (core/help assets: 177 entries, both languages, pictures) and the legal
// documents (docs/legal), written with the web's house rules: no kasra marks, «ه‌ی» for the
// hamza ezafe, «چارت» for the chart.
const KASRA = String.fromCharCode(0x0650);
const HAMZA_HEH = String.fromCharCode(0x0647, 0x0654);
const HEH_YEH = String.fromCharCode(0x06c0);
const FORMAL_CHART = String.fromCharCode(0x0646, 0x0645, 0x0648, 0x062f, 0x0627, 0x0631);
const house = (text) => text.split(KASRA).join('').split(HAMZA_HEH).join('ه\u200cی').split(HEH_YEH).join('ه\u200cی').split(FORMAL_CHART).join('چارت');
const helpRoot = join(repo, 'core/help/src/main/assets/help');
mkdirSync(join(out, 'help', 'images'), { recursive: true });
writeFileSync(join(out, 'help', 'content.json'), house(readFileSync(join(helpRoot, 'content.json'), 'utf8')));
for (const f of readdirSync(join(helpRoot, 'images'))) copyFileSync(join(helpRoot, 'images', f), join(out, 'help', 'images', f));
mkdirSync(join(out, 'legal'), { recursive: true });
for (const f of ['TERMS.md', 'TERMS_EN.md', 'PRIVACY_POLICY.md']) writeFileSync(join(out, 'legal', f), house(readFileSync(join(repo, 'docs/legal', f), 'utf8')));

console.log(`assets: ${fonts.length} fonts, ${symbols.length} icons, ${Object.keys(logos).length} logos, ${Object.keys(names).length} name maps, ${Object.keys(aliases).length} aliases`);
