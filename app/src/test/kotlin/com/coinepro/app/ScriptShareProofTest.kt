package com.coinepro.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.coinepro.core.community.CommunityCategory
import com.coinepro.core.community.CommunityController
import com.coinepro.core.community.CommunityFeedPage
import com.coinepro.core.community.CommunityGateway
import com.coinepro.core.community.CommunityLeaderboard
import com.coinepro.core.community.CommunityLikeOutcome
import com.coinepro.core.community.CommunityMember
import com.coinepro.core.community.CommunityPost
import com.coinepro.core.community.CommunityReactionOutcome
import com.coinepro.core.community.CommunityThread
import com.coinepro.core.community.CommunityWriteOutcome
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.designsystem.LocalTeachingDismissals
import com.coinepro.core.script.ScriptDocument
import com.coinepro.core.script.ScriptPresets
import com.coinepro.core.script.ScriptShare
import com.coinepro.feature.community.CommunityThreadScreen
import com.coinepro.feature.script.ScriptMinePanelPreview
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import com.coinepro.core.common.BidiText
import androidx.compose.ui.semantics.SemanticsProperties
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **Sharing a script, and installing one** (run Σ, S3 item D).
 *
 * Two frames and two assertions, because the claim has two halves and each is useless alone: a
 * share button that composes a post nobody can open, or an open button on a post nothing produces.
 *
 * The post carries the code rather than a link. That is not a shortcut taken because the service
 * behind `pro-chart.com/s/<id>` does not exist yet — it is the board's own rule: every post goes
 * through a server-side block on links, and a share that put an address in the body would be
 * refused at the door, today and after the service is built. `ScriptShareTest` holds the format;
 * this holds the two screens.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScriptShareProofTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private val shared: ScriptDocument = ScriptDocument.of(
        source = ScriptPresets.byId("rsi-zones")!!.source,
        name = "آر‌اس‌آی من",
        at = 1_726_000_000_000L,
        description = "روی خط سی می‌خرد، روی هفتاد می‌فروشد",
    ).copy(tags = listOf("مومنتوم"))

    @Test
    @Config(sdk = [34], qualifiers = FA_PHONE)
    fun `the studio offers to share what the reader made`() {
        proof("sigma4-share-mine-phone-fa") {
            val controller = remember {
                ScreenshotFixtures.scriptController(scope).also {
                    it.setSeries(ScreenshotFixtures.chartSeries())
                    it.openPreset(ScriptPresets.byId("rsi-zones")!!)
                    it.rename("آر‌اس‌آی من")
                    it.describe("روی خط سی می‌خرد، روی هفتاد می‌فروشد")
                    it.setTags("مومنتوم")
                }
            }
            ScriptMinePanelPreview(controller, share = true)
        }
        composeRule.onNodeWithText("هم‌رسانی در انجمن").assertExists()
    }

    @Test
    @Config(sdk = [34], qualifiers = FA_PHONE)
    fun `a post carrying a script offers to open it`() {
        // The button names the script, so a reader knows what they are about to open before they
        // open it — the code is in the post above it either way.
        val controller = CommunityController(ScriptBoard(), FakeCommunityIdentity(), scope)
        proof("sigma4-share-thread-phone-fa") {
            CommunityThreadScreen(controller = controller, postId = 7L, onClose = {}, onOpenScript = {})
        }
        composeRule.onNodeWithText("«آر‌اس‌آی من» را در استودیو باز کنید").assertExists()
    }

    @Test
    @Config(sdk = [34], qualifiers = FA_PHONE)
    fun `the code in a post is the file, not a rearrangement of it`() {
        // Run Σ-FIX item 1. The first version drew the whole post as one right-to-left paragraph
        // and every line of the file came out with its leading token at the far end — «1 nama //».
        // The second wrapped each code run in a bidi isolate, which straightened the tokens and
        // left an invisible control character inside the code.
        //
        // The block now renders the file's own characters, so this asserts the absence of the
        // isolate rather than the presence of the text: `// nama 1` matches either way, and only
        // one of them is a code block.
        val controller = CommunityController(ScriptBoard(), FakeCommunityIdentity(), scope)
        composeRule.setContent {
            CoineProTheme(darkTheme = true) {
                CompositionLocalProvider(LocalTeachingDismissals provides AllTeachingDismissed) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        CommunityThreadScreen(controller = controller, postId = 7L, onClose = {}, onOpenScript = {})
                    }
                }
            }
        }
        composeRule.waitForIdle()
        val drawn = composeRule.onNodeWithText("// nama 1", substring = true)
            .fetchSemanticsNode()
            .config[SemanticsProperties.Text]
            .first()
            .text
        assertFalse("the code block carries a bidi isolate", drawn.contains(BidiText.LRI))
        assertFalse("the code block carries a bidi isolate", drawn.contains(BidiText.PDI))
        assertTrue("the block is the file rather than a line of it", drawn.contains("plot(rsi"))
    }

    @Test
    @Config(sdk = [34], qualifiers = FA_PHONE)
    fun `a post offers the chart first and the studio second`() {
        // Run Σ-FIX 6. «I want this indicator» is what a reader means when they open a post that
        // shares one, so the chart is the primary and the editor the secondary — the reverse of
        // what 4.85.0 shipped, which offered only the editor and left the install to the reader.
        val controller = CommunityController(ScriptBoard(), FakeCommunityIdentity(), scope)
        proof("sigma-fix-share-thread-install-phone-fa") {
            CommunityThreadScreen(
                controller = controller,
                postId = 7L,
                onClose = {},
                onOpenScript = {},
                onAddScriptToChart = {},
                installed = setOf(shared.id),
            )
        }
        composeRule.onNodeWithText("«آر‌اس‌آی من» را به چارت من اضافه کنید").assertExists()
        composeRule.onNodeWithText("«آر‌اس‌آی من» را در استودیو باز کنید").assertExists()
        // Said only of this device, and in those words. There is no «۱۲ نصب» anywhere, because
        // nothing knows that — see `ScriptInstallStore` and BLOCKED.md.
        composeRule.onNodeWithText("شما این را اضافه کرده‌اید").assertExists()
    }

    @Test
    fun `what the studio composes is what the thread reads`() {
        // The two halves, joined without a screen in between. A change to either side that broke
        // the other would pass both frames and fail here.
        val body = ScriptShare.post(shared)
        assertEquals(shared.source, ScriptShare.scriptIn(body)?.source)
        assertEquals(shared.name, ScriptShare.scriptIn(body)?.name)
        assertEquals(emptyList<String>(), ScriptShare.refusals(body))
    }

    private fun proof(name: String, content: @Composable () -> Unit) {
        composeRule.setContent {
            CoineProTheme(darkTheme = true) {
                CompositionLocalProvider(LocalTeachingDismissals provides AllTeachingDismissed) {
                    Surface(modifier = Modifier.fillMaxSize()) { content() }
                }
            }
        }
        composeRule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        composeRule.waitForIdle()
        val view = composeRule.activity.window.decorView
        val metrics = composeRule.activity.resources.displayMetrics
        if (view.width == 0 || view.height == 0) {
            view.measure(
                View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, metrics.widthPixels, metrics.heightPixels)
        }
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        OUTPUT.mkdirs()
        File(OUTPUT, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** A board with one post on it, and the post is a shared script. */
    private inner class ScriptBoard : CommunityGateway {

        private val post = CommunityPost(
            id = 7L,
            author = "بهنام",
            content = ScriptShare.post(shared),
            category = CommunityCategory.EXPERIENCE,
            categoryLabel = "تجربه",
            likes = 4,
            liked = false,
            replyCount = 1,
            reactions = mapOf("🔥" to 2),
            bestReplyId = null,
            createdAt = java.time.Instant.parse("2026-09-11T07:40:00Z"),
            pending = false,
        )

        override suspend fun me(): CommunityMember = CommunityMember(id = 1L, displayName = "بهنام")
        override suspend fun register(displayName: String) = CommunityMember(id = 1L, displayName = displayName)
        override suspend fun feed(page: Int, category: CommunityCategory?) =
            CommunityFeedPage(posts = listOf(post), page = page, received = 1)
        override suspend fun search(query: String) = listOf(post)
        override suspend fun thread(id: Long) = CommunityThread(post = post, replies = emptyList())
        override suspend fun post(content: String, category: CommunityCategory, image: ByteArray?) =
            CommunityWriteOutcome(id = 8L, published = true, message = "منتشر شد.")
        override suspend fun image(post: CommunityPost): ByteArray? = null
        override suspend fun reply(postId: Long, content: String, parentId: Long?) =
            CommunityWriteOutcome(id = null, published = true, message = null)
        override suspend fun like(postId: Long, currentLikes: Int) =
            CommunityLikeOutcome(likes = currentLikes + 1, liked = true)
        override suspend fun react(postId: Long, emoji: String) =
            CommunityReactionOutcome(counts = mapOf(emoji to 1), mine = setOf(emoji))
        override suspend fun report(postId: Long) = Unit
        override suspend fun bestReply(postId: Long, replyId: Long) = Unit
        override suspend fun leaderboard() =
            CommunityLeaderboard(leaders = emptyList(), myRank = null, totalStudents = 0)
    }

    private companion object {
        val OUTPUT = File("build/proof")
        const val FA_PHONE = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi"
    }
}
