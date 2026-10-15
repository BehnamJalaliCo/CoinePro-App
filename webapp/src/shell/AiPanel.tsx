import { useState } from 'react';
import { usePrefs, useT } from '../state/prefs';
import { useSession } from '../data/session';
import { askAssistant, readChart, visionBackend, type VisionResult } from '../data/ai';
import { chartImage } from '../chart/snapshot';
import { useOverlay } from '../state/overlay';
import { digitsOf } from '../state/market';
import { formatPrice } from '../chart/format';
import { HttpError } from '../data/http';
import { Icon } from '../ui/Icon';

// «تحلیل هوشمند»: the open chart, exactly as drawn (with the reader's lines and indicators), read by
// the platform's chart-vision model — trend, structure, a setup with entry, stop and targets that
// can be put on the chart in one click — and the assistant to ask about it.
export function AiPanel({ onSignIn }: { onSignIn(): void }) {
  const t = useT();
  const symbol = usePrefs((s) => s.symbol);
  const sessions = useSession((s) => s.sessions);
  const show = useOverlay((s) => s.show);
  const [busy, setBusy] = useState<string | null>(null);
  const [result, setResult] = useState<VisionResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [question, setQuestion] = useState('');
  const [chat, setChat] = useState<{ q: string; a: string }[]>([]);
  const canRead = visionBackend(symbol) != null;
  const canAsk = Boolean(sessions.coineprofx);
  const d = digitsOf(symbol);

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
      setResult(await readChart(symbol, img, () => setBusy(t('aiReading'))));
    } catch (e) { fail(e); } finally { setBusy(null); }
  };

  const ask = async () => {
    const q = question.trim();
    if (!q) return;
    setQuestion(''); setBusy(t('aiThinking')); setError(null);
    try { const r = await askAssistant(`${symbol}: ${q}`); setChat((c) => [...c, { q, a: r.answer }]); } catch (e) { fail(e); } finally { setBusy(null); }
  };

  const putOnChart = (r: VisionResult) => {
    const entry = r.entryLow != null && r.entryHigh != null ? (r.entryLow + r.entryHigh) / 2 : r.entryLow ?? r.entryHigh;
    show({ symbol, direction: r.direction ?? 'long', entry, sl: r.sl, targets: r.targets, label: 'AI' });
  };

  return (
    <section className="panel-generic ai">
      <header className="panel-head"><span className="fa">{t('aiTitle')}</span></header>
      <div className="panel-body ai-body" dir="rtl">
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
              {result.direction ? <span className={`sig-dir ${result.direction === 'long' ? 'up' : 'down'}`}>{result.direction === 'long' ? 'LONG' : 'SHORT'}</span> : null}
              {result.confidence != null ? <span className="num muted">{result.confidence}%</span> : null}
              {result.assessment ? <span className="muted num">{result.assessment}</span> : null}
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
              <button className="btn btn-quiet sm" onClick={() => putOnChart(result)}><Icon name="tool_longshort" size="sm" /><span>{t('aiShowOnChart')}</span></button>
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
              <button className="ib" type="submit" disabled={Boolean(busy) || !question.trim()}><Icon name="ph-caret-left" size="sm" /></button>
            </form>
          ) : null}
        </div>
      </div>
    </section>
  );
}
