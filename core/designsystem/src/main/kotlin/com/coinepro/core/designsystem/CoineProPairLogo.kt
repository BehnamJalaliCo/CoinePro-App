package com.coinepro.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * What a forex pair or a metal looks like in a list beside the coins.
 *
 * A crypto market is one asset and gets one disc. A forex market is a *ratio* of two, and a single
 * disc cannot say which two — EURUSD and GBPUSD would be the same picture. So a pair is drawn the
 * way every terminal draws it: the base currency's flag in front, the quote's behind and smaller,
 * with a notch cut between them so the back disc reads as a separate object rather than as a
 * shadow.
 *
 * The flags are drawn here rather than shipped as artwork. At the size these appear — 24 to 42dp,
 * and the quote flag is half of that again — a national flag is four coloured shapes and no more;
 * a detailed one would only be a detailed smudge. Drawing them means they are sharp at any size,
 * cost the APK nothing, and are unambiguously original rather than someone's asset file.
 *
 * They are simplified on purpose and are not accurate flags. Nothing here should be reused as one.
 */
@Composable
fun CoineProPairLogo(
    /** The wire symbol, e.g. `EURUSD` or `XAUUSD`. */
    symbol: String,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
) {
    val pair = pairOf(symbol)
    if (pair == null) {
        CoineProAssetLogo(symbol = symbol, modifier = modifier, size = size)
        return
    }
    val (base, quote) = pair
    // The proportions are the ones every terminal converged on: the quote is half the base, and
    // the two overlap by about a third. Bigger and the pair reads as two markets; smaller and the
    // quote is a dot.
    val frontSize = size * 0.74f
    val backSize = size * 0.50f

    // Absolute rather than start/end, so the composition is identical in Persian and English. It
    // encodes "base in front of quote", not a reading order, and a mirrored version of it would
    // silently swap which currency a reader takes to be the base.
    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .align(AbsoluteAlignment.BottomRight)
                .size(backSize)
                .clip(CircleShape)
                .border(1.dp, CoineProColors.assetRing, CircleShape),
        ) {
            CurrencyDisc(base = quote, size = backSize)
        }
        Box(
            modifier = Modifier
                .align(AbsoluteAlignment.TopLeft)
                .size(frontSize)
                .clip(CircleShape)
                // Two rings, and they do different jobs. The outer one is the notch that separates
                // the front disc from the one behind it, in the colour of the card they sit on so
                // it reads as a gap rather than as a dark scar. The inner is the same hairline
                // every coin gets, so a pair and a coin look like members of one set.
                .border(2.dp, CoineProColors.Surface, CircleShape)
                .border(1.dp, CoineProColors.assetRing, CircleShape),
        ) {
            CurrencyDisc(base = base, size = frontSize)
        }
    }
}

/** One currency or metal as a filled disc. Falls back to a lettered token for anything unknown. */
@Composable
private fun CurrencyDisc(base: String, size: Dp) {
    // The metals are lettered rather than drawn. Every terminal shows gold as "Au" or "XAU" and
    // never as a plain yellow disc, which at this size is indistinguishable from a dozen coins.
    METALS[base]?.let { (label, tint) ->
        MetalDisc(label = label, tint = tint, size = size)
        return
    }
    val flag = flagFor(base)
    if (flag == null) {
        CoineProAssetToken(
            label = base.take(2),
            tint = CoineProColors.assetTint(base),
            size = size,
        )
        return
    }
    Canvas(modifier = Modifier.size(size).clip(CircleShape)) { flag(this) }
}

/**
 * A precious metal as a solid disc with its element symbol.
 *
 * Solid rather than the muted [CoineProAssetToken] wash, which is the treatment for an instrument
 * whose logo is *missing*. Gold is not missing — it is the most important market on the forex side,
 * and beside a row of vivid coin discs a faint gold circle read as the one that had failed to load.
 */
