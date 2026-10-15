import { useRef, useState } from 'react';
import { Icon } from '../ui/Icon';
import { Dropdown } from '../ui/Menu';
import { useT, usePrefs } from '../state/prefs';
import { SymbolLogo } from '../ui/SymbolLogo';
import { INTERVALS, intervalLabel } from '../data/intervals';
import { intervalsFor } from '../data/candles';
import { AccountMenu } from './AccountMenu';
import { useChartBus } from '../chart/chartBus';
import { CHART_TYPES, chartTypeDef } from '../chart/chartTypes';
import { takeSnapshot } from '../chart/snapshot';
import { useReplay } from '../state/replay';
import { LayoutMenu, TemplateMenu } from './SaveMenus';
import { cellCount, useGrid, type GridId } from '../state/grid';

const FAVOURITE_INTERVALS = ['1', '5', '15', '60', '240', '1D'];
const INTERVAL_GROUPS: { fa: string; en: string; ids: string[] }[] = [
  { fa: 'دقیقه', en: 'Minutes', ids: ['1', '3', '5', '15', '30'] },
  { fa: 'ساعت', en: 'Hours', ids: ['60', '120', '240'] },
  { fa: 'روز', en: 'Days', ids: ['1D', '1W', '1M'] },
];

// TradingView's top toolbar, left to right: symbol, compare, intervals, chart type, indicators,
// templates, alert, replay, undo/redo — then layout, save, quick search, settings, fullscreen,
// snapshot, and ours: the premium button and the account.
export function TopBar({ onSymbolSearch, onIndicators, onSignIn, onCompare, onAlert, onReplay, onSettings, onPremium, onMenu }: { onSymbolSearch(): void; onIndicators(): void; onSignIn(): void; onCompare(): void; onAlert(): void; onReplay(): void; onSettings(): void; onPremium(): void; onMenu(): void }) {
  const t = useT();
  const lang = usePrefs((s) => s.lang);
  const symbol = usePrefs((s) => s.symbol);
  const interval = usePrefs((s) => s.interval);
  const setInterval = usePrefs((s) => s.setInterval);
  const chartType = usePrefs((s) => s.chartType);
  const setChartType = usePrefs((s) => s.setChartType);
  const { canUndo, canRedo, drawingLayer: layer, bumpDrawings } = useChartBus();
  const bump = () => { if (layer) bumpDrawings(layer.canUndo(), layer.canRedo(), layer.getDrawings().length); };
  const [menu, setMenu] = useState<'interval' | 'type' | 'save' | 'templates' | 'grid' | null>(null);
  const gridBtn = useRef<HTMLButtonElement>(null);
  const { grid, setGrid } = useGrid();
  const saveBtn = useRef<HTMLButtonElement>(null);
  const tplBtn = useRef<HTMLButtonElement>(null);
  const ivBtn = useRef<HTMLButtonElement>(null);
  const typeBtn = useRef<HTMLButtonElement>(null);
  const supported = intervalsFor(symbol);
  const replayOn = useReplay((r) => r.selecting || Boolean(r.state?.on));
  const label = (x: { fa: string; en: string }) => (lang === 'fa' ? x.fa : x.en);

  const fullscreen = () => {
    if (document.fullscreenElement) void document.exitFullscreen();
    else void document.documentElement.requestFullscreen?.();
  };

  return (
    <header className="ws-top" role="toolbar" aria-label={t('appName')}>
      <button className="ib menu-btn" title={t('menu')} onClick={onMenu}><Icon name="ph-list" size="md" /></button>
      <button className="ib topbar-symbol" onClick={onSymbolSearch} title={t('symbolSearch')}>
        <SymbolLogo symbol={symbol} size={20} />
        <span className="label sym">{symbol}</span>
      </button>
      <button className="ib desk-only" title={t('compare')} onClick={onCompare}><Icon name="ph-plus" size="md" /></button>
      <span className="sep" />
      {FAVOURITE_INTERVALS.filter((iv) => supported.includes(iv)).map((iv) => (
        <button key={iv} className={`ib iv${iv === interval ? ' on' : ''}`} aria-pressed={iv === interval}
          onClick={() => setInterval(iv)} title={t('interval')}>
          <span className="label num">{intervalLabel(iv)}</span>
        </button>
      ))}
      {INTERVALS.some((i) => i.id === interval) && !FAVOURITE_INTERVALS.includes(interval) ? (
        <button className="ib iv on" aria-pressed><span className="label num">{intervalLabel(interval)}</span></button>
      ) : null}
      <button ref={ivBtn} className={`ib${menu === 'interval' ? ' open' : ''}`} title={t('interval')} onClick={() => setMenu(menu === 'interval' ? null : 'interval')}>
        <Icon name="ph-caret-down" size="sm" />
      </button>
      <span className="sep" />
      <button ref={typeBtn} className={`ib${menu === 'type' ? ' open' : ''}`} title={t('chartType')} onClick={() => setMenu(menu === 'type' ? null : 'type')}>
        <Icon name={chartTypeDef(chartType).icon} />
      </button>
      <span className="sep" />
      <button className="ib" onClick={onIndicators} title={t('indicators')}>
        <Icon name="ph-chart-line-up" size="md" /><span className="label fa">{t('indicators')}</span>
      </button>
      <button ref={tplBtn} className="ib desk-only" title={t('indicatorTemplates')} onClick={() => setMenu(menu === 'templates' ? null : 'templates')}><Icon name="layout_grid" size="md" /></button>
      <span className="sep" />
      <button className="ib" title={t('alert')} onClick={onAlert}><Icon name="ph-bell" size="md" /><span className="label fa">{t('alert')}</span></button>
      <button className={`ib${replayOn ? ' on' : ''}`} title={t('replay')} onClick={onReplay}><Icon name="ph-rewind" size="md" /><span className="label fa">{t('replay')}</span></button>
      <span className="sep" />
      <button className="ib desk-only" title={t('undo')} disabled={!canUndo} onClick={() => { layer?.undo(); bump(); }}><Icon name="ph-arrow-counter-clockwise" size="md" /></button>
      <button className="ib desk-only" title={t('redo')} disabled={!canRedo} onClick={() => { layer?.redo(); bump(); }}><Icon name="ph-arrow-clockwise" size="md" /></button>
      <button className="ib phone-only" title={t('drawTrend')} onClick={() => document.body.classList.toggle('rail-open')}><Icon name="pencil" /></button>
      <span className="ws-spacer" />
      <button ref={gridBtn} className="ib desk-only" title={t('layout')} onClick={() => setMenu(menu === 'grid' ? null : 'grid')}><Icon name="ph-squares-four" size="md" /></button>
      <button ref={saveBtn} className="ib desk-only" title={t('save')} onClick={() => setMenu(menu === 'save' ? null : 'save')}><span className="label fa">{t('save')}</span><Icon name="ph-caret-down" size="sm" /></button>
      <span className="sep" />
      <button className="ib desk-only" title={t('quickSearch')} onClick={onSymbolSearch}><Icon name="search" /></button>
      <button className="ib desk-only" title={t('settings')} onClick={onSettings}><Icon name="settings2" /></button>
      <button className="ib desk-only" title={t('fullscreen')} onClick={fullscreen}><Icon name="maximize2" /></button>
      <button className="ib desk-only" title={t('snapshot')} onClick={() => void takeSnapshot()}><Icon name="camera" /></button>
      <span className="sep" />
      <button className="cta" title={t('premium')} onClick={onPremium}><span className="fa">{t('premium')}</span></button>
      <AccountMenu onSignIn={onSignIn} onPremium={onPremium} />

      {menu === 'interval' ? (
        <Dropdown anchor={ivBtn.current} onClose={() => setMenu(null)} className="iv-menu">
          {INTERVAL_GROUPS.map((g) => (
            <div key={g.en} className="menu-group">
              <div className="menu-title fa">{label(g)}</div>
              {g.ids.map((id) => {
                const ok = supported.includes(id);
                return (
                  <button key={id} role="menuitemradio" aria-checked={id === interval} disabled={!ok}
                    className={`menu-item${id === interval ? ' on' : ''}`} onClick={() => { setInterval(id); setMenu(null); }}>
                    <span className="num">{intervalLabel(id)}</span>
                    {FAVOURITE_INTERVALS.includes(id) ? <Icon name="ph-star-fill" size="sm" className="fav" /> : null}
                  </button>
                );
              })}
            </div>
          ))}
        </Dropdown>
      ) : null}
      {menu === 'grid' ? (
        <Dropdown anchor={gridBtn.current} onClose={() => setMenu(null)} align="right" className="grid-menu">
          {(['1', '2v', '2h', '3', '4'] as GridId[]).map((g) => (
            <button key={g} className={`grid-opt${grid === g ? ' on' : ''}`} onClick={() => { setGrid(g); setMenu(null); }} aria-label={g}>
              <span className={`grid-glyph g-${g}`}>{Array.from({ length: cellCount(g) }, (_, i) => <i key={i} />)}</span>
            </button>
          ))}
        </Dropdown>
      ) : null}
      {menu === 'save' ? <LayoutMenu anchor={saveBtn.current} onClose={() => setMenu(null)} /> : null}
      {menu === 'templates' ? <TemplateMenu anchor={tplBtn.current} onClose={() => setMenu(null)} /> : null}
      {menu === 'type' ? (
        <Dropdown anchor={typeBtn.current} onClose={() => setMenu(null)} className="type-menu">
          {CHART_TYPES.map((c, i) => (
            <div key={c.id}>
              {i > 0 && CHART_TYPES[i - 1]!.group !== c.group ? <div className="menu-sep" /> : null}
              <button role="menuitemradio" aria-checked={c.id === chartType} className={`menu-item${c.id === chartType ? ' on' : ''}`}
                onClick={() => { setChartType(c.id); setMenu(null); }}>
                <Icon name={c.icon} size="md" /><span className={lang === 'fa' ? 'fa' : ''}>{label(c)}</span>
              </button>
            </div>
          ))}
        </Dropdown>
      ) : null}
    </header>
  );
}
