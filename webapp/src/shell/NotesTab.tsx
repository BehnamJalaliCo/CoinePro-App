import { useEffect, useRef, useState } from 'react';
import { usePrefs } from '../state/prefs';
import { useL } from '../pages/kit';
import { faNum } from '../i18n/time';

// Notes under the chart (the old Pro-Chart dock's): one notebook for the whole app, kept in this
// browser as it is typed (a moment after the last key), with its character count and when it was
// last saved.
const KEY = 'pc.notes.v1';
const read = (): { text: string; at: number } => { try { const v = JSON.parse(localStorage.getItem(KEY) ?? 'null') as { text?: string; at?: number } | null; return { text: v?.text ?? '', at: v?.at ?? 0 }; } catch { return { text: '', at: 0 }; } };

export function NotesTab() {
  const L = useL();
  const fa = usePrefs((s) => s.lang) === 'fa';
  const [note, setNote] = useState(read);
  const [saved, setSaved] = useState(true);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  useEffect(() => () => { if (timer.current) clearTimeout(timer.current); }, []);
  const change = (text: string) => {
    setNote((n) => ({ ...n, text }));
    setSaved(false);
    if (timer.current) clearTimeout(timer.current);
    timer.current = setTimeout(() => {
      const at = Date.now();
      try { localStorage.setItem(KEY, JSON.stringify({ text, at })); setNote({ text, at }); setSaved(true); } catch { setSaved(false); }
    }, 500);
  };
  const time = note.at ? new Date(note.at).toTimeString().slice(0, 5) : '';
  return (
    <div className="notes">
      <textarea className="notes-area" dir="auto" value={note.text} onChange={(e) => change(e.target.value)} placeholder={L('یادداشت‌های معاملاتی‌ات را اینجا بنویس؛ خودکار ذخیره می‌شود.', 'Write your trading notes here; they save as you type.')} aria-label={L('یادداشت‌ها', 'Notes')} />
      <div className="notes-foot">
        <span className={fa ? 'fa' : ''}>{fa ? `${faNum(note.text.length)} نویسه` : `${note.text.length} characters`}</span>
        <span className="ws-spacer" />
        <span className={fa ? 'fa' : ''}>{saved ? (note.at ? L(`ذخیره شد · ${time}`, `Saved · ${time}`) : '') : L('در حال ذخیره…', 'Saving…')}</span>
      </div>
    </div>
  );
}
