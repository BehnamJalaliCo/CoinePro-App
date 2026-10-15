import { useState } from 'react';
import { useT } from '../state/prefs';
import { useStudies } from '../state/studies';
import { REGISTRY, SOURCE_OPTS } from '../engine/indicators.js';
import { entryOf } from '../chart/indicatorCatalog';
import { Icon } from '../ui/Icon';

// A study's inputs, as TradingView's settings sheet shows them: one row per input, numbers as
// number fields, the price source as a menu, switches as checkboxes. Defaults restore the registry's.
const LABELS: Record<string, string> = {
  period: 'Length', source: 'Source', mult: 'StdDev / Multiplier', fast: 'Fast length', slow: 'Slow length', sig: 'Signal smoothing',
  d: '%D smoothing', k: '%K smoothing', maLength: 'MA length', tenkan: 'Conversion line', kijun: 'Base line', span: 'Leading span B',
  start: 'Start', inc: 'Increment', max: 'Maximum', anchorBars: 'Anchor (bars back)', offset: 'Offset', sigma: 'Sigma', factor: 'Timeframe ×',
};

export function StudySettings({ uid, onClose }: { uid: string; onClose(): void }) {
  const t = useT();
  const study = useStudies((s) => s.list.find((x) => x.uid === uid));
  const setInputs = useStudies((s) => s.setInputs);
  const def = study ? REGISTRY[study.key] : undefined;
  const [values, setValues] = useState<Record<string, number | string | boolean>>(() => ({ ...(def?.inputs ?? {}), ...(study?.inputs ?? {}) }));
  if (!study || !def) return null;
  const entry = entryOf(study.key);

  const apply = () => { setInputs(uid, values); onClose(); };

  return (
    <div className="scrim" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div className="dialog settings" role="dialog" aria-modal="true" aria-label={entry?.en} onKeyDown={(e) => { if (e.key === 'Escape') onClose(); if (e.key === 'Enter') apply(); }}>
        <header className="dialog-head">
          <h2>{entry?.en ?? study.key}</h2>
          <button className="ib" onClick={onClose} aria-label="close"><Icon name="ph-x" size="md" /></button>
        </header>
        <div className="settings-body">
          {Object.entries(def.inputs).length === 0 ? <p className="hint fa">{t('noResults')}</p> : null}
          {Object.entries(def.inputs).map(([key, initial]) => (
            <label key={key} className="setting-row">
              <span>{LABELS[key] ?? key}</span>
              {key === 'source' ? (
                <select value={String(values[key])} onChange={(e) => setValues({ ...values, [key]: e.target.value })}>
                  {SOURCE_OPTS.map((o) => <option key={o} value={o}>{o}</option>)}
                </select>
              ) : typeof initial === 'boolean' ? (
                <input type="checkbox" checked={Boolean(values[key])} onChange={(e) => setValues({ ...values, [key]: e.target.checked })} />
              ) : typeof initial === 'number' ? (
                <input type="number" className="num" step={Number.isInteger(initial) ? 1 : 0.01} value={Number(values[key])}
                  onChange={(e) => setValues({ ...values, [key]: e.target.value === '' ? initial : Number(e.target.value) })} />
              ) : (
                <input value={String(values[key])} onChange={(e) => setValues({ ...values, [key]: e.target.value })} />
              )}
            </label>
          ))}
        </div>
        <footer className="dialog-foot">
          <button className="btn btn-quiet" onClick={() => setValues({ ...def.inputs })}>Defaults</button>
          <span className="ws-spacer" />
          <button className="btn btn-quiet" onClick={onClose}>Cancel</button>
          <button className="btn btn-primary" onClick={apply}>Ok</button>
        </footer>
      </div>
    </div>
  );
}
