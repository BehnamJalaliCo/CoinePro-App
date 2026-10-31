// The coach (OLD core/designsystem CoineProCoach.kt, 5.21.0): what a reader can do that nothing on
// the screen says — a wheel on the chart, a drag on the price column, a hold on a group's arrow —
// taught once, at the moment the reader first reaches it. The rules are written against fatigue:
//
// * Just in time: a tip is asked for by the control it is about, while that control is on screen
//   and its moment exists (a drawing selected, a study on the chart).
// * One at a time, three a launch on a touch screen and one a visit with a mouse (5.33.0: a desktop
//   page is reloaded far more often than an app is opened), COOLDOWN_MS apart — except an urgent
//   tip the reader needs this second (the drawing cursor the moment a tool is armed).
// * Once, for good: put away the moment it is shown.
// * A way out of all of it: «بلدم، دیگر نشان داده نشود».
//
// The list is the Kotlin one in its order, less the tips about gestures the web app does not have
// (double tap to the latest candle, the phone's symbol wheel and bottom band, swipe to star, hold a
// row for a preview, swipe a drawing away, hold a timeframe to hide it) and the store build's own.
// Where the web's control differs from the phone's, the sentence says what the web does.
import type { SceneId } from './films';

export type CoachGroup = 'chart' | 'drawing' | 'lists';
export interface Bi { fa: string; en: string }

export interface CoachTip {
  /** Stable id, part of the persisted key. */
  id: string;
  group: CoachGroup;
  /** The film, or null for a tip that is one lit control and a sentence. */
  scene: SceneId | null;
  title: Bi;
  text: Bi;
  /** The sentence for a mouse, where the gesture differs. */
  mouseText?: Bi;
  /** The heading for a mouse, where the touch one names a gesture a mouse does not make. */
  mouseTitle?: Bi;
  /** A gesture a mouse does not have; never shown to a mouse. */
  touchOnly?: boolean;
  /** Needed this second: bypasses the cooldown and the cap. */
  urgent?: boolean;
}

