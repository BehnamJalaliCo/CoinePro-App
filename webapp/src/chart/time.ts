// The time axis and crosshair label in the reader's time zone, Latin digits and English month
// names (figures are Latin, as on TradingView). Daily and longer bars keep their UTC date.
import type { DeepPartial, TimeChartOptions } from 'lightweight-charts';

export function timeOptions(timezone: string, interval: string): DeepPartial<TimeChartOptions> {
  const daily = ['1D', '1W', '1M'].includes(interval);
  const tz = daily ? 'UTC' : timezone;
  const time = new Intl.DateTimeFormat('en-GB', { timeZone: tz, hour: '2-digit', minute: '2-digit', hour12: false });
  const day = new Intl.DateTimeFormat('en-US', { timeZone: tz, day: 'numeric' });
  const month = new Intl.DateTimeFormat('en-US', { timeZone: tz, month: 'short' });
  const year = new Intl.DateTimeFormat('en-US', { timeZone: tz, year: 'numeric' });
  const full = new Intl.DateTimeFormat('en-GB', { timeZone: tz, weekday: 'short', day: '2-digit', month: 'short', year: '2-digit' });
  const at = (t: unknown) => new Date((typeof t === 'number' ? t : 0) * 1000);
  return {
    localization: { timeFormatter: (t: unknown) => (daily ? full.format(at(t)) : `${full.format(at(t))}  ${time.format(at(t))}`) },
    timeScale: {
      tickMarkFormatter: (t: unknown, type: number) => {
        const d = at(t);
        if (type === 0) return year.format(d);
        if (type === 1) return month.format(d);
        if (type === 2) return day.format(d);
        return time.format(d);
      },
    },
  };
}
