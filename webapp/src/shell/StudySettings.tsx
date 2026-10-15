import { useMemo, useRef, useState, type ReactNode } from 'react';
import { usePrefs } from '../state/prefs';
import { SCRIPT, VIS_RANGES, useStudies, type FillStyle, type LevelStyle, type LineStyleName, type PlotStyle, type PlotType, type Study, type StudyStyle, type VisClass, type Visibility } from '../state/studies';
import { STUDIES as REGISTRY } from '../engine/indicators_ext_c.js';
import { DEFAULT_PROPS, useScript, scriptTitle, type StrategyProps } from '../state/script';
import { entryOf } from '../chart/indicatorCatalog';
import { GUIDE, shortName, splitColor, studyGuides, studyPlots, withAlpha } from '../chart/studies';
import { INPUT_LABELS, MENU_INPUTS, SOURCES, SOURCE_LABELS, TIMEFRAMES, inputLabel, plotName } from '../chart/studyMeta';
import { useChartBus } from '../chart/chartBus';
import { useReplay } from '../state/replay';
import { intervalSeconds } from '../data/intervals';
import { useL } from '../pages/kit';
import { usePage } from '../state/page';
import { Dropdown } from '../ui/Menu';
import { Icon } from '../ui/Icon';
import './studies.css';

// TradingView's indicator settings: Inputs, Style and Visibility tabs (and Properties for a strategy
// script), every change shown on the chart at once, Cancel putting the study back as it was, Ok
// keeping it, and a Defaults menu to reset it or save it as the default for the next one added.
type Tab = 'inputs' | 'style' | 'visibility' | 'properties';

/** TradingView's colour picker palette: greys, the ten main colours, then their tints and shades. */
export const PALETTE = [
  '#FFFFFF', '#EBEBEB', '#D6D6D6', '#BFBFBF', '#A8A8A8', '#8F8F8F', '#757575', '#5C5C5C', '#434343', '#000000',
  '#F23645', '#FF9800', '#FFEB3B', '#4CAF50', '#089981', '#00BCD4', '#2962FF', '#673AB7', '#9C27B0', '#E91E63',
  '#FCCBCD', '#FFE0B2', '#FFF9C4', '#C8E6C9', '#ACE5DC', '#B2EBF2', '#BBD9FB', '#D1C4E9', '#E1BEE7', '#F8BBD0',
  '#FAA1A4', '#FFCC80', '#FFF59D', '#A5D6A7', '#70CCBD', '#80DEEA', '#90BFF9', '#B39DDB', '#CE93D8', '#F48FB1',
  '#F77C80', '#FFB74D', '#FFF176', '#81C784', '#42BDA8', '#4DD0E1', '#5B9CF6', '#9575CD', '#BA68C8', '#F06292',
  '#F7525F', '#FFA726', '#FFEE58', '#66BB6A', '#22AB94', '#26C6DA', '#3179F5', '#7E57C2', '#AB47BC', '#EC407A',
  '#B22833', '#F57C00', '#FBC02D', '#388E3C', '#056656', '#0097A7', '#1848CC', '#512DA8', '#7B1FA2', '#C2185B',
  '#801922', '#E65100', '#F57F17', '#1B5E20', '#00332A', '#006064', '#0C3299', '#311B92', '#4A148C', '#880E4F',
];

const PLOT_TYPES: { id: PlotType; fa: string; en: string }[] = [
  { id: 'line', fa: 'خط', en: 'Line' }, { id: 'step', fa: 'خط پله‌ای', en: 'Step line' }, { id: 'histogram', fa: 'هیستوگرام', en: 'Histogram' },
  { id: 'area', fa: 'ناحیه', en: 'Area' }, { id: 'columns', fa: 'ستون‌ها', en: 'Columns' }, { id: 'circles', fa: 'دایره‌ها', en: 'Circles' }, { id: 'cross', fa: 'ضربدر', en: 'Cross' },
];
const LINE_STYLES: { id: LineStyleName; fa: string; en: string }[] = [
  { id: 'solid', fa: 'توپر', en: 'Solid' }, { id: 'dashed', fa: 'خط‌چین', en: 'Dashed' }, { id: 'dotted', fa: 'نقطه‌چین', en: 'Dotted' },
];
const VIS_NAMES: Record<VisClass, [string, string]> = { seconds: ['ثانیه', 'Seconds'], minutes: ['دقیقه', 'Minutes'], hours: ['ساعت', 'Hours'], days: ['روز', 'Days'], weeks: ['هفته', 'Weeks'], months: ['ماه', 'Months'] };

