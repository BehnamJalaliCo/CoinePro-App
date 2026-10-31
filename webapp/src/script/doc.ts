// A script as a document — its name, one line about it, its colour, tags, whether it wants its own
// panel, the input values it was tuned to — and the two ways that document travels: as a `.nama`
// file (a comment-headed text that is also runnable source) and inside a community post.
// Ported from the phone (namascript ScriptDocument.kt / ScriptShare.kt) so a file or a post written
// on one reads on the other: the same `// nama 1` header, the same keys, the same separator.

export interface ScriptMeta {
  /** One line about it; at most 140 characters, no newlines. */
  description?: string;
  /** `#RRGGBB`; recolours the plots the script left in the engine's default colour. Unset: as drawn. */
  colour?: string;
  tags?: string[];
  /** Draw the script in a panel of its own rather than wherever its numbers put it. */
  ownPane?: boolean;
}

export interface ScriptDocument extends ScriptMeta {
  id: string;
  name: string;
  source: string;
  /** Input values by title, as tuned when the file was written. */
  defaults?: Record<string, number>;
  origin?: string | null;
}

export const NAME_LIMIT = 60;
export const DESCRIPTION_LIMIT = 140;
const PREFIX = '//';
const SEPARATOR = '// ---';
const ID_ALPHABET = 'abcdefghijkmnpqrstuvwxyz23456789';

/** The phone's id for a script: an FNV-1a hash of the source, mixed with the time, in 11 letters. */
export function idFor(source: string, at: number): string {
  const M = (1n << 64n) - 1n;
  let hash = 0xcbf29ce484222325n;
  for (let i = 0; i < source.length; i++) hash = ((hash ^ BigInt(source.charCodeAt(i))) * 0x100000001b3n) & M;
  hash = (hash ^ (BigInt(Math.trunc(at)) & M)) & M;
  let out = '';
  let remaining = hash;
  for (let k = 0; k < 11; k++) {
    out += ID_ALPHABET[Number((remaining >> BigInt(k * 5)) & 0x1fn)];
    remaining = (remaining ^ (remaining >> 7n)) & M;
  }
  return out;
}

