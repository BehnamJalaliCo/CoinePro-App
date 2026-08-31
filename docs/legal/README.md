# The legal documents, and how they were produced

`TERMS.md` and `PRIVACY_POLICY.md` in this directory are the source of truth. They are copied
into the app by `scripts/release/sync-legal-documents.py` and published to the website by
`scripts/site/build-site.py`; nothing edits either copy by hand.

**Nothing in those two files is a note to us.** Both of them used to open with an editors' note
explaining where the text came from — one of them naming a file path in another repository —
and both notes were shipped: they were the first paragraph a reader saw inside the app's own
terms of use, and they were on the public web. A note about how a legal instrument was written
is repository documentation. It lives here.

## The terms

پایهٔ این متن، سند حقوقیِ خودِ مالک برای بازارنما (`Pro-Chart/src/pages/Legal.jsx`) است. برند و
شرح محصول عوض شده‌اند، چون این اپ همان محصول نیست: بازارنما یک ترمینال تحلیل است و پرو چارت یک اپ
سیگنال و کپی‌تریدینگ روی دو سرویس. بندهایی که به namascript یا امکاناتی که این اپ ندارد اشاره
می‌کردند حذف شده‌اند، نه بازنویسی — نگه‌داشتنشان یعنی تعهد دادن به چیزی که وجود ندارد.

بند ۶ (عضویت و پرداخت) بازنویسی شده تا مدل واقعی را بگوید: این اپ اشتراک نمی‌فروشد و عضویت آن
رایگان است. مدل قبلیِ «اشتراک تتری» در این محصول وجود ندارد.

## The privacy policy

این سند برای فرم **Data safety** در Google Play و برای نمایش داخل اپ نوشته شده است. هر ادعای زیر
از روی کد همین مخزن نوشته شده، نه از روی یک الگوی آماده؛ اگر رفتار اپ عوض شد، این فایل هم باید عوض
شود.

The policy carried an app-version stamp of «۱.۰» long after the app had passed 4.x, so a
document whose whole worth is that it is accurate opened on a figure that was not. It is
dated by its revision date alone now, which is the field that is actually revised with it.
