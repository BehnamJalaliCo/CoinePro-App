# Google Play listing — CoinePro

Everything Play asks for, filled in where this repository knows the answer and marked
**[OWNER]** where only the owner does. The Data safety answers in §4 are derived from the code, not
from a template — `docs/legal/PRIVACY_POLICY.md` records where each one comes from.

**Package:** `com.coinepro.app` · **Default language:** فارسی (fa-IR) · **Category:** Finance

---

## 1) Store listing (فارسی — the default)

**App name** (30 characters max)

```
کوین‌پرو | سیگنال و تحلیل بازار
```
*۳۰ کاراکتر. اگر بلند بود: «کوین‌پرو — سیگنال بازار» (۲۲).*

**Short description** (80 characters max)

```
سیگنال فارکس و رمزارز، نمودار حرفه‌ای، تحلیل هوش مصنوعی و آکادمی — همه در یک اپ.
```

**Full description** (4000 characters max)

```
کوین‌پرو دو بازار را در یک اپ می‌آورد: فارکس و فلزات از کوین‌پرو اف‌ایکس، و رمزارز از تریدیار.

■ سیگنال با جزئیات کامل
هر سیگنال با نقطهٔ ورود، حد ضرر، اهداف و نسبت ریسک به ریوارد می‌آید — و کندل‌های همان نماد پشت
سرش، تا ببینید ادعا روی چه قیمتی ساخته شده. سیگنال کهنه، کهنه علامت می‌خورد.

■ نمودار حرفه‌ای، بومی اندروید
۱۸ نوع نمودار، ۵۶ اندیکاتور و ۵۰ ابزار ترسیم — از فیبوناچی و کانال تا الگوهای هارمونیک. هشت
تایم‌فریم از یک‌دقیقه تا هفتگی، با تاریخچهٔ عمیق. برای هر اندیکاتور یک «؟» هست که می‌گوید چیست و
کِی به کار می‌آید.

■ ۴۶۰+ نماد
تمام جفت‌ارزهای اصلی، طلا و نقره، و ۴۴۱ بازار رمزارز. جست‌وجو هم نام فارسی را می‌فهمد هم نماد
انگلیسی را، و ترتیب نتایج بر اساس نقدشوندگی است نه الفبا.

■ تحلیل هوش مصنوعی
یک ستاپ کامل با ورود و حد ضرر و هدف، به‌همراه شواهدی که مدل روی آن تصمیم گرفته. یا عکس نمودارتان را
بفرستید و تحلیلش را بگیرید.

■ کپی‌تریدینگ و اجرا
روی کوین‌پرو اف‌ایکس حساب بروکرتان را وصل کنید و معاملات به‌صورت خودکار کپی شوند. روی تریدیار سفارش
را مستقیم روی صرافی بفرستید.

■ عملکرد حساب
سود خالص، نرخ برد، ضریب سود، بیشترین افت و منحنی سرمایه — از دفتر خود بروکر یا صرافی، نه از محاسبهٔ
ما. هر معاملهٔ بسته‌شده با ورود و خروج واقعی‌اش.

■ آکادمی
مسیر درس‌به‌درس از مقدماتی تا حرفه‌ای، با آزمون، نشان و روزشمار.

■ ابزار و اطلاعات
ماشین‌حساب حجم و ریسک، تقویم اقتصادی با هشدار رویدادهای پرتأثیر، و اخبار بازار.

━━━━━━━━━━━━━━━━━━━━

⚠ هشدار ریسک
کوین‌پرو کارگزار نیست و مشاورهٔ مالی نمی‌دهد. معامله در بازارهای مالی ریسک بالایی دارد و ممکن است به
از دست رفتن تمام سرمایهٔ شما بینجامد. عملکرد گذشته تضمین آینده نیست. تصمیم معاملاتی با خود شماست.

🔒 حریم خصوصی
این اپ هیچ SDK تحلیلی، تبلیغاتی یا ردیابی ندارد. توکن ورود شما با کلیدی که از دستگاه بیرون
نمی‌آید رمزنگاری و روی همان دستگاه نگه داشته می‌شود. موقعیت مکانی، دفترچه تلفن و شناسهٔ تبلیغاتی
خوانده نمی‌شود.
```

## 2) Store listing (English)

**App name:** `CoinePro — Market Signals`
**Short description:** `Forex and crypto signals, a pro chart, AI analysis and an academy.`
**Full description:** translate §1 rather than writing a second one, so the two listings cannot
drift into describing different apps.

## 3) Graphics — **[OWNER]**

| Asset | Spec | Source |
| --- | --- | --- |
| App icon | 512×512 PNG, 32-bit | `app/src/main/res/mipmap-*` at full size |
| Feature graphic | 1024×500 PNG | new artwork needed |
| Phone screenshots | 2–8, min 320px, 16:9 or 9:16 | `app/build/screenshots/` has 57 renders in Persian RTL; pick the ones below |
| Tablet screenshots | optional | not produced |

Suggested screenshots, in order — each shows one claim from the description and nothing else:

