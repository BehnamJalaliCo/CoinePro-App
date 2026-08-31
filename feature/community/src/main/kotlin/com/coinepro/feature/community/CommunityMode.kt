package com.coinepro.feature.community

import com.coinepro.core.community.CommunityCategory
import com.coinepro.core.community.CommunityError
import com.coinepro.core.community.CommunityUiState

/**
 * What the board is showing, which is six things and would be drawn as two by anybody in a hurry.
 *
 * The two that matter most are the two refusals, and they are the reason this is an enum rather
 * than a pair of booleans on the screen. [SIGNED_OUT] and [LOCKED] arrive as an HTTP 401 and an
 * HTTP 403 from the same route, one line apart in `academy.py`, and they have **opposite buttons**:
 * one is «ورود» and the other is «تهیهٔ اشتراک». A screen that folds them into "you cannot see this"
 * sends half its readers to a control that cannot help them, and the reader who was merely signed
 * out is told to buy something they already have.
 *
 * [UNREADABLE] is the third one worth naming. Twenty rows on the wire and none of them parsed is an
 * HTTP 200 with an empty screen behind it — the exact failure `CommunityWire`'s KDoc is about — and
 * it must not be worded as «هنوز چیزی نوشته نشده», which is a false statement about the community
 * rather than about this build.
 *
 * An error only counts while there is nothing to show: a refresh that fails over a board already on
 * screen leaves the posts there, because yesterday's discussion is still the discussion.
 */
internal enum class CommunityMode {
    LOADING,

    /** No academy token. `401 {"detail":"ورود لازم است."}` — the button is «ورود». */
    SIGNED_OUT,

    /** A free or expired tier. `403` from `require_vip` — the button is «تهیهٔ اشتراک». */
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
    // Ordered by how specific the answer is, not by status code. A tier lock is the one refusal a
    // reader can act on without leaving the app twice, so it is tested before the generic failure.
    state.posts.isEmpty() && state.error == CommunityError.SIGNED_OUT -> CommunityMode.SIGNED_OUT
    state.posts.isEmpty() && state.error == CommunityError.LOCKED -> CommunityMode.LOCKED
    state.posts.isEmpty() && state.error == CommunityError.UNREADABLE -> CommunityMode.UNREADABLE
    state.posts.isEmpty() && state.error != null -> CommunityMode.ERROR
    state.posts.isEmpty() -> CommunityMode.NOTHING_POSTED
    else -> CommunityMode.POSTS
}

/**
 * The chip strip: «همه», then the server's five categories in the server's own order.
 *
 * Declared rather than derived, which is the opposite of what Explore's strip does, and the
 * difference is where the truth lives. Explore's categories are a property of the *catalogue* — a
 * platform quoting no forex must not be offered a forex chip — and this screen's are a property of
 * a **tuple in `academy.py`**. A chip for a category nobody has posted in yet is not a dead end: it
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
