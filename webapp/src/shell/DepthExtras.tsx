// The order book's tools, shared by the DOM panel and the trade page (OLD feature/dom
// DepthOfMarketScreen.kt): the price aggregation chips and the size/cumulative switch, the bid share
// with its meter, the depth curve, the notes under the ladder, and «هشدار در این قیمت».
import { useEffect, useState } from 'react';
import { useL } from '../pages/kit';
import { Icon } from '../ui/Icon';
import { openAlertDialog } from '../chart/actions';
import { faNum } from '../i18n/time';
import { usePrefs } from '../state/prefs';
import { IMBALANCE_LEVELS, STACKED_ORDERS_THRESHOLD, readLadderPref, saveLadderPref, stepLabel, type DepthCurve, type LadderFigure } from './depthLadder';
import './markets2.css';

/** The step and the figure mode, kept per symbol and restored on the next visit. */
export function useLadderPref(symbol: string): [number | null, LadderFigure, (step: number | null, figure: LadderFigure) => void] {
  const [pref, setPref] = useState(() => readLadderPref(symbol));
  useEffect(() => { setPref(readLadderPref(symbol)); }, [symbol]);
  const choose = (step: number | null, figure: LadderFigure) => { setPref({ step, figure }); saveLadderPref(symbol, { step, figure }); };
  return [pref.step, pref.figure, choose];
}

export function DepthControls({ steps, step, figure, onStep, onFigure }: { steps: number[]; step: number | null; figure: LadderFigure; onStep(s: number | null): void; onFigure(f: LadderFigure): void }) {
  const L = useL();
  return (
    <div className="depth-controls">
      {steps.length ? (
        <>
          <span className="muted small fa">{L('تجمیع قیمت', 'Price grouping')}</span>
          <div className="depth-steps" role="radiogroup" aria-label={L('تجمیع قیمت', 'Price grouping')}>
            <button role="radio" aria-checked={step == null} className={`chip sm${step == null ? ' on' : ''}`} onClick={() => onStep(null)}><span className="fa">{L('خام', 'Raw')}</span></button>
            {steps.map((s) => <button key={s} role="radio" aria-checked={step === s} data-step={s} className={`chip sm num${step === s ? ' on' : ''}`} onClick={() => onStep(s)}>{stepLabel(s)}</button>)}
          </div>
        </>
      ) : null}
      <div className="seg depth-figure" role="radiogroup">
        <button role="radio" aria-checked={figure === 'amount'} className={figure === 'amount' ? 'on' : ''} onClick={() => onFigure('amount')}><span className="fa">{L('اندازه', 'Size')}</span></button>
        <button role="radio" aria-checked={figure === 'cumulative'} className={figure === 'cumulative' ? 'on' : ''} onClick={() => onFigure('cumulative')}><span className="fa">{L('تجمعی', 'Cumulative')}</span></button>
      </div>
    </div>
  );
}

/** The bid's share of the book near the touch, one flat bar split at it. */
export function BidShare({ share }: { share: number | null }) {
  const L = useL();
  const fa = usePrefs((s) => s.lang) === 'fa';
  if (share == null) return null;
  return (
    <div className="bid-share">
      <div className="bid-share-head"><span className="muted small fa">{L('سهم خرید', 'Bid share')}</span><b className="num">{Math.round(share * 100)}%</b></div>
      <div className="bid-share-meter" aria-hidden><i style={{ width: `${Math.min(1, Math.max(0, share)) * 100}%` }} /></div>
      <p className="muted small fa">{fa
        ? `سهم خرید روی ${faNum(IMBALANCE_LEVELS)} سطح نزدیک اسپرد در هر سمت سنجیده می‌شود، نه روی کل دفتری که گرفته شده و نه فقط سطرهای روی صفحه.`
        : `Bid share is measured over the ${IMBALANCE_LEVELS} levels nearest the spread on each side — not the whole book fetched, and not only the rows on screen.`}</p>
    </div>
  );
}

