import { useState } from 'react';
import { Dropdown } from '../ui/Menu';
import { useLayouts } from '../state/layouts';
import { useT } from '../state/prefs';
import { Icon } from '../ui/Icon';

// TradingView's "Save" menu: save, save under a new name, and the saved layouts to open or delete.
export function LayoutMenu({ anchor, onClose }: { anchor: HTMLElement | null; onClose(): void }) {
  const t = useT();
  const { layouts, current, save, load, remove } = useLayouts();
  const [name, setName] = useState('');
  return (
    <Dropdown anchor={anchor} onClose={onClose} align="right" className="save-menu">
      <button className="menu-item" onClick={() => { save(); onClose(); }}>
        <Icon name="ph-floppy-disk" size="sm" /><span className="fa">{t('layoutSave')}</span><kbd className="num">Ctrl+S</kbd>
      </button>
      <form className="menu-form" onSubmit={(e) => { e.preventDefault(); if (name.trim()) { save(name); setName(''); onClose(); } }}>
        <input value={name} onChange={(e) => setName(e.target.value)} placeholder={t('layoutName')} />
        <button className="btn btn-quiet sm" type="submit"><span className="fa">{t('layoutSaveAs')}</span></button>
      </form>
      <div className="menu-sep" />
      <div className="menu-title fa">{t('layoutsSaved')}</div>
      {!layouts.length ? <div className="menu-empty fa">{t('nothingSaved')}</div> : null}
      {layouts.map((l) => (
        <div key={l.name} className={`menu-item saved${l.name === current ? ' on' : ''}`} role="menuitem" onClick={() => { load(l.name); onClose(); }}>
          <span>{l.name}</span><span className="num muted">{l.symbol} · {l.interval}</span>
          <button className="lt" aria-label="remove" onClick={(e) => { e.stopPropagation(); remove(l.name); }}><Icon name="ph-x" size="sm" /></button>
        </div>
      ))}
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
