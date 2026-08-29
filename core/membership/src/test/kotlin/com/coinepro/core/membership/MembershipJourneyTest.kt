package com.coinepro.core.membership

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule this whole file exists for: **a tick beside a step is a claim about somebody's account.**
 *
 * The screen that reads this model draws «انجام شد» in green. Every one of those has to be a
 * deduction from something the server actually said, and the cheapest way for that to stop being
 * true is a well-meant refactor that decides `verifying` probably means the exchange account is
 * fine. These tests pin the deductions rather than the rendering.
 */
class MembershipJourneyTest {

    @Test
    fun `awaiting a UID claims nothing about the exchange account or the balance`() {
        // The server cannot know either until a UID has been checked, and a reader shown "done"
        // beside a step they never took stops looking for the one that is blocking them.
        val journey = journeyOf(MembershipStatus.AWAITING_UID, nextStep = "uid")

        assertEquals(MembershipStepState.UNKNOWN, journey.stateOf(MembershipStep.EXCHANGE_ACCOUNT))
        assertEquals(MembershipStepState.UNKNOWN, journey.stateOf(MembershipStep.FUNDING))
        assertEquals(MembershipStep.UID, journey.current)
        assertEquals(MembershipAction.SUBMIT_UID, journey.action)
        assertTrue(journey.uidFormOffered)
    }

    @Test
    fun `pending deposit is a working sign-up, not a refusal`() {
        // The exchange confirmed the sub-account — that is what the verifier checks first — and
        // only the balance is short. So the two steps behind it are genuinely done, and the reader
        // is pointed at the deposit rather than back to the start.
        val journey = journeyOf(MembershipStatus.PENDING_DEPOSIT, uid = "1234", nextStep = "uid")

        assertEquals(MembershipStepState.DONE, journey.stateOf(MembershipStep.EXCHANGE_ACCOUNT))
        assertEquals(MembershipStepState.DONE, journey.stateOf(MembershipStep.UID))
        assertEquals(MembershipStep.FUNDING, journey.current)
        // `next_step` says "uid" here and is deliberately overruled: this reader's UID is already
        // on file, and a form in front of them would be the app misreading its own server.
        assertEquals(MembershipAction.FUND_ACCOUNT, journey.action)
    }

    @Test
    fun `verifying says a UID arrived and nothing at all about the account behind it`() {
        val journey = journeyOf(MembershipStatus.VERIFYING, uid = "1234")

        assertEquals(MembershipStepState.DONE, journey.stateOf(MembershipStep.UID))
        assertEquals(MembershipStepState.UNKNOWN, journey.stateOf(MembershipStep.EXCHANGE_ACCOUNT))
        assertEquals(MembershipStep.VERIFICATION, journey.current)
        assertEquals(MembershipAction.WAIT, journey.action)
    }

    @Test
    fun `a missing UID under a status that implies one is unknown rather than undone`() {
        // The field is optional on the wire. Its absence is not evidence that the server holds no
        // UID, and drawing an empty circle would tell a reader to submit one they already sent.
        val journey = journeyOf(MembershipStatus.VERIFYING, uid = null)

        assertEquals(MembershipStepState.UNKNOWN, journey.stateOf(MembershipStep.UID))
    }

    @Test
    fun `a rejected referral puts the account step back rather than marking it failed and done`() {
        val journey = journeyOf(MembershipStatus.REJECTED_REFERRAL, uid = "1234")

        assertEquals(MembershipStepState.BLOCKED, journey.stateOf(MembershipStep.EXCHANGE_ACCOUNT))
        // For the account that will actually work — a new one, opened through the link — none of
        // the later steps has happened, so none of them is drawn as done.
        assertEquals(MembershipStepState.AHEAD, journey.stateOf(MembershipStep.UID))
        assertEquals(MembershipAction.OPEN_EXCHANGE, journey.action)
    }

    @Test
    fun `a rejected referral that may be resubmitted offers the form as well`() {
        val journey = journeyOf(MembershipStatus.REJECTED_REFERRAL, uid = "1234", canResubmit = true)

        assertEquals(MembershipAction.SUBMIT_UID, journey.action)
        assertTrue(journey.uidFormOffered)
    }

