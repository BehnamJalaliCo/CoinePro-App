package com.coinepro.core.academy

import com.coinepro.core.marketdata.AcademyTokenStore
import com.google.gson.annotations.SerializedName
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * The academy, on CoinePro-FX only.
 *
 * TradeYar has no equivalent and this returns nothing there rather than being wired to routes that
 * answer 404 — the same shape as `ExecutionPaths`, and for the same reason: a feature that is
 * absent should say so, not look like an outage.
 *
 * Every call carries the academy-scoped token explicitly. It is a different credential from the
 * one the network interceptor attaches, so `NetworkFactory` was taught to leave an explicit
 * Authorization header alone; without that the interceptor would overwrite it with the mobile
 * token and every route here would answer 401.
 */
interface AcademyGateway {
    suspend fun profile(): AcademyProfile
    suspend fun catalog(): AcademyCatalog
    suspend fun roadmap(level: String): LevelRoadmap
    suspend fun lesson(slug: String): Lesson
    suspend fun complete(slug: String, quizScore: Int? = null): ProgressResult
    suspend fun quiz(slug: String): Quiz
    suspend fun submitQuiz(slug: String, answers: Map<Long, Int>): QuizResult
    suspend fun streak(): Streak
    suspend fun achievements(): Achievements
    suspend fun leaderboard(): Leaderboard
    suspend fun glossary(): List<GlossaryTerm>
}

/**
 * Thrown when a lesson is locked.
 *
 * Two different situations behind one HTTP status, and the difference is the whole point: a tier
 * lock is "buy a subscription" and a phone lock is "fill in one field in your profile". Their team
 * flagged both as existing academy behaviour rather than new, so both are handled rather than
 * treated as an error.
 */
class LessonLockedException(val reason: LockReason) : Exception("lesson_locked_$reason")

internal interface AcademyApi {
    @GET("academy/me")
    suspend fun profile(@Header("Authorization") auth: String): ProfileDto

    @GET("academy/catalog")
    suspend fun catalog(@Header("Authorization") auth: String): CatalogDto

    @GET("academy/lessons/{level}/roadmap")
    suspend fun roadmap(@Header("Authorization") auth: String, @Path("level") level: String): RoadmapDto

    @GET("academy/lesson/{slug}")
    suspend fun lesson(@Header("Authorization") auth: String, @Path("slug") slug: String): LessonDto

    @POST("academy/progress/{slug}")
    suspend fun complete(
        @Header("Authorization") auth: String,
        @Path("slug") slug: String,
        @Body body: ProgressBody,
    ): ProgressDto

    @GET("academy/lesson/{slug}/quiz")
    suspend fun quiz(@Header("Authorization") auth: String, @Path("slug") slug: String): QuizDto

    @POST("academy/lesson/{slug}/quiz/submit")
    suspend fun submitQuiz(
        @Header("Authorization") auth: String,
        @Path("slug") slug: String,
        @Body body: QuizSubmitBody,
    ): QuizResultDto

    @GET("academy/streak")
    suspend fun streak(@Header("Authorization") auth: String): StreakDto

    @GET("academy/achievements")
    suspend fun achievements(@Header("Authorization") auth: String): AchievementsDto

    @GET("academy/leaderboard")
    suspend fun leaderboard(@Header("Authorization") auth: String): LeaderboardDto

    @GET("academy/glossary")
    suspend fun glossary(@Header("Authorization") auth: String): GlossaryDto
}

internal data class ProgressBody(val quizScore: Int?)

/**
 * Answers keyed by question id.
 *
 * The server reads `answers` as a plain map and accepts the key as either a string or a number —
 * `answers.get(str(q.id), answers.get(q.id))` — so string keys are sent, which is what JSON has.
 */
internal data class QuizSubmitBody(val answers: Map<String, Int>)

internal data class ProfileDto(
    val username: String? = null,
    val fullName: String? = null,
    val tier: String? = null,
    val phoneNumber: String? = null,
    val phoneRequired: Boolean = false,
    val completed: Int = 0,
    val totalLessons: Int = 0,
    val progressPct: Double = 0.0,
    val xp: Int = 0,
    val badges: List<String> = emptyList(),
    val byLevel: Map<String, LevelProgressDto> = emptyMap(),
    val quizzesTaken: Int = 0,
    val avgQuiz: Int? = null,
    val streak: StreakDto? = null,
    val achievementsCount: Int = 0,
)

