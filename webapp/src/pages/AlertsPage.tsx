import { useState } from 'react';
import { useAlerts } from '../state/alerts';
import { usePrefs, useT } from '../state/prefs';
import { useMarket } from '../state/market';
import { useSession } from '../data/session';
import { backendOf, FX_SYMBOLS } from '../data/symbols';
import { ty, fx, num, ms, str } from '../data/member';
import { SymbolSearch } from '../shell/SymbolSearch';
import { SymbolLogo } from '../ui/SymbolLogo';
import { Field, State, Tabs, dateTime, errorText, parseNum, useL, useLoad } from './kit';
import type { Backend } from '../data/symbols';

// The alert centre (feature/alerts): this browser's alerts beside the ones held by the platforms,
// which reach the phone and Telegram when this tab is closed. A new alert can be a level, a
// crossing, a channel, a move over bars or RSI against a level (the server's spec, CoinePro-FX),
// once or every time, with an expiry and the delivery channels.
type Kind = 'price' | 'channel' | 'move' | 'rsi';
type Cond = 'above' | 'below' | 'cross_up' | 'cross_down' | 'cross';
interface ServerAlert { id: string; symbol: string; condition: string; value: number | null; trigger: string; active: boolean; createdAt: number | null; lastAt: number | null; expiresAt: number | null; spec: Record<string, string> | null; channels: string[]; backend: Backend }

const call = (b: Backend) => (b === 'tradeyar' ? (p: string, o = {}) => ty<unknown>(`api/mobile/v1/${p}`, o) : (p: string, o = {}) => fx<unknown>(`user/mobile/${p}`, o));

function readAlert(x: Record<string, unknown>, backend: Backend): ServerAlert {
  return {
    id: str(x.id), symbol: str(x.symbol).toUpperCase(), condition: str(x.condition), value: num(x.value), trigger: str(x.trigger || 'once'), active: Boolean(x.active),
    createdAt: ms(x.created_at_ms ?? x.created_at), lastAt: ms(x.last_triggered_at_ms), expiresAt: ms(x.expires_at_ms ?? x.expires_at),
    spec: (x.spec as Record<string, string> | null) ?? null, channels: Array.isArray(x.channels) ? (x.channels as string[]) : ['push'], backend,
  };
}

