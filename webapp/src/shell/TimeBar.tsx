import { useEffect, useRef, useState } from 'react';
import { Dropdown } from '../ui/Menu';
import { TIMEZONES } from '../engine/scales_crosshair.js';
import { usePrefs, useT } from '../state/prefs';
import { useChartBus, showSpan } from '../chart/chartBus';
import { Icon } from '../ui/Icon';

// TradingView's bar under the chart: date ranges on the left (each picks an interval and shows
// that span), go-to-date, then the clock and the scale switches on the right.
const RANGES: { id: string; interval: string; span: number }[] = [
  { id: '1D', interval: '1', span: 86400 },
  { id: '5D', interval: '5', span: 5 * 86400 },
  { id: '1M', interval: '30', span: 30 * 86400 },
  { id: '3M', interval: '60', span: 91 * 86400 },
  { id: '6M', interval: '120', span: 182 * 86400 },
  { id: 'YTD', interval: '1D', span: 0 },
  { id: '1Y', interval: '1D', span: 365 * 86400 },
  { id: '5Y', interval: '1W', span: 5 * 365 * 86400 },
  { id: 'All', interval: '1M', span: Number.MAX_SAFE_INTEGER },
];

function ytdSpan(): number {
  const now = new Date();
  return Math.floor((now.getTime() - Date.UTC(now.getUTCFullYear(), 0, 1)) / 1000);
}

function offsetLabel(tz: string): string {
  try {
    const part = new Intl.DateTimeFormat('en-US', { timeZone: tz, timeZoneName: 'shortOffset' }).formatToParts(new Date()).find((p) => p.type === 'timeZoneName');
    return (part?.value ?? 'UTC').replace('GMT', 'UTC').replace(/^UTC$/, 'UTC');
  } catch { return 'UTC'; }
}

// The clock at the right of the time bar, in the chosen time zone; a click lists the zones.
function Clock() {
  const t = useT();
  const lang = usePrefs((s) => s.lang);
  const tz = usePrefs((s) => s.timezone);
  const setTz = usePrefs((s) => s.setTimezone);
  const [now, setNow] = useState(() => new Date());
  const [open, setOpen] = useState(false);
  const btn = useRef<HTMLButtonElement>(null);
  useEffect(() => { const id = setInterval(() => setNow(new Date()), 1000); return () => clearInterval(id); }, []);
  let time = '';
  try { time = new Intl.DateTimeFormat('en-GB', { timeZone: tz, hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false }).format(now); } catch { time = now.toISOString().slice(11, 19); }
  return (
    <>
      <button ref={btn} className="ib timebar-clock num" title={t('timezone')} onClick={() => setOpen(!open)}>{time} ({offsetLabel(tz)})</button>
      {open ? (
        <Dropdown anchor={btn.current} onClose={() => setOpen(false)} align="right" className="tz-menu">
          {TIMEZONES.map((z) => (
            <button key={z.id} className={`menu-item${z.id === tz ? ' on' : ''}`} onClick={() => { setTz(z.id); setOpen(false); }}>
              <span className="num tz-off">({offsetLabel(z.id)})</span><span className={lang === 'fa' ? 'fa' : ''}>{lang === 'fa' ? z.label : z.id.split('/').pop()!.replace('_', ' ')}</span>
            </button>
          ))}
        </Dropdown>
      ) : null}
    </>
  );
}

export function TimeBar({ supported }: { supported: (interval: string) => boolean }) {
  const t = useT();
  const setInterval = usePrefs((s) => s.setInterval);
  const interval = usePrefs((s) => s.interval);
  const { api, bars, scale, autoScale, setScale, setAutoScale, setPendingSpan } = useChartBus();

  const pick = (r: (typeof RANGES)[number]) => {
    let iv = r.interval;
    if (!supported(iv)) iv = ['5', '15', '60', '1D'].find(supported) ?? iv;
    const span = r.id === 'YTD' ? ytdSpan() : r.span;
    if (iv === interval && api) {
      if (span === Number.MAX_SAFE_INTEGER) api.timeScale().fitContent(); else showSpan(api, bars, span);
    } else {
      setPendingSpan(span === Number.MAX_SAFE_INTEGER ? null : span);
      setInterval(iv);
    }
  };

  return (
    <div className="ws-timebar" role="toolbar">
      {RANGES.map((r) => (
        <button key={r.id} className="ib range num" onClick={() => pick(r)}>{r.id}</button>
      ))}
      <span className="sep" />
      <button className="ib" title={t('goToDate')}><Icon name="ph-calendar-blank" size="sm" /></button>
      <span className="ws-spacer" />
      <Clock />
      <span className="sep" />
      <button className={`ib scale${scale === 'percent' ? ' on' : ''}`} aria-pressed={scale === 'percent'}
        onClick={() => setScale(scale === 'percent' ? 'normal' : 'percent')}>%</button>
      <button className={`ib scale${scale === 'log' ? ' on' : ''}`} aria-pressed={scale === 'log'}
        onClick={() => setScale(scale === 'log' ? 'normal' : 'log')}>log</button>
      <button className={`ib scale${autoScale ? ' on' : ''}`} aria-pressed={autoScale}
        onClick={() => setAutoScale(!autoScale)}>auto</button>
    </div>
  );
}