    @Test
    fun `a failed check is not a rejection and its steps stay unspoken for`() {
        // A timeout is not evidence that somebody is not a sub-account. Filing it as a rejection
        // tells a reader with a perfectly good account to go and open another one.
        val journey = journeyOf(MembershipStatus.ERROR, uid = "1234")

        assertEquals(MembershipStepState.UNKNOWN, journey.stateOf(MembershipStep.EXCHANGE_ACCOUNT))
        assertEquals(MembershipStep.VERIFICATION, journey.current)
        assertEquals(MembershipAction.RETRY, journey.action)
    }

    @Test
    fun `approval marks every step done and asks the locked surface to load`() {
        val journey = journeyOf(MembershipStatus.APPROVED, uid = "1234")

        assertTrue(journey.steps.all { it.state == MembershipStepState.DONE })
        assertNull(journey.current)
        assertEquals(MembershipAction.RELOAD, journey.action)
    }

    @Test
    fun `a status this build has never heard of still ends at something the reader can press`() {
        val journey = journeyOf(MembershipStatus.UNKNOWN)

        assertFalse(journey.statusKnown)
        assertEquals(MembershipAction.RETRY, journey.action)
        // Nothing is claimed either way about a state whose meaning is unknown.
        assertTrue(
            journey.steps.none {
                it.step != MembershipStep.SIGN_IN && it.state != MembershipStepState.UNKNOWN
            },
        )
    }

    @Test
    fun `an unreadable status claims nothing, not even the sign-in`() {
        // What failed may well *be* the session, and a screen that ticks sign-in while the server
        // is refusing the reader's token is arguing with the only evidence it has.
        val journey = membershipJourney(MembershipUiState.Unavailable("سرور پاسخ نداد."))

        assertTrue(journey.steps.all { it.state == MembershipStepState.UNKNOWN })
        assertEquals(MembershipAction.RETRY, journey.action)
        assertFalse(journey.uidFormOffered)
        assertEquals("سرور پاسخ نداد.", journey.serverMessage)
        assertFalse(journey.loading)
    }

    @Test
    fun `the first read is loading rather than unreadable`() {
        // Both draw every step as unknown; only one of them is a screen that failed. Telling a
        // reader the server said nothing before the request has finished is a lie that corrects
        // itself, which is the kind readers remember.
        assertTrue(membershipJourney(MembershipUiState.Loading).loading)
        assertTrue(membershipJourney(MembershipUiState.Idle).loading)
    }

    @Test
    fun `every status resolves to an action and to at most one current step`() {
        MembershipStatus.entries.forEach { status ->
            val journey = journeyOf(status)
            assertTrue(
                "$status must draw one step at most as current",
                journey.steps.count { it.state == MembershipStepState.CURRENT } <= 1,
            )
            assertEquals(
                "$status must name all five steps",
                MembershipStep.entries,
                journey.steps.map { it.step },
            )
        }
    }

    @Test
    fun `the triage note is not carried into the journey at all`() {
        // A field that is not here cannot be rendered by mistake. The server's instruction is that
        // `note` — which holds things like referral_status=false — is never shown.
        val journey = journeyOf(MembershipStatus.REJECTED_REFERRAL, note = "referral_status=false")

        assertEquals("پیام سرور", journey.serverMessage)
        assertFalse(MembershipJourney::class.java.declaredFields.any { it.name == "note" })
    }

    private fun journeyOf(
        status: MembershipStatus,
        uid: String? = null,
        nextStep: String? = null,
        canResubmit: Boolean = false,
        note: String? = null,
    ) = membershipJourney(
        MembershipUiState.Ready(
            MembershipState(
                status = status,
                messageFa = "پیام سرور",
                nextStep = nextStep,
                canResubmit = canResubmit,
                uid = uid,
                exchange = "lbank",
                note = note,
            ),
        ),
    )

    private fun MembershipJourney.stateOf(step: MembershipStep): MembershipStepState =
        steps.first { it.step == step }.state
}
