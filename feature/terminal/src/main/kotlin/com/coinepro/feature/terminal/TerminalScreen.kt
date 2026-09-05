package com.coinepro.feature.terminal

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.PredictiveBackHandler
import kotlinx.coroutines.CancellationException
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProThinkingDots

/**
 * The web terminal, in a WebView, for the parts that were never ported.
 *
 * Everything about this screen is arranged so that it is a door rather than a home: it is reached
 * by one button, it takes the whole screen, and the system back button walks its history before
 * leaving. A reader who never presses that button never meets a WebView.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TerminalScreen(
    controller: TerminalController,
    onClose: () -> Unit,
) {
    LaunchedEffect(controller) { controller.start() }
    val state by controller.state.collectAsStateWithLifecycle()
    var webView by remember { mutableStateOf<WebView?>(null) }

    // The terminal is a single-page app with its own navigation. Back should walk that first and
    // only leave the screen once there is nothing left to go back to — otherwise one tap on back
    // from four panels deep drops the reader out of the whole feature.
    PredictiveBackHandler(enabled = true) { progress ->
        try {
            progress.collect { }
        } catch (cancelled: CancellationException) {
            return@PredictiveBackHandler
        }
        val view = webView
        if (view != null && view.canGoBack()) view.goBack() else onClose()
    }

    Box(modifier = Modifier.fillMaxSize().background(CoineProColors.Stage)) {
        val url = state.url
        val launchUrl = state.launchUrl
        if (url != null && launchUrl != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        settings.javaScriptEnabled = true
                        // The terminal keeps layouts, watchlists and the script editor's drafts in
                        // browser storage. Without this every visit starts from nothing.
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        // It is a responsive web app, so it lays itself out. Letting the WebView
                        // apply desktop-width heuristics on top produces a page zoomed out to
                        // illegibility on first paint.
                        settings.loadWithOverviewMode = false
                        settings.useWideViewPort = true
                        settings.setSupportZoom(false)
                        settings.builtInZoomControls = false
                        // No file or content access. The page is remote and has no business
                        // reading anything off this device.
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String) {
                                controller.onLoaded()
                            }

                            override fun onReceivedError(
                                view: WebView,
                                request: WebResourceRequest,
                                error: WebResourceError,
                            ) {
                                // Only the main document. A sub-resource that fails — one icon,
                                // one font — is not a page that failed to open, and reporting it
                                // as one would replace a working terminal with an error.
                                if (request.isForMainFrame) controller.onLoadFailed()
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest,
                            ): Boolean {
                                // Everything off the terminal's own host is refused rather than
                                // opened. This WebView holds a token in its storage; letting it
                                // navigate to an arbitrary link would put that token one
                                // same-origin bug away from a page nobody vetted.
                                //
                                // Compared by parsed host, not by string prefix. A prefix test on
                                // `https://terminal.example` also accepts
                                // `https://terminal.example.evil.tld` and
                                // `https://terminal.example@evil.tld`, both of which resolve
                                // somewhere else entirely — see `isTerminalUrl`.
                                return !isTerminalUrl(request.url.toString(), url)
                            }
                        }
                        // The token rides in the fragment of this one address and nothing is
                        // injected into the page at all — see `launchUrl`. Every later navigation
                        // is checked against `url`, which carries no credential.
                        loadUrl(launchUrl)
                    }
                },
                onRelease = { view ->
                    view.stopLoading()
                    view.destroy()
                },
                update = { webView = it },
            )
        }

        if (state.loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CoineProThinkingDots()
            }
        }

        state.error?.let { error ->
            Column(
                modifier = Modifier.fillMaxSize().padding(CoineProSpacing.Gutter),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(
                    CoineProSpacing.OneHalf,
                    Alignment.CenterVertically,
                ),
            ) {
                Text(
                    text = stringResource(
                        when (error) {
                            TerminalError.NOT_CONFIGURED -> R.string.terminal_error_not_configured
                            TerminalError.NO_TOKEN -> R.string.terminal_error_no_token
                            TerminalError.DISABLED -> R.string.terminal_error_disabled
                            TerminalError.LOAD_FAILED -> R.string.terminal_error_load
                        },
                    ),
                    color = CoineProColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
                // Nothing to retry when the address is missing from the build or the server has
                // the door shut. Both would fail identically every time.
                if (error == TerminalError.LOAD_FAILED || error == TerminalError.NO_TOKEN) {
                    CoineProPrimaryButton(
                        text = stringResource(R.string.terminal_retry),
                        onClick = controller::retry,
                    )
                }
            }
        }
    }
}
