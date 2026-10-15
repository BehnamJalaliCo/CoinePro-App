import { useEffect, useRef, useState } from 'react';
import { Icon } from '../ui/Icon';
import { Dropdown } from '../ui/Menu';
import { useT, usePrefs } from '../state/prefs';
import { SymbolLogo } from '../ui/SymbolLogo';
import { AccountMenu } from './AccountMenu';
import { useChartBus } from '../chart/chartBus';
import { CHART_TYPES, chartTypeDef, type ChartType } from '../chart/chartTypes';
import { useReplay } from '../state/replay';
import { useAlerts } from '../state/alerts';
import { LayoutMenu, TemplateMenu, type LayoutDialogMode } from './SaveMenus';
import { LayoutDialog } from './LayoutDialog';
import { GridMenu, GridGlyph } from './GridMenu';
import { SnapshotMenu } from './SnapshotMenu';
import { IntervalControl } from './IntervalMenu';
import { QuickSearch, type QuickActions } from './QuickSearch';
import { MoreSheet } from './MoreSheet';
import { Toasts, toast } from './toast';
import { applyChartType, useGrid } from '../state/grid';
import { startLayoutWatch, useLayouts } from '../state/layouts';
import { isExpression } from '../data/symbols';
import { faNum } from '../i18n/time';
import { useL } from '../pages/kit';
import { openTrade, publishIdea } from './publish';
import './topbar.css';
import { ChartTypeParamsButton } from '../chart/ChartTypeParams';

type MenuId = 'type' | 'layout' | 'templates' | 'grid' | 'snapshot' | null;

/** The active chart's symbol and type: the top bar shows and drives the chart the reader picked. */
function useActive(): { symbol: string; interval: string; chartType: ChartType } {
  const p = usePrefs();
  const active = useGrid((g) => g.active);
  const cell = useGrid((g) => (g.active > 0 ? g.cells[g.active - 1] : undefined));
  return active > 0 && cell ? { symbol: cell.symbol, interval: cell.interval, chartType: cell.chartType ?? 'candles' } : { symbol: p.symbol, interval: p.interval, chartType: p.chartType };
}

const fullscreen = () => {
  if (document.fullscreenElement) void document.exitFullscreen();
  else void document.documentElement.requestFullscreen?.();
};

