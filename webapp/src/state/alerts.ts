// Price alerts. Every alert is watched here, in the browser, against the live prices (the phone's
// GuestAlertMarketSource does the same); a signed-in reader's plain price alert is also created on
// the platform that owns the symbol, so it fires on the phone and in Telegram when this tab is
// closed.
//   TradeYar     POST/GET/PATCH/DELETE /up/tradeyar/api/mobile/v1/alerts   (any *USDT)
//   CoinePro-FX  POST/GET/PATCH/DELETE /up/coineprofx/api/user/mobile/alerts (the fixed 19)
//
// What TradingView's alert dialog and the old Pro-Chart offered, together: crossing (either way, up
// or down), greater / less than, entering / exiting / inside / outside a channel, moving up or down
// by a value or a per cent over N bars; on the price (last, bid, ask, the bar's high or low), on a
// trend line (the level slides with time) or on any catalogue indicator's output; a second AND
// condition; once, once per bar, once per bar close, once per minute or every time (with a
// cool-down); an expiry; a name and a message with {{placeholders}}; a sound and its length. Every
// firing goes into the log.
//
// And the phone's (feature/alerts, core/notifications, core/webhook): up to five conditions that
// must all hold; once a day, and «until I see it» every N minutes; the 24-hour change over or
// under a per cent; a NamaScript `alertcondition`; every firing posted to the reader's webhooks;
// and each alert's own history of what it did (alertAudit.ts).
import { create } from 'zustand';
import { useMarket } from './market';
import { authed } from '../data/auth';
import { backendOf } from '../data/symbols';
import { useSession } from '../data/session';
import { uuid } from '../data/http';
import { intervalSeconds } from '../data/intervals';
import { barsFor } from '../data/symbolStats';
import { lastQuote, watchQuote } from '../data/quote';
import { indicatorSeries } from '../data/technicals';
import { inQuietHours, playSound, readNotify, type SoundId } from './notify';
import { useSaved } from '../data/saved';
import { SAVED_TICKERS } from '../data/prices';
import { boundsAt, findDrawing, BAND_ALERT_TYPES, LINE_ALERT_TYPES } from './drawingLinks';
import type { DrawingObject } from '../engine/drawings.js';
import { toolName } from '../chart/tools';
import { useAlertAudit, saveLine, type AuditEvent, type AuditView, type ChangeKey, type DeliveryReason } from './alertAudit';
import { useWebhooks, failed as hookFailed, timeframeName } from './webhooks';
import { runScript } from '../engine/namascript.js';

export type AlertCondition =
  | 'above' | 'below' | 'cross' | 'cross_up' | 'cross_down'
  | 'enter_channel' | 'exit_channel' | 'inside_channel' | 'outside_channel'
  | 'move_up_pct' | 'move_down_pct' | 'move_up' | 'move_down'
  | 'change24_over' | 'change24_under';
export type AlertFreq = 'once' | 'per_bar' | 'per_bar_close' | 'per_minute' | 'daily' | 'every';
export type PriceField = 'last' | 'bid' | 'ask' | 'close' | 'high' | 'low';
export interface TrendLine { t1: number; p1: number; t2: number; p2: number }
export interface Operand { condition: AlertCondition; value: number; high?: number; bars?: number }

export const CHANNEL: AlertCondition[] = ['enter_channel', 'exit_channel', 'inside_channel', 'outside_channel'];
export const MOVES: AlertCondition[] = ['move_up_pct', 'move_down_pct', 'move_up', 'move_down'];
/** The 24-hour change against a per cent (LocalAlertCondition.CHANGE_24H_OVER / _UNDER). */
export const CHANGE24: AlertCondition[] = ['change24_over', 'change24_under'];
/** At most this many conditions, the first included, and all of them must hold (MultiCondition). */
export const MAX_CONDITIONS = 5;
/** «تا وقتی ببینمش»: the intervals offered, and the default (LocalPriceAlert.DEFAULT_REPEAT_MINUTES). */
export const UNTIL_SEEN_MINUTES = [5, 15, 30, 60];
export const DEFAULT_REPEAT_MINUTES = 15;
const DAY_MS = 24 * 60 * 60 * 1000;

export interface PriceAlert {
  id: string;
  symbol: string;
  condition: AlertCondition;
  /** The level; a channel's lower bound; a move's size. */
  value: number;
  active: boolean;
  createdAt: number;
  triggeredAt?: number;
  serverId?: string;
  /** A channel's upper bound. */
  high?: number;
  /** A move's bar count. */
  bars?: number;
  /** The timeframe the bars, the moves and the indicator are counted on. */
  interval?: string;
  name?: string;
  message?: string;
  freq?: AlertFreq;
  cooldownMs?: number;
  expiresAt?: number;
  expired?: boolean;
  field?: PriceField;
  source?: 'price' | 'line' | 'indicator' | 'script';
  line?: TrendLine;
  indicator?: { key: string; output: string; inputs?: Record<string, number | string | boolean>; label?: string; uid?: string };
  /** The second condition of alerts made before there could be more (read through `extraConditions`). */
  and?: Operand;
  /** The conditions after the first, up to MAX_CONDITIONS − 1; every one must hold. */
  ands?: Operand[];
  /** «تا وقتی ببینمش»: keeps firing every `repeatMinutes` until the reader says they saw it. */
  untilSeen?: boolean;
  repeatMinutes?: number;
  /** When the reader saw it (an until-seen alert stops then). */
  seenAt?: number;
  /** A NamaScript `alertcondition`: the script travels with the alert (AlertTrigger.ScriptCondition). */
  script?: { source: string; condition: string; name: string; inputs?: Record<string, unknown> };
  /** The webhooks it posts to; absent: every webhook that is switched on. */
  webhooks?: string[];
  sound?: SoundId;
  soundSecs?: number;
  popup?: boolean;
  /** Alerts made together on a watchlist share this. */
  group?: string;
  fires?: number;
  lastFiredAt?: number;
  /** The drawing this alert watches (its stable id): the alert follows the drawing when it is edited. */
  drawingId?: string;
  /** That drawing's tool, so the alert can name it and read it as a line, a band or a price. */
  drawingType?: string;
}

