// The drawing tools on the left rail, in TradingView's groups, sections and order. Every id is one
// the drawing engine knows (src/engine/drawings.js built-ins, or EXT_REGISTRY in drawtools_ext.js);
// every icon is the TradingView glyph of the same name in the shared sprite (`tool_<id>`), or the
// Phosphor glyph named in `icon` for the tools TradingView added later.
export interface ToolDef {
  id: string; fa: string; en: string; key?: string;
  /** The icon when there is no `tool_<id>` glyph. */
  icon?: string;
  /** Starts a section of the group's menu (TradingView's LINES, CHANNELS, PITCHFORKS…). */
  sec?: [fa: string, en: string];
}
export interface ToolGroup { id: string; fa: string; en: string; tools: ToolDef[] }

/** The rail's cursors: TradingView's Cross, Dot, Arrow, Demonstration and Eraser. */
export const CURSOR_TOOLS: ToolDef[] = [
  { id: 'cursor', fa: 'صلیب', en: 'Cross', icon: 'crosshair' },
  { id: 'dot', fa: 'نقطه', en: 'Dot', icon: 'ph-dot-outline' },
  { id: 'arrowcursor', fa: 'پیکان', en: 'Arrow', icon: 'tool_cursor' },
  { id: 'demonstration', fa: 'نمایش ارائه', en: 'Demonstration', icon: 'ph-cursor-click' },
  { id: 'eraser', fa: 'پاک‌کن', en: 'Eraser', icon: 'ph-eraser' },
];

