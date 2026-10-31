// The clipboard, read and written where the browser allows it.

/** The clipboard's text; null where the browser will not hand it over (the caller offers a box instead). */
export async function readClipboard(): Promise<string | null> {
  try { return typeof navigator.clipboard?.readText === 'function' ? await navigator.clipboard.readText() : null; } catch { return null; }
}

export async function writeClipboard(text: string): Promise<boolean> {
  try { await navigator.clipboard.writeText(text); return true; } catch { /* no async clipboard here */ }
  // The older way, which works where the async clipboard is missing (a page not served over https).
  try {
    const ta = document.createElement('textarea');
    ta.value = text; ta.setAttribute('readonly', ''); ta.style.position = 'fixed'; ta.style.opacity = '0';
    document.body.appendChild(ta); ta.select();
    const ok = document.execCommand('copy');
    ta.remove();
    return ok;
  } catch { return false; }
}
