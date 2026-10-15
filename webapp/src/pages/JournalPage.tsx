import { useMemo, useState } from 'react';
import { EMOTIONS, EMOTIONS_EN, TAGS, pnlFor, useJournal, type JournalEntry } from '../state/journal';
import { usePaper } from '../state/paper';
import { usePrefs } from '../state/prefs';
import { useMarket } from '../state/market';
import { chartImage } from '../chart/snapshot';
import { Field, Tabs, dateTime, downloadCsv, downloadText, money, parseNum, pickText, useL } from './kit';

// The trade journal (feature/journal): write a trade down with the feeling and the lesson, see
// what the record says — win rate, the result by feeling and by tag — and take it with you.
const blank = (symbol: string, price: number | null) => ({ symbol, buy: true, entry: price ? String(price) : '', exit: '', size: '1', pnl: '', emotion: 'آرام', note: '', lesson: '', tags: [] as string[], screenshot: undefined as string | undefined, id: undefined as string | undefined });

async function shrink(blob: Blob): Promise<string | undefined> {
  try {
    const bmp = await createImageBitmap(blob);
    const k = Math.min(1, 720 / bmp.width);
    const c = document.createElement('canvas');
    c.width = Math.round(bmp.width * k); c.height = Math.round(bmp.height * k);
    c.getContext('2d')!.drawImage(bmp, 0, 0, c.width, c.height);
    return c.toDataURL('image/jpeg', 0.72);
  } catch { return undefined; }
}

