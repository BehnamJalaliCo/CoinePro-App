import { useState } from 'react';
import { useT } from '../state/prefs';
import { usePage } from '../state/page';
import { useSession } from '../data/session';
import { digitsOf } from '../state/market';
import { formatPrice } from '../chart/format';
import { uuid } from '../data/http';
import { ty, str } from '../data/member';
import { SymbolLogo } from '../ui/SymbolLogo';
import { MiniChart } from './MiniChart';
import { intervalOfTimeframe, signalDetail } from '../data/signals';
import { showSignal } from './SignalsPage';
import { Field, State, dateTime, errorText, money, parseNum, pct, useL, useLoad } from './kit';
import type { PageProps } from './registry';

// One signal (feature/signal-detail): its levels with which targets were hit, the live price, the
// score's parts, the rationale and the result; on the chart in one click; and, on TradeYar, sent to
// the connected exchange after an explicit confirmation (core/execution executeSignal).
export default function SignalPage({ param }: PageProps) {
  const L = useL();
  const t = useT();
  const close = usePage((s) => s.close);
  const open = usePage((s) => s.open);
  const sessions = useSession((s) => s.sessions);
  const [backend, id] = (() => { const m = /^(tradeyar|coineprofx)-(.+)$/.exec(param ?? ''); return m ? [m[1] as 'tradeyar' | 'coineprofx', m[2]!] : [sessions.tradeyar ? 'tradeyar' as const : 'coineprofx' as const, param ?? '']; })();
  const sig = useLoad(() => signalDetail(backend, id), [backend, id]);
  const [qty, setQty] = useState('');
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const s = sig.data;
  const d = s ? digitsOf(s.symbol) : 2;
  const f = (v: number | null | undefined) => (v == null ? '—' : formatPrice(v, d));

  const execute = async () => {
    if (!s) return;
    const q = parseNum(qty);
    if (!q || q <= 0) { setMsg(L('مقدار را وارد کنید.', 'Enter the quantity.')); return; }
    if (!window.confirm(`${L('این سیگنال روی صرافی اجرا می‌شود:', 'This executes the signal on the exchange:')}\n${s.symbol} ${s.direction.toUpperCase()} ${q}`)) return;
    setBusy(true); setMsg(null);
    try {
      const r = await ty<{ execution?: Record<string, unknown> }>('api/mobile/v1/executions', { body: { signal_id: Number(s.id), venue: 'lbank', quantity: q, client_request_id: uuid(), confirmed: true } });
      setMsg(`${L('فرستاده شد', 'Sent')}: ${str(r.execution?.status) || 'queued'}`);
    } catch (e) { setMsg(errorText(e, t)); } finally { setBusy(false); }
  };

  return (
    <div className="page-inner narrow">
      <State loading={sig.loading} error={sig.error} onRetry={sig.reload}>
        {s ? (
          <div className="stack">
            <div className="row">
              <SymbolLogo symbol={s.symbol} size={28} /><h2 className="sym">{s.symbol}</h2>
              <span className={`sig-dir ${s.direction === 'long' ? 'up' : 'down'}`}>{s.direction === 'long' ? 'LONG' : 'SHORT'}</span>
              <span className="num muted">{s.timeframe} · {s.status}</span>
              <span className="ws-spacer" />
              <button className="btn btn-primary sm" onClick={() => { showSignal(s); close(); }}>{L('نمایش روی چارت', 'Show on the chart')}</button>
            </div>
            <div className="card"><MiniChart symbol={s.symbol} interval={intervalOfTimeframe(s.timeframe) ?? '60'} levels={[
              ...(s.entry != null ? [{ price: s.entry, color: '#2962FF', title: 'E' }] : []),
              ...(s.sl != null ? [{ price: s.sl, color: '#F23645', title: 'SL' }] : []),
              ...s.targets.map((p, i) => ({ price: p, color: '#089981', title: `TP${i + 1}` })),
            ]} /></div>
            <div className="bt-stats wide">
              {([
                [L('ورود', 'Entry'), s.entryLow != null && s.entryHigh != null && s.entryLow !== s.entryHigh ? `${f(s.entryLow)} – ${f(s.entryHigh)}` : f(s.entry)],
                [L('حد ضرر', 'Stop loss'), f(s.sl), 'down'],
                ...s.targets.map((tp, i): [string, string, string] => [`TP${i + 1}${s.hits?.[i] ? ' ✓' : ''}`, f(tp), 'up']),
                ['R:R', s.rr != null ? s.rr.toFixed(2) : '—'],
                [L('اعتماد', 'Confidence'), s.confidence != null ? `${Math.round(s.confidence)}%` : '—'],
                [L('قیمت فعلی', 'Current price'), f(s.current)],
                [L('نتیجه', 'Result'), `${pct(s.pctGain)}${s.pnlUsd != null ? ` · ${money(s.pnlUsd)}$` : ''}`, (s.pctGain ?? 0) >= 0 ? 'up' : 'down'],
              ] as [string, string, string?][]).map(([k, v, tone]) => <div key={k} className="bt-stat"><span className="bt-k fa">{k}</span><span className={`bt-v num ${tone ?? ''}`}>{v}</span></div>)}
            </div>
            {s.breakdown ? (
              <section className="card"><h3 className="fa">{L('اجزای امتیاز', 'Score parts')}</h3>
                {([[L('تکنیکال', 'Technical'), s.breakdown.technical], [L('الگو', 'Pattern'), s.breakdown.pattern], [L('یادگیری ماشین', 'Machine learning'), s.breakdown.ml]] as [string, number | null][]).map(([k, v]) => v == null ? null : (
                  <div key={k} className="meter"><span className="fa">{k}</span><span className="bar"><i style={{ width: `${Math.min(100, v <= 1 ? v * 100 : v)}%` }} /></span><span className="num">{Math.round(v <= 1 ? v * 100 : v)}</span></div>
                ))}
              </section>
            ) : null}
            {s.rationale ? <section className="card"><h3 className="fa">{L('دلیل', 'Rationale')}</h3><p className="fa" dir="auto">{s.rationale}</p></section> : null}
            <p className="muted small num">{dateTime(s.createdAt)}{s.closedAt ? ` → ${dateTime(s.closedAt)}` : ''}{s.closeReason ? ` · ${s.closeReason}` : ''}</p>
            {backend === 'tradeyar' && sessions.tradeyar && s.status.toLowerCase() === 'active' ? (
              <form className="card form" onSubmit={(e) => { e.preventDefault(); void execute(); }}>
                <h3 className="fa">{L('اجرا روی صرافی', 'Execute on the exchange')}</h3>
                <Field label={L('مقدار', 'Quantity')}><input className="num" dir="ltr" inputMode="decimal" value={qty} onChange={(e) => setQty(e.target.value)} /></Field>
                {msg ? <p className="fa">{msg}</p> : null}
                <div className="row">
                  <button className={`btn ${s.direction === 'long' ? 'buy' : 'sell'}`} disabled={busy}>{L('اجرای سیگنال', 'Execute the signal')}</button>
                  <button type="button" className="btn btn-quiet sm" onClick={() => open('connections')}>{L('اتصال صرافی', 'Exchange connection')}</button>
                </div>
                <p className="muted small fa">{L('اجرا فقط وقتی انجام می‌شود که سرور اتصال صرافی را تأیید کرده باشد.', 'Execution happens only when the server has verified the exchange link.')}</p>
              </form>
            ) : null}
          </div>
        ) : null}
      </State>
    </div>
  );
}
