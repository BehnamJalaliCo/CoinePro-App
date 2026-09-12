package com.coinepro.core.script

/**
 * The shipped presets in English (4.73.0, run I item 4).
 *
 * ### Why this file exists rather than a resource
 *
 * `:namascript` is the language, and it has no Android in it and therefore no `strings.xml`. That
 * is the right boundary and it is also exactly the kind of place a translation leak hides — run H
 * found the same shape in the drawing tools, which are a catalogue in `:chart-core`. A reader who
 * put the app in English and opened the studio was offered «تقاطع دو میانگین», and tapping it
 * inserted a script that plotted a line labelled «تند» *on their chart*.
 *
 * ### Why the source is translated and not only the title
 *
 * Because the source is the thing the reader ends up owning. Everything inside a preset's quotes —
 * an input's title, a plot's name, a marker's caption — becomes a control in their settings sheet
 * and a row in their legend. Translating the card and leaving the code would hand an English reader
 * a study they cannot read, which is worse than the card being Persian.
 *
 * The **code** is identical, line for line, in both tables. Only the prose inside it moves, so a
 * reader who switches language keeps the same script, and a preset that gains a line gains it in
 * one place and is missing from the other until somebody writes it — which is what the conformance
 * check in `ScriptPresetsEnTest` is for.
 */
internal object ScriptPresetsEn {

    /** The blank script's first three lines, in English. */
    val BLANK = """
        // A new script
        // Draw one line, then change it:
        plot(ta.ema(close, 20), title = "EMA 20", color = color.gold)
    """.trimIndent()

