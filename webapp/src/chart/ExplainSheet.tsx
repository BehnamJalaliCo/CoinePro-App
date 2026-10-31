// What a tap on a signal opens (OLD feature/chart ExplainSheet.kt, ChartScreen.kt's EXPLAIN sheet):
// what the study is saying now, what it is, how it has done here — win rate with its sample size
// and the horizon it was measured over, average R, the last five outcomes as dots — the suggested
// stop, «روی سیگنال بعدی هشدار بسازید» (the alert dialog), «در ریپلی تمرین کنید» (replay from the
// signal), what it draws on the candles, «روی تایم‌فریم‌های دیگر», and the one line that is not
// negotiable. Tapping the Setup score opens the chart as a whole: the score and every study in it.
import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { useL } from '../pages/kit';
import { faNum } from '../i18n/time';
import { usePrefs } from '../state/prefs';
import { useStudies } from '../state/studies';
import { useReplay } from '../state/replay';
import { Icon } from '../ui/Icon';
import { helpKey, loadHelp } from '../pages/helpLib';
import { entryOf } from './indicatorCatalog';
import { formatPrice } from './format';
import { openAlertDialog } from './actions';
import { useChartBus } from './chartBus';
import { readAcross, reportOf, useSignals, type SignalLayer } from './signalStore';
import { goldTone, stateColour } from './SignalStrip';
import {
  HORIZONS, MARKER_STYLES, formatR, isolateNumbers, noteText, percentOf, stateLabel, trustworthy,
  type ConfidenceReport, type MarkerStyle, type SignalRead,
} from './signals';

/** The fewest bars replay can rewind through (Replay.MINIMUM_BARS). */
export const REPLAY_MIN_BARS = 30;

/** The sentence for one study in the reader's language, figures isolated (ChartSignalLayer.sentence). */
export function sentenceOf(layer: SignalLayer, id: string, english: boolean): string | null {
  const read = layer.reads.find((r) => r.id === id);
  if (!read || read.note.shape === 'quiet') return null;
  return isolateNumbers(noteText(read.note, english));
}

/** The help library's first sentence: what the study is, in one line rather than a lesson. */
export function firstSentence(text: string | null): string | null {
  if (!text) return null;
  const m = /^(.+?[.؛!?])(\s|$)/.exec(text.trim());
  return (m ? m[1]! : text.trim()).replace(/[.؛]$/, '');
}

/** Where replay starts for «practise this»: on the study's newest signal, or 55% in without one. */
export function practiseStart(read: SignalRead | null, barsLen: number): number {
  const e = read?.events[read.events.length - 1];
  return e ? e.bar : Math.floor(barsLen * 0.55);
}

export function ExplainSheetHost({ digits }: { digits: number }) {
  const explaining = useSignals((s) => s.explaining);
  const close = useSignals((s) => s.closeExplain);
  const symbol = usePrefs((s) => s.symbol);
  const interval = usePrefs((s) => s.interval);
  // A sheet about one chart closes when the chart changes under it.
  useEffect(() => { close(); useSignals.setState({ across: {} }); }, [symbol, interval, close]);
  if (!explaining) return null;
  return <ExplainSheet id={explaining.id} digits={digits} onClose={close} />;
}

function ExplainSheet({ id, digits, onClose }: { id: string | null; digits: number; onClose(): void }) {
  const L = useL();
  const fa = usePrefs((s) => s.lang) === 'fa';
  const layer = useSignals((s) => s.layer);
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') { e.stopPropagation(); onClose(); } };
    window.addEventListener('keydown', onKey, true);
    return () => window.removeEventListener('keydown', onKey, true);
  }, [onClose]);
  const title = L('چارت چه می‌گوید', 'What the chart says');
  const subtitle = id ? layer.names[id] ?? '' : L('وضعیت', 'Setup');
  return createPortal(
    <div className="scrim center" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div className="dialog tv-small ev-sheet explain-sheet" role="dialog" aria-modal="true" aria-label={title} dir={fa ? 'rtl' : 'ltr'} data-id={id ?? ''}>
        <header className="dialog-head">
          <div className="ev-sheet-head">
            <h2 className={fa ? 'fa' : ''}>{title}</h2>
            <p className="ev-sheet-sub muted num" dir="ltr">{subtitle}</p>
          </div>
          <button type="button" className="ib" onClick={onClose} aria-label={L('بستن', 'Close')}><Icon name="ph-x" size="md" /></button>
        </header>
        <div className="ev-sheet-body xp-body">
          {id == null ? <ChartExplanation layer={layer} /> : <StudyExplanation id={id} layer={layer} digits={digits} onClose={onClose} />}
          <p className={`xp-foot muted${fa ? ' fa' : ''}`}>{L('خوانشی از گذشته است، نه توصیه.', 'A reading of the past, not advice.')}</p>
        </div>
      </div>
    </div>,
    document.body,
  );
}

