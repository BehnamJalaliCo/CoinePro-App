import { useEffect, useRef, useState } from 'react';
import { Icon } from '../ui/Icon';
import { TOOL_GROUPS, type ToolGroup } from '../chart/tools';
import { usePrefs, useT } from '../state/prefs';
import { useDrawing } from '../state/drawing';

// The left rail: one button per group showing the group's last-used tool, a corner arrow that
// opens the whole group, then measure and zoom, then the drawing switches. TradingView's layout.
export function ToolRail() {
  const t = useT();
  const lang = usePrefs((s) => s.lang);
  const { tool, setTool, lastInGroup, magnet, setMagnet, locked, setLocked, hidden, setHidden, clearAll } = useDrawing();
  const [open, setOpen] = useState<string | null>(null);
  const railRef = useRef<HTMLElement>(null);

  useEffect(() => {
    if (!open) return;
    const close = (e: MouseEvent) => { if (!railRef.current?.contains(e.target as Node)) setOpen(null); };
    const esc = (e: KeyboardEvent) => { if (e.key === 'Escape') setOpen(null); };
    document.addEventListener('mousedown', close);
    document.addEventListener('keydown', esc);
    return () => { document.removeEventListener('mousedown', close); document.removeEventListener('keydown', esc); };
  }, [open]);

  const label = (g: { fa: string; en: string }) => (lang === 'fa' ? g.fa : g.en);

  const groupButton = (g: ToolGroup) => {
    const current = g.tools.find((x) => x.id === (lastInGroup[g.id] ?? g.tools[0]?.id)) ?? g.tools[0]!;
    const active = g.tools.some((x) => x.id === tool);
    return (
      <div key={g.id} className="rail-group">
        <button className={`tool${active ? ' on' : ''}`} title={label(current)} aria-pressed={active}
          onClick={() => setTool(current.id, g.id)}>
          <Icon name={`tool_${current.id}`} />
        </button>
        {g.tools.length > 1 ? (
          <button className="tool-more" aria-label={label(g)} aria-expanded={open === g.id}
            onClick={() => setOpen(open === g.id ? null : g.id)}><span className="more" /></button>
        ) : null}
        {open === g.id ? (
          <div className="flyout" role="menu" aria-label={label(g)}>
            <div className="flyout-title">{label(g)}</div>
            {g.tools.map((x) => (
              <button key={x.id} role="menuitem" className={`flyout-item${x.id === tool ? ' on' : ''}`}
                onClick={() => { setTool(x.id, g.id); setOpen(null); }}>
                <Icon name={`tool_${x.id}`} size="md" />
                <span className={lang === 'fa' ? 'fa' : ''}>{label(x)}</span>
                {x.key ? <kbd className="num">{x.key}</kbd> : null}
              </button>
            ))}
          </div>
        ) : null}
      </div>
    );
  };

  return (
    <nav className="ws-rail" ref={railRef} aria-label="drawing tools">
      {TOOL_GROUPS.map(groupButton)}
      <span className="rail-sep" />
      <button className={`tool${tool === 'ruler' ? ' on' : ''}`} title={t('drawMeasure')} onClick={() => setTool('ruler', 'measure')}><Icon name="tool_ruler" /></button>
      <button className="tool" title={t('zoomIn')}><Icon name="ph-magnifying-glass" size="md" /></button>
      <span className="rail-sep" />
      <button className={`tool${magnet ? ' on' : ''}`} title={t('magnet')} aria-pressed={magnet} onClick={() => setMagnet(!magnet)}><Icon name="magnet" /></button>
      <button className={`tool${locked ? ' on' : ''}`} title={t('lockDrawings')} aria-pressed={locked} onClick={() => setLocked(!locked)}><Icon name={locked ? 'lock' : 'unlock'} /></button>
      <button className={`tool${hidden ? ' on' : ''}`} title={t('hideDrawings')} aria-pressed={hidden} onClick={() => setHidden(!hidden)}><Icon name={hidden ? 'eye_off' : 'ph-eye'} size={hidden ? 'lg' : 'md'} /></button>
      <span className="rail-sep" />
      <button className="tool" title={t('removeDrawings')} onClick={clearAll}><Icon name="trash2" /></button>
    </nav>
  );
}
