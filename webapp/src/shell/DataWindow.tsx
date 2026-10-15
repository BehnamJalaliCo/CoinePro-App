// TradingView's data window: the bar under the crosshair (the last bar otherwise) in the chart's
// time zone — the symbol, interval and market as its heading, open/high/low/close coloured by the
// bar's direction, the change against the previous close with its percent, volume and the last
// day's change — then every indicator's values with their colours, and the compared symbols.
import { useChartBus } from '../chart/chartBus';
import { usePrefs, useT } from '../state/prefs';
import { useMarket } from '../state/market';
import { useChartSettings } from '../state/chartSettings';
import { formatPct, formatPrice, formatVolume } from '../chart/format';
import { instrumentOf, marketOf } from '../data/symbols';
import { intervalLabel } from '../data/intervals';
import { chartTypeDef } from '../chart/chartTypes';
import { clockText, dateText } from '../chart/time';
import { useStudies } from '../state/studies';
import { extras, studyTitle, valueDigits } from '../chart/Legend';
import { indexAt } from '../chart/ChartView';
import { useL } from '../pages/kit';
import { Icon } from '../ui/Icon';
import '../chart/chart-x.css';

const MARKET: Record<string, [string, string]> = { crypto: ['کریپتو', 'Crypto'], forex: ['فارکس', 'Forex'], metals: ['فلزات', 'Metals'], indices: ['شاخص‌ها', 'Indices'], energy: ['انرژی', 'Energy'] };

export function DataWindow() {
  const t = useT();
  const L = useL();
  const symbol = usePrefs((s) => s.symbol);
  const interval = usePrefs((s) => s.interval);
  const chartType = usePrefs((s) => s.chartType);
  const lang = usePrefs((s) => s.lang);
  const timezone = usePrefs((s) => s.timezone);
  const hover = useChartBus((b) => b.hover);
  const shown = useChartBus((b) => b.shown);
  const bars = useChartBus((b) => b.bars);
  const studyRows = useChartBus((b) => b.studyRows);
  const compareRows = useChartBus((b) => b.compareRows);
  const studies = useStudies((s) => s.list);
  const cs = useChartSettings();
  const tk = useMarket((s) => s.tickers[symbol]);
  const inst = useMarket((s) => s.instruments[symbol]) ?? instrumentOf(symbol);
  const digits = cs.precision >= 0 ? cs.precision : inst.digits;
  const list = shown.length ? shown : bars;
  const bar = hover ?? list[list.length - 1];
  const i = bar ? indexAt(list, bar.t) : -1;
  const prev = i > 0 ? list[i - 1] : undefined;
  const base = prev?.c ?? bar?.o;
  const chg = bar && base != null ? bar.c - base : 0;
  const pct = bar && base ? (chg / base) * 100 : 0;
  const tone = bar && bar.c >= bar.o ? 'up' : 'down';
  const daily = ['1D', '1W', '1M'].includes(interval);
  const look = { hour12: cs.hour12, dayOfWeek: cs.dayOfWeek, dateFormat: cs.dateFormat };
  const fa = lang === 'fa';
  const def = chartTypeDef(chartType);
  const row = (k: string, v: React.ReactNode, cls = '', key = k) => (<div key={key} className="dw-row"><dt className={fa ? 'fa' : ''}>{k}</dt><dd className={`num ${cls}`}>{v}</dd></div>);
  return (
    <section className="panel-generic" dir={fa ? 'rtl' : 'ltr'}>
      <header className="panel-head"><span className="fa">{t('dataWindow')}</span></header>
      <div className="panel-body dw-body">
        {bar ? (
          <>
            <dl className="dw">
              {row(L('تاریخ', 'Date'), dateText(bar.t, daily ? 'UTC' : timezone, look))}
              {!daily ? row(L('زمان', 'Time'), clockText(bar.t, timezone, cs.hour12)) : null}
            </dl>
            <div className="dw-sec">
              <div className="dw-head"><Icon name={def.icon} size="sm" /><span className="sym">{symbol}</span><span className="muted num"> · {intervalLabel(interval)} · </span><span className={`muted${fa ? ' fa' : ''}`}>{MARKET[marketOf(symbol)]?.[fa ? 0 : 1]}</span></div>
              <dl className="dw">
                {row(L('باز شدن', 'Open'), formatPrice(bar.o, digits), tone)}
                {row(L('سقف', 'High'), formatPrice(bar.h, digits), tone)}
                {row(L('کف', 'Low'), formatPrice(bar.l, digits), tone)}
                {row(L('پایانی', 'Close'), formatPrice(bar.c, digits), tone)}
                {row(L('تغییر', 'Change'), `${chg >= 0 ? '+' : '−'}${formatPrice(Math.abs(chg), digits)} (${formatPct(pct)})`, chg >= 0 ? 'up' : 'down')}
                {row(L('حجم', 'Vol'), formatVolume(bar.v), tone)}
                {tk ? row(L('تغییر روز گذشته', 'Last day change'), `${tk.change >= 0 ? '+' : '−'}${formatPrice(Math.abs(tk.change), digits)} (${formatPct(tk.changePct)})`, tk.change >= 0 ? 'up' : 'down') : null}
              </dl>
            </div>
            {studyRows.map((r) => {
              const st = studies.find((s) => s.uid === r.uid);
              const x = extras(r);
              const title = x.name != null ? { name: x.name, inputs: x.args ?? '' } : st ? studyTitle(st) : { name: r.label, inputs: '' };
              return (
                <div key={r.uid} className={`dw-sec${r.hidden ? ' off' : ''}`} data-uid={r.uid}>
                  <div className="dw-head"><span className="dw-swatch" style={{ background: r.colors.find(Boolean) ?? 'var(--text-3)' }} /><span className="num">{title.name}</span>{title.inputs ? <span className="muted num"> {title.inputs}</span> : null}</div>
                  <dl className="dw">
                    {r.values.map((v, k) => row(
                      x.plotNames?.[k] || (r.values.length > 1 ? `${title.name} ${k + 1}` : title.name),
                      <><i className="dw-swatch" style={{ background: r.colors[k] }} />{r.hidden || v == null ? '∅' : formatPrice(v, valueDigits(r, v, digits))}</>,
                      '', `${r.uid}-${k}`,
                    ))}
                  </dl>
                </div>
              );
            })}
            {compareRows.length ? (
              <div className="dw-sec">
                <div className="dw-head"><span className={fa ? 'fa' : ''}>{L('مقایسه', 'Compare')}</span></div>
                <dl className="dw">
                  {compareRows.map((c) => row(c.symbol,
                    <><i className="dw-swatch" style={{ background: c.color }} />{c.value != null ? formatPrice(c.value, Math.min(8, Math.max(2, digits))) : '—'}{c.change != null ? <span className={c.change >= 0 ? 'up' : 'down'}> {formatPct(c.change)}</span> : null}</>,
                    c.hidden ? 'off' : '', `cmp-${c.symbol}`))}
                </dl>
              </div>
            ) : null}
          </>
        ) : <div className="empty fa">{t('loading')}…</div>}
      </div>
    </section>
  );
}
