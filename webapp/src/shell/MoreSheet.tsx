// The phone's "⋯": every top-bar button a phone has no room for, as a sheet from the bottom —
// compare, templates, undo and redo, the layout's save / save as / open / new / autosave, the
// snapshot, quick search, settings, fullscreen, publish and trade.
import { createPortal } from 'react-dom';
import { useState } from 'react';
import { Icon } from '../ui/Icon';
import { useL } from '../pages/kit';
import { usePrefs } from '../state/prefs';
import { useChartBus } from '../chart/chartBus';
import { useLayouts } from '../state/layouts';
import { copyChartLink, copySnapshot, downloadSnapshot } from '../chart/snapshot';
import { toast } from './toast';
import { openTrade, publishIdea } from './publish';
import { TemplateMenu, type LayoutDialogMode } from './SaveMenus';
import { GridMenu, GridGlyph } from './GridMenu';
import { SnapshotMenu } from './SnapshotMenu';
import { useGrid } from '../state/grid';
import type { QuickActions } from './QuickSearch';

export function MoreSheet({ onClose, actions, onDialog, onQuick }: { onClose(): void; actions: QuickActions; onDialog(m: LayoutDialogMode): void; onQuick(): void }) {
  const L = useL();
  const fa = usePrefs((s) => s.lang) === 'fa';
  const { canUndo, canRedo, drawingLayer: layer, bumpDrawings } = useChartBus();
  const { current, autosave, setAutosave, save, newLayout } = useLayouts();
  const [tpl, setTpl] = useState<HTMLElement | null>(null);
  const [grid, setGrid] = useState<HTMLElement | null>(null);
  const [snap, setSnap] = useState<HTMLElement | null>(null);
  const gridId = useGrid((g) => g.grid);
  const bump = () => { if (layer) bumpDrawings(layer.canUndo(), layer.canRedo(), layer.getDrawings().length); };
  const close = (fn: () => void) => () => { onClose(); fn(); };
  const tile = (icon: string, label: string, onClick: (el: HTMLElement) => void, opts: { disabled?: boolean; on?: boolean; className?: string } = {}) => (
    <button className={`sheet-tile${opts.on ? ' on' : ''}${opts.className ? ` ${opts.className}` : ''}`} disabled={opts.disabled} onClick={(e) => onClick(e.currentTarget)}>
      {icon === 'grid' ? <GridGlyph id={gridId} /> : <Icon name={icon} size="md" />}<span className={fa ? 'fa' : ''}>{label}</span>
    </button>
  );
  return createPortal(
    <div className="scrim sheet-scrim" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }} onKeyDown={(e) => { if (e.key === 'Escape') onClose(); }}>
      <div className="sheet" role="dialog" aria-modal="true" aria-label={L('بیشتر', 'More')} dir={fa ? 'rtl' : 'ltr'}>
        <div className="sheet-grip" aria-hidden />
        <div className={`sheet-title${fa ? ' fa' : ''}`}>{L('چارت', 'Chart')}</div>
        <div className="sheet-grid">
          {tile('ph-plus', L('مقایسه', 'Compare'), close(actions.compare))}
          {tile('layout_grid', L('قالب اندیکاتور', 'Templates'), setTpl)}
          {tile('grid', L('چند چارت', 'Multi-chart'), setGrid, { className: 'grid-tile' })}
          {tile('ph-arrow-counter-clockwise', L('واگرد', 'Undo'), () => { layer?.undo(); bump(); }, { disabled: !canUndo })}
          {tile('ph-arrow-clockwise', L('ازنو', 'Redo'), () => { layer?.redo(); bump(); }, { disabled: !canRedo })}
          {tile('search', L('جستجوی سریع', 'Quick search'), onQuick)}
          {tile('settings2', L('تنظیمات', 'Settings'), close(actions.settings))}
          {tile('maximize2', L('تمام‌صفحه', 'Fullscreen'), close(() => { if (document.fullscreenElement) void document.exitFullscreen(); else void document.documentElement.requestFullscreen?.(); }))}
          {tile('ph-lightbulb', L('انتشار ایده', 'Publish'), close(() => void publishIdea()))}
          {tile('ph-lightning', L('معامله', 'Trade'), close(openTrade))}
        </div>
        <div className={`sheet-title${fa ? ' fa' : ''}`}>{L('تصویر چارت', 'Snapshot')}</div>
        <div className="sheet-grid">
          {tile('ph-download-simple', L('دانلود', 'Download'), close(() => { void downloadSnapshot(); }))}
          {tile('ph-copy', L('کپی تصویر', 'Copy image'), close(() => { void copySnapshot().then((ok) => toast(ok ? L('تصویر کپی شد', 'Image copied') : L('مرورگر اجازه نداد؛ دانلود کنید', 'Not allowed here; download it'))); }))}
          {tile('camera', L('گزینه‌ها', 'Options'), setSnap)}
          {tile('ph-link', L('کپی لینک', 'Copy link'), close(() => { void copyChartLink().then((u) => toast(u ? L('لینک همین چارت کپی شد', 'Link to this chart copied') : L('کپی ممکن نشد', 'Could not copy'))); }))}
        </div>
        <div className={`sheet-title${fa ? ' fa' : ''}`}>{L('چیدمان', 'Layout')}{current ? <span className="muted" dir="auto"> · {current}</span> : null}</div>
        <div className="sheet-grid">
          {tile('ph-floppy-disk', L('ذخیره', 'Save'), close(() => { if (save()) toast(L('چیدمان ذخیره شد', 'Layout saved')); }))}
          {tile('ph-file-plus', L('ذخیره با نام', 'Save as'), () => onDialog('saveAs'))}
          {tile('ph-folder-open', L('باز کردن', 'Open'), () => onDialog('open'))}
          {tile('ph-plus', L('تازه', 'New'), close(newLayout))}
          {tile('ph-cloud-check', L('ذخیره‌ی خودکار', 'Autosave'), () => setAutosave(!autosave), { on: autosave })}
        </div>
        {tpl ? <TemplateMenu anchor={tpl} onClose={() => setTpl(null)} /> : null}
        {grid ? <GridMenu anchor={grid} onClose={() => setGrid(null)} /> : null}
        {snap ? <SnapshotMenu anchor={snap} onClose={() => setSnap(null)} /> : null}
      </div>
    </div>,
    document.body,
  );
}