/** The depth curve: both sides' cumulative size on one price axis, the mid down the middle. */
export function DepthCurveView({ curve, fmt }: { curve: DepthCurve; fmt(v: number): string }) {
  const L = useL();
  const W = 300, H = 80;
  const path = (pts: { x: number; y: number }[]) => pts.map((p, i) => `${i ? 'L' : 'M'}${(p.x * W).toFixed(1)},${((1 - p.y) * H).toFixed(1)}`).join(' ');
  const area = (pts: { x: number; y: number }[]) => (pts.length < 2 ? '' : `M${(pts[0]!.x * W).toFixed(1)},${H} ${pts.map((p) => `L${(p.x * W).toFixed(1)},${((1 - p.y) * H).toFixed(1)}`).join(' ')} L${(pts[pts.length - 1]!.x * W).toFixed(1)},${H} Z`);
  const peak = curve.peakTotal >= 1000 ? curve.peakTotal.toFixed(0) : String(+curve.peakTotal.toPrecision(6));
  const desc = L(`منحنی عمق بازار. بیشترین حجم تجمعی ${peak} است، در بازه‌ی قیمتی ${fmt(curve.lowPrice)} تا ${fmt(curve.highPrice)}.`, `Depth curve. Largest cumulative size ${peak}, across ${fmt(curve.lowPrice)} to ${fmt(curve.highPrice)}.`);
  return (
    <div className="depth-curve">
      <span className="muted small fa">{L('منحنی عمق', 'Depth curve')}</span>
      <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" role="img" aria-label={desc}>
        <line x1={W / 2} y1={0} x2={W / 2} y2={H} className="depth-axis" />
        {curve.bids.length >= 2 ? <><path d={area(curve.bids)} className="depth-area bid" /><path d={path(curve.bids)} className="depth-line bid" /></> : null}
        {curve.asks.length >= 2 ? <><path d={area(curve.asks)} className="depth-area ask" /><path d={path(curve.asks)} className="depth-line ask" /></> : null}
      </svg>
      <div className="depth-axis-labels num" dir="ltr"><span>{fmt(curve.lowPrice)}</span><span>{fmt(curve.mid)}</span><span>{fmt(curve.highPrice)}</span></div>
    </div>
  );
}

export function DepthNotes({ showStepNote, showOrdersNote }: { showStepNote: boolean; showOrdersNote: boolean }) {
  const L = useL();
  const fa = usePrefs((s) => s.lang) === 'fa';
  return (
    <div className="depth-notes muted small fa">
      {showStepNote ? <p>{L('با تجمیع قیمت، چند سطح مجاور در یک ردیف جمع می‌شوند و ستون قیمت به همان اندازه رقم اعشار می‌گیرد. سمت خرید به پایینِ گام و سمت فروش به بالای آن گرد می‌شود، پس اسپرد فقط می‌تواند بازتر شود، هرگز تنگ‌تر.', 'With price grouping, neighbouring levels are summed into one row and the price column takes the step’s decimals. Bids round down to the step and asks up, so the spread can only widen, never tighten.')}</p> : null}
      {showOrdersNote ? <p>{fa ? `عددی که روی میله می‌آید تعداد سفارش‌های آن سطح است و فقط از ${faNum(STACKED_ORDERS_THRESHOLD)} سفارش به بالا نشان داده می‌شود؛ نزدیک نُه سطح از هر ده سطح یک سفارش بیشتر ندارند.` : `The number on a bar is that level’s order count, shown only from ${STACKED_ORDERS_THRESHOLD} orders up; nearly nine levels in ten hold a single order.`}</p> : null}
    </div>
  );
}

/** «هشدار در این قیمت»: the alert dialog, prefilled at the level's price. */
export function AlertAtPrice({ symbol, price, label }: { symbol: string; price: number; label: string }) {
  const L = useL();
  return (
    <button type="button" className="dom-alert" title={L(`هشدار در این قیمت (${label})`, `Alert at this price (${label})`)} aria-label={L(`ساختن هشدار روی قیمت ${label}`, `Create an alert at ${label}`)}
      onClick={(e) => { e.stopPropagation(); openAlertDialog({ symbol, price }); }}>
      <Icon name="ph-bell" size="sm" />
    </button>
  );
}
