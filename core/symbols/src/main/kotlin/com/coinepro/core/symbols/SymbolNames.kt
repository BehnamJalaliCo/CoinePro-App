package com.coinepro.core.symbols

/**
 * The Persian names behind every description this module produces.
 *
 * Data rather than logic, and kept apart from [SymbolClassifier] for that reason: adding a listing
 * is an edit to one map here, and no rule changes.
 *
 * These are names, not translations. A Persian trader says «بیت‌کوین» and «نزدک ۱۰۰», so those are
 * the entries; where no Persian name is in common use the ticker stands, because inventing one
 * would make the instrument *harder* to recognise than leaving it in Latin.
 */
internal object SymbolNames {

    /** The twenty-one currencies the MT5 feed quotes. */
    val CURRENCY: Map<String, String> = mapOf(
        "USD" to "دلار آمریکا",
        "EUR" to "یورو",
        "GBP" to "پوند انگلیس",
        "JPY" to "ین ژاپن",
        "CHF" to "فرانک سوئیس",
        "CAD" to "دلار کانادا",
        "AUD" to "دلار استرالیا",
        "NZD" to "دلار نیوزیلند",
        "TRY" to "لیر ترکیه",
        "SEK" to "کرون سوئد",
        "NOK" to "کرون نروژ",
        "DKK" to "کرون دانمارک",
        "ZAR" to "رند آفریقای جنوبی",
        "MXN" to "پزو مکزیک",
        "SGD" to "دلار سنگاپور",
        "HKD" to "دلار هنگ‌کنگ",
        "CNH" to "یوآن چین",
        "CNY" to "یوآن چین",
        "PLN" to "زلوتی لهستان",
        "CZK" to "کرون چک",
        "HUF" to "فورینت مجارستان",
    )

    /**
     * The same currencies, in the shortest form a Persian trader still recognises.
     *
     * ### Why a second map exists
     *
     * A list row cannot hold «دلار آمریکا / فرانک سوئیس» — twenty-four characters under a ticker,
     * beside a price. The first answer to that was to print the **base** alone, and it produced
     * something worse than the ellipsis it removed: USDJPY, USDCHF and USDCAD all read «دلار
     * آمریکا», three different instruments with one subtitle, in the column whose whole job is to
     * tell them apart.
     *
     * A pair is two things and the row has to say both. «دلار/ین» is seven characters and says
     * them; «دلار آمریکا» is eleven and says half. Where a currency has no shorter name in use the
     * long one stands — «رند» and «فورینت» are already as short as they get.
     */
    val CURRENCY_SHORT: Map<String, String> = mapOf(
        "USD" to "دلار",
        "EUR" to "یورو",
        "GBP" to "پوند",
        "JPY" to "ین",
        "CHF" to "فرانک",
        // The commodity dollars by their country alone. «دلار استرالیا/دلار» is eighteen characters
        // and does not fit; «استرالیا/دلار» is thirteen and is not ambiguous, because the row above
        // already reads AUDUSD in Latin and carries both flags. The word this drops — «دلار» — is
        // the one word every entry in this half of the map would otherwise repeat.
        "CAD" to "کانادا",
        "AUD" to "استرالیا",
        "NZD" to "نیوزیلند",
        "TRY" to "لیر",
        "SEK" to "کرون سوئد",
        "NOK" to "کرون نروژ",
        "DKK" to "کرون دانمارک",
        "ZAR" to "رند",
        "MXN" to "پزو",
        "SGD" to "سنگاپور",
        "HKD" to "هنگ‌کنگ",
        "CNH" to "یوآن",
        "CNY" to "یوآن",
        "PLN" to "زلوتی",
        "CZK" to "کرون چک",
        "HUF" to "فورینت",
    )

    /** The four precious metals, by their element symbols — which is how every terminal writes them. */
    val METAL: Map<String, String> = mapOf(
        "XAU" to "طلا",
        "XAG" to "نقره",
        "XPT" to "پلاتین",
        "XPD" to "پالادیوم",
    )

    /** Stock indices, under the names a Persian reader actually uses for them. */
    val INDEX: Map<String, String> = mapOf(
        "US30" to "داوجونز ۳۰",
        "US500" to "اس‌اند‌پی ۵۰۰",
        "US100" to "نزدک ۱۰۰",
        "UK100" to "فوتسی ۱۰۰",
        "GER40" to "دکس ۴۰",
        "JPN225" to "نیکی ۲۲۵",
        "FRA40" to "کک ۴۰",
        "HK50" to "هنگ‌سنگ",
        "AUS200" to "ASX 200",
        "EU50" to "یوروستاکس ۵۰",
    )

    /** Energy contracts. */
    val ENERGY: Map<String, String> = mapOf(
        "USOIL" to "نفت آمریکا (WTI)",
        "UKOIL" to "نفت برنت",
        "NATGAS" to "گاز طبیعی",
    )

