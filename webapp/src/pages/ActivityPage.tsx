import { useMemo, useState } from 'react';
import { usePrefs } from '../state/prefs';
import { usePage } from '../state/page';
import { useSession } from '../data/session';
import { member, ty, fx, num, ms, str } from '../data/member';
import { faAgo, faNum } from '../i18n/time';
import { State, Tabs, dateTime, money, useL, useLoad } from './kit';
import { canRequestClose, filterHistory, performanceResult, readSignalRecord, summarizeSignals, type MarketKind, type RateMetric, type ResultFilter, type SignalRecord } from './records';
import './records.css';

// Activity (feature/activity): the notification inbox the platforms keep (read all), the signal
// executions sent to the exchange with their state, a close request and the cancel of a queued one
// (OLD ExecutionScreen), and the closed-signal performance built from the record as the app builds
// it (OLD ActivityScreen, SignalPerformance): win rate, target hits, stop rate and the average
// planned risk to reward, each over its own evidence, filtered by market, instrument and result.
const HISTORY_LIMIT = 100;
const STATUS: Record<string, [string, string]> = {
  queued: ['در صف', 'Queued'], submitted: ['ارسال‌شده', 'Submitted'], open: ['باز', 'Open'], close_requested: ['درخواست بستن', 'Close requested'],
  closed: ['بسته', 'Closed'], failed: ['ناموفق', 'Failed'], cancelled: ['لغو شد', 'Cancelled'],
};

async function closedRecords(tradeyar: boolean, forex: boolean): Promise<{ items: SignalRecord[]; complete: boolean }> {
  const jobs: Promise<{ items: SignalRecord[]; complete: boolean }>[] = [];
  if (tradeyar) jobs.push(ty<{ items?: Record<string, unknown>[] }>(`api/mobile/v1/signals?status=closed&limit=${HISTORY_LIMIT}`).then((r) => ({ items: (r.items ?? []).map((x) => readSignalRecord(x, 'crypto')), complete: (r.items ?? []).length < HISTORY_LIMIT })));
  if (forex) jobs.push(fx<{ items?: Record<string, unknown>[] }>(`public/signals/recent?limit=${HISTORY_LIMIT}`).then((r) => { const raw = r.items ?? []; return { items: raw.filter((x) => str(x.status).toLowerCase() !== 'active').map((x) => readSignalRecord(x, 'forex')), complete: raw.length < HISTORY_LIMIT }; }));
  const all = await Promise.all(jobs);
  return { items: all.flatMap((x) => x.items).filter((x) => x.symbol), complete: all.every((x) => x.complete) };
}
interface Note { kind: string; title: string; body: string; ts: number | null; read: boolean; data: Record<string, string> }

