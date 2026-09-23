# Update notes — the sentence a reader actually sees

**This file is the source of `notes_fa` and `notes_en` in the update document**
(`pro-chart.com/api/app/latest`, `docs/web/SERVER.md` §4.6). Whoever writes that document copies
the newest entry below, verbatim, into the two fields.

It exists because those two fields were being written by whoever happened to be publishing, from
whatever material was to hand — and what was to hand was a release body that is boilerplate and a
`CHANGELOG.md` that stopped at 0.2.0. The update card is the one place this product speaks to a
reader about itself; it should not be written by accident.

---

## This file is parsed. Its shape is a contract.

`app-latest.py` on the Pro Chart server reads it directly — nobody retypes these sentences — so the
format below is an interface, not a layout preference:

* **`## MAJOR.MINOR.PATCH`** is the key, and it is the **released** version, not the build. `5.0.0+4`
  reads `## 5.0.0`. A note per build would be a note nobody writes.
* Under it, **`**fa**`** and **`**en**`**, each followed by a **blockquote**.
* The server strips the `>`, joins the lines with a space, and puts the result in `notes_fa` /
  `notes_en` **byte for byte**. Nothing else in the entry is read; prose outside the blockquotes is
  for whoever is editing.

`scripts/release/check-update-notes.py` parses it the same way and fails the build if the version
being released has no entry, if an entry is missing a language, if the Persian breaks the house
orthography, or if either language runs past 400 characters. It runs in both workflows and prints
what a reader will see, so a CI log answers «what is on the card?».

Verified against the server's own reading on 2026-09-19: 5.0.0 is **244** characters of Persian and
**274** of English on both sides, identical.

## How to write one

* **Two or three sentences.** It is a card on a screen, not a changelog. Somebody deciding whether
  to spend twenty megabytes on a metered connection is the reader.
* **What changed for them**, not what changed in the repository. «The chart no longer stretches on a
  tablet» is a note; «refactored `ChartSkeletonGrid`» is not.
* **Both languages say the same thing.** Not a translation exercise — write each so it reads as if
  it were the original, and leave out of both anything you cannot say in both.
* **The house rules apply**: «به‌روز» not «بروز», ZWNJ where it belongs, «نسخه‌ی» not «نسخهٔ», Latin
  digits for figures. `tools/i18n/lint_strings.py` does not read this file, so the care has to be
  yours.
* Empty is allowed. The card then shows the version and the digest alone, which is honest for a
  build with nothing to say.

---

## 5.9.1

**fa**

> کل برنامه‌ی پرو چارت در مرورگر هم باز می‌شود: دیده‌بان، رصد، ایده‌ها، ابزارها، اخبار و بقیه‌ی صفحه‌ها، با همان کدی که روی گوشی اجرا می‌شود.
> در این نسخه‌ی گوشی چیزی عوض نشده است.

**en**

> The whole Pro Chart app opens in a browser: the watchlist, Rasad, ideas, tools, news and every other screen, from
> the same code that runs on your phone. Nothing on the phone has changed in this version.

## 5.9.0

**fa**

> حالا کل برنامه‌ی پرو چارت در مرورگر هم باز می‌شود، نه فقط نمودار: دیده‌بان، رصد، ایده‌ها، ابزارها، اخبار و بقیه‌ی صفحه‌ها، با همان کدی که روی گوشی اجرا می‌شود.
> در این نسخه‌ی گوشی چیزی عوض نشده است.

**en**

> The whole Pro Chart app now opens in a browser, not only the chart: the watchlist, Rasad, ideas, tools, news and
> every other screen, from the same code that runs on your phone. Nothing on the phone has changed in this version.

## 5.8.1

**fa**

> نمودار پرو چارت حالا در مرورگر هم باز می‌شود، با همان کدی که روی گوشی نمودار را می‌کشد، و بدون ثبت‌نام.
> در این نسخه‌ی گوشی چیزی عوض نشده است.

**en**

> The Pro Chart chart now opens in a browser too, drawn by the same code that draws it on your phone, with
> no sign-up. Nothing on the phone has changed in this version.

