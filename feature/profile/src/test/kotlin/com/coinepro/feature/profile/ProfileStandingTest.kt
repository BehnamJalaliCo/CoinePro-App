package com.coinepro.feature.profile

import com.coinepro.core.account.KycState
import com.coinepro.core.account.KycStatus
import com.coinepro.core.membership.MembershipAction
import com.coinepro.core.membership.MembershipJourney
import com.coinepro.core.membership.MembershipStatus
import com.coinepro.core.membership.MembershipStep
import com.coinepro.core.membership.MembershipStepProgress
import com.coinepro.core.membership.MembershipStepState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The one rule this screen cannot get wrong.
 *
 * Everything else on the profile is a count of the reader's own work, and being wrong about it is
 * embarrassing. Being wrong about membership or verification is different in kind: those are claims
 * about somebody's account, and a claim invented from a failed request sends a verified reader to
 * re-verify or tells a paying member they are not one.
 *
 * So what is asserted here is never a rendering — no counts, no layout, no strings — but the shape
 * of the decision: which inputs are allowed to produce a word at all.
 */
class ProfileStandingTest {

    @Test
    fun `a membership nobody asked about is not named`() {
        assertNull(knownMembershipStatus(null))
    }

    @Test
    fun `a membership still loading is not named`() {
        assertNull(knownMembershipStatus(journey(MembershipStatus.APPROVED, loading = true)))
    }

    @Test
    fun `a status this build has never heard of is not named`() {
        // The server is free to add one after this build shipped. Mapping it to its nearest
        // neighbour would be guessing at somebody's standing, so it reads as unknown instead.
        assertNull(knownMembershipStatus(journey(MembershipStatus.UNKNOWN)))
    }

    @Test
    fun `an unreadable status is not named even when the server sent a sentence`() {
        // `statusKnown` is false here, and the sentence still travels — it is often the only
        // explanation the reader gets — but it travels as a detail line, never as the answer.
        val unreadable = MembershipJourney(
            steps = MembershipStep.entries.map {
                MembershipStepProgress(it, MembershipStepState.UNKNOWN)
            },
            action = MembershipAction.RETRY,
            uidFormOffered = false,
            status = null,
            serverMessage = "سرور پاسخ نداد",
            uidOnFile = null,
            exchangeOnFile = null,
        )
        assertNull(knownMembershipStatus(unreadable))
    }

    @Test
    fun `a status the server did give is named as the server gave it`() {
        assertEquals(
            MembershipStatus.PENDING_DEPOSIT,
            knownMembershipStatus(journey(MembershipStatus.PENDING_DEPOSIT)),
        )
    }

    @Test
    fun `a failed check is not coloured as a refusal`() {
        // ERROR is the exchange being unreachable, not evidence about the account. Drawing it in
        // the refusal colour would tell somebody with a perfectly good sub-account to open another.
        assertEquals(ProfileFactTone.WAITING, MembershipStatus.ERROR.tone())
        assertEquals(ProfileFactTone.REFUSED, MembershipStatus.REJECTED_REFERRAL.tone())
    }

    @Test
    fun `an unread verification is not reported as unverified`() {
        assertNull(verificationLabel(null))
    }

    @Test
    fun `a state the server did give is reported`() {
        val approved = verificationLabel(KycStatus(level = 1, state = KycState.APPROVED))
        val notStarted = verificationLabel(KycStatus(level = 0, state = KycState.NOT_STARTED))
        // Different words, and — the point — both of them words, unlike the unread case above.
        assertEquals(R.string.profile_verify_approved, approved)
        assertEquals(R.string.profile_verify_not_started, notStarted)
    }

    private fun journey(status: MembershipStatus, loading: Boolean = false) = MembershipJourney(
        steps = MembershipStep.entries.map {
            MembershipStepProgress(it, MembershipStepState.UNKNOWN)
        },
        action = MembershipAction.RETRY,
        uidFormOffered = false,
        status = status,
        serverMessage = null,
        uidOnFile = null,
        exchangeOnFile = null,
        loading = loading,
    )
}
