package com.coinepro.core.auth

import com.coinepro.core.common.AppResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Somewhere to keep a half-finished registration across a process death.
 *
 * Registration is two steps with a wait in the middle, and the wait is somebody leaving the app to
 * open their e-mail. Android is free to kill the process while they are gone — and it does — which
 * used to lose the registration token held in memory here. What that looks like from the outside is
 * the worst possible thing: the reader typed their details, got a code, came back, and found a
 * sign-in screen for an account that was never created. Then they try to sign in and are told,
 * accurately, that the credentials are wrong.
 *
 * So the token is written down. It is not a credential — it names an unfinished registration and is
 * spent by the code — and the password is deliberately **not** stored beside it.
 */
interface RegistrationMemory {
    suspend fun save(pending: PendingRegistration?)

    suspend fun load(): PendingRegistration?
}

/** A registration that has been started and not yet verified. */
data class PendingRegistration(val registrationToken: String, val email: String)

/** Which part of the flow the reader is in. One screen renders all of them. */
enum class EmailAuthStep {
    SIGN_IN,
    REGISTER,
    VERIFY_CODE,
    FORGOT_PASSWORD,
    RESET_PASSWORD,
}

/**
 * Something worth telling the reader that is not an error.
 *
 * These are app copy rather than server text, because the server has no message for them — the
 * response is a bare success. Resolving them to words is the UI's job; naming them here keeps this
 * module free of Android resources.
 */
enum class EmailAuthNotice {
    /** A verification code was sent to the address being registered. */
    CODE_SENT,

    /**
     * Recovery was requested. The wording must not confirm the address exists — the server answers
     * identically either way precisely so that it cannot be used to test for accounts, and copy
     * that says "check your inbox" as though delivery were certain would give that back.
     */
    RESET_REQUESTED,

    /** The password was changed and the reader can now sign in with it. */
    PASSWORD_CHANGED,
}

data class EmailAuthUiState(
    val methods: AuthMethods = AuthMethods(),
    val methodsKnown: Boolean = false,
    val step: EmailAuthStep = EmailAuthStep.SIGN_IN,
    val busy: Boolean = false,
    val failure: AuthFailure? = null,
    val notice: EmailAuthNotice? = null,
    /** Seconds until another verification code may be requested; zero when one may be now. */
    val resendAvailableIn: Int = 0,
    /** Seconds until a rate-limited step may be retried, from the server's Retry-After. */
    val retryAvailableIn: Int = 0,
    /** Carried between the two registration steps so the code screen can name the address. */
    val pendingEmail: String = "",
) {
    /** A rate limit the server put a clock on: the button stays down until it runs out. */
    val waiting: Boolean get() = retryAvailableIn > 0
}

/**
 * Drives the email-first flow and owns nothing else.
 *
 * Token storage, profile state and platform switching stay where they already are; a successful
 * credential step is handed to [onAuthenticated] and this controller forgets it. That keeps the one
 * piece of the app most likely to be rewritten — the sign-in screens — from becoming a second place
 * that decides what "signed in" means.
 *
 * Nothing here retries on the reader's behalf. Every step is a deliberate act with a consequence at
 * the far end (an account created, a password changed, an attempt spent against a limit), and a
 * silent second attempt would spend one of a small number of tries without anyone asking for it.
 */
