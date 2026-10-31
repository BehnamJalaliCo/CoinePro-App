import { useMemo, useState } from 'react';
import { usePrefs } from '../state/prefs';
import { usePage } from '../state/page';
import { useSession } from '../data/session';
import { activeBackend, member, ty, fx, num, ms, str, usePlatform } from '../data/member';
import { SymbolLogo, hasLogo } from '../ui/SymbolLogo';
import { faNum } from '../i18n/time';
import { State, Tabs, dateTime, money, pct, useL, useLoad } from './kit';
import {
  JALALI_MONTHS, JALALI_MONTHS_EN, XLSX_MIME, attribution, byMonth, compactMoney, downloadFile, durationLabel, jalaliAt, jalaliShort, runningPeaks,
  tradeMetrics, tradesCsv, tradesFileName, tradesXlsx, type ClosedTrade, type DrawdownSpan, type EquityPoint, type MonthlyPerformance, type RunUpSpan, type TradeMetrics,
} from './records';
import type { Backend } from '../data/symbols';
import './records.css';

// The portfolio and its full report (feature/portfolio PortfolioScreen, PortfolioReportScreen): what
// the account holds now, the closed trades — CoinePro-FX's trade history, TradeYar's portfolio
// history — and the report a trader is normally charged for: the curve with its deepest fall and
// best climb marked, the whole metric set in the order a report is read, each symbol's share of the
// result, the Solar Hijri month grid, and the history as CSV or a real Excel workbook.
const side = (v: unknown): 'buy' | 'sell' => (/^(s|short)/i.test(str(v)) ? 'sell' : 'buy');
const sec = (v: unknown) => { const t = ms(v); return t == null ? null : Math.floor(t / 1000); };

