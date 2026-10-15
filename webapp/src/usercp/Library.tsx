// What the workspace holds, item by item: the saved layouts and indicator templates, the watchlists
// and their symbols, the price alerts — each opened on the chart or removed from here, as the old
// panel's layout, watchlist and alert tabs did, now beside their cloud copy.
import { useLayouts } from '../state/layouts';
import { useWatchlist } from '../state/watchlist';
import { useAlerts } from '../state/alerts';
import { usePage } from '../state/page';
import { usePrefs } from '../state/prefs';
import { useL } from '../pages/kit';
import { Card, Pill, stamp, useCount } from './ui';

export function Library() {
  const L = useL();
  const count = useCount();
  const { layouts, templates, load, remove, applyTemplate, removeTemplate } = useLayouts();
  const lists = useWatchlist((s) => s.lists);
  const alerts = useAlerts((s) => s.list);
  const setSymbol = usePrefs((s) => s.setSymbol);
  const { open, close } = usePage();
  const toChart = () => close();

  return (
    <Card title={L('در این مرورگر', 'In this browser')} sub={L('هر چیزی که در بخش‌های بالا همگام می‌شود، یکی‌یکی.', 'Everything the parts above carry, one by one.')}>
      <div className="ucp-library">
        <section>
          <h4>{L('چیدمان‌ها', 'Layouts')} <span className="ucp-muted">{count(layouts.length)}</span></h4>
          {layouts.length ? <ul>{layouts.map((l) => (
            <li key={l.name}>
              <span><b dir="auto">{l.name}</b><span className="ucp-muted num">{l.symbol} · {l.interval} · {stamp(l.savedAt)}</span></span>
              <span className="ucp-actions">
                <button className="btn btn-quiet sm" onClick={() => { load(l.name); toChart(); }}>{L('باز کردن', 'Open')}</button>
                <button className="btn btn-quiet sm" aria-label={L('حذف', 'Delete')} onClick={() => { if (window.confirm(L(`چیدمان «${l.name}» حذف شود؟`, `Delete the layout «${l.name}»?`))) remove(l.name); }}>×</button>
              </span>
            </li>
          ))}</ul> : <p className="ucp-muted">{L('چیدمانی ذخیره نشده؛ از نوار بالای چارت «ذخیره» را بزنید.', 'No saved layout; use Save in the chart’s top bar.')}</p>}
        </section>
        <section>
          <h4>{L('قالب‌های اندیکاتور', 'Indicator templates')} <span className="ucp-muted">{count(templates.length)}</span></h4>
          {templates.length ? <ul>{templates.map((t) => (
            <li key={t.name}>
              <span><b dir="auto">{t.name}</b><span className="ucp-muted">{L(`${count(t.studies.length)} اندیکاتور`, `${t.studies.length} indicators`)}</span></span>
              <span className="ucp-actions">
                <button className="btn btn-quiet sm" onClick={() => { applyTemplate(t.name); toChart(); }}>{L('اعمال', 'Apply')}</button>
                <button className="btn btn-quiet sm" aria-label={L('حذف', 'Delete')} onClick={() => { if (window.confirm(L(`قالب «${t.name}» حذف شود؟`, `Delete the template «${t.name}»?`))) removeTemplate(t.name); }}>×</button>
              </span>
            </li>
          ))}</ul> : <p className="ucp-muted">{L('قالبی ذخیره نشده.', 'No saved template.')}</p>}
        </section>
        <section>
          <h4>{L('دیده‌بان‌ها', 'Watchlists')} <span className="ucp-muted">{count(lists.length)}</span></h4>
          <ul>{lists.map((w) => (
            <li key={w.id} className="wrap">
              <span><b dir="auto">{w.name}</b><span className="ucp-muted">{L(`${count(w.symbols.length)} نماد`, `${w.symbols.length} symbols`)}</span></span>
              <span className="ucp-symbols">{w.symbols.slice(0, 12).map((s) => <button key={s} className="num" onClick={() => { setSymbol(s); toChart(); }}>{s}</button>)}{w.symbols.length > 12 ? <span className="ucp-muted num">+{w.symbols.length - 12}</span> : null}</span>
            </li>
          ))}</ul>
        </section>
        <section>
          <h4>{L('هشدارهای قیمت', 'Price alerts')} <span className="ucp-muted">{count(alerts.length)}</span></h4>
          {alerts.length ? <ul>{alerts.slice(0, 8).map((a) => (
            <li key={a.id}><span><b className="num">{a.symbol}</b><span className="ucp-muted num">{a.condition} {a.value}</span></span>{a.active ? <Pill tone="good">{L('فعال', 'Active')}</Pill> : <Pill>{L('خاموش', 'Off')}</Pill>}</li>
          ))}</ul> : null}
          <button className="ucp-link" onClick={() => open('alerts')}>{L('مرکز هشدار', 'Alert center')}</button>
        </section>
      </div>
    </Card>
  );
}
