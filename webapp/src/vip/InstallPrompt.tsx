import { useEffect, useState } from 'react';
import { usePrefs } from '../state/prefs';
import { Icon } from '../ui/Icon';
import { useL } from '../pages/kit';

// «نصب پرو چارت» — the user site's install banner (components/pwa/InstallPrompt.tsx): Chrome's own
// install prompt when the browser offers one, a short guide on iOS Safari, nothing once installed
// or for two weeks after «later». The page is installable through manifest.webmanifest and a
// pass-through service worker (public/sw.js).
interface InstallEvent extends Event { prompt(): Promise<void>; userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }> }

const KEY = 'pc.install.v1';
const QUIET = 14 * 86_400_000;

const standalone = () =>
  (typeof window.matchMedia === 'function' && window.matchMedia('(display-mode: standalone)').matches)
  || Boolean((navigator as { standalone?: boolean }).standalone);

function quiet(): boolean {
  try { const at = Number(localStorage.getItem(KEY) ?? 0); return Date.now() - at < QUIET; } catch { return false; }
}

export function InstallPrompt() {
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const [deferred, setDeferred] = useState<InstallEvent | null>(null);
  const [ios, setIos] = useState(false);
  const [guide, setGuide] = useState(false);
  const [hidden, setHidden] = useState(() => standalone() || quiet());

  useEffect(() => {
    if (hidden) return;
    const ua = navigator.userAgent;
    if (/iPhone|iPad|iPod/i.test(ua) && /Safari/i.test(ua) && !/CriOS|FxiOS/i.test(ua)) setIos(true);
    const on = (e: Event) => { e.preventDefault(); setDeferred(e as InstallEvent); };
    window.addEventListener('beforeinstallprompt', on);
    const installed = () => setHidden(true);
    window.addEventListener('appinstalled', installed);
    return () => { window.removeEventListener('beforeinstallprompt', on); window.removeEventListener('appinstalled', installed); };
  }, [hidden]);

  useEffect(() => {
    // The pass-through worker only makes the page installable; it caches nothing.
    if (navigator.webdriver || !('serviceWorker' in navigator)) return;
    navigator.serviceWorker.register(`${import.meta.env.BASE_URL}sw.js`, { scope: import.meta.env.BASE_URL }).catch(() => undefined);
  }, []);

  if (hidden || (!deferred && !ios)) return null;
  const later = () => { try { localStorage.setItem(KEY, String(Date.now())); } catch { /* storage off */ } setHidden(true); };
  const install = async () => {
    if (ios) { setGuide(true); return; }
    if (!deferred) return;
    await deferred.prompt();
    const choice = await deferred.userChoice.catch(() => ({ outcome: 'dismissed' as const }));
    if (choice.outcome === 'accepted') setHidden(true); else later();
  };
  return (
    <div className="vip-install" role="dialog" aria-label={L('نصب پرو چارت', 'Install Pro Chart')} dir={lang === 'fa' ? 'rtl' : 'ltr'}>
      <img src={`${import.meta.env.BASE_URL}generated/app-icons/icon-192.png`} alt="" width={36} height={36} />
      <div className="vip-install-text">
        <b className="fa">{L('نصب پرو چارت', 'Install Pro Chart')}</b>
        <span className="muted fa">{guide
          ? L('در سافاری دکمه‌ی اشتراک‌گذاری را بزنید و «Add to Home Screen» را انتخاب کنید.', 'In Safari, tap Share, then «Add to Home Screen».')
          : L('چارت، سیگنال‌ها و هشدارها با یک لمس از صفحه‌ی اصلی.', 'Charts, signals and alerts one tap from your home screen.')}</span>
      </div>
      {!guide ? <button className="btn btn-primary sm" onClick={() => void install()}><Icon name="ph-download-simple" size="sm" /><span className="fa">{ios ? L('راهنما', 'How') : L('نصب', 'Install')}</span></button> : null}
      <button className="ib" aria-label={L('بعداً', 'Later')} title={L('بعداً', 'Later')} onClick={later}><Icon name="ph-x" size="sm" /></button>
    </div>
  );
}
