import { forwardRef, useCallback, useEffect, useImperativeHandle, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { COMPLETIONS, KEYWORDS, REFERENCE } from '../engine/scriptlib.js';
import { useL } from '../pages/kit';

// NamaScript's code editor, as Pine Editor works: the code over a highlighted copy of itself, line
// numbers, the current line and the failing line marked (with a wavy underline), autocomplete
// (as you type and on Ctrl+Space) with each function's description, the signature of the call under
// the caret, Ctrl+/ to comment lines, Enter keeping the indent, brackets and quotes closing
// themselves (typed over and deleted in pairs), Tab and Shift+Tab, find and replace (Ctrl+F,
// Ctrl+H), Ln/Col in the status line. Ported from the old Pro-Chart CodeEditor; drawn in the
// product's one typeface, so the caret position is measured, not counted.
const KW = new Set(KEYWORDS);
const TOKEN = /(\/\/[^\n]*)|("(?:[^"\\\n]|\\.)*"?|'(?:[^'\\\n]|\\.)*'?)|(\b\d+\.?\d*(?:e[+-]?\d+)?\b)|([A-Za-z_][A-Za-z0-9_.]*)/g;
const PAIRS: Record<string, string> = { '(': ')', '[': ']', '{': '}', '"': '"', "'": "'" };
const CLOSERS = new Set([')', ']', '}', '"', "'"]);
const LH = 20; const PAD = 8;

