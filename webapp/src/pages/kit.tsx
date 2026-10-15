// Small pieces every page uses: the loading/error/empty states, tabs, a hook for one request.
import { useCallback, useEffect, useState, type ReactNode } from 'react';
import { HttpError } from '../data/http';
import { usePrefs, useT } from '../state/prefs';

export function useLoad<T>(fn: () => Promise<T>, deps: unknown[] = []): { data: T | null; error: string | null; loading: boolean; reload(): void } {
  const t = useT();
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [n, setN] = useState(0);
  useEffect(() => {
    let alive = true;
    setLoading(true); setError(null);
    fn().then((d) => { if (alive) setData(d); }).catch((e) => { if (alive) setError(errorText(e, t)); }).finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, n]);
  return { data, error, loading, reload: useCallback(() => setN((x) => x + 1), []) };
}

export function errorText(e: unknown, t: ReturnType<typeof useT>): string {
  if (e instanceof HttpError) {
    if (e.readerMessage) return e.readerMessage;
    if (e.status === 401) return t('memberOnly');
    if (e.status === 403) return t('aiNeedsPremium');
    if (e.status === 404 || e.status === 405) return t('notAvailable');
    if (e.status === 429) return t('tooMany');
  }
  if (e instanceof Error && e.message === 'signed-out') return t('memberOnly');
  return t('networkError');
}

export function State({ loading, error, empty, onRetry, children }: { loading: boolean; error: string | null; empty?: boolean; onRetry?(): void; children: ReactNode }) {
  const t = useT();
  if (loading) return <div className="empty fa">{t('loading')}…</div>;
  if (error) return <div className="empty fa">{error} {onRetry ? <button className="link" onClick={onRetry}>{t('retry')}</button> : null}</div>;
  if (empty) return <div className="empty fa">{t('noResults')}</div>;
  return <>{children}</>;
}

export function Tabs<K extends string>({ value, onChange, tabs }: { value: K; onChange(k: NoInfer<K>): void; tabs: { id: NoInfer<K>; label: string }[] }) {
  return (
    <nav className="chips page-tabs" role="tablist">
      {tabs.map((x) => (
        <button key={x.id} role="tab" aria-selected={value === x.id} className={`chip${value === x.id ? ' on' : ''}`} onClick={() => onChange(x.id)}>
          <span className="fa">{x.label}</span>
        </button>
      ))}
    </nav>
  );
}

export const money = (v: number | null | undefined, digits = 2) =>
  v == null || !Number.isFinite(v) ? '—' : `${v < 0 ? '−' : ''}${Math.abs(v).toLocaleString('en-US', { minimumFractionDigits: digits, maximumFractionDigits: digits })}`;

export const pct = (v: number | null | undefined) => (v == null || !Number.isFinite(v) ? '—' : `${v >= 0 ? '+' : '−'}${Math.abs(v).toFixed(2)}%`);

export const dateTime = (msv: number | null | undefined) => {
  if (!msv) return '—';
  const d = new Date(msv);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
};

/** Saves rows as a CSV file (UTF-8 with BOM so Excel reads Persian). */
export function downloadCsv(name: string, rows: (string | number | null)[][]): void {
  const esc = (v: string | number | null) => { const s = v == null ? '' : String(v); return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s; };
  const blob = new Blob(['﻿' + rows.map((r) => r.map(esc).join(',')).join('\n')], { type: 'text/csv;charset=utf-8' });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob); a.download = name; a.click();
  setTimeout(() => URL.revokeObjectURL(a.href), 4000);
}

/** A line in both languages, for page copy that is not shared with the workspace. */
export function useL(): (fa: string, en: string) => string {
  const lang = usePrefs((s) => s.lang);
  return (f, e) => (lang === 'fa' ? f : e);
}

/** A labelled input row for the page forms. */
export function Field({ label, children, hint }: { label: string; children: ReactNode; hint?: string }) {
  return (
    <label className="field">
      <span className="fa">{label}</span>
      {children}
      {hint ? <small className="muted fa">{hint}</small> : null}
    </label>
  );
}

/** Reads a number from an input's text: Persian and Arabic digits, thousands separators. */
export function parseNum(raw: string): number | null {
  const s = raw.replace(/[۰-۹]/g, (d) => String(d.charCodeAt(0) - 0x06f0)).replace(/[٠-٩]/g, (d) => String(d.charCodeAt(0) - 0x0660)).replace(/[,\s٬]/g, '').replace('٫', '.');
  if (!s) return null;
  const n = Number(s);
  return Number.isFinite(n) ? n : null;
}

/** Downloads a JSON or text file. */
export function downloadText(name: string, text: string, type = 'application/json'): void {
  const a = document.createElement('a');
  a.href = URL.createObjectURL(new Blob([text], { type }));
  a.download = name; a.click();
  setTimeout(() => URL.revokeObjectURL(a.href), 4000);
}

/** Opens a file picker and resolves with the chosen file's text. */
export function pickText(accept = '.json,application/json'): Promise<string | null> {
  return new Promise((resolve) => {
    const input = document.createElement('input');
    input.type = 'file'; input.accept = accept;
    input.onchange = () => { const f = input.files?.[0]; if (!f) { resolve(null); return; } f.text().then(resolve, () => resolve(null)); };
    input.click();
  });
}
