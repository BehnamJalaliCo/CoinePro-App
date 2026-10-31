// «این صفحه چیست؟» — every screen that teaches, its key, and the two sentences it teaches with
// (OLD core/designsystem CoineProTeaching.kt `TeachingSurface`, strings teaching_*_lead/_pitfall).
//
// The lead says what the screen does; the pitfall is the one thing a first-time reader gets wrong.
// The card sits in the page's own flow, above the page and never over it, so it covers no control;
// its × puts it away for good (per surface, in this browser), and the «؟» in the page's header
// brings it back. The key is the persistence contract: renaming one re-teaches that screen once.
import { create } from 'zustand';
import type { PageId } from './registry';

export type TeachingKey =
  | 'home' | 'markets' | 'chart' | 'signals' | 'ai' | 'tools' | 'news' | 'calendar' | 'screener' | 'heatmap'
  | 'watchlist' | 'alerts' | 'journal' | 'paper_trade' | 'backtest' | 'script' | 'portfolio' | 'connections'
  | 'membership' | 'activity' | 'academy' | 'dom' | 'search';

export interface TeachingSurface { key: TeachingKey; lead: [fa: string, en: string]; pitfall: [fa: string, en: string] | null }

export const TEACHING: Record<TeachingKey, TeachingSurface> = {
  home: { key: 'home', lead: ['خلاصه‌ی حساب و بازار در یک صفحه: موجودی، چند نماد و سیگنال‌های باز.', 'Your account and the market summarised on one screen: balance, a few symbols, open signals.'], pitfall: ['هرچه اینجاست خلاصه است؛ رقم کامل هر بخش داخل صفحه‌ی خودش است.', 'Everything here is a summary; each section’s full figures are on its own screen.'] },
  markets: { key: 'markets', lead: ['قیمت لحظه‌ای و تغییر ۲۴ ساعته‌ی همه‌ی نمادها، در یک فهرست.', 'Every symbol’s live price and 24-hour change, in one list.'], pitfall: ['درصد هر ردیف تغییر ۲۴ ساعت گذشته است، نه تغییر از اول امروز.', 'The percentage on a row is the change over the past 24 hours, not the change since midnight.'] },
  chart: { key: 'chart', lead: ['قیمت یک نماد در طول زمان. نام نماد را بکشید تا عوض شود؛ مداد ابزار ترسیم، لغزنده‌ها اندیکاتورها، و «…» عمق بازار، مقایسه و بقیه را باز می‌کند.', 'One symbol’s price over time. Drag the ticker to change it; the pencil is the drawing tools, the sliders the indicators, and «…» holds depth of market, compare and the rest.'], pitfall: ['آخرین کندل هنوز بسته نشده و تا پایان تایم‌فریم عوض می‌شود.', 'The last candle has not closed yet and keeps changing until the timeframe ends.'] },
  signals: { key: 'signals', lead: ['سیگنال‌های منتشرشده، با نقطه‌ی ورود، حد ضرر و هدف هرکدام.', 'Published signals, each with its entry, stop loss and target.'], pitfall: ['سیگنال یک گزارش است، نه توصیه؛ حجم و تصمیم با خودتان است.', 'A signal is a report, not advice; the size and the decision are yours.'] },
  ai: { key: 'ai', lead: ['از مدل می‌خواهی یک نماد را تحلیل کند و نتیجه را همین‌جا می‌خوانی.', 'You ask the model to analyse a symbol and read its answer here.'], pitfall: ['مدل روی داده‌ی گذشته حرف می‌زند؛ خروجی‌اش تحلیل است، نه پیش‌بینی.', 'The model speaks from past data; what it produces is analysis, not a forecast.'] },
  tools: { key: 'tools', lead: ['ماشین‌حساب‌های معامله: حجم، ریسک، حد ضرر و سود و زیان.', 'Trading calculators: position size, risk, stop loss, profit and loss.'], pitfall: ['این‌ها فقط حساب می‌کنند؛ هیچ سفارشی از اینجا به صرافی نمی‌رود.', 'These only calculate; no order goes to an exchange from here.'] },
  news: { key: 'news', lead: ['خبرهای بازار با منبع مشخص، تازه‌ترین در بالا.', 'Market news with its source named, newest first.'], pitfall: ['ترتیب خبرها زمانی است، نه بر پایه‌ی اهمیت یا اثرشان بر قیمت.', 'Stories are ordered by time, not by importance or by their effect on price.'] },
  calendar: { key: 'calendar', lead: ['زمان انتشار داده‌ی اقتصادی و عددی که بازار انتظارش را دارد.', 'When each economic figure is released, and the number the market expects.'], pitfall: ['تا لحظه‌ی انتشار، عددی که می‌بینی پیش‌بینی است، نه رقم واقعی.', 'Until the moment of release, the number you see is the forecast, not the actual figure.'] },
  screener: { key: 'screener', lead: ['شرط می‌گذاری و نمادهایی که با آن شرط می‌خوانند فهرست می‌شوند.', 'You set conditions and the symbols that meet them are listed.'], pitfall: ['این فهرست عکس همین لحظه است؛ با هر به‌روزرسانی نمادها کم و زیاد می‌شوند.', 'The list is a snapshot of this moment; symbols join and leave it on every refresh.'] },
  heatmap: { key: 'heatmap', lead: ['کل بازار در یک تصویر: رنگ هر خانه تغییر قیمت است و اندازه‌اش وزن آن نماد.', 'The whole market in one picture: each cell’s colour is its price change and its size is that symbol’s weight.'], pitfall: ['اندازه‌ی خانه ارزش بازار نیست؛ نقدشوندگی یا حجمی است که در تنظیمات انتخاب کرده‌ای.', 'Cell size is not market cap; it is the liquidity or volume you chose in the settings.'] },
  watchlist: { key: 'watchlist', lead: ['نمادهایی که خودتان ستاره زده‌اید، جدا از فهرست کامل بازار.', 'The symbols you starred, kept apart from the full market list.'], pitfall: ['دیده‌بان فهرست تماشای شماست، نه سبد دارایی؛ ستاره زدن چیزی نمی‌خرد.', 'A watchlist is a list you watch, not a portfolio; starring a symbol buys nothing.'] },
  alerts: { key: 'alerts', lead: ['شرط قیمتی می‌گذاری و وقتی برقرار شد اعلان می‌گیری.', 'You set a price condition and get a notification when it is met.'], pitfall: ['هشدار فقط خبر می‌دهد؛ هیچ معامله‌ای را باز یا بسته نمی‌کند.', 'An alert only tells you; it never opens or closes a trade.'] },
  journal: { key: 'journal', lead: ['هر معامله را با دلیل ورود و خروجش ثبت می‌کنی تا بعد الگوی خطاها را ببینی.', 'You record each trade with why you entered and why you left, so the pattern in your mistakes becomes visible.'], pitfall: ['ژورنال دستی است و از حساب متصل چیزی نمی‌خواند؛ هرچه اینجاست را خودتان نوشته‌اید.', 'The journal is manual and reads nothing from a connected account; everything here you wrote yourself.'] },
  paper_trade: { key: 'paper_trade', lead: ['تمرین معامله روی همان بازار واقعی، با پول فرضی.', 'Practice trading on the real market with imaginary money.'], pitfall: ['قیمت‌ها واقعی است و پول فرضی؛ سود و زیان اینجا به حساب واقعی نمی‌رود.', 'The prices are real and the money is not; profit and loss here never reach a real account.'] },
  backtest: { key: 'backtest', lead: ['یک قانون معاملاتی را روی داده‌ی گذشته اجرا می‌کنی تا ببینی چه نتیجه‌ای می‌داد.', 'You run a trading rule over past data to see what it would have done.'], pitfall: ['نتیجه فقط درباره‌ی همان بازه‌ی گذشته است؛ بازه‌ی دیگر جواب دیگری می‌دهد.', 'The result is about that stretch of history only; a different stretch gives a different answer.'] },
  script: { key: 'script', lead: ['اندیکاتور و استراتژی نمااسکریپت را اینجا می‌نویسید و روی چارت اجرا می‌کنید.', 'You write NamaScript indicators and strategies here and run them on the chart.'], pitfall: ['اسکریپت فقط روی چارت محاسبه می‌کند؛ خودش سفارشی ثبت نمی‌کند.', 'A script only calculates on the chart; it never places an order itself.'] },
  portfolio: { key: 'portfolio', lead: ['کارنامه‌ی معامله‌های بسته‌شده‌ی حساب متصل: سود خالص، نرخ برد و بیشترین افت.', 'The record of your connected account’s closed trades: net profit, win rate and worst drawdown.'], pitfall: ['فقط معامله‌های بسته‌شده در این عددها هستند؛ سود شناور پوزیشن باز شمرده نمی‌شود.', 'Only closed trades are in these figures; an open position’s floating profit is not counted.'] },
  connections: { key: 'connections', lead: ['حساب صرافی را با کلید API وصل می‌کنی تا سفارش از داخل برنامه ثبت شود.', 'You connect an exchange account with an API key so orders can be placed from the app.'], pitfall: ['کلید روی گوشی ذخیره نمی‌شود، و دسترسی برداشت هم لازم نیست.', 'The key is never stored on the phone, and withdrawal permission is not needed.'] },
  membership: { key: 'membership', lead: ['وضعیت اشتراکت و اینکه هر پلن کدام بخش‌ها را باز می‌کند.', 'Your subscription’s state, and which parts of the app each plan opens.'], pitfall: ['اشتراک دسترسی به بخش‌های برنامه را باز می‌کند، نه سود یا سیگنال تضمینی.', 'A subscription opens parts of the app, not profit or a guaranteed signal.'] },
  activity: { key: 'activity', lead: ['کارنامه‌ی سیگنال‌های بسته و اجراهای ثبت‌شده‌ی شما.', 'The record of your closed signals and executions.'], pitfall: ['هر عددی اینجا از حساب شما می‌آید؛ چیزی روی گوشی تخمین زده نمی‌شود.', 'Every figure here comes from your account; nothing is estimated on the phone.'] },
  academy: { key: 'academy', lead: ['درس‌های کوتاه درباره‌ی بازار و کار با همین برنامه.', 'Short lessons about the market and about using this app.'], pitfall: ['درس‌ها آموزش‌اند؛ هیچ‌کدام نمادی را برای خرید معرفی نمی‌کند.', 'The lessons teach; none of them names a symbol to buy.'] },
  dom: { key: 'dom', lead: ['سفارش‌های خرید و فروشی که همین حالا در دفتر صرافی منتظرند.', 'The buy and sell orders waiting in the exchange’s book right now.'], pitfall: ['این سفارش‌ها هنوز انجام نشده‌اند و هر لحظه می‌توانند برداشته شوند.', 'These orders have not been filled and can be pulled at any moment.'] },
  search: { key: 'search', lead: ['نماد یا صفحه‌ای از برنامه را با نامش پیدا می‌کنی.', 'You find a symbol or a screen of the app by its name.'], pitfall: ['نام فارسی هم کار می‌کند؛ لازم نیست نماد لاتین را بدانی.', 'Persian names work too; you do not have to know the Latin ticker.'] },
};

