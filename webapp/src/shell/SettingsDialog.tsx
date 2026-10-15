import { useState } from 'react';
import { useChartSettings, type ChartSettings } from '../state/chartSettings';
import { usePrefs, useT, type Theme } from '../state/prefs';
import { Icon } from '../ui/Icon';
import { useChartBus } from '../chart/chartBus';

// Candle colour pairs: TradingView's, the reversed pair some markets read, colour-blind and monochrome.
const PRESETS = [
  { name: 'Classic', up: '#089981', down: '#F23645' },
  { name: 'Reversed', up: '#F23645', down: '#089981' },
  { name: 'Colour-blind', up: '#2962FF', down: '#FF9800' },
  { name: 'Monochrome', up: '#787B86', down: '#131722' },
  { name: 'Vivid', up: '#26A69A', down: '#EF5350' },
];

type Tab = 'symbol' | 'status' | 'appearance';

// TradingView's chart settings, in its tabs: Symbol (candle colours), Status line (what the legend
// shows), Appearance (theme, grid, crosshair, price line).
export function SettingsDialog({ onClose }: { onClose(): void }) {
  const t = useT();
  const s = useChartSettings();
  const { theme, setTheme } = usePrefs();
  const invert = useChartBus((b) => b.invert);
  const setInvert = useChartBus((b) => b.setInvert);
  const [tab, setTab] = useState<Tab>('symbol');
  const tabs: { id: Tab; label: string; icon: string }[] = [
    { id: 'symbol', label: 'Symbol', icon: 'chart_candles' },
    { id: 'status', label: 'Status line', icon: 'ph-info' },
    { id: 'appearance', label: 'Appearance', icon: 'ph-sun' },
  ];
  const toggle = (k: keyof ChartSettings) => (
    <input type="checkbox" checked={Boolean(s[k])} onChange={(e) => s.set({ [k]: e.target.checked } as Partial<ChartSettings>)} />
  );
  return (
    <div className="scrim" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div className="dialog chart-settings" role="dialog" aria-modal="true" aria-label={t('settings')} onKeyDown={(e) => { if (e.key === 'Escape') onClose(); }}>
        <header className="dialog-head">
          <h2 className="fa">{t('settings')}</h2>
          <button className="ib" onClick={onClose} aria-label="close"><Icon name="ph-x" size="md" /></button>
        </header>
        <div className="cs-body">
          <nav className="ind-cats">
            {tabs.map((x) => (
              <button key={x.id} className={`ind-cat${tab === x.id ? ' on' : ''}`} onClick={() => setTab(x.id)}><Icon name={x.icon} size="sm" /><span>{x.label}</span></button>
            ))}
          </nav>
          <div className="settings-body">
            {tab === 'symbol' ? (
              <>
                <label className="setting-row"><span>Up colour</span><input type="color" value={s.up} onChange={(e) => s.set({ up: e.target.value })} /></label>
                <label className="setting-row"><span>Down colour</span><input type="color" value={s.down} onChange={(e) => s.set({ down: e.target.value })} /></label>
                <div className="setting-row"><span>Colour presets</span>
                  <span className="swatches">{PRESETS.map((p) => (
                    <button key={p.name} className={`preset${s.up === p.up && s.down === p.down ? ' on' : ''}`} title={p.name} onClick={() => s.set({ up: p.up, down: p.down })}>
                      <i style={{ background: p.up }} /><i style={{ background: p.down }} />
                    </button>
                  ))}</span>
                </div>
                <label className="setting-row"><span>Price line</span>{toggle('priceLine')}</label>
                <label className="setting-row"><span>Previous close line</span>{toggle('prevClose')}</label>
                <label className="setting-row"><span>Countdown to bar close</span>{toggle('countdown')}</label>
              </>
            ) : null}
            {tab === 'status' ? (
              <>
                <label className="setting-row"><span>OHLC values</span>{toggle('showOhlc')}</label>
                <label className="setting-row"><span>Bar change values</span>{toggle('showChange')}</label>
              </>
            ) : null}
            {tab === 'appearance' ? (
              <>
                <label className="setting-row"><span className="fa">{t('theme')}</span>
                  <select value={theme} onChange={(e) => setTheme(e.target.value as Theme)}>
                    <option value="system">{t('system')}</option><option value="light">{t('light')}</option><option value="dark">{t('dark')}</option>
                  </select>
                </label>
                <label className="setting-row"><span>Grid lines</span>
                  <select value={s.grid} onChange={(e) => s.set({ grid: e.target.value as ChartSettings['grid'] })}>
                    <option value="both">Vert and horz</option><option value="vertical">Vert only</option><option value="horizontal">Horz only</option><option value="none">None</option>
                  </select>
                </label>
                <label className="setting-row"><span>Session breaks</span>{toggle('sessionBreaks')}</label>
                <label className="setting-row"><span>Auto: RSI divergences</span>{toggle('autoDivergence')}</label>
                <label className="setting-row"><span>Auto: harmonic patterns</span>{toggle('autoHarmonic')}</label>
                <label className="setting-row"><span>Auto: price gaps</span>{toggle('autoGaps')}</label>
                <label className="setting-row"><span>Watermark</span>{toggle('watermark')}</label>
                <label className="setting-row"><span>Economic events on the chart</span>{toggle('events')}</label>
                <label className="setting-row"><span>Invert price scale</span><input type="checkbox" checked={invert} onChange={(e) => setInvert(e.target.checked)} /></label>
                <label className="setting-row"><span>Crosshair</span>
                  <select value={s.crosshair} onChange={(e) => s.set({ crosshair: e.target.value as ChartSettings['crosshair'] })}>
                    <option value="normal">Normal</option><option value="magnet">Magnet</option>
                  </select>
                </label>
              </>
            ) : null}
          </div>
        </div>
        <footer className="dialog-foot">
          <button className="btn btn-quiet" onClick={s.reset}>Defaults</button>
          <span className="ws-spacer" />
          <button className="btn btn-primary" onClick={onClose}>Ok</button>
        </footer>
      </div>
    </div>
  );
}
