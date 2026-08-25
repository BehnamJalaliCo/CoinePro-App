package com.coinepro.core.copytrade

import com.coinepro.core.model.MarketPlatform
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Pins the copy-trading payload as CoinePro-FX actually sends it.
 *
 * The fixture below is that server's own `/user/copy-status` response, keys and spellings intact,
 * because every field in it is one the panel and the app both read and neither can rename. The one
 * that has already caused trouble elsewhere is `sl`, which arrives as a literal 0 for "no stop".
 */
class CopyTradeWireTest {

    private val gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    @Test
    fun `the live status arrives whole`() {
        val status = gson.fromJson(FIXTURE, CopyStatusDto::class.java).toDomain()

        val account = requireNotNull(status.account)
        assertEquals("OneRoyal", account.broker)
        assertEquals("1••••89", account.loginMasked)
        assertEquals("connected", account.status)
        assertTrue(account.alive)
        assertEquals(4821.5, account.balance!!, 0.001)
        assertEquals(312.0, account.marginLevel!!, 0.001)
        assertEquals(2, account.openCount)
        assertEquals("USD", account.currency)
        assertEquals(Instant.parse("2026-08-25T09:12:04Z"), account.lastSeen)

        assertTrue(status.preferences.enabled)
        assertEquals("risk_percent", status.preferences.riskMode)
        assertEquals(1.0, status.preferences.riskValue!!, 0.001)
        assertEquals(5, status.preferences.maxOpenTrades)
        assertTrue(status.preferences.copyStopAndTargets)
        assertEquals(listOf("XAUUSD"), status.preferences.symbols)

        assertEquals(3, status.master.open)
        assertEquals("XAUUSD", status.master.positions.single().symbol)

        val mirrored = status.mirrored.single()
        assertEquals("XAUUSD", mirrored.symbol)
        assertEquals("buy", mirrored.direction)
        assertEquals(0.05, mirrored.lots, 0.0001)
        assertEquals(3312.4, mirrored.stopLoss!!, 0.001)
        assertEquals(9114L, mirrored.signalId)

        assertEquals("live", status.mode)
        assertFalse(status.accountMismatch)
    }

    @Test
    fun `a stop of zero is no stop, not a stop at zero`() {
        val position = requireNotNull(
            gson.fromJson(
                """{"symbol": "XAGUSD", "direction": "sell", "lots": 0.1, "profit": -2.4, "sl": 0}""",
                CopyPositionDto::class.java,
            ).toDomain(),
        )
        assertNull(
            "Printing 0.00 beside a live position reads as a stop placed at zero",
            position.stopLoss,
        )
    }

    @Test
    fun `the execution events keep the server's own Persian explanation`() {
        val status = gson.fromJson(FIXTURE, CopyStatusDto::class.java).toDomain()
        val event = status.events.single()

        assertEquals("open_failed", event.code)
        assertEquals("failed", event.outcome)
        assertEquals(9114L, event.signalId)
        assertEquals(Instant.ofEpochSecond(1787648000L), event.at)
        assertTrue(
            "The server assembles the broker's reason into this text; rewording it loses it",
            event.message.contains("حجمِ درخواستی"),
        )
    }

    @Test
    fun `an event without a code is dropped rather than shown as a blank row`() {
        val dto = gson.fromJson("""{"ts": 1787648000, "message": "x"}""", CopyEventDto::class.java)
        assertNull(dto.toDomain())
    }

    @Test
    fun `a healthy slot sends nothing and renders nothing`() {
        val status = gson.fromJson("""{"account": null, "slot_state": null}""", CopyStatusDto::class.java)
            .toDomain()
        assertNull(status.slotState)
        assertNull(status.account)
        assertFalse(
            "A missing settings block must not read as copying being on",
            status.preferences.enabled,
        )
    }

    @Test
    fun `a mismatched terminal is carried through with the account it is really on`() {
        val status = gson.fromJson(
            """{"account_mismatch": true, "live_account": "77771234", "mirrored": []}""",
            CopyStatusDto::class.java,
        ).toDomain()

        assertTrue(status.accountMismatch)
        assertEquals("77771234", status.liveAccount)
        assertTrue(
            "The server withholds the positions here, and the app must not invent any",
            status.mirrored.isEmpty(),
        )
    }

    @Test
    fun `only CoinePro-FX has a copy-trading surface`() {
        assertEquals("user/copy-status", CopyTradePaths.of(MarketPlatform.COINEPRO_FX)!!.status)
        assertEquals("user/copy-config", CopyTradePaths.of(MarketPlatform.COINEPRO_FX)!!.config)
        assertEquals("user/account/link", CopyTradePaths.of(MarketPlatform.COINEPRO_FX)!!.link)
        assertNull(
            "TradeYar executes orders per signal; asking it for a copy status reaches nothing",
            CopyTradePaths.of(MarketPlatform.TRADEYAR),
        )
    }

    @Test
    fun `the switch is the only field ever written`() {
        assertEquals(
            "An omitted field means no change; echoing a whole settings object back would " +
                "overwrite risk parameters the reader never touched",
            """{"enabled":true}""",
            gson.toJson(CopyConfigPatchDto(enabled = true)),
        )
    }

    private companion object {
        val FIXTURE = """
        {
          "account": {
            "broker": "OneRoyal",
            "server": "OneRoyal-Live",
            "login_masked": "1••••89",
            "status": "connected",
            "last_error": null,
            "raw_status": "connected",
            "alive": true,
            "status_source": "heartbeat",
            "health_evidence": {},
            "balance": 4821.5,
            "equity": 4903.1,
            "margin": 120.0,
            "free_margin": 4783.1,
            "margin_level": 312.0,
            "floating_pnl": 81.6,
            "open_count": 2,
            "currency": "USD",
            "last_seen": "2026-08-25T09:12:04+00:00"
          },
          "copy": {
            "enabled": true,
            "risk_mode": "risk_percent",
            "risk_value": 1.0,
            "max_lot": 0.5,
            "max_open_trades": 5,
            "copy_sl_tp": true,
            "max_daily_loss_pct": 10.0,
            "symbols": ["XAUUSD"],
            "use_trailing": true,
            "breakeven_at_tp1": true,
            "trail_distance_frac": 0.5,
            "close_on_signal_gone": false
          },
          "demo": null,
          "master": {
            "open": 3,
            "positions": [
              {"symbol": "XAUUSD", "direction": "buy", "lots": 0.5, "profit": 214.0}
            ]
          },
          "mirrored": [
            {
              "symbol": "XAUUSD",
              "direction": "buy",
              "lots": 0.05,
              "profit": 21.4,
              "sl": 3312.4,
              "signal_id": 9114
            }
          ],
          "mode": "live",
          "account_mismatch": false,
          "live_account": "1234589",
          "exec_events": [
            {
              "ts": 1787648000,
              "signal_id": 9114,
              "code": "open_failed",
              "outcome": "failed",
              "retcode": 10014,
              "symbol": "XAGUSD",
              "message": "این سیگنال روی حسابِ شما اجرا نشد. (علتِ فنی: حجمِ درخواستی از حداقلِ بروکر کمتر است) [کد بروکر: 10014]"
            }
          ],
          "slot_state": null
        }
        """
    }
}
