import { useEffect, useRef, useState } from 'react';
import { usePrefs } from '../state/prefs';
import { useWatchlist } from '../state/watchlist';
import { Markdown } from '../pages/Markdown';
import { SymbolLogo } from '../ui/SymbolLogo';
import { Icon } from '../ui/Icon';
import { faNum } from '../i18n/time';
import { useL } from '../pages/kit';
import './panels-x.css';

// Notes per symbol (TradingView's Notes widget): written in a light Markdown (**bold**, *italic*,
// lists, headings, links), shown rendered, saved as you type. They are the same notes the watchlist
// shows on a row, so they travel with the reader's account. Every symbol with a note is listed under.
export function NotesPanel() {
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const fa = lang === 'fa';
  const symbol = usePrefs((s) => s.symbol);
  const setSymbol = usePrefs((s) => s.setSymbol);
  const notes = useWatchlist((s) => s.notes);
  const note = useWatchlist((s) => s.note);
  const [text, setText] = useState(notes[symbol] ?? '');
  const [edit, setEdit] = useState(!notes[symbol]);
  const area = useRef<HTMLTextAreaElement>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const dirty = useRef(false);
  const latest = useRef(text);
  latest.current = text;

  useEffect(() => { setText(useWatchlist.getState().notes[symbol] ?? ''); setEdit(!useWatchlist.getState().notes[symbol]); dirty.current = false; }, [symbol]);
  useEffect(() => { if (!dirty.current) setText(notes[symbol] ?? ''); }, [notes, symbol]);
  // Saved half a second after the last key, and when the symbol changes.
  const change = (v: string) => {
    setText(v); dirty.current = true;
    if (timer.current) clearTimeout(timer.current);
    const s = symbol;
    timer.current = setTimeout(() => { note(s, v); dirty.current = false; }, 500);
  };
  // Leaving the symbol (or the panel) saves what was typed since the last save.
  useEffect(() => () => { if (timer.current) clearTimeout(timer.current); if (dirty.current) { note(symbol, latest.current); dirty.current = false; } }, [symbol, note]);
  const wrap = (a: string, b = a) => {
    const el = area.current;
    if (!el) return;
    const s = el.selectionStart, e = el.selectionEnd;
    change(text.slice(0, s) + a + text.slice(s, e) + b + text.slice(e));
    requestAnimationFrame(() => { el.focus(); el.setSelectionRange(s + a.length, e + a.length); });
  };
  const others = Object.entries(notes).filter(([s]) => s !== symbol).sort(([a], [b]) => a.localeCompare(b));

  return (
    <section className="panel-generic notes">
      <header className="panel-head">
        <span className="fa">{L('یادداشت‌ها', 'Notes')}</span>
        <span className="ws-spacer" />
        <SymbolLogo symbol={symbol} size={16} /><span className="sym notes-sym">{symbol}</span>
      </header>
      <div className="notes-tools">
        <span className="seg sm">
          <button className={edit ? 'on' : ''} onClick={() => setEdit(true)}><span className="fa">{L('نوشتن', 'Write')}</span></button>
          <button className={!edit ? 'on' : ''} onClick={() => { if (dirty.current) { note(symbol, text); dirty.current = false; } setEdit(false); }}><span className="fa">{L('پیش‌نمایش', 'Preview')}</span></button>
        </span>
        <span className="ws-spacer" />
        {edit ? (
          <>
            <button className="lt" title={L('پررنگ', 'Bold')} onClick={() => wrap('**')}><b>B</b></button>
            <button className="lt" title={L('کج', 'Italic')} onClick={() => wrap('*')}><i>I</i></button>
            <button className="lt" title={L('فهرست', 'List')} onClick={() => wrap('\n- ', '')}><Icon name="ph-list-dashes" size="sm" /></button>
            <button className="lt" title={L('عنوان', 'Heading')} onClick={() => wrap('\n## ', '')}><b>H</b></button>
          </>
        ) : null}
        <button className="lt" title={L('پاک کردن یادداشت', 'Delete the note')} disabled={!text} onClick={() => { if (window.confirm(L(`یادداشت ${symbol} پاک شود؟`, `Delete the note for ${symbol}?`))) { change(''); note(symbol, ''); } }}><Icon name="ph-trash" size="sm" /></button>
      </div>
      <div className="panel-body notes-body">
        {edit ? (
          <textarea ref={area} className="notes-area" value={text} onChange={(e) => change(e.target.value)} dir="auto" autoFocus
            placeholder={L(`یادداشت شما درباره‌ی ${symbol}… (**پررنگ**، *کج*، - فهرست)`, `Your note on ${symbol}… (**bold**, *italic*, - list)`)} />
        ) : text ? <div className="notes-view md" dir="auto"><Markdown source={text} /></div> : <div className="empty fa">{L('برای این نماد یادداشتی نیست.', 'No note for this symbol.')}</div>}
        {others.length ? (
          <div className="notes-all">
            <div className="menu-title fa">{L(`یادداشت‌های دیگر (${faNum(others.length)})`, `Other notes (${others.length})`)}</div>
            {others.map(([s, n]) => (
              <button key={s} className="notes-row" onClick={() => setSymbol(s)}>
                <SymbolLogo symbol={s} size={16} /><b className="sym">{s}</b>
                <span className={`muted${fa ? ' fa' : ''}`} dir="auto">{n.replace(/[#*`>-]/g, '').split('\n').find((l) => l.trim()) ?? ''}</span>
              </button>
            ))}
          </div>
        ) : null}
      </div>
    </section>
  );
}
