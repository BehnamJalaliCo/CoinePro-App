// House rules for interface copy (CLAUDE.md): «چارت», never «نمودار»; «ه‌ی» for the ezafe after a
// final heh, never «هٔ»; one typeface, so no font family other than IRANYekanX anywhere in src/.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const src = join(dirname(fileURLToPath(import.meta.url)), '../../src');
const files = [];
(function walk(d) {
  for (const f of readdirSync(d)) {
    const p = join(d, f);
    if (statSync(p).isDirectory()) { if (f !== 'engine') walk(p); }
    else if (/\.(tsx?|css|html)$/.test(f)) files.push(p);
  }
})(src);
files.push(join(src, '../index.html'));

test('no «نمودار» in interface copy', () => {
  for (const f of files) assert.ok(!readFileSync(f, 'utf8').includes('نمودار'), f);
});

test('no «هٔ» (hamza ezafe) in interface copy', () => {
  for (const f of files) assert.ok(!/هٔ|ۀ/.test(readFileSync(f, 'utf8')), f);
});

test('IRANYekanX is the only font family', () => {
  for (const f of files) {
    const text = readFileSync(f, 'utf8');
    for (const m of text.matchAll(/font-family\s*:\s*([^;}]+)/g)) {
      assert.match(m[1], /^\s*(var\(--font\)|'IRANYekanX'|inherit)/, `${f}: ${m[1]}`);
    }
    assert.ok(!text.includes('fonts.googleapis.com'), f);
  }
});
