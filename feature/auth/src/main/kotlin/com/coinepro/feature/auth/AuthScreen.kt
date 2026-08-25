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
import com.coinepro.core.designsystem.CoineProLockup
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
    onTelegramPayload: (TelegramAuthPayload) -> Unit,
    onRetryLoginConfig: () -> Unit,
    onRetry: () -> Unit,
    onLogout: () -> Unit,
) {
    var showTelegram by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CoineProColors.Stage)
            .padding(CoineProSpacing.Three),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CoineProLockup(
            markSize = 112.dp,
            wordmarkWidth = 190.dp,
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
                    // Server wording, verbatim.
                    Text(
                        text = loginConfigState.message,
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
                is LoginConfigState.Ready -> {
                    if (showTelegram) {
                        TelegramLoginWebView(loginConfigState.botUsername, onTelegramPayload)
                        Spacer(Modifier.height(CoineProSpacing.Two))
                        CoineProSecondaryButton(
                            text = stringResource(R.string.auth_cancel),
                            onClick = { showTelegram = false },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        CoineProPrimaryButton(
                            text = stringResource(R.string.auth_continue_telegram),
                            onClick = { showTelegram = true },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            is SessionState.RevalidationRequired -> {
                Text(
                    text = state.message,
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

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
internal fun TelegramLoginWebView(
    botUsername: String,
    onPayload: (TelegramAuthPayload) -> Unit,
) {
    val safeBot = botUsername.takeIf { it.matches(Regex("^[A-Za-z0-9_]{5,32}$")) } ?: return
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(180.dp),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.domStorageEnabled = false
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                webViewClient = WebViewClient()
                addJavascriptInterface(TelegramBridge(onPayload), "CoineProAuth")
                loadDataWithBaseURL(
                    "https://telegram.org/",
                    telegramHtml(safeBot),
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
        },
    )
}

private class TelegramBridge(
    private val onPayload: (TelegramAuthPayload) -> Unit,
) {
    @JavascriptInterface
    fun onAuth(json: String) {
        runCatching {
            val value = JSONObject(json)
            TelegramAuthPayload(
                id = value.getLong("id"),
                firstName = value.optString("first_name").takeIf { it.isNotBlank() },
                lastName = value.optString("last_name").takeIf { it.isNotBlank() },
                username = value.optString("username").takeIf { it.isNotBlank() },
                photoUrl = value.optString("photo_url").takeIf { it.isNotBlank() },
                authDate = value.getLong("auth_date"),
                hash = value.getString("hash"),
            )
        }.onSuccess { payload ->
            Handler(Looper.getMainLooper()).post { onPayload(payload) }
        }
    }
}

private fun telegramHtml(botUsername: String) = """
<!doctype html>
<html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head>
<body style="margin:0;background:transparent;text-align:center">
<script>
function onTelegramAuth(user) { CoineProAuth.onAuth(JSON.stringify(user)); }
</script>
<script async src="https://telegram.org/js/telegram-widget.js?22"
 data-telegram-login="$botUsername"
 data-size="large"
 data-userpic="false"
 data-onauth="onTelegramAuth(user)"></script>
</body></html>
""".trimIndent()
