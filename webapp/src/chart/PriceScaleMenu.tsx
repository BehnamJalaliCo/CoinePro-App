// TradingView's right-click menu on the price axis: auto-scale, lock price to bar ratio, scale the
// price only, invert; the four scale modes (Alt+P percent, Alt+L logarithmic); the side the scale
// sits on; the Labels and Lines submenus; the «+» button; More settings.
import { usePrefs } from '../state/prefs';
import { useChartSettings, showsLine, showsValue, type LineVis } from '../state/chartSettings';
import { useChartBus, type ScaleMode } from './chartBus';
import { useL } from '../pages/kit';
import { MI, MSep, MSub, PopMenu } from './menuKit';
import { openChartSettings } from '../shell/SettingsDialog';

/** A line's visibility with its axis label turned on or off (the line itself as it was). */
export function withLabel(v: LineVis, on: boolean): LineVis {
  const line = showsLine(v);
  return on ? (line ? 'value-line' : 'value') : (line ? 'line' : 'hidden');
}
/** A line's visibility with the line turned on or off (the label as it was). */
export function withLine(v: LineVis, on: boolean): LineVis {
  const label = showsValue(v);
  return on ? (label ? 'value-line' : 'line') : (label ? 'value' : 'hidden');
}

/** The current price per bar width: the lock's ratio, as TradingView shows it beside the item. */
export function priceToBarRatio(): number | null {
  const { api, series } = useChartBus.getState();
  if (!api || !series) return null;
  const h = api.panes()[0]?.getHeight() ?? 0;
  const a = series.coordinateToPrice(0); const b = series.coordinateToPrice(h);
  if (!h || typeof a !== 'number' || typeof b !== 'number') return null;
  return (Math.abs(a - b) / h) * api.timeScale().options().barSpacing;
}

