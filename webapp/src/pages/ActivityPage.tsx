import { useMemo, useState } from 'react';
import { usePrefs } from '../state/prefs';
import { usePage } from '../state/page';
import { useSession } from '../data/session';
import { member, ty, num, ms, str } from '../data/member';
import { faAgo } from '../i18n/time';
import { State, Tabs, dateTime, money, useL, useLoad } from './kit';

// Activity (feature/activity): the notification inbox the platforms keep (read all), the signal
// executions sent to the exchange with their state and a close request, and the closed-signal
// performance built from the record, as the app builds it.
interface Note { kind: string; title: string; body: string; ts: number | null; read: boolean; data: Record<string, string> }

export default function ActivityPage() {
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const open = usePage((s) => s.open);
  const ty0 = useSession((s) => Boolean(s.sessions.tradeyar));
  const [tab, setTab] = useState<'inbox' | 'executions' | 'performance'>('inbox');
  const inbox = useLoad(() => member<{ items?: Record<string, unknown>[]; unread?: number }>('notifications?limit=100').then((r) => ({
    unread: num(r.unread) ?? 0,
    items: (r.items ?? []).map((x): Note => ({ kind: str(x.kind), title: str(x.title), body: str(x.body), ts: ms(x.ts), read: Boolean(x.read), data: (x.data as Record<string, string>) ?? {} })).filter((x) => x.title),
  })), []);
  const execs = useLoad(() => (tab === 'executions' && ty0 ? ty<{ items?: Record<string, unknown>[] }>('api/mobile/v1/executions?limit=50').then((r) => r.items ?? []) : Promise.resolve([])), [tab, ty0]);
  const closed = useLoad(() => (tab === 'performance' && ty0 ? ty<{ items?: Record<string, unknown>[] }>('api/mobile/v1/signals?status=closed&limit=100').then((r) => r.items ?? []) : Promise.resolve([])), [tab, ty0]);

  const perf = useMemo(() => {
    const list = (closed.data ?? []).map((x) => ({ symbol: str(x.symbol), win: x.hit_target != null ? Boolean(x.hit_target) : (num(x.live_pnl_percent ?? x.pct_gain) ?? 0) > 0, pct: num(x.live_pnl_percent ?? x.pct_gain) ?? 0, tf: str(x.timeframe) }));
    const wins = list.filter((x) => x.win).length;
    const by = new Map<string, { n: number; w: number; pct: number }>();
    for (const x of list) { const r = by.get(x.symbol) ?? { n: 0, w: 0, pct: 0 }; r.n++; if (x.win) r.w++; r.pct += x.pct; by.set(x.symbol, r); }
    return { n: list.length, win: list.length ? (wins / list.length) * 100 : 0, sum: list.reduce((n, x) => n + x.pct, 0), by: [...by.entries()].sort((a, b) => b[1].n - a[1].n) };
  }, [closed.data]);

  const readAll = async () => { await member('notifications/read', { body: {} }).catch(() => null); inbox.reload(); };
  const openNote = (n: Note) => {
    const sid = n.data.signal_id ?? n.data.signalId;
    if (sid) open('signal', sid);
  };

  return (
    <div className="page-inner narrow">
      <Tabs value={tab} onChange={setTab} tabs={[
        { id: 'inbox', label: `${L('اعلان‌ها', 'Inbox')}${inbox.data?.unread ? ` (${inbox.data.unread})` : ''}` },
        ...(ty0 ? [{ id: 'executions' as const, label: L('اجراهای سیگنال', 'Signal executions') }, { id: 'performance' as const, label: L('کارنامه‌ی سیگنال‌ها', 'Signal performance') }] : []),
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
              const st = str(x.status);
              return (
                <tr key={str(x.id)}>
                  <td><button className="link-btn sym" onClick={() => open('signal', str(x.signal_id))}>{str(sig.symbol)} {str(sig.direction)} {str(sig.timeframe)}</button></td>
                  <td>{str(x.venue)} {str(x.product)}</td><td title={str(x.error_message)} className={st === 'failed' ? 'down' : st === 'open' ? 'up' : ''}>{st}</td>
                  <td className="r num">{str(x.quantity)}</td><td className="r num">{dateTime(ms(x.created_at))}</td>
                  <td className="r">{st === 'open' ? <button className="btn btn-quiet sm" onClick={async () => { if (window.confirm(L('درخواست بستن این اجرا فرستاده شود؟', 'Request closing this execution?'))) { await ty(`api/mobile/v1/executions/${encodeURIComponent(str(x.id))}/close`, { body: {} }).catch(() => null); execs.reload(); } }}>{L('بستن', 'Close')}</button> : null}</td>
                </tr>
              );
            })}</tbody></table>
        </State>
      ) : null}
      {tab === 'performance' ? (
        <State loading={closed.loading} error={closed.error} onRetry={closed.reload} empty={!perf.n}>
          <div className="pulse">
            <div className="card"><span className="muted fa">{L('سیگنال بسته', 'Closed signals')}</span><b className="num">{perf.n}</b></div>
            <div className="card"><span className="muted fa">{L('نرخ موفقیت', 'Hit rate')}</span><b className="num">{perf.win.toFixed(1)}%</b></div>
            <div className="card"><span className="muted fa">{L('جمع درصد', 'Sum of %')}</span><b className={`num ${perf.sum >= 0 ? 'up' : 'down'}`}>{money(perf.sum)}%</b></div>
          </div>
          <table className="table"><tbody>{perf.by.map(([s, r]) => <tr key={s}><td className="sym">{s}</td><td className="r num">{r.n}</td><td className="r num">{((r.w / r.n) * 100).toFixed(0)}%</td><td className={`r num ${r.pct >= 0 ? 'up' : 'down'}`}>{r.pct.toFixed(2)}%</td></tr>)}</tbody></table>
        </State>
      ) : null}
    </div>
  );
}