internal data class LevelProgressDto(
    val name: String? = null,
    val done: Int = 0,
    val total: Int = 0,
    val mastered: Boolean = false,
)

internal data class CatalogDto(
    val tier: String? = null,
    val levels: List<LevelDto> = emptyList(),
)

internal data class LevelDto(
    val key: String? = null,
    val name: String? = null,
    val lessons: List<LessonSummaryDto> = emptyList(),
)

internal data class LessonSummaryDto(
    val slug: String? = null,
    val title: String? = null,
    val order: Int = 0,
    val tier: String? = null,
    val locked: Boolean = false,
    val lockReason: String? = null,
    val completed: Boolean = false,
    val hasVideo: Boolean = false,
)

internal data class RoadmapDto(
    val level: String? = null,
    val name: String? = null,
    val modules: List<ModuleDto> = emptyList(),
)

internal data class ModuleDto(
    val index: Int = 0,
    val title: String? = null,
    val lessons: List<LessonSummaryDto> = emptyList(),
    val done: Int = 0,
    val total: Int = 0,
)

internal data class LessonDto(
    val slug: String? = null,
    val level: String? = null,
    val title: String? = null,
    val summary: String? = null,
    val content: String? = null,
    val diagramImage: String? = null,
    val tier: String? = null,
    val videoUrl: String? = null,
    val videoDuration: Int? = null,
    val readingTimeMin: Int = 1,
    val watermark: String? = null,
)

internal data class ProgressDto(
    val ok: Boolean = false,
    val slug: String? = null,
    val status: String? = null,
    val newAchievements: List<String> = emptyList(),
    val streak: StreakDto? = null,
)

internal data class StreakDto(
    val current: Int = 0,
    val longest: Int = 0,
    val lastActive: String? = null,
    val todayDone: Boolean = false,
)

internal data class QuizDto(
    val slug: String? = null,
    val count: Int = 0,
    val lastScore: Int? = null,
    val questions: List<QuizQuestionDto> = emptyList(),
)

internal data class QuizQuestionDto(
    val id: Long? = null,
    val question: String? = null,
    val options: List<String> = emptyList(),
)

internal data class QuizResultDto(
    val score: Int = 0,
    val correct: Int = 0,
    val total: Int = 0,
    val passed: Boolean = false,
    val results: List<QuizAnswerDto> = emptyList(),
)

internal data class QuizAnswerDto(
    val id: Long? = null,
    val correctIndex: Int? = null,
    val yourIndex: Int? = null,
    val isCorrect: Boolean = false,
    val explanation: String? = null,
)

internal data class AchievementsDto(
    val items: List<AchievementDto> = emptyList(),
    val earnedCount: Int = 0,
    val total: Int = 0,
)

internal data class AchievementDto(
    val badge: String? = null,
    val title: String? = null,
    val desc: String? = null,
    val icon: String? = null,
    val earned: Boolean = false,
    val earnedAt: String? = null,
)

internal data class LeaderboardDto(
    val items: List<LeaderboardRowDto> = emptyList(),
    val myRank: Int? = null,
    val totalStudents: Int = 0,
)

internal data class LeaderboardRowDto(
    val rank: Int = 0,
    val username: String? = null,
    val xp: Int = 0,
    val completed: Int = 0,
    val isMe: Boolean = false,
)

internal data class GlossaryDto(val terms: List<GlossaryTermDto> = emptyList())

internal data class GlossaryTermDto(
    val term: String? = null,
    /**
     * The file's key is `def`, which Gson's snake-case policy would map from a Kotlin `def`.
     * Spelled out because `def` reads as an accident next to `definition` everywhere else.
     */
    @SerializedName("def") val definition: String? = null,
    @SerializedName("cat") val category: String? = null,
)

