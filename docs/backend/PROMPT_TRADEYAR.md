# پرامپت برای Claude Code روی سرور تریدیار (کریپتو)

> این متن را همان‌طور که هست به Claude Code روی سرور تریدیار بدهید. هر بخش یک کار مستقل است با
> شرط پذیرش و دستور تأیید. اپ اندروید (Pro Chart) نسخه‌ی **4.42.0** روی این قرارداد بسته شده.

---

## زمینه

اپ اندروید `Pro Chart` (مخزن `BehnamJalaliCo/CoinePro-App`) در نسخه‌ی 4.42.0 دو تغییر داد که به
سمت شما مربوط است:

1. **خبر و تقویم دیگر از روی گوشی از Cointelegraph و ForexFactory خوانده نمی‌شود.** در build
   ی که به Play می‌رود `DIRECT_THIRD_PARTY_FEEDS=false` است. یعنی هر چه روت‌های خودمان بدهند،
   همان است؛ خالی بدهند، صفحه خالی می‌ماند و اپ می‌نویسد «چیزی منتشر نشده».
2. **Play Integrity** روی سه درخواست حساس فعال شد و سرور باید توکنش را تأیید کند.

`{BASE}` در این سند یعنی همان چیزی که اپ در `BuildConfig.TRADEYAR_API_BASE_URL` دارد.

**چیزی که به شما مربوط نیست:** عکس‌های راهنما و لوگوی نمادها از میزبان CoinePro-FX می‌آید
(`BuildConfig.API_BASE_URL`)، نه از شما. اگر پرامپت فارکس را هم دیدید، آن دو کار آنجاست.

---

## کار ۱ — رله‌ی تقویم: `api/v1/public/calendar/week` (اولویت: بالا)

این روت شما، **تنها راه رسیدن تقویم اقتصادی به هر دو پلتفرم** است وقتی روت آکادمی فارکس جواب
ندهد. دلیل وجودش هم همین بود: میزبان خود ForexFactory از ایران جواب نمی‌دهد، شما بایت‌به‌بایت
رله می‌کنید.

**شرط پذیرش**

- عمومی، بدون توکن.
- هر ساعت یک بار فایل بالادست را دوباره بخوانید.
- **هرگز آرایه‌ی خالی برنگردانید در حالی که فایل بالادست ردیف دارد.** آرایه‌ی خالی همان چیزی
  است که قبلاً باعث می‌شد اپ به میزبان خود فایل بیفتد — و حالا که fallback خاموش است، یعنی
  تقویم خالی برای کاربر.
- اگر خواندن بالادست شکست خورد، **آخرین نسخه‌ی موفق را نگه دارید و همان را بدهید**؛ خالی‌دادن
  بدترین حالت است.
- شکل فعلی حفظ شود (اپ همین را پارس می‌کند):

```json
[{"title":"Non-Farm Employment Change","country":"USD",
  "date":"2026-09-04T08:30:00-04:00","impact":"High",
  "forecast":"75K","previous":"73K","actual":null}]
```

**تأیید**

```bash
curl -s {BASE}/api/v1/public/calendar/week | python3 -c "import json,sys; d=json.load(sys.stdin); print(len(d),'events'); print(d[0])"
# انتظار: تعداد بیشتر از صفر برای هفته‌ی جاری
```

---

## کار ۲ — خبر: `api/v1/news/list` و مسیر عضو (اولویت: بالا)

مسیر عمومی `GET {BASE}/api/v1/news/list?limit=30` امروز سالم است: خبر فارسی، خلاصه‌ی فارسی، و
`sourceImageUrl`. این همان چیزی است که کاربر مهمان می‌بیند.

**۲.۱ — تأیید کنید که `source_image_url` روی مسیر عضو هم مستقر شده.**
در `docs/SERVER_ASK_NEWS_MEDIA.md` (پاسخ ۲۰۲۶-۰۹-۰۱) گفته شد که ستون در `SELECT` نبود و اضافه
شد. اگر آن deploy انجام شده، بگویید — چون اپ تا آن موقع یک درخواست اضافه می‌زند تا عکس‌های
جاافتاده را از مسیر عمومی پر کند (`PublicMarketIntel.illustrate`)، و آن درخواست بعد از تأیید
شما حذف می‌شود.

**۲.۲ — پایداری فید.** سؤال قدیمی `docs/SERVER_ASK_NEWS_STALE_TRADEYAR.md` («اخبار تکان
نمی‌خورد») هنوز باز است. حالا که fallback خاموش شده، این مستقیماً یعنی صفحه‌ی اخبار کریپتو
ثابت می‌ماند. یک اندازه‌گیری ساده کافی است:

```bash
for i in 1 2 3; do curl -s "{BASE}/api/v1/news/list?limit=5" \
  | python3 -c "import json,sys;d=json.load(sys.stdin);print([x.get('publishedAt') or x.get('published_at') for x in (d.get('items') or d)])"; sleep 3600; done
```

اگر `published_at` بین سه نمونه جابه‌جا نشد، مشکل در ingest است نه در اپ.

**۲.۳ — خالی یعنی خالی، نه خطا.** اگر واقعاً خبری نیست، `200` با آرایه‌ی خالی بدهید. اپ آن را
«چیزی منتشر نشده» نشان می‌دهد. `500` را به‌عنوان «خطای اتصال» نشان می‌دهد که غلط است.

---

