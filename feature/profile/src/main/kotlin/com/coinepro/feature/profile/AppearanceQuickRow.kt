package com.coinepro.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.coinepro.core.common.AppLanguage
import com.coinepro.core.datastore.ThemeMode
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProNote
import com.coinepro.core.designsystem.CoineProSegmentedControl
import com.coinepro.core.designsystem.CoineProSpacing

/**
 * The theme and the language, in reach.
 *
 * ### Why this exists when [AppearanceSheet] already does
 *
 * Because the sheet is three taps away — menu, profile, «ظاهر» — and the two settings on it that a
 * reader changes most are the two that decide whether they can *read* the app at all. «حالت تیره و
 * روشن و زبان باید یه جای دم دست باشه تا اینکه انقدر کاربر دنبالش بگرده.» A control somebody has to
 * hunt for is a control they conclude does not exist; the dark-mode switch in particular is the
 * first thing a reader looks for on a finance app at night, and finding it under a profile page is
 * finding it by accident.
 *
 * So both live at the top of the menu tab — one tap from anywhere in the app — as two segmented
 * controls that answer immediately, with no sheet in between. The sheet stays exactly as it is:
 * it carries the third question (which colour a rise is drawn in), which is not one anybody needs
 * on the way past, and it is still where the profile row leads.
 *
 * Two rows rather than one, because the answers are different lengths in both languages and a
 * single row would put «سیستم» beside "English" at two different type sizes to make them fit.
 */
@Composable
fun AppearanceQuickRow(
    theme: ThemeMode,
    onSelectTheme: (ThemeMode) -> Unit,
    language: AppLanguage,
    onSelectLanguage: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Gutter),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
    ) {
        CoineProSegmentedControl(
            options = QUICK_THEMES.map { it to stringResource(it.shortLabelRes()) },
            // Midnight sits under «تیره» here rather than as a fourth segment. This row answers one
            // question — light or dark, now — and four segments across a phone is where a segmented
            // control stops being readable; Midnight is a refinement of dark and lives on the sheet
            // with its swatch and its one line, where a reader choosing it can see what it is.
            selected = if (theme == ThemeMode.MIDNIGHT) ThemeMode.DARK else theme,
            // And pressing «تیره» while already on Midnight keeps Midnight. A reader who has chosen
            // true black and then taps the segment that is already lit has asked for nothing; taking
            // their choice away would be this row quietly undoing the sheet.
            onSelect = { mode ->
                val keepMidnight = mode == ThemeMode.DARK && theme == ThemeMode.MIDNIGHT
                if (!keepMidnight) onSelectTheme(mode)
            },
        )
        CoineProSegmentedControl(
            // Each language named in itself — «فارسی», "English" — for the same reason the sheet
            // does it: somebody looking for their own language finds it by recognising the word.
            options = AppLanguage.entries.map { it to it.displayName },
            selected = language,
            onSelect = onSelectLanguage,
        )
        // The one thing a reader has to be told before they press it, and the reason it is one
        // line here rather than the sheet's paragraph: the activity restarts, because the locale
        // is applied in `attachBaseContext` and nothing already composed would pick it up.
        CoineProNote(
            R.string.appearance_language_note,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth().padding(top = CoineProSpacing.Half),
        )
    }
}

/**
 * The theme's name with nothing after it.
 *
 * [ThemeMode] carries a full label and a sentence for the sheet's cards — «تیره · همیشه شب» — and
 * a segment three across has room for the first word only.
 */
@androidx.annotation.StringRes
private fun ThemeMode.shortLabelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.appearance_theme_system_short
    ThemeMode.LIGHT -> R.string.appearance_theme_light_short
    // Midnight never reaches this row as a segment of its own — see [QUICK_THEMES] — but a `when`
    // that threw for it would be a landmine under the next reader of this file.
    ThemeMode.DARK, ThemeMode.MIDNIGHT -> R.string.appearance_theme_dark_short
}

/**
 * The three the quick row offers, in the order they read.
 *
 * Not `ThemeMode.entries`, and that is the whole point: [ThemeMode.MIDNIGHT] is a fourth stored value
 * and not a fourth question. `AppearanceSheetTest` holds both halves of that — every mode reachable
 * somewhere, and this row at three.
 */
internal val QUICK_THEMES = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)