export interface AlertLogEntry { id: string; alertId: string; symbol: string; name: string; message: string; price: number; at: number }
export interface AlertPreset { name: string; settings: Partial<PriceAlert> }

const KEY = 'pc.alerts.v1';
const LOG_KEY = 'pc.alerts.log.v1';
const PRESET_KEY = 'pc.alerts.presets.v1';
const CHECKED_KEY = 'pc.alerts.checked.v1';
const readChecked = (): number | null => { try { const v = Number(localStorage.getItem(CHECKED_KEY)); return Number.isFinite(v) && v > 0 ? v : null; } catch { return null; } };
function readArr<T>(key: string): T[] { try { const v = JSON.parse(localStorage.getItem(key) ?? '[]') as unknown; return Array.isArray(v) ? (v as T[]) : []; } catch { return []; } }

export type NewAlert = Partial<PriceAlert> & { symbol: string; condition: AlertCondition; value: number };

interface AlertsState {
  list: PriceAlert[];
  fired: PriceAlert | null;
  log: AlertLogEntry[];
  /** Firings the reader has not looked at yet (the log's badge). */
  unseen: number;
  presets: AlertPreset[];
  /**
   * When a pass last read live prices (ms), or null if none ever has — one time for the whole list,
   * because a pass reads every alert's symbols together (core/notifications AlertReach.kt).
   */
  checkedAt: number | null;
  /** `serverId`: the alert already exists on the account (made by the alert centre); only watch it here. */
  add(symbol: string, condition: AlertCondition, value: number, serverId?: string): void;
  /** A full alert; returns its id. */
  create(a: NewAlert): string;
  /** `silent`: a change the reader did not make here (the drawing it follows moved) writes no history. */
  update(id: string, patch: Partial<PriceAlert>, opts?: { silent?: boolean }): void;
  /** The reader saw it: an until-seen alert stops («دیدم»). */
  acknowledge(id: string): void;
  clone(id: string): string | null;
  remove(id: string): void;
  toggle(id: string): void;
  stopAll(): void;
  restartInactive(): void;
  deleteInactive(): void;
  dismiss(): void;
  clearLog(): void;
  markSeen(): void;
  savePreset(name: string, settings: Partial<PriceAlert>): void;
  deletePreset(name: string): void;
}

function serverPath(symbol: string): string | null {
  // A spread (BTCUSDT/ETHUSDT) is watched in this browser only: no backend knows it.
  if (/[-+*/() ]/.test(symbol)) return null;
  const backend = backendOf(symbol);
  if (!useSession.getState().sessions[backend]) return null;
  // Any CoinePro-FX chart symbol: the server says which it takes, and a refusal leaves the alert
  // watched in this browser.
  return backend === 'tradeyar' ? '/up/tradeyar/api/mobile/v1/alerts' : '/up/coineprofx/api/user/mobile/alerts';
}
/** Only a plain level on the price reaches the platforms; the rest is watched here. */
const serverable = (a: PriceAlert) => (a.source ?? 'price') === 'price' && ['above', 'below', 'cross', 'cross_up', 'cross_down'].includes(a.condition) && !extraConditions(a).length && (a.field ?? 'last') === 'last'
  // Once a day and until-seen have no server spelling; the platforms would fire every time.
  && !a.untilSeen && (a.freq ?? 'once') !== 'daily';

/** What the editor shows of an alert, for the history's «what changed» (AlertAuditTrail.changes). */
export function auditView(a: PriceAlert): AuditView {
  return {
    symbol: a.symbol,
    scope: a.group ?? '',
    condition: [describeAlert(a, true), a.field ?? 'last', a.interval ?? '', a.source ?? 'price', ...extraConditions(a).map((o) => `${o.condition} ${o.value} ${o.high ?? ''} ${o.bars ?? ''}`)].join('|'),
    frequency: `${a.freq ?? 'once'}|${a.freq === 'every' ? a.cooldownMs ?? 0 : ''}|${a.untilSeen ? a.repeatMinutes ?? DEFAULT_REPEAT_MINUTES : ''}`,
    channels: `${a.popup !== false}|${a.webhooks ? a.webhooks.join(',') : '*'}`,
    loudness: `${a.sound ?? 'chime'}|${a.soundSecs ?? 0}`,
    message: a.message ?? '',
    active: a.active,
    spent: Boolean(a.triggeredAt),
  };
}
/** One line about the reader's own act: no price, because none of these is a reading of the market. */
const audit = (a: PriceAlert, event: AuditEvent, extra: { changed?: ChangeKey[] } = {}) =>
  useAlertAudit.getState().record({ alertId: a.id, event, at: Date.now(), symbol: a.symbol, timeframe: a.interval, ...extra });

