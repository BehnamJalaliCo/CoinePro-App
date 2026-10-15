// The chart's right-click menu, TradingView's and the old Pro-Chart's items together: the symbol at
// the top, reset view, copy and paste, an alert, a paper order, a horizontal or vertical line, a
// note or a position at the point clicked, indicators, the drawings switches, the scale switches,
// what is shown on the chart, the panels, the picture and the data, full screen and Settings.
import { usePrefs } from '../state/prefs';
import { useStudies, VOLUME } from '../state/studies';
import { useDrawing } from '../state/drawing';
import { formatPrice } from './format';
import { digitsOf, useMarket } from '../state/market';
import { copyChartImage, exportBarsCsv, offerOrderTicket, addHorizontalLine, addPosition, addTextNote, addVerticalLine, openIndicators, resetChartView } from './actions';
import { useChartSettings } from '../state/chartSettings';
import { usePage } from '../state/page';
import { usePanels } from '../state/panels';
import { usePaper } from '../state/paper';
import { useChartBus } from './chartBus';
import { intervalLabel } from '../data/intervals';
import { useL } from '../pages/kit';
import { useT } from '../state/prefs';
import { faNum } from '../i18n/time';
import { MI, MSep, MSub, MTitle, PopMenu } from './menuKit';
import { openChartSettings } from '../shell/SettingsDialog';
import type { DrawingObject } from '../engine/drawings.js';

/** What the drawings area offers for pasting: its drawing clipboard (`useDrawing().clip`) and the
 *  layer's `pasteMany`, when the drawings area has them. */
function clipboardOf(): { paste(): void } | null {
  const layer = useChartBus.getState().drawingLayer as unknown as { pasteMany?(list: DrawingObject[]): number[]; canUndo(): boolean; canRedo(): boolean; getDrawings(): unknown[] } | null;
  const clip = (useDrawing.getState() as unknown as { clip?: DrawingObject[] | null }).clip;
  if (!layer?.pasteMany || !clip?.length) return null;
  return {
    paste: () => {
      layer.pasteMany!(clip);
      useChartBus.getState().bumpDrawings(layer.canUndo(), layer.canRedo(), layer.getDrawings().length);
    },
  };
}