class NetworkAcademyGateway(
    retrofit: Retrofit,
    private val tokens: AcademyTokenStore,
) : AcademyGateway {

    private val api = retrofit.create(AcademyApi::class.java)

    private suspend fun auth(): String = "Bearer " + tokens.token()

    override suspend fun profile(): AcademyProfile = api.profile(auth()).toDomain()

    override suspend fun catalog(): AcademyCatalog = api.catalog(auth()).toDomain()

    override suspend fun roadmap(level: String): LevelRoadmap = api.roadmap(auth(), level).toDomain()

    /**
     * A lesson, or a lock.
     *
     * The route answers 403 with a Persian sentence rather than a code — `lock_reason` only
     * appears on the catalogue — so the two locks are told apart by what the sentence is about.
     * A defensive path either way: the catalogue already carries the reason, and a screen that
     * opens a lesson it was told is locked has a bug of its own.
     */
    override suspend fun lesson(slug: String): Lesson = try {
        api.lesson(auth(), slug).toDomain()
    } catch (failure: HttpException) {
        if (failure.code() != 403) throw failure
        throw LessonLockedException(lockReasonFromMessage(failure.response()?.errorBody()?.string()))
    }

    override suspend fun complete(slug: String, quizScore: Int?): ProgressResult =
        api.complete(auth(), slug, ProgressBody(quizScore)).toDomain()

    override suspend fun quiz(slug: String): Quiz = api.quiz(auth(), slug).toDomain()

    override suspend fun submitQuiz(slug: String, answers: Map<Long, Int>): QuizResult =
        api.submitQuiz(auth(), slug, QuizSubmitBody(answers.mapKeys { it.key.toString() })).toDomain()

    override suspend fun streak(): Streak = api.streak(auth()).toDomain()

    override suspend fun achievements(): Achievements = api.achievements(auth()).toDomain()

    override suspend fun leaderboard(): Leaderboard = api.leaderboard(auth()).toDomain()

    override suspend fun glossary(): List<GlossaryTerm> = api.glossary(auth()).terms.mapNotNull { it.toDomain() }
}

// ── mapping ──────────────────────────────────────────────────────────────────────────────────

/**
 * Which lock a 403 body is describing.
 *
 * Matched on the word for "phone" — «موبایل» — because that message is the only one of the two
 * that names an action the reader can take without paying. Anything else is a tier lock, which is
 * also the safer default: telling somebody to add a phone number when the real problem is their
 * subscription sends them to a field that will not help.
 */
internal fun lockReasonFromMessage(body: String?): LockReason = when {
    body == null -> LockReason.TIER
    body.contains("موبایل") || body.contains("phone", ignoreCase = true) -> LockReason.PHONE
    else -> LockReason.TIER
}

internal fun lockReasonOf(wire: String?): LockReason? = when (wire?.trim()?.lowercase()) {
    "tier" -> LockReason.TIER
    "phone" -> LockReason.PHONE
    else -> null
}

internal fun ProfileDto.toDomain() = AcademyProfile(
    username = username.orEmpty(),
    fullName = fullName?.trim()?.takeIf { it.isNotEmpty() },
    tier = tier?.trim()?.takeIf { it.isNotEmpty() } ?: "free",
    phoneRequired = phoneRequired,
    completed = completed,
    totalLessons = totalLessons,
    progressPercent = progressPct,
    xp = xp,
    badges = badges,
    // Ordered by the level's own progress key rather than by map iteration, because a JSON object
    // has no order and a progress list that reshuffles between refreshes is unreadable.
    byLevel = LEVEL_ORDER.mapNotNull { key ->
        byLevel[key]?.let {
            LevelProgress(key, it.name ?: key, it.done, it.total, it.mastered)
        }
    },
    quizzesTaken = quizzesTaken,
    averageQuiz = avgQuiz,
    streak = streak?.toDomain() ?: Streak(0, 0),
    achievementsCount = achievementsCount,
)

/**
 * The server's level order, which is a curriculum rather than an alphabet.
 *
 * Hard-coded because the `/me` response keys its levels by name in a JSON object and the order is
 * lost on the wire. `/catalog` sends an array and keeps it; this is the one route that does not.
 */
internal val LEVEL_ORDER = listOf("beginner", "intermediate", "advanced", "ai")

internal fun StreakDto.toDomain() = Streak(current, longest, lastActive, todayDone)

internal fun CatalogDto.toDomain() = AcademyCatalog(
    tier = tier ?: "free",
    levels = levels.mapNotNull { level ->
        val key = level.key?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        AcademyLevel(key, level.name ?: key, level.lessons.mapNotNull { it.toDomain() })
    },
)

