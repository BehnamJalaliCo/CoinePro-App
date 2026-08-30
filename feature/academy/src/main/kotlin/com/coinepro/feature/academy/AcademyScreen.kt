package com.coinepro.feature.academy

import com.coinepro.core.common.toPersianDigits
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.coinepro.core.academy.AcademyExtra
import com.coinepro.core.academy.AcademyExtrasState
import com.coinepro.core.designsystem.CoineProChip
import com.coinepro.core.designsystem.CoineProChipRow
import com.coinepro.core.designsystem.CoineProSheet
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.academy.AcademyController
import com.coinepro.core.academy.AcademyError
import com.coinepro.core.academy.AcademyLevel
import com.coinepro.core.academy.AcademyProfile
import com.coinepro.core.academy.LessonSummary
import com.coinepro.core.academy.LockReason
import com.coinepro.core.common.BidiText
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProThinkingDots
import com.coinepro.core.designsystem.CoineProTeachingStrip
import com.coinepro.core.designsystem.TeachingSurface

/**
 * The curriculum, as a path rather than a list.
 *
 * Lessons alternate left and right down the column, each one a circle carrying its number, a tick
 * or a lock. Not decoration: the alternation is what makes "where am I" answerable at a glance,
 * and it is why a reader scrolling a hundred and forty lessons can see a level end and another
 * begin without reading a single heading. The idea is Duolingo's and the debt is worth
 * acknowledging — though not the connecting path, which is theirs and is not drawn here.
 *
 * Everything here is Persian prose *about* the curriculum, so the counts use Persian digits. The
 * one exception is nothing at all — there are no market figures on this screen.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AcademyScreen(
    controller: AcademyController,
    onOpenLesson: (String) -> Unit,
    /** Opens the profile, where a phone number can be added. Null hides the offer. */
    onOpenProfile: (() -> Unit)? = null,
) {
    LaunchedEffect(controller) { controller.start() }
    val state by controller.state.collectAsStateWithLifecycle()
    val extras by controller.extras.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf<AcademyExtra?>(null) }

    when {
        state.loading && state.catalog == null -> Centre { CoineProThinkingDots() }
        state.error != null && state.catalog == null -> Centre {
            Failure(state.error!!, controller::retry)
        }
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize().background(CoineProColors.Stage),
            contentPadding = PaddingValues(
                start = CoineProSpacing.Gutter,
                end = CoineProSpacing.Gutter,
                top = CoineProSpacing.OneHalf,
                bottom = CoineProSpacing.Six,
            ),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Stack),
        ) {
            item { CoineProTeachingStrip(TeachingSurface.ACADEMY, gutter = false) }
            state.profile?.let { profile ->
                item { ProfileHeader(profile) }
                item {
                    ExtraChips(profile) { extra ->
                        sheet = extra
                        controller.loadExtra(extra)
                    }
                }
                if (profile.phoneRequired && onOpenProfile != null) {
                    // One action that unlocks a whole level, so it sits at the top rather than
                    // being repeated as "locked" thirty times down the page.
                    item { PhoneNeeded(onOpenProfile) }
                }
            }
            state.nextLesson?.let { next ->
                item {
                    CoineProPrimaryButton(
                        text = stringResource(R.string.academy_continue) + " · " + next.title,
                        onClick = { onOpenLesson(next.slug) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            state.catalog?.levels?.forEach { level ->
                item(key = "head-${level.key}") { LevelHeader(level) }
                items(level.lessons, key = { level.key + "/" + it.slug }) { lesson ->
                    LessonNode(
                        lesson = lesson,
                        // Alternating on the lesson's own order rather than on the list index, so
                        // the zigzag does not flip when a level's first lesson is filtered out.
                        alignEnd = lesson.order % 2 == 0,
                        onClick = { onOpenLesson(lesson.slug) },
                    )
                }
            }
        }
    }

    sheet?.let { open ->
        CoineProSheet(
            title = stringResource(
                when (open) {
                    AcademyExtra.ACHIEVEMENTS -> R.string.academy_achievements
                    AcademyExtra.LEADERBOARD -> R.string.academy_leaderboard
                    AcademyExtra.GLOSSARY -> R.string.academy_glossary
                },
            ),
            onDismiss = { sheet = null },
        ) {
            AcademyExtraBody(open, extras)
        }
    }
}

/**
 * One sheet's content, chosen by which chip opened it.
 *
 * Public rather than internal so a screenshot can render it directly. A `ModalBottomSheet` draws in
 * its own window and the capture takes the activity's decor view, so a screenshot of the sheet
 * itself comes out blank — the body has to be reachable on its own.
 */
@Composable
fun AcademyExtraBody(extra: AcademyExtra, extras: AcademyExtrasState) {
    when {
        extras.failed == extra -> ExtraFailed()
        extra == AcademyExtra.ACHIEVEMENTS ->
            extras.achievements?.let { AchievementsBody(it) } ?: ExtraLoading()
        extra == AcademyExtra.LEADERBOARD ->
            extras.leaderboard?.let { LeaderboardBody(it) } ?: ExtraLoading()
        else ->
            extras.glossary.takeIf { it.isNotEmpty() }?.let { GlossaryBody(it) } ?: ExtraLoading()
    }
}

/**
 * The three lists that hang off the roadmap.
 *
 * A row rather than a menu, and above the levels rather than at the bottom of them: a badge count
 * and a rank are the things a reader checks on the way past, and one of them is the reason they
 * came back today. Buried under a hundred and forty lesson rows they would be checked never.
 */
@Composable
private fun ExtraChips(profile: AcademyProfile, onOpen: (AcademyExtra) -> Unit) {
    val options = listOf(
        CoineProChip(
            id = AcademyExtra.ACHIEVEMENTS.name,
            label = stringResource(R.string.academy_achievements),
            count = profile.achievementsCount,
        ),
        CoineProChip(AcademyExtra.LEADERBOARD.name, stringResource(R.string.academy_leaderboard)),
        CoineProChip(AcademyExtra.GLOSSARY.name, stringResource(R.string.academy_glossary)),
    )
    CoineProChipRow(
        options = options,
        // Never marked selected: these open a sheet and come straight back, so a chip left filled
        // in would claim a state the screen is not in.
        selectedId = null,
        onSelect = { id -> id?.let { onOpen(AcademyExtra.valueOf(it)) } },
    )
}

@Composable
private fun ProfileHeader(profile: AcademyProfile) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = stringResource(
                        R.string.academy_progress,
                        (profile.completed).toPersianDigits(),
                        (profile.totalLessons).toPersianDigits(),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = CoineProColors.TextPrimary,
                )
                Text(
                    text = (profile.xp).toPersianDigits() + " " + stringResource(R.string.academy_xp),
                    style = MaterialTheme.typography.bodySmall,
                    color = CoineProColors.TextMuted,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "🔥 " + (profile.streak.current).toPersianDigits(),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (profile.streak.todayDone) CoineProColors.Gold else CoineProColors.TextSecondary,
                )
                Text(
                    text = stringResource(R.string.academy_streak),
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextMuted,
                    fontWeight = FontWeight.Normal,
                )
            }
        }
        Spacer(Modifier.height(CoineProSpacing.One))
        LinearProgressIndicator(
            progress = { (profile.progressPercent / 100.0).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            color = CoineProColors.Gold,
            trackColor = CoineProColors.Border,
            // The default gap draws a notch at the end of the bar, which at six lessons out of a
            // hundred and forty reads as a second, tiny segment of progress.
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

@Composable
private fun PhoneNeeded(onOpenProfile: () -> Unit) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.academy_locked_phone),
            style = MaterialTheme.typography.titleSmall,
            color = CoineProColors.Warning,
        )
        Text(
            text = stringResource(R.string.academy_locked_phone_body),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextSecondary,
        )
        Spacer(Modifier.height(CoineProSpacing.One))
        CoineProPrimaryButton(
            text = stringResource(R.string.academy_add_phone),
            onClick = onOpenProfile,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun LevelHeader(level: AcademyLevel) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = CoineProSpacing.One)) {
        Text(
            text = level.name,
            style = MaterialTheme.typography.titleLarge,
            color = CoineProColors.TextPrimary,
        )
        Text(
            text = (level.completed).toPersianDigits() + " / " + (level.lessons.size).toPersianDigits(),
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
            fontWeight = FontWeight.Normal,
        )
    }
}