export const useAlerts = create<AlertsState>((set, get) => {
  const save = () => { try { localStorage.setItem(KEY, JSON.stringify(get().list)); } catch { /* ignore */ } };
  const toServer = (a: PriceAlert) => {
    const path = a.serverId || !serverable(a) ? null : serverPath(a.symbol);
    if (!path) return;
    const backend = backendOf(a.symbol);
    const body: Record<string, unknown> = { symbol: a.symbol, condition: a.condition, value: a.value, trigger: (a.freq ?? 'once') === 'once' ? 'once' : 'recurring' };
    if (a.expiresAt && backend === 'coineprofx') body.expires_at_ms = a.expiresAt;
    void authed<{ alert?: { id?: string | number } }>(backend, path, { body }).then((r) => {
      const sid = r.alert?.id;
      if (sid != null) { set({ list: get().list.map((x) => (x.id === a.id ? { ...x, serverId: String(sid) } : x)) }); save(); }
    }).catch(() => undefined);
  };
  const patchServer = (a: PriceAlert, body: Record<string, unknown>) => {
    const path = a.serverId && serverPath(a.symbol);
    if (path) void authed(backendOf(a.symbol), `${path}/${encodeURIComponent(a.serverId!)}`, { method: 'PATCH', body }).catch(() => undefined);
  };
  const deleteServer = (a: PriceAlert) => {
    const path = a.serverId && serverPath(a.symbol);
    if (path) void authed(backendOf(a.symbol), `${path}/${encodeURIComponent(a.serverId!)}`, { method: 'DELETE' }).catch(() => undefined);
  };
  return {
    list: readArr<PriceAlert>(KEY),
    fired: null,
    log: readArr<AlertLogEntry>(LOG_KEY),
    unseen: 0,
    presets: readArr<AlertPreset>(PRESET_KEY),
    checkedAt: readChecked(),
    add: (symbol, condition, value, serverId) => { get().create({ symbol, condition, value, serverId }); },
    create: (input) => {
      const a: PriceAlert = { ...input, id: uuid(), active: true, createdAt: Date.now() } as PriceAlert;
      for (const k of Object.keys(a) as (keyof PriceAlert)[]) if (a[k] === undefined) delete a[k];
      set({ list: [a, ...get().list] }); save();
      audit(a, 'created');
      toServer(a);
      return a.id;
    },
    update: (id, patch, opts) => {
      const before = get().list.find((x) => x.id === id);
      if (!before) return;
      const next: PriceAlert = { ...before, ...patch, id };
      for (const k of Object.keys(next) as (keyof PriceAlert)[]) if (next[k] === undefined) delete next[k];
      set({ list: get().list.map((x) => (x.id === id ? next : x)) }); save();
      if (!opts?.silent) { const line = saveLine(auditView(before), auditView(next)); if (line) audit(next, line.event, { changed: line.changed }); }
      lastSeen.delete(id); prevInd.delete(id); closes.delete(id);
      if (before.serverId) {
        if (serverable(next)) patchServer(next, { active: next.active, value: next.value, condition: next.condition, trigger: (next.freq ?? 'once') === 'once' ? 'once' : 'recurring' });
        else { deleteServer(before); set({ list: get().list.map((x) => (x.id === id ? { ...x, serverId: undefined } : x)) }); save(); }
      } else toServer(next);
    },
    clone: (id) => {
      const a = get().list.find((x) => x.id === id);
      if (!a) return null;
      const { id: _i, serverId: _s, triggeredAt: _t, fires: _f, lastFiredAt: _l, expired: _e, createdAt: _c, active: _a, ...rest } = a;
      return get().create({ ...rest, name: a.name ? `${a.name} (2)` : undefined });
    },
    remove: (id) => {
      const a = get().list.find((x) => x.id === id);
      set({ list: get().list.filter((x) => x.id !== id) }); save();
      if (a) { audit(a, 'deleted'); deleteServer(a); }
    },
    acknowledge: (id) => {
      const a = get().list.find((x) => x.id === id);
      if (!a?.untilSeen || a.seenAt) return;
      set({ list: get().list.map((x) => (x.id === id ? { ...x, seenAt: Date.now(), active: false } : x)) }); save();
      patchServer(a, { active: false });
    },
    toggle: (id) => {
      const a = get().list.find((x) => x.id === id);
      if (!a) return;
      const active = !a.active;
      const expired = active && a.expiresAt && a.expiresAt < Date.now() ? undefined : a.expired;
      set({ list: get().list.map((x) => (x.id === id ? { ...x, active, triggeredAt: x.active ? x.triggeredAt : undefined, expired, expiresAt: active && a.expired ? undefined : x.expiresAt, seenAt: active ? undefined : x.seenAt } : x)) }); save();
      audit(a, active ? 'armed' : 'snoozed');
      patchServer(a, { active });
    },
    stopAll: () => { const on = get().list.filter((a) => a.active); set({ list: get().list.map((a) => ({ ...a, active: false })) }); save(); on.forEach((a) => { audit(a, 'snoozed'); patchServer(a, { active: false }); }); },
    restartInactive: () => {
      const off = get().list.filter((a) => !a.active);
      set({ list: get().list.map((a) => (a.active ? a : { ...a, active: true, triggeredAt: undefined, expired: undefined, seenAt: undefined, expiresAt: a.expired ? undefined : a.expiresAt })) }); save();
      off.forEach((a) => { lastSeen.delete(a.id); audit(a, 'armed'); patchServer(a, { active: true }); });
    },
    deleteInactive: () => { const off = get().list.filter((a) => !a.active); set({ list: get().list.filter((a) => a.active) }); save(); off.forEach((a) => { audit(a, 'deleted'); deleteServer(a); }); },
    dismiss: () => set({ fired: null }),
    clearLog: () => { set({ log: [], unseen: 0 }); try { localStorage.setItem(LOG_KEY, '[]'); } catch { /* ignore */ } },
    markSeen: () => set({ unseen: 0 }),
    savePreset: (name, settings) => {
      const presets = [{ name, settings }, ...get().presets.filter((p) => p.name !== name)].slice(0, 30);
      set({ presets }); try { localStorage.setItem(PRESET_KEY, JSON.stringify(presets)); } catch { /* ignore */ }
    },
    deletePreset: (name) => {
      const presets = get().presets.filter((p) => p.name !== name);
      set({ presets }); try { localStorage.setItem(PRESET_KEY, JSON.stringify(presets)); } catch { /* ignore */ }
    },
  };
});

