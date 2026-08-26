# ساخت خودکار APK روی GitHub

هر پوش روی `main` یک APK امضاشده می‌سازد و آن را به یک GitHub Release می‌چسباند.
`.github/workflows/android-apk.yml`.

**نتیجه‌ای که مهم است:** هر نسخهٔ تازه **روی نسخهٔ قبلی نصب می‌شود** و لازم نیست قبلی را حذف کنید.
اندروید این را وقتی اجازه می‌دهد که دو شرط برقرار باشد، و هر دو در همین workflow تأمین شده‌اند:

1. **امضا یکی باشد.** همهٔ buildها با یک کلید ثابت از secretهای مخزن امضا می‌شوند. اگر workflow هر
   بار کلید موقت می‌ساخت، APK بعدی روی قبلی نصب نمی‌شد و کاربر باید هر بار اپ را — با نشست و
   تنظیماتش — پاک می‌کرد.
2. **versionCode همیشه بالاتر برود.** `run_number` فقط زیاد می‌شود؛ آفست `+1000` یک بار اضافه شده و
   دیگر دست نمی‌خورد. آفست هست چون APKهایی که دستی فرستاده شد `versionCode 1` داشتند، پس اولین
   build خودکار از ۱۰۰۱ شروع می‌شود و برای همیشه بالاتر می‌ماند.

نسخهٔ debug اینجا ساخته نمی‌شود، عمداً. دو مسیر یعنی دو امضا، و کسی که از مسیر اشتباه نصب کند دوباره
به پاک‌کردن برمی‌گردد. یک مسیرِ امضاشده، همیشه.

---

## چهار secret که باید یک بار بسازید

**Settings → Secrets and variables → Actions → New repository secret**

| نام secret | مقدارش از کجا می‌آید |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | فایل `ANDROID_KEYSTORE_BASE64.txt` که برایتان فرستاده شد — کلِ محتوایش را یک‌جا paste کنید (یک خط، بدون newline) |
| `ANDROID_KEYSTORE_PASSWORD` | همان مقدارِ `COINEPRO_RELEASE_STORE_PASSWORD` در `local.properties` |
| `ANDROID_KEY_ALIAS` | همان مقدارِ `COINEPRO_RELEASE_KEY_ALIAS` در `local.properties` |
| `ANDROID_KEY_PASSWORD` | همان مقدارِ `COINEPRO_RELEASE_KEY_PASSWORD` در `local.properties` |

سه تای آخر را از فایل `local.properties` روی سیستم خودتان بردارید. آن فایل gitignore شده و هیچ‌وقت
داخل مخزن نمی‌رود؛ همین است که این چهار secret لازم‌اند.

### و یکی اختیاری ولی مهم

| نام secret | چرا |
| --- | --- |
| `GOOGLE_SERVICES_JSON_BASE64` | از فایل `GOOGLE_SERVICES_JSON_BASE64.txt` |

بدون آن، build کار می‌کند ولی **اعلان‌ها کار نمی‌کنند** — `app/google-services.json` هم gitignore شده
و روی runner وجود ندارد. workflow در لاگ هشدار می‌دهد و ادامه می‌دهد، ولی APK بی‌اعلان است.

---

## متغیرهای اختیاری

**Settings → Secrets and variables → Actions → Variables**

| نام | اگر ندهید |
| --- | --- |
| `COINEPRO_PRODUCTION_TERMINAL_URL` | دکمهٔ ترمینال حرفه‌ای در هدر چارت نمایش داده نمی‌شود |
| `COINEPRO_PRODUCTION_API_BASE_URL` | `https://coineprofx.com/api/` |
| `COINEPRO_PRODUCTION_TRADEYAR_API_BASE_URL` | `https://tradeyar.trade-future.ir/` |

دو تای آخر پیش‌فرضِ خودِ `app/build.gradle.kts` هستند و فقط اگر سروری جابه‌جا شد لازم می‌شوند.

---

## بعد از ساخت

هر build یک Release می‌سازد با تگ `apk-<versionCode>`. فایل را از همان‌جا دانلود و نصب کنید.

**اگر «app not installed» دیدید:** نسخه‌ای که روی گوشی است با کلید دیگری امضا شده — یعنی نسخهٔ
debug یا یکی از buildهای دستی. یک بار حذفش کنید؛ از آن به بعد هر build اینجا روی قبلی می‌نشیند.

## چه چیزی قبل از ساختن APK بررسی می‌شود

workflow قبل از انتشار جلوی خودش را می‌گیرد اگر هرکدام از این‌ها رد شود:

* سه گیت کیفیت — سازگاری ماژول‌ها، سیاست حرکت و سطح، و اسکن secret
* کل تست‌های واحد
* امضای APK، با `apksigner verify`
* **اسم فیلدهای درخواست‌ها در dex.** این یکی چون یک بار واقعاً خراب شد: R8 اسم فیلدهایی را که
  Gson با reflection می‌خواند عوض کرده بود و همهٔ ورودها `{"a":…,"b":…}` می‌فرستادند — فقط در
  build ریلیز، و هیچ‌جا چیزی نمی‌گفت. حالا خودِ dex خوانده می‌شود، نه فایل قانون.

اعتبارسنجیِ کلید هم قبل از ده دقیقه build انجام می‌شود: `keytool -list` با همان alias و رمز، تا اگر
secretها اشتباه باشند همان اول معلوم شود.