## کار ۳ — تأیید Play Integrity (اولویت: متوسط، امنیتی)

اپ روی درخواست‌هایی که پول یا اعتبارنامه جابه‌جا می‌کنند دو هدر می‌فرستد:

| هدر | مقدار |
|---|---|
| `X-Play-Integrity` | توکنی که Google Play صادر کرده |
| `X-Play-Integrity-Nonce` | nonce که توکن به آن bind شده |

**روت‌های سمت شما که این هدرها را می‌گیرند** (فقط `POST`/`PUT`/`PATCH`):

| مسیر | چه کاری |
|---|---|
| هر مسیری که به `/login` ختم شود | ورود |
| `api/mobile/v1/venues/lbank` | ثبت/به‌روزرسانی کلیدهای صرافی |
| `api/mobile/v1/executions` | اجرای سیگنال — سفارش واقعی |

**nonce چطور ساخته می‌شود** (تا بتوانید بازمحاسبه کنید):

```
nonce = base64url_nopad( SHA-256( "METHOD path minute" ) )
minute = floor(epoch_millis / 60000)
```

`path` همان `encodedPath` درخواست است (مثل `/api/mobile/v1/executions`)، `METHOD` با حروف بزرگ،
یک فاصله بین هر سه جزء.

**کاری که سرور باید بکند**

1. توکن را با Play Integrity API (`decodeIntegrityToken`) و همان Cloud project number باز کنید.
2. چک کنید `requestDetails.nonce` برابر هدر nonce باشد و برابر nonce بازمحاسبه‌شده برای
   **دقیقه‌ی جاری یا دقیقه‌ی قبل** (اپ ممکن است روی مرز دقیقه بیفتد).
3. `requestDetails.requestPackageName == "com.coinepro.app"` و `timestampMillis` تازه باشد.
4. `appIntegrity.appRecognitionVerdict == PLAY_RECOGNIZED` و
   `deviceIntegrity.deviceRecognitionVerdict` شامل `MEETS_DEVICE_INTEGRITY`.
5. سیاست پیشنهادی، از سست به سخت:
   - `login`: verdict بد یا هدر غایب ⟵ **مسدود نکنید**، مرحله‌ی دوم بخواهید.
   - `venues/lbank` (کلید صرافی): verdict بد ⟵ رد کنید.
   - `executions` (سفارش واقعی): verdict بد ⟵ رد کنید، و لاگ کنید.

**نکته‌ی مهم:** اپ هرگز خودش امتناع نمی‌کند. گوشی بدون Play Services، شبیه‌ساز، یا شبکه‌ای که
به Google نمی‌رسد، درخواست را **بدون این دو هدر** می‌فرستد. در بازار ایران این نادر نیست، پس
«هدر ندارد» را با «verdict رد شد» یکی نگیرید — وگرنه بخشی از کاربران واقعی قفل می‌شوند.

سند کامل: `docs/security/INTEGRITY.md` در مخزن اپ.

---

## کار ۴ — پین‌های TLS (اولویت: متوسط، هماهنگی لازم)

اپ `CertificatePinner` دارد و تا وقتی پین ندهیم خاموش است. برای روشن‌کردنش لازم داریم:

- **SHA-256 SPKI pin گواهی فعلی** میزبان API تریدیار.
- **یک پین پشتیبان** برای کلیدی که هنوز صادر نشده. بدون پشتیبان، اولین تمدید گواهی همه‌ی
  کاربران را قفل می‌کند و راه برگشتی جز انتشار نسخه‌ی جدید در Play نیست.

```bash
openssl s_client -connect <host>:443 -servername <host> < /dev/null 2>/dev/null \
  | openssl x509 -pubkey -noout \
  | openssl pkey -pubin -outform der \
  | openssl dgst -sha256 -binary | base64
```

**قرار کاری:** هر تغییر گواهی/کلید باید **حداقل ۳۰ روز قبل** اعلام شود. سند:
`docs/security/PINNING.md`.

---

## چیزهایی که تغییر نکرده و نباید تغییر کند

- هدرهای فعلی اپ سر جایشان است: `Authorization: Bearer`, `X-Install-Id`,
  `X-App-Platform: android`, `X-App-Version`.
- قرارداد `api/mobile/v1/*` (کندل، سیگنال، اجرا، پرتفوی) دست‌نخورده است.
- اپ برای هر پلتفرم یک session و یک توکن جدا نگه می‌دارد؛ توکن فارکس هرگز به میزبان شما
  فرستاده نمی‌شود و برعکس.

## پرسش‌های باز قبلی که هنوز جواب نگرفته‌اند

در مخزن اپ منتظرند: `docs/SERVER_ASK_NEWS_STALE_TRADEYAR.md`،
`docs/SERVER_ASK_TICKER24H_TRADEYAR.md`، `docs/SERVER_ASK_AI_GENERATE_TRADEYAR.md`،
`docs/SERVER_ASKS_DOM.md`، `docs/SERVER_ASK_ONE_ACCOUNT_TWO_BACKENDS.md`.

## خروجی‌ای که از شما می‌خواهیم

یک پیام کوتاه با این چهار چیز:

1. خروجی `curl` تقویم هفته (تعداد رویدادها).
2. تأیید اینکه `source_image_url` روی مسیر عضو مستقر شده یا نه.
3. پین اصلی + پین پشتیبان.
4. Cloud project number ی که Play Integrity را با آن تأیید می‌کنید (اگر روشنش کردید).
