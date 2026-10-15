import { useEffect, useMemo, useState } from 'react';
import { usePrefs, useT } from '../state/prefs';
import { usePage } from '../state/page';
import { ty, num, str, ms } from '../data/member';
import { digitsOf } from '../state/market';
import { formatPrice } from '../chart/format';
import { faAgo, faNum } from '../i18n/time';
import { Icon } from '../ui/Icon';
import { SymbolLogo } from '../ui/SymbolLogo';
import { Field, State, Tabs, dateTime, errorText, money, useL, useLoad } from '../pages/kit';
import { Pill } from './ui';

// «کپی‌ترید»: the user site's dashboard, positions and settings for TradeYar's copy trading — the
// LBank equity, whether a signal would open on this account now and why not, the open copy
// positions, what the exchange itself holds, the orders, the closed history, and every setting the
// bot's settings screen has (bot and auto-entry, exit mode, margin, leverage, concurrency, stop,
// API keys with the exchange's own check, connection test, change log). Crypto only: forex is
// signals only.
type Tab = 'dashboard' | 'positions' | 'settings';

export default function CopyTradePage() {
  const L = useL();
  const [tab, setTab] = useState<Tab>('dashboard');
  return (
    <div className="page-inner vip-page">
      <Tabs value={tab} onChange={setTab} tabs={[{ id: 'dashboard', label: L('داشبورد', 'Dashboard') }, { id: 'positions', label: L('پوزیشن‌ها', 'Positions') }, { id: 'settings', label: L('تنظیمات', 'Settings') }]} />
      {tab === 'dashboard' ? <Dashboard go={setTab} /> : tab === 'positions' ? <Positions /> : <Settings />}
    </div>
  );
}

interface Status { willTrade: boolean; linked: boolean; apiReady: boolean; botEnabled: boolean; autoEntry: boolean; open: number; blockers: { code: string; message: string; action: string }[]; lastError: string | null; egress: string }
const readStatus = (r: Record<string, unknown>): Status => ({
  willTrade: Boolean(r.willTrade), linked: r.linked !== false, apiReady: Boolean(r.apiReady), botEnabled: Boolean(r.botEnabled), autoEntry: Boolean(r.autoEntryEnabled),
  open: num(r.openPositions) ?? 0, egress: str(r.serverEgressIp),
  blockers: (Array.isArray(r.blockers) ? (r.blockers as Record<string, unknown>[]) : []).map((b) => ({ code: str(b.code), message: str(b.message), action: str(b.action) })),
  lastError: r.lastError && typeof r.lastError === 'object' ? str((r.lastError as Record<string, unknown>).message) : null,
});

