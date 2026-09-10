package com.coinepro.core.script

/**
 * The diagnostic codes, and the one-line fix each carries.
 *
 * A message says what went wrong at a line; a code is what the reader searches the reference for
 * and what the editor keys a hint on. Codes are stable across releases — a reader who learned that
 * `E301` is "not defined" should not have to learn it again. Ranges:
 *
 * | Range | Source |
 * | --- | --- |
 * | `E1xx` | the lexer and parser: the text is not a program |
 * | `E2xx` | a type: the program asked a number of a condition, a constant of a series |
 * | `E3xx` | a name: undefined, redefined, reserved |
 * | `E4xx` | a limit the sandbox enforces: work, time, size, output |
 *
 * `E000` is a message without a code, which is what a diagnostic raised before this table existed
 * carries; the hint is empty and the message stands alone. The full list, with an example of each,
 * is `docs/namascript/SPEC.md` §7 — `ScriptDiagnosticsTest` holds the two in step.
 */
object ScriptDiagnostics {

    /** Every code this build can raise, with its hint in both languages. */
    val CODES: Map<String, Pair<String, String>> = linkedMapOf(
        "E101" to ("هر دستور را در یک خط بنویسید؛ چیزی که بعد از پایان دستور آمده را پاک کنید یا به خط بعد ببرید." to
            "Write one statement per line; remove what follows the statement or move it to the next line."),
        "E102" to ("برای هر «(» یا «[» باز یک بسته لازم است." to "Every “(” or “[” needs its closing half."),
        "E103" to ("شرط سه‌بخشی به این شکل است: شرط ? اگر‌درست : اگر‌نادرست" to "A conditional reads: condition ? whenTrue : whenFalse"),
        "E104" to ("عبارت با چیزی ناتمام مانده — یک عدد، نام یا پرانتز بعد از عملگر لازم است." to
            "The expression stops short — an operator needs a number, a name or a parenthesis after it."),
        "E105" to ("عدد را با رقم‌های لاتین و یک نقطه‌ی اعشار بنویسید: 1.5" to "Write numbers with Latin digits and one decimal point: 1.5"),
        "E106" to ("رشته را با همان علامت نقل‌قولی که باز کردید ببندید." to "Close the string with the same quote it opened with."),
        "E107" to ("این نویسه در نمااسکریپت معنایی ندارد؛ عملگرها + - * / % == != < > <= >= and or not هستند." to
            "That character means nothing in NamaScript; the operators are + - * / % == != < > <= >= and or not."),
        "E108" to ("این عملگر اینجا پشتیبانی نمی‌شود." to "That operator is not supported here."),
        "E201" to ("منفی کردن و «not» فقط روی عدد یا شرط کار می‌کنند." to "Negation and “not” apply to a number or a condition."),
        "E202" to ("«[]» یک عدد ثابت و نامنفی می‌گیرد: close[1]، نه close[-1] یا close[n]." to
            "“[]” takes a constant, non-negative number: close[1], not close[-1] or close[n]."),
        "E203" to ("اینجا عدد یا سری عددی لازم است؛ شرط را با iff به عدد تبدیل کنید." to
            "A number or number series is needed here; turn a condition into a number with iff."),
        "E204" to ("اینجا شرط لازم است؛ یک مقایسه بنویسید: close > open" to "A condition is needed here; write a comparison: close > open"),
        "E205" to ("این مقدار باید ثابت باشد، نه سری — یک عدد بنویسید یا از input بگیرید." to
            "This must be a constant, not a series — write a number or take it from input."),
        "E206" to ("طول دوره باید عددی بین ۱ و چند برابر طول چارت باشد." to "A length must be between 1 and a few times the chart's length."),
        "E207" to ("plot سری عددی می‌کشد؛ برای شرط از marker یا plotshape استفاده کنید." to
            "plot draws a number series; use marker or plotshape for a condition."),
        "E208" to ("این آرگومان متن می‌خواهد: title = \"نام\"" to "This argument wants text: title = \"name\""),
        "E209" to ("این آرگومان رنگ می‌خواهد: color = color.gold یا color.new(color.gold, 50)" to
            "This argument wants a colour: color = color.gold or color.new(color.gold, 50)"),
        "E210" to ("request.security یک تایم‌فریم متنی می‌خواهد که مضرب درشت‌تری از تایم‌فریم چارت باشد: \"240\"، \"H4\"، \"D\"." to
            "request.security wants a text timeframe that is a coarser multiple of the chart's: \"240\", \"H4\", \"D\"."),
        "E301" to ("نام را پیش از استفاده با «=» تعریف کنید، یا املای تابع را در مرجع ببینید." to
            "Define the name with “=” before using it, or check the function's spelling in the reference."),
        "E302" to ("نام‌های درون‌ساخته (close، volume، …) را نمی‌شود دوباره تعریف کرد؛ نام دیگری انتخاب کنید." to
            "Built-in names (close, volume, …) cannot be redefined; pick another name."),
        "E303" to ("«:=» فقط مقدار متغیری را عوض می‌کند که قبلاً با «=» ساخته شده." to "“:=” only changes a variable already made with “=”."),
        "E304" to ("این تابع در نمااسکریپت نیست؛ نام‌های موجود در مرجع فهرست شده‌اند." to
            "That function is not in NamaScript; the reference lists the ones that are."),
        "E401" to ("اسکریپت را کوتاه‌تر کنید یا نتایج میانی را در یک متغیر نگه دارید." to
            "Shorten the script or keep intermediate results in a variable."),
        "E402" to ("بیش از دوازده خط قابل رسم نیست؛ خط‌های کمتری بکشید." to "No more than twelve lines can be plotted; plot fewer."),
        "E403" to ("اسکریپت را کوتاه‌تر کنید — سقف بیست هزار نویسه است." to "Shorten the script — the cap is twenty thousand characters."),
        "E404" to ("چارت هنوز کندلی ندارد؛ منتظر بارگذاری بمانید." to "The chart has no bars yet; wait for it to load."),
        "E405" to ("پرانتزهای تودرتو را کمتر کنید یا عبارت را در چند متغیر بشکنید." to
            "Nest fewer parentheses, or split the expression across variables."),
        "E406" to ("اسکریپت در دو ثانیه تمام نشد؛ طول دوره‌ها یا تعداد محاسبه‌ها را کم کنید." to
            "The script did not finish in two seconds; reduce the lengths or the number of computations."),
    )

    fun hint(code: String): String = CODES[code]?.first.orEmpty()

    fun hintEn(code: String): String = CODES[code]?.second.orEmpty()
}