## 5.8.0

**fa**

> ویجت دوم: **یک بازار**، در یک کاشی کوچک روی صفحه‌ی گوشی، با قیمتی که از دور هم خوانده می‌شود.
> وقتی می‌گذاریدش می‌پرسد کدام بازار — از میان همان‌هایی که ستاره کرده‌اید — و هر تعداد که بخواهید
> می‌توانید بگذارید. اگر آن بازار از فهرست‌تان برود، کاشی همین را می‌گوید و بازار دیگری را جای آن
> نشان نمی‌دهد.

**en**

> A second widget: **one market**, as a small tile on your home screen, with a price you can read
> from across the room. Placing it asks which market — from the ones you have starred — and you can
> place as many as you like. If that market leaves your list, the tile says so rather than quietly
> showing a different one.

## 5.7.0

**fa**

> روی چارت، «بیشتر» یک کاشی تازه دارد: **تماشای پیوسته**. بازار را در یک پنجره‌ی کوچک گوشه‌ی صفحه
> می‌گذارد که وقتی از برنامه بیرون می‌روید هم می‌ماند — نماد، قیمت، تغییر، یک خط کوتاه و شمارش
> معکوس کندل. چیز دیگری رویش نیست، چون در آن اندازه خوانده نمی‌شود. با یک ضربه به خود چارت
> برمی‌گردید.

**en**

> The chart's «more» sheet has a new tile: **Keep watching**. It puts the market in a small window
> in the corner of the screen that stays there when you leave the app — the ticker, the price, the
> change, a short line and the candle's countdown. Nothing else, because nothing else is readable
> at that size. One tap takes you back to the chart.

## 5.6.0

**fa**

> حالا هر صفحه‌ای که تصویر ذخیره‌شده نشان می‌دهد، **می‌گوید چقدر قدیمی است** — چارت، دیده‌بان، اخبار
> و تقویم: «ذخیره‌شده · ۲ ساعت پیش». کم‌رنگ شدن می‌گفت «زنده نیست»؛ این می‌گوید ده دقیقه یا دو روز،
> که تصمیم را عوض می‌کند. و هشداری که ساخته‌اید ولی هنوز یک‌بار هم با بازار مقایسه نشده، به‌جای
> «فعال» می‌گوید **هنوز بررسی نشده**.

**en**

> Every screen that shows you a saved picture now **says how old it is** — the chart, the
> watchlist, news and the calendar: «Saved · 2 hours ago». Dimming said «not live»; this says ten
> minutes or two days, which is what changes the decision. And an alert you armed that has never
> once been compared against a market now says **not checked yet** instead of «armed».

## 5.5.0

**fa**

> **دوئل با گذشته**: هر روز یک لحظه از یکی از بازارهای خودتان، با بیست کندل بعدش پنهان. فقط یک
> سؤال — بالا یا پایین — و بعد از جواب، همان بیست کندل باز می‌شود. اگر بازار تکان معناداری نخورده
> باشد، جواب شما **نه درست است نه نادرست**؛ نویز خوانش نیست. روزی یک دور، و تا پنج دور واقعی
> کامل نشود نرخ درستی نشان داده نمی‌شود.

**en**

> **Duel with the past**: each day, one moment from a market you actually watch, with the next
> twenty candles hidden. One question — up or down — and the moment you answer, those twenty
> candles appear. If the market barely moved, your call is **neither right nor wrong**; noise is
> not a read. One round a day, and no hit rate until five rounds the market actually answered.

## 5.4.0

**fa**

> **هفته‌ی من** بالای دفترچه‌ی معاملات آمد: چند معامله بستید، چندتا برنده بود، چند هشدار به صدا درآمد
> و چند جلسه بازپخش کردید — از شنبه، چون هفته‌ی شما شنبه شروع می‌شود. اگر کمتر از ۵ معامله بسته باشید
> **درصد برد نشان داده نمی‌شود** و به‌جایش شمارش می‌آید؛ یک نتیجه رقم را آن‌قدر جابه‌جا می‌کند که
> دیگر معنایی ندارد. هفته‌ی خالی هم چهار تا صفر نیست، یک جمله است. و می‌توانید هفته را هم‌رسانی کنید.

