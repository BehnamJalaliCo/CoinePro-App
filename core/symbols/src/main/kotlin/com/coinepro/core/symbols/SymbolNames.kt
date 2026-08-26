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
}
