package com.coinepro.core.account

import com.coinepro.core.common.AppResult
import com.coinepro.core.common.ErrorKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountControllerTest {

    @Test
    fun `an empty briefing is a resting state, not a failure`() = runTest {
        val gateway = FakeAccountGateway(briefing = AppResult.Success(null))
        val controller = AccountController(gateway, this)

        controller.refreshBriefing()
        runCurrent()

        assertEquals(BriefingState.Nothing, controller.briefing.value)
    }

    @Test
    fun `a failed briefing carries the server's wording`() = runTest {
        val gateway = FakeAccountGateway(
            briefing = AppResult.Failure(ErrorKind.SERVER, message = "سرویسِ رصد در دسترس نیست."),
        )
        val controller = AccountController(gateway, this)

        controller.refreshBriefing()
        runCurrent()

        assertEquals(
            BriefingState.Unavailable("سرویسِ رصد در دسترس نیست."),
            controller.briefing.value,
        )
    }

    @Test
    fun `a null total survives as null and is never turned into zero`() = runTest {
        val gateway = FakeAccountGateway(portfolio = AppResult.Success(AccountPortfolio()))
        val controller = AccountController(gateway, this)

        controller.refreshPortfolio()
        runCurrent()

        val state = controller.portfolio.value
        assertTrue(state is PortfolioState.Ready)
        assertNull(
            "null means the bridge said nothing; zero would mean the account is empty",
            (state as PortfolioState.Ready).portfolio.total,
        )
    }

    @Test
    fun `a briefing and a portfolio fail independently`() = runTest {
        val gateway = FakeAccountGateway(
            briefing = AppResult.Failure(ErrorKind.SERVER, message = "قطع است."),
            portfolio = AppResult.Success(AccountPortfolio(total = Money(48074.69, "USD"))),
        )
        val controller = AccountController(gateway, this)

        controller.refresh()
        runCurrent()

        assertTrue(controller.briefing.value is BriefingState.Unavailable)
        assertTrue(
            "One card failing must not take the other down",
            controller.portfolio.value is PortfolioState.Ready,
        )
    }

    @Test
    fun `a failed kyc refresh leaves the known status alone`() = runTest {
        val gateway = FakeAccountGateway(
            kyc = AppResult.Success(KycStatus(level = 1, state = KycState.APPROVED)),
        )
        val controller = AccountController(gateway, this)

        controller.refreshKyc()
        runCurrent()
        assertEquals(KycState.APPROVED, controller.kyc.value?.state)

        gateway.kyc = AppResult.Failure(ErrorKind.NETWORK)
        controller.refreshKyc()
        runCurrent()

        assertEquals(
            "Losing the status would read on screen as 'not verified'",
            KycState.APPROVED,
            controller.kyc.value?.state,
        )
    }

    @Test
    fun `an unrecognised kyc state is read as pending, never as approved`() {
        assertEquals(KycState.PENDING, KycState.fromWire("something_new"))
        assertEquals(KycState.PENDING, KycState.fromWire(null))
        assertEquals(KycState.APPROVED, KycState.fromWire("approved"))
        assertEquals(KycState.NOT_STARTED, KycState.fromWire("not_started"))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AccountDeletionTest {

    @Test
    fun `a deleted account ends in Done, which is what signs the reader out`() = runTest {
        val gateway = FakeAccountGateway(deletion = AppResult.Success(DeletionOutcome.DELETED))
        val controller = AccountController(gateway, this)

        controller.deleteAccount()
        runCurrent()

        assertEquals(AccountDeletion.Done, controller.deletion.value)
    }

    @Test
    fun `a server with no deletion route is Unsupported, not a failure`() = runTest {
        val gateway = FakeAccountGateway(deletion = AppResult.Success(DeletionOutcome.UNSUPPORTED))
        val controller = AccountController(gateway, this)

        controller.deleteAccount()
        runCurrent()

        // The distinction is the whole point: Refused would put an error in front of a reader who
        // did nothing wrong, and hide the out-of-app route that does work.
        assertEquals(AccountDeletion.Unsupported, controller.deletion.value)
    }

    @Test
    fun `a refusal carries the server's own wording`() = runTest {
        val gateway = FakeAccountGateway(
            deletion = AppResult.Failure(ErrorKind.VALIDATION, message = "یک معامله‌ی باز دارید"),
        )
        val controller = AccountController(gateway, this)

        controller.deleteAccount()
        runCurrent()

        assertEquals(AccountDeletion.Refused("یک معامله‌ی باز دارید"), controller.deletion.value)
    }
}

private class FakeAccountGateway(
    var briefing: AppResult<AccountBriefing?> = AppResult.Success(null),
    var portfolio: AppResult<AccountPortfolio> = AppResult.Success(AccountPortfolio()),
    var kyc: AppResult<KycStatus> = AppResult.Success(KycStatus(0, KycState.NOT_STARTED)),
    var deletion: AppResult<DeletionOutcome> = AppResult.Success(DeletionOutcome.DELETED),
) : AccountGateway {
    override suspend fun briefing() = briefing
    override suspend fun portfolio() = portfolio
    override suspend fun kyc() = kyc
    override suspend fun submitKycLevel1(identity: KycIdentity) = kyc

    override suspend fun deleteAccount() = deletion
}
