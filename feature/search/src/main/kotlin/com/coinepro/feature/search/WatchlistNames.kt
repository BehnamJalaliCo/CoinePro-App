package com.coinepro.feature.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import com.coinepro.core.datastore.Watchlist
import com.coinepro.core.designsystem.R as DesignR

/**
 * What a list is called on screen.
 *
 * The list that always exists is created with a Persian name — `Watchlist.DEFAULT_LIST_NAME`,
 * which is *stored user data* from the first launch, because the reader can rename it and a name
 * substituted at draw time would snap back the moment they cleared their own. That is the right
 * decision for storage and it was wrong on screen: the English watchlist was headed «دیده‌بان» with
 * an English count beside it.
 *
 * So the stored name is left exactly as it is, and this is the word drawn in its place *while it is
 * still the untouched default*. The moment somebody renames the list — to anything, in any script —
 * the name they gave is what shows, in both languages, because it is theirs and not a translation.
 */
@Composable
@ReadOnlyComposable
internal fun Watchlist.localName(): String =
    if (isDefault && name == Watchlist.DEFAULT_LIST_NAME) {
        stringResource(DesignR.string.watchlist_default_name)
    } else {
        name
    }