1. `17-home-fa.png` — the balance and the market list
2. `50-chart-screen-loaded-fa.png` — the chart with indicators
3. `51-signal-detail-chart-fa.png` — a signal with its bars behind it
4. `15-ai-studio-fa.png` — the AI setup
5. `52-portfolio-fa.png` — performance
6. `53-academy-fa.png` — the academy roadmap

These are off-device Robolectric renders at xxhdpi, which is legitimate for a listing — they are the
real UI at real density — but they carry fixture data. Anything that would read as a *promise* of
returns must be checked before upload: the portfolio render shows a profitable month, and Play's
financial-services policy treats an implied guarantee of profit as a violation. Prefer a fixture
near break-even for that one, or drop the screenshot.

## 4) Data safety form

Answers below map one-to-one onto the form's own wording. Every "yes" has a reason in the code.

**Does your app collect or share any of the required user data types?** — **Yes**

**Is all of the user data collected by your app encrypted in transit?** — **Yes.** Every call is
HTTPS; `usesCleartextTraffic` is `false` and a network security config pins that.

**Do you provide a way for users to request that their data is deleted?** — **[OWNER]** — needs a
deletion URL before this can be answered yes. Play requires an out-of-app route.

| Data type | Collected | Shared | Optional | Purpose |
| --- | --- | --- | --- | --- |
| Name | Yes | No | Yes — identity form only | Account management |
| Email address | Yes | No | No | Account management |
| User IDs | Yes | No | No | Account management, app functionality |
| Phone number | Yes | No | Yes — identity form only | Account management |
| Other personal info (national id, date of birth) | Yes | No | Yes — identity form only | Account management |
| Photos | Yes | No | Yes — image analysis only | App functionality |
| Other financial info (exchange API key) | Yes | No | Yes — only if you connect an exchange | App functionality |
| Purchase history | **[OWNER]** | | | only if in-app purchases ship |

**Not collected**, and each is a deliberate absence rather than an oversight: location, contacts,
calendar, messages, audio, files, health, app activity, search history, installed apps, device or
advertising ids, crash logs, diagnostics, performance data.

The last three are worth stating plainly on the form: the app ships **no analytics SDK and no crash
SDK**. Stability data comes only from Android Vitals, which Google collects itself and which the
form does not count as app collection.

**Data types shared with third parties:** none in the form's sense. Firebase Cloud Messaging carries
notification tokens and payloads, which is Google's own infrastructure and is declared under "User
IDs → collected". If a "shared" answer is required for FCM by the reviewer, the honest answer is
User IDs, purpose "app functionality".

## 5) Content rating questionnaire

* Violence, sexuality, language, controlled substances: **none**.
* **Does the app contain gambling or simulated gambling?** — **No.** Trading is not gambling under
  the questionnaire, but expect a reviewer to look: the app must never present a signal as a
  guaranteed outcome, which is why the risk warning is in the description, in the terms, and on the
  signal screen itself.
* **Does the app share user location?** — No.
* **Does the app allow users to interact?** — No. The community routes on the backend are
  deliberately not wired into this app.
* Expected rating: **Everyone / PEGI 3**, with a Finance category.

## 6) Financial features declaration

Play asks every Finance app to declare which financial features it offers. The honest answer for
this app:

* **Not** a banking app, not a payment app, not a crypto exchange, not a wallet, not a lender.
* It is an **investment information / market analysis** app, and it *does* place orders on the
  user's own connected exchange or broker account.

That last part matters. In several markets Play requires a licence declaration from an app that
executes trades. **[OWNER]** — check the target-country list before release; where a licence is
required and absent, the copy-trading and order-execution features must be withheld in that country
rather than shipped and hidden.

## 7) App access — **[OWNER]**

Play reviewers cannot see past the sign-in screen. Supply demo credentials for both platforms, or
the review will be rejected as "unable to access the app".

| Field | Value |
| --- | --- |
| Instructions | «برای فارکس از حساب اول و برای رمزارز از حساب دوم استفاده کنید. پلتفرم با کلید بالای صفحهٔ خانه عوض می‌شود.» |
| CoinePro-FX demo account | **[OWNER]** |
| TradeYar demo account | **[OWNER]** |

## 8) Contact and policy URLs — **[OWNER]**

| Field | Value |
| --- | --- |
| Email | **[OWNER]** |
| Website | **[OWNER]** |
| Privacy policy URL | **[OWNER]** — `docs/legal/PRIVACY_POLICY.md` must be published at a public HTTPS address; Play rejects a link to a repository file |
| Terms URL | **[OWNER]** — same, for `docs/legal/TERMS.md` |

## 9) Release blockers

Everything below is outside this repository and none of it can be closed from here.

1. **Signing key** — `local.properties` needs the four `COINEPRO_RELEASE_*` values. Without them the
   release build is unsigned.
2. **`google-services.json`** — absent, so push notifications do not work in a real build.
3. **SHA-1 in the Firebase console** — the release key's fingerprint must be registered or Google
   sign-in fails on a signed build with an error that reads like a network problem.
4. **Privacy and terms URLs** — §8.
5. **Demo accounts** — §7.
6. **Account deletion route** — §4.
7. **A real device run against live servers.** Nothing in this repository substitutes for it: 57
   off-device screenshots and a green test suite say the app renders and computes correctly, and say
   nothing about whether the two servers answer as documented under a real network.
