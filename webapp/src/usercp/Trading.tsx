import { useState } from 'react';
import { usePage } from '../state/page';
import { usePrefs } from '../state/prefs';
import { getTrades } from '../data/panel';
import { State, Tabs, downloadCsv, money, useL, useLoad } from '../pages/kit';
import { Card, Pill, Section, Stat, stamp } from './ui';
import { actionLabel, deviceLabel } from './labels';
import { BACKENDS, closedTrades, flat, orders, positions, sumBy, tradeAccount } from './sources';

const sideCell = (side: string) => <span className={`num ${side === 'buy' || side === 'long' ? 'up' : side ? 'down' : ''}`}>{side ? side.toUpperCase() : '—'}</span>;
const fig = (v: number | null | undefined, d = 2) => (v == null ? '—' : money(v, d));
const BACKEND_NAME = { tradeyar: 'TradeYar', coineprofx: 'CoinePro-FX' } as const;

export default function Trading() {
  const L = useL();
  const setSymbol = usePrefs((s) => s.setSymbol);
  const close = usePage((s) => s.close);
  const [tab, setTab] = useState<'open' | 'history' | 'audit'>('open');
  const [days, setDays] = useState(30);
  const account = useLoad(() => tradeAccount('tradeyar'), []);
  const pos = useLoad(() => Promise.all(BACKENDS.map(positions)).then(flat), []);
  const ords = useLoad(() => (tab === 'open' ? Promise.all(BACKENDS.map(orders)).then(flat) : Promise.resolve([])), [tab]);
  const hist = useLoad(() => (tab === 'history' ? Promise.all(BACKENDS.map((b) => closedTrades(b, days))).then(flat) : Promise.resolve([])), [tab, days]);
  const audit = useLoad(() => (tab === 'audit' ? getTrades() : Promise.resolve(null)), [tab]);
  const toChart = (symbol: string) => { if (!symbol) return; setSymbol(symbol); close(); };

  const net = sumBy(hist.data ?? [], (t) => t.pnl);
  const wins = (hist.data ?? []).filter((t) => t.pnl > 0).length;
  const a = account.data;

  return (
    <Section title={L('معاملات', 'Trading')} sub={L('پوزیشن‌ها و سفارش‌های باز، معاملات بسته روی هر دو پلتفرم، و هر سفارشی که از چارت وب فرستاده‌اید.', 'Open positions and orders, closed trades on both platforms, and every order you sent from the web chart.')}
      actions={<button className="btn btn-quiet sm" onClick={() => usePage.getState().open('trade')}>{L('معامله‌ی زنده', 'Live trading')}</button>}>
      <div className="ucp-stats">
        <Stat label={L('موجودی آزاد', 'Available')} value={fig(a?.available)} foot="TradeYar · USDT" />
        <Stat label={L('ارزش حساب', 'Equity')} value={fig(a?.equity)} foot={a ? (a.enabled ? L('معامله فعال است', 'Trading enabled') : L('معامله خاموش است', 'Trading disabled')) : L('حساب معامله وصل نیست', 'No trading account')} />
        <Stat label={L('سود و زیان باز', 'Unrealised P&L')} value={fig(pos.data ? sumBy(pos.data, (p) => p.pnl ?? 0) : null)} tone={pos.data && sumBy(pos.data, (p) => p.pnl ?? 0) < 0 ? 'bad' : 'good'} />
        <Stat label={L('پوزیشن باز', 'Open positions')} value={pos.data?.length ?? '—'} />
      </div>
      <Tabs value={tab} onChange={setTab} tabs={[{ id: 'open', label: L('باز', 'Open') }, { id: 'history', label: L('تاریخچه', 'History') }, { id: 'audit', label: L('ثبت‌شده از چارت وب', 'Sent from the web chart') }]} />

      {tab === 'open' ? (
        <>
          <Card title={L('پوزیشن‌ها', 'Positions')}>
            <State loading={pos.loading} error={pos.error} onRetry={pos.reload} empty={!pos.data?.length}>
              <div className="ucp-table-wrap"><table className="table ucp-table">
                <thead><tr><th>{L('نماد', 'Symbol')}</th><th>{L('جهت', 'Side')}</th><th className="r">{L('مقدار', 'Qty')}</th><th className="r">{L('ورود', 'Entry')}</th><th className="r">{L('قیمت', 'Mark')}</th><th className="r">{L('سود و زیان', 'P&L')}</th><th className="r">{L('اهرم', 'Lev.')}</th><th>{L('پلتفرم', 'Platform')}</th></tr></thead>
                <tbody>{(pos.data ?? []).map((p, i) => (
                  <tr key={i} className="click" onClick={() => toChart(p.symbol)}><td className="num"><b>{p.symbol}</b></td><td>{sideCell(p.side)}</td><td className="r num">{fig(p.quantity, 4)}</td><td className="r num">{fig(p.entry)}</td><td className="r num">{fig(p.mark)}</td>
                    <td className={`r num ${(p.pnl ?? 0) >= 0 ? 'up' : 'down'}`}>{fig(p.pnl)}</td><td className="r num">{p.leverage ? `${p.leverage}x` : '—'}</td><td>{BACKEND_NAME[p.backend]}</td></tr>
                ))}</tbody></table></div>
            </State>
          </Card>
          <Card title={L('سفارش‌ها', 'Orders')}>
            <State loading={ords.loading} error={ords.error} onRetry={ords.reload} empty={!ords.data?.length}>
              <div className="ucp-table-wrap"><table className="table ucp-table">
                <thead><tr><th>{L('نماد', 'Symbol')}</th><th>{L('جهت', 'Side')}</th><th>{L('نوع', 'Type')}</th><th className="r">{L('مقدار', 'Qty')}</th><th className="r">{L('قیمت', 'Price')}</th><th>{L('وضعیت', 'Status')}</th><th>{L('پلتفرم', 'Platform')}</th></tr></thead>
                <tbody>{(ords.data ?? []).map((o) => (
                  <tr key={`${o.backend}${o.id}`}><td className="num"><b>{o.symbol}</b></td><td>{sideCell(o.side)}</td><td>{o.type}</td><td className="r num">{fig(o.quantity, 4)}</td><td className="r num">{fig(o.price)}</td><td>{o.status}</td><td>{BACKEND_NAME[o.backend]}</td></tr>
                ))}</tbody></table></div>
            </State>
          </Card>
        </>
      ) : null}

      {tab === 'history' ? (
        <Card title={L('معاملات بسته', 'Closed trades')} sub={L(`${wins} سودده از ${(hist.data ?? []).length} · خالص ${money(net)}`, `${wins} of ${(hist.data ?? []).length} won · net ${money(net)}`)}
          actions={<>
            <select className="ucp-select" value={days} onChange={(e) => setDays(Number(e.target.value))} aria-label={L('بازه', 'Range')}>
              {[7, 30, 90].map((d) => <option key={d} value={d}>{L(`${d} روز`, `${d} days`)}</option>)}
            </select>
            <button className="btn btn-quiet sm" disabled={!hist.data?.length} onClick={() => downloadCsv(`pro-chart-trades-${days}d.csv`, [['platform', 'symbol', 'side', 'opened', 'closed', 'entry', 'exit', 'qty', 'pnl', 'fee', 'currency'], ...(hist.data ?? []).map((t) => [t.backend, t.symbol, t.side, stamp(t.opened), stamp(t.closed), t.entry, t.exit, t.qty, t.pnl, t.fee, t.currency])])}>CSV</button>
          </>}>
          <State loading={hist.loading} error={hist.error} onRetry={hist.reload} empty={!hist.data?.length}>
            <div className="ucp-table-wrap"><table className="table ucp-table">
              <thead><tr><th>{L('نماد', 'Symbol')}</th><th>{L('جهت', 'Side')}</th><th className="r">{L('ورود', 'Entry')}</th><th className="r">{L('خروج', 'Exit')}</th><th className="r">{L('مقدار', 'Qty')}</th><th className="r">{L('سود و زیان', 'P&L')}</th><th className="r">{L('بسته شد', 'Closed')}</th></tr></thead>
              <tbody>{[...(hist.data ?? [])].sort((x, y) => (y.closed ?? 0) - (x.closed ?? 0)).map((t) => (
                <tr key={`${t.backend}${t.id}`}><td className="num"><b>{t.symbol}</b></td><td>{sideCell(t.side)}</td><td className="r num">{fig(t.entry)}</td><td className="r num">{fig(t.exit)}</td><td className="r num">{fig(t.qty, 4)}</td>
                  <td className={`r num ${t.pnl >= 0 ? 'up' : 'down'}`}>{money(t.pnl)} {t.currency}</td><td className="r num">{stamp(t.closed)}</td></tr>
              ))}</tbody></table></div>
          </State>
        </Card>
      ) : null}

      {tab === 'audit' ? (
        <Card title={L('ثبت‌شده از چارت وب', 'Sent from the web chart')} sub={L('هر سفارش، بستن، حد سود و ضرر، اهرم و اجرای سیگنال که از این وب فرستاده شده، با پاسخ صرافی. کلید و رمز هرگز ثبت نمی‌شود.', 'Every order, close, stop and target, leverage change and signal execution sent from the web, with the exchange’s answer. Keys and secrets are never recorded.')}
          actions={<button className="btn btn-quiet sm" disabled={!audit.data?.items.length} onClick={() => downloadCsv('pro-chart-web-orders.csv', [['time', 'action', 'symbol', 'side', 'quantity', 'type', 'price', 'status', 'order_id', 'platform'], ...(audit.data?.items ?? []).map((t) => [stamp(t.at * 1000), t.action, t.symbol, t.side, t.quantity, t.type, t.price, t.status, t.order_id, t.backend])])}>CSV</button>}>
          <State loading={audit.loading} error={audit.error} onRetry={audit.reload} empty={!audit.data?.items.length}>
            <div className="ucp-table-wrap"><table className="table ucp-table">
              <thead><tr><th>{L('زمان', 'Time')}</th><th>{L('اقدام', 'Action')}</th><th>{L('نماد', 'Symbol')}</th><th>{L('جهت', 'Side')}</th><th className="r">{L('مقدار', 'Qty')}</th><th className="r">{L('قیمت', 'Price')}</th><th>{L('پاسخ', 'Answer')}</th><th>{L('شناسه‌ی سفارش', 'Order id')}</th><th>{L('دستگاه', 'Device')}</th></tr></thead>
              <tbody>{(audit.data?.items ?? []).map((t) => (
                <tr key={t.id}><td className="num">{stamp(t.at * 1000)}</td><td>{actionLabel(t.action, L)}</td><td className="num"><b>{t.symbol ?? '—'}</b></td><td>{sideCell(t.side ?? '')}</td><td className="r num">{t.quantity ?? '—'}</td><td className="r num">{t.price ?? '—'}</td>
                  <td>{t.ok ? <Pill tone="good">{t.state || 'OK'}</Pill> : <Pill tone="bad"><span className="num">{t.status || '—'}</span>{t.detail ? ` · ${t.detail}` : ''}</Pill>}</td><td className="num">{t.order_id ?? '—'}</td><td>{deviceLabel(t.device, L)}</td></tr>
              ))}</tbody></table></div>
          </State>
        </Card>
      ) : null}
    </Section>
  );
}
