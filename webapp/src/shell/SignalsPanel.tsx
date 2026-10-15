import { useEffect, useMemo, useState } from 'react';
import { usePrefs, useT } from '../state/prefs';
import { useSession } from '../data/session';
import { activeSignals, intervalOfTimeframe, trackRecord, type Signal } from '../data/signals';
import { useOverlay } from '../state/overlay';
import { digitsOf, useMarket } from '../state/market';
import { formatPrice } from '../chart/format';
import { SymbolLogo } from '../ui/SymbolLogo';
import { Icon } from '../ui/Icon';
import { useNow } from '../ui/useFlash';
import { faAgo, faNum } from '../i18n/time';
import { useL } from '../pages/kit';
import './panels-x.css';

/** Where a signal stands: by the platform's status, its target hits, or the live price against its levels. */
export function signalState(s: Signal, last: number | null | undefined): { key: 'active' | 'tp' | 'sl' | 'win' | 'loss' | 'closed'; hits: number } {
  const hits = (s.hits ?? []).filter(Boolean).length;
  const st = s.status.toLowerCase();
  if (s.isWin === true) return { key: 'win', hits };
  if (s.isWin === false) return { key: 'loss', hits };
  if (/sl|stop/.test(st)) return { key: 'sl', hits };
  if (st && st !== 'active' && st !== 'open' && !/tp/.test(st)) return { key: 'closed', hits };
  let live = hits;
  if (last != null && s.targets.length) {
    const reached = s.targets.filter((tp) => (s.direction === 'long' ? last >= tp : last <= tp)).length;
    live = Math.max(live, reached);
    if (s.sl != null && (s.direction === 'long' ? last <= s.sl : last >= s.sl)) return { key: 'sl', hits: live };
  }
  return { key: live ? 'tp' : 'active', hits: live };
}

