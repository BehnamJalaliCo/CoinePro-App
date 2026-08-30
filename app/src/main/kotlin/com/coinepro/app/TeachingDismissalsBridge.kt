package com.coinepro.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.datastore.TeachingStore
import com.coinepro.core.designsystem.TeachingDismissals
import kotlinx.coroutines.launch

/**
 * The seam between the teaching banner and where its dismissals live.
 *
 * `core:designsystem` cannot see `core:datastore`, so the app supplies the state. One collection for
 * the whole composition — provided once at the root — rather than one per screen.
 *
 * `initialValue = null` is what [TeachingDismissals.ready] is for: until the first emission arrives
 * the set is not "empty", it is *unknown*, and a banner drawn against an assumed-empty set would
 * flash on at cold start for somebody who dismissed it last week.
 */
@Composable
fun rememberTeachingDismissals(store: TeachingStore): TeachingDismissals {
    val stored by store.dismissed().collectAsStateWithLifecycle(initialValue = null)
    val latest = rememberUpdatedState(stored)
    val scope = rememberCoroutineScope()
    return remember(store, scope) {
        object : TeachingDismissals {
            override val dismissed: Set<String> get() = latest.value.orEmpty()
            override val ready: Boolean get() = latest.value != null
            override fun dismiss(key: String) { scope.launch { store.dismiss(key) } }
            override fun restore(key: String) { scope.launch { store.restore(key) } }
        }
    }
}
