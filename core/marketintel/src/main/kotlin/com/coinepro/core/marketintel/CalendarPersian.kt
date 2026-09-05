package com.coinepro.core.marketintel

import com.coinepro.core.common.BidiText

/**
 * The economic calendar, in Persian.
 *
 * ### Why this is compositional rather than a table
 *
 * A week of the ForexFactory calendar carries about a hundred distinct event names, and a year
 * carries a few hundred — but they are not a few hundred *ideas*. They are a small vocabulary
 * assembled the same way every time:
 *
 *     [country or issuer] [qualifier] [indicator] [period]
 *     German              Prelim      CPI          m/m
 *     Italian             —           Retail Sales m/m
 *     —                   Revised     Nonfarm Productivity q/q
 *
 * A flat lookup table of whole titles would translate «German Prelim CPI m/m» and then be silent
 * about «German Prelim CPI y/y», which is the same event on a different period. So the title is
 * taken apart, each piece is translated on its own, and the pieces are put back together in Persian
 * word order. A word nobody has taught it survives untranslated in the middle of a Persian phrase,
 * which is the correct failure: a reader sees «شاخص PMI فلان» and knows exactly which row it is,
 * rather than seeing nothing or seeing a wrong guess.
 *
 * ### Why the app translates at all
 *
 * It should not have to. `academy/bn/calendar` on CoinePro-FX is documented to serve exactly this
 * data with «عنوان‌ها فارسی‌شده توسطِ news-worker», and it answers `{"items":[]}` because that
 * worker has never run. Measured, twice, months apart. The reader has now reported an empty
 * calendar five times. Until the worker exists this is the calendar, and when it exists the server's
 * own Persian wins without a code change — see [PublicMarketIntel].
 */
internal object CalendarPersian {

    /**
     * The currency code a row is filed under, as a country a Persian reader recognises.
     *
     * `All` is the feed's own marker for an event with no single owner — a G20 meeting, an OPEC
     * summit — and it is a real value rather than a gap, so it gets a real word.
     */
    fun country(code: String?): String? = when (code?.trim()?.uppercase()) {
        null, "" -> null
        // The long forms too. A server that sends `United States` rather than `USD` is naming the
        // same country in the same language, and the reader is owed the same Persian for it.
        "UNITED STATES", "US", "USA" -> "آمریکا"
        "EURO AREA", "EUROZONE", "EUROPEAN UNION", "EU" -> "منطقه‌ی یورو"
        "UNITED KINGDOM", "UK", "GREAT BRITAIN" -> "بریتانیا"
        "JAPAN" -> "ژاپن"
        "SWITZERLAND" -> "سوئیس"
        "CANADA" -> "کانادا"
        "AUSTRALIA" -> "استرالیا"
        "NEW ZEALAND" -> "نیوزیلند"
        "CHINA" -> "چین"
        "GERMANY" -> "آلمان"
        "FRANCE" -> "فرانسه"
        "ITALY" -> "ایتالیا"
        "SPAIN" -> "اسپانیا"
        "USD" -> "آمریکا"
        "EUR" -> "منطقه‌ی یورو"
        "GBP" -> "بریتانیا"
        "JPY" -> "ژاپن"
        "CHF" -> "سوئیس"
        "CAD" -> "کانادا"
        "AUD" -> "استرالیا"
        "NZD" -> "نیوزیلند"
        "CNY" -> "چین"
        "ALL" -> "جهانی"
        else -> code
    }

    /**
     * The whole title, in Persian, assembled from its pieces.
     *
     * **A title that is already Persian is returned untouched**, and that is what lets this run on
     * every source rather than only on the published file. Where a server does the translating its
     * words win, which is the same rule the whole module follows; where it does not — which is
     * every server, today — the reader still gets Persian instead of «US Core CPI (MoM)» on a
     * Persian screen.
     */
    fun title(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return trimmed
        if (!BidiText.isLatinSentence(trimmed)) return trimmed
        var rest = trimmed

        // The period comes off the end first, because it is the one piece whose position in Persian
        // is different: English puts it last, Persian puts it directly after the indicator it
        // qualifies and before anything else. Taking it off here means the rest can be read
        // left-to-right without the suffix getting in the way of a prefix match.
        var period: String? = null
        for ((suffix, persian) in PERIODS) {
            if (rest.endsWith(suffix, ignoreCase = true)) {
                period = persian
                rest = rest.dropLast(suffix.length).trim()
                break
            }
        }

        // Then the leading qualifiers, in the order they appear. There can be two — «German Prelim
        // CPI» — and they nest: the country owns the qualifier, not the other way round.
        val qualifiers = mutableListOf<String>()
        var moved = true
        while (moved) {
            moved = false
            for ((prefix, persian) in PREFIXES) {
                if (rest.equals(prefix, ignoreCase = true)) break
                if (rest.startsWith("$prefix ", ignoreCase = true)) {
                    qualifiers += persian
                    rest = rest.substring(prefix.length + 1).trim()
                    moved = true
                    break
                }
            }
        }

        val indicator = INDICATORS[rest.lowercase()] ?: rest
        return buildString {
            append(indicator)
            if (period != null) append(' ').append(period)
            qualifiers.asReversed().forEach { append(' ').append(it) }
        }.trim()
    }

