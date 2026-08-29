package com.coinepro.core.papertrade

import com.coinepro.core.database.PaperTradeDao
import com.coinepro.core.database.PaperTradeEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PaperTradeControllerTest {

    @Test
    fun `the account survives a restart`() = runTest {
        // The whole point of the store. A second controller over the same store is what the reader
        // gets when Android kills the process and they come back.
        val store = InMemoryPaperLedgerStore()
        val first = PaperTradeController(store, eager(), { AT })
        advanceUntilIdle()
        first.onQuotes(quotes(100.0))
        first.market("BTCUSDT", PaperSide.BUY, 1.0)
        advanceUntilIdle()
        assertEquals(1, first.state.value.book.positions.size)

        val second = PaperTradeController(store, eager(), { AT })
        advanceUntilIdle()

        assertEquals(1, second.state.value.book.positions.size)
        assertEquals(
            first.state.value.book.account.balance,
            second.state.value.book.account.balance,
            1e-9,
        )
    }

    @Test
    fun `a price tick that changes nothing does not rewrite the store`() = runTest {
        // Prices arrive several times a second. A write per tick is a preference file rewritten a
        // few hundred times a minute for a reader who is doing nothing but watching.
        val store = CountingStore()
        val controller = PaperTradeController(store, eager(), { AT })
        advanceUntilIdle()
        controller.onQuotes(quotes(100.0))
        controller.market("BTCUSDT", PaperSide.BUY, 1.0)
        advanceUntilIdle()
        val afterTrade = store.writes

        repeat(20) { controller.onQuotes(quotes(100.0 + it)) }
        advanceUntilIdle()

        assertEquals(afterTrade, store.writes)
    }

    @Test
    fun `an old Room row becomes a position in the new book`() = runTest {
        val dao = FakeDao(
            listOf(
                PaperTradeEntity(1, "XAUUSD", true, 2_648.0, 0.2, AT),
                PaperTradeEntity(2, "BTCUSDT", false, 92_800.0, 0.05, AT, exit = 91_960.0, closedAtEpochMillis = AT),
            ),
        )
        val controller = PaperTradeController(InMemoryPaperLedgerStore(), eager(), { AT }, dao)
        advanceUntilIdle()
        val book = controller.state.value.book

        assertEquals(1, book.positions.size)
        assertEquals(1, book.closed.size)
        // (92,800 − 91,960) × 0.05 on a sell is 42, and the old model charged nothing for it.
        assertEquals(42.0, book.closed.single().net, 1e-9)
        assertEquals(0.0, book.closed.single().fees, 1e-9)
    }

    @Test
    fun `the chart's entry point still opens a position, and pays the spread for it`() = runTest {
        val controller = PaperTradeController(InMemoryPaperLedgerStore(), eager(), { AT })
        advanceUntilIdle()

        controller.open("btcusdt", buy = true, price = 100.0, size = 1.0)
        advanceUntilIdle()
        val position = controller.state.value.book.positions.single()

        assertEquals("BTCUSDT", position.symbol)
        assertTrue("a market order from a chart is still a market order", position.entry > 100.0)
    }

    @Test
    fun `a stale feed leaves the book alone`() = runTest {
        val controller = PaperTradeController(InMemoryPaperLedgerStore(), eager(), { AT })
        advanceUntilIdle()
        controller.onQuotes(quotes(100.0))
        controller.market("BTCUSDT", PaperSide.BUY, 1.0, stopLoss = 95.0)
        advanceUntilIdle()

        controller.onQuotes(mapOf("BTCUSDT" to PaperQuote("BTCUSDT", 50.0, stale = true, atEpochMillis = AT)))
        advanceUntilIdle()

        assertEquals(1, controller.state.value.book.positions.size)
        assertTrue(controller.state.value.stale)
    }

    @Test
    fun `nothing is traded before the stored book has been read`() = runTest {
        // A command that landed on the default book would trade an account that is not the
        // reader's, and the write behind it would replace the one on disk with it. The gate here
        // is a store that has not answered yet, which is the first frame after every cold launch.
        val gate = MutableSharedFlow<String?>(replay = 1)
        val store = object : PaperLedgerStore {
            override val text: Flow<String?> = gate
            override suspend fun save(text: String) {
                gate.emit(text)
            }
        }
        val controller = PaperTradeController(store, eager(), { AT })

        controller.market("BTCUSDT", PaperSide.BUY, 1.0)
        assertFalse(controller.state.value.loaded)
        assertTrue(controller.state.value.book.positions.isEmpty())

        gate.emit(PaperBookCodec.encode(saved()))
        advanceUntilIdle()
        assertEquals(555.0, controller.state.value.book.account.balance, 1e-9)
    }

    /**
     * The scope this repository's other controller tests use.
     *
     * Unconfined, so a launch inside `init` has run by the time the constructor returns. With a
     * queueing dispatcher the store read is still pending on the line after the constructor, and
     * every one of these tests would be asserting against a book that had not been loaded yet.
     */
    private fun TestScope.eager() = TestScope(UnconfinedTestDispatcher(testScheduler))

    private fun saved() = PaperBook(account = PaperAccount(555.0, 500.0, AT, 1))

    private fun quotes(last: Double) =
        mapOf("BTCUSDT" to PaperQuote("BTCUSDT", last = last, atEpochMillis = AT))

    private class CountingStore : PaperLedgerStore {
        private val state = MutableStateFlow<String?>(null)
        var writes = 0
        override val text: Flow<String?> = state
        override suspend fun save(text: String) {
            writes++
            state.value = text
        }
    }

    private class FakeDao(rows: List<PaperTradeEntity>) : PaperTradeDao {
        private val state = MutableStateFlow(rows)
        override fun trades(): Flow<List<PaperTradeEntity>> = state
        override suspend fun insert(trade: PaperTradeEntity): Long = trade.id
        override suspend fun update(trade: PaperTradeEntity) = Unit
        override suspend fun clear() = Unit
    }

    private companion object {
        const val AT = 1_756_000_000_000L
    }
}
