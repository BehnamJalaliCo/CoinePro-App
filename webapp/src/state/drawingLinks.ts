// What ties an alert to a line the reader drew (OLD core/notifications AlertDrawingLinks.kt and
// app/alerts AlertEvaluation.kt levelAt/boundsAt), kept pure so the alert watcher, the alert lists
// and the chart all read a drawing the same way.
//
// An alert made from a drawing carries the drawing's stable id (engine/drawings.js gives every
// drawing one). The watcher re-reads the drawing's geometry at each evaluation, so moving the line
// moves the alert; a drawing that is gone resolves to nothing and its alert can never fire — which
// the alert lists then say, in the refusal colour, instead of showing the alert as armed.
//
// One rule is load-bearing: not knowing a symbol's drawings is not the same as knowing it has none.
// When the drawings cannot be read (storage blocked) the answer is «unknown», never «gone».
import type { DrawingObject } from '../engine/drawings.js';

export interface Pt { t: number; p: number }

/** Drawings that have a time and no price: no alert can watch them. */
export const TIME_ONLY_TOOLS = ['vline', 'fibtime', 'fibtimeext', 'daterange', 'timecycles', 'cyclic', 'sine'];
/** Lines an alert follows as a sloped level (TradingView: trend line, ray, extended line). */
export const LINE_ALERT_TYPES = ['trend', 'ray', 'extline', 'infoline', 'angle'];
/** Horizontal drawings an alert reads as one price. */
export const PRICE_ALERT_TYPES = ['hline', 'hray', 'pricelabel', 'crossline', 'pricenote'];
/** Shapes an alert reads as two boundaries (TradingView's shape alerts): enter and exit conditions. */
export const BAND_ALERT_TYPES = ['rect', 'channel', 'flatchannel', 'disjointchannel'];

const isPt = (v: unknown): v is Pt => !!v && typeof (v as Pt).t === 'number' && typeof (v as Pt).p === 'number' && Number.isFinite((v as Pt).p);

/**
 * The drawing's stored anchors, in order — what the small sketch beside an alert is drawn from.
 * A horizontal mark is one anchor (a level), not two equal ones.
 */
export function anchorsOf(d: DrawingObject): Pt[] {
  if (d.type === 'hline' && typeof d.p === 'number') return [{ t: 0, p: d.p }];
  if (PRICE_ALERT_TYPES.includes(d.type) && isPt(d.p0)) return [d.p0];
  if (Array.isArray(d.pts)) return (d.pts as unknown[]).filter(isPt);
  const out: Pt[] = [];
  if (isPt(d.p0)) out.push(d.p0);
  if (isPt(d.p1) && !(out[0] && out[0].t === (d.p1 as Pt).t && out[0].p === (d.p1 as Pt).p)) out.push(d.p1 as Pt);
  if (!out.length && typeof d.t === 'number' && typeof d.p === 'number') out.push({ t: d.t, p: d.p });
  return out;
}

/** A straight line through two anchors at a moment, extended past both; no slope → the first price. */
export function lineAt(a: Pt, b: Pt, tSec: number): number {
  const span = b.t - a.t;
  if (span === 0) return a.p;
  return a.p + ((b.p - a.p) * (tSec - a.t)) / span;
}

/**
 * Every price boundary the drawing has at `tSec` (OLD AlertEvaluation.boundsAt): a rectangle is its
 * top and bottom, a parallel channel its two rails, a flat top/bottom its sloped side and its flat
 * one, a disjoint channel its two lines — sorted low to high. Anything else is its one level; a
 * time-only drawing has none.
 */
export function boundsAt(d: DrawingObject, tSec: number): number[] {
  if (TIME_ONLY_TOOLS.includes(d.type)) return [];
  const pts = Array.isArray(d.pts) ? (d.pts as unknown[]).filter(isPt) : [];
  const sorted = (xs: number[]) => xs.filter(Number.isFinite).sort((x, y) => x - y);
  switch (d.type) {
    case 'rect': if (isPt(d.p0) && isPt(d.p1)) return sorted([d.p0.p, (d.p1 as Pt).p]); break;
    case 'channel': if (pts.length >= 3) {
      const base = lineAt(pts[0]!, pts[1]!, tSec);
      const offset = pts[2]!.p - lineAt(pts[0]!, pts[1]!, pts[2]!.t);
      return sorted([base, base + offset]);
    } break;
    case 'flatchannel': if (pts.length >= 3) return sorted([lineAt(pts[0]!, pts[1]!, tSec), pts[2]!.p]); break;
    case 'disjointchannel': if (pts.length >= 4) return sorted([lineAt(pts[0]!, pts[1]!, tSec), lineAt(pts[2]!, pts[3]!, tSec)]); break;
    default: break;
  }
  const one = levelAt(d, tSec);
  return one == null ? [] : [one];
}

