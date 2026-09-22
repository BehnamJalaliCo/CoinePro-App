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
