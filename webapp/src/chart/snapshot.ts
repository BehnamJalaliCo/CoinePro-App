// The camera menu's picture: every chart of the layout as it is on screen — the chart canvases
// (panes, axes, series, primitives), the layers over them (drawings, session breaks and shading,
// the automatic overlay, the volume profile, anything else drawn on a canvas over the chart) and
// the legend text — with an optional caption bar (symbol · interval · date) and the Pro Chart
// watermark, as PNG or JPG, at 1× or 2×. Downloaded, copied, opened in a tab; "copy link" copies
// the chart's own address (there is no image hosting to upload to).
import type { IChartApi } from 'lightweight-charts';
import { useChartBus } from './chartBus';
import { usePrefs, isDark } from '../state/prefs';
import { intervalLabel } from '../data/intervals';
import { liteCharts } from './LiteChart';
import { chartPath } from '../route';

export interface SnapshotOptions { format: 'png' | 'jpg'; hiRes: boolean; watermark: boolean; caption: boolean }
const KEY = 'pc.snapshot.v1';
export const DEFAULT_SNAPSHOT: SnapshotOptions = { format: 'png', hiRes: false, watermark: true, caption: true };

export function readSnapshotOptions(): SnapshotOptions {
  try { return { ...DEFAULT_SNAPSHOT, ...(JSON.parse(localStorage.getItem(KEY) ?? '{}') as Partial<SnapshotOptions>) }; } catch { return DEFAULT_SNAPSHOT; }
}
export function writeSnapshotOptions(o: SnapshotOptions): void {
  try { localStorage.setItem(KEY, JSON.stringify(o)); } catch { /* storage blocked */ }
}

const BASE = import.meta.env.BASE_URL;
let brand: Promise<HTMLImageElement | null> | null = null;
function brandImage(): Promise<HTMLImageElement | null> {
  brand ??= new Promise((resolve) => {
    const img = new Image();
    img.onload = () => resolve(img);
    img.onerror = () => resolve(null);
    img.src = `${BASE}generated/app-icons/icon-192.png`;
  });
  return brand;
}

const visible = (el: Element) => {
  const cs = getComputedStyle(el);
  return cs.display !== 'none' && cs.visibility !== 'hidden' && Number(cs.opacity) > 0;
};

/** The text of a legend, drawn where it stands on screen, in its own colour and weight. */
function paintText(ctx: CanvasRenderingContext2D, root: Element, origin: DOMRect): void {
  const skip = '.legend-tools, .lite-tools, .legend-trade, button.lt, .legend-collapse';
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
  for (let n = walker.nextNode(); n; n = walker.nextNode()) {
    const text = n.textContent ?? '';
    const parent = n.parentElement;
    if (!text.trim() || !parent || parent.closest(skip) || !visible(parent)) continue;
    const range = document.createRange();
    range.selectNodeContents(n);
    const r = range.getClientRects()[0];
    if (!r || !r.width) continue;
    const cs = getComputedStyle(parent);
    const size = parseFloat(cs.fontSize) || 12;
    ctx.font = `${cs.fontWeight} ${size}px IRANYekanX, sans-serif`;
    ctx.fillStyle = cs.color;
    ctx.textBaseline = 'middle';
    const rtl = cs.direction === 'rtl';
    ctx.direction = rtl ? 'rtl' : 'ltr';
    ctx.textAlign = rtl ? 'right' : 'left';
    ctx.fillText(text.replace(/\s+/g, ' '), (rtl ? r.right : r.left) - origin.left, r.top + r.height / 2 - origin.top);
  }
  for (const img of root.querySelectorAll<HTMLImageElement>('img')) {
    if (!img.complete || !visible(img) || img.closest(skip)) continue;
    const r = img.getBoundingClientRect();
    try {
      ctx.save();
      ctx.beginPath();
      ctx.arc(r.left - origin.left + r.width / 2, r.top - origin.top + r.height / 2, Math.min(r.width, r.height) / 2, 0, Math.PI * 2);
      ctx.clip();
      ctx.drawImage(img, r.left - origin.left, r.top - origin.top, r.width, r.height);
      ctx.restore();
    } catch { /* a tainted or broken image is left out */ }
  }
}

