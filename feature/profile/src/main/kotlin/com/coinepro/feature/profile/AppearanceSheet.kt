package com.coinepro.feature.profile

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.coinepro.core.common.AppLanguage
import com.coinepro.core.common.BidiText
import com.coinepro.core.datastore.MarketColorScheme
import com.coinepro.core.datastore.ThemeMode
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProDarkPalette
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProLightPalette
import com.coinepro.core.designsystem.CoineProSheet
import com.coinepro.core.designsystem.CoineProSheetBody
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTint
import com.coinepro.core.designsystem.rememberCoineProHaptics

/**
 * Where the reader chooses a palette.
 *
 * A sheet rather than a screen: three options and no explanation worth a page. It is reached from
 * one row on the profile list, which shows the current answer beside it — a settings row whose
 * value you have to open it to learn is a row you open twice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSheet(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** Which colour a rise is drawn in. See [MarketColorScheme]. */
    colours: MarketColorScheme = MarketColorScheme.GREEN_UP,
    onSelectColours: (MarketColorScheme) -> Unit = {},
    /**
     * The reader's language.
     *
     * It lives here because it is the reader's setting and this is where the reader's settings
     * are. It used to sit in the diagnostics panel, which is reached by tapping the version number
     * five times — so the one control in the app that a Persian speaker who opened an English
     * build would need most was behind a gesture nobody would find, in a screen written for an
     * operator. That is not a placement, it is a hiding place.
     */
    language: AppLanguage = AppLanguage.Default,
    onSelectLanguage: (AppLanguage) -> Unit = {},
) {
    CoineProSheet(
        title = stringResource(R.string.appearance_title),
        subtitle = stringResource(R.string.appearance_subtitle),
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        AppearanceOptions(
            selected = selected,
            onSelect = onSelect,
            colours = colours,
            onSelectColours = onSelectColours,
            language = language,
            onSelectLanguage = onSelectLanguage,
        )
    }
}

/** The sheet's body without the sheet, so the screenshot tests can see it. See [CoineProSheetBody]. */
@Composable
fun ColumnScope.AppearanceOptions(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    colours: MarketColorScheme = MarketColorScheme.GREEN_UP,
    onSelectColours: (MarketColorScheme) -> Unit = {},
    language: AppLanguage = AppLanguage.Default,
    onSelectLanguage: (AppLanguage) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.Row),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Row),
    ) {
        ThemeMode.entries.forEach { mode ->
            ThemeOption(
                mode = mode,
                selected = mode == selected,
                onSelect = { onSelect(mode) },
            )
        }

        // Below the theme and under its own heading, because it is a different question. The
        // theme is about the room; this is about what a candle *means*, and getting it wrong
        // inverts every price, percentage and signal direction in the product at once.
        Text(
            text = stringResource(R.string.appearance_colours),
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
            modifier = Modifier.padding(top = CoineProSpacing.Row),
        )
        MarketColorScheme.entries.forEach { scheme ->
            ColourOption(
                scheme = scheme,
                selected = scheme == colours,
                onSelect = { onSelectColours(scheme) },
            )
        }

        // Last, and under the same roof as the other two, because all three answer the same
        // question — how this app should look and read to *this* person — and a reader who has
        // come here to change one of them has come to the right place for the others.
        //
        // Changing it restarts the activity, which is why the note under it says so. Android
        // re-bases the context on the new locale and every string in the tree is resolved again;
        // a change that silently left half the app in the old language would be worse than a
        // visible blink.
        Text(
            text = stringResource(R.string.appearance_language),
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
            modifier = Modifier.padding(top = CoineProSpacing.Row),
        )
        AppLanguage.entries.forEach { option ->
            LanguageOption(
                language = option,
                selected = option == language,
                onSelect = { onSelectLanguage(option) },
            )
        }
        Text(
            text = stringResource(R.string.appearance_language_note),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextMuted,
            textAlign = TextAlign.Right,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * One language, named in itself.
 *
 * «فارسی» and "English", never «فارسی / Persian» — a reader looking for their own language finds it
 * by recognising the word, and a translation is for somebody who does not need this row at all.
 *
 * Drawn as the same filled-and-bordered card [ThemeOption] and [ColourOption] use, because these
 * are three answers to one question and a fourth visual language for the third of them would make
 * the sheet read as three unrelated settings that happen to share a screen.
 */
@Composable
private fun LanguageOption(language: AppLanguage, selected: Boolean, onSelect: () -> Unit) {
    val haptics = rememberCoineProHaptics()
    val accent = CoineProColors.Gold
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (selected) {
                    CoineProTint.fill(accent, CoineProColors.SurfaceElevated)
                } else {
                    CoineProColors.Surface
                },
            )
            .border(
                width = 1.dp,
                color = if (selected) CoineProTint.edge(accent) else CoineProColors.Border,
                shape = MaterialTheme.shapes.medium,
            )
            .clickable {
                haptics.select()
                onSelect()
            }
            .padding(horizontal = CoineProSpacing.CardHorizontal, vertical = CoineProSpacing.Row),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Row),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            // A language name is the one string on this screen that must not be reordered by the
            // row it sits in: "English" inside a right-to-left row loses its own direction without
            // this, and the row whose whole job is to be recognised becomes the one that is not.
            text = BidiText.isolateLtr(language.displayName),
            style = MaterialTheme.typography.bodyMedium,
            color = CoineProColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
    }
}


