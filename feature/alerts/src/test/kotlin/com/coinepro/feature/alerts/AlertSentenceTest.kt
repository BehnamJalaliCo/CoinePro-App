package com.coinepro.feature.alerts

import com.coinepro.core.common.BidiText
import com.coinepro.core.notifications.AlertScope
import com.coinepro.core.notifications.AlertTrigger
import com.coinepro.core.notifications.ChannelOp
import com.coinepro.core.notifications.LocalAlertCondition
import com.coinepro.core.notifications.LocalPriceAlert
import com.coinepro.core.notifications.MoveOp
import com.coinepro.core.notifications.PriceOp
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The condition-to-Persian-sentence renderer, under the locale the app actually runs in.
 *
 * The default locale is set to Persian for every case here on purpose. That is the condition under
 * which the bug this whole file guards against appears: `DecimalFormat` and `String.format` follow
 * the default locale, so a price formatted without pinning `Locale.US` comes out as «۶۸٬۵۰۰» — on
 * screen, in a Persian sentence, looking entirely reasonable and comparing against nothing on the
 * reader's exchange. It has happened once in this repository already.
 */
class AlertSentenceTest {

    private val original = Locale.getDefault()

    @Before
    fun usePersianAsTheDeviceWould() {
        Locale.setDefault(Locale.forLanguageTag("fa-IR"))
    }

    @After
    fun restoreTheDefaultLocale() {
        Locale.setDefault(original)
    }

    @Test
    fun `a price alert reads as one sentence with the pair first and the level last`() {
        val sentence = plain(
            AlertSentence.render(
                alert(trigger = AlertTrigger.Price(PriceOp.GREATER_THAN, 68_500.0)),
            ),
        )

        assertEquals("BTC/USDT بالای 68,500.00", sentence)
    }

    @Test
    fun `the level keeps Latin digits although the default locale is Persian`() {
        val sentence = plain(
            AlertSentence.render(
                alert(trigger = AlertTrigger.Price(PriceOp.GREATER_THAN, 68_500.0)),
            ),
        )

        assertTrue("the Latin, grouped level must survive", sentence.contains("68,500.00"))
        assertFalse(
            "a Persian digit in a market figure means Locale.US was not pinned",
            sentence.any { it in '۰'..'۹' },
        )
        assertFalse(
            "an Arabic-Indic digit means the same thing",
            sentence.any { it in '٠'..'٩' },
        )
    }

    @Test
    fun `a small price keeps the decimals its own magnitude needs`() {
        val sentence = plain(
            AlertSentence.render(alert(symbol = "SHIBUSDT", trigger = AlertTrigger.Price(PriceOp.LESS_THAN, 0.00002418))),
        )

        assertEquals("SHIB/USDT زیر 0.00002418", sentence)
    }

    @Test
    fun `the level is wrapped in a bidirectional isolate so it cannot reorder in a Persian line`() {
        val sentence = AlertSentence.render(alert(trigger = AlertTrigger.Price(PriceOp.CROSSING_UP, 100.0)))

        assertTrue("the Latin run must be isolated", sentence.contains('⁦'))
        assertTrue(sentence.contains('⁩'))
    }

    @Test
    fun `a channel names both bounds in the order they were given`() {
        val sentence = plain(
            AlertSentence.render(
                alert(trigger = AlertTrigger.Channel(ChannelOp.EXITING, low = 64_000.0, high = 66_000.0)),
            ),
        )

        assertEquals("BTC/USDT خروج از محدودهٔ 64,000.00 تا 66,000.00", sentence)
    }

    @Test
    fun `a percentage move carries its sign inside the isolate with the digits`() {
        val sentence = plain(
            AlertSentence.render(alert(trigger = AlertTrigger.Move(MoveOp.DOWN_PERCENT, 3.0))),
        )

        assertEquals("BTC/USDT افت 3.00%", sentence)
    }

    @Test
    fun `an indicator condition names the study and its period as one Latin run`() {
        val sentence = plain(
            AlertSentence.render(
                alert(
                    trigger = AlertTrigger.Indicator(
                        indicatorId = "rsi",
                        period = 14,
                        op = PriceOp.GREATER_THAN,
                        value = 70.0,
                    ),
                ),
            ),
        )

        assertEquals("BTC/USDT RSI(14) بالای 70.00", sentence)
    }

    @Test
    fun `a multi-condition alert names its instrument once and joins the conditions with and`() {
        val sentence = plain(
            AlertSentence.render(
                alert(
                    trigger = AlertTrigger.MultiCondition(
                        listOf(
                            AlertTrigger.Price(PriceOp.GREATER_THAN, 68_500.0),
                            AlertTrigger.Indicator("rsi", 14, PriceOp.GREATER_THAN, 70.0),
                        ),
                    ),
                ),
            ),
        )

        assertEquals("BTC/USDT بالای 68,500.00 و RSI(14) بالای 70.00", sentence)
    }

    @Test
    fun `an alert stored before triggers existed still reads as a sentence`() {
        val sentence = plain(
            AlertSentence.render(
                LocalPriceAlert(
                    id = "a",
                    symbol = "XAUUSD",
                    condition = LocalAlertCondition.BELOW,
                    value = 2_412.5,
                ),
            ),
        )

        assertEquals("XAU/USD زیر 2,412.50", sentence)
    }

    @Test
    fun `a twenty-four hour condition writes its own count in Persian digits and its figure in Latin`() {
        val sentence = plain(
            AlertSentence.render(
                LocalPriceAlert(
                    id = "a",
                    symbol = "BTCUSDT",
                    condition = LocalAlertCondition.CHANGE_24H_UNDER,
                    value = 5.0,
                ),
            ),
        )

        assertEquals("BTC/USDT افت ۲۴ ساعته بیش از 5.00%", sentence)
    }

    @Test
    fun `a watchlist alert names the list rather than a symbol`() {
        val sentence = plain(
            AlertSentence.render(
                alert(trigger = AlertTrigger.Move(MoveOp.UP_PERCENT, 5.0))
                    .copy(scope = AlertScope.Watchlist("default")),
                watchlistName = { "دنبال‌شده‌ها" },
            ),
        )

        assertEquals("فهرست دنبال‌شده‌ها رشد 5.00%", sentence)
    }

    private fun alert(
        symbol: String = "BTCUSDT",
        trigger: AlertTrigger,
    ) = LocalPriceAlert(
        id = "alert",
        symbol = symbol,
        condition = LocalAlertCondition.ABOVE,
        value = 0.0,
        trigger = trigger,
        scope = AlertScope.Symbol(symbol),
    )

    /** The sentence without its bidirectional isolates, which are invisible and unassertable. */
    private fun plain(sentence: String): String = BidiText.strip(sentence)
}