/**
 * «هنوز بررسی نشده»: the ids of alerts created since the last pass that read live prices. Every
 * active alert when no pass ever succeeded; never a paused one (the reader told it not to watch),
 * never one with no creation time (rows from before the field existed).
 */
export function uncheckedIds(list: Pick<PriceAlert, 'id' | 'active' | 'createdAt'>[], checkedAt: number | null): Set<string> {
  return new Set(list.filter((a) => a.active && (a.createdAt ?? 0) > 0 && (checkedAt == null || a.createdAt > checkedAt)).map((a) => a.id));
}

// ── Describing ────────────────────────────────────────────────────────────────────────────────────
export const CONDITION_NAMES: Record<AlertCondition, { fa: string; en: string; sym: string }> = {
  cross: { fa: 'عبور از', en: 'Crossing', sym: '⇄' },
  cross_up: { fa: 'عبور رو به بالا از', en: 'Crossing up', sym: '↗' },
  cross_down: { fa: 'عبور رو به پایین از', en: 'Crossing down', sym: '↘' },
  above: { fa: 'بالاتر از', en: 'Greater than', sym: '≥' },
  below: { fa: 'پایین‌تر از', en: 'Less than', sym: '≤' },
  enter_channel: { fa: 'ورود به کانال', en: 'Entering channel', sym: '⇥' },
  exit_channel: { fa: 'خروج از کانال', en: 'Exiting channel', sym: '⇤' },
  inside_channel: { fa: 'داخل کانال', en: 'Inside channel', sym: '▭' },
  outside_channel: { fa: 'بیرون کانال', en: 'Outside channel', sym: '▯' },
  move_up_pct: { fa: 'رشد درصدی', en: 'Moving up %', sym: '▲%' },
  move_down_pct: { fa: 'افت درصدی', en: 'Moving down %', sym: '▼%' },
  move_up: { fa: 'رشد به اندازه‌ی', en: 'Moving up', sym: '▲' },
  move_down: { fa: 'افت به اندازه‌ی', en: 'Moving down', sym: '▼' },
  change24_over: { fa: '۲۴ ساعته بالای', en: '24h over', sym: '24h▲' },
  change24_under: { fa: '۲۴ ساعته زیر', en: '24h under', sym: '24h▼' },
};
export const FREQ_NAMES: Record<AlertFreq, { fa: string; en: string }> = {
  once: { fa: 'فقط یک بار', en: 'Only once' },
  per_bar: { fa: 'یک بار در هر کندل', en: 'Once per bar' },
  per_bar_close: { fa: 'یک بار در بسته شدن کندل', en: 'Once per bar close' },
  per_minute: { fa: 'یک بار در دقیقه', en: 'Once per minute' },
  daily: { fa: 'روزی یک بار', en: 'Once a day' },
  every: { fa: 'هر بار', en: 'Every time' },
};

/** A trend line's level at a moment (seconds), extended beyond its anchors. */
export function lineLevel(l: TrendLine, tSec: number): number {
  if (l.t2 === l.t1) return l.p2;
  return l.p1 + ((l.p2 - l.p1) * (tSec - l.t1)) / (l.t2 - l.t1);
}

/** The conditions after the first: the list, or the one `and` of an older alert. */
export const extraConditions = (a: Pick<PriceAlert, 'and' | 'ands'>): Operand[] => a.ands ?? (a.and ? [a.and] : []);

/** The alert's own words: "BTCUSDT Crossing 90,000". */
export function describeAlert(a: Pick<PriceAlert, 'symbol' | 'condition' | 'value' | 'high' | 'bars' | 'source' | 'indicator' | 'line'> & { drawingType?: string; script?: PriceAlert['script'] }, fa: boolean, fmt: (v: number) => string = String): string {
  if (a.source === 'script' && a.script) return fa ? `اسکریپت «${a.script.name}»: ${a.script.condition}` : `Script "${a.script.name}": ${a.script.condition}`;
  // The 24-hour change (the phone's sentences): «تغییر ۲۴ ساعته‌ی BTCUSDT بیشتر از 5%».
  if (a.condition === 'change24_over') return fa ? `تغییر ۲۴ ساعته‌ی ${a.symbol} بیشتر از ${a.value}%` : `${a.symbol} 24h change over ${a.value}%`;
  if (a.condition === 'change24_under') return fa ? `تغییر ۲۴ ساعته‌ی ${a.symbol} کمتر از منفی ${a.value}%` : `${a.symbol} 24h change under minus ${a.value}%`;
  const c = CONDITION_NAMES[a.condition];
  const drawn = a.source === 'line' && a.drawingType ? toolName(a.drawingType, fa ? 'fa' : 'en') : null;
  const what = a.source === 'indicator' ? (a.indicator?.label ?? a.indicator?.key ?? '').toString() : a.source === 'line' ? (drawn ?? (fa ? 'خط روند' : 'Trend line')) : a.symbol;
  const word = fa ? c.fa : c.en;
  if (CHANNEL.includes(a.condition)) return `${what} ${word} ${fmt(a.value)} – ${fmt(a.high ?? a.value)}`;
  if (a.condition === 'move_up_pct' || a.condition === 'move_down_pct') return `${what} ${word} ${a.value}% / ${a.bars ?? 1} ${fa ? 'کندل' : 'bars'}`;
  if (a.condition === 'move_up' || a.condition === 'move_down') return `${what} ${word} ${fmt(a.value)} / ${a.bars ?? 1} ${fa ? 'کندل' : 'bars'}`;
  if (a.source === 'line') return `${what} ${word}`;
  return `${what} ${word} ${fmt(a.value)}`;
}

