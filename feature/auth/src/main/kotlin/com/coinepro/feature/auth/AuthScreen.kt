package com.coinepro.feature.auth

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.ProChartLockup
import com.coinepro.core.designsystem.resolve
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProThinkingDots
import com.coinepro.core.auth.LoginConfigState
import com.coinepro.core.auth.SessionState
import com.coinepro.core.auth.TelegramAuthPayload
import org.json.JSONObject

@Composable
fun AuthScreen(
    state: SessionState,
    loginConfigState: LoginConfigState,
    onRetryLoginConfig: () -> Unit,
    onRetry: () -> Unit,
    onLogout: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CoineProColors.Stage)
            .padding(CoineProSpacing.Three),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ProChartLockup(
            wordmarkWidth = 260.dp,
            contentDescription = stringResource(R.string.auth_wordmark_description),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.auth_secure_sign_in),
            style = MaterialTheme.typography.bodyLarge,
            color = CoineProColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(CoineProSpacing.Two))
        Text(
            text = stringResource(R.string.auth_flow),
            style = MaterialTheme.typography.bodyMedium,
            color = CoineProColors.TextMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(CoineProSpacing.One))
        // The risk warning is the one thing on this screen that must not read as boilerplate, so
        // it keeps a warning colour rather than joining the grey copy above it.
        Text(
            text = stringResource(R.string.auth_risk),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.Warning,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(CoineProSpacing.Four))

        when (state) {
            SessionState.Loading -> CoineProThinkingDots()
            SessionState.SignedOut -> when (loginConfigState) {
                LoginConfigState.Loading -> {
                    CoineProThinkingDots()
                    Spacer(Modifier.height(CoineProSpacing.OneHalf))
                    Text(
                        text = stringResource(R.string.auth_loading_config),
                        style = MaterialTheme.typography.bodyMedium,
                        color = CoineProColors.TextSecondary,
                    )
                }
                is LoginConfigState.Error -> {
                    // Owned copy, resolved in the reader's language — see `UiMessage`. It used to
                    // be an English sentence written in the controller and shown as it stood.
                    Text(
                        text = loginConfigState.message.resolve(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = CoineProColors.Sell,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(CoineProSpacing.Two))
                    CoineProPrimaryButton(
                        text = stringResource(R.string.auth_retry_config),
                        onClick = onRetryLoginConfig,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                is LoginConfigState.Ready -> TelegramSignInNote()
            }
            is SessionState.RevalidationRequired -> {
                Text(
                    text = state.message.resolve(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoineProColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(CoineProSpacing.Two))
                CoineProPrimaryButton(
                    text = stringResource(R.string.auth_retry),
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(CoineProSpacing.One))
                CoineProSecondaryButton(
                    text = stringResource(R.string.auth_sign_out),
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            is SessionState.SignedIn -> Unit
        }
    }
}

/**
 * Why there is no Telegram button here any more.
 *
 * The app used to embed Telegram's Login Widget in a WebView, loaded with
 * `loadDataWithBaseURL("https://telegram.org/", …)`. That could never authenticate anybody, and it
 * is worth writing down why so nobody puts it back. The widget asks `oauth.telegram.org` to sign a
 * payload for a given page origin, and Telegram checks that origin against the domain the bot's
 * owner registered with BotFather. The origin here was `telegram.org` — a domain nobody can
 * register as their own bot's — so Telegram refused every time and rendered its own error inside
 * the frame. That error is what a reader saw: a sign-in method that looked available, opened, and
 * then complained about the bot.
 *
 * Removed rather than fixed in place, because it cannot be fixed in place. The widget needs a real
 * page on a registered domain and a mobile app has no page. The supported mobile shape is a bot
 * deep link plus a server route that mints a session from it, which CoinePro-FX does not serve
 * today — `docs/REQUEST3_COINEPROFX.md` asks for it. `SessionController.completeTelegramLogin` and
 * the server's own `/user/auth/telegram` are both left standing for when it arrives.
 */
@Composable
private fun TelegramSignInNote() {
    Text(
        text = stringResource(R.string.auth_telegram_unavailable),
        style = MaterialTheme.typography.bodySmall,
        color = CoineProColors.TextMuted,
        textAlign = TextAlign.Center,
    )
}