const clone = <T,>(v: T): T => JSON.parse(JSON.stringify(v)) as T;

export function StudySettings({ uid, onClose }: { uid: string; onClose(): void }) {
  const L = useL();
  const fa = usePrefs((s) => s.lang) === 'fa';
  const study = useStudies((s) => s.list.find((x) => x.uid === uid));
  const all = useStudies((s) => s.list);
  const { patch, restore, saveDefault, defaults, resetDefault } = useStudies();
  const snapshot = useRef<Study | null>(study ? clone(study) : null);
  const scriptSnap = useRef({ inputs: { ...useScript.getState().inputs }, props: { ...useScript.getState().props } });
  const isScript = study?.key === SCRIPT;
  const script = useScript();
  const hasStrategy = Boolean(script.result?.ok && (script.result.strategy || script.result.signals));
  const [tab, setTab] = useState<Tab>('inputs');
  const [defaultsAt, setDefaultsAt] = useState<HTMLElement | null>(null);
  const [popOpen, setPopOpen] = useState(false);
  const def = study && !isScript ? REGISTRY[study.key] : undefined;
  const bars = useReplay((r) => r.slice) ?? useChartBus.getState().bars;
  const plots = useMemo(() => (study ? studyPlots(study, bars, all) : []), [study, bars, all]);
  const guides = useMemo(() => (study && def ? studyGuides(study, bars) : { levels: [], zone: false, fill: null, fill2: null }), [study, def, bars]);
  if (!study || (!def && !isScript)) return null;
  const entry = entryOf(study.key);
  const title = isScript ? scriptTitle(script.runCode) ?? 'NamaScript' : entry ? (/^[\x20-\x7e]+$/.test(entry.abbr) ? entry.abbr : shortName(study.key, def!.label)) : study.key;
  const style = study.style ?? {};
  const setStyle = (p: Partial<StudyStyle>) => patch(uid, { style: { ...style, ...p } });
  const setPlot = (k: number, p: Partial<PlotStyle>) => {
    const list = [...(style.plots ?? [])];
    while (list.length <= k) list.push({});
    list[k] = { ...list[k], ...p };
    setStyle({ plots: list });
  };

  const cancel = () => {
    if (snapshot.current) restore(snapshot.current);
    if (isScript) { const s = useScript.getState(); s.setInputs(scriptSnap.current.inputs); s.setProps(scriptSnap.current.props); }
    onClose();
  };
  const ok = () => onClose();
  const reset = () => {
    if (isScript) { script.setInputs({}); script.setProps({ ...DEFAULT_PROPS }); patch(uid, { style: undefined, visibility: undefined }); return; }
    patch(uid, { inputs: { ...def!.inputs }, style: undefined, visibility: undefined, timeframe: undefined, waitClose: undefined });
  };

  const tabs: { id: Tab; label: string }[] = [
    { id: 'inputs', label: L('ورودی‌ها', 'Inputs') }, ...(isScript && hasStrategy ? [{ id: 'properties' as const, label: L('ویژگی‌ها', 'Properties') }] : []),
    { id: 'style', label: L('ظاهر', 'Style') }, { id: 'visibility', label: L('نمایش', 'Visibility') },
  ];

  return (
    <div className="scrim" onMouseDown={(e) => { if (e.target === e.currentTarget) cancel(); }}>
      <div className="dialog settings st-dialog" dir={fa ? 'rtl' : 'ltr'} role="dialog" aria-modal="true" aria-label={title}
        onKeyDown={(e) => {
          if (popOpen || defaultsAt) return;
          if (e.key === 'Escape') cancel();
          if (e.key === 'Enter' && (e.target as HTMLElement).tagName !== 'TEXTAREA' && (e.target as HTMLElement).tagName !== 'BUTTON') ok();
        }}>
        <header className="dialog-head st-head">
          <h2 className="num" dir="ltr">{title}</h2>
          <span className="ws-spacer" />
          <button className="ib" onClick={() => { cancel(); usePage.getState().open('help', isScript ? 'namascript' : study.key); }} aria-label={L('راهنما', 'Help')} title={L('راهنما', 'Help')}><Icon name="ph-question" size="md" /></button>
          <button className="ib" onClick={cancel} aria-label={L('بستن', 'Close')}><Icon name="ph-x" size="md" /></button>
        </header>
        <nav className="st-tabs" role="tablist">
          {tabs.map((x) => <button key={x.id} role="tab" aria-selected={tab === x.id} className={`st-tab${tab === x.id ? ' on' : ''}`} onClick={() => setTab(x.id)}><span className="fa">{x.label}</span></button>)}
        </nav>
        <div className="settings-body st-body">
          {tab === 'inputs' ? (isScript ? <ScriptInputs /> : <Inputs study={study} all={all} />) : null}
          {tab === 'properties' ? <Properties /> : null}
          {tab === 'style' ? (
            <StyleTab study={study} plots={plots} guides={guides} fa={fa} setPlot={setPlot} setStyle={setStyle} onPop={setPopOpen} />
          ) : null}
          {tab === 'visibility' ? <VisibilityTab value={study.visibility} onChange={(v) => patch(uid, { visibility: v })} fa={fa} /> : null}
        </div>
        <footer className="dialog-foot st-foot">
          <button className="btn btn-quiet st-defaults" onClick={(e) => setDefaultsAt(defaultsAt ? null : e.currentTarget)}>
            <span className="fa">{L('پیش‌فرض‌ها', 'Defaults')}</span><Icon name="ph-caret-down" size="sm" />
          </button>
          <span className="ws-spacer" />
          <button className="btn btn-quiet" onClick={cancel}><span className="fa">{L('انصراف', 'Cancel')}</span></button>
          <button className="btn btn-primary" onClick={ok}><span className="fa">{L('تأیید', 'Ok')}</span></button>
        </footer>
        {defaultsAt ? (
          <Dropdown anchor={defaultsAt} onClose={() => setDefaultsAt(null)} className="st-pop">
            <button className="menu-item" onClick={() => { reset(); setDefaultsAt(null); }}><span className="fa">{L('بازگشت به تنظیمات اولیه', 'Reset settings')}</span></button>
            {!isScript ? <button className="menu-item" onClick={() => { saveDefault(uid); setDefaultsAt(null); }}><span className="fa">{L('ذخیره به‌عنوان پیش‌فرض', 'Save as default')}</span></button> : null}
            {!isScript && defaults[study.key] ? <button className="menu-item" onClick={() => { resetDefault(study.key); setDefaultsAt(null); }}><span className="fa">{L('حذف پیش‌فرض ذخیره‌شده', 'Forget the saved default')}</span></button> : null}
          </Dropdown>
        ) : null}
      </div>
    </div>
  );
}