async function history(backend: Backend, days: number): Promise<ClosedTrade[]> {
  const out: ClosedTrade[] = [];
  if (backend === 'coineprofx') {
    for (let page = 1; page <= 5; page++) {
      const r = await fx<{ items?: Record<string, unknown>[]; total_pages?: number }>(`user/trade-history?page=${page}&per_page=100`);
      for (const x of r.items ?? []) {
        const closedAt = sec(x.close_time);
        if (closedAt == null || !str(x.symbol)) continue;
        out.push({ id: str(x.id ?? x.deal_id), symbol: str(x.symbol).toUpperCase(), side: side(x.direction), volume: num(x.volume), entry: num(x.entry_price), exit: num(x.exit_price), openedAt: sec(x.open_time), closedAt,
          gross: num(x.gross_profit), commission: num(x.commission), swap: num(x.swap), net: num(x.net_profit), pips: num(x.pips), reason: str(x.close_reason), balanceAfter: num(x.balance_after), liquidated: false, currency: 'USD' });
      }
      if (!r.total_pages || page >= r.total_pages) break;
    }
    return out;
  }
  const to = Math.floor(Date.now() / 1000); const from = to - days * 86400;
  for (let page = 1; page <= 5; page++) {
    const r = await ty<{ trades?: Record<string, unknown>[]; total?: number; per_page?: number }>(`api/mobile/v1/portfolio/history?from=${from}&to=${to}&page=${page}&per_page=100`);
    for (const x of r.trades ?? []) {
      const closedAt = sec(x.closed_at);
      if (closedAt == null || !str(x.symbol)) continue;
      const fee = num(x.fee);
      out.push({ id: str(x.id), symbol: str(x.symbol).toUpperCase(), side: side(x.side), volume: num(x.quantity), entry: num(x.entry), exit: num(x.exit), openedAt: sec(x.opened_at), closedAt,
        gross: null, commission: fee == null ? null : -fee, swap: null, net: num(x.pnl), pips: null, reason: x.liquidated ? 'liquidated' : '', balanceAfter: null, liquidated: Boolean(x.liquidated), currency: str(x.currency) });
    }
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
  const trades = useLoad(() => (backend && wantTrades ? history(backend, days) : Promise.resolve([] as ClosedTrade[])), [backend, days, wantTrades]);

  const h = holdings.data;
  const total = h?.total as { amount?: number; currency?: string } | undefined;
  const change = h?.change as { amount?: number; percent?: number; period?: string } | undefined;
  const rows = (Array.isArray(h?.holdings) ? h!.holdings : []) as Record<string, unknown>[];
  const both = Boolean(sessions.tradeyar && sessions.coineprofx);
  const list = trades.data ?? [];

  return (
    <div className="page-inner">
      <div className="row">
        {both ? <Tabs value={platform} onChange={setPlatform} tabs={[{ id: 'tradeyar', label: L('رمزارز (تریدیار)', 'Crypto (TradeYar)') }, { id: 'coineprofx', label: L('فارکس (کوین‌پرو اف‌ایکس)', 'Forex (CoinePro-FX)') }]} /> : null}
        <Tabs value={tab} onChange={setTab} tabs={[{ id: 'holdings', label: L('دارایی‌ها', 'Holdings') }, { id: 'history', label: L('معاملات بسته', 'Closed trades') }, { id: 'report', label: L('گزارش کامل', 'Full report') }]} />
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
          {backend === 'tradeyar' ? <div className="row"><select value={days} onChange={(e) => setDays(Number(e.target.value))} aria-label={L('بازه', 'Window')}>{[7, 30, 90].map((d) => <option key={d} value={d}>{d} {L('روز', 'days')}</option>)}</select></div> : null}
          {tab === 'report' ? <p className="muted fa small">{L('سنجه‌های پیشرفته و خروجی فایل، بدون اشتراک.', 'Advanced metrics and file export, with no subscription.')}</p> : null}
          <State loading={trades.loading} error={trades.error} onRetry={trades.reload} empty={!list.length}
            emptyText={tab === 'report' ? `${L('هنوز معامله‌ای بسته نشده تا گزارشی از آن ساخته شود.', 'No trade has closed yet, so there is nothing to report on.')} ${L('وقتی اولین معامله‌تان بسته شد، منحنی سرمایه و همه‌ی سنجه‌ها همین‌جا می‌آیند.', 'Once your first trade closes, the equity curve and every metric appear here.')}` : undefined}>
            {tab === 'history' ? (
              <table className="table"><thead><tr><th>{L('نماد', 'Symbol')}</th><th>{L('جهت', 'Side')}</th><th className="r">{L('ورود', 'Entry')}</th><th className="r">{L('خروج', 'Exit')}</th><th className="r">{L('حجم', 'Size')}</th><th className="r">P&amp;L</th><th className="r">{L('بسته شد', 'Closed')}</th></tr></thead>
                <tbody>{list.map((x) => (
                  <tr key={x.id + x.closedAt}><td className="sym">{x.symbol}</td><td className={x.side === 'buy' ? 'up' : 'down'}>{x.side === 'buy' ? 'Buy' : 'Sell'}</td><td className="r num">{x.entry ?? '—'}</td><td className="r num">{x.exit ?? '—'}</td><td className="r num">{x.volume ?? '—'}</td><td className={`r num ${(x.net ?? 0) >= 0 ? 'up' : 'down'}`}>{money(x.net)}</td><td className="r num">{dateTime(x.closedAt * 1000)}</td></tr>
                ))}</tbody></table>
            ) : <Report trades={list} />}
          </State>
        </>
      ) : null}
    </div>
  );
}

/** The report: curve, metrics, attribution, months, export — the order PortfolioReportScreen reads. */
export function Report({ trades }: { trades: ClosedTrade[] }) {
  const metrics = useMemo(() => tradeMetrics(trades), [trades]);
  const shares = useMemo(() => attribution(trades), [trades]);
  const months = useMemo(() => byMonth(trades), [trades]);
  return (
    <div className="rp-report">
      <CurveCard m={metrics} />
      <MetricsCard m={metrics} />
      {shares.length ? <AttributionCard rows={shares} /> : null}
      {months.length ? <MonthGrid months={months} /> : null}
      <ExportCard trades={trades} />
    </div>
  );
}