/** Fills a message's {{placeholders}} from the moment the alert fired. */
export function fillMessage(template: string, ctx: { symbol: string; price: number; interval?: string; open?: number; high?: number; low?: number; volume?: number; plot?: number | null; time?: number; name?: string }): string {
  const now = new Date(ctx.time ?? Date.now());
  const map: Record<string, string> = {
    ticker: ctx.symbol, symbol: ctx.symbol, close: String(ctx.price), price: String(ctx.price),
    open: ctx.open != null ? String(ctx.open) : '', high: ctx.high != null ? String(ctx.high) : '', low: ctx.low != null ? String(ctx.low) : '',
    volume: ctx.volume != null ? String(ctx.volume) : '', interval: ctx.interval ?? '', plot_0: ctx.plot != null ? String(ctx.plot) : '',
    time: now.toISOString().replace(/\.\d+Z$/, 'Z'), timenow: new Date().toISOString().replace(/\.\d+Z$/, 'Z'), name: ctx.name ?? '',
  };
  return template.replace(/\{\{\s*([a-z_0-9]+)\s*\}\}/gi, (m, k: string) => map[k.toLowerCase()] ?? m);
}

/** Whether a condition holds, from the previous and the current value (and a move's reference). */
export function conditionHolds(op: Operand, prev: number | null, cur: number, ref?: number | null): boolean {
  const v = op.value;
  const lo = Math.min(v, op.high ?? v), hi = Math.max(v, op.high ?? v);
  const inside = (x: number) => x >= lo && x <= hi;
  switch (op.condition) {
    case 'above': return cur >= v;
    case 'below': return cur <= v;
    case 'cross': return prev != null && ((prev < v && cur >= v) || (prev > v && cur <= v));
    case 'cross_up': return prev != null && prev < v && cur >= v;
    case 'cross_down': return prev != null && prev > v && cur <= v;
    case 'enter_channel': return prev != null && !inside(prev) && inside(cur);
    case 'exit_channel': return prev != null && inside(prev) && !inside(cur);
    case 'inside_channel': return inside(cur);
    case 'outside_channel': return !inside(cur);
    case 'move_up_pct': return ref != null && ref > 0 && ((cur - ref) / ref) * 100 >= v;
    case 'move_down_pct': return ref != null && ref > 0 && ((ref - cur) / ref) * 100 >= v;
    case 'move_up': return ref != null && cur - ref >= v;
    case 'move_down': return ref != null && ref - cur >= v;
    // `cur` is the 24-hour change in per cent here; a missing figure is not a flat market.
    case 'change24_over': return Number.isFinite(cur) && cur >= v;
    case 'change24_under': return Number.isFinite(cur) && cur <= -v;
  }
}

// ── Watching ────────────────────────────────────────────────────────────────────────────────────
const lastSeen = new Map<string, number>();
const prevInd = new Map<string, number>();
const bar = new Map<string, { start: number; high: number; low: number; close: number; prevClose: number | null }>();
const refs = new Map<string, { start: number; ref: number | null }>();
const quoteStops = new Map<string, () => void>();
const closes = new Map<string, { start: number; last: number; prevClose: number | null }>();

function notify(a: PriceAlert, price: number, plot: number | null = null) {
  const now = Date.now();
  const name = a.name || describeAlert(a, false);
  const tk = useMarket.getState().tickers[a.symbol];
  const message = fillMessage(a.message || `${a.symbol} ${describeAlert(a, false)} (${price})`, { symbol: a.symbol, price, interval: a.interval, high: tk?.high, low: tk?.low, volume: tk?.volume, plot, time: now, name });
  const st = useAlerts.getState();
  const log = [{ id: uuid(), alertId: a.id, symbol: a.symbol, name, message, price, at: now }, ...st.log].slice(0, 300);
  useAlerts.setState({ log, unseen: st.unseen + 1 });
  try { localStorage.setItem(LOG_KEY, JSON.stringify(log)); } catch { /* ignore */ }
  const record = useAlertAudit.getState().record;
  const base = { alertId: a.id, symbol: a.symbol, at: now, price, timeframe: a.interval };
  // The decision first, then whether it reached anybody (AlertEvaluator's order).
  record({ ...base, event: 'fired' });
  postWebhooks(a, base, a.message ? message : '');
  const prefs = readNotify();
  if (inQuietHours(new Date(), prefs)) { record({ ...base, event: 'delivery_failed', reasons: ['suppressed'] }); return; }
  if (prefs.sound) playSound(a.sound ?? 'chime', a.soundSecs ?? 0);
  const reasons: DeliveryReason[] = [];
  let shown = false;
  try {
    if ('Notification' in window && Notification.permission === 'granted') {
      const n = new Notification(name, { body: message, tag: a.id, icon: `${import.meta.env.BASE_URL}generated/app-icons/favicon-32.png` });
      shown = true;
      // Opening it is seeing it: an until-seen alert stops.
      n.onclick = () => { try { window.focus(); } catch { /* ignore */ } useAlerts.getState().acknowledge(a.id); };
    } else reasons.push('no_permission');
  } catch { reasons.push('post_failed'); }
  // The toast in this tab counts only while the tab is on screen.
  const onPage = a.popup !== false && typeof document !== 'undefined' && document.visibilityState === 'visible';
  if (shown || onPage) record({ ...base, event: 'delivered' });
  else {
    if (a.popup !== false) reasons.push('tab_hidden');
    record({ ...base, event: 'delivery_failed', reasons: reasons.length ? reasons : ['no_channels'] });
  }
}

