package com.coinepro.core.script

/**
 * One lesson in the in-app course.
 *
 * [example] is runnable, and the screen offers a button that puts it straight into the editor. That
 * is the whole design of this course: nothing is explained that the reader cannot immediately run
 * on the chart in front of them, on the symbol they were already looking at. A tutorial whose
 * examples live only on the page is a tutorial people read once.
 */
data class ScriptLesson(
    val id: String,
    val title: String,
    /** Persian prose. Paragraphs, in order. */
    val body: List<String>,
    val example: String? = null,
    /** The one sentence worth remembering. */
    val takeaway: String? = null,
)

/**
 * The course, in the order it has to be read.
 *
 * Ordered by what the next lesson assumes, not by how interesting each is. The two hardest ideas in
 * the language — that everything is a whole series at once, and that an absent value is not zero —
 * come third and fourth, before anything that would quietly depend on them.
 */
object ScriptLessons {

    val ALL: List<ScriptLesson> = listOf(
        ScriptLesson(
            id = "what",
            title = "نما اسکریپت چیست",
            body = listOf(
                "نما اسکریپت زبان کوچکی است برای نوشتن اندیکاتور خودتان. چیزی که می‌نویسید مستقیم روی همان نموداری که باز است اجرا می‌شود و همان‌جا کشیده می‌شود — نه در یک محیط جدا، نه روی داده‌ای دیگر.",
                "یک اسکریپت چند سطر است. هر سطر یا چیزی را حساب می‌کند و اسم می‌گذارد، یا چیزی روی نمودار می‌کشد. همین.",
                "کد شما روی همین دستگاه اجرا می‌شود. جایی فرستاده نمی‌شود و چیزی از حساب یا سفارش‌های شما نمی‌بیند: نما اسکریپت فقط کندل‌ها را می‌خواند و فقط روی نمودار می‌کشد. اسکریپت نمی‌تواند معامله باز کند.",
            ),
            example = "plot(close, title = \"قیمت بسته\", color = color.gold)",
            takeaway = "هر اسکریپت روی کندل‌های همان نمودار اجرا می‌شود و فقط رسم می‌کند.",
        ),
        ScriptLesson(
            id = "values",
            title = "مقدارها و نام‌ها",
            body = listOf(
                "با «=» به یک محاسبه اسم می‌دهید. بعد از آن هر جا اسم را بنویسید، همان محاسبه است.",
                "اسم‌ها با حرف انگلیسی یا زیرخط شروع می‌شوند. یک اسم را نمی‌شود دو بار تعریف کرد؛ اگر لازم شد مقدارش را عوض کنید، از «:=» استفاده کنید.",
                "چهار جور مقدار وجود دارد: عدد، متن، درست/نادرست، و رنگ. کنار اینها «سری» هست که درس بعدی است.",
            ),
            example = """
                length = 20
                average = ta.sma(close, length)
                plot(average, title = "میانگین", color = color.gold)
            """.trimIndent(),
            takeaway = "«=» تعریف می‌کند، «:=» مقدار را عوض می‌کند.",
        ),
        ScriptLesson(
            id = "series",
            title = "همه‌چیز یک سری است",
            body = listOf(
                "این مهم‌ترین ایده‌ی زبان است. «close» یک عدد نیست؛ قیمت بسته‌شدن همه‌ی کندل‌ها با هم است. وقتی می‌نویسید «close * 2»، این محاسبه روی تک‌تک کندل‌ها انجام می‌شود و نتیجه‌اش دوباره یک سری است.",
                "پس در نما اسکریپت حلقه (for) وجود ندارد و لازم هم نیست: هر عبارتی که می‌نویسید یک‌بار برای کل نمودار نوشته می‌شود و روی همه‌ی کندل‌ها اجرا می‌شود.",
                "اگر یک طرف محاسبه عدد ثابت باشد، همان عدد برای همه‌ی کندل‌ها تکرار می‌شود. «close - 10» یعنی از قیمت هر کندل ده واحد کم کن.",
            ),
            example = """
                spread = high - low
                plot(spread, title = "دامنه‌ی کندل", color = color.blue, pane = "separate")
            """.trimIndent(),
            takeaway = "یک‌بار می‌نویسید، برای همه‌ی کندل‌ها حساب می‌شود.",
        ),
        ScriptLesson(
            id = "absent",
            title = "کندل‌های بی‌مقدار",
            body = listOf(
                "میانگین بیست‌کندلی روی کندل پنجم مقداری ندارد — هنوز بیست کندل نگذشته است. زبان آنجا صفر نمی‌گذارد؛ آن کندل را بی‌مقدار می‌گذارد و رسم از جایی شروع می‌شود که واقعاً مقدار وجود دارد.",
                "این تفاوت ظاهری نیست. اگر صفر گذاشته می‌شد، میانگینِ میانگین‌ها خراب می‌شد و نمودار یک افت جعلی نشان می‌داد که هرگز اتفاق نیفتاده بود.",
                "تقسیم بر صفر هم بی‌مقدار است، نه بی‌نهایت. «close[1]» روی اولین کندل نمودار هم بی‌مقدار است، چون کندلی قبل از آن وجود ندارد.",
                "اگر جایی لازم شد بی‌مقدارها را با عدد پر کنید — و مطمئنید که درست است — «nz» این کار را می‌کند.",
            ),
            example = """
                change = ta.change(close, 1)
                plot(nz(change, 0), title = "تغییر", color = color.grey, pane = "separate")
            """.trimIndent(),
            takeaway = "بی‌مقدار یعنی «نمی‌دانم»، نه صفر.",
        ),
        ScriptLesson(
            id = "history",
            title = "نگاه به کندل‌های قبل",
            body = listOf(
                "«[1]» یعنی یک کندل قبل، «[5]» یعنی پنج کندل قبل. این کار روی هر سری‌ای جواب می‌دهد، نه فقط روی قیمت.",
                "«close > close[1]» یعنی این کندل بالاتر از کندل قبلی بسته شده — و مثل هر عبارت دیگری، برای همه‌ی کندل‌ها حساب می‌شود.",
                "روی کندل‌های ابتدای نمودار که آن‌قدر گذشته وجود ندارد، نتیجه بی‌مقدار است؛ زبان به اولین کندل نمی‌چسبد و عدد جعلی نمی‌سازد.",
            ),
            example = """
                rising = close > close[1] and close[1] > close[2]
                marker(rising, title = "سه کندل صعودی", style = "up")
            """.trimIndent(),
            takeaway = "«[n]» گذشته را می‌خواند و پیش از شروع نمودار، بی‌مقدار است.",
        ),
        ScriptLesson(
            id = "conditions",
            title = "شرط‌ها",
            body = listOf(
                "مقایسه‌ها — «>»، «<»، «>=»، «<=»، «==»، «!=» — یک سری درست/نادرست می‌سازند: برای هر کندل یک جواب.",
                "با «and»، «or» و «not» شرط‌ها را ترکیب می‌کنید. اگر یک طرف روی کندلی بی‌مقدار باشد، جواب آن کندل هم بی‌مقدار است، نه نادرست.",
                "برای انتخاب بین دو مقدار روی هر کندل، از «iff(شرط، الف، ب)» استفاده کنید.",
            ),
            example = """
                trend = ta.sma(close, 50)
                above = close > trend
                plot(iff(above, high, low), title = "لبه‌ی همسو", color = color.teal)
            """.trimIndent(),
            takeaway = "شرط هم یک سری است: برای هر کندل یک جواب.",
        ),
        ScriptLesson(
            id = "indicators",
            title = "اندیکاتورهای آماده",
            body = listOf(
                "همه‌ی اندیکاتورها زیر «ta.» هستند: «ta.sma»، «ta.ema»، «ta.rsi»، «ta.atr»، «ta.macd» و بقیه. فهرست کاملشان در برگه‌ی «مرجع» است.",
                "اینها دقیقاً همان محاسبه‌ای‌اند که خود برنامه برای اندیکاتورهای آماده‌اش استفاده می‌کند. یعنی «ta.ema(close, 20)» شما و EMA بیستِ نمودار عدد به عدد یکی‌اند — نه نزدیک به هم، یکی.",
                "ورودی اول معمولاً سری است و دوم طول دوره. بعضی‌ها — مثل «ta.atr» و «ta.cci» — خودشان high و low و close را از نمودار برمی‌دارند و فقط طول می‌خواهند.",
            ),
            example = """
                plot(ta.ema(close, 20), title = "EMA ۲۰", color = color.gold)
                plot(ta.ema(close, 50), title = "EMA ۵۰", color = color.blue)
            """.trimIndent(),
            takeaway = "«ta.» همان اندیکاتورهای خود برنامه است، نه نسخه‌ی دیگری از آنها.",
        ),
        ScriptLesson(
            id = "crosses",
            title = "تقاطع‌ها",
            body = listOf(
                "«ta.crossover(a, b)» فقط روی همان کندلی درست است که a از پایین به بالای b رفته — نه روی همه‌ی کندل‌هایی که a بالاتر است.",
                "برابر بودن، تقاطع نیست. دو خط که به هم می‌رسند و بی‌آنکه از هم رد شوند برمی‌گردند، هیچ تقاطعی نساخته‌اند؛ اگر این‌طور نبود، دو خط صاف روی هر کندل سیگنال می‌دادند.",
                "«ta.crossunder» همان است در جهت مخالف.",
            ),
            example = """
                fast = ta.ema(close, 9)
                slow = ta.ema(close, 21)
                plot(fast, title = "تند", color = color.gold)
                plot(slow, title = "کند", color = color.blue)
                marker(ta.crossover(fast, slow), title = "تقاطع", style = "up")
            """.trimIndent(),
            takeaway = "تقاطع یک لحظه است، نه یک وضعیت.",
        ),
        ScriptLesson(
            id = "drawing",
            title = "کشیدن روی نمودار",
            body = listOf(
                "«plot» خط می‌کشد، «hline» خط افقی ثابت، و «marker» روی کندل‌هایی که شرط برقرار است نشانه می‌گذارد.",
                "«plot» خودش تصمیم می‌گیرد خط روی قیمت بنشیند یا در پنل جدا، و این تصمیم را با «اندازه‌گیری» می‌گیرد نه از روی اسم: سری‌ای که کندل‌به‌کندل نزدیک قیمت می‌ماند روی قیمت کشیده می‌شود و سری‌ای که نمی‌ماند، پایین. اگر نظر دیگری دارید، «pane = \"price\"» یا «pane = \"separate\"» را بدهید.",
                "همه‌ی خطوطی که پنل جدا می‌خواهند در یک پنل جمع می‌شوند و یک مقیاس مشترک دارند — که دقیقاً همان چیزی است که مقایسه‌شان را ممکن می‌کند.",
                "بیش از دوازده خط رسم نمی‌شود. این محدودیت برای این است که نموداری با بیست خط، نمودار نیست.",
            ),
            example = """
                rsi = ta.rsi(close, 14)
                plot(rsi, title = "RSI", color = color.gold)
                hline(70, title = "اشباع خرید", color = color.sell)
                hline(30, title = "اشباع فروش", color = color.buy)
            """.trimIndent(),
            takeaway = "جای خط با اندازه‌گیری تعیین می‌شود، نه با نامش.",
        ),
        ScriptLesson(
            id = "inputs",
            title = "ورودی برای خواننده",
            body = listOf(
                "«input» عددی می‌سازد که از پنل کنار نمودار قابل تغییر است — بی‌آنکه کسی به کد دست بزند.",
                "«title» اسم آن ورودی در پنل است. «min» و «max» بازه‌اش را می‌بندند، و همین بستن است که جلوی «دوره‌ی صفر» یا «دوره‌ی منفی» را می‌گیرد.",
                "مقداری که خواننده انتخاب کرده ذخیره می‌شود و دفعه‌ی بعد هم همان است. اگر بعداً بازه را در کد تنگ‌تر کنید، مقدار ذخیره‌شده به داخل بازه‌ی جدید کشیده می‌شود، نه اینکه اسکریپت خطا بدهد.",
            ),
            example = """
                length = input(14, title = "دوره‌ی میانگین", min = 2, max = 200)
                plot(ta.ema(close, length), title = "میانگین", color = color.gold)
            """.trimIndent(),
            takeaway = "هر عددی که ممکن است عوض شود، باید input باشد.",
        ),
        ScriptLesson(
            id = "setups",
            title = "ساختن یک ستاپ",
            body = listOf(
                "«signal» از یک شرط، یک ستاپ کامل می‌سازد: ورود، حد ضرر و هدف — و همان ناحیه‌های رنگی‌ای را روی نمودار می‌کشد که سیگنال‌های خود برنامه می‌کشند.",
                "ستاپ از «آخرین» کندلی گرفته می‌شود که شرط در آن برقرار شده، نه اولی. چیزی که ممکن است حالا به آن عمل کنید تازه‌ترین آن است؛ ستاپی از سه هفته پیش، ستاپ نیست.",
                "اگر در خرید حد ضرر بالاتر از ورود باشد، زبان آن را رد می‌کند و می‌گوید چرا. این اشتباهِ تایپی نیست که بی‌صدا بگذرد: ریسک منفی و نسبت بی‌معنا تولید می‌کند.",
                "نسبت ریسک به بازده خودکار حساب می‌شود و کنار ستاپ نوشته می‌شود.",
            ),
            example = """
                atr = ta.atr(14)
                roof = ta.highest(high, 20)
                broke = ta.crossover(close, roof[1])

                entry = close
                stop = close - atr * 2
                signal(broke, entry, stop, target = close + atr * 4, buy = true)
            """.trimIndent(),
            takeaway = "یک ستاپ سه عدد است: ورود، حد ضرر، هدف.",
        ),
        ScriptLesson(
            id = "limits",
            title = "مرزهای زبان",
            body = listOf(
                "حلقه و تابعِ کاربر وجود ندارد. زبان عمداً این‌طور طراحی شده: هر عبارت یک‌بار روی کل سری اجرا می‌شود، و همین است که اجرای اسکریپت روی گوشی را سریع و قابل پیش‌بینی نگه می‌دارد.",
                "چیزی که با حلقه می‌نوشتید، تقریباً همیشه با «iff» و «[n]» و توابع پنجره‌ای مثل «ta.highest» نوشتنی است.",
                "اسکریپت به شبکه، فایل و حساب شما دسترسی ندارد و نمی‌تواند سفارش بگذارد. هر اجرا هم سقف حجم محاسبه دارد، پس یک اسکریپت اشتباه، برنامه را قفل نمی‌کند.",
                "اجرای اسکریپت روی همان کندل‌هایی است که نمودار نشان می‌دهد. در حالت بازپخش، اسکریپت هم فقط تا همان کندل را می‌بیند — آینده نه به شما نشان داده می‌شود و نه به اسکریپت شما.",
            ),
            takeaway = "بدون حلقه، بدون شبکه، بدون سفارش — عمداً.",
        ),
    )

    fun byId(id: String): ScriptLesson? = ALL.firstOrNull { it.id == id }
}