/**
 * One lesson on the path.
 *
 * Three states and they have to be told apart without reading: done is a filled gold disc, open is
 * an outlined one, locked is dimmed and does not respond to a tap. A locked node that opens a
 * screen saying "locked" is a tap that taught the reader nothing.
 */
@Composable
private fun LessonNode(lesson: LessonSummary, alignEnd: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(NODE_WIDTH_FRACTION)
                .then(if (lesson.locked) Modifier else Modifier.clickable(onClick = onClick)),
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Disc(lesson)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = lesson.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (lesson.locked) CoineProColors.TextMuted else CoineProColors.TextPrimary,
                )
                val note = when {
                    lesson.lockReason == LockReason.PHONE -> stringResource(R.string.academy_locked_phone)
                    lesson.lockReason == LockReason.TIER -> stringResource(R.string.academy_locked_tier)
                    lesson.hasVideo -> stringResource(R.string.academy_has_video)
                    else -> null
                }
                note?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (lesson.locked) CoineProColors.Warning else CoineProColors.TextMuted,
                        fontWeight = FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun Disc(lesson: LessonSummary) {
    val done = lesson.completed
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (done) CoineProColors.Gold else CoineProColors.SurfaceElevated)
            .border(
                width = 1.dp,
                color = when {
                    done -> CoineProColors.Gold
                    lesson.locked -> CoineProColors.Border
                    else -> CoineProColors.Gold
                },
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = when {
                done -> "✓"
                lesson.locked -> "🔒"
                else -> (lesson.order).toPersianDigits()
            },
            style = MaterialTheme.typography.labelMedium,
            color = when {
                done -> CoineProColors.OnAccent
                lesson.locked -> CoineProColors.TextMuted
                else -> CoineProColors.TextPrimary
            },
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun Failure(error: AcademyError, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
    ) {
        Text(
            text = stringResource(
                when (error) {
                    AcademyError.NETWORK -> R.string.academy_error_network
                    AcademyError.DISABLED -> R.string.academy_error_disabled
                },
            ),
            color = CoineProColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
        // Nothing to retry when the server has the door shut. A button that fails identically
        // every time is a button that says the app is broken.
        if (error == AcademyError.NETWORK) {
            CoineProPrimaryButton(text = stringResource(R.string.academy_retry), onClick = onRetry)
        }
    }
}

@Composable
private fun Centre(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(CoineProColors.Stage).padding(CoineProSpacing.Gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content,
    )
}

/**
 * How much of the width a node takes.
 *
 * Under three quarters, so the left and right columns visibly do not overlap and the zigzag is a
 * zigzag rather than two slightly offset lists.
 */
private const val NODE_WIDTH_FRACTION = 0.72f
