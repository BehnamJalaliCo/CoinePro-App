package com.coinepro.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The allowlist is small on purpose and stays that way: this is the first thing to fail when
 * somebody promotes a note without demoting another. `tools/i18n/lint_strings.py` holds the
 * other half — that every name here is a real string key and agrees with `tools/i18n/notes.tsv`.
 */
class NotePolicyTest {

    @Test
    fun `the visible allowlist stays within the budget`() {
        assertTrue("${NotePolicy.visible.size} visible notes over ${NotePolicy.BUDGET}", NotePolicy.visible.size <= NotePolicy.BUDGET)
    }

    @Test
    fun `every allowlisted name is a note key`() {
        NotePolicy.visible.forEach { name ->
            assertTrue(name, Regex("^[a-z0-9_]+_(note|hint|body)$").matches(name))
        }
    }

    @Test
    fun `a name is looked up by exact spelling`() {
        assertTrue(NotePolicy.isVisible("logout_confirm_body"))
        assertFalse(NotePolicy.isVisible("chart_more_studio_note"))
        assertFalse(NotePolicy.isVisible("LOGOUT_CONFIRM_BODY"))
        assertFalse(NotePolicy.isVisible(""))
    }

    @Test
    fun `the money and deletion notes are the ones that stay`() {
        // A spot check of the policy's stated reason, so the list cannot drift into "notes somebody
        // liked" without this sentence changing too.
        val kept = setOf("copy_switch_note", "alerts_delete_body", "delete_account_kept_note", "webhooks_secret_hint")
        assertEquals(kept, NotePolicy.visible intersect kept)
    }
}
