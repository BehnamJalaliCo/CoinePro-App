// TradingView's camera menu: Download image, Copy image, Copy link, Open in new tab — then the
// picture's options (PNG or JPG, 2× resolution, the Pro Chart watermark, the caption bar with
// symbol · interval · date). "Copy link" copies the chart's own address: there is no image host.
import { useState } from 'react';
import { Dropdown } from '../ui/Menu';
import { Icon } from '../ui/Icon';
import { usePrefs } from '../state/prefs';
import { useL } from '../pages/kit';
import {
  copyChartLink, copySnapshot, downloadSnapshot, openSnapshotTab, readSnapshotOptions, writeSnapshotOptions, type SnapshotOptions,
} from '../chart/snapshot';
import { toast } from './toast';

export function SnapshotMenu({ anchor, onClose }: { anchor: HTMLElement | null; onClose(): void }) {
  const L = useL();
  const fa = usePrefs((s) => s.lang) === 'fa';
  const [opts, setOpts] = useState<SnapshotOptions>(readSnapshotOptions);
  const set = (p: Partial<SnapshotOptions>) => { const next = { ...opts, ...p }; setOpts(next); writeSnapshotOptions(next); };
  const run = (fn: () => Promise<void>) => { onClose(); void fn(); };
  const item = (icon: string, label: string, onClick: () => void, key?: string) => (
    <button className="menu-item" role="menuitem" onClick={onClick}>
      <Icon name={icon} size="sm" /><span className={fa ? 'fa' : ''}>{label}</span>{key ? <kbd className="num">{key}</kbd> : null}
    </button>
  );
  const check = (on: boolean, label: string, onClick: () => void) => (
    <button className="menu-item mcheck" role="menuitemcheckbox" aria-checked={on} onClick={onClick}>
      <span className={`tick${on ? ' on' : ''}`} aria-hidden>{on ? <Icon name="ph-check" size="sm" /> : null}</span>
      <span className={fa ? 'fa' : ''}>{label}</span>
    </button>
  );
  return (
    <Dropdown anchor={anchor} onClose={onClose} align="right" className="snap-menu tvm">
      <div dir={fa ? 'rtl' : 'ltr'}>
        <div className={`menu-title${fa ? ' fa' : ''}`}>{L('عکس چارت', 'Chart snapshot')}</div>
        {item('ph-download-simple', L('دانلود تصویر', 'Download image'), () => run(async () => {
          const ok = await downloadSnapshot();
          toast(ok ? L('تصویر ذخیره شد', 'Image saved') : L('چارت هنوز آماده نیست', 'The chart is not ready yet'));
        }), 'Alt+S')}
        {item('ph-copy', L('کپی تصویر', 'Copy image'), () => run(async () => {
          const ok = await copySnapshot();
          toast(ok ? L('تصویر در کلیپ‌بورد کپی شد', 'Image copied to the clipboard') : L('مرورگر اجازه‌ی کپی تصویر نداد؛ دانلود کنید', 'The browser did not allow copying; download it instead'));
        }))}
        {item('ph-link', L('کپی لینک', 'Copy link'), () => run(async () => {
          const url = await copyChartLink();
          toast(url ? L('لینک همین چارت (نماد و بازه) کپی شد؛ میزبانی تصویر نداریم', 'Link to this chart (symbol and interval) copied — there is no image hosting') : L('کپی لینک ممکن نشد', 'Could not copy the link'));
        }))}
        {item('ph-arrow-square-out', L('باز کردن در زبانه‌ی تازه', 'Open in new tab'), () => run(async () => { await openSnapshotTab(); }))}
        <div className="menu-sep" />
        <div className={`menu-title${fa ? ' fa' : ''}`}>{L('گزینه‌ها', 'Options')}</div>
        <div className="menu-row snap-format">
          <span className={fa ? 'fa' : ''}>{L('قالب', 'Format')}</span>
          <div className="seg" role="radiogroup">
            {(['png', 'jpg'] as const).map((f) => (
              <button key={f} role="radio" aria-checked={opts.format === f} className={opts.format === f ? 'on' : ''} onClick={() => set({ format: f })}>{f.toUpperCase()}</button>
            ))}
          </div>
        </div>
        {check(opts.hiRes, L('وضوح دوبرابر (۲×)', '2× resolution'), () => set({ hiRes: !opts.hiRes }))}
        {check(opts.watermark, L('واترمارک Pro Chart', 'Pro Chart watermark'), () => set({ watermark: !opts.watermark }))}
        {check(opts.caption, L('نوار نماد · بازه · تاریخ', 'Caption: symbol · interval · date'), () => set({ caption: !opts.caption }))}
      </div>
    </Dropdown>
  );
}
