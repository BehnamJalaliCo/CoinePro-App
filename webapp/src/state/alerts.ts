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
import { create } from 'zustand';
import { useMarket } from './market';
import { authed } from '../data/auth';
import { backendOf, FX_SYMBOLS } from '../data/symbols';
import { useSession } from '../data/session';
import { uuid } from '../data/http';
import { intervalSeconds } from '../data/intervals';
import { barsFor } from '../data/symbolStats';
import { lastQuote, watchQuote } from '../data/quote';
import { indicatorSeries } from '../data/technicals';
import { inQuietHours, playSound, readNotify, type SoundId } from './notify';

export type AlertCondition =
  | 'above' | 'below' | 'cross' | 'cross_up' | 'cross_down'
  | 'enter_channel' | 'exit_channel' | 'inside_channel' | 'outside_channel'
  | 'move_up_pct' | 'move_down_pct' | 'move_up' | 'move_down';
export type AlertFreq = 'once' | 'per_bar' | 'per_bar_close' | 'per_minute' | 'every';
export type PriceField = 'last' | 'bid' | 'ask' | 'close' | 'high' | 'low';
export interface TrendLine { t1: number; p1: number; t2: number; p2: number }
export interface Operand { condition: AlertCondition; value: number; high?: number; bars?: number }

export const CHANNEL: AlertCondition[] = ['enter_channel', 'exit_channel', 'inside_channel', 'outside_channel'];
export const MOVES: AlertCondition[] = ['move_up_pct', 'move_down_pct', 'move_up', 'move_down'];

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
  source?: 'price' | 'line' | 'indicator';
  line?: TrendLine;
  indicator?: { key: string; output: string; inputs?: Record<string, number | string | boolean>; label?: string; uid?: string };
  and?: Operand;
  sound?: SoundId;
  soundSecs?: number;
  popup?: boolean;
  /** Alerts made together on a watchlist share this. */
  group?: string;
  fires?: number;
  lastFiredAt?: number;
}

export interface AlertLogEntry { id: string; alertId: string; symbol: string; name: string; message: string; price: number; at: number }
export interface AlertPreset { name: string; settings: Partial<PriceAlert> }

const KEY = 'pc.alerts.v1';
const LOG_KEY = 'pc.alerts.log.v1';
const PRESET_KEY = 'pc.alerts.presets.v1';
function readArr<T>(key: string): T[] { try { const v = JSON.parse(localStorage.getItem(key) ?? '[]') as unknown; return Array.isArray(v) ? (v as T[]) : []; } catch { return []; } }

export type NewAlert = Partial<PriceAlert> & { symbol: string; condition: AlertCondition; value: number };

interface AlertsState {
  list: PriceAlert[];
  fired: PriceAlert | null;
  log: AlertLogEntry[];
  /** Firings the reader has not looked at yet (the log's badge). */
  unseen: number;
  presets: AlertPreset[];
  /** `serverId`: the alert already exists on the account (made by the alert centre); only watch it here. */
  add(symbol: string, condition: AlertCondition, value: number, serverId?: string): void;
  /** A full alert; returns its id. */
  create(a: NewAlert): string;
  update(id: string, patch: Partial<PriceAlert>): void;
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
  const backend = backendOf(symbol);
  if (!useSession.getState().sessions[backend]) return null;
  if (backend === 'coineprofx' && !FX_SYMBOLS.includes(symbol)) return null;
  return backend === 'tradeyar' ? '/up/tradeyar/api/mobile/v1/alerts' : '/up/coineprofx/api/user/mobile/alerts';
}
/** Only a plain level on the price reaches the platforms; the rest is watched here. */
const serverable = (a: PriceAlert) => (a.source ?? 'price') === 'price' && ['above', 'below', 'cross', 'cross_up', 'cross_down'].includes(a.condition) && !a.and && (a.field ?? 'last') === 'last';

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
    add: (symbol, condition, value, serverId) => { get().create({ symbol, condition, value, serverId }); },
    create: (input) => {
      const a: PriceAlert = { ...input, id: uuid(), active: true, createdAt: Date.now() } as PriceAlert;
      for (const k of Object.keys(a) as (keyof PriceAlert)[]) if (a[k] === undefined) delete a[k];
      set({ list: [a, ...get().list] }); save();
      toServer(a);
      return a.id;
    },
    update: (id, patch) => {
      const before = get().list.find((x) => x.id === id);
      if (!before) return;
      const next: PriceAlert = { ...before, ...patch, id };
      set({ list: get().list.map((x) => (x.id === id ? next : x)) }); save();
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
      if (a) deleteServer(a);
    },
    toggle: (id) => {
      const a = get().list.find((x) => x.id === id);
      if (!a) return;
      const active = !a.active;
      const expired = active && a.expiresAt && a.expiresAt < Date.now() ? undefined : a.expired;
      set({ list: get().list.map((x) => (x.id === id ? { ...x, active, triggeredAt: x.active ? x.triggeredAt : undefined, expired, expiresAt: active && a.expired ? undefined : x.expiresAt } : x)) }); save();
      patchServer(a, { active });
    },
    stopAll: () => { const on = get().list.filter((a) => a.active); set({ list: get().list.map((a) => ({ ...a, active: false })) }); save(); on.forEach((a) => patchServer(a, { active: false })); },
    restartInactive: () => {
      const off = get().list.filter((a) => !a.active);
      set({ list: get().list.map((a) => (a.active ? a : { ...a, active: true, triggeredAt: undefined, expired: undefined, expiresAt: a.expired ? undefined : a.expiresAt })) }); save();
      off.forEach((a) => { lastSeen.delete(a.id); patchServer(a, { active: true }); });
    },
    deleteInactive: () => { const off = get().list.filter((a) => !a.active); set({ list: get().list.filter((a) => a.active) }); save(); off.forEach(deleteServer); },
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
};
export const FREQ_NAMES: Record<AlertFreq, { fa: string; en: string }> = {
  once: { fa: 'فقط یک بار', en: 'Only once' },
  per_bar: { fa: 'یک بار در هر کندل', en: 'Once per bar' },
  per_bar_close: { fa: 'یک بار در بسته شدن کندل', en: 'Once per bar close' },
  per_minute: { fa: 'یک بار در دقیقه', en: 'Once per minute' },
  every: { fa: 'هر بار', en: 'Every time' },
};

