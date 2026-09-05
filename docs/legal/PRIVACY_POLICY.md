# سیاست حریم خصوصی — پرو چارت

**آخرین بازنگری:** ۱۴۰۵/۰۶/۰۴

---

## ۱) این اپ چه کاری می‌کند

پرو چارت یک اپلیکیشن **تحلیل و سیگنالِ بازار** است که به دو سرویس مستقل وصل می‌شود:

* **کوین‌پرو اف‌ایکس** — فارکس و فلزات؛ سیگنال، کپی‌تریدینگ، آکادمی و نمودار.
* **تریدیار** — رمزارز؛ سیگنال، اجرای سفارش روی صرافی، نمودار.

این دو **سیستم جدا** با پایگاه کاربران جدا هستند. حساب یکی، حساب دیگری نیست، و توکن یکی برای دیگری
بی‌معناست. اپ هر داده‌ای را به‌ازای هر پلتفرم جدا نگه می‌دارد و خروج از یکی، شما را از دیگری خارج
نمی‌کند.

پرو چارت **کارگزار نیست**، پول شما را نگه نمی‌دارد و مشاورهٔ مالی نمی‌دهد.

---

## ۲) چه داده‌هایی جمع می‌شود

### ۲-۱ آنچه روی دستگاه شما می‌ماند و هرگز ارسال نمی‌شود

| داده | کجا | چرا |
| --- | --- | --- |
| توکن نشست و توکن تازه‌سازی | DataStore رمزنگاری‌شده با کلید AES-GCM در Android Keystore | شما را وارد نگه می‌دارد |
| شناسهٔ نصب (`install_id`) | DataStore معمولی | پایین‌تر توضیح داده شده |
| پلتفرم فعال، زبان، تم | DataStore معمولی | تنظیمات شما |
| کش قیمت و سیگنال | Room | نمایش آخرین وضعیت وقتی شبکه نیست |

کلید رمزنگاری در Android Keystore ساخته می‌شود و **از دستگاه بیرون نمی‌آید**. اگر قفل صفحهٔ دستگاه
عوض شود و کلید باطل شود، اپ به‌جای خطا، شما را از حساب خارج می‌کند.

### ۲-۲ آنچه به سرورهای ما فرستاده می‌شود

| داده | چه وقت | به کدام سرور |
| --- | --- | --- |
| ایمیل و رمز عبور (یا توکن Google) | هنگام ورود یا ثبت‌نام | همان پلتفرمی که وارد می‌شوید |
| توکن نشست در هدر `Authorization` | هر درخواست | همان پلتفرم |
| شناسهٔ نصب در هدر `X-Install-Id` | هر درخواست | همان پلتفرم |
| توکن اعلان Firebase | وقتی اعلان را روشن می‌کنید | همان پلتفرم |
| نام، کد ملی، تاریخ تولد، شماره موبایل | فقط اگر خودتان فرم احراز هویت را پر کنید | کوین‌پرو اف‌ایکس |
| کلید API صرافی | فقط اگر خودتان حساب صرافی وصل کنید | تریدیار |
| شناسهٔ کاربری صرافی (UID) | فقط اگر برای عضویت آن را وارد کنید | تریدیار |
| تصویر نمودار | فقط وقتی خودتان عکس را برای تحلیل می‌فرستید | همان پلتفرم |
| نمادهایی که روی صفحه‌اند | هنگام اتصال به فید قیمت | همان پلتفرم |

**UID صرافی چرا فرستاده می‌شود.** عضویت پرو چارت رایگان است و شرطش داشتن حساب فعال در یکی از
صرافی‌های همکار است (شرایط استفاده، بند ۶). برای احراز این موضوع، UID شما به صرافی داده می‌شود تا
صرافی تأیید کند این حساب به پرو چارت متصل است و موجودی لازم را دارد. آنچه برمی‌گردد یک پاسخ
آری/نه و وضعیت موجودی است؛ پرو چارت با UID شما نه به حساب صرافی دسترسی دارد و نه می‌تواند معامله یا
برداشتی انجام دهد.

**شناسهٔ نصب چیست و چرا هست.** یک رشتهٔ تصادفی است که هنگام اولین اجرا ساخته می‌شود، به هیچ حساب،
دستگاه یا شمارهٔ سریالی وصل نیست و از هیچ شناسهٔ سخت‌افزاری ساخته نشده. تنها کاری که می‌کند این است
که محدودکنندهٔ نرخ سرور بتواند دو نصب پشت یک IP مشترک را از هم تشخیص دهد — بدون آن، یک کاربر پرمصرف
روی شبکهٔ اپراتور، بقیهٔ کاربران همان IP را هم محدود می‌کرد. پاک‌کردن دادهٔ اپ، یک شناسهٔ تازه
می‌سازد.

### ۲-۳ آنچه اصلاً جمع نمی‌شود

