package com.coinepro.feature.auth

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import com.coinepro.core.designsystem.CoineProPillShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.foldDigitsToLatin
import com.coinepro.core.auth.AuthFailureReason
import com.coinepro.core.auth.EmailAuthNotice
import com.coinepro.core.auth.EmailAuthStep
import com.coinepro.core.auth.EmailAuthUiState
import com.coinepro.core.auth.TelegramAuthPayload
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.ProChartLockup
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTextField
import com.coinepro.core.designsystem.CoineProThinkingDots

/**
 * Everything a reader does before they have a session: sign in, register, verify, recover.
 *
 * One screen rather than five destinations. The whole flow is a single decision with detours — most
 * people arrive intending to sign in, and the ones who need to register or recover are handled
 * without a navigation stack that can strand them halfway with a half-created account behind them.
 *
 * Nothing here decides anything. The buttons a reader sees are the ones the server said exist, the
 * waits are the ones the server sent, and the refusals are worded by whoever refused.
 */
@Composable
fun EmailAuthScreen(
    state: EmailAuthUiState,
    onSignIn: (email: String, password: String) -> Unit,
    onRegister: (email: String, password: String, fullName: String) -> Unit,
    onVerify: (code: String) -> Unit,
    onStartOver: () -> Unit,
    onRequestReset: (email: String) -> Unit,
    onResetPassword: (token: String, newPassword: String) -> Unit,
    onGoTo: (EmailAuthStep) -> Unit,
    onRetryMethods: () -> Unit,
    onGoogleSignIn: () -> Unit,
    /** Handed a verified Telegram payload; only ever called when the server reports that method. */
    /** Prefilled when the recovery App Link opened the app, empty when the reader will paste it. */
    initialResetToken: String = "",
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CoineProColors.Stage)
            .verticalScroll(rememberScrollState())
            .padding(CoineProSpacing.Three),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 168, down from 220, and the number changed because what it measures did.
        //
        // `wordmarkWidth` is the width of the *name*, and until `ProChartLockup` was fixed the name
        // ignored it: the artwork drew at its own intrinsic 168dp inside whatever box the caller
        // asked for. So 220 was never the width of anything on screen — it was 168dp of name, 26dp
        // of empty box, and a mark sized off a number the name was not obeying. Now that the name
        // fills what it is given, 220 would put a 335dp lockup on a 363dp-wide column: the brand
        // running the full width of a sign-in screen, which is a splash screen rather than a
        // heading. 168 draws the name at exactly the size the artwork was cut for and the whole
        // lockup at 256dp — about seventy percent of the column, which is where a logo sits above a
        // form without becoming the form's subject.
        ProChartLockup(
            wordmarkWidth = 168.dp,
            contentDescription = stringResource(R.string.auth_wordmark_description),
            // One ink, the owner's call: a gold mark beside a near-black name over a form reads as
            // two objects rather than as the product's logo. See `ProChartLockup.markTint`.
            markTint = CoineProColors.TextPrimary,
        )
        // Four rather than three. The brand needs its own air or it reads as the card's cap rather
        // than as the product's name — the same gap TradingView leaves under a sheet's title, and
        // the cheapest thing on this screen that makes it feel unhurried.
        Spacer(Modifier.height(CoineProSpacing.Four))

        CoineProCard(modifier = Modifier.fillMaxWidth()) {
            when (state.step) {
                EmailAuthStep.SIGN_IN ->
                    SignInStep(state, onSignIn, onGoTo, onGoogleSignIn, onRetryMethods)
                EmailAuthStep.REGISTER -> RegisterStep(state, onRegister, onGoTo)
                EmailAuthStep.VERIFY_CODE -> VerifyStep(state, onVerify, onStartOver, onGoTo)
                EmailAuthStep.FORGOT_PASSWORD -> ForgotStep(state, onRequestReset, onGoTo)
                EmailAuthStep.RESET_PASSWORD -> ResetStep(state, initialResetToken, onResetPassword, onGoTo)
            }
        }

        Spacer(Modifier.height(CoineProSpacing.Three))
        // The risk line stays outside the card and keeps its warning colour. It belongs to the
        // product, not to whichever step happens to be showing.
        Text(
            text = stringResource(R.string.auth_risk),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.Warning,
            textAlign = TextAlign.Center,
        )
    }
}

/* ------------------------------------------------------------------- steps */

