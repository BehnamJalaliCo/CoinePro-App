// «میدان»'s band and result (OLD feature/chart ArenaChrome.kt): the clock above the replay controls —
// red under a minute while it runs, a rule showing how much is gone — and «شروع» / «تمام»; then the
// sheet: the total, the two halves out of 60 and 40, what the discipline half was made of, the
// streak, the best so far, «هم‌رسانی» and the market. No trades is not a zero: it is said, not scored.
import { useEffect } from 'react';
import { createPortal } from 'react-dom';
import {
  useArena, challengeFor, arenaToday, clockOf, total, played, streak, best, TIMER_SECONDS, URGENT_SECONDS, DISCIPLINE_POINTS, PROFIT_POINTS,
} from '../state/arena';
import { useMarket } from '../state/market';
import { usePrefs } from '../state/prefs';
import { useChartBus } from '../chart/chartBus';
import { canDraw } from '../data/candles';
import { isExpression } from '../data/symbols';
import { feed } from '../data/feed';
import { hasLogo, loadLogoMap } from '../ui/SymbolLogo';
import { foundingStamp } from '../data/founding';
import { renderShareCard, sharePicture } from '../pages/shareCard';
import { faNum } from '../i18n/time';
import { useL } from '../pages/kit';
import { Icon } from '../ui/Icon';
import { duelSymbols } from './DuelBar';
import type { Candle } from '../data/types';
import '../chart/rasad.css';

/** The catalogue the challenge is picked from, in a stable order: every plain symbol with artwork the chart can draw. */
export async function arenaSymbols(interval: string): Promise<string[]> {
  await loadLogoMap();
  const m = useMarket.getState();
  const all = new Set([...Object.keys(m.instruments), ...Object.keys(m.tickers)]);
  const list = [...all].filter((s) => !isExpression(s) && hasLogo(s) && canDraw(s, interval)).sort();
  return list.length ? list : duelSymbols(interval).filter(hasLogo);
}

/** Starts today's challenge: opens its market when it is another one, then puts the replay at its bar. */
export async function startArena(): Promise<'started' | 'none'> {
  const { symbol, interval } = usePrefs.getState();
  const symbols = await arenaSymbols(interval);
  const day = arenaToday();
  // The market comes from the date alone; only the bar inside it depends on the history.
  const pick = challengeFor(day, symbols, Number.MAX_SAFE_INTEGER);
  if (!pick) return 'none';
  let bars: Candle[];
  if (pick.symbol === symbol) bars = useChartBus.getState().bars;
  else {
    try { bars = (await feed.history(pick.symbol, interval)).bars; } catch { return 'none'; }
    usePrefs.getState().setSymbol(pick.symbol);
    await chartShows(bars);
  }
  const challenge = challengeFor(day, symbols, bars.length);
  if (!challenge || challenge.symbol !== pick.symbol) return 'none';
  return useArena.getState().begin(challenge, bars) ? 'started' : 'none';
}

function chartShows(bars: Candle[]): Promise<void> {
  const first = bars[0]?.t;
  return new Promise((resolve) => {
    const done = () => { clearTimeout(timer); off(); setTimeout(resolve, 50); };
    const off = useChartBus.subscribe((b, p) => { if (b.bars !== p.bars && b.bars[0]?.t === first) done(); });
    const timer = setTimeout(done, 15_000);
  });
}

export function ArenaBar() {
  const L = useL();
  const session = useArena((a) => a.session);
  if (!session || session.score) return null;
  const urgent = session.running && session.remaining <= URGENT_SECONDS;
  const progress = 1 - session.remaining / TIMER_SECONDS;
  return (
    <div className="arena-bar" role="timer" aria-label={L('میدان', 'Arena')} onMouseDown={(e) => e.stopPropagation()}>
      <div className="arena-row">
        <span className="arena-name">{L('میدان', 'Arena')}</span>
        <bdi dir="ltr" className={`num arena-clock${urgent ? ' urgent' : ''}`}>{clockOf(session.remaining)}</bdi>
        <button type="button" className="btn btn-primary sm arena-go" data-running={session.running ? '1' : '0'}
          onClick={() => (session.running ? useArena.getState().finish() : useArena.getState().start())}>
          {session.running ? L('تمام', 'Finish') : L('شروع', 'Start')}
        </button>
        <button type="button" className="ib" title={L('خروج از میدان', 'Leave the Arena')} aria-label={L('خروج از میدان', 'Leave the Arena')} onClick={() => useArena.getState().leave()}><Icon name="ph-x" size="sm" /></button>
      </div>
      <div className={`arena-rule${urgent ? ' urgent' : ''}`} style={{ width: `${Math.max(0, Math.min(1, progress)) * 100}%` }} />
    </div>
  );
}

