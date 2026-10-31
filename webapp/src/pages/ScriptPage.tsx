import { useEffect, useMemo, useState } from 'react';
import { useScript } from '../state/script';
import { usePage } from '../state/page';
import { usePanels } from '../state/panels';
import { usePrefs } from '../state/prefs';
import { intervalLabel } from '../data/intervals';
import { NamaScriptPanel } from '../shell/NamaScriptPanel';
import { setPendingPost } from '../shell/publish';
import { readPaste, type Paste } from '../script/paste';
import { scriptPrompt } from '../script/promptKit';
import { readScriptFile, sharePost, shareRefusals, writeScriptFile } from '../script/doc';
import { currentDocument, openDocument } from '../script/open';
import { PasteBox, PasteSheet, PromptSheet, readClipboard, writeClipboard } from '../script/ScriptSheets';
import { MinePanel } from '../script/MinePanel';
import { LessonsTab } from '../script/LessonsTab';
import { Tabs, downloadText, pickText, useL } from './kit';

// NamaScript as a page (feature/script): the reader's library of saved scripts beside the editor,
// a converter for code pasted from Pine, files in and out, and a share link that carries the
// script itself (compressed in the link's fragment, so no server ever holds it). Beside them, from
// the phone's studio: «چسباندن» (whatever is pasted, read, repaired and undoable), «گرفتن اسکریپت از
// هوش مصنوعی» (the prompt kit), «این اسکریپت مال من است», sharing on the board, and the lessons.
/** Pine habits this engine reads differently, rewritten; each rewrite is reported. */
export function convertPine(src: string): { code: string; fixes: string[] } {
  const fixes: string[] = [];
  let code = src;
  // Rewrites only outside string literals, so a title that says "and" keeps its word.
  const rule = (name: string, re: RegExp, to: string | ((...m: string[]) => string)) => {
    const next = code.split(/("(?:[^"\\\n]|\\.)*"|'(?:[^'\\\n]|\\.)*')/).map((part, i) => (i % 2 ? part : part.replace(re, to as never))).join('');
    if (next !== code) { fixes.push(name); code = next; }
  };
  rule('//@version', /^\s*\/\/@version\s*=\s*\d+\s*$/gm, '');
  rule('and → &&', /\band\b/g, '&&');
  rule('or → ||', /\bor\b/g, '||');
  rule('not → !', /\bnot\s+/g, '!');
  rule('min/maxval', /\b(min|max)val\s*=/g, (_m, a) => `${a}=`);
  rule('linewidth → width', /\blinewidth\s*=/g, 'width=');
  rule('“ ” « » → "', /[“”«»]/g, '"');
  rule('۰-۹ → 0-9', /[۰-۹]/g, (d) => String(d.charCodeAt(0) - 0x06f0));
  rule('na comparison', /([A-Za-z_]\w*)\s*==\s*na\b/g, (_m, a) => `na(${a})`);
  return { code, fixes };
}

