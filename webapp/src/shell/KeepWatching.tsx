import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { usePrefs } from '../state/prefs';
import { useMarket, digitsOf } from '../state/market';
import { useChartBus } from '../chart/chartBus';
import { formatPrice } from '../chart/format';
import { intervalLabel, intervalSeconds } from '../data/intervals';
import { secondsToClose } from '../engine/scales_crosshair.js';
import { zoneOf } from '../chart/time';
import { Icon } from '../ui/Icon';
import { useL, pct } from '../pages/kit';
import { LINE_POINTS, countdownSeconds, countdownText, shouldPublish, visibleChange, watchShape, type WatchSnapshot } from './watch';
import './shell.css';

// «تماشای پیوسته» (chart/core ChartWatch.kt, app/watch/WatchWindow.kt): the chart in a window the
// size of a stamp that stays on top of everything else — the browser's Document Picture-in-Picture.
// Four facts and a line, no axes, no controls. The button is not drawn where the browser has no
// such window.
interface DocumentPip { requestWindow(options?: { width?: number; height?: number }): Promise<Window>; window: Window | null }
const pipApi = (): DocumentPip | undefined => (window as unknown as { documentPictureInPicture?: DocumentPip }).documentPictureInPicture;

export const keepWatchingSupported = () => Boolean(pipApi());

/** The snapshot the window draws, from the live ticker and the chart's bars. */
function readSnapshot(symbol: string, interval: string): WatchSnapshot | null {
  const tk = useMarket.getState().tickers[symbol];
  const bus = useChartBus.getState();
  const bars = bus.bars;
  const last = bars[bars.length - 1];
  const price = tk?.last ?? last?.c;
  if (price == null || !Number.isFinite(price)) return null;
  const allCloses = bars.map((b) => b.c);
  let range: { from: number; to: number } | null = null;
  try { range = bus.api?.timeScale().getVisibleLogicalRange() ?? null; } catch { range = null; }
  const changePct = range ? visibleChange(allCloses, range.from, range.to) : tk?.changePct ?? null;
  const now = Math.floor(Date.now() / 1000);
  const secs = intervalSeconds(interval);
  const tf = ({ '1W': 'W1', '1M': 'MN' } as Record<string, string>)[interval];
  let closesAt: number | null = null;
  if (last && secs > 0) closesAt = tf ? (now - last.t < secs * 1.2 ? now + secondsToClose(tf, now, zoneOf(usePrefs.getState().timezone)) : null) : last.t + secs;
  return { symbol, interval: intervalLabel(interval), price, changePct, closes: allCloses.slice(-LINE_POINTS), closesAt };
}

function WatchWindow({ symbol, interval, doc }: { symbol: string; interval: string; doc: Document }) {
  const [snap, setSnap] = useState<WatchSnapshot | null>(() => readSnapshot(symbol, interval));
  const [now, setNow] = useState(() => Math.floor(Date.now() / 1000));
  const lastAt = useRef<number | null>(null);
  const prev = useRef<WatchSnapshot | null>(null);
  const theme = usePrefs((s) => s.theme);

  useEffect(() => {
    // The window follows the page's theme.
    const root = document.documentElement;
    const theirs = doc.documentElement;
    for (const a of ['data-theme', 'lang']) { const v = root.getAttribute(a); if (v == null) theirs.removeAttribute(a); else theirs.setAttribute(a, v); }
  }, [theme, doc]);

  useEffect(() => {
    const step = () => {
      const t = Date.now();
      setNow(Math.floor(t / 1000));
      const next = readSnapshot(symbol, interval);
      if (next && shouldPublish(prev.current, next, lastAt.current, t)) { prev.current = next; lastAt.current = t; setSnap(next); }
    };
    prev.current = null; lastAt.current = null;
    step();
    const id = setInterval(step, 1000);
    return () => clearInterval(id);
  }, [symbol, interval]);

  useEffect(() => { doc.title = snap ? `${snap.symbol} ${formatPrice(snap.price, digitsOf(snap.symbol))}` : symbol; }, [doc, snap, symbol]);

  // Nothing yet: a blank stage, not a spinner — a moving object in the corner of the eye.
  if (!snap) return <div className="pip" />;
  const shape = watchShape(snap.closes);
  const left = countdownSeconds(snap.closesAt, now);
  const path = shape ? shape.points.map((p, i) => `${i ? 'L' : 'M'}${(p.x * 100).toFixed(2)},${(p.y * 20 + 1).toFixed(2)}`).join(' ') : '';
  return (
    <div className="pip" data-symbol={snap.symbol}>
      <div className="pip-head"><span className="sym">{snap.symbol}</span><span className="pip-iv num">{snap.interval.toUpperCase()}</span></div>
      <div className="pip-price num">{formatPrice(snap.price, digitsOf(snap.symbol))}</div>
      {snap.changePct != null ? <div className={`pip-chg num ${snap.changePct >= 0 ? 'up' : 'down'}`}>{pct(snap.changePct)}</div> : null}
      {shape ? (
        <svg className="pip-line" viewBox="0 0 100 22" preserveAspectRatio="none" aria-hidden>
          <path d={path} fill="none" stroke={shape.rising ? 'var(--up)' : 'var(--down)'} strokeWidth={1.5} vectorEffect="non-scaling-stroke" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      ) : null}
      {left != null ? <div className="pip-cd num">{countdownText(left)}</div> : null}
    </div>
  );
}

/** The top bar's button: opens the window, or closes it when it is open. */
export function KeepWatchingButton({ symbol, interval, className = '' }: { symbol: string; interval: string; className?: string }) {
  const L = useL();
  const [win, setWin] = useState<Window | null>(null);
  useEffect(() => () => { win?.close(); }, [win]);
  if (!pipApi()) return null;

  const open = async () => {
    if (win) { win.close(); setWin(null); return; }
    let w: Window;
    try { w = await pipApi()!.requestWindow({ width: 300, height: 170 }); } catch { return; }
    // The page's own styles (tokens, the typeface), so the window reads as the app.
    for (const node of document.querySelectorAll('link[rel="stylesheet"], style')) w.document.head.appendChild(node.cloneNode(true));
    w.document.body.className = 'pip-body';
    w.addEventListener('pagehide', () => setWin((cur) => (cur === w ? null : cur)));
    setWin(w);
  };
  const label = L('تماشای پیوسته', 'Keep watching');
  return (
    <>
      <button className={`ib keep-watching${win ? ' on' : ''} ${className}`.trim()} title={label} aria-label={label} aria-pressed={Boolean(win)} onClick={() => void open()}>
        <Icon name="ph-picture-in-picture" size="md" />
      </button>
      {win ? createPortal(<WatchWindow symbol={symbol} interval={interval} doc={win.document} />, win.document.body) : null}
    </>
  );
}
