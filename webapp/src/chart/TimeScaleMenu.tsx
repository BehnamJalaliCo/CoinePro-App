// TradingView's right-click menu on the time axis, with the old Pro-Chart's additions: reset the
// time scale, jump to the newest bar, go to a date (Alt+G), the time zone, session breaks, the
// trading sessions, day of week and the 12-hour clock, More settings.
import { usePrefs } from '../state/prefs';
import { useChartSettings, SESSION_IDS } from '../state/chartSettings';
import { useChartBus } from './chartBus';
import { useL } from '../pages/kit';
import { MI, MSep, MSub, PopMenu } from './menuKit';
import { openChartSettings, sessionName } from '../shell/SettingsDialog';
import { TIMEZONES } from '../engine/scales_crosshair.js';

function offsetLabel(tz: string): string {
  try {
    const part = new Intl.DateTimeFormat('en-US', { timeZone: tz === 'exchange' ? 'UTC' : tz, timeZoneName: 'shortOffset' }).formatToParts(new Date()).find((p) => p.type === 'timeZoneName');
    return (part?.value ?? 'UTC').replace('GMT', 'UTC');
  } catch { return 'UTC'; }
}

export function TimeScaleMenu({ at, onClose }: { at: { x: number; y: number }; onClose(): void }) {
  const L = useL();
  const cs = useChartSettings();
  const tz = usePrefs((s) => s.timezone);
  const lang = usePrefs((s) => s.lang);
  const fa = lang === 'fa';
  const api = useChartBus((b) => b.api);
  return (
    <PopMenu x={at.x} y={at.y} onClose={onClose} className="time-menu" label={L('مقیاس زمان', 'Time scale')}>
      <MI icon="ph-arrow-counter-clockwise" act="ts-reset" label={L('بازنشانی مقیاس زمان', 'Reset time scale')} onClick={() => { api?.timeScale().applyOptions({ barSpacing: 6, rightOffset: cs.rightOffset }); api?.timeScale().scrollToRealTime(); }} />
      <MI icon="ph-caret-double-right" act="ts-end" label={L('رفتن به آخرین میله', 'Scroll to the most recent bar')} hint="End" onClick={() => api?.timeScale().scrollToRealTime()} />
      <MI icon="ph-calendar-blank" act="ts-goto" label={L('رفتن به تاریخ…', 'Go to date…')} hint="Alt+G" onClick={() => window.dispatchEvent(new CustomEvent('pc:goto'))} />
      <MSep />
      <MSub icon="ph-globe" act="ts-tz" label={L('منطقه‌ی زمانی', 'Time zone')} hint={offsetLabel(tz)}>
        {[{ id: 'exchange', label: 'بورس' }, ...TIMEZONES].map((z) => (
          <MI key={z.id} act={`tz-${z.id}`} checked={z.id === tz} label={<><span className="num muted">({offsetLabel(z.id)})</span> {z.id === 'exchange' ? L('بورس', 'Exchange') : fa ? z.label : z.id.split('/').pop()!.replace('_', ' ')}</>}
            onClick={() => usePrefs.getState().setTimezone(z.id)} />
        ))}
      </MSub>
      <MI act="ts-breaks" checked={cs.sessionBreaks} label={L('جداکننده‌ی روزها', 'Session breaks')} onClick={() => cs.set({ sessionBreaks: !cs.sessionBreaks })} />
      <MSub act="ts-sessions" label={L('جلسه‌های معاملاتی', 'Trading sessions')}>
        <MI act="sessions-on" checked={cs.sessionsOn} keep label={L('نمایش سایه‌ی جلسه‌ها', 'Show session shading')} onClick={() => cs.set({ sessionsOn: !cs.sessionsOn })} />
        <MSep />
        {SESSION_IDS.map((id) => (
          <MI key={id} act={`session-${id}`} keep checked={cs.sessions.includes(id)} label={sessionName(id, fa)}
            onClick={() => cs.set({ sessionsOn: true, sessions: cs.sessions.includes(id) ? cs.sessions.filter((x) => x !== id) : [...cs.sessions, id] })} />
        ))}
        <MI act="session-overlaps" keep checked={cs.sessionOverlaps} label={L('هم‌پوشانی‌ها', 'Overlaps')} onClick={() => cs.set({ sessionOverlaps: !cs.sessionOverlaps })} />
        <MSep />
        <MI act="sessions-all" keep label={L('همه', 'All')} onClick={() => cs.set({ sessionsOn: true, sessions: [...SESSION_IDS] })} />
        <MI act="sessions-none" keep label={L('هیچ‌کدام', 'None')} onClick={() => cs.set({ sessions: [] })} />
      </MSub>
      <MSep />
      <MI act="ts-dow" checked={cs.dayOfWeek} label={L('روز هفته روی برچسب‌ها', 'Day of week on labels')} onClick={() => cs.set({ dayOfWeek: !cs.dayOfWeek })} />
      <MI act="ts-12h" checked={cs.hour12} label={L('ساعت ۱۲ ساعته', '12-hour clock')} onClick={() => cs.set({ hour12: !cs.hour12 })} />
      <MSep />
      <MI icon="ph-gear" act="ts-settings" label={L('تنظیمات بیشتر…', 'More settings…')} onClick={() => openChartSettings('scales')} />
    </PopMenu>
  );
}