// The platforms' signals beside the chart: the active ones for a member, the public record of
// closed ones for everyone. Each row: the status (active, TP1–3 reached, stopped, won or lost), the
// confidence as a bar, entry, stop and every target (ticked when reached), R:R, and the platform's
// reasoning. A click opens the symbol on the signal's interval with its levels drawn on the chart;
// "this symbol" narrows the list; the chart's setup can be cleared from here.
export function SignalsPanel({ onSignIn }: { onSignIn(): void }) {
  const t = useT();
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const fa = lang === 'fa';
  const signed = useSession((s) => Boolean(s.sessions.tradeyar || s.sessions.coineprofx));
  const [tab, setTab] = useState<'active' | 'record'>(signed ? 'active' : 'record');
  const [list, setList] = useState<Signal[] | null>(null);
  const [failed, setFailed] = useState(false);
  const [mine, setMine] = useState(false);
  const [why, setWhy] = useState<string | null>(null);
  const { symbol, setSymbol, setInterval: setChartInterval } = usePrefs();
  const tickers = useMarket((s) => s.tickers);
  const setup = useOverlay((s) => s.setup);
  const show = useOverlay((s) => s.show);
  const clear = useOverlay((s) => s.clear);
  const now = useNow(60_000);

  useEffect(() => {
    let alive = true;
    setList(null); setFailed(false);
    const load = () => (tab === 'active' ? activeSignals() : trackRecord(40))
      .then((l) => { if (alive) setList(l); })
      .catch(() => { if (alive) { setFailed(true); setList([]); } });
    void load();
    const id = setInterval(load, 120_000);
    return () => { alive = false; clearInterval(id); };
  }, [tab, signed]);

  const open = (s: Signal) => {
    setSymbol(s.symbol);
    const iv = intervalOfTimeframe(s.timeframe);
    if (iv) setChartInterval(iv);
    show({ symbol: s.symbol, direction: s.direction, entry: s.entry, sl: s.sl, targets: s.targets, label: `${s.direction === 'long' ? 'Long' : 'Short'} ${s.timeframe}`, time: s.createdAt / 1000 });
  };

  const shown = useMemo(() => (list ?? []).filter((s) => !mine || s.symbol === symbol), [list, mine, symbol]);
  const wins = shown.filter((s) => s.isWin).length;
  const closed = shown.filter((s) => s.isWin != null).length;
  const stateName = (k: ReturnType<typeof signalState>['key'], hits: number) => ({
    active: L('فعال', 'Active'), tp: `TP${hits} ✓`, sl: L('حد ضرر خورد', 'Stopped'), win: L('سود', 'Win'), loss: L('زیان', 'Loss'), closed: L('بسته', 'Closed'),
  }[k]);

  return (
    <section className="panel-generic signals">
      <header className="panel-head">
        <span className="fa">{t('signals')}</span>
        <span className="ws-spacer" />
        {setup ? <button className="btn btn-quiet sm" onClick={clear} title={L('پاک کردن ستاپ از روی چارت', 'Clear the setup from the chart')}><Icon name="ph-x" size="sm" /><span className="fa">{L('پاک از چارت', 'Clear chart')}</span></button> : null}
      </header>
      <nav className="chips panel-chips">
        <button className={`chip${tab === 'active' ? ' on' : ''}`} onClick={() => setTab('active')}><span className="fa">{t('signalsActive')}</span></button>
        <button className={`chip${tab === 'record' ? ' on' : ''}`} onClick={() => setTab('record')}><span className="fa">{t('signalsRecord')}</span></button>
        <button className={`chip${mine ? ' on' : ''}`} onClick={() => setMine(!mine)} title={L('فقط همین نماد', 'This symbol only')}><span className="sym">{symbol}</span></button>
        {tab === 'record' && closed ? <span className="sig-rate num fa">{fa ? `${faNum(Math.round((wins / closed) * 100))}٪ برد` : `${Math.round((wins / closed) * 100)}% win`}</span> : null}
      </nav>
      <div className="panel-body">
        {tab === 'active' && !signed ? (
          <div className="empty fa">{t('signalsSignIn')} <button className="link" onClick={onSignIn}>{t('signIn')}</button></div>
        ) : null}
        {list === null && (tab === 'record' || signed) ? <div className="empty fa">{t('loading')}…</div> : null}
        {list !== null && !shown.length && (tab === 'record' || signed) ? <div className="empty fa">{failed ? t('networkError') : t('noResults')}</div> : null}
        {shown.map((s) => {
          const d = digitsOf(s.symbol);
          const state = signalState(s, tickers[s.symbol]?.last);
          const onChart = setup && setup.symbol === s.symbol && setup.entry === s.entry && setup.sl === s.sl;
          return (
            <div key={s.id} className={`sig-row${onChart ? ' on' : ''}`} role="button" tabIndex={0} onClick={() => open(s)} onKeyDown={(e) => { if (e.key === 'Enter') open(s); }}>
              <span className="sig-top">
                <SymbolLogo symbol={s.symbol} size={18} />
                <span className="sym">{s.symbol}</span>
                <span className={`sig-dir ${s.direction === 'long' ? 'up' : 'down'}`}>{s.direction === 'long' ? L('خرید', 'LONG') : L('فروش', 'SHORT')}</span>
                <span className="num muted">{s.timeframe}</span>
                <span className={`sig-state ${state.key}`}>{stateName(state.key, state.hits)}</span>
                <span className="ws-spacer" />
                {s.pctGain != null ? <span className={`num ${s.pctGain > 0 ? 'up' : s.pctGain < 0 ? 'down' : ''}`}>{s.pctGain > 0 ? '+' : s.pctGain < 0 ? '−' : ''}{Math.abs(s.pctGain).toFixed(2)}%</span> : null}
              </span>
              {s.confidence != null ? (
                <span className="sig-conf" title={L('اطمینان', 'Confidence')}>
                  <span className="bar"><i className={s.confidence >= 70 ? 'hi' : s.confidence >= 50 ? 'mid' : 'lo'} style={{ width: `${Math.max(4, Math.min(100, s.confidence))}%` }} /></span>
                  <span className="num">{Math.round(s.confidence)}%</span>
                </span>
              ) : null}
              <span className="sig-levels num">
                {s.entry != null ? <span>E {formatPrice(s.entry, d)}</span> : null}
                {s.sl != null ? <span className="down">SL {formatPrice(s.sl, d)}</span> : null}
                {s.targets.slice(0, 3).map((tp, i) => <span key={i} className={`up${i < state.hits ? ' hit' : ''}`}>TP{i + 1} {formatPrice(tp, d)}{i < state.hits ? ' ✓' : ''}</span>)}
                {s.rr != null ? <span className="muted">R:R {s.rr.toFixed(2)}</span> : null}
              </span>
              {s.rationale ? (
                why === s.id ? <span className="sig-why" dir="auto">{s.rationale}</span>
                  : <button className="link-btn fa sig-why-btn" onClick={(e) => { e.stopPropagation(); setWhy(s.id); }}>{L('چرا؟', 'Why?')}</button>
              ) : null}
              <span className="sig-time fa">{onChart ? <b className="sig-onchart">{L('روی چارت', 'On chart')} · </b> : null}{fa ? faAgo(s.closedAt ?? s.createdAt, now) : new Date(s.closedAt ?? s.createdAt).toISOString().slice(0, 16).replace('T', ' ')}</span>
            </div>
          );
        })}
      </div>
    </section>
  );
}
