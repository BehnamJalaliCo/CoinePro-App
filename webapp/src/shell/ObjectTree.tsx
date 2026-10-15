import { useChartBus } from '../chart/chartBus';
import { usePrefs, useT } from '../state/prefs';
import { DrawingLayer } from '../engine/drawings.js';
import { cleanFa } from '../chart/indicatorCatalog';
import { TOOL_GROUPS } from '../chart/tools';
import { useStudies, VOLUME } from '../state/studies';
import { entryOf } from '../chart/indicatorCatalog';
import { Icon } from '../ui/Icon';

const toolName = (type: string, lang: 'fa' | 'en') => {
  for (const g of TOOL_GROUPS) { const x = g.tools.find((tt) => tt.id === type); if (x) return lang === 'fa' ? x.fa : x.en; }
  return lang === 'fa' ? cleanFa(DrawingLayer.label(type)) : type;
};

// TradingView's object tree: the studies, then every drawing on this symbol, each with show/hide,
// lock and remove. Selecting a drawing here selects it on the chart.
export function ObjectTree() {
  const t = useT();
  const lang = usePrefs((s) => s.lang);
  const layer = useChartBus((s) => s.drawingLayer);
  useChartBus((s) => s.drawingVersion);
  const studies = useStudies((s) => s.list);
  const toggleStudy = useStudies((s) => s.toggle);
  const removeStudy = useStudies((s) => s.remove);
  const drawings = layer?.getDrawings() ?? [];
  const bump = () => { if (layer) useChartBus.getState().bumpDrawings(layer.canUndo(), layer.canRedo(), layer.getDrawings().length); };

  return (
    <section className="panel-generic objects">
      <header className="panel-head"><span className="fa">{t('objectTree')}</span></header>
      <div className="panel-body">
        {studies.map((s) => (
          <div key={s.uid} className={`obj-row${s.hidden ? ' off' : ''}`}>
            <Icon name="ph-chart-line-up" size="sm" />
            <span className="obj-name">{s.key === VOLUME ? 'Volume' : entryOf(s.key)?.en ?? s.key}</span>
            <span className="obj-tools">
              <button className="lt" onClick={() => toggleStudy(s.uid)} title={t('show')}><Icon name={s.hidden ? 'eye_off' : 'ph-eye'} size="sm" /></button>
              <button className="lt" onClick={() => removeStudy(s.uid)} title={t('remove')}><Icon name="ph-trash" size="sm" /></button>
            </span>
          </div>
        ))}
        {studies.length && drawings.length ? <div className="menu-sep" /> : null}
        {!drawings.length ? <div className="empty fa">{t('objectsEmpty')}</div> : null}
        {drawings.map((d, i) => (
          <div key={i} className={`obj-row${d.visible === false ? ' off' : ''}${layer?.selected === i ? ' on' : ''}`}
            onClick={() => { if (layer) { layer.selected = i; layer.render(); bump(); } }}>
            <Icon name={`tool_${d.type}`} size="sm" />
            <span className={`obj-name${lang === 'fa' ? ' fa' : ''}`}>{toolName(d.type, lang)}</span>
            <span className="obj-tools">
              <button className="lt" title={t('show')} onClick={(e) => { e.stopPropagation(); layer?.setStyle(i, { visible: d.visible === false }); bump(); }}><Icon name={d.visible === false ? 'eye_off' : 'ph-eye'} size="sm" /></button>
              <button className="lt" title={t('lock')} onClick={(e) => { e.stopPropagation(); layer?.setStyle(i, { locked: !d.locked }); bump(); }}><Icon name={d.locked ? 'lock' : 'unlock'} size="sm" /></button>
              <button className="lt" title={t('remove')} onClick={(e) => { e.stopPropagation(); layer?.removeAt(i); bump(); }}><Icon name="ph-trash" size="sm" /></button>
            </span>
          </div>
        ))}
      </div>
    </section>
  );
}
