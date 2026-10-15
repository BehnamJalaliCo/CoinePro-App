import { useMemo, useState } from 'react';
import { usePrefs } from '../state/prefs';
import { usePage } from '../state/page';
import { useOverlay } from '../state/overlay';
import { useSession } from '../data/session';
import { signalList, trackRecord, intervalOfTimeframe, type Signal } from '../data/signals';
import { digitsOf } from '../state/market';
import { formatPrice } from '../chart/format';
import { SymbolLogo } from '../ui/SymbolLogo';
import { faAgo } from '../i18n/time';
import { State, Tabs, dateTime, pct, useL, useLoad } from './kit';
import type { PageProps } from './registry';

// Signals (feature/signals): active, recent and closed signals of the platform the reader picks,
// with filters, the public track record for everyone, and each one opening its detail page or
// going straight onto the chart with entry, stop and targets.
type Tab = 'active' | 'recent' | 'closed' | 'record';

export function showSignal(s: Signal): void {
  const p = usePrefs.getState();
  p.setSymbol(s.symbol);
  const iv = intervalOfTimeframe(s.timeframe);
  if (iv) p.setInterval(iv);
  useOverlay.getState().show({ symbol: s.symbol, direction: s.direction, entry: s.entry, sl: s.sl, targets: s.targets, label: `${s.direction === 'long' ? 'Long' : 'Short'} ${s.timeframe}`, time: s.createdAt / 1000 });
}

export default function SignalsPage({ onSignIn }: PageProps) {
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const open = usePage((s) => s.open);
  const close = usePage((s) => s.close);
  const sessions = useSession((s) => s.sessions);
  const signed = Boolean(sessions.tradeyar || sessions.coineprofx);
  const [backend, setBackend] = useState<'tradeyar' | 'coineprofx'>(sessions.tradeyar ? 'tradeyar' : 'coineprofx');
  const [tab, setTab] = useState<Tab>(signed ? 'active' : 'record');
  const [dir, setDir] = useState<'all' | 'long' | 'short'>('all');
  const [q, setQ] = useState('');
  const list = useLoad(() => (tab === 'record' ? trackRecord(100) : sessions[backend] ? signalList(backend, tab) : Promise.resolve([])), [tab, backend, Boolean(sessions[backend])]);

  const rows = useMemo(() => (list.data ?? []).filter((s) => (dir === 'all' || s.direction === dir) && (!q || s.symbol.includes(q.toUpperCase()))), [list.data, dir, q]);
  const closed = rows.filter((s) => s.isWin != null || s.pctGain != null);
  const wins = closed.filter((s) => s.isWin ?? (s.pctGain ?? 0) > 0).length;

  return (
    <div className="page-inner">
      <div className="row">
        {sessions.tradeyar && sessions.coineprofx ? <Tabs value={backend} onChange={setBackend} tabs={[{ id: 'tradeyar', label: L('رمزارز', 'Crypto') }, { id: 'coineprofx', label: L('فارکس', 'Forex') }]} /> : null}
        <Tabs value={tab} onChange={setTab} tabs={[
          ...(signed ? [{ id: 'active' as Tab, label: L('فعال', 'Active') }, { id: 'recent' as Tab, label: L('اخیر', 'Recent') }, { id: 'closed' as Tab, label: L('بسته‌شده', 'Closed') }] : []),
          { id: 'record', label: L('کارنامه‌ی عمومی', 'Public track record') },
        ]} />
      </div>
      <div className="row">
        <Tabs value={dir} onChange={setDir} tabs={[{ id: 'all', label: L('همه', 'All') }, { id: 'long', label: L('خرید', 'Long') }, { id: 'short', label: L('فروش', 'Short') }]} />
        <input className="scr-search" value={q} onChange={(e) => setQ(e.target.value)} placeholder={L('نماد', 'Symbol')} dir="ltr" />
        {closed.length ? <span className="num muted">{wins}/{closed.length} · {((wins / closed.length) * 100).toFixed(0)}% win</span> : null}
      </div>
      {!signed && tab !== 'record' ? <div className="empty fa">{L('سیگنال‌های فعال برای اعضاست.', 'Active signals are for members.')} <button className="link" onClick={onSignIn}>{L('ورود', 'Sign in')}</button></div> : (
        <State loading={list.loading} error={list.error} onRetry={list.reload} empty={!rows.length}>
          <table className="table">
            <thead><tr><th>{L('نماد', 'Symbol')}</th><th>{L('جهت', 'Side')}</th><th>TF</th><th className="r">{L('ورود', 'Entry')}</th><th className="r">SL</th><th className="r">TP</th><th className="r">R:R</th><th className="r">{L('اعتماد', 'Confidence')}</th><th className="r">{L('نتیجه', 'Result')}</th><th className="r">{L('زمان', 'Time')}</th><th /></tr></thead>
            <tbody>{rows.map((s) => {
              const d = digitsOf(s.symbol);
              const f = (v: number | null | undefined) => (v == null ? '—' : formatPrice(v, d));
              return (
                <tr key={`${s.backend ?? ''}-${s.id}`} className="click" onClick={() => (s.backend ? open('signal', `${s.backend}-${s.id}`) : (showSignal(s), close()))}>
                  <td><span className="row"><SymbolLogo symbol={s.symbol} size={18} /><b className="sym">{s.symbol}</b></span></td>
                  <td className={s.direction === 'long' ? 'up' : 'down'}>{s.direction === 'long' ? 'LONG' : 'SHORT'}</td>
                  <td className="num">{s.timeframe}</td><td className="r num">{f(s.entry)}</td><td className="r num down">{f(s.sl)}</td><td className="r num up">{f(s.targets[0])}</td>
                  <td className="r num">{s.rr != null ? s.rr.toFixed(2) : '—'}</td><td className="r num">{s.confidence != null ? `${Math.round(s.confidence)}%` : '—'}</td>
                  <td className={`r num ${(s.pctGain ?? 0) >= 0 ? 'up' : 'down'}`}>{pct(s.pctGain)}</td>
                  <td className="r num">{lang === 'fa' ? faAgo(s.closedAt ?? s.createdAt) : dateTime(s.closedAt ?? s.createdAt)}</td>
                  <td className="r"><button className="btn btn-quiet sm" onClick={(e) => { e.stopPropagation(); showSignal(s); close(); }}>{L('روی چارت', 'On chart')}</button></td>
                </tr>
              );
            })}</tbody>
          </table>
        </State>
      )}
    </div>
  );
}