export function ArenaResult() {
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const fa = lang === 'fa';
  const open = useArena((a) => a.result);
  const session = useArena((a) => a.session);
  const rows = useArena((a) => a.rows);
  useEffect(() => {
    if (!open) return;
    const esc = (e: KeyboardEvent) => { if (e.key === 'Escape') useArena.getState().closeResult(); };
    window.addEventListener('keydown', esc);
    return () => window.removeEventListener('keydown', esc);
  }, [open]);
  if (!open || !session?.score) return null;
  const s = session.score;
  const n = (v: number) => (fa ? faNum(v) : String(v));
  const days = streak(rows, arenaToday());
  const top = best(rows)?.total ?? null;
  const symbol = session.challenge.symbol;
  const close = () => useArena.getState().closeResult();
  const share = async () => {
    const cv = await renderShareCard({
      title: symbol, subtitle: L('پنج دقیقه، با امتیاز', 'Five minutes, scored'), headline: `${total(s)}/100`, tone: 'neutral',
      lines: [L(`شیوه‌ی معامله: ${faNum(s.discipline)} از ۶۰.`, `How it was traded: ${s.discipline} out of 60.`), L(`نتیجه: ${faNum(s.profit)} از ۴۰.`, `What it made: ${s.profit} out of 40.`)],
      footer: 'pro-chart.com', rtl: fa, badge: foundingStamp(!fa),
    });
    await sharePicture(cv, 'arena', L('میدان', 'Arena'));
  };
  return createPortal(
    <div className="scrim center" onMouseDown={(e) => { if (e.target === e.currentTarget) close(); }}>
      <div className="dialog arena-result" role="dialog" aria-modal="true" aria-label={L('پنج دقیقه، با امتیاز', 'Five minutes, scored')} dir={fa ? 'rtl' : 'ltr'}>
        <header className="dialog-head"><h2>{L('پنج دقیقه، با امتیاز', 'Five minutes, scored')}</h2>
          <button type="button" className="ib" onClick={close} aria-label={L('بستن', 'Close')}><Icon name="ph-x" size="md" /></button></header>
        <div className="arena-body">
          {!played(s) ? <p className="arena-none">{L('تماشا کردید و معامله نکردید. این هم یک تصمیم است و امتیاز نمی‌گیرد.', 'You watched and did not trade. That is a decision, and it is not scored.')}</p> : (
            <>
              <bdi dir="ltr" className="num arena-total">{total(s)}</bdi>
              <div className="arena-halves">
                <div data-half="discipline"><span>{L('چطور معامله کردید', 'How you traded')}</span><bdi dir="ltr" className="num"><b>{s.discipline}/{DISCIPLINE_POINTS}</b></bdi></div>
                <div data-half="profit"><span>{L('چه ساخت', 'What it made')}</span><bdi dir="ltr" className="num"><b>{s.profit}/{PROFIT_POINTS}</b></bdi></div>
              </div>
              <p className="arena-working">{L(`${n(s.withStop)} از ${n(s.trades)} معامله حد ضرر داشت و ${n(s.revenge)} خیلی زود بعد از یک ضرر باز شد.`, `${s.withStop} of ${s.trades} trades carried a stop, and ${s.revenge} followed a loss too closely.`)}</p>
              {days > 1 ? <p className="arena-streak">{L(`${n(days)} روز پشت سر هم.`, `${days} days in a row.`)}</p> : null}
              {top != null && top > 0 ? <p className="arena-best">{L('بهترین امتیاز شما تا حالا ', 'Your best so far is ')}<bdi dir="ltr" className="num">{top}</bdi>{L(' است.', '.')}</p> : null}
              <button type="button" className="btn btn-primary arena-share" onClick={() => void share()}>{L('هم‌رسانی', 'Share this')}</button>
            </>
          )}
          <bdi dir="ltr" className="arena-symbol sym">{symbol}</bdi>
        </div>
      </div>
    </div>,
    document.body,
  );
}
