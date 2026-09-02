package com.coinepro.feature.community

import com.coinepro.core.community.CommunityCategory
import com.coinepro.core.community.CommunityError
import com.coinepro.core.community.CommunityUiState

/**
 * What the board is showing, which is six things and would be drawn as two by anybody in a hurry.
 *
 * The two that matter most are the two refusals, and they are the reason this is an enum rather
 * than a pair of booleans on the screen. [UNREGISTERED] and [LOCKED] arrive as an HTTP 401 and an
 * HTTP 403 from the same routes and they have **opposite controls**: one is a form asking for a
 * name and the other is nothing at all. A screen that folds them into "you cannot do this" asks a
 * banned reader to pick a name, and tells a reader with no name that they are banned.
 *
 * [UNREADABLE] is the third one worth naming. Twenty rows on the wire and none of them parsed is an
 * HTTP 200 with an empty screen behind it — the exact failure `CommunityWire`'s KDoc is about — and
 * it must not be worded as «هنوز چیزی نوشته نشده», which is a false statement about the community
 * rather than about this build.
 *
 * An error only counts while there is nothing to show: a refresh that fails over a board already on
 * screen leaves the posts there, because yesterday's discussion is still the discussion. And a
 * *write* that was refused never empties the board at all — it is reported beside it.
 */
internal enum class CommunityMode {
    LOADING,

    /** The server holds no name for this key. The control is a name field. */
    UNREGISTERED,

    /** The key is banned. `403`, with the server's sentence. Nothing to press. */
    LOCKED,

    /** Rows arrived and none could be read. Nothing to press; this is a report, not a retry. */
    UNREADABLE,

    /** Anything else that failed with nothing on screen. The button is «تلاش دوباره». */
    ERROR,

    /** The server answered and this category has nothing in it. */
    NOTHING_POSTED,

    POSTS,
}

internal fun communityMode(state: CommunityUiState): CommunityMode = when {
    state.loading -> CommunityMode.LOADING
    // Ordered by how specific the answer is, not by status code.
    state.posts.isEmpty() && state.error == CommunityError.UNREGISTERED -> CommunityMode.UNREGISTERED
    state.posts.isEmpty() && state.error == CommunityError.LOCKED -> CommunityMode.LOCKED
    state.posts.isEmpty() && state.error == CommunityError.UNREADABLE -> CommunityMode.UNREADABLE
    // A refused *text* is not a failed *board*: the composer reports it and the list stays.
    state.posts.isEmpty() && state.error != null && state.error != CommunityError.REFUSED -> CommunityMode.ERROR
    state.posts.isEmpty() -> CommunityMode.NOTHING_POSTED
    else -> CommunityMode.POSTS
}

/**
 * The chip strip: «همه», then the server's five categories in the server's own order.
 *
 * Declared rather than derived, which is the opposite of what Explore's strip does, and the
 * difference is where the truth lives. Explore's categories are a property of the *catalogue* — a
 * platform quoting no forex must not be offered a forex chip — and this screen's are a property of
 * a **tuple on the server**. A chip for a category nobody has posted in yet is not a dead end: it
 * is an empty room somebody can be the first to write in, and hiding it would make the app's list
 * of topics depend on who happened to post today.
 */
internal val COMMUNITY_CHIPS: List<CommunityCategory?> = listOf(null) + CommunityCategory.entries

/** The chip's label. The server's categories are Persian on the wire; see [CommunityCategory]. */
internal fun CommunityCategory?.chipLabelRes(): Int = when (this) {
    null -> R.string.community_category_all
    CommunityCategory.ANALYSIS -> R.string.community_category_analysis
    CommunityCategory.QUESTION -> R.string.community_category_question
    CommunityCategory.EXPERIENCE -> R.string.community_category_experience
    CommunityCategory.NEWS -> R.string.community_category_news
    CommunityCategory.GENERAL -> R.string.community_category_general
}
