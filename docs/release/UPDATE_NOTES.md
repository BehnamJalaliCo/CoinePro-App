# Update notes — the sentence a reader actually sees

**This file is the source of `notes_fa` and `notes_en` in the update document**
(`pro-chart.com/api/app/latest`, `docs/web/SERVER.md` §4.6). Whoever writes that document copies
the newest entry below, verbatim, into the two fields.

It exists because those two fields were being written by whoever happened to be publishing, from
whatever material was to hand — and what was to hand was a release body that is boilerplate and a
`CHANGELOG.md` that stopped at 0.2.0. The update card is the one place this product speaks to a
reader about itself; it should not be written by accident.

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
