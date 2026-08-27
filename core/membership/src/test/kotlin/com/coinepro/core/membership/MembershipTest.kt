package com.coinepro.core.membership

import com.coinepro.core.common.AppResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MembershipTest {

    @Test
    fun `every state the server documents maps to a distinct one here`() {
        val mapped = listOf(
            "awaiting_uid" to MembershipStatus.AWAITING_UID,
            "verifying" to MembershipStatus.VERIFYING,
            "approved" to MembershipStatus.APPROVED,
            "pending_deposit" to MembershipStatus.PENDING_DEPOSIT,
            "rejected_referral" to MembershipStatus.REJECTED_REFERRAL,
            "error" to MembershipStatus.ERROR,
            "pending" to MembershipStatus.PENDING,
        )
        mapped.forEach { (wire, expected) ->
            assertEquals(wire, expected, gateway(MembershipDto(status = wire)).statusNow().status)
        }
    }

    @Test
    fun `a state added after this build shipped is UNKNOWN, not the nearest neighbour`() {
        // The nearest neighbour to a state nobody has heard of is a guess about somebody's
        // membership. UNKNOWN draws the server's own sentence and offers no action.
        val state = gateway(MembershipDto(status = "suspended_pending_review")).statusNow()

        assertEquals(MembershipStatus.UNKNOWN, state.status)
    }

    @Test
    fun `a UID typed on a Persian keyboard is folded before it is sent`() = runTest {
        val api = RecordingApi(MembershipDto(status = "verifying"))
        NetworkMembershipGateway(api).submitUid("lbank", " ۱۲۳۴۵۶۷۸ ")

        // The exchange, asked about ۱۲۳, says it has never heard of that account — and the refusal
        // reads as "you are not a sub-account", which is a sentence about the person rather than
        // about the keyboard.
        assertEquals("12345678", api.sentUid)
        assertEquals("lbank", api.sentExchange)
    }

    @Test
    fun `the triage note never reaches a caller that renders the message`() {
        val state = gateway(
            MembershipDto(status = "rejected_referral", messageFa = "حساب زیرمجموعه نیست.", note = "referral_status=false"),
        ).statusNow()

        assertEquals("حساب زیرمجموعه نیست.", state.messageFa)
        // Carried for a bug report, and it is the screen's job not to draw it. Kept apart so that
        // is a decision somebody made rather than one field they forgot about.
        assertEquals("referral_status=false", state.note)
    }

    @Test
    fun `blank server strings become null rather than empty text on screen`() {
        val state = gateway(MembershipDto(status = "pending", messageFa = "  ", uid = "")).statusNow()

        assertNull(state.messageFa)
        assertNull(state.uid)
    }

    @Test
    fun `awaitsReader is true while the reader still has something to do`() {
        assertTrue(gateway(MembershipDto(status = "awaiting_uid", nextStep = "uid")).statusNow().awaitsReader)
        assertTrue(gateway(MembershipDto(status = "rejected_referral", canResubmit = true)).statusNow().awaitsReader)
        assertFalse(gateway(MembershipDto(status = "verifying", nextStep = "wait")).statusNow().awaitsReader)
        assertFalse(gateway(MembershipDto(status = "approved")).statusNow().awaitsReader)
    }

    @Test
    fun `a submission answers with the new status and does not read it again`() = runTest {
        val api = RecordingApi(MembershipDto(status = "verifying", nextStep = "wait"))
        val controller = MembershipController(
            NetworkMembershipGateway(api),
            TestScope(UnconfinedTestDispatcher(testScheduler)),
        )

        controller.submitUid("lbank", "12345678")

        assertEquals(UidSubmission.Sent, controller.submission.value)
        assertEquals(
            MembershipStatus.VERIFYING,
            (controller.state.value as MembershipUiState.Ready).state.status,
        )
        // A second read here would race the server's own write and could show the state before it.
        assertEquals(0, api.statusCalls)
    }

    @Test
    fun `a second submission while one is in flight is dropped, not queued`() = runTest {
        val api = RecordingApi(MembershipDto(status = "verifying"))
        // A *standard* dispatcher, deliberately: an unconfined one runs the first submission to
        // completion before the second call is made, so the guard is never reached and the test
        // would pass without testing anything. Queued, both taps land while the first is in flight,
        // which is the situation a double tap actually creates.
        val controller = MembershipController(
            NetworkMembershipGateway(api),
            TestScope(StandardTestDispatcher(testScheduler)),
        )

        controller.submitUid("lbank", "1")
        controller.submitUid("lbank", "1")
        runCurrent()

        // Five submissions per ten minutes per account. A double tap that spends two of them locks
        // the reader out of their own verification.
        assertEquals(1, api.submitCalls)
    }

    private fun gateway(dto: MembershipDto) = NetworkMembershipGateway(RecordingApi(dto))

    private fun NetworkMembershipGateway.statusNow(): MembershipState = kotlinx.coroutines.runBlocking {
        (status() as AppResult.Success).value
    }
}

private class RecordingApi(private val answer: MembershipDto) : MembershipApi {
    var sentUid: String? = null
    var sentExchange: String? = null
    var submitCalls = 0
    var statusCalls = 0

    override suspend fun status(): MembershipDto {
        statusCalls++
        return answer
    }

    override suspend fun submitUid(body: SubmitUidRequest): MembershipDto {
        submitCalls++
        sentUid = body.uid
        sentExchange = body.exchange
        return answer
    }
}