/**
 * The 22 pages that carry a card, and which surface each one teaches. The AI's three faces share
 * one explanation, and a single signal shares the signals' (one dismissal puts both away).
 */
export const PAGE_TEACHING: Partial<Record<PageId, TeachingKey>> = {
  home: 'home', markets: 'markets', signals: 'signals', signal: 'signals', ai: 'ai', vision: 'ai', assistant: 'ai',
  tools: 'tools', news: 'news', calendar: 'calendar', screener: 'screener', heatmap: 'heatmap', alerts: 'alerts',
  journal: 'journal', paper: 'paper_trade', backtest: 'backtest', script: 'script', portfolio: 'portfolio',
  connections: 'connections', membership: 'membership', activity: 'activity', academy: 'academy',
};

export const teachingFor = (page: PageId | null): TeachingSurface | null => {
  const key = page ? PAGE_TEACHING[page] : undefined;
  return key ? TEACHING[key] : null;
};

/** The dismiss control's name, the header action's name. */
export const TEACHING_DISMISS: [string, string] = ['بستن این توضیح', 'Close this explanation'];
export const TEACHING_WHAT: [string, string] = ['این صفحه چیست؟', 'What is this screen?'];

// ── Which cards are put away ────────────────────────────────────────────────────────────────────
const KEY = 'pc.teaching.v1';
/** The shape `TeachingStore.usable` allows: short, lowercase letters, digits, underscore, hyphen and dot. */
export const usableKey = (k: string) => /^[a-z0-9_.-]{1,40}$/.test(k);
const read = (): string[] => {
  try { const v = JSON.parse(localStorage.getItem(KEY) ?? '[]') as unknown; return Array.isArray(v) ? v.filter((x): x is string => typeof x === 'string' && usableKey(x)) : []; } catch { return []; }
};

interface TeachingState { dismissed: string[]; dismiss(key: string): void; restore(key: string): void }
export const useTeaching = create<TeachingState>((set, get) => {
  const save = (dismissed: string[]) => { set({ dismissed }); try { localStorage.setItem(KEY, JSON.stringify(dismissed)); } catch { /* private window */ } };
  return {
    dismissed: read(),
    dismiss: (key) => { if (usableKey(key) && !get().dismissed.includes(key)) save([...get().dismissed, key]); },
    restore: (key) => { if (get().dismissed.includes(key)) save(get().dismissed.filter((x) => x !== key)); },
  };
});