/**
 * Posts a firing to the reader's webhooks and writes the ones that did not arrive into the alert's
 * history beside the firing (AlertEvaluator.postWebhooks); a delivery that worked lives in the log.
 */
function postWebhooks(a: PriceAlert, base: { alertId: string; symbol: string; at: number; price: number; timeframe?: string }, message: string) {
  const hooks = useWebhooks.getState();
  if (!hooks.targets.some((t) => t.enabled && (!a.webhooks || a.webhooks.includes(t.id)))) return;
  void hooks.dispatch({ alertId: a.id, symbol: a.symbol, firedAt: base.at, message, price: base.price, timeframe: timeframeName(a.interval) }, a.webhooks)
    .then((attempts) => {
      const bad = attempts.filter(hookFailed);
      if (bad.length) useAlertAudit.getState().record(bad.map((x) => ({ ...base, event: 'delivery_failed' as const, hook: { name: x.targetName, outcome: x.outcome, reason: x.reason } })));
    })
    .catch(() => undefined);
}

const barStart = (interval: string | undefined, now: number) => { const s = intervalSeconds(interval ?? '1') * 1000; return Math.floor(now / s) * s; };

/** The field's value for an alert, and its bar bookkeeping. */
function fieldValue(a: PriceAlert, last: number, now: number): number | null {
  const key = `${a.symbol}|${a.interval ?? '1'}`;
  const start = barStart(a.interval, now);
  let b = bar.get(key);
  if (!b || b.start !== start) { b = { start, high: last, low: last, close: last, prevClose: b ? b.close : null }; bar.set(key, b); }
  b.high = Math.max(b.high, last); b.low = Math.min(b.low, last); b.close = last;
  switch (a.field ?? 'last') {
    case 'bid': return lastQuote(a.symbol)?.bid ?? null;
    case 'ask': return lastQuote(a.symbol)?.ask ?? null;
    case 'high': return b.high;
    case 'low': return b.low;
    default: return last;
  }
}

/** A move's reference: the close `bars` bars back on the alert's timeframe, fetched once a bar. */
function moveRef(a: PriceAlert, bars: number | undefined, now: number): number | null {
  const start = barStart(a.interval ?? '60', now);
  const key = `${a.id}|${bars ?? 1}`;
  const r = refs.get(key);
  if (r && r.start === start) return r.ref;
  refs.set(key, { start, ref: r?.ref ?? null });
  void barsFor(a.symbol, a.interval ?? '60', 30_000).then((list) => {
    const n = Math.max(1, bars ?? 1);
    const ref = list.length > n ? list[list.length - 1 - n]!.c : list[0]?.c ?? null;
    refs.set(key, { start, ref });
  });
  return r?.ref ?? null;
}

/** Applies the trigger frequency to a hit: whether it fires now, and the alert after it. */
export function fire(a: PriceAlert, now: number): PriceAlert | null {
  const freq = a.freq ?? 'once';
  const last = a.lastFiredAt ?? 0;
  if (a.untilSeen) {
    // Until the reader says they saw it, every N minutes (clamped to a minute … a day).
    if (a.seenAt) return null;
    const every = Math.min(24 * 60, Math.max(1, a.repeatMinutes ?? DEFAULT_REPEAT_MINUTES)) * 60_000;
    if (last && now - last < every) return null;
  }
  if (freq === 'per_bar' && last && barStart(a.interval, last) === barStart(a.interval, now)) return null;
  if (freq === 'per_minute' && now - last < 60_000) return null;
  if (freq === 'daily' && last && now - last < DAY_MS) return null;
  if (freq === 'every' && now - last < (a.cooldownMs ?? 0)) return null;
  return { ...a, active: freq === 'once' && !a.untilSeen ? false : a.active, triggeredAt: now, lastFiredAt: now, fires: (a.fires ?? 0) + 1 };
}

/** Every condition after the first holds (they are ANDed). */
function extrasHold(a: PriceAlert, prev: number | null, cur: number, now: number): boolean {
  return extraConditions(a).every((op) => conditionHolds(op, prev, cur, MOVES.includes(op.condition) ? moveRef(a, op.bars, now) : null));
}

