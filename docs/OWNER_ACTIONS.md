# سه کاری که فقط از دست مالک برمی‌آید

> آخرین بازبینی: ۲۰۲۶-۰۹-۰۵ · اپ ۴.۴۶.۰

هر سه‌تا **بیرون از کد** هستند: دو تا در کنسول گوگل، یکی یک اندازه‌گیری از یک شبکهٔ عادی. سمت اپ و
سمت هر دو سرور آماده و خاموش است و روزی که این‌ها انجام شود خودش روشن می‌شود.

هر سه، آخرِ کار، یک **repository variable** در گیت‌هاب می‌خواهند. جای هر سه یکی است:

> `github.com/BehnamJalaliCo/CoinePro-App` ← **Settings** ← **Secrets and variables** ← **Actions**
> ← زبانهٔ **Variables** ← **New repository variable**

*Secret* نه، *Variable*. هیچ‌کدام از این سه مقدار محرمانه نیست: شمارهٔ پروژه در کنسول چاپ شده و
پین، هَشِ یک کلید عمومی است که هر کسی می‌تواند بگیرد.

---

## ۱) Play Integrity — یک لینک در Play Console

**چرا:** هر دو سرور تأیید توکن را ساخته‌اند و هر دو به یک دیوار خورده‌اند:
`400 "App is not found."` یعنی بستهٔ اپ به پروژهٔ Cloud لینک نشده. تا آن لینک نباشد هر تأیید
`unverified` برمی‌گردد و روشن‌کردنش یعنی کنترلی که واقعی به نظر می‌رسد و هیچ چیزی را چک نمی‌کند.

**قدم به قدم**

1. `play.google.com/console` ← اپ **CoinePro** را باز کنید.
2. از ستون چپ: **Test and release** ← **App integrity**.
   (در بعضی حساب‌ها زیر **Release** ← **App integrity** است.)
3. زبانهٔ **Integrity API** ← بخش **Google Cloud project**.
4. **Link Cloud project** را بزنید و پروژهٔ **`1033486124390`** (نامش `coinepro-app`) را انتخاب کنید.
5. حساب گوگلی که این کار را می‌کند باید در آن پروژهٔ Cloud هم Owner/Editor باشد؛ اگر پروژه در
   فهرست نیامد، علتش همین است.

**بعدش، در گیت‌هاب:**

| Name | Value |
|---|---|
| `COINEPRO_PLAY_INTEGRITY_PROJECT` | `1033486124390` |

**تأیید:** به هر دو سرور بگویید لینک انجام شد؛ همان `decodeIntegrityToken` که تا الان
`App is not found` می‌داد باید verdict بدهد. سمت اپ، بعد از اولین build ی که این متغیر را دارد،
درخواست‌های ورود / کلید صرافی / اجرای سیگنال دو هدر `X-Play-Integrity` می‌برند.

**خطری ندارد:** هر دو سرور در حالت observe‌اند و «هدر نیامده» و «نتوانستیم بسنجیم» هر دو **عبور**
می‌کنند. یعنی هیچ درخواستی که امروز کار می‌کند بعد از این کار نمی‌افتد.

---

## ۲) ورود با گوگل — دو تا OAuth client

**چرا:** client فارکس **پاک شده** (`deleted_client`، مستقیم از خود گوگل اندازه گرفته شد) و کل
پروژه هیچ **Android** client ندارد، که همان چیزی است که Credential Manager روی گوشی لازم دارد.

### ۲.۱ — اول SHA-1 را بردارید

سه‌تا SHA-1 در کار است و بهتر است **هر سه** ثبت شوند:

| کدام کلید | از کجا بخوانید | برای چه |
|---|---|---|
| **App Signing** گوگل | Play Console ← اپ ← **Test and release** ← **Setup** ← **App signing** ← «SHA-1 certificate fingerprint» | نسخه‌ای که کاربر از Play نصب می‌کند |
| **Upload key** | همان صفحه، بخش **Upload key certificate** | build های تست داخلی |
| **کلید تست من** | `7A:D2:89:03:2A:C8:0D:EB:11:8A:79:BE:B6:88:4C:6C:D6:03:5F:D7` | APK هایی که این‌جا تحویلتان می‌دهم |

