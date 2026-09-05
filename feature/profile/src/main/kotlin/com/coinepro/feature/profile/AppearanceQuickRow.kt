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
            options = ThemeMode.entries.map { it to stringResource(it.shortLabelRes()) },
            selected = theme,
            onSelect = onSelectTheme,
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
        Text(
            text = stringResource(R.string.appearance_language_note),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextMuted,
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
    ThemeMode.DARK -> R.string.appearance_theme_dark_short
}