@Composable
private fun SignInStep(
    state: EmailAuthUiState,
    onSignIn: (String, String) -> Unit,
    onGoTo: (EmailAuthStep) -> Unit,
    onGoogleSignIn: () -> Unit,
    onRetryMethods: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    StepTitle(R.string.auth_sign_in_title)
    Feedback(state)

    // Until the server has said which methods exist, no method is drawn. Guessing would put a
    // button on screen that is certain to fail, and a reader who taps it concludes the fault is
    // theirs rather than the deployment's.
    if (!state.methodsKnown) {
        Waiting(R.string.auth_methods_unknown, onRetryMethods, R.string.auth_methods_retry, state.busy)
        return
    }
    if (!state.methods.any) {
        Notice(stringResource(R.string.auth_methods_none), CoineProColors.Warning)
        return
    }

    if (state.methods.emailPassword) {
        EmailField(email) { email = it }
        PasswordField(password, R.string.auth_password) { password = it }
        Spacer(Modifier.height(CoineProSpacing.OneHalf))
        Action(
            text = stringResource(R.string.auth_sign_in),
            state = state,
            enabled = email.isNotBlank() && password.isNotBlank(),
        ) { onSignIn(email, password) }
    }

    if (state.methods.google) {
        Spacer(Modifier.height(CoineProSpacing.One))
        GoogleButton(onClick = onGoogleSignIn)
    }

    // Telegram is still a live sign-in method on CoinePro-FX and still has no shape that works in
    // an app — see `TelegramSignInNote` in AuthScreen.kt for exactly why the widget could not.
    // Said in one line rather than offered as a button, because the server advertising the method
    // is not the same as this client being able to use it.
    if (state.methods.telegram) {
        Spacer(Modifier.height(CoineProSpacing.One))
        Text(
            text = stringResource(R.string.auth_telegram_unavailable),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (state.methods.emailPassword) {
        Link(R.string.auth_no_account) { onGoTo(EmailAuthStep.REGISTER) }
        Link(R.string.auth_forgot) { onGoTo(EmailAuthStep.FORGOT_PASSWORD) }
    }
}

/**
 * «ادامه با گوگل», with Google's own mark on it.
 *
 * The mark is not decoration and it is not optional. A button that only says "continue with Google"
 * is a claim in text; the G is what a reader recognises before they have read anything, and it is
 * also what Google's sign-in guidelines ask for. It sits on the reading edge — the leading side, so
 * it is on the right in Persian and on the left in English — because a logo at the far end of a
 * full-width button reads as an ornament rather than as the thing being offered.
 *
 * Drawn here rather than through [CoineProSecondaryButton] for one reason: that component takes a
 * label and nothing else, and widening it to take an icon would put an icon slot on forty buttons
 * that must not have one.
 *
 * The mark is never tinted. See the note in `logo_google.xml`.
 */
@Composable
private fun GoogleButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(CoineProPillShape)
            .background(CoineProColors.SurfaceElevated)
            .border(1.dp, CoineProColors.Border, CoineProPillShape)
            .clickable(onClick = onClick)
            .padding(horizontal = CoineProSpacing.Two),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.logo_google),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(CoineProSpacing.OneHalf))
        Text(
            text = stringResource(R.string.auth_continue_google),
            style = MaterialTheme.typography.labelLarge,
            color = CoineProColors.TextPrimary,
        )
    }
}

@Composable
private fun RegisterStep(
    state: EmailAuthUiState,
    onRegister: (String, String, String) -> Unit,
    onGoTo: (EmailAuthStep) -> Unit,
) {
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    StepTitle(R.string.auth_register_title)
    Feedback(state)

    CoineProTextField(
        value = fullName,
        onValueChange = { fullName = it },
        label = stringResource(R.string.auth_full_name),
        modifier = Modifier.fillMaxWidth(),
    )
    EmailField(email) { email = it }
    PasswordField(password, R.string.auth_password, newPassword = true) { password = it }
    Hint(R.string.auth_password_hint)

    Spacer(Modifier.height(CoineProSpacing.OneHalf))
    Action(
        text = stringResource(R.string.auth_register),
        state = state,
        // The length is checked here only to spare an obviously doomed round trip. The server
        // decides what it accepts, and its refusal is what the reader is shown.
        enabled = fullName.isNotBlank() && email.isNotBlank() && password.length >= MIN_PASSWORD,
    ) { onRegister(email, password, fullName) }

    Link(R.string.auth_have_account) { onGoTo(EmailAuthStep.SIGN_IN) }
}

