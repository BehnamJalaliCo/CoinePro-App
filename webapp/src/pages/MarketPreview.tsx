// The market preview (OLD feature/search MarketPreviewSheet.kt, MarketPreviewChart.kt): what a held
// row (or its preview button, or a right click) opens instead of the chart. A price, the day's move
// or why there is none, a small line over six spans you can run a pointer along, one sentence on
// what the market is doing, the three actions — star, alert, chart — and «اگر امروز این‌قدر حرکت
// کرد خبرم کنید»: one alert, in one direction, on today's move, with no number to type.
//
// Opening it costs no network beyond the day's line; only a span chip asks for history, once per
// span, and the answer is kept for the session.
import { useEffect, useMemo, useRef, useState, type PointerEvent as RPointerEvent } from 'react';
import { createPortal } from 'react-dom';
import { create } from 'zustand';
import { useMarket } from '../state/market';
import { usePrefs } from '../state/prefs';
import { usePage } from '../state/page';
import { useWatchlist } from '../state/watchlist';
import { useAlerts } from '../state/alerts';
import { requestSparkline, useSparklines } from '../data/sparklines';
import { instrumentOf } from '../data/symbols';
import { marketState } from '../data/sessions';
import { formatPrice } from '../chart/format';
import { openAlertDialog } from '../chart/actions';
import { toast } from '../shell/toast';
import { Icon } from '../ui/Icon';
import { SymbolLogo } from '../ui/SymbolLogo';
import { useL } from './kit';
import { prettyOf } from './heatmapLib';
import { MILESTONES, PREVIEW_RANGES, heldSpan, linePoints, loadSpan, momentOf, readingOf, readingText, type PreviewRange } from './previewLib';
import type { Candle } from '../data/types';
import '../shell/markets2.css';

interface PreviewState { symbol: string | null }
export const usePreview = create<PreviewState>(() => ({ symbol: null }));
export const openPreview = (symbol: string) => usePreview.setState({ symbol });
export const closePreview = () => usePreview.setState({ symbol: null });

/** How long a press must rest on a row before it is a hold rather than a tap. */
const HOLD_MS = 500;

/**
 * A hold: pressing for half a second runs `onHold`, and the click that ends the press is swallowed
 * so the row does not also do its tap. A right click is a hold too.
 */
export function useHold(onHold: () => void) {
  const timer = useRef<number | null>(null);
  const held = useRef(false);
  const start = useRef<{ x: number; y: number } | null>(null);
  const fn = useRef(onHold);
  fn.current = onHold;
  const cancel = () => { if (timer.current != null) { window.clearTimeout(timer.current); timer.current = null; } };
  useEffect(() => cancel, []);
  return {
    onPointerDown: (e: RPointerEvent) => {
      if (e.button !== 0) return;
      held.current = false;
      start.current = { x: e.clientX, y: e.clientY };
      cancel();
      timer.current = window.setTimeout(() => { held.current = true; timer.current = null; fn.current(); }, HOLD_MS);
    },
    onPointerMove: (e: RPointerEvent) => { const s = start.current; if (s && Math.hypot(e.clientX - s.x, e.clientY - s.y) > 8) cancel(); },
    onPointerUp: cancel,
    onPointerLeave: cancel,
    onPointerCancel: cancel,
    onClickCapture: (e: { stopPropagation(): void; preventDefault(): void }) => { if (held.current) { held.current = false; e.stopPropagation(); e.preventDefault(); } },
    onContextMenu: (e: { preventDefault(): void }) => { e.preventDefault(); cancel(); fn.current(); },
  };
}

/** A market row's hold: the preview, instead of the chart. */
export const usePreviewPress = (symbol: string) => useHold(() => openPreview(symbol));

/** The preview button a row can carry: the same sheet, for a pointer that cannot hold. */
export function PreviewButton({ symbol }: { symbol: string }) {
  const L = useL();
  return (
    <button type="button" className="lt preview-btn" title={L('پیش‌نمایش', 'Preview')} aria-label={L(`پیش‌نمایش ${symbol}`, `Preview ${symbol}`)} onClick={(e) => { e.stopPropagation(); openPreview(symbol); }}>
      <Icon name="ph-eye" size="sm" />
    </button>
  );
}

export function MarketPreviewHost() {
  const symbol = usePreview((s) => s.symbol);
  if (!symbol) return null;
  return <MarketPreviewSheet key={symbol} symbol={symbol} />;
}

