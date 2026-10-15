import { Icon } from '../ui/Icon';
import { useT, usePrefs } from '../state/prefs';
import { SymbolLogo } from '../ui/SymbolLogo';
import { INTERVALS, intervalLabel } from '../data/intervals';
import { AccountMenu } from './AccountMenu';

const FAVOURITE_INTERVALS = ['1', '5', '15', '60', '240', '1D'];

export function TopBar({ onSymbolSearch, onIndicators, onSignIn }: { onSymbolSearch(): void; onIndicators(): void; onSignIn(): void }) {
  const t = useT();
  const symbol = usePrefs((s) => s.symbol);
  const interval = usePrefs((s) => s.interval);
  const setInterval = usePrefs((s) => s.setInterval);
  return (
    <header className="ws-top" role="toolbar" aria-label={t('appName')}>
      <button className="ib topbar-symbol" onClick={onSymbolSearch} title={t('symbolSearch')}>
        <SymbolLogo symbol={symbol} size={20} />
        <span className="label sym">{symbol}</span>
      </button>
      <button className="ib" title={t('compare')}><Icon name="ph-plus" size="md" /></button>
      <span className="sep" />
      {FAVOURITE_INTERVALS.map((iv) => (
        <button key={iv} className={`ib iv${iv === interval ? ' on' : ''}`} aria-pressed={iv === interval}
          onClick={() => setInterval(iv)} title={t('interval')}>
          <span className="label num">{intervalLabel(iv)}</span>
        </button>
      ))}
      {INTERVALS.some((i) => i.id === interval) && !FAVOURITE_INTERVALS.includes(interval) ? (
        <button className="ib iv on" aria-pressed><span className="label num">{intervalLabel(interval)}</span></button>
      ) : null}
      <button className="ib" title={t('interval')}><Icon name="ph-caret-down" size="sm" /></button>
      <span className="sep" />
      <button className="ib" title={t('chartType')}><Icon name="chart_candles" /></button>
      <span className="sep" />
      <button className="ib" onClick={onIndicators} title={t('indicators')}>
        <Icon name="ph-chart-line-up" size="md" /><span className="label fa">{t('indicators')}</span>
      </button>
      <button className="ib" title={t('indicatorTemplates')}><Icon name="layout_grid" size="md" /></button>
      <span className="sep" />
      <button className="ib" title={t('alert')}><Icon name="ph-bell" size="md" /><span className="label fa">{t('alert')}</span></button>
      <button className="ib" title={t('replay')}><Icon name="ph-rewind" size="md" /><span className="label fa">{t('replay')}</span></button>
      <span className="sep" />
      <button className="ib" title={t('undo')} disabled><Icon name="ph-arrow-counter-clockwise" size="md" /></button>
      <button className="ib" title={t('redo')} disabled><Icon name="ph-arrow-clockwise" size="md" /></button>
      <span className="ws-spacer" />
      <button className="ib" title={t('layout')}><Icon name="ph-squares-four" size="md" /></button>
      <button className="ib" title={t('save')}><span className="label fa">{t('save')}</span><Icon name="ph-caret-down" size="sm" /></button>
      <span className="sep" />
      <button className="ib" title={t('quickSearch')}><Icon name="search" /></button>
      <button className="ib" title={t('settings')}><Icon name="settings2" /></button>
      <button className="ib" title={t('fullscreen')}><Icon name="maximize2" /></button>
      <button className="ib" title={t('snapshot')}><Icon name="camera" /></button>
      <span className="sep" />
      <button className="cta" title={t('premium')}><span className="fa">{t('premium')}</span></button>
      <AccountMenu onSignIn={onSignIn} />
    </header>
  );
}