export const TOOL_GROUPS: ToolGroup[] = [
  { id: 'cursors', fa: 'نشانگرها', en: 'Cursors', tools: [
    { id: 'cursor', fa: 'صلیب', en: 'Cross', icon: 'crosshair' },
    { id: 'select', fa: 'انتخاب', en: 'Select' },
  ] },
  { id: 'lines', fa: 'ابزارهای روند', en: 'Trend line tools', tools: [
    { id: 'trend', fa: 'خط روند', en: 'Trend line', key: 'Alt+T', sec: ['خط‌ها', 'Lines'] },
    { id: 'ray', fa: 'پرتو', en: 'Ray' },
    { id: 'infoline', fa: 'خط اطلاعاتی', en: 'Info line' },
    { id: 'extline', fa: 'خط امتدادیافته', en: 'Extended line' },
    { id: 'angle', fa: 'زاویه‌ی روند', en: 'Trend angle' },
    { id: 'hline', fa: 'خط افقی', en: 'Horizontal line', key: 'Alt+H' },
    { id: 'hray', fa: 'پرتو افقی', en: 'Horizontal ray', key: 'Alt+J' },
    { id: 'vline', fa: 'خط عمودی', en: 'Vertical line', key: 'Alt+V' },
    { id: 'crossline', fa: 'خط صلیبی', en: 'Cross line', key: 'Alt+C' },
    { id: 'channel', fa: 'کانال موازی', en: 'Parallel channel', sec: ['کانال‌ها', 'Channels'] },
    { id: 'regchannel', fa: 'کانال رگرسیون', en: 'Regression trend' },
    { id: 'flatchannel', fa: 'کانال سقف و کف صاف', en: 'Flat top/bottom' },
    { id: 'disjointchannel', fa: 'کانال ناپیوسته', en: 'Disjoint channel' },
    { id: 'pitchfork', fa: 'چنگال اندروز', en: 'Pitchfork', sec: ['چنگال‌ها', 'Pitchforks'] },
    { id: 'schiff', fa: 'چنگال شیف', en: 'Schiff pitchfork' },
    { id: 'modschiff', fa: 'چنگال شیف اصلاح‌شده', en: 'Modified Schiff pitchfork' },
    { id: 'insidepitchfork', fa: 'چنگال داخلی', en: 'Inside pitchfork' },
  ] },
  { id: 'fib', fa: 'فیبوناچی و گان', en: 'Gann and Fibonacci tools', tools: [
    { id: 'fib', fa: 'اصلاحی فیبوناچی', en: 'Fib retracement', key: 'Alt+F', sec: ['فیبوناچی', 'Fibonacci'] },
    { id: 'fib3', fa: 'گسترش فیبوناچی روندی', en: 'Trend-based fib extension' },
    { id: 'fibext', fa: 'گسترش فیبوناچی', en: 'Fib extension' },
    { id: 'fibchannel', fa: 'کانال فیبوناچی', en: 'Fib channel' },
    { id: 'fibtime', fa: 'منطقه‌ی زمانی فیبوناچی', en: 'Fib time zone' },
    { id: 'fibfan', fa: 'بادبزن فیبوناچی', en: 'Fib speed resistance fan' },
    { id: 'fibtimeext', fa: 'زمان فیبوناچی روندی', en: 'Trend-based fib time' },
    { id: 'fibcircles', fa: 'دایره‌های فیبوناچی', en: 'Fib circles' },
    { id: 'fibspiral', fa: 'مارپیچ فیبوناچی', en: 'Fib spiral' },
    { id: 'fibarcs', fa: 'کمان‌های فیبوناچی', en: 'Fib speed resistance arcs' },
    { id: 'fibwedge', fa: 'گوه‌ی فیبوناچی', en: 'Fib wedge' },
    { id: 'pitchfan', fa: 'پیچ‌فن', en: 'Pitchfan' },
    { id: 'gannbox', fa: 'جعبه‌ی گان', en: 'Gann box', sec: ['گان', 'Gann'] },
    { id: 'gannfixed', fa: 'مربع ثابت گان', en: 'Gann square fixed' },
    { id: 'gannsquare', fa: 'مربع گان', en: 'Gann square' },
    { id: 'gannfan', fa: 'بادبزن گان', en: 'Gann fan' },
  ] },
  { id: 'patterns', fa: 'الگوها', en: 'Patterns', tools: [
    { id: 'xabcd', fa: 'الگوی XABCD', en: 'XABCD pattern', sec: ['الگوهای چارت', 'Chart patterns'] },
    { id: 'cypher', fa: 'الگوی سایفر', en: 'Cypher pattern' },
    { id: 'hns', fa: 'سر و شانه', en: 'Head and shoulders' },
    { id: 'abcd', fa: 'الگوی ABCD', en: 'ABCD pattern' },
    { id: 'tripattern', fa: 'الگوی مثلث', en: 'Triangle pattern' },
    { id: 'threedrives', fa: 'الگوی سه حرکت', en: 'Three drives pattern' },
    { id: 'ell_impulse', fa: 'موج ایمپالس الیوت (1·2·3·4·5)', en: 'Elliott impulse wave (1·2·3·4·5)', sec: ['امواج الیوت', 'Elliott waves'] },
    { id: 'ell_abc', fa: 'موج اصلاحی الیوت (A·B·C)', en: 'Elliott correction wave (A·B·C)' },
    { id: 'ell_triangle', fa: 'مثلث الیوت (A·B·C·D·E)', en: 'Elliott triangle wave (A·B·C·D·E)' },
    { id: 'ell_wxy', fa: 'ترکیب دوگانه‌ی الیوت (W·X·Y)', en: 'Elliott double combo wave (W·X·Y)' },
    { id: 'ell_wxyxz', fa: 'ترکیب سه‌گانه‌ی الیوت (W·X·Y·X·Z)', en: 'Elliott triple combo wave (W·X·Y·X·Z)' },
    { id: 'cyclic', fa: 'خط‌های دوره‌ای', en: 'Cyclic lines', sec: ['چرخه‌ها', 'Cycles'] },
    { id: 'timecycles', fa: 'چرخه‌های زمانی', en: 'Time cycles' },
    { id: 'sine', fa: 'خط سینوسی', en: 'Sine line' },
  ] },
  { id: 'forecast', fa: 'پیش‌بینی و اندازه‌گیری', en: 'Forecasting and measurement tools', tools: [
    { id: 'longshort', fa: 'موقعیت خرید', en: 'Long position', sec: ['پیش‌بینی', 'Forecasting'] },
    { id: 'short', fa: 'موقعیت فروش', en: 'Short position' },
    { id: 'forecast', fa: 'پیش‌بینی موقعیت', en: 'Position forecast' },
    { id: 'barspattern', fa: 'الگوی کندل‌ها', en: 'Bars pattern', icon: 'ph-chart-bar' },
    { id: 'ghostfeed', fa: 'خوراک شبح', en: 'Ghost feed', icon: 'ph-ghost' },
    { id: 'projection', fa: 'پروجکشن', en: 'Projection' },
    { id: 'avwap', fa: 'VWAP لنگرشده', en: 'Anchored VWAP', icon: 'ph-anchor-simple', sec: ['بر پایه‌ی حجم', 'Volume-based'] },
    { id: 'fixedvp', fa: 'پروفایل حجم بازه‌ی ثابت', en: 'Fixed range volume profile', icon: 'ph-chart-bar-horizontal' },
    { id: 'anchoredvp', fa: 'پروفایل حجم لنگرشده', en: 'Anchored volume profile', icon: 'ph-align-left' },
    { id: 'pricerange', fa: 'بازه‌ی قیمت', en: 'Price range', sec: ['اندازه‌گیری', 'Measurers'] },
    { id: 'daterange', fa: 'بازه‌ی زمان', en: 'Date range' },
    { id: 'dprange', fa: 'بازه‌ی قیمت و زمان', en: 'Date and price range' },
  ] },
  { id: 'shapes', fa: 'شکل‌ها', en: 'Geometric shapes', tools: [
    { id: 'brush', fa: 'قلم‌مو', en: 'Brush', sec: ['قلم‌موها', 'Brushes'] },
    { id: 'highlighter', fa: 'هایلایتر', en: 'Highlighter' },
    { id: 'arrowdir', fa: 'نشانگر پیکان', en: 'Arrow marker', sec: ['پیکان‌ها', 'Arrows'] },
    { id: 'arrow', fa: 'پیکان', en: 'Arrow' },
    { id: 'arrowup', fa: 'فلش بالا', en: 'Arrow mark up' },
    { id: 'arrowdown', fa: 'فلش پایین', en: 'Arrow mark down' },
    { id: 'rect', fa: 'مستطیل', en: 'Rectangle', key: 'Alt+Shift+R', sec: ['شکل‌ها', 'Shapes'] },
    { id: 'rotrect', fa: 'مستطیل چرخیده', en: 'Rotated rectangle' },
    { id: 'path', fa: 'مسیر', en: 'Path' },
    { id: 'circle', fa: 'دایره', en: 'Circle' },
    { id: 'ellipse', fa: 'بیضی', en: 'Ellipse' },
    { id: 'polyline', fa: 'خط چندتکه', en: 'Polyline' },
    { id: 'triangle', fa: 'مثلث', en: 'Triangle' },
    { id: 'arc', fa: 'کمان', en: 'Arc' },
    { id: 'curve', fa: 'منحنی', en: 'Curve' },
    { id: 'doublecurve', fa: 'منحنی دوگانه', en: 'Double curve' },
  ] },
  { id: 'annotations', fa: 'متن و برچسب', en: 'Annotation tools', tools: [
    { id: 'text', fa: 'متن', en: 'Text', key: 'Alt+X', sec: ['متن و یادداشت', 'Text and notes'] },
    { id: 'anchoredtext', fa: 'متن لنگرشده', en: 'Anchored text', icon: 'ph-textbox' },
    { id: 'note', fa: 'یادداشت', en: 'Note' },
    { id: 'pricenote', fa: 'یادداشت قیمت', en: 'Price note', icon: 'ph-tag-simple' },
    { id: 'pin', fa: 'سنجاق', en: 'Pin', icon: 'ph-push-pin' },
    { id: 'table', fa: 'جدول', en: 'Table', icon: 'ph-table' },
    { id: 'callout', fa: 'کال‌اوت', en: 'Callout' },
    { id: 'comment', fa: 'دیدگاه', en: 'Comment', icon: 'ph-chat-teardrop-text' },
    { id: 'pricelabel', fa: 'برچسب قیمت', en: 'Price label' },
    { id: 'signpost', fa: 'تابلو', en: 'Signpost' },
    { id: 'flag', fa: 'پرچم', en: 'Flag mark' },
  ] },
  { id: 'icons', fa: 'آیکون‌ها', en: 'Icons', tools: [
    { id: 'emoji', fa: 'آیکون', en: 'Icon', icon: 'ph-smiley' },
  ] },
];

