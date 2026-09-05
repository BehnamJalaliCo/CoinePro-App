# اخبار تکان نمی‌خورد — یک اندازه‌گیری که تکلیف را روشن می‌کند

> **پاسخ گرفت — ۲۰۲۶-۰۹-۰۵** <!-- ANSWERED-2026-09-05 -->
>
> با یک هفته دادهٔ دیتابیس جواب داده شد، نه سه نمونه: ۲۴ تا ۴۹ خبر در روز، میانگین فاصلهٔ دو خبر ۴۵ دقیقه. **ولی بیشترین فاصله ۶۶۲ دقیقه است** — پنجرهٔ شبانه (آخرین خبر حوالی ۱۹:۳۸). گزارش «اخبار تکان نمی‌خورد» اگر شبانه بوده، همین است و باگ نیست. خالی هم واقعاً ۲۰۰ با آرایهٔ خالی است، نه ۵۰۰.


**۱۴۰۵/۰۶/۰۸ · ۲۰۲۶-۰۸-۳۰**
**از:** اپ اندروید پرو چارت
**به:** تریدیار — `GET api/mobile/v1/market-intelligence`
**موضوع:** صاحب محصول سه بار گفته است «اخبار اصلاً آپدیت نمی‌شود، همان چیزی است که
از ورژن ۱ بوده»

این سند فقط مرز HTTP را می‌نویسد. هیچ توکن، هیچ کلید، هیچ نام میزبان داخلی.

---

## کوتاهش

سه چیز را می‌دانیم و یکی را نمی‌دانیم.

۱. **اپ هیچ کش خبری ندارد.** نه دیسک، نه دیتابیس، نه کش HTTP. هرچه روی صفحه است
   در همان اجرا از سرور آمده. این را پایین‌تر از روی کد خودمان نشان می‌دهیم، نه
   با ادعا.
۲. **جدول خبر شما زنده است.** مسیر عمومی `api/v1/news/list` را همین امروز خودمان
   خواندیم: ۲۰ ردیف، مرتب نزولی، تازه‌ترین `publishedAt` برابر
   `2026-08-30T12:37:54.012304+00:00`. یعنی `news_posts` مرتب پر می‌شود.
۳. **یک اشکال سمت خودمان بود و اصلاح شد.** اپ فهرست خبر را به همان ترتیبی که
   می‌رسید نشان می‌داد و خودش مرتب نمی‌کرد. اگر آداپتور شما به هر ترتیبی جز نزولی
   جواب بدهد، قدیمی‌ترین خبرها برای همیشه بالای فهرست می‌مانند و هر خبر تازه زیر
   خط تا می‌افتد — که دقیقاً همان چیزی است که کاربر می‌بیند و می‌گوید «آپدیت
   نمی‌شود». حالا اپ خودش مرتب می‌کند و این حالت دیگر ممکن نیست.

**آنچه نمی‌دانیم:** پاسخ `api/mobile/v1/market-intelligence` چیست. آن مسیر توکن
می‌خواهد و ما از بیرون فقط `401` می‌گیریم. مسیر **هست** — پیش‌تر در
`BACKEND_ROUTE_MAP.md` نوشته بودیم «ندارید» و آن حرف دیگر درست نیست، چون بدون
توکن `401` می‌دهد نه `404`.

پس یک پرسش می‌ماند و یک اندازه‌گیری جوابش است. بخش «۳» پایین.

---

## ۱. اپ کجا و چطور خبر را می‌گیرد

### مسیر

`core/marketintel/.../MarketIntelGateway.kt`:

```kotlin
private interface MarketIntelApi {
    @GET("user/mobile/market-intelligence")
    suspend fun forexSnapshot(): MarketIntelSnapshotDto

    @GET("api/mobile/v1/market-intelligence")
    suspend fun cryptoSnapshot(): MarketIntelSnapshotDto
}
```

کاربر کریپتو فقط و فقط سطر دوم را صدا می‌زند. هیچ ادغامی با فید فارکس نیست.

### هیچ کشی در کار نیست

کلاینت HTTP اپ، `core/network/.../NetworkFactory.kt` — سازنده را کامل بخوانید و
ببینید هیچ `.cache(...)` ندارد:

```kotlin
val builder = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .pingInterval(20, TimeUnit.SECONDS)
    .apply { recorder?.let(::addInterceptor) }
    .addInterceptor(auth)
```

OkHttp بدون `Cache` هیچ پاسخی را نگه نمی‌دارد — هر `Cache-Control` که بفرستید،
حتی `max-age` بسیار بلند، در اپ اثری ندارد. جدول‌های Room اپ هم فقط `cached_market_quotes`،
`cached_signal_history`، `cached_signal_targets`، `cache_metadata`، `journal`،
`paper trades`، `scripts` و `candles` هستند؛ **هیچ جدول خبری وجود ندارد**. تنها
چیزی که روی دیسک نوشته می‌شود «خبرهای ذخیرهٔ خودِ کاربر» است، که فهرست جداگانه‌ای
است و فید را دست نمی‌زند.

### هر بار که صفحه باز شود دوباره می‌خوانَد

`feature/news/.../NewsScreen.kt`:

```kotlin
LaunchedEffect(controller) { controller.refresh() }
```

و `MarketIntelController.refresh()` هیچ راهی برای قفل شدن ندارد: نگهبانِ ابتدای
آن روی `loading`/`refreshing` است و هر دو خروجی، هم موفق و هم ناموفق، آن دو را
پاک می‌کنند:

```kotlin
scope.launch {
    runCatching { gateway.snapshot() }
        .onSuccess { snapshot ->
            mutableState.value = MarketIntelState(
                news = snapshot.news,
                calendar = snapshot.calendar,
                serverTime = snapshot.serverTime,
                calendarSource = snapshot.calendarSource,
            )
            onSnapshot(snapshot)
        }
        .onFailure { error ->
            val latest = mutableState.value
            mutableState.value = latest.copy(
                loading = false,
                refreshing = false,
                error = error.serverTextOrNull(),
            )
        }
}
```

`scope` عمرِ خودِ اپلیکیشن را دارد و هرگز کنسل نمی‌شود، پس بدنهٔ `launch` همیشه
اجرا می‌شود. علاوه بر این، کشیدنِ فهرست به پایین (pull-to-refresh) و دکمهٔ
«به‌روزرسانی» هر دو همین `refresh()` را صدا می‌زنند.

### با پاسخ چه می‌کند

```kotlin
internal fun MarketNewsDto.toDomain(): MarketNewsItem? {
    val safeId = id?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val safeTitle = title?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val safeSource = source?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val published = parseInstant(publishedAt) ?: return null
    …
}
```

یعنی ردیفی که `id` یا `title` یا `source` یا `published_at` نداشته باشد **حذف
می‌شود و هیچ‌جا دیده نمی‌شود**. اگر خبر می‌فرستید و کاربر نمی‌بیند، اول همین چهار
فیلد را نگاه کنید.

`published_at` با سه قالب خوانده می‌شود و نه بیشتر: `2026-08-30T12:00:00Z`،
`2026-08-30T12:37:54.012304+00:00` و `2026-08-30T12:00:00`. عدد میلی‌ثانیه خوانده
**نمی‌شود** و ردیفش می‌افتد.

و از این نسخه، ترتیب را خودِ اپ تعیین می‌کند:

```kotlin
news = news.mapNotNull(MarketNewsDto::toDomain).sortedByDescending(MarketNewsItem::publishedAt),
```

---

## ۲. آنچه خودمان اندازه گرفتیم — مسیر عمومی، همین امروز

```
GET https://tradeyar.trade-future.ir/api/v1/news/list?type=news&limit=20
→ HTTP 200
```

- تعداد ردیف: 20
- تازه‌ترین `publishedAt`: `2026-08-30T12:37:54.012304+00:00`
- قدیمی‌ترین `publishedAt` در همین ۲۰ تا: `2026-08-29T13:36:52.639536+00:00`
- ترتیب: نزولی، درست

پس **دادهٔ خبر روی سرور شما تازه است و لحظه‌به‌لحظه به‌روز می‌شود.** هر مشکلی که
هست، بین `news_posts` و پاسخِ `market-intelligence` است، نه در تولید خبر.

ضمناً هر ردیفِ همین مسیر عمومی `sourceImageUrl` دارد — مثلاً
`https://cryptoslate.com/wp-content/uploads/2026/08/treasury-bond-buybacks-.jpg`.
در `SERVER_ASK_NEWS_MEDIA.md` نوشته بودیم «تریدیار تصویر ندارد»؛ آن حرف غلط بود و
اشتباه از ما بود. اپ حالا این فیلد را روی مسیر عمومی می‌خواند و عکس را نشان
می‌دهد. **درخواست:** همین آدرس را روی `market-intelligence` هم زیر کلید
`image_url` بفرستید تا کاربر واردشده هم عکس داشته باشد.

---

## ۳. آن یک اندازه‌گیری که تکلیف را روشن می‌کند

با یک توکن معتبر، یک درخواست:

```
GET api/mobile/v1/market-intelligence
Authorization: Bearer <access_token>
```

و سه عدد از پاسخش:

| چه چیزی | چرا |
|---|---|
| `len(news)` | تعداد ردیفی که واقعاً برمی‌گردد |
| `max(news[].published_at)` | تازه‌ترین خبری که به کاربر واردشده می‌رسد |
| `news[0].published_at` و `news[-1].published_at` | ترتیبِ واقعیِ آداپتور |

**و آن را با عدد بالا مقایسه کنید:** تازه‌ترین `publishedAt` روی مسیر عمومی
`2026-08-30T12:37:54Z` است.

- اگر `max(news[].published_at)` هم امروز باشد → دادهٔ شما درست است و ترتیب،
  تنها متغیر باقی‌مانده بود؛ اصلاحِ سمت اپ (بخش ۱) کار را تمام می‌کند و این پرونده
  بسته است.
- اگر تاریخِ کهنه‌ای باشد → مشکل داخل خودِ آداپتور است، و سه جای محتمل دارد:
  1. **کوئری روی جدول یا کلیدِ کشِ دیگری است** و نه `news_posts` زنده.
  2. **فیلترها ردیف‌های تازه را رد می‌کنند** — `status = 'published'` و
     `content_type = 'news'`. اگر ردیف‌های تازه با `content_type` دیگری نوشته
     می‌شوند، فقط ردیف‌های قدیمیِ نسل اول از فیلتر رد می‌شوند و پاسخ تا ابد ثابت
     می‌ماند.
  3. **خودِ مسیر کش دارد** و TTL آن بسیار بلند است. اگر چنین است، فقط بگویید
     چند ثانیه؛ اپ کش نمی‌کند و چیزی که شما نگه دارید دقیقاً همان چیزی است که
     کاربر می‌بیند.

اگر پاسخ‌دادن به این سه سخت است، یک خروجی خام از همان درخواست هم کافی است —
بدون هدرِ `Authorization`، فقط بدنه.

---

## چیزی که لازم **نیست**

- مسیر تازه نمی‌خواهیم.
- خط خبری تازه نمی‌خواهیم؛ همان `news_posts` که همین حالا کار می‌کند کافی است.
- صفحه‌بندی نمی‌خواهیم؛ اپ ۲۰ تا ۵۰ ردیف را یک‌جا می‌گیرد.