function Dashboard({ go }: { go(t: Tab): void }) {
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const open = usePage((s) => s.open);
  const balance = useLoad(() => ty<Record<string, unknown>>('api/user/v1/account/balance'), []);
  const status = useLoad(() => ty<Record<string, unknown>>('api/user/v1/settings/status').then(readStatus), []);
  const positions = useLoad(() => ty<{ positions?: Record<string, unknown>[] }>('api/user/v1/positions/active?limit=10&offset=0').then((r) => r.positions ?? []), []);
  const signals = useLoad(() => ty<{ signals?: Record<string, unknown>[] }>('api/user/v1/signals/recent?limit=8').then((r) => r.signals ?? []), []);
  const b = balance.data;
  const err = b ? str(b.error) : '';
  const keysMissing = err === 'API keys not configured';
  const stale = err === 'cached:last-good';
  const blocker = status.data && !status.data.willTrade ? status.data.blockers[0] : undefined;
  return (
    <div className="stack">
      <section className="card stack vip-equity">
        <State loading={balance.loading} error={balance.error} onRetry={balance.reload}>
          {keysMissing ? (
            <><p className="fa">{L('کلید API صرافی وصل نشده است، بنابراین موجودی خوانده نمی‌شود.', 'No exchange API key is connected, so the balance cannot be read.')}</p><div className="row"><button className="btn btn-primary sm" onClick={() => go('settings')}><span className="fa">{L('اتصال کلید API', 'Connect the API key')}</span></button></div></>
          ) : err && !stale ? <p className="fa down">{L('موجودی حساب از صرافی دریافت نشد.', 'The balance could not be read from the exchange.')}</p> : (
            <>
              <span className="muted fa">{L('موجودی کل حساب · LBank', 'Total equity · LBank')}{stale ? ` · ${L('آخرین مقدار ثبت‌شده', 'last known value')}` : ''}</span>
              <b className="big num">{money(num(b?.totalBalance))} <small>USDT</small></b>
              <div className="vip-tiles">
                <div className="vip-tile"><span className="muted fa">{L('آزاد', 'Available')}</span><b className="num">{money(num(b?.availableBalance))}</b></div>
                <div className="vip-tile"><span className="muted fa">{L('سود/زیان باز', 'Open P&L')}</span><b className={`num ${(num(b?.unrealizedPnl) ?? 0) >= 0 ? 'up' : 'down'}`}>{money(num(b?.unrealizedPnl))}</b></div>
                <div className="vip-tile"><span className="muted fa">{L('مارجین درگیر', 'Margin used')}</span><b className="num">{money(num(b?.marginUsed))}</b></div>
              </div>
              <span className="muted small">{ms(b?.timestamp) ? (lang === 'fa' ? faAgo(ms(b?.timestamp)!) : dateTime(ms(b?.timestamp))) : ''}</span>
            </>
          )}
        </State>
      </section>
      {blocker ? (
        <section className="card warn stack" data-blocker={blocker.code}>
          <div className="row-between"><h3 className="fa">{L('کپی‌ترید شما الان اجرا نمی‌شود', 'Copy trading would not run now')}</h3><Pill tone="warn">{L('نیاز به اقدام', 'Action needed')}</Pill></div>
          <p className="fa">{blocker.message}</p>
          <div className="row"><button className="btn btn-primary sm" onClick={() => go('settings')}><span className="fa">{L('رفع در تنظیمات', 'Fix in settings')}</span></button></div>
        </section>
      ) : null}
      <div className="vip-grid">
        <section className="card stack">
          <div className="row-between"><h3 className="fa">{L('پوزیشن‌های باز', 'Open positions')}</h3><button className="link-btn" onClick={() => go('positions')}><span className="fa">{L('همه', 'All')}</span></button></div>
          <State loading={positions.loading} error={positions.error} onRetry={positions.reload} empty={!positions.data?.length}>
            <PositionTable rows={positions.data ?? []} />
          </State>
        </section>
        <section className="card stack">
          <div className="row-between"><h3 className="fa">{L('آخرین سیگنال‌ها', 'Latest signals')}</h3><button className="link-btn" onClick={() => open('signals')}><span className="fa">{L('سیگنال‌ها', 'Signals')}</span></button></div>
          <State loading={signals.loading} error={signals.error} onRetry={signals.reload} empty={!signals.data?.length}>
            <ul className="vip-sigs">
              {(signals.data ?? []).map((s) => {
                const sym = str(s.symbol).toUpperCase().replace('/', '').replace('_', '');
                const side = str(s.side).toUpperCase();
                return (
                  <li key={str(s.positionId ?? s.positionCode)}>
                    <span className="row"><SymbolLogo symbol={sym} size={16} /><b className="sym">{sym}</b></span>
                    <span className={side === 'LONG' ? 'up' : 'down'}>{side}</span>
                    <span className="num">{num(s.entryPrice) ? formatPrice(num(s.entryPrice)!, digitsOf(sym)) : '—'}</span>
                    <span className="muted small">{ms(s.createdAt) ? (lang === 'fa' ? faAgo(ms(s.createdAt)!) : dateTime(ms(s.createdAt))) : ''}</span>
                  </li>
                );
              })}
            </ul>
          </State>
        </section>
      </div>
    </div>
  );
}