export function PriceScaleMenu({ at, onClose }: { at: { x: number; y: number }; onClose(): void }) {
  const L = useL();
  const cs = useChartSettings();
  const bus = useChartBus();
  const lang = usePrefs((s) => s.lang);
  const ratio = cs.lockRatio ? cs.lockRatioValue : priceToBarRatio();
  const mode = (m: ScaleMode) => () => bus.setScale(m);
  const vis = (k: 'lastPrice' | 'prevDayClose' | 'highLow' | 'bidAsk' | 'avgClose' | 'pdhl', part: 'label' | 'line') => ({
    checked: part === 'label' ? showsValue(cs[k]) : showsLine(cs[k]),
    onClick: () => cs.set({ [k]: part === 'label' ? withLabel(cs[k], !showsValue(cs[k])) : withLine(cs[k], !showsLine(cs[k])) }),
  });
  return (
    <PopMenu x={at.x} y={at.y} anchorRight={lang !== 'fa'} onClose={onClose} className="price-menu" label={L('مقیاس قیمت', 'Price scale')}>
      <MI act="ps-auto" checked={bus.autoScale} label={L('خودکار (اندازه‌ی داده‌ها)', 'Auto (fits data to screen)')} onClick={() => bus.setAutoScale(!bus.autoScale)} />
      <MI act="ps-lock" checked={cs.lockRatio} label={L('قفل نسبت قیمت به میله', 'Lock price to bar ratio')} hint={ratio != null ? ratio.toFixed(4) : undefined}
        onClick={() => { const r = priceToBarRatio(); cs.set(cs.lockRatio ? { lockRatio: false } : { lockRatio: true, lockRatioValue: r ?? cs.lockRatioValue }); }} />
      <MI act="ps-priceonly" checked={cs.scalePriceOnly} label={L('مقیاس فقط برای قیمت', 'Scale price chart only')} onClick={() => cs.set({ scalePriceOnly: !cs.scalePriceOnly })} />
      <MI act="ps-invert" checked={bus.invert} label={L('وارونه‌کردن مقیاس', 'Invert scale')} hint="Alt+I" onClick={() => bus.setInvert(!bus.invert)} />
      <MSep />
      <MI act="ps-regular" checked={bus.scale === 'normal'} label={L('عادی', 'Regular')} onClick={mode('normal')} />
      <MI act="ps-percent" checked={bus.scale === 'percent'} label={L('درصدی', 'Percent')} hint="Alt+P" onClick={mode('percent')} />
      <MI act="ps-indexed" checked={bus.scale === 'indexed'} label={L('پایه‌ی ۱۰۰', 'Indexed to 100')} onClick={mode('indexed')} />
      <MI act="ps-log" checked={bus.scale === 'log'} label={L('لگاریتمی', 'Logarithmic')} hint="Alt+L" onClick={mode('log')} />
      <MSep />
      <MI act="ps-side" label={cs.scalePlacement === 'left' ? L('بردن مقیاس به راست', 'Move scale to right') : L('بردن مقیاس به چپ', 'Move scale to left')}
        onClick={() => cs.set({ scalePlacement: cs.scalePlacement === 'left' ? 'right' : 'left' })} />
      <MSep />
      <MSub act="ps-labels" label={L('برچسب‌ها', 'Labels')}>
        <MI act="lbl-name" checked={cs.symbolNameLabel} keep label={L('برچسب نام نماد', 'Symbol name label')} onClick={() => cs.set({ symbolNameLabel: !cs.symbolNameLabel })} />
        <MI act="lbl-last" keep label={L('برچسب آخرین قیمت', 'Symbol last price label')} {...vis('lastPrice', 'label')} />
        <MI act="lbl-prevclose" keep label={L('برچسب پایانی روز گذشته', 'Symbol previous day close price label')} {...vis('prevDayClose', 'label')} />
        <MI act="lbl-pdhl" keep label={L('برچسب سقف و کف روز گذشته', 'Previous day high and low labels')} {...vis('pdhl', 'label')} />
        <MI act="lbl-highlow" keep label={L('برچسب سقف و کف دیده‌شده', 'High and low price labels')} {...vis('highLow', 'label')} />
        <MI act="lbl-bidask" keep label={L('برچسب خرید و فروش', 'Bid and ask labels')} {...vis('bidAsk', 'label')} />
        <MI act="lbl-avg" keep label={L('برچسب میانگین پایانی', 'Average close price label')} {...vis('avgClose', 'label')} />
        <MI act="lbl-ind-name" checked={cs.indicatorLabels === 'name-value'} keep label={L('برچسب نام اندیکاتورها', 'Indicators and financials name labels')}
          onClick={() => cs.set({ indicatorLabels: cs.indicatorLabels === 'name-value' ? 'value' : 'name-value' })} />
        <MI act="lbl-ind-value" checked={cs.indicatorLabels !== 'hidden'} keep label={L('برچسب مقدار اندیکاتورها', 'Indicators and financials value labels')}
          onClick={() => cs.set({ indicatorLabels: cs.indicatorLabels === 'hidden' ? 'value' : 'hidden' })} />
        <MI act="lbl-currency" checked={cs.currencyLabel !== 'hidden'} keep label={L('برچسب ارز', 'Currency and unit label')} onClick={() => cs.set({ currencyLabel: cs.currencyLabel === 'hidden' ? 'always' : 'hidden' })} />
        <MI act="lbl-countdown" checked={cs.countdown} keep label={L('شمارش معکوس تا بسته شدن میله', 'Countdown to bar close')} onClick={() => cs.set({ countdown: !cs.countdown })} />
        <MSep />
        <MI act="lbl-overlap" checked={cs.noOverlap} keep label={L('برچسب‌ها روی هم نیفتند', 'No overlapping labels')} onClick={() => cs.set({ noOverlap: !cs.noOverlap })} />
      </MSub>
      <MSub act="ps-lines" label={L('خطوط', 'Lines')}>
        <MI act="line-last" keep label={L('خط قیمت', 'Price line')} {...vis('lastPrice', 'line')} />
        <MI act="line-prevclose" keep label={L('خط پایانی روز گذشته', 'Previous day close price line')} {...vis('prevDayClose', 'line')} />
        <MI act="line-pdhl" keep label={L('خطوط سقف و کف روز گذشته', 'Previous day high and low lines')} {...vis('pdhl', 'line')} />
        <MI act="line-highlow" keep label={L('خطوط سقف و کف دیده‌شده', 'High and low price lines')} {...vis('highLow', 'line')} />
        <MI act="line-bidask" keep label={L('خطوط خرید و فروش', 'Bid and ask lines')} {...vis('bidAsk', 'line')} />
        <MI act="line-avg" keep label={L('خط میانگین پایانی', 'Average close price line')} {...vis('avgClose', 'line')} />
        <MI act="line-hhll" checked={cs.hhll} keep label={L('نشانه‌ی بالاترین سقف و پایین‌ترین کف', 'Highest high and lowest low marks')} onClick={() => cs.set({ hhll: !cs.hhll })} />
      </MSub>
      <MI act="ps-plus" checked={cs.plusButton} label={L('دکمه‌ی +', 'Plus button')} onClick={() => cs.set({ plusButton: !cs.plusButton })} />
      <MSep />
      <MI icon="ph-gear" act="ps-settings" label={L('تنظیمات بیشتر…', 'More settings…')} onClick={() => openChartSettings('scales')} />
    </PopMenu>
  );
}