const signedMoney = (v: number) => `${v > 0 ? '+' : ''}${money(v)}`;
const tone = (v: number | null | undefined) => (v == null ? '' : v > 0 ? 'up' : v < 0 ? 'down' : '');
const drawdownFigure = (s: DrawdownSpan) => `${signedMoney(-s.depth)}${s.depthPercent != null ? ` (${s.depthPercent.toFixed(1)}%)` : ''}`;
const runUpFigure = (s: RunUpSpan) => `${compactMoney(s.height)}${s.heightPercent != null ? ` · ${s.heightPercent.toFixed(1)}%` : ''}`;

function useDay() {
  const fa = usePrefs((s) => s.lang) === 'fa';
  return (sec: number) => {
    if (!fa) return new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short' }).format(new Date(sec * 1000));
    const j = jalaliAt(sec * 1000); return j ? jalaliShort(j) : '—';
  };
}

/** The curve with the given-back region shaded under the running peak, the deepest fall and best climb banded. */
export function ReportCurve({ points, drawdown, runUp, isBalance, height = 200 }: { points: EquityPoint[]; drawdown: DrawdownSpan | null; runUp: RunUpSpan | null; isBalance: boolean; height?: number }) {
  const W = 600;
  const eq = points.map((p) => p.equity);
  const peaks = runningPeaks(eq);
  let low = Math.min(...eq); let high = Math.max(...peaks);
  if (!isBalance) { low = Math.min(low, 0); high = Math.max(high, 0); }
  const range = high - low;
  if (points.length < 2 || !(range > 0)) return null;
  const pad = height * 0.08; const plot = height - pad * 2;
  const y = (v: number) => pad + ((high - v) / range) * plot;
  const x = (i: number) => (i / (eq.length - 1)) * W;
  const path = (vs: number[]) => vs.map((v, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)},${y(v).toFixed(1)}`).join(' ');
  const region = `${path(peaks)} ${eq.map((_, k) => { const i = eq.length - 1 - k; return `L${x(i).toFixed(1)},${y(eq[i]!).toFixed(1)}`; }).join(' ')} Z`;
  const rising = eq[eq.length - 1]! >= eq[0]!;
  return (
    <svg className="rp-curve" viewBox={`0 0 ${W} ${height}`} preserveAspectRatio="none" role="img" style={{ height }} aria-label="equity">
      {!isBalance ? <line className="rp-zero" x1="0" x2={W} y1={y(0)} y2={y(0)} vectorEffect="non-scaling-stroke" /> : null}
      {runUp && runUp.peakIndex > runUp.troughIndex ? <rect className="rp-band up" x={x(runUp.troughIndex)} width={x(runUp.peakIndex) - x(runUp.troughIndex)} y="0" height={height} /> : null}
      {drawdown && drawdown.troughIndex > drawdown.peakIndex ? <rect className="rp-band down" x={x(drawdown.peakIndex)} width={x(drawdown.troughIndex) - x(drawdown.peakIndex)} y="0" height={height} /> : null}
      <path className="rp-given" d={region} />
      <path className="rp-peak" d={path(peaks)} vectorEffect="non-scaling-stroke" />
      <path className={`rp-line ${rising ? 'up' : 'down'}`} d={path(eq)} vectorEffect="non-scaling-stroke" />
      {runUp && runUp.peakIndex > runUp.troughIndex ? <line className="rp-rule up" x1={x(runUp.peakIndex)} x2={x(runUp.peakIndex)} y1={y(runUp.peakEquity)} y2={y(runUp.troughEquity)} vectorEffect="non-scaling-stroke" /> : null}
      {drawdown && drawdown.troughIndex > drawdown.peakIndex ? <line className="rp-rule down" x1={x(drawdown.troughIndex)} x2={x(drawdown.troughIndex)} y1={y(drawdown.peakEquity)} y2={y(drawdown.troughEquity)} vectorEffect="non-scaling-stroke" /> : null}
    </svg>
  );
}

function CurveCard({ m }: { m: TradeMetrics }) {
  const L = useL(); const day = useDay();
  if (m.equity.length < 2) return null;
  return (
    <section className="card rp-card" data-part="curve">
      <h3 className="fa">{m.equityIsBalance ? L('موجودی حساب', 'Account balance') : L('سود انباشته', 'Cumulative profit')}</h3>
      <ReportCurve points={m.equity} drawdown={m.drawdown} runUp={m.runUp} isBalance={m.equityIsBalance} />
      {m.runUp ? <SpanLegend kind="up" title={L('بیشترین رشد پیوسته', 'Largest run-up')} span={L(`از ${day(m.runUp.troughAt)} تا ${day(m.runUp.peakAt)}`, `${day(m.runUp.troughAt)} to ${day(m.runUp.peakAt)}`)} figure={runUpFigure(m.runUp)} /> : null}
      {m.drawdown ? <SpanLegend kind="down" title={L('بیشترین افت', 'Max drawdown')} span={L(`از ${day(m.drawdown.peakAt)} تا ${day(m.drawdown.troughAt)}`, `${day(m.drawdown.peakAt)} to ${day(m.drawdown.troughAt)}`)} figure={drawdownFigure(m.drawdown)} /> : null}
      <p className="muted small fa">{L('خط چین، سقف تا آن لحظه است؛ ناحیه‌ی رنگی، فاصله‌ی سرمایه تا آن سقف.', 'The dashed line is the high so far; the shaded area is how far equity sat below it.')}</p>
      {!m.equityIsBalance ? <p className="muted small fa">{L('این منحنی سود محقق‌شده است، نه موجودی حساب؛ سود شناور در آن نیست.', 'This is realised profit, not account balance — open positions are not in it.')}</p> : null}
    </section>
  );
}

function SpanLegend({ kind, title, span, figure }: { kind: 'up' | 'down'; title: string; span: string; figure: string }) {
  return (
    <div className="rp-span">
      <span className={`rp-swatch ${kind}`} />
      <span className="grow"><span className="fa">{title}</span><small className="muted fa">{span}</small></span>
      <b className={`num ${kind}`} dir="ltr">{figure}</b>
    </div>
  );
}

function MetricsCard({ m }: { m: TradeMetrics }) {
  const L = useL();
  const fa = usePrefs((s) => s.lang) === 'fa';
  const count = (x: number) => (fa ? faNum(x) : String(x));
  const row = (label: string, value: string | null, t?: number | null) => (
    <div className="rp-metric" key={label}><span className="muted fa">{label}</span><b className={`num ${value == null ? 'muted' : tone(t)}`} dir="ltr">{value ?? '—'}</b></div>
  );
  const note = (text: string) => <p className="rp-note muted small fa">{text}</p>;
  return (
    <section className="card rp-card" data-part="metrics">
      <h3 className="fa">{L('سنجه‌ها', 'Metrics')}</h3>
      {row(L('نرخ برد', 'Win rate'), m.winRate == null ? null : `${m.winRate.toFixed(1)}%`, m.winRate == null ? null : m.winRate - 50)}
      {row(L('ضریب سود', 'Profit factor'), m.profitFactor == null ? null : m.profitFactor.toFixed(2), m.profitFactor == null ? null : m.profitFactor - 1)}
      {row(L('میانگین هر معامله', 'Average per trade'), m.expectancy == null ? null : signedMoney(m.expectancy), m.expectancy)}
      <hr />
      {row(L('میانگین برد', 'Average win'), m.averageWin == null ? null : money(m.averageWin), 1)}
      {row(L('میانگین باخت', 'Average loss'), m.averageLoss == null ? null : money(m.averageLoss), -1)}
      {row(L('نسبت برد به باخت', 'Win / loss ratio'), m.winLossRatio == null ? null : m.winLossRatio.toFixed(2), m.winLossRatio == null ? null : m.winLossRatio - 1)}
      {row(L('بزرگ‌ترین برد', 'Largest win'), m.largestWin == null ? null : signedMoney(m.largestWin), 1)}
      {row(L('بزرگ‌ترین باخت', 'Largest loss'), m.largestLoss == null ? null : signedMoney(m.largestLoss), -1)}
      <hr />
      {row(L('بیشترین رشد پیوسته', 'Largest run-up'), m.runUp ? runUpFigure(m.runUp) : null, 1)}
      {note(L('از کف تا سقف منحنی سرمایه؛ بالاترین جایی که حساب رسید، نه جایی که ماند. فاصله‌ی آن تا امروز همان چیزی است که پس داده شده.', 'Trough to peak on the equity curve: the highest the account reached, not where it stayed. The gap between that and today is what was handed back.'))}
      {row(L('بیشترین افت', 'Max drawdown'), m.drawdown ? drawdownFigure(m.drawdown) : null, -1)}
      {note(L('این عدد از سقف تا کف منحنی سرمایه است، نه بزرگ‌ترین معامله‌ی زیان‌ده؛ چند زیان پشت‌سرهم افتی بزرگ‌تر از هر کدامشان می‌سازد.', 'This is peak to trough on the equity curve, not the largest losing trade — several losses in a row make a fall larger than any one of them.'))}
      {row(L('طولانی‌ترین دوره‌ی افت', 'Longest drawdown'), m.longestDrawdown ? durationLabel(m.longestDrawdown.seconds, fa) : null, -1)}
      {m.longestDrawdown ? note(m.longestDrawdown.recovered
        ? L(`${count(m.longestDrawdown.trades)} معامله طول کشید تا سقف قبلی دوباره زده شود.`, `It took ${m.longestDrawdown.trades} trades to make a new high.`)
        : L(`${count(m.longestDrawdown.trades)} معامله است که هنوز زیر سقف قبلی هستید.`, `You have been below the previous high for ${m.longestDrawdown.trades} trades.`)) : null}
      {row(L('شارپ', 'Sharpe'), m.sharpe == null ? null : m.sharpe.toFixed(2), m.sharpe)}
      {row(L('سورتینو', 'Sortino'), m.sortino == null ? null : m.sortino.toFixed(2), m.sortino)}
      {note(L('شارپ و سورتینو هر دو به ازای هر معامله محاسبه شده‌اند، نه سالانه؛ با عدد سالانه‌ی یک صندوق قابل مقایسه نیستند.', "Sharpe and Sortino are both per trade rather than annualised, so they are not comparable with a fund's published figure."))}
      <hr />
      {row(L('میانگین مدت نگهداری', 'Average holding time'), m.averageHoldingSeconds == null ? null : durationLabel(m.averageHoldingSeconds, fa))}
      {m.holdingSample >= 1 && m.holdingSample < m.trades ? note(L(`میانگین بر پایه‌ی ${count(m.holdingSample)} معامله از ${count(m.trades)} معامله است؛ بقیه زمان باز شدن ندارند.`, `Averaged over ${m.holdingSample} trades of ${m.trades}; the rest carry no open time.`)) : null}
      {row(L('بیشترین بردهای پیاپی', 'Longest winning streak'), String(m.longestWinStreak), 1)}
      {row(L('بیشترین باخت‌های پیاپی', 'Longest losing streak'), String(m.longestLossStreak), -1)}
      {m.scratches > 0 ? row(L('معاملات سربه‌سر', 'Break-even trades'), String(m.scratches)) : null}
    </section>
  );
}

function AttributionCard({ rows }: { rows: ReturnType<typeof attribution> }) {
  const L = useL();
  return (
    <section className="card rp-card" data-part="attribution">
      <h3 className="fa">{L('سهم هر نماد', 'By symbol')}</h3>
      {rows.map((r) => (
        <div key={r.symbol} className="rp-attr">
          <div className="row">
            <span className="grow"><b className="sym" dir="ltr">{r.symbol}</b><small className="muted"><span className="num">{r.trades}</span> <span className="fa">{L('معامله', 'Trades')}</span>{r.winRate != null ? <span className="num"> · {r.winRate.toFixed(0)}%</span> : null}</small></span>
            <b className={`num ${tone(r.net)}`} dir="ltr">{signedMoney(r.net)}</b>
          </div>
          {r.share != null ? <div className="rp-share"><span className={tone(r.net)} style={{ width: `${Math.min(100, Math.max(2, r.share))}%` }} /></div> : null}
        </div>
      ))}
    </section>
  );
}

function MonthGrid({ months }: { months: MonthlyPerformance[] }) {
  const L = useL();
  const fa = usePrefs((s) => s.lang) === 'fa';
  const years = [...new Set(months.map((m) => m.year))].sort((a, b) => a - b);
  return (
    <section className="card rp-card" data-part="months">
      <h3 className="fa">{L('ماه‌به‌ماه', 'By month')}</h3>
      <div className="rp-grid-scroll">
        <table className="rp-grid">
          <thead><tr><th />{(fa ? JALALI_MONTHS : JALALI_MONTHS_EN).map((n) => <th key={n} className="fa">{n}</th>)}<th className="fa">{L('کل سال', 'Year')}</th></tr></thead>
          <tbody>{years.map((y) => {
            const rows = months.filter((m) => m.year === y);
            const total = rows.reduce((n, m) => n + m.net, 0);
            return (
              <tr key={y}><th className="num">{y}</th>
                {Array.from({ length: 12 }, (_, i) => { const c = rows.find((m) => m.month === i + 1); return <td key={i} className={`num ${c && c.trades ? tone(c.net) : ''}`} dir="ltr">{!c || !c.trades ? '' : compactMoney(c.net)}</td>; })}
                <td className={`num rp-total ${tone(total)}`} dir="ltr">{compactMoney(total)}</td></tr>
            );
          })}</tbody>
        </table>
      </div>
    </section>
  );
}

/** The two exports, built in the browser and handed over as files the reader owns. */
export function ExportCard({ trades, note, name = tradesFileName }: { trades: ClosedTrade[]; note?: string; name?: (ext: string) => string }) {
  const L = useL();
  const [done, setDone] = useState<string | null>(null);
  const save = (kind: 'csv' | 'xlsx') => {
    try {
      if (kind === 'csv') downloadFile(name('csv'), tradesCsv(trades), 'text/csv;charset=utf-8');
      else downloadFile(name('xlsx'), tradesXlsx(trades), XLSX_MIME);
      setDone(L('فایل ذخیره شد.', 'The file was saved.'));
    } catch { setDone(L('فایل نوشته نشد. جای دیگری را انتخاب کنید.', 'The file could not be written. Choose another location.')); }
  };
  return (
    <section className="card rp-card" data-part="export">
      <h3 className="fa">{L('خروجی گرفتن', 'Export')}</h3>
      <div className="row">
        <button type="button" className="btn btn-quiet sm" data-export="csv" disabled={!trades.length} onClick={() => save('csv')}>CSV</button>
        <button type="button" className="btn btn-quiet sm" data-export="xlsx" disabled={!trades.length} onClick={() => save('xlsx')}>Excel</button>
      </div>
      {note ? <p className="muted small fa">{note}</p> : null}
      {done ? <p className="muted small fa">{done}</p> : null}
    </section>
  );
}
