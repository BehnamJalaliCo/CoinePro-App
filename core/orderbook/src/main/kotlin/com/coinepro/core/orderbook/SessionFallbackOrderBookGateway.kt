package com.coinepro.core.orderbook

import com.coinepro.core.common.AppResult
import kotlinx.coroutines.flow.Flow

/**
 * The relay first, and the exchange's own public book for the reader the relay will not serve.
 *
 * ### What it is for, in one sentence
 *
 * A reader without a TradeYar session gets a working crypto chart and a working live price — both of
 * those have public routes — and a `401` on the one route the depth ladder needs, which has none.
 * This puts the same book on that screen from the same venue, instead of a sentence blaming their
 * connection. See [DepthUnavailableReason.SESSION_REQUIRED] for the measurements and
 * `docs/SERVER_ASK_ONE_ACCOUNT_TWO_BACKENDS.md` for why "a reader without a session" is most of them.
 *
 * ### Why the fallback is narrow on purpose
 *
 * It is entered on **one** condition — [DepthUnavailableReason.SESSION_REQUIRED] — and on nothing
 * else. Every other answer the relay gives is already the truth and is already better than anything
 * the exchange could tell this app directly:
 *
 * * `422` means this platform does not carry the market. Going around the platform to draw a book
 *   for a symbol the platform does not serve would put a ladder on screen for an instrument the
 *   reader cannot trade here, and hide a symbol-mapping bug behind it.
 * * `502`/`503` mean the relay or the exchange is having a bad minute. Retrying the same venue from
 *   the phone, once a second, is not a fix; it is the same outage with more traffic in it.
 * * A dropped connection is the reader's network, and the second request would drop too.
 *
 * So this is not a general "try the other one" wrapper. It closes exactly one hole, and it is worth
 * saying out loud that widening it would turn a relay with a public escape hatch into a client that
 * quietly prefers the exchange whenever the platform is inconvenient.
 *
 * ### When the fallback also fails, the relay's answer is the one reported
 *
 * Not the fallback's. The reader's situation is "this app cannot show you this book because it does
 * not know who you are on this platform", and that remains true whether or not an exchange in
 * another country happened to answer. Reporting the fallback's own network failure instead would
 * send a reader who needs to sign in off to restart a router — the exact mistake this whole change
 * exists to stop. The one thing it costs is a retry button, and there is nothing on this path a retry
 * would fix.
 *
 * ### Which gateway the stream follows
 *
 * Whichever one answered [load]. [OrderBookController] always calls [load] first and opens [stream]
 * only after that call returned a book, so by the time [stream] is reached the choice has been made
 * and recorded. A [stream] reached without that — nobody does it today — falls back to the relay,
 * which refuses immediately and completes, so the screen keeps whatever book it has and stops rather
 * than polling something nobody chose.
 *
 * The alternative was to re-make the decision on every snapshot, which reads better and behaves
 * worse: it would send a doomed `401` to the relay once a second for the whole time the screen is
 * open, on top of the request that actually works.
 */
class SessionFallbackOrderBookGateway(
    private val relay: OrderBookGateway,
    private val exchange: OrderBookGateway = LBankPublicOrderBookGateway(),
) : OrderBookGateway {

    /**
     * The relay's, because both read the same half of the same exchange.
     *
     * There is deliberately nothing on screen that distinguishes the two paths. The provenance line
     * exists so a reader can check this ladder against the venue's own book, and both paths make
     * that claim truthfully; adding "(direct)" to one of them would answer a question about this
     * app's plumbing that no reader asked, on the line reserved for the one about the market.
     */
    override val sourceName: String get() = relay.sourceName

    /**
     * The symbol whose stream should follow the public book, or null while the relay is serving.
     *
     * Volatile because [load] and [stream] can be touched from different dispatchers, and a stale
     * read here would point the poll at the gateway that is not answering. It is keyed on the symbol
     * rather than held as a bare flag so that switching markets cannot inherit the previous market's
     * choice — a symbol the relay serves would otherwise keep polling the exchange because the one
     * before it did.
     */
    @Volatile
    private var publicFor: String? = null

    override suspend fun load(symbol: String, depth: Int): AppResult<OrderBook> {
        val wanted = symbol.uppercase()
        val first = relay.load(symbol, depth)
        if (first !is AppResult.Failure || first.depthUnavailableReason != DepthUnavailableReason.SESSION_REQUIRED) {
            publicFor = null
            return first
        }
        return when (val second = exchange.load(symbol, depth)) {
            is AppResult.Success -> {
                publicFor = wanted
                second
            }
            // The relay's refusal, not this one's. See the note on the class.
            is AppResult.Failure -> {
                publicFor = null
                first
            }
        }
    }

    override fun stream(symbol: String): Flow<OrderBook> =
        // Anything that is not the symbol the exchange was chosen for goes to the relay, including
        // the case where nothing was chosen at all. That is the safe direction: the relay either
        // serves the book or refuses and completes, and a completed stream leaves the screen holding
        // whatever `load` gave it instead of polling a source nobody selected.
        if (symbol.uppercase() == publicFor) exchange.stream(symbol) else relay.stream(symbol)
}
