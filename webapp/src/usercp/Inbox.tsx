import { useState } from 'react';
import { usePage } from '../state/page';
import { member, num, ms, str } from '../data/member';
import { dismissMessage, getInbox, readAll, readMessage, type InboxItem } from '../data/panel';
import { State, Tabs, useL, useLoad } from '../pages/kit';
import { pageFromPath } from '../pages/registry';
import { usePanel } from './PanelPage';
import { Pill, Section, useAgo, type Tone } from './ui';

interface PlatformNote { title: string; body: string; ts: number | null; read: boolean; signal: string | null }

const KIND_TONE: Record<string, Tone> = { info: 'info', offer: 'gold', warning: 'warn', update: 'good', ticket: 'info' };

export default function Inbox() {
  const L = useL();
  const ago = useAgo();
  const open = usePage((s) => s.open);
  const { reload: reloadMe } = usePanel();
  const [tab, setTab] = useState<'panel' | 'platform'>('panel');
  const inbox = useLoad(() => getInbox(), []);
  const platform = useLoad(() => (tab === 'platform' ? member<{ items?: Record<string, unknown>[]; unread?: number }>('notifications?limit=50').then((r) => ({
    unread: num(r.unread) ?? 0,
    items: (r.items ?? []).map((x): PlatformNote => ({ title: str(x.title), body: str(x.body), ts: ms(x.ts), read: Boolean(x.read), signal: str((x.data as Record<string, unknown> | undefined)?.signal_id) || null })).filter((x) => x.title),
  })) : Promise.resolve(null)), [tab]);
  const [expanded, setExpanded] = useState<number | null>(null);

  const kindName: Record<string, string> = { info: L('اطلاعیه', 'Notice'), offer: L('پیشنهاد', 'Offer'), warning: L('هشدار', 'Warning'), update: L('به‌روزرسانی', 'Update'), ticket: L('پشتیبانی', 'Support') };

  const pick = async (m: InboxItem) => {
    setExpanded(expanded === m.id ? null : m.id);
    if (!m.read) { await readMessage(m.id).catch(() => null); inbox.reload(); reloadMe(); }
  };
  const follow = (m: InboxItem) => {
    if (m.ticket_id) { open('panel', 'support'); return; }
    if (m.link.startsWith('/next/')) { const p = pageFromPath(m.link.slice('/next/'.length)); if (p) open(p.id, p.param); return; }
    if (/^https:\/\//.test(m.link)) window.open(m.link, '_blank', 'noopener');
  };

  return (
    <Section title={L('اعلان‌ها', 'Notifications')} sub={L('پیام‌های تیم پرو چارت و پاسخ‌های پشتیبانی، و اعلان‌های هر پلتفرم.', 'Messages from the Pro Chart team and support replies, and each platform’s notifications.')}
      actions={tab === 'panel' && inbox.data?.unread ? <button className="btn btn-quiet sm" onClick={() => void readAll().then(() => { inbox.reload(); reloadMe(); })}>{L('همه خوانده شد', 'Mark all read')}</button> : null}>
      <Tabs value={tab} onChange={setTab} tabs={[{ id: 'panel', label: L('پرو چارت', 'Pro Chart') }, { id: 'platform', label: L('اعلان‌های پلتفرم', 'Platform notifications') }]} />
      {tab === 'panel' ? (
        <State loading={inbox.loading} error={inbox.error} onRetry={inbox.reload} empty={!inbox.data?.items.length}>
          <ul className="ucp-inbox">
            {(inbox.data?.items ?? []).map((m) => (
              <li key={m.id} className={m.read ? '' : 'unread'}>
                <button className="ucp-inbox-head" onClick={() => void pick(m)} aria-expanded={expanded === m.id}>
                  <Pill tone={KIND_TONE[m.kind] ?? 'muted'}>{kindName[m.kind] ?? m.kind}</Pill>
                  <b dir="auto">{m.title}</b>
                  <span className="ucp-muted">{ago(m.created_at * 1000)}</span>
                </button>
                {expanded === m.id ? (
                  <div className="ucp-inbox-body">
                    <p dir="auto">{m.body}</p>
                    <div className="ucp-actions">
                      {m.link || m.ticket_id ? <button className="btn btn-quiet sm" onClick={() => follow(m)}>{m.ticket_id ? L('دیدن تیکت', 'Open the ticket') : L('باز کردن', 'Open')}</button> : null}
                      <button className="btn btn-quiet sm" onClick={() => void dismissMessage(m.id).then(() => { inbox.reload(); reloadMe(); })}>{L('حذف از فهرست', 'Remove')}</button>
                    </div>
                  </div>
                ) : <p className="ucp-muted ucp-clip" dir="auto">{m.body}</p>}
              </li>
            ))}
          </ul>
        </State>
      ) : (
        <State loading={platform.loading} error={platform.error} onRetry={platform.reload} empty={!platform.data?.items.length}>
          <ul className="ucp-inbox">
            {(platform.data?.items ?? []).map((n, i) => (
              <li key={i} className={n.read ? '' : 'unread'}>
                <button className="ucp-inbox-head" onClick={() => n.signal && open('signal', n.signal)}>
                  <b dir="auto">{n.title}</b><span className="ucp-muted">{n.ts ? ago(n.ts) : ''}</span>
                </button>
                <p className="ucp-muted" dir="auto">{n.body}</p>
              </li>
            ))}
          </ul>
        </State>
      )}
    </Section>
  );
}
