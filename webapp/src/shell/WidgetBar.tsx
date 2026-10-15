import { Icon } from '../ui/Icon';
import { usePrefs, useT } from '../state/prefs';
import { usePanels, type PanelId } from '../state/panels';
import { usePage } from '../state/page';
import { useAlerts } from '../state/alerts';
import { faNum } from '../i18n/time';
import { startCalendarReminders } from '../data/reminders';
import { useEffect } from 'react';
import { PANEL_TITLES } from './RightPanel';
import type { StringKey } from '../i18n/strings';
import './panels-x.css';

// The right edge, in TradingView's two groups: what belongs to this chart on top (watchlist,
// alerts, objects, data window, the order ticket and the book), the market's widgets at the bottom,
// then notifications and help. Clicking the open one closes the panel. The alert icon carries the
// count of active alerts.
const TOP: { id: PanelId; icon: string; label?: StringKey }[] = [
  { id: 'watchlist', icon: 'ph-list', label: 'watchlist' },
  { id: 'alerts', icon: 'ph-alarm', label: 'alerts' },
  { id: 'objects', icon: 'ph-stack', label: 'objectTree' },
  { id: 'data', icon: 'ph-columns', label: 'dataWindow' },
  { id: 'order', icon: 'ph-shopping-cart' },
  { id: 'dom', icon: 'ph-list-numbers' },
];
const BOTTOM: { id: PanelId; icon: string; label?: StringKey }[] = [
  { id: 'signals', icon: 'ph-lightning', label: 'signals' },
  { id: 'ai', icon: 'ph-brain', label: 'aiTitle' },
  { id: 'screener', icon: 'ph-funnel', label: 'screener' },
  { id: 'hotlists', icon: 'ph-fire' },
  { id: 'calendar', icon: 'ph-calendar-blank', label: 'calendar' },
  { id: 'news', icon: 'ph-newspaper', label: 'news' },
  { id: 'notes', icon: 'ph-notebook' },
  { id: 'log', icon: 'ph-tray' },
];

export function WidgetBar() {
  const t = useT();
  const lang = usePrefs((s) => s.lang);
  const { open, toggle } = usePanels();
  const active = useAlerts((s) => s.list.filter((a) => a.active).length);
  const fired = useAlerts((s) => s.unseen ?? 0);
  useEffect(() => { startCalendarReminders(); }, []);
  const name = (it: { id: PanelId; label?: StringKey }) => (it.label ? t(it.label) : PANEL_TITLES[it.id][lang === 'fa' ? 'fa' : 'en']);
  const button = (it: { id: PanelId; icon: string; label?: StringKey }) => (
    <button key={it.id} className={`wbar-btn${open === it.id ? ' on' : ''}`} title={name(it)} data-tip={name(it)} data-panel={it.id}
      aria-pressed={open === it.id} onClick={() => toggle(it.id)}>
      <Icon name={it.icon} size="md" />
      {it.id === 'alerts' && active ? <span className="wbar-badge num" aria-label={`${active}`}>{lang === 'fa' ? faNum(active) : active}</span> : null}
      {it.id === 'log' && fired ? <span className="wbar-badge num">{lang === 'fa' ? faNum(Math.min(99, fired)) : Math.min(99, fired)}</span> : null}
    </button>
  );
  return (
    <aside className="ws-wbar" aria-label="panels">
      {TOP.map(button)}
      <span className="ws-spacer" />
      {BOTTOM.map(button)}
      <span className="wbar-sep" />
      <button className="wbar-btn" title={t('notifications')} data-tip={t('notifications')} onClick={() => usePage.getState().open('notifications')}><Icon name="ph-bell" size="md" /></button>
      <button className="wbar-btn" title={t('help')} data-tip={t('help')} onClick={() => usePage.getState().open('help')}><Icon name="help_circle" /></button>
    </aside>
  );
}
