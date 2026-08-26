package com.coinepro.core.academy

/**
 * Who the reader is inside the academy, and how far they have got.
 *
 * A second identity from the app's own account, and the app does not pretend otherwise: the
 * academy runs on `AcademyStudent` rows, and `POST /user/academy-token` binds one to the signed-in
 * user — creating it on first use, or attaching to an existing student when the verified email
 * matches, so somebody who studied on the web keeps their progress.
 */
data class AcademyProfile(
    val username: String,
    val fullName: String?,
    val tier: String,
    /**
     * The reader is on a paid tier but has not registered a phone number, so paid lessons stay
     * locked.
     *
     * Worth its own field rather than folding into the lock reason on each lesson: it is one
     * action that unlocks a whole level, and a screen that only says "locked" thirty times never
     * tells anybody what to do about it.
     */
    val phoneRequired: Boolean,
    val completed: Int,
    val totalLessons: Int,
    val progressPercent: Double,
    val xp: Int,
    val badges: List<String>,
    val byLevel: List<LevelProgress>,
    val quizzesTaken: Int,
    /** Mean quiz score, or null before the first quiz — not zero, which is a failing grade. */
    val averageQuiz: Int?,
    val streak: Streak,
    val achievementsCount: Int,
)

data class LevelProgress(
    val key: String,
    val name: String,
    val done: Int,
    val total: Int,
    val mastered: Boolean,
) {
    val fraction: Double get() = if (total <= 0) 0.0 else done.toDouble() / total
}

data class Streak(
    val current: Int,
    val longest: Int,
    /** ISO date of the last active day, or null when nothing has been studied yet. */
    val lastActive: String? = null,
    val todayDone: Boolean = false,
)

/** Why a lesson will not open. */
enum class LockReason {
    /** The lesson needs a higher subscription tier. */
    TIER,

    /** The tier is high enough but the account has no phone number registered. */
    PHONE,
}

data class LessonSummary(
    val slug: String,
    val title: String,
    val order: Int,
    val tier: String?,
    val locked: Boolean,
    val lockReason: LockReason?,
    val completed: Boolean,
    val hasVideo: Boolean,
)

data class AcademyLevel(
    val key: String,
    val name: String,
    val lessons: List<LessonSummary>,
) {
    val completed: Int get() = lessons.count { it.completed }
}

data class AcademyCatalog(
    val tier: String,
    val levels: List<AcademyLevel>,
)

/** One module of a level's roadmap — the server groups lessons in eights. */
data class RoadmapModule(
    val index: Int,
    val title: String,
    val lessons: List<LessonSummary>,
    val done: Int,
    val total: Int,
)

data class LevelRoadmap(
    val level: String,
    val name: String?,
    val modules: List<RoadmapModule>,
)

data class Lesson(
    val slug: String,
    val level: String,
    val title: String,
    val summary: String?,
    /** HTML from the server's own editor. Rendered rather than displayed raw — see `LessonHtml`. */
    val content: String,
    val diagramImage: String?,
    val tier: String?,
    /**
     * A path, not a URL, and it carries a three-hour token bound to this student.
     *
     * Their team deliberately never exposes the raw file. Whatever plays this has to send it to
     * the same host the rest of the academy is on, with the query string intact.
     */
    val videoPath: String?,
    val videoDurationSeconds: Int?,
    val readingTimeMinutes: Int,
    /**
     * The reader's own username, which the web player paints over the video.
     *
     * Kept because it is an anti-piracy measure the content owner chose, and dropping it on mobile
     * would make the mobile client the leak.
     */
    val watermark: String?,
)

data class QuizQuestion(
    val id: Long,
    val question: String,
    val options: List<String>,
)

data class Quiz(
    val slug: String,
    val questions: List<QuizQuestion>,
    /** The best score so far, or null if never attempted. */
    val lastScore: Int?,
)

data class QuizAnswer(
    val id: Long,
    val correctIndex: Int,
    val yourIndex: Int?,
    val isCorrect: Boolean,
    val explanation: String?,
)

data class QuizResult(
    val score: Int,
    val correct: Int,
    val total: Int,
    /** Sixty percent, decided by the server. Passing also marks the lesson complete. */
    val passed: Boolean,
    val answers: List<QuizAnswer>,
)

data class Achievement(
    val badge: String,
    val title: String,
    val description: String?,
    val icon: String?,
    val earned: Boolean,
    val earnedAt: String?,
)

data class Achievements(
    val items: List<Achievement>,
    val earnedCount: Int,
    val total: Int,
)

data class LeaderboardRow(
    val rank: Int,
    val username: String,
    val xp: Int,
    val completed: Int,
    val isMe: Boolean,
)

data class Leaderboard(
    val items: List<LeaderboardRow>,
    /**
     * The reader's own rank, which can be outside the top fifty the list holds.
     *
     * Null means they have completed nothing yet — the server ranks only students with completed
     * lessons — and that is a different message from "you are 312th".
     */
    val myRank: Int?,
    val totalStudents: Int,
)

/** One glossary entry, for the tap-to-define chips inside a lesson. */
data class GlossaryTerm(
    val term: String,
    val definition: String,
    val english: String? = null,
)

/** What `POST /progress/{slug}` gives back: the badges that tipped over, and the new streak. */
data class ProgressResult(
    val newAchievements: List<String>,
    val streak: Streak,
)