function MarketPreviewSheet({ symbol }: { symbol: string }) {
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const en = lang === 'en';
  const tk = useMarket((s) => s.tickers[symbol]);
  const starred = useWatchlist((s) => s.symbols.includes(symbol));
  const line = useSparklines((s) => s.lines[symbol]);
  const inst = instrumentOf(symbol, tk?.last);
  const state = marketState(inst.market);
  // The move is dropped when the market is closed: a stale percentage beside «بسته» claims to be today's.
  const change = tk && state === 'open' && Number.isFinite(tk.changePct) ? tk.changePct : null;
  useEffect(() => { requestSparkline(symbol); }, [symbol]);
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') { e.stopPropagation(); closePreview(); } };
    window.addEventListener('keydown', onKey, true);
    return () => window.removeEventListener('keydown', onKey, true);
  }, []);

  const openChart = () => { closePreview(); usePrefs.getState().setSymbol(symbol); usePage.getState().close(); };
  const toggleStar = () => { const w = useWatchlist.getState(); if (starred) w.remove(symbol); else w.add(symbol); };
  const alert = tk ? () => { closePreview(); openAlertDialog({ symbol, price: tk.last }); } : null;
  const milestone = (up: boolean, percent: number) => {
    closePreview();
    // Once, and in one direction: «tell me if this moves five per cent today» is answered the first
    // time it does. Today's move is measured from the previous daily close.
    useAlerts.getState().create({
      symbol, condition: up ? 'move_up_pct' : 'move_down_pct', value: percent, interval: '1D', bars: 1, freq: 'once',
      name: `${symbol} ${up ? '+' : '−'}${percent.toFixed(2)}% ${L('امروز', 'today')}`,
    });
    toast(L('هشدار ساخته شد', 'Alert created'));
  };

  return createPortal(
    <div className="scrim sheet-scrim preview-scrim" onMouseDown={(e) => { if (e.target === e.currentTarget) closePreview(); }}>
      <div className="sheet market-preview" role="dialog" aria-modal="true" aria-label={prettyOf(symbol)} dir={en ? 'ltr' : 'rtl'} data-symbol={symbol}>
        <div className="sheet-grip" aria-hidden />
        <header className="mp-head">
          <SymbolLogo symbol={symbol} size={28} />
          <div className="mp-title"><b className="sym" dir="ltr">{prettyOf(symbol)}</b><span className={`muted${en ? '' : ' fa'}`}>{en ? inst.nameEn : inst.nameFa}</span></div>
          <button type="button" className="ib" onClick={closePreview} aria-label={L('بستن', 'Close')}><Icon name="ph-x" size="md" /></button>
        </header>
        <div className="mp-quote">
          {/* An em dash, not a zero: the feed has not quoted this market. */}
          <span className="mp-price num" dir="ltr">{tk ? formatPrice(tk.last, inst.digits) : '—'}</span>
          {change != null ? <span className={`mp-pill num ${change >= 0 ? 'up' : 'down'}`} dir="ltr">{change > 0 ? '+' : change < 0 ? '−' : ''}{Math.abs(change).toFixed(2)}%</span>
            : state !== 'open' ? <span className="muted fa mp-closed">{state === 'weekend' ? L('تعطیل آخر هفته', 'Weekend') : L('بسته', 'Closed')}</span> : null}
        </div>
        <PreviewChart symbol={symbol} fallback={line ?? []} en={en} />
        <div className="mp-actions">
          <button type="button" className={`mp-action${starred ? ' on' : ''}`} aria-pressed={starred} onClick={toggleStar}>
            <Icon name={starred ? 'ph-star-fill' : 'ph-star'} size="md" /><span className="fa">{starred ? L('حذف از دیده‌بان', 'Remove from watchlist') : L('افزودن به دیده‌بان', 'Add to watchlist')}</span>
          </button>
          {alert ? <button type="button" className="mp-action" onClick={alert}><Icon name="ph-bell" size="md" /><span className="fa">{L('هشدار', 'Alert')}</span></button> : null}
          <button type="button" className="mp-action mp-open" onClick={openChart}><Icon name="ph-chart-line-up" size="md" /><span className="fa">{L('چارت', 'Chart')}</span></button>
        </div>
        <div className="mp-milestones">
          <span className="muted small fa">{L('اگر امروز این‌قدر حرکت کرد خبرم کنید', 'Tell me if it moves this much today')}</span>
          <div className="mp-chips" dir="ltr">
            {MILESTONES.flatMap((p) => [true, false].map((up) => (
              <button key={`${p}${up}`} type="button" className={`mp-chip num ${up ? 'up' : 'down'}`} data-milestone={`${up ? '+' : '-'}${p}`} onClick={() => milestone(up, p)}>{up ? '+' : '−'}{p.toFixed(2)}%</button>
            )))}
          </div>
        </div>
      </div>
    </div>,
    document.body,
  );
}