function Row({ label, children, className = '' }: { label: ReactNode; children: ReactNode; className?: string }) {
  return <label className={`setting-row st-row ${className}`}><span className="st-label fa">{label}</span><span className="st-ctl">{children}</span></label>;
}

function Section({ title }: { title: string }) {
  return <div className="st-section fa">{title}</div>;
}

function NumberInput({ value, onChange, step, min, max }: { value: number; onChange(v: number): void; step?: number; min?: number; max?: number }) {
  const [text, setText] = useState(String(value));
  const last = useRef(value);
  if (last.current !== value && Number(text) !== value) { last.current = value; setText(String(value)); }
  const commit = (raw: string) => {
    setText(raw);
    const n = Number(raw.replace(/[۰-۹]/g, (d) => String(d.charCodeAt(0) - 0x06f0)));
    if (raw.trim() !== '' && Number.isFinite(n)) { last.current = n; onChange(n); }
  };
  return <input className="num st-num" type="number" dir="ltr" step={step ?? 'any'} min={min} max={max} value={text} onChange={(e) => commit(e.target.value)} />;
}

function Inputs({ study, all }: { study: Study; all: Study[] }) {
  const L = useL();
  const fa = usePrefs((s) => s.lang) === 'fa';
  const interval = usePrefs((s) => s.interval);
  const patch = useStudies((s) => s.patch);
  const def = REGISTRY[study.key]!;
  const values = { ...def.inputs, ...study.inputs };
  const set = (k: string, v: number | string | boolean) => patch(study.uid, { inputs: { ...values, [k]: v } });
  const bars = useChartBus.getState().bars;
  // Other studies' outputs a source can read (indicator on indicator), never itself or a study that reads it.
  const sources = useMemo(() => all.filter((s) => s.uid !== study.uid && REGISTRY[s.key] && !String(s.inputs.source ?? '').includes(study.uid)).map((s) => {
    const d = REGISTRY[s.key]!;
    const args = Object.values({ ...d.inputs, ...s.inputs }).filter((v) => typeof v === 'number').join(' ');
    return { s, name: `${shortName(s.key, d.label)}${args ? ` (${args})` : ''}`, plots: studyPlots(s, bars, all) };
  }), [all, study.uid, bars]);
  const keys = Object.keys(def.inputs);
  const higher = TIMEFRAMES.filter((x) => !x.id || intervalSeconds(x.id) > intervalSeconds(interval));
  return (
    <>
      {!keys.length ? <p className="st-note fa">{L('این اندیکاتور ورودی ندارد.', 'This indicator has no inputs.')}</p> : null}
      {keys.map((key) => {
        const initial = def.inputs[key];
        const v = values[key];
        const label = inputLabel(key, fa);
        if (key === 'source') {
          return (
            <Row key={key} label={label}>
              <select className="st-select" value={String(v)} onChange={(e) => set(key, e.target.value)}>
                <optgroup label={L('قیمت', 'Price')}>{SOURCES.map((o) => <option key={o} value={o}>{fa ? SOURCE_LABELS[o]?.[0] ?? o : SOURCE_LABELS[o]?.[1] ?? o}</option>)}</optgroup>
                {sources.length ? (
                  <optgroup label={L('اندیکاتورها', 'Indicators')}>
                    {sources.flatMap(({ s, name, plots }) => plots.map((p, i) => <option key={`${s.uid}:${i}`} value={`study:${s.uid}:${i}`}>{`${name}: ${plotName(s.key, i, p.name, fa)}`}</option>))}
                  </optgroup>
                ) : null}
              </select>
            </Row>
          );
        }
        if (MENU_INPUTS[key]) {
          return (
            <Row key={key} label={label}>
              <select className="st-select" value={String(v)} onChange={(e) => set(key, e.target.value)}>
                {MENU_INPUTS[key]!.map((o) => <option key={o.value} value={o.value}>{fa ? o.fa : o.en}</option>)}
              </select>
            </Row>
          );
        }
        if (typeof initial === 'boolean') return <Row key={key} label={label} className="check"><input type="checkbox" checked={Boolean(v)} onChange={(e) => set(key, e.target.checked)} /></Row>;
        if (typeof initial === 'number') return <Row key={key} label={label}><NumberInput value={Number(v)} step={Number.isInteger(initial) ? 1 : 0.01} onChange={(n) => set(key, n)} /></Row>;
        return <Row key={key} label={label}><input className="st-text" value={String(v)} onChange={(e) => set(key, e.target.value)} /></Row>;
      })}
      <Section title={L('محاسبه', 'Calculation')} />
      <Row label={L('تایم‌فریم', 'Timeframe')}>
        <select className="st-select" value={study.timeframe ?? ''} onChange={(e) => patch(study.uid, { timeframe: e.target.value || undefined })}>
          {higher.map((x) => <option key={x.id} value={x.id}>{fa ? x.fa : x.en}</option>)}
        </select>
      </Row>
      <label className={`setting-row st-row check${study.timeframe ? '' : ' off'}`}>
        <input type="checkbox" disabled={!study.timeframe} checked={study.waitClose ?? true} onChange={(e) => patch(study.uid, { waitClose: e.target.checked })} />
        <span className="st-label fa">{L('صبر برای بسته شدن کندل تایم‌فریم', 'Wait for timeframe closes')}</span>
      </label>
    </>
  );
}