    /**
     * The period, and why it is not «ماهانه» alone.
     *
     * `m/m` is *month on month* — this month against last month — which is a different claim from
     * "monthly", and the difference is the whole reason the figure exists. «ماهبه‌ماه» says it.
     */
    private val PERIODS: List<Pair<String, String>> = listOf(
        "m/m" to "ماه‌به‌ماه",
        "y/y" to "سال‌به‌سال",
        "q/q" to "فصل‌به‌فصل",
        "q/y" to "فصلی سالانه",
        // The bracketed spelling, which is what the app's own backends send where the published
        // file sends `m/m`. Same event, same period, two houses' punctuation.
        "(mom)" to "ماه‌به‌ماه",
        "(yoy)" to "سال‌به‌سال",
        "(qoq)" to "فصل‌به‌فصل",
    )

    /** Leading qualifiers. Longest first, so «German Final» does not stop at a shorter match. */
    private val PREFIXES: List<Pair<String, String>> = listOf(
        "Core CPI Flash Estimate" to "برآورد اولیه‌ی هسته‌ی",
        "CPI Flash Estimate" to "برآورد اولیه‌ی",
        "German Buba President" to "رئیس بوندس‌بانک آلمان",
        "French" to "فرانسه",
        "German" to "آلمان",
        "Italian" to "ایتالیا",
        "Spanish" to "اسپانیا",
        "Prelim" to "مقدماتی",
        "Preliminary" to "مقدماتی",
        "Flash" to "اولیه",
        "Final" to "نهایی",
        "Revised" to "بازنگری‌شده",
        "Core" to "هسته",
        "Advance" to "پیش‌برآورد",
        "Second Estimate" to "برآورد دوم",
        "Monthly" to "ماهانه",
        "Weekly" to "هفتگی",
    )

