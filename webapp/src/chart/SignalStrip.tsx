// What sits immediately under the plot (OLD feature/chart ChartSignalStrip.kt and ProvenanceLine):
// the Now strip — the Setup score on a grey→gold scale with the studies it came from, then one pill
// per study: its state as a dot, its name and how often it has been right here («61% از 18»), or
// «کم‌داده» under eight samples — and the provenance line, every way this picture legitimately
// differs from the venue's own. Direction is green or red; confidence is grey to gold; never swapped.
import { useLayoutEffect, useRef, type RefObject } from 'react';
import { useL } from '../pages/kit';
import { usePrefs } from '../state/prefs';
import { useSignals, reportOf } from './signalStore';
import { exclusionsLine, isolateNumbers, percentOf, stateLabel, trustworthy, type MarketState } from './signals';

/** A state's colour: the market's own; neutral is muted rather than a third hue. */
export const stateColour = (s: MarketState) => (s === 'bull' ? 'var(--up)' : s === 'bear' ? 'var(--down)' : 'var(--text-3)');
/** Grey → gold by a 0..1 tone (lerp TextMuted → Gold). */
export const goldTone = (tone: number) => `color-mix(in srgb, var(--gold-strong) ${Math.round(Math.max(0, Math.min(1, tone)) * 100)}%, var(--text-3))`;

/** The pill's figure: «61% از 18», «کم‌داده», or nothing for a study with no completed signal. */
export function pillFigure(samples: number, percent: number, ok: boolean, english: boolean): string | null {
  if (samples === 0) return null;
  if (!ok) return english ? 'low data' : 'کم‌داده';
  return english ? `${percent}% of ${samples}` : `${percent}% از ${samples}`;
}

export function NowStrip() {
  const L = useL();
  const fa = usePrefs((s) => s.lang) === 'fa';
  const layer = useSignals((s) => s.layer);
  const open = useSignals((s) => s.openExplain);
  if (!layer.reads.length) return null;
  const english = !fa;
  const setup = layer.setup;
  const tone = goldTone(setup.score / 100);
  return (
    <div className="now-strip" dir={fa ? 'rtl' : 'ltr'} aria-label="now-strip" role="toolbar">
      {/* The chart's own number first: it answers «is anything happening»; the pills are the working. */}
      <button type="button" className="setup-chip" data-score={setup.score} data-side={setup.side} style={{ borderColor: `color-mix(in srgb, ${tone} 60%, transparent)` }} onClick={() => open(null)} aria-label="setup-score">
        <span className="setup-chip-label">{L('وضعیت', 'Setup')}</span>
        <b className="num" style={{ color: tone }}>{setup.score}</b>
        {setup.studies > 0 ? <span className="setup-chip-side" style={{ color: stateColour(setup.side) }}>{stateLabel(setup.side, english)}</span> : null}
      </button>
      {layer.reads.map((read) => {
        const report = reportOf(layer, read.id);
        const ok = trustworthy(report);
        const fig = pillFigure(report.samples, percentOf(report), ok, english);
        return (
          <button key={read.id} type="button" className="sig-pill" data-id={read.id} data-key={layer.keys[read.id]} data-state={read.state} data-samples={report.samples} onClick={() => open(read.id)} aria-label={`signal-pill-${layer.keys[read.id] ?? read.id}`}>
            <i className="sig-dot" style={{ background: stateColour(read.state) }} />
            <span className="sig-name num" dir="ltr">{layer.names[read.id] ?? read.id}</span>
            {fig ? <span className={`sig-rate${ok ? '' : ' thin'}`} style={ok ? { color: goldTone(report.winRate) } : undefined}>{ok ? isolateNumbers(fig) : fig}</span> : null}
          </button>
        );
      })}
    </div>
  );
}

/**
 * The provenance line: what is not in this picture, said once, under the venue caption — the web's
 * own provenance line («منبع کندل‌ها: …») sits in the legend, and this is the other half of it.
 */
export function ProvenanceLine() {
  const fa = usePrefs((s) => s.lang) === 'fa';
  const exclusions = useSignals((s) => s.exclusions);
  const line = exclusionsLine(exclusions, !fa);
  if (!line) return null;
  return <div className={`prov-line${fa ? ' fa' : ''}`} dir={fa ? 'rtl' : 'ltr'} data-kinds={exclusions.map((x) => x.kind).join(' ')}>{isolateNumbers(line)}</div>;
}

/** The Now strip under the plot; the plot gives up exactly its height. */
export function SignalFoot({ wrap }: { wrap: RefObject<HTMLDivElement | null> }) {
  const foot = useRef<HTMLDivElement>(null);
  const empty = useSignals((s) => s.layer.reads.length === 0);
  useLayoutEffect(() => {
    const host = wrap.current; const el = foot.current;
    if (!host) return;
    if (!el || empty) { host.style.setProperty('--sigfoot-h', '0px'); return; }
    const apply = () => host.style.setProperty('--sigfoot-h', `${Math.ceil(el.getBoundingClientRect().height)}px`);
    apply();
    const ro = new ResizeObserver(apply);
    ro.observe(el);
    return () => { ro.disconnect(); host.style.setProperty('--sigfoot-h', '0px'); };
  }, [wrap, empty]);
  if (empty) return null;
  return (
    <div ref={foot} className="sig-foot">
      <NowStrip />
    </div>
  );
}
