package com.coinepro.feature.auth

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.coinepro.core.auth.SessionState
import com.coinepro.core.auth.TelegramAuthPayload
import org.json.JSONObject

@Composable
fun AuthScreen(
    state: SessionState,
    botUsername: String?,
    onTelegramPayload: (TelegramAuthPayload) -> Unit,
    onRetry: () -> Unit,
    onLogout: () -> Unit,
) {
    var showTelegram by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("CoinePro", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(12.dp))
        Text("Secure sign-in", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Text(
            "Signal → Analysis → Entry / SL / TP → explicit execution confirmation → Monitor → Result / History",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Trading involves risk of loss. Signals and AI analysis are not guaranteed outcomes, and Android never invents provider or execution success.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))

        when (state) {
            SessionState.Loading -> CircularProgressIndicator()
            SessionState.SignedOut -> {
                if (showTelegram && botUsername != null) {
                    TelegramLoginWebView(botUsername, onTelegramPayload)
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = { showTelegram = false }) { Text("Cancel") }
                } else {
                    Button(
                        enabled = botUsername != null,
                        onClick = { showTelegram = true },
                    ) { Text(if (botUsername == null) "Loading Telegram login…" else "Continue with Telegram") }
                }
            }
            is SessionState.RevalidationRequired -> {
                Text(state.message, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onRetry) { Text("Retry") }
                OutlinedButton(onClick = onLogout) { Text("Sign out") }
            }
            is SessionState.SignedIn -> Unit
        }
    }
}

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
private fun TelegramLoginWebView(
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