function ScriptInputs() {
  const L = useL();
  const result = useScript((s) => s.result);
  const inputs = useScript((s) => s.inputs);
  const setInputs = useScript((s) => s.setInputs);
  const decls = result?.ok ? result.inputs ?? [] : [];
  if (!decls.length) return <p className="st-note fa">{L('این اسکریپت ورودی ندارد.', 'This script has no inputs.')}</p>;
  const set = (k: string, v: unknown) => setInputs({ ...inputs, [k]: v });
  return (
    <>
      {decls.map((d) => {
        const v = inputs[d.key] ?? d.def;
        if (d.type === 'bool') return <Row key={d.key} label={d.key} className="check"><input type="checkbox" checked={Boolean(v)} onChange={(e) => set(d.key, e.target.checked)} /></Row>;
        if (d.type === 'string' && d.options?.length) return <Row key={d.key} label={d.key}><select className="st-select" value={String(v)} onChange={(e) => set(d.key, e.target.value)}>{d.options.map((o) => <option key={o}>{o}</option>)}</select></Row>;
        if (d.type === 'color') return <Row key={d.key} label={d.key}><input type="color" value={splitColor(String(v)).hex} onChange={(e) => set(d.key, e.target.value)} /></Row>;
        if (d.type === 'source') return <Row key={d.key} label={d.key}><select className="st-select" value={String(v)} onChange={(e) => set(d.key, e.target.value)}>{SOURCES.map((o) => <option key={o}>{o}</option>)}</select></Row>;
        if (d.type === 'string') return <Row key={d.key} label={d.key}><input className="st-text" value={String(v)} onChange={(e) => set(d.key, e.target.value)} /></Row>;
        return <Row key={d.key} label={d.key}><NumberInput value={Number(v)} step={d.step ?? (d.type === 'int' ? 1 : 0.1)} min={d.min} max={d.max} onChange={(n) => set(d.key, d.type === 'int' ? Math.round(n) : n)} /></Row>;
      })}
    </>
  );
}