function PositionTable({ rows, history = false }: { rows: Record<string, unknown>[]; history?: boolean }) {
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  return (
    <table className="table">
      <thead><tr><th>{L('نماد', 'Symbol')}</th><th>{L('جهت', 'Side')}</th><th className="r">{L('ورود', 'Entry')}</th><th className="r">SL</th><th className="r">TP</th><th className="r">{history ? L('نتیجه', 'Outcome') : L('وضعیت', 'State')}</th><th className="r">{L('زمان', 'Time')}</th></tr></thead>
      <tbody>{rows.map((p, i) => {
        const sym = (str(p.symbolNorm) || str(p.symbol)).toUpperCase();
        const d = digitsOf(sym);
        const f = (v: unknown) => (num(v) ? formatPrice(num(v)!, d) : '—');
        const outcome = p.outcome as { label?: string; tone?: string } | undefined;
        const at = ms(history ? p.closedAt : (p.openedAt ?? p.lastSeenAt));
        return (
          <tr key={str(p.id) || i}>
            <td><span className="row"><SymbolLogo symbol={sym} size={16} /><b className="sym">{sym}</b></span></td>
            <td className={str(p.direction) === 'SHORT' ? 'down' : 'up'}>{str(p.direction)}</td>
            <td className="r num">{f(p.entryPrice)}</td><td className="r num down">{f(p.slPrice)}</td><td className="r num up">{f(p.tpPrice)}</td>
            <td className={`r ${outcome?.tone === 'up' ? 'up' : outcome?.tone === 'down' ? 'down' : ''}`}><span className="fa">{history ? (outcome?.label ?? str(p.closedReason)) : str(p.status)}</span></td>
            <td className="r num">{at ? (lang === 'fa' ? faAgo(at) : dateTime(at)) : '—'}</td>
          </tr>
        );
      })}</tbody>
    </table>
  );
}