async function pack(text: string): Promise<string> {
  const bytes = new TextEncoder().encode(text);
  let out = bytes;
  if (typeof CompressionStream === 'function') {
    const s = new Blob([bytes]).stream().pipeThrough(new CompressionStream('deflate-raw'));
    out = new Uint8Array(await new Response(s).arrayBuffer());
  }
  let bin = ''; for (const b of out) bin += String.fromCharCode(b);
  return (typeof CompressionStream === 'function' ? 'z' : 'r') + btoa(bin).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

export async function unpack(s: string): Promise<string | null> {
  try {
    const raw = atob(s.slice(1).replace(/-/g, '+').replace(/_/g, '/'));
    const bytes = Uint8Array.from(raw, (c) => c.charCodeAt(0));
    if (s[0] === 'z') {
      const st = new Blob([bytes]).stream().pipeThrough(new DecompressionStream('deflate-raw'));
      return await new Response(st).text();
    }
    return new TextDecoder().decode(bytes);
  } catch { return null; }
}

export default function ScriptPage({ param }: { param: string | null }) {
  const L = useL();
  const english = usePrefs((s) => s.lang) !== 'fa';
  const symbol = usePrefs((s) => s.symbol);
  const interval = usePrefs((s) => s.interval);
  const { source, apply, lib, tabs, active, save: saveTab, open, deleteSaved, newTab, rename, switchTab, setSource } = useScript();
  const close = usePage((s) => s.close);
  const openPage = usePage((s) => s.open);
  const setBottom = usePanels((s) => s.setBottom);
  const current = tabs.find((t) => t.id === active)?.savedId ?? null;
  const [note, setNote] = useState<string | null>(null);
  const [paste, setPaste] = useState<string | null>(null);
  const [view, setView] = useState<'editor' | 'lessons'>('editor');
  // The paste just read, and the tab it went into (for the undo).
  const [pasted, setPasted] = useState<{ paste: Paste; tabId: string | null } | null>(null);
  const [box, setBox] = useState<'paste' | 'import' | null>(null);
  const [prompt, setPrompt] = useState(false);

  // A shared link: #ns=<packed> opens that script; /s/<id> opens a saved one of this browser.
  useEffect(() => {
    const h = /#ns=([A-Za-z0-9_-]+)/.exec(window.location.hash);
    if (h) { void unpack(h[1]!).then((code) => { if (code) { newTab(code, /\b(?:indicator|strategy)\s*\(\s*["']([^"']+)/.exec(code)?.[1] ?? L('اسکریپت اشتراکی', 'Shared script')); setNote(L('اسکریپت اشتراکی باز شد؛ برای نگه داشتن ذخیره کنید.', 'The shared script is open; save it to keep it.')); } }); return; }
    if (param) {
      if (lib.some((x) => x.id === param)) open(param); else setNote(L('این اسکریپت در این مرورگر نیست.', 'That script is not in this browser.'));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const save = (asNew = false) => {
    const have = lib.find((x) => x.id === current);
    const name = asNew || !have ? window.prompt(L('نام اسکریپت', 'Script name'), have?.name ?? /indicator\s*\(\s*["']([^"']+)/.exec(source)?.[1] ?? 'NamaScript') : have.name;
    if (!name) return;
    if (!have || asNew) rename(name);
    saveTab(asNew, name);
    setNote(L('ذخیره شد.', 'Saved.'));
  };
  const shareUrl = async () => `${window.location.origin}${import.meta.env.BASE_URL}script#ns=${await pack(source)}`;
  const share = async () => {
    const url = await shareUrl();
    try { await navigator.clipboard.writeText(url); setNote(L('پیوند اشتراک کپی شد.', 'The share link is copied.')); } catch { window.prompt('URL', url); }
  };
  const converted = useMemo(() => (paste ? convertPine(paste) : null), [paste]);

  /** Bring your own script: read it, repair it into a new tab (the raw text kept for the undo), and say what changed. */
  const take = (p: Paste) => {
    setBox(null); setView('editor');
    if (p.dialect === 'prose') { setPasted({ paste: p, tabId: null }); return; }
    newTab(p.fixed, /\b(?:indicator|study|strategy)\s*\(\s*["']([^"']+)/.exec(p.fixed)?.[1] ?? L('اسکریپت چسبانده‌شده', 'Pasted script'));
    setPasted({ paste: p, tabId: useScript.getState().active });
  };
  /** Text arriving by «ورودی گرفتن»: a whole .nama file opens with its name and fields; anything else is a paste. */
  const incoming = (text: string) => {
    const doc = readScriptFile(text);
    if (doc) { setBox(null); setView('editor'); openDocument(doc); setNote(null); return; }
    if (text.trim()) { take(readPaste(text)); return; }
    setNote(L('اسکریپتی در حافظه‌ی موقت نیست.', 'There is no script in the clipboard.'));
  };
  const fromClipboard = async (kind: 'paste' | 'import') => {
    const text = await readClipboard();
    if (text == null) { setBox(kind); return; }
    if (kind === 'import') { incoming(text); return; }
    if (!text.trim()) { setNote(L('چیزی در حافظه‌ی موقت نیست.', 'There is nothing in the clipboard.')); return; }
    take(readPaste(text));
  };
  const exportDoc = async () => {
    const ok = await writeClipboard(writeScriptFile(currentDocument()));
    setNote(ok ? L('کپی شد. هر جا بخواهید بچسبانید و نگه دارید.', 'Copied. Paste it anywhere to keep it.') : null);
    if (!ok) downloadText(`${currentDocument().name || 'script'}.nama`, writeScriptFile(currentDocument()), 'text/plain');
  };
  const copyLink = async () => {
    const url = await shareUrl();
    if (await writeClipboard(url)) setNote(L('پیوند کپی شد.', 'Link copied.')); else window.prompt('URL', url);
  };
  /** «هم‌رسانی در انجمن»: the post carries the code itself (the board refuses links), checked against its rules first. */
  const shareBoard = () => {
    const body = sharePost(currentDocument(), english);
    if (shareRefusals(body).length) { setNote(L('انجمن پیوند، شماره تلفن و آی‌دی را نمی‌پذیرد. این اسکریپت یکی از این‌ها را دارد.', 'The board refuses links, phone numbers and handles. This script has one in it.')); return; }
    setPendingPost({ image: null, text: body });
    openPage('community', 'compose');
  };

  return (
    <div className="page-inner script-page">
      <Tabs value={view} onChange={setView} tabs={[{ id: 'editor', label: L('ویرایشگر', 'Editor') }, { id: 'lessons', label: L('آموزش', 'Lessons') }]} />
      {view === 'lessons' ? <LessonsTab onTryExample={(code, title) => { newTab(code, title); setView('editor'); }} /> : null}
      {view === 'editor' ? (
      <>
      <div className="row">
        <button className="btn btn-primary sm" onClick={() => save(false)}>{L('ذخیره', 'Save')}</button>
        <button className="btn btn-quiet sm" onClick={() => save(true)}>{L('ذخیره با نام تازه', 'Save as new')}</button>
        <button className="btn btn-quiet sm" onClick={() => { apply(); if (usePanels.getState().bottom !== 'namascript') setBottom('namascript'); close(); }}>{L('اجرا روی چارت', 'Run on the chart')}</button>
        <button className="btn btn-quiet sm" onClick={() => void share()}>{L('پیوند اشتراک', 'Share link')}</button>
        <button className="btn btn-quiet sm" onClick={() => downloadText(`${lib.find((x) => x.id === current)?.name ?? 'script'}.ns`, source, 'text/plain')}>{L('خروجی فایل', 'Export')}</button>
        <button className="btn btn-quiet sm" onClick={() => void pickText('.ns,.nama,.txt,.pine,text/plain').then((t) => { if (t != null) { const doc = readScriptFile(t); if (doc) openDocument(doc); else newTab(t, L('فایل واردشده', 'Imported')); } })}>{L('ورود فایل', 'Import')}</button>
        <button className="btn btn-quiet sm" onClick={() => setPaste('')}>{L('تبدیل از Pine', 'Convert from Pine')}</button>
        <button className="btn btn-quiet sm script-paste" onClick={() => void fromClipboard('paste')}>{L('چسباندن', 'Paste')}</button>
        <button className="btn btn-quiet sm script-prompt-kit" onClick={() => setPrompt(true)}>{L('گرفتن اسکریپت از هوش مصنوعی', 'Get a script from an AI')}</button>
        {note ? <span className="muted fa script-note" role="status">{note}</span> : null}
      </div>
      {paste != null ? (
        <section className="card">
          <h3 className="fa">{L('کد Pine را اینجا بچسبانید', 'Paste the Pine code here')}</h3>
          <textarea className="ns-code paste" rows={8} dir="ltr" spellCheck={false} value={paste} onChange={(e) => setPaste(e.target.value)} />
          {converted?.fixes.length ? <ul className="muted small">{converted.fixes.map((f) => <li key={f} className="num">{f}</li>)}</ul> : null}
          <div className="row">
            <button className="btn btn-primary sm" disabled={!paste.trim()} onClick={() => { if (converted) { newTab(converted.code, L('تبدیل از Pine', 'From Pine')); setPaste(null); } }}>{L('باز کردن در ویرایشگر', 'Open in the editor')}</button>
            <button className="btn btn-quiet sm" onClick={() => setPaste(null)}>{L('انصراف', 'Cancel')}</button>
          </div>
        </section>
      ) : null}
      <div className="split">
        <aside className="script-side">
          <div className="card script-lib">
            <h3 className="fa">{L('اسکریپت‌های من', 'My scripts')}</h3>
            {!lib.length ? <p className="muted fa small">{L('هنوز اسکریپتی ذخیره نکرده‌اید.', 'No saved scripts yet.')}</p> : null}
            {lib.map((s) => (
              <div key={s.id} className={`lib-row${s.id === current ? ' on' : ''}`}>
                <button className="link-btn" onClick={() => open(s.id)}>{s.name}</button>
                <button className="lt" title={L('حذف', 'Delete')} onClick={() => { if (window.confirm(L('حذف شود؟', 'Delete?'))) deleteSaved(s.id); }}>×</button>
              </div>
            ))}
          </div>
          <MinePanel onExport={() => void exportDoc()} onImport={() => void fromClipboard('import')} onCopyLink={() => void copyLink()} onShare={shareBoard} />
        </aside>
        <div className="grow script-editor"><NamaScriptPanel /></div>
      </div>
      </>
      ) : null}
      {box ? <PasteBox onClose={() => setBox(null)} onRead={(p) => (box === 'import' ? incoming(p.raw) : take(p))} /> : null}
      {pasted ? (
        <PasteSheet paste={pasted.paste} onKeep={() => setPasted(null)}
          onUndo={() => { if (pasted.tabId) { switchTab(pasted.tabId); setSource(pasted.paste.raw); } setPasted(null); }}
          onUseTemplate={(t, src) => { newTab(src, english ? t.titleEn : t.title); setPasted(null); }} />
      ) : null}
      {prompt ? <PromptSheet prompt={scriptPrompt(symbol, intervalLabel(interval), english)} onClose={() => setPrompt(false)} onPasteResult={() => { setPrompt(false); void fromClipboard('paste'); }} /> : null}
    </div>
  );
}