function evaluate(a: PriceAlert, tkLast: number, now: number, changePct?: number): { hit: boolean; value: number } | null {
  if ((a.source ?? 'price') === 'indicator' || a.source === 'script') return null;
  const cur = fieldValue(a, tkLast, now);
  if (cur == null) return null;
  const prev = lastSeen.get(a.id) ?? null;
  lastSeen.set(a.id, cur);
  if (CHANGE24.includes(a.condition)) {
    const pct = changePct ?? NaN;
    return { hit: conditionHolds({ condition: a.condition, value: a.value }, null, pct) && extrasHold(a, prev, cur, now), value: cur };
  }
  if (a.freq === 'per_bar_close') {
    // Judged once, on the close of each bar: the closed bar's last price against the one before.
    const start = barStart(a.interval, now);
    const st = closes.get(a.id);
    if (!st) { closes.set(a.id, { start, last: cur, prevClose: null }); return { hit: false, value: cur }; }
    if (st.start === start) { st.last = cur; return { hit: false, value: cur }; }
    const closed = st.last;
    closes.set(a.id, { start, last: cur, prevClose: closed });
    return { hit: test(a, st.prevClose, closed, now), value: closed };
  }
  return { hit: test(a, prev, cur, now), value: cur };
}


/**
 * A drawing alert's hit, read from the drawing as it is now (the line was maybe moved since the
 * alert was made). `null`: not a drawing alert, or the drawings cannot be read — the stored
 * geometry answers. A drawing that is gone never fires.
 */
function drawingHit(a: PriceAlert, prev: number | null, cur: number, now: number, ref: number | null): boolean | null {
  if (!a.drawingId) return null;
  const d = findDrawing(a.symbol, a.drawingId);
  if (d === null) return null;
  if (!d) return false;
  const b = boundsAt(d, now / 1000);
  if (!b.length) return false;
  const lo = b[0]!, hi = b[b.length - 1]!;
  if (CHANNEL.includes(a.condition)) return conditionHolds({ condition: a.condition, value: lo, high: hi }, prev, cur, ref);
  if (a.condition === 'above') return conditionHolds({ condition: 'above', value: hi }, prev, cur, ref);
  if (a.condition === 'below') return conditionHolds({ condition: 'below', value: lo }, prev, cur, ref);
  // A crossing of a shape is a crossing of any of its boundaries (price entering or leaving it).
  return b.some((v) => conditionHolds({ condition: a.condition, value: v, bars: a.bars }, prev, cur, ref));
}

function test(a: PriceAlert, prev: number | null, cur: number, now: number): boolean {
  const viaDrawing = MOVES.includes(a.condition) ? null : drawingHit(a, prev, cur, now, null);
  if (viaDrawing != null) return viaDrawing && extrasHold(a, prev, cur, now);
  const op: Operand = a.source === 'line' && a.line ? { condition: a.condition, value: lineLevel(a.line, now / 1000) } : { condition: a.condition, value: a.value, high: a.high, bars: a.bars };
  const ref = MOVES.includes(a.condition) ? moveRef(a, a.bars, now) : null;
  return conditionHolds(op, prev, cur, ref) && extrasHold(a, prev, cur, now);
}

/** Whether a script's `alertcondition` named `condition` holds on the bar that opened at `barTime`. */
export function scriptConditionHolds(alerts: { msg: string; bars: number[]; dynamic?: boolean }[] | undefined, condition: string, barTime: number): boolean {
  const al = (alerts ?? []).find((x) => !x.dynamic && x.msg === condition);
  return Boolean(al && al.bars.includes(barTime));
}