function Positions() {
  const L = useL();
  const [view, setView] = useState<'active' | 'exchange' | 'orders' | 'history'>('active');
  const [page, setPage] = useState(0);
  const per = 25;
  const data = useLoad(async () => {
    if (view === 'active') return { rows: (await ty<{ positions?: Record<string, unknown>[] }>('api/user/v1/positions/active?limit=50&offset=0')).positions ?? [], total: 0, error: null as string | null };
    if (view === 'history') { const r = await ty<{ positions?: Record<string, unknown>[]; total?: number }>(`api/user/v1/positions/history?limit=${per}&offset=${page * per}`); return { rows: r.positions ?? [], total: num(r.total) ?? 0, error: null }; }
    if (view === 'exchange') { const r = await ty<{ positions?: Record<string, unknown>[]; error?: { message?: string } | null }>('api/user/v1/positions/exchange'); return { rows: r.positions ?? [], total: 0, error: r.error?.message ?? null }; }
    const r = await ty<{ orders?: Record<string, unknown>[]; error?: { message?: string } | null }>('api/user/v1/positions/orders?limit=15');
    return { rows: r.orders ?? [], total: 0, error: r.error?.message ?? null };
  }, [view, page]);
  const d = data.data;
  return (
    <div className="stack">
      <Tabs value={view} onChange={(v) => { setView(v); setPage(0); }} tabs={[{ id: 'active', label: L('باز', 'Open') }, { id: 'exchange', label: L('روی صرافی', 'On the exchange') }, { id: 'orders', label: L('سفارش‌ها', 'Orders') }, { id: 'history', label: L('تاریخچه', 'History') }]} />
      <State loading={data.loading} error={data.error} onRetry={data.reload} empty={!d?.rows.length && !d?.error}>
        {d?.error ? <p className="fa down">{d.error}</p> : null}
        {view === 'active' || view === 'history' ? <PositionTable rows={d?.rows ?? []} history={view === 'history'} /> : view === 'exchange' ? (
          <table className="table">
            <thead><tr><th>{L('نماد', 'Symbol')}</th><th>{L('جهت', 'Side')}</th><th className="r">{L('حجم', 'Size')}</th><th className="r">{L('ورود', 'Entry')}</th><th className="r">{L('مارک', 'Mark')}</th><th className="r">PnL</th><th className="r">{L('اهرم', 'Lev.')}</th><th className="r">{L('لیکوئید', 'Liq.')}</th></tr></thead>
            <tbody>{(d?.rows ?? []).map((p, i) => <tr key={i}><td><b className="sym">{str(p.symbol)}</b></td><td className={str(p.direction) === 'SHORT' ? 'down' : 'up'}>{str(p.direction)}</td><td className="r num">{str(p.volume)}</td><td className="r num">{str(p.entryPrice)}</td><td className="r num">{str(p.markPrice)}</td><td className={`r num ${(num(p.unrealizedPnl) ?? 0) >= 0 ? 'up' : 'down'}`}>{money(num(p.unrealizedPnl))}</td><td className="r num">{num(p.leverage) ? `${num(p.leverage)}x` : '—'}</td><td className="r num">{str(p.liquidationPrice) || '—'}</td></tr>)}</tbody>
          </table>
        ) : (
          <table className="table">
            <thead><tr><th>{L('نماد', 'Symbol')}</th><th>{L('سمت', 'Side')}</th><th>{L('عمل', 'Action')}</th><th className="r">{L('قیمت', 'Price')}</th><th className="r">{L('حجم', 'Size')}</th><th className="r">{L('وضعیت', 'Status')}</th><th className="r">{L('زمان', 'Time')}</th></tr></thead>
            <tbody>{(d?.rows ?? []).map((o, i) => <tr key={i}><td><b className="sym">{str(o.symbol)}</b></td><td>{str(o.side)}</td><td>{str(o.action)}</td><td className="r num">{str(o.price) || '—'}</td><td className="r num">{str(o.volume) || '—'}</td><td className="r">{str(o.status)}</td><td className="r num">{dateTime(ms(o.at))}</td></tr>)}</tbody>
          </table>
        )}
        {view === 'history' && (d?.total ?? 0) > per ? (
          <div className="row">
            <button className="btn btn-quiet sm" disabled={page === 0} onClick={() => setPage(page - 1)}><span className="fa">{L('قبلی', 'Previous')}</span></button>
            <span className="num muted">{page + 1} / {Math.ceil((d?.total ?? 0) / per)}</span>
            <button className="btn btn-quiet sm" disabled={(page + 1) * per >= (d?.total ?? 0)} onClick={() => setPage(page + 1)}><span className="fa">{L('بعدی', 'Next')}</span></button>
          </div>
        ) : null}
      </State>
    </div>
  );
}

interface Draft { enabled: boolean; autoEntryEnabled: boolean; leverageValue: number; amountValue: number; exitMode: 'follow' | 'ladder'; maxPositions: number; slMode: 'EXACT' | 'OFFSET'; slOffsetPct: number | null }
const LEVERAGES = [1, 2, 3, 4, 5, 6, 7, 8, 10, 12, 15, 20, 25, 50, 75, 100, 125];

function toDraft(s: Record<string, unknown>): Draft {
  const off = num(s.slOffsetPct);
  return {
    enabled: Boolean(s.enabled), autoEntryEnabled: s.autoEntryEnabled !== false, leverageValue: num(s.leverageValue) ?? 5, amountValue: num(s.amountValue) ?? 10,
    exitMode: s.exitMode === 'ladder' || s.tpFollowBot === false ? 'ladder' : 'follow', maxPositions: num(s.maxPositions) || 20,
    slMode: s.slMode === 'OFFSET' && off ? 'OFFSET' : 'EXACT', slOffsetPct: off && off > 0 ? off : null,
  };
}