/** A trend line's level at a moment (seconds), extended beyond its anchors. */
export function lineLevel(l: TrendLine, tSec: number): number {
  if (l.t2 === l.t1) return l.p2;
  return l.p1 + ((l.p2 - l.p1) * (tSec - l.t1)) / (l.t2 - l.t1);
}

/** The alert's own words: "BTCUSDT Crossing 90,000". */
export function describeAlert(a: Pick<PriceAlert, 'symbol' | 'condition' | 'value' | 'high' | 'bars' | 'source' | 'indicator' | 'line'>, fa: boolean, fmt: (v: number) => string = String): string {
  const c = CONDITION_NAMES[a.condition];
  const what = a.source === 'indicator' ? (a.indicator?.label ?? a.indicator?.key ?? '').toString() : a.source === 'line' ? (fa ? 'خط روند' : 'Trend line') : a.symbol;
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
  const prefs = readNotify();
  if (inQuietHours(new Date(), prefs)) return;
  if (prefs.sound) playSound(a.sound ?? 'chime', a.soundSecs ?? 0);
  try {
    if ('Notification' in window && Notification.permission === 'granted') {
      new Notification(name, { body: message, tag: a.id, icon: `${import.meta.env.BASE_URL}generated/app-icons/favicon-32.png` });
    }
  } catch { /* notifications unavailable */ }
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
function fire(a: PriceAlert, now: number): PriceAlert | null {
  const freq = a.freq ?? 'once';
  const last = a.lastFiredAt ?? 0;
  if (freq === 'per_bar' && last && barStart(a.interval, last) === barStart(a.interval, now)) return null;
  if (freq === 'per_minute' && now - last < 60_000) return null;
  if (freq === 'every' && now - last < (a.cooldownMs ?? 0)) return null;
  return { ...a, active: freq === 'once' ? false : a.active, triggeredAt: now, lastFiredAt: now, fires: (a.fires ?? 0) + 1 };
}

function evaluate(a: PriceAlert, tkLast: number, now: number): { hit: boolean; value: number } | null {
  if ((a.source ?? 'price') === 'indicator') return null;
  const cur = fieldValue(a, tkLast, now);
  if (cur == null) return null;
  const prev = lastSeen.get(a.id) ?? null;
  lastSeen.set(a.id, cur);
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


function test(a: PriceAlert, prev: number | null, cur: number, now: number): boolean {
  const op: Operand = a.source === 'line' && a.line ? { condition: a.condition, value: lineLevel(a.line, now / 1000) } : { condition: a.condition, value: a.value, high: a.high, bars: a.bars };
  const ref = MOVES.includes(a.condition) ? moveRef(a, a.bars, now) : null;
  const main = conditionHolds(op, prev, cur, ref);
  if (!main || !a.and) return main;
  const ref2 = MOVES.includes(a.and.condition) ? moveRef(a, a.and.bars, now) : null;
  return conditionHolds(a.and, prev, cur, ref2);
}

let watching = false;
export function startAlertWatch(): void {
  if (watching) return;
  watching = true;
  const commit = (next: PriceAlert[]) => {
    useAlerts.setState({ list: next });
    try { localStorage.setItem(KEY, JSON.stringify(next)); } catch { /* ignore */ }
  };
  useMarket.subscribe((m) => {
    const st = useAlerts.getState();
    if (!st.list.some((a) => a.active)) return;
    const now = Date.now();
    let changed = false;
    const next = st.list.map((a) => {
      if (!a.active) return a;
      if (a.expiresAt && a.expiresAt < now) { changed = true; return { ...a, active: false, expired: true }; }
      const tk = m.tickers[a.symbol];
      if (!tk) return a;
      const r = evaluate(a, tk.last, now);
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
      const hit = conditionHolds({ condition: a.condition, value: a.value, high: a.high }, prev, cur) && (!a.and || conditionHolds(a.and, prev, cur));
      if (!hit) continue;
      const fresh = useAlerts.getState().list.find((x) => x.id === a.id);
      if (!fresh?.active) continue;
      const after = fire(fresh, now);
      if (!after) continue;
      notify(after, useMarket.getState().tickers[a.symbol]?.last ?? cur, cur);
      useAlerts.setState({ fired: after });
      commit(useAlerts.getState().list.map((x) => (x.id === a.id ? after : x)));
    }
  };
  if (typeof window !== 'undefined') setInterval(() => { void scan().catch(() => undefined); }, 15_000);
}