> سومی را حتماً اضافه کنید اگر می‌خواهید **قبل از انتشار در Play** ورود با گوگل را روی همین
> APK ها تست کنید. بدون آن، روی APK های من همیشه «ثبت نشده» می‌گیرید.
> از خود اپ هم می‌شود خواندش: منو ← «ایمنی و نسخه».

### ۲.۲ — Android client (برای هر دو پلتفرم، یک بار)

1. `console.cloud.google.com` ← بالای صفحه پروژهٔ **`coinepro-app` (1033486124390)** را انتخاب کنید.
2. **APIs & Services** ← **Credentials**.
3. **+ CREATE CREDENTIALS** ← **OAuth client ID**.
4. Application type: **Android**.
5. Name: هرچه (مثلاً `Pro Chart Android`).
6. Package name: **`com.coinepro.app`** (دقیقاً همین).
7. SHA-1: یکی از بالا. **برای هر SHA-1 یک client جدا بسازید** — یک client فقط یک اثر انگشت می‌گیرد.
8. Create.

> این client هیچ‌جا در اپ یا سرور نوشته نمی‌شود. فقط باید در همان پروژه **وجود داشته باشد** تا
> گوگل قبول کند برای این اپ توکن بسازد.

### ۲.۳ — Web client تازه (فقط فارکس)

1. همان صفحهٔ **Credentials** ← **+ CREATE CREDENTIALS** ← **OAuth client ID**.
2. Application type: **Web application**.
3. Name: مثلاً `CoinePro FX mobile audience`.
4. Authorized redirect URIs: **لازم نیست** چیزی اضافه کنید (اپ از این مسیر استفاده نمی‌کند).
5. Create ← **Client ID** را کپی کنید (`…apps.googleusercontent.com`).
6. آن رشته را به **سرور CoinePro-FX** بدهید. کاری که آن‌ها می‌کنند:
   - در `GOOGLE_OAUTH_CLIENT_IDS` بنشیند (برای تأیید `aud` توکن)،
   - و `auth/methods` دوباره `"google": true` با همان `google_client_id` بدهد.
   خودشان نوشته‌اند که یک چک ۶‌ساعته دارند، پس به‌محض معتبرشدن، `google` خودش `true` می‌شود.

> **تریدیار Web client لازم ندارد** — مال او زنده و درست است
> (`…-07nqc4h9j1agsrcrpvq7cgsa5k6evced`). فقط از Android client بالا سود می‌برد.

### ۲.۴ — صفحهٔ رضایت

اگر `OAuth consent screen` هنوز پیکربندی نشده، گوگل قبل از ساخت client می‌فرستدتان آنجا:
User type **External**، نام اپ، ایمیل پشتیبانی، و در Scopes فقط `email`، `profile`، `openid`.
حالت **Testing** برای تست کافی است ولی فقط برای حساب‌هایی که در Test users اضافه کرده‌اید؛
برای همه، **Publish app** را بزنید.

**تأیید:** ورود با گوگل را روی گوشی بزنید. اگر باز هم نشد، خود اپ حالا در متن خطا package و
SHA-1 همان نصب را نشان می‌دهد — همان دو مقدار را با آنچه در کنسول ثبت کرده‌اید مقایسه کنید.

**در گیت‌هاب چیزی لازم نیست.** این یکی از سمت سرور می‌آید.

---

## ۳) پین TLS — از ۴.۵۶.۰ در خود build است؛ کار شما فقط تمدید تاریخ است