// TradingView's top toolbar, left to right: menu, symbol, compare, intervals, chart type,
// indicators, templates, alert, replay, undo/redo — then the multi-chart layout, the layout's name
// and Save, quick search, settings, fullscreen, snapshot, publish, Trade, and ours: premium and
// the account. On a phone the desktop-only buttons move into the "⋯" sheet.
export function TopBar({ onSymbolSearch, onIndicators, onSignIn, onCompare, onAlert, onReplay, onSettings, onPremium, onMenu }: { onSymbolSearch(): void; onIndicators(): void; onSignIn(): void; onCompare(): void; onAlert(): void; onReplay(): void; onSettings(): void; onPremium(): void; onMenu(): void }) {
  const t = useT();
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const fa = lang === 'fa';
  const favTypes = usePrefs((s) => s.favChartTypes);
  const { symbol, chartType } = useActive();
  const { canUndo, canRedo, drawingLayer: layer, bumpDrawings } = useChartBus();
  const bump = () => { if (layer) bumpDrawings(layer.canUndo(), layer.canRedo(), layer.getDrawings().length); };
  const [menu, setMenu] = useState<MenuId>(null);
  const [dialog, setDialog] = useState<LayoutDialogMode | null>(null);
  const [quick, setQuick] = useState(false);
  const [more, setMore] = useState(false);
  const gridBtn = useRef<HTMLButtonElement>(null);
  const layoutBtn = useRef<HTMLButtonElement>(null);
  const tplBtn = useRef<HTMLButtonElement>(null);
  const typeBtn = useRef<HTMLButtonElement>(null);
  const snapBtn = useRef<HTMLButtonElement>(null);
  const grid = useGrid((g) => g.grid);
  const replayOn = useReplay((r) => r.selecting || Boolean(r.state?.on));
  const alerts = useAlerts((s) => s.list.filter((a) => a.active).length);
  const current = useLayouts((s) => s.current);
  const dirty = useLayouts((s) => s.dirty);
  const toggle = (m: MenuId) => setMenu(menu === m ? null : m);

  // Autosave and the unsaved mark follow every change the layout holds.
  useEffect(() => startLayoutWatch(), []);
  // Ctrl+K and the menus open these from elsewhere.
  useEffect(() => {
    const q = () => setQuick(true);
    const d = (e: Event) => setDialog(((e as CustomEvent<{ mode?: LayoutDialogMode }>).detail?.mode) ?? 'save');
    window.addEventListener('pc:quick-search', q);
    window.addEventListener('pc:layout-dialog', d);
    return () => { window.removeEventListener('pc:quick-search', q); window.removeEventListener('pc:layout-dialog', d); };
  }, []);

  const save = () => { if (useLayouts.getState().save()) toast(L('چیدمان ذخیره شد', 'Layout saved')); };
  const actions: QuickActions = { indicators: onIndicators, compare: onCompare, alert: onAlert, replay: onReplay, settings: onSettings, symbolSearch: onSymbolSearch };
  const label = (x: { fa: string; en: string }) => (fa ? x.fa : x.en);
  const toggleFavType = (id: ChartType) => {
    const p = usePrefs.getState();
    p.setFavChartTypes(p.favChartTypes.includes(id) ? p.favChartTypes.filter((x) => x !== id) : [...p.favChartTypes, id]);
  };

  return (
    <header className="ws-top" role="toolbar" aria-label={t('appName')}>
      <button className="ib menu-btn" title={t('menu')} onClick={onMenu}><Icon name="ph-list" size="md" /></button>
      <button className="ib topbar-symbol" onClick={onSymbolSearch} title={t('symbolSearch')}>
        {isExpression(symbol) ? <span className="logo logo-expr" aria-hidden><Icon name="ph-function" size="sm" /></span> : <SymbolLogo symbol={symbol} size={20} />}
        <span className="label sym">{symbol}</span>
      </button>
      <button className="ib desk-only tb-d compare-btn" title={t('compare')} onClick={onCompare}><Icon name="ph-plus" size="md" /></button>
      <span className="sep" />
      <IntervalControl />
      <span className="sep" />
      <button ref={typeBtn} className={`ib type-btn${menu === 'type' ? ' open' : ''}`} title={t('chartType')} aria-haspopup="menu" aria-expanded={menu === 'type'} onClick={() => toggle('type')}>
        <Icon name={chartTypeDef(chartType).icon} />
      </button>
      {favTypes.filter((id) => id !== chartType).map((id) => (
        <button key={id} className="ib desk-only fav-type tb-b" title={label(chartTypeDef(id))} onClick={() => applyChartType(id)}><Icon name={chartTypeDef(id).icon} /></button>
      ))}
      <ChartTypeParamsButton />
      <span className="sep" />
      <button className="ib" onClick={onIndicators} title={t('indicators')}>
        <Icon name="ph-chart-line-up" size="md" /><span className="label fa tb-lbl">{t('indicators')}</span>
      </button>
      <button ref={tplBtn} className="ib desk-only tb-c" title={t('indicatorTemplates')} onClick={() => toggle('templates')}><Icon name="layout_grid" size="md" /></button>
      <span className="sep" />
      <button className="ib alert-btn" title={t('alert')} onClick={onAlert}>
        <Icon name="ph-bell" size="md" /><span className="label fa tb-lbl">{t('alert')}</span>
        {alerts ? <span className="badge num" aria-label={L(`${faNum(alerts)} هشدار فعال`, `${alerts} active alerts`)}>{fa ? faNum(alerts) : alerts}</span> : null}
      </button>
      <button className={`ib${replayOn ? ' on' : ''}`} title={t('replay')} onClick={onReplay}><Icon name="ph-rewind" size="md" /><span className="label fa tb-lbl">{t('replay')}</span></button>
      <span className="sep" />
      <button className="ib desk-only tb-c" title={t('undo')} disabled={!canUndo} onClick={() => { layer?.undo(); bump(); }}><Icon name="ph-arrow-counter-clockwise" size="md" /></button>
      <button className="ib desk-only tb-c" title={t('redo')} disabled={!canRedo} onClick={() => { layer?.redo(); bump(); }}><Icon name="ph-arrow-clockwise" size="md" /></button>
      <button className="ib phone-only" title={t('drawTrend')} onClick={() => document.body.classList.toggle('rail-open')}><Icon name="pencil" /></button>
      <span className="ws-spacer" />
      <button ref={gridBtn} className={`ib desk-only tb-c grid-btn${menu === 'grid' ? ' open' : ''}`} title={t('layout')} aria-haspopup="menu" aria-expanded={menu === 'grid'} onClick={() => toggle('grid')}>
        <GridGlyph id={grid} />
      </button>
      <span className="save-group desk-only tb-c">
        <button className={`ib layout-name${menu === 'layout' ? ' open' : ''}`} ref={layoutBtn} title={L('منوی چیدمان', 'Manage layouts')} aria-haspopup="menu" aria-expanded={menu === 'layout'} onClick={() => toggle('layout')}>
          <span className="label" dir="auto">{current ?? L('بی‌نام', 'Unnamed')}</span>
          <Icon name={menu === 'layout' ? 'ph-caret-up' : 'ph-caret-down'} size="sm" />
        </button>
        <button className={`ib save-btn${dirty || !current ? ' dirty' : ''}`} title={t('save')} onClick={save}>
          <span className="label fa">{t('save')}</span>
        </button>
      </span>
      <span className="sep desk-only tb-c" />
      <button className="ib desk-only tb-b qs-btn" title={`${t('quickSearch')} (Ctrl+K)`} onClick={() => setQuick(true)}><Icon name="search" size="md" /></button>
      <button className="ib desk-only tb-c" title={t('settings')} onClick={onSettings}><Icon name="settings2" size="md" /></button>
      <button className="ib desk-only tb-b" title={t('fullscreen')} onClick={fullscreen}><Icon name="maximize2" size="md" /></button>
      <button ref={snapBtn} className={`ib desk-only tb-c snap-btn${menu === 'snapshot' ? ' open' : ''}`} title={t('snapshot')} aria-haspopup="menu" aria-expanded={menu === 'snapshot'} onClick={() => toggle('snapshot')}><Icon name="camera" size="md" /></button>
      <button className="ib desk-only tb-b publish-btn" title={L('انتشار ایده در انجمن', 'Publish idea')} onClick={() => void publishIdea()}><Icon name="ph-lightbulb" size="md" /></button>
      <span className="sep desk-only tb-b" />
      <button className="trade-btn desk-only tb-b" title={L('معامله', 'Trade')} onClick={openTrade}><span className="fa">{L('معامله', 'Trade')}</span></button>
      <button className="cta" title={t('premium')} onClick={onPremium}><span className="fa">{t('premium')}</span></button>
      <AccountMenu onSignIn={onSignIn} onPremium={onPremium} />
      <button className="ib more-btn" title={L('بیشتر', 'More')} aria-haspopup="dialog" onClick={() => setMore(true)}><Icon name="ph-dots-three" size="md" /></button>

      {menu === 'grid' ? <GridMenu anchor={gridBtn.current} onClose={() => setMenu(null)} /> : null}
      {menu === 'layout' ? <LayoutMenu anchor={layoutBtn.current} onClose={() => setMenu(null)} onDialog={setDialog} /> : null}
      {menu === 'templates' ? <TemplateMenu anchor={tplBtn.current} onClose={() => setMenu(null)} /> : null}
      {menu === 'snapshot' ? <SnapshotMenu anchor={snapBtn.current} onClose={() => setMenu(null)} /> : null}
      {menu === 'type' ? (
        <Dropdown anchor={typeBtn.current} onClose={() => setMenu(null)} className="type-menu tvm">
          <div dir={fa ? 'rtl' : 'ltr'}>
            {CHART_TYPES.map((c, i) => (
              <div key={c.id}>
                {i > 0 && CHART_TYPES[i - 1]!.group !== c.group ? <div className="menu-sep" /> : null}
                <div role="menuitemradio" aria-checked={c.id === chartType} tabIndex={-1} className={`menu-item type-row${c.id === chartType ? ' on' : ''}`}
                  onClick={() => { applyChartType(c.id); setMenu(null); }}>
                  <Icon name={c.icon} size="md" /><span className={fa ? 'fa' : ''}>{label(c)}</span>
                  <button className={`lt iv-star${favTypes.includes(c.id) ? ' fav' : ''}`} aria-pressed={favTypes.includes(c.id)}
                    title={favTypes.includes(c.id) ? L('برداشتن از نوار', 'Remove from favorites') : L('افزودن به نوار', 'Add to favorites')}
                    onClick={(e) => { e.stopPropagation(); toggleFavType(c.id); }}>
                    <Icon name={favTypes.includes(c.id) ? 'ph-star-fill' : 'ph-star'} size="sm" />
                  </button>
                </div>
              </div>
            ))}
          </div>
        </Dropdown>
      ) : null}
      {dialog ? <LayoutDialog mode={dialog} onClose={() => setDialog(null)} /> : null}
      {quick ? <QuickSearch actions={actions} onClose={() => setQuick(false)} /> : null}
      {more ? <MoreSheet onClose={() => setMore(false)} actions={actions} onDialog={(m) => { setMore(false); setDialog(m); }} onQuick={() => { setMore(false); setQuick(true); }} /> : null}
      <Toasts />
    </header>
  );
}
