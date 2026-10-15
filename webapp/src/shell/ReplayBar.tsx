import { useEffect, useMemo, useRef, useState } from 'react';
import type { MouseEventParams, Time } from 'lightweight-charts';
import { useReplay, SPEED_LADDER, UPDATE_STEPS, practiceStats, tradeMoney, type ReplayTrade, type PracticeOrder } from '../state/replay';
import { useChartBus } from '../chart/chartBus';
import { usePrefs, useT } from '../state/prefs';
import { useDrawing } from '../state/drawing';
import { useL } from '../pages/kit';
import { Icon } from '../ui/Icon';
import { Dropdown } from '../ui/Menu';
import { useTradeLines, type TradeLine } from '../chart/tradeLines';
import { intervalLabel } from '../data/intervals';
import { formatPrice, guessDigits } from '../chart/format';
import { faNum } from '../i18n/time';
import type { Candle } from '../data/types';
import './studies.css';

// TradingView's replay controls, floating over the chart's bottom edge, with what the old Pro-Chart
// added: pick the start by clicking a bar, a date, the first bar or a random one (or resume the last
// session); jump to the first or last bar, ±10, step back and forward, play, a scrubber, the replayed
// bar's date, speed and the update interval (bars built from a lower timeframe). Clicking a revealed
// bar goes back to it. Practice trading — Buy/Sell (Shift+B/S), Flatten, and a panel with orders,
// risk sizing, ATR stop and target, partial close, breakeven, reverse and the session's statistics —
// draws its lines on the chart, where the stop, the target and orders can be dragged.

const money = (v: number) => `${v >= 0 ? '+' : '−'}${Math.abs(v).toFixed(2)}`;

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

