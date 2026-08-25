# نقشهٔ واقعی دو بک‌اند — خوانده‌شده از سورس

این سند از روی کدِ خودِ دو سرور نوشته شده، نه از روی قرارداد. تاریخ خواندن:
۱۴۰۵/۰۶/۰۳ — CoineProFx روی `975b2b3`، TradeYar روی `425b8bc`.

دلیل وجودش: ریپوهای بک‌اند ممکن است دوباره خصوصی شوند. هرچه اپ برای تکمیل کار
لازم دارد این‌جاست تا بسته‌شدن آن‌ها چیزی را از دست ندهد.

## آدرس پایه

| پلتفرم | آدرس | پیشوند مسیرها |
| --- | --- | --- |
| CoinePro-FX | `https://coineprofx.com/api/` | `user/…` (و `public/…`) |
| TradeYar | `https://tradeyar.trade-future.ir/` | `api/mobile/v1/…` |

FX روتر موبایل را با `prefix="/user"` سوار می‌کند؛ TradeYar با
`APIRouter(prefix="/api/mobile/v1")`.

---

## ۱. سیگنال‌ها پشت اشتراک‌اند — روی هر دو، و این درست است

تصمیم محصول: **سیگنال و کپی‌ترید فقط برای مشترکین**؛ بقیهٔ امکانات برای هر کاربر
واردشده باز. پس گیتِ زیر مطابق تصمیم است و نباید برداشته شود. آنچه اپ لازم دارد
این است که «اشتراک لازم است» را از «توکن باطل است» تشخیص بدهد.

**TradeYar** — `app/api/routers/mobile/signals.py`:

```python
REQUIRE_VIP = os.getenv("MOBILE_SIGNALS_REQUIRE_VIP", "1") == "1"
...
if not await _has_membership(user_id):
    return {"items": [], "next_cursor": None, "membership_required": True}
```

`_has_membership` عمداً **fail-closed** است — که درست است. پاسخِ کاربرِ بدون
اشتراک `membership_required: true` دارد، و همین است که اپ لازم دارد تا «اشتراک
لازم است» را از «سیگنالی نیست» تشخیص بدهد.

شکافِ باز: چون `vip_members.user_id` کلید خارجی به `users(user_id)` دارد، حسابِ
ایمیل‌محور ساختاراً عضو نمی‌شود — پس مسیرِ عضو شدنِ کاربری که در اپ ثبت‌نام کرده
هنوز روشن نیست. سؤالش در `FOLLOWUP4_TRADEYAR.md` پرسیده شده.

بقیهٔ مسیرهای موبایل TradeYar هیچ گیتِ اشتراکی ندارند — که با تصمیم می‌خواند.

**CoinePro-FX** — `src/api/routes/public.py`:

```python
@router.get("/signals/active")
async def public_active_signals(..., vip: dict = Depends(require_vip)):
```

`/public/signals/active` و `/public/signals/recent` هر دو `require_vip` دارند —
مطابق تصمیم. `/public/signals/showcase` باز است و عمداً یک سیگنالِ **بسته‌شده**
می‌دهد، که برای ویترین درست است.

مشکل اپ این‌جاست: ردِ VIP یک ۴۰۳ ساده است و از ۴۰۳ِ «توکن باطل» قابل تشخیص نیست،
پس کاربرِ بدون اشتراک ممکن است از حساب بیرون انداخته شود. درخواستِ یک کد
ماشین‌خوان در بدنه در `FOLLOWUP4_COINEPROFX.md` آمده.

---

## ۲. CoinePro-FX — چه چیزی هست و چه چیزی نیست

### هست