    /**
     * Coins with a Persian name in common use.
     *
     * Roughly the hundred largest, because that is where a Persian name exists at all: below it
     * traders say the ticker, and «آر‌اِن‌دی‌آر» helps nobody. A coin absent from this map is not
     * broken — its description is its ticker, which is what it would have been called anyway.
     *
     * The reason to have it at all is search. Somebody typing «سولانا» should land on SOL, and
     * without this map the only searchable text for a coin is three Latin letters they may not know.
     */
    val CRYPTO: Map<String, String> = mapOf(
        "BTC" to "بیت‌کوین",
        "ETH" to "اتریوم",
        "USDT" to "تتر",
        "USDC" to "یو‌اس‌دی کوین",
        "DAI" to "دای",
        "BNB" to "بایننس‌کوین",
        "SOL" to "سولانا",
        "XRP" to "ریپل",
        "ADA" to "کاردانو",
        "DOGE" to "دوج‌کوین",
        "TRX" to "ترون",
        "AVAX" to "آوالانچ",
        "SHIB" to "شیبا اینو",
        "DOT" to "پولکادات",
        "LINK" to "چین‌لینک",
        "BCH" to "بیت‌کوین کش",
        "NEAR" to "نیر",
        "POL" to "پالیگان",
        "MATIC" to "پالیگان",
        "LTC" to "لایت‌کوین",
        "UNI" to "یونی‌سواپ",
        "ICP" to "اینترنت کامپیوتر",
        "ETC" to "اتریوم کلاسیک",
        "APT" to "اپتوس",
        "XLM" to "استلار",
        "RENDER" to "رندر",
        "RNDR" to "رندر",
        "ATOM" to "کازماس",
        "XMR" to "مونرو",
        "FIL" to "فایل‌کوین",
        "HBAR" to "هدرا",
        "ARB" to "آربیتروم",
        "VET" to "وی‌چین",
        "MKR" to "میکر",
        "INJ" to "اینجکتیو",
        "OP" to "اپتیمیزم",
        "IMX" to "ایمیوتبل",
        "GRT" to "گراف",
        "AAVE" to "آوی",
        "STX" to "استکس",
        "TAO" to "بیت‌تنسور",
        "RUNE" to "تورچین",
        "FTM" to "فانتوم",
        "SEI" to "سی",
        "THETA" to "تتا",
        "FLOW" to "فلو",
        "LDO" to "لیدو",
        "TIA" to "سلستیا",
        "SUI" to "سویی",
        "ALGO" to "الگورند",
        "EGLD" to "الروند",
        "QNT" to "کوانت",
        "GALA" to "گالا",
        "JUP" to "ژوپیتر",
        "PYTH" to "پیث",
        "ORDI" to "اوردینالز",
        "WIF" to "داگ‌ویف‌هت",
        "BONK" to "بونک",
        "PEPE" to "پپه",
        "FLOKI" to "فلوکی",
        "AXS" to "اکسی اینفینیتی",
        "SAND" to "سندباکس",
        "MANA" to "دیسنترالند",
        "CHZ" to "چیلیز",
        "EOS" to "ایاس",
        "XTZ" to "تزوس",
        "NEO" to "نئو",
        "KAVA" to "کاوا",
        "MINA" to "مینا",
        "IOTA" to "آیوتا",
        "GMX" to "جی‌ام‌ایکس",
        "ENS" to "ای‌ان‌اس",
        "DYDX" to "دی‌وای‌دی‌ایکس",
        "ONDO" to "اوندو",
        "WLD" to "ورلدکوین",
        "ENA" to "اتنا",
        "BLUR" to "بلر",
        "1INCH" to "وان‌اینچ",
        "COMP" to "کامپاند",
        "CRV" to "کرو",
        "SNX" to "سینتتیکس",
        "SUSHI" to "سوشی‌سواپ",
        "YFI" to "یرن فایننس",
        "ZEC" to "زی‌کش",
        "DASH" to "دش",
        "BAT" to "بت",
        "TON" to "تون",
        "KAS" to "کاسپا",
        "FET" to "فچ",
        "AR" to "آرویو",
        "CAKE" to "پنکیک‌سواپ",
    )

    /**
     * A currency or metal code as the app names it, or the code itself where it has no name.
     *
     * The code is a real answer rather than a fallback: an exotic leg an MT5 broker quotes and this
     * app has never named is still better shown as «HKD» than as nothing.
     */
    fun displayOf(code: String): String =
        CURRENCY[code] ?: METAL[code] ?: code

    /** The same, in the short form a list row can hold. A metal's name is already short. */
    fun shortDisplayOf(code: String): String =
        CURRENCY_SHORT[code] ?: CURRENCY[code] ?: METAL[code] ?: code
}
