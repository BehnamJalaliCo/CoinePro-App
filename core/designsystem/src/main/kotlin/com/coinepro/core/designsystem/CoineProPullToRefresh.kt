package com.coinepro.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Drag a list down to ask for it again.
 *
 * ### Why the button was not enough
 *
 * Every list in this app already had a refresh control — a circular arrow in the header, or a
 * freshness strip saying how old the figures were with a button beside it. Both are correct and
 * both are the wrong shape for the gesture a reader actually makes. Somebody watching a price does
 * not travel to the top of the screen and aim at a 24dp target; they flick the list. On a market
 * app that flick is close to a reflex, and an app that ignores it feels like a page rather than an
 * application — the reader tugs, nothing happens, and they conclude the numbers are not live.
 *
 * So this is not a replacement for the buttons. The button says *when* the data is from and gives
 * a reader who wants certainty something to press; the gesture answers the reflex. They call the
 * same function.
 *
 * ### The indicator
 *
 * The platform's own, tinted to the screen's accent, rather than a bespoke one. A pull indicator is
 * a mechanism a reader already knows, and a distinctive one is a distinctive version of something
 * nobody wants to learn twice. Its container is the elevated surface so it reads as a sheet coming
 * out from under the header rather than as a dot floating over the content.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoineProPullToRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    val haptics = rememberCoineProHaptics()
    PullToRefreshBox(
        isRefreshing = refreshing,
        // The tick fires on the release that commits the pull, not on the drag — a haptic that ran
        // while the finger was still moving would be indistinguishable from the scroll itself.
        onRefresh = {
            haptics.commit()
            onRefresh()
        },
        modifier = modifier.fillMaxSize(),
        state = state,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = CoineProColors.SurfaceElevated,
                color = CoineProColors.pageAccent,
            )
        },
        content = content,
    )
}

/** The same, for a screen whose content is not a box — kept so call sites read the same way. */
@Composable
fun CoineProPullToRefreshColumn(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    CoineProPullToRefresh(refreshing = refreshing, onRefresh = onRefresh, modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize()) { content() }
    }
}