**چه شد:** اندازه‌گیری از همین محیط انجام شد و درست درآمد — proxy این‌جا TLS را باز نمی‌کند
(صادرکننده‌ای که دیدیم خودِ Let's Encrypt و Google Trust Services بود، نه proxy). لیف تریدیار دقیقاً
`RO8Xwx…` بود، همان که خودشان داده بودند. پس پین‌ها **به‌عنوان پیش‌فرض داخل `app/build.gradle.kts`**
گذاشته شدند و هر build انتشار با آن‌ها ساخته می‌شود؛ متغیر `COINEPRO_CERTIFICATE_PINS` اگر ست شود
پیش‌فرض را کنار می‌زند، اگر نه پیش‌فرض می‌رود.

| میزبان | پین اصلی | پین پشتیبان |
|---|---|---|
| `tradeyar.trade-future.ir` | لیف: `RO8XwxTQmKWLxQ7Ij7dkTd5vWTS4aC2pROWNg3Sh25c=` (تا ۶ نوامبر ۲۰۲۶؛ با `reuse_key` همان کلید می‌ماند) | کلید آفلاین تریدیار: `Q1JB2C45jMeyX4xQi8ZE83kmB+EfduUc2utHJ+H6YHI=` |
| `coineprofx.com` | intermediate «GTS WE1»: `kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4=` (تا فوریه ۲۰۲۹) | ریشهٔ «GTS Root R4»: `mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c=`، به‌علاوهٔ GTS Root R1 و ISRG Root X1/X2 برای روزی که Cloudflare صادرکننده را عوض کند |

**تاریخ انقضای پین‌ها:** `2027-03-31`. بعد از آن اپ پینر را نصب نمی‌کند و به trust store برمی‌گردد.
**هر انتشار این تاریخ را جلو ببرید** — یا با متغیر `COINEPRO_CERTIFICATE_PINS_UNTIL`، یا با تغییر
`DEFAULT_CERTIFICATE_PINS_UNTIL` در `app/build.gradle.kts`.

### ۳.۱ — دربارهٔ `coineprofx.com`

پشت Cloudflare است و لیف را بدون اطلاع عوض می‌کند، پس لیف پین نشده؛ پین روی intermediate و ریشهٔ
Google Trust Services است — همان دو پینی که خود سرور فارکس تولید کرده بود. این یعنی «فقط گواهی‌ای
که Google Trust Services (یا Let's Encrypt) برای این دامنه صادر کند»، که از «هر CA در trust store
اندروید» تنگ‌تر است ولی از پین لیف گشادتر. شما خواستید پین شود؛ این تنگ‌ترین پینی است که با
Cloudflare Universal SSL زنده می‌ماند. اگر روزی Cloudflare به CA سومی برود (SSL.com)، تا تاریخ بالا
اپ به فارکس وصل نمی‌شود — تاریخ برای همین است. راه بهتر همان است که در `docs/security/PINNING.md`
آمده: Custom Certificate در Cloudflare با کلید خودتان.

### ۳.۲ — تأیید بعد از نصب

اپ را نصب کنید، یک صفحهٔ کریپتو و یک صفحهٔ فارکس باز کنید. اگر قیمت‌ها می‌آیند، پین‌ها درست‌اند.
اگر یکی جواب نداد، `COINEPRO_CERTIFICATE_PINS_UNTIL` را روی دیروز بگذارید و build بگیرید — همان
build بدون پین می‌شود.

## ۴) فونت — دو فایل IRANYekanX که فقط شما دارید

اپ فقط `iranyekanx_regular.ttf` و `iranyekanx_bold.ttf` را دارد. وزن‌های Medium (500) و SemiBold (600)
که برنامه در عنوان‌ها و ارقام می‌خواهد، فعلاً روی Bold می‌افتند (`CoineProType.kt`, عمداً — که یک
عنوان بی‌صدا نازک نشود). فایل‌های `IRANYekanX-Medium.ttf` و `IRANYekanX-SemiBold.ttf` زیر مجوز
شماست و در مخزن نیست؛ نمی‌توانم از جایی بیاورم.

**کار شما:** دو فایل را با نام‌های `iranyekanx_medium.ttf` و `iranyekanx_semibold.ttf` در
`core/designsystem/src/main/res/font/` بگذارید. بعد از آن دو خط `Font(R.font.iranyekanx_bold,
FontWeight.Medium)` و `…SemiBold)` در `CoineProType.kt` را به فایل‌های جدید اشاره دهید؛
`check_tabular_digits` در gate هر فایل جدید را هم می‌سنجد که ارقام لاتینش هم‌عرض باشند.

## بعد از هر سه

یک build بگیرید تا متغیرها وارد شوند: **Actions** ← workflow انتشار ← **Run workflow**.
تا وقتی هیچ‌کدام از این متغیرها ست نشده باشند، build دقیقاً همان چیزی است که امروز هست.

## Certificate pins expire on 2027-03-01 (4.57.0)

The app ships pinned to both API hosts (`docs/security/PINNING.md`). Before 2027-03-01 a release
must re-measure the two hosts and move `DEFAULT_CERTIFICATE_PINS_UNTIL` in `app/build.gradle.kts`,
or the app goes unpinned from that date. TradeYar: keep `reuse_key = True` on certbot, or send the
new key's digest a release ahead. CoinePro-FX: nothing to do unless Cloudflare moves the edge
certificate to a CA other than Google Trust Services or Let's Encrypt.
