// The inputs of the chart types built from the bars, as TradingView asks for them: Renko's box
// size (by ATR or fixed) and wicks, Range's size, Line break's line count, Kagi's reversal, Point &
// figure's box, reversal and source, the Baseline's base level. Shown in a pop-over from the gear
// beside the chart-type button and inside Settings → Symbol.
import { useRef, useState } from 'react';
import { usePrefs } from '../state/prefs';
import { useChartSettings, type ChartSettings } from '../state/chartSettings';
import { useL } from '../pages/kit';
import { Icon } from '../ui/Icon';
import { PARAM_TYPES, chartTypeDef, type ChartType } from './chartTypes';
import { PopMenu } from './menuKit';
import './chart-x.css';

type Key = keyof ChartSettings;

function NumField({ k, min, max, step, label, suffix }: { k: Key; min: number; max?: number; step?: number; label: string; suffix?: string }) {
  const s = useChartSettings();
  const v = s[k] as number;
  return (
    <label className="ctp-row">
      <span>{label}</span>
      <span className="ctp-ctl">
        <input type="number" className="num" value={Number.isFinite(v) ? v : 0} min={min} max={max} step={step ?? 1}
          onChange={(e) => { const n = Number(e.target.value); if (Number.isFinite(n)) s.set({ [k]: max != null ? Math.min(max, Math.max(min, n)) : Math.max(min, n) } as Partial<ChartSettings>); }} />
        {suffix ? <span className="muted">{suffix}</span> : null}
      </span>
    </label>
  );
}

function Method({ k, label }: { k: 'renkoMethod' | 'rangeMethod' | 'kagiMethod' | 'pnfMethod'; label: string }) {
  const s = useChartSettings();
  const L = useL();
  return (
    <label className="ctp-row">
      <span>{label}</span>
      <span className="ctp-ctl">
        <select value={s[k]} onChange={(e) => s.set({ [k]: e.target.value } as Partial<ChartSettings>)}>
          <option value="atr">ATR</option>
          <option value="traditional">{L('سنتی (ثابت)', 'Traditional')}</option>
        </select>
      </span>
    </label>
  );
}

/** The fields of one chart type (nothing for a type without inputs). */
export function ChartTypeFields({ type }: { type: ChartType }) {
  const L = useL();
  const s = useChartSettings();
  switch (type) {
    case 'renko':
      return (
        <>
          <Method k="renkoMethod" label={L('روش اندازه‌ی آجر', 'Box size assignment method')} />
          {s.renkoMethod === 'atr'
            ? <NumField k="renkoAtr" min={1} max={500} label={L('طول ATR', 'ATR length')} />
            : <NumField k="renkoBox" min={0} step={0.0001} label={L('اندازه‌ی آجر', 'Box size')} />}
          <label className="ctp-row"><span>{L('فتیله‌ها', 'Wicks')}</span><span className="ctp-ctl"><input type="checkbox" checked={s.renkoWicks} onChange={(e) => s.set({ renkoWicks: e.target.checked })} /></span></label>
        </>
      );
    case 'range':
      return (
        <>
          <Method k="rangeMethod" label={L('روش اندازه‌ی بازه', 'Range assignment method')} />
          {s.rangeMethod === 'traditional' ? <NumField k="rangeSize" min={0} step={0.0001} label={L('اندازه‌ی بازه', 'Range')} /> : null}
        </>
      );
    case 'linebreak':
      return <NumField k="lineBreakCount" min={1} max={20} label={L('تعداد خط', 'Number of lines')} />;
    case 'kagi':
      return (
        <>
          <Method k="kagiMethod" label={L('روش اندازه‌ی برگشت', 'Reversal assignment method')} />
          {s.kagiMethod === 'atr'
            ? <NumField k="kagiAtr" min={1} max={500} label={L('طول ATR', 'ATR length')} />
            : <NumField k="kagiReversal" min={0} step={0.0001} label={L('مقدار برگشت', 'Reversal amount')} />}
        </>
      );
    case 'pnf':
      return (
        <>
          <Method k="pnfMethod" label={L('روش اندازه‌ی جعبه', 'Box size assignment method')} />
          {s.pnfMethod === 'atr'
            ? <NumField k="pnfAtr" min={1} max={500} label={L('طول ATR', 'ATR length')} />
            : <NumField k="pnfBox" min={0} step={0.0001} label={L('اندازه‌ی جعبه', 'Box size')} />}
          <NumField k="pnfReversal" min={1} max={20} label={L('مقدار برگشت (جعبه)', 'Reversal amount')} />
          <label className="ctp-row">
            <span>{L('منبع', 'Source')}</span>
            <span className="ctp-ctl">
              <select value={s.pnfSource} onChange={(e) => s.set({ pnfSource: e.target.value as ChartSettings['pnfSource'] })}>
                <option value="close">{L('پایانی', 'Close')}</option>
                <option value="hl">{L('سقف و کف', 'High/Low')}</option>
              </select>
            </span>
          </label>
        </>
      );
    case 'baseline':
      return <NumField k="baselinePct" min={0} max={100} label={L('سطح مبنا', 'Base level')} suffix="%" />;
    default:
      return null;
  }
}

/** The gear beside the chart-type button; only there for a type with inputs. */
export function ChartTypeParamsButton() {
  const L = useL();
  const type = usePrefs((s) => s.chartType);
  const lang = usePrefs((s) => s.lang);
  const btn = useRef<HTMLButtonElement>(null);
  const [at, setAt] = useState<{ x: number; y: number } | null>(null);
  if (!PARAM_TYPES.includes(type)) return null;
  const def = chartTypeDef(type);
  return (
    <>
      <button ref={btn} className={`ib ctp-gear${at ? ' open' : ''}`} title={L('تنظیمات نوع چارت', 'Chart type settings')} aria-label={L('تنظیمات نوع چارت', 'Chart type settings')}
        onClick={() => { const r = btn.current!.getBoundingClientRect(); setAt(at ? null : { x: lang === 'fa' ? r.right : r.left, y: r.bottom + 4 }); }}>
        <Icon name="ph-sliders-horizontal" size="sm" />
      </button>
      {at ? (
        <PopMenu x={at.x} y={at.y} onClose={() => setAt(null)} className="ctp-pop" label={L('تنظیمات نوع چارت', 'Chart type settings')}>
          <div className="cm-title">{lang === 'fa' ? def.fa : def.en}</div>
          <div className="ctp-body"><ChartTypeFields type={type} /></div>
        </PopMenu>
      ) : null}
    </>
  );
}
