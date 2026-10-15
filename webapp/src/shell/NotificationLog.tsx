import { useEffect, useMemo, useState } from 'react';
import { usePrefs } from '../state/prefs';
import { useAlerts } from '../state/alerts';
import { useOverlay } from '../state/overlay';
import { usePage } from '../state/page';
import { useSession } from '../data/session';
import { activeSignals, intervalOfTimeframe, trackRecord, type Signal } from '../data/signals';
import { readReminded, type Reminder } from '../data/reminders';
import { SymbolLogo, CurrencyFlag } from '../ui/SymbolLogo';
import { Icon } from '../ui/Icon';
import { useNow } from '../ui/useFlash';
import { faAgo, faNum } from '../i18n/time';
import { dateTime, useL } from '../pages/kit';
import './panels-x.css';

// Everything that reached the reader, newest first (TradingView's notifications widget): fired
// alerts with their message, the platforms' signals as they appear, and calendar reminders that
// rang. Filters by kind; the settings page is one click away.
type Kind = 'alert' | 'signal' | 'calendar';
interface Item { id: string; kind: Kind; at: number; title: string; body: string; symbol?: string; currency?: string; signal?: Signal }
const SEEN_KEY = 'pc.notify.seen.v1';

export function NotificationLog() {
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const fa = lang === 'fa';
  const setSymbol = usePrefs((s) => s.setSymbol);
  const setInterval0 = usePrefs((s) => s.setInterval);
  const show = useOverlay((s) => s.show);
  const log = useAlerts((s) => s.log);
  const markSeen = useAlerts((s) => s.markSeen);
  const clearLog = useAlerts((s) => s.clearLog);
  const signed = useSession((s) => Boolean(s.sessions.tradeyar || s.sessions.coineprofx));
  const [signals, setSignals] = useState<Signal[]>([]);
  const [reminded, setReminded] = useState<Reminder[]>(readReminded);
  const [filter, setFilter] = useState<Kind | 'all'>('all');
  const [seenAt] = useState(() => { try { return Number(localStorage.getItem(SEEN_KEY)) || 0; } catch { return 0; } });
  const now = useNow(30_000);

  useEffect(() => { markSeen(); try { localStorage.setItem(SEEN_KEY, String(Date.now())); } catch { /* ignore */ } }, [markSeen, log.length]);
  useEffect(() => {
    let alive = true;
    const load = () => (signed ? activeSignals() : trackRecord(15)).then((l) => { if (alive) setSignals(l.slice(0, 30)); }).catch(() => undefined);
    void load();
    const id = setInterval(load, 120_000);
    return () => { alive = false; clearInterval(id); };
  }, [signed]);
  useEffect(() => { const on = () => setReminded(readReminded()); window.addEventListener('pc:reminder', on); return () => window.removeEventListener('pc:reminder', on); }, []);

  const items = useMemo<Item[]>(() => [
    ...log.map((e) => ({ id: `a${e.id}`, kind: 'alert' as const, at: e.at, title: e.name, body: e.message, symbol: e.symbol })),
    ...signals.map((s) => ({ id: `s${s.id}`, kind: 'signal' as const, at: s.closedAt ?? s.createdAt, title: `${s.symbol} · ${s.direction === 'long' ? L('خرید', 'Long') : L('فروش', 'Short')} ${s.timeframe}`, body: s.closedAt ? (s.isWin ? L('بسته شد با سود', 'Closed in profit') : s.isWin === false ? L('بسته شد با زیان', 'Closed at a loss') : L('بسته شد', 'Closed')) : L('سیگنال تازه', 'New signal'), symbol: s.symbol, signal: s })),
    ...reminded.map((r) => ({ id: `c${r.key}`, kind: 'calendar' as const, at: r.time, title: r.title, body: L('یادآوری تقویم اقتصادی', 'Economic calendar reminder'), currency: r.currency })),
  ].filter((x) => filter === 'all' || x.kind === filter).sort((a, b) => b.at - a.at), [log, signals, reminded, filter, L]);

  const openItem = (x: Item) => {
    if (x.signal) {
      const s = x.signal;
      setSymbol(s.symbol);
      const iv = intervalOfTimeframe(s.timeframe);
      if (iv) setInterval0(iv);
      show({ symbol: s.symbol, direction: s.direction, entry: s.entry, sl: s.sl, targets: s.targets, label: `${s.direction === 'long' ? 'Long' : 'Short'} ${s.timeframe}`, time: s.createdAt / 1000 });
    } else if (x.symbol) setSymbol(x.symbol);
  };
  const count = (k: Kind) => { const n = k === 'alert' ? log.length : k === 'signal' ? signals.length : reminded.length; return n ? ` ${fa ? faNum(n) : n}` : ''; };

  return (
    <section className="panel-generic notif-log">
      <header className="panel-head">
        <span className="fa">{L('اعلان‌ها', 'Notifications')}</span>
        <span className="ws-spacer" />
        <button className="lt" title={L('پاک کردن گزارش هشدارها', 'Clear the alert log')} disabled={!log.length} onClick={clearLog}><Icon name="ph-trash" size="sm" /></button>
        <button className="lt" title={L('تنظیمات اعلان', 'Notification settings')} onClick={() => usePage.getState().open('notifications')}><Icon name="ph-gear" size="sm" /></button>
      </header>
      <nav className="chips panel-chips">
        {(['all', 'alert', 'signal', 'calendar'] as const).map((k) => (
          <button key={k} className={`chip sm${filter === k ? ' on' : ''}`} onClick={() => setFilter(k)}>
            <span className="fa">{k === 'all' ? L('همه', 'All') : k === 'alert' ? L('هشدارها', 'Alerts') : k === 'signal' ? L('سیگنال‌ها', 'Signals') : L('تقویم', 'Calendar')}</span>
            {k !== 'all' ? <span className="num count">{count(k)}</span> : null}
          </button>
        ))}
      </nav>
      <div className="panel-body">
        {!items.length ? <div className="empty fa">{L('هنوز اعلانی نیست. هشدارهای رخ‌داده، سیگنال‌ها و یادآورهای تقویم این‌جا می‌آیند.', 'Nothing yet. Fired alerts, signals and calendar reminders appear here.')}</div> : null}
        {items.map((x) => (
          <button key={x.id} className={`notif-row ${x.kind}${x.at > seenAt ? ' unread' : ''}`} onClick={() => openItem(x)} title={dateTime(x.at)}>
            <span className="notif-icon">{x.symbol ? <SymbolLogo symbol={x.symbol} size={20} /> : x.currency ? <CurrencyFlag code={x.currency} size={18} /> : <Icon name="ph-bell" size="sm" />}<i className={`kind ${x.kind}`}><Icon name={x.kind === 'alert' ? 'ph-alarm' : x.kind === 'signal' ? 'ph-lightning' : 'ph-calendar-blank'} size="sm" /></i></span>
            <span className="notif-text">
              <b dir="auto" className={fa ? 'fa' : ''}>{x.title}</b>
              <span className="muted" dir="auto">{x.body}</span>
            </span>
            <span className={`muted small ${fa ? 'fa' : 'num'}`}>{fa ? faAgo(x.at, now) : dateTime(x.at).slice(5)}</span>
          </button>
        ))}
      </div>
    </section>
  );
}
