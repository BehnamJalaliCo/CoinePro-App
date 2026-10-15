import { useState } from 'react';
import { usePrefs, useT } from '../state/prefs';
import { usePage } from '../state/page';
import { useOverlay } from '../state/overlay';
import { useSession } from '../data/session';
import { ty, fx, num, str } from '../data/member';
import { HttpError } from '../data/http';
import { digitsOf } from '../state/market';
import { formatPrice } from '../chart/format';
import { MiniChart } from './MiniChart';
import { Field, State, Tabs, errorText, useL, useLoad } from './kit';
import type { Backend } from '../data/symbols';

// The AI setup builder (feature/ai, core/aisignal): the reader's symbol, timeframe, style, bias,
// risk appetite, minimum R:R and account size go to the platform's model; the job is polled until
// the setup comes back with entry, stop, targets, confidence and its reasons, then drawn on a chart.
const CRYPTO = ['BTCUSDT', 'ETHUSDT', 'SOLUSDT', 'BNBUSDT', 'XRPUSDT', 'ADAUSDT', 'DOGEUSDT', 'TRXUSDT'];
const FOREX = ['XAUUSD', 'XAGUSD', 'EURUSD', 'GBPUSD', 'USDJPY', 'USDCHF', 'AUDUSD', 'USDCAD'];
const TF: Record<Backend, string[]> = { tradeyar: ['5m', '15m', '30m', '1h', '4h', '1d'], coineprofx: ['M5', 'M15', 'H1', 'H4', 'D1'] };
const IV: Record<string, string> = { '5m': '5', '15m': '15', '30m': '30', '1h': '60', '4h': '240', '1d': '1D', M5: '5', M15: '15', H1: '60', H4: '240', D1: '1D' };
const call = <T,>(b: Backend, p: string, o = {}) => (b === 'tradeyar' ? ty<T>(`api/mobile/v1/ai/${p}`, o) : fx<T>(`user/ai-signal/${p}`, o));

interface Setup { symbol: string; direction: 'long' | 'short'; timeframe: string; entry: number | null; zoneLow: number | null; zoneHigh: number | null; sl: number | null; tps: number[]; confidence: number | null; rr: number | null; rationale: string; warnings: string[]; lot: number | null }

function readSetup(r: Record<string, unknown>): Setup {
  const zone = (r.entry_zone ?? {}) as Record<string, unknown>;
  const conf = num(r.confidence);
  return {
    symbol: str(r.symbol), direction: str(r.direction).toLowerCase().startsWith('s') ? 'short' : 'long', timeframe: str(r.timeframe),
    entry: num(r.entry), zoneLow: num(zone.low), zoneHigh: num(zone.high), sl: num(r.sl ?? r.stop_loss),
    tps: [r.tp1, r.tp2, r.tp3].map(num).filter((v): v is number => v != null), confidence: conf == null ? null : conf <= 1 ? conf * 100 : conf,
    rr: num(r.rr ?? r.risk_reward_tp1), rationale: str(r.rationale), warnings: Array.isArray(r.warnings) ? (r.warnings as unknown[]).map(String) : r.warnings ? [String(r.warnings)] : [], lot: num(r.lot),
  };
}