* **هیچ SDK تحلیلی، تبلیغاتی یا ردیابی در اپ نیست.** نه Google Analytics، نه Facebook SDK، نه
  AppsFlyer، نه هیچ چیز مشابه. تنها کتابخانهٔ Google در اپ، Firebase Messaging برای اعلان است.
* موقعیت مکانی، دفترچه تلفن، تقویم، میکروفون، فایل‌های شما.
* شناسهٔ تبلیغاتی (`AAID`)، IMEI، MAC، یا هر شناسهٔ سخت‌افزاری.
* **گزارش خطای خودکار.** اپ هیچ SDK کرش ندارد؛ گزارش پایداری فقط از Android Vitals در Play Console
  می‌آید که خود گوگل جمع می‌کند و ما به دادهٔ فردی در آن دسترسی نداریم.

---

## ۳) مجوزهایی که اپ می‌خواهد

| مجوز | برای چه | اگر ندهید |
| --- | --- | --- |
| `INTERNET` | بدون آن اپ کاری ندارد | — |
| `POST_NOTIFICATIONS` | اعلان سیگنال و هشدار | اپ کامل کار می‌کند، فقط اعلان نمی‌گیرید |
| `CAMERA` | فقط برای عکس‌گرفتن از نمودار در «تحلیل تصویری» | بقیهٔ اپ دست‌نخورده کار می‌کند |

دوربین **فقط** وقتی روشن می‌شود که خودتان در صفحهٔ تحلیل تصویری دکمه را بزنید. تصویر پس از ارسال روی
دستگاه نگه داشته نمی‌شود.

---

## ۴) داده با چه کسی به اشتراک گذاشته می‌شود

داده‌های شما **فروخته نمی‌شوند**. اشتراک‌گذاری فقط در این موارد است:

* **Google (Firebase Cloud Messaging)** — توکن اعلان و متن اعلان‌ها از زیرساخت گوگل عبور می‌کند.
  این ذاتی اعلان روی اندروید است.
* **صرافی LBank** — اگر خودتان حساب صرافی وصل کنید، سفارش‌ها با کلید API خودتان از سمت سرور تریدیار
  امضا می‌شوند.
* **بروکر MT5** — اگر کپی‌تریدینگ را فعال کنید.
* **الزام قانونی** — در حدی که قانون حاکم ایجاب کند.

### منابع عمومی خبر و تقویم

وقتی سرور ما خبر یا رویداد تقویمی نفرستد، اپ همان بخش را مستقیم از این منابع عمومی می‌خواند:

* `nfs.faireconomy.media` — تقویم اقتصادی هفتگی
* `investing.com` — خوراک خبری (RSS)
* `cointelegraph.com` — خوراک خبری کریپتو (RSS)

این درخواست‌ها **هیچ توکن، شناسه یا داده‌ای از شما همراه ندارند** و فقط وقتی فرستاده می‌شوند که
سرور خودمان آن بخش را خالی برگردانده باشد. مثل هر درخواست اینترنتی، نشانی IP دستگاه شما برای آن
میزبان قابل مشاهده است؛ چیز دیگری از شما به آن‌ها نمی‌رسد.

---

## ۵) نگهداری و حذف

* **روی دستگاه:** خروج از حساب، توکن نشست و توکن تازه‌سازی را هر دو پاک می‌کند. حذف اپ یا پاک‌کردن
  دادهٔ اپ، همه‌چیز شامل کش و تنظیمات را می‌برد.
* **روی سرور:** دادهٔ حساب تا زمانی می‌ماند که حساب فعال است.

**حذف حساب — دو راه، هر دو بدون تماس با پشتیبانی:**

