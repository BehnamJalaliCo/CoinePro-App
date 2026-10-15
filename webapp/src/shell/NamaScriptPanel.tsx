import { useEffect, useMemo, useRef, useState } from 'react';
import { useScript, scriptTitle } from '../state/script';
import { usePrefs } from '../state/prefs';
import { usePage } from '../state/page';
import { EXAMPLES, REFERENCE } from '../engine/scriptlib.js';
import { cleanFa } from '../chart/indicatorCatalog';
import { CodeEditor, type CodeEditorHandle } from './CodeEditor';
import { Dropdown } from '../ui/Menu';
import { useL } from '../pages/kit';
import { dateTime } from '../pages/kit';
import { faNum } from '../i18n/time';
import { Icon } from '../ui/Icon';
import './studies.css';

// NamaScript's editor, where TradingView keeps Pine Editor: the script's name and its menu (new,
// open, save, save as, rename, copy, version history, examples), tabs for several scripts, "Add
// to chart" / "Update on chart" (Ctrl+Enter), Save (Ctrl+S), the inputs, bar-by-bar mode, the
// reference beside the code, and the console under it: compile result, each plot with its colour,
// each alert condition with how often it fired, the strategy's summary, errors with their line.
interface LogLine { at: number; kind: 'ok' | 'err' | 'info'; text: string; line?: number }