/** The chart as a whole: the score, the average win rate under it, and every study that voted. */
function ChartExplanation({ layer }: { layer: SignalLayer }) {
  const L = useL();
  const fa = usePrefs((s) => s.lang) === 'fa';
  const select = useSignals((s) => s.openExplain);
  const setup = layer.setup;
  const n = (v: number) => (fa ? faNum(v) : String(v));
  const rate = setup.winRate;
  const from = rate == null
    ? L(`${n(setup.studies)} اندیکاتور · ${n(setup.signals)} سیگنال`, `${setup.studies} indicators · ${setup.signals} signals`)
    : L(`${n(setup.studies)} اندیکاتور · ${n(setup.signals)} سیگنال · میانگین برد ${Math.round(rate * 100)}%`, `${setup.studies} indicators · ${setup.signals} signals · ${Math.round(rate * 100)}% average win rate`);
  return (
    <>
      <div className="xp-score">
        <b className="num" style={{ color: goldTone(setup.score / 100) }}>{setup.score}</b>
        <div>
          <div className="xp-side" style={{ color: stateColour(setup.side) }}>{stateLabel(setup.side, !fa)}</div>
          <div className="xp-from muted">{isolateNumbers(from)}</div>
        </div>
      </div>
      <hr className="xp-rule" />
      {layer.reads.map((read) => {
        const report = reportOf(layer, read.id);
        return (
          <button key={read.id} type="button" className="xp-row" data-id={read.id} onClick={() => select(read.id)}>
            <i className="sig-dot big" style={{ background: stateColour(read.state) }} />
            <span className="xp-row-text">{sentenceOf(layer, read.id, !fa) ?? read.id}</span>
            {trustworthy(report) ? <span className="xp-row-rate" style={{ color: goldTone(report.winRate) }}>{isolateNumbers(L(`${percentOf(report)}% از ${report.samples}`, `${percentOf(report)}% of ${report.samples}`))}</span> : null}
          </button>
        );
      })}
    </>
  );
}

function StudyExplanation({ id, layer, digits, onClose }: { id: string; layer: SignalLayer; digits: number; onClose(): void }) {
  const L = useL();
  const fa = usePrefs((s) => s.lang) === 'fa';
  const symbol = usePrefs((s) => s.symbol);
  const interval = usePrefs((s) => s.interval);
  const studies = useStudies((s) => s.list);
  const replaying = useReplay((r) => r.slice != null || r.selecting);
  const styles = useSignals((s) => s.markerStyles);
  const setStyle = useSignals((s) => s.setMarkerStyle);
  const across = useSignals((s) => s.across[id]);
  const setAcross = useSignals((s) => s.setAcross);
  const read = layer.reads.find((r) => r.id === id) ?? null;
  const key = layer.keys[id] ?? studies.find((s) => s.uid === id)?.key ?? '';
  const [what, setWhat] = useState<string | null>(null);
  useEffect(() => {
    let alive = true;
    setWhat(null);
    void loadHelp().then((lib) => {
      const k = helpKey(lib, key);
      const w = k ? lib[k]?.what : undefined;
      if (alive) setWhat(firstSentence((fa ? w?.fa : w?.en) ?? null));
    }).catch(() => undefined);
    return () => { alive = false; };
  }, [key, fa]);
  if (!read) return <p className={`muted${fa ? ' fa' : ''}`}>{L('این اندیکاتور دیگر روی چارت نیست.', 'This indicator is no longer on the chart.')}</p>;
  const report = reportOf(layer, id);
  const entry = entryOf(key);
  const sentence = sentenceOf(layer, id, !fa);
  const style: MarkerStyle = styles[key] ?? 'labels';
  const bars = useChartBus.getState().bars;
  const canPractise = !replaying && bars.length >= REPLAY_MIN_BARS;
  const st = studies.find((s) => s.uid === id);

  const alert = () => {
    const last = layer.bars[layer.bars.length - 1];
    if (st && key !== '__script') openAlertDialog({ symbol, study: { uid: id, key, plot: 0 } });
    else openAlertDialog({ symbol, price: last?.c });
    onClose();
  };
  const practise = () => {
    const at = practiseStart(read, layer.bars.length);
    const t = layer.bars[Math.max(0, Math.min(layer.bars.length - 1, at))]?.t;
    if (t != null) useReplay.getState().start(bars, t, { keepView: false });
    onClose();
  };
  const askAcross = () => {
    if (!st) return;
    setAcross(id, 'loading');
    void readAcross(st, symbol, interval, !fa, studies).then((rows) => setAcross(id, rows));
  };

  return (
    <>
      <div className="xp-state">
        <i className="sig-dot big" style={{ background: stateColour(read.state) }} />
        <b style={{ color: stateColour(read.state) }}>{stateLabel(read.state, !fa)}</b>
      </div>
      {sentence ? <p className="xp-sentence" data-sentence>{sentence}</p> : null}
      {entry ? <p className="xp-what muted"><span>{fa ? entry.fa : entry.en}</span>{what ? <span className="xp-what-help">{` — ${what}`}</span> : null}</p> : null}
      <hr className="xp-rule" />
      <ConfidenceCard report={report} />
      {read.stop != null ? (
        <p className="xp-stop" data-stop={read.stop}>{isolateNumbers(read.events[read.events.length - 1]?.side === 'sell'
          ? L(`حد ضرر پیشنهادی: بالای ${formatPrice(read.stop, digits)}`, `Suggested stop: above ${formatPrice(read.stop, digits)}`)
          : L(`حد ضرر پیشنهادی: زیر ${formatPrice(read.stop, digits)}`, `Suggested stop: below ${formatPrice(read.stop, digits)}`))}</p>
      ) : null}
      <button type="button" className="btn btn-quiet xp-btn" data-act="alert" onClick={alert}><Icon name="ph-bell" size="sm" /><span className="fa">{L('روی سیگنال بعدی هشدار بسازید', 'Alert me on the next signal')}</span></button>
      {canPractise ? <button type="button" className="btn btn-quiet xp-btn" data-act="practise" onClick={practise}><Icon name="ph-play" size="sm" /><span className="fa">{L('در ریپلی تمرین کنید', 'Practise this in replay')}</span></button> : null}
      <hr className="xp-rule" />
      <div className="xp-label muted">{L('روی کندل‌ها', 'On the candles')}</div>
      <div className="xp-chips" role="radiogroup" aria-label="marker-style-row">
        {MARKER_STYLES.map((m) => (
          <button key={m} type="button" role="radio" aria-checked={m === style} className={`xp-chip${m === style ? ' on' : ''}`} data-style={m} onClick={() => setStyle(key, m)}>
            {m === 'labels' ? L('با برچسب', 'Labels') : m === 'triangles' ? L('فقط مثلث', 'Triangles only') : L('خاموش', 'Off')}
          </button>
        ))}
      </div>
      {st ? (
        across == null ? <button type="button" className="btn btn-quiet xp-btn" data-act="timeframes" onClick={askAcross}><Icon name="ph-stack" size="sm" /><span className="fa">{L('روی تایم‌فریم‌های دیگر', 'Show on other timeframes')}</span></button>
          : across === 'loading' ? <p className="xp-tf-wait muted">…</p>
            : !across.length ? <p className={`muted${fa ? ' fa' : ''}`} data-tf-none>{L('هیچ تایم‌فریم دیگری برای این نماد جواب نداد.', 'No other bar length answered for this symbol.')}</p>
              : (
                <div className="xp-tf">
                  {across.map((row) => (
                    <div key={row.timeframe} className="xp-tf-row" data-tf={row.timeframe} data-state={row.state}>
                      <i className="sig-dot" style={{ background: stateColour(row.state) }} />
                      <b className="num">{row.timeframe}</b>
                      <span className="muted">{isolateNumbers(row.sentence)}</span>
                    </div>
                  ))}
                </div>
              )
      ) : null}
    </>
  );
}