/**
 * One of the two conventions, shown as the thing itself.
 *
 * Two candles rather than two words: «سبز صعودی» is a sentence a reader has to parse, and a green
 * candle beside a red one is the answer at a glance. They are drawn from the same two palette
 * colours the chart uses, so the swatch cannot drift from what the chart will actually do.
 */
@Composable
private fun ColourOption(
    scheme: MarketColorScheme,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val haptics = rememberCoineProHaptics()
    val accent = CoineProColors.Gold
    val up = if (scheme == MarketColorScheme.GREEN_UP) CoineProColors.Buy else CoineProColors.Sell
    val down = if (scheme == MarketColorScheme.GREEN_UP) CoineProColors.Sell else CoineProColors.Buy
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (selected) {
                    CoineProTint.fill(accent, CoineProColors.SurfaceElevated)
                } else {
                    CoineProColors.Surface
                },
            )
            .border(
                width = 1.dp,
                color = if (selected) CoineProTint.edge(accent) else CoineProColors.Border,
                shape = MaterialTheme.shapes.medium,
            )
            .clickable {
                haptics.select()
                onSelect()
            }
            .padding(horizontal = CoineProSpacing.CardHorizontal, vertical = CoineProSpacing.Row),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Row),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Box(Modifier.size(width = CANDLE_WIDTH, height = SWATCH).background(up))
            Box(Modifier.size(width = CANDLE_WIDTH, height = SWATCH).background(down))
        }
        Text(
            text = stringResource(
                if (scheme == MarketColorScheme.GREEN_UP) {
                    R.string.appearance_green_up
                } else {
                    R.string.appearance_red_up
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = CoineProColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                painter = painterResource(CoineProIcons.Success),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(SWATCH_CHECK),
            )
        }
    }
}

@Composable
private fun ThemeOption(
    mode: ThemeMode,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val haptics = rememberCoineProHaptics()
    val accent = CoineProColors.Gold
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (selected) {
                    CoineProTint.fill(accent, CoineProColors.SurfaceElevated)
                } else {
                    CoineProColors.Surface
                },
            )
            .border(
                width = 1.dp,
                color = if (selected) CoineProTint.edge(accent) else CoineProColors.Border,
                shape = MaterialTheme.shapes.medium,
            )
            .clickable {
                haptics.select()
                onSelect()
            }
            .padding(horizontal = CoineProSpacing.CardHorizontal, vertical = CoineProSpacing.Row),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Row),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The swatch is the honest part of this list: it shows the stage colour each choice
        // produces rather than describing it. `SYSTEM` has two, split down the middle, because it
        // is the only option whose answer is "whichever the phone is".
        ThemeSwatch(mode)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(mode.labelRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = CoineProColors.TextPrimary,
            )
            Text(
                text = stringResource(mode.noteRes()),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
            )
        }
        if (selected) {
            Icon(
                painter = painterResource(CoineProIcons.Success),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(SWATCH_CHECK),
            )
        }
    }
}

@Composable
private fun ThemeSwatch(mode: ThemeMode) {
    Box(
        modifier = Modifier
            .size(SWATCH)
            .clip(CircleShape)
            .border(1.dp, CoineProColors.Border, CircleShape),
    ) {
        when (mode) {
            ThemeMode.DARK -> Box(Modifier.fillMaxSize().background(DARK_STAGE))
            ThemeMode.LIGHT -> Box(Modifier.fillMaxSize().background(LIGHT_STAGE))
            // Two halves, because "follow the phone" has two answers and showing one of them would
            // be showing whichever the phone happens to be at this instant.
            ThemeMode.SYSTEM -> Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight().background(LIGHT_STAGE))
                Box(Modifier.weight(1f).fillMaxHeight().background(DARK_STAGE))
            }
        }
    }
}

/**
 * What the profile row calls this setting.
 *
 * Exported rather than duplicated into the app module's own strings: the sheet and the row that
 * opens it are the same setting, and two resources with the same meaning drift the first time one
 * of them is reworded.
 */
@get:StringRes
val AppearanceTitle: Int get() = R.string.appearance_title

/**
 * The short name of a mode, for the profile row that shows the current answer beside the label.
 *
 * Public because the row lives in the app module and this file owns the wording. Two files naming
 * the same three modes is how they drift.
 */
@StringRes
fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.appearance_system
    ThemeMode.DARK -> R.string.appearance_dark
    ThemeMode.LIGHT -> R.string.appearance_light
}

@StringRes
private fun ThemeMode.noteRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.appearance_system_note
    ThemeMode.DARK -> R.string.appearance_dark_note
    ThemeMode.LIGHT -> R.string.appearance_light_note
}

/**
 * The two stage colours, taken from the palettes themselves.
 *
 * Read from [CoineProDarkPalette] and [CoineProLightPalette] rather than through
 * `CoineProColors.Stage`, which resolves to whichever theme is currently in force and would draw
 * three identical discs. A swatch has to show the theme it *offers*, not the one running.
 */
private val DARK_STAGE = CoineProDarkPalette.stage
private val LIGHT_STAGE = CoineProLightPalette.stage

private val SWATCH = 28.dp
private val SWATCH_CHECK = 18.dp

/** A candle in the colour swatch. Narrow, so two of them read as a pair rather than as a flag. */
private val CANDLE_WIDTH = 9.dp