| مسیر | یادداشت |
| --- | --- |
| `user/auth/{methods,register/start,register/verify,login,google,password/forgot,password/reset,refresh,logout}` | همه تأیید شد |
| `user/auth/config`, `user/auth/telegram`, `user/auth/link/telegram` | مسیر تلگرام |
| `user/me` | پروفایل **خالی** (بدون پوشش) — `_profile_dict` در `user_panel.py` |
| `user/mobile/{briefing,portfolio,kyc,kyc/level1}` | |
| `user/mobile/{notifications,notifications/read,alerts,alerts/{id},push/devices,push/preferences}` | |
| `ws/snapshot` | |
| `user/ai-signal/{quota,generate,result/{job_id}}` | سوار روی `prefix="/user/ai-signal"` |
| `user/ai-signal/stream/{job_id}` | SSE |
| `user/ai-vision/jobs`, `user/ai-vision/jobs/{job_id}` | **نه** `user/ai/vision/…` |
| `user/ai/chat` | دستیار |
| `public/signals/{active,recent,showcase,stats}` | فهرست سیگنال — پشت VIP جز showcase |
| `user/{broker-servers,account/link,copy-config,copy-status,copy-svc-status}` | اتصال بروکر |
| `user/copy/{stop,close-all,close-position}` | بستن پوزیشن |
| `user/{history,trade-history,trade-history/stats,trade-history/report,trade-history/daily}` | تاریخچه |
| `user/{subscription,economic-calendar,notifications,notifications/read}` | نسخهٔ پنل، جدا از `user/mobile/*` |
| `academy/bn/news`, `academy/bn/calendar` | **خبر فارسی و تقویم، آماده** |

### نیست — و اپ صدایشان می‌زند

- `user/signals` و `user/signals/{id}` → فهرست زیر `public/signals/*` است.
  `/signals` و `/signals/{id}` وجود دارند ولی `Depends(get_current_admin)` دارند.
- `user/signals/execution/*` (هر هشت‌تا) → هیچ معادلی ندارد.
- `user/market-intelligence` → ساخته نشده. دادهٔ خام در `bn:news` و `bn:calendar`.

### شکل شیء سیگنال FX (`_pub_signal`)

مسطح، و با اسم‌های متفاوت از آنچه اپ می‌خواند:

```
id, symbol, direction, signal_type, entry_price, sl, tp1, tp2, tp3,
signal_score, timeframe, status, close_reason, pnl_pips, hit_target,
created_at, closed_at
```

روی `/signals/active` این سه هم اضافه می‌شود: `current_price`, `pnl_percent`,
`pnl_pips_live`. پاکت: `{"items": [...], "count": N}`.

نگاشت لازم: `entry_price→entry`، `sl→stop_loss`، `tp1..tp3→targets[]`،
`signal_score→confidence`. و `market`, `strategy`, `entry_zone`, `rationale`,
`score_breakdown`, `result`, `current_quote` اصلاً وجود ندارند.

### AI Vision روی FX

- `POST user/ai-vision/jobs` → `{"job_id", "status", "quota": {used, limit, reset_at}}`
- `GET user/ai-vision/jobs/{id}` → `{"status", "result"?, "error_code"?, "error_message"?}`
  — **شناسهٔ جاب را برنمی‌گرداند.**
- وضعیت‌ها: `queued`, `running`, `done`, `failed`, `expired`
- فرم: `image` (اجباری)، `symbol` و `timeframe` (اختیاری)

### دستیار روی FX

`POST user/ai/chat` با `{"message": "..."}` → `{"answer", "used", "quota", "remaining"}`.
`Depends(require_vip)`. تاریخچه‌ای سمت سرور نگه داشته نمی‌شود.

### پروفایل FX (`_profile_dict`) — snake_case

```
telegram_id, name, username, phone, email, email_verified, skill_level,
skill_score, kyc_status, kyc_full_name, kyc_country, is_vip, is_paid,
panel_approved, panel_allowed, panel_state, plan, plan_expires_at,
disclaimer_accepted, disclaimer_version, account{...}
```

`plan_expires_at` با `.isoformat()` ساخته می‌شود، یعنی `+00:00` نه `Z`.

### `user/auth/methods` روی FX

`email_password`, `google`, `google_client_id`, `telegram`,
`telegram_bot_username`, `push`, `chart_vision`.
**`ai_signals` و `assistant` ندارد** — با اینکه هر دو محصول را دارد.

---

## ۳. TradeYar — چه چیزی هست

همه زیر `api/mobile/v1`:

| مسیر | متد |
| --- | --- |
| `auth/{methods,register/start,register/verify,login,google,password/forgot,password/reset,refresh,logout}` | |
| `me` | GET |
| `kyc`, `kyc/level1` | GET, POST |
| `ws/snapshot` | GET |
| `signals`, `signals/{id}` | GET |
| `briefing` | GET |
| `portfolio` | GET |
| `executions` | GET, POST |
| `executions/{id}`, `executions/{id}/close` | GET, POST |
| `venues/lbank` | GET, POST, DELETE |
| `push/devices` | POST, DELETE |
| `push/preferences` | GET, PATCH |
| `alerts`, `alerts/{id}` | GET/POST، PATCH/DELETE |
| `notifications`, `notifications/read` | GET, POST |
| `ai/{quota,generate,result/{job_id},vision/jobs,vision/jobs/{job_id},stream/{job_id}}` | |

**`market-intelligence` ندارد.**

### شکل سیگنال TradeYar — دقیقاً همان چیزی که اپ می‌خواند

`app/api/mobile/signals.py::build_signal` این کلیدها را می‌دهد:

```
id, market ("crypto" همیشه), symbol, direction, status, timeframe, strategy,
confidence, entry, entry_zone, stop_loss, targets[], risk_reward_tp1,
rationale, score_breakdown, current_quote{price, ts, is_stale}, …
```

یعنی برای سیگنال‌های کریپتو فقط **مسیر و پاکت** فرق دارد، نه شکل شیء.
پاکت: `{"items": [...], "next_cursor": ..., "membership_required"?: true}` —
صفحه‌بندی با cursor است نه offset.
پارامترها: `status` یکی از `active|recent|closed`، `limit`، `cursor`.

### اجرای معامله TradeYar

`POST executions` بدنه: `{"signal_id": int, "quantity": float, "client_request_id": str}`
— **شناسه در بدنه است نه در مسیر.**

پاسخ (`_row_to_execution`):
```
id, signal_id, venue, side, product, quantity, status, provider_order_id,
error_message, can_request_close, created_at, closed_at
```

### venue TradeYar

`GET venues/lbank` یک **شیء** برمی‌گرداند نه لیست:
```json
{"configured": bool, "connected": bool, "key_hint": "…", "permission": "…",
 "status": "not_configured|connected|unverified|rejected", "error_message"?: "…"}
```

### briefing TradeYar

`GET briefing` → `{"body", "generated_at": <ثانیهٔ عدد>, "streaming": false, …}`
یا **۲۰۴ بدون بدنه** وقتی داده‌ای نیست.

### پروفایل TradeYar — camelCase و داخل کلید `user`

`GET me` → `{"user": {...}}`. محتوا از `identity.build_user_payload`:

```
userId, username, fullName, verificationStatus, lbankUid, email, phone,
source, createdAt
```

**هیچ فیلد اشتراکی ندارد** — نه `isVip`، نه `plan`. پس کارت اشتراک اپ برای هیچ
کاربر TradeYar نمایش داده نمی‌شود، حتی عضوی که در `vip_members` هست.

ولی پاسخِ `login` و `register/verify` کلید `user` را **مسطح** در کنار توکن‌ها
می‌گذارند (نه تودرتو)، و `refresh` اصلاً پروفایل نمی‌دهد.

### `auth/methods` روی TradeYar

`email_password`, `google`, `google_client_id`, `telegram: true`,
`push`, `chart_vision`, `ai_signals`, `assistant: false`.
**`telegram_bot_username` نمی‌فرستد** — پس اپ دکمهٔ تلگرام را نمی‌کشد، که درست است.

### خبر روی TradeYar

جدول `news_posts` با `title_fa`، `summary_fa`، `source`، `source_url`،
`importance`، `tags`، `published_at`، `content_type`، `status`.
سرو می‌شود در `GET /list` روی روتر عمومی `news`.

---

## ۴. آنچه هنوز از سورس نخوانده‌ام

برای صداقت: این‌ها را باز نکردم و اگر لازم شد باید دوباره خوانده شوند —
شکل دقیقِ `alerts`/`notifications`/`push` روی TradeYar (مسیرها تأیید شد، بدنه‌ها
نه)، `ai/quota` و `ai/generate` روی هر دو، `portfolio` و `kyc` روی TradeYar، و
`trade-history*` و `copy-*` روی FX.
