// End-to-end suite for the built web app (dist/), in a real Chromium.
//
//   node scripts/e2e/run.mjs            backends mocked from fixtures (deterministic, offline)
//   E2E_UPSTREAM=https://pro-chart.com node scripts/e2e/run.mjs   the live relay
//
// The page is served from dist at http://pc.local/next/…; /up/* and /api/* are answered by the
// fixtures or fetched from the upstream through the browser context. Every test fails on any
// uncaught page error. Playwright is a dev dependency; CI installs its Chromium first.
import { createRequire } from 'node:module';
import { existsSync, readFileSync, readdirSync, statSync, mkdirSync } from 'node:fs';
import { dirname, extname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
const here = dirname(fileURLToPath(import.meta.url));
const DIST = join(here, '../../dist');
const SHOTS = process.env.E2E_SHOTS ?? join(here, '../../.e2e-shots');
const UPSTREAM = process.env.E2E_UPSTREAM ?? '';
const ONLY = process.env.E2E_ONLY ?? '';
const { chromium } = require(process.env.PLAYWRIGHT_MODULE ?? 'playwright');

if (!existsSync(join(DIST, 'index.html'))) { console.error('dist/ is missing: run npm run build first'); process.exit(2); }
mkdirSync(SHOTS, { recursive: true });

// ── Fixtures ──────────────────────────────────────────────────────────────────────────────────────
const NOW = Math.floor(Date.now() / 1000);
function series(seed, base, step, n) {
  let p = base, x = seed;
  const rnd = () => { x = (x * 9301 + 49297) % 233280; return x / 233280; };
  const out = [];
  const start = Math.floor(NOW / step) * step - (n - 1) * step;
  for (let i = 0; i < n; i++) {
    const o = p; const c = o * (1 + (rnd() - 0.5) * 0.02);
    out.push({ t: start + i * step, o, h: Math.max(o, c) * (1 + rnd() * 0.005), l: Math.min(o, c) * (1 - rnd() * 0.005), c, v: 100 + rnd() * 900, closed: i < n - 1 });
    p = c;
  }
  return out;
}
const TF = { M1: 60, M5: 300, M15: 900, M30: 1800, H1: 3600, H4: 14400, D1: 86400, W1: 604800 };
const tfOf = (q) => ({ '1m': 'M1', '5m': 'M5', '15m': 'M15', '30m': 'M30', '1h': 'H1', '4h': 'H4', '1d': 'D1', '1w': 'W1' }[q?.toLowerCase()] ?? q?.toUpperCase());
const CRYPTO = ['BTCUSDT', 'ETHUSDT', 'SOLUSDT', 'BNBUSDT', 'XRPUSDT', 'ADAUSDT', 'DOGEUSDT', 'TRXUSDT', 'LTCUSDT', 'SUIUSDT'];
const PRICE = { BTCUSDT: 81000, ETHUSDT: 2400, SOLUSDT: 103, BNBUSDT: 720, XRPUSDT: 1.31, ADAUSDT: 0.22, DOGEUSDT: 0.08, TRXUSDT: 0.3, LTCUSDT: 90, SUIUSDT: 1.0, XAUUSD: 4140, XAGUSD: 56, EURUSD: 1.12, GBPUSD: 1.32, USDJPY: 157.7, AUDUSD: 0.695, USDCHF: 0.83 };
const FX = ['XAUUSD', 'XAGUSD', 'EURUSD', 'GBPUSD', 'USDJPY', 'AUDUSD', 'USDCHF'];
const json = (body, status = 200) => ({ status, contentType: 'application/json', body: JSON.stringify(body) });

let r_method = 'GET';
function mock(url, method = 'GET') {
  r_method = method;
  const u = new URL(url);
  const p = u.pathname;
  const q = u.searchParams;
  if (p === '/api/crypto/prices') return json({ ts: Date.now(), data: CRYPTO.map((s) => ({ s, p: PRICE[s] * (1 + (Math.random() - 0.5) * 0.001), t: NOW, c: -2.1, h: PRICE[s] * 1.03, l: PRICE[s] * 0.97, v: 123456 })) });
  if (p === '/api/crypto/candles') { const tf = tfOf(q.get('tf')); return json({ symbol: q.get('symbol'), tf, candles: series(q.get('symbol').length * 7, PRICE[q.get('symbol')] ?? 10, TF[tf] ?? 3600, Math.min(500, Number(q.get('limit') ?? 500))) }); }
  if (p === '/up/coineprofx/api/user/auth/guest') return json({ token: 'pch_e2eguest', expires_in: 7200, scope: 'guest' });
  const chart = /^\/up\/coineprofx\/api\/academy\/chart\/([A-Z0-9]+)$/.exec(p);
  if (chart) { const tf = q.get('tf'); return json({ symbol: chart[1], timeframe: tf, candles: series(chart[1].length * 11, PRICE[chart[1]] ?? 1, TF[tf] ?? 3600, Math.min(1500, Number(q.get('limit') ?? 1500))).map(({ closed, ...b }) => b) }); }
  if (p === '/up/coineprofx/api/ws/snapshot') return json({ prices: Object.fromEntries((q.get('symbols') ?? '').split(',').filter(Boolean).map((s) => [s, { symbol: s, price: PRICE[s] ?? 1, ts: Date.now() }])) });
  if (p === '/up/coineprofx/api/user/mobile/market/tickers') return json({ tickers: FX.map((s) => ({ symbol: s, last: PRICE[s], open_24h: PRICE[s] * 1.004, high_24h: PRICE[s] * 1.01, low_24h: PRICE[s] * 0.99, change_percent_24h: -0.4, volume_24h: 1000, ts: Date.now() })), cache_ttl_ms: 5000 });
  if (p === '/api/news') return json({ data: [{ id: 1, slug: 'e2e-story', source: 'e2e', titleFa: 'خبر آزمایشی بیت‌کوین', summaryFa: 'خلاصه‌ی آزمایشی', publishedAt: new Date().toISOString(), importance: 8 }] });
  if (p === '/up/tradeyar/api/v1/news/e2e-story') return json({ data: { bodyFa: 'متن کامل خبر آزمایشی.' } });
  if (p === '/up/coineprofx/api/academy/bn/news') return json({ items: [{ id: '9', title: 'خبر آزمایشی طلا', summary: 'خلاصه', published_at: new Date().toISOString(), source: 'e2e' }] });
  if (p === '/up/tradeyar/api/v1/public/calendar/week') return json([{ title: 'CPI m/m', country: 'USD', date: new Date(Date.now() + 3600e3).toISOString(), impact: 'High', forecast: '0.3%', previous: '0.2%' }]);
  if (p === '/api/track-record') return json({ signals: [{ id: 1, symbol: 'BTCUSDT', timeframe: '1h', direction: 'LONG', entry_price: 80000, sl_price: 79000, tp1_price: 82000, is_win: true, pct_gain: 5, risk_reward: 2, created_at: new Date(Date.now() - 86400e3).toISOString(), closed_at: new Date().toISOString() }] });
  if (p === '/up/tradeyar/api/mobile/v1/auth/methods') return json({ email_password: true, google: true, google_client_id: 'e2e.apps.googleusercontent.com' });
  if (p === '/up/tradeyar/api/mobile/v1/auth/login') return json({ detail: 'ایمیل یا رمز عبور درست نیست.' }, 401);
  if (p === '/up/coineprofx/api/user/auth/login') return json({ detail: { code: 'bad', message: 'ایمیل یا رمز عبور درست نیست.' } }, 401);
  if (p === '/up/tradeyar/api/mobile/v1/payments/plans') return json({ plans: [{ id: 'monthly', days: 30, price_toman: 1, price_usdt: '15' }], usdt: { network: 'BEP20', wallet: '0x0000000000000000000000000000000000000000' } });

  // ── Member routes (a seeded session, handle pch_e2e) ──
  const TYM = '/up/tradeyar/api/mobile/v1/';
  const FXM = '/up/coineprofx/api/user/mobile/';
  const iso = (d = 0) => new Date(Date.now() + d).toISOString();
  const signal = (id, symbol, status = 'active') => ({ id, symbol, direction: 'BUY', timeframe: '1h', status, confidence: 0.82, entry: PRICE[symbol], entry_zone: { low: PRICE[symbol] * 0.998, high: PRICE[symbol] * 1.002 }, stop_loss: PRICE[symbol] * 0.98, targets: [{ level: 1, price: PRICE[symbol] * 1.02, hit: status !== 'active' }, { level: 2, price: PRICE[symbol] * 1.04, hit: false }], risk_reward_tp1: 1.5, rationale: 'شکست مقاومت با حجم بالا.', score_breakdown: { technical: 0.8, pattern: 0.7, ml: 0.75 }, live_pnl_percent: status === 'active' ? 0.6 : 2.1, created_at: iso(-7200e3), closed_at: status === 'active' ? null : iso() });
  for (const pre of [TYM, FXM]) {
    if (p === `${pre}briefing`) return json({ body: '## خلاصه‌ی امروز\n\n* بیت‌کوین بالای **80,000** مانده است.\n* داده‌ی تورم آمریکا امروز منتشر می‌شود.', generated_at: Date.now() });
    if (p === `${pre}portfolio`) return json({ total: { amount: 12345.67, currency: 'USDT' }, change: { amount: 120.5, percent: 0.98, period: '24h' }, holdings: [{ symbol: 'BTCUSDT', display_name: 'Bitcoin', quantity: 0.1, quantity_unit: 'BTC', value: 8100, change_percent: 1.2 }], as_of: Date.now() });
    if (p === `${pre}kyc`) return json({ level: 0, status: 'not_started', required_fields: ['full_name'] });
    if (p === `${pre}kyc/level1`) return json({ level: 1, status: 'pending', submitted_at: Date.now() });
    if (p === `${pre}notifications`) return json({ items: [{ kind: 'signal', title: 'سیگنال تازه BTCUSDT', body: 'خرید در 81000', ts: NOW - 600, read: false, data: { signal_id: '11' } }], unread: 1, has_more: false });
    if (p === `${pre}notifications/read`) return json({ ok: true, marked: 1 });
    if (p === `${pre}push/preferences`) return json({ preferences: { new_signals: true, signal_updates: true, price_alerts: true, announcements: true } });
    if (p === `${pre}alerts`) return r_method === 'POST' ? json({ alert: { id: 'a2', symbol: 'BTCUSDT', condition: 'above', value: 90000, trigger: 'once', active: true } }) : json({ items: [{ id: 'a1', symbol: pre === TYM ? 'BTCUSDT' : 'XAUUSD', condition: 'cross_up', value: pre === TYM ? 90000 : 4200, trigger: 'once', active: true, created_at_ms: Date.now() - 1000, channels: ['push'] }] });
    if (p.startsWith(`${pre}alerts/`)) return json({ alert: { id: 'a1', symbol: 'BTCUSDT', condition: 'above', value: 90000, trigger: 'once', active: false }, removed: true });
    if (p === `${pre}account`) return json({ ok: true });
  }
  if (p === `${TYM}market/candles`) { const tf = tfOf(q.get('tf')); return json({ symbol: q.get('symbol'), tf, has_more: false, candles: series(q.get('symbol').length * 7, PRICE[q.get('symbol')] ?? 10, TF[tf] ?? 3600, Math.min(1000, Number(q.get('limit') ?? 500))) }); }
  if (p === `${TYM}signals`) return json({ items: [signal(11, 'BTCUSDT', q.get('status') === 'active' ? 'active' : 'closed'), signal(12, 'ETHUSDT', q.get('status') === 'active' ? 'active' : 'closed')], total: 2 });
  if (/^\/up\/tradeyar\/api\/mobile\/v1\/signals\/\d+$/.test(p)) return json({ signal: signal(Number(p.split('/').pop()), 'BTCUSDT') });
  if (p === '/up/coineprofx/api/public/signals/active' || p === '/up/coineprofx/api/public/signals/recent') return json({ items: [signal(21, 'XAUUSD', p.endsWith('active') ? 'active' : 'closed')] });
  if (p.startsWith('/up/coineprofx/api/public/signals/detail/')) return json({ signal: signal(21, 'XAUUSD') });
  if (p === `${TYM}executions`) return r_method === 'POST' ? json({ execution: { id: 'e1', signal_id: 11, venue: 'lbank', status: 'queued' } }) : json({ items: [{ id: 'e1', signal_id: 11, venue: 'lbank', product: 'futures', status: 'open', side: 'buy', quantity: '0.01', signal: { symbol: 'BTCUSDT', direction: 'BUY', timeframe: '1h' }, created_at: iso(-3600e3) }] });
  if (p === `${TYM}venues/lbank`) return json({ configured: true, connected: true, status: 'ok', permission: 'futures', key_hint: 'abcd…wxyz' });
  if (p === `${TYM}trade/account`) return json({ available: 1000, equity: 1050, margin_used: 50, unrealized_pnl: 5, trading_enabled: true });
  if (p === `${TYM}trade/positions`) return json({ items: [{ symbol: 'BTCUSDT', side: 'long', quantity: 0.01, entry_price: 80000, mark_price: 81000, unrealized_pnl: 10, leverage: 5, liquidation_price: 65000 }] });
  if (p === `${TYM}trade/orders`) return r_method === 'POST' ? json({ order: { id: 5, status: 'submitted' } }) : json({ items: [{ id: 4, symbol: 'ETHUSDT', side: 'buy', type: 'limit', quantity: 0.5, price: 2300, status: 'open', can_cancel: true, can_amend: true, protection: 'none' }] });
  // Trading from the chart and the VIP channel (suites/trade.mjs).
  if (/^\/up\/tradeyar\/api\/mobile\/v1\/trade\/orders\/\d+$/.test(p)) { const del = r_method === 'DELETE'; return json({ order: { id: Number(p.split('/').pop()), symbol: 'BTCUSDT', side: 'buy', type: 'limit', quantity: 0.01, price: 80000, status: del ? 'cancelled' : 'open', can_cancel: !del, can_amend: !del, protection: 'none' } }); }
  if (p === `${TYM}trade/positions/protection`) return json({ position: { symbol: 'BTCUSDT', side: 'long', quantity: 0.01, entry_price: 80000 } });
  if (p === `${TYM}trade/positions/close`) return json({ order: { id: 6, symbol: 'BTCUSDT', side: 'sell', type: 'market', quantity: 0.01, status: 'filled', reduce_only: true, can_cancel: false, can_amend: false } });
  if (p === `${TYM}trade/leverage`) return json({ symbol: 'BTCUSDT', leverage: 10 });
  if (/^\/up\/tradeyar\/api\/mobile\/v1\/executions\/[\w-]+\/close$/.test(p)) return json({ execution: { id: 'e1', signal_id: 11, venue: 'lbank', status: 'closed', can_request_close: false } });
  if (p === `${TYM}market/depth` || p === '/up/tradeyar/api/v1/public/market/depth') { const m = PRICE[q.get('symbol')] ?? 100; return json({ symbol: q.get('symbol'), bids: Array.from({ length: 20 }, (_, i) => [m * (1 - (i + 1) * 0.0005), 1 + i * 0.3, 3]), asks: Array.from({ length: 20 }, (_, i) => [m * (1 + (i + 1) * 0.0005), 1 + i * 0.2, 2]) }); }
  if (p === `${TYM}portfolio/history`) return json({ trades: [{ id: 1, symbol: 'BTCUSDT', side: 'buy', opened_at: NOW - 86400 * 3, closed_at: NOW - 86400 * 2, entry: 78000, exit: 80000, quantity: 0.01, pnl: 20, fee: 0.4, currency: 'USDT' }, { id: 2, symbol: 'ETHUSDT', side: 'sell', opened_at: NOW - 86400, closed_at: NOW - 3600, entry: 2500, exit: 2550, quantity: 0.2, pnl: -10, fee: 0.2, currency: 'USDT' }], total: 2 });
  if (p === '/up/coineprofx/api/user/trade-history') return json({ items: [{ id: 1, symbol: 'XAUUSD', direction: 'BUY', volume: 0.1, entry_price: 4100, exit_price: 4140, open_time: iso(-86400e3), close_time: iso(-3600e3), net_profit: 40, commission: 0.7, swap: 0, pips: 400 }], total_pages: 1 });
  if (p === `${TYM}membership/status`) return json({ status: 'awaiting_uid', message_fa: 'UID صرافی را ثبت کنید.', next_step: 'uid', can_resubmit: true });
  if (p === `${TYM}membership/uid`) return json({ status: 'verifying', message_fa: 'در حال بررسی' });
  if (p === '/up/tradeyar/api/v1/public/membership') return json({ lbank_referral_url: 'https://lbank.com/ref/e2e', ourbit_referral_url: 'https://ourbit.com/ref/e2e', min_deposit_usdt: 50, uid_exchanges: ['lbank', 'ourbit'], notice_fa: 'با لینک ما ثبت‌نام کنید.' });
  if (p === `${TYM}announcements`) return json({ announcements: [{ id: 1, title: 'به‌روزرسانی سرویس', summary: 'نسخه‌ی تازه', published_at: iso(-3600e3) }] });
  if (p === `${TYM}entitlements`) return json({ premium: false, all: true });
  if (p === `${TYM}ai/quota` || p === '/up/coineprofx/api/user/ai-signal/quota') return json({ remaining: 2, used: 1, limit: 3 });
  if (p === `${TYM}ai/generate` || p === '/up/coineprofx/api/user/ai-signal/generate') return json({ id: 'job1', status: 'queued' });
  if (p.endsWith('/result/job1')) return json({ id: 'job1', status: 'done', result: { symbol: 'BTCUSDT', direction: 'long', timeframe: '1h', entry: 81000, entry_zone: { low: 80800, high: 81200 }, sl: 79800, tp1: 82500, tp2: 84000, confidence: 0.74, rr: 1.25, rationale: 'ساختار صعودی در تایم ساعتی.', warnings: ['خبر مهم امروز'] } });
  if (p === '/up/coineprofx/api/user/ai/chat') return json({ answer: 'روند کوتاه‌مدت **صعودی** است.', remaining: 9 });
  if (p === '/up/coineprofx/api/user/academy-token') return json({ token: 'pch_e2eacademy', expires_in: 3600 });
  const AC = '/up/coineprofx/api/academy/';
  if (p === `${AC}me`) return json({ username: 'e2e', tier: 'free', completed: 3, total_lessons: 40, progress_pct: 7.5, xp: 120, quizzes_taken: 2, avg_quiz: 80 });
  if (p === `${AC}catalog`) return json({ tier: 'free', levels: [{ key: 'beginner', name: 'مقدماتی', lessons: [{ slug: 'intro', title: 'بازار فارکس چیست', order: 1, completed: true }, { slug: 'candles', title: 'کندل‌ها', order: 2 }, { slug: 'pro', title: 'درس ویژه', order: 3, locked: true, lock_reason: 'tier' }] }] });
  if (p === `${AC}lessons/beginner/roadmap`) return json({ modules: [{ index: 1, title: 'شروع', lessons: [{ slug: 'intro', title: 'بازار فارکس چیست', order: 1, completed: true }], done: 1, total: 1 }] });
  if (p.startsWith(`${AC}lesson/`) && p.endsWith('/quiz')) return json({ questions: [{ id: 1, question: 'کندل سبز یعنی؟', options: ['بسته شدن بالاتر از باز شدن', 'برعکس'] }] });
  if (p.startsWith(`${AC}lesson/`) && p.endsWith('/quiz/submit')) return json({ score: 100, correct: 1, total: 1, passed: true, results: [{ id: 1, correct_index: 0, your_index: 0, is_correct: true, explanation: 'درست است.' }] });
  if (p.startsWith(`${AC}lesson/`)) return json({ slug: 'candles', title: 'کندل‌ها', summary: 'آشنایی با کندل', content: '## کندل\n\nهر کندل چهار قیمت دارد.', reading_time_min: 4 });
  if (p.startsWith(`${AC}progress/`)) return json({ ok: true });
  if (p === `${AC}streak`) return json({ current: 3, longest: 5, today_done: true });
  if (p === `${AC}achievements`) return json({ items: [{ badge: 'first', title: 'اولین درس', desc: 'یک درس تمام شد', icon: '🎓', earned: true }] });
  if (p === `${AC}leaderboard`) return json({ items: [{ rank: 1, username: 'top', xp: 900, completed: 30 }, { rank: 7, username: 'e2e', xp: 120, completed: 3, is_me: true }], my_rank: 7, total_students: 100 });
  if (p === `${AC}glossary`) return json({ terms: [{ term: 'پیپ', def: 'کوچک‌ترین واحد تغییر قیمت' }] });
  const CM = '/up/tradeyar/api/v1/public/app-community/';
  if (p === `${CM}me`) return json(r_method === 'POST' ? { registered: true, display_name: 'e2e' } : { registered: true, display_name: 'e2e' });
  if (p === `${CM}posts` && r_method === 'POST') return json({ id: 99, status: 'published' });
  if (p === `${CM}posts` || p === `${CM}posts/search`) return json({ posts: [{ id: 1, content: 'نظرتان درباره‌ی طلا چیست؟', author: 'ali', category: 'تحلیل', likes: 2, replies_count: 1, reactions: { '🔥': 1 }, created_at: iso(-3600e3), status: 'published' }] });
  if (/\/posts\/\d+$/.test(p) && p.startsWith(CM)) return json({ post: { id: 1, content: 'نظرتان درباره‌ی طلا چیست؟', author: 'e2e', category: 'تحلیل', likes: 2, created_at: iso(-3600e3) }, replies: [{ id: 5, content: 'صعودی است.', author: 'sara', created_at: iso(-600e3) }] });
  if (p.startsWith(CM) && /\/(like|react|reply|report|best-reply\/\d+)$/.test(p)) return json({ likes: 3, liked: true, reactions: { '🔥': 2 }, ok: true });
  if (p === `${CM}leaderboard`) return json({ items: [{ rank: 1, username: 'ali', xp: 50 }] });
  if (p === '/api/app/latest') return json({ version_code: 600, version_name: '6.0.0', notes_fa: '* بهبودها', url: 'https://pro-chart.com/app.apk' });
  const vip = vipFixture(p, q);
  if (vip) return vip;
  return json({ detail: 'not in fixtures' }, 404);
}

// ── VIP (area H): TradeYar's VIP rules and gate, the user site's portal routes, the forex VIP ──────
const VIP_RULES = {
  currency: 'USDT', price: 0, default_exchange: 'lbank',
  exchanges: [
    { id: 'lbank', label: 'LBank', referral_url: 'https://lbank.com/ref/E2E', uid_pattern: '^LBA[0-9A-Z]{5,10}$', uid_example: 'LBA1A23456', copy_trade: true },
    { id: 'ourbit', label: 'Ourbit', referral_url: 'https://www.ourbit.com/register?inviteCode=E2E', uid_pattern: '^[0-9]{6,10}$', uid_example: '40429440', copy_trade: false },
  ],
  entry: { min_deposit: 50, registration_min_deposit: 50, invite_ttl_hours: 24, one_uid_one_account: true },
  stay: { warn_below: 30, critical_below: 10, grace_hours: 48, rejoin_at: 50, warn_repeat_hours: 24 },
  checks: { balance_every_seconds: 1800, pending_every_seconds: 60, pending_window_hours: 168, pending_reminder_hours: [24, 72], uid_recheck_every_seconds: 60, uid_recheck_hours: 24, exchange_lag_fa: '۳۰ دقیقه تا ۱ ساعت' },
  support_url: 'https://t.me/CoinePro_Admin',
  texts_html: {
    deal: 'کانال VIP رایگان است؛ فقط کافیست حداقل <b>۵۰ تتر</b> در حساب خودت در صرافی (LBank یا Ourbit) با لینک ما داشته باشی — پول دست خودت می‌ماند.',
    have_account: 'صرافی فقط حساب‌هایی را به ما نشان می‌دهد که <b>با لینک تریدیار</b> ساخته شده‌اند.',
    lbank_register: 'UID صرافی LBank با <b>LBA</b> شروع می‌شود (مثل LBA1A23456).',
    ourbit_register: 'UID صرافی Ourbit یک عدد هشت‌رقمی است.',
    fresh_signup: 'اگر همین امروز ثبت‌نام کرده‌ای، ثبت ارتباط زیرمجموعه ۳۰ دقیقه تا ۱ ساعت طول می‌کشد.',
    stay_rule: '📌 <b>شرط ماندن در کانال:</b> اگر موجودی زیر <b>۳۰ تتر</b> برود هشدار می‌گیری، و اگر زیر <b>۱۰ تتر</b> برود و <b>۴۸ ساعت</b> بماند، از کانال خارج می‌شوی.',
  },
  registration_status_fa: { awaiting_uid: 'هنوز UID صرافی را ثبت نکرده‌اید.', pending_deposit: 'حساب شما زیرمجموعه‌ی تریدیار تأیید شد، اما موجودی به حد لازم نرسیده است.', rejected_referral: 'هنوز به‌عنوان زیرمجموعه‌ی تریدیار شناسایی نشدی.', approved: 'حساب شما تأیید شده است.', error: 'بررسی خودکار انجام نشد.' },
  notices: { low: { level: 'warning', title: 'هشدار کمبود موجودی VIP' }, critical: { level: 'error', title: 'اخطار نهایی موجودی VIP' }, removed: { level: 'error', title: 'دسترسی VIP لغو شد' } },
};
function vipMember(status = 'active', over = {}) {
  const entitled = ['active', 'low', 'critical'].includes(status);
  const bal = { active: 82.5, low: 21.4, critical: 4.2, removed: 0, pending_deposit: 12, awaiting_referral: null, none: null, expired: null }[status] ?? null;
  const ev = (event, level, title, body, ago) => ({ id: String(100 + ago), event, level, title, body, balance: bal, at: new Date(Date.now() - ago * 3600e3).toISOString() });
  const events = {
    active: [ev('granted', 'ok', 'دسترسی VIP فعال شد', 'کانال سیگنال VIP و امکانات رایگان برای شما باز است.', 30)],
    low: [ev('low', 'warning', 'هشدار کمبود موجودی VIP', 'موجودی حساب LBank شما 21.4 تتر است.', 2), ev('granted', 'ok', 'دسترسی VIP فعال شد', '', 300)],
    critical: [ev('critical', 'error', 'اخطار نهایی موجودی VIP', 'اگر تا ۴۸ ساعت آینده به بالای ۱۰ تتر نرسد، دسترسی لغو می‌شود.', 1), ev('low', 'warning', 'هشدار کمبود موجودی VIP', '', 26)],
    removed: [ev('removed', 'error', 'دسترسی VIP لغو شد', 'به دلیل پایین ماندن موجودی، دسترسی شما لغو شد.', 3), ev('critical', 'error', 'اخطار نهایی موجودی VIP', '', 52)],
  }[status] ?? [];
  return {
    status, entitled, balance: bal, exchange: status === 'none' ? null : 'lbank', uid: status === 'none' ? null : 'LBA1A23456',
    message_fa: { active: 'دسترسی VIP شما فعال است: کانال سیگنال و امکانات رایگان.', low: 'موجودی حسابتان زیر ۳۰ تتر است.', critical: 'اخطار نهایی: موجودی زیر ۱۰ تتر است.', removed: 'دسترسی VIP به دلیل پایین ماندن موجودی لغو شد.', pending_deposit: 'حساب شما زیرمجموعه‌ی تریدیار است؛ برای فعال شدن VIP موجودی باید به ۵۰ تتر برسد.', awaiting_referral: 'UID شما ثبت شد و هر دقیقه خودکار بررسی می‌شود.', none: 'هنوز عضو VIP نیستید.' }[status] ?? '',
    rule_fa: 'ورود با حداقل ۵۰ تتر در حساب خودتان.',
    checked_at: new Date(Date.now() - 600e3).toISOString(),
    critical_since: status === 'critical' ? new Date(Date.now() - 2 * 3600e3).toISOString() : null,
    removal_due_at: status === 'critical' ? new Date(Date.now() + 46 * 3600e3).toISOString() : null,
    pending_since: status === 'pending_deposit' ? new Date(Date.now() - 3600e3).toISOString() : null,
    pending_until: status === 'pending_deposit' ? new Date(Date.now() + 167 * 3600e3).toISOString() : null,
    thresholds: { min_deposit: 50, warn: 30, critical: 10, grace_hours: 48, pending_window_hours: 168 },
    channel: { configured: true, telegram_known: entitled, invite_url: entitled ? 'https://t.me/+e2eVip' : null },
    support_url: 'https://t.me/CoinePro_Admin', events, ...over,
  };
}
const FOREX_RULES = {
  market: 'forex', signals_only: true, price: 0, broker: { name: 'OneRoyal', referral_url: 'https://vc.cabinet.oneroyal.com/links/go/e2e' },
  entry: { min_deposit: 500, currency: 'USD', verification: 'manual', plan: 'free_lifetime', lifetime: true }, access: ['signal_channel', 'app_signals'],
  alternatives: { trial_hours: 48, plans: [{ key: 'monthly', months: 1, price: 30 }], currency: 'USDT' }, bot_url: 'https://t.me/CoineProFxBot', support_url: 'https://t.me/CoinePro_Admin',
  texts_html: { broker_guide: '<b>۱) ورود از لینک رفرال</b> و ساخت حساب.', broker_funding: 'واریز <b>ریالی</b> یا USDT.' },
  steps_fa: ['در بروکر OneRoyal از طریق لینک ما ثبت‌نام کنید.', 'حساب معاملاتی خودتان را با حداقل 500 دلار شارژ کنید.', 'شماره‌ی حساب معاملاتی و ایمیل بروکر را بفرستید.'],
};
function forexMember(status = 'none') {
  return {
    status, entitled: status === 'approved', signals_only: true,
    message_fa: { none: 'هنوز درخواستی ثبت نکرده‌اید.', submitted: 'درخواست شما ثبت شد؛ پشتیبانی واریز را بررسی می‌کند.', approved: 'دسترسی VIP فارکس برای شما فعال است.', rejected: 'درخواست شما تأیید نشد.' }[status],
    vip: status === 'approved' ? { plan: 'free_lifetime', expires_at: '2126-01-01T00:00:00+00:00', lifetime: true } : null,
    claim: status === 'none' ? null : { id: 5, account_login: '•••••567', email: 'fx@example.com', status: status === 'approved' ? 'approved' : status, reason: status === 'rejected' ? 'واریز پیدا نشد' : null, submitted_at: new Date().toISOString(), reviewed_at: null },
    telegram_linked: false, channel_invite: status === 'approved' ? 'https://t.me/+e2eFx' : null, can_submit: status !== 'approved',
  };
}
function vipFixture(p, q) {
  const TYM = '/up/tradeyar/api/mobile/v1/';
  const PU = '/up/tradeyar/api/user/v1/';
  const iso = (d = 0) => new Date(Date.now() + d).toISOString();
  if (p === '/up/tradeyar/api/v1/public/vip/rules' || p === '/up/tradeyar/api/v1/public/membership/rules') return json(VIP_RULES);
  if (p === `${TYM}membership/vip`) return json(vipMember('active'));
  if (p === `${TYM}membership/vip/invite`) return json({ invite_url: 'https://t.me/+e2eFresh', expires_at: iso(864e5), single_use: true });
  if (p === `${TYM}membership/vip/recheck`) return json({ ...vipMember('active'), recheck: { outcome: 'already_member', granted: true, message_fa: 'شما هم‌اکنون عضو فعال کانال VIP هستی.' } });
  if (p === '/up/coineprofx/api/public/forex-vip/rules') return json(FOREX_RULES);
  if (p === '/up/coineprofx/api/user/mobile/forex-vip') return json(forexMember('none'));
  if (p === '/up/coineprofx/api/user/mobile/forex-vip/claim') return json(forexMember('submitted'));
  if (p === '/up/coineprofx/api/public/auth/config') return json({ bot_username: 'CoineProFxBot' });
  if (p === `${PU}performance/stats`) return json({ totalTrades: 26, closedTrades: 24, activeTrades: 2, winCount: 15, lossCount: 7, breakevenCount: 1, unknownOutcomeCount: 1, winRate: 68.2, winRateDenominator: 22, longCount: 16, shortCount: 10, outcomeAvailable: true, activeSignalClosed: 0, monthlyBreakdown: [{ month: '2026-08', wins: 6, losses: 3, breakeven: 0, unknown: 0, total: 9, graded: 9, winRate: 66.7 }, { month: '2026-09', wins: 5, losses: 2, breakeven: 1, unknown: 1, total: 9, graded: 7, winRate: 71.4 }, { month: '2026-10', wins: 4, losses: 2, breakeven: 0, unknown: 0, total: 6, graded: 6, winRate: 66.7 }] });
  if (p === `${PU}performance/history`) return json({ trades: Array.from({ length: 22 }, (_, i) => ({ id: i, symbol: 'BTCUSDT', direction: 'LONG', outcome: i % 3 === 2 ? 'LOSS' : 'WIN', closedAt: iso(-(22 - i) * 864e5), createdAt: iso(-(23 - i) * 864e5), isWin: i % 3 !== 2 })), total: 22 });
  if (p === `${PU}affiliate/link`) return json({ lbankUid: 'LBA1A23456', referralCode: 'E2ECODE', referralLink: 'https://lbank.com/ref/E2ECODE' });
  if (p === `${PU}affiliate/summary`) return json({ configured: true, totalReferrals: 4, activeReferrals: 2, monthlyReferrals: 4, members: [{ openId: 'LBA9X11111', transaction: true }, { openId: 'LBA9X22222', transaction: false }] });
  if (p === `${PU}account/balance`) return json({ userId: -2000000042, totalBalance: 1250.4, availableBalance: 1100.2, unrealizedPnl: 12.6, marginUsed: 150.2, error: null, timestamp: iso(-60e3) });
  if (p === `${PU}settings/status`) return json({ willTrade: false, linked: true, apiReady: true, botEnabled: true, autoEntryEnabled: false, registrationStatus: 'approved', openPositions: 1, blockers: [{ code: 'auto_entry_off', message: 'ورود خودکار خاموش است؛ سیگنال‌ها می‌آیند اما اجرا نمی‌شوند.', action: 'quick_enable' }], lastError: null, lastSuccessAt: null, serverEgressIp: '203.0.113.7', exitMode: 'follow' });
  if (p === `${PU}settings/test-connection`) return json({ ok: true, code: null, message: 'اتصال برقرار است.', serverEgressIp: '203.0.113.7', available: 1100.2, equity: 1250.4 });
  if (p === `${PU}settings/audit`) return json({ entries: [{ field: 'leverage_value', oldValue: '5', newValue: '10', actorId: 1, source: 'web', changedAt: iso(-864e5) }] });
  if (p === `${PU}settings`) return r_method === 'PUT' ? json({ ok: true, updated: 1, savedFields: ['leverageValue', 'enabled', 'autoEntryEnabled'], notes: [] }) : json({ enabled: true, apiKeySet: true, apiSecretSet: true, leverageValue: 5, amountValue: 25, tpTarget: 3, exitMode: 'follow', autoTpSlEnabled: true, autoEntryEnabled: false, trailingStopEnabled: false, maxPositions: 5, maxPositionsChoices: [1, 2, 3, 5, 10, 20], leverageCap: 15, slMode: 'EXACT', slOffsetPct: null, slOffsetMaxPct: 4.8, amountMin: 5 });
  if (p === `${PU}positions/active`) return json({ positions: [{ id: 1, positionCode: 'P1', symbol: 'BTC_USDT', symbolNorm: 'BTCUSDT', direction: 'LONG', entryPrice: 80000, slPrice: 78000, tpPrice: 84000, tpTarget: 2, status: 'ACTIVE', openedAt: iso(-7200e3) }], total: 1 });
  if (p === `${PU}positions/history`) return json({ positions: [{ id: 2, positionCode: 'P2', symbol: 'ETH_USDT', symbolNorm: 'ETHUSDT', direction: 'SHORT', entryPrice: 2500, slPrice: 2600, tpPrice: 2300, status: 'CLOSED', closedAt: iso(-864e5), outcome: { code: 'CLOSED_TP', label: 'هدف ۲', tone: 'up' } }], total: 1 });
  if (p === `${PU}positions/exchange`) return json({ positions: [{ symbol: 'BTCUSDT', direction: 'LONG', volume: 0.01, entryPrice: 80000, markPrice: 81000, marginUsdt: 160, notionalUsdt: 810, unrealizedPnl: 10, leverage: 5, liquidationPrice: 65000 }], error: null });
  if (p === `${PU}positions/orders`) return json({ orders: [{ symbol: 'BTCUSDT', side: 'buy', action: 'open', price: 80000, volume: 0.01, status: 'filled', clientOrderId: 'c1', at: iso(-7200e3) }], error: null });
  if (p === `${PU}signals/recent`) return json({ signals: [{ positionId: 11, positionCode: 'S11', symbol: 'BTC/USDT', side: 'LONG', entryPrice: 80000, tp1: 82000, tp2: 84000, tp3: null, sl: 78000, confidence: 0.8, strength: 3, metaScore: null, timeframe: '1h', status: 'ACTIVE', createdAt: iso(-3600e3) }], total: 1 });
  void q;
  return null;
}

// ── Harness ───────────────────────────────────────────────────────────────────────────────────────
const TYPES = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.svg': 'image/svg+xml', '.png': 'image/png', '.ttf': 'font/ttf', '.map': 'application/json', '.txt': 'text/plain' };
const browser = await chromium.launch({ args: ['--use-gl=swiftshader', '--enable-unsafe-swiftshader'], ...(UPSTREAM && process.env.HTTPS_PROXY ? { proxy: { server: process.env.HTTPS_PROXY } } : {}) });

async function open(path, { width = 1440, height = 900, scheme = 'light', locale = 'fa-IR', signed = false, setup = null, mock: custom = null } = {}) {
  const ctx = await browser.newContext({ viewport: { width, height }, colorScheme: scheme, locale, timezoneId: 'Asia/Tehran' });
  if (signed) {
    // A seeded member session on both platforms (fixtures only: the handles mean nothing upstream).
    await ctx.addInitScript(() => {
      const s = { access: 'pch_e2e', refresh: 'pch_e2e_r', refreshAt: Date.now() + 36e5, user: { name: 'E2E', email: 'e2e@example.com' } };
      try { if (!localStorage.getItem('pc.sessions.v1')) localStorage.setItem('pc.sessions.v1', JSON.stringify({ tradeyar: s, coineprofx: s })); } catch { /* ignore */ }
    });
  }
  const net = { calls: 0, failed: [] };
  await ctx.route('http://pc.local/**', async (r) => {
    const u = new URL(r.request().url());
    if (u.pathname.startsWith('/up/') || u.pathname.startsWith('/api/')) {
      net.calls++;
      if (UPSTREAM) {
        try { const resp = await r.fetch({ url: UPSTREAM + u.pathname + u.search }); if (resp.status() >= 500) net.failed.push(`${resp.status()} ${u.pathname}`); return r.fulfill({ response: resp }); }
        catch { net.failed.push(`ERR ${u.pathname}`); return r.abort(); }
      }
      // A suite may answer some routes itself (`open(path, { mock(url, method) })`), else the fixtures do.
      const own = custom ? custom(u, r.request().method(), r.request()) : null;
      return r.fulfill(own ?? mock(u.href, r.request().method()));
    }
    const rel = u.pathname.replace(/^\/next\//, '');
    let file = join(DIST, rel);
    if (!rel || !existsSync(file) || statSync(file).isDirectory()) file = join(DIST, 'index.html');
    return r.fulfill({ status: 200, body: readFileSync(file), headers: { 'content-type': TYPES[extname(file)] ?? 'application/octet-stream' } });
  });
  // A suite's own routes (a stateful fake of a server, say). Registered last, so asked first; a
  // route it does not answer goes on to the fixtures above with route.fallback().
  if (setup) await setup(ctx);
  const page = await ctx.newPage();
  const errors = [];
  page.on('pageerror', (e) => errors.push(`pageerror: ${e.message} @ ${(e.stack ?? '').split('\n').slice(1, 3).join(' ').slice(0, 300)}`));
  page.on('console', (m) => { if (m.type() === 'error' && !/Failed to load resource/.test(m.text())) errors.push(`console: ${m.text().slice(0, 200)}`); });
  const t0 = Date.now();
  await page.goto(`http://pc.local/next/${path}`, { waitUntil: 'domcontentloaded' });
  return { ctx, page, errors, net, t0 };
}

const results = [];
async function test(name, fn) {
  if (ONLY && !name.includes(ONLY)) return;
  const t = Date.now();
  let env;
  try {
    env = await fn();
    if (env?.errors?.length) throw new Error(env.errors.slice(0, 3).join(' | '));
    results.push({ name, ok: true, ms: Date.now() - t });
    console.log(`  ✓ ${name} (${Date.now() - t} ms)`);
  } catch (e) {
    results.push({ name, ok: false, ms: Date.now() - t, error: String(e.message ?? e).slice(0, process.env.E2E_VERBOSE ? 4000 : 400) });
    console.log(`  ✗ ${name}\n      ${String(e.message ?? e).slice(0, process.env.E2E_VERBOSE ? 4000 : 400)}`);
    if (env?.page) await env.page.screenshot({ path: join(SHOTS, `${name.replace(/\W+/g, '_')}.png`) }).catch(() => {});
  } finally {
    await env?.ctx?.close().catch(() => {});
  }
}
const expect = (cond, msg) => { if (!cond) throw new Error(msg); };
const ready = async (page) => {
  await page.waitForSelector('.chart-host canvas', { timeout: 20000 });
  await page.waitForFunction(() => !document.querySelector('.chart-state'), null, { timeout: 25000 });
};
const pc = (page, fn, arg) => page.evaluate(fn, arg);

console.log(`e2e against ${UPSTREAM || 'fixtures'}`);

await test('first paint and live chart (BTCUSDT 1h)', async () => {
  const env = await open('BTCUSDT/1h?e2e=1');
  await ready(env.page);
  const ms = Date.now() - env.t0;
  expect(ms < 15000, `chart took ${ms} ms`);
  const legend = await env.page.textContent('.legend-ohlc');
  expect(/O.*H.*L.*C/.test(legend ?? ''), 'legend OHLC missing');
  expect(!/[۰-۹]/.test(legend ?? ''), 'legend figures must be Latin');
  await env.page.waitForFunction(() => /BTCUSDT [\d,.]+/.test(document.title), null, { timeout: 15000 });
  return env;
});

await test('forex chart (XAUUSD 4h) with guest token', async () => {
  const env = await open('XAUUSD/4h?e2e=1');
  await ready(env.page);
  const title = await env.page.textContent('.legend-title');
  expect(title && title.length > 0, 'no display name');
  return env;
});

await test('watchlist click, interval buttons and the address', async () => {
  const env = await open('BTCUSDT/1h?e2e=1');
  await ready(env.page);
  await env.page.click('.wl-row >> text=ETHUSDT');
  await env.page.waitForURL(/\/next\/ETHUSDT\/1h$/);
  await env.page.click('.ws-top .iv >> text=4h');
  await env.page.waitForURL(/\/next\/ETHUSDT\/4h$/);
  await ready(env.page);
  return env;
});

await test('symbol search by typing on the chart', async () => {
  const env = await open('BTCUSDT/1h?e2e=1');
  await ready(env.page);
  await env.page.keyboard.type('sol');
  await env.page.waitForSelector('.search-row');
  await env.page.keyboard.press('Enter');
  await env.page.waitForURL(/SOLUSDT/);
  return env;
});

await test('every chart type renders', async () => {
  const env = await open('BTCUSDT/1h?e2e=1');
  await ready(env.page);
  const types = await pc(env.page, () => window.__pc.chartTypes);
  for (const t of types) {
    await pc(env.page, (x) => window.__pc.usePrefs.getState().setChartType(x), t);
    await env.page.waitForTimeout(250);
  }
  await pc(env.page, () => window.__pc.usePrefs.getState().setChartType('candles'));
  expect(types.length === 18, `expected 18 chart types, got ${types.length}`);
  return env;
});

await test('all 108 indicators add, compute and remove', async () => {
  const env = await open('ETHUSDT/1h?e2e=1');
  await ready(env.page);
  const keys = await pc(env.page, () => window.__pc.indicators);
  expect(keys.length === 108, `expected 108 indicators, got ${keys.length}`);
  for (let i = 0; i < keys.length; i += 6) {
    const batch = keys.slice(i, i + 6);
    await pc(env.page, (ks) => { const s = window.__pc.useStudies.getState(); for (const k of ks) s.add(k); }, batch);
    await env.page.waitForTimeout(350);
    const rows = await env.page.$$eval('.legend-study', (els) => els.length);
    expect(rows >= batch.length, `legend rows ${rows} for ${batch.join(',')}`);
    await pc(env.page, () => window.__pc.useStudies.getState().clear());
  }
  return env;
});

await test('indicator dialog: search, add RSI and MACD in their own panes', async () => {
  const env = await open('BTCUSDT/1h?e2e=1');
  await ready(env.page);
  await env.page.keyboard.press('/');
  await env.page.waitForSelector('.dialog.indicators');
  await env.page.keyboard.type('RSI');
  await env.page.waitForTimeout(200);
  const first = await env.page.textContent('.ind-row.on .ind-name');
  expect(first === 'Relative Strength Index', `RSI search ranked "${first}" first`);
  await env.page.keyboard.press('Enter');
  await env.page.fill('.dialog.indicators input', 'MACD');
  await env.page.keyboard.press('Enter');
  await env.page.keyboard.press('Escape');
  await env.page.waitForTimeout(600);
  const panes = await env.page.$$eval('.pane-legend', (els) => els.length);
  expect(panes === 2, `expected 2 pane legends, got ${panes}`);
  return env;
});

await test('every drawing tool draws', async () => {
  const env = await open('BTCUSDT/1h?e2e=1');
  await ready(env.page);
  env.page.on('dialog', (d) => { d.accept('e2e').catch(() => {}); });
  const tools = await pc(env.page, () => window.__pc.tools.filter((t) => !['cursor', 'select'].includes(t.id)));
  const box = await env.page.locator('.chart-host').boundingBox();
  const at = (fx, fy) => [box.x + box.width * fx, box.y + box.height * fy];
  let drawn = 0;
  const missed = [];
  for (const t of tools) {
    await pc(env.page, (x) => window.__pc.useDrawing.getState().setTool(x.id, x.group), t);
    const before = await pc(env.page, () => window.__pc.useChartBus.getState().drawingLayer.getDrawings().length);
    if (['brush', 'highlighter'].includes(t.id)) {
      const [x0, y0] = at(0.3, 0.4); const [x1, y1] = at(0.5, 0.5);
      await env.page.mouse.move(x0, y0); await env.page.mouse.down(); await env.page.mouse.move(x1, y1, { steps: 8 }); await env.page.mouse.up();
    } else if (['polyline', 'path'].includes(t.id)) {
      for (const [x, y] of [at(0.3, 0.5), at(0.45, 0.35), at(0.6, 0.55)]) await env.page.mouse.click(x, y);
      await env.page.keyboard.press('Enter');
    } else {
      const pts = [at(0.2, 0.6), at(0.3, 0.35), at(0.4, 0.55), at(0.5, 0.3), at(0.6, 0.5), at(0.7, 0.4), at(0.8, 0.45), at(0.85, 0.3)];
      for (const [x, y] of pts) {
        await env.page.mouse.click(x, y);
        const n = await pc(env.page, () => window.__pc.useChartBus.getState().drawingLayer.getDrawings().length);
        if (n > before) break;
      }
    }
    await env.page.waitForTimeout(80);
    const after = await pc(env.page, () => window.__pc.useChartBus.getState().drawingLayer.getDrawings().length);
    if (after > before) drawn++; else missed.push(t.id);
    await env.page.keyboard.press('Escape');
    await pc(env.page, () => window.__pc.useDrawing.getState().setTool('cursor', 'cursors'));
  }
  expect(drawn === tools.length, `only ${drawn} of ${tools.length} tools drew; missed: ${missed.join(', ')}`);
  // Undo/redo and persistence per symbol.
  const n = await pc(env.page, () => window.__pc.useChartBus.getState().drawingLayer.getDrawings().length);
  await env.page.keyboard.press('Control+z');
  const m = await pc(env.page, () => window.__pc.useChartBus.getState().drawingLayer.getDrawings().length);
  expect(m === n - 1, 'undo did not remove one drawing');
  const saved = await env.page.evaluate(() => JSON.parse(localStorage.getItem('pc.drawings.v1.BTCUSDT') ?? '[]').length);
  expect(saved === m, `saved ${saved} drawings, layer has ${m}`);
  console.log(`      ${drawn}/${tools.length} tools drew`);
  return env;
});

await test('every NamaScript example runs', async () => {
  const env = await open('BTCUSDT/1h?e2e=1');
  await ready(env.page);
  const codes = await pc(env.page, () => window.__pc.exampleCode);
  const failures = [];
  for (let i = 0; i < codes.length; i++) {
    await pc(env.page, (c) => { const s = window.__pc.useScript.getState(); s.setSource(c); s.apply(); }, codes[i]);
    await env.page.waitForFunction(() => { const s = window.__pc.useScript.getState(); return !s.running && s.result; }, null, { timeout: 15000 });
    const r = await pc(env.page, () => { const s = window.__pc.useScript.getState().result; return { ok: s.ok, error: s.error }; });
    if (!r.ok) failures.push(`#${i}: ${r.error}`);
    await pc(env.page, () => window.__pc.useScript.getState().remove());
  }
  expect(!failures.length, `examples failed: ${failures.join(' ; ')}`);
  console.log(`      ${codes.length} examples ran`);
  return env;
});

await test('backtest shows the strategy results', async () => {
  const env = await open('BTCUSDT/1h?e2e=1');
  await ready(env.page);
  const idx = await pc(env.page, () => window.__pc.exampleCode.findIndex((c) => c.includes('strategy.entry')));
  await pc(env.page, (i) => { const s = window.__pc.useScript.getState(); s.setSource(window.__pc.exampleCode[i]); s.apply(); window.__pc.usePanels.getState().setBottom('backtest'); }, idx);
  await env.page.waitForSelector('.bt-stats', { timeout: 15000 });
  return env;
});

await test('replay: select, step, play, exit', async () => {
  const env = await open('BTCUSDT/15m?e2e=1');
  await ready(env.page);
  await env.page.click('[title="بازپخش"]');
  const box = await env.page.locator('.chart-host').boundingBox();
  await env.page.mouse.click(box.x + box.width * 0.4, box.y + box.height * 0.5);
  await env.page.waitForSelector('.replay-bar:not(.selecting)');
  const i0 = await pc(env.page, () => window.__pc.useReplay.getState().state.idx);
  await env.page.keyboard.press('Shift+ArrowRight');
  const i1 = await pc(env.page, () => window.__pc.useReplay.getState().state.idx);
  expect(i1 === i0 + 1, 'replay step');
  await env.page.click('.replay-bar [title="exit"]');
  expect(!(await pc(env.page, () => window.__pc.useReplay.getState().state?.on)), 'replay did not exit');
  return env;
});

await test('alert from the chart context menu', async () => {
  const env = await open('BTCUSDT/1h?e2e=1');
  await ready(env.page);
  const box = await env.page.locator('.chart-host').boundingBox();
  await env.page.mouse.click(box.x + box.width * 0.5, box.y + box.height * 0.3, { button: 'right' });
  await env.page.click('.ctx >> text=هشدار در این قیمت');
  await env.page.click('.dialog .btn-primary');
  const n = await pc(env.page, () => window.__pc.useAlerts.getState().list.length);
  expect(n === 1, 'alert not created');
  return env;
});

await test('every side panel opens', async () => {
  const env = await open('BTCUSDT/1h?e2e=1');
  await ready(env.page);
  for (const id of ['watchlist', 'alerts', 'signals', 'ai', 'objects', 'data', 'news', 'calendar', 'screener']) {
    await pc(env.page, (x) => { const p = window.__pc.usePanels.getState(); if (p.open !== x) p.toggle(x); }, id);
    await env.page.waitForTimeout(600);
    expect(await env.page.$('.ws-panel:not([hidden])'), `panel ${id} not shown`);
  }
  await pc(env.page, () => window.__pc.usePanels.getState().toggle('news'));
  await env.page.waitForSelector('.news-row', { timeout: 10000 });
  await env.page.click('.news-row');
  await env.page.waitForSelector('.article', { timeout: 10000 });
  return env;
});

await test('every bottom tab opens', async () => {
  const env = await open('BTCUSDT/1h?e2e=1');
  await ready(env.page);
  for (const tab of ['screener', 'namascript', 'backtest', 'paper']) {
    await pc(env.page, (x) => { const p = window.__pc.usePanels.getState(); if (p.bottom !== x) p.setBottom(x); }, tab);
    await env.page.waitForTimeout(400);
    expect(await env.page.$('.ws-bottom-body'), `bottom ${tab}`);
  }
  return env;
});

await test('paper trading opens and closes a position', async () => {
  const env = await open('BTCUSDT/1h?e2e=1');
  await ready(env.page);
  await pc(env.page, () => window.__pc.usePanels.getState().setBottom('paper'));
  await env.page.waitForSelector('.paper-ticket .buy');
  await env.page.waitForFunction(() => !document.querySelector('.paper-ticket .buy').disabled, null, { timeout: 10000 });
  await env.page.click('.paper-ticket .buy');
  await env.page.waitForSelector('.paper-lists .bt-row:not(.bt-head) .lt');
  await env.page.click('.paper-lists .bt-row:not(.bt-head) .lt');
  return env;
});

await test('multi-chart layouts 2/3/4', async () => {
  const env = await open('BTCUSDT/1h?e2e=1');
  await ready(env.page);
  for (const g of ['2v', '2h', '3', '4']) {
    await pc(env.page, (x) => window.__pc.useGrid.getState().setGrid(x), g);
    await env.page.waitForTimeout(1500);
    const n = await env.page.$$eval('.lite', (els) => els.length);
    expect(n === ({ '2v': 1, '2h': 1, '3': 2, '4': 3 })[g], `grid ${g}: ${n} lite charts`);
  }
  await pc(env.page, () => window.__pc.useGrid.getState().setGrid('1'));
  return env;
});

await test('settings, theme and language', async () => {
  const env = await open('BTCUSDT/1h?e2e=1');
  await ready(env.page);
  await pc(env.page, () => { const p = window.__pc.usePrefs.getState(); p.setTheme('dark'); });
  await env.page.waitForTimeout(300);
  const bg = await env.page.evaluate(() => getComputedStyle(document.body).backgroundColor);
  expect(bg === 'rgb(15, 15, 15)', `dark background ${bg}`);
  await pc(env.page, () => window.__pc.usePrefs.getState().setLang('en'));
  await env.page.waitForTimeout(300);
  expect((await env.page.textContent('.ws-top')).includes('Indicators'), 'English top bar');
  await env.page.screenshot({ path: join(SHOTS, 'dark-en.png') });
  return env;
});

await test('sign-in dialog shows the backend error', async () => {
  const env = await open('BTCUSDT/1h?e2e=1');
  await ready(env.page);
  await env.page.click('.avatar');
  await env.page.click('.account-menu .menu-item >> text=ورود');
  await env.page.fill('input[type=email]', 'nobody@example.com');
  await env.page.fill('input[type=password]', 'wrong-password');
  await env.page.keyboard.press('Enter');
  await env.page.waitForSelector('.dialog.auth .error', { timeout: 15000 });
  return env;
});

await test('premium dialog lists plans', async () => {
  const env = await open('BTCUSDT/1h?e2e=1');
  await ready(env.page);
  await env.page.click('.cta');
  await env.page.waitForSelector('.plan', { timeout: 10000 });
  return env;
});

await test('signals track record opens a setup on the chart', async () => {
  const env = await open('ETHUSDT/1h?e2e=1');
  await ready(env.page);
  await pc(env.page, () => window.__pc.usePanels.getState().toggle('signals'));
  await env.page.waitForSelector('.sig-row', { timeout: 15000 });
  await env.page.click('.sig-row');
  await env.page.waitForTimeout(2500);
  return env;
});

await test('phone layout (390×844)', async () => {
  const env = await open('BTCUSDT/1h?e2e=1', { width: 390, height: 844 });
  await ready(env.page);
  expect(!(await env.page.isVisible('.ws-panel:not([hidden])')), 'a panel covers the chart on a phone');
  const overflow = await env.page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth);
  expect(!overflow, 'horizontal overflow on a phone');
  await env.page.click('.phone-only');
  await env.page.waitForTimeout(300);
  expect(await env.page.evaluate(() => document.body.classList.contains('rail-open')), 'rail did not open');
  await env.page.screenshot({ path: join(SHOTS, 'phone.png') });
  return env;
});

await test('tablet layout (820×1180)', async () => {
  const env = await open('XAUUSD/1h?e2e=1', { width: 820, height: 1180 });
  await ready(env.page);
  const overflow = await env.page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth);
  expect(!overflow, 'horizontal overflow on a tablet');
  await env.page.screenshot({ path: join(SHOTS, 'tablet.png') });
  return env;
});

