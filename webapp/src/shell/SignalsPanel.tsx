import { useEffect, useState } from 'react';
import { usePrefs, useT } from '../state/prefs';
import { useSession } from '../data/session';
import { activeSignals, intervalOfTimeframe, trackRecord, type Signal } from '../data/signals';
import { useOverlay } from '../state/overlay';
import { digitsOf } from '../state/market';
import { formatPrice } from '../chart/format';
import { SymbolLogo } from '../ui/SymbolLogo';
import { faAgo } from '../i18n/time';

// The platforms' signals beside the chart: the active ones for a member, the public record of
// closed ones for everyone. A click opens the symbol on the signal's interval with its entry, stop
// and targets drawn on the chart.
export function SignalsPanel({ onSignIn }: { onSignIn(): void }) {
  const t = useT();
  const signed = useSession((s) => Boolean(s.sessions.tradeyar || s.sessions.coineprofx));
  const [tab, setTab] = useState<'active' | 'record'>(signed ? 'active' : 'record');
  const [list, setList] = useState<Signal[] | null>(null);
  const [failed, setFailed] = useState(false);
  const { setSymbol, setInterval } = usePrefs();
  const show = useOverlay((s) => s.show);

  useEffect(() => {
    let alive = true;
    setList(null); setFailed(false);
    (tab === 'active' ? activeSignals() : trackRecord(40))
      .then((l) => { if (alive) setList(l); })
      .catch(() => { if (alive) { setFailed(true); setList([]); } });
    return () => { alive = false; };
  }, [tab, signed]);

  const open = (s: Signal) => {
    setSymbol(s.symbol);
    const iv = intervalOfTimeframe(s.timeframe);
    if (iv) setInterval(iv);
    show({ symbol: s.symbol, direction: s.direction, entry: s.entry, sl: s.sl, targets: s.targets, label: `${s.direction === 'long' ? 'Long' : 'Short'} ${s.timeframe}`, time: s.createdAt / 1000 });
  };

  const wins = (list ?? []).filter((s) => s.isWin).length;
  const closed = (list ?? []).filter((s) => s.isWin != null).length;

  return (
    <section className="panel-generic signals">
      <header className="panel-head"><span className="fa">{t('signals')}</span></header>
      <nav className="chips panel-chips">
        <button className={`chip${tab === 'active' ? ' on' : ''}`} onClick={() => setTab('active')}><span className="fa">{t('signalsActive')}</span></button>
        <button className={`chip${tab === 'record' ? ' on' : ''}`} onClick={() => setTab('record')}><span className="fa">{t('signalsRecord')}</span></button>
        {tab === 'record' && closed ? <span className="sig-rate num">{Math.round((wins / closed) * 100)}% win</span> : null}
      </nav>
      <div className="panel-body">
        {tab === 'active' && !signed ? (
          <div className="empty fa">{t('signalsSignIn')} <button className="link" onClick={onSignIn}>{t('signIn')}</button></div>
        ) : null}
        {list === null && (tab === 'record' || signed) ? <div className="empty fa">{t('loading')}…</div> : null}
        {list !== null && !list.length && (tab === 'record' || signed) ? <div className="empty fa">{failed ? t('networkError') : t('noResults')}</div> : null}
        {(list ?? []).map((s) => {
          const d = digitsOf(s.symbol);
          return (
            <button key={s.id} className="sig-row" onClick={() => open(s)}>
              <span className="sig-top">
                <SymbolLogo symbol={s.symbol} size={18} />
                <span className="sym">{s.symbol}</span>
                <span className={`sig-dir ${s.direction === 'long' ? 'up' : 'down'}`}>{s.direction === 'long' ? 'LONG' : 'SHORT'}</span>
                <span className="num muted">{s.timeframe}</span>
                <span className="ws-spacer" />
                {s.pctGain != null ? <span className={`num ${s.pctGain >= 0 ? 'up' : 'down'}`}>{s.pctGain >= 0 ? '+' : '−'}{Math.abs(s.pctGain).toFixed(2)}%</span> : null}
              </span>
              <span className="sig-levels num">
                {s.entry != null ? <span>E {formatPrice(s.entry, d)}</span> : null}
                {s.sl != null ? <span className="down">SL {formatPrice(s.sl, d)}</span> : null}
                {s.targets[0] != null ? <span className="up">TP {formatPrice(s.targets[0], d)}</span> : null}
                {s.rr != null ? <span className="muted">R:R {s.rr.toFixed(2)}</span> : null}
              </span>
              <span className="sig-time fa">{faAgo(s.closedAt ?? s.createdAt)}</span>
            </button>
          );
        })}
      </div>
    </section>
  );
}