@Composable
private fun MetalDisc(label: String, tint: Color, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(tint),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            // Dark ink on the metal, which is how a stamped bar reads and what keeps silver legible
            // — white on silver is the one combination here with no contrast at all.
            color = Color(0xFF1A1A1A),
            fontSize = with(LocalDensity.current) { (size.toPx() * 0.30f).toSp() },
        )
    }
}

/**
 * Splits a forex or metal symbol into base and quote, or null when it is neither.
 *
 * Length is the whole test: a forex symbol is exactly two three-letter ISO codes, and a crypto one
 * is not. `XAUUSD` passes and lands on the metal discs; `BTCUSDT` is seven characters and is left
 * to the coin table where it belongs.
 */
internal fun pairOf(symbol: String): Pair<String, String>? {
    val clean = symbol.uppercase(Locale.US).filter { it.isLetter() }
    if (clean.length != 6) return null
    val base = clean.substring(0, 3)
    val quote = clean.substring(3, 6)
    // Both halves must be things we recognise as currencies, or a six-letter coin ticker would be
    // torn in half and drawn as a pair of unknown flags.
    val known = { c: String -> flagFor(c) != null || c in METALS }
    if (!known(base) && !known(quote)) return null
    return base to quote
}

/* ------------------------------------------------------------------ the artwork */

private typealias Flag = DrawScope.() -> Unit

/** Draws in a 0–1 square, so one description serves every size. */
private fun DrawScope.field(colour: Long) = drawRect(Color(colour))

private fun DrawScope.band(top: Float, height: Float, colour: Long) = drawRect(
    color = Color(colour),
    topLeft = Offset(0f, size.height * top),
    size = Size(size.width, size.height * height),
)

private fun DrawScope.stripe(left: Float, width: Float, colour: Long) = drawRect(
    color = Color(colour),
    topLeft = Offset(size.width * left, 0f),
    size = Size(size.width * width, size.height),
)

private fun DrawScope.disc(cx: Float, cy: Float, r: Float, colour: Long) = drawCircle(
    color = Color(colour),
    radius = size.minDimension * r,
    center = Offset(size.width * cx, size.height * cy),
)

private fun DrawScope.ring(cx: Float, cy: Float, r: Float, width: Float, colour: Long) = drawCircle(
    color = Color(colour),
    radius = size.minDimension * r,
    center = Offset(size.width * cx, size.height * cy),
    style = Stroke(width = size.minDimension * width),
)

private fun DrawScope.wedge(vararg points: Pair<Float, Float>, colour: Long) {
    val path = Path().apply {
        points.forEachIndexed { index, (x, y) ->
            val px = size.width * x
            val py = size.height * y
            if (index == 0) moveTo(px, py) else lineTo(px, py)
        }
        close()
    }
    drawPath(path, Color(colour))
}

private fun DrawScope.diagonals(width: Float, colour: Long) {
    val w = size.minDimension * width
    drawLine(Color(colour), Offset(0f, 0f), Offset(size.width, size.height), w)
    drawLine(Color(colour), Offset(size.width, 0f), Offset(0f, size.height), w)
}

private fun DrawScope.cross(width: Float, colour: Long) {
    val w = size.minDimension * width
    drawLine(Color(colour), Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), w)
    drawLine(Color(colour), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), w)
}

private fun DrawScope.canton(w: Float, h: Float, field: Long, pale: Long, accent: Long) {
    val cw = size.width * w
    val ch = size.height * h
    drawRect(Color(field), size = Size(cw, ch))
    val thick = size.minDimension * 0.09f
    val thin = size.minDimension * 0.04f
    drawLine(Color(pale), Offset(0f, 0f), Offset(cw, ch), thick)
    drawLine(Color(pale), Offset(cw, 0f), Offset(0f, ch), thick)
    drawLine(Color(pale), Offset(cw / 2, 0f), Offset(cw / 2, ch), thick * 1.4f)
    drawLine(Color(pale), Offset(0f, ch / 2), Offset(cw, ch / 2), thick * 1.4f)
    drawLine(Color(accent), Offset(cw / 2, 0f), Offset(cw / 2, ch), thin)
    drawLine(Color(accent), Offset(0f, ch / 2), Offset(cw, ch / 2), thin)
}