internal fun LessonSummaryDto.toDomain(): LessonSummary? {
    val slug = slug?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return LessonSummary(
        slug = slug,
        title = title?.trim().orEmpty().ifEmpty { slug },
        order = order,
        tier = tier,
        locked = locked,
        lockReason = lockReasonOf(lockReason),
        completed = completed,
        hasVideo = hasVideo,
    )
}

internal fun RoadmapDto.toDomain() = LevelRoadmap(
    level = level.orEmpty(),
    name = name,
    modules = modules.map { module ->
        val lessons = module.lessons.mapNotNull { it.toDomain() }
        RoadmapModule(
            index = module.index,
            title = module.title.orEmpty(),
            lessons = lessons,
            done = module.done,
            // The server's own count, not the mapped list's: a lesson dropped here for a missing
            // slug should show as a gap in a module of eight, not silently shrink it to seven.
            total = module.total,
        )
    },
)

internal fun LessonDto.toDomain() = Lesson(
    slug = slug.orEmpty(),
    level = level.orEmpty(),
    title = title.orEmpty(),
    summary = summary?.trim()?.takeIf { it.isNotEmpty() },
    content = content.orEmpty(),
    diagramImage = diagramImage?.trim()?.takeIf { it.isNotEmpty() },
    tier = tier,
    videoPath = videoUrl?.trim()?.takeIf { it.isNotEmpty() },
    videoDurationSeconds = videoDuration?.takeIf { it > 0 },
    readingTimeMinutes = readingTimeMin.coerceAtLeast(1),
    watermark = watermark?.trim()?.takeIf { it.isNotEmpty() },
)

internal fun ProgressDto.toDomain() = ProgressResult(
    newAchievements = newAchievements,
    streak = streak?.toDomain() ?: Streak(0, 0),
)

internal fun QuizDto.toDomain() = Quiz(
    slug = slug.orEmpty(),
    questions = questions.mapNotNull { question ->
        val id = question.id ?: return@mapNotNull null
        val text = question.question?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        // A question with fewer than two options cannot be answered, and rendering one radio
        // button is worse than rendering nothing.
        if (question.options.size < 2) return@mapNotNull null
        QuizQuestion(id, text, question.options)
    },
    lastScore = lastScore,
)

internal fun QuizResultDto.toDomain() = QuizResult(
    score = score,
    correct = correct,
    total = total,
    passed = passed,
    answers = results.mapNotNull { answer ->
        val id = answer.id ?: return@mapNotNull null
        val correctIndex = answer.correctIndex ?: return@mapNotNull null
        QuizAnswer(
            id = id,
            correctIndex = correctIndex,
            yourIndex = answer.yourIndex,
            isCorrect = answer.isCorrect,
            explanation = answer.explanation?.trim()?.takeIf { it.isNotEmpty() },
        )
    },
)

internal fun AchievementsDto.toDomain() = Achievements(
    items = items.mapNotNull { item ->
        val badge = item.badge?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        Achievement(
            badge = badge,
            title = item.title?.trim().orEmpty().ifEmpty { badge },
            description = item.desc?.trim()?.takeIf { it.isNotEmpty() },
            icon = item.icon?.trim()?.takeIf { it.isNotEmpty() },
            earned = item.earned,
            earnedAt = item.earnedAt,
        )
    },
    earnedCount = earnedCount,
    total = total,
)

internal fun LeaderboardDto.toDomain() = Leaderboard(
    items = items.map {
        LeaderboardRow(
            rank = it.rank,
            // The server writes an em dash for a student whose row it could not name. Kept rather
            // than replaced with "unknown": it is already the app's own convention for absent.
            username = it.username?.trim().orEmpty().ifEmpty { "—" },
            xp = it.xp,
            completed = it.completed,
            isMe = it.isMe,
        )
    },
    myRank = myRank,
    totalStudents = totalStudents,
)

internal fun GlossaryTermDto.toDomain(): GlossaryTerm? {
    val name = term?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val meaning = definition?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    // The file writes "پیپ (Pip)" — the Persian term with its English in brackets. Split so the
    // two can be styled apart and so a search can match either.
    val match = Regex("^(.*?)\\s*\\(([^)]+)\\)\\s*$").matchEntire(name)
    return GlossaryTerm(
        term = match?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() } ?: name,
        definition = meaning,
        english = match?.groupValues?.get(2)?.trim(),
    )
}
