package com.coinepro.core.notifications

import com.coinepro.core.common.BidiText
import java.time.ZoneId
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The reader's own wording, rendered on a phone whose locale is Persian.
 *
 * That last part is the whole reason this class sets a locale in [setUp]. Persian is this app's
 * default, `String.format` without an explicit locale renders `۶۵٬۰۰۰٫۰۰` for a price, and a market
 * figure in Persian digits inside a notification is a bug that has already happened once in this
 * repository. Every assertion below runs with the default locale set to Persian precisely so that a
 * rendering which quietly stops passing a locale fails here rather than on somebody's phone.
 *
 * The rendered text carries bidi isolates around each Latin run, as everything Latin in this app
 * does. They are zero-width and they are not what these tests are about, so most assertions compare
 * against [BidiText.strip]; one asserts that they are there.
 */
class AlertMessageTemplateTest {

    private val original: Locale = Locale.getDefault()
    private val utc: ZoneId = ZoneId.of("UTC")

    /** 2023-11-14, 22:13 UTC. Fixed, so the clock in the rendered text is an exact assertion. */
    private val at = 1_700_000_000_000L

    @Before
    fun setUp() {
        Locale.setDefault(Locale.forLanguageTag("fa-IR"))
    }

    @After
    fun tearDown() {
        Locale.setDefault(original)
    }

    private fun render(message: String?, symbol: String = "BTCUSDT", price: Double = 65_000.5) =
        BidiText.strip(
            AlertMessageTemplate.render(
                message = message,
                symbol = symbol,
                price = price,
                at = at,
                timeframe = "1h",
                zone = utc,
            ),
        )

    @Test
    fun `every placeholder is replaced with the fact it names`() {
        assertEquals(
            "BTCUSDT at 65,000.50 on 1h at 22:13",
            render("{symbol} at {price} on {tf} at {time}"),
        )
    }

    @Test
    fun `a rendered message carries no Persian digits`() {
        val rendered = render("{price} {time}", price = 1_234.5)
        assertEquals("1,234.50 22:13", rendered)
        assertFalse(rendered, rendered.any { character -> character in '۰'..'۹' })
        assertFalse(rendered, rendered.any { character -> character in '٠'..'٩' })
    }

    /** A typo should look like a typo. Blanking it would look like a missing price. */
    @Test
    fun `an unknown placeholder is left exactly as it was typed`() {
        assertEquals("{sybmol} 2,500.00", render("{sybmol} {price}", price = 2_500.0))
    }

    @Test
    fun `no message falls back to the symbol and the price`() {
        assertEquals("ETHUSDT 2,500.00", render(null, symbol = "ETHUSDT", price = 2_500.0))
        assertEquals("ETHUSDT 2,500.00", render("   ", symbol = "ETHUSDT", price = 2_500.0))
    }

    /** A Latin price inside Persian prose reorders against its neighbours without one. */
    @Test
    fun `each Latin run is wrapped in a bidi isolate`() {
        val rendered = AlertMessageTemplate.render(
            message = "{price} تومان",
            symbol = "BTCUSDT",
            price = 65_000.0,
            at = at,
            timeframe = "1h",
            zone = utc,
        )
        assertTrue(rendered, rendered.startsWith("⁦"))
        assertTrue(rendered, rendered.contains("⁦" + "65,000.00" + "⁩"))
        assertEquals("65,000.00 تومان", BidiText.strip(rendered))
    }

    /**
     * A price gets the decimals its own magnitude deserves.
     *
     * Two decimals on a token priced at 0.000031 renders `0.00`, which a reader takes for an outage
     * rather than for a price.
     */
    @Test
    fun `a price is formatted for its own magnitude`() {
        assertEquals("65,000.00", AlertMessageTemplate.formatPrice(65_000.0))
        assertEquals("1.50", AlertMessageTemplate.formatPrice(1.5))
        assertEquals("0.0250", AlertMessageTemplate.formatPrice(0.025))
        assertEquals("0.00003100", AlertMessageTemplate.formatPrice(0.000031))
        assertEquals("-1,200.00", AlertMessageTemplate.formatPrice(-1_200.0))
    }

    @Test
    fun `a price that is not a number renders as a dash rather than as NaN`() {
        assertEquals("-", AlertMessageTemplate.formatPrice(Double.NaN))
        assertEquals("-", AlertMessageTemplate.formatPrice(Double.POSITIVE_INFINITY))
    }

    @Test
    fun `the same placeholder may appear more than once`() {
        assertEquals("BTCUSDT BTCUSDT", render("{symbol} {symbol}"))
    }
}
