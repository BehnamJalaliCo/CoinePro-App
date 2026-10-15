import { useEffect, useState } from 'react';
import { create } from 'zustand';
import { usePrefs, useT } from '../state/prefs';
import { useSession } from '../data/session';
import { askAssistant, readChart, visionBackend, type VisionResult } from '../data/ai';
import { chartImage } from '../chart/snapshot';
import { useOverlay } from '../state/overlay';
import { digitsOf, useMarket } from '../state/market';
import { formatPrice } from '../chart/format';
import { HttpError, uuid } from '../data/http';
import { ty, fx, num } from '../data/member';
import { SymbolLogo } from '../ui/SymbolLogo';
import { Icon } from '../ui/Icon';
import { faAgo, faNum } from '../i18n/time';
import { useL } from '../pages/kit';
import './panels-x.css';

// The reader's AI readings, kept in this browser (the old Pro-Chart's "my AI setups"): each with
// its live state worked out from the price against its stop and targets.
interface Reading { id: string; symbol: string; interval: string; at: number; r: VisionResult }
const KEY = 'pc.ai.history.v1';
const useHistory = create<{ list: Reading[]; add(r: Reading): void; remove(id: string): void; clear(): void }>((set, get) => {
  const read = (): Reading[] => { try { const v = JSON.parse(localStorage.getItem(KEY) ?? '[]') as unknown; return Array.isArray(v) ? (v as Reading[]) : []; } catch { return []; } };
  const save = () => { try { localStorage.setItem(KEY, JSON.stringify(get().list)); } catch { /* ignore */ } };
  return {
    list: read(),
    add: (r) => { set({ list: [r, ...get().list].slice(0, 40) }); save(); },
    remove: (id) => { set({ list: get().list.filter((x) => x.id !== id) }); save(); },
    clear: () => { set({ list: [] }); save(); },
  };
});

/** A reading's state now: waiting, a target reached (TP1–3), or stopped. */
export function readingState(r: VisionResult, last: number | null | undefined): { key: 'active' | 'tp' | 'sl' | 'none'; hits: number } {
  if (!r.direction || last == null) return { key: r.direction ? 'active' : 'none', hits: 0 };
  const long = r.direction === 'long';
  const hits = r.targets.filter((tp) => (long ? last >= tp : last <= tp)).length;
  if (r.sl != null && (long ? last <= r.sl : last >= r.sl)) return { key: 'sl', hits };
  return { key: hits ? 'tp' : 'active', hits };
}

function Confidence({ v }: { v: number }) {
  return (
    <span className="sig-conf">
      <span className="bar"><i className={v >= 70 ? 'hi' : v >= 50 ? 'mid' : 'lo'} style={{ width: `${Math.max(4, Math.min(100, v))}%` }} /></span>
      <span className="num">{Math.round(v)}%</span>
    </span>
  );
}