await test('stress: 60 symbol and interval switches', async () => {
  const env = await open('BTCUSDT/1h?e2e=1');
  await ready(env.page);
  const syms = ['BTCUSDT', 'ETHUSDT', 'XAUUSD', 'EURUSD', 'SOLUSDT', 'GBPUSD'];
  const ivs = ['5', '15', '60', '240', '1D'];
  for (let i = 0; i < 60; i++) {
    await pc(env.page, ([s, iv]) => { const p = window.__pc.usePrefs.getState(); p.setSymbol(s); p.setInterval(iv); }, [syms[i % syms.length], ivs[i % ivs.length]]);
    await env.page.waitForTimeout(40);
  }
  await ready(env.page);
  const heap = await env.page.evaluate(() => performance.memory?.usedJSHeapSize ?? 0);
  expect(heap === 0 || heap < 400e6, `heap ${Math.round(heap / 1e6)} MB`);
  console.log(`      heap ${Math.round(heap / 1e6)} MB`);
  return env;
});


// ── Pages (every screen of the app beside the chart) ─────────────────────────────────────────────
const BAD = ['ارتباط برقرار نشد', 'این امکان روی این سرور فعال نیست', 'درخواست‌ها زیاد بود'];
const settle = async (page) => {
  await page.waitForSelector('.page-body', { timeout: 10000 });
  await page.waitForFunction(() => { const b = document.querySelector('.page-body'); return b && !/در حال بارگذاری…/.test(b.textContent ?? ''); }, null, { timeout: 15000 }).catch(() => {});
  await page.waitForTimeout(250);
};
const openPage = async (page, id, param = null) => { await pc(page, ([i, p]) => window.__pc.usePage.getState().open(i, p), [id, param]); await settle(page); };