/** One chart: its own screenshot over its host, then every canvas laid over it, then its legend. */
function paintChart(ctx: CanvasRenderingContext2D, api: IChartApi, cell: Element, origin: DOMRect): void {
  const host = cell.querySelector('.chart-host, .lite-host') ?? cell;
  const hr = host.getBoundingClientRect();
  try { ctx.drawImage(api.takeScreenshot(), hr.left - origin.left, hr.top - origin.top, hr.width, hr.height); } catch { /* chart not drawn yet */ }
  for (const c of cell.querySelectorAll<HTMLCanvasElement>('canvas')) {
    if (c.closest('.chart-host, .lite-host') || !visible(c) || !c.width || !c.height) continue;
    const r = c.getBoundingClientRect();
    try { ctx.drawImage(c, r.left - origin.left, r.top - origin.top, r.width, r.height); } catch { /* skip */ }
  }
  for (const legend of cell.querySelectorAll('.legend, .lite-legend')) paintText(ctx, legend, origin);
}

/** The layout as one canvas, or null when no chart is mounted. */
export async function composeSnapshot(opts: SnapshotOptions = readSnapshotOptions()): Promise<HTMLCanvasElement | null> {
  const api = useChartBus.getState().api;
  if (!api) return null;
  const mainEl = document.querySelector('.cell-main') ?? document.querySelector('.ws-chart');
  if (!mainEl) return null;
  const others = [...liteCharts().entries()].filter(([, v]) => v.el.isConnected && visible(v.el));
  // Another chart maximised (Alt+Enter) hides the main one: the picture is of what is shown.
  const mainShown = visible(mainEl) && mainEl.getBoundingClientRect().width > 0;
  if (!mainShown && !others.length) return null;
  const area = (others.length ? document.querySelector('.chart-grid') : mainEl) ?? mainEl;
  const origin = area.getBoundingClientRect();
  const dark = isDark(usePrefs.getState().theme);
  const bg = dark ? '#0F0F0F' : '#FFFFFF';
  const caption = opts.caption ? 32 : 0;
  const s = opts.hiRes ? 2 : 1;

  const out = document.createElement('canvas');
  out.width = Math.round(origin.width * s);
  out.height = Math.round((origin.height + caption) * s);
  const ctx = out.getContext('2d');
  if (!ctx) return null;
  ctx.scale(s, s);
  ctx.imageSmoothingQuality = 'high';
  ctx.fillStyle = dark ? '#2E2E2E' : '#EBEBEB';
  ctx.fillRect(0, 0, origin.width, origin.height + caption);
  ctx.fillStyle = bg;
  ctx.fillRect(0, 0, origin.width, origin.height);

  if (mainShown) paintChart(ctx, api, mainEl, origin);
  for (const [, { api: a, el }] of others) {
    const r = el.getBoundingClientRect();
    ctx.fillStyle = bg;
    ctx.fillRect(r.left - origin.left, r.top - origin.top, r.width, r.height);
    paintChart(ctx, a, el, origin);
  }

  const { symbol, interval } = usePrefs.getState();
  if (opts.watermark) {
    const img = await brandImage();
    const x = 14;
    const y = origin.height - 46;
    ctx.save();
    ctx.globalAlpha = 0.55;
    if (img) ctx.drawImage(img, x, y, 26, 26);
    ctx.font = "600 15px IRANYekanX, sans-serif";
    ctx.fillStyle = dark ? '#DBDBDB' : '#0F0F0F';
    ctx.textBaseline = 'middle';
    ctx.textAlign = 'left';
    ctx.direction = 'ltr';
    ctx.fillText('Pro Chart', x + (img ? 32 : 0), y + 13);
    ctx.restore();
  }
  if (caption) {
    const y = origin.height + caption / 2;
    ctx.fillStyle = bg;
    ctx.fillRect(0, origin.height + 1, origin.width, caption - 1);
    const now = new Date();
    let stamp = '';
    const tz = usePrefs.getState().timezone;
    try {
      stamp = new Intl.DateTimeFormat('en-GB', { timeZone: tz, year: 'numeric', month: 'short', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }).format(now);
    } catch { stamp = now.toISOString().slice(0, 16).replace('T', ' '); }
    ctx.font = "500 12px IRANYekanX, sans-serif";
    ctx.textBaseline = 'middle';
    ctx.direction = 'ltr';
    ctx.textAlign = 'left';
    ctx.fillStyle = dark ? '#DBDBDB' : '#0F0F0F';
    ctx.fillText(`${symbol} · ${intervalLabel(interval)} · ${stamp}`, 12, y);
    ctx.textAlign = 'right';
    ctx.fillStyle = '#D4A64A';
    ctx.fillText('pro-chart.com', origin.width - 12, y);
  }
  return out;
}