/** The drawing's one level at `tSec`: a line slides with time, a mark keeps its price. */
export function levelAt(d: DrawingObject, tSec: number): number | null {
  if (TIME_ONLY_TOOLS.includes(d.type)) return null;
  if (d.type === 'hline' && typeof d.p === 'number') return d.p;
  if (LINE_ALERT_TYPES.includes(d.type) && isPt(d.p0) && isPt(d.p1)) return lineAt(d.p0, d.p1 as Pt, tSec);
  if (PRICE_ALERT_TYPES.includes(d.type) && isPt(d.p0)) return d.p0.p;
  if ((d.type === 'longshort' || d.type === 'short') && isPt(d.p0)) return d.p0.p;
  const pts = Array.isArray(d.pts) ? (d.pts as unknown[]).filter(isPt) : [];
  const last = pts[pts.length - 1] ?? (isPt(d.p1) ? d.p1 : isPt(d.p0) ? d.p0 : null);
  if (last) return last.p;
  if (typeof d.p === 'number' && Number.isFinite(d.p)) return d.p;
  return null;
}

export type DrawingAlertKind = 'line' | 'band' | 'price';
/** How an alert reads this drawing: a sloped line, two boundaries, or one price. Null: no alert. */
export function alertKindOf(d: DrawingObject): DrawingAlertKind | null {
  if (TIME_ONLY_TOOLS.includes(d.type)) return null;
  if (BAND_ALERT_TYPES.includes(d.type) && boundsAt(d, Date.now() / 1000).length === 2) return 'band';
  if (LINE_ALERT_TYPES.includes(d.type) && isPt(d.p0) && isPt(d.p1)) return 'line';
  return levelAt(d, Date.now() / 1000) == null ? null : 'price';
}

// ── Reading the drawings of any symbol ─────────────────────────────────────────────────────────
const KEY = (symbol: string) => `pc.drawings.v1.${symbol}`;
const cache = new Map<string, { at: number; list: DrawingObject[] }>();
const TTL = 2000;

/** The symbol's drawings as stored, or null when they cannot be read. Cached briefly. */
export function drawingsOf(symbol: string): DrawingObject[] | null {
  const c = cache.get(symbol);
  if (c && Date.now() - c.at < TTL) return c.list;
  try {
    const v = JSON.parse(localStorage.getItem(KEY(symbol)) ?? '[]') as unknown;
    const list = Array.isArray(v) ? (v as DrawingObject[]) : [];
    cache.set(symbol, { at: Date.now(), list });
    return list;
  } catch { return null; }
}

/** The chart saved a symbol's drawings: the watcher reads the new geometry at once. */
export function noteDrawings(symbol: string, list: DrawingObject[]): void {
  cache.set(symbol, { at: Date.now(), list });
}

if (typeof window !== 'undefined') {
  window.addEventListener('storage', (e) => { if (e.key?.startsWith('pc.drawings.v1.')) cache.delete(e.key.slice('pc.drawings.v1.'.length)); });
}

/** The drawing, `undefined` when it is gone, `null` when the drawings cannot be read. */
export function findDrawing(symbol: string, id: string): DrawingObject | undefined | null {
  const list = drawingsOf(symbol);
  if (!list) return null;
  return list.find((d) => d && d.id === id);
}

interface Linked { id: string; symbol: string; drawingId?: string }

/** The alerts watching any of these drawings of one symbol (the symbol compared case-insensitively). */
export function alertsOn<T extends Linked>(alerts: T[], symbol: string, drawingIds: string[]): T[] {
  const want = symbol.trim().toUpperCase();
  const ids = new Set(drawingIds);
  return alerts.filter((a) => a.drawingId && ids.has(a.drawingId) && a.symbol.trim().toUpperCase() === want);
}

/**
 * The ids of the alerts whose drawing is gone. `known` maps a symbol to the ids its drawings still
 * have, and only for symbols that were actually read; an alert on a symbol missing from it gets no
 * verdict.
 */
export function orphanIds(alerts: Linked[], known: Map<string, Set<string>>): Set<string> {
  const out = new Set<string>();
  if (!known.size) return out;
  for (const a of alerts) {
    if (!a.drawingId) continue;
    const present = known.get(a.symbol);
    if (!present) continue;
    if (!present.has(a.drawingId)) out.add(a.id);
  }
  return out;
}

/** `orphanIds` against the stored drawings of every symbol the list mentions. */
export function storedOrphans(alerts: Linked[]): Set<string> {
  const known = new Map<string, Set<string>>();
  for (const s of new Set(alerts.filter((a) => a.drawingId).map((a) => a.symbol))) {
    const list = drawingsOf(s);
    if (list) known.set(s, new Set(list.map((d) => d.id).filter((x): x is string => typeof x === 'string')));
  }
  return orphanIds(alerts, known);
}
