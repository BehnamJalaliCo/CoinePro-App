package com.coinepro.feature.community

import java.time.Instant
import com.coinepro.core.community.CommunityCategory
import com.coinepro.core.community.CommunityError
import com.coinepro.core.community.CommunityPost
import com.coinepro.core.community.CommunityUiState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which of the six states the board is in.
 *
 * The two that matter are the two refusals, and the reason this test exists is that they are one
 * status code apart and have opposite buttons. `401` is «ورود لازم است» — sign in. `403` is
 * `require_vip` — buy a subscription. A screen that folds them together tells a reader who is
 * merely signed out to purchase something they may already own, and tells a free-tier reader who
 * *is* signed in to sign in again.
 */
class CommunityModeTest {

    private fun post(id: Long) = CommunityPost(
        id = id,
        author = "رضا",
        content = "متن",
        category = CommunityCategory.ANALYSIS,
        categoryLabel = "تحلیل",
        likes = 0,
        liked = false,
        replyCount = 0,
        reactions = emptyMap(),
        bestReplyId = null,
        createdAt = Instant.parse("2026-08-30T09:00:00Z"),
        pending = false,
    )

    @Test
    fun `the two refusals are two different screens`() {
        assertEquals(
            CommunityMode.SIGNED_OUT,
            communityMode(CommunityUiState(error = CommunityError.SIGNED_OUT)),
        )
        assertEquals(
            CommunityMode.LOCKED,
            communityMode(CommunityUiState(error = CommunityError.LOCKED)),
        )
    }

    @Test
    fun `rows that arrived and could not be read are never worded as an empty board`() {
        // «هنوز چیزی نوشته نشده» would be a false statement about the community rather than about
        // this build, and it would send nobody to the diagnostics export that can fix it.
        assertEquals(
            CommunityMode.UNREADABLE,
            communityMode(CommunityUiState(error = CommunityError.UNREADABLE, unreadable = 20)),
        )
    }

    @Test
    fun `an empty answer with no failure is an empty board`() {
        assertEquals(CommunityMode.NOTHING_POSTED, communityMode(CommunityUiState()))
    }

    @Test
    fun `a failed refresh over a board already on screen leaves the board on screen`() {
        // Yesterday's discussion is still the discussion. An error only counts while there is
        // nothing to show.
        val state = CommunityUiState(posts = listOf(post(1)), error = CommunityError.NETWORK)

        assertEquals(CommunityMode.POSTS, communityMode(state))
    }

    @Test
    fun `even a tier refusal does not blank a board that is already loaded`() {
        // A token that expired mid-session answers 403 to the *next* page. Blanking the twenty
        // posts already read to show a sales page would be losing the reader's place to make an
        // offer.
        val state = CommunityUiState(posts = listOf(post(1)), error = CommunityError.LOCKED)

        assertEquals(CommunityMode.POSTS, communityMode(state))
    }

    @Test
    fun `loading wins over everything, so a refusal from the last run does not flash`() {
        val state = CommunityUiState(loading = true, error = CommunityError.LOCKED)

        assertEquals(CommunityMode.LOADING, communityMode(state))
    }

    @Test
    fun `the strip is the server's five categories plus everything, in the server's order`() {
        // Declared rather than derived, unlike Explore's: these are a tuple in `academy.py`, not a
        // property of what happens to have been posted today. A category nobody has written in yet
        // is an empty room somebody can be the first to write in.
        assertEquals(
            listOf(
                null,
                CommunityCategory.ANALYSIS,
                CommunityCategory.QUESTION,
                CommunityCategory.EXPERIENCE,
                CommunityCategory.NEWS,
                CommunityCategory.GENERAL,
            ),
            COMMUNITY_CHIPS,
        )
    }

    @Test
    fun `every chip has a label, including the one for everything`() {
        // A `when` with no branch for a category added to the enum would fail to compile; this
        // catches the other half — a branch pointing at a string resource that was never written.
        assertEquals(COMMUNITY_CHIPS.size, COMMUNITY_CHIPS.map { it.chipLabelRes() }.distinct().size)
    }
}