/** The replayed bar's time as the chart's own labels write it: «30 Sep '26  00:30». */
export function barDate(t: number, timezone: string, interval: string): string {
  const daily = ['1D', '1W', '1M'].includes(interval);
  let parts: Record<string, string> = {};
  try {
    const f = new Intl.DateTimeFormat('en-GB', { timeZone: daily ? 'UTC' : timezone, year: '2-digit', month: 'numeric', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' });
    parts = Object.fromEntries(f.formatToParts(new Date(t * 1000)).map((p) => [p.type, p.value]));
  } catch {
    const d = new Date(t * 1000).toISOString();
    parts = { year: d.slice(2, 4), month: String(Number(d.slice(5, 7))), day: d.slice(8, 10), hour: d.slice(11, 13), minute: d.slice(14, 16) };
  }
  const day = `${parts.day} ${MONTHS[Number(parts.month) - 1] ?? ''} '${parts.year}`;
  return daily ? day : `${day}  ${parts.hour}:${parts.minute}`;
}

/** The view: the chart keeps where the bar was picked; while playing it follows the new bars. */
function useReplayView() {
  const slice = useReplay((r) => r.slice);
  const startedAt = useReplay((r) => r.startedAt);
  const prevLen = useRef(0);
  const startKey = useRef<number | null>(null);
  useEffect(() => {
    const api = useChartBus.getState().api;
    if (!api || !slice) { prevLen.current = 0; startKey.current = null; return; }
    const len = slice.length;
    const fresh = startKey.current !== startedAt;
    const before = prevLen.current;
    prevLen.current = len;
    // After the chart has painted the slice (its effect runs after this one).
    let raf2 = 0;
    const raf = requestAnimationFrame(() => { raf2 = requestAnimationFrame(() => {
      const ts = api.timeScale();
      const last = len - 1;
      if (fresh) {
        startKey.current = startedAt;
        const keep = useReplay.getState().keepView;
        const now = ts.getVisibleLogicalRange();
        const span = Math.max(60, Math.min(220, keep ? keep.to - keep.from : now ? now.to - now.from : 120));
        if (keep && last >= keep.from + 5 && last <= keep.to - 3) ts.setVisibleLogicalRange(keep);
        else ts.setVisibleLogicalRange({ from: last - span * 0.72, to: last + span * 0.28 });
        return;
      }
      if (len <= before) return;
      const lr = ts.getVisibleLogicalRange();
      if (!lr) return;
      // Follow only when the reader was watching the edge (the previous bar was on screen).
      if (before - 1 <= lr.to + 0.5 && last > lr.to - 3) {
        const d = last - (lr.to - 3);
        ts.setVisibleLogicalRange({ from: lr.from + d, to: lr.to + d });
      }
    }); });
    return () => { cancelAnimationFrame(raf); cancelAnimationFrame(raf2); };
  }, [slice, startedAt]);
}

/** A click on a revealed bar goes back to it (with the cursor, not while drawing). */
function useReplayClicks(on: boolean) {
  const api = useChartBus((b) => b.api);
  useEffect(() => {
    if (!on || !api) return;
    const click = (p: MouseEventParams<Time>) => {
      const r = useReplay.getState();
      if (!r.state?.on || r.selecting || typeof p.time !== 'number') return;
      const tool = useDrawing.getState().tool;
      if (tool !== 'cursor' && tool !== 'select') return;
      const full = r.controller.full as Candle[];
      const i = full.findIndex((b) => b.t === p.time);
      if (i >= 0 && i < r.controller.idx) r.controller.seek({ index: i });
    };
    api.subscribeClick(click);
    return () => { try { api.unsubscribeClick(click); } catch { /* chart gone */ } };
  }, [on, api]);
}

/** Shift+B / Shift+S place practice trades while replaying (not paper trades), as the old app did. */
function useReplayKeys(on: boolean) {
  useEffect(() => {
    if (!on) return;
    const key = (e: KeyboardEvent) => {
      if (!e.shiftKey || e.ctrlKey || e.altKey || e.metaKey) return;
      if (e.code !== 'KeyB' && e.code !== 'KeyS') return;
      const el = e.target as HTMLElement | null;
      if (el && (/^(INPUT|TEXTAREA|SELECT)$/.test(el.tagName) || el.isContentEditable)) return;
      e.preventDefault(); e.stopImmediatePropagation();
      useReplay.getState().trade(e.code === 'KeyB' ? 'long' : 'short');
    };
    window.addEventListener('keydown', key, true);
    return () => window.removeEventListener('keydown', key, true);
  }, [on]);
}

/** The practice trades' lines: position, stop, target and waiting orders; stops, targets and orders drag. */
function useReplayLines(on: boolean) {
  const L = useL();
  const trades = useReplay((r) => r.trades);
  const slice = useReplay((r) => r.slice);
  useEffect(() => {
    const set = useTradeLines.getState().set;
    if (!on) { set('replay', []); return; }
    const price = slice?.[slice.length - 1]?.c ?? 0;
    const r = useReplay.getState();
    const out: TradeLine[] = [];
    for (const t of trades) {
      if (t.exit != null) continue;
      const long = t.side === 'long';
      const at = (p: number) => (p - t.entry) * (long ? 1 : -1) * t.qty;
      if (t.pending) {
        out.push({ id: `${t.id}:o`, price: t.entry, color: long ? '#2962FF' : '#F23645', dashed: true, label: `${long ? L('خرید', 'Buy') : L('فروش', 'Sell')} ${t.type === 'limit' ? L('لیمیت', 'limit') : L('استاپ', 'stop')} ${t.qty}`, onMove: (p) => r.modify(t.id, { entry: p }), onClose: () => r.cancel(t.id), closeTitle: L('لغو سفارش', 'Cancel order') });
      } else {
        const pnl = tradeMoney(t, price);
        out.push({ id: `${t.id}:p`, price: t.entry, color: '#2962FF', label: `${long ? L('خرید', 'Long') : L('فروش', 'Short')} ${t.qty}`, value: money(pnl), valueColor: pnl >= 0 ? 'var(--up)' : 'var(--down)', onClose: () => r.closeTrade(t.id), closeTitle: L('بستن معامله', 'Close position') });
      }
      if (t.sl != null) out.push({ id: `${t.id}:sl`, price: t.sl, color: '#F23645', dashed: t.pending, label: 'SL', value: money(at(t.sl)), valueColor: 'var(--down)', onMove: (p) => r.modify(t.id, { sl: p }), onClose: () => r.modify(t.id, { sl: null }), closeTitle: L('حذف حد ضرر', 'Remove stop loss') });
      if (t.tp != null) out.push({ id: `${t.id}:tp`, price: t.tp, color: '#089981', dashed: t.pending, label: 'TP', value: money(at(t.tp)), valueColor: 'var(--up)', onMove: (p) => r.modify(t.id, { tp: p }), onClose: () => r.modify(t.id, { tp: null }), closeTitle: L('حذف حد سود', 'Remove take profit') });
    }
    set('replay', out);
  }, [on, trades, slice, L]);
  useEffect(() => () => useTradeLines.getState().set('replay', []), []);
}

export function ReplayBar() {
  const t = useT();
  const L = useL();
  const fa = usePrefs((s) => s.lang) === 'fa';
  const timezone = usePrefs((s) => s.timezone);
  const symbol = usePrefs((s) => s.symbol);
  const interval = usePrefs((s) => s.interval);
  const { selecting, state, controller, select, exit, slice, trades, trade, flatten, start, session, settings, intrabar } = useReplay();
  const bars = useChartBus((b) => b.bars);
  const [menu, setMenu] = useState<HTMLElement | null>(null);
  const [speedMenu, setSpeedMenu] = useState<HTMLElement | null>(null);
  const [dateOpen, setDateOpen] = useState(false);
  const [panel, setPanel] = useState(false);
  const on = Boolean(state?.on);
  useReplayView();
  useReplayClicks(on);
  useReplayKeys(on);
  useReplayLines(on);

  const randomBar = () => {
    if (bars.length < 60) return;
    const i = Math.floor(bars.length * (0.2 + Math.random() * 0.6));
    start(bars, bars[i]!.t, { keepView: false });
  };
  const firstBar = () => { if (bars.length >= 60) start(bars, bars[Math.min(29, bars.length - 1)]!.t, { keepView: false }); };
  const canResume = Boolean(session && session.symbol === symbol && session.interval === interval && bars.length && bars[0]!.t <= session.at && bars[bars.length - 1]!.t >= session.at);
  const resume = () => { useReplay.getState().resume(bars); };
  const seek = (index: number) => controller.seek({ index: Math.max(0, Math.min((state?.length ?? 1) - 1, index)) });
  const jump = (n: number) => seek((state?.idx ?? 0) + n);
  const price = slice?.[slice.length - 1]?.c ?? 0;
  const result = trades.reduce((n, x) => n + tradeMoney(x, price), 0);
  const open = trades.find((x) => x.exit == null && !x.pending);
  const stats = useMemo(() => practiceStats(trades, settings.balance, price), [trades, settings.balance, price]);
  const steps = UPDATE_STEPS[interval] ?? [];
  const date = slice?.length ? barDate(slice[slice.length - 1]!.t, timezone, interval) : '';

  if (!selecting && !on) return null;

  const pickMenu = menu ? (
    <Dropdown anchor={menu} onClose={() => setMenu(null)} className="rp-menu">
      <div dir={fa ? 'rtl' : 'ltr'}>
        <button className="menu-item" onClick={() => { setMenu(null); select(); }}><Icon name="ph-target" size="sm" /><span className="fa">{L('انتخاب کندل', 'Select bar')}</span></button>
        <button className="menu-item" onClick={() => { setMenu(null); select(); setDateOpen(true); }}><Icon name="ph-calendar-blank" size="sm" /><span className="fa">{L('انتخاب تاریخ…', 'Select date…')}</span></button>
        <button className="menu-item" onClick={() => { setMenu(null); exit(); firstBar(); }} disabled={bars.length < 60}><Icon name="ph-skip-back" size="sm" /><span className="fa">{L('اولین تاریخ موجود', 'Select the first available date')}</span></button>
        <button className="menu-item" onClick={() => { setMenu(null); exit(); randomBar(); }} disabled={bars.length < 60}><Icon name="ph-shuffle" size="sm" /><span className="fa">{L('کندل تصادفی', 'Random bar')}</span></button>
      </div>
    </Dropdown>
  ) : null;

  if (selecting) {
    return (
      <div className="replay-bar selecting" dir={fa ? 'rtl' : 'ltr'}>
        <Icon name="ph-rewind" size="sm" />
        <span className="fa">{t('replayPick')}</span>
        {dateOpen ? <PickDate onClose={() => setDateOpen(false)} /> : <button className="btn btn-quiet sm" onClick={() => setDateOpen(true)}><Icon name="ph-calendar-blank" size="sm" /><span className="fa">{L('تاریخ', 'Date')}</span></button>}
        <button className="btn btn-quiet sm" onClick={firstBar} disabled={bars.length < 60} title={L('اولین تاریخ موجود', 'Select the first available date')}><Icon name="ph-skip-back" size="sm" /><span className="fa">{L('اولین کندل', 'First bar')}</span></button>
        <button className="btn btn-quiet sm" onClick={randomBar} disabled={bars.length < 60}><Icon name="ph-shuffle" size="sm" /><span>Random bar</span></button>
        {canResume ? <button className="btn btn-quiet sm" onClick={resume} title={session ? barDate(session.at, timezone, interval) : ''}><Icon name="ph-play" size="sm" /><span className="fa">{L('ادامه‌ی جلسه‌ی قبل', 'Resume last session')}</span></button> : null}
        <button className="lt" onClick={exit} aria-label="close"><Icon name="ph-x" size="sm" /></button>
      </div>
    );
  }

  const last = (state?.length ?? 1) - 1;
  return (
    <div className="replay-bar rp2" role="toolbar" aria-label={t('replay')}>
      {panel ? <PracticePanel onClose={() => setPanel(false)} /> : null}
      <div className="rp-row" dir="ltr">
        <button className="ib rp-pick" title="select bar" aria-label={L('انتخاب کندل', 'Select bar')} onClick={(e) => setMenu(menu ? null : e.currentTarget)}><Icon name="ph-target" size="sm" /><Icon name="ph-caret-down" size="sm" className="rp-caret" /></button>
        <span className="sep" />
        <button className="ib" title={L('پرش به اولین کندل', 'Jump to the first bar')} onClick={() => seek(Math.min(29, last))} disabled={state?.atStart}><Icon name="ph-caret-line-left" size="sm" /></button>
        <button className="ib num" title="−10 bars" onClick={() => jump(-10)}>−10</button>
        <button className="ib" title="step back" aria-label={L('یک کندل عقب (Shift+←)', 'Step back (Shift+←)')} onClick={() => controller.stepBack()}><Icon name="ph-skip-back" size="sm" /></button>
        <button className={`ib rp-play${state?.playing ? ' on' : ''}`} title="play" aria-label={state?.playing ? L('مکث', 'Pause') : L('پخش', 'Play')} onClick={() => controller.toggle()}><Icon name={state?.playing ? 'ph-pause' : 'ph-play'} size="md" /></button>
        <button className="ib" title="step" aria-label={L('یک کندل جلو (Shift+→)', 'Forward (Shift+→)')} onClick={() => controller.step()}><Icon name="ph-skip-forward" size="sm" /></button>
        <button className="ib num" title="+10 bars" onClick={() => jump(10)}>+10</button>
        <button className="ib" title={L('پرش به آخرین کندل', 'Jump to the last bar')} onClick={() => seek(last)} disabled={state?.atEnd}><Icon name="ph-caret-line-right" size="sm" /></button>
        <input className="rp-scrub" type="range" min={0} max={Math.max(0, last)} value={state?.idx ?? 0} onChange={(e) => seek(Number(e.target.value))} aria-label={L('پرش به کندل', 'Go to bar')} title={state ? `${state.idx + 1} / ${state.length}` : ''} />
        <span className="rp-date num" title={L('تاریخ کندل فعلی؛ روی هر کندل چارت کلیک کنید تا به آن برگردید', 'The replayed bar; click any bar on the chart to go back to it')}>{date}</span>
        <button className={`ib num rp-speed${intrabar.state === 'on' ? ' magnified' : ''}`} title={L('سرعت و بازه‌ی به‌روزرسانی', 'Speed and update interval')} aria-haspopup="menu" onClick={(e) => setSpeedMenu(speedMenu ? null : e.currentTarget)}>
          {state?.speed ?? 1}x{settings.update !== 'bar' ? <small>{intrabar.state === 'loading' ? '…' : intervalLabel(settings.update)}</small> : null}<Icon name="ph-caret-down" size="sm" className="rp-caret" />
        </button>
        <span className="sep" />
        <button className="btn buy sm" title="Buy (practice)" aria-label={L('خرید تمرینی (Shift+B)', 'Practice buy (Shift+B)')} onClick={() => trade('long')} disabled={open?.side === 'long'}>Buy</button>
        <button className="btn sell sm" title="Sell (practice)" aria-label={L('فروش تمرینی (Shift+S)', 'Practice sell (Shift+S)')} onClick={() => trade('short')} disabled={open?.side === 'short'}>Sell</button>
        <button className={`btn btn-quiet sm${open || trades.some((x) => x.pending) ? '' : ' idle'}`} onClick={flatten} title={L('بستن همه و لغو سفارش‌ها', 'Close everything and cancel orders')}>Flatten</button>
        {trades.length ? <span className={`num rp-result ${result >= 0 ? 'up' : 'down'}`} title={fa ? `${faNum(stats.trades)} معامله‌ی بسته` : `${stats.trades} closed trades`}>{money(result)}</span> : null}
        <button className={`ib${panel ? ' on' : ''}`} title={L('پنل معامله‌ی تمرینی', 'Practice trading panel')} aria-pressed={panel} onClick={() => setPanel(!panel)}><Icon name="ph-chart-line-up" size="sm" /></button>
        <span className="sep" />
        <button className="ib" title="jump to real time" aria-label={L('برگشت به زمان واقعی', 'Jump to real time')} onClick={exit}><Icon name="ph-fast-forward" size="sm" /></button>
        <button className="ib" title="exit" aria-label={L('خروج از بازپخش', 'Exit replay')} onClick={exit}><Icon name="ph-x" size="sm" /></button>
      </div>
      {pickMenu}
      {speedMenu ? (
        <Dropdown anchor={speedMenu} onClose={() => setSpeedMenu(null)} className="rp-menu rp-speed-menu">
          <div dir={fa ? 'rtl' : 'ltr'}>
            <div className="menu-head fa">{L('سرعت', 'Speed')}</div>
            {SPEED_LADDER.map((x) => <button key={x} className={`menu-item num${state?.speed === x ? ' on' : ''}`} onClick={() => { controller.setSpeed(x); setSpeedMenu(null); }}>{x}x</button>)}
            {steps.length ? <>
              <div className="menu-sep" />
              <div className="menu-head fa" title={L('هر کندل از کندل‌های تایم‌فریم پایین‌تر ساخته می‌شود', 'Each bar is built from lower-timeframe bars')}>{L('بازه‌ی به‌روزرسانی', 'Update interval')}</div>
              <button className={`menu-item rp-upd${settings.update === 'bar' ? ' on' : ''}`} data-step="bar" onClick={() => { useReplay.getState().setUpdate('bar'); setSpeedMenu(null); }}><span className="fa">{L('۱ کندل', '1 bar')}</span></button>
              {steps.map((x) => <button key={x} className={`menu-item num rp-upd${settings.update === x ? ' on' : ''}`} data-step={x} onClick={() => { useReplay.getState().setUpdate(x); setSpeedMenu(null); }}>{intervalLabel(x)}</button>)}
              {intrabar.state === 'none' ? <p className="muted fa rp-note">{L('برای این بازه کندل کوچک‌تری پیدا نشد؛ کندل‌ها کامل جلو می‌روند.', 'No lower-timeframe bars here; the replay steps whole bars.')}</p> : null}
            </> : null}
          </div>
        </Dropdown>
      ) : null}
    </div>
  );
}

/** Start the replay at a date (TradingView's «Select date»). */
function PickDate({ onClose }: { onClose(): void }) {
  const L = useL();
  const [value, setValue] = useState('');
  const [miss, setMiss] = useState(false);
  const go = () => {
    const ms = Date.parse(value);
    if (!Number.isFinite(ms)) return;
    const bars = useChartBus.getState().bars;
    const t = Math.floor(ms / 1000);
    if (!bars.length || bars[0]!.t > t || bars[bars.length - 1]!.t < t) { setMiss(true); return; }
    useReplay.getState().start(bars, t, { keepView: false });
    onClose();
  };
  const bars = useChartBus.getState().bars;
  const min = bars[0] ? new Date(bars[0].t * 1000).toISOString().slice(0, 16) : undefined;
  return (
    <form className="rp-date-pick" onSubmit={(e) => { e.preventDefault(); go(); }} onKeyDown={(e) => { if (e.key === 'Escape') onClose(); }}>
      <input type="datetime-local" value={value} min={min} onChange={(e) => { setValue(e.target.value); setMiss(false); }} autoFocus aria-label={L('تاریخ شروع', 'Start date')} />
      <button className="btn btn-primary sm" disabled={!value}>{L('شروع', 'Start')}</button>
      {miss ? <span className="muted fa">{L('این تاریخ در کندل‌های بارشده نیست', 'That date is outside the loaded bars')}</span> : null}
    </form>
  );
}

/** The practice account: an order ticket, risk and protection, open trades and orders, statistics. */
function PracticePanel({ onClose }: { onClose(): void }) {
  const L = useL();
  const fa = usePrefs((s) => s.lang) === 'fa';
  const symbol = usePrefs((s) => s.symbol);
  const r = useReplay();
  const price = r.slice?.[r.slice.length - 1]?.c ?? 0;
  const digits = guessDigits(symbol, price);
  const [px, setPx] = useState('');
  const st = r.settings;
  const stats = practiceStats(r.trades, st.balance, price);
  const live = r.trades.filter((t) => t.exit == null);
  const n = (v: number) => (fa ? faNum(v) : String(v));
  const place = (side: 'long' | 'short') => {
    if (st.order === 'market') r.trade(side);
    else r.order(side, st.order, Number(px) || price);
  };
  const ORDERS: Array<[PracticeOrder, string]> = [['market', L('بازار', 'Market')], ['limit', L('لیمیت', 'Limit')], ['stop', L('استاپ', 'Stop')]];
  const how = (t: ReplayTrade) => ({ sl: L('حد ضرر', 'Stop loss'), tp: L('حد سود', 'Take profit'), manual: L('دستی', 'Manual'), reverse: L('معکوس', 'Reverse'), flatten: L('بستن همه', 'Flatten') }[t.how ?? 'manual']);
  return (
    <div className="rp-panel" dir={fa ? 'rtl' : 'ltr'} onMouseDown={(e) => e.stopPropagation()}>
      <header className="rp-head">
        <b className="fa">{L('معامله‌ی تمرینی', 'Practice trading')}</b>
        <span className="muted fa">{L('فقط در همین بازپخش؛ به حساب کاغذی کاری ندارد', 'Only inside this replay; the paper account is untouched')}</span>
        <button className="ib" title={L('شروع دوباره‌ی آمار', 'Reset the statistics')} onClick={r.resetPractice}><Icon name="ph-arrow-counter-clockwise" size="sm" /></button>
        <button className="ib" title={L('بستن', 'Close')} onClick={onClose}><Icon name="ph-x" size="sm" /></button>
      </header>
      <div className="rp-ticket">
        <div className="seg" role="radiogroup" aria-label={L('نوع سفارش', 'Order type')}>
          {ORDERS.map(([k, label]) => <button key={k} role="radio" aria-checked={st.order === k} className={st.order === k ? 'on' : ''} onClick={() => { r.setSettings({ order: k }); if (k !== 'market' && !px) setPx(price.toFixed(digits)); }}><span className="fa">{label}</span></button>)}
        </div>
        {st.order !== 'market' ? <input className="num rp-in" inputMode="decimal" value={px} onChange={(e) => setPx(e.target.value)} placeholder={price.toFixed(digits)} aria-label={L('قیمت سفارش', 'Order price')} dir="ltr" /> : null}
        <button className="btn buy sm" onClick={() => place('long')}><span className="fa">{L('خرید', 'Buy')}</span></button>
        <button className="btn sell sm" onClick={() => place('short')}><span className="fa">{L('فروش', 'Sell')}</span></button>
      </div>
      <div className="rp-risk">
        <label><span className="fa">{L('ریسک هر معامله', 'Risk per trade')}</span><input className="num rp-in sm" type="number" step="0.1" min="0.01" value={st.riskPct} onChange={(e) => r.setSettings({ riskPct: Number(e.target.value) || 1 })} dir="ltr" /><span className="num">%</span></label>
        <label className="rp-check"><input type="checkbox" checked={st.auto} onChange={(e) => r.setSettings({ auto: e.target.checked })} /><span className="fa">{L('حد ضرر و سود خودکار (ATR)', 'Automatic stop and target (ATR)')}</span></label>
        {st.auto ? <>
          <label><span>SL</span><input className="num rp-in sm" type="number" step="0.1" value={st.slAtr} onChange={(e) => r.setSettings({ slAtr: Number(e.target.value) || 1.5 })} dir="ltr" /><span className="num">×ATR</span></label>
          <label><span>TP</span><input className="num rp-in sm" type="number" step="0.1" value={st.tpAtr} onChange={(e) => r.setSettings({ tpAtr: Number(e.target.value) || 3 })} dir="ltr" /><span className="num">×ATR</span></label>
        </> : null}
        <label className="rp-check"><input type="checkbox" checked={st.spread} onChange={(e) => r.setSettings({ spread: e.target.checked })} /><span className="fa">{L('اسپرد و لغزش', 'Spread and slippage')}</span></label>
      </div>
      {live.length ? (
        <div className="rp-trades" role="table">
          <div className="rp-tr rp-th" role="row"><span className="fa">{L('سمت', 'Side')}</span><span className="fa">{L('حجم', 'Qty')}</span><span className="fa">{L('ورود', 'Entry')}</span><span>SL</span><span>TP</span><span className="fa">{L('سود/زیان', 'P&L')}</span><span>R</span><span /></div>
          {live.map((t) => {
            const pnl = tradeMoney(t, price);
            return (
              <div key={t.id} className="rp-tr" role="row">
                <span className={`fa ${t.side === 'long' ? 'up' : 'down'}`}>{t.side === 'long' ? L('خرید', 'Long') : L('فروش', 'Short')}{t.pending ? ` · ${t.type === 'limit' ? L('لیمیت', 'limit') : L('استاپ', 'stop')}` : ''}</span>
                <span className="num">{t.qty}</span>
                <span className="num">{formatPrice(t.entry, digits)}</span>
                <span className="num">{t.sl != null ? formatPrice(t.sl, digits) : '—'}</span>
                <span className="num">{t.tp != null ? formatPrice(t.tp, digits) : '—'}</span>
                <span className={`num ${t.pending ? 'muted' : pnl >= 0 ? 'up' : 'down'}`}>{t.pending ? L('در انتظار', 'Pending') : money(pnl)}</span>
                <span className="num">{!t.pending && t.risk > 0 ? (pnl / t.risk).toFixed(2) : '—'}</span>
                <span className="rp-acts">
                  {t.pending ? <button className="link-btn" onClick={() => r.cancel(t.id)}><span className="fa">{L('لغو', 'Cancel')}</span></button> : <>
                    <button className="link-btn num" title={L('بستن نیمی از حجم', 'Close half')} onClick={() => r.closeTrade(t.id, 0.5)}>50%</button>
                    <button className="link-btn" title={L('حد ضرر روی نقطه‌ی ورود', 'Stop to breakeven')} onClick={() => r.breakeven(t.id)}>BE</button>
                    <button className="link-btn" title={L('معکوس کردن', 'Reverse')} onClick={r.reverse}><Icon name="ph-arrows-down-up" size="sm" /></button>
                    <button className="link-btn" title={L('بستن', 'Close')} onClick={() => r.closeTrade(t.id)}><Icon name="ph-x" size="sm" /></button>
                  </>}
                </span>
              </div>
            );
          })}
        </div>
      ) : <p className="muted fa rp-empty">{L('معامله‌ی بازی نیست. خرید یا فروش کنید یا Shift+B و Shift+S را بزنید.', 'No open trades. Buy or sell, or press Shift+B / Shift+S.')}</p>}
      <dl className="rp-stats">
        <div><dt className="fa">{L('موجودی', 'Balance')}</dt><dd className="num">{stats.balance.toFixed(2)}</dd></div>
        <div><dt className="fa">{L('ارزش حساب', 'Equity')}</dt><dd className="num">{stats.equity.toFixed(2)}</dd></div>
        <div><dt className="fa">{L('سود/زیان خالص', 'Net P&L')}</dt><dd className={`num ${stats.net >= 0 ? 'up' : 'down'}`}>{money(stats.net)} <small>({stats.netPct >= 0 ? '+' : '−'}{Math.abs(stats.netPct).toFixed(2)}%)</small></dd></div>
        <div><dt className="fa">{L('نرخ برد', 'Win rate')}</dt><dd className="num">{stats.winRate == null ? '—' : `${stats.winRate.toFixed(0)}%`}</dd></div>
        <div><dt className="fa">{L('معامله‌ها', 'Trades')}</dt><dd className={fa ? 'fa' : 'num'}>{n(stats.trades)}{stats.trades ? (fa ? ` (${faNum(stats.wins)} برد)` : ` (${stats.wins} won)`) : ''}</dd></div>
        <div><dt className="fa">{L('مجموع R', 'Net R')}</dt><dd className={`num ${stats.netR >= 0 ? 'up' : 'down'}`}>{stats.netR >= 0 ? '+' : '−'}{Math.abs(stats.netR).toFixed(2)}R</dd></div>
        <div className="wide"><dt className="fa">{L('آخرین نتیجه', 'Last result')}</dt><dd>{stats.last ? <><span className={`num ${(stats.last.pnl ?? 0) >= 0 ? 'up' : 'down'}`}>{money(stats.last.pnl ?? 0)}{stats.last.r != null ? ` · ${stats.last.r >= 0 ? '+' : '−'}${Math.abs(stats.last.r).toFixed(2)}R` : ''}</span> <span className="muted fa">{how(stats.last)}</span></> : '—'}</dd></div>
      </dl>
    </div>
  );
}