| راه | کجا |
| --- | --- |
| از داخل اپ | تنظیمات ← حذف حساب کاربری |
| از بیرون اپ | [وبگاه پرو چارت](https://coineprofx.com/legal/delete-account/) |

**چه چیزی حذف می‌شود:** حساب، ایمیل، نام، اطلاعات احراز هویتی که خودتان وارد کرده‌اید، **حساب
آکادمی و پیشرفت درس‌هایتان اگر با همین ایمیل ساخته شده باشد**، UID صرافی،
کلیدهای API صرافی، تنظیمات اعلان، توکن اعلان، تاریخچهٔ سیگنال‌های ذخیره‌شده و هشدارهای قیمت.

**چه چیزی نگه داشته می‌شود و چرا:** سوابق معاملاتی که به‌حکم قانون یا قرارداد صرافی باید نگهداری
شوند، و لاگ‌های امنیتی و ضدتقلب، به‌صورت **بی‌نام** — یعنی بدون ایمیل، نام یا UID شما — برای حداکثر
دوازده ماه. این‌ها دیگر به شما قابل انتساب نیستند.

**چقدر طول می‌کشد:** حذف بلافاصله ثبت می‌شود و ظرف حداکثر ۳۰ روز از پشتیبان‌ها هم پاک می‌شود.

**آنچه حذف نمی‌کند:** حساب شما نزد صرافی. آن حساب مال خودتان است و پرو چارت نه آن را ساخته و نه
می‌تواند حذفش کند؛ برای بستنش به خودِ صرافی مراجعه کنید.

---

## ۶) کودکان

این اپ برای افراد **زیر ۱۸ سال نیست** و ما آگاهانه دادهٔ کودکان را جمع نمی‌کنیم.

---

## ۷) تغییرات

تاریخ بالای همین صفحه، تاریخ نسخهٔ معتبر است. تغییر بااهمیت را از داخل اپ اطلاع می‌دهیم.

---

## ۸) تماس

پشتیبانی در تلگرام: <https://t.me/CoinePro_Admin>

**توسعه‌دهنده و مسئول حقوقی داده‌ها:** بهنام جلالی

---
---

# Privacy Policy — Pro Chart

**Last reviewed:** 2026-08-26

Every claim below was written from this repository's code rather than from a template. If the app's
behaviour changes, this file changes with it.

## 1) What this app is

Pro Chart is a **market analysis and signals** app talking to two independent services — CoinePro-FX
(forex and metals) and TradeYar (crypto). They are separate systems with separate user tables; an
account on one is not an account on the other, and the app keeps everything per platform. Pro Chart
is **not a broker**, does not hold your money, and does not give financial advice.

## 2) What is collected

**Stays on your device, never sent:** session and refresh tokens (in a DataStore encrypted with an
AES-GCM key held in the Android Keystore, which never leaves the device); an install id; your active
platform, language and theme; a cache of the last prices and signals so the app has something to
show when the network does not.

**Sent to our servers:** your email and password or Google token at sign-in; the session token on
every request; the install id in an `X-Install-Id` header; a Firebase notification token if you turn
notifications on; your name, national id, date of birth and phone number *only* if you complete the
identity form; an exchange API key *only* if you connect an exchange account; a chart image *only*
when you send one for analysis; and the list of symbols currently on screen, so the price feed sends
those and not all 441.

**The install id** is a random string generated on first launch. It is not derived from any hardware
identifier and is not linked to an account or a device. Its only job is letting the server's rate
limiter tell two installs behind one shared IP apart — without it, one heavy user on a carrier
network would rate-limit everyone else on the same address. Clearing app data generates a new one.

**Not collected at all:** there is **no analytics, advertising or tracking SDK in this app** — no
Google Analytics, no Facebook SDK, no attribution library. The only Google library present is
Firebase Messaging, for notifications. No location, contacts, calendar, microphone or files. No
advertising id, IMEI or MAC address. No automatic crash reporting: the app ships no crash SDK, and
stability data comes only from Android Vitals in the Play Console.

## 3) Permissions

`INTERNET` (the app does nothing without it), `POST_NOTIFICATIONS` (signal alerts — decline and
everything else still works), and `CAMERA`, used **only** when you press the capture button on the
image-analysis screen. The image is not kept on the device after it is sent.

## 4) Sharing

Your data is **not sold**. It is shared only with Google (Firebase Cloud Messaging carries
notification tokens and payloads — inherent to Android notifications); the LBank or Ourbit exchange
and an MT5 broker if *you* connect those accounts or submit a UID for membership verification; and
where the law requires it.

Where our own server returns no news or no calendar events, the app reads that section directly from
public sources — `nfs.faireconomy.media` for the weekly economic calendar, and the `investing.com`
and `cointelegraph.com` RSS feeds for news. Those requests carry no token, no identifier and nothing
about you. As with any web request the host can see your device's IP address; nothing else reaches
them. A UID check asks the exchange one question — is this account linked to
Pro Chart, and is it funded — and gets back a yes/no and a balance state. It grants no access to your
exchange account.

## 5) Retention and deletion

Signing out clears both tokens. Uninstalling or clearing app data removes everything local.

**Deleting your account — two routes, neither needing support:**

| Route | Where |
| --- | --- |
| In the app | Settings → Delete account |
| Outside the app | [The Pro Chart website](https://coineprofx.com/legal/delete-account/) |

**Deleted:** the account, your e-mail and name, any identity details you entered, **your academy
account and lesson progress where it was created with the same e-mail**, your exchange UID, exchange
API keys, notification preferences and token, saved signal history and price alerts.

**Kept, and why:** trading records that law or the exchange's own contract requires be retained, and
security/anti-fraud logs — both **anonymised**, carrying no e-mail, name or UID, for at most twelve
months. They are no longer attributable to you.

**How long:** the deletion is recorded immediately and is gone from backups within 30 days.

**What it does not delete:** your account at the exchange. That account is yours; Pro Chart neither
created it nor can close it. Ask the exchange.

## 6) Children

Not intended for anyone under 18. We do not knowingly collect children's data.

## 7) Contact

Support on Telegram: <https://t.me/CoinePro_Admin>

**Developer and data controller:** Behnam Jalali

