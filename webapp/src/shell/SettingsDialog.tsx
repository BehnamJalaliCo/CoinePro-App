// TradingView's chart «Settings», tab for tab: Symbol, Status line, Scales and lines, Canvas,
// Trading, Alerts, Events. Every change shows on the chart at once; Cancel (or Escape) puts back
// what was there when the dialog opened; Template saves, applies and deletes named sets.
import { useEffect, useRef, useState, type ReactNode } from 'react';
import { DEFAULT_SETTINGS, SESSION_IDS, pickSettings, useChartSettings, type ChartSettings, type LineVis, type SessionId, type ShowWhen } from '../state/chartSettings';
import { usePrefs, isDark, type Theme } from '../state/prefs';
import { Icon } from '../ui/Icon';
import { useChartBus, type ScaleMode } from '../chart/chartBus';
import { useL } from '../pages/kit';
import { TIMEZONES } from '../engine/scales_crosshair.js';
import { ChartTypeFields } from '../chart/ChartTypeParams';
import { PARAM_TYPES, chartTypeDef } from '../chart/chartTypes';
import { DATE_FORMATS } from '../chart/time';
import { PopMenu, MI, MSep } from '../chart/menuKit';
import { priceToBarRatio } from '../chart/PriceScaleMenu';
import '../chart/chart-x.css';

// Candle colour pairs: TradingView's, the reversed pair some markets read, colour-blind and monochrome.
const PRESETS = [
  { name: 'Classic', fa: 'کلاسیک', up: '#089981', down: '#F23645' },
  { name: 'Reversed', fa: 'وارونه', up: '#F23645', down: '#089981' },
  { name: 'Colour-blind', fa: 'برای کوررنگی', up: '#2962FF', down: '#FF9800' },
  { name: 'Monochrome', fa: 'تک‌رنگ', up: '#787B86', down: '#131722' },
  { name: 'Vivid', fa: 'زنده', up: '#26A69A', down: '#EF5350' },
];

export type SettingsTab = 'symbol' | 'status' | 'scales' | 'canvas' | 'trading' | 'alerts' | 'events';
const TABS: SettingsTab[] = ['symbol', 'status', 'scales', 'canvas', 'trading', 'alerts', 'events'];

/** The theme's own colours behind every '' (follow the theme) colour setting. */
export function themeColours(dark: boolean) {
  return dark
    ? { bg: '#0F0F0F', grid: '#1F1F1F', text: '#B8B8B8', line: '#2E2E2E', cross: '#9C9C9C', watermark: '#FFFFFF', sessionBreak: '#4A4A4A' }
    : { bg: '#FFFFFF', grid: '#F0F0F0', text: '#0F0F0F', line: '#EBEBEB', cross: '#9C9C9C', watermark: '#131722', sessionBreak: '#B2B5BE' };
}

/** A colour as #rrggbb for the colour picker (rgba keeps its hue; the alpha is set apart). */
export function toHex(c: string, fallback = '#000000'): string {
  if (/^#[0-9a-f]{6}$/i.test(c)) return c.toLowerCase();
  const m = /^rgba?\((\d+)\s*,\s*(\d+)\s*,\s*(\d+)/i.exec(c);
  if (m) return `#${[m[1], m[2], m[3]].map((x) => Number(x).toString(16).padStart(2, '0')).join('')}`;
  return fallback;
}
export function alphaOf(c: string): number {
  const m = /^rgba\([^)]*,\s*([\d.]+)\s*\)$/i.exec(c);
  return m ? Number(m[1]) : 1;
}
export function withAlpha(hex: string, a: number): string {
  const m = /^#([0-9a-f]{6})$/i.exec(hex);
  if (!m) return hex;
  const n = parseInt(m[1]!, 16);
  return a >= 1 ? hex : `rgba(${n >> 16},${(n >> 8) & 255},${n & 255},${Number(a.toFixed(2))})`;
}

/** Opens Chart Settings on a tab from anywhere (contract 3). */
export function openChartSettings(tab: SettingsTab = 'symbol'): void {
  window.dispatchEvent(new CustomEvent('pc:settings', { detail: { tab } }));
}

/** Listens for `pc:settings` and shows the dialog on the asked tab. */
export function SettingsHost() {
  const [tab, setTab] = useState<SettingsTab | null>(null);
  useEffect(() => {
    const on = (e: Event) => {
      const t = (e as CustomEvent<{ tab?: string }>).detail?.tab;
      setTab(TABS.includes(t as SettingsTab) ? (t as SettingsTab) : 'symbol');
    };
    window.addEventListener('pc:settings', on);
    return () => window.removeEventListener('pc:settings', on);
  }, []);
  return tab ? <SettingsDialog key={tab} initialTab={tab} onClose={() => setTab(null)} /> : null;
}

// ── Rows ─────────────────────────────────────────────────────────────────────────────────────────
type Patch = (p: Partial<ChartSettings>) => void;

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="cs-section">
      <h3 className="cs-sec-title">{title}</h3>
      {children}
    </section>
  );
}

