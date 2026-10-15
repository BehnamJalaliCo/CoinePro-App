import { useMemo, useState } from 'react';
import { usePrefs } from '../state/prefs';
import { useMarket } from '../state/market';
import { marketOf } from '../data/symbols';
import { Field, Tabs, money, parseNum, useL } from './kit';

// The trader's toolbox (feature/tools TraderToolsCalculator.kt): the same eight calculators with
// the same rules and formulas, plus the live price of the open symbol one click away.
type Calc = 'risk' | 'position' | 'rr' | 'pip' | 'profit' | 'crypto' | 'compound' | 'drawdown';
type Dir = 'long' | 'short';

/** Pip size of the open symbol: JPY pairs 0.01, other forex 0.0001, gold 0.1, silver 0.01. */
export function pipSizeOf(symbol: string): number {
  if (symbol.startsWith('XAU')) return 0.1;
  if (symbol.startsWith('XAG')) return 0.01;
  if (marketOf(symbol) === 'forex') return symbol.includes('JPY') ? 0.01 : 0.0001;
  return 1;
}

/** Contract size of one standard lot: forex 100,000, gold 100 oz, silver 5,000 oz. */
export function contractOf(symbol: string): number {
  if (symbol.startsWith('XAU')) return 100;
  if (symbol.startsWith('XAG')) return 5000;
  if (marketOf(symbol) === 'forex') return 100_000;
  return 1;
}

export const calc = {
  risk: (capital: number, riskPct: number) => { const r = capital * (riskPct / 100); return { risk: r, after: capital - r }; },
  position: (riskAmount: number, stopPips: number, pipValue: number) => ({ lots: riskAmount / (stopPips * pipValue) }),
  rr(entry: number, stop: number, target: number, dir: Dir): { risk: number; reward: number; ratio: number } | null {
    const ok = dir === 'long' ? stop < entry && target > entry : stop > entry && target < entry;
    if (!ok) return null;
    const risk = Math.abs(entry - stop); const reward = Math.abs(target - entry);
    return { risk, reward, ratio: reward / risk };
  },
  profit: (entry: number, exit: number, lots: number, contract: number, dir: Dir) => { const move = dir === 'long' ? exit - entry : entry - exit; return { pnl: move * lots * contract, move }; },
  pips: (entry: number, exit: number, lots: number, pipSize: number, pipValue: number, dir: Dir) => { const d = dir === 'long' ? exit - entry : entry - exit; const pips = d / pipSize; return { pips, pnl: pips * lots * pipValue }; },
  crypto(entry: number, exit: number, qty: number, feePct: number, dir: Dir) {
    const gross = (dir === 'long' ? exit - entry : entry - exit) * qty;
    const fees = (entry * qty + exit * qty) * (feePct / 100);
    const net = gross - fees;
    return { gross, fees, net, ret: (net / (entry * qty)) * 100 };
  },
  compound: (principal: number, ratePct: number, periods: number) => { const end = principal * (1 + ratePct / 100) ** periods; return { end, profit: end - principal }; },
  drawdown(start: number, lossPct: number, losses: number) {
    const end = start * (1 - lossPct / 100) ** losses;
    const amount = start - end;
    return { end, amount, pct: (amount / start) * 100, recovery: end === 0 ? Infinity : (start / end - 1) * 100 };
  },
};

function useNums(init: Record<string, string>) {
  const [v, setV] = useState(init);
  const n = useMemo(() => Object.fromEntries(Object.entries(v).map(([k, x]) => [k, parseNum(x)])) as Record<string, number | null>, [v]);
  const bind = (k: string) => ({ value: v[k] ?? '', onChange: (e: React.ChangeEvent<HTMLInputElement>) => setV({ ...v, [k]: e.target.value }), inputMode: 'decimal' as const, dir: 'ltr' as const, className: 'num' });
  return { n, bind, set: (k: string, x: string) => setV((cur) => ({ ...cur, [k]: x })) };
}