export default function JournalPage() {
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const symbol = usePrefs((s) => s.symbol);
  const live = useMarket((s) => s.tickers[symbol]?.last ?? null);
  const { entries, upsert, remove, importAll, error } = useJournal();
  const paper = usePaper((s) => s.history);
  const [tab, setTab] = useState<'list' | 'stats'>('list');
  const [draft, setDraft] = useState<ReturnType<typeof blank> | null>(null);
  const [filter, setFilter] = useState('');
  const emo = (e: string) => (lang === 'fa' ? e : EMOTIONS_EN[e] ?? e);

  const list = useMemo(() => entries.filter((e) => !filter || e.symbol.includes(filter.toUpperCase()) || e.tags.includes(filter) || e.emotion === filter), [entries, filter]);
  const stats = useMemo(() => {
    const closed = entries.filter((e) => e.pnl != null);
    const wins = closed.filter((e) => e.pnl! > 0);
    const by = (key: (e: JournalEntry) => string[]) => {
      const m = new Map<string, { n: number; pnl: number; wins: number }>();
      for (const e of closed) for (const k of key(e)) { const r = m.get(k) ?? { n: 0, pnl: 0, wins: 0 }; r.n++; r.pnl += e.pnl!; if (e.pnl! > 0) r.wins++; m.set(k, r); }
      return [...m.entries()].sort((a, b) => b[1].pnl - a[1].pnl);
    };
    return { n: entries.length, closed: closed.length, win: closed.length ? (wins.length / closed.length) * 100 : 0, net: closed.reduce((n, e) => n + e.pnl!, 0), byEmotion: by((e) => [e.emotion]), byTag: by((e) => e.tags), bySymbol: by((e) => [e.symbol]) };
  }, [entries]);

  const save = () => {
    if (!draft) return;
    const entry = parseNum(draft.entry); const size = parseNum(draft.size);
    if (!draft.symbol.trim() || entry == null || size == null) return;
    const exit = parseNum(draft.exit);
    upsert({ id: draft.id, symbol: draft.symbol.trim().toUpperCase(), buy: draft.buy, entry, exit, size, pnl: parseNum(draft.pnl) ?? pnlFor({ buy: draft.buy, entry, exit, size }), emotion: draft.emotion, note: draft.note, lesson: draft.lesson, tags: draft.tags, screenshot: draft.screenshot });
    setDraft(null);
  };
  const attach = async () => { const b = await chartImage(); if (b && draft) { const url = await shrink(b); setDraft({ ...draft, screenshot: url }); } };
  const fromPaper = () => {
    const have = new Set(entries.map((e) => e.id));
    for (const h of paper) if (!have.has(`paper-${h.id}`)) upsert({ id: `paper-${h.id}`, symbol: h.symbol, buy: h.side === 'long', entry: h.entry, exit: h.exit, size: h.qty, pnl: h.pnl, emotion: 'آرام', note: L('از معامله‌ی آزمایشی', 'From paper trading'), lesson: '', tags: [], createdAt: h.closedAt });
  };

  if (draft) {
    const set = (k: keyof typeof draft) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => setDraft({ ...draft, [k]: e.target.value });
    return (
      <div className="page-inner narrow">
        <form className="form card" onSubmit={(e) => { e.preventDefault(); save(); }}>
          <div className="row two">
            <Field label={L('نماد', 'Symbol')}><input className="num" dir="ltr" value={draft.symbol} onChange={set('symbol')} /></Field>
            <Tabs value={draft.buy ? 'b' : 's'} onChange={(k) => setDraft({ ...draft, buy: k === 'b' })} tabs={[{ id: 'b', label: L('خرید', 'Buy') }, { id: 's', label: L('فروش', 'Sell') }]} />
          </div>
          <div className="row three">
            <Field label={L('ورود', 'Entry')}><input className="num" dir="ltr" inputMode="decimal" value={draft.entry} onChange={set('entry')} /></Field>
            <Field label={L('خروج', 'Exit')}><input className="num" dir="ltr" inputMode="decimal" value={draft.exit} onChange={set('exit')} /></Field>
            <Field label={L('حجم', 'Size')}><input className="num" dir="ltr" inputMode="decimal" value={draft.size} onChange={set('size')} /></Field>
          </div>
          <Field label={L('سود و زیان (خالی = محاسبه از قیمت‌ها)', 'P&L (empty = from the prices)')}><input className="num" dir="ltr" inputMode="decimal" value={draft.pnl} onChange={set('pnl')} /></Field>
          <Field label={L('احساس', 'Feeling')}><div className="chips flat">{EMOTIONS.map((e) => <button type="button" key={e} className={`chip${draft.emotion === e ? ' on' : ''}`} onClick={() => setDraft({ ...draft, emotion: e })}><span className="fa">{emo(e)}</span></button>)}</div></Field>
          <Field label={L('برچسب‌ها', 'Tags')}><div className="chips flat">{TAGS.map((tg) => <button type="button" key={tg} className={`chip${draft.tags.includes(tg) ? ' on' : ''}`} onClick={() => setDraft({ ...draft, tags: draft.tags.includes(tg) ? draft.tags.filter((x) => x !== tg) : [...draft.tags, tg] })}><span className="fa">{tg}</span></button>)}</div></Field>
          <Field label={L('یادداشت', 'Note')}><textarea rows={3} value={draft.note} onChange={set('note')} dir="auto" /></Field>
          <Field label={L('درس این معامله', 'The lesson')}><textarea rows={2} value={draft.lesson} onChange={set('lesson')} dir="auto" /></Field>
          <div className="row">
            <button type="button" className="btn btn-quiet sm" onClick={() => void attach()}>{L('پیوست تصویر چارت', 'Attach the chart picture')}</button>
            {draft.screenshot ? <button type="button" className="btn btn-quiet sm" onClick={() => setDraft({ ...draft, screenshot: undefined })}>{L('حذف تصویر', 'Remove the picture')}</button> : null}
          </div>
          {draft.screenshot ? <img className="journal-shot" src={draft.screenshot} alt="" /> : null}
          <div className="row">
            <button className="btn btn-primary sm">{L('ذخیره', 'Save')}</button>
            <button type="button" className="btn btn-quiet sm" onClick={() => setDraft(null)}>{L('انصراف', 'Cancel')}</button>
          </div>
        </form>
      </div>
    );
  }

  return (
    <div className="page-inner">
      <div className="row">
        <Tabs value={tab} onChange={setTab} tabs={[{ id: 'list', label: L('معاملات', 'Trades') }, { id: 'stats', label: L('آمار', 'Statistics') }]} />
        <span className="ws-spacer" />
        <button className="btn btn-primary sm" onClick={() => setDraft(blank(symbol, live))}>{L('ثبت معامله', 'Log a trade')}</button>
        <button className="btn btn-quiet sm" onClick={fromPaper} disabled={!paper.length}>{L('افزودن معاملات آزمایشی', 'Add paper trades')}</button>
        <button className="btn btn-quiet sm" onClick={() => downloadCsv('journal.csv', [['date', 'symbol', 'side', 'entry', 'exit', 'size', 'pnl', 'emotion', 'tags', 'note', 'lesson', 'has_screenshot'], ...entries.map((e) => [new Date(e.createdAt).toISOString(), e.symbol, e.buy ? 'buy' : 'sell', e.entry, e.exit, e.size, e.pnl, e.emotion, e.tags.join('|'), e.note, e.lesson, e.screenshot ? 1 : 0])])}>CSV</button>
        <button className="btn btn-quiet sm" onClick={() => downloadText('journal.json', JSON.stringify(entries))}>JSON</button>
        <button className="btn btn-quiet sm" onClick={() => void pickText().then((t) => { if (t) { try { importAll(JSON.parse(t)); } catch { /* not JSON */ } } })}>{L('ورود از فایل', 'Import')}</button>
      </div>
      {error ? <p className="down fa">{L('حافظه‌ی مرورگر پر است؛ تصویرهای قدیمی را حذف کنید.', 'Browser storage is full; remove some pictures.')}</p> : null}
      {tab === 'list' ? (
        <>
          <input className="scr-search" value={filter} onChange={(e) => setFilter(e.target.value)} placeholder={L('نماد، برچسب یا احساس', 'Symbol, tag or feeling')} dir="auto" />
          {!list.length ? <div className="empty fa">{L('هنوز معامله‌ای ثبت نشده است.', 'No trades logged yet.')}</div> : null}
          <div className="cards">
            {list.map((e) => (
              <article key={e.id} className="card journal-card">
                <div className="row"><b className="sym">{e.symbol}</b><span className={e.buy ? 'up' : 'down'}>{e.buy ? 'Buy' : 'Sell'}</span><span className="ws-spacer" />{e.pnl != null ? <b className={`num ${e.pnl >= 0 ? 'up' : 'down'}`}>{money(e.pnl)}</b> : <span className="muted fa">{L('باز', 'Open')}</span>}</div>
                <div className="num muted small">{e.entry} → {e.exit ?? '—'} · {e.size} · {dateTime(e.createdAt)}</div>
                <div className="chips flat"><span className="chip on"><span className="fa">{emo(e.emotion)}</span></span>{e.tags.map((tg) => <span key={tg} className="chip"><span className="fa">{tg}</span></span>)}</div>
                {e.note ? <p className="fa" dir="auto">{e.note}</p> : null}
                {e.lesson ? <p className="fa muted" dir="auto">💡 {e.lesson}</p> : null}
                {e.screenshot ? <img className="journal-shot" src={e.screenshot} alt="" loading="lazy" /> : null}
                <div className="row">
                  <button className="link-btn" onClick={() => setDraft({ ...blank(e.symbol, null), ...e, entry: String(e.entry), exit: e.exit == null ? '' : String(e.exit), size: String(e.size), pnl: e.pnl == null ? '' : String(e.pnl) })}>{L('ویرایش', 'Edit')}</button>
                  <button className="link-btn" onClick={() => { if (window.confirm(L('این معامله حذف شود؟', 'Delete this trade?'))) remove(e.id); }}>{L('حذف', 'Delete')}</button>
                </div>
              </article>
            ))}
          </div>
        </>
      ) : (
        <>
          <div className="pulse">
            <div className="card"><span className="muted fa">{L('معاملات', 'Trades')}</span><b className="num">{stats.n}</b></div>
            <div className="card"><span className="muted fa">{L('نرخ برد', 'Win rate')}</span><b className="num">{stats.win.toFixed(1)}%</b></div>
            <div className="card"><span className="muted fa">{L('نتیجه‌ی خالص', 'Net result')}</span><b className={`num ${stats.net >= 0 ? 'up' : 'down'}`}>{money(stats.net)}</b></div>
          </div>
          {([[L('بر اساس احساس', 'By feeling'), stats.byEmotion, true], [L('بر اساس برچسب', 'By tag'), stats.byTag, false], [L('بر اساس نماد', 'By symbol'), stats.bySymbol, false]] as const).map(([title, rows, isEmo]) => (
            <section key={title} className="card"><h3 className="fa">{title}</h3>
              <table className="table"><tbody>{rows.map(([k, r]) => <tr key={k}><td className="fa">{isEmo ? emo(k) : k}</td><td className="r num">{r.n}</td><td className="r num">{((r.wins / r.n) * 100).toFixed(0)}%</td><td className={`r num ${r.pnl >= 0 ? 'up' : 'down'}`}>{money(r.pnl)}</td></tr>)}</tbody></table>
            </section>
          ))}
        </>
      )}
    </div>
  );
}