export function NamaScriptPanel() {
  const L = useL();
  const fa = usePrefs((s) => s.lang) === 'fa';
  const s = useScript();
  const editor = useRef<CodeEditorHandle>(null);
  const [menu, setMenu] = useState<{ at: HTMLElement; kind: 'title' | 'versions' } | null>(null);
  const [renaming, setRenaming] = useState(false);
  const [showRef, setShowRef] = useState(false);
  const [refQuery, setRefQuery] = useState('');
  const [consoleOpen, setConsoleOpen] = useState(true);
  const [log, setLog] = useState<LogLine[]>([]);
  const tab = s.tabs.find((t) => t.id === s.active) ?? s.tabs[0]!;
  const saved = tab.savedId ? s.lib.find((x) => x.id === tab.savedId) : undefined;
  const dirty = !saved || saved.code !== tab.code;
  const onChart = s.on && s.runCode === tab.code;
  const result = s.result;
  const errorLine = s.on && result && !result.ok && s.runCode === tab.code ? result.errorLine ?? 0 : 0;

  // The console keeps a short history of runs, newest last.
  useEffect(() => {
    if (!result) return;
    const at = Date.now();
    const lines: LogLine[] = [];
    if (!result.ok) lines.push({ at, kind: 'err', text: `${result.errorLine ? L(`خط ${faNum(result.errorLine)}: `, `Line ${result.errorLine}: `) : ''}${result.error ?? L('خطای اجرا', 'Run error')}`, line: result.errorLine });
    else {
      lines.push({ at, kind: 'ok', text: `${L('کامپایل شد', 'Compiled')}${s.engine === 'bar' ? L(' · کندل‌به‌کندل', ' · bar by bar') : s.barMode ? L(' · سری‌محور (کندل‌به‌کندل این تابع را نداشت)', ' · series (bar by bar lacked a function)') : ''}` });
      const st = s.backtest;
      if (st) lines.push({ at, kind: 'info', text: L(`${faNum(st.trades.length)} معامله · سود خالص ${st.net.toFixed(2)} · برد ${st.win.toFixed(1)}%`, `${st.trades.length} trades · net ${st.net.toFixed(2)} · win ${st.win.toFixed(1)}%`) });
    }
    setLog((l) => [...l, ...lines].slice(-40));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [result]);

  const reference = useMemo(() => {
    const q = refQuery.trim().toLowerCase();
    return REFERENCE.map((g) => ({ g: cleanFa(g.g), items: g.items.filter(([sig, d]) => !q || sig.toLowerCase().includes(q) || d.includes(refQuery.trim())) })).filter((g) => g.items.length);
  }, [refQuery]);

  const save = () => { s.save(false); setLog((l) => [...l, { at: Date.now(), kind: 'info' as const, text: L('ذخیره شد.', 'Saved.') }].slice(-40)); };
  const name = tab.name || scriptTitle(tab.code) || 'Untitled';
  const isStrategy = /\bstrategy\s*\.\s*(entry|exit|close)\b|\bstrategy\s*\(/.test(tab.code);
  const plots = result?.ok ? result.plots ?? [] : [];
  const alerts = result?.ok ? (result.alerts ?? []).filter((a) => !a.dynamic) : [];

  return (
    <div className={`ns ns2${fa ? '' : ' sa-ltr'}`}>
      <div className="ns-bar" dir={fa ? 'rtl' : 'ltr'}>
        <span className="ns-kind" title={isStrategy ? L('استراتژی', 'Strategy') : L('اندیکاتور', 'Indicator')}><Icon name={isStrategy ? 'ph-flask' : 'ph-chart-line-up'} size="sm" /></span>
        {renaming ? (
          <input className="ns-rename" autoFocus defaultValue={name} onBlur={(e) => { s.rename(e.target.value); setRenaming(false); }} onKeyDown={(e) => { if (e.key === 'Enter') (e.target as HTMLInputElement).blur(); if (e.key === 'Escape') setRenaming(false); }} aria-label={L('نام اسکریپت', 'Script name')} />
        ) : (
          <button className="ns-title" onClick={(e) => setMenu({ at: e.currentTarget, kind: 'title' })} onDoubleClick={() => setRenaming(true)} title={L('منوی اسکریپت', 'Script menu')}>
            <span dir="auto">{name}</span>{dirty ? <i className="ns-dirty" title={L('ذخیره نشده', 'Unsaved')} /> : null}<Icon name="ph-caret-down" size="sm" />
          </button>
        )}
        <span className="ns-sep" />
        <button className={`ns-btn primary${onChart ? '' : ' strong'}`} onClick={() => s.apply()} title={`${onChart ? L('به‌روزرسانی روی چارت', 'Update on chart') : L('افزودن به چارت', 'Add to chart')} (Ctrl+Enter)`}>
          <Icon name={onChart ? 'ph-arrow-clockwise' : 'ph-play'} size="sm" /><span className="fa">{onChart ? L('به‌روزرسانی', 'Update on chart') : L('افزودن به چارت', 'Add to chart')}</span>
        </button>
        <button className="ns-btn" onClick={save} title={`${L('ذخیره', 'Save')} (Ctrl+S)`}><Icon name="ph-floppy-disk" size="sm" /><span className="fa">{L('ذخیره', 'Save')}</span></button>
        {s.on ? <button className="ns-btn" onClick={() => window.dispatchEvent(new CustomEvent('pc:study-settings', { detail: { uid: 'script' } }))} title={L('ورودی‌ها و ویژگی‌ها', 'Inputs and properties')}><Icon name="ph-gear" size="sm" /><span className="fa">{L('ورودی‌ها', 'Inputs')}</span></button> : null}
        {s.on ? <button className="ns-btn" onClick={() => s.remove()} title={L('حذف از چارت', 'Remove from chart')}><Icon name="ph-x" size="sm" /><span className="fa">{L('حذف از چارت', 'Remove')}</span></button> : null}
        <span className="ws-spacer" />
        {s.running ? <span className="ns-running fa">{L('در حال اجرا…', 'Running…')}</span> : null}
        <label className="ns-toggle" title={L('اجرای کندل‌به‌کندل، مثل Pine؛ اگر تابعی را نداشته باشد خودکار سری‌محور می‌شود', 'Run bar by bar, as Pine does; falls back to the series engine when a function is missing')}>
          <input type="checkbox" checked={s.barMode} onChange={(e) => s.setBarMode(e.target.checked)} /><span className="fa">{L('کندل‌به‌کندل', 'Bar by bar')}</span>
        </label>
        {saved?.versions?.length ? <button className="ib" title={L('تاریخچه‌ی نسخه‌ها', 'Version history')} onClick={(e) => setMenu({ at: e.currentTarget, kind: 'versions' })}><Icon name="ph-clock" size="sm" /></button> : null}
        <button className={`ib${showRef ? ' on' : ''}`} onClick={() => setShowRef(!showRef)} title={L('راهنمای توابع', 'Reference')}><Icon name="ph-info" size="sm" /></button>
      </div>
      <div className="ns-tabs" role="tablist" dir="ltr">
        {s.tabs.map((t) => (
          <div key={t.id} role="tab" aria-selected={t.id === s.active} className={`ns-tab${t.id === s.active ? ' on' : ''}`} onClick={() => s.switchTab(t.id)}>
            <span dir="auto">{t.name || 'Untitled'}</span>
            {s.tabs.length > 1 ? <button className="lt" aria-label={L('بستن زبانه', 'Close tab')} onClick={(e) => { e.stopPropagation(); s.closeTab(t.id); }}><Icon name="ph-x" size="sm" /></button> : null}
          </div>
        ))}
        <button className="lt ns-newtab" aria-label={L('اسکریپت تازه', 'New script')} title={L('اسکریپت تازه', 'New script')} onClick={() => s.newTab()}><Icon name="ph-plus" size="sm" /></button>
      </div>
      <div className="ns-main">
        <CodeEditor ref={editor} value={tab.code} onChange={s.setSource} onRun={() => s.apply()} onSave={save} errorLine={errorLine}
          status={<span className="fa">{s.on ? (onChart ? L('روی چارت', 'On chart') : L('تغییرهای اعمال‌نشده', 'Changes not on chart')) : L('روی چارت نیست', 'Not on chart')}</span>} />
        {showRef ? (
          <aside className="ns-ref" dir={fa ? 'rtl' : 'ltr'}>
            <input className="scr-search" value={refQuery} onChange={(e) => setRefQuery(e.target.value)} placeholder={L('جستجوی تابع', 'Search functions')} />
            {reference.map((g) => (
              <div key={g.g} className="ns-ref-group">
                <div className="menu-title fa">{g.g}</div>
                {g.items.map(([sig, d]) => (
                  <button key={sig} className="ns-ref-item" title={L('درج در محل نشانگر', 'Insert at the caret')} onClick={() => editor.current?.insert((/^[A-Za-z_][A-Za-z0-9_.]*/.exec(sig) ?? [sig])[0])}>
                    <code className="num" dir="ltr">{sig}</code><span className="fa">{cleanFa(d)}</span>
                  </button>
                ))}
              </div>
            ))}
          </aside>
        ) : null}
      </div>
      <div className={`ns-console${consoleOpen ? ' open' : ''}`} dir={fa ? 'rtl' : 'ltr'}>
        <div className="ns-console-head">
          <button className="lt" onClick={() => setConsoleOpen(!consoleOpen)} aria-label={L('کنسول', 'Console')}><Icon name={consoleOpen ? 'ph-caret-down' : 'ph-caret-up'} size="sm" /></button>
          <span className="fa">{L('کنسول', 'Console')}</span>
          {result?.ok ? (
            <span className="ns-console-sum num">
              {plots.map((p) => <span key={p.name} className="ns-plot"><i style={{ background: p.color }} />{p.name}</span>)}
            </span>
          ) : null}
          <span className="ws-spacer" />
          {log.length ? <button className="lt" title={L('پاک کردن', 'Clear')} onClick={() => setLog([])}><Icon name="ph-trash" size="sm" /></button> : null}
        </div>
        {consoleOpen ? (
          <div className="ns-console-body" role="log">
            {log.map((l, i) => (
              <div key={i} className={`ns-log ${l.kind}`}>
                <span className="num ns-time">{new Date(l.at).toTimeString().slice(0, 8)}</span>
                {l.line ? <button className="link-btn" onClick={() => editor.current?.goToLine(l.line!)}>{l.text}</button> : <span dir="auto">{l.text}</span>}
              </div>
            ))}
            {alerts.map((a, i) => <div key={`a${i}`} className="ns-log info"><span className="num ns-time">alert</span><span dir="auto">{a.msg} · <b>{L(`${faNum(a.bars.length)} بار`, `${a.bars.length} hits`)}</b></span></div>)}
            {!log.length && !alerts.length ? <div className="ns-log muted fa">{L('هنوز اجرا نشده؛ «افزودن به چارت» را بزنید.', 'Nothing run yet: press Add to chart.')}</div> : null}
          </div>
        ) : null}
      </div>
      {menu?.kind === 'title' ? (
        <Dropdown anchor={menu.at} onClose={() => setMenu(null)} className="ns-menu">
          <button className="menu-item" onClick={() => { s.newTab('// NamaScript\nindicator("My indicator")\nplot(ta.sma(close, 20), "SMA", color.blue)\n', 'My indicator'); setMenu(null); }}><Icon name="ph-chart-line-up" size="sm" /><span className="fa">{L('اندیکاتور تازه', 'New indicator')}</span></button>
          <button className="menu-item" onClick={() => { s.newTab('// NamaScript\nstrategy("My strategy")\nfast = ta.ema(close, 9)\nslow = ta.ema(close, 21)\nstrategy.entry("long", crossover(fast, slow))\nstrategy.entry("short", crossunder(fast, slow))\n', 'My strategy'); setMenu(null); }}><Icon name="ph-flask" size="sm" /><span className="fa">{L('استراتژی تازه', 'New strategy')}</span></button>
          <div className="menu-sep" />
          <button className="menu-item" onClick={() => { save(); setMenu(null); }}><Icon name="ph-floppy-disk" size="sm" /><span className="fa">{L('ذخیره', 'Save')}</span><kbd className="num">Ctrl+S</kbd></button>
          <button className="menu-item" onClick={() => { s.save(true); setMenu(null); }}><Icon name="ph-copy" size="sm" /><span className="fa">{L('ذخیره با نام تازه', 'Save as…')}</span></button>
          <button className="menu-item" onClick={() => { setRenaming(true); setMenu(null); }}><Icon name="ph-pencil-simple" size="sm" /><span className="fa">{L('تغییر نام', 'Rename…')}</span></button>
          <button className="menu-item" onClick={() => { s.newTab(tab.code, `${name} (copy)`); setMenu(null); }}><Icon name="ph-copy" size="sm" /><span className="fa">{L('ساخت نسخه‌ی تازه', 'Make a copy')}</span></button>
          <div className="menu-sep" />
          <div className="menu-title fa">{L('اسکریپت‌های من', 'My scripts')}</div>
          {!s.lib.length ? <div className="menu-item muted fa">{L('هنوز اسکریپتی ذخیره نشده.', 'No saved scripts yet.')}</div> : null}
          {s.lib.slice(0, 12).map((x) => (
            <div key={x.id} className="menu-item ns-lib">
              <button className="link-btn" onClick={() => { s.open(x.id); setMenu(null); }}><span dir="auto">{x.name}</span></button>
              <span className="muted num">{dateTime(x.updatedAt)}</span>
              <button className="lt" title={L('حذف', 'Delete')} onClick={() => { if (window.confirm(L('این اسکریپت حذف شود؟', 'Delete this script?'))) s.deleteSaved(x.id); }}><Icon name="ph-trash" size="sm" /></button>
            </div>
          ))}
          <div className="menu-sep" />
          <div className="menu-title fa">{L('نمونه‌ها', 'Examples')}</div>
          <div className="ns-examples-list">
            {EXAMPLES.map((ex, i) => <button key={i} className="menu-item" onClick={() => { s.newTab(ex.code, cleanFa(ex.name).replace(/^[^\p{L}\p{N}]+/u, '').trim()); setMenu(null); }}><Icon name={ex.kind === 'strategy' ? 'ph-flask' : ex.kind === 'library' ? 'ph-code' : 'ph-chart-line-up'} size="sm" /><span className="fa">{cleanFa(ex.name)}</span></button>)}
          </div>
          <div className="menu-sep" />
          <button className="menu-item" onClick={() => { usePage.getState().open('script'); setMenu(null); }}><Icon name="ph-arrows-out" size="sm" /><span className="fa">{L('صفحه‌ی کامل (اشتراک، فایل، تبدیل از Pine)', 'Full page (share, files, from Pine)')}</span></button>
        </Dropdown>
      ) : null}
      {menu?.kind === 'versions' && saved ? (
        <Dropdown anchor={menu.at} onClose={() => setMenu(null)} align={fa ? 'left' : 'right'} className="ns-menu">
          <div className="menu-title fa">{L('تاریخچه‌ی نسخه‌ها', 'Version history')}</div>
          <div className="menu-item ns-ver"><span className="fa">{L('نسخه‌ی کنونی', 'Current version')}</span><span className="muted num">{dateTime(saved.updatedAt)}</span></div>
          {[...(saved.versions ?? [])].reverse().map((v) => (
            <button key={v.at} className="menu-item ns-ver" onClick={() => { s.restoreVersion(saved.id, v.at); setMenu(null); }}>
              <span className="fa">{L('بازگرداندن', 'Restore')}</span><span className="muted num">{dateTime(v.at)} · {L(`${faNum(v.code.split('\n').length)} خط`, `${v.code.split('\n').length} lines`)}</span>
            </button>
          ))}
        </Dropdown>
      ) : null}
    </div>
  );
}
