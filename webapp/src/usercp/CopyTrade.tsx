import { useState } from 'react';
import { usePage } from '../state/page';
import { State, useL, useLoad } from '../pages/kit';
import { Card, Note, Pill, Section, Stat, hasPage, openPage, stamp, type Tone } from './ui';
import { BACKENDS, closeExecution, executions, flat, membership, signedOn, type Execution } from './sources';
import { vipStatus } from './Overview';
import { usePremium } from '../trade/entitlements';

const TONE: Record<string, Tone> = { open: 'good', filled: 'good', queued: 'info', submitted: 'info', closed: 'muted', failed: 'bad', rejected: 'bad', cancelled: 'muted' };

export default function CopyTrade() {
  const L = useL();
  const open = usePage((s) => s.open);
  const vip = useLoad(() => membership(), []);
  const premium = usePremium();
  const list = useLoad(() => Promise.all(BACKENDS.map(executions)).then(flat), []);
  const [busy, setBusy] = useState<string | null>(null);
  const items = list.data ?? [];
  const by = (s: string[]) => items.filter((x) => s.includes(x.status)).length;

  const closeOne = async (x: Execution) => {
    if (!window.confirm(L('درخواست بستن این اجرا فرستاده شود؟', 'Request closing this execution?'))) return;
    setBusy(x.id);
    try { await closeExecution(x.backend, x.id); } catch { /* the list shows the state the server keeps */ } finally { setBusy(null); list.reload(); }
  };

  return (
    <Section title={L('کپی‌ترید VIP', 'VIP copy trading')} sub={L('سیگنال‌هایی که برای اجرا روی حساب صرافی شما فرستاده شده‌اند، با وضعیت هر کدام.', 'Signals sent for execution on your exchange account, with the state of each.')}
      actions={<button className="btn btn-quiet sm" onClick={() => open('signals')}>{L('سیگنال‌ها', 'Signals')}</button>}>
      {!signedOn('tradeyar') ? <Note tone="info">{L('کپی‌ترید VIP با حساب تریدیار و اتصال صرافی LBank است.', 'VIP copy trading uses a TradeYar account and an LBank connection.')}</Note> : null}
      {premium.forexVip && !premium.vip ? <Note tone="info">{L('عضویت VIP فارکس شما فعال است و همه‌ی امکانات پرو چارت را باز می‌کند. کپی‌ترید رمزارز روی LBank مال کانال VIP تریدیار است و عضویت جدای خودش را دارد.', 'Your forex VIP is active and opens all of Pro Chart. Crypto copy trading on LBank belongs to the TradeYar VIP channel, a membership of its own.')}</Note> : null}
      <div className="ucp-stats">
        <Stat label={L('عضویت VIP', 'VIP membership')} value={vip.data ? vipStatus(vip.data.status, L) : '—'} tone={vip.data?.status === 'approved' ? 'gold' : undefined}
          foot={<span className="ucp-actions">
            <button className="ucp-link" onClick={() => openPage(vip.data?.status === 'approved' ? 'vip-status' : 'vip-join', 'membership')}>{vip.data?.status === 'approved' ? L('وضعیت VIP', 'VIP status') : L('پیوستن به VIP', 'Join VIP')}</button>
            {hasPage('performance') ? <button className="ucp-link" onClick={() => openPage('performance')}>{L('کارنامه', 'Performance')}</button> : null}
          </span>} />
        <Stat label={L('اجرای باز', 'Open')} value={list.data ? by(['open', 'filled']) : '—'} />
        <Stat label={L('در صف', 'Queued')} value={list.data ? by(['queued', 'submitted', 'pending']) : '—'} />
        <Stat label={L('ناموفق', 'Failed')} value={list.data ? by(['failed', 'rejected']) : '—'} tone={list.data && by(['failed', 'rejected']) ? 'bad' : undefined} />
      </div>
      <Card title={L('اجراها', 'Executions')} actions={<button className="btn btn-quiet sm" onClick={() => open('panel', 'connections')}>{L('اتصال صرافی', 'Exchange connection')}</button>}>
        <State loading={list.loading} error={list.error} onRetry={list.reload} empty={!items.length}>
          <div className="ucp-table-wrap"><table className="table ucp-table">
            <thead><tr><th>{L('سیگنال', 'Signal')}</th><th>{L('صرافی', 'Venue')}</th><th className="r">{L('مقدار', 'Qty')}</th><th>{L('وضعیت', 'Status')}</th><th className="r">{L('زمان', 'Time')}</th><th /></tr></thead>
            <tbody>{items.map((x) => (
              <tr key={`${x.backend}${x.id}`}>
                <td><button className="ucp-link num" onClick={() => x.signalId && open('signal', x.signalId)}>{[x.symbol, x.direction, x.timeframe].filter(Boolean).join(' ')}</button></td>
                <td>{x.venue || '—'}</td><td className="r num">{x.quantity || '—'}</td>
                <td title={x.error}><Pill tone={TONE[x.status] ?? 'muted'}>{x.status || '—'}</Pill></td><td className="r num">{stamp(x.at)}</td>
                <td className="r">{x.status === 'open' ? <button className="btn btn-quiet sm" disabled={busy === x.id} onClick={() => void closeOne(x)}>{L('بستن', 'Close')}</button> : null}</td>
              </tr>
            ))}</tbody></table></div>
        </State>
      </Card>
    </Section>
  );
}
