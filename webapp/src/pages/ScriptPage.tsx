import { useEffect, useMemo, useState } from 'react';
import { useScript } from '../state/script';
import { usePage } from '../state/page';
import { usePanels } from '../state/panels';
import { uuid } from '../data/http';
import { NamaScriptPanel } from '../shell/NamaScriptPanel';
import { downloadText, pickText, useL } from './kit';

// NamaScript as a page (feature/script): the reader's library of saved scripts beside the editor,
// a converter for code pasted from Pine, files in and out, and a share link that carries the
// script itself (compressed in the link's fragment, so no server ever holds it).
interface Saved { id: string; name: string; code: string; updatedAt: number }
const KEY = 'pc.scripts.v1';
const readLib = (): Saved[] => { try { const v = JSON.parse(localStorage.getItem(KEY) ?? '[]') as unknown; return Array.isArray(v) ? (v as Saved[]) : []; } catch { return []; } };
const writeLib = (l: Saved[]) => { try { localStorage.setItem(KEY, JSON.stringify(l)); } catch { /* full */ } };

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
  const { source, setSource, apply } = useScript();
  const close = usePage((s) => s.close);
  const setBottom = usePanels((s) => s.setBottom);
  const [lib, setLib] = useState<Saved[]>(readLib);
  const [current, setCurrent] = useState<string | null>(null);
  const [note, setNote] = useState<string | null>(null);
  const [paste, setPaste] = useState<string | null>(null);

  // A shared link: #ns=<packed> opens that script; /s/<id> opens a saved one of this browser.
  useEffect(() => {
    const h = /#ns=([A-Za-z0-9_-]+)/.exec(window.location.hash);
    if (h) { void unpack(h[1]!).then((code) => { if (code) { setSource(code); setNote(L('اسکریپت اشتراکی باز شد؛ برای نگه داشتن ذخیره کنید.', 'The shared script is open; save it to keep it.')); } }); return; }
    if (param) {
      const s = readLib().find((x) => x.id === param);
      if (s) { setSource(s.code); setCurrent(s.id); } else setNote(L('این اسکریپت در این مرورگر نیست.', 'That script is not in this browser.'));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const save = (asNew = false) => {
    const have = lib.find((x) => x.id === current);
    const name = asNew || !have ? window.prompt(L('نام اسکریپت', 'Script name'), have?.name ?? /indicator\s*\(\s*["']([^"']+)/.exec(source)?.[1] ?? 'NamaScript') : have.name;
    if (!name) return;
    const id = asNew || !have ? uuid().slice(0, 8) : have.id;
    const next = [{ id, name, code: source, updatedAt: Date.now() }, ...lib.filter((x) => x.id !== id)];
    setLib(next); writeLib(next); setCurrent(id); setNote(L('ذخیره شد.', 'Saved.'));
  };
  const share = async () => {
    const url = `${window.location.origin}${import.meta.env.BASE_URL}script#ns=${await pack(source)}`;
    try { await navigator.clipboard.writeText(url); setNote(L('پیوند اشتراک کپی شد.', 'The share link is copied.')); } catch { window.prompt('URL', url); }
  };
  const converted = useMemo(() => (paste ? convertPine(paste) : null), [paste]);

  return (
    <div className="page-inner script-page">
      <div className="row">
        <button className="btn btn-primary sm" onClick={() => save(false)}>{L('ذخیره', 'Save')}</button>
        <button className="btn btn-quiet sm" onClick={() => save(true)}>{L('ذخیره با نام تازه', 'Save as new')}</button>
        <button className="btn btn-quiet sm" onClick={() => { apply(); setBottom('namascript'); close(); }}>{L('اجرا روی چارت', 'Run on the chart')}</button>
        <button className="btn btn-quiet sm" onClick={() => void share()}>{L('پیوند اشتراک', 'Share link')}</button>
        <button className="btn btn-quiet sm" onClick={() => downloadText(`${lib.find((x) => x.id === current)?.name ?? 'script'}.ns`, source, 'text/plain')}>{L('خروجی فایل', 'Export')}</button>
        <button className="btn btn-quiet sm" onClick={() => void pickText('.ns,.txt,.pine,text/plain').then((t) => { if (t != null) { setSource(t); setCurrent(null); } })}>{L('ورود فایل', 'Import')}</button>
        <button className="btn btn-quiet sm" onClick={() => setPaste('')}>{L('تبدیل از Pine', 'Convert from Pine')}</button>
        {note ? <span className="muted fa">{note}</span> : null}
      </div>
      {paste != null ? (
        <section className="card">
          <h3 className="fa">{L('کد Pine را اینجا بچسبانید', 'Paste the Pine code here')}</h3>
          <textarea className="ns-code paste" rows={8} dir="ltr" spellCheck={false} value={paste} onChange={(e) => setPaste(e.target.value)} />
          {converted?.fixes.length ? <ul className="muted small">{converted.fixes.map((f) => <li key={f} className="num">{f}</li>)}</ul> : null}
          <div className="row">
            <button className="btn btn-primary sm" disabled={!paste.trim()} onClick={() => { if (converted) { setSource(converted.code); setCurrent(null); setPaste(null); } }}>{L('باز کردن در ویرایشگر', 'Open in the editor')}</button>
            <button className="btn btn-quiet sm" onClick={() => setPaste(null)}>{L('انصراف', 'Cancel')}</button>
          </div>
        </section>
      ) : null}
      <div className="split">
        <aside className="card script-lib">
          <h3 className="fa">{L('اسکریپت‌های من', 'My scripts')}</h3>
          {!lib.length ? <p className="muted fa small">{L('هنوز اسکریپتی ذخیره نکرده‌اید.', 'No saved scripts yet.')}</p> : null}
          {lib.map((s) => (
            <div key={s.id} className={`lib-row${s.id === current ? ' on' : ''}`}>
              <button className="link-btn" onClick={() => { setSource(s.code); setCurrent(s.id); }}>{s.name}</button>
              <button className="lt" title={L('حذف', 'Delete')} onClick={() => { if (window.confirm(L('حذف شود؟', 'Delete?'))) { const next = lib.filter((x) => x.id !== s.id); setLib(next); writeLib(next); if (current === s.id) setCurrent(null); } }}>×</button>
            </div>
          ))}
        </aside>
        <div className="grow script-editor"><NamaScriptPanel /></div>
      </div>
    </div>
  );
}
