import { useMemo, useState } from 'react';
import { usePrefs } from '../state/prefs';
import { usePage } from '../state/page';
import { useSession } from '../data/session';
import { activeBackend, member, ty, fx, num, ms, str, usePlatform } from '../data/member';
import { SymbolLogo, hasLogo } from '../ui/SymbolLogo';
import { State, Tabs, dateTime, downloadCsv, money, pct, useL, useLoad } from './kit';
import type { Backend } from '../data/symbols';

// The portfolio and its report (feature/portfolio, core/account AccountApi, core/portfolio): what
// the account holds now, and the closed trades — CoinePro-FX's trade history, TradeYar's portfolio
// history — with the figures a trader reads them by, by symbol and by month, and a CSV.
interface Trade { id: string; symbol: string; side: string; opened: number | null; closed: number | null; entry: number | null; exit: number | null; qty: number | null; pnl: number; fee: number; pips: number | null; reason: string }

async function history(backend: Backend, days: number): Promise<Trade[]> {
  if (backend === 'coineprofx') {
    const out: Trade[] = [];
    for (let page = 1; page <= 5; page++) {
      const r = await fx<{ items?: Record<string, unknown>[]; total_pages?: number }>(`user/trade-history?page=${page}&per_page=100`);
      for (const x of r.items ?? []) out.push({ id: str(x.id ?? x.deal_id), symbol: str(x.symbol), side: str(x.direction).toLowerCase(), opened: ms(x.open_time), closed: ms(x.close_time), entry: num(x.entry_price), exit: num(x.exit_price), qty: num(x.volume), pnl: num(x.net_profit) ?? 0, fee: (num(x.commission) ?? 0) + (num(x.swap) ?? 0), pips: num(x.pips), reason: str(x.close_reason) });
      if (!r.total_pages || page >= r.total_pages) break;
    }
    return out;
  }
  const to = Math.floor(Date.now() / 1000); const from = to - days * 86400;
  const out: Trade[] = [];
  for (let page = 1; page <= 5; page++) {
    const r = await ty<{ trades?: Record<string, unknown>[]; total?: number; per_page?: number }>(`api/mobile/v1/portfolio/history?from=${from}&to=${to}&page=${page}&per_page=100`);
    for (const x of r.trades ?? []) out.push({ id: str(x.id), symbol: str(x.symbol), side: str(x.side).toLowerCase(), opened: ms(x.opened_at), closed: ms(x.closed_at), entry: num(x.entry), exit: num(x.exit), qty: num(x.quantity), pnl: num(x.pnl) ?? 0, fee: num(x.fee) ?? 0, pips: null, reason: x.liquidated ? 'liquidated' : '' });
    if ((r.trades ?? []).length < 100) break;
  }
  return out;
}

