@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.coinepro.web

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.ComposeViewport
import com.coinepro.app.AppLanguageStore
import com.coinepro.app.WebApp
import com.coinepro.app.WebLaunch
import com.coinepro.app.di.WebGraph
import com.coinepro.core.chart.ChartWeb
import com.coinepro.core.common.AppLocale
import com.coinepro.core.database.CoineProDatabase
import com.coinepro.core.database.WebCoineProDatabase
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

/*
 * The page's entry point — the browser's `Application.onCreate` and `attachBaseContext` together:
 * the language chosen, the database registered, the workers registered, the typeface and the
 * strings and the text assets in memory, then one Compose surface with the app in it.
 */

private fun onKeyEscapeJs(callback: () -> Unit): Unit =
    js("window.addEventListener('keydown', function (e) { if (e.key === 'Escape') callback(); })")
private fun onPopStateJs(callback: () -> Unit): Unit =
    js("(function(){ history.pushState({ app: true }, ''); window.addEventListener('popstate', function () { callback(); }); })()")
private fun pushStateJs(): Unit = js("history.pushState({ app: true }, '')")
private fun historyBackJs(): Unit = js("history.back()")
private fun onErrorJs(callback: (String) -> Unit): Unit =
    js("window.addEventListener('error', function (e) { callback(String(e && e.message || e)); })")

private val FONTS = listOf("iranyekanx_regular", "iranyekanx_medium", "iranyekanx_semibold", "iranyekanx_bold")

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // `attachBaseContext`: the stored language, before anything reads a string.
    AppLanguageStore.apply(android.content.Context.Page)
    Strings.persian = !AppLocale.english
    setDocumentLanguage(if (Strings.persian) "fa" else "en", rtl = Strings.persian)
    ChartWeb.persian = Strings.persian

    // What Hilt and Room are on the phone.
    androidx.room.WebRoom.register(CoineProDatabase::class) { WebCoineProDatabase() }
    WebGraph.registerWorkers()
    androidx.biometric.BiometricManager.probe()
    onErrorJs { message -> com.coinepro.web.jvm.WebThread.report(IllegalStateException(message)) }

    MainScope().launch {
        listOf(
            async { WebFonts.preload(FONTS) },
            async { Strings.load() },
            async { DrawableIndex.load() },
            async { com.coinepro.web.assets.Assets.preload(); true },
        ).awaitAll()
        ChartWeb.fontFamily = androidx.compose.ui.text.font.FontFamily(
            FONTS.zip(listOf(400, 500, 600, 700)).map { (name, weight) ->
                WebFonts.font(name, androidx.compose.ui.text.font.FontWeight(weight), androidx.compose.ui.text.font.FontStyle.Normal)
            },
        )
        // `CoineProApplication.onCreate`: the process-wide start-up the phone runs once.
        com.coinepro.core.diagnostics.CrashReport(android.content.Context.Page, WebGraph.p_appLog).install()
        WebGraph.p_appLog.info(
            tag = com.coinepro.core.diagnostics.LogTag.LIFECYCLE,
            message = "process start",
            fields = mapOf("version" to com.coinepro.app.BuildConfig.VERSION_NAME, "debug" to "false"),
        )
        WebGraph.i_EntitlementStartUp.begin()
        com.coinepro.app.notifications.NotificationChannels.ensure(android.content.Context.Page)
        installImageLoader()
        WebLaunch.install()
        installBack()
        hideSplash()
        ComposeViewport(viewportContainerId = "terminal") {
            PageConfiguration { WebApp() }
        }
    }
}

/**
 * Back: the browser's button and the Escape key go to whatever on screen registered a back handler,
 * exactly as the phone's back gesture does; with none, the history entry is left alone.
 */
private fun installBack() {
    onPopStateJs {
        MainScope().launch {
            if (androidx.activity.compose.WebBack.dispatch()) pushStateJs() else historyBackJs()
        }
    }
    onKeyEscapeJs { MainScope().launch { androidx.activity.compose.WebBack.dispatch() } }
}

/**
 * `LocalConfiguration`, from the window the page is given: its size in dp for the adaptive layouts,
 * the language the reader chose, the system's light or dark, and the reading direction.
 */
@Composable
private fun PageConfiguration(content: @Composable () -> Unit) {
    val window = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val widthDp = with(density) { window.width.toDp().value.toInt() }
    val heightDp = with(density) { window.height.toDp().value.toInt() }
    val dark = isSystemInDarkTheme()
    val rtl = Strings.persian
    val configuration = remember(widthDp, heightDp, dark, rtl) {
        android.content.res.Configuration(
            screenWidthDp = widthDp,
            screenHeightDp = heightDp,
            smallestScreenWidthDp = minOf(widthDp, heightDp),
            locales = android.content.res.LocaleList(java.util.Locale.getDefault()),
            orientation = if (widthDp >= heightDp) android.content.res.Configuration.ORIENTATION_LANDSCAPE
            else android.content.res.Configuration.ORIENTATION_PORTRAIT,
            uiMode = if (dark) android.content.res.Configuration.UI_MODE_NIGHT_YES else android.content.res.Configuration.UI_MODE_NIGHT_NO,
            densityDpi = (density.density * 160).toInt(),
            layoutDirection = if (rtl) 1 else 0,
        )
    }
    CompositionLocalProvider(
        LocalConfiguration provides configuration,
        LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
        content = content,
    )
}

/**
 * Coil's loader, as `CoineProApplication.newImageLoader` builds it, with the page's one difference:
 * an address on either backend is fetched through the relay (`WebRoutes`), because neither answers
 * a cross-origin read from this page.
 */
private fun installImageLoader() {
    coil3.SingletonImageLoader.setSafe { context ->
        coil3.ImageLoader.Builder(context)
            .components {
                add(coil3.network.ktor3.KtorNetworkFetcherFactory())
                add(
                    coil3.map.Mapper<String, String> { data, _ ->
                        if (data.startsWith("https://") || data.startsWith("http://")) com.coinepro.web.net.WebRoutes.mapImage(data) else null
                    },
                )
            }
            .build()
    }
}