const oneLine = (v: string) => v.replace(/[\r\n]/g, ' ').trim().slice(0, DESCRIPTION_LIMIT);
const hexOf = (c: string | undefined) => (c && /^#[0-9a-f]{6}$/i.test(c) ? `FF${c.slice(1).toUpperCase()}` : null);

/** The document as a `.nama` file. */
export function writeScriptFile(doc: ScriptDocument): string {
  const lines = [`${PREFIX} nama 1`, `${PREFIX} id: ${doc.id}`, `${PREFIX} name: ${oneLine(doc.name)}`];
  if (doc.description?.trim()) lines.push(`${PREFIX} description: ${oneLine(doc.description)}`);
  lines.push(`${PREFIX} colour: ${hexOf(doc.colour) ?? 'FFD8A848'}`);
  if (doc.tags?.length) lines.push(`${PREFIX} tags: ${doc.tags.map(oneLine).join(', ')}`);
  lines.push(`${PREFIX} pane: ${doc.ownPane ? 'own' : 'price'}`);
  for (const [title, value] of Object.entries(doc.defaults ?? {})) lines.push(`${PREFIX} input: ${oneLine(title)} = ${value}`);
  if (doc.origin) lines.push(`${PREFIX} origin: ${oneLine(doc.origin)}`);
  lines.push(SEPARATOR, doc.source.trimEnd());
  return lines.join('\n') + '\n';
}

/** A `.nama` file read back, or null when the text is not one (no header, no separator, no id or code). */
export function readScriptFile(text: string): ScriptDocument | null {
  const lines = text.split(/\r?\n/);
  if (!lines[0]?.trim().startsWith(`${PREFIX} nama `)) return null;
  const sep = lines.findIndex((l) => l.trim() === SEPARATOR);
  if (sep < 0) return null;
  const doc: ScriptDocument = { id: '', name: '', source: '' };
  const defaults: Record<string, number> = {};
  let colourSeen = false;
  for (const line of lines.slice(1, sep)) {
    const body = line.trim().replace(/^\/\//, '').trim();
    const at = body.indexOf(':');
    if (at < 0) continue;
    const key = body.slice(0, at).trim();
    const value = body.slice(at + 1).trim();
    if (key === 'id') doc.id = value;
    else if (key === 'name') doc.name = value;
    else if (key === 'description') doc.description = value;
    else if (key === 'colour') {
      const hex = /^(?:[0-9a-f]{2})?([0-9a-f]{6})$/i.exec(value);
      if (hex) { doc.colour = `#${hex[1]!.toUpperCase()}`; colourSeen = true; }
    } else if (key === 'tags') doc.tags = value.split(',').map((t) => t.trim()).filter(Boolean);
    else if (key === 'pane') doc.ownPane = value === 'own';
    else if (key === 'origin') doc.origin = value || null;
    else if (key === 'input') {
      const eq = value.lastIndexOf('=');
      const title = eq < 0 ? '' : value.slice(0, eq).trim();
      const n = eq < 0 ? NaN : Number(value.slice(eq + 1).trim());
      if (title && Number.isFinite(n)) defaults[title] = n;
    }
  }
  // The phone writes its own gold when nobody chose a colour; that is "as drawn" here.
  if (colourSeen && doc.colour === '#D8A848') delete doc.colour;
  doc.source = lines.slice(sep + 1).join('\n').trim();
  if (Object.keys(defaults).length) doc.defaults = defaults;
  if (!doc.id || !doc.source) return null;
  return doc;
}

// ── On the board ────────────────────────────────────────────────────────────────────────────────

const HEADER = '// nama ';

/** A post that shares the document: a sentence the feed can show, the description, then the file. */
export function sharePost(doc: ScriptDocument, english = false): string {
  const head = english ? `A script of mine: ${doc.name}` : `یک اسکریپت از من: ${doc.name}`;
  return [head, ...(doc.description?.trim() ? [doc.description.trim()] : []), '', writeScriptFile(doc)].join('\n');
}

/** A post in its two halves: what the author wrote, and the file they attached (null on an ordinary post). */
export function splitPost(body: string): { prose: string; code: string | null } {
  const start = body.indexOf(HEADER);
  if (start < 0 || !readScriptFile(body.slice(start))) return { prose: body, code: null };
  return { prose: body.slice(0, start).trimEnd(), code: body.slice(start) };
}

/** The script inside a post, or null. */
export function scriptIn(body: string): ScriptDocument | null {
  const start = body.indexOf(HEADER);
  return start < 0 ? null : readScriptFile(body.slice(start));
}

const URL_RE = /(https?:\/\/|www\.)\S+|\b[a-z0-9-]+\.(com|ir|org|net|me|io)\b/i;
const PHONE_RE = /[0-9۰-۹]{10,}/;
const HANDLE_RE = /(^|[\s(،,])@[A-Za-z][A-Za-z0-9_]{3,}/;

/** What the board will refuse in a body: a link, a phone number, a messenger handle. */
export function shareRefusals(body: string): ('link' | 'number' | 'handle')[] {
  const out: ('link' | 'number' | 'handle')[] = [];
  if (URL_RE.test(body)) out.push('link');
  if (PHONE_RE.test(body)) out.push('number');
  if (HANDLE_RE.test(body)) out.push('handle');
  return out;
}

// ── Installed from the board (this browser's own, and nobody else's) ─────────────────────────────

const INSTALLED_KEY = 'pc.script.installed.v1';
export function installedScripts(): string[] {
  try { const v = JSON.parse(localStorage.getItem(INSTALLED_KEY) ?? '[]') as unknown; return Array.isArray(v) ? v.filter((x): x is string => typeof x === 'string') : []; } catch { return []; }
}
export function noteInstalled(id: string): void {
  const have = installedScripts();
  if (have.includes(id)) return;
  try { localStorage.setItem(INSTALLED_KEY, JSON.stringify([...have, id].slice(-200))); } catch { /* blocked */ }
}