export default function AiSetupPage() {
  const L = useL();
  const t = useT();
  const sessions = useSession((s) => s.sessions);
  const close = usePage((s) => s.close);
  const chartSymbol = usePrefs((s) => s.symbol);
  const [backend, setBackend] = useState<Backend>(sessions.tradeyar && !(FOREX.includes(chartSymbol) && sessions.coineprofx) ? 'tradeyar' : 'coineprofx');
  const quota = useLoad(() => call<Record<string, unknown>>(backend, 'quota'), [backend]);
  const symbols = (Array.isArray(quota.data?.symbols) && (quota.data!.symbols as string[]).length ? quota.data!.symbols as string[] : backend === 'tradeyar' ? CRYPTO : FOREX);
  const tfs = (Array.isArray(quota.data?.timeframes) && (quota.data!.timeframes as string[]).length ? quota.data!.timeframes as string[] : TF[backend]);
  const [f, setF] = useState({ symbol: symbols.includes(chartSymbol) ? chartSymbol : '', timeframe: '', style: 'intraday', bias: 'auto', risk: 'balanced', minRr: '2', riskPct: '1', balance: '', lot: '' });
  const [busy, setBusy] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [setup, setSetup] = useState<Setup | null>(null);
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => setF({ ...f, [k]: e.target.value });
  const symbol = f.symbol || symbols[0] || '';
  const timeframe = f.timeframe || tfs[Math.min(3, tfs.length - 1)] || '';

  const generate = async () => {
    setErr(null); setSetup(null); setBusy(L('در صف…', 'Queued…'));
    try {
      const body: Record<string, unknown> = { symbol, timeframe, trade_style: f.style, risk_appetite: f.risk, direction_bias: f.bias };
      for (const [k, v] of [['min_rr', f.minRr], ['risk_percent', f.riskPct], ['balance', f.balance], ...(backend === 'coineprofx' ? [['lot', f.lot]] : [])] as [string, string][]) { const n = Number(v); if (v && n > 0) body[k] = n; }
      const job = await call<Record<string, unknown>>(backend, 'generate', { body, timeoutMs: 60_000 });
      const id = str(job.id ?? job.job_id);
      let cur = job;
      const started = Date.now();
      while (!['done', 'failed', 'expired'].includes(str(cur.status))) {
        setBusy(str(cur.status) === 'running' ? L('در حال تحلیل…', 'Analysing…') : L('در صف…', 'Queued…'));
        await new Promise((r) => setTimeout(r, 2500));
        if (Date.now() - started > 180_000) throw new HttpError(504, null, null);
        cur = await call<Record<string, unknown>>(backend, `result/${encodeURIComponent(id)}`);
      }
      if (str(cur.status) !== 'done') throw new HttpError(422, { detail: str(cur.error_message) || str(cur.status) }, null);
      setSetup(readSetup((cur.result ?? {}) as Record<string, unknown>));
      quota.reload();
    } catch (e) { setErr(errorText(e, t)); } finally { setBusy(null); }
  };

  const d = setup ? digitsOf(setup.symbol || symbol) : 2;
  const fp = (v: number | null) => (v == null ? '—' : formatPrice(v, d));
  return (
    <div className="page-inner">
      <div className="row">
        {sessions.tradeyar && sessions.coineprofx ? <Tabs value={backend} onChange={(b) => { setBackend(b); setF({ ...f, symbol: '', timeframe: '' }); }} tabs={[{ id: 'tradeyar', label: L('رمزارز', 'Crypto') }, { id: 'coineprofx', label: L('فارکس', 'Forex') }]} /> : null}
        <State loading={quota.loading} error={quota.error} onRetry={quota.reload}>
          <span className="muted fa">{L('سهمیه‌ی امروز', "Today's quota")}: <b className="num">{num(quota.data?.remaining) ?? '—'} / {num(quota.data?.limit ?? quota.data?.daily_limit) ?? '—'}</b></span>
        </State>
      </div>
      <div className="split">
        <form className="card form" onSubmit={(e) => { e.preventDefault(); void generate(); }}>
          <div className="row two">
            <Field label={L('نماد', 'Symbol')}><select value={symbol} onChange={set('symbol')}>{symbols.map((s) => <option key={s} value={s}>{s}</option>)}</select></Field>
            <Field label={L('تایم‌فریم', 'Timeframe')}><select value={timeframe} onChange={set('timeframe')}>{tfs.map((s) => <option key={s} value={s}>{s}</option>)}</select></Field>
          </div>
          <Field label={L('سبک معامله', 'Trading style')}><select value={f.style} onChange={set('style')}><option value="scalp">{L('اسکالپ', 'Scalp')}</option><option value="intraday">{L('روزانه', 'Intraday')}</option><option value="swing">{L('سوینگ', 'Swing')}</option></select></Field>
          <Field label={L('جهت', 'Direction')}><select value={f.bias} onChange={set('bias')}><option value="auto">{L('خودکار', 'Automatic')}</option><option value="long">{L('فقط خرید', 'Long')}</option><option value="short">{L('فقط فروش', 'Short')}</option></select></Field>
          <Field label={L('ریسک‌پذیری', 'Risk appetite')}><select value={f.risk} onChange={set('risk')}><option value="conservative">{L('محتاط', 'Conservative')}</option><option value="balanced">{L('متعادل', 'Balanced')}</option><option value="aggressive">{L('جسور', 'Aggressive')}</option></select></Field>
          <div className="row three">
            <Field label={L('حداقل R:R', 'Min R:R')}><input className="num" dir="ltr" inputMode="decimal" value={f.minRr} onChange={set('minRr')} /></Field>
            <Field label={L('ریسک (درصد)', 'Risk %')}><input className="num" dir="ltr" inputMode="decimal" value={f.riskPct} onChange={set('riskPct')} /></Field>
            <Field label={L('موجودی', 'Balance')}><input className="num" dir="ltr" inputMode="decimal" value={f.balance} onChange={set('balance')} /></Field>
          </div>
          {backend === 'coineprofx' ? <Field label={L('لات (اختیاری)', 'Lot (optional)')}><input className="num" dir="ltr" inputMode="decimal" value={f.lot} onChange={set('lot')} /></Field> : null}
          <button className="btn btn-primary" disabled={Boolean(busy) || !symbol}>{busy ?? L('ساخت ستاپ', 'Build the setup')}</button>
          {err ? <p className="down fa">{err}</p> : null}
        </form>
        <div className="grow">
          {setup ? (
            <div className="stack">
              <div className="row"><b className="sym">{setup.symbol || symbol}</b><span className={`sig-dir ${setup.direction === 'long' ? 'up' : 'down'}`}>{setup.direction === 'long' ? 'LONG' : 'SHORT'}</span><span className="num muted">{setup.timeframe || timeframe}</span>
                <span className="ws-spacer" />
                <button className="btn btn-primary sm" onClick={() => { const s = setup.symbol || symbol; usePrefs.getState().setSymbol(s); useOverlay.getState().show({ symbol: s, direction: setup.direction, entry: setup.entry, sl: setup.sl, targets: setup.tps, label: 'AI' }); close(); }}>{L('نمایش روی چارت', 'Show on the chart')}</button></div>
              <div className="card"><MiniChart symbol={setup.symbol || symbol} interval={IV[setup.timeframe || timeframe] ?? '60'} levels={[...(setup.entry != null ? [{ price: setup.entry, color: '#2962FF', title: 'E' }] : []), ...(setup.sl != null ? [{ price: setup.sl, color: '#F23645', title: 'SL' }] : []), ...setup.tps.map((p, i) => ({ price: p, color: '#089981', title: `TP${i + 1}` }))]} /></div>
              <div className="bt-stats wide">
                {([[L('ورود', 'Entry'), setup.zoneLow != null && setup.zoneHigh != null ? `${fp(setup.zoneLow)} – ${fp(setup.zoneHigh)}` : fp(setup.entry)], [L('حد ضرر', 'Stop'), fp(setup.sl), 'down'], ...setup.tps.map((p, i): [string, string, string] => [`TP${i + 1}`, fp(p), 'up']), ['R:R', setup.rr?.toFixed(2) ?? '—'], [L('اعتماد', 'Confidence'), setup.confidence != null ? `${Math.round(setup.confidence)}%` : '—'], ...(setup.lot != null ? [[L('لات', 'Lot'), String(setup.lot)] as [string, string]] : [])] as [string, string, string?][]).map(([k, v, tone]) => <div key={k} className="bt-stat"><span className="bt-k fa">{k}</span><span className={`bt-v num ${tone ?? ''}`}>{v}</span></div>)}
              </div>
              {setup.rationale ? <section className="card"><h3 className="fa">{L('دلیل', 'Reasoning')}</h3><p className="fa" dir="auto">{setup.rationale}</p></section> : null}
              {setup.warnings.length ? <section className="card warn">{setup.warnings.map((w, i) => <p key={i} className="fa" dir="auto">⚠ {w}</p>)}</section> : null}
              <p className="muted small fa">{L('خروجی هوش مصنوعی تضمین نتیجه نیست؛ پیش از هر معامله خودتان بررسی کنید.', 'AI output is not a guaranteed outcome; check it yourself before any trade.')}</p>
            </div>
          ) : <div className="empty fa">{L('ورودی‌ها را انتخاب کنید و ستاپ بسازید.', 'Choose the inputs and build a setup.')}</div>}
        </div>
      </div>
    </div>
  );
}