export default function ToolsPage() {
  const L = useL();
  const symbol = usePrefs((s) => s.symbol);
  const live = useMarket((s) => s.tickers[symbol]?.last ?? null);
  const [tab, setTab] = useState<Calc>('risk');
  const [dir, setDir] = useState<Dir>('long');
  const f = useNums({
    capital: '10000', riskPct: '1', riskAmount: '100', stopPips: '20', pipValue: '10',
    entry: '', stop: '', target: '', exit: '', lots: '1', contract: String(contractOf(symbol)), pipSize: String(pipSizeOf(symbol)),
    qty: '1', fee: '0.1', principal: '1000', rate: '2', periods: '12', start: '10000', loss: '2', losses: '5',
  });
  const { n } = f;
  const pos = (...xs: (number | null | undefined)[]) => xs.every((x) => x != null && x > 0);
  const fillLive = (k: string) => { if (live) f.set(k, String(live)); };
  const liveBtn = (k: string) => (live ? <button type="button" className="link-btn num" onClick={() => fillLive(k)}>{symbol} {live}</button> : null);

  let out: [string, string, string?][] = [];
  let invalid: string | null = null;
  const need = L('همه‌ی خانه‌ها را با عدد مثبت پر کنید.', 'Fill every field with a positive number.');
  switch (tab) {
    case 'risk':
      if (pos(n.capital) && n.riskPct != null && n.riskPct > 0 && n.riskPct <= 100) { const r = calc.risk(n.capital!, n.riskPct); out = [[L('مبلغ ریسک', 'Risk amount'), money(r.risk)], [L('سرمایه پس از ضرر کامل', 'Capital after a full loss'), money(r.after)]]; }
      else invalid = need;
      break;
    case 'position':
      if (pos(n.riskAmount, n.stopPips, n.pipValue)) { const r = calc.position(n.riskAmount!, n.stopPips!, n.pipValue!); out = [[L('حجم (لات استاندارد)', 'Size (standard lots)'), r.lots.toFixed(2)], [L('ریسک', 'Risk'), money(n.riskAmount)]]; }
      else invalid = need;
      break;
    case 'rr':
      if (pos(n.entry, n.stop, n.target)) {
        const r = calc.rr(n.entry!, n.stop!, n.target!, dir);
        if (r) out = [[L('فاصله‌ی ریسک', 'Risk distance'), String(+r.risk.toFixed(8))], [L('فاصله‌ی سود', 'Reward distance'), String(+r.reward.toFixed(8))], ['R:R', `1 : ${r.ratio.toFixed(2)}`, r.ratio >= 2 ? 'up' : r.ratio < 1 ? 'down' : '']];
        else invalid = dir === 'long' ? L('در خرید، حد ضرر زیر ورود و هدف بالای آن است.', 'For a long, the stop is below the entry and the target above it.') : L('در فروش، حد ضرر بالای ورود و هدف زیر آن است.', 'For a short, the stop is above the entry and the target below it.');
      } else invalid = need;
      break;
    case 'pip':
      if (pos(n.entry, n.exit, n.lots, n.pipSize, n.pipValue)) { const r = calc.pips(n.entry!, n.exit!, n.lots!, n.pipSize!, n.pipValue!, dir); out = [[L('پیپ', 'Pips'), r.pips.toFixed(1), r.pips >= 0 ? 'up' : 'down'], [L('سود و زیان', 'P&L'), money(r.pnl), r.pnl >= 0 ? 'up' : 'down']]; }
      else invalid = need;
      break;
    case 'profit':
      if (pos(n.entry, n.exit, n.lots, n.contract)) { const r = calc.profit(n.entry!, n.exit!, n.lots!, n.contract!, dir); out = [[L('تغییر قیمت', 'Price move'), String(+r.move.toFixed(8))], [L('سود و زیان تخمینی', 'Estimated P&L'), money(r.pnl), r.pnl >= 0 ? 'up' : 'down']]; }
      else invalid = need;
      break;
    case 'crypto':
      if (pos(n.entry, n.exit, n.qty) && n.fee != null && n.fee >= 0 && n.fee < 100) { const r = calc.crypto(n.entry!, n.exit!, n.qty!, n.fee, dir); out = [[L('سود ناخالص', 'Gross P&L'), money(r.gross)], [L('کارمزد', 'Fees'), money(r.fees)], [L('سود خالص', 'Net P&L'), money(r.net), r.net >= 0 ? 'up' : 'down'], [L('بازده', 'Return'), `${r.ret.toFixed(2)}%`, r.ret >= 0 ? 'up' : 'down']]; }
      else invalid = need;
      break;
    case 'compound':
      if (pos(n.principal, n.periods) && n.rate != null && n.rate > -100) { const r = calc.compound(n.principal!, n.rate, Math.round(n.periods!)); out = [[L('موجودی پایانی', 'Ending balance'), money(r.end)], [L('سود', 'Profit'), money(r.profit), r.profit >= 0 ? 'up' : 'down']]; }
      else invalid = need;
      break;
    case 'drawdown':
      if (pos(n.start, n.losses) && n.loss != null && n.loss > 0 && n.loss < 100) { const r = calc.drawdown(n.start!, n.loss, Math.round(n.losses!)); out = [[L('موجودی پایانی', 'Ending balance'), money(r.end)], [L('افت', 'Drawdown'), `${money(r.amount)} · ${r.pct.toFixed(2)}%`, 'down'], [L('بازده لازم برای جبران', 'Gain needed to recover'), Number.isFinite(r.recovery) ? `${r.recovery.toFixed(2)}%` : '∞']]; }
      else invalid = need;
      break;
  }

  const dirSwitch = <Tabs value={dir} onChange={setDir} tabs={[{ id: 'long', label: L('خرید', 'Long') }, { id: 'short', label: L('فروش', 'Short') }]} />;
  const inp = (k: string, label: string, hint?: string, liveKey = false) => (
    <Field label={label} hint={hint}><span className="row"><input {...f.bind(k)} aria-label={label} />{liveKey ? liveBtn(k) : null}</span></Field>
  );

  return (
    <div className="page-inner narrow tools">
      <Tabs value={tab} onChange={setTab} tabs={[
        { id: 'risk', label: L('مدیریت ریسک', 'Risk') }, { id: 'position', label: L('حجم معامله', 'Position size') },
        { id: 'rr', label: L('ریسک به ریوارد', 'Risk : reward') }, { id: 'pip', label: L('ارزش پیپ', 'Pip value') },
        { id: 'profit', label: L('سود فارکس و فلز', 'Forex and metal P&L') }, { id: 'crypto', label: L('سود کریپتو', 'Crypto P&L') },
        { id: 'compound', label: L('سود مرکب', 'Compounding') }, { id: 'drawdown', label: L('افت سرمایه', 'Drawdown') },
      ]} />
      <div className="tools-grid">
        <form className="form card" onSubmit={(e) => e.preventDefault()}>
          {tab === 'risk' ? <>{inp('capital', L('سرمایه‌ی حساب', 'Account capital'))}{inp('riskPct', L('درصد ریسک هر معامله', 'Risk per trade %'), L('معمولاً ۱ تا ۲ درصد', 'Usually 1–2%'))}</> : null}
          {tab === 'position' ? <>{inp('riskAmount', L('مبلغ ریسک', 'Risk amount'))}{inp('stopPips', L('فاصله‌ی حد ضرر (پیپ)', 'Stop distance (pips)'))}{inp('pipValue', L('ارزش هر پیپ برای یک لات', 'Pip value per lot'), L('برای جفت‌ارزهای دلاری ۱۰ دلار', '$10 for USD-quoted pairs'))}</> : null}
          {tab === 'rr' ? <>{dirSwitch}{inp('entry', L('ورود', 'Entry'), undefined, true)}{inp('stop', L('حد ضرر', 'Stop loss'))}{inp('target', L('حد سود', 'Take profit'))}</> : null}
          {tab === 'pip' ? <>{dirSwitch}{inp('entry', L('ورود', 'Entry'), undefined, true)}{inp('exit', L('خروج', 'Exit'))}{inp('lots', L('لات', 'Lots'))}{inp('pipSize', L('اندازه‌ی پیپ', 'Pip size'), `${symbol}: ${pipSizeOf(symbol)}`)}{inp('pipValue', L('ارزش هر پیپ برای یک لات', 'Pip value per lot'))}</> : null}
          {tab === 'profit' ? <>{dirSwitch}{inp('entry', L('ورود', 'Entry'), undefined, true)}{inp('exit', L('خروج', 'Exit'))}{inp('lots', L('لات', 'Lots'))}{inp('contract', L('اندازه‌ی قرارداد', 'Contract size'), L('فارکس ۱۰۰٬۰۰۰ · طلا ۱۰۰ · نقره ۵۰۰۰', 'Forex 100,000 · gold 100 · silver 5,000'))}</> : null}
          {tab === 'crypto' ? <>{dirSwitch}{inp('entry', L('ورود', 'Entry'), undefined, true)}{inp('exit', L('خروج', 'Exit'))}{inp('qty', L('مقدار', 'Quantity'))}{inp('fee', L('کارمزد هر طرف (درصد)', 'Fee per side %'))}</> : null}
          {tab === 'compound' ? <>{inp('principal', L('اصل سرمایه', 'Principal'))}{inp('rate', L('بازده هر دوره (درصد)', 'Return per period %'))}{inp('periods', L('تعداد دوره', 'Periods'))}</> : null}
          {tab === 'drawdown' ? <>{inp('start', L('موجودی اولیه', 'Starting balance'))}{inp('loss', L('ضرر هر معامله (درصد)', 'Loss per trade %'))}{inp('losses', L('ضررهای پشت سر هم', 'Consecutive losses'))}</> : null}
        </form>
        <div className="card tools-out" aria-live="polite">
          {invalid ? <p className="muted fa">{invalid}</p> : (
            <dl className="kv big">{out.map(([k, v, tone]) => <div key={k} className="kv-row"><dt className="fa">{k}</dt><dd className={`num ${tone ?? ''}`}>{v}</dd></div>)}</dl>
          )}
          <p className="muted fa small">{L('محاسبه‌ها تخمینی‌اند؛ اسپرد، سوآپ و لغزش را در نظر نمی‌گیرند.', 'Estimates only: spread, swap and slippage are not included.')}</p>
        </div>
      </div>
    </div>
  );
}