**en**

> **My week** now sits at the top of the journal: trades closed, how many won, alerts that went off,
> replay sessions — from Saturday, because that is when your week starts. Under five closed trades
> the **win rate is not shown** and the count is shown instead; one outcome moves the figure too far
> for it to mean anything. An empty week is a sentence, not four zeros. And you can share the week.

## 5.3.0

**fa**

> کندلی که به‌طور غیرعادی بزرگ است حالا زیر خودش یک نقطه‌ی کوچک دارد، در همان نوار زیر چارت که
> نشانه‌های رویداد آنجا هستند. با زدنش می‌بینید چقدر بزرگ بوده نسبت به معمولِ اخیرِ همان چارت، از باز
> تا بسته چقدر حرکت کرده، و چه چیزی در تقویم و اخبار **داخل همان کندل** افتاده. اگر چیزی نیفتاده،
> همین را می‌گوید — نزدیک‌ترین خبر را به‌عنوان «دلیل» جا نمی‌زند.

**en**

> An unusually large candle now carries a small dot beneath it, in the same strip the event glyphs
> live in. Tapping it says how big the bar was against that chart's own recent normal, how far it
> moved open to close, and what was on the calendar and the wire **inside that bar**. Where nothing
> was, it says so — rather than putting the nearest headline under «why».

## 5.2.0

**fa**

> **رصد صبح**: اگر بخواهید، روزی یک‌بار در ساعتی که خودتان انتخاب می‌کنید، یک اعلان می‌گوید فهرست
> شما چه وضعی دارد، کدام بازار بیشتر تکان خورده و چارتش چه می‌گوید — با یک خط کوچک از شب همان بازار.
> پیش‌فرض خاموش است؛ در «اعلان‌ها» روشنش کنید. در شب آرام هم می‌آید و همین را می‌گوید، چون اعلانی که
> بعضی روزها نمی‌آید با اعلان خراب فرقی ندارد.

**en**

> **The morning brief**: once a day, at an hour you pick, one notification says how your watchlist
> stands, which market moved most and what its chart says — with a small line of that market's
> night. Off until you switch it on, under Notifications. It arrives on a quiet night too, and says
> so: a brief that sometimes does not come is indistinguishable from a broken one.

## 5.1.0

**fa**

> اگر روی یک ترسیم هشدار گذاشته باشید و بعد آن ترسیم را پاک کنید، برنامه پیش از پاک‌کردن می‌پرسد:
> هشدارها هم بروند یا بمانند. هشداری که ترسیمش رفته دیگر هرگز به صدا درنمی‌آمد و در فهرست هم
> «فعال» به نظر می‌رسید؛ حالا در مرکز هشدارها نشان‌دار می‌شود. کنار هر هشدارِ ترسیمی هم طرح کوچکی
> از همان خط دیده می‌شود. و «برگرداندن» بعد از پاک‌کردن یک ترسیم، بالاخره واقعاً برش می‌گرداند.

**en**

> Put an alert on a drawing, then delete the drawing, and the app now asks whether the alerts
> should go with it. An alert whose drawing is gone could never fire again while still reading as
> live in the list; it is marked now. Each drawing alert also carries a small sketch of the line it
> watches. And undo after deleting a drawing finally brings the drawing back.

## 5.0.6

**fa**

> یک کلید ریشه‌ی تازه به فهرست گواهی‌های مورد اعتماد برنامه اضافه شد. چیزی روی صفحه عوض نمی‌شود؛
> این کار می‌کند که روزهای آینده، وقتی گواهی سرور عوض شود، برنامه از کار نیفتد.

**en**

> A new root key was added to the certificates this app is built to accept. Nothing changes on
> screen; it keeps the app from locking itself out of its own server when the certificate changes.

## 5.0.5

**fa**

