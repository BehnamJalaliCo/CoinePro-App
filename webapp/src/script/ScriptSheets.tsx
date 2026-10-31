import { useState } from 'react';
import { Sheet } from './Sheet';
import { readClipboard, writeClipboard } from './clipboard';
import { usePrefs } from '../state/prefs';
import { faNum } from '../i18n/time';
import { useL } from '../pages/kit';
import { readPaste, type Paste } from './paste';
import { templateSource, type ScriptTemplate } from './templates';
import { CHATGPT, CLAUDE, PROMPT_VERSION, promptParts } from './promptKit';
import './script2.css';

// The two sheets around «bring your own script» (the phone's ScriptPasteSheet.kt): the paste sheet —
// what was read, what was repaired and how to put it back, or for a sentence the template picker —
// and the prompt kit that asks an assistant for a script in the first place.

/** A count in the reader's own numerals: Persian in Persian prose (line numbers, a version). */
export function useCount(): (n: number | string) => string {
  const fa = usePrefs((s) => s.lang) === 'fa';
  return (n) => (fa ? faNum(n) : String(n));
}

export { readClipboard, writeClipboard };
export { Sheet };

/** Where the paste lands when the clipboard cannot be read: a box, and «بخوانش». */
export function PasteBox({ onRead, onClose }: { onRead(p: Paste): void; onClose(): void }) {
  const L = useL();
  const [text, setText] = useState('');
  return (
    <Sheet title={L('چسباندن اسکریپت', 'Paste a script')} onClose={onClose} className="sx-paste-box">
      <p className="muted fa">{L('هر چیزی را اینجا بچسبانید — نمااسکریپت، اسکریپت تریدینگ‌ویو، یا فقط یک جمله که چه می‌خواهید.', 'Paste anything here — NamaScript, a TradingView script, or just a sentence saying what you want.')}</p>
      <textarea className="ns-code paste" rows={10} dir="auto" spellCheck={false} value={text} onChange={(e) => setText(e.target.value)} autoFocus />
      <button className="btn btn-primary sm" disabled={!text.trim()} onClick={() => onRead(readPaste(text))}>{L('بخوانش', 'Read it')}</button>
    </Sheet>
  );
}

const DIALECT: Record<Paste['dialect'], [string, string]> = {
  nama: ['به عنوان نمااسکریپت خوانده شد', 'Read as NamaScript'],
  pine: ['به عنوان اسکریپت تریدینگ‌ویو خوانده و ترجمه شد', 'Read as a TradingView script and translated'],
  prose: ['به عنوان توضیح خوانده شد', 'Read as a description'],
};

/** «چسباندن اسکریپت»: the repairs already applied, listed, and undone in one tap; or the picker. */
export function PasteSheet({ paste, onKeep, onUndo, onUseTemplate }: { paste: Paste; onKeep(): void; onUndo(): void; onUseTemplate(t: ScriptTemplate, source: string): void }) {
  const L = useL();
  const n = useCount();
  const fa = usePrefs((s) => s.lang) === 'fa';
  const [d, e] = DIALECT[paste.dialect];
  return (
    <Sheet title={L('چسباندن اسکریپت', 'Paste a script')} subtitle={L(d, e)} onClose={onKeep} className="sx-paste">
      {paste.dialect === 'prose' ? (
        <>
          <h3 className="fa">{L('کدام‌یک را می‌خواستید؟', 'Which of these did you mean?')}</h3>
          {paste.templates.map((t) => (
            <div key={t.id} className="card sx-template" data-template={t.id}>
              <b className="fa">{L(t.title, t.titleEn)}</b>
              <p className="muted fa">{L(t.summary, t.summaryEn)}</p>
              <button className="btn btn-primary sm" onClick={() => onUseTemplate(t, templateSource(t, paste.numbers))}>{L('همین را بسازید', 'Use this')}</button>
            </div>
          ))}
        </>
      ) : !paste.fixes.length && !paste.unsupported.length ? (
        <p className="muted fa sx-clean">{L('چیزی برای اصلاح نبود.', 'Nothing needed fixing.')}</p>
      ) : (
        <>
          {paste.fixes.length ? <h3 className="fa">{paste.fixes.length === 1 ? L('یک مورد اصلاح شد', 'One thing was fixed') : L(`${n(paste.fixes.length)} مورد اصلاح شد`, `${paste.fixes.length} things were fixed`)}</h3> : null}
          {paste.fixes.map((f) => (
            <div key={f.id} className="card sx-fix" data-fix={f.id}>
              <span className="fa" dir="auto">{fa ? f.what : f.whatEn}</span>
              {f.lines.length ? <small className="muted fa">{f.lines.length === 1 ? L(`خط ${n(f.lines[0]!)}`, `Line ${f.lines[0]}`) : L(`خط‌های ${f.lines.map(n).join('، ')}`, `Lines ${f.lines.join(', ')}`)}</small> : null}
            </div>
          ))}
          {paste.unsupported.length ? (
            <>
              <p className="muted fa">{L('این اسکریپت چیزهایی دارد که در نمااسکریپت نیست. آن‌ها به صورت توضیح باقی ماندند:', 'This script uses things NamaScript does not have. They were left as comments:')}</p>
              {paste.unsupported.map((u) => <code key={u.line} className="sx-unsupported" dir="ltr" title={fa ? u.what : u.whatEn}>{u.source}</code>)}
            </>
          ) : null}
        </>
      )}
      {paste.dialect !== 'prose' ? (
        <div className="row sx-actions">
          <button className="btn btn-primary sm" onClick={onKeep}>{L('همین خوب است', 'Keep them')}</button>
          {paste.fixes.length ? <button className="btn btn-quiet sm sx-undo" onClick={onUndo}>{L('برگرداندن اصلاح‌ها', 'Undo the fixes')}</button> : null}
        </div>
      ) : null}
    </Sheet>
  );
}