export default function AlertsPage() {
  const L = useL();
  const t = useT();
  const sessions = useSession((s) => s.sessions);
  const local = useAlerts();
  const [tab, setTab] = useState<'server' | 'browser' | 'new'>(sessions.tradeyar || sessions.coineprofx ? 'server' : 'browser');
  const server = useLoad(async () => {
    const out: ServerAlert[] = [];
    for (const b of ['tradeyar', 'coineprofx'] as Backend[]) {
      if (!sessions[b]) continue;
      const r = await call(b)('alerts').catch(() => null) as { items?: Record<string, unknown>[] } | null;
      out.push(...(r?.items ?? []).map((x) => readAlert(x, b)));
    }
    return out.sort((a, b) => (b.createdAt ?? 0) - (a.createdAt ?? 0));
  }, [sessions.tradeyar, sessions.coineprofx]);

  const describe = (a: ServerAlert) => {
    if (a.spec?.kind === 'channel') return `${L('کانال', 'Channel')} ${a.spec.low}–${a.spec.high} · ${a.spec.op}`;
    if (a.spec?.kind === 'move') return `${a.spec.op === 'up_percent' ? '▲' : '▼'} ${a.spec.amount}% / ${a.spec.bars} ${L('کندل', 'bars')} (${a.spec.tf})`;
    if (a.spec?.kind === 'indicator') return `RSI(${a.spec.period}) ${a.spec.op} ${a.spec.value} (${a.spec.tf})`;
    const sym = { above: '≥', below: '≤', cross_up: '↗', cross_down: '↘', cross: '⇄' }[a.condition] ?? a.condition;
    return `${sym} ${a.value ?? ''}`;
  };
  const toggle = async (a: ServerAlert) => { await call(a.backend)(`alerts/${encodeURIComponent(a.id)}`, { method: 'PATCH', body: { active: !a.active } }).catch(() => null); server.reload(); };
  const del = async (a: ServerAlert) => { if (!window.confirm(L('این هشدار حذف شود؟', 'Delete this alert?'))) return; await call(a.backend)(`alerts/${encodeURIComponent(a.id)}`, { method: 'DELETE' }).catch(() => null); server.reload(); };

  return (
    <div className="page-inner">
      <Tabs value={tab} onChange={setTab} tabs={[
        { id: 'server', label: L('هشدارهای حساب', 'Account alerts') }, { id: 'browser', label: `${L('هشدارهای این مرورگر', 'This browser')} (${local.list.length})` }, { id: 'new', label: L('هشدار تازه', 'New alert') },
      ]} />
      {tab === 'server' ? (
        !(sessions.tradeyar || sessions.coineprofx) ? <div className="empty fa">{t('memberOnly')}</div> : (
          <State loading={server.loading} error={server.error} onRetry={server.reload} empty={!server.data?.length}>
            <table className="table">
              <thead><tr><th>{L('نماد', 'Symbol')}</th><th>{L('شرط', 'Condition')}</th><th>{L('تکرار', 'Repeat')}</th><th>{L('کانال', 'Channels')}</th><th>{L('آخرین بار', 'Last fired')}</th><th>{L('انقضا', 'Expires')}</th><th /></tr></thead>
              <tbody>{(server.data ?? []).map((a) => (
                <tr key={a.backend + a.id} className={a.active ? '' : 'muted'}>
                  <td><span className="row"><SymbolLogo symbol={a.symbol} size={18} /><b className="sym">{a.symbol}</b></span></td>
                  <td className="num">{describe(a)}</td><td>{a.trigger === 'recurring' ? L('هر بار', 'Every time') : L('یک بار', 'Once')}</td>
                  <td className="num">{a.channels.join(', ')}</td><td className="num">{dateTime(a.lastAt)}</td><td className="num">{dateTime(a.expiresAt)}</td>
                  <td className="r"><button className="btn btn-quiet sm" onClick={() => void toggle(a)}>{a.active ? L('توقف', 'Pause') : L('فعال', 'Resume')}</button> <button className="btn btn-quiet sm" onClick={() => void del(a)}>{L('حذف', 'Delete')}</button></td>
                </tr>
              ))}</tbody>
            </table>
          </State>
        )
      ) : null}
      {tab === 'browser' ? (
        <>
          <div className="row">
            {'Notification' in window && Notification.permission !== 'granted' ? <button className="btn btn-quiet sm" onClick={() => void Notification.requestPermission()}>{L('اجازه‌ی اعلان مرورگر', 'Allow browser notifications')}</button> : null}
          </div>
          {!local.list.length ? <div className="empty fa">{t('noResults')}</div> : null}
          <table className="table"><tbody>{local.list.map((a) => (
            <tr key={a.id} className={a.active ? '' : 'muted'}>
              <td><span className="row"><SymbolLogo symbol={a.symbol} size={18} /><b className="sym">{a.symbol}</b></span></td>
              <td className="num">{a.condition === 'above' ? '≥' : a.condition === 'below' ? '≤' : '⇄'} {a.value}</td>
              <td className="num">{a.triggeredAt ? `✓ ${dateTime(a.triggeredAt)}` : dateTime(a.createdAt)}</td>
              <td>{a.serverId ? L('در حساب هم ثبت شد', 'Also on the account') : ''}</td>
              <td className="r"><button className="btn btn-quiet sm" onClick={() => local.toggle(a.id)}>{a.active ? L('توقف', 'Pause') : L('فعال', 'Resume')}</button> <button className="btn btn-quiet sm" onClick={() => local.remove(a.id)}>{L('حذف', 'Delete')}</button></td>
            </tr>
          ))}</tbody></table>
        </>
      ) : null}
      {tab === 'new' ? <NewAlert onDone={() => { server.reload(); setTab(sessions.tradeyar || sessions.coineprofx ? 'server' : 'browser'); }} /> : null}
    </div>
  );
}

