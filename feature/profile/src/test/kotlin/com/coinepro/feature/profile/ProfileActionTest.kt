package com.coinepro.feature.profile

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The account list gained blocks; the rows that were already in it must not have moved.
 *
 * [ProfileAction.group] was added between the last named argument and the trailing lambda, which is
 * the one position where a new field is invisible to every existing call site. This test is what
 * keeps it that way: a row built without naming a block still lands in the account block, in the
 * order the caller passed, exactly as it did when the list was flat.
 */
class ProfileActionTest {

    @Test
    fun `a row built without naming a block belongs to the account block`() {
        val row = ProfileAction(label = "احراز هویت") {}
        assertEquals(ProfileGroup.ACCOUNT, row.group)
    }

    @Test
    fun `the block order is fixed, and ends with the two rows that end things`() {
        // Not an arbitrary assertion about an enum. The page is a settings list somebody learns by
        // position, and «خروج» and «حذف حساب» are the two rows a thumb must never reach by
        // accident — so their block is last, and it stays last whatever is added above it.
        assertEquals(
            listOf(ProfileGroup.ACCOUNT, ProfileGroup.APP, ProfileGroup.SESSION),
            ProfileGroup.entries,
        )
    }
}
