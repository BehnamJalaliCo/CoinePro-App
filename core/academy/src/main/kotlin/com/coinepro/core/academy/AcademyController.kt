package com.coinepro.core.academy

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why the academy is not on screen. */
enum class AcademyError {
    NETWORK,

    /**
     * The server has `MOBILE_ACADEMY_TOKEN_ENABLED` switched off.
     *
     * A different message from being signed out, which is what a bare 403 looks like. Their team
     * added the flag precisely so the door can be closed without touching a route, so the app has
     * to be able to say "this is closed" rather than "you are logged out".
     */
    DISABLED,
}

data class AcademyUiState(
    val profile: AcademyProfile? = null,
    val catalog: AcademyCatalog? = null,
    val loading: Boolean = false,
    val error: AcademyError? = null,
) {
    /**
     * The level to open when the reader taps «ادامه».
     *
     * The first level that is not finished, or the last one when everything is. Never null while
     * there is a catalogue, because a continue button that does nothing is worse than no button.
     */
    val currentLevel: AcademyLevel?
        get() = catalog?.levels?.firstOrNull { level -> level.lessons.any { !it.completed } }
            ?: catalog?.levels?.lastOrNull()

    /** The next lesson to open — the first unfinished, unlocked one in the current level. */
    val nextLesson: LessonSummary?
        get() = currentLevel?.lessons?.firstOrNull { !it.completed && !it.locked }
}

/**
 * The three lists that hang off the roadmap rather than sitting on it.
 *
 * Loaded on demand, one at a time, because each is a round trip a reader who never opens the sheet
 * should not pay for — and the leaderboard in particular is a group-by over every student's
 * progress, which is the most expensive read in the academy.
 */
data class AcademyExtrasState(
    val achievements: Achievements? = null,
    val leaderboard: Leaderboard? = null,
    val glossary: List<GlossaryTerm> = emptyList(),
    val loading: AcademyExtra? = null,
    val failed: AcademyExtra? = null,
)

enum class AcademyExtra { ACHIEVEMENTS, LEADERBOARD, GLOSSARY }

data class LessonUiState(
    val lesson: Lesson? = null,
    val quiz: Quiz? = null,
    val result: QuizResult? = null,
    /** Question id to the option index the reader picked. */
    val answers: Map<Long, Int> = emptyMap(),
    val loading: Boolean = false,
    val submitting: Boolean = false,
    val completed: Boolean = false,
    val newAchievements: List<String> = emptyList(),
    val locked: LockReason? = null,
    val error: AcademyError? = null,
) {
    /** Every question answered. The submit button stays off until then. */
    val canSubmit: Boolean
        get() = quiz != null &&
            quiz.questions.isNotEmpty() &&
            quiz.questions.all { it.id in answers } &&
            !submitting &&
            result == null
}

/**
 * The academy's state, for the roadmap and the lesson alike.
 *
 * One controller rather than two because the two screens share the thing that actually changes:
 * finishing a lesson moves the roadmap. A separate lesson controller would leave the roadmap stale
 * behind it, and the reader would come back to a screen still saying they have not done the lesson
 * they just did.
 */