    val BY_ID: Map<String, ScriptPreset> = listOf(
        ScriptPreset(
            id = "ema-cross",
            title = "Two moving averages",
            summary = "Two exponential averages, and a mark on every crossing.",
            teaches = "Variables, ta.ema, ta.crossover and marker",
            source = """
                // Two exponential averages; a crossing up is marked green and a crossing down red.
                fastLength = input(9, title = "Fast length", min = 2, max = 200)
                slowLength = input(21, title = "Slow length", min = 3, max = 400)

                fast = ta.ema(close, fastLength)
                slow = ta.ema(close, slowLength)

                plot(fast, title = "Fast", color = color.gold)
                plot(slow, title = "Slow", color = color.blue)

                marker(ta.crossover(fast, slow), title = "Crossing up", style = "up")
                marker(ta.crossunder(fast, slow), title = "Crossing down", style = "down")
            """.trimIndent(),
        ),
        ScriptPreset(
            id = "rsi-zones",
            title = "RSI with zones",
            summary = "The relative strength index in its own pane, with the 30 and 70 lines.",
            teaches = "A separate pane, hline, and why RSI is not drawn over the price",
            source = """
                // RSI goes in a pane of its own: it is scaled nought to a hundred, and over the
                // price it flattens the price axis into a straight line.
                length = input(14, title = "RSI length", min = 2, max = 100)
                rsi = ta.rsi(close, length)

                plot(rsi, title = "RSI", color = color.gold)
                hline(70, title = "Overbought", color = color.sell)
                hline(50, color = color.grey)
                hline(30, title = "Oversold", color = color.buy)

                marker(ta.crossunder(rsi, 30), title = "Back off the floor", style = "up")
                marker(ta.crossover(rsi, 70), title = "Back off the ceiling", style = "down")
            """.trimIndent(),
        ),
        ScriptPreset(
            id = "bollinger-squeeze",
            title = "Bollinger squeeze",
            summary = "Bollinger bands, and a mark on the bars where the band is at its tightest.",
            teaches = "Bands, ta.lowest, and comparing a series against its own past",
            source = """
                // The band's width against its middle; when it reaches the narrowest of the last
                // hundred bars the market has coiled. This is not a direction — it is readiness.
                length = input(20, title = "Band length", min = 5, max = 200)
                lookback = input(100, title = "Comparison window", min = 20, max = 500)

                upper = ta.bb_upper(close, length, 2)
                lower = ta.bb_lower(close, length, 2)
                basis = ta.bb_basis(close, length, 2)

                plot(upper, title = "Upper edge", color = color.grey)
                plot(basis, title = "Middle", color = color.gold, dashed = true)
                plot(lower, title = "Lower edge", color = color.grey)

                width = (upper - lower) / basis * 100
                tightest = ta.lowest(width, lookback)
                marker(width <= tightest, title = "Squeeze", style = "circle", color = color.orange)
            """.trimIndent(),
        ),
        ScriptPreset(
            id = "atr-stop",
            title = "An ATR stop",
            summary = "Takes the stop's distance from the market's own volatility, not a fixed percentage.",
            teaches = "ta.atr, and why one percent is not one thing on gold and on Bitcoin",
            source = """
                // A fixed percentage means something different on every instrument. ATR takes the
                // distance from that instrument's own movement, so one script works on both.
                multiplier = input(2, title = "ATR factor", min = 0.5, max = 6)
                atr = ta.atr(14)

                longStop = close - atr * multiplier
                shortStop = close + atr * multiplier

                plot(longStop, title = "Long stop", color = color.buy, dashed = true)
                plot(shortStop, title = "Short stop", color = color.sell, dashed = true)
            """.trimIndent(),
        ),
        ScriptPreset(
            id = "breakout-setup",
            title = "A twenty-bar breakout",
            summary = "A whole setup: in on the break, stop under the low, target at twice the risk.",
            teaches = "signal, and that a setup is three numbers rather than an arrow",
            source = """
                // A whole setup. signal takes the *latest* bar the condition held on rather than
                // the first, because the one you might act on now is the most recent.
                length = input(20, title = "High and low window", min = 5, max = 200)

                roof = ta.highest(high, length)
                floor = ta.lowest(low, length)
                plot(roof, title = "High", color = color.grey, dashed = true)
                plot(floor, title = "Low", color = color.grey, dashed = true)

                broke = ta.crossover(close, roof[1])
                marker(broke, title = "Break", style = "up")

                entry = close
                stop = floor
                risk = entry - stop
                signal(broke, entry, stop, target = entry + risk * 2, buy = true)
            """.trimIndent(),
        ),
        ScriptPreset(
            id = "macd-histogram",
            title = "MACD and its histogram",
            summary = "The MACD line, the signal line and the difference between them, in one pane.",
            teaches = "Several outputs from one study, and how plots gather in one pane",
            source = """
                // All three come out of one calculation and have to be read in one pane, on one
                // scale.
                fast = input(12, title = "Fast", min = 2, max = 100)
                slow = input(26, title = "Slow", min = 3, max = 200)
                smooth = input(9, title = "Signal", min = 2, max = 100)

                macd = ta.macd(close, fast, slow, smooth)
                signalLine = ta.macd_signal(close, fast, slow, smooth)

                plot(macd, title = "MACD", color = color.blue)
                plot(signalLine, title = "Signal", color = color.gold)
                hline(0, color = color.grey)

                marker(ta.crossover(macd, signalLine), title = "Crossing up", style = "up")
                marker(ta.crossunder(macd, signalLine), title = "Crossing down", style = "down")
            """.trimIndent(),
        ),
        ScriptPreset(
            id = "trend-filter",
            title = "A trend filter",
            summary = "The same crossing, but only in the direction of the longer trend.",
            teaches = "and, iff, and colouring a line by a condition",
            source = """
                // A crossing against the larger trend is not the same crossing. `and` weighs two
                // conditions together on every bar.
                trendLength = input(200, title = "Trend length", min = 20, max = 500)
                fastLength = input(10, title = "Fast", min = 2, max = 100)
                slowLength = input(30, title = "Slow", min = 3, max = 200)

                trend = ta.sma(close, trendLength)
                fast = ta.ema(close, fastLength)
                slow = ta.ema(close, slowLength)

                up = close > trend
                plot(trend, title = "Trend", color = color.grey, width = 2)
                plot(fast, title = "Fast", color = color.gold)
                plot(slow, title = "Slow", color = color.blue)

                marker(ta.crossover(fast, slow) and up, title = "Buy with the trend", style = "up")
                marker(ta.crossunder(fast, slow) and not up, title = "Sell with the trend", style = "down")
            """.trimIndent(),
        ),
        ScriptPreset(
            id = "volume-spike",
            title = "A volume spike",
            summary = "The bars whose volume is several times the average.",
            teaches = "volume, ta.sma over volume, and taking the ratio of two series",
            source = """
                // Volume is weighed against its own average rather than a fixed number: «high
                // volume» is a different figure on every instrument.
                length = input(20, title = "Average length", min = 5, max = 200)
                threshold = input(2.5, title = "Times the average", min = 1.2, max = 10)

                average = ta.sma(volume, length)
                ratio = volume / average

                plot(ratio, title = "Volume ratio", color = color.orange, pane = "separate")
                hline(threshold, title = "Threshold", color = color.grey)
                marker(ratio > threshold, title = "Volume spike", style = "circle", color = color.orange)
            """.trimIndent(),
        ),
        ScriptPreset(
            id = "inside-bar",
            title = "An inside bar",
            summary = "A bar that sits entirely inside the one before it.",
            teaches = "Reaching the previous bar with [1]",
            source = """
                // [1] means «one bar ago». On the chart's first bar there is no such value, and
                // the language leaves it absent rather than putting a nought there.
                inside = high < high[1] and low > low[1]
                marker(inside, title = "Inside bar", style = "circle", color = color.gold)

                narrow = (high - low) < (high[1] - low[1]) * 0.5
                marker(inside and narrow, title = "Inside and narrow", style = "circle", color = color.orange)
            """.trimIndent(),
        ),
        ScriptPreset(
            id = "session-range",
            title = "The day's range",
            summary = "The high and the low of the last twenty-four hours, as two lines.",
            teaches = "ta.highest and ta.lowest to build a zone",
            source = """
                // On the hourly bar, twenty-four bars is a day. Change the timeframe and change
                // this number too — the language does not know what timeframe it is on.
                bars = input(24, title = "Bar count", min = 2, max = 500)

                roof = ta.highest(high, bars)
                floor = ta.lowest(low, bars)
                middle = (roof + floor) / 2

                plot(roof, title = "Period high", color = color.sell)
                plot(middle, title = "Middle", color = color.grey, dashed = true)
                plot(floor, title = "Period low", color = color.buy)

                log("The current range is drawn")
            """.trimIndent(),
        ),
    ).associateBy { it.id }
}