    /**
     * The indicators themselves.
     *
     * Keyed on the lower-cased remainder after the prefixes and the period have been taken off, so
     * one entry serves every country and every period the feed files it under.
     */
    private val INDICATORS: Map<String, String> = mapOf(
        // Prices
        "cpi" to "شاخص قیمت مصرف‌کننده",
        "core cpi" to "شاخص قیمت مصرف‌کننده هسته",
        "ppi" to "شاخص قیمت تولیدکننده",
        "cpi flash estimate" to "برآورد اولیه‌ی شاخص قیمت مصرف‌کننده",
        "mi inflation gauge" to "سنجه‌ی تورم مؤسسه‌ی ملبورن",
        "brc shop price index" to "شاخص قیمت خرده‌فروشی بریتانیا",
        "nationwide hpi" to "شاخص قیمت مسکن نیشن‌واید",
        "gdt price index" to "شاخص قیمت لبنیات جهانی",
        "commodity prices" to "قیمت کالاها",
        "anz commodity prices" to "قیمت کالاها — ای‌ان‌زد",
        "ism manufacturing prices" to "شاخص قیمت‌های تولید — آی‌اس‌ام",
        "overseas trade index" to "شاخص تجارت خارجی",

        // Jobs
        "non-farm employment change" to "تغییر اشتغال غیرکشاورزی",
        "adp non-farm employment change" to "تغییر اشتغال غیرکشاورزی — ای‌دی‌پی",
        "employment change" to "تغییر اشتغال",
        "unemployment rate" to "نرخ بیکاری",
        "monthly unemployment rate" to "نرخ بیکاری ماهانه",
        "unemployment claims" to "درخواست‌های بیمه‌ی بیکاری",
        "unemployment change" to "تغییر بیکاری",
        "jolts job openings" to "فرصت‌های شغلی — جولتس",
        "challenger job cuts" to "تعدیل نیرو — چلنجر",
        "average hourly earnings" to "میانگین دستمزد ساعتی",
        "labor productivity" to "بهره‌وری نیروی کار",
        "nonfarm productivity" to "بهره‌وری غیرکشاورزی",
        "unit labor costs" to "هزینه‌ی واحد نیروی کار",

        // Activity
        "gdp" to "تولید ناخالص داخلی",
        "retail sales" to "خرده‌فروشی",
        "industrial production" to "تولید صنعتی",
        "factory orders" to "سفارش‌های کارخانه‌ای",
        "manufacturing pmi" to "شاخص مدیران خرید تولیدی",
        "services pmi" to "شاخص مدیران خرید خدمات",
        "construction pmi" to "شاخص مدیران خرید ساخت‌وساز",
        "non-manufacturing pmi" to "شاخص مدیران خرید غیرتولیدی",
        "ism manufacturing pmi" to "شاخص مدیران خرید تولیدی — آی‌اس‌ام",
        "ism services pmi" to "شاخص مدیران خرید خدمات — آی‌اس‌ام",
        "ratingdog manufacturing pmi" to "شاخص مدیران خرید تولیدی — ریتینگ‌داگ",
        "ratingdog services pmi" to "شاخص مدیران خرید خدمات — ریتینگ‌داگ",
        "ivey pmi" to "شاخص مدیران خرید آیوی",
        "building approvals" to "مجوزهای ساخت",
        "building consents" to "پروانه‌های ساخت",
        "housing starts" to "شروع ساخت مسکن",
        "construction spending" to "هزینه‌ی ساخت‌وساز",
        "company operating profits" to "سود عملیاتی شرکت‌ها",
        "capital spending" to "مخارج سرمایه‌ای",
        "household spending" to "مخارج خانوار",
        "omdia total vehicle sales" to "فروش خودرو — امدیا",

        // Money and rates
        "official cash rate" to "نرخ بهره‌ی رسمی",
        "overnight rate" to "نرخ بهره‌ی شبانه",
        "m4 money supply" to "حجم پول ام‌۴",
        "monetary base" to "پایه‌ی پولی",
        "private sector credit" to "اعتبار بخش خصوصی",
        "net lending to individuals" to "وام‌دهی خالص به اشخاص",
        "mortgage approvals" to "تأییدیه‌های وام مسکن",
        "beige book" to "کتاب بژ فدرال‌رزرو",
        "boc rate statement" to "بیانیه‌ی نرخ بهره‌ی بانک مرکزی کانادا",
        "boc press conference" to "نشست خبری بانک مرکزی کانادا",
        "rbnz rate statement" to "بیانیه‌ی نرخ بهره‌ی بانک مرکزی نیوزیلند",
        "rbnz monetary policy statement" to "بیانیه‌ی سیاست پولی بانک مرکزی نیوزیلند",
        "rbnz press conference" to "نشست خبری بانک مرکزی نیوزیلند",

        // Trade and external
        "trade balance" to "تراز تجاری",
        "goods trade balance" to "تراز تجاری کالا",
        "current account" to "حساب جاری",
        "gov budget balance" to "تراز بودجه‌ی دولت",

        // Sentiment
        "consumer confidence" to "اعتماد مصرف‌کننده",
        "anz business confidence" to "اعتماد کسب‌وکار — ای‌ان‌زد",
        "rcm/tipp economic optimism" to "خوش‌بینی اقتصادی — آر‌سی‌ام/تیپ",

        // Energy
        "crude oil inventories" to "ذخایر نفت خام",
        "natural gas storage" to "ذخایر گاز طبیعی",
        "api weekly statistical bulletin" to "گزارش هفتگی مؤسسه‌ی نفت آمریکا",

        // Auctions and diary
        "10-y bond auction" to "حراج اوراق ۱۰ ساله",
        "30-y bond auction" to "حراج اوراق ۳۰ ساله",
        "bank holiday" to "تعطیلی بانکی",
        "g20 meetings" to "نشست گروه بیست",
        "fomc member speech" to "سخنرانی عضو فدرال‌رزرو",
        "fomc statement" to "بیانیه‌ی فدرال‌رزرو",
        "fomc press conference" to "نشست خبری فدرال‌رزرو",
        "fomc meeting minutes" to "صورت‌جلسه‌ی فدرال‌رزرو",
        "federal funds rate" to "نرخ بهره‌ی فدرال‌رزرو",
        "initial jobless claims" to "درخواست‌های اولیه‌ی بیمه‌ی بیکاری",
        "continuing jobless claims" to "درخواست‌های مستمر بیمه‌ی بیکاری",
        "us core cpi" to "شاخص قیمت مصرف‌کننده هسته‌ی آمریکا",
        "nonfarm payrolls" to "اشتغال غیرکشاورزی",
        "ecb press conference" to "نشست خبری بانک مرکزی اروپا",
        "ecb rate statement" to "بیانیه‌ی نرخ بهره‌ی بانک مرکزی اروپا",
        "boe rate statement" to "بیانیه‌ی نرخ بهره‌ی بانک مرکزی انگلستان",
        "boj policy rate" to "نرخ سیاستی بانک مرکزی ژاپن",
    )
}
