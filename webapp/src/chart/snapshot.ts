// The camera button: the chart as a PNG, with the drawings and a footer naming the symbol, the
// interval, the time and pro-chart.com — downloaded, and copied to the clipboard where allowed.
import { useChartBus } from './chartBus';
import { usePrefs } from '../state/prefs';
import { intervalLabel } from '../data/intervals';

/** The chart as a PNG with its drawings and the footer, or null when no chart is mounted. */
export async function chartImage(): Promise<Blob | null> {
  const api = useChartBus.getState().api;
  if (!api) return null;
  const shot = api.takeScreenshot();
  const draw = document.querySelector<HTMLCanvasElement>('.draw-layer');
  const footer = 28;
  const out = document.createElement('canvas');
  out.width = shot.width;
  out.height = shot.height + footer;
  const ctx = out.getContext('2d')!;
  const dark = getComputedStyle(document.body).backgroundColor !== 'rgb(255, 255, 255)';
  ctx.fillStyle = dark ? '#0F0F0F' : '#FFFFFF';
  ctx.fillRect(0, 0, out.width, out.height);
  ctx.drawImage(shot, 0, 0);
  if (draw && draw.style.visibility !== 'hidden') ctx.drawImage(draw, 0, 0, shot.width, shot.height);
  const { symbol, interval } = usePrefs.getState();
  const now = new Date();
  const stamp = `${now.getUTCFullYear()}-${String(now.getUTCMonth() + 1).padStart(2, '0')}-${String(now.getUTCDate()).padStart(2, '0')} ${String(now.getUTCHours()).padStart(2, '0')}:${String(now.getUTCMinutes()).padStart(2, '0')} UTC`;
  ctx.fillStyle = dark ? '#B8B8B8' : '#4A4A4A';
  ctx.font = "12px 'IRANYekanX', sans-serif";
  ctx.textBaseline = 'middle';
  ctx.fillText(`${symbol} · ${intervalLabel(interval)} · ${stamp}`, 12, shot.height + footer / 2);
  ctx.textAlign = 'right';
  ctx.fillStyle = '#D4A64A';
  ctx.fillText('pro-chart.com', out.width - 12, shot.height + footer / 2);

  return new Promise<Blob | null>((r) => out.toBlob(r, 'image/png'));
}

export async function takeSnapshot(): Promise<void> {
  const blob = await chartImage();
  if (!blob) return;
  const { symbol, interval } = usePrefs.getState();
  const now = new Date();
  try { await navigator.clipboard?.write?.([new ClipboardItem({ 'image/png': blob })]); } catch { /* clipboard not allowed: the download still happens */ }
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = `${symbol}_${intervalLabel(interval)}_${now.getTime()}.png`;
  a.click();
  setTimeout(() => URL.revokeObjectURL(a.href), 4000);
}
