// TradingView's six ready-made indicator templates, in its menu's order, with the old Pro-Chart's
// exact contents (OLD chart/core IndicatorTemplates.kt BuiltInIndicatorTemplates.ALL) mapped onto
// this catalogue's keys: alligator → alligator, ema → ema, sma → ma, maribbon → maRibbon,
// stochastic → stoch, the rest by the same name. Applying one replaces the indicators on the chart
// (the volume histogram stays), as TradingView's does, and touches nothing else.
import { useStudies, VOLUME } from '../state/studies';

export interface BuiltInTemplate {
  id: string;
  fa: string;
  en: string;
  /** The indicators in order, each with the inputs the template sets (a period, an offset). */
  studies: { key: string; inputs?: Record<string, number> }[];
}

export const BUILT_IN_TEMPLATES: BuiltInTemplate[] = [
  { id: 'tv_bw3lines', fa: 'سه خط بیل ویلیامز', en: "Bill Williams' 3 Lines", studies: [{ key: 'alligator' }] },
  { id: 'tv_displaced_ema', fa: 'EMA جابه‌جاشده', en: 'Displaced EMA', studies: [{ key: 'ema', inputs: { period: 20, shift: 5 } }] },
  { id: 'tv_ma_exp_ribbon', fa: 'نوار میانگین‌های نمایی', en: 'MA Exp Ribbon', studies: [{ key: 'maRibbon' }] },
  { id: 'tv_oscillators', fa: 'نوسانگرها', en: 'Oscillators', studies: [{ key: 'rsi' }, { key: 'macd' }, { key: 'stoch' }] },
  { id: 'tv_swing', fa: 'معامله‌ی نوسانی', en: 'Swing Trading', studies: [{ key: 'ema', inputs: { period: 20 } }, { key: 'ma', inputs: { period: 50 } }, { key: 'rsi' }] },
  { id: 'tv_volume_based', fa: 'بر پایه‌ی حجم', en: 'Volume Based', studies: [{ key: 'vwap' }, { key: 'obv' }, { key: 'mfi' }, { key: 'cmf' }] },
];

const ABBR: Record<string, string> = { alligator: 'Alligator', ema: 'EMA', ma: 'SMA', maRibbon: 'MA Ribbon', rsi: 'RSI', macd: 'MACD', stoch: 'Stoch', vwap: 'VWAP', obv: 'OBV', mfi: 'MFI', cmf: 'CMF' };

/** What a template puts on the chart, as every terminal's legend abbreviates it: «EMA 20 +5 · RSI». */
export function templateSummary(t: BuiltInTemplate): string {
  return t.studies.map(({ key, inputs }) => {
    const period = inputs?.period != null ? ` ${inputs.period}` : '';
    const shift = inputs?.shift ? ` +${inputs.shift}` : '';
    return `${ABBR[key] ?? key.toUpperCase()}${period}${shift}`;
  }).join(' · ');
}

/** Puts a built-in template's indicators on the chart in place of the ones there (volume kept). */
export function applyBuiltInTemplate(id: string): boolean {
  const t = BUILT_IN_TEMPLATES.find((x) => x.id === id);
  if (!t) return false;
  const st = useStudies.getState();
  for (const s of st.list) if (s.key !== VOLUME) st.remove(s.uid);
  for (const { key, inputs } of t.studies) {
    const uid = useStudies.getState().add(key);
    if (!uid || !inputs) continue;
    const cur = useStudies.getState().list.find((s) => s.uid === uid);
    if (cur) useStudies.getState().setInputs(uid, { ...cur.inputs, ...inputs });
  }
  return true;
}
