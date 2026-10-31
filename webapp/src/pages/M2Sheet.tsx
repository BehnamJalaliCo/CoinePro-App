// A small sheet from the bottom, one column wide on a wide screen: the markets filter, the pulse's
// explanations, the heat map's settings and a tile's figures (the OLD CoineProSheet).
import { useEffect, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { usePrefs } from '../state/prefs';
import { Icon } from '../ui/Icon';
import { useL } from './kit';
import '../shell/markets2.css';

/** A small sheet from the bottom, one column wide on a wide screen. */
export function Sheet({ title, subtitle, onClose, children, className = '' }: { title: string; subtitle?: string; onClose(): void; children: ReactNode; className?: string }) {
  const L = useL();
  const fa = usePrefs((s) => s.lang) === 'fa';
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') { e.stopPropagation(); onClose(); } };
    window.addEventListener('keydown', onKey, true);
    return () => window.removeEventListener('keydown', onKey, true);
  }, [onClose]);
  return createPortal(
    <div className="scrim sheet-scrim" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div className={`sheet m2-sheet ${className}`} role="dialog" aria-modal="true" aria-label={title} dir={fa ? 'rtl' : 'ltr'}>
        <div className="sheet-grip" aria-hidden />
        <header className="m2-sheet-head">
          <div className="grow"><h2 className={fa ? 'fa' : ''}>{title}</h2>{subtitle ? <span className={`muted${fa ? ' fa' : ''}`}>{subtitle}</span> : null}</div>
          <button type="button" className="ib" onClick={onClose} aria-label={L('بستن', 'Close')}><Icon name="ph-x" size="md" /></button>
        </header>
        {children}
      </div>
    </div>,
    document.body,
  );
}