function Settings() {
  const L = useL();
  const t = useT();
  const lang = usePrefs((s) => s.lang);
  const settings = useLoad(() => ty<Record<string, unknown>>('api/user/v1/settings'), []);
  const status = useLoad(() => ty<Record<string, unknown>>('api/user/v1/settings/status').then(readStatus), []);
  const audit = useLoad(() => ty<{ entries?: Record<string, unknown>[] }>('api/user/v1/settings/audit?limit=20').then((r) => r.entries ?? []), []);
  const [draft, setDraft] = useState<Draft | null>(null);
  const [base, setBase] = useState<Draft | null>(null);
  const [key, setKey] = useState('');
  const [secret, setSecret] = useState('');
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);
  const [test, setTest] = useState<Record<string, unknown> | null>(null);
  useEffect(() => { if (settings.data) { const d = toDraft(settings.data); setDraft(d); setBase(d); } }, [settings.data]);
  const s = settings.data;
  const cap = num(s?.leverageCap) ?? 15;
  const capChoices = (Array.isArray(s?.maxPositionsChoices) ? (s!.maxPositionsChoices as unknown[]).map(num).filter((x): x is number => x != null) : [1, 2, 3, 5, 10, 20]);
  const offsetMax = num(s?.slOffsetMaxPct);
  const dirty = useMemo(() => (draft && base ? (Object.keys(draft) as (keyof Draft)[]).filter((k) => draft[k] !== base[k]) : []), [draft, base]);
  const put = async (body: Record<string, unknown>, after?: () => void) => {
    setBusy(true); setMsg(null);
    try {
      const r = await ty<{ savedFields?: string[]; keyCheck?: { ok?: boolean; reason?: string }; notes?: string[] }>('api/user/v1/settings', { method: 'PUT', body });
      const saved = new Set(r.savedFields ?? []);
      const missed = Object.keys(body).filter((k) => !saved.has(k) && k !== 'apiKey' && k !== 'apiSecret');
      if (r.keyCheck && r.keyCheck.ok === false) setMsg({ ok: false, text: L('کلیدها ذخیره شد اما صرافی آن‌ها را نپذیرفت.', 'Keys saved, but the exchange refused them.') + (r.keyCheck.reason ? ` (${r.keyCheck.reason})` : '') });
      else if (missed.length) setMsg({ ok: false, text: `${L('ذخیره نشد', 'Not saved')}: ${missed.join(', ')}` });
      else setMsg({ ok: true, text: [L('ذخیره شد؛ از سیگنال بعدی اعمال می‌شود.', 'Saved; applies from the next signal.'), ...(r.notes ?? [])].join(' ') });
      after?.();
      settings.reload(); status.reload(); audit.reload();
    } catch (e) { setMsg({ ok: false, text: errorText(e, t) }); } finally { setBusy(false); }
  };
  const runTest = async () => {
    setBusy(true); setTest(null);
    try { setTest(await ty<Record<string, unknown>>('api/user/v1/settings/test-connection', { method: 'POST', body: {} })); status.reload(); }
    catch (e) { setTest({ ok: false, message: errorText(e, t) }); } finally { setBusy(false); }
  };
  const set = <K extends keyof Draft>(k: K, v: Draft[K]) => setDraft((d) => (d ? { ...d, [k]: v } : d));
  const st = status.data;
  return (
    <State loading={settings.loading} error={settings.error} onRetry={settings.reload}>
      {draft ? (
        <div className="vip-settings">
          <section className="card stack">
            <div className="row-between"><h3 className="fa">{L('وضعیت کپی‌ترید', 'Copy-trade status')}</h3>{st ? <Pill tone={st.willTrade ? 'ok' : 'warn'}>{st.willTrade ? L('آماده‌ی اجرا', 'Ready') : L('اجرا نمی‌شود', 'Will not run')}</Pill> : null}</div>
            {st?.blockers.length ? <ul className="vip-todo">{st.blockers.map((b) => <li key={b.code} className="fa">{b.message}</li>)}</ul> : null}
            {st && !st.linked ? <p className="muted fa">{L('این حساب وب به حساب تلگرامی که کپی‌ترید رویش است وصل نیست؛ کپی‌ترید را از ربات فعال کنید.', 'This web account is not the Telegram account copy trading runs on; enable it from the bot.')}</p> : null}
            {st && (!st.botEnabled || !st.autoEntry) ? <div className="row"><button className="btn btn-primary sm" disabled={busy} onClick={() => void put({ enabled: true, autoEntryEnabled: true }, () => setDraft((d) => (d ? { ...d, enabled: true, autoEntryEnabled: true } : d)))}><span className="fa">{L('فعال‌سازی سریع', 'Quick enable')}</span></button></div> : null}
            <label className="check row-between"><span className="fa">{L('ربات کپی‌ترید روشن باشد', 'Copy-trade bot on')}</span><input type="checkbox" role="switch" checked={draft.enabled} onChange={(e) => set('enabled', e.target.checked)} /></label>
            <label className="check row-between"><span className="fa">{L('ورود خودکار به سیگنال‌های تازه', 'Enter new signals automatically')}</span><input type="checkbox" role="switch" checked={draft.autoEntryEnabled} onChange={(e) => set('autoEntryEnabled', e.target.checked)} /></label>
          </section>
          <section className="card stack">
            <h3 className="fa">{L('حالت خروج', 'Exit mode')}</h3>
            <div className="vip-radios" role="radiogroup">
              {([['follow', L('همراه ربات: اهداف، حد ضرر و بستن کانال', 'Follow the bot: the channel’s targets, stop and close')], ['ladder', L('پله‌ای: پس از هدف دوم حد ضرر به نقطه‌ی ورود و یک پله عقب‌تر', 'Ladder: break-even after TP2, then one rung behind')]] as const).map(([id, text]) => (
                <label key={id} className={`vip-radio${draft.exitMode === id ? ' on' : ''}`}><input type="radio" name="exit" checked={draft.exitMode === id} onChange={() => set('exitMode', id)} /><span className="fa">{text}</span></label>
              ))}
            </div>
          </section>
          <section className="card stack form">
            <h3 className="fa">{L('اندازه‌ی پوزیشن و اهرم', 'Position size and leverage')}</h3>
            <Field label={L('مارجین هر معامله (USDT)', 'Margin per trade (USDT)')} hint={num(s?.amountMin) ? L(`حداقل ${num(s?.amountMin)} USDT`, `Minimum ${num(s?.amountMin)} USDT`) : undefined}><input className="num" dir="ltr" inputMode="decimal" value={draft.amountValue} onChange={(e) => set('amountValue', Number(e.target.value.replace(/[^\d.]/g, '')) || 0)} /></Field>
            <Field label={L('اهرم', 'Leverage')} hint={L(`سقف این حساب ${cap}x`, `This account’s cap: ${cap}x`)}><select value={draft.leverageValue} onChange={(e) => set('leverageValue', Number(e.target.value))}>{LEVERAGES.filter((x) => x <= Math.max(cap, draft.leverageValue)).map((x) => <option key={x} value={x}>{x}x</option>)}</select></Field>
            <Field label={L('سقف پوزیشن هم‌زمان', 'Concurrent positions')}><select value={draft.maxPositions} onChange={(e) => set('maxPositions', Number(e.target.value))}>{capChoices.map((x) => <option key={x} value={x}>{x === 20 ? L('همه‌ی سیگنال‌ها', 'Every signal') : lang === 'fa' ? faNum(x) : x}</option>)}</select></Field>
          </section>
          <section className="card stack form">
            <h3 className="fa">{L('حد ضرر اولیه', 'Initial stop')}</h3>
            <div className="vip-radios" role="radiogroup">
              <label className={`vip-radio${draft.slMode === 'EXACT' ? ' on' : ''}`}><input type="radio" name="sl" checked={draft.slMode === 'EXACT'} onChange={() => set('slMode', 'EXACT')} /><span className="fa">{L('دقیقاً حد ضرر سیگنال', 'Exactly the signal’s stop')}</span></label>
              <label className={`vip-radio${draft.slMode === 'OFFSET' ? ' on' : ''}`}><input type="radio" name="sl" checked={draft.slMode === 'OFFSET'} onChange={() => set('slMode', 'OFFSET')} /><span className="fa">{L('درصد ثابت از نقطه‌ی ورود', 'A fixed percent from the entry')}</span></label>
            </div>
            {draft.slMode === 'OFFSET' ? <Field label={L('فاصله‌ی حد ضرر (٪)', 'Stop distance (%)')} hint={offsetMax ? L(`حداکثر ${offsetMax}% با این اهرم`, `At most ${offsetMax}% at this leverage`) : undefined}><input className="num" dir="ltr" inputMode="decimal" value={draft.slOffsetPct ?? ''} onChange={(e) => set('slOffsetPct', Number(e.target.value.replace(/[^\d.]/g, '')) || null)} /></Field> : null}
          </section>
          <div className="vip-save row">
            <button className="btn btn-primary sm" disabled={busy || !dirty.length} onClick={() => void put(Object.fromEntries(dirty.map((k) => [k, draft[k]])))}><span className="fa">{L('ذخیره‌ی تنظیمات', 'Save settings')}</span></button>
            {dirty.length ? <span className="muted fa">{L('تغییرات ذخیره‌نشده', 'Unsaved changes')}</span> : null}
            {msg ? <span className={`fa ${msg.ok ? 'up' : 'down'}`} role="status">{msg.text}</span> : null}
          </div>
          <section className="card stack form" id="api">
            <div className="row-between"><h3 className="fa">{L('کلیدهای API صرافی', 'Exchange API keys')}</h3><Pill tone={s?.apiKeySet ? 'ok' : 'mute'}>{s?.apiKeySet ? L('وصل', 'Connected') : L('وصل نیست', 'Not connected')}</Pill></div>
            {s?.credentialsNeedReentry ? <p className="down fa">{L('کلید ذخیره‌شده قابل استفاده نیست؛ دوباره وارد کنید.', 'The stored key cannot be used; enter it again.')}</p> : null}
            <p className="muted fa">{L('کلید را بدون دسترسی برداشت بسازید. آی‌پی سرور برای لیست سفید:', 'Create the key without withdrawal rights. Server IP to whitelist:')} {st?.egress ? <code className="num">{st.egress}</code> : '—'}</p>
            <Field label="API Key"><input dir="ltr" autoComplete="off" spellCheck={false} value={key} onChange={(e) => setKey(e.target.value)} /></Field>
            <Field label="API Secret"><input dir="ltr" type="password" autoComplete="new-password" spellCheck={false} value={secret} onChange={(e) => setSecret(e.target.value)} /></Field>
            <div className="row">
              <button className="btn btn-primary sm" disabled={busy || key.trim().length < 8 || secret.trim().length < 8} onClick={() => void put({ apiKey: key.trim(), apiSecret: secret.trim() }, () => { setKey(''); setSecret(''); })}><Icon name="ph-lock-key-open" size="sm" /><span className="fa">{L('ذخیره و بررسی با صرافی', 'Save and check with the exchange')}</span></button>
              <button className="btn btn-quiet sm" disabled={busy} onClick={() => void runTest()}><span className="fa">{L('تست اتصال', 'Test the connection')}</span></button>
            </div>
            {test ? <p className={`fa ${test.ok && !test.code ? 'up' : 'down'}`} role="status">{str(test.message)}{num(test.available) != null ? <> · <span className="num">{money(num(test.available))} USDT</span></> : null}</p> : null}
          </section>
          <section className="card stack">
            <h3 className="fa">{L('تاریخچه‌ی تغییر تنظیمات', 'Settings change log')}</h3>
            <State loading={audit.loading} error={audit.error} empty={!audit.data?.length}>
              <table className="table"><tbody>{(audit.data ?? []).map((a, i) => <tr key={i}><td>{str(a.field)}</td><td className="num">{str(a.oldValue) || '—'} → {str(a.newValue) || '—'}</td><td>{str(a.source)}</td><td className="r num">{dateTime(ms(a.changedAt))}</td></tr>)}</tbody></table>
            </State>
          </section>
        </div>
      ) : null}
    </State>
  );
}