export const COACH_TIPS: CoachTip[] = [
  {
    id: 'draw_cursor', group: 'drawing', scene: 'trackpad', touchOnly: true, urgent: true,
    title: { fa: 'نشانگر آبی ترسیم', en: 'The drawing cursor' },
    text: { fa: 'انگشت را هر جای صفحه بکشید تا نشانگر آبی حرکت کند. با یک ضربه، نقطه همان‌جا ثبت می‌شود.', en: 'Drag anywhere to move the blue cursor, then tap to set the point exactly under it.' },
  },
  {
    id: 'draw_toolbar', group: 'drawing', scene: 'toolbar',
    title: { fa: 'نوار ابزار شناور', en: 'The floating toolbar' },
    text: { fa: 'شش نقطه را بکشید تا نوار جابه‌جا شود و با یک ضربه جمعش کنید. روی هر ابزار نگه دارید تا ابزارهای هم‌گروهش باز شوند.', en: 'Drag the six dots to move the toolbar and tap them to fold it. Hold a tool to see the rest of its group.' },
    // On the desktop page the drawing tools are the rail at the left edge, whose groups open from the
    // small arrow at each tool's corner.
    mouseTitle: { fa: 'نوار ابزار ترسیم', en: 'The drawing toolbar' },
    mouseText: { fa: 'نشانگر را روی فلش کوچک گوشه‌ی هر ابزار نگه دارید یا رویش کلیک کنید تا ابزارهای هم‌گروهش باز شوند.', en: 'Rest the pointer on the small arrow at a tool\'s corner, or click it, to see the rest of its group.' },
  },
  {
    id: 'chart_pinch', group: 'chart', scene: 'pinch',
    title: { fa: 'زوم با دو انگشت', en: 'Pinch to zoom' },
    text: { fa: 'دو انگشت را باز و بسته کنید تا چارت بزرگ و کوچک شود.', en: 'Spread or pinch two fingers to zoom the chart.' },
    mouseTitle: { fa: 'زوم با چرخ موس', en: 'Zoom with the wheel' },
    mouseText: { fa: 'با چرخ موس زمان را زوم کنید؛ برای زوم قیمت، ستون قیمت را بالا و پایین بکشید.', en: 'Scroll the wheel to zoom time; to zoom price, drag the price column up or down.' },
  },
  {
    id: 'chart_axis', group: 'chart', scene: 'axis',
    title: { fa: 'کشیدن ستون قیمت', en: 'Stretch the price scale' },
    text: { fa: 'ستون قیمت را بالا و پایین بکشید تا چارت کشیده یا فشرده شود. دو ضربه روی آن، اندازه را خودکار می‌کند.', en: 'Drag the price column up or down to stretch or squeeze the chart. Double-tap it to fit again.' },
    mouseText: { fa: 'ستون قیمت را با موس بالا و پایین بکشید. دوبار کلیک روی آن، اندازه را خودکار می‌کند.', en: 'Drag the price column up or down with the mouse. Double-click it to fit again.' },
  },
  {
    id: 'chart_hold', group: 'chart', scene: 'hold',
    title: { fa: 'نگه‌داشتن روی چارت', en: 'Press and hold' },
    text: { fa: 'انگشت را روی چارت نگه دارید تا نشانگر بماند و قیمت و زمان هر نقطه را بخوانید.', en: 'Press and hold on the chart to keep the crosshair and read the price and time of any point.' },
    mouseTitle: { fa: 'منوی کلیک راست', en: 'The right-click menu' },
    mouseText: { fa: 'روی چارت کلیک راست کنید تا منوی همان قیمت باز شود.', en: 'Right-click the chart to open the menu for that price.' },
  },
  {
    id: 'draw_band', group: 'drawing', scene: null,
    title: { fa: 'نوار حالت ترسیم', en: 'Drawing mode bar' },
    text: { fa: 'آهن‌ربا نقطه‌ها را به قیمت شمع می‌چسباند؛ قفل و چشم، همه‌ی ترسیم‌ها را با هم قفل یا پنهان می‌کنند.', en: 'The magnet snaps points to candle prices; the lock and the eye lock or hide every drawing at once.' },
  },
  {
    id: 'draw_handle', group: 'drawing', scene: 'handle',
    title: { fa: 'جابه‌جا کردن ترسیم', en: 'Move a drawing' },
    text: { fa: 'نقطه‌های ترسیم را بکشید تا شکلش عوض شود، یا وسط آن را بکشید تا کل ترسیم جابه‌جا شود.', en: 'Drag a drawing\'s points to reshape it, or drag its middle to move all of it.' },
  },
  {
    id: 'select_bar', group: 'drawing', scene: null,
    title: { fa: 'نوار ترسیم انتخاب‌شده', en: 'The drawing\'s toolbar' },
    text: { fa: 'رنگ، ضخامت، قفل، کپی و حذف ترسیم انتخاب‌شده همه روی همین نوار است.', en: 'Colour, width, lock, copy and delete for the selected drawing are all on this bar.' },
  },
  {
    id: 'gutter_plus', group: 'chart', scene: 'plus',
    title: { fa: 'هشدار از کنار قیمت', en: 'Alert from the price scale' },
    text: { fa: 'روی «+» کنار ستون قیمت بزنید تا برای همان قیمت هشدار بسازید.', en: 'Tap the + beside the price column to set an alert at that price.' },
    mouseText: { fa: 'موس را کنار ستون قیمت ببرید و روی «+» کلیک کنید تا برای همان قیمت هشدار بسازید.', en: 'Move the mouse beside the price column and click the + to set an alert at that price.' },
  },
  {
    id: 'alert_line', group: 'chart', scene: 'alertLine',
    title: { fa: 'جابه‌جا کردن هشدار', en: 'Move an alert' },
    text: { fa: 'خط هشدار را بالا و پایین بکشید تا قیمت هشدار عوض شود.', en: 'Drag the alert line up or down to change its price.' },
  },
  {
    // The phone's A and L keys are the time bar's «auto» and «log» on the web.
    id: 'axis_minis', group: 'chart', scene: null,
    title: { fa: 'دکمه‌های auto و log', en: 'The auto and log keys' },
    text: { fa: 'دکمه‌ی auto اندازه‌ی قیمت را خودکار می‌کند و دکمه‌ی log محور را لگاریتمی.', en: 'auto fits the price scale automatically; log makes it logarithmic.' },
  },
  {
    id: 'legend', group: 'chart', scene: null,
    title: { fa: 'نام اندیکاتورها', en: 'Indicator names' },
    text: { fa: 'روی نام هر اندیکاتور بزنید تا توضیحش را ببینید و از همان‌جا پنهان یا تنظیمش کنید.', en: 'Tap an indicator\'s name to read about it, hide it or change its settings.' },
    mouseText: { fa: 'نشانگر را روی نام هر اندیکاتور ببرید تا دکمه‌های پنهان کردن و تنظیم بیایند؛ دوبار کلیک روی نام، تنظیماتش را باز می‌کند.', en: 'Point at an indicator\'s name for its hide and settings buttons; double-click the name to open its settings.' },
  },
  {
    id: 'chart_events', group: 'chart', scene: null,
    title: { fa: 'علامت‌های روی چارت', en: 'Marks on the chart' },
    text: { fa: 'علامت‌های پایین چارت رویدادها و نشانه‌های چارت هستند. رویشان بزنید تا جزئیات را ببینید.', en: 'The marks along the bottom of the chart are events and chart cues. Tap one for the details.' },
    mouseText: { fa: 'علامت‌های پایین چارت رویدادها و نشانه‌های چارت هستند. رویشان کلیک کنید تا جزئیات را ببینید.', en: 'The marks along the bottom of the chart are events and chart cues. Click one for the details.' },
  },
  {
    id: 'realtime', group: 'chart', scene: null,
    title: { fa: 'رفتن به آخرین شمع', en: 'Jump to now' },
    text: { fa: 'با این دکمه به آخرین شمع برمی‌گردید.', en: 'This button takes you back to the latest candle.' },
  },
  {
    id: 'replay_bar', group: 'chart', scene: null,
    title: { fa: 'ریپلی', en: 'Replay' },
    text: { fa: 'شمع‌ها را یکی‌یکی جلو ببرید و ببینید بازار بعدش چه کرد. با دکمه‌ی خروج به چارت زنده برمی‌گردید.', en: 'Step the candles forward one by one and see what the market did next. Exit returns you to the live chart.' },
  },
  {
    // The web's watchlist rows are dragged whole; there is no separate handle.
    id: 'row_reorder', group: 'lists', scene: 'reorder',
    title: { fa: 'چیدن دیده‌بان', en: 'Arrange your watchlist' },
    text: { fa: 'ردیف هر نماد را بگیرید و بالا و پایین بکشید تا ترتیب دیده‌بان عوض شود.', en: 'Drag a row up or down to change the order of your watchlist.' },
  },
];