/** The base rate with everything needed to discount it: N, the horizon, average R, the last five. */
function ConfidenceCard({ report }: { report: ConfidenceReport }) {
  const L = useL();
  const fa = usePrefs((s) => s.lang) === 'fa';
  const setHorizon = useSignals((s) => s.setHorizon);
  const n = (v: number) => (fa ? faNum(v) : String(v));
  return (
    <div className="xp-conf" data-samples={report.samples} data-horizon={report.horizon}>
      <div className="xp-conf-title">{L('اینجا چطور کار کرده', 'How it has done here')}</div>
      {report.samples === 0 ? (
        <p className="muted">{L('هنوز سیگنال کاملی روی این سری نیست.', 'No completed signals on this series yet.')}</p>
      ) : (
        <>
          <p className="xp-conf-line" data-line>{isolateNumbers(trustworthy(report)
            ? L(`در ${percentOf(report)}% از ${n(report.samples)} بار درست بوده، با اندازه‌گیری ${n(report.horizon)} کندل بعد.`, `Right ${percentOf(report)}% of ${report.samples} times, measured ${report.horizon} bars later.`)
            : L(`فقط ${n(report.samples)} سیگنال کامل — کمتر از آن است که عدد بدهیم.`, `Only ${report.samples} completed signals — too few to put a figure on.`))}</p>
          <p className="xp-conf-r">{isolateNumbers(L(`نتیجه‌ی میانگین: ${formatR(report.averageR)} برابر ریسک.`, `Average result: ${formatR(report.averageR)} times the risk.`))}</p>
          <div className="xp-dots" data-recent={report.recent.map((w) => (w ? 1 : 0)).join('')}>
            {report.recent.map((w, i) => <i key={i} className="sig-dot" style={{ background: w ? 'var(--up)' : 'var(--down)' }} />)}
          </div>
        </>
      )}
      <div className="xp-chips" role="radiogroup" aria-label="horizon">
        {HORIZONS.map((h) => (
          <button key={h} type="button" role="radio" aria-checked={report.horizon === h} className={`xp-chip${report.horizon === h ? ' on' : ''}`} data-horizon={h} onClick={() => setHorizon(h)}>
            {L(`${n(h)} کندل`, `${h} bars`)}
          </button>
        ))}
      </div>
    </div>
  );
}
