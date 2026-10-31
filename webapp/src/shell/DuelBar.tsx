import { useDuel, roundFor, localEpochDay, judged, rightPercent, FORWARD_BARS, MINIMUM_ROUNDS, type DuelVerdict } from '../state/duel';
import { useWatchlist } from '../state/watchlist';
import { usePrefs } from '../state/prefs';
import { useChartBus } from '../chart/chartBus';
import { canDraw } from '../data/candles';
import { isExpression } from '../data/symbols';
import { feed } from '../data/feed';
import { faNum } from '../i18n/time';
import { useL } from '../pages/kit';
import type { Candle } from '../data/types';
import '../pages/extras.css';

// The duel's band (OLD feature/chart DuelChrome.kt): the question and the two answers; after the
// call, what the market did, the record or the reason there is none yet, and «پایان». It stands
// where the replay controls stand, so nothing on screen can step into the hidden bars.

/** The reader's own markets, in a stable order: every watchlist's plain symbols the chart can draw. */
export function duelSymbols(interval: string): string[] {
  const all = new Set(useWatchlist.getState().lists.flatMap((l) => l.symbols));
  return [...all].filter((s) => !isExpression(s) && canDraw(s, interval)).sort();
}

/** Starts today's round: opens its market when it is another one, then hides the next 20 bars. */
export async function startDuel(): Promise<'started' | 'none'> {
  const { symbol, interval } = usePrefs.getState();
  const symbols = duelSymbols(interval);
  const day = localEpochDay();
  // The market is picked from the date alone; only the bar inside it depends on the history.
  const pick = roundFor(day, symbols, Number.MAX_SAFE_INTEGER);
  if (!pick) return 'none';
  let bars: Candle[];
  if (pick.symbol === symbol) bars = useChartBus.getState().bars;
  else {
    try { bars = (await feed.history(pick.symbol, interval)).bars; } catch { return 'none'; }
    usePrefs.getState().setSymbol(pick.symbol);
    await chartShows(bars);
  }
  const round = roundFor(day, symbols, bars.length);
  if (!round || round.symbol !== pick.symbol) return 'none';
  return useDuel.getState().begin(round, bars) ? 'started' : 'none';
}

/** Resolves once the chart has drawn the bars of the market it was switched to (or after 15 s). */
function chartShows(bars: Candle[]): Promise<void> {
  const first = bars[0]?.t;
  return new Promise((resolve) => {
    const done = () => { clearTimeout(timer); off(); setTimeout(resolve, 50); };
    const off = useChartBus.subscribe((b, p) => { if (b.bars !== p.bars && b.bars[0]?.t === first) done(); });
    const timer = setTimeout(done, 15_000);
  });
}

export function DuelBar() {
  const L = useL();
  const fa = usePrefs((s) => s.lang) === 'fa';
  const session = useDuel((s) => s.session);
  const tally = useDuel((s) => s.tally);
  if (!session) return null;
  const n = (v: number) => (fa ? faNum(v) : String(v));
  const o = session.outcome;
  const verdict: Record<DuelVerdict, string> = {
    right: L('درست بود.', 'Right.'),
    wrong: L('نادرست بود.', 'Wrong.'),
    too_close: L('بازار آن‌قدر تکان نخورد که این خوانش درست یا نادرست باشد.', 'Too close to call.'),
  };
  const rate = rightPercent(tally);
  const move = o ? `${o.movePercent > 0 ? '+' : ''}${o.movePercent.toFixed(2)}%` : '';
  return (
    <div className="duel-bar" role="toolbar" aria-label={L('دوئل', 'Duel')} dir={fa ? 'rtl' : 'ltr'} onMouseDown={(e) => e.stopPropagation()}>
      <span className="duel-name fa">{L('دوئل', 'Duel')}</span>
      {!o ? (
        <>
          <span className="duel-q fa">{L(`${n(FORWARD_BARS)} کندل بعد، بازار به کدام سمت رفت؟`, `Which way did it go over the next ${FORWARD_BARS} candles?`)}</span>
          <button className="btn buy sm duel-pill" data-call="up" onClick={() => useDuel.getState().call('up')}><span className="fa">{L('بالا', 'Up')}</span></button>
          <button className="btn sell sm duel-pill" data-call="down" onClick={() => useDuel.getState().call('down')}><span className="fa">{L('پایین', 'Down')}</span></button>
        </>
      ) : (
        <>
          <span className="duel-out">
            <b className={`duel-verdict ${o.verdict} fa`}>{verdict[o.verdict]}</b>
            <span className="duel-sub fa">{L('بازار ', 'The market moved ')}<bdi className="num" dir="ltr">{move}</bdi>{L(' حرکت کرد.', '.')}</span>
            <span className="duel-sub fa duel-record">{session.alreadyAnswered
              ? L('امروز را قبلاً جواب داده‌اید، پس این دور ثبت نشد.', 'You have already answered today, so this round was not recorded.')
              : rate == null
                ? L(`${n(judged(tally))} دور از ${n(MINIMUM_ROUNDS)} دور. نرخ درستی با کمتر از این چیزی را نشان نمی‌دهد.`, `${judged(tally)} of ${MINIMUM_ROUNDS} rounds. A hit rate needs more than this to mean anything.`)
                : <><bdi className="num" dir="ltr">{rate.toFixed(0)}%</bdi>{L(` درست، از ${n(judged(tally))} دوری که بازار جوابشان را داد.`, ` right, of ${judged(tally)} rounds the market answered.`)}</>}</span>
          </span>
          <button className="btn btn-primary sm duel-pill duel-finish" onClick={() => useDuel.getState().finish()}><span className="fa">{L('پایان', 'Done')}</span></button>
        </>
      )}
    </div>
  );
}
