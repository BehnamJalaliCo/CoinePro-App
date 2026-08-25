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

    @Test
    fun `persian and arabic digits fold to latin so a typed national id is not stripped away`() {
        assertEquals("0012345678", "۰۰۱۲۳۴۵۶۷۸".foldDigitsToLatin())
        assertEquals("0987654321", "٠٩٨٧٦٥٤٣٢١".foldDigitsToLatin())
        assertEquals("+989121234567", "+۹۸۹۱۲۱۲۳۴۵۶۷".foldDigitsToLatin())
        assertEquals("already-latin-1", "already-latin-1".foldDigitsToLatin())
    }
}

private class FakeAccountGateway(
    var briefing: AppResult<AccountBriefing?> = AppResult.Success(null),
    var portfolio: AppResult<AccountPortfolio> = AppResult.Success(AccountPortfolio()),
    var kyc: AppResult<KycStatus> = AppResult.Success(KycStatus(0, KycState.NOT_STARTED)),
) : AccountGateway {
    override suspend fun briefing() = briefing
    override suspend fun portfolio() = portfolio
    override suspend fun kyc() = kyc
    override suspend fun submitKycLevel1(
        fullName: String,
        nationalId: String,
        birthDate: String,
        phone: String,
    ) = kyc
}
