package com.coinepro.core.papertrade

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Where the book is kept between runs.
 *
 * An interface over a *string* rather than over a [PaperBook], and that is the whole design. The
 * codec, the tolerance and every decision about what a half-written row means live in
 * [PaperBookCodec], which this module owns and tests; the implementation outside only has to hold
 * one preference and hand it back. That keeps the part that can lose a reader's account under test
 * here, and makes the wiring in `core:datastore` the four lines it should be.
 *
 * The contract is short and all of it matters:
 *
 *  * [text] emits the stored value, and then again on every write. Null means nothing stored — the
 *    first run — which is not the same as an empty book and must not be turned into one.
 *  * [save] replaces the whole value. There is no partial write: the book is one value, and a
 *    reader must never be able to observe a book with a position whose closing trade was lost.
 */
interface PaperLedgerStore {
    val text: Flow<String?>

    suspend fun save(text: String)
}

/**
 * The store a book gets when nothing better has been wired in.
 *
 * It works and it does not survive the process, which is the honest failure mode for a missing
 * provider: the feature runs, the arithmetic is right, and the reader's account resets when the app
 * is killed. The alternative — refusing to construct the controller without a store — would take a
 * whole screen out of the app over a one-line dependency-injection change.
 */
class InMemoryPaperLedgerStore(initial: String? = null) : PaperLedgerStore {
    private val state = MutableStateFlow(initial)
    override val text: Flow<String?> = state.asStateFlow()

    override suspend fun save(text: String) {
        state.value = text
    }
}
