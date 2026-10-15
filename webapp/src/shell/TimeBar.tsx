import { useEffect, useState } from 'react';
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

function Clock() {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => { const id = setInterval(() => setNow(new Date()), 1000); return () => clearInterval(id); }, []);
  const hh = String(now.getUTCHours()).padStart(2, '0');
  const mm = String(now.getUTCMinutes()).padStart(2, '0');
  const ss = String(now.getUTCSeconds()).padStart(2, '0');
  return <span className="timebar-clock num">{hh}:{mm}:{ss} UTC</span>;
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