/** A strategy script's Properties: TradingView's, every one feeding the tester. */
export function Properties() {
  const L = useL();
  const p = useScript((s) => s.props);
  const setProps = useScript((s) => s.setProps);
  const num = (k: keyof StrategyProps, label: string, step = 1) => <Row label={label}><NumberInput value={Number(p[k] ?? 0)} step={step} onChange={(v) => setProps({ [k]: v } as Partial<StrategyProps>)} /></Row>;
  const date = (v: number | null | undefined) => (v ? new Date(v * 1000).toISOString().slice(0, 10) : '');
  return (
    <>
      <Section title={L('سرمایه', 'Capital')} />
      {num('capital', L('سرمایه‌ی اولیه', 'Initial capital'), 100)}
      <Row label={L('اندازه‌ی سفارش', 'Order size')}>
        <span className="st-pair">
          <NumberInput value={p.sizeMode === 'qty' ? p.qty ?? 1 : p.sizeMode === 'cash' ? p.cash ?? 1000 : p.riskShare} onChange={(v) => setProps(p.sizeMode === 'qty' ? { qty: v } : p.sizeMode === 'cash' ? { cash: v } : { riskShare: v })} />
          <select className="st-select" value={p.sizeMode ?? 'percent'} onChange={(e) => setProps({ sizeMode: e.target.value as StrategyProps['sizeMode'] })}>
            <option value="percent">{L('درصد سرمایه', '% of equity')}</option><option value="qty">{L('قرارداد', 'Contracts')}</option><option value="cash">USDT</option>
          </select>
        </span>
      </Row>
      {num('pyramiding', L('هرم‌سازی (ورود هم‌جهت)', 'Pyramiding'))}
      <Section title={L('هزینه‌ها', 'Costs')} />
      {num('feePct', L('کارمزد (درصد)', 'Commission (%)'), 0.01)}
      {num('feeCash', L('کارمزد هر سفارش (USDT)', 'Commission per order (USDT)'), 0.1)}
      {num('slippagePct', L('لغزش (درصد)', 'Slippage (%)'), 0.01)}
      <Section title={L('اجرا', 'Execution')} />
      <Row label={L('جهت معامله', 'Direction')}>
        <select className="st-select" value={p.direction} onChange={(e) => setProps({ direction: e.target.value as StrategyProps['direction'] })}>
          <option value="both">{L('هر دو', 'Both')}</option><option value="long">{L('فقط خرید', 'Long only')}</option><option value="short">{L('فقط فروش', 'Short only')}</option>
        </select>
      </Row>
      {num('stopPct', L('حد ضرر (درصد)', 'Stop loss (%)'), 0.1)}
      {num('targetPct', L('حد سود (درصد)', 'Take profit (%)'), 0.1)}
      <label className="setting-row st-row check"><input type="checkbox" checked={Boolean(p.fillOnClose)} onChange={(e) => setProps({ fillOnClose: e.target.checked })} /><span className="st-label fa">{L('اجرای سفارش با بسته شدن کندل', 'Fill orders on bar close')}</span></label>
      <label className="setting-row st-row check"><input type="checkbox" checked={p.showTrades} onChange={(e) => setProps({ showTrades: e.target.checked })} /><span className="st-label fa">{L('نمایش معامله‌ها روی چارت', 'Show trades on the chart')}</span></label>
      <Section title={L('بازه‌ی تاریخ', 'Date range')} />
      <Row label={L('از', 'From')}><input className="st-text num" type="date" dir="ltr" value={date(p.from)} onChange={(e) => setProps({ from: e.target.value ? Date.parse(`${e.target.value}T00:00:00Z`) / 1000 : null })} /></Row>
      <Row label={L('تا', 'To')}><input className="st-text num" type="date" dir="ltr" value={date(p.to)} onChange={(e) => setProps({ to: e.target.value ? Date.parse(`${e.target.value}T23:59:59Z`) / 1000 : null })} /></Row>
    </>
  );
}

