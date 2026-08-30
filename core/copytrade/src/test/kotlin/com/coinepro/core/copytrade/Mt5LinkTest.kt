package com.coinepro.core.copytrade

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The MetaTrader 5 connection state, which is the copy-trade account link read from the
 * connections screen.
 *
 * What is pinned here is the precedence, because every wrong answer this mapping could give is a
 * screen telling somebody their broker account is working when the server has just said it is not.
 */
class Mt5LinkTest {

    private fun account(
        alive: Boolean = true,
        status: String? = "connected",
        lastError: String? = null,
    ) = CopyAccount(
        broker = "OneRoyal",
        server = "OneRoyal-Live",
        loginMasked = "1••••89",
        status = status,
        lastError = lastError,
        alive = alive,
        balance = null,
        equity = null,
        marginLevel = null,
        floatingPnl = null,
        openCount = 0,
        currency = "USD",
        lastSeen = null,
    )

    private fun status(
        account: CopyAccount? = account(),
        mismatch: Boolean = false,
        liveAccount: String? = null,
    ) = CopyTradeStatus(
        account = account,
        preferences = CopyPreferences(),
        master = CopyBook(),
        mirrored = emptyList(),
        mode = null,
        accountMismatch = mismatch,
        liveAccount = liveAccount,
        events = emptyList(),
        slotState = null,
    )

    @Test
    fun `a platform without copy trading offers no MetaTrader link at all`() {
        val link = CopyTradeState(unsupported = true).toMt5Link()

        assertEquals(Mt5LinkStage.UNAVAILABLE, link.stage)
        assertFalse(link.canLink)
        assertFalse(link.canUnlink)
    }

    @Test
    fun `a subscription gates the status read but not the link itself`() {
        // `user/copy-status` sits behind require_vip and `user/account/link` does not. A reader
        // without a subscription can still connect an account; the form must not be withheld.
        val link = CopyTradeState(
            membershipRequired = true,
            membershipMessage = "  اشتراک فعالی روی این حساب نیست  ",
        ).toMt5Link()

        assertEquals(Mt5LinkStage.LOCKED, link.stage)
        assertTrue(link.canLink)
        assertFalse(link.linked)
        assertEquals("اشتراک فعالی روی این حساب نیست", link.serverNote)
    }

    @Test
    fun `nothing is offered as unlinkable while the account is unreadable`() {
        // A destructive action drawn over an account the app has never read is offered on a guess.
        assertFalse(CopyTradeState(membershipRequired = true).toMt5Link().canUnlink)
    }

    @Test
    fun `a read that found no account is not linked and not an error`() {
        val link = CopyTradeState(status = status(account = null)).toMt5Link()

        assertEquals(Mt5LinkStage.NOT_LINKED, link.stage)
        assertTrue(link.canLink)
        assertFalse(link.linked)
        assertNull(link.serverNote)
    }

    @Test
    fun `a link whose terminal has never checked in is pending, not connected`() {
        val link = CopyTradeState(status = status(account(alive = false, status = "pending"))).toMt5Link()

        assertEquals(Mt5LinkStage.PENDING, link.stage)
        assertTrue(link.linked)
        assertEquals("pending", link.serverStatus)
        assertEquals("1••••89", link.loginMasked)
    }

    @Test
    fun `a live terminal on the linked account is connected`() {
        val link = CopyTradeState(status = status()).toMt5Link()

        assertEquals(Mt5LinkStage.CONNECTED, link.stage)
        assertEquals("OneRoyal", link.broker)
        assertEquals("OneRoyal-Live", link.server)
        assertNull(link.liveAccount)
    }

    @Test
    fun `a broker error outranks a live terminal`() {
        val link = CopyTradeState(
            status = status(account(alive = true, lastError = "رمز حساب پذیرفته نشد")),
        ).toMt5Link()

        assertEquals(Mt5LinkStage.ATTENTION, link.stage)
        assertEquals("رمز حساب پذیرفته نشد", link.serverNote)
    }

    @Test
    fun `a terminal on another account is never reported as connected`() {
        val link = CopyTradeState(
            status = status(mismatch = true, liveAccount = "5551234"),
        ).toMt5Link()

        assertEquals(Mt5LinkStage.ATTENTION, link.stage)
        assertEquals("5551234", link.liveAccount)
    }

    @Test
    fun `the live account number is carried only while the terminal really differs`() {
        val link = CopyTradeState(status = status(liveAccount = "5551234")).toMt5Link()

        assertEquals(Mt5LinkStage.CONNECTED, link.stage)
        assertNull(link.liveAccount)
    }

    @Test
    fun `a write in flight is carried through every stage that can hold one`() {
        assertTrue(CopyTradeState(saving = true, status = status()).toMt5Link().busy)
        assertTrue(CopyTradeState(saving = true).toMt5Link().busy)
        assertTrue(CopyTradeState(saving = true, membershipRequired = true).toMt5Link().busy)
    }

    @Test
    fun `a first read in flight is distinguishable from an account that is known to be absent`() {
        assertTrue(CopyTradeState(loading = true).toMt5Link().loading)
        assertFalse(CopyTradeState(status = status(account = null)).toMt5Link().loading)
    }
}
