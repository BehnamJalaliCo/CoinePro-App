// A square picture somebody can post (OLD core/designsystem ShareCard): a title and a subtitle, a
// headline in the tone of what it says, up to three lines under it, the app's name at the foot.
// Drawn on a canvas in the app's one typeface and handed to the system share sheet, or saved as a
// PNG where the browser has no share sheet for files.
export type ShareTone = 'up' | 'down' | 'neutral';
export interface ShareCardContent { title: string; subtitle: string; headline: string; tone: ShareTone; lines: string[]; footer: string; rtl: boolean; /** «عضو بنیان‌گذار», above the foot, for a founding member (OLD ShareCard badge). */ badge?: string | null }

const css = (name: string, fallback: string) => {
  try { return getComputedStyle(document.documentElement).getPropertyValue(name).trim() || fallback; } catch { return fallback; }
};

export async function renderShareCard(c: ShareCardContent, size = 1080): Promise<HTMLCanvasElement> {
  try { await Promise.all(['500', '700'].map((w) => document.fonts?.load(`${w} 48px IRANYekanX`))); } catch { /* the fallback font draws */ }
  const cv = document.createElement('canvas');
  cv.width = size; cv.height = size;
  const g = cv.getContext('2d')!;
  const bg = css('--bg', '#FFFFFF'); const text = css('--text', '#0F0F0F'); const muted = css('--text-3', '#8C8C8C');
  const tone = c.tone === 'up' ? css('--up', '#089981') : c.tone === 'down' ? css('--down', '#F23645') : text;
  g.fillStyle = bg; g.fillRect(0, 0, size, size);
  const pad = size * 0.08;
  const x = c.rtl ? size - pad : pad;
  g.direction = c.rtl ? 'rtl' : 'ltr';
  g.textAlign = c.rtl ? 'right' : 'left';
  g.textBaseline = 'alphabetic';
  const font = (w: number, px: number) => `${w} ${Math.round(px)}px IRANYekanX, sans-serif`;
  /** Wraps a line to the card's width; returns the y after it. */
  const para = (s: string, y: number, px: number, w: number, color: string, gap = 1.45) => {
    g.font = font(w, px); g.fillStyle = color;
    const max = size - pad * 2;
    let line = '';
    for (const word of s.split(/\s+/)) {
      const next = line ? `${line} ${word}` : word;
      if (g.measureText(next).width > max && line) { g.fillText(line, x, y); y += px * gap; line = word; } else line = next;
    }
    if (line) { g.fillText(line, x, y); y += px * gap; }
    return y;
  };
  let y = pad + size * 0.05;
  y = para(c.title, y, size * 0.06, 700, text);
  y = para(c.subtitle, y, size * 0.032, 500, muted);
  y += size * 0.06;
  y = para(c.headline, y + size * 0.06, size * 0.11, 700, tone, 1.2);
  y += size * 0.03;
  for (const l of c.lines.slice(0, 3)) y = para(l, y, size * 0.034, 500, text, 1.5) + size * 0.012;
  if (c.badge) { g.font = font(700, size * 0.03); g.fillStyle = css('--gold-strong', '#B8862F'); g.fillText(c.badge, x, size - pad - size * 0.05); }
  g.font = font(700, size * 0.032); g.fillStyle = muted;
  g.fillText(c.footer, x, size - pad);
  return cv;
}

/** The system share sheet with the picture, or a download where files cannot be shared. */
export async function sharePicture(cv: HTMLCanvasElement, name: string, title: string): Promise<'shared' | 'saved'> {
  const blob = await new Promise<Blob | null>((r) => cv.toBlob(r, 'image/png'));
  if (blob) {
    const file = new File([blob], `${name}.png`, { type: 'image/png' });
    try {
      if (navigator.canShare?.({ files: [file] })) { await navigator.share({ files: [file], title }); return 'shared'; }
    } catch { /* cancelled or refused: save it instead */ }
  }
  const a = document.createElement('a');
  a.href = cv.toDataURL('image/png'); a.download = `${name}.png`; a.click();
  return 'saved';
}
