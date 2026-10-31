import { useEffect, useRef, useState } from 'react';
import { usePrefs, useT } from '../state/prefs';
import { useSession } from '../data/session';
import { askAssistant } from '../data/ai';
import { Markdown } from './Markdown';
import { errorText, useL } from './kit';
import { usePage } from '../state/page';
import type { ContextItem } from './aiEvidence';

// The assistant (feature/ai-assistant, CoinePro-FX user/ai/chat): a conversation about the market
// and the open chart, kept for this visit, with the day's remaining questions.
const KEY = 'pc.assistant.v1';
interface Msg { q: string; a: string; at: number; ctx?: ContextItem[] }
const read = (): Msg[] => { try { const v = JSON.parse(sessionStorage.getItem(KEY) ?? '[]') as unknown; return Array.isArray(v) ? (v as Msg[]) : []; } catch { return []; } };

export default function AssistantPage() {
  const L = useL();
  const t = useT();
  const symbol = usePrefs((s) => s.symbol);
  const fx = useSession((s) => Boolean(s.sessions.coineprofx));
  const [chat, setChat] = useState<Msg[]>(read);
  const [q, setQ] = useState('');
  const [busy, setBusy] = useState(false);
  const [left, setLeft] = useState<number | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [withChart, setWithChart] = useState(true);
  const end = useRef<HTMLDivElement>(null);
  useEffect(() => { try { sessionStorage.setItem(KEY, JSON.stringify(chat.slice(-40))); } catch { /* ignore */ } end.current?.scrollIntoView({ block: 'end' }); }, [chat]);

  if (!fx) return <div className="empty fa">{L('دستیار با حساب کوین‌پرو اف‌ایکس کار می‌کند؛ با همان ایمیل وارد شوید.', 'The assistant works with a CoinePro-FX account; sign in with that email.')}</div>;
  const ask = async (text = q) => {
    const question = text.trim();
    if (!question || busy) return;
    setQ(''); setBusy(true); setErr(null);
    try { const r = await askAssistant(withChart ? `${symbol}: ${question}` : question); setChat((c) => [...c, { q: question, a: r.answer, at: Date.now(), ctx: r.context }]); setLeft(r.remaining); }
    catch (e) { setErr(errorText(e, t)); } finally { setBusy(false); }
  };
  const starters = [L('روند امروز این نماد چیست؟', 'What is the trend of this symbol today?'), L('مهم‌ترین سطوح حمایت و مقاومت کجاست؟', 'Where are the key support and resistance levels?'), L('ریسک خبرهای این هفته برای این نماد چیست؟', "What is this week's news risk for this symbol?"), L('مدیریت سرمایه را برای حساب کوچک توضیح بده.', 'Explain money management for a small account.')];

  return (
    <div className="page-inner narrow assistant">
      <div className="chat">
        {!chat.length ? <div className="chips flat">{starters.map((s) => <button key={s} className="chip" onClick={() => void ask(s)}><span className="fa">{s}</span></button>)}</div> : null}
        {chat.map((m, i) => (
          <div key={i} className="ai-msg"><p className="q fa" dir="auto">{m.q}</p><div className="a"><Markdown source={m.a} /><Sources items={m.ctx ?? []} /></div></div>
        ))}
        {busy ? <p className="muted fa">{t('aiThinking')}…</p> : null}
        <div ref={end} />
      </div>
      {err ? <p className="down fa">{err}</p> : null}
      <form className="ai-ask big" onSubmit={(e) => { e.preventDefault(); void ask(); }}>
        <textarea value={q} onChange={(e) => setQ(e.target.value)} rows={2} maxLength={4000} placeholder={t('aiAsk')} dir="auto" onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); void ask(); } }} />
        <button className="btn btn-primary sm" disabled={busy || !q.trim()}>{L('بفرست', 'Send')}</button>
      </form>
      <div className="row muted small">
        <label className="check"><input type="checkbox" checked={withChart} onChange={(e) => setWithChart(e.target.checked)} /> <span className="fa">{L(`درباره‌ی ${symbol}`, `About ${symbol}`)}</span></label>
        {left != null ? <span className="fa">{L('پرسش باقی‌مانده‌ی امروز', 'Questions left today')}: <b className="num">{left}</b></span> : null}
        {chat.length ? <button className="link-btn" onClick={() => setChat([])}>{L('گفت‌وگوی تازه', 'New conversation')}</button> : null}
      </div>
    </div>
  );
}

const KIND: Record<ContextItem['kind'], [string, string]> = {
  active_signal: ['سیگنال فعال', 'Active signal'], market: ['بازار', 'Market'], news: ['خبر', 'News'],
  calendar: ['تقویم', 'Calendar'], risk: ['ریسک', 'Risk'], tool: ['ابزار', 'Tool'],
};
const FRESH: Record<ContextItem['freshness'], [string, string, string]> = {
  fresh: ['تازه', 'Fresh', 'up'], stale: ['قدیمی', 'Stale', 'warn-text'], unknown: ['تازگی نامشخص', 'Freshness unknown', 'muted'],
};

/**
 * «منابعِ استفاده‌شده» under each reply (the phone's ContextCard): what kind of source, how fresh — an
 * answer built on a stale quote is a different answer, so freshness is coloured — and, for an active
 * signal, a way to it. A reply with none says so plainly rather than leaving a blank: it is a different
 * kind of answer, and the reader has to be able to tell.
 */
function Sources({ items }: { items: ContextItem[] }) {
  const L = useL();
  if (!items.length) return <p className="small fa ai-no-context">{L('هیچ منبع ساختاریافته‌ای به این پاسخ پیوست نشده است.', 'No structured source was attached to this reply.')}</p>;
  return (
    <div className="ai-sources">
      <span className="muted small fa">{L('منابعِ استفاده‌شده', 'Sources used')}</span>
      {items.map((it, i) => {
        const [ff, fe, tone] = FRESH[it.freshness];
        const provenance = [it.source, it.asOf].filter(Boolean).join(' · ');
        return (
          <div key={i} className="ai-source" data-kind={it.kind}>
            <div className="row"><span className="small fa">{L(...KIND[it.kind])}</span><span className={`small fa ${tone}`}>{L(ff, fe)}</span></div>
            <b dir="auto">{it.title}</b>
            {it.summary ? <span className="small" dir="auto">{it.summary}</span> : null}
            {provenance ? <span className="muted small" dir="auto">{provenance}</span> : null}
            {it.kind === 'active_signal' && it.signalId ? <button className="btn btn-quiet sm" onClick={() => usePage.getState().open('signal', it.signalId)}>{L('باز کردن سیگنال', 'Open signal')}</button> : null}
          </div>
        );
      })}
    </div>
  );
}
