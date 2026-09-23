package com.coinepro.core.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.coinepro.core.common.SavedAge
import com.coinepro.core.common.SavedAgeUnit
import kotlinx.coroutines.delay

/**
 * «ذخیره‌شده · یک ساعت پیش» — how old the picture in front of the reader is (run Τ2, B6).
 *
 * ### Why this is a second bar and not a change to [CoineProOfflineBar]
 *
 * They answer different questions and a reader needs both answers.
 *
 * * **Offline** is about the *phone*. It clears when the reader walks to a window, and it says
 *   nothing about how old anything is — a phone that dropped its network ten seconds ago and one
 *   that has been in a basement since yesterday draw the same bar.
 * * **This** is about the *numbers*. It is the sentence a reader actually needs in order to decide
 *   whether to act on what they are looking at, and it is true on a perfect connection too: a
 *   screen restored from the cache while a request is still in flight is just as old.
 *
 * Folding them into one line would mean telling somebody to check their connection over a problem
 * no connection of theirs can fix, which is the mistake `CoineProPriceFeedBar` exists to avoid at
 * the other end.
 *
 * ### The ink is neutral, deliberately
 *
 * Not the offline bar's red and not the feed bar's amber. **Saved is not a fault.** Every screen in
 * this app is meant to be readable from the cache; drawing the fact in a warning colour would tell
 * a reader that the thing the app was designed to do has gone wrong. It is a statement of fact in
 * the muted ink, above the content, with no dismiss — for `CoineProOfflineBar`'s reason: the
 * condition lasts exactly as long as it lasts, and it leaves by itself when a fresh picture lands.
 */
@Composable
fun CoineProSavedBar(
    /**
     * How old, or null to draw nothing.
     *
     * Null covers both «this is live» and «there is no stored time», and the caller decides which
     * of those it is holding. See [SavedAge.of] for why an unknown age is not reported as a fresh
     * one.
     */
    age: SavedAge?,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = age != null,
        modifier = modifier,
        // Expanding rather than sliding over, so it never covers the first line of the screen.
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CoineProColors.Surface)
                .padding(horizontal = CoineProSpacing.Gutter, vertical = BAR_VERTICAL),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
        ) {
            Icon(
                painter = painterResource(CoineProIcons.Age),
                contentDescription = null,
                tint = CoineProColors.TextMuted,
                modifier = Modifier.size(GLYPH),
            )
            Text(
                // Held through the exit animation, so the sentence does not blank a frame before
                // the row finishes collapsing.
                text = stringResource(R.string.saved_ago, savedAgeSentence(age ?: HELD)),
                style = MaterialTheme.typography.labelMedium,
                color = CoineProColors.TextSecondary,
            )
        }
    }
}

/**
 * «۲ ساعت پیش» — the age alone, with the count in **Persian digits**.
 *
 * A count of hours is prose, not a market figure. The rule is in the working agreement, and it is
 * why this does not reach for the numeric style or a bidi isolate: «۲ ساعت پیش» is read as words.
 *
 * Public and without the «ذخیره‌شده ·» prefix, because two surfaces want the same figure under
 * different headings — the bar, which adds the prefix, and the diagnostics page, whose row is
 * already labelled. One arithmetic and one wording, in one place.
 */
@Composable
fun savedAgeSentence(age: SavedAge): String = when (age.unit) {
    // No number at all under a minute. «۰ دقیقه پیش» is not a thing anybody says, and a picture
    // that fresh is one the reader can simply use.
    SavedAgeUnit.MOMENTS -> stringResource(R.string.saved_moments)
    SavedAgeUnit.MINUTES ->
        pluralStringResource(R.plurals.saved_minutes, age.count, age.count.proseDigits())
    SavedAgeUnit.HOURS ->
        pluralStringResource(R.plurals.saved_hours, age.count, age.count.proseDigits())
    SavedAgeUnit.DAYS ->
        pluralStringResource(R.plurals.saved_days, age.count, age.count.proseDigits())
}

/**
 * The same sentence from a raw stored time, or **null where there is nothing to say**.
 *
 * For a caller holding a timestamp and a field to put a string in. Null rather than a placeholder,
 * so a surface with no stored time draws its own «—» rather than being handed a sentence about a
 * freshness nobody measured. See [SavedAge.of].
 */
@Composable
fun savedAgeSentenceOrNull(savedAtMillis: Long?, nowMillis: Long = System.currentTimeMillis()): String? =
    SavedAge.of(savedAtMillis, nowMillis)?.let { savedAgeSentence(it) }

/** What the bar says while it animates out, so the row never collapses around an empty line. */
private val HELD = SavedAge(SavedAgeUnit.MOMENTS, 0)

private val BAR_VERTICAL = 6.dp
private val GLYPH = 14.dp

/**
 * [SavedAge] that keeps up while the screen is open.
 *
 * A bar that read «۲ دقیقه پیش» when the page opened and still says it forty minutes later is a
 * lie of exactly the kind the bar exists to remove, and the reader has no way to tell — the
 * sentence looks the same whether it is current or frozen. So the age is recomputed on a slow tick
 * rather than once.
 *
 * [TICK_MILLIS] is half a minute: fast enough that the printed figure is never more than thirty
 * seconds behind, slow enough to be nothing — one recomposition of one line of text, and the
 * coroutine stops with the composition.
 *
 * Null [savedAtMillis] never starts the tick at all, so a screen showing live content pays nothing.
 */
@Composable
fun rememberSavedAge(savedAtMillis: Long?): SavedAge? {
    if (savedAtMillis == null) return null
    val age by produceState(
        initialValue = SavedAge.of(savedAtMillis, System.currentTimeMillis()),
        key1 = savedAtMillis,
    ) {
        while (true) {
            value = SavedAge.of(savedAtMillis, System.currentTimeMillis())
            delay(TICK_MILLIS)
        }
    }
    return age
}

/** How often the printed age is refreshed. See [rememberSavedAge]. */
private const val TICK_MILLIS = 30_000L
