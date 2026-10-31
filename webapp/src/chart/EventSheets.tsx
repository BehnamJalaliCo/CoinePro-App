// What a click on the event lane opens (OLD core/chartevents ChartEventSheet.kt, NotableBarSheet.kt):
// a glyph's whole group, earliest first, or — for the dot under an unusually large bar — «چرا این
// حرکت؟»: how big the bar was against this chart's recent normal, open to close, and what was on
// the calendar and the wire inside that bar, or the honest sentence that nothing was.
import { useEffect, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { usePrefs } from '../state/prefs';
import { useL } from '../pages/kit';
import { faDay, faNum } from '../i18n/time';
import { Icon } from '../ui/Icon';
import { clockText, dateText } from './time';
import { IMPORTANCE_COLOR, NEWS_PURPLE, type Importance, type LaneItem, type NotableReading } from './eventLane';

const IMPORTANCE_FA: Record<Importance, string> = { high: 'زیاد', medium: 'متوسط', low: 'کم', holiday: 'تعطیل' };
const IMPORTANCE_EN: Record<Importance, string> = { high: 'High', medium: 'Medium', low: 'Low', holiday: 'Holiday' };

function Sheet({ title, subtitle, onClose, children, kind }: { title: string; subtitle: string; onClose(): void; children: ReactNode; kind: string }) {
  const fa = usePrefs((s) => s.lang) === 'fa';
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') { e.stopPropagation(); onClose(); } };
    window.addEventListener('keydown', onKey, true);
    return () => window.removeEventListener('keydown', onKey, true);
  }, [onClose]);
  return createPortal(
    <div className="scrim center" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div className={`dialog tv-small ev-sheet ${kind}`} role="dialog" aria-modal="true" aria-label={title} dir={fa ? 'rtl' : 'ltr'}>
        <header className="dialog-head">
          <div className="ev-sheet-head">
            <h2 className={fa ? 'fa' : ''}>{title}</h2>
            <p className={`ev-sheet-sub muted${fa ? ' fa' : ''}`}>{subtitle}</p>
          </div>
          <button type="button" className="ib" onClick={onClose} aria-label={fa ? 'بستن' : 'Close'}><Icon name="ph-x" size="md" /></button>
        </header>
        <div className="ev-sheet-body">{children}</div>
      </div>
    </div>,
    document.body,
  );
}

/** One event, as a card: the same row whichever mark the reader reached it through. */
export function EventRow({ item }: { item: LaneItem }) {
  const L = useL();
  const fa = usePrefs((s) => s.lang) === 'fa';
  const timezone = usePrefs((s) => s.timezone);
  const isNews = item.kind === 'news';
  const title = isNews ? item.news.title : item.event.title;
  const persianTitle = /[؀-ۿ]/.test(title);
  const ev = item.kind === 'event' ? item.event : null;
  // «واقعی 3.2% · پیش‌بینی 3.1% · قبلی 3.0%»: the server's own figures, each kept left to right.
  const figures = ev ? ([[L('واقعی', 'Actual'), ev.actual], [L('پیش‌بینی', 'Forecast'), ev.forecast], [L('قبلی', 'Prior'), ev.previous]] as const).filter(([, v]) => v) : [];
  const summary = item.kind === 'news' ? item.news.summary : '';
  const source = item.kind === 'news' ? item.news.source : ev?.currency ?? '';
  return (
    <article className="ev-row" data-kind={item.kind}>
      <div className="ev-row-top">
        <span className="ev-pills">
          <span className="ev-pill" style={{ ['--pill' as string]: isNews ? NEWS_PURPLE : 'var(--accent, #2962FF)' }}>{isNews ? L('خبر', 'News') : L('اقتصادی', 'Economic')}</span>
          {ev ? <span className="ev-pill" style={{ ['--pill' as string]: IMPORTANCE_COLOR[ev.impact] }}>{fa ? `اثر ${IMPORTANCE_FA[ev.impact]}` : `${IMPORTANCE_EN[ev.impact]} impact`}</span> : null}
        </span>
        <span className="num muted ev-clock">{clockText(item.at, timezone)}</span>
      </div>
      <div className={`ev-title${persianTitle ? ' fa' : ''}`} dir={persianTitle ? 'rtl' : 'ltr'}>{title}</div>
      {summary ? <div className={`ev-detail${/[؀-ۿ]/.test(summary) ? ' fa' : ''}`} dir={/[؀-ۿ]/.test(summary) ? 'rtl' : 'ltr'}>{summary}</div> : null}
      {figures.length ? (
        <div className={`ev-detail${fa ? ' fa' : ''}`}>
          {figures.map(([label, v], i) => <span key={label}>{i ? ' · ' : ''}{label} <span className="num">{v}</span></span>)}
        </div>
      ) : null}
      <div className="ev-row-foot muted">
        <span className="ev-source">{source}</span>
        <span className={fa ? 'fa' : 'num'}>{fa ? faDay(item.at * 1000) : dateText(item.at, timezone)}</span>
      </div>
    </article>
  );
}

