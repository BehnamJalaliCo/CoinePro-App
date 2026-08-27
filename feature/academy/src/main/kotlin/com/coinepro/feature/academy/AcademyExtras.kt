package com.coinepro.feature.academy

import com.coinepro.core.common.toPersianDigits
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coinepro.core.academy.Achievements
import com.coinepro.core.academy.GlossaryTerm
import com.coinepro.core.academy.Leaderboard
import com.coinepro.core.common.BidiText
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProSheetSearch
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProThinkingDots

/**
 * The badges, earned and unearned together.
 *
 * Unearned ones are shown dimmed rather than hidden, which is the whole point of a badge list: a
 * grid of eleven with three filled in says what there is to aim at. Hiding the rest would turn it
 * into a list of three things that already happened.
 */
@Composable
internal fun AchievementsBody(achievements: Achievements) {
    if (achievements.items.isEmpty()) {
        EmptyNote()
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.One),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        item {
            Text(
                text = stringResource(
                    R.string.academy_achievements_count,
                    (achievements.earnedCount).toPersianDigits(),
                    (achievements.total).toPersianDigits(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
            )
        }
        items(achievements.items, key = { it.badge }) { badge ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (badge.earned) CoineProColors.Gold.copy(alpha = 0.16f)
                            else CoineProColors.SurfaceElevated,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = badge.icon.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = badge.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (badge.earned) CoineProColors.TextPrimary else CoineProColors.TextMuted,
                    )
                    badge.description?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = CoineProColors.TextMuted,
                            fontWeight = FontWeight.Normal,
                        )
                    }
                }
                if (badge.earned) {
                    Text("✓", color = CoineProColors.Gold, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

/**
 * The top fifty, with the reader's own row marked.
 *
 * Their rank is stated above the list even when it is outside the fifty, because "you are 312nd" is
 * the answer somebody scrolling a leaderboard wants and scrolling will never give it to them. A
 * null rank is not last place — the server ranks only students who have completed something — so it
 * says that instead of printing a number.
 */
@Composable
internal fun LeaderboardBody(leaderboard: Leaderboard) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.One),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
    ) {
        item {
            Text(
                text = leaderboard.myRank?.let {
                    stringResource(
                        R.string.academy_leaderboard_rank,
                        (it).toPersianDigits(),
                        (leaderboard.totalStudents).toPersianDigits(),
                    )
                } ?: stringResource(R.string.academy_leaderboard_unranked),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextSecondary,
                modifier = Modifier.padding(bottom = CoineProSpacing.One),
            )
        }
        if (leaderboard.items.isEmpty()) {
            item { EmptyNote() }
        }
        items(leaderboard.items, key = { it.rank }) { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // Transparent for everyone else, not the stage colour: the sheet already has a
                    // ground, and painting a second one under each row turns a list into stripes
                    // nobody asked for. Only the reader's own row is tinted, which is the one thing
                    // this list has to answer at a glance.
                    .background(
                        if (row.isMe) CoineProColors.Gold.copy(alpha = 0.10f) else Color.Transparent,
                    )
                    .padding(vertical = CoineProSpacing.One, horizontal = CoineProSpacing.Half),
                horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = (row.rank).toPersianDigits(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (row.isMe) CoineProColors.Gold else CoineProColors.TextMuted,
                    modifier = Modifier.width(28.dp),
                )
                Text(
                    text = BidiText.isolateLtr(row.username),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoineProColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = (row.xp).toPersianDigits(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (row.isMe) CoineProColors.Gold else CoineProColors.TextSecondary,
                )
            }
        }
    }
}

/**
 * The glossary, searchable in either script.
 *
 * The file writes «پیپ (Pip)» as one string and the gateway splits it, so a reader who knows the
 * word only in English finds the entry they would have written down in Persian — and the other way
 * round. Matching on the definition too, because "what is the word for the gap between bid and
 * ask" is the question a glossary is actually opened with.
 */
@Composable
internal fun GlossaryBody(terms: List<GlossaryTerm>) {
    var query by remember { mutableStateOf("") }
    val trimmed = query.trim()
    val shown = remember(terms, trimmed) {
        if (trimmed.isEmpty()) {
            terms
        } else {
            terms.filter { term ->
                term.term.contains(trimmed, ignoreCase = true) ||
                    term.english?.contains(trimmed, ignoreCase = true) == true ||
                    term.definition.contains(trimmed, ignoreCase = true)
            }
        }
    }
    Column {
        CoineProSheetSearch(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.academy_glossary_search),
        )
        if (shown.isEmpty()) {
            EmptyNote()
            return@Column
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.One),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
        ) {
            items(shown, key = { it.term }) { term ->
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
                        Text(
                            text = term.term,
                            style = MaterialTheme.typography.titleSmall,
                            color = CoineProColors.TextPrimary,
                        )
                        term.english?.let {
                            Text(
                                text = BidiText.isolateLtr(it),
                                style = MaterialTheme.typography.labelSmall,
                                color = CoineProColors.TextMuted,
                                fontWeight = FontWeight.Normal,
                            )
                        }
                    }
                    Text(
                        text = term.definition,
                        style = MaterialTheme.typography.bodySmall,
                        color = CoineProColors.TextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ExtraLoading() {
    Box(
        modifier = Modifier.fillMaxWidth().height(120.dp),
        contentAlignment = Alignment.Center,
    ) { CoineProThinkingDots() }
}

@Composable
internal fun ExtraFailed() {
    Box(
        modifier = Modifier.fillMaxWidth().height(120.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.academy_extra_failed),
            color = CoineProColors.TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun EmptyNote() {
    Box(
        modifier = Modifier.fillMaxWidth().height(120.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.academy_extra_empty),
            color = CoineProColors.TextMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