// «تحلیل هوشمند»: the open chart, exactly as drawn (with the reader's lines and indicators), read by
// the platform's chart-vision model — trend, structure, a setup with entry, stop and targets that
// can be put on the chart in one click (and taken off it) — the assistant to ask about it, the
// quota left, and the history of readings with their live state.
export function AiPanel({ onSignIn }: { onSignIn(): void }) {
  const t = useT();
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const fa = lang === 'fa';
  const symbol = usePrefs((s) => s.symbol);
  const interval = usePrefs((s) => s.interval);
  const setSymbol = usePrefs((s) => s.setSymbol);
  const sessions = useSession((s) => s.sessions);
  const show = useOverlay((s) => s.show);
  const clear = useOverlay((s) => s.clear);
  const setup = useOverlay((s) => s.setup);
  const tickers = useMarket((s) => s.tickers);
  const history = useHistory();
  const [busy, setBusy] = useState<string | null>(null);
  const [result, setResult] = useState<VisionResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [question, setQuestion] = useState('');
  const [chat, setChat] = useState<{ q: string; a: string }[]>([]);
  const [askLeft, setAskLeft] = useState<number | null>(null);
  const [quota, setQuota] = useState<{ remaining: number | null; limit: number | null } | null>(null);
  const canRead = visionBackend(symbol) != null;
  const canAsk = Boolean(sessions.coineprofx);
  const d = digitsOf(symbol);

  // The AI quota the platform reports (the setup builder's; the reading itself has no endpoint).
  useEffect(() => {
    let alive = true;
    setQuota(null);
    const b = visionBackend(symbol);
    if (!b) return;
    const req = b === 'tradeyar' ? ty<Record<string, unknown>>('api/mobile/v1/ai/quota') : fx<Record<string, unknown>>('user/ai-signal/quota');
    void req.then((r) => { if (alive) setQuota({ remaining: num(r.remaining), limit: num(r.limit) }); }).catch(() => undefined);
    return () => { alive = false; };
  }, [symbol, sessions.tradeyar, sessions.coineprofx]);

  const fail = (e: unknown) => {
    if (e instanceof HttpError) {
      if (e.readerMessage) return setError(e.readerMessage);
      if (e.status === 429) return setError(t('tooMany'));
      if (e.status === 403) return setError(t('aiNeedsPremium'));
    }
    setError(t('networkError'));
  };

  const analyse = async () => {
    setError(null); setResult(null); setBusy(t('aiReading'));
    try {
      const img = await chartImage();
      if (!img) throw new Error('no chart');
      const r = await readChart(symbol, img, () => setBusy(t('aiReading')));
      setResult(r);
      history.add({ id: uuid(), symbol, interval, at: Date.now(), r });
      setQuota((q) => (q && q.remaining != null ? { ...q, remaining: Math.max(0, q.remaining - 1) } : q));
    } catch (e) { fail(e); } finally { setBusy(null); }
  };

  const ask = async () => {
    const q = question.trim();
    if (!q) return;
    setQuestion(''); setBusy(t('aiThinking')); setError(null);
    try { const r = await askAssistant(`${symbol}: ${q}`); setChat((c) => [...c, { q, a: r.answer }]); setAskLeft(r.remaining); } catch (e) { fail(e); } finally { setBusy(null); }
  };

  const putOnChart = (r: VisionResult, sym = symbol) => {
    const entry = r.entryLow != null && r.entryHigh != null ? (r.entryLow + r.entryHigh) / 2 : r.entryLow ?? r.entryHigh;
    if (sym !== symbol) setSymbol(sym);
    show({ symbol: sym, direction: r.direction ?? 'long', entry, sl: r.sl, targets: r.targets, label: 'AI' });
  };
  const isOnChart = (r: VisionResult, sym: string) => Boolean(setup && setup.label === 'AI' && setup.symbol === sym && setup.sl === r.sl && setup.targets.join() === r.targets.join());
  const stateName = (k: ReturnType<typeof readingState>['key'], hits: number) => ({ active: L('فعال', 'Active'), tp: `TP${hits} ✓`, sl: L('حد ضرر خورد', 'Stopped'), none: L('بدون ستاپ', 'No setup') }[k]);

  return (
    <section className="panel-generic ai">
      <header className="panel-head">
        <span className="fa">{t('aiTitle')}</span>
        <span className="ws-spacer" />
        {quota && quota.remaining != null ? <span className="ai-quota fa" title={L('سهمیه‌ی باقی‌مانده‌ی تحلیل هوشمند', 'AI quota left')}>{L('سهمیه', 'Quota')} <b className="num">{fa ? faNum(quota.remaining) : quota.remaining}{quota.limit != null ? ` / ${fa ? faNum(quota.limit) : quota.limit}` : ''}</b></span> : null}
        {setup ? <button className="lt" title={L('پاک کردن از روی چارت', 'Remove from chart')} onClick={clear}><Icon name="ph-eye-slash" size="sm" /></button> : null}
      </header>
      <div className="panel-body ai-body" dir={fa ? 'rtl' : 'ltr'}>
        {!canRead ? (
          <div className="empty fa">{t('aiSignIn')} <button className="link" onClick={onSignIn}>{t('signIn')}</button></div>
        ) : (
          <button className="btn btn-primary ai-go" disabled={Boolean(busy)} onClick={() => void analyse()}>
            <Icon name="ph-brain" size="sm" /><span>{t('aiReadChart')}</span>
          </button>
        )}
        {busy ? <p className="hint ai-busy"><span className="spinner" />{busy}</p> : null}
        {error ? <p className="error">{error}</p> : null}
        {result ? (
          <div className="ai-result">
            <div className="ai-head">
              {result.direction ? <span className={`sig-dir ${result.direction === 'long' ? 'up' : 'down'}`}>{result.direction === 'long' ? L('خرید', 'LONG') : L('فروش', 'SHORT')}</span> : null}
              {result.assessment ? <span className="muted num">{result.assessment}</span> : null}
              <span className="ws-spacer" />
              {result.confidence != null ? <Confidence v={result.confidence} /> : null}
            </div>
            {result.trendBias ? <p><b>{t('aiTrend')}:</b> {result.trendBias}</p> : null}
            {result.structure ? <p><b>{t('aiStructure')}:</b> {result.structure}</p> : null}
            {result.setup ? <p><b>{t('aiSetup')}:</b> {result.setup}</p> : null}
            <div className="sig-levels num" dir="ltr">
              {result.entryLow != null ? <span>E {formatPrice(result.entryLow, d)}{result.entryHigh != null && result.entryHigh !== result.entryLow ? `–${formatPrice(result.entryHigh, d)}` : ''}</span> : null}
              {result.sl != null ? <span className="down">SL {formatPrice(result.sl, d)}</span> : null}
              {result.targets.map((x, i) => <span key={i} className="up">TP{i + 1} {formatPrice(x, d)}</span>)}
            </div>
            {result.reasoning ? <p className="ai-reason">{result.reasoning}</p> : null}
            {result.risk ? <p className="muted">{result.risk}</p> : null}
            {(result.sl != null || result.targets.length) ? (
              isOnChart(result, symbol)
                ? <button className="btn btn-quiet sm" onClick={clear}><Icon name="ph-eye-slash" size="sm" /><span className="fa">{L('برداشتن از چارت', 'Remove from chart')}</span></button>
                : <button className="btn btn-quiet sm" onClick={() => putOnChart(result)}><Icon name="tool_longshort" size="sm" /><span>{t('aiShowOnChart')}</span></button>
            ) : null}
          </div>
        ) : null}
        <div className="ai-chat">
          {chat.map((c, i) => (
            <div key={i} className="ai-msg"><p className="q">{c.q}</p><p className="a">{c.a}</p></div>
          ))}
          {canAsk ? (
            <form className="ai-ask" onSubmit={(e) => { e.preventDefault(); void ask(); }}>
              <input value={question} onChange={(e) => setQuestion(e.target.value)} placeholder={t('aiAsk')} disabled={Boolean(busy)} />
              <button className="ib" type="submit" disabled={Boolean(busy) || !question.trim()}><Icon name={fa ? 'ph-caret-left' : 'ph-caret-right'} size="sm" /></button>
            </form>
          ) : null}
          {askLeft != null ? <p className="muted small fa">{L(`${faNum(askLeft)} پرسش دیگر باقی است`, `${askLeft} questions left`)}</p> : null}
        </div>
        {history.list.length ? (
          <div className="ai-history">
            <div className="ai-history-head"><b className="fa">{L('تحلیل‌های من', 'My readings')}</b><span className="ws-spacer" /><button className="link-btn fa" onClick={() => { if (window.confirm(L('همه‌ی تحلیل‌ها پاک شوند؟', 'Clear every reading?'))) history.clear(); }}>{L('پاک کردن', 'Clear')}</button></div>
            {history.list.map((h) => {
              const st = readingState(h.r, tickers[h.symbol]?.last);
              const on = isOnChart(h.r, h.symbol);
              return (
                <div key={h.id} className={`ai-hrow${on ? ' on' : ''}`} dir="ltr">
                  <button className="ai-hmain" onClick={() => putOnChart(h.r, h.symbol)} title={L('نمایش روی چارت', 'Show on chart')}>
                    <SymbolLogo symbol={h.symbol} size={16} /><b className="sym">{h.symbol}</b>
                    {h.r.direction ? <span className={`sig-dir ${h.r.direction === 'long' ? 'up' : 'down'}`}>{h.r.direction === 'long' ? 'L' : 'S'}</span> : null}
                    <span className={`sig-state ${st.key === 'sl' ? 'sl' : st.key === 'tp' ? 'tp' : 'active'} fa`}>{stateName(st.key, st.hits)}</span>
                    {on ? <span className="sig-onchart fa">{L('روی چارت', 'On chart')}</span> : null}
                    <span className="ws-spacer" />
                    {h.r.confidence != null ? <span className="num muted">{h.r.confidence}%</span> : null}
                    <span className="muted small fa">{fa ? faAgo(h.at) : new Date(h.at).toISOString().slice(5, 16).replace('T', ' ')}</span>
                  </button>
                  <button className="lt" title={L('حذف', 'Delete')} onClick={() => { if (on) clear(); history.remove(h.id); }}><Icon name="ph-trash" size="sm" /></button>
                </div>
              );
            })}
          </div>
        ) : null}
      </div>
    </section>
  );
}