/**
 * The twenty currencies the MT5 feed actually quotes, plus the four precious metals.
 *
 * Simplified to what survives at 20dp: a field, one or two bands, and a single device. The Union
 * Jack keeps its diagonals because without them it is a red cross on blue and reads as Denmark.
 */
private val FLAGS: Map<String, Flag> = mapOf(
    "USD" to {
        field(0xFFB22234)
        for (i in 0..5) band(i * 0.166f + 0.083f, 0.083f, 0xFFFFFFFF)
        drawRect(Color(0xFF3C3B6E), size = Size(size.width * 0.54f, size.height * 0.58f))
    },
    "EUR" to {
        field(0xFF003399)
        ring(0.5f, 0.5f, 0.25f, 0.06f, 0xFFFFCC00)
    },
    "GBP" to {
        field(0xFF012169)
        diagonals(0.17f, 0xFFFFFFFF)
        diagonals(0.075f, 0xFFC8102E)
        cross(0.25f, 0xFFFFFFFF)
        cross(0.125f, 0xFFC8102E)
    },
    "JPY" to {
        field(0xFFFFFFFF)
        disc(0.5f, 0.5f, 0.25f, 0xFFBC002D)
    },
    "CHF" to {
        field(0xFFD52B1E)
        drawRect(
            Color(0xFFFFFFFF),
            topLeft = Offset(size.width * 0.44f, size.height * 0.21f),
            size = Size(size.width * 0.12f, size.height * 0.58f),
        )
        drawRect(
            Color(0xFFFFFFFF),
            topLeft = Offset(size.width * 0.21f, size.height * 0.44f),
            size = Size(size.width * 0.58f, size.height * 0.12f),
        )
    },
    "CAD" to {
        field(0xFFFFFFFF)
        stripe(0f, 0.25f, 0xFFD52B1E)
        stripe(0.75f, 0.25f, 0xFFD52B1E)
        disc(0.5f, 0.5f, 0.13f, 0xFFD52B1E)
    },
    "AUD" to {
        field(0xFF00247D)
        // The canton, reduced to the two crosses. Without it these are a navy disc with dots and
        // read as a night sky rather than as a flag; the full Union Jack at this size is mush.
        canton(0.46f, 0.34f, 0xFF012169, 0xFFFFFFFF, 0xFFC8102E)
        disc(0.71f, 0.63f, 0.05f, 0xFFFFFFFF)
        disc(0.84f, 0.38f, 0.04f, 0xFFFFFFFF)
    },
    "NZD" to {
        field(0xFF00247D)
        // The canton, reduced to the two crosses. Without it these are a navy disc with dots and
        // read as a night sky rather than as a flag; the full Union Jack at this size is mush.
        canton(0.46f, 0.34f, 0xFF012169, 0xFFFFFFFF, 0xFFC8102E)
        disc(0.75f, 0.58f, 0.05f, 0xFFCC142B)
        disc(0.84f, 0.38f, 0.04f, 0xFFCC142B)
    },
    "TRY" to {
        field(0xFFE30A17)
        disc(0.42f, 0.5f, 0.21f, 0xFFFFFFFF)
        disc(0.48f, 0.5f, 0.17f, 0xFFE30A17)
        disc(0.69f, 0.5f, 0.07f, 0xFFFFFFFF)
    },
    "SEK" to {
        field(0xFF006AA7)
        stripe(0.29f, 0.17f, 0xFFFECC00)
        band(0.42f, 0.17f, 0xFFFECC00)
    },
    "NOK" to {
        field(0xFFBA0C2F)
        stripe(0.25f, 0.25f, 0xFFFFFFFF)
        band(0.375f, 0.25f, 0xFFFFFFFF)
        stripe(0.31f, 0.125f, 0xFF00205B)
        band(0.44f, 0.125f, 0xFF00205B)
    },
    "DKK" to {
        field(0xFFC8102E)
        stripe(0.29f, 0.17f, 0xFFFFFFFF)
        band(0.42f, 0.17f, 0xFFFFFFFF)
    },
    "ZAR" to {
        field(0xFFFFFFFF)
        band(0f, 0.33f, 0xFFE03C31)
        band(0.67f, 0.33f, 0xFF001489)
        wedge(0f to 0f, 0.5f to 0.5f, 0f to 1f, colour = 0xFF007749)
        wedge(0f to 0.125f, 0.375f to 0.5f, 0f to 0.875f, colour = 0xFFFFB81C)
        wedge(0f to 0.25f, 0.25f to 0.5f, 0f to 0.75f, colour = 0xFF000000)
    },
    "MXN" to {
        field(0xFFFFFFFF)
        stripe(0f, 0.33f, 0xFF006341)
        stripe(0.67f, 0.33f, 0xFFC8102E)
        disc(0.5f, 0.5f, 0.1f, 0xFF8C6D3F)
    },
    "SGD" to {
        field(0xFFFFFFFF)
        band(0f, 0.5f, 0xFFED2939)
        disc(0.29f, 0.25f, 0.14f, 0xFFFFFFFF)
        disc(0.35f, 0.25f, 0.125f, 0xFFED2939)
    },
    "HKD" to {
        field(0xFFDE2910)
        disc(0.5f, 0.5f, 0.19f, 0xFFFFFFFF)
        disc(0.5f, 0.5f, 0.06f, 0xFFDE2910)
    },
    "CNH" to {
        field(0xFFDE2910)
        disc(0.25f, 0.25f, 0.11f, 0xFFFFDE00)
        disc(0.46f, 0.15f, 0.04f, 0xFFFFDE00)
        disc(0.52f, 0.27f, 0.04f, 0xFFFFDE00)
        disc(0.46f, 0.4f, 0.04f, 0xFFFFDE00)
    },
    "PLN" to {
        field(0xFFFFFFFF)
        band(0.5f, 0.5f, 0xFFDC143C)
    },
    "CZK" to {
        field(0xFFFFFFFF)
        band(0.5f, 0.5f, 0xFFD7141A)
        wedge(0f to 0f, 0.5f to 0.5f, 0f to 1f, colour = 0xFF11457E)
    },
    "HUF" to {
        field(0xFFFFFFFF)
        band(0f, 0.33f, 0xFFCD2A3E)
        band(0.67f, 0.33f, 0xFF436F4D)
    },
)

/**
 * The precious metals, by their element symbols.
 *
 * Lettered rather than drawn, because that is how every terminal shows them and because a plain
 * gold disc at this size is indistinguishable from any of a dozen yellow coins. The colours are the
 * metals' own, so gold still reads as gold at a glance before the letters resolve.
 */
private val METALS: Map<String, Pair<String, Color>> = mapOf(
    "XAU" to ("Au" to Color(0xFFC9982A)),
    "XAG" to ("Ag" to Color(0xFF9EA7B3)),
    "XPT" to ("Pt" to Color(0xFF8A9597)),
    "XPD" to ("Pd" to Color(0xFF7D8A8C)),
)

/**
 * Currencies whose names differ from their artwork's.
 *
 * The renminbi trades as CNY onshore and CNH offshore; the MT5 feed quotes both and they are one
 * flag.
 */
private val CURRENCY_ALIASES = mapOf("CNY" to "CNH")

private fun flagFor(currency: String): Flag? = FLAGS[CURRENCY_ALIASES[currency] ?: currency]

/** Whether a symbol names a forex or metal market rather than a coin. */
internal fun isPairSymbol(symbol: String): Boolean = pairOf(symbol) != null