await test('every page opens for a guest (members-only pages ask to sign in)', async () => {
  const env = await open('BTCUSDT/1h?e2e=1');
  await ready(env.page);
  const pages = await pc(env.page, () => window.__pc.pages);
  expect(pages.length >= 35, `only ${pages.length} pages`);
  for (const p of pages) {
    if (['signal', 'lesson', 'thread', 'reset'].includes(p.id)) continue;
    await openPage(env.page, p.id);
    const path = await env.page.evaluate(() => location.pathname);
    expect(path === `/next/${p.path}`, `${p.id}: address ${path}`);
    if (p.member) expect(await env.page.isVisible('.gate'), `${p.id}: no sign-in gate`);
    else {
      const text = await env.page.textContent('.page-body');
      expect((text ?? '').trim().length > 10, `${p.id}: empty page`);
      const bad = BAD.find((b) => (text ?? '').includes(b));
      if (bad) console.log(`      note: ${p.id} shows "${bad}"`);
    }
  }
  await pc(env.page, () => window.__pc.usePage.getState().close());
  await env.page.waitForFunction(() => /\/next\/[A-Z0-9]+\//.test(location.pathname), null, { timeout: 5000 });
  return env;
});

await test('every page opens for a member, with data and no error state', async () => {
  const env = await open('BTCUSDT/1h?e2e=1', { signed: true });
  await ready(env.page);
  const pages = await pc(env.page, () => window.__pc.pages);
  const params = { signal: 'tradeyar-11', lesson: 'candles', thread: '1' };
  for (const p of pages) {
    if (p.id === 'reset') continue;
    await openPage(env.page, p.id, params[p.id] ?? null);
    expect(!(await env.page.isVisible('.gate')), `${p.id}: gate shown to a member`);
    const text = (await env.page.textContent('.page-body')) ?? '';
    for (const b of BAD) expect(!text.includes(b), `${p.id}: shows "${b}"`);
    if (!UPSTREAM) await env.page.screenshot({ path: join(SHOTS, `page-${p.id}.png`) });
  }
  return env;
});

await test('deep link opens a lesson; its quiz is taken and passed', async () => {
  const env = await open('academy/lesson/candles?e2e=1', { signed: true });
  await settle(env.page);
  await env.page.waitForSelector('text=کندل‌ها', { timeout: 10000 });
  await env.page.click('text=آزمون این درس');
  await env.page.waitForSelector('.quiz input[type=radio]', { timeout: 10000 });
  await env.page.click('.quiz .opt >> nth=0');
  await env.page.click('text=ثبت پاسخ‌ها');
  await env.page.waitForSelector('text=قبول شدید', { timeout: 10000 });
  return env;
});

await test('main menu: open, search, go to a page, back to the chart', async () => {
  const env = await open('ETHUSDT/4h?e2e=1');
  await ready(env.page);
  await env.page.click('.menu-btn');
  await env.page.waitForSelector('.drawer', { timeout: 5000 });
  const items = await env.page.locator('.drawer-item').count();
  expect(items >= 30, `menu lists ${items} items`);
  await env.page.fill('.drawer-search input, input.drawer-search', 'هیت');
  await env.page.click('.drawer-item >> nth=0');
  await env.page.waitForSelector('.heat-box .tile', { timeout: 10000 });
  await env.page.keyboard.press('Escape');
  await env.page.waitForFunction(() => location.pathname === '/next/ETHUSDT/4h', null, { timeout: 5000 });
  return env;
});

await test('tools: the calculators compute', async () => {
  const env = await open('tools?e2e=1');
  await settle(env.page);
  const out = await env.page.textContent('.tools-out');
  expect(/100\.00/.test(out ?? ''), `risk output: ${out}`);
  await env.page.click('.page-tabs >> text=ریسک به ریوارد');
  await env.page.fill('input[aria-label="ورود"]', '100');
  await env.page.fill('input[aria-label="حد ضرر"]', '95');
  await env.page.fill('input[aria-label="حد سود"]', '110');
  await env.page.waitForFunction(() => /1 : 2\.00/.test(document.querySelector('.tools-out')?.textContent ?? ''), null, { timeout: 5000 });
  return env;
});

await test('paper trading: buy, protect, close, history', async () => {
  const env = await open('paper-trade?e2e=1');
  await settle(env.page);
  await env.page.waitForFunction(() => window.__pc.useMarket.getState().tickers.BTCUSDT, null, { timeout: 15000 });
  await env.page.fill('.ticket input >> nth=0', '0.01');
  await env.page.click('.ticket button.btn.buy');
  await env.page.waitForFunction(() => window.__pc.usePaper.getState().positions.length === 1, null, { timeout: 5000 });
  await env.page.click('table >> text=بستن');
  await env.page.waitForFunction(() => window.__pc.usePaper.getState().history.length === 1, null, { timeout: 5000 });
  await env.page.click('.page-tabs >> text=آمار');
  await env.page.waitForSelector('text=نرخ برد', { timeout: 5000 });
  return env;
});

await test('journal: log a trade and read the statistics', async () => {
  const env = await open('journal?e2e=1');
  await settle(env.page);
  await env.page.click('text=ثبت معامله');
  await env.page.fill('.form input >> nth=1', '100');
  await env.page.fill('.form input >> nth=2', '110');
  await env.page.click('.chip >> text=ترند');
  await env.page.click('.form button.btn-primary');
  await env.page.waitForFunction(() => window.__pc.useJournal.getState().entries.length === 1 && window.__pc.useJournal.getState().entries[0].pnl === 10, null, { timeout: 5000 });
  await env.page.click('.page-tabs >> text=آمار');
  await env.page.waitForSelector('text=بر اساس برچسب', { timeout: 5000 });
  return env;
});

await test('backtest page runs every built-in strategy', async () => {
  const env = await open('backtest?e2e=1');
  await settle(env.page);
  const options = await env.page.$$eval('.form select >> nth=1', () => []);
  void options;
  for (const id of ['ma_cross', 'rsi', 'macd', 'bollinger', 'breakout', 'supertrend']) {
    await env.page.selectOption('.form select:near(:text("استراتژی"))', id).catch(async () => { await env.page.locator('.form select').nth(1).selectOption(id); });
    await env.page.click('text=اجرای بک‌تست');
    await env.page.waitForSelector('.bt-stats.wide', { timeout: 20000 });
  }
  return env;
});

await test('AI setup builder returns a setup and draws it', async () => {
  const env = await open('ai?e2e=1', { signed: true });
  await settle(env.page);
  await env.page.click('text=ساخت ستاپ');
  await env.page.waitForSelector('text=ساختار صعودی', { timeout: 20000 });
  await env.page.click('text=نمایش روی چارت');
  await ready(env.page);
  return env;
});

await test('live trade: order book, order with confirmation, position actions', async () => {
  const env = await open('trade?e2e=1', { signed: true });
  await settle(env.page);
  env.page.on('dialog', (d) => void d.accept().catch(() => {}));
  await env.page.click('.page-tabs >> text=دفتر سفارش');
  await env.page.waitForSelector('.dom-row.bid', { timeout: 10000 });
  await env.page.fill('.ticket input >> nth=0', '0.01');
  await env.page.click('.ticket button.btn.buy');
  await env.page.waitForSelector('text=ثبت شد', { timeout: 10000 });
  return env;
});

await test('community: thread, reply and reactions', async () => {
  const env = await open('community?e2e=1');
  await settle(env.page);
  await env.page.click('.reactions .chip >> nth=1');
  await env.page.click('.post.click');
  await settle(env.page);
  await env.page.waitForSelector('text=صعودی است', { timeout: 10000 });
  await env.page.fill('.form textarea', 'موافقم، بالای مقاومت است.');
  await env.page.click('text=ارسال پاسخ');
  return env;
});

await test('help library: search and an entry', async () => {
  const env = await open('tutorials?e2e=1');
  await settle(env.page);
  await env.page.fill('.scr-search', 'RSI');
  await env.page.click('.help-card >> nth=0');
  await env.page.waitForSelector('.help-entry h2', { timeout: 5000 });
  await env.page.click('.page-tabs >> text=میانبرهای صفحه‌کلید');
  await env.page.waitForSelector('kbd', { timeout: 5000 });
  return env;
});

await test('legal: terms and privacy render from the documents', async () => {
  const env = await open('legal/terms?e2e=1');
  await settle(env.page);
  await env.page.waitForSelector('.md h2', { timeout: 10000 });
  await env.page.click('.page-tabs >> text=حریم خصوصی');
  await env.page.waitForFunction(() => location.pathname === '/next/legal/privacy', null, { timeout: 5000 });
  await env.page.waitForSelector('.md h2', { timeout: 10000 });
  return env;
});

await test('pages fit a phone (390 wide)', async () => {
  const env = await open('BTCUSDT/1h?e2e=1', { width: 390, height: 844, signed: true });
  await ready(env.page);
  for (const id of ['markets', 'portfolio', 'paper', 'tools', 'signals', 'academy', 'community', 'trade', 'profile', 'backtest']) {
    await openPage(env.page, id);
    const overflow = await env.page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth + 1);
    expect(!overflow, `${id}: horizontal overflow on a phone`);
  }
  return env;
});