function NewAlert({ onDone }: { onDone(): void }) {
  const L = useL();
  const t = useT();
  const chart = usePrefs((s) => s.symbol);
  const [symbol, setSymbol] = useState(chart);
  const price = useMarket((s) => s.tickers[symbol]?.last ?? null);
  const sessions = useSession((s) => s.sessions);
  const local = useAlerts((s) => s.add);
  const backend = backendOf(symbol);
  const onServer = Boolean(sessions[backend]) && (backend === 'tradeyar' ? symbol.endsWith('USDT') : FX_SYMBOLS.includes(symbol));
  const advanced = onServer && backend === 'coineprofx';
  const [kind, setKind] = useState<Kind>('price');
  const [cond, setCond] = useState<Cond>('cross');
  const [f, setF] = useState({ value: price ? String(price) : '', low: '', high: '', chOp: 'enter', moveOp: 'up_percent', amount: '2', bars: '4', rsiOp: 'cross_up', rsiLen: '14', rsiLevel: '70', expires: '' });
  const [trigger, setTrigger] = useState<'once' | 'recurring'>('once');
  const [channels, setChannels] = useState<string[]>(['push']);
  const [pick, setPick] = useState(false);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => setF({ ...f, [k]: e.target.value });

  const submit = async () => {
    setErr(null);
    const value = parseNum(f.value);
    let spec: Record<string, string> | null = null;
    let wireValue = value;
    if (kind === 'channel') { const lo = parseNum(f.low); const hi = parseNum(f.high); if (lo == null || hi == null || lo > hi) { setErr(L('کف و سقف کانال را درست وارد کنید.', 'Enter a valid channel low and high.')); return; } spec = { kind: 'channel', low: String(lo), high: String(hi), op: f.chOp }; wireValue = hi; }
    if (kind === 'move') { spec = { kind: 'move', op: f.moveOp, amount: f.amount, bars: f.bars, tf: 'H1' }; wireValue = parseNum(f.amount); }
    if (kind === 'rsi') { spec = { kind: 'indicator', id: 'rsi', period: f.rsiLen, op: f.rsiOp, value: f.rsiLevel, tf: 'H1' }; wireValue = parseNum(f.rsiLevel); }
    if (!wireValue || wireValue <= 0) { setErr(L('مقدار را وارد کنید.', 'Enter a value.')); return; }
    if (kind === 'price' && !onServer) {
      local(symbol, cond === 'above' ? 'above' : cond === 'below' ? 'below' : 'cross', wireValue);
      onDone(); return;
    }
    setBusy(true);
    try {
      const expires = f.expires ? Date.parse(f.expires) : null;
      const body: Record<string, unknown> = { symbol, condition: spec ? 'spec' : cond, value: wireValue, trigger };
      if (advanced) { if (spec) body.spec = spec; body.channels = channels; if (expires) body.expires_at_ms = expires; }
      const r = await call(backend)('alerts', { body }) as { alert?: { id?: string | number } } | null;
      // The browser watches a plain level too, so it rings in this tab at once.
      if (kind === 'price') local(symbol, cond === 'above' ? 'above' : cond === 'below' ? 'below' : 'cross', wireValue, String(r?.alert?.id ?? 'server'));
      onDone();
    } catch (e) { setErr(errorText(e, t)); } finally { setBusy(false); }
  };

  return (
    <form className="form card" onSubmit={(e) => { e.preventDefault(); void submit(); }}>
      {pick ? <SymbolSearch onClose={() => setPick(false)} onPick={(s) => { setSymbol(s); setPick(false); }} /> : null}
      <div className="row"><button type="button" className="btn btn-quiet sm" onClick={() => setPick(true)}><SymbolLogo symbol={symbol} size={18} /><span className="sym">{symbol}</span></button><span className="num muted">{price ?? '—'}</span></div>
      <p className="muted small fa">{onServer ? L('این هشدار در حساب شما ثبت می‌شود و روی گوشی و تلگرام هم می‌رسد.', 'This alert is kept on your account and also reaches the phone and Telegram.') : L('این هشدار در همین مرورگر پایش می‌شود (تا وقتی صفحه باز است).', 'This alert is watched in this browser while the page is open.')}</p>
      <Tabs value={kind} onChange={setKind} tabs={[
        { id: 'price', label: L('قیمت', 'Price') },
        ...(advanced ? [{ id: 'channel' as Kind, label: L('کانال', 'Channel') }, { id: 'move' as Kind, label: L('حرکت درصدی', 'Move %') }, { id: 'rsi' as Kind, label: 'RSI' }] : []),
      ]} />
      {kind === 'price' ? (
        <div className="row two">
          <Field label={L('شرط', 'Condition')}><select value={cond} onChange={(e) => setCond(e.target.value as Cond)}>
            <option value="cross">{L('عبور', 'Crossing')}</option><option value="cross_up">{L('عبور رو به بالا', 'Crossing up')}</option><option value="cross_down">{L('عبور رو به پایین', 'Crossing down')}</option>
            <option value="above">{L('بالاتر از', 'Greater than')}</option><option value="below">{L('پایین‌تر از', 'Less than')}</option>
          </select></Field>
          <Field label={L('قیمت', 'Price')}><input className="num" dir="ltr" inputMode="decimal" value={f.value} onChange={set('value')} /></Field>
        </div>
      ) : null}
      {kind === 'channel' ? (
        <div className="row three">
          <Field label={L('کف', 'Low')}><input className="num" dir="ltr" inputMode="decimal" value={f.low} onChange={set('low')} /></Field>
          <Field label={L('سقف', 'High')}><input className="num" dir="ltr" inputMode="decimal" value={f.high} onChange={set('high')} /></Field>
          <Field label={L('وقتی', 'When')}><select value={f.chOp} onChange={set('chOp')}><option value="enter">{L('وارد شود', 'Entering')}</option><option value="exit">{L('خارج شود', 'Exiting')}</option><option value="inside">{L('داخل باشد', 'Inside')}</option><option value="outside">{L('بیرون باشد', 'Outside')}</option></select></Field>
        </div>
      ) : null}
      {kind === 'move' ? (
        <div className="row three">
          <Field label={L('جهت', 'Direction')}><select value={f.moveOp} onChange={set('moveOp')}><option value="up_percent">{L('بالا', 'Up')}</option><option value="down_percent">{L('پایین', 'Down')}</option></select></Field>
          <Field label={L('درصد', 'Percent')}><input className="num" dir="ltr" inputMode="decimal" value={f.amount} onChange={set('amount')} /></Field>
          <Field label={L('در چند کندل یک‌ساعته', 'Over how many 1h bars')}><input className="num" dir="ltr" inputMode="numeric" value={f.bars} onChange={set('bars')} /></Field>
        </div>
      ) : null}
      {kind === 'rsi' ? (
        <div className="row three">
          <Field label={L('دوره', 'Length')}><input className="num" dir="ltr" inputMode="numeric" value={f.rsiLen} onChange={set('rsiLen')} /></Field>
          <Field label={L('شرط', 'Condition')}><select value={f.rsiOp} onChange={set('rsiOp')}><option value="cross_up">{L('عبور رو به بالا', 'Crossing up')}</option><option value="cross_down">{L('عبور رو به پایین', 'Crossing down')}</option><option value="above">{L('بالاتر از', 'Above')}</option><option value="below">{L('پایین‌تر از', 'Below')}</option></select></Field>
          <Field label={L('سطح', 'Level')}><input className="num" dir="ltr" inputMode="decimal" value={f.rsiLevel} onChange={set('rsiLevel')} /></Field>
        </div>
      ) : null}
      {onServer ? <Field label={L('تکرار', 'Repeat')}><select value={trigger} onChange={(e) => setTrigger(e.target.value as 'once' | 'recurring')}><option value="once">{L('یک بار', 'Once')}</option><option value="recurring">{L('هر بار', 'Every time')}</option></select></Field> : null}
      {advanced ? (
        <>
          <Field label={L('کانال‌های اطلاع‌رسانی', 'Delivery channels')}><div className="chips flat">{['push', 'telegram', 'email'].map((c) => <button type="button" key={c} className={`chip${channels.includes(c) ? ' on' : ''}`} onClick={() => setChannels(channels.includes(c) ? (channels.length > 1 ? channels.filter((x) => x !== c) : channels) : [...channels, c])}><span className="num">{c}</span></button>)}</div></Field>
          <Field label={L('انقضا (اختیاری)', 'Expires (optional)')}><input type="datetime-local" value={f.expires} onChange={set('expires')} /></Field>
        </>
      ) : null}
      {err ? <p className="down fa">{err}</p> : null}
      <button className="btn btn-primary" disabled={busy}>{busy ? `${t('loading')}…` : L('ساخت هشدار', 'Create the alert')}</button>
    </form>
  );
}