export const COACH_GROUPS: { id: CoachGroup; title: Bi }[] = [
  { id: 'chart', title: { fa: 'چارت', en: 'Chart' } },
  { id: 'drawing', title: { fa: 'ترسیم', en: 'Drawing' } },
  { id: 'lists', title: { fa: 'فهرست‌ها', en: 'Lists' } },
];

export const tipById = (id: string | null | undefined): CoachTip | null => COACH_TIPS.find((t) => t.id === id) ?? null;

/** At most this many tips a launch on a touch screen, the urgent ones aside. */
export const SESSION_CAP = 3;
/** A mouse gets one tip a visit (5.33.0). */
export const MOUSE_SESSION_CAP = 1;
/** The quiet after a tip before the next may come. */
export const COOLDOWN_MS = 25_000;
/** How long a control may be missing before its tip is put away. */
export const LOST_TARGET_MS = 900;
/** How long a control must stay on screen before its tip comes. */
export const SETTLE_DELAY_MS = 1_400;
export const URGENT_DELAY_MS = 450;
export const AUTO_CLOSE_SCENE_MS = 11_000;
export const AUTO_CLOSE_LINE_MS = 7_000;
export const MIN_TARGET_PX = 8;

export interface Box { left: number; top: number; width: number; height: number }

export interface PickInput {
  /** Every tip whose control is on screen and whose moment exists, with that control's bounds. */
  requests: Map<string, Box>;
  dismissed: ReadonlySet<string>;
  off: boolean;
  touch: boolean;
  shown: number;
  cooling: boolean;
  windowHeight: number;
  windowWidth?: number;
  /** Something covers the app (a dialog, a page, a menu, the first run). */
  blocked: boolean;
}

/** The next tip to show, in the list's order, or null. */
export function pickTip(i: PickInput): CoachTip | null {
  if (i.off || i.blocked) return null;
  const cap = i.touch ? SESSION_CAP : MOUSE_SESSION_CAP;
  for (const tip of COACH_TIPS) {
    const b = i.requests.get(tip.id);
    if (!b) continue;
    if (i.dismissed.has(tip.id)) continue;
    if (tip.touchOnly && !i.touch) continue;
    if (b.width < MIN_TARGET_PX || b.height < MIN_TARGET_PX) continue;
    if (b.top + b.height <= 0 || b.top >= i.windowHeight) continue;
    if (i.windowWidth !== undefined && (b.left + b.width <= 0 || b.left >= i.windowWidth)) continue;
    if (!tip.urgent && (i.cooling || i.shown >= cap)) continue;
    return tip;
  }
  return null;
}

export const tipTitle = (tip: CoachTip, touch: boolean): Bi => (!touch && tip.mouseTitle ? tip.mouseTitle : tip.title);
export const tipText = (tip: CoachTip, touch: boolean): Bi => (!touch && tip.mouseText ? tip.mouseText : tip.text);

/**
 * Where the card goes in the full (dimmed) spotlight: under the light when there is room, over it
 * when not, over the foot of a control that fills the glass, in the middle for a tip played from the
 * library (OLD CoachSpotlight).
 */
export function cardTop(hole: Box | null, cardHeight: number, windowHeight: number, gap = 16, foot = 96): number {
  let top: number;
  if (!hole) top = (windowHeight - cardHeight) / 2;
  else if (windowHeight - (hole.top + hole.height) - gap >= cardHeight) top = hole.top + hole.height + gap;
  else if (hole.top - gap >= cardHeight) top = hole.top - gap - cardHeight;
  else top = Math.max(gap, windowHeight - cardHeight - foot);
  return Math.min(Math.max(top, gap), Math.max(gap, windowHeight - cardHeight - gap));
}