function Row({ label, children, hint, indent, check }: { label: ReactNode; children?: ReactNode; hint?: string; indent?: boolean; check?: { on: boolean; set(v: boolean): void; act?: string } }) {
  return (
    <div className={`cs-row${indent ? ' indent' : ''}`}>
      <label className="cs-label">
        {check ? <input type="checkbox" checked={check.on} data-k={check.act} onChange={(e) => check.set(e.target.checked)} /> : null}
        <span>{label}</span>
      </label>
      {children ? <div className="cs-ctl">{children}</div> : null}
      {hint ? <p className="cs-hint">{hint}</p> : null}
    </div>
  );
}

function Sel<T extends string | number>({ value, options, onChange, act, wide }: { value: T; options: [T, string][]; onChange(v: T): void; act?: string; wide?: boolean }) {
  return (
    <select className={`cs-select${wide ? ' wide' : ''}`} data-k={act} value={String(value)} onChange={(e) => {
      const raw = e.target.value;
      const hit = options.find(([v]) => String(v) === raw);
      if (hit) onChange(hit[0]);
    }}>
      {options.map(([v, l]) => <option key={String(v)} value={String(v)}>{l}</option>)}
    </select>
  );
}

/** A colour swatch; '' means «follow the theme» and shows the theme's colour, with a reset when changed. */
function Swatch({ value, fallback, onChange, title, alpha, act }: { value: string; fallback: string; onChange(v: string): void; title: string; alpha?: boolean; act?: string }) {
  const L = useL();
  const shown = value || fallback;
  return (
    <span className="cs-swatch-wrap">
      <label className="cs-swatch" title={title} style={{ ['--sw' as string]: shown }}>
        <i />
        <input type="color" aria-label={title} data-k={act} value={toHex(shown)} onChange={(e) => onChange(alpha ? withAlpha(e.target.value, alphaOf(shown)) : e.target.value)} />
      </label>
      {value && fallback !== '' && value !== fallback ? <button type="button" className="cs-reset" title={L('رنگ پوسته', 'Theme colour')} aria-label={L('رنگ پوسته', 'Theme colour')} onClick={() => onChange('')}><Icon name="ph-arrow-counter-clockwise" size="sm" /></button> : null}
    </span>
  );
}

function Num({ value, onChange, min, max, step = 1, suffix, act }: { value: number; onChange(v: number): void; min: number; max: number; step?: number; suffix?: string; act?: string }) {
  return (
    <span className="cs-num">
      <input type="number" className="num" data-k={act} value={value} min={min} max={max} step={step}
        onChange={(e) => { const n = Number(e.target.value); if (Number.isFinite(n)) onChange(Math.min(max, Math.max(min, n))); }} />
      {suffix ? <span className="muted">{suffix}</span> : null}
    </span>
  );
}

function Range({ value, onChange, min = 0, max = 100, act }: { value: number; onChange(v: number): void; min?: number; max?: number; act?: string }) {
  return (
    <span className="cs-range">
      <input type="range" min={min} max={max} value={value} data-k={act} onChange={(e) => onChange(Number(e.target.value))} />
      <span className="num muted">{value}%</span>
    </span>
  );
}