class EmailAuthController(
    private val gateway: EmailAuthGateway,
    private val scope: CoroutineScope,
    private val onAuthenticated: suspend (EmailAuthSession) -> Unit,
    /** Null keeps the old in-memory behaviour, which is what the tests want. */
    private val memory: RegistrationMemory? = null,
) {
    private val stateMutable = MutableStateFlow(EmailAuthUiState())
    val state: StateFlow<EmailAuthUiState> = stateMutable.asStateFlow()

    private var registration: RegistrationChallenge? = null
    private var countdown: Job? = null

    /**
     * Asks which ways in exist. Until this answers, [EmailAuthUiState.methodsKnown] stays false and
     * the screen shows no buttons rather than a guess at which ones work.
     */
    fun loadMethods() {
        scope.launch {
            when (val result = gateway.methods()) {
                is AppResult.Success ->
                    stateMutable.update { it.copy(methods = result.value, methodsKnown = true) }
                is AppResult.Failure ->
                    stateMutable.update { it.copy(failure = result.toAuthFailure(), methodsKnown = false) }
            }
        }
    }

    fun goTo(step: EmailAuthStep) {
        stateMutable.update { it.copy(step = step, failure = null, notice = null) }
    }

    fun dismissFailure() = stateMutable.update { it.copy(failure = null) }

    fun dismissNotice() = stateMutable.update { it.copy(notice = null) }

    fun signIn(email: String, password: String) = run {
        gateway.signIn(email.normalizedAddress(), password).authenticated()
    }

    fun signInWithGoogle(idToken: String) = run {
        gateway.signInWithGoogle(idToken).authenticated()
    }

    /**
     * Google refused before the server was ever asked.
     *
     * Reported here so the one screen shows one failure area rather than two. [message] is Google's
     * own wording where it gave one and is shown as written; where it gave none the screen falls
     * back to its own copy, because inventing a reason on Google's behalf would be a guess in
     * somebody else's voice.
     */
    fun reportGoogleFailure(message: String?) {
        stateMutable.update {
            it.copy(
                busy = false,
                notice = null,
                failure = AuthFailure(AuthFailureReason.UNREACHABLE, message),
            )
        }
    }

    fun startRegistration(email: String, password: String, fullName: String) = run {
        val address = email.normalizedAddress()
        when (val result = gateway.startRegistration(address, password, fullName.trim())) {
            is AppResult.Success -> {
                registration = result.value
                memory?.save(PendingRegistration(result.value.registrationToken, address))
                stateMutable.update {
                    it.copy(
                        step = EmailAuthStep.VERIFY_CODE,
                        pendingEmail = address,
                        notice = EmailAuthNotice.CODE_SENT,
                    )
                }
                startCooldown(result.value.cooldownSeconds)
            }
            is AppResult.Failure -> fail(result)
        }
    }

    fun verifyCode(code: String) = run {
        val token = registration?.registrationToken ?: return@run restartRegistration()
        val result = gateway.verifyRegistration(token, code.trim())
        // Cleared on success only. A wrong code leaves the registration open, which is the whole
        // point of having written it down.
        if (result is AppResult.Success) memory?.save(null)
        result.authenticated()
    }

    /**
     * Picks a half-finished registration back up.
     *
     * Called when the sign-in surface appears. Landing the reader on the code screen with their own
     * address already named is the difference between "where did my sign-up go" and "oh, right".
     */
    fun resume() {
        val store = memory ?: return
        scope.launch {
            val pending = store.load() ?: return@launch
            registration = RegistrationChallenge(pending.registrationToken, cooldownSeconds = null)
            stateMutable.update {
                it.copy(step = EmailAuthStep.VERIFY_CODE, pendingEmail = pending.email)
            }
        }
    }

    /**
     * Abandons the half-finished registration and returns to the form.
     *
     * There is no resend endpoint — the server's `cooldown_seconds` governs how soon registration
     * may be started again, and starting again is what sends another code. So the app offers
     * exactly that, gated on the same cooldown, rather than a resend button that would have to be
     * backed by keeping the reader's password in memory for the length of the wait.
     */
    fun startOver() {
        if (stateMutable.value.resendAvailableIn > 0) return
        restartRegistration()
    }

    fun requestPasswordReset(email: String) = run {
        when (val result = gateway.requestPasswordReset(email.normalizedAddress())) {
            is AppResult.Success ->
                stateMutable.update { it.copy(notice = EmailAuthNotice.RESET_REQUESTED) }
            is AppResult.Failure -> fail(result)
        }
    }

    fun resetPassword(resetToken: String, newPassword: String) = run {
        when (val result = gateway.resetPassword(resetToken.trim(), newPassword)) {
            is AppResult.Success -> stateMutable.update {
                it.copy(step = EmailAuthStep.SIGN_IN, notice = EmailAuthNotice.PASSWORD_CHANGED)
            }
            is AppResult.Failure -> fail(result)
        }
    }

    /**
     * A registration token the app no longer holds means the process was interrupted — the app was
     * killed between the two steps. Sending the reader back to the start is the honest move: the
     * code in their inbox belongs to a registration this install can no longer complete.
     */
    private fun restartRegistration() {
        registration = null
        // Forgotten here too, or a reader who chose to start over would be put back on the code
        // screen for the abandoned attempt the next time the app opened.
        memory?.let { store -> scope.launch { store.save(null) } }
        stateMutable.update {
            it.copy(step = EmailAuthStep.REGISTER, failure = null, notice = null, pendingEmail = "")
        }
    }

    private fun run(block: suspend () -> Unit) {
        if (stateMutable.value.busy || stateMutable.value.waiting) return
        stateMutable.update { it.copy(busy = true, failure = null, notice = null) }
        scope.launch {
            try {
                block()
            } finally {
                stateMutable.update { it.copy(busy = false) }
            }
        }
    }

    private suspend fun AppResult<EmailAuthSession>.authenticated() = when (this) {
        is AppResult.Success -> onAuthenticated(value)
        is AppResult.Failure -> fail(this)
    }

    private fun fail(failure: AppResult.Failure) {
        val authFailure = failure.toAuthFailure()
        stateMutable.update { it.copy(failure = authFailure) }
        authFailure.retryAfterSeconds?.let(::startRetryWait)
    }

    private fun startCooldown(seconds: Int?) {
        val value = seconds?.takeIf { it > 0 } ?: return
        stateMutable.update { it.copy(resendAvailableIn = value) }
        tick()
    }

    private fun startRetryWait(seconds: Int) {
        stateMutable.update { it.copy(retryAvailableIn = seconds.coerceAtLeast(0)) }
        tick()
    }

    /**
     * One ticker serves both counters, and it stops as soon as neither is running.
     *
     * A countdown is the only continuously moving thing on this screen, so it must not outlive the
     * reason it exists — a timer left running after the wait is over is exactly the kind of idle
     * animation the app's motion policy is written to prevent.
     */
    private fun tick() {
        if (countdown?.isActive == true) return
        countdown = scope.launch {
            while (isActive) {
                delay(1_000)
                var running = false
                stateMutable.update { current ->
                    val resend = (current.resendAvailableIn - 1).coerceAtLeast(0)
                    val retry = (current.retryAvailableIn - 1).coerceAtLeast(0)
                    running = resend > 0 || retry > 0
                    current.copy(resendAvailableIn = resend, retryAvailableIn = retry)
                }
                if (!running) return@launch
            }
        }
    }
}

/**
 * An e-mail address as the server should see it: trimmed, and lower-cased.
 *
 * The case matters more than it looks. A phone keyboard capitalises the first letter of a field
 * often enough that the same person types `Panizd…@gmail.com` when registering and `panizd…` when
 * signing in — and a server that compares the local part exactly then answers, correctly from its
 * own point of view and falsely from theirs, that the password is wrong. There is no case in which
 * a reader means two different accounts by two spellings of one address, so this is normalised at
 * the one place every step goes through rather than trusted to five call sites and a keyboard.
 *
 * The domain is case-insensitive by the RFC; the local part is not, in theory. In practice no mail
 * provider a reader of this app uses treats it as case-sensitive, and the alternative — lower-
 * casing the domain only — leaves exactly the bug above in place.
 */
private fun String.normalizedAddress(): String = trim().lowercase()
