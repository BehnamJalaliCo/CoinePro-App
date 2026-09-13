package com.coinepro.app

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.coinepro.core.datastore.ReaderMode
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTint
import com.coinepro.core.designsystem.R as DesignR
import com.coinepro.core.designsystem.pageAccentInk
import com.coinepro.core.designsystem.CoineProPress
import com.coinepro.core.designsystem.pressScale
import com.coinepro.core.designsystem.rememberCoineProHaptics

/**
 * **The one question this app asks before it shows anything** (run Ω3).
 *
 * ### Why there is exactly one, and why it is this one
 *
 * The first sixty seconds decide the install. The brief is explicit about them: no sign-up, no form,
 * one question, and then a chart with the Signal Layer on it and Rasad's three sentences. So this is
 * the only thing between the splash and the chart, it has three answers and a way out, and answering
 * it costs one tap.
 *
 * It asks about *how much to show*, not about experience, and the wording matters: «تازه‌کارم» is a
 * thing somebody is willing to say about themselves, where «سطح دانش شما» is a test. Nothing here is
 * a gate — see [ReaderMode] — so a wrong answer costs a reader one tap in the «…» hub, and that is
 * said on the screen in one line rather than discovered.
 *
 * ### Why skipping is a real option and not a dark pattern
 *
 * A reader who does not want to answer a question about themselves before seeing the product is
 * right, and the app has a defensible default for them: [ReaderMode.TRADER], the middle, which is
 * wrong in the smallest way for the most people. «بعداً» is drawn as plainly as the three cards and
 * is not a smaller target.
 *
 * ### What it is not
 *
 * Not a carousel, not three screens, not a progress dot. One screen, three cards, one skip. Every
 * onboarding flow that is longer than the thing it introduces is a flow people learn to dismiss.
 */
@Composable
fun FirstRunQuestion(
    onChoose: (ReaderMode) -> Unit,
    /** Takes the default without storing an answer, so the question can be asked again. */
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CoineProColors.Stage)
            .padding(horizontal = CoineProSpacing.Gutter)
            .semantics { contentDescription = SEMANTIC_TAG },
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.first_run_question),
            style = MaterialTheme.typography.headlineSmall,
            color = CoineProColors.TextPrimary,
            modifier = Modifier.padding(bottom = CoineProSpacing.Half),
        )
        // The one line under it, and it is here because it prevents a real mistake: without it a
        // reader believes they are choosing a *tier* and hesitates over a question that has no wrong
        // answer. See R7 — this is the case the rule allows for.
        Text(
            text = stringResource(R.string.first_run_reassurance),
            style = MaterialTheme.typography.bodyMedium,
            color = CoineProColors.TextMuted,
            modifier = Modifier.padding(bottom = CoineProSpacing.Two),
        )
        for (choice in FIRST_RUN_CHOICES) {
            ModeCard(choice = choice, onClick = { onChoose(choice.mode) })
        }
        Text(
            text = stringResource(R.string.first_run_skip),
            style = MaterialTheme.typography.bodyMedium,
            color = CoineProColors.pageAccentInk,
            modifier = Modifier
                .padding(top = CoineProSpacing.Two)
                .clip(CoineProShapes.small)
                .clickable(onClick = onSkip)
                .padding(vertical = CoineProSpacing.One, horizontal = CoineProSpacing.One)
                .semantics { contentDescription = SKIP_TAG },
        )
    }
}

/**
 * One answer: an icon, a name, and one line about what it puts on the screen.
 *
 * The same filled-and-bordered card `ThemeOption` uses, because this is the same kind of choice —
 * one of a short list, taken once, changeable later — and a fourth visual language for it would make
 * the first screen of the app the one that looks unlike the rest.
 */
@Composable
private fun ModeCard(choice: FirstRunChoice, onClick: () -> Unit) {
    val haptics = rememberCoineProHaptics()
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = CoineProSpacing.One)
            .clip(MaterialTheme.shapes.medium)
            .background(CoineProColors.Surface)
            .border(1.dp, CoineProColors.Border, MaterialTheme.shapes.medium)
            .pressScale(interaction, CoineProPress.ROW)
            .clickable(interaction, null) {
                // A commit, not a select: this is the answer to the only question the app asks and
                // the screen leaves as it lands.
                haptics.commit()
                onClick()
            }
            .padding(horizontal = CoineProSpacing.CardHorizontal, vertical = CoineProSpacing.Row)
            .semantics { contentDescription = choice.mode.id },
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Row),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(ICON_PLATE)
                .clip(CoineProShapes.small)
                .background(CoineProTint.fill(CoineProColors.Gold, CoineProColors.SurfaceElevated)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(choice.icon),
                contentDescription = null,
                tint = CoineProColors.pageAccentInk,
                modifier = Modifier.size(ICON_GLYPH),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(choice.title),
                style = MaterialTheme.typography.titleSmall,
                color = CoineProColors.TextPrimary,
            )
            Text(
                text = stringResource(choice.body),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
            )
        }
        Icon(
            painter = painterResource(CoineProIcons.ChevronForward),
            contentDescription = null,
            tint = CoineProColors.TextDisabled,
            modifier = Modifier.size(CHEVRON),
        )
    }
}

/** One row of the question: which mode, and how it is named and drawn. */
private class FirstRunChoice(
    val mode: ReaderMode,
    @StringRes val title: Int,
    @StringRes val body: Int,
    val icon: Int,
)

/**
 * The three, in the order they are offered.
 *
 * Simple first, and that is the decision the whole screen rests on: the reader this question exists
 * for is the beginner, and putting them last would put the app's own preference above the question's
 * purpose. A trader reads three cards in two seconds either way.
 */
private val FIRST_RUN_CHOICES = listOf(
    FirstRunChoice(
        mode = ReaderMode.SIMPLE,
        title = R.string.first_run_simple,
        body = R.string.first_run_simple_blurb,
        icon = DesignR.drawable.tv_chart_candles,
    ),
    FirstRunChoice(
        mode = ReaderMode.TRADER,
        title = R.string.first_run_trader,
        body = R.string.first_run_trader_blurb,
        icon = DesignR.drawable.tv_tool_longshort,
    ),
    FirstRunChoice(
        mode = ReaderMode.PRO,
        title = R.string.first_run_pro,
        body = R.string.first_run_pro_blurb,
        icon = DesignR.drawable.tv_code2,
    ),
)

/** The plate behind each icon, and the glyph inside it. */
private val ICON_PLATE = 40.dp
private val ICON_GLYPH = 20.dp
private val CHEVRON = 18.dp

/** What a screenshot test looks for. */
private const val SEMANTIC_TAG = "first-run-question"
private const val SKIP_TAG = "first-run-skip"