/** A glyph's group: every event, earliest first. */
export function EventSheet({ items, bars, onClose }: { items: LaneItem[]; bars: number; onClose(): void }) {
  const L = useL();
  const fa = usePrefs((s) => s.lang) === 'fa';
  const n = items.length;
  const title = bars > 1 ? L('رویدادهای این نقطه از چارت', 'Events at this point of the chart') : L('رویداد روی این کندل', 'Events on this bar');
  const subtitle = bars > 1
    ? (fa ? `${faNum(n)} رویداد در ${faNum(bars)} کندل کنار هم، به ترتیب وقوع` : `${n} events across ${bars} neighbouring bars, in order`)
    : n > 1
      ? (fa ? `${faNum(n)} رویداد در بازه‌ی این کندل، به ترتیب وقوع` : `${n} events inside this bar, in order`)
      : L('یک رویداد در بازه‌ی این کندل', 'One event inside this bar');
  return (
    <Sheet title={title} subtitle={subtitle} onClose={onClose} kind="ev-group">
      <div className="ev-list">{items.map((it, i) => <EventRow key={i} item={it} />)}</div>
    </Sheet>
  );
}

/** «چرا این حرکت؟» — what the dot under an unusually large bar opens. */
export function NotableSheet({ reading, onClose }: { reading: NotableReading; onClose(): void }) {
  const L = useL();
  const fa = usePrefs((s) => s.lang) === 'fa';
  const timezone = usePrefs((s) => s.timezone);
  const moment = fa ? `${faDay(reading.from * 1000)} · ${clockText(reading.from, timezone)}` : `${dateText(reading.from, timezone)} · ${clockText(reading.from, timezone)}`;
  return (
    <Sheet title={L('چرا این حرکت؟', 'Why this move?')} subtitle={moment} onClose={onClose} kind="ev-notable">
      <dl className="ev-reading">
        {reading.ratio != null ? (
          <div><dt className={fa ? 'fa' : ''}>{L('چقدر بزرگ، نسبت به معمولِ اخیرِ همین چارت', 'How big, against what this chart has been doing lately')}</dt>
            <dd className={`ev-ratio${fa ? ' fa' : ''}`}><span className="num">{reading.ratio.toFixed(1)}</span>{fa ? ' برابر' : '×'}</dd></div>
        ) : null}
        {reading.movePct != null ? (
          <div><dt className={fa ? 'fa' : ''}>{L('باز تا بسته', 'Open to close')}</dt>
            <dd className={`ev-move ${reading.up ? 'up' : 'down'}${fa ? ' fa' : ''}`}><span className="num">{`${Math.abs(reading.movePct).toFixed(2)}%`}</span>{` ${reading.up ? L('بالا', 'up') : L('پایین', 'down')}`}</dd></div>
        ) : null}
      </dl>
      {reading.items.length ? (
        <>
          <div className={`ev-within muted${fa ? ' fa' : ''}`}>{L('در تقویم و اخبار، داخل همین کندل', 'On the calendar and the wire, inside this bar')}</div>
          <div className="ev-list">{reading.items.map((it, i) => <EventRow key={i} item={it} />)}</div>
        </>
      ) : (
        <p className={`ev-nothing muted${fa ? ' fa' : ''}`}>{L(
          'این کندل به‌طور غیرعادی بزرگ است و هیچ‌چیزی که این برنامه دارد داخلش نیفتاده — نه رویدادی، نه خبری. بازار گاهی به دلایلی تکان می‌خورد که جایی منتشر نمی‌شوند، و نام‌بردن نزدیک‌ترین خبر حدس است نه پاسخ.',
          'The bar is unusually large and nothing this app carries fell inside it — no release, no headline. Markets move for reasons that are not always published, and naming the nearest one would be a guess rather than an answer.',
        )}</p>
      )}
    </Sheet>
  );
}