@Composable
private fun VerifyStep(
    state: EmailAuthUiState,
    onVerify: (String) -> Unit,
    onStartOver: () -> Unit,
    onGoTo: (EmailAuthStep) -> Unit,
) {
    var code by remember { mutableStateOf("") }

    StepTitle(R.string.auth_verify_title)
    // Isolated: an address is a left-to-right run inside a right-to-left sentence, and one
    // beginning or ending with a digit reorders the whole line without it.
    Body(stringResource(R.string.auth_verify_body, BidiText.isolateLtr(state.pendingEmail)))
    Feedback(state)

    CoineProTextField(
        value = code,
        // Folded before filtering, not after. A Persian keyboard produces ۰-۹ by default and those
        // are Unicode category Nd, so isDigit() keeps them and the field would send Persian numerals
        // while showing what looks like a correct code — the reader sees "wrong code" and has no way
        // to spot the difference. The length stays unenforced: the contract names the field and not
        // its shape, and insisting on six would kill the button for good if it were ever five.
        onValueChange = { entered ->
            code = entered.foldDigitsToLatin().filter(Char::isDigit).take(MAX_CODE_LENGTH)
        },
        label = stringResource(R.string.auth_code),
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Done,
        ),
        // Android reads the code out of the SMS or the notification and offers it here. Without
        // this the reader switches apps to read six digits and types them back by hand.
        autofill = ContentType.SmsOtpCode,
    )

    Spacer(Modifier.height(CoineProSpacing.OneHalf))
    Action(
        text = stringResource(R.string.auth_verify_submit),
        state = state,
        enabled = code.length >= MIN_CODE_LENGTH,
    ) { onVerify(code) }

    Spacer(Modifier.height(CoineProSpacing.One))
    // There is no resend route: the server's cooldown governs starting registration again, and
    // starting again is what sends another code. The countdown is shown rather than the control
    // hidden, because a button that does nothing when tapped teaches a reader to tap it repeatedly.
    if (state.resendAvailableIn > 0) {
        Hint(R.string.auth_start_over_in, state.resendAvailableIn)
    } else {
        CoineProSecondaryButton(
            text = stringResource(R.string.auth_start_over),
            onClick = onStartOver,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Link(R.string.auth_back_to_sign_in) { onGoTo(EmailAuthStep.SIGN_IN) }
}

@Composable
private fun ForgotStep(
    state: EmailAuthUiState,
    onRequestReset: (String) -> Unit,
    onGoTo: (EmailAuthStep) -> Unit,
) {
    var email by remember { mutableStateOf("") }

    StepTitle(R.string.auth_forgot_title)
    Body(stringResource(R.string.auth_forgot_body))
    Feedback(state)

    EmailField(email) { email = it }
    Spacer(Modifier.height(CoineProSpacing.OneHalf))
    Action(
        text = stringResource(R.string.auth_send_reset),
        state = state,
        enabled = email.isNotBlank(),
    ) { onRequestReset(email) }

    Link(R.string.auth_reset_title) { onGoTo(EmailAuthStep.RESET_PASSWORD) }
    Link(R.string.auth_back_to_sign_in) { onGoTo(EmailAuthStep.SIGN_IN) }
}

@Composable
private fun ResetStep(
    state: EmailAuthUiState,
    initialResetToken: String,
    onResetPassword: (String, String) -> Unit,
    onGoTo: (EmailAuthStep) -> Unit,
) {
    var token by remember(initialResetToken) { mutableStateOf(initialResetToken) }
    var password by remember { mutableStateOf("") }

    StepTitle(R.string.auth_reset_title)
    Body(stringResource(R.string.auth_reset_body))
    Feedback(state)

    CoineProTextField(
        value = token,
        onValueChange = { token = it },
        label = stringResource(R.string.auth_reset_token),
        modifier = Modifier.fillMaxWidth(),
    )
    PasswordField(password, R.string.auth_new_password, newPassword = true) { password = it }
    Hint(R.string.auth_password_hint)

    Spacer(Modifier.height(CoineProSpacing.OneHalf))
    Action(
        text = stringResource(R.string.auth_reset_submit),
        state = state,
        enabled = token.isNotBlank() && password.length >= MIN_PASSWORD,
    ) { onResetPassword(token, password) }

    Link(R.string.auth_back_to_sign_in) { onGoTo(EmailAuthStep.SIGN_IN) }
}

/* ------------------------------------------------------------------- parts */

/**
 * The one place a step's outcome is rendered, so no step can quietly grow its own wording.
 *
 * A server's own message wins over the app's generic line whenever there is one. The app knows only
 * the shape of a failure; the server knows what actually happened.
 */
@Composable
private fun Feedback(state: EmailAuthUiState) {
    state.notice?.let {
        Notice(stringResource(it.copyRes()), CoineProColors.Buy)
        Spacer(Modifier.height(CoineProSpacing.One))
    }
    state.failure?.let { failure ->
        // The wait is not repeated here: the action button already counts it down, and saying the
        // same number twice on one screen reads as two different waits.
        Notice(failure.message ?: stringResource(failure.reason.copyRes()), CoineProColors.Sell)
        Spacer(Modifier.height(CoineProSpacing.One))
    }
}

/**
 * The step's single gold action.
 *
 * It goes down while a request is in flight and while a server-sent wait is running, and it says so
 * with a countdown rather than by silently ignoring taps.
 */
@Composable
private fun Action(
    text: String,
    state: EmailAuthUiState,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    if (state.busy) {
        CoineProThinkingDots()
        return
    }
    CoineProPrimaryButton(
        text = if (state.waiting) {
            stringResource(R.string.auth_retry_in, state.retryAvailableIn)
        } else {
            text
        },
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled && !state.waiting,
    )
}

@Composable
private fun EmailField(value: String, onChange: (String) -> Unit) {
    CoineProTextField(
        value = value,
        onValueChange = onChange,
        label = stringResource(R.string.auth_email),
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            // Next, not Done. Every screen this field appears on has a password under it, and a
            // keyboard that closes after the address makes the reader reach for the field again.
            imeAction = ImeAction.Next,
        ),
        autofill = ContentType.EmailAddress,
    )
}

