// The replay session's own report (OLD feature/chart ReplayBar.kt ReplaySessionReport, BacktestSheet
// ReplaySessionCard and SampleWarning): the closed result, the counts, what is still open and worth,
// how each trade ended with the discipline line under it, the candles revealed, and the rates
// withheld — with the sentence saying why — until thirty trades have closed. Saved nowhere.
import { createPortal } from 'react-dom';
import { useReplay } from '../state/replay';
import { replayReport, CONFIDENT_TRADES } from '../state/replayReport';
import { usePrefs } from '../state/prefs';
import { faNum } from '../i18n/time';
import { useL } from '../pages/kit';
import { Icon } from '../ui/Icon';
import '../chart/rasad.css';

const money = (v: number, signed = false) => `${signed ? (v > 0 ? '+' : v < 0 ? '−' : '') : v < 0 ? '−' : ''}${Math.abs(v).toFixed(2)}`;

export function ReplayReportSheet({ onClose }: { onClose(): void }) {
  const L = useL();
  const fa = usePrefs((s) => s.lang) === 'fa';
  const trades = useReplay((r) => r.trades);
  const slice = useReplay((r) => r.slice);
  const price = slice?.[slice.length - 1]?.c ?? 0;
  const r = replayReport(trades, price, slice?.length ?? 0);
  const n = (v: number) => (fa ? faNum(v) : String(v));
  const row = (key: string, label: string, value: string, tone = '') => (
    <div data-row={key}><dt>{label}</dt><dd className={`num ${tone}`}><bdi dir="ltr">{value}</bdi></dd></div>
  );
  const tone = (v: number) => (v > 0 ? 'up' : v < 0 ? 'down' : '');
  return createPortal(
    <div className="scrim center" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div className="dialog rp-report" role="dialog" aria-modal="true" aria-label={L('بک‌تست معامله‌های خودتان', 'A backtest of your own trading')} dir={fa ? 'rtl' : 'ltr'}
        onMouseDown={(e) => e.stopPropagation()} onKeyDown={(e) => { if (e.key === 'Escape') { e.stopPropagation(); onClose(); } }}>
        <header className="dialog-head"><h2>{L('بک‌تست معامله‌های خودتان', 'A backtest of your own trading')}</h2>
          <button type="button" className="ib" onClick={onClose} aria-label={L('بستن', 'Close')} autoFocus><Icon name="ph-x" size="md" /></button></header>
        <div className="rp-report-body">
          <p className="rp-report-sub">{L('همان سنجه‌های گزارش استراتژی، روی معامله‌هایی که خودتان در این ریپلی گرفتید.', 'The same measures the strategy report uses, over the trades you took in this replay.')}</p>
          <dl>
            {row('net', L('نتیجه‌ی بسته‌شده', 'Closed result'), money(r.net, true), tone(r.net))}
            {row('trades', L('معامله‌ی بسته‌شده', 'Closed trades'), String(r.trades))}
            {row('wins', L('برنده', 'Winners'), String(r.wins), 'up')}
            {row('losses', L('بازنده', 'Losers'), String(r.losses), 'down')}
            {r.open > 0 ? <>
              {row('open', L('موقعیت باز', 'Still open'), String(r.open))}
              {row('unrealised', L('سود شناور موقعیت‌های باز', 'Unrealised on open positions'), money(r.unrealised, true), tone(r.unrealised))}
              <p className="note">{L(`${n(r.open)} موقعیت هنوز باز است و در هیچ‌کدام از عددهای این گزارش نیست؛ سود شناورشان جدا نوشته شده.`, `${r.open} position(s) are still open and are in none of the figures here; what they are worth is stated separately.`)}</p>
            </> : null}
            {r.trades > 0 ? <>
              {row('exit-stop', L('با حد ضرر بسته شد', 'Closed at the stop'), String(r.exits.stop), 'down')}
              {row('exit-target', L('با حد سود بسته شد', 'Closed at the target'), String(r.exits.target), 'up')}
              {row('exit-manual', L('دستی بسته شد', 'Closed by hand'), String(r.exits.manual))}
              {row('exit-session', L('با پایان جلسه بسته شد', 'Closed with the session'), String(r.exits.session))}
              <p className="note rp-discipline">{L('چند معامله را حد ضرر بست و چندتا را خودتان زودتر بستید: این همان چیزی است که بک‌تست یک راهبرد نمی‌تواند بگوید، چون راهبرد دستی ندارد که بترسد.', 'How many were closed by the stop and how many you closed early is the one thing a strategy backtest can never report, because a strategy has no hand to lose its nerve with.')}</p>
            </> : null}
            {row('bars', L('کندل‌های این جلسه', 'Candles in this session'), String(r.bars))}
          </dl>
          {r.trades === 0 ? <p className="muted rp-empty-report">{L('هنوز معامله‌ای نبسته‌اید. یک موقعیت باز کنید، حد ضرر و حد سود بگذارید و کندل‌ها را جلو ببرید تا معلوم شود کدام‌یک زودتر خورد.', 'Nothing has closed yet. Open a position, give it a stop and a target, and step the candles forward to find out which one price reached first.')}</p> : (
            <>
              <dl>
                {row('win-rate', L('درصد برد', 'Win rate'), r.winRate == null ? '—' : `${r.winRate.toFixed(0)}%`)}
                {row('profit-factor', L('ضریب سود', 'Profit factor'), r.profitFactor == null ? '—' : r.profitFactor.toFixed(2))}
              </dl>
              {!r.sampled ? <p className="note warn rp-sample">{L(`فقط ${n(r.trades)} معامله بسته شده. زیر ${n(CONFIDENT_TRADES)} معامله، درصد برد و ضریب سود اندازه‌گیری نیستند و به‌جای عدد خط تیره می‌بینید. برد و باخت را بشمارید، نه درصدشان را.`, `Only ${r.trades} trades have closed. Under ${CONFIDENT_TRADES}, a win rate and a profit factor are not measurements, so a dash is shown instead of a number. Count the wins and the losses rather than reading a percentage of them.`)}</p> : null}
              <p className="note">{L('این گزارش فقط برای همین جلسه‌ی بازپخش است و هیچ‌جا ذخیره نمی‌شود. اگر یک کندل هم به حد ضرر و هم به حد سود رسیده باشد، حد ضرر حساب می‌شود؛ ترتیبشان درون کندل معلوم نیست و فرض بدبینانه تنها فرض درست است.', 'This report belongs to this replay session and is saved nowhere. Where a single candle reached both the stop and the target, the stop is taken: their order inside the candle is unknowable, and the pessimistic assumption is the only defensible one.')}</p>
            </>
          )}
        </div>
      </div>
    </div>,
    document.body,
  );
}
