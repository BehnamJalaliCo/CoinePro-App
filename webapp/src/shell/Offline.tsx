import { useEffect, useState } from 'react';
import { usePrefs } from '../state/prefs';
import { savedAgeOf, savedAgo } from '../data/saved';
import { Icon } from '../ui/Icon';
import './shell.css';

// Two different facts, two different lines (core/designsystem CoineProOfflineBar.kt and
// CoineProSavedBar.kt): «offline» is about the device and leaves when the network returns; «saved
// · two hours ago» is about the numbers in front of the reader and is true on a good connection
// too. Neither blocks anything, neither has a dismiss, both leave by themselves.

/** Whether the browser says it has a network, kept up to date. */
export function useOnline(): boolean {
  const [online, setOnline] = useState(() => (typeof navigator === 'undefined' ? true : navigator.onLine !== false));
  useEffect(() => {
    const on = () => setOnline(true);
    const off = () => setOnline(false);
    window.addEventListener('online', on);
    window.addEventListener('offline', off);
    return () => { window.removeEventListener('online', on); window.removeEventListener('offline', off); };
  }, []);
  return online;
}

/** The offline banner: one line, the sell colour's tint, while the browser has no network. */
export function OfflineBar() {
  const online = useOnline();
  const lang = usePrefs((s) => s.lang);
  if (online) return null;
  return (
    <div className="offline-bar" role="status" dir={lang === 'fa' ? 'rtl' : 'ltr'}>
      <Icon name="ph-warning" size="sm" />
      <span className={lang === 'fa' ? 'fa' : ''}>{lang === 'fa'
        ? 'اینترنت این دستگاه قطع است. قیمت‌ها همان آخرین چیزی است که رسیده.'
        : 'This device has no internet. Prices are the last that arrived.'}</span>
    </div>
  );
}

/** How often the printed age is recomputed: never more than half a minute behind. */
const TICK_MS = 30_000;

/** The saved picture's age, kept current while it is on screen; null draws nothing. */
export function useSavedAge(savedAt: number | null | undefined) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (savedAt == null) return;
    setNow(Date.now());
    const id = setInterval(() => setNow(Date.now()), TICK_MS);
    return () => clearInterval(id);
  }, [savedAt]);
  return savedAgeOf(savedAt, now);
}

/** «ذخیره‌شده · ۲ ساعت پیش» in the muted ink, or nothing while the picture is live. */
export function SavedLine({ at, className }: { at: number | null | undefined; className?: string }) {
  const lang = usePrefs((s) => s.lang);
  const age = useSavedAge(at);
  if (!age) return null;
  return (
    <div className={`saved-line${className ? ` ${className}` : ''}`} role="status" dir={lang === 'fa' ? 'rtl' : 'ltr'}>
      <Icon name="ph-clock-counter-clockwise" size="sm" />
      <span className={lang === 'fa' ? 'fa' : ''}>{savedAgo(age, lang)}</span>
    </div>
  );
}