// ── Chart parity: style bar, scales, go to date, watchlists, replay trading ───────────────────────
await test('drawing style bar: colour, width, style, lock, clone, delete', async () => {
  const env = await open('BTCUSDT/1h?e2e=1');
  await ready(env.page);
  await pc(env.page, () => { const l = window.__pc.useChartBus.getState().drawingLayer; l.addHLine(window.__pc.useChartBus.getState().bars.at(-1).c, '#2962FF'); l.selectAt(0); });
  await env.page.waitForSelector('.draw-style-bar', { timeout: 5000 });
  await env.page.click('.draw-style-bar .dsb-group >> nth=0 >> button');
  await env.page.click('.dsb-pop .swatch >> nth=1');
  const color = await pc(env.page, () => window.__pc.useChartBus.getState().drawingLayer.getDrawings()[0].color);
  expect(color === '#F23645', `colour ${color}`);
  await env.page.click('.draw-style-bar .dsb-group >> nth=1 >> button');
  await env.page.click('.dsb-pop .dsb-line >> nth=3');
  await env.page.click('.draw-style-bar .dsb-group >> nth=2 >> button');
  await env.page.click('.dsb-pop .dsb-line >> nth=1');
  const d = await pc(env.page, () => window.__pc.useChartBus.getState().drawingLayer.getDrawings()[0]);
  expect(d.width === 4 && d.lineStyle === 'dashed', `style ${JSON.stringify(d)}`);
  await env.page.click('.draw-style-bar [data-act="clone"]');
  expect(await pc(env.page, () => window.__pc.useChartBus.getState().drawingLayer.getDrawings().length) === 2, 'clone');
  await env.page.click('.draw-style-bar [title="حذف"]');
  expect(await pc(env.page, () => window.__pc.useChartBus.getState().drawingLayer.getDrawings().length) === 1, 'delete');
  return env;
});