let watching = false;
export function startAlertWatch(): void {
  if (watching) return;
  watching = true;
  const commit = (next: PriceAlert[]) => {
    useAlerts.setState({ list: next });
    try { localStorage.setItem(KEY, JSON.stringify(next)); } catch { /* ignore */ }
  };
  let checkedSaved = 0;
  useMarket.subscribe((m, p) => {
    // A pass read live prices: every alert created before it has now been checked at least once.
    if (m.liveAt && m.liveAt !== p.liveAt) {
      useAlerts.setState({ checkedAt: m.liveAt });
      if (m.liveAt - checkedSaved > 15_000) { checkedSaved = m.liveAt; try { localStorage.setItem(CHECKED_KEY, String(m.liveAt)); } catch { /* ignore */ } }
    }
    if (m.tickers === p.tickers) return;
    const st = useAlerts.getState();
    if (!st.list.some((a) => a.active)) return;
    // Saved prices shown before the first poll answers are a picture, not a market: never judged.
    const saved = useSaved.getState().at;
    const now = Date.now();
    let changed = false;
    const next = st.list.map((a) => {
      if (!a.active) return a;
      if (a.expiresAt && a.expiresAt < now) {
        changed = true;
        useAlertAudit.getState().record({ alertId: a.id, event: 'expired', at: now, symbol: a.symbol });
        return { ...a, active: false, expired: true };
      }
      const tk = m.tickers[a.symbol];
      if (!tk) return a;
      if (saved[backendOf(a.symbol) === 'tradeyar' ? SAVED_TICKERS.crypto : SAVED_TICKERS.fx] != null) return a;
      const r = evaluate(a, tk.last, now, tk.changePct);
      if (!r?.hit) return a;
      const after = fire(a, now);
      if (!after) return a;
      changed = true;
      notify(after, r.value);
      useAlerts.setState({ fired: after });
      return after;
    });
    if (changed) commit(next);
  });
  // Bid and ask are polled only while an active alert watches them.
  useAlerts.subscribe((s) => {
    const want = new Set(s.list.filter((a) => a.active && (a.field === 'bid' || a.field === 'ask')).map((a) => a.symbol));
    for (const [sym, stop] of quoteStops) if (!want.has(sym)) { stop(); quoteStops.delete(sym); }
    for (const sym of want) if (!quoteStops.has(sym)) quoteStops.set(sym, watchQuote(sym));
  });
  // Indicator alerts: the indicator over the alert's timeframe, every 15 seconds.
  const scan = async () => {
    const now = Date.now();
    const list = useAlerts.getState().list.filter((a) => a.active && a.source === 'indicator' && a.indicator);
    for (const a of list) {
      const iv = a.interval ?? '60';
      const bars = await barsFor(a.symbol, iv, Math.min(60_000, intervalSeconds(iv) * 500));
      const out = indicatorSeries(a.indicator!.key, bars, a.indicator!.inputs).find((o) => o.id === a.indicator!.output);
      if (!out) continue;
      const vals = out.values;
      const closeOnly = a.freq === 'per_bar_close';
      const at = closeOnly ? vals.length - 2 : vals.length - 1;
      const cur = vals[at];
      if (cur == null || !Number.isFinite(cur)) continue;
      const prev = prevInd.get(a.id) ?? (vals[at - 1] ?? null);
      prevInd.set(a.id, cur);
      if (closeOnly && a.lastFiredAt && barStart(iv, a.lastFiredAt) === barStart(iv, now)) continue;
      const hit = conditionHolds({ condition: a.condition, value: a.value, high: a.high }, prev, cur) && extraConditions(a).every((op) => conditionHolds(op, prev, cur));
      if (!hit) continue;
      const fresh = useAlerts.getState().list.find((x) => x.id === a.id);
      if (!fresh?.active) continue;
      const after = fire(fresh, now);
      if (!after) continue;
      notify(after, useMarket.getState().tickers[a.symbol]?.last ?? cur, cur);
      useAlerts.setState({ fired: after });
      commit(useAlerts.getState().list.map((x) => (x.id === a.id ? after : x)));
    }
    // Script alerts: the reader's script over the alert's timeframe; it fires when the named
    // `alertcondition` holds on the last bar (the last closed one, on bar close).
    const scripts = useAlerts.getState().list.filter((a) => a.active && a.source === 'script' && a.script);
    for (const a of scripts) {
      const iv = a.interval ?? '60';
      const bars = await barsFor(a.symbol, iv, Math.min(60_000, intervalSeconds(iv) * 500));
      const closeOnly = a.freq === 'per_bar_close';
      const at = bars[closeOnly ? bars.length - 2 : bars.length - 1];
      if (!at) continue;
      if (closeOnly && a.lastFiredAt && barStart(iv, a.lastFiredAt) === barStart(iv, now)) continue;
      const r = await runScript(a.script!.source, bars, a.script!.inputs).catch(() => null);
      if (!r?.ok) continue;
      if (!scriptConditionHolds(r.alerts, a.script!.condition, at.t)) continue;
      const fresh = useAlerts.getState().list.find((x) => x.id === a.id);
      if (!fresh?.active) continue;
      const after = fire(fresh, now);
      if (!after) continue;
      notify(after, useMarket.getState().tickers[a.symbol]?.last ?? at.c);
      useAlerts.setState({ fired: after });
      commit(useAlerts.getState().list.map((x) => (x.id === a.id ? after : x)));
    }
  };
  if (typeof window !== 'undefined') setInterval(() => { void scan().catch(() => undefined); }, 15_000);
}

// ── Following the drawings ────────────────────────────────────────────────────────────────────────
/**
 * The geometry a drawing alert keeps of its drawing (its line, its band now, its price): what the
 * dialog and the lists show, and what the platforms hold for a plain level. The watcher re-reads
 * the drawing itself at every evaluation; this keeps the stored copy in step.
 */
export function drawingGeometry(d: DrawingObject, nowSec = Date.now() / 1000): Partial<PriceAlert> | null {
  const b = boundsAt(d, nowSec);
  if (!b.length) return null;
  if (BAND_ALERT_TYPES.includes(d.type) && b.length === 2) return { value: b[0]!, high: b[1]! };
  const p0 = d.p0 as { t: number; p: number } | undefined, p1 = d.p1 as { t: number; p: number } | undefined;
  if (LINE_ALERT_TYPES.includes(d.type) && p0 && p1) return { line: { t1: p0.t, p1: p0.p, t2: p1.t, p2: p1.p }, value: b[0]! };
  return { value: b[0]! };
}

const close = (x: number | undefined, y: number | undefined) => x === y || (x != null && y != null && Math.abs(x - y) <= Math.abs(y) * 1e-9);

/** The chart's drawings of a symbol changed: every alert on one of them takes its new geometry. */
export function followDrawings(symbol: string, list: DrawingObject[]): void {
  const st = useAlerts.getState();
  const nowSec = Date.now() / 1000;
  for (const a of st.list) {
    if (!a.drawingId || a.symbol !== symbol) continue;
    const d = list.find((x) => x.id === a.drawingId);
    if (!d) continue;
    const g = drawingGeometry(d, nowSec);
    if (!g) continue;
    const lineSame = !g.line || (a.line && close(a.line.t1, g.line.t1) && close(a.line.p1, g.line.p1) && close(a.line.t2, g.line.t2) && close(a.line.p2, g.line.p2));
    const bandSame = g.high == null || close(a.high, g.high);
    // A sloped line's level slides with time by itself; only its anchors count as an edit.
    const valueSame = g.line ? true : close(a.value, g.value);
    if (lineSame && bandSame && valueSame) continue;
    const patch: Partial<PriceAlert> = g.line ? { line: g.line } : g;
    st.update(a.id, patch, { silent: true });
  }
}