/**
 * A password, with a reveal and with the platform's password manager told what it is.
 *
 * [newPassword] is the distinction Android's autofill actually acts on: a *new* password gets
 * offered a generated one and an offer to save, an existing one gets the saved credential filled.
 * Naming them the same way makes registration offer the old password and sign-in offer to generate
 * a new one, which are both exactly the wrong prompt.
 */
@Composable
private fun PasswordField(
    value: String,
    @StringRes label: Int,
    newPassword: Boolean = false,
    onChange: (String) -> Unit,
) {
    CoineProTextField(
        value = value,
        onValueChange = onChange,
        label = stringResource(label),
        modifier = Modifier.fillMaxWidth(),
        secret = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
        autofill = if (newPassword) ContentType.NewPassword else ContentType.Password,
    )
}

/**
 * The card's own heading — «ورود به حساب», «ثبت‌نام», «بازیابی رمز».
 *
 * `headlineSmall`, up from `titleMedium`. This is the only heading on the screen and it names the
 * one thing the reader came to do; at 17sp it was the same size as a field's label and the card
 * opened with no subject at all. Twenty-two is the size a sheet's title is set at in every
 * reference this was measured against, and it is what gives the card a top rather than just an
 * edge.
 *
 * The gap under it grows with it. A heading needs the space below it to belong to the heading — at
 * 8dp under a 22sp line the title read as glued to the first field, which is the specific thing
 * that makes a form look thrown together.
 */
@Composable
private fun StepTitle(@StringRes title: Int) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.headlineSmall,
        color = CoineProColors.TextPrimary,
    )
    Spacer(Modifier.height(CoineProSpacing.Two))
}

@Composable
private fun Body(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = CoineProColors.TextSecondary,
    )
    Spacer(Modifier.height(CoineProSpacing.OneHalf))
}

@Composable
private fun Hint(@StringRes text: Int, vararg args: Any) {
    Text(
        text = stringResource(text, *args),
        style = MaterialTheme.typography.bodySmall,
        color = CoineProColors.TextMuted,
    )
}

@Composable
private fun Notice(message: String, accent: Color) {
    Text(
        text = message,
        modifier = Modifier
            .fillMaxWidth()
            .background(accent.copy(alpha = 0.10f), MaterialTheme.shapes.medium)
            .padding(horizontal = CoineProSpacing.Two, vertical = CoineProSpacing.OneHalf),
        style = MaterialTheme.typography.bodySmall,
        color = accent,
    )
}

@Composable
private fun Waiting(
    @StringRes message: Int,
    onRetry: () -> Unit,
    @StringRes retryLabel: Int,
    busy: Boolean,
) {
    if (busy) {
        CoineProThinkingDots()
        return
    }
    Body(stringResource(message))
    CoineProSecondaryButton(
        text = stringResource(retryLabel),
        onClick = onRetry,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Link(@StringRes text: Int, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(text),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextSecondary,
        )
    }
}

@StringRes
private fun EmailAuthNotice.copyRes(): Int = when (this) {
    EmailAuthNotice.CODE_SENT -> R.string.auth_notice_code_sent
    EmailAuthNotice.RESET_REQUESTED -> R.string.auth_notice_reset_requested
    EmailAuthNotice.PASSWORD_CHANGED -> R.string.auth_notice_password_changed
}

@StringRes
private fun AuthFailureReason.copyRes(): Int = when (this) {
    AuthFailureReason.REJECTED -> R.string.auth_error_rejected
    AuthFailureReason.INVALID -> R.string.auth_error_invalid
    AuthFailureReason.RATE_LIMITED -> R.string.auth_error_rate_limited
    AuthFailureReason.UNREACHABLE -> R.string.auth_error_unreachable
}

/** Matches the server's stated minimum; the server is still what enforces it. */
private const val MIN_PASSWORD = 10
private const val MIN_CODE_LENGTH = 4
private const val MAX_CODE_LENGTH = 8
