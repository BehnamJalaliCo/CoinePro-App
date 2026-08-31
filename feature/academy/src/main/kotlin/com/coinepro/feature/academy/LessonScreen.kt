package com.coinepro.feature.academy

import com.coinepro.core.common.BidiText
import com.coinepro.core.common.toPersianDigits
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.academy.AcademyController
import com.coinepro.core.academy.LockReason
import com.coinepro.core.academy.Quiz
import com.coinepro.core.academy.QuizQuestion
import com.coinepro.core.academy.QuizResult
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProPageHeading
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProThinkingDots

/**
 * One lesson: the prose, then its quiz.
 *
 * Prose and quiz in one scroll rather than two screens. The quiz is not a test the reader has to
 * go and find; it is the end of the lesson, and putting it behind a navigation step is what makes
 * a course feel like homework.
 */
@Composable
fun LessonScreen(
    controller: AcademyController,
    slug: String,
    onClose: () -> Unit,
    onOpenProfile: (() -> Unit)? = null,
) {
    LaunchedEffect(controller, slug) { controller.openLesson(slug) }
    DisposableEffect(slug) { onDispose(controller::closeLesson) }
    val state by controller.lesson.collectAsStateWithLifecycle()

    when {
        state.loading -> Centre { CoineProThinkingDots() }
        state.locked != null -> Centre { Locked(state.locked!!, onOpenProfile, onClose) }
        state.error != null -> Centre { Failure(state.error!!) { controller.openLesson(slug) } }
        state.lesson == null -> Centre { Text("—", color = CoineProColors.TextMuted) }
        else -> {
            val lesson = state.lesson!!
            // Parsed once per lesson rather than on every recomposition: the HTML walk allocates a
            // Spanned and an AnnotatedString, and a quiz answer would otherwise redo both.
            val body = remember(lesson.content) { htmlToAnnotated(lesson.content) }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CoineProColors.Stage)
                    .verticalScroll(rememberScrollState())
                    .padding(CoineProSpacing.Gutter),
                verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Stack),
            ) {
                // The gold voice: a lesson is one thing, so it gets a heading with the gold rule
                // under it rather than a title floating over the body text.
                CoineProPageHeading(
                    title = lesson.title,
                    eyebrow = stringResource(R.string.academy_eyebrow),
                    subtitle = stringResource(
                        R.string.academy_reading_time,
                        (lesson.readingTimeMinutes).toPersianDigits(),
                    ),
                    modifier = Modifier.padding(horizontal = 0.dp),
                )

                lesson.summary?.let {
                    CoineProCard(modifier = Modifier.fillMaxWidth()) {
                        Text(it, color = CoineProColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                if (lesson.videoPath != null) {
                    // Not played here, and said plainly rather than shown as a dead play button.
                    // The route hands back a path with a three-hour student-bound token, and a
                    // player that cannot present that token would fail in a way that looks like a
                    // broken video rather than a missing feature.
                    Text(
                        text = stringResource(R.string.academy_video_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = CoineProColors.TextMuted,
                    )
                }

                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = CoineProColors.TextPrimary,
                )

                when {
                    state.quiz != null -> QuizSection(
                        quiz = state.quiz!!,
                        answers = state.answers,
                        result = state.result,
                        canSubmit = state.canSubmit,
                        submitting = state.submitting,
                        onAnswer = controller::answer,
                        onSubmit = controller::submitQuiz,
                    )
                    state.completed -> Text(
                        text = "✓ " + stringResource(R.string.academy_done),
                        color = CoineProColors.Buy,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    else -> CoineProPrimaryButton(
                        text = stringResource(R.string.academy_mark_read),
                        onClick = controller::markRead,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(CoineProSpacing.Three))
            }
        }
    }
}

@Composable
private fun QuizSection(
    quiz: Quiz,
    answers: Map<Long, Int>,
    result: QuizResult?,
    canSubmit: Boolean,
    submitting: Boolean,
    onAnswer: (Long, Int) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.academy_quiz),
                style = MaterialTheme.typography.titleMedium,
                color = CoineProColors.TextPrimary,
            )
            quiz.lastScore?.let {
                Text(
                    text = stringResource(R.string.academy_quiz_last_score, (it).toPersianDigits()),
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextMuted,
                    fontWeight = FontWeight.Normal,
                )
            }
        }

        quiz.questions.forEachIndexed { index, question ->
            QuestionCard(
                index = index,
                question = question,
                chosen = answers[question.id],
                // Null until marked, which is what keeps every option neutral while the reader is
                // still deciding. Colouring an option before the quiz is submitted would answer it.
                answer = result?.answers?.firstOrNull { it.id == question.id },
                onAnswer = { onAnswer(question.id, it) },
            )
        }

        if (result == null) {
            CoineProPrimaryButton(
                text = stringResource(R.string.academy_quiz_submit),
                onClick = onSubmit,
                enabled = canSubmit && !submitting,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            CoineProCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.academy_quiz_score, (result.score).toPersianDigits()),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (result.passed) CoineProColors.Buy else CoineProColors.Sell,
                )
                Text(
                    text = stringResource(
                        if (result.passed) R.string.academy_quiz_passed else R.string.academy_quiz_failed,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = CoineProColors.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun QuestionCard(
    index: Int,
    question: QuizQuestion,
    chosen: Int?,
    answer: com.coinepro.core.academy.QuizAnswer?,
    onAnswer: (Int) -> Unit,
) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            // The question is the server's prose, so a ratio in it — «با اهرم ۱:۱۰۰» — needs its
            // own direction or the paragraph reverses it into the opposite leverage.
            text = (index + 1).toPersianDigits() + ". " + BidiText.isolateNumericRuns(question.question),
            style = MaterialTheme.typography.bodyMedium,
            color = CoineProColors.TextPrimary,
        )
        Spacer(Modifier.height(CoineProSpacing.One))
        question.options.forEachIndexed { optionIndex, option ->
            val marked = answer != null
            val isCorrect = marked && optionIndex == answer.correctIndex
            val isWrongPick = marked && optionIndex == chosen && optionIndex != answer.correctIndex
            val border = when {
                isCorrect -> CoineProColors.Buy
                isWrongPick -> CoineProColors.Sell
                !marked && optionIndex == chosen -> CoineProColors.Gold
                else -> CoineProColors.Border
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = CoineProSpacing.Half)
                    .border(1.dp, border, RoundedCornerShape(12.dp))
                    .background(
                        color = when {
                            isCorrect -> CoineProColors.Buy.copy(alpha = TINT_ALPHA)
                            isWrongPick -> CoineProColors.Sell.copy(alpha = TINT_ALPHA)
                            else -> Color.Transparent
                        },
                        shape = RoundedCornerShape(12.dp),
                    )
                    .then(if (marked) Modifier else Modifier.clickable { onAnswer(optionIndex) })
                    .padding(CoineProSpacing.OneHalf),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    // An answer is «۲۰٬۰۰۰ دلار» — the same rule as the question above it.
                    text = BidiText.isolateNumericRuns(option),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoineProColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        // The explanation is the point of taking the quiz, so it is shown whether the answer was
        // right or wrong — a reader who guessed correctly learned nothing without it.
        answer?.explanation?.let {
            Spacer(Modifier.height(CoineProSpacing.One))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun Locked(reason: LockReason, onOpenProfile: (() -> Unit)?, onClose: () -> Unit) {
    Text(
        text = stringResource(
            when (reason) {
                LockReason.PHONE -> R.string.academy_locked_phone_body
                LockReason.TIER -> R.string.academy_locked_tier_body
            },
        ),
        color = CoineProColors.TextSecondary,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(CoineProSpacing.OneHalf))
    if (reason == LockReason.PHONE && onOpenProfile != null) {
        CoineProPrimaryButton(
            text = stringResource(R.string.academy_add_phone),
            onClick = onOpenProfile,
        )
    } else {
        CoineProSecondaryButton(text = stringResource(R.string.academy_close), onClick = onClose)
    }
}

@Composable
private fun Centre(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CoineProColors.Stage)
            .padding(CoineProSpacing.Gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content,
    )
}

/** The soft fill behind a marked option — the design system's 14% tint. */
private const val TINT_ALPHA = 0.14f
