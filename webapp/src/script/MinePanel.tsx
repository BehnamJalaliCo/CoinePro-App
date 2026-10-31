import { useEffect, useState } from 'react';
import { useScript, ENGINE_PLOT_COLOUR } from '../state/script';
import { useL } from '../pages/kit';
import { DESCRIPTION_LIMIT } from './doc';
import { useCount } from './ScriptSheets';

// «این اسکریپت مال من است» (the phone's ScriptScreen MinePanel): a line about the script, its colour,
// tags, whether it wants its own panel, the versions before this one, and the ways it leaves the
// studio. Fields beside the script rather than a form on save: a save takes whatever is in them,
// including nothing. Restoring a version puts it in the editor and does not save — looking through
// a history is looking.

/** The engine's own palette, so a swatch is a colour the reader can also write as color.<name>. */
export const SWATCHES: [string, string][] = [
  ['blue', '#3b82f6'], ['red', '#ef4444'], ['green', '#22c55e'], ['orange', '#f59e0b'], ['purple', '#a855f7'], ['gray', '#94a3b8'],
  ['yellow', '#eab308'], ['aqua', '#22d3ee'], ['teal', '#14b8a6'], ['lime', '#84cc16'], ['fuchsia', '#d946ef'], ['white', '#e5e7eb'],
];
const VERSIONS_SHOWN = 5;
const PREVIEW = 60;

export function MinePanel({ onExport, onImport, onCopyLink, onShare, startOpen = false }: { onExport(): void; onImport(): void; onCopyLink(): void; onShare(): void; startOpen?: boolean }) {
  const L = useL();
  const n = useCount();
  const { tabs, active, lib, setMeta, restoreVersion } = useScript();
  const tab = tabs.find((t) => t.id === active) ?? tabs[0]!;
  const meta = tab.meta ?? {};
  const saved = tab.savedId ? lib.find((x) => x.id === tab.savedId) : undefined;
  const versions = [...(saved?.versions ?? [])].reverse().slice(0, VERSIONS_SHOWN);
  const [open, setOpen] = useState(startOpen);
  // The tags as typed, so a comma in progress is not rewritten under the reader's finger.
  const [tags, setTags] = useState((meta.tags ?? []).join('، '));
  useEffect(() => { setTags((tab.meta?.tags ?? []).join('، ')); }, [tab.id]); // eslint-disable-line react-hooks/exhaustive-deps
  const colour = meta.colour ?? ENGINE_PLOT_COLOUR;

  return (
    <section className="card sx-mine">
      <button className="sx-mine-head" aria-expanded={open} onClick={() => setOpen(!open)}>
        <b className="fa">{L('این اسکریپت مال من است', 'This script is mine')}</b>
        <i className="sx-swatch" style={{ background: colour }} />
      </button>
      {open ? (
        <div className="sx-mine-body">
          <input className="sx-field sx-description" dir="auto" maxLength={DESCRIPTION_LIMIT} value={meta.description ?? ''} placeholder={L('یک خط درباره‌اش', 'One line about it')}
            onChange={(e) => setMeta({ description: e.target.value.replace(/\n/g, ' ') })} />
          <div className="sx-label fa">{L('رنگ', 'Colour')}</div>
          <div className="sx-swatches" role="radiogroup" aria-label={L('رنگ', 'Colour')}>
            {SWATCHES.map(([name, hex]) => (
              <button key={name} role="radio" aria-checked={colour.toLowerCase() === hex} className={`sx-swatch big${colour.toLowerCase() === hex ? ' on' : ''}`} style={{ background: hex }} title={`color.${name}`} data-colour={name}
                onClick={() => setMeta({ colour: hex === ENGINE_PLOT_COLOUR ? undefined : hex })} />
            ))}
          </div>
          <input className="sx-field sx-tags" dir="auto" value={tags} placeholder={L('برچسب‌ها، با ویرگول جدا شوند', 'Tags, separated by commas')}
            onChange={(e) => { setTags(e.target.value); setMeta({ tags: e.target.value.split(/[,،]/).map((t) => t.trim()).filter(Boolean) }); }} />
          <label className="sx-switch">
            <span className="fa">{L('در پنل جدا', 'In its own panel')}</span>
            <input type="checkbox" role="switch" checked={Boolean(meta.ownPane)} onChange={(e) => setMeta({ ownPane: e.target.checked })} />
          </label>
          <div className="sx-label fa">{L('نسخه‌های قبلی', 'Earlier versions')} <span className="muted">{L(`${n(versions.length)} نسخه نگه داشته شده`, `${versions.length} kept`)}</span></div>
          {!versions.length ? <p className="muted small fa">{L('هنوز نسخه‌ی قبلی نیست. هر بار که تغییری را ذخیره کنید یکی نگه داشته می‌شود، تا پنج تا.', 'No earlier version yet. One is kept each time you save a change, up to five.')}</p> : null}
          {versions.map((v) => (
            <div key={v.at} className="sx-version">
              <code dir="ltr">{(v.code.split('\n').find((l) => l.trim()) ?? '').slice(0, PREVIEW)}</code>
              <button className="link-btn" onClick={() => restoreVersion(saved!.id, v.at)}>{L('همین را برگردانید', 'Put this one back')}</button>
            </div>
          ))}
          <div className="row sx-actions">
            <button className="btn btn-quiet sm sx-export" onClick={onExport}>{L('خروجی گرفتن', 'Export')}</button>
            <button className="btn btn-quiet sm sx-import" onClick={onImport}>{L('ورودی گرفتن', 'Import')}</button>
          </div>
          <button className="btn btn-quiet sm sx-copy-link" onClick={onCopyLink}>{L('کپی پیوند', 'Copy the link')}</button>
          <button className="btn btn-quiet sm sx-share-board" onClick={onShare}>{L('هم‌رسانی در انجمن', 'Share on the board')}</button>
        </div>
      ) : null}
    </section>
  );
}