export default function PortfolioPage() {
  const L = useL();
  const sessions = useSession((s) => s.sessions);
  const platform = usePlatform((s) => s.platform);
  const setPlatform = usePlatform((s) => s.set);
  const setSymbol = usePrefs((s) => s.setSymbol);
  const close = usePage((s) => s.close);
  const backend = activeBackend();
  const [tab, setTab] = useState<'holdings' | 'history' | 'report'>('holdings');
  const [days, setDays] = useState(30);
  const holdings = useLoad(() => member<Record<string, unknown>>('portfolio'), [backend]);
  const wantTrades = tab !== 'holdings';
  const trades = useLoad(() => (backend && wantTrades ? history(backend, days) : Promise.resolve([] as Trade[])), [backend, days, wantTrades]);

  const report = useMemo(() => {
    const list = trades.data ?? [];
    const wins = list.filter((x) => x.pnl > 0); const losses = list.filter((x) => x.pnl <= 0);
    const gw = wins.reduce((n, x) => n + x.pnl, 0); const gl = Math.abs(losses.reduce((n, x) => n + x.pnl, 0));
    const group = (key: (x: Trade) => string) => {
      const m = new Map<string, { n: number; pnl: number; wins: number }>();
      for (const x of list) { const k = key(x); const r = m.get(k) ?? { n: 0, pnl: 0, wins: 0 }; r.n++; r.pnl += x.pnl; if (x.pnl > 0) r.wins++; m.set(k, r); }
      return [...m.entries()];
    };
    let peak = 0; let run = 0; let dd = 0;
    for (const x of [...list].sort((a, b) => (a.closed ?? 0) - (b.closed ?? 0))) { run += x.pnl; peak = Math.max(peak, run); dd = Math.max(dd, peak - run); }
    return {
      n: list.length, net: gw - gl, win: list.length ? (wins.length / list.length) * 100 : 0, pf: gl ? gw / gl : gw ? Infinity : 0,
      avgWin: wins.length ? gw / wins.length : 0, avgLoss: losses.length ? -gl / losses.length : 0, fees: list.reduce((n, x) => n + x.fee, 0), dd,
      bySymbol: group((x) => x.symbol).sort((a, b) => b[1].pnl - a[1].pnl),
      byMonth: group((x) => (x.closed ? new Date(x.closed).toISOString().slice(0, 7) : '—')).sort((a, b) => a[0].localeCompare(b[0])),
    };
  }, [trades.data]);

  const h = holdings.data;
  const total = h?.total as { amount?: number; currency?: string } | undefined;
  const change = h?.change as { amount?: number; percent?: number; period?: string } | undefined;
  const rows = (Array.isArray(h?.holdings) ? h!.holdings : []) as Record<string, unknown>[];
  const both = Boolean(sessions.tradeyar && sessions.coineprofx);

  return (
    <div className="page-inner">
      <div className="row">
        {both ? <Tabs value={platform} onChange={setPlatform} tabs={[{ id: 'tradeyar', label: L('رمزارز (تریدیار)', 'Crypto (TradeYar)') }, { id: 'coineprofx', label: L('فارکس (کوین‌پرو اف‌ایکس)', 'Forex (CoinePro-FX)') }]} /> : null}
        <Tabs value={tab} onChange={setTab} tabs={[{ id: 'holdings', label: L('دارایی‌ها', 'Holdings') }, { id: 'history', label: L('معاملات بسته', 'Closed trades') }, { id: 'report', label: L('گزارش', 'Report') }]} />
      </div>
      {tab === 'holdings' ? (
        <State loading={holdings.loading} error={holdings.error} onRetry={holdings.reload}>
          <div className="pulse">
            <div className="card"><span className="muted fa">{L('ارزش کل', 'Total value')}</span><b className="num">{money(num(total?.amount))} {total?.currency ?? ''}</b></div>
            <div className="card"><span className="muted fa">{L('تغییر', 'Change')} {change?.period ? `(${change.period})` : ''}</span><b className={`num ${(change?.amount ?? 0) >= 0 ? 'up' : 'down'}`}>{money(num(change?.amount))} · {pct(num(change?.percent))}</b></div>
            <div className="card"><span className="muted fa">{L('به‌روز تا', 'As of')}</span><b className="num">{dateTime(ms(h?.as_of))}</b></div>
          </div>
          {!rows.length ? <div className="empty fa">{L('دارایی‌ای نیست.', 'Nothing held.')}</div> : (
            <table className="table"><thead><tr><th>{L('دارایی', 'Asset')}</th><th className="r">{L('مقدار', 'Quantity')}</th><th className="r">{L('ارزش', 'Value')}</th><th className="r">{L('تغییر', 'Change')}</th></tr></thead>
              <tbody>{rows.map((x) => { const s = str(x.symbol); return (
                <tr key={s} className={hasLogo(s) ? 'click' : ''} onClick={() => { if (hasLogo(s)) { setSymbol(s); close(); } }}>
                  <td><span className="row">{hasLogo(s) ? <SymbolLogo symbol={s} size={18} /> : null}<b className="sym">{s}</b><span className="muted">{str(x.display_name)}</span></span></td>
                  <td className="r num">{num(x.quantity) ?? '—'} {str(x.quantity_unit)}</td><td className="r num">{money(num(x.value))}</td><td className={`r num ${(num(x.change_percent) ?? 0) >= 0 ? 'up' : 'down'}`}>{pct(num(x.change_percent))}</td>
                </tr>
              ); })}</tbody></table>
          )}
        </State>
      ) : null}
      {tab !== 'holdings' ? (
        <>
          <div className="row">
            {backend === 'tradeyar' ? <select value={days} onChange={(e) => setDays(Number(e.target.value))}>{[7, 30, 90].map((d) => <option key={d} value={d}>{d} {L('روز', 'days')}</option>)}</select> : null}
            <button className="btn btn-quiet sm" disabled={!trades.data?.length} onClick={() => downloadCsv('trades.csv', [['symbol', 'side', 'opened', 'closed', 'entry', 'exit', 'quantity', 'pnl', 'fees', 'pips', 'reason'], ...(trades.data ?? []).map((x) => [x.symbol, x.side, x.opened ? new Date(x.opened).toISOString() : '', x.closed ? new Date(x.closed).toISOString() : '', x.entry, x.exit, x.qty, x.pnl, x.fee, x.pips, x.reason])])}>CSV</button>
          </div>
          <State loading={trades.loading} error={trades.error} onRetry={trades.reload} empty={!trades.data?.length}>
            {tab === 'history' ? (
              <table className="table"><thead><tr><th>{L('نماد', 'Symbol')}</th><th>{L('جهت', 'Side')}</th><th className="r">{L('ورود', 'Entry')}</th><th className="r">{L('خروج', 'Exit')}</th><th className="r">{L('حجم', 'Size')}</th><th className="r">P&amp;L</th><th className="r">{L('بسته شد', 'Closed')}</th></tr></thead>
                <tbody>{(trades.data ?? []).map((x) => (
                  <tr key={x.id + x.closed}><td className="sym">{x.symbol}</td><td className={x.side.startsWith('b') || x.side === 'long' ? 'up' : 'down'}>{x.side}</td><td className="r num">{x.entry ?? '—'}</td><td className="r num">{x.exit ?? '—'}</td><td className="r num">{x.qty ?? '—'}</td><td className={`r num ${x.pnl >= 0 ? 'up' : 'down'}`}>{money(x.pnl)}</td><td className="r num">{dateTime(x.closed)}</td></tr>
                ))}</tbody></table>
            ) : (
              <>
                <div className="bt-stats wide">
                  {([[L('معاملات', 'Trades'), String(report.n)], [L('نتیجه‌ی خالص', 'Net'), money(report.net), report.net >= 0 ? 'up' : 'down'], [L('نرخ برد', 'Win rate'), `${report.win.toFixed(1)}%`], [L('ضریب سود', 'Profit factor'), Number.isFinite(report.pf) ? report.pf.toFixed(2) : '∞'], [L('میانگین برد', 'Avg win'), money(report.avgWin), 'up'], [L('میانگین باخت', 'Avg loss'), money(report.avgLoss), 'down'], [L('کارمزد و سوآپ', 'Fees and swap'), money(report.fees)], [L('بیشترین افت', 'Max drawdown'), money(report.dd), 'down']] as [string, string, string?][]).map(([k, v, tone]) => <div key={k} className="bt-stat"><span className="bt-k fa">{k}</span><span className={`bt-v num ${tone ?? ''}`}>{v}</span></div>)}
                </div>
                <div className="cards">
                  <section className="card"><h3 className="fa">{L('بر اساس نماد', 'By symbol')}</h3><table className="table"><tbody>{report.bySymbol.map(([k, r]) => <tr key={k}><td className="sym">{k}</td><td className="r num">{r.n}</td><td className="r num">{((r.wins / r.n) * 100).toFixed(0)}%</td><td className={`r num ${r.pnl >= 0 ? 'up' : 'down'}`}>{money(r.pnl)}</td></tr>)}</tbody></table></section>
                  <section className="card"><h3 className="fa">{L('بر اساس ماه', 'By month')}</h3><table className="table"><tbody>{report.byMonth.map(([k, r]) => <tr key={k}><td className="num">{k}</td><td className="r num">{r.n}</td><td className={`r num ${r.pnl >= 0 ? 'up' : 'down'}`}>{money(r.pnl)}</td></tr>)}</tbody></table></section>
                </div>
              </>
            )}
          </State>
        </>
      ) : null}
    </div>
  );
}