const mime = (f: SnapshotOptions['format']) => (f === 'jpg' ? 'image/jpeg' : 'image/png');

export async function snapshotBlob(opts: SnapshotOptions = readSnapshotOptions()): Promise<Blob | null> {
  const c = await composeSnapshot(opts);
  if (!c) return null;
  return new Promise<Blob | null>((r) => c.toBlob(r, mime(opts.format), opts.format === 'jpg' ? 0.92 : undefined));
}

/** The chart as a PNG (the clipboard takes PNG only), for "Copy image" and the context menu. */
export async function chartImage(): Promise<Blob | null> {
  return snapshotBlob({ ...readSnapshotOptions(), format: 'png' });
}

export function snapshotName(ext: string): string {
  const { symbol, interval } = usePrefs.getState();
  const safe = symbol.replace(/[^A-Za-z0-9]+/g, '_');
  const d = new Date();
  const p2 = (n: number) => String(n).padStart(2, '0');
  return `${safe}_${intervalLabel(interval)}_${d.getFullYear()}-${p2(d.getMonth() + 1)}-${p2(d.getDate())}_${p2(d.getHours())}-${p2(d.getMinutes())}-${p2(d.getSeconds())}.${ext}`;
}

/** Download image. */
export async function downloadSnapshot(): Promise<boolean> {
  const opts = readSnapshotOptions();
  const blob = await snapshotBlob(opts);
  if (!blob) return false;
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = snapshotName(opts.format);
  a.click();
  setTimeout(() => URL.revokeObjectURL(a.href), 4000);
  return true;
}

/** Copy image: false where the browser does not allow it. */
export async function copySnapshot(): Promise<boolean> {
  const blob = await chartImage();
  if (!blob || typeof ClipboardItem === 'undefined' || !navigator.clipboard?.write) return false;
  try { await navigator.clipboard.write([new ClipboardItem({ 'image/png': blob })]); return true; } catch { return false; }
}

/** Copy link: the chart's own address (symbol and interval), not an image — there is no image host. */
export async function copyChartLink(): Promise<string | null> {
  const { symbol, interval } = usePrefs.getState();
  const url = `${window.location.origin}${chartPath(symbol, interval)}`;
  try { await navigator.clipboard.writeText(url); return url; } catch { /* fall back below */ }
  try {
    const ta = document.createElement('textarea');
    ta.value = url; ta.style.position = 'fixed'; ta.style.opacity = '0';
    document.body.appendChild(ta); ta.select();
    const ok = document.execCommand('copy');
    ta.remove();
    return ok ? url : null;
  } catch { return null; }
}

/** Open in new tab: the tab is opened at the click, so no pop-up blocker stops it, then filled. */
export async function openSnapshotTab(): Promise<boolean> {
  const tab = window.open('', '_blank');
  const opts = readSnapshotOptions();
  const blob = await snapshotBlob(opts);
  if (!blob) { tab?.close(); return false; }
  const url = URL.createObjectURL(blob);
  if (tab) tab.location.href = url; else window.open(url, '_blank');
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
  return true;
}

/** Alt+S and the old one-click camera: download, and copy where allowed. */
export async function takeSnapshot(): Promise<void> {
  const ok = await downloadSnapshot();
  if (ok) void copySnapshot();
}
