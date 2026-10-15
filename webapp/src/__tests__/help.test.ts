import { describe, expect, it } from 'vitest';
import { helpKey, type HelpEntry } from '../pages/helpLib';
import { ALL_TOOLS } from '../chart/tools';
import { STUDIES } from '../engine/indicators_ext_c.js';
// @ts-expect-error a build script, plain JS
import extra from '../../scripts/help/extra.mjs';
import { groupOf } from '../pages/HelpPage';

// The library as prepare-assets builds it: the phone's entries plus the web's own (read from disk:
// Node's fs, named indirectly because the app's types do not include Node's).
const fs = (await import(/* @vite-ignore */ ['node', 'fs'].join(':'))) as { readFileSync(p: URL, e: string): string };
const read = (rel: string) => fs.readFileSync(new URL(rel, import.meta.url), 'utf8');
const core = JSON.parse(read('../../../core/help/src/main/assets/help/content.json')) as Record<string, HelpEntry>;
const legacyRaw = read('../../scripts/help/legacy.json');
const lib: Record<string, HelpEntry> = { ...extra, ...core };
const legacy = JSON.parse(legacyRaw) as Record<string, { sections?: unknown[]; diagram?: unknown }>;

describe('help library', () => {
  it('every drawing tool opens an entry', () => {
    const missing = ALL_TOOLS.map((t) => t.id).filter((id) => !helpKey(lib, id));
    expect(missing).toEqual([]);
  });
  it('every indicator opens an entry', () => {
    const missing = Object.keys(STUDIES).filter((id) => !helpKey(lib, id));
    expect(missing).toEqual([]);
  });
  it('the web entries carry both languages', () => {
    for (const [id, e] of Object.entries(extra as Record<string, HelpEntry>)) {
      expect(e.title?.fa && e.title?.en, id).toBeTruthy();
      expect(e.what?.fa && e.what?.en, id).toBeTruthy();
    }
  });
  it('renamed tools are filed under drawing tools', () => {
    for (const id of ['regression', 'flattop', 'pitchfork_schiff', 'gannsquarefixed', 'ell_double', 'longshort']) expect(groupOf(id, lib[id]), id).toBe('drawings');
    for (const id of ['chandekroll', 'volstop', 'cp_hammer']) expect(groupOf(id, lib[id]), id).toBe('indicators');
  });
  it('the old library brings sections and diagrams to shared entries', () => {
    const shared = Object.keys(legacy).filter((k) => lib[k]);
    expect(shared.filter((k) => legacy[k]!.sections?.length).length).toBeGreaterThan(140);
    expect(shared.filter((k) => legacy[k]!.diagram).length).toBeGreaterThan(60);
  });
});
