import type { ReactNode } from 'react';
import { usePrefs } from '../state/prefs';
import { Icon } from '../ui/Icon';
import './script2.css';

/** A sheet over the workspace: a title, a line under it, and the body; Escape or the scrim closes it. */
export function Sheet({ title, subtitle, onClose, className = '', children }: { title: string; subtitle?: string; onClose(): void; className?: string; children: ReactNode }) {
  const fa = usePrefs((s) => s.lang) === 'fa';
  return (
    <div className="scrim" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div className={`dialog sx-sheet ${className}`} role="dialog" aria-modal="true" aria-label={title} dir={fa ? 'rtl' : 'ltr'} onKeyDown={(e) => { if (e.key === 'Escape') onClose(); }}>
        <header className="dialog-head">
          <div><h2 className="fa">{title}</h2>{subtitle ? <p className="muted fa">{subtitle}</p> : null}</div>
          <button className="ib" onClick={onClose} aria-label="close"><Icon name="ph-x" size="md" /></button>
        </header>
        <div className="sx-sheet-body">{children}</div>
      </div>
    </div>
  );
}

