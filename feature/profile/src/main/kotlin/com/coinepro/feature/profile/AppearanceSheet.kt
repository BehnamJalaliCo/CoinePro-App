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
import androidx.compose.ui.unit.dp
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
) {
    CoineProSheet(
        title = stringResource(R.string.appearance_title),
        subtitle = stringResource(R.string.appearance_subtitle),
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        AppearanceOptions(selected = selected, onSelect = onSelect)
    }
}

/** The sheet's body without the sheet, so the screenshot tests can see it. See [CoineProSheetBody]. */
@Composable
fun ColumnScope.AppearanceOptions(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
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