/** A colour swatch that opens TradingView's picker: palette, opacity and, for lines, thickness and style. */
export function ColorButton({ color, opacity, width, lineStyle, onChange, onPop, line = false }: {
  color: string; opacity?: number; width?: number; lineStyle?: LineStyleName; line?: boolean;
  onChange(p: { color?: string; opacity?: number; width?: number; lineStyle?: LineStyleName }): void; onPop?(open: boolean): void;
}) {
  const L = useL();
  const [at, setAt] = useState<HTMLElement | null>(null);
  const open = (el: HTMLElement | null) => { setAt(el); onPop?.(Boolean(el)); };
  const op = opacity ?? 1;
  return (
    <>
      <button type="button" className={`st-swatch${line ? ' line' : ''}`} aria-label={L('رنگ', 'Colour')} onClick={(e) => { e.preventDefault(); open(at ? null : e.currentTarget); }}>
        <i style={{ background: withAlpha(color, op) }} />
        {line ? <b style={{ borderTopColor: withAlpha(color, op), borderTopWidth: width ?? 1, borderTopStyle: lineStyle === 'dashed' ? 'dashed' : lineStyle === 'dotted' ? 'dotted' : 'solid' }} /> : null}
      </button>
      {at ? (
        <Dropdown anchor={at} onClose={() => open(null)} className="st-pop st-color">
          <div className="st-palette">
            {PALETTE.map((c) => <button key={c} type="button" className={`st-chip${c.toUpperCase() === color.toUpperCase() ? ' on' : ''}`} style={{ background: c }} title={c} onClick={() => onChange({ color: c })} />)}
          </div>
          <label className="st-custom"><span className="fa">{L('رنگ دلخواه', 'Custom')}</span><input type="color" value={splitColor(color).hex} onChange={(e) => onChange({ color: e.target.value })} /></label>
          <div className="st-opacity">
            <span className="fa">{L('شفافیت', 'Opacity')}</span>
            <input type="range" min={0} max={100} value={Math.round(op * 100)} onChange={(e) => onChange({ opacity: Number(e.target.value) / 100 })}
              style={{ background: `linear-gradient(to right, ${withAlpha(color, 0)}, ${withAlpha(color, 1)})` }} />
            <span className="num">{Math.round(op * 100)}%</span>
          </div>
          {line ? (
            <>
              <div className="st-opts"><span className="fa">{L('ضخامت', 'Thickness')}</span>
                {[1, 2, 3, 4].map((w) => <button key={w} type="button" className={`st-opt${(width ?? 1) === w ? ' on' : ''}`} onClick={() => onChange({ width: w })} aria-label={`${w}px`}><b style={{ borderTopWidth: w }} /></button>)}
              </div>
              <div className="st-opts"><span className="fa">{L('سبک خط', 'Line style')}</span>
                {LINE_STYLES.map((s) => <button key={s.id} type="button" className={`st-opt${(lineStyle ?? 'solid') === s.id ? ' on' : ''}`} onClick={() => onChange({ lineStyle: s.id })} aria-label={s.en}><b style={{ borderTopStyle: s.id }} /></button>)}
              </div>
            </>
          ) : null}
        </Dropdown>
      ) : null}
    </>
  );
}