> اگر ورود به مشکل بخورد، حالا برنامه می‌گوید کدام مشکل: پاسخی نرسید، یا سرور خطا داد، یا پاسخ آمد
> و برنامه نتوانست بخواندش. مورد سوم یک ایراد واقعی بود که همین‌جا پیدا و درست شد.

**en**

> When sign-in fails the app now says which failure it was: no answer came, the server returned a
> fault, or the answer arrived and the app could not read it. That third one was a real bug, found
> and fixed here.

## 5.0.4

**fa**

> شماره‌ی نسخه از این به بعد ساده است: ۵.۰.۴، نه ۵.۰.۳+۱. هر نسخه‌ای که منتشر می‌شود شماره‌ی خودش
> را دارد، پس هر چه روی گوشی‌تان نوشته شده همان است که در فهرست نسخه‌ها پیدا می‌کنید.

**en**

> Version numbers are plain from now on: 5.0.4, not 5.0.3+1. Every published build gets its own
> number, so what the app shows you is exactly what you will find in the list of releases.

## 5.0.3

**fa**

> پشتیبان‌گیری حالا چیدمان‌های نمودار و دفترچه‌ی معاملات را هم می‌برد، نه فقط دیده‌بان و
> اسکریپت‌ها. هر چه در یادداشت‌هایتان نوشته‌اید همان‌طور برمی‌گردد — با هر نویسه و هر خط.

**en**

> A backup now carries your chart layouts and your trading journal too, not just your watchlist
> and your scripts. Whatever you wrote in a note comes back exactly as you wrote it, every
> character and every line of it.

## 5.0.2

**fa**

> زیر نمودار می‌نوشت «منبع قیمت» و فقط نام منبع کندل‌ها را می‌آورد. روی فارکس این دو یکی نیستند:
> کندل‌ها از MetaTrader 5 می‌آیند و آخرین قیمت از Finnhub. حالا هر کدام نام خودش را دارد، تا اگر
> خواستید نمودار را با خودِ آن بازار بسنجید، بدانید کدام را باز کنید.

**en**

> The caption under the chart said «price source» and named only where the candles came from. On
> forex those are not the same: the bars are MetaTrader 5 and the last price is Finnhub. Each is
> now named, so if you want to check this chart against the venue's own you know which to open.

## 5.0.1

**fa**

> صفحه‌ی ورود دیگر خالی نمی‌ماند. اگر برنامه نتواند از سرور بپرسد کدام روش‌های ورود فعال‌اند، ورود
> با ایمیل را نشان می‌دهد و می‌گوید چرا. پیام‌های خطا هم روشن‌تر شده‌اند: حالا فرق «پاسخی نرسید» با
> «سرور پاسخ داد ولی خطا داشت» معلوم است.

**en**

> The sign-in screen no longer comes up empty. If the app cannot ask the server which ways in are
> available, it offers e-mail and says why. The errors are clearer too: «no answer came back» and
> «the server answered with a fault of its own» are now two different sentences.

## 5.0.0

**fa**

> از این نسخه به بعد، خبر نسخه‌ی تازه را خودِ برنامه به شما می‌دهد — در صفحه‌ی «ایمنی و انتشار» —
> چون گوگل‌پلی برای ما این کار را نمی‌کند. همان صفحه اثر انگشت فایل و گواهی نصب شما را هم نشان
> می‌دهد، تا بتوانید بررسی کنید چه چیزی روی گوشی‌تان است.

**en**

> From this version the app tells you itself when a newer one is out — on the «Safety and launch»
> screen — because Google Play does not do it for us. The same screen shows the file's fingerprint
> and the certificate your install carries, so you can check what is on your phone.

---

## 4.99.0

**fa**

> چارت روی تبلت دیگر کشیده نمی‌شود و اسکلت بارگذاری با اندازه‌ی صفحه جور در می‌آید. سیگنال فارکس از
> این پس فقط طلاست.

**en**

> The chart no longer stretches on a tablet, and the loading skeleton fits the screen it is drawn
> on. Forex signals are the gold call and nothing else.
