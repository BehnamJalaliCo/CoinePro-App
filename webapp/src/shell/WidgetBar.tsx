import { Icon } from '../ui/Icon';
import { useT } from '../state/prefs';
import { usePanels, type PanelId } from '../state/panels';
import type { StringKey } from '../i18n/strings';

// The right edge: one icon per panel, TradingView's order. Clicking the open one closes the panel.
const ITEMS: { id: PanelId; icon: string; label: StringKey }[] = [
  { id: 'watchlist', icon: 'ph-list', label: 'watchlist' },
  { id: 'alerts', icon: 'ph-bell', label: 'alerts' },
  { id: 'signals', icon: 'ph-lightning', label: 'signals' },
  { id: 'ai', icon: 'ph-brain', label: 'aiTitle' },
  { id: 'objects', icon: 'ph-rows', label: 'objectTree' },
  { id: 'data', icon: 'ph-columns', label: 'dataWindow' },
  { id: 'news', icon: 'ph-newspaper', label: 'news' },
  { id: 'calendar', icon: 'ph-calendar-blank', label: 'calendar' },
  { id: 'screener', icon: 'ph-funnel', label: 'screener' },
];

export function WidgetBar() {
  const t = useT();
  const { open, toggle } = usePanels();
  return (
    <aside className="ws-wbar" aria-label="panels">
      {ITEMS.map((it) => (
        <button key={it.id} className={`wbar-btn${open === it.id ? ' on' : ''}`} title={t(it.label)}
          aria-pressed={open === it.id} onClick={() => toggle(it.id)}>
          <Icon name={it.icon} size="md" />
        </button>
      ))}
      <span className="ws-spacer" />
      <button className="wbar-btn" title={t('notifications')}><Icon name="ph-bell-ringing" size="md" /></button>
      <button className="wbar-btn" title={t('help')}><Icon name="help_circle" /></button>
    </aside>
  );
}