export default function ActivityPage() {
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const open = usePage((s) => s.open);
  const ty0 = useSession((s) => Boolean(s.sessions.tradeyar));
  const fx0 = useSession((s) => Boolean(s.sessions.coineprofx));
  const [market, setMarket] = useState<MarketKind | null>(null);
  const [instrument, setInstrument] = useState('');
  const [result, setResult] = useState<ResultFilter>('all');
  const [tab, setTab] = useState<'inbox' | 'executions' | 'performance'>('inbox');
  const inbox = useLoad(() => member<{ items?: Record<string, unknown>[]; unread?: number }>('notifications?limit=100').then((r) => ({
    unread: num(r.unread) ?? 0,
    items: (r.items ?? []).map((x): Note => ({ kind: str(x.kind), title: str(x.title), body: str(x.body), ts: ms(x.ts), read: Boolean(x.read), data: (x.data as Record<string, string>) ?? {} })).filter((x) => x.title),
  })), []);
  const execs = useLoad(() => (tab === 'executions' && ty0 ? ty<{ items?: Record<string, unknown>[] }>('api/mobile/v1/executions?limit=50').then((r) => r.items ?? []) : Promise.resolve([])), [tab, ty0]);
  const closed = useLoad(() => (tab === 'performance' && (ty0 || fx0) ? closedRecords(ty0, fx0) : Promise.resolve({ items: [] as SignalRecord[], complete: true })), [tab, ty0, fx0]);
  const records = closed.data?.items ?? [];
  const markets = useMemo(() => [...new Set(records.map((x) => x.market))], [records]);
  const shown = useMemo(() => filterHistory(records, { market, symbol: instrument, result }), [records, market, instrument, result]);
  const summary = useMemo(() => summarizeSignals(shown), [shown]);
  const clearFilters = () => { setMarket(null); setInstrument(''); setResult('all'); };
  const sample = (d: number) => (d === 0 ? L('بدون شواهد', 'no evidence') : L(`${faNum(d)} نمونه`, `n=${d}`));
  const rateText = (r: RateMetric) => (r.percent == null ? '—' : `${r.percent.toFixed(1)}%`);
  const resultLabel: Record<ResultFilter, string> = { all: L('همه‌ی نتایج', 'All results'), win: L('برد', 'Win'), loss: L('زیان', 'Loss'), breakeven: L('سربه‌سر', 'Breakeven'), unknown: L('نتیجه‌ی ناموجود', 'Result missing') };

  const readAll = async () => { await member('notifications/read', { body: {} }).catch(() => null); inbox.reload(); };
  const openNote = (n: Note) => {
    const sid = n.data.signal_id ?? n.data.signalId;
    if (sid) open('signal', sid);
  };

  return (
    <div className="page-inner narrow">
      <Tabs value={tab} onChange={setTab} tabs={[
        { id: 'inbox', label: `${L('اعلان‌ها', 'Inbox')}${inbox.data?.unread ? ` (${inbox.data.unread})` : ''}` },
        ...(ty0 ? [{ id: 'executions' as const, label: L('اجراهای سیگنال', 'Signal executions') }] : []),
        ...(ty0 || fx0 ? [{ id: 'performance' as const, label: L('کارنامه‌ی سیگنال‌ها', 'Signal performance') }] : []),
      ]} />
      {tab === 'inbox' ? (
        <>
          {inbox.data?.unread ? <button className="btn btn-quiet sm" onClick={() => void readAll()}>{L('همه خوانده شد', 'Mark all read')}</button> : null}
          <State loading={inbox.loading} error={inbox.error} onRetry={inbox.reload} empty={!inbox.data?.items.length}>
            <div className="list">
              {(inbox.data?.items ?? []).map((n, i) => (
                <button key={i} className={`note-row${n.read ? '' : ' unread'}`} onClick={() => openNote(n)}>
                  <b className="fa" dir="auto">{n.title}</b><span className="fa muted" dir="auto">{n.body}</span>
                  <span className="muted small">{n.ts ? (lang === 'fa' ? faAgo(n.ts) : dateTime(n.ts)) : ''}</span>
                </button>
              ))}
            </div>
          </State>
        </>
      ) : null}
      {tab === 'executions' ? (
        <State loading={execs.loading} error={execs.error} onRetry={execs.reload} empty={!execs.data?.length}>
          <table className="table"><thead><tr><th>{L('سیگنال', 'Signal')}</th><th>{L('صرافی', 'Venue')}</th><th>{L('وضعیت', 'Status')}</th><th className="r">{L('مقدار', 'Qty')}</th><th className="r">{L('زمان', 'Time')}</th><th /></tr></thead>
            <tbody>{(execs.data ?? []).map((x) => {
              const sig = (x.signal ?? {}) as Record<string, unknown>;
              const st = str(x.status).toLowerCase();
              const queued = st === 'queued';
              const action = queued ? L('لغو درخواستِ در صف', 'Cancel queued request') : L('درخواست بستن', 'Request close');
              const label = STATUS[st] ? L(STATUS[st]![0], STATUS[st]![1]) : st || L('نامشخص', 'Unknown');
              return (
                <tr key={str(x.id)}>
                  <td><button className="link-btn sym" onClick={() => open('signal', str(x.signal_id))}>{str(sig.symbol)} {str(sig.direction)} {str(sig.timeframe)}</button></td>
                  <td>{str(x.venue)} {str(x.product)}</td><td title={str(x.error_message)} className={`fa ${st === 'failed' ? 'down' : st === 'open' ? 'up' : ''}`} data-status={st}>{label}</td>
                  <td className="r num">{str(x.quantity)}</td><td className="r num">{dateTime(ms(x.created_at))}</td>
                  <td className="r">{canRequestClose(st, str(x.venue)) ? <button className="btn btn-quiet sm" data-act={queued ? 'cancel' : 'close'} onClick={async () => { if (window.confirm(queued ? `${action}؟` : L('درخواست بستن این اجرا فرستاده شود؟', 'Request closing this execution?'))) { await ty(`api/mobile/v1/executions/${encodeURIComponent(str(x.id))}/close`, { body: {} }).catch(() => null); execs.reload(); } }}><span className="fa">{action}</span></button> : null}</td>
                </tr>
              );
            })}</tbody></table>
        </State>
      ) : null}
      {tab === 'performance' ? (
        <State loading={closed.loading} error={closed.error} onRetry={closed.reload} empty={!records.length} emptyText={L('هنوز سیگنال بسته‌ای ثبت نشده. نرخ‌ها ناموجود می‌مانند، نه صفر.', 'No closed signals have been recorded yet. Rates stay missing, not zero.')}>
          <section className="card" data-part="performance">
            <h3 className="fa">{L('شواهد عملکرد', 'Performance evidence')}</h3>
            <p className="muted small fa">{closed.data?.complete ? L('تاریخچه‌ی بارگذاری‌شده کامل است.', 'The loaded history is complete.') : L('آمار فقط از شواهد بارگذاری‌شده است؛ پوشش کامل نیست.', 'Metrics use loaded evidence only; coverage is incomplete.')}</p>
            <div className="bt-stats wide">
              {([
                [L('سیگنال‌ها', 'Signals'), shown.length ? String(summary.total) : '—', shown.length ? L('رکورد فیلترشده', 'filtered records') : L('رکوردی نیست', 'no records'), 'signals'],
                [L('نرخ برد', 'Win rate'), rateText(summary.winRate), sample(summary.winRate.denominator), 'win'],
                [L('زیان‌ها', 'Losses'), shown.length ? String(summary.losses) : '—', shown.length ? L('سود و زیان صریح', 'explicit P&L') : L('رکوردی نیست', 'no records'), 'losses'],
                [L('خوردن هدف', 'Target hit'), rateText(summary.tpHitRate), sample(summary.tpHitRate.denominator), 'tp'],
                [L('نرخ حد ضرر', 'Stop rate'), rateText(summary.stopLossRate), sample(summary.stopLossRate.denominator), 'sl'],
                [L('میانگین ریسک به ریوارد', 'Average risk to reward'), summary.averageRiskReward == null ? '—' : `1:${summary.averageRiskReward.toFixed(2)}`, sample(summary.riskRewardDenominator), 'rr'],
              ] as const).map(([k, v, d, id]) => <div key={id} className="bt-stat" data-metric={id}><span className="bt-k fa">{k}</span><span className="bt-v num" dir="ltr">{v}</span><small className="muted fa">{d}</small></div>)}
            </div>
            <div className="muted small fa">{L('پوشش شواهد', 'Evidence coverage')}</div>
            <div className="row" data-part="evidence">
              <span><b className="num up">{summary.wins}</b> <span className="fa small">{L('برد', 'Wins')}</span></span>
              <span><b className="num down">{summary.losses}</b> <span className="fa small">{L('زیان‌ها', 'Losses')}</span></span>
              <span><b className="num">{summary.breakeven}</b> <span className="fa small">{L('سربه‌سر', 'Breakeven')}</span></span>
              <span><b className="num rec-warn">{summary.unknownPnl}</b> <span className="fa small">{L('سود و زیانِ ناموجود', 'P&L missing')}</span></span>
            </div>
            <p className="muted small fa">{L('صفر فقط وقتی نشان داده می‌شود که شواهد صریح صفر بدهند. مخرجِ ناموجود با — نمایش داده می‌شود.', 'Zero is shown only when explicit evidence produces zero. A missing denominator is shown as —.')}</p>
          </section>
          <section className="card rec-filters" data-part="activity-filters">
            <div className="row"><span className="grow"><b className="fa">{L('فیلترها', 'Filters')}</b> <small className="muted fa">{L('بازار، نماد و نتیجه‌ی صریح', 'Market, instrument and explicit result')}</small></span><button className="link" onClick={clearFilters}>{L('بازنشانی', 'Reset')}</button></div>
            {markets.length > 1 ? <div className="chips flat" role="group">{(['crypto', 'forex'] as const).map((m) => <button key={m} type="button" data-market={m} className={`chip${market === m ? ' on' : ''}`} onClick={() => setMarket(market === m ? null : m)}><span className="fa">{m === 'crypto' ? L('کریپتو', 'Crypto') : L('فارکس', 'Forex')}</span></button>)}</div> : null}
            <input className="scr-search num" dir="ltr" value={instrument} placeholder="BTCUSDT" aria-label={L('نماد', 'Instrument')} onChange={(e) => setInstrument(e.target.value.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 18))} />
            <div className="chips flat" role="group">{(['all', 'win', 'loss', 'breakeven', 'unknown'] as const).map((r) => <button key={r} type="button" data-result={r} className={`chip${result === r ? ' on' : ''}`} onClick={() => setResult(r)}><span className="fa">{resultLabel[r]}</span></button>)}</div>
          </section>
          {!shown.length ? (
            <div className="empty fa"><b>{L('رکوردی با این فیلترها نیست', 'No records match these filters')}</b> {L('فیلترها را پاک یا عوض کنید. آمار عملکردِ موجود برای نمای فیلترشده‌ی خالی دوباره استفاده نمی‌شود.', 'Clear or change the filters. Existing performance totals are not reused for an empty filtered view.')} <button className="link" onClick={clearFilters}>{L('پاک‌کردن فیلترها', 'Clear filters')}</button></div>
          ) : (
            <table className="table" data-part="signal-history"><thead><tr><th>{L('سیگنال', 'Signal')}</th><th>{L('نتیجه', 'Result')}</th><th className="r">{L('سود و زیان', 'P&L')}</th><th className="r">{L('ورود', 'Entry')}</th><th className="r">{L('ریسک به ریوارد', 'R:R')}</th><th>{L('بسته شدن', 'Close')}</th></tr></thead>
              <tbody>{shown.map((x) => { const r = performanceResult(x); return (
                <tr key={`${x.market}-${x.id}`} className="click" onClick={() => open('signal', x.id)}>
                  <td><span className="sym" dir="ltr">{x.symbol} {x.direction} {x.timeframe}</span></td>
                  <td className={`fa ${r === 'win' ? 'up' : r === 'loss' ? 'down' : ''}`}>{resultLabel[r]}</td>
                  <td className={`r num ${r === 'win' ? 'up' : r === 'loss' ? 'down' : ''}`} dir="ltr">{x.pnlUsd == null ? '—' : `${x.pnlUsd > 0 ? '+' : ''}$${money(x.pnlUsd)}`}</td>
                  <td className="r num">{x.entry ?? '—'}</td>
                  <td className="r num" dir="ltr">{x.rr != null && x.rr > 0 ? `1:${x.rr.toFixed(2)}` : '—'}</td>
                  <td className="muted small" dir="auto">{x.closeReason ?? ''}</td>
                </tr>
              ); })}</tbody></table>
          )}
        </State>
      ) : null}
    </div>
  );
}
