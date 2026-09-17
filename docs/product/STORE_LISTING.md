# Store listing

The words this app is described by outside itself. Kept here rather than in a console so that the
positioning and the copy in the product cannot drift apart — every line below is the same sentence
as a string in `values/` and `values-fa/`, and the key is named beside it.

## Short description

* **فارسی** — «خانه‌ی تحلیل کریپتو — با یک نگاه به طلا و دلار» (`brand_positioning`)
* **English** — "Crypto analysis, with an eye on gold and the dollar" (`brand_positioning`)

## The one line under it

* **فارسی** — «نسخه‌ی کامل، رایگان برای همه — بدون محدودیت، بدون آگهی» (`brand_positioning_free`)
* **English** — "The full app, free for everyone — no limits, no ads" (`brand_positioning_free`)

That second line is a promise about the build, not a campaign: it is true exactly while
`FeatureFlags.allUnlocked` is true, and it comes out of the listing on the day that changes. See
`docs/product/MONETISATION.md`.

## What this app is not

One sentence, in the listing and in the product, word for word (`menu_no_trading`, drawn at the
foot of the menu):

* **فارسی** — «این اپ معامله انجام نمی‌دهد و کارمزدی دریافت نمی‌کند.»
* **English** — "This app does not trade and takes no commission."

It leads the listing's body on both stores. A reader arriving from a search for a trading app is
entitled to know before they install, and a reader who installed anyway finds the same sentence in
the same words on the menu rather than discovering it from an absence.

## Long description — فارسی

چارت حرفه‌ای برای بازار کریپتو، با همان ابزارهایی که روی دسکتاپ انتظار دارید: بیش از ۲۴۰ اندیکاتور،
ابزارهای ترسیم، هشدار قیمت، دیده‌بان چندگانه، ریپلی بازار و زبان اسکریپت‌نویسی نمااسکریپت.

طلا، دلار، شاخص‌ها و جفت‌ارزها هم هستند — برای تحلیل، نه معامله. هر کاری که روی یک نماد کریپتو
می‌کنید، روی XAUUSD هم می‌کنید: چارت، اندیکاتور، ترسیم، هشدار، اسکریپت و ریپلی.

همه‌چیز فارسی است، و همه‌چیز رایگان.

## Long description — English

A professional chart for the crypto market, with the tools you would expect on a desktop: over 240
indicators, drawing tools, price alerts, several watchlists, market replay and the NamaScript
language.

Gold, the dollar, the indices and the currency pairs are here too — to analyse, not to trade.
Everything you can do to a coin you can do to XAUUSD: chart it, put indicators on it, draw on it,
alert on it, script it and replay it.

All of it in Persian, and all of it free.

## What the listing must not say

* No price, no tier, no "premium".
* No claim about a spread, a fee or a bonus at any venue. The app does not know those and would be
  wrong about them within a month — the same rule `TradePartners` follows.
* No forecast, no win rate, no "signals that make money". The signal layer states a confidence and
  the app is not going to promise an outcome in a shop window.
