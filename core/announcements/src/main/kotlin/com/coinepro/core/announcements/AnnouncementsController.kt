package com.coinepro.core.announcements

import com.coinepro.core.common.AppResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Holds the announcement list for as long as the app is open.
 *
 * A singleton per platform in the app's graph, like every other controller here, and for the usual
 * reason: the list is read once on the way into the screen and is still there when the reader comes
 * back to it, so returning to announcements costs no second request and shows no second spinner.
 */
class AnnouncementsController(
    private val gateway: AnnouncementsGateway,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(AnnouncementsState())
    val state: StateFlow<AnnouncementsState> = mutableState.asStateFlow()

    /**
     * Reads the list again.
     *
     * The first read of a session is a [AnnouncementsState.loading]; every read after it is a
     * [AnnouncementsState.refreshing], so a reader who pulls to refresh keeps the announcements
     * they were reading on screen instead of watching them be replaced by a spinner and then by
     * themselves. `loaded` is what separates the two, not `announcements.isEmpty()` — on this route
     * an empty list is an ordinary successful answer, and keying the spinner off emptiness would
     * make every refresh of a correctly-empty list look like a cold start.
     */
    fun refresh() {
        val current = mutableState.value
        if (current.loading || current.refreshing) return
        mutableState.value = current.copy(
            loading = !current.loaded,
            refreshing = current.loaded,
            failure = null,
            failureText = null,
        )
        scope.launch {
            mutableState.value = when (val result = gateway.announcements()) {
                is AppResult.Success -> AnnouncementsState(
                    loaded = true,
                    announcements = result.value,
                )
                // The list that was on screen stays on screen. These are durable statements — an
                // outage notice is still the last thing the service said whether or not this
                // request reached it — and clearing them on a failed refresh would take a live
                // notice off a reader's screen precisely when the connection that failed is the
                // thing the notice might be explaining.
                is AppResult.Failure -> mutableState.value.copy(
                    loading = false,
                    refreshing = false,
                    failure = result.kind,
                    failureText = result.message?.trim()?.takeIf(String::isNotEmpty),
                )
            }
        }
    }

    /**
     * Forgets everything, for a sign-out or a platform switch.
     *
     * [AnnouncementsState.loaded] goes back to false with the rest of it, which is the point: the
     * next reader gets the honest "we have not asked yet" state rather than inheriting a previous
     * account's answer to a question this one has not put.
     */
    fun clear() {
        mutableState.value = AnnouncementsState()
    }
}