export function ChartContextMenu({ at, onClose, onAlertAt }: { at: { x: number; y: number; price: number | null; time: number | null }; onClose(): void; onAlertAt?(price: number | null): void }) {
  const t = useT();
  const L = useL();
  const symbol = usePrefs((s) => s.symbol);
  const interval = usePrefs((s) => s.interval);
  const fa = usePrefs((s) => s.lang) === 'fa';
  const bus = useChartBus();
  const settings = useChartSettings();
  const studies = useStudies((s) => s.list).filter((x) => x.key !== VOLUME);
  const drawing = useDrawing();
  const panels = usePanels();
  const digits = settings.precision >= 0 ? settings.precision : digitsOf(symbol);
  const price = at.price != null ? Number(at.price.toFixed(digits)) : null;
  const label = price != null ? formatPrice(price, digits) : '';
  const clip = clipboardOf();
  const railApi = useDrawing.getState() as unknown as { railHidden?: boolean; setRailHidden?(on: boolean): void };
  const last = useMarket.getState().tickers[symbol]?.last ?? price ?? 0;
  const qty = price ? Number((1000 / price).toPrecision(2)) : 0;
  const order = (side: 'long' | 'short') => {
    if (price == null) return;
    if (offerOrderTicket({ symbol, side: side === 'long' ? 'buy' : 'sell', price, source: 'context-menu', ...(price !== last ? { type: 'limit' as const } : {}) })) return;
    const type = (side === 'long') === (price < last) ? 'limit' : 'stop';
    usePaper.getState().place({ symbol, side, type, qty, ...(type === 'limit' ? { limitPrice: price } : { stopPrice: price }) }, last);
    if (usePanels.getState().bottom !== 'paper') usePanels.getState().setBottom('paper');
  };
  const kind = (side: 'long' | 'short') => ((side === 'long') === ((price ?? 0) < last) ? L('محدود', 'limit') : L('توقف', 'stop'));
  const toggle = (k: 'sessionBreaks' | 'events' | 'news' | 'sessionsOn' | 'autoDivergence' | 'autoHarmonic' | 'autoGaps' | 'volumeProfile') => () => settings.set({ [k]: !settings[k] });
  const n = studies.length;
  const fullscreen = () => { if (document.fullscreenElement) void document.exitFullscreen(); else void document.documentElement.requestFullscreen?.(); };

  return (
    <PopMenu x={at.x} y={at.y} onClose={onClose} label={symbol}>
      <MTitle><bdi dir="ltr"><span className="sym">{symbol}</span><span className="num muted"> · {intervalLabel(interval)}</span></bdi></MTitle>
      <MI icon="ph-arrow-counter-clockwise" act="reset" label={t('contextReset')} onClick={() => resetChartView(settings.rightOffset)} />
      <MSep />
      {price != null ? <MI icon="ph-copy" act="copy-price" label={<>{t('contextCopy')} <span className="num">{label}</span></>} onClick={() => { void navigator.clipboard?.writeText(label.replace(/,/g, '')); }} /> : null}
      {clip ? <MI icon="ph-clipboard" act="paste" label={L('چسباندن', 'Paste')} hint="Ctrl+V" onClick={clip.paste} /> : null}
      {price != null ? (
        <>
          <MSep />
          <MI icon="ph-bell" act="alert" label={<>{t('contextAlert')} <span className="num muted">{label}</span></>} hint="Alt+A" onClick={() => onAlertAt?.(price)} />
          <MI icon="ph-arrow-down" act="sell" label={<>{L('فروش', 'Sell')} <span className="num">{qty}</span> <span className="sym">{symbol}</span> @ <span className="num">{label}</span> {kind('short')}</>} onClick={() => order('short')} />
          <MI icon="ph-arrow-up" act="buy" label={<>{L('خرید', 'Buy')} <span className="num">{qty}</span> <span className="sym">{symbol}</span> @ <span className="num">{label}</span> {kind('long')}</>} onClick={() => order('long')} />
          <MSep />
          <MI icon="tool_hline" act="hline" label={<>{t('contextHline')} <span className="num muted">{label}</span></>} onClick={() => addHorizontalLine(price)} />
          <MI icon="tool_vline" act="vline" label={L('خط عمودی در این زمان', 'Add vertical line here')} onClick={() => addVerticalLine(at.time)} />
          <MI icon="ph-note" act="note" label={<>{L('یادداشت متنی در', 'Add text note at')} <span className="num muted">{label}</span></>} onClick={() => addTextNote(price, at.time)} />
          <MI icon="tool_longshort" act="long" label={L('پوزیشن خرید از اینجا', 'Long position from here')} onClick={() => addPosition('long', price, at.time)} />
          <MI icon="tool_short" act="short" label={L('پوزیشن فروش از اینجا', 'Short position from here')} onClick={() => addPosition('short', price, at.time)} />
        </>
      ) : null}
      <MSep />
      <MI icon="ph-chart-line-up" act="add-indicator" label={L('افزودن اندیکاتور…', 'Add indicator…')} hint="/" onClick={openIndicators} />
      <MI icon="ph-arrows-down-up" act="invert" checked={bus.invert} label={L('وارونه‌کردن مقیاس', 'Invert scale')} hint="Alt+I" onClick={() => bus.setInvert(!bus.invert)} />
      <MI icon="ph-percent" act="indexed" checked={bus.scale === 'indexed'} label={L('پایه‌ی ۱۰۰', 'Indexed to 100')} onClick={() => bus.setScale(bus.scale === 'indexed' ? 'normal' : 'indexed')} />
      <MSub icon="ph-eye" act="show" label={L('نمایش روی چارت', 'Show on chart')}>
        <MI act="prevclose" checked={settings.prevDayClose !== 'hidden'} label={L('خط پایانی روز گذشته', 'Previous close line')} onClick={() => settings.set({ prevDayClose: settings.prevDayClose === 'hidden' ? 'value-line' : 'hidden' })} />
        <MI act="sessionbreaks" checked={settings.sessionBreaks} label={L('جداکننده‌ی روزها', 'Session breaks')} onClick={toggle('sessionBreaks')} />
        <MI act="sessions" checked={settings.sessionsOn} label={L('جلسه‌های معاملاتی', 'Trading sessions')} onClick={toggle('sessionsOn')} />
        <MI act="events" checked={settings.events} label={L('رویدادهای اقتصادی', 'Economic events')} onClick={toggle('events')} />
        <MI act="news" checked={settings.news} label={L('آخرین خبرها', 'Latest news')} onClick={toggle('news')} />
        <MI act="vp" checked={settings.volumeProfile} label={L('پروفایل حجم', 'Volume profile')} onClick={toggle('volumeProfile')} />
        <MSep />
        <MI act="auto-div" checked={settings.autoDivergence} label={L('واگرایی‌های RSI (خودکار)', 'Auto: RSI divergences')} onClick={toggle('autoDivergence')} />
        <MI act="auto-harm" checked={settings.autoHarmonic} label={L('الگوهای هارمونیک (خودکار)', 'Auto: harmonic patterns')} onClick={toggle('autoHarmonic')} />
        <MI act="auto-gaps" checked={settings.autoGaps} label={L('گپ‌های قیمت (خودکار)', 'Auto: price gaps')} onClick={toggle('autoGaps')} />
      </MSub>
      <MSep />
      <MI icon={drawing.hidden ? 'ph-eye' : 'ph-eye-slash'} act="hide-drawings" label={drawing.hidden ? L('نمایش همه‌ی ترسیم‌ها', 'Show all drawings') : L('پنهان کردن همه‌ی ترسیم‌ها', 'Hide all drawings')} hint="Ctrl+Alt+H" onClick={() => drawing.setHidden(!drawing.hidden)} />
      {typeof railApi.setRailHidden === 'function' ? (
        <MI icon="ph-sidebar-simple" act="rail" checked={!railApi.railHidden} label={L('نوار ابزار ترسیم', 'Drawings toolbar')} onClick={() => railApi.setRailHidden!(!railApi.railHidden)} />
      ) : null}
      <MSub icon="ph-dots-three" act="more-tools" label={L('بیشتر', 'More')}>
        <MI icon="ph-tree-structure" act="objects" checked={panels.open === 'objects'} label={L('درخت اشیا', 'Object tree')} onClick={() => panels.toggle('objects')} />
        <MI icon="ph-rows" act="data-window" checked={panels.open === 'data'} label={L('پنجره‌ی داده', 'Data window')} onClick={() => panels.toggle('data')} />
        <MSep />
        <MI icon="ph-camera" act="copy-image" label={L('کپی تصویر چارت', 'Copy chart image')} onClick={() => { void copyChartImage(); }} />
        <MI icon="ph-download-simple" act="csv" label={L('خروجی داده‌ی چارت (CSV)', 'Export chart data (CSV)')} onClick={exportBarsCsv} />
        <MI icon="ph-keyboard" act="shortcuts" label={L('میان‌برهای صفحه‌کلید', 'Keyboard shortcuts')} hint="?" onClick={() => usePage.getState().open('help', 'shortcuts')} />
        <MI icon="ph-arrows-out" act="fullscreen" label={L('تمام‌صفحه', 'Fullscreen')} hint="Shift+F" onClick={fullscreen} />
      </MSub>
      <MSep />
      <MI icon="trash2" act="remove-drawings" danger label={t('contextRemoveDrawings')} onClick={drawing.clearAll} />
      <MI icon="ph-trash" act="remove-indicators" danger disabled={!n} label={n ? (fa ? `حذف ${faNum(n)} اندیکاتور` : `Remove ${n} indicator${n === 1 ? '' : 's'}`) : t('contextRemoveStudies')} onClick={() => useStudies.getState().clear()} />
      <MSep />
      <MI icon="ph-gear" act="settings" label={L('تنظیمات…', 'Settings…')} onClick={() => openChartSettings('symbol')} />
    </PopMenu>
  );
}