/** The glyphs the Icons group offers (TradingView's emoji, stickers and icons, in short). */
export const EMOJIS = ['🙂', '😀', '😎', '🤔', '😱', '🥳', '🚀', '🔥', '💎', '💰', '📈', '📉', '🐂', '🐻', '⚠️', '✅', '❌', '⭐', '🎯', '⏰', '🔔', '📌', '💡', '👍', '👎', '👀', '🏁', '⚡', '🌙', '☀️'];

/** Freehand tools are drawn by dragging, not by clicking points (EXT_FREEHAND in the engine). */
export const FREEHAND_TOOLS = ['brush', 'highlighter'];

export const ALL_TOOLS: ToolDef[] = TOOL_GROUPS.flatMap((g) => g.tools);

/** The rail's other tools: TradingView's Measure and Zoom in. */
export const EXTRA_TOOLS: ToolDef[] = [
  { id: 'ruler', fa: 'اندازه‌گیری', en: 'Measure' },
  { id: 'zoom', fa: 'بزرگ‌نمایی', en: 'Zoom in', icon: 'ph-magnifying-glass-plus' },
];

const BY_ID = new Map<string, ToolDef>([...CURSOR_TOOLS, ...ALL_TOOLS, ...EXTRA_TOOLS].map((t) => [t.id, t]));

/** A tool's definition by id (drawing tools, cursors, measure and zoom). */
export const toolDef = (id: string): ToolDef | undefined => BY_ID.get(id);
/** A tool's icon name in the sprite. */
export const toolIcon = (id: string): string => BY_ID.get(id)?.icon ?? `tool_${id}`;
/** A tool's name in the reader's language. */
export const toolName = (id: string, lang: 'fa' | 'en'): string => { const t = BY_ID.get(id); return t ? (lang === 'fa' ? t.fa : t.en) : id; };
/** The group a tool belongs to on the rail. */
export const groupOf = (id: string): string | undefined => (CURSOR_TOOLS.some((t) => t.id === id) ? 'cursors' : TOOL_GROUPS.find((g) => g.tools.some((t) => t.id === id))?.id);
