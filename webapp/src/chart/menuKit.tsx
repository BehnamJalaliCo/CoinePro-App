// The chart's pop-up menus in TradingView's shape: an icon or a check mark in the leading column,
// the label, a muted value or shortcut at the end, submenus that open beside their row (on the
// other side when there is no room, and mirrored in Persian). Kept on screen; closed by a pick,
// an outside click or Escape.
import { createContext, useContext, useEffect, useLayoutEffect, useRef, useState, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { Icon } from '../ui/Icon';
import { usePrefs } from '../state/prefs';
import './chart-x.css';

const CloseCtx = createContext<() => void>(() => undefined);

/** Where a box of size w×h fits near (x, y) inside the window, 8px clear of every edge. */
export function fitBox(x: number, y: number, w: number, h: number, vw = window.innerWidth, vh = window.innerHeight): { left: number; top: number } {
  const left = Math.max(8, Math.min(x, vw - w - 8));
  const top = y + h > vh - 8 ? Math.max(8, Math.min(y - h, vh - h - 8)) : Math.max(8, y);
  return { left, top };
}

export function PopMenu({ x, y, onClose, children, className = '', label, anchorRight }: { x: number; y: number; onClose(): void; children: ReactNode; className?: string; label?: string; anchorRight?: boolean }) {
  const ref = useRef<HTMLDivElement>(null);
  const rtl = usePrefs((s) => s.lang) === 'fa';
  const [pos, setPos] = useState<{ left: number; top: number } | null>(null);
  useLayoutEffect(() => {
    const el = ref.current;
    if (!el) return;
    const r = el.getBoundingClientRect();
    // In Persian a menu opened at a point grows to the left of it, as a mirrored TradingView would.
    const fromX = anchorRight || rtl ? x - r.width : x;
    setPos(fitBox(fromX, y, r.width, r.height));
  }, [x, y, rtl, anchorRight]);
  useEffect(() => {
    const close = (e: MouseEvent) => { if (!ref.current?.contains(e.target as Node) && !(e.target as HTMLElement).closest?.('.cm-sub')) onClose(); };
    const esc = (e: KeyboardEvent) => { if (e.key === 'Escape') { e.stopPropagation(); onClose(); } };
    const away = () => onClose();
    document.addEventListener('mousedown', close);
    document.addEventListener('keydown', esc, true);
    window.addEventListener('blur', away);
    window.addEventListener('resize', away);
    return () => { document.removeEventListener('mousedown', close); document.removeEventListener('keydown', esc, true); window.removeEventListener('blur', away); window.removeEventListener('resize', away); };
  }, [onClose]);
  return createPortal(
    <CloseCtx.Provider value={onClose}>
      <div ref={ref} className={`menu dropdown ctx cm ${className}`} role="menu" aria-label={label} dir={rtl ? 'rtl' : 'ltr'}
        style={{ position: 'fixed', left: pos?.left ?? x, top: pos?.top ?? y, visibility: pos ? 'visible' : 'hidden' }}
        onContextMenu={(e) => e.preventDefault()}>
        {children}
      </div>
    </CloseCtx.Provider>,
    document.body,
  );
}

export function MI({ icon, label, hint, checked, onClick, disabled, danger, act, keep, title }: {
  icon?: string; label: ReactNode; hint?: ReactNode; checked?: boolean; onClick?(): void; disabled?: boolean; danger?: boolean; act?: string; keep?: boolean; title?: string;
}) {
  const close = useContext(CloseCtx);
  return (
    <button type="button" role={checked === undefined ? 'menuitem' : 'menuitemcheckbox'} aria-checked={checked} className={`menu-item cm-item${danger ? ' danger' : ''}`}
      data-act={act} disabled={disabled} title={title}
      onClick={() => { onClick?.(); if (!keep) close(); }}>
      <span className="cm-lead">{checked ? <Icon name="ph-check" size="sm" /> : icon ? <Icon name={icon} size="sm" /> : null}</span>
      <span className="cm-label">{label}</span>
      {hint != null ? <span className="cm-hint num">{hint}</span> : null}
    </button>
  );
}

export const MSep = () => <div className="menu-sep" role="separator" />;

export function MTitle({ children }: { children: ReactNode }) {
  return <div className="cm-title">{children}</div>;
}

/** A row that opens its own menu beside it on hover or click. */
export function MSub({ icon, label, hint, children, act }: { icon?: string; label: ReactNode; hint?: ReactNode; children: ReactNode; act?: string }) {
  const row = useRef<HTMLButtonElement>(null);
  const panel = useRef<HTMLDivElement>(null);
  const rtl = usePrefs((s) => s.lang) === 'fa';
  const [open, setOpen] = useState(false);
  const [pos, setPos] = useState<{ left: number; top: number } | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const close = useContext(CloseCtx);
  useLayoutEffect(() => {
    if (!open || !row.current || !panel.current) { setPos(null); return; }
    const r = row.current.getBoundingClientRect();
    const p = panel.current.getBoundingClientRect();
    const menu = row.current.closest('.cm')?.getBoundingClientRect() ?? r;
    const after = rtl ? menu.left - p.width + 2 : menu.right - 2;
    const before = rtl ? menu.right - 2 : menu.left - p.width + 2;
    const fitsAfter = rtl ? after >= 8 : after + p.width <= window.innerWidth - 8;
    const fitsBefore = rtl ? before + p.width <= window.innerWidth - 8 : before >= 8;
    // On a narrow screen neither side has room: the submenu opens under its row instead.
    if (!fitsAfter && !fitsBefore) setPos(fitBox(rtl ? r.right - p.width - 12 : r.left + 12, r.bottom, p.width, p.height));
    else setPos(fitBox(fitsAfter ? after : before, r.top - 6, p.width, p.height));
  }, [open, rtl]);
  const enter = () => { if (timer.current) clearTimeout(timer.current); setOpen(true); };
  const leave = () => { if (timer.current) clearTimeout(timer.current); timer.current = setTimeout(() => setOpen(false), 180); };
  useEffect(() => () => { if (timer.current) clearTimeout(timer.current); }, []);
  return (
    <>
      <button ref={row} type="button" role="menuitem" aria-haspopup="menu" aria-expanded={open} data-act={act}
        className={`menu-item cm-item cm-has-sub${open ? ' open' : ''}`} onMouseEnter={enter} onMouseLeave={leave} onClick={() => setOpen(true)}>
        <span className="cm-lead">{icon ? <Icon name={icon} size="sm" /> : null}</span>
        <span className="cm-label">{label}</span>
        {hint != null ? <span className="cm-hint num">{hint}</span> : null}
        <Icon name={rtl ? 'ph-caret-left' : 'ph-caret-right'} size="sm" className="cm-caret" />
      </button>
      {open ? createPortal(
        <CloseCtx.Provider value={close}>
          <div ref={panel} className="menu dropdown ctx cm cm-sub" role="menu" dir={rtl ? 'rtl' : 'ltr'} onMouseEnter={enter} onMouseLeave={leave}
            style={{ position: 'fixed', left: pos?.left ?? 0, top: pos?.top ?? 0, visibility: pos ? 'visible' : 'hidden' }}>
            {children}
          </div>
        </CloseCtx.Provider>,
        document.body,
      ) : null}
    </>
  );
}