await test('context menu: invert, indexed, prev close, CSV; Alt+I', async () => {
  const env = await open('ETHUSDT/1h?e2e=1');
  await ready(env.page);
  const box = await env.page.locator('.chart-host').boundingBox();
  await env.page.mouse.click(box.x + box.width / 2, box.y + box.height / 2, { button: 'right' });
  await env.page.click('.ctx [data-act=invert]');
  expect(await pc(env.page, () => window.__pc.useChartBus.getState().invert), 'not inverted');
  await env.page.keyboard.press('Alt+i');
  expect(!(await pc(env.page, () => window.__pc.useChartBus.getState().invert)), 'Alt+I did not toggle');
  await env.page.mouse.click(box.x + box.width / 2, box.y + box.height / 2, { button: 'right' });
  await env.page.click('.ctx [data-act=indexed]');
  expect(await pc(env.page, () => window.__pc.useChartBus.getState().scale) === 'indexed', 'indexed');
  await env.page.mouse.click(box.x + box.width / 2, box.y + box.height / 2, { button: 'right' });
  const dl = env.page.waitForEvent('download', { timeout: 5000 });
  await env.page.hover('.ctx [data-act=more-tools]');
  await env.page.click('.ctx [data-act=csv]');
  const file = await dl;
  expect(/ETHUSDT_1h\.csv/.test(file.suggestedFilename()), file.suggestedFilename());
  return env;
});

