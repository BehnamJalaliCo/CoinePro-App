package com.coinepro.core.academy

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AcademyControllerTest {

    private fun summary(
        slug: String,
        completed: Boolean = false,
        locked: Boolean = false,
    ) = LessonSummary(
        slug = slug,
        title = slug,
        order = 1,
        tier = "free",
        locked = locked,
        lockReason = if (locked) LockReason.TIER else null,
        completed = completed,
        hasVideo = false,
    )

    private val profile = AcademyProfile(
        username = "reza",
        fullName = null,
        tier = "vip",
        phoneRequired = false,
        completed = 1,
        totalLessons = 4,
        progressPercent = 25.0,
        xp = 30,
        badges = emptyList(),
        byLevel = emptyList(),
        quizzesTaken = 1,
        averageQuiz = 80,
        streak = Streak(2, 5),
        achievementsCount = 1,
    )

    private open class FakeGateway : AcademyGateway {
        var profileCalls = 0
        var catalogCalls = 0
        var completions = mutableListOf<String>()

        var catalogValue = AcademyCatalog("vip", emptyList())
        var profileValue: AcademyProfile? = null
        var lessonValue: Lesson? = null
        var quizValue: Quiz? = null
        var resultValue: QuizResult? = null
        var lessonFailure: Throwable? = null
        var profileFailure: Throwable? = null

        override suspend fun profile(): AcademyProfile {
            profileCalls++
            profileFailure?.let { throw it }
            return profileValue ?: error("no profile")
        }

        override suspend fun catalog(): AcademyCatalog {
            catalogCalls++
            profileFailure?.let { throw it }
            return catalogValue
        }

        override suspend fun roadmap(level: String) = LevelRoadmap(level, null, emptyList())

        override suspend fun lesson(slug: String): Lesson {
            lessonFailure?.let { throw it }
            return lessonValue ?: error("no lesson")
        }

        override suspend fun complete(slug: String, quizScore: Int?): ProgressResult {
            completions += slug
            return ProgressResult(listOf("first_lesson"), Streak(3, 5))
        }

        override suspend fun quiz(slug: String): Quiz = quizValue ?: throw IllegalStateException("404")

        override suspend fun submitQuiz(slug: String, answers: Map<Long, Int>): QuizResult =
            resultValue ?: error("no result")

        var achievementCalls = 0
        var leaderboardCalls = 0
        var glossaryCalls = 0
        var extrasFail: Throwable? = null

        override suspend fun streak() = Streak(0, 0)

        override suspend fun achievements(): Achievements {
            achievementCalls++
            extrasFail?.let { throw it }
            return Achievements(
                items = listOf(Achievement("first_lesson", "اولین قدم", null, "🎯", true, null)),
                earnedCount = 1,
                total = 11,
            )
        }

        override suspend fun leaderboard(): Leaderboard {
            leaderboardCalls++
            extrasFail?.let { throw it }
            return Leaderboard(
                items = listOf(LeaderboardRow(1, "reza", 420, 12, isMe = true)),
                myRank = 1,
                totalStudents = 312,
            )
        }

        override suspend fun glossary(): List<GlossaryTerm> {
            glossaryCalls++
            extrasFail?.let { throw it }
            return listOf(GlossaryTerm("پیپ", "کوچک‌ترین واحد تغییر قیمت.", "Pip"))
        }
    }

    private fun controller(gateway: AcademyGateway, scheduler: kotlinx.coroutines.test.TestCoroutineScheduler) =
        AcademyController(gateway, TestScope(UnconfinedTestDispatcher(scheduler)))

    private val lesson = Lesson(
        slug = "lots",
        level = "beginner",
        title = "لات",
        summary = null,
        content = "<p>متن</p>",
        diagramImage = null,
        tier = "free",
        videoPath = null,
        videoDurationSeconds = null,
        readingTimeMinutes = 3,
        watermark = "reza",
    )

    @Test
    fun `the profile and the catalogue are fetched together, not one after the other`() = runTest {
        val gateway = FakeGateway().apply { profileValue = profile }
        val controller = controller(gateway, testScheduler)
        controller.start()

        assertEquals(1, gateway.profileCalls)
        assertEquals(1, gateway.catalogCalls)
        assertEquals(profile, controller.state.value.profile)
    }

    @Test
    fun `the next lesson is the first unfinished, unlocked one`() = runTest {
        val gateway = FakeGateway().apply {
            profileValue = profile
            catalogValue = AcademyCatalog(
                tier = "vip",
                levels = listOf(
                    AcademyLevel(
                        "beginner", "مقدماتی",
                        listOf(summary("a", completed = true), summary("b", completed = true)),
                    ),
                    AcademyLevel(
                        "intermediate", "متوسط",
                        listOf(summary("c", locked = true), summary("d"), summary("e")),
                    ),
                ),
            )
        }
        val controller = controller(gateway, testScheduler)
        controller.start()

        // The first level is finished, so the current one is the second — and inside it the first
        // unfinished lesson is locked, so the next openable one is the one after.
        assertEquals("intermediate", controller.state.value.currentLevel?.key)
        assertEquals("d", controller.state.value.nextLesson?.slug)
    }

    @Test
    fun `a finished curriculum still offers a level to open`() = runTest {
        // A continue button that does nothing is worse than no button, so the last level stands in
        // once everything is done.
        val gateway = FakeGateway().apply {
            profileValue = profile
            catalogValue = AcademyCatalog(
                tier = "vip",
                levels = listOf(AcademyLevel("beginner", "مقدماتی", listOf(summary("a", completed = true)))),
            )
        }
        val controller = controller(gateway, testScheduler)
        controller.start()

        assertEquals("beginner", controller.state.value.currentLevel?.key)
        assertNull("nothing left to open", controller.state.value.nextLesson)
    }

    @Test
    fun `the disabled flag is not a sign-out`() = runTest {
        // MOBILE_ACADEMY_TOKEN_ENABLED off answers 403, which looks exactly like an expired
        // session unless the body is read.
        val gateway = FakeGateway().apply {
            profileFailure = IllegalStateException("""{"code":"academy_disabled"}""")
        }
        val controller = controller(gateway, testScheduler)
        controller.start()

        assertEquals(AcademyError.DISABLED, controller.state.value.error)
    }

    @Test
    fun `anything else is a network failure`() = runTest {
        val gateway = FakeGateway().apply { profileFailure = IllegalStateException("502") }
        val controller = controller(gateway, testScheduler)
        controller.start()
        assertEquals(AcademyError.NETWORK, controller.state.value.error)
    }

    @Test
    fun `start does not hammer a failing server`() = runTest {
        val gateway = FakeGateway().apply { profileFailure = IllegalStateException("502") }
        val controller = controller(gateway, testScheduler)
        controller.start()
        controller.start()
        controller.start()
        assertEquals(1, gateway.profileCalls)
    }

    @Test
    fun `opening a lesson fetches its quiz alongside`() = runTest {
        // Not on a "start quiz" tap: a button that then spins for a second reads as a button that
        // did not work.
        val gateway = FakeGateway().apply {
            profileValue = profile
            lessonValue = lesson
            quizValue = Quiz("lots", listOf(QuizQuestion(1, "س", listOf("الف", "ب"))), null)
        }
        val controller = controller(gateway, testScheduler)
        controller.openLesson("lots")

        assertEquals(lesson, controller.lesson.value.lesson)
        assertEquals(1, controller.lesson.value.quiz?.questions?.size)
    }

    @Test
    fun `a lesson with no quiz is a normal lesson, not an error`() = runTest {
        val gateway = FakeGateway().apply { lessonValue = lesson }
        val controller = controller(gateway, testScheduler)
        controller.openLesson("lots")

        assertNull(controller.lesson.value.quiz)
        assertNull(controller.lesson.value.error)
    }

    @Test
    fun `a locked lesson reports which lock`() = runTest {
        val gateway = FakeGateway().apply { lessonFailure = LessonLockedException(LockReason.PHONE) }
        val controller = controller(gateway, testScheduler)
        controller.openLesson("rsi")

        assertEquals(LockReason.PHONE, controller.lesson.value.locked)
        assertNull("a lock is not a failure", controller.lesson.value.error)
    }

    @Test
    fun `submit stays off until every question is answered`() = runTest {
        val gateway = FakeGateway().apply {
            lessonValue = lesson
            quizValue = Quiz(
                "lots",
                listOf(
                    QuizQuestion(1, "الف", listOf("۱", "۲")),
                    QuizQuestion(2, "ب", listOf("۱", "۲")),
                ),
                null,
            )
        }
        val controller = controller(gateway, testScheduler)
        controller.openLesson("lots")
        assertFalse(controller.lesson.value.canSubmit)

        controller.answer(1, 0)
        assertFalse(controller.lesson.value.canSubmit)

        controller.answer(2, 1)
        assertTrue(controller.lesson.value.canSubmit)
    }

    @Test
    fun `passing a quiz refreshes the roadmap behind it`() = runTest {
        // The server marks the lesson complete on a pass, so the map the reader came from is now
        // stale. Refreshed here, so the node is already filled in when they go back.
        val gateway = FakeGateway().apply {
            profileValue = profile
            lessonValue = lesson
            quizValue = Quiz("lots", listOf(QuizQuestion(1, "الف", listOf("۱", "۲"))), null)
            resultValue = QuizResult(100, 1, 1, passed = true, answers = emptyList())
        }
        val controller = controller(gateway, testScheduler)
        controller.start()
        val before = gateway.catalogCalls

        controller.openLesson("lots")
        controller.answer(1, 0)
        controller.submitQuiz()

        assertTrue(controller.lesson.value.completed)
        assertEquals(before + 1, gateway.catalogCalls)
    }

    @Test
    fun `failing a quiz does not refresh and does not mark the lesson done`() = runTest {
        val gateway = FakeGateway().apply {
            profileValue = profile
            lessonValue = lesson
            quizValue = Quiz("lots", listOf(QuizQuestion(1, "الف", listOf("۱", "۲"))), null)
            resultValue = QuizResult(0, 0, 1, passed = false, answers = emptyList())
        }
        val controller = controller(gateway, testScheduler)
        controller.start()
        val before = gateway.catalogCalls

        controller.openLesson("lots")
        controller.answer(1, 1)
        controller.submitQuiz()

        assertFalse(controller.lesson.value.completed)
        assertEquals(before, gateway.catalogCalls)
    }

    @Test
    fun `answers are frozen once the quiz has been marked`() = runTest {
        val gateway = FakeGateway().apply {
            profileValue = profile
            lessonValue = lesson
            quizValue = Quiz("lots", listOf(QuizQuestion(1, "الف", listOf("۱", "۲"))), null)
            resultValue = QuizResult(0, 0, 1, passed = false, answers = emptyList())
        }
        val controller = controller(gateway, testScheduler)
        controller.start()
        controller.openLesson("lots")
        controller.answer(1, 1)
        controller.submitQuiz()

        controller.answer(1, 0)
        assertEquals(mapOf(1L to 1), controller.lesson.value.answers)
    }

    @Test
    fun `mark-as-read is offered only where there is no quiz to pass`() = runTest {
        // Two ways to finish one lesson would let a reader mark it done and then fail its quiz.
        val withQuiz = FakeGateway().apply {
            profileValue = profile
            lessonValue = lesson
            quizValue = Quiz("lots", listOf(QuizQuestion(1, "الف", listOf("۱", "۲"))), null)
        }
        val a = controller(withQuiz, testScheduler)
        a.openLesson("lots")
        a.markRead()
        assertTrue("a quiz lesson is not completed by reading", withQuiz.completions.isEmpty())

        val withoutQuiz = FakeGateway().apply {
            profileValue = profile
            lessonValue = lesson
        }
        val b = controller(withoutQuiz, testScheduler)
        b.start()
        b.openLesson("lots")
        b.markRead()
        assertEquals(listOf("lots"), withoutQuiz.completions)
        assertTrue(b.lesson.value.completed)
        assertEquals(listOf("first_lesson"), b.lesson.value.newAchievements)
    }

    @Test
    fun `closing a lesson clears it so the next one does not open on stale content`() = runTest {
        val gateway = FakeGateway().apply { lessonValue = lesson }
        val controller = controller(gateway, testScheduler)
        controller.openLesson("lots")
        controller.closeLesson()

        assertNull(controller.lesson.value.lesson)
        assertTrue(controller.lesson.value.answers.isEmpty())
    }

    @Test
    fun `each side list is fetched once and then held`() = runTest {
        // A badge is earned once and the glossary is a file on disk. Re-reading them every time a
        // sheet opens would spend a request on an answer that has not moved — and the leaderboard
        // is a group-by over every student's progress, the most expensive read in the academy.
        val gateway = FakeGateway()
        val controller = controller(gateway, testScheduler)

        repeat(3) { controller.loadExtra(AcademyExtra.ACHIEVEMENTS) }
        repeat(3) { controller.loadExtra(AcademyExtra.LEADERBOARD) }
        repeat(3) { controller.loadExtra(AcademyExtra.GLOSSARY) }

        assertEquals(1, gateway.achievementCalls)
        assertEquals(1, gateway.leaderboardCalls)
        assertEquals(1, gateway.glossaryCalls)

        val extras = controller.extras.value
        assertEquals(1, extras.achievements?.earnedCount)
        assertEquals(1, extras.leaderboard?.myRank)
        assertEquals("Pip", extras.glossary.single().english)
        assertNull(extras.loading)
        assertNull(extras.failed)
    }

    @Test
    fun `one side list failing does not mark the others failed`() = runTest {
        // They are three independent reads. A leaderboard the server choked on says nothing about
        // whether the glossary is readable, and one shared error flag would close all three sheets.
        val gateway = FakeGateway().apply { extrasFail = IllegalStateException("502") }
        val controller = controller(gateway, testScheduler)

        controller.loadExtra(AcademyExtra.LEADERBOARD)
        assertEquals(AcademyExtra.LEADERBOARD, controller.extras.value.failed)

        gateway.extrasFail = null
        controller.loadExtra(AcademyExtra.GLOSSARY)

        assertNull(controller.extras.value.failed)
        assertEquals(1, controller.extras.value.glossary.size)
    }

    @Test
    fun `a failed side list is tried again on the next open`() = runTest {
        val gateway = FakeGateway().apply { extrasFail = IllegalStateException("502") }
        val controller = controller(gateway, testScheduler)

        controller.loadExtra(AcademyExtra.ACHIEVEMENTS)
        assertEquals(1, gateway.achievementCalls)

        gateway.extrasFail = null
        controller.loadExtra(AcademyExtra.ACHIEVEMENTS)

        assertEquals(2, gateway.achievementCalls)
        assertEquals(1, controller.extras.value.achievements?.earnedCount)
    }
}