function StyleTab({ study, plots, guides, fa, setPlot, setStyle, onPop }: {
  study: Study; plots: ReturnType<typeof studyPlots>; guides: ReturnType<typeof studyGuides>; fa: boolean;
  setPlot(k: number, p: Partial<PlotStyle>): void; setStyle(p: Partial<StudyStyle>): void; onPop(open: boolean): void;
}) {
  const L = useL();
  const style = study.style ?? {};
  const levels: LevelStyle[] = style.levels ?? guides.levels.map((v) => ({ value: v, color: GUIDE, lineStyle: 'dashed', visible: true }));
  const setLevels = (l: LevelStyle[]) => setStyle({ levels: l });
  const fill = style.fill ?? {};
  const setFill = (p: Partial<FillStyle>) => setStyle({ fill: { ...fill, ...p } });
  const lf = style.levelFill ?? {};
  const setLf = (p: Partial<FillStyle>) => setStyle({ levelFill: { ...lf, ...p } });
  const baseFill = guides.fill ? splitColor(guides.fill) : null;
  const levelNames = (i: number, n: number) => (n === 2 ? (i === 0 ? L('سطح ۱', 'Level 1') : L('سطح ۲', 'Level 2')) : L(`سطح ${i + 1}`, `Level ${i + 1}`));
  return (
    <>
      {!plots.length ? <p className="st-note fa">{L('این اندیکاتور خطی برای تنظیم ندارد.', 'This indicator has no plots to style.')}</p> : null}
      {plots.map((p, k) => {
        const ps = style.plots?.[k] ?? {};
        const type = ps.type ?? (p.hist ? 'histogram' : 'line');
        return (
          <div key={k} className="setting-row st-row st-plot">
            <label className="st-check"><input type="checkbox" checked={ps.visible !== false} onChange={(e) => setPlot(k, { visible: e.target.checked })} /><span className="st-label">{study.key === '__script' ? p.name : plotName(study.key, k, p.name, fa)}</span></label>
            <span className="st-ctl">
              <ColorButton line={type !== 'histogram' && type !== 'columns'} color={ps.color ?? p.color} opacity={ps.opacity} width={ps.width ?? p.width} lineStyle={ps.lineStyle ?? (p.dashed ? 'dashed' : 'solid')} onChange={(x) => setPlot(k, x)} onPop={onPop} />
              <select className="st-select sm" value={type} onChange={(e) => setPlot(k, { type: e.target.value as PlotType })} aria-label={L('نوع رسم', 'Plot type')}>
                {PLOT_TYPES.map((t) => <option key={t.id} value={t.id}>{fa ? t.fa : t.en}</option>)}
              </select>
            </span>
          </div>
        );
      })}
      {baseFill ? (
        <div className="setting-row st-row st-plot">
          <label className="st-check"><input type="checkbox" checked={fill.visible !== false} onChange={(e) => setFill({ visible: e.target.checked })} /><span className="st-label fa">{L('پس‌زمینه', 'Background')}</span></label>
          <span className="st-ctl">
            <ColorButton color={fill.color ?? baseFill.hex} opacity={fill.opacity ?? baseFill.opacity} onChange={(x) => setFill(x)} onPop={onPop} />
            {guides.fill2 ? <ColorButton color={fill.color2 ?? splitColor(guides.fill2).hex} opacity={fill.opacity ?? splitColor(guides.fill2).opacity} onChange={(x) => setFill(x.color ? { color2: x.color } : x)} onPop={onPop} /> : null}
          </span>
        </div>
      ) : null}
      {study.key !== '__script' ? (
        <>
          <Section title={L('سطح‌ها', 'Levels')} />
          {levels.map((g, i) => (
            <div key={i} className="setting-row st-row st-plot">
              <label className="st-check"><input type="checkbox" checked={g.visible} onChange={(e) => setLevels(levels.map((x, j) => (j === i ? { ...x, visible: e.target.checked } : x)))} /><span className="st-label fa">{levelNames(i, levels.length)}</span></label>
              <span className="st-ctl">
                <ColorButton line color={g.color} width={g.width} lineStyle={g.lineStyle} onChange={(x) => setLevels(levels.map((y, j) => (j === i ? { ...y, ...(x.color ? { color: x.color } : {}), ...(x.opacity != null ? { color: withAlpha(splitColor(y.color).hex, x.opacity) } : {}), ...(x.width ? { width: x.width } : {}), ...(x.lineStyle ? { lineStyle: x.lineStyle } : {}) } : y)))} onPop={onPop} />
                <NumberInput value={g.value} onChange={(v) => setLevels(levels.map((x, j) => (j === i ? { ...x, value: v } : x)))} />
                <button type="button" className="lt" aria-label={L('حذف سطح', 'Remove level')} onClick={() => setLevels(levels.filter((_, j) => j !== i))}><Icon name="ph-x" size="sm" /></button>
              </span>
            </div>
          ))}
          <button type="button" className="st-add fa" onClick={() => setLevels([...levels, { value: levels.length ? levels[levels.length - 1]!.value : 0, color: GUIDE, lineStyle: 'dashed', visible: true }])}>
            <Icon name="ph-plus" size="sm" /><span>{L('افزودن سطح', 'Add level')}</span>
          </button>
          {levels.length >= 2 ? (
            <div className="setting-row st-row st-plot">
              <label className="st-check"><input type="checkbox" checked={guides.zone ? lf.visible !== false : lf.visible === true} onChange={(e) => setLf({ visible: e.target.checked })} /><span className="st-label fa">{L('پس‌زمینه‌ی سطح‌ها', 'Levels background')}</span></label>
              <span className="st-ctl"><ColorButton color={lf.color ?? '#7E57C2'} opacity={lf.opacity ?? 0.1} onChange={(x) => setLf(x)} onPop={onPop} /></span>
            </div>
          ) : null}
        </>
      ) : null}
      <Section title={L('مقدارهای خروجی', 'Output values')} />
      <Row label={L('دقت اعشار', 'Precision')}>
        <select className="st-select" value={style.precision ?? ''} onChange={(e) => setStyle({ precision: e.target.value === '' ? undefined : Number(e.target.value) })}>
          <option value="">{L('پیش‌فرض', 'Default')}</option>
          {[0, 1, 2, 3, 4, 5, 6, 7, 8].map((n) => <option key={n} value={n}>{n}</option>)}
        </select>
      </Row>
      <Row label={L('مقیاس قیمت', 'Scale')}>
        <select className="st-select" value={style.scale ?? 'right'} onChange={(e) => setStyle({ scale: e.target.value as StudyStyle['scale'] })}>
          <option value="right">{L('مقیاس راست', 'Pin to right scale')}</option>
          <option value="left">{L('مقیاس چپ', 'Pin to left scale')}</option>
          <option value="none">{L('بدون مقیاس (تمام‌صفحه)', 'No scale (full screen)')}</option>
        </select>
      </Row>
      <label className="setting-row st-row check"><input type="checkbox" checked={style.priceLabels !== false} onChange={(e) => setStyle({ priceLabels: e.target.checked })} /><span className="st-label fa">{L('برچسب روی مقیاس قیمت', 'Labels on price scale')}</span></label>
      <label className="setting-row st-row check"><input type="checkbox" checked={style.statusValues !== false} onChange={(e) => setStyle({ statusValues: e.target.checked })} /><span className="st-label fa">{L('مقدارها در خط وضعیت', 'Values in status line')}</span></label>
    </>
  );
}