await test('go to date scrolls the chart there', async () => {
  const env = await open('BTCUSDT/1h?e2e=1');
  await ready(env.page);
  // The date as the page's own clock writes it (the browser's time zone, not the runner's).
  const local = await pc(env.page, () => {
    const d = new Date(window.__pc.useChartBus.getState().bars[100].t * 1000);
    const p2 = (n) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${p2(d.getMonth() + 1)}-${p2(d.getDate())}T${p2(d.getHours())}:${p2(d.getMinutes())}`;
  });
  await env.page.keyboard.press('Alt+g');
  await env.page.fill('.goto-pop input', local);
  await env.page.click('.goto-pop button');
  await env.page.waitForSelector('.goto-pop', { state: 'detached', timeout: 8000 });
  await env.page.waitForTimeout(400);
  const range = await pc(env.page, () => window.__pc.useChartBus.getState().api.timeScale().getVisibleLogicalRange());
  expect(range.from < 100 && range.to > 100, `range ${JSON.stringify(range)}`);
  return env;
});

await test('watchlists: create, switch, flag, note, import text', async () => {
  const env = await open('BTCUSDT/1h?e2e=1');
  await ready(env.page);
  env.page.on('dialog', (dlg) => void dlg.accept(/^New list|فهرست تازه/.test(dlg.message()) ? 'Scalps' : 'buy the dip').catch(() => {}));
  await env.page.click('.wl-title');
  await env.page.click('.wl-lists >> text=/Create new list|فهرست تازه/');
  await env.page.waitForFunction(() => document.querySelector('.wl-title')?.textContent?.includes('Scalps'), null, { timeout: 5000 });
  await pc(env.page, () => window.__pc.useWatchlist.getState().add('SOLUSDT'));
  await env.page.click('.wl-row >> nth=0', { button: 'right' });
  await env.page.click('.menu-row.flags .wl-flag >> nth=2');
  await env.page.click('.wl-row >> nth=0', { button: 'right' });
  await env.page.click('.menu >> text=/Add note|افزودن یادداشت/');
  const st = await pc(env.page, () => { const w = window.__pc.useWatchlist.getState(); return { lists: w.lists.length, flags: w.flags, notes: w.notes }; });
  expect(st.lists === 2 && st.flags.SOLUSDT === 2 && st.notes.SOLUSDT === 'buy the dip', JSON.stringify(st));
  await env.page.click('.wl-title');
  await env.page.click('.wl-lists button >> text=/Flagged|پرچم‌دار/');
  await env.page.waitForSelector('.wl-row >> text=SOLUSDT', { timeout: 5000 });
  await env.page.goto('http://pc.local/next/BTCUSDT/1h?e2e=1');
  await ready(env.page);
  expect(await pc(env.page, () => window.__pc.useWatchlist.getState().lists.length) === 2, 'lists not kept');
  return env;
});

await test('replay: random bar, ±10, practice trades, back to real time', async () => {
  const env = await open('BTCUSDT/1h?e2e=1');
  await ready(env.page);
  await pc(env.page, () => window.__pc.useReplay.getState().select());
  await env.page.click('.replay-bar >> text=Random bar');
  await env.page.waitForSelector('.replay-bar .btn.buy', { timeout: 5000 });
  const i0 = await pc(env.page, () => window.__pc.useReplay.getState().state.idx);
  await env.page.click('.replay-bar >> text=+10');
  const i1 = await pc(env.page, () => window.__pc.useReplay.getState().state.idx);
  expect(i1 === i0 + 10, `idx ${i0} → ${i1}`);
  await env.page.click('.replay-bar .btn.buy');
  await env.page.click('.replay-bar >> text=+10');
  await env.page.click('.replay-bar >> text=Flatten');
  const trades = await pc(env.page, () => window.__pc.useReplay.getState().trades);
  expect(trades.length === 1 && trades[0].exit != null, JSON.stringify(trades));
  await env.page.click('.replay-bar [title="jump to real time"]');
  expect(!(await pc(env.page, () => window.__pc.useReplay.getState().state)), 'replay still on');
  return env;
});


// ── Symbol details, quote boxes, session breaks (the old Pro-Chart's and TradingView's) ───────────
await test('symbol details: sparkline, quote, rating, ranges, performance', async () => {
  const env = await open('BTCUSDT/1h?e2e=1');
  await ready(env.page);
  await env.page.waitForSelector('.details .gauge', { timeout: 15000 });
  await env.page.waitForSelector('.details .perf-grid .perf', { timeout: 15000 });
  await env.page.waitForSelector('.details .spark polyline', { timeout: 15000 });
  const text = await env.page.textContent('.details');
  expect(/اسپرد/.test(text ?? ''), 'no quote in details');
  expect(/بازه‌ی ۵۲ هفته/.test(text ?? ''), 'no 52-week range');
  await env.page.click('.rating-tfs .chip >> nth=0');
  await env.page.waitForSelector('.details .gauge', { timeout: 15000 });
  return env;
});

await test('legend buy/sell boxes place a paper trade', async () => {
  const env = await open('ETHUSDT/1h?e2e=1');
  await ready(env.page);
  await env.page.waitForSelector('.legend-trade .lt-buy', { timeout: 10000 });
  await env.page.click('.legend-trade .lt-buy');
  // Once the legend offers the order (pc:order-ticket), the ticket opens on paper for a guest.
  if (await env.page.waitForSelector('.ot .ot-submit', { timeout: 1500 }).catch(() => null)) await env.page.click('.ot .ot-submit');
  await env.page.waitForFunction(() => window.__pc.usePaper.getState().positions.some((p) => p.symbol === 'ETHUSDT' && p.side === 'long'), null, { timeout: 5000 });
  return env;
});

await test('session breaks draw on intraday; HLC area renders', async () => {
  const env = await open('BTCUSDT/15m?e2e=1');
  await ready(env.page);
  await pc(env.page, () => window.__pc.useChartSettings?.getState().set({ sessionBreaks: true }));
  await env.page.waitForSelector('canvas.session-breaks', { timeout: 5000 });
  const painted = await env.page.evaluate(() => { const c = document.querySelector('canvas.session-breaks'); const d = c.getContext('2d').getImageData(0, 0, c.width, c.height).data; for (let i = 3; i < d.length; i += 4) if (d[i]) return true; return false; });
  expect(painted, 'no session break drawn');
  await pc(env.page, () => window.__pc.usePrefs.getState().setChartType('hlcarea'));
  await env.page.waitForTimeout(500);
  await ready(env.page);
  return env;
});


await test('auto analysis: divergences, harmonics and gaps draw without errors', async () => {
  const env = await open('BTCUSDT/1h?e2e=1');
  await ready(env.page);
  await pc(env.page, () => window.__pc.useChartSettings.getState().set({ autoDivergence: true, autoHarmonic: true, autoGaps: true }));
  await env.page.waitForSelector('canvas.auto-overlay', { timeout: 5000 });
  const found = Number(await env.page.getAttribute('canvas.auto-overlay', 'data-found'));
  console.log(`      ${found} detections`);
  for (const iv of ['15', '240', '1D']) { await pc(env.page, (x) => window.__pc.usePrefs.getState().setInterval(x), iv); await ready(env.page); }
  return env;
});

// Further suites, one file per area of the workspace: scripts/e2e/suites/*.mjs, each a default export
// that takes the same helpers the tests above use.
const suites = join(here, 'suites');
if (existsSync(suites)) {
  for (const f of readdirSync(suites).filter((x) => x.endsWith('.mjs')).sort()) {
    const mod = await import(join(suites, f));
    await mod.default({ test, open, ready, pc, expect, fixtures: { json, vipMember, forexMember, VIP_RULES } });
  }
}

await browser.close();
const failed = results.filter((r) => !r.ok);
console.log(`\n${results.length - failed.length}/${results.length} passed`);
// The failures again, last, so the end of a long log carries them; on GitHub also as annotations.
for (const f of failed) {
  const msg = f.error.replace(/\s+/g, ' ').slice(0, 300);
  console.log(`  ✗ ${f.name} — ${msg}`);
  if (process.env.GITHUB_ACTIONS) console.log(`::error title=e2e::${f.name} — ${msg}`);
}
process.exit(failed.length ? 1 : 0);
