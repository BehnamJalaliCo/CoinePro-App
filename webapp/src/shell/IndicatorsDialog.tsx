import { useEffect, useMemo, useRef, useState } from 'react';
import { usePrefs, useT } from '../state/prefs';
import { useStudies } from '../state/studies';
import { CATALOG, CATEGORIES, POPULAR, type Category } from '../chart/indicatorCatalog';
import { Icon } from '../ui/Icon';

type Section = 'favourites' | 'popular' | Category | 'all';

// TradingView's "Indicators, metrics & strategies": search on top, categories on the left, one
// row per study with its star. A click adds the study and keeps the dialog open, as TradingView does.
export function IndicatorsDialog({ onClose }: { onClose(): void }) {
  const t = useT();
  const lang = usePrefs((s) => s.lang);
  const add = useStudies((s) => s.add);
  const favourites = useStudies((s) => s.favourites);
  const toggleFavourite = useStudies((s) => s.toggleFavourite);
  const [q, setQ] = useState('');
  const [section, setSection] = useState<Section>('popular');
  const [cursor, setCursor] = useState(0);
  const [added, setAdded] = useState<string | null>(null);
  const input = useRef<HTMLInputElement>(null);
  useEffect(() => { input.current?.focus(); }, []);

  const rows = useMemo(() => {
    const needle = q.trim().toLowerCase();
    if (needle) {
      // TradingView's order: the abbreviation itself, then names starting with the query, then words.
      const score = (c: (typeof CATALOG)[number]) => {
        const en = c.en.toLowerCase();
        const abbr = c.abbr.toLowerCase();
        if (abbr === needle || c.key.toLowerCase() === needle) return 0;
        if (en.startsWith(needle)) return 1;
        if (en.split(/[\s/(-]+/).some((w) => w.startsWith(needle))) return 2;
        return 3;
      };
      return CATALOG
        .filter((c) => c.en.toLowerCase().includes(needle) || c.abbr.toLowerCase().includes(needle) || c.fa.includes(q.trim()) || c.key.toLowerCase().includes(needle))
        .sort((a, b) => score(a) - score(b) || a.en.length - b.en.length);
    }
    if (section === 'favourites') return CATALOG.filter((c) => favourites.includes(c.key));
    if (section === 'popular') return POPULAR.map((k) => CATALOG.find((c) => c.key === k)!).filter(Boolean);
    if (section === 'all') return CATALOG;
    return CATALOG.filter((c) => c.category === section);
  }, [q, section, favourites]);

  useEffect(() => { setCursor(0); }, [q, section]);

  const pick = (key: string) => { add(key); setAdded(key); setTimeout(() => setAdded((k) => (k === key ? null : k)), 900); };

  const onKey = (e: React.KeyboardEvent) => {
    if (e.key === 'Escape') onClose();
    else if (e.key === 'ArrowDown') { e.preventDefault(); setCursor((c) => Math.min(rows.length - 1, c + 1)); }
    else if (e.key === 'ArrowUp') { e.preventDefault(); setCursor((c) => Math.max(0, c - 1)); }
    else if (e.key === 'Enter' && rows[cursor]) pick(rows[cursor]!.key);
  };

  const sections: { id: Section; fa: string; en: string; icon: string }[] = [
    { id: 'favourites', fa: 'منتخب‌ها', en: 'Favourites', icon: 'ph-star' },
    { id: 'popular', fa: 'پرکاربرد', en: 'Popular', icon: 'ph-lightning' },
    { id: 'all', fa: 'همه', en: 'All', icon: 'ph-list' },
    ...CATEGORIES.map((c) => ({ ...c, icon: 'ph-chart-line-up' })),
  ];

  return (
    <div className="scrim" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div className="dialog indicators" role="dialog" aria-modal="true" aria-label={t('indicators')} onKeyDown={onKey}>
        <header className="dialog-head">
          <h2 className="fa">{t('indicators')}</h2>
          <button className="ib" onClick={onClose} aria-label="close"><Icon name="ph-x" size="md" /></button>
        </header>
        <label className="search-field">
          <Icon name="search" size="md" />
          <input ref={input} value={q} onChange={(e) => setQ(e.target.value)} placeholder={t('search')} spellCheck={false} autoComplete="off" dir="auto" />
        </label>
        <div className="ind-body">
          <nav className="ind-cats">
            {sections.map((s) => (
              <button key={s.id} className={`ind-cat${section === s.id && !q ? ' on' : ''}`} onClick={() => { setSection(s.id); setQ(''); }}>
                <Icon name={s.icon} size="sm" /><span className={lang === 'fa' ? 'fa' : ''}>{lang === 'fa' ? s.fa : s.en}</span>
              </button>
            ))}
          </nav>
          <div className="ind-list" role="listbox">
            {rows.length === 0 ? <div className="empty fa">{t('noResults')}</div> : rows.map((r, i) => (
              <div key={r.key} role="option" aria-selected={i === cursor} className={`ind-row${i === cursor ? ' on' : ''}`} onMouseEnter={() => setCursor(i)} onClick={() => pick(r.key)}>
                <button className={`star${favourites.includes(r.key) ? ' on' : ''}`} onClick={(e) => { e.stopPropagation(); toggleFavourite(r.key); }} aria-label="favourite">
                  <Icon name={favourites.includes(r.key) ? 'ph-star-fill' : 'ph-star'} size="sm" />
                </button>
                <span className="ind-name">{r.en}</span>
                {lang === 'fa' ? <span className="ind-fa fa">{r.fa}</span> : /^[\x20-\x7e]+$/.test(r.abbr) && r.abbr !== r.en ? <span className="ind-fa num">{r.abbr}</span> : null}
                {added === r.key ? <span className="ind-added"><Icon name="ph-check" size="sm" /></span> : null}
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