function VisibilityTab({ value, onChange, fa }: { value: Visibility | undefined; onChange(v: Visibility): void; fa: boolean }) {
  const v = value ?? {};
  return (
    <>
      {(Object.keys(VIS_RANGES) as VisClass[]).map((cls) => {
        const [lo, hi] = VIS_RANGES[cls];
        const c = v[cls] ?? { on: true, from: lo, to: hi };
        const set = (p: Partial<typeof c>) => onChange({ ...v, [cls]: { ...c, ...p } });
        return (
          <div key={cls} className={`setting-row st-row st-vis${c.on ? '' : ' off'}`}>
            <label className="st-check"><input type="checkbox" checked={c.on} onChange={(e) => set({ on: e.target.checked })} /><span className="st-label fa">{fa ? VIS_NAMES[cls][0] : VIS_NAMES[cls][1]}</span></label>
            <span className="st-ctl" dir="ltr">
              <NumberInput value={c.from} min={lo} max={hi} step={1} onChange={(n) => set({ from: Math.max(lo, Math.min(hi, Math.round(n))) })} />
              <input type="range" className="st-range" min={lo} max={hi} value={c.to} disabled={!c.on} onChange={(e) => set({ to: Math.max(c.from, Number(e.target.value)) })} aria-label="range" />
              <NumberInput value={c.to} min={lo} max={hi} step={1} onChange={(n) => set({ to: Math.max(lo, Math.min(hi, Math.round(n))) })} />
            </span>
          </div>
        );
      })}
    </>
  );
}

/** Every input key has a label in both languages (the unit test reads this). */
export const LABELLED_KEYS = Object.keys(INPUT_LABELS);
