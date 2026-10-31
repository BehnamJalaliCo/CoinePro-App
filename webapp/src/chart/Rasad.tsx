// «رصد» on the chart page (OLD feature/chart RasadSheet.kt, ChartChrome.kt ChartReadingsPanel): one
// line under the plot — the coach's first sentence, two lines at most and never an ellipsis — that
// opens the sheet: «این بازار چه می‌کند» (trend strength, volatility and bias, each with the figure
// it came from), the three sentences, the suggested alert with «وقتی رسید خبرم کنید», and a review
// of the reader's last paper trade on this market. An action with nothing behind it is not drawn.
import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { useChartBus } from './chartBus';
import { useReplay } from '../state/replay';
import { usePrefs } from '../state/prefs';
import { usePaper } from '../state/paper';
import { useL } from '../pages/kit';
import { Icon } from '../ui/Icon';
import { intervalLabel } from '../data/intervals';
import { openAlertDialog } from './actions';
import {
  ADX_FULL_SCALE, BIAS_FULL_SCALE, TRENDING_FLOOR, biasLabel, chartReading, factsOfPaper, isDown, isUp, readChart,
  reviewTrade, strengthLabel, suggestAlert, useRasadReads, volatilityLabel, type ChartReading,
} from './rasad';
import type { Candle } from '../data/types';
import './rasad.css';

const EVENT = 'pc:rasad';
/** Opens the رصد sheet (the line under the plot, the menus). */
export function openRasad(): void { window.dispatchEvent(new CustomEvent(EVENT)); }

/** The bars the reader can see: the replay's slice while replaying (the future stays hidden). */
export function useVisibleBars(): Candle[] {
  const bars = useChartBus((b) => b.bars);
  const slice = useReplay((r) => (r.state?.on ? r.slice : null));
  return slice ?? bars;
}

/** Latin figures inside a Persian sentence, each in its own left-to-right run. */
export function isolateNumbers(s: string): ReactNode[] {
  const out: ReactNode[] = [];
  const re = /[−+-]?\d[\d.,]*%?/g;
  let at = 0;
  let m: RegExpExecArray | null;
  while ((m = re.exec(s))) {
    if (m.index > at) out.push(s.slice(at, m.index));
    out.push(<bdi key={m.index} dir="ltr" className="num">{m[0]}</bdi>);
    at = m.index + m[0].length;
  }
  if (at < s.length) out.push(s.slice(at));
  return out;
}

export function RasadLine() {
  const L = useL();
  const en = usePrefs((s) => s.lang) === 'en';
  const bars = useVisibleBars();
  const first = useMemo(() => readChart(bars, null, null, en)[0] ?? null, [bars, en]);
  if (!first) return null;
  return (
    <button type="button" className="rasad-line" onClick={openRasad} dir={en ? 'ltr' : 'rtl'} aria-label={L('رصد این چارت را می‌خواند', 'Rasad reads this chart')}>
      <span className="rasad-mark">{L('رصد', 'Rasad')}</span>
      <span className="rasad-first">{isolateNumbers(first)}</span>
    </button>
  );
}

/** The question and the bar it was asked on, and the three readings with their figures. */
export function ReadingsPanel({ reading, interval }: { reading: ChartReading; interval: string }) {
  const L = useL();
  const en = usePrefs((s) => s.lang) === 'en';
  const strengthTone = reading.strength < TRENDING_FLOOR ? 'muted' : isUp(reading) ? 'up' : isDown(reading) ? 'down' : '';
  const biasTone = isUp(reading) ? 'up' : isDown(reading) ? 'down' : '';
  const pct = (v: number, d: number) => `${(v * 100).toFixed(d)}%`;
  const col = (key: string, label: string, value: string, fraction: number, tone: string, why: ReactNode) => (
    <div className="rd-col" data-reading={key}>
      <span className="rd-label">{label}</span>
      <b className={`rd-value ${tone}`}>{value}</b>
      <span className={`rd-meter ${tone}`}><i style={{ width: `${Math.max(0, Math.min(1, fraction)) * 100}%` }} /></span>
      <span className="rd-why">{why}</span>
    </div>
  );
  return (
    <section className="rd-panel">
      <header className="rd-head"><span>{L('این بازار چه می‌کند', 'What this market is doing')}</span><b className="num rd-iv">{intervalLabel(interval)}</b></header>
      <div className="rd-cols">
        {col('strength', L('قدرت روند', 'Trend strength'), strengthLabel(reading, en), reading.strength / ADX_FULL_SCALE, strengthTone,
          <bdi dir="ltr" className="num">ADX {reading.strength.toFixed(0)}</bdi>)}
        {col('volatility', L('نوسان', 'Volatility'), volatilityLabel(reading, en), reading.volatility, '',
          en ? <><bdi dir="ltr" className="num">{pct(reading.volatility, 0)}</bdi> of its recent range</> : <><bdi dir="ltr" className="num">{pct(reading.volatility, 0)}</bdi> دامنه‌ی اخیر</>)}
        {col('bias', L('سوگیری', 'Bias'), biasLabel(reading, en), Math.abs(reading.bias) / BIAS_FULL_SCALE, biasTone,
          en ? <><bdi dir="ltr" className="num">{reading.bias > 0 ? '+' : ''}{pct(reading.bias, 2)}</bdi> between the averages</> : <><bdi dir="ltr" className="num">{reading.bias > 0 ? '+' : ''}{pct(reading.bias, 2)}</bdi> فاصله‌ی میانگین‌ها</>)}
      </div>
    </section>
  );
}

