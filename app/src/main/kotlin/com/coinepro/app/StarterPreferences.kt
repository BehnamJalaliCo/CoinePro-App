package com.coinepro.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import com.coinepro.core.designsystem.CONTENT_MAX_WIDTH
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coinepro.core.common.AppLanguage
import com.coinepro.core.datastore.MarketColorScheme
import com.coinepro.core.datastore.QuoteCurrency
import com.coinepro.core.datastore.ThemeMode
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPillShape
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.numeric
import com.coinepro.core.designsystem.rememberCoineProHaptics

/**
 * **The four questions the app asks once, together** (run ΤΦΥ, U2).
 *
 * CoinMarketCap's opening screen, and the reason it works is that it asks everything it is ever
 * going to ask in one place and then never asks again. The alternative — a settings screen the
 * reader has to discover — means an app that looks wrong for weeks to somebody who would have
 * fixed it in four taps on day one.
 *
 * Four, and each is a thing this app cannot guess:
 *
 * * **Theme** — a phone in a pocket at night and a phone on a desk are different screens, and the
 *   system answer is right often enough to be the default and wrong often enough to be asked.
 * * **Language** — Persian is the product's default and a reader who wants English has, until now,
 *   had to find it in the menu. Both options are written **in themselves**, so the row works for
 *   somebody who cannot read the rest of the screen.
 * * **Quote currency** — which pair a list prefers. Not a converter; see [QuoteCurrency].
 * * **Up colour** — green-up is not universal, and in this app's own market it is frequently not
 *   what a reader expects. Asking is cheaper than being wrong on every chart.
 *
 * Nothing here is required and nothing is validated: every row has a value the moment the screen
 * opens, so «بزن بریم» is always live and a reader who touches nothing gets the defaults they were
 * going to get anyway. It is shown once, and `MENU → ظاهر` is where it lives afterwards.
 */
@Composable
fun StarterPreferences(
    theme: ThemeMode,
    onTheme: (ThemeMode) -> Unit,
    language: AppLanguage,
    onLanguage: (AppLanguage) -> Unit,
    quote: QuoteCurrency,
    onQuote: (QuoteCurrency) -> Unit,
    colours: MarketColorScheme,
    onColours: (MarketColorScheme) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Capped and centred on a tablet (MOBILE-11), as the welcome page before it already is: four
    // rows of 330-point pills and a 990-point button changed the flow's width in mid-stride.
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CoineProColors.Stage)
            .systemBarsPadding()
            .testTag(STARTER_TAG),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = CONTENT_MAX_WIDTH)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CoineProSpacing.Gutter)
                .padding(top = CoineProSpacing.Four, bottom = CoineProSpacing.Three),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Three),
        ) {
            Text(
                text = stringResource(R.string.starter_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = CoineProColors.TextPrimary,
            )
            Choice(
                title = stringResource(R.string.starter_theme),
                // The brief's three. `ThemeMode.MIDNIGHT` — the true-black palette — is real and
                // stays reachable in «ظاهر»; a fourth pill here would make the row unreadable on a
                // narrow phone to offer a choice between two darks to somebody who has not seen
                // either yet.
                options = STARTER_THEMES.map { it to stringResource(it.labelRes()) },
                selected = theme,
                onSelect = onTheme,
            )
            Choice(
                title = stringResource(R.string.starter_language),
                // In itself, never translated — the one row that has to work for a reader who
                // cannot read the others. See `AppLanguage.displayName`.
                options = AppLanguage.entries.map { it to it.displayName },
                selected = language,
                onSelect = onLanguage,
            )
            Choice(
                title = stringResource(R.string.starter_quote),
                options = QuoteCurrency.entries.map { it to it.code },
                selected = quote,
                onSelect = onQuote,
                numeric = true,
            )
            // The colour itself, beside its name: «رشد یعنی سبز» is a sentence about a colour.
            // The palette's up ink is whichever colour the chosen scheme rises in, so the option
            // that is on shows it and the other shows the opposite (MOBILE-31).
            val upInk = CoineProColors.MarketUp
            val downInk = CoineProColors.MarketDown
            Choice(
                title = stringResource(R.string.starter_colours),
                options = MarketColorScheme.entries.map { it to stringResource(it.labelRes()) },
                selected = colours,
                onSelect = onColours,
                swatch = { scheme -> if (scheme == colours) upInk else downInk },
            )
            // **Under the last question, not at the foot of the glass** (MOBILE-31). Pinned to the
            // bottom it sat four hundred points below the choices on a tall phone, in a gap that
            // read as a page still loading.
            CoineProPrimaryButton(
                text = stringResource(R.string.starter_done),
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * One question: a heading and a row of pills, exactly one of them filled.
 *
 * Pills rather than a dropdown, because every one of these has four options or fewer and a menu
 * that has to be opened to see what is in it is a question the reader cannot answer at a glance.
 */
@Composable
private fun <T> Choice(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    numeric: Boolean = false,
    /** A small disc of colour before the label, for a choice that *is* a colour. */
    swatch: ((T) -> Color)? = null,
) {
    val haptics = rememberCoineProHaptics()
    Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
            fontWeight = FontWeight.Normal,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
            options.forEach { (value, label) ->
                val on = value == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CoineProPillShape)
                        .background(if (on) CoineProColors.AccentFill else Color.Transparent)
                        .border(
                            width = 1.dp,
                            color = if (on) Color.Transparent else CoineProColors.Border,
                            shape = CoineProPillShape,
                        )
                        .clickable {
                            haptics.select()
                            onSelect(value)
                        }
                        .padding(vertical = CoineProSpacing.OneHalf),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
                    ) {
                        swatch?.let { ink ->
                            Box(modifier = Modifier.size(8.dp).clip(CoineProPillShape).background(ink(value)))
                        }
                        Text(
                            text = label,
                            style = if (numeric) {
                                MaterialTheme.typography.labelMedium.numeric()
                            } else {
                                MaterialTheme.typography.labelMedium
                            },
                            // Dark ink on the gold reads a weight lighter than the same ink on the
                            // stage, so the chosen pill is set a step heavier to look the same
                            // (MOBILE-21).
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (on) CoineProColors.OnAccent else CoineProColors.TextPrimary,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** Kept here rather than on the enum, so `core:datastore` stays free of resources. */
private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.starter_theme_system
    ThemeMode.LIGHT -> R.string.starter_theme_light
    ThemeMode.DARK, ThemeMode.MIDNIGHT -> R.string.starter_theme_dark
}

/** The three this screen offers. See the note at the call site. */
private val STARTER_THEMES = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)

private fun MarketColorScheme.labelRes(): Int = when (this) {
    MarketColorScheme.GREEN_UP -> R.string.starter_colours_green_up
    MarketColorScheme.RED_UP -> R.string.starter_colours_red_up
}

/** So a test can find the screen without knowing what is on it. */
const val STARTER_TAG: String = "starter-preferences"

