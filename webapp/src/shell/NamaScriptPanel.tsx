import { useMemo, useRef, useState } from 'react';
import { useScript } from '../state/script';
import { useT } from '../state/prefs';
import { EXAMPLES, REFERENCE } from '../engine/scriptlib.js';
import { cleanFa } from '../chart/indicatorCatalog';
import { Icon } from '../ui/Icon';

// NamaScript's editor, where TradingView keeps Pine Editor: the code with line numbers, the
// examples, «افزودن به چارت» (Ctrl/Cmd+Enter), the reference beside it, the console under it.
export function NamaScriptPanel() {
  const t = useT();
  const { source, setSource, applied, apply, remove, result, running } = useScript();
  const [showRef, setShowRef] = useState(false);
  const [refQuery, setRefQuery] = useState('');
  const area = useRef<HTMLTextAreaElement>(null);
  const gutter = useRef<HTMLDivElement>(null);
  const lines = useMemo(() => source.split('\n').length, [source]);

  const onKey = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') { e.preventDefault(); apply(); return; }
    if (e.key === 'Tab') {
      e.preventDefault();
      const el = e.currentTarget;
      const { selectionStart: a, selectionEnd: b } = el;
      const next = `${source.slice(0, a)}  ${source.slice(b)}`;
      setSource(next);
      requestAnimationFrame(() => { el.selectionStart = el.selectionEnd = a + 2; });
    }
  };

  const reference = useMemo(() => {
    const q = refQuery.trim().toLowerCase();
    return REFERENCE.map((g) => ({ g: cleanFa(g.g), items: g.items.filter(([sig, d]) => !q || sig.toLowerCase().includes(q) || d.includes(refQuery.trim())) })).filter((g) => g.items.length);
  }, [refQuery]);

  const strat = result?.ok ? result.strategy : null;
  return (
    <div className="ns">
      <div className="ns-bar">
        <select className="ns-examples" value="" onChange={(e) => { const ex = EXAMPLES[Number(e.target.value)]; if (ex) setSource(ex.code); }} aria-label="examples">
          <option value="">{t('nsExamples')}</option>
          {EXAMPLES.map((ex, i) => <option key={i} value={i}>{cleanFa(ex.name)}</option>)}
        </select>
        <span className="ws-spacer" />
        {running ? <span className="muted fa">{t('loading')}…</span> : null}
        {applied ? (
          <>
            <button className="btn btn-quiet sm" onClick={apply} title="Ctrl+Enter"><Icon name="ph-arrow-clockwise" size="sm" /><span className="fa">{t('nsUpdate')}</span></button>
            <button className="btn btn-quiet sm" onClick={remove}><Icon name="ph-x" size="sm" /><span className="fa">{t('remove')}</span></button>
          </>
        ) : (
          <button className="btn btn-primary sm" onClick={apply} title="Ctrl+Enter"><Icon name="ph-play" size="sm" /><span className="fa">{t('nsAdd')}</span></button>
        )}
        <button className={`ib${showRef ? ' on' : ''}`} onClick={() => setShowRef(!showRef)} title="reference"><Icon name="ph-info" size="md" /></button>
      </div>
      <div className="ns-main">
        <div className="ns-editor">
          <div className="ns-gutter num" ref={gutter} aria-hidden>{Array.from({ length: lines }, (_, i) => <div key={i}>{i + 1}</div>)}</div>
          <textarea ref={area} className="ns-code" value={source} spellCheck={false} dir="ltr" autoCapitalize="off" autoCorrect="off"
            onChange={(e) => setSource(e.target.value)} onKeyDown={onKey}
            onScroll={(e) => { if (gutter.current) gutter.current.scrollTop = e.currentTarget.scrollTop; }} />
        </div>
        {showRef ? (
          <aside className="ns-ref">
            <input className="scr-search" value={refQuery} onChange={(e) => setRefQuery(e.target.value)} placeholder={t('search')} />
            {reference.map((g) => (
              <div key={g.g} className="ns-ref-group">
                <div className="menu-title fa">{g.g}</div>
                {g.items.map(([sig, d]) => (
                  <button key={sig} className="ns-ref-item" onClick={() => {
                    const el = area.current; if (!el) return;
                    const a = el.selectionStart;
                    setSource(`${source.slice(0, a)}${sig}${source.slice(el.selectionEnd)}`);
                  }}>
                    <code className="num">{sig}</code><span className="fa">{cleanFa(d)}</span>
                  </button>
                ))}
              </div>
            ))}
          </aside>
        ) : null}
      </div>
      <div className={`ns-console${result && !result.ok ? ' err' : ''}`} dir="auto">
        {result && !result.ok ? <span>{result.error}</span> : null}
        {result?.ok ? (
          <span className="fa">
            {`${(result.plots ?? []).length} plot · ${(result.shapes ?? []).reduce((n, s) => n + s.points.length, 0)} signal`}
            {strat ? ` · ${strat.trades} trades` : ''}
          </span>
        ) : null}
      </div>
    </div>
  );
}