/** «گرفتن اسکریپت از هوش مصنوعی»: the prompt in full (the reader may read what they send), and three taps. */
export function PromptSheet({ prompt, onClose, onPasteResult }: { prompt: string; onClose(): void; onPasteResult(): void }) {
  const L = useL();
  const n = useCount();
  const [note, setNote] = useState<string | null>(null);
  const [ask, spec] = promptParts(prompt);
  const open = (url: string) => { if (!/^https:\/\//.test(url)) return; const w = window.open(url, '_blank', 'noopener,noreferrer'); if (!w) setNote(L('هیچ برنامه‌ای روی این دستگاه نمی‌تواند آن پیوند را باز کند.', 'No app on this device can open that link.')); };
  return (
    <Sheet title={L('گرفتن اسکریپت از هوش مصنوعی', 'Get a script from an AI')} subtitle={L(`نسخه‌ی متن ${n(PROMPT_VERSION)}`, `Prompt version ${PROMPT_VERSION}`)} onClose={onClose} className="sx-prompt">
      <p className="muted fa">{L('این را کپی کنید و به هر دستیاری بدهید. نمااسکریپت را یادش می‌دهد، چارت شما را نام می‌برد و فقط کد می‌خواهد — پس چیزی که برمی‌گردد معمولاً همان بار اول اجرا می‌شود.', 'Copy this and give it to any assistant. It teaches NamaScript, names your chart, and asks for code only — so what comes back usually runs on the first try.')}</p>
      <div className="sx-prompt-text">
        {/* Each line takes its own direction: Persian sentences right to left, code left to right. */}
        <div className="sx-prompt-ask">{ask.split('\n').map((l, i) => <p key={i} dir="auto">{l || ' '}</p>)}</div>
        {spec ? <pre className="sx-code" dir="ltr">{spec}</pre> : null}
      </div>
      <button className="btn btn-primary sm sx-copy" onClick={() => void writeClipboard(prompt).then((ok) => setNote(ok ? L('کپی شد. در دستیار بچسبانید.', 'Copied. Paste it into the assistant.') : null))}>{L('کپی متن', 'Copy the prompt')}</button>
      <div className="row sx-actions">
        <button className="btn btn-quiet sm" onClick={() => open(CHATGPT)}>{L('باز کردن ChatGPT', 'Open ChatGPT')}</button>
        <button className="btn btn-quiet sm" onClick={() => open(CLAUDE)}>{L('باز کردن Claude', 'Open Claude')}</button>
      </div>
      <button className="btn btn-quiet sm sx-paste-result" onClick={onPasteResult}>{L('چسباندن جواب', 'Paste the result')}</button>
      {note ? <p className="muted fa sx-note" role="status">{note}</p> : null}
    </Sheet>
  );
}
