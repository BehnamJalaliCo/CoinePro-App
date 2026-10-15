// The panel's building blocks: a section with its heading, cards, figure tiles, settings rows,
// pills, and the times and counts in the reader's language. Laid out like TradingView's own
// account settings: a list of sections at the side, one section at a time beside it.
import type { ReactNode } from 'react';
import { usePrefs } from '../state/prefs';
import { faAgo, faNum } from '../i18n/time';
import { usePage } from '../state/page';
import { PAGES, type PageId } from '../pages/registry';
import { Icon } from '../ui/Icon';

export function Section({ title, sub, actions, children }: { title: string; sub?: string; actions?: ReactNode; children: ReactNode }) {
  return (
    <section className="ucp-section" aria-label={title}>
      <header className="ucp-section-head">
        <div><h2>{title}</h2>{sub ? <p>{sub}</p> : null}</div>
        {actions ? <div className="ucp-actions">{actions}</div> : null}
      </header>
      {children}
    </section>
  );
}

export function Card({ title, sub, actions, children, className = '', id }: { title?: string; sub?: string; actions?: ReactNode; children: ReactNode; className?: string; id?: string }) {
  return (
    <div className={`ucp-card ${className}`} id={id}>
      {title || actions ? (
        <div className="ucp-card-head">
          <div>{title ? <h3>{title}</h3> : null}{sub ? <p>{sub}</p> : null}</div>
          {actions ? <div className="ucp-actions">{actions}</div> : null}
        </div>
      ) : null}
      {children}
    </div>
  );
}

export type Tone = 'good' | 'bad' | 'gold' | 'info' | 'warn' | 'muted';

export function Stat({ label, value, foot, tone, icon }: { label: string; value: ReactNode; foot?: ReactNode; tone?: Tone; icon?: string }) {
  return (
    <div className={`ucp-stat${tone ? ` t-${tone}` : ''}`}>
      <span className="ucp-stat-label">{icon ? <Icon name={icon} size="sm" /> : null}{label}</span>
      <b className="ucp-stat-value num">{value}</b>
      {foot ? <span className="ucp-stat-foot">{foot}</span> : null}
    </div>
  );
}

export function Row({ label, hint, children }: { label: string; hint?: ReactNode; children?: ReactNode }) {
  return (
    <div className="ucp-row">
      <div className="ucp-row-label"><span>{label}</span>{hint ? <small>{hint}</small> : null}</div>
      <div className="ucp-row-value">{children}</div>
    </div>
  );
}

export function Pill({ tone = 'muted', children }: { tone?: Tone; children: ReactNode }) {
  return <span className={`ucp-pill t-${tone}`}>{children}</span>;
}

export function Note({ tone = 'info', icon = 'ph-info', children }: { tone?: Tone; icon?: string; children: ReactNode }) {
  return <div className={`ucp-note t-${tone}`} role={tone === 'bad' ? 'alert' : undefined}><Icon name={icon} size="sm" /><div>{children}</div></div>;
}

export function Blank({ icon = 'ph-info', title, children }: { icon?: string; title: string; children?: ReactNode }) {
  return <div className="ucp-blank"><Icon name={icon} size="md" /><b>{title}</b>{children ? <span>{children}</span> : null}</div>;
}

/** A switch row control. */
export function Switch({ on, onChange, label, disabled }: { on: boolean; onChange(v: boolean): void; label: string; disabled?: boolean }) {
  return <input type="checkbox" role="switch" className="ucp-switch" aria-label={label} checked={on} disabled={disabled} onChange={(e) => onChange(e.target.checked)} />;
}

/** «۳ ساعت پیش» in Persian, «3 h ago» in English. */
export function useAgo(): (ms: number | null | undefined) => string {
  const lang = usePrefs((s) => s.lang);
  return (ms) => {
    if (!ms) return '—';
    if (lang === 'fa') return faAgo(ms);
    const s = Math.max(0, Math.round((Date.now() - ms) / 1000));
    if (s < 60) return 'just now';
    if (s < 3600) return `${Math.round(s / 60)} min ago`;
    if (s < 86400) return `${Math.round(s / 3600)} h ago`;
    return `${Math.round(s / 86400)} d ago`;
  };
}

/** A count inside a sentence: Persian digits in Persian. */
export function useCount(): (n: number) => string {
  const lang = usePrefs((s) => s.lang);
  return (n) => (lang === 'fa' ? faNum(n) : String(n));
}

/** A calendar date for a figure: 2026-10-15 14:05 in Latin digits, in both languages. */
export const stamp = (ms: number | null | undefined): string => {
  if (!ms) return '—';
  const d = new Date(ms);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
};

export const bytes = (n: number): string => (n < 1024 ? `${n} B` : n < 1024 * 1024 ? `${(n / 1024).toFixed(1)} KB` : `${(n / 1024 / 1024).toFixed(2)} MB`);

/** True when the app has registered that page (some arrive from other areas of the app). */
export const hasPage = (id: string): boolean => PAGES.some((p) => p.id === id);

/** Opens another page of the app, when it exists; otherwise the fallback. */
export function openPage(id: string, fallback?: PageId, param?: string | null): void {
  const target = hasPage(id) ? (id as PageId) : fallback;
  if (target) usePage.getState().open(target, param ?? null);
}

export const COUNTRY = (code: string | null | undefined, lang: string): string => {
  if (!code) return '—';
  try { return new Intl.DisplayNames([lang === 'fa' ? 'fa' : 'en'], { type: 'region' }).of(code) ?? code; } catch { return code; }
};
