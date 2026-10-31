import { useEffect, useRef, useState } from 'react';
import { usePrefs, useT } from '../state/prefs';
import { faNum } from '../i18n/time';
import { SavedLine } from '../shell/Offline';
import '../shell/shell.css';

/** Seconds before each automatic retry of a chart that did not load; the last step repeats. */
export const RETRY_STEPS = [5, 10, 20, 40, 60];
/** How often a chart drawn from its saved bars asks for fresh ones while the browser is online. */
export const SAVED_REFRESH_S = 60;

/** The wait before the n-th automatic retry (0-based). */
export const retryDelay = (n: number): number => RETRY_STEPS[Math.min(Math.max(0, n), RETRY_STEPS.length - 1)]!;

/** Opens the symbol search from anywhere (App listens). */
export const openSymbolSearch = () => window.dispatchEvent(new CustomEvent('pc:symbol-search'));

// What the chart says when it is not simply live: the failure with its two ways out — try again
// (by hand, or by itself after a growing pause, at once when the network comes back) and «نماد
// دیگر» — or, over a chart drawn from its saved bars, how old they are, refreshed by itself.
export function ChartStatus({ state, savedAt, onRetry }: { state: 'loading' | 'ready' | 'error'; savedAt: number | null; onRetry(): void }) {
  const t = useT();
  const fa = usePrefs((s) => s.lang) === 'fa';
  const fails = useRef(0);
  const [left, setLeft] = useState<number | null>(null);
  const retry = useRef(onRetry);
  retry.current = onRetry;

  useEffect(() => {
    if (state === 'loading') return;
    if (state === 'ready' && savedAt == null) { fails.current = 0; setLeft(null); return; }
    const again = () => retry.current();
    window.addEventListener('online', again);
    // A saved chart offline waits for the network; online it asks again every minute.
    let id: ReturnType<typeof setInterval> | null = null;
    if (state === 'error' || navigator.onLine !== false) {
      let n = state === 'error' ? retryDelay(fails.current++) : SAVED_REFRESH_S;
      setLeft(state === 'error' ? n : null);
      id = setInterval(() => {
        n -= 1;
        if (n <= 0) { clearInterval(id!); again(); } else if (state === 'error') setLeft(n);
      }, 1000);
    }
    return () => { window.removeEventListener('online', again); if (id) clearInterval(id); };
  }, [state, savedAt]);

  if (state === 'ready') return <SavedLine at={savedAt} className="on-chart" />;
  if (state !== 'error') return null;
  return (
    <div className="chart-state chart-fail fa">
      <span>{t('chartFailed')}</span>
      <span className="chart-fail-actions">
        <button className="btn btn-primary sm" onClick={onRetry}>{t('retry')}</button>
        <button className="btn sm" onClick={openSymbolSearch}>{fa ? 'نماد دیگر' : 'Another symbol'}</button>
      </span>
      {left != null ? <span className="chart-fail-auto">{fa ? `تلاش دوباره‌ی خودکار تا ${faNum(left)} ثانیه‌ی دیگر` : `Trying again by itself in ${left} s`}</span> : null}
    </div>
  );
}
