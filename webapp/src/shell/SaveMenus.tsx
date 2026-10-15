import { useState } from 'react';
import { Dropdown } from '../ui/Menu';
import { recentLayouts, useLayouts } from '../state/layouts';
import { usePrefs, useT } from '../state/prefs';
import { Icon } from '../ui/Icon';
import { useL } from '../pages/kit';
import { exportBarsCsv } from '../chart/actions';
import { intervalLabel } from '../data/intervals';

export type LayoutDialogMode = 'save' | 'saveAs' | 'rename' | 'copy' | 'open';

// TradingView's layout menu (the caret beside the layout name): save (Ctrl+S), save as, rename,
// make a copy, download the chart's data, a new layout, open a layout, the recently used ones,
// and autosave.
export function LayoutMenu({ anchor, onClose, onDialog }: { anchor: HTMLElement | null; onClose(): void; onDialog(mode: LayoutDialogMode): void }) {
  const L = useL();
  const fa = usePrefs((s) => s.lang) === 'fa';
  const { current, autosave, save, load, setAutosave, newLayout } = useLayouts();
  useLayouts((s) => s.layouts);
  const recent = recentLayouts(5);
  const item = (icon: string, label: string, onClick: () => void, opts: { key?: string; disabled?: boolean } = {}) => (
    <button className="menu-item" role="menuitem" disabled={opts.disabled} onClick={() => { onClose(); onClick(); }}>
      <Icon name={icon} size="sm" /><span className={fa ? 'fa' : ''}>{label}</span>{opts.key ? <kbd className="num">{opts.key}</kbd> : null}
    </button>
  );
  return (
    <Dropdown anchor={anchor} onClose={onClose} align="right" className="save-menu layout-menu tvm">
      <div dir={fa ? 'rtl' : 'ltr'}>
        {item('ph-floppy-disk', L('ذخیره‌ی چیدمان', 'Save layout'), () => { if (!save()) onDialog('save'); }, { key: 'Ctrl+S' })}
        {item('ph-file-plus', L('ذخیره با نام تازه…', 'Save as…'), () => onDialog('saveAs'))}
        {item('ph-pencil-simple', L('تغییر نام…', 'Rename…'), () => onDialog('rename'), { disabled: !current })}
        {item('ph-copy', L('ساخت رونوشت…', 'Make a copy…'), () => onDialog('copy'))}
        {item('ph-download-simple', L('دانلود داده‌ی چارت…', 'Download chart data…'), () => exportBarsCsv())}
        <div className="menu-sep" />
        {item('ph-plus', L('چیدمان تازه…', 'Create new layout…'), () => newLayout())}
        {item('ph-folder-open', L('باز کردن چیدمان…', 'Open layout…'), () => onDialog('open'))}
        {recent.length ? (
          <>
            <div className="menu-sep" />
            <div className={`menu-title${fa ? ' fa' : ''}`}>{L('اخیراً استفاده‌شده', 'Recently used')}</div>
            {recent.map((l) => (
              <button key={l.name} className="menu-item saved" role="menuitem" onClick={() => { onClose(); load(l.name); }}>
                <span className="lname" dir="auto">{l.name}</span>
                <span className="num muted">{l.symbol} · {intervalLabel(l.interval)}</span>
              </button>
            ))}
          </>
        ) : null}
        <div className="menu-sep" />
        <button className="menu-item mcheck toggle-row" role="menuitemcheckbox" aria-checked={autosave} onClick={() => setAutosave(!autosave)}>
          <Icon name="ph-cloud-check" size="sm" /><span className={fa ? 'fa' : ''}>{L('ذخیره‌ی خودکار', 'Autosave')}</span>
          <span className={`switch${autosave ? ' on' : ''}`} aria-hidden><i /></span>
        </button>
        {autosave && !current ? <p className={`menu-note${fa ? ' fa' : ''}`}>{L('پس از نخستین ذخیره، تغییرها خودکار ذخیره می‌شوند.', 'Changes save themselves once the layout has a name.')}</p> : null}
      </div>
    </Dropdown>
  );
}

export function TemplateMenu({ anchor, onClose }: { anchor: HTMLElement | null; onClose(): void }) {
  const t = useT();
  const { templates, saveTemplate, applyTemplate, removeTemplate } = useLayouts();
  const [name, setName] = useState('');
  return (
    <Dropdown anchor={anchor} onClose={onClose} className="save-menu">
      <form className="menu-form" onSubmit={(e) => { e.preventDefault(); if (name.trim()) { saveTemplate(name); setName(''); } }}>
        <input value={name} onChange={(e) => setName(e.target.value)} placeholder={t('templateName')} />
        <button className="btn btn-quiet sm" type="submit"><span className="fa">{t('templateSave')}</span></button>
      </form>
      <div className="menu-sep" />
      <div className="menu-title fa">{t('templatesSaved')}</div>
      {!templates.length ? <div className="menu-empty fa">{t('nothingSaved')}</div> : null}
      {templates.map((tp) => (
        <div key={tp.name} className="menu-item saved" role="menuitem" onClick={() => { applyTemplate(tp.name); onClose(); }}>
          <span>{tp.name}</span><span className="num muted">{tp.studies.length}</span>
          <button className="lt" aria-label="remove" onClick={(e) => { e.stopPropagation(); removeTemplate(tp.name); }}><Icon name="ph-x" size="sm" /></button>
        </div>
      ))}
    </Dropdown>
  );
}