const PLOT_W = 320, PLOT_H = 96;
function PreviewChart({ symbol, fallback, en }: { symbol: string; fallback: number[]; en: boolean }) {
  const L = useL();
  const [range, setRange] = useState<PreviewRange | null>(null);
  const [bars, setBars] = useState<Candle[]>([]);
  const [waiting, setWaiting] = useState(false);
  const [scrub, setScrub] = useState<number | null>(null);
  const box = useRef<HTMLDivElement>(null);
  useEffect(() => {
    let alive = true;
    if (!range) { setBars([]); return; }
    const have = heldSpan(symbol, range);
    if (have) { setBars(have); return; }
    // Cleared first, so a slow span shows its own emptiness rather than the last span's shape.
    setBars([]); setWaiting(true);
    void loadSpan(symbol, range).then((b) => { if (alive) { setBars(b); setWaiting(false); } });
    return () => { alive = false; };
  }, [symbol, range]);
  const closes = range == null ? (fallback.length >= 2 ? fallback : []) : bars.map((b) => b.c);
  const reading = useMemo(() => (bars.length >= 2 ? readingOf(bars) : null), [bars]);
  const tone = closes.length < 2 ? 'flat' : closes[closes.length - 1]! >= closes[0]! ? 'up' : 'down';
  const pts = linePoints(closes, PLOT_W, PLOT_H);
  const scrubbable = bars.length >= 2 && closes.length >= 2;
  const at = (clientX: number) => {
    const el = box.current;
    if (!el) return;
    const r = el.getBoundingClientRect();
    const f = Math.min(1, Math.max(0, (clientX - r.left) / Math.max(1, r.width)));
    setScrub(Math.round(f * (closes.length - 1)));
  };
  const bar = scrub != null ? bars[scrub] : undefined;
  const p = scrub != null ? pts[Math.min(pts.length - 1, Math.max(0, scrub))] : undefined;
  return (
    <div className="mp-chart">
      {/* Always present at the same height: a label that appears on touch would push the line under the pointer. */}
      <div className="mp-readout" aria-live="polite">
        {bar ? <><b className="num" dir="ltr">{formatPrice(bar.c, instrumentOf(symbol, bar.c).digits)}</b><span className={`muted${en ? '' : ' fa'}`}>{momentOf(bar.t, range, en)}</span></>
          : waiting ? <span className="muted fa">{L('در حال گرفتن این بازه…', 'Gathering that span…')}</span>
          : reading ? <span className="fa mp-reading">{readingText(reading, en)}</span> : null}
      </div>
      <div ref={box} className={`mp-plot${scrubbable ? ' scrub' : ''}`} dir="ltr"
        onPointerDown={scrubbable ? (e) => { (e.currentTarget as HTMLElement).setPointerCapture?.(e.pointerId); at(e.clientX); } : undefined}
        onPointerMove={scrubbable ? (e) => { if (e.buttons || e.pointerType === 'mouse') at(e.clientX); } : undefined}
        onPointerUp={scrubbable ? (e) => { if (e.pointerType !== 'mouse') setScrub(null); } : undefined}
        onPointerLeave={scrubbable ? () => setScrub(null) : undefined}>
        <svg viewBox={`0 0 ${PLOT_W} ${PLOT_H}`} preserveAspectRatio="none" aria-hidden>
          <line x1={0} x2={PLOT_W} y1={PLOT_H / 3} y2={PLOT_H / 3} className="mp-rule" />
          <line x1={0} x2={PLOT_W} y1={(PLOT_H * 2) / 3} y2={(PLOT_H * 2) / 3} className="mp-rule" />
          {pts.length >= 2 ? <polyline className={`mp-line ${tone}`} points={pts.map((q) => `${q.x.toFixed(1)},${q.y.toFixed(1)}`).join(' ')} /> : null}
          {p ? <><line className="mp-cross" x1={p.x} x2={p.x} y1={0} y2={PLOT_H} /><circle className={`mp-dot ${tone}`} cx={p.x} cy={p.y} r={3} /></> : null}
        </svg>
      </div>
      {/* No chip is lit at first: that is «the day the list already had», the one span that costs nothing. */}
      <div className="mp-ranges" dir="ltr" role="radiogroup">
        {PREVIEW_RANGES.map((r) => (
          <button key={r.id} type="button" role="radio" aria-checked={range?.id === r.id} data-range={r.id} className={`mp-range${range?.id === r.id ? ' on' : ''}${r.id === 'all' && !en ? ' fa' : ' num'}`} onClick={() => { setScrub(null); setRange(r); }}>{en ? r.en : r.fa}</button>
        ))}
      </div>
    </div>
  );
}