/** The function name → its signature and description, from the reference. */
export const DOCS: Record<string, { sig: string; desc: string }> = (() => {
  const map: Record<string, { sig: string; desc: string }> = {};
  for (const g of REFERENCE) for (const [sig, desc] of g.items) {
    const re = /([A-Za-z_][A-Za-z0-9_.]*)\s*\(/g; let m: RegExpExecArray | null; let found = false;
    while ((m = re.exec(sig))) { if (!map[m[1]!]) map[m[1]!] = { sig, desc }; found = true; }
    if (!found) for (const n of sig.split(/\s+/)) { const k = n.replace(/[^A-Za-z0-9_.]/g, ''); if (k && !map[k]) map[k] = { sig, desc }; }
  }
  return map;
})();

const esc = (s: string) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
/** The code as highlighted HTML (comments, strings, numbers, keywords, calls). */
export function highlight(code: string): string {
  let out = ''; let last = 0; let m: RegExpExecArray | null;
  TOKEN.lastIndex = 0;
  while ((m = TOKEN.exec(code))) {
    out += esc(code.slice(last, m.index));
    const [full, comment, str, num, ident] = m;
    if (comment) out += `<span class="tk-cm">${esc(comment)}</span>`;
    else if (str) out += `<span class="tk-str">${esc(str)}</span>`;
    else if (num) out += `<span class="tk-num">${esc(num)}</span>`;
    else if (ident) {
      const root = ident.split('.')[0]!;
      const after = code[m.index + full.length];
      out += `<span class="${KW.has(root) ? 'tk-kw' : after === '(' ? 'tk-fn' : 'tk-id'}">${esc(ident)}</span>`;
    }
    last = m.index + full.length;
  }
  return `${out}${esc(code.slice(last))}\n`;
}

/** The call whose parentheses hold the caret, on the caret's line, with its reference. */
export function activeCall(upto: string): { name: string; sig: string; desc: string } | null {
  let depth = 0;
  for (let i = upto.length - 1; i >= 0; i--) {
    const ch = upto[i];
    if (ch === '\n') return null;
    if (ch === ')') depth++;
    else if (ch === '(') {
      if (depth === 0) { const nm = (upto.slice(0, i).match(/[A-Za-z_][A-Za-z0-9_.]*$/) ?? [''])[0]; return nm && DOCS[nm] ? { name: nm, ...DOCS[nm]! } : null; }
      depth--;
    }
  }
  return null;
}

/** Comments or uncomments the lines a selection touches; returns the new text and selection. */
export function toggleComment(v: string, s: number, e: number): { text: string; start: number; end: number } {
  const ls = v.lastIndexOf('\n', s - 1) + 1;
  let le = v.indexOf('\n', e > s && v[e - 1] === '\n' ? e - 1 : e); if (le === -1) le = v.length;
  const rows = v.slice(ls, le).split('\n');
  const all = rows.every((r) => r.trim() === '' || /^\s*\/\//.test(r));
  const next = rows.map((r) => { if (r.trim() === '') return r; if (all) return r.replace(/^(\s*)\/\/ ?/, '$1'); const lead = (/^\s*/.exec(r) ?? [''])[0]; return `${lead}// ${r.slice(lead.length)}`; }).join('\n');
  return { text: v.slice(0, ls) + next + v.slice(le), start: ls, end: ls + next.length };
}

export interface CodeEditorHandle { focus(): void; goToLine(line: number): void; insert(text: string): void }

interface Props {
  value: string;
  onChange(v: string): void;
  onRun?(): void;
  onSave?(): void;
  errorLine?: number;
  status?: React.ReactNode;
}

export const CodeEditor = forwardRef<CodeEditorHandle, Props>(function CodeEditor({ value, onChange, onRun, onSave, errorLine = 0, status }, ref) {
  const L = useL();
  const ta = useRef<HTMLTextAreaElement>(null);
  const pre = useRef<HTMLPreElement>(null);
  const gutter = useRef<HTMLDivElement>(null);
  const measure = useRef<CanvasRenderingContext2D | null>(null);
  const [sel, setSel] = useState(0);
  const [scroll, setScroll] = useState({ top: 0, left: 0 });
  const [ac, setAc] = useState<{ items: string[]; idx: number } | null>(null);
  const [find, setFind] = useState<{ open: boolean; replace: boolean; q: string; r: string; cs: boolean }>({ open: false, replace: false, q: '', r: '', cs: false });
  const findInput = useRef<HTMLInputElement>(null);
  const lines = useMemo(() => value.split('\n').length, [value]);
  const caret = useMemo(() => { const up = value.slice(0, Math.min(sel, value.length)).split('\n'); return { line: up.length - 1, col: (up[up.length - 1] ?? '').length, prefix: up[up.length - 1] ?? '' }; }, [value, sel]);
  const hint = useMemo(() => (ac ? null : activeCall(value.slice(0, Math.min(sel, value.length)))), [ac, value, sel]);

  // The caret's x, measured in the editor's own font (proportional, so columns cannot be counted).
  const xOf = useCallback((text: string) => {
    const el = ta.current; if (!el) return 0;
    if (!measure.current) measure.current = document.createElement('canvas').getContext('2d');
    const ctx = measure.current; if (!ctx) return text.length * 7.5;
    const cs = getComputedStyle(el); ctx.font = `${cs.fontWeight} ${cs.fontSize} ${cs.fontFamily}`;
    return ctx.measureText(text.replace(/\t/g, '  ')).width;
  }, []);

  // The caret goes where an edit leaves it as soon as the new text is in the textarea (before the next
  // key), so fast typing never lands in the wrong place.
  const pending = useRef<{ a: number; b: number } | null>(null);
  const set = (text: string, caretAt: number, end = caretAt) => {
    pending.current = { a: caretAt, b: end };
    if (text === value) { const el = ta.current; if (el) { el.selectionStart = caretAt; el.selectionEnd = end; } pending.current = null; setSel(caretAt); return; }
    onChange(text);
  };
  useLayoutEffect(() => {
    const p = pending.current; const el = ta.current;
    if (!p || !el) return;
    pending.current = null;
    el.selectionStart = p.a; el.selectionEnd = p.b; setSel(p.a);
  }, [value]);
  const goToLine = (line: number) => {
    const el = ta.current; if (!el) return;
    const rows = value.split('\n'); let pos = 0;
    for (let i = 0; i < Math.min(line - 1, rows.length); i++) pos += rows[i]!.length + 1;
    el.focus(); el.selectionStart = el.selectionEnd = pos; setSel(pos);
    el.scrollTop = Math.max(0, (line - 4) * LH);
  };
  useImperativeHandle(ref, () => ({
    focus: () => ta.current?.focus(),
    goToLine,
    insert: (text: string) => { const el = ta.current; if (!el) return; const a = el.selectionStart, b = el.selectionEnd; set(value.slice(0, a) + text + value.slice(b), a + text.length); el.focus(); },
  }), [value]);

  const wordAt = (text: string, pos: number) => (text.slice(0, pos).match(/[A-Za-z_][A-Za-z0-9_.]*$/) ?? [''])[0];
  const openAc = (text: string, pos: number, forced: boolean) => {
    const word = wordAt(text, pos);
    if (!word && !forced) { setAc(null); return; }
    const items = COMPLETIONS.filter((c) => c.toLowerCase().startsWith(word.toLowerCase()) && c !== word).slice(0, 10);
    setAc(items.length ? { items, idx: 0 } : null);
  };
  const accept = (item: string) => {
    const el = ta.current; if (!el) return;
    const pos = el.selectionStart; const word = wordAt(value, pos); const start = pos - word.length;
    const call = DOCS[item] && /\(/.test(DOCS[item]!.sig) ? '()' : '';
    set(value.slice(0, start) + item + call + value.slice(pos), start + item.length + (call ? 1 : 0));
    setAc(null);
    el.focus();
  };

  // Find and replace.
  const matches = useMemo(() => {
    if (!find.open || !find.q) return [] as number[];
    const hay = find.cs ? value : value.toLowerCase(); const needle = find.cs ? find.q : find.q.toLowerCase();
    const out: number[] = []; let i = hay.indexOf(needle);
    while (i >= 0 && out.length < 5000) { out.push(i); i = hay.indexOf(needle, i + Math.max(1, needle.length)); }
    return out;
  }, [find, value]);
  const current = matches.indexOf(sel);
  const goMatch = (dir: 1 | -1) => {
    const el = ta.current;
    if (!matches.length || !el) return;
    let i: number;
    if (dir > 0) { i = matches.findIndex((m) => m >= el.selectionEnd); if (i < 0) i = 0; }
    else { i = -1; for (let j = matches.length - 1; j >= 0; j--) if (matches[j]! < el.selectionStart) { i = j; break; } if (i < 0) i = matches.length - 1; }
    const at = matches[i]!;
    el.focus(); el.selectionStart = at; el.selectionEnd = at + find.q.length; setSel(at);
    const line = value.slice(0, at).split('\n').length - 1;
    if (line * LH < el.scrollTop || line * LH > el.scrollTop + el.clientHeight - LH * 2) el.scrollTop = Math.max(0, (line - 3) * LH);
    findInput.current?.focus();
  };
  const replaceOne = () => {
    const el = ta.current; if (!el || !find.q) return;
    const a = el.selectionStart, b = el.selectionEnd;
    const selected = value.slice(a, b);
    if ((find.cs ? selected : selected.toLowerCase()) === (find.cs ? find.q : find.q.toLowerCase())) { set(value.slice(0, a) + find.r + value.slice(b), a + find.r.length); }
    setTimeout(() => goMatch(1), 0);
  };
  const replaceAll = () => {
    if (!find.q) return;
    const re = new RegExp(find.q.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), find.cs ? 'g' : 'gi');
    set(value.replace(re, () => find.r), 0);
  };

  const onKey = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    const el = e.currentTarget;
    const mod = e.ctrlKey || e.metaKey;
    if (ac) {
      if (e.key === 'ArrowDown') { e.preventDefault(); setAc({ ...ac, idx: (ac.idx + 1) % ac.items.length }); return; }
      if (e.key === 'ArrowUp') { e.preventDefault(); setAc({ ...ac, idx: (ac.idx - 1 + ac.items.length) % ac.items.length }); return; }
      if (e.key === 'Enter' || e.key === 'Tab') { e.preventDefault(); accept(ac.items[ac.idx]!); return; }
      if (e.key === 'Escape') { e.preventDefault(); setAc(null); return; }
    }
    if (mod && e.key === 'Enter') { e.preventDefault(); onRun?.(); return; }
    if (mod && (e.key === 's' || e.key === 'S')) { e.preventDefault(); onSave?.(); return; }
    if (mod && (e.key === ' ' || e.code === 'Space')) { e.preventDefault(); openAc(value, el.selectionStart, true); return; }
    if (mod && e.key === '/') { e.preventDefault(); const r = toggleComment(value, el.selectionStart, el.selectionEnd); set(r.text, r.start, r.end); return; }
    if (mod && (e.key === 'f' || e.key === 'h')) { e.preventDefault(); const picked = value.slice(el.selectionStart, el.selectionEnd); setFind((f) => ({ ...f, open: true, replace: e.key === 'h', q: picked && !picked.includes('\n') ? picked : f.q })); setTimeout(() => findInput.current?.select(), 0); return; }
    if (e.key === 'Escape' && find.open) { setFind((f) => ({ ...f, open: false })); return; }
    if (e.key === 'Tab') {
      e.preventDefault();
      const s = el.selectionStart, en = el.selectionEnd;
      if (s !== en && value.slice(s, en).includes('\n') || e.shiftKey) {
        const ls = value.lastIndexOf('\n', s - 1) + 1; const le = value.indexOf('\n', en) === -1 ? value.length : value.indexOf('\n', en);
        const rows = value.slice(ls, le).split('\n').map((r) => (e.shiftKey ? r.replace(/^ {1,2}/, '') : `  ${r}`)).join('\n');
        set(value.slice(0, ls) + rows + value.slice(le), ls, ls + rows.length);
      } else set(`${value.slice(0, s)}  ${value.slice(en)}`, s + 2);
      return;
    }
    if (e.key === 'Enter' && !mod) {
      e.preventDefault();
      const s = el.selectionStart, en = el.selectionEnd; const before = value.slice(0, s);
      const line = before.slice(before.lastIndexOf('\n') + 1);
      let indent = (/^[\t ]*/.exec(line) ?? [''])[0];
      if (/[({[]\s*$/.test(line) || /\b(if|for|while|else)\b.*$/.test(line) && /\)\s*$/.test(line)) indent += '  ';
      set(`${value.slice(0, s)}\n${indent}${value.slice(en)}`, s + 1 + indent.length);
      return;
    }
    const plain = !mod && !e.altKey;
    if (plain && CLOSERS.has(e.key) && el.selectionStart === el.selectionEnd && value[el.selectionStart] === e.key) { e.preventDefault(); set(value, el.selectionStart + 1); return; }
    if (plain && PAIRS[e.key] && el.selectionStart === el.selectionEnd) {
      const s = el.selectionStart; const nextCh = value[s] ?? '';
      if ((e.key === '"' || e.key === "'") && /\w/.test(value[s - 1] ?? '')) return;
      if (/\w/.test(nextCh)) return;
      e.preventDefault(); set(value.slice(0, s) + e.key + PAIRS[e.key] + value.slice(s), s + 1); return;
    }
    if (e.key === 'Backspace' && el.selectionStart === el.selectionEnd) {
      const s = el.selectionStart; const a = value[s - 1] ?? '', b = value[s] ?? '';
      if (PAIRS[a] && PAIRS[a] === b) { e.preventDefault(); set(value.slice(0, s - 1) + value.slice(s + 1), s - 1); }
    }
  };

  useEffect(() => { if (pre.current) { pre.current.scrollTop = scroll.top; pre.current.scrollLeft = scroll.left; } if (gutter.current) gutter.current.scrollTop = scroll.top; }, [scroll]);

  const caretX = PAD + xOf(caret.prefix) - scroll.left;
  const caretY = PAD + (caret.line + 1) * LH - scroll.top;
  // Popups open under the caret, or over it when the code area has no room below.
  const areaH = ta.current?.clientHeight ?? 300;
  const below = caretY + 160 < areaH || caretY < areaH / 2;
  const popPos = (left: number) => (below ? { top: Math.max(PAD, caretY + 2), left } : { bottom: Math.max(2, areaH - caretY + LH + 2), left });
  const word = ac ? wordAt(value, sel) : '';
  return (
    <div className="ce" dir="ltr">
      {find.open ? (
        <div className="ce-find" role="search">
          <input ref={findInput} value={find.q} placeholder={L('جستجو', 'Find')} aria-label={L('جستجو', 'Find')} onChange={(e) => setFind({ ...find, q: e.target.value })}
            onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); goMatch(e.shiftKey ? -1 : 1); } if (e.key === 'Escape') { setFind({ ...find, open: false }); ta.current?.focus(); } }} />
          <span className="ce-count num">{matches.length ? `${current >= 0 ? current + 1 : '?'}/${matches.length}` : find.q ? L('بدون نتیجه', 'No results') : ''}</span>
          <button className={`ce-btn${find.cs ? ' on' : ''}`} title={L('حساس به حروف بزرگ و کوچک', 'Match case')} onClick={() => setFind({ ...find, cs: !find.cs })}>Aa</button>
          <button className="ce-btn" title={L('قبلی', 'Previous')} onClick={() => goMatch(-1)}>↑</button>
          <button className="ce-btn" title={L('بعدی', 'Next')} onClick={() => goMatch(1)}>↓</button>
          <button className={`ce-btn${find.replace ? ' on' : ''}`} title={L('جایگزینی', 'Replace')} onClick={() => setFind({ ...find, replace: !find.replace })}>⇄</button>
          {find.replace ? (
            <>
              <input value={find.r} placeholder={L('جایگزین', 'Replace')} aria-label={L('جایگزین', 'Replace')} onChange={(e) => setFind({ ...find, r: e.target.value })} onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); replaceOne(); } }} />
              <button className="ce-btn wide" onClick={replaceOne}>{L('جایگزینی', 'Replace')}</button>
              <button className="ce-btn wide" onClick={replaceAll}>{L('همه', 'All')}</button>
            </>
          ) : null}
          <button className="ce-btn" title={L('بستن', 'Close')} onClick={() => { setFind({ ...find, open: false }); ta.current?.focus(); }}>×</button>
        </div>
      ) : null}
      <div className="ce-main">
        <div className="ce-gutter num" ref={gutter} aria-hidden>
          {Array.from({ length: lines }, (_, i) => <div key={i} className={i + 1 === errorLine ? 'err' : i === caret.line ? 'cur' : ''}>{i + 1}</div>)}
        </div>
        <div className="ce-area">
          <div className="ce-lines" aria-hidden>
            <div className="ce-curline" style={{ top: PAD + caret.line * LH - scroll.top }} />
            {errorLine > 0 ? <div className="ce-errline" style={{ top: PAD + (errorLine - 1) * LH - scroll.top }} /> : null}
          </div>
          <pre ref={pre} className="ce-pre" aria-hidden dangerouslySetInnerHTML={{ __html: highlight(value) }} />
          <textarea ref={ta} className="ce-ta" value={value} spellCheck={false} wrap="off" autoCapitalize="off" autoCorrect="off" aria-label="NamaScript"
            onChange={(e) => { onChange(e.target.value); setSel(e.target.selectionStart); const ch = e.target.value[e.target.selectionStart - 1] ?? ''; if (/[A-Za-z_.]/.test(ch)) openAc(e.target.value, e.target.selectionStart, false); else setAc(null); }}
            onKeyDown={onKey}
            onKeyUp={(e) => setSel(e.currentTarget.selectionStart)}
            onClick={(e) => { setAc(null); setSel(e.currentTarget.selectionStart); }}
            onSelect={(e) => setSel(e.currentTarget.selectionStart)}
            onBlur={() => setTimeout(() => setAc(null), 150)}
            onScroll={(e) => setScroll({ top: e.currentTarget.scrollTop, left: e.currentTarget.scrollLeft })} />
          {hint ? (
            <div className="ce-hint" style={popPos(Math.max(4, Math.min(caretX, 360)))}>
              <code>{hint.sig}</code><span className="fa">{hint.desc}</span>
            </div>
          ) : null}
          {ac ? (
            <div className="ce-ac" role="listbox" style={popPos(Math.max(4, Math.min(caretX - xOf(word), 360)))}>
              {ac.items.map((it, i) => (
                <div key={it} role="option" aria-selected={i === ac.idx} className={`ce-ac-row${i === ac.idx ? ' on' : ''}`} onMouseDown={(e) => { e.preventDefault(); accept(it); }}>
                  <code>{it}</code>{DOCS[it] ? <span className="fa">{DOCS[it]!.desc}</span> : null}
                </div>
              ))}
            </div>
          ) : null}
        </div>
      </div>
      <div className="ce-status num">
        <span>{L(`خط ${caret.line + 1}، ستون ${caret.col + 1}`, `Line ${caret.line + 1}, Col ${caret.col + 1}`)}</span>
        {errorLine > 0 ? <button className="ce-errlink" onClick={() => goToLine(errorLine)}>{L(`خطا در خط ${errorLine}`, `Error on line ${errorLine}`)}</button> : null}
        <span className="ws-spacer" />
        {status}
        <span>NamaScript v1</span>
      </div>
    </div>
  );
});
