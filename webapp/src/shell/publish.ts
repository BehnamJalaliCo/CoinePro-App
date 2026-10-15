// "Publish idea" and "Trade" from the top bar. Publish hands the chart's picture and a first line
// to the community composer, which takes them once when it opens (pages/CommunityPage.tsx). Trade
// asks for the order panel (whoever mounts one answers `pc:trade` and cancels it); with none, it
// opens the paper-trading page.
import { usePrefs } from '../state/prefs';
import { usePage } from '../state/page';
import { snapshotBlob } from '../chart/snapshot';
import { intervalLabel } from '../data/intervals';

export interface PendingPost { image: string | null; text: string }
let pending: PendingPost | null = null;

export function setPendingPost(p: PendingPost): void { pending = p; }

/** The post waiting for the composer, given once. */
export function takePendingPost(): PendingPost | null {
  const p = pending;
  pending = null;
  return p;
}

export function openTrade(): void {
  const e = new CustomEvent('pc:trade', { cancelable: true, detail: { symbol: usePrefs.getState().symbol } });
  if (window.dispatchEvent(e)) usePage.getState().open('paper');
}

export async function publishIdea(): Promise<void> {
  const { symbol, interval } = usePrefs.getState();
  let image: string | null = null;
  try {
    const blob = await snapshotBlob({ format: 'jpg', hiRes: false, watermark: true, caption: true });
    if (blob) image = await new Promise<string>((r) => { const fr = new FileReader(); fr.onload = () => r(String(fr.result)); fr.readAsDataURL(blob); });
  } catch { /* the post goes without the picture */ }
  setPendingPost({ image, text: `${symbol} · ${intervalLabel(interval)}\n` });
  usePage.getState().open('community', 'compose');
}