class AcademyController(
    private val gateway: AcademyGateway,
    private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow(AcademyUiState())
    val state: StateFlow<AcademyUiState> = _state.asStateFlow()

    private val _lesson = MutableStateFlow(LessonUiState())
    val lesson: StateFlow<LessonUiState> = _lesson.asStateFlow()

    private val _extras = MutableStateFlow(AcademyExtrasState())
    val extras: StateFlow<AcademyExtrasState> = _extras.asStateFlow()

    private var job: Job? = null
    private var lessonJob: Job? = null

    fun start() {
        if (_state.value.catalog == null && job == null && _state.value.error == null) refresh()
    }

    fun retry() = refresh()

    private fun refresh() {
        job?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        job = scope.launch {
            runCatching {
                // Together rather than in sequence: the header needs the profile and the body
                // needs the catalogue, and neither depends on the other. Two round trips one after
                // the other would double the wait for a screen that shows both at once.
                coroutineScope {
                    val profile = async { gateway.profile() }
                    val catalog = async { gateway.catalog() }
                    profile.await() to catalog.await()
                }
            }
                .onSuccess { (profile, catalog) ->
                    _state.value = AcademyUiState(profile = profile, catalog = catalog)
                }
                .onFailure { failure ->
                    _state.update { it.copy(loading = false, error = failure.toAcademyError()) }
                }
            job = null
        }
    }

    /**
     * Fetch one of the three side lists, once.
     *
     * Already held means nothing happens: these change slowly — a badge is earned once, the
     * glossary is a file on disk — and re-reading them every time a sheet opens would spend a
     * request on an answer that has not moved.
     */
    fun loadExtra(extra: AcademyExtra) {
        val current = _extras.value
        val alreadyHeld = when (extra) {
            AcademyExtra.ACHIEVEMENTS -> current.achievements != null
            AcademyExtra.LEADERBOARD -> current.leaderboard != null
            AcademyExtra.GLOSSARY -> current.glossary.isNotEmpty()
        }
        if (alreadyHeld || current.loading == extra) return
        _extras.update { it.copy(loading = extra, failed = null) }
        scope.launch {
            // Branched on the request rather than on the answer's type. Dispatching on the result
            // would mean `glossary()` and any future list-returning route being told apart by
            // erased generics, which is exactly the kind of thing that compiles and then puts the
            // wrong list in the wrong sheet.
            val outcome = runCatching {
                when (extra) {
                    AcademyExtra.ACHIEVEMENTS -> {
                        val value = gateway.achievements()
                        _extras.update { it.copy(achievements = value, loading = null) }
                    }
                    AcademyExtra.LEADERBOARD -> {
                        val value = gateway.leaderboard()
                        _extras.update { it.copy(leaderboard = value, loading = null) }
                    }
                    AcademyExtra.GLOSSARY -> {
                        val value = gateway.glossary()
                        _extras.update { it.copy(glossary = value, loading = null) }
                    }
                }
            }
            outcome.onFailure { _extras.update { it.copy(loading = null, failed = extra) } }
        }
    }

    /**
     * Open a lesson and its quiz.
     *
     * The quiz is fetched alongside rather than when the reader reaches the bottom, because a
     * "start quiz" button that then spins for a second reads as though the button did not work.
     * A lesson with no quiz answers 404, and that is a normal outcome rather than an error.
     */
    fun openLesson(slug: String) {
        lessonJob?.cancel()
        _lesson.value = LessonUiState(loading = true)
        lessonJob = scope.launch {
            runCatching { gateway.lesson(slug) }
                .onSuccess { lesson ->
                    val quiz = runCatching { gateway.quiz(slug) }.getOrNull()
                        ?.takeIf { it.questions.isNotEmpty() }
                    _lesson.value = LessonUiState(lesson = lesson, quiz = quiz)
                }
                .onFailure { failure ->
                    _lesson.value = when (failure) {
                        is LessonLockedException -> LessonUiState(locked = failure.reason)
                        else -> LessonUiState(error = failure.toAcademyError())
                    }
                }
            lessonJob = null
        }
    }

    fun closeLesson() {
        lessonJob?.cancel()
        lessonJob = null
        _lesson.value = LessonUiState()
    }

    /** Pick an option. Ignored once the quiz has been marked — the answers are then history. */
    fun answer(questionId: Long, optionIndex: Int) {
        if (_lesson.value.result != null) return
        _lesson.update { it.copy(answers = it.answers + (questionId to optionIndex)) }
    }

    fun submitQuiz() {
        val current = _lesson.value
        val slug = current.lesson?.slug ?: return
        if (!current.canSubmit) return
        _lesson.update { it.copy(submitting = true) }
        scope.launch {
            runCatching { gateway.submitQuiz(slug, current.answers) }
                .onSuccess { result ->
                    _lesson.update { it.copy(result = result, submitting = false, completed = result.passed) }
                    // Passing marks the lesson complete server-side, so the roadmap behind this
                    // screen is now out of date. Refreshed here rather than on the way back, so
                    // the node is already filled in when the reader returns to it.
                    if (result.passed) refresh()
                }
                .onFailure { failure ->
                    val mapped = failure.toAcademyError()
                    _lesson.update { it.copy(submitting = false, error = mapped) }
                }
        }
    }

    /**
     * Mark a lesson read, for the ones with no quiz.
     *
     * A lesson with a quiz is completed by passing it — the server does that itself on a score of
     * sixty or more — so this is not offered there. Two ways to complete one lesson would let a
     * reader mark it done and then fail its quiz.
     */
    fun markRead() {
        val current = _lesson.value
        val slug = current.lesson?.slug ?: return
        if (current.quiz != null || current.completed) return
        scope.launch {
            runCatching { gateway.complete(slug) }
                .onSuccess { progress ->
                    _lesson.update {
                        it.copy(completed = true, newAchievements = progress.newAchievements)
                    }
                    refresh()
                }
        }
    }
}

internal fun Throwable.toAcademyError(): AcademyError {
    val text = (message ?: "") + (cause?.message ?: "")
    return if (text.contains("academy_disabled")) AcademyError.DISABLED else AcademyError.NETWORK
}