export function RasadHost() {
  const [open, setOpen] = useState(false);
  useEffect(() => {
    const on = () => setOpen(true);
    window.addEventListener(EVENT, on);
    return () => window.removeEventListener(EVENT, on);
  }, []);
  // Always one element, so the workspace's rows (plot, this line, the time bar, the panel) stay put.
  return (
    <div className="rasad-slot">
      <RasadLine />
      {open ? <RasadSheet onClose={() => setOpen(false)} /> : null}
    </div>
  );
}

function RasadSheet({ onClose }: { onClose(): void }) {
  const L = useL();
  const en = usePrefs((s) => s.lang) === 'en';
  const symbol = usePrefs((s) => s.symbol);
  const interval = usePrefs((s) => s.interval);
  const bars = useVisibleBars();
  const { reads, side } = useRasadReads();
  const history = usePaper((s) => s.history);
  const sentences = useMemo(() => readChart(bars, reads, side, en), [bars, reads, side, en]);
  const reading = useMemo(() => chartReading(bars), [bars]);
  const suggestion = useMemo(() => suggestAlert(bars, en), [bars, en]);
  // On this instrument, not the account's last trade anywhere: «معامله‌ی آخرم» under a gold chart means gold.
  const last = history.find((t) => t.symbol.toUpperCase() === symbol.toUpperCase());
  const review = last ? reviewTrade(factsOfPaper(last), en) : [];
  return createPortal(
    <div className="scrim center" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div className="dialog rasad-sheet" role="dialog" aria-modal="true" aria-label={L('رصد این چارت را می‌خواند', 'Rasad reads this chart')} dir={en ? 'ltr' : 'rtl'}
        onKeyDown={(e) => { if (e.key === 'Escape') { e.stopPropagation(); onClose(); } }}>
        <header className="dialog-head"><h2>{L('رصد این چارت را می‌خواند', 'Rasad reads this chart')}</h2>
          <button type="button" className="ib" onClick={onClose} aria-label={L('بستن', 'Close')} autoFocus><Icon name="ph-x" size="md" /></button></header>
        <div className="rasad-body">
          {reading ? <ReadingsPanel reading={reading} interval={interval} /> : null}
          {!sentences.length ? <p className="muted rasad-short">{L('هنوز کندل کافی نیست که بشود چیزی درباره‌ی این بازار گفت.', 'There are not enough bars here yet to say anything about this market.')}</p> : (
            <>
              <div className="rasad-sentences">
                {sentences.map((s, i) => <p key={i} className="rasad-sentence" data-i={i}>{isolateNumbers(s)}</p>)}
              </div>
              {suggestion ? (
                <div className="rasad-suggest">
                  <p className="rasad-why">{isolateNumbers(suggestion.why)}</p>
                  <button type="button" className="btn btn-primary rasad-alert" data-price={suggestion.price}
                    onClick={() => { onClose(); openAlertDialog({ symbol, price: suggestion.price }); }}>
                    <Icon name="ph-bell" size="sm" /><span>{L('وقتی رسید خبرم کنید', 'Tell me when it gets there')}</span>
                  </button>
                </div>
              ) : null}
              {review.length ? (
                <div className="rasad-review">
                  <span className="rasad-review-head">{L('آخرین معامله‌ی آزمایشی شما', 'Your last rehearsal trade')}</span>
                  {review.map((s, i) => <p key={i}>{isolateNumbers(s)}</p>)}
                </div>
              ) : null}
            </>
          )}
        </div>
      </div>
    </div>,
    document.body,
  );
}