// ── The dialog ───────────────────────────────────────────────────────────────────────────────────
export function SettingsDialog({ onClose, initialTab = 'symbol' }: { onClose(): void; initialTab?: SettingsTab }) {
  const L = useL();
  const s = useChartSettings();
  const set: Patch = s.set;
  const lang = usePrefs((p) => p.lang);
  const theme = usePrefs((p) => p.theme);
  const chartType = usePrefs((p) => p.chartType);
  const timezone = usePrefs((p) => p.timezone);
  const bus = useChartBus();
  const [tab, setTab] = useState<SettingsTab>(initialTab);
  const [tplAt, setTplAt] = useState<{ x: number; y: number } | null>(null);
  const [tplName, setTplName] = useState('');
  const tplBtn = useRef<HTMLButtonElement>(null);
  const dark = isDark(theme);
  const tc = themeColours(dark);

  // What to put back on Cancel: the settings, the scale switches, the time zone and the theme.
  const snap = useRef<{ cs: ChartSettings; scale: ScaleMode; autoScale: boolean; invert: boolean; timezone: string; theme: Theme } | null>(null);
  if (!snap.current) {
    const b = useChartBus.getState(); const p = usePrefs.getState();
    snap.current = { cs: pickSettings(useChartSettings.getState()), scale: b.scale, autoScale: b.autoScale, invert: b.invert, timezone: p.timezone, theme: p.theme };
  }
  const cancel = () => {
    const x = snap.current!;
    useChartSettings.getState().set(x.cs);
    const b = useChartBus.getState();
    b.setScale(x.scale); b.setAutoScale(x.autoScale); b.setInvert(x.invert);
    const p = usePrefs.getState();
    if (p.timezone !== x.timezone) p.setTimezone(x.timezone);
    if (p.theme !== x.theme) p.setTheme(x.theme);
    onClose();
  };
  const cancelRef = useRef(cancel);
  cancelRef.current = cancel;
  useEffect(() => {
    const esc = (e: KeyboardEvent) => { if (e.key === 'Escape' && !document.querySelector('.cs-tpl')) { e.preventDefault(); cancelRef.current(); } };
    window.addEventListener('keydown', esc);
    return () => window.removeEventListener('keydown', esc);
  }, []);

  const tabs: { id: SettingsTab; label: string; icon: string }[] = [
    { id: 'symbol', label: L('نماد', 'Symbol'), icon: 'chart_candles' },
    { id: 'status', label: L('خط وضعیت', 'Status line'), icon: 'ph-text-align-left' },
    { id: 'scales', label: L('مقیاس‌ها و خطوط', 'Scales and lines'), icon: 'ph-ruler' },
    { id: 'canvas', label: L('بوم', 'Canvas'), icon: 'ph-paint-brush' },
    { id: 'trading', label: L('معامله', 'Trading'), icon: 'ph-chart-line-up' },
    { id: 'alerts', label: L('هشدارها', 'Alerts'), icon: 'ph-bell' },
    { id: 'events', label: L('رویدادها', 'Events'), icon: 'ph-calendar-blank' },
  ];
  const chk = (k: keyof ChartSettings) => ({ on: Boolean(s[k]), set: (v: boolean) => set({ [k]: v } as Partial<ChartSettings>), act: String(k) });
  const vis: [LineVis, string][] = [['value-line', L('مقدار و خط', 'Value, line')], ['value', L('مقدار', 'Value')], ['line', L('خط', 'Line')], ['hidden', L('پنهان', 'Hidden')]];
  const when: [ShowWhen, string][] = [['hover', L('با بردن نشانگر', 'Visible on mouse over')], ['always', L('همیشه', 'Always visible')], ['hidden', L('هرگز', 'Always invisible')]];
  const lineVis = (k: 'lastPrice' | 'prevDayClose' | 'highLow' | 'bidAsk' | 'avgClose' | 'pdhl') => <Sel act={k} value={s[k]} options={vis} onChange={(v) => set({ [k]: v } as Partial<ChartSettings>)} />;
  const typeName = lang === 'fa' ? chartTypeDef(chartType).fa : chartTypeDef(chartType).en;
  const candleLike = ['candles', 'hollow', 'heikin', 'volcandles', 'renko', 'range', 'linebreak'].includes(chartType);
  const lineLike = ['line', 'linemarkers', 'step', 'area', 'baseline'].includes(chartType);

  return (
    <div className="scrim cs-scrim" onMouseDown={(e) => { if (e.target === e.currentTarget) cancel(); }}>
      <div className="dialog chart-settings" role="dialog" aria-modal="true" aria-label={L('تنظیمات', 'Settings')} dir={lang === 'fa' ? 'rtl' : 'ltr'}>
        <header className="dialog-head">
          <h2>{L('تنظیمات', 'Settings')}</h2>
          <button className="ib" onClick={cancel} aria-label={L('بستن', 'Close')}><Icon name="ph-x" size="md" /></button>
        </header>
        <div className="cs-body">
          <nav className="cs-tabs" role="tablist">
            {tabs.map((x) => (
              <button key={x.id} role="tab" aria-selected={tab === x.id} data-tab={x.id} className={`cs-tab${tab === x.id ? ' on' : ''}`} onClick={() => setTab(x.id)}>
                <Icon name={x.icon} size="sm" /><span>{x.label}</span>
              </button>
            ))}
          </nav>
          <div className="cs-pane" role="tabpanel">
            {tab === 'symbol' ? (
              <>
                <Section title={typeName}>
                  {chartType !== 'highlow' && chartType !== 'columns' && chartType !== 'kagi' && chartType !== 'pnf' && !lineLike ? (
                    <Row label={L('رنگ میله‌ها بر پایه‌ی پایانی قبلی', 'Color bars based on previous close')} check={chk('colorByPrevClose')} />
                  ) : null}
                  {candleLike || chartType === 'bars' || chartType === 'highlow' || chartType === 'columns' || chartType === 'kagi' || chartType === 'pnf' ? (
                    <Row label={chartType === 'bars' || chartType === 'highlow' || chartType === 'columns' || chartType === 'kagi' || chartType === 'pnf' ? L('رنگ صعودی و نزولی', 'Up and down colours') : L('بدنه', 'Body')} check={candleLike ? chk('bodyVisible') : undefined}>
                      <Swatch act="up" value={s.up} fallback={s.up} title={L('صعودی', 'Up')} onChange={(v) => set({ up: v })} />
                      <Swatch act="down" value={s.down} fallback={s.down} title={L('نزولی', 'Down')} onChange={(v) => set({ down: v })} />
                    </Row>
                  ) : null}
                  {candleLike ? (
                    <>
                      <Row label={L('حاشیه‌ها', 'Borders')} check={chk('borderVisible')}>
                        <Swatch act="borderUp" value={s.borderUp} fallback={s.borderUp} title={L('صعودی', 'Up')} onChange={(v) => set({ borderUp: v })} />
                        <Swatch act="borderDown" value={s.borderDown} fallback={s.borderDown} title={L('نزولی', 'Down')} onChange={(v) => set({ borderDown: v })} />
                      </Row>
                      <Row label={L('فتیله', 'Wick')} check={chk('wickVisible')}>
                        <Swatch act="wickUp" value={s.wickUp} fallback={s.wickUp} title={L('صعودی', 'Up')} onChange={(v) => set({ wickUp: v })} />
                        <Swatch act="wickDown" value={s.wickDown} fallback={s.wickDown} title={L('نزولی', 'Down')} onChange={(v) => set({ wickDown: v })} />
                      </Row>
                      {chartType === 'candles' ? <Row label={L('بدنه‌ی صعودی توخالی', 'Hollow up bodies')} check={chk('hollow')} /> : null}
                    </>
                  ) : null}
                  {chartType === 'bars' ? <Row label={L('میله‌های باریک', 'Thin bars')} check={chk('thinBars')} /> : null}
                  {lineLike ? (
                    <Row label={L('خط', 'Line')}>
                      {chartType !== 'baseline' ? <Swatch act="lineColor" value={s.lineColor} fallback={s.lineColor} title={L('رنگ خط', 'Line colour')} onChange={(v) => set({ lineColor: v })} /> : (
                        <>
                          <Swatch act="up" value={s.up} fallback={s.up} title={L('بالای مبنا', 'Above the base')} onChange={(v) => set({ up: v })} />
                          <Swatch act="down" value={s.down} fallback={s.down} title={L('زیر مبنا', 'Below the base')} onChange={(v) => set({ down: v })} />
                        </>
                      )}
                      <Sel act="lineWidth" value={s.lineWidth} options={[[1, '1px'], [2, '2px'], [3, '3px'], [4, '4px']]} onChange={(v) => set({ lineWidth: v })} />
                    </Row>
                  ) : null}
                  {chartType === 'area' ? (
                    <Row label={L('پرشدگی', 'Fill')}>
                      <Swatch act="areaTop" alpha value={s.areaTop} fallback={s.areaTop} title={L('بالا', 'Top')} onChange={(v) => set({ areaTop: v })} />
                      <Swatch act="areaBottom" alpha value={s.areaBottom} fallback={s.areaBottom} title={L('پایین', 'Bottom')} onChange={(v) => set({ areaBottom: v })} />
                    </Row>
                  ) : null}
                  <Row label={L('رنگ‌های آماده', 'Colour presets')}>
                    <span className="swatches">{PRESETS.map((p) => (
                      <button key={p.name} type="button" className={`preset${s.up === p.up && s.down === p.down ? ' on' : ''}`} title={L(p.fa, p.name)} aria-label={L(p.fa, p.name)}
                        onClick={() => set({ up: p.up, down: p.down, borderUp: p.up, borderDown: p.down, wickUp: p.up, wickDown: p.down })}>
                        <i style={{ background: p.up }} /><i style={{ background: p.down }} />
                      </button>
                    ))}</span>
                  </Row>
                </Section>
                {PARAM_TYPES.includes(chartType) ? (
                  <Section title={L('ورودی‌های نوع چارت', 'Chart type inputs')}><div className="ctp-body in-settings"><ChartTypeFields type={chartType} /></div></Section>
                ) : null}
                <Section title={L('اصلاح داده', 'Data modification')}>
                  <Row label={L('دقت', 'Precision')}>
                    <Sel act="precision" value={s.precision} wide onChange={(v) => set({ precision: v })}
                      options={[[-1, L('پیش‌فرض', 'Default')], [0, '1'], [1, '0.1'], [2, '0.01'], [3, '0.001'], [4, '0.0001'], [5, '0.00001'], [6, '0.000001'], [7, '0.0000001'], [8, '0.00000001']]} />
                  </Row>
                  <Row label={L('منطقه‌ی زمانی', 'Timezone')}>
                    <Sel act="timezone" value={timezone} wide onChange={(v) => usePrefs.getState().setTimezone(v)}
                      options={[['exchange', L('بورس', 'Exchange')] as [string, string], ...TIMEZONES.map((z) => [z.id, lang === 'fa' ? z.label : z.id.split('/').pop()!.replace('_', ' ')] as [string, string])]} />
                  </Row>
                </Section>
              </>
            ) : null}

            {tab === 'status' ? (
              <>
                <Section title={L('نماد', 'Instrument')}>
                  <Row label={L('لوگو', 'Logo')} check={chk('showLogo')} />
                  <Row label={L('عنوان', 'Title')} check={chk('showTitle')}>
                    <Sel act="titleMode" value={s.titleMode} wide onChange={(v) => set({ titleMode: v })}
                      options={[['both', L('نماد و توضیح', 'Ticker and description')], ['description', L('توضیح', 'Description')], ['ticker', L('نماد', 'Ticker')]]} />
                  </Row>
                  <Row label={L('وضعیت باز بودن بازار', 'Open market status')} check={chk('showMarketStatus')} />
                  <Row label={L('مقادیر چارت', 'Chart values')} check={chk('showOhlc')} />
                  <Row label={L('تغییر میله', 'Bar change values')} check={chk('showChange')} />
                  <Row label={L('حجم', 'Volume')} check={chk('showVolume')} />
                  <Row label={L('تغییر روز گذشته', 'Last day change values')} check={chk('showLastDay')} />
                  <Row label={L('دکمه‌های خرید و فروش', 'Buy/sell buttons')} check={chk('tradeButtons')} hint={L('دکمه‌های خرید و فروش را روی چارت نشان می‌دهد', 'Displays buy and sell buttons directly on the chart')} />
                </Section>
                <Section title={L('اندیکاتورها', 'Indicators')}>
                  <Row label={L('عنوان‌ها', 'Titles')} check={chk('indTitles')} />
                  <Row label={L('ورودی‌ها', 'Inputs')} indent check={chk('indInputs')} />
                  <Row label={L('مقادیر', 'Values')} check={chk('indValues')} />
                  <Row label={L('پس‌زمینه', 'Background')} check={chk('legendBg')}>
                    <Range act="legendBgOpacity" value={s.legendBgOpacity} onChange={(v) => set({ legendBgOpacity: v })} />
                  </Row>
                </Section>
              </>
            ) : null}

            {tab === 'scales' ? (
              <>
                <Section title={L('مقیاس قیمت', 'Price scale')}>
                  <Row label={L('ارز و واحد', 'Currency and Unit')}><Sel act="currencyLabel" value={s.currencyLabel} wide options={when} onChange={(v) => set({ currencyLabel: v })} /></Row>
                  <Row label={L('حالت مقیاس', 'Scale mode')}>
                    <Sel act="scaleMode" value={bus.scale} wide onChange={(m) => bus.setScale(m)}
                      options={[['normal', L('عادی', 'Regular')], ['percent', L('درصدی', 'Percent')], ['indexed', L('پایه‌ی ۱۰۰', 'Indexed to 100')], ['log', L('لگاریتمی', 'Logarithmic')]] as [ScaleMode, string][]} />
                  </Row>
                  <Row label={L('مقیاس خودکار', 'Auto (fits data to screen)')} check={{ on: bus.autoScale, set: bus.setAutoScale, act: 'autoScale' }} />
                  <Row label={L('وارونه‌کردن مقیاس', 'Invert scale')} check={{ on: bus.invert, set: bus.setInvert, act: 'invert' }} />
                  <Row label={L('قفل نسبت قیمت به میله', 'Lock price to bar ratio')} check={{ on: s.lockRatio, act: 'lockRatio', set: (v) => set(v ? { lockRatio: true, lockRatioValue: s.lockRatioValue || priceToBarRatio() || 0 } : { lockRatio: false }) }}>
                    <Num act="lockRatioValue" value={Number((s.lockRatio || s.lockRatioValue ? s.lockRatioValue : priceToBarRatio() ?? 0).toFixed(4))} min={0} max={1e9} step={0.0001} onChange={(v) => set({ lockRatioValue: v })} />
                  </Row>
                  <Row label={L('مقیاس فقط برای قیمت', 'Scale price chart only')} check={chk('scalePriceOnly')} />
                  <Row label={L('جای مقیاس', 'Scales placement')}>
                    <Sel act="scalePlacement" value={s.scalePlacement} options={[['auto', L('خودکار', 'Auto')], ['left', L('چپ', 'Left')], ['right', L('راست', 'Right')]]} onChange={(v) => set({ scalePlacement: v })} />
                  </Row>
                </Section>
                <Section title={L('برچسب‌ها و خطوط قیمت', 'Price labels & lines')}>
                  <Row label={L('برچسب‌ها روی هم نیفتند', 'No overlapping labels')} check={chk('noOverlap')} />
                  <Row label={L('دکمه‌ی + روی محور', 'Plus button')} check={chk('plusButton')} />
                  <Row label={L('شمارش معکوس تا بسته شدن میله', 'Countdown to bar close')} check={chk('countdown')} />
                  <Row label={L('آخرین قیمت', 'Symbol')}>{lineVis('lastPrice')}</Row>
                  <Row label={L('برچسب نام نماد', 'Symbol name label')} check={chk('symbolNameLabel')} />
                  <Row label={L('پایانی روز گذشته', 'Previous day close')}>{lineVis('prevDayClose')}</Row>
                  <Row label={L('سقف و کف روز گذشته', 'Previous day high and low')}>{lineVis('pdhl')}</Row>
                  <Row label={L('سقف و کف بازه‌ی دیده‌شده', 'High and low')}>{lineVis('highLow')}</Row>
                  <Row label={L('میانگین پایانی بازه‌ی دیده‌شده', 'Average close')}>{lineVis('avgClose')}</Row>
                  <Row label={L('خرید و فروش (Bid/Ask)', 'Bid and ask')}>{lineVis('bidAsk')}</Row>
                  <Row label={L('اندیکاتورها', 'Indicators and financials')}>
                    <Sel act="indicatorLabels" value={s.indicatorLabels} wide onChange={(v) => set({ indicatorLabels: v })}
                      options={[['value', L('مقدار', 'Value')], ['name-value', L('نام و مقدار', 'Name and value')], ['hidden', L('پنهان', 'Hidden')]]} />
                  </Row>
                  <Row label={L('نشانه‌ی بالاترین سقف و پایین‌ترین کف', 'Highest high and lowest low marks')} check={chk('hhll')} />
                  <Row label={L('نمادهای مقایسه', 'Compared symbols')}>
                    <Sel act="compareMode" value={s.compareMode} wide onChange={(v) => set({ compareMode: v })}
                      options={[['own', L('هر کدام با مقیاس خودش', 'Each on its own scale')], ['percent', L('همه به درصد', 'All in percent')]]} />
                  </Row>
                </Section>
                <Section title={L('مقیاس زمان', 'Time scale')}>
                  <Row label={L('روز هفته روی برچسب‌ها', 'Day of week on labels')} check={chk('dayOfWeek')} />
                  <Row label={L('قالب تاریخ', 'Date format')}>
                    <Sel act="dateFormat" value={s.dateFormat} wide onChange={(v) => set({ dateFormat: v })} options={DATE_FORMATS.map((f) => [f, sampleDate(f)] as [ChartSettings['dateFormat'], string])} />
                  </Row>
                  <Row label={L('قالب ساعت', 'Time hours format')}>
                    <Sel act="hour12" value={s.hour12 ? '12' : '24'} options={[['24', L('۲۴ ساعته', '24-hours')], ['12', L('۱۲ ساعته', '12-hours')]]} onChange={(v) => set({ hour12: v === '12' })} />
                  </Row>
                </Section>
              </>
            ) : null}

            {tab === 'canvas' ? (
              <>
                <Section title={L('ظاهر پایه‌ی چارت', 'Chart basic styles')}>
                  <Row label={L('پوسته', 'Theme')}>
                    <Sel act="theme" value={theme} onChange={(v) => usePrefs.getState().setTheme(v)} options={[['system', L('همانند سیستم', 'System')], ['light', L('روشن', 'Light')], ['dark', L('تیره', 'Dark')]]} />
                  </Row>
                  <Row label={L('پس‌زمینه', 'Background')}>
                    <Sel act="bgType" value={s.bgType} onChange={(v) => set({ bgType: v })} options={[['solid', L('یکدست', 'Solid')], ['gradient', L('گرادیان', 'Gradient')]]} />
                    <Swatch act="bgColor" value={s.bgColor} fallback={tc.bg} title={L('رنگ', 'Colour')} onChange={(v) => set({ bgColor: v })} />
                    {s.bgType === 'gradient' ? <Swatch act="bgColor2" value={s.bgColor2} fallback={tc.bg} title={L('رنگ پایین', 'Bottom colour')} onChange={(v) => set({ bgColor2: v })} /> : null}
                  </Row>
                  <Row label={L('خطوط عمودی شبکه', 'Vertical grid lines')} check={chk('vertGrid')}>
                    <Swatch act="vertGridColor" value={s.vertGridColor} fallback={tc.grid} title={L('رنگ', 'Colour')} onChange={(v) => set({ vertGridColor: v })} />
                  </Row>
                  <Row label={L('خطوط افقی شبکه', 'Horizontal grid lines')} check={chk('horzGrid')}>
                    <Swatch act="horzGridColor" value={s.horzGridColor} fallback={tc.grid} title={L('رنگ', 'Colour')} onChange={(v) => set({ horzGridColor: v })} />
                  </Row>
                  <Row label={L('خط نشانگر', 'Crosshair')}>
                    <Swatch act="crossColor" value={s.crossColor} fallback={tc.cross} title={L('رنگ', 'Colour')} onChange={(v) => set({ crossColor: v })} />
                    <Sel act="crossWidth" value={s.crossWidth} options={[[1, '1px'], [2, '2px'], [3, '3px'], [4, '4px']]} onChange={(v) => set({ crossWidth: v })} />
                    <Sel act="crossStyle" value={s.crossStyle} onChange={(v) => set({ crossStyle: v })}
                      options={[['solid', L('یکسره', 'Solid')], ['dashed', L('خط‌چین', 'Dashed')], ['large-dashed', L('خط‌چین بلند', 'Large dashed')], ['dotted', L('نقطه‌چین', 'Dotted')], ['sparse-dotted', L('نقطه‌چین باز', 'Sparse dotted')]]} />
                  </Row>
                  <Row label={L('حالت نشانگر', 'Crosshair snapping')}>
                    <Sel act="crosshair" value={s.crosshair} onChange={(v) => set({ crosshair: v })} options={[['normal', L('آزاد', 'Normal')], ['magnet', L('آهنربا', 'Magnet')]]} />
                  </Row>
                  <Row label={L('واترمارک', 'Watermark')} check={chk('watermark')}>
                    <Swatch act="watermarkColor" value={s.watermarkColor} fallback={tc.watermark} title={L('رنگ', 'Colour')} onChange={(v) => set({ watermarkColor: v })} />
                    <Range act="watermarkOpacity" value={s.watermarkOpacity} onChange={(v) => set({ watermarkOpacity: v })} />
                  </Row>
                </Section>
                <Section title={L('مقیاس‌ها', 'Scales')}>
                  <Row label={L('متن', 'Text')}>
                    <Swatch act="scaleTextColor" value={s.scaleTextColor} fallback={tc.text} title={L('رنگ متن', 'Text colour')} onChange={(v) => set({ scaleTextColor: v })} />
                    <Sel act="scaleFontSize" value={s.scaleFontSize} options={[10, 11, 12, 13, 14, 16].map((n) => [n, String(n)] as [number, string])} onChange={(v) => set({ scaleFontSize: v })} />
                  </Row>
                  <Row label={L('خطوط', 'Lines')}>
                    <Swatch act="scaleLineColor" value={s.scaleLineColor} fallback={tc.line} title={L('رنگ خطوط', 'Lines colour')} onChange={(v) => set({ scaleLineColor: v })} />
                  </Row>
                </Section>
                <Section title={L('دکمه‌ها', 'Buttons')}>
                  <Row label={L('ناوبری', 'Navigation')}><Sel act="navButtons" value={s.navButtons} wide options={when} onChange={(v) => set({ navButtons: v })} /></Row>
                  <Row label={L('پنل', 'Pane')}><Sel act="paneButtons" value={s.paneButtons} wide options={when} onChange={(v) => set({ paneButtons: v })} /></Row>
                </Section>
                <Section title={L('حاشیه‌ها', 'Margins')}>
                  <Row label={L('بالا', 'Top')}><Num act="marginTop" value={s.marginTop} min={0} max={40} suffix="%" onChange={(v) => set({ marginTop: v })} /></Row>
                  <Row label={L('پایین', 'Bottom')}><Num act="marginBottom" value={s.marginBottom} min={0} max={40} suffix="%" onChange={(v) => set({ marginBottom: v })} /></Row>
                  <Row label={L('راست', 'Right')}><Num act="rightOffset" value={s.rightOffset} min={0} max={200} suffix={L('میله', 'bars')} onChange={(v) => set({ rightOffset: v })} /></Row>
                </Section>
                <Section title={L('تعامل', 'Interaction')}>
                  <Row label={L('جابه‌جایی و بزرگ‌نمایی با ماوس و لمس', 'Scroll and scale with the mouse and touch')} check={chk('scrollScale')} />
                </Section>
                <Section title={L('لایه‌های روی چارت', 'Overlays')}>
                  <Row label={L('میانگین متحرک حجم', 'Volume moving average')} check={chk('volumeMa')}>
                    <Num act="volumeMaLength" value={s.volumeMaLength} min={2} max={200} onChange={(v) => set({ volumeMaLength: v })} />
                  </Row>
                  <Row label={L('پروفایل حجم بازه‌ی دیده‌شده', 'Volume profile (visible range)')} check={chk('volumeProfile')}>
                    <Num act="vpRows" value={s.vpRows} min={6} max={120} suffix={L('ردیف', 'rows')} onChange={(v) => set({ vpRows: v })} />
                    <Num act="vpValueArea" value={s.vpValueArea} min={10} max={95} suffix="%" onChange={(v) => set({ vpValueArea: v })} />
                  </Row>
                  <Row label={L('واگرایی‌های RSI (خودکار)', 'Auto: RSI divergences')} check={chk('autoDivergence')} />
                  <Row label={L('الگوهای هارمونیک (خودکار)', 'Auto: harmonic patterns')} check={chk('autoHarmonic')} />
                  <Row label={L('گپ‌های قیمت (خودکار)', 'Auto: price gaps')} check={chk('autoGaps')} />
                </Section>
              </>
            ) : null}

            {tab === 'trading' ? (
              <>
                <Section title={L('عمومی', 'General')}>
                  <Row label={L('دکمه‌های خرید و فروش', 'Buy/sell buttons')} check={chk('tradeButtons')} hint={L('دکمه‌های خرید و فروش را روی چارت نشان می‌دهد', 'Displays buy and sell buttons directly on the chart')} />
                  <Row label={L('معامله با یک کلیک', 'One-click trading')} check={chk('instantOrders')} hint={L('سفارش بدون پرسش تأیید ثبت می‌شود', 'Places orders instantly, without a confirmation')} />
                </Section>
                <Section title={L('ظاهر', 'Appearance')}>
                  <Row label={L('پوزیشن‌ها', 'Positions')} check={chk('positionLines')} hint={L('ورود، حد ضرر و حد سود هر پوزیشن باز روی چارت', 'Each open position\'s entry, stop loss and take profit on the chart')} />
                  <Row label={L('سفارش‌ها', 'Orders')} check={chk('orderLines')} />
                  <Row label={L('نشانه‌های اجرا', 'Execution marks')} check={chk('executionMarks')} />
                </Section>
                <p className="cs-note">{L('معامله‌ها روی حساب آزمایشی این مرورگر انجام می‌شوند.', 'Trades go to this browser\'s paper-trading account.')}</p>
              </>
            ) : null}

            {tab === 'alerts' ? (
              <Section title={L('نمایش خطوط روی چارت', 'Chart line visibility')}>
                <Row label={L('خطوط هشدار', 'Alert lines')} check={chk('alertLines')}>
                  <Swatch act="alertLineColor" value={s.alertLineColor} fallback={DEFAULT_SETTINGS.alertLineColor} title={L('رنگ', 'Colour')} onChange={(v) => set({ alertLineColor: v || DEFAULT_SETTINGS.alertLineColor })} />
                </Row>
                <Row label={L('فقط هشدارهای فعال', 'Only active alerts')} check={chk('alertLinesActiveOnly')} />
              </Section>
            ) : null}

            {tab === 'events' ? (
              <>
                <Section title={L('رویدادها', 'Events')}>
                  <Row label={L('جداکننده‌ی روزها', 'Session breaks')} check={chk('sessionBreaks')}>
                    <Swatch act="sessionBreakColor" value={s.sessionBreakColor} fallback={tc.sessionBreak} title={L('رنگ', 'Colour')} onChange={(v) => set({ sessionBreakColor: v })} />
                    <Sel act="sessionBreakStyle" value={s.sessionBreakStyle} onChange={(v) => set({ sessionBreakStyle: v })} options={[['solid', L('یکسره', 'Solid')], ['dashed', L('خط‌چین', 'Dashed')], ['dotted', L('نقطه‌چین', 'Dotted')]]} />
                    <Sel act="sessionBreakWidth" value={s.sessionBreakWidth} options={[[1, '1px'], [2, '2px'], [3, '3px']]} onChange={(v) => set({ sessionBreakWidth: v })} />
                  </Row>
                  <Row label={L('رویدادهای اقتصادی', 'Economic events')} check={chk('events')}>
                    <Sel act="eventImportance" value={s.eventImportance} wide onChange={(v) => set({ eventImportance: v })}
                      options={[['high', L('فقط مهم', 'High importance')], ['medium', L('مهم و متوسط', 'Medium and high')], ['all', L('همه', 'All')]]} />
                  </Row>
                  <Row label={L('فقط رویدادهای آینده', 'Only future events')} indent check={chk('eventsFutureOnly')} />
                  <Row label={L('آخرین خبرها', 'Latest news')} check={chk('news')} />
                </Section>
                <Section title={L('جلسه‌های معاملاتی', 'Trading sessions')}>
                  <Row label={L('سایه‌ی جلسه‌ها', 'Session shading')} check={chk('sessionsOn')}>
                    <button type="button" className="btn btn-quiet sm" onClick={() => set({ sessions: [...SESSION_IDS], sessionsOn: true })}>{L('همه', 'All')}</button>
                    <button type="button" className="btn btn-quiet sm" onClick={() => set({ sessions: [] })}>{L('هیچ‌کدام', 'None')}</button>
                  </Row>
                  {SESSION_IDS.map((id) => (
                    <Row key={id} indent label={sessionName(id, lang === 'fa')} check={{ on: s.sessions.includes(id), act: `session-${id}`, set: (v) => set({ sessions: v ? [...s.sessions.filter((x) => x !== id), id] : s.sessions.filter((x) => x !== id) }) }} />
                  ))}
                  <Row indent label={L('هم‌پوشانی‌ها', 'Overlaps')} check={chk('sessionOverlaps')} />
                </Section>
              </>
            ) : null}
          </div>
        </div>
        <footer className="dialog-foot">
          <button ref={tplBtn} type="button" className="btn btn-quiet cs-tpl-btn" aria-haspopup="menu" aria-expanded={Boolean(tplAt)}
            onClick={() => { const r = tplBtn.current!.getBoundingClientRect(); setTplAt(tplAt ? null : { x: lang === 'fa' ? r.right : r.left, y: r.top - 4 }); }}>
            <span>{L('قالب', 'Template')}</span><Icon name="ph-caret-down" size="sm" />
          </button>
          <span className="ws-spacer" />
          <button type="button" className="btn btn-quiet" onClick={cancel}>{L('انصراف', 'Cancel')}</button>
          <button type="button" className="btn btn-primary" onClick={onClose}>{L('تأیید', 'Ok')}</button>
        </footer>
      </div>
      {tplAt ? (
        <PopMenu x={tplAt.x} y={tplAt.y - 4} onClose={() => setTplAt(null)} className="cs-tpl" label={L('قالب', 'Template')}>
          <form className="menu-form" onSubmit={(e) => { e.preventDefault(); if (tplName.trim()) { s.saveTemplate(tplName); setTplName(''); } }}>
            <input value={tplName} onChange={(e) => setTplName(e.target.value)} placeholder={L('نام قالب تازه', 'New template name')} aria-label={L('نام قالب', 'Template name')} />
            <button type="submit" className="btn btn-primary sm" disabled={!tplName.trim()}>{L('ذخیره', 'Save')}</button>
          </form>
          <MI icon="ph-arrow-counter-clockwise" act="tpl-defaults" label={L('بازگشت به پیش‌فرض‌ها', 'Apply defaults')} onClick={() => { s.reset(); const b = useChartBus.getState(); b.setScale('normal'); b.setAutoScale(true); b.setInvert(false); }} />
          {s.templates.length ? <MSep /> : null}
          {s.templates.map((t) => (
            <div key={t.name} className="cs-tpl-row">
              <MI label={t.name} act={`tpl-${t.name}`} onClick={() => s.applyTemplate(t.name)} />
              <button type="button" className="lt" title={L('حذف قالب', 'Remove template')} aria-label={L('حذف قالب', 'Remove template')} onClick={() => s.deleteTemplate(t.name)}><Icon name="ph-trash" size="sm" /></button>
            </div>
          ))}
          {!s.templates.length ? <div className="menu-empty">{L('هنوز قالبی ذخیره نشده است.', 'No templates saved yet.')}</div> : null}
        </PopMenu>
      ) : null}
    </div>
  );
}

function sampleDate(f: ChartSettings['dateFormat']): string {
  switch (f) {
    case 'MMM dd, yyyy': return 'Sep 29, 1997';
    case 'yyyy-MM-dd': return '1997-09-29';
    case 'dd/MM/yyyy': return '29/09/1997';
    case 'MM/dd/yyyy': return '09/29/1997';
    default: return '29 Sep \'97';
  }
}

export function sessionName(id: SessionId, fa: boolean): string {
  const names: Record<SessionId, [string, string]> = { sydney: ['سیدنی', 'Sydney'], tokyo: ['توکیو', 'Tokyo'], london: ['لندن', 'London'], newyork: ['نیویورک', 'New York'] };
  return names[id][fa ? 0 : 1];
}
