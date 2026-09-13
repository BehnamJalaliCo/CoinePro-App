package com.coinepro.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import com.coinepro.core.designsystem.CoineProSheetBody
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.database.SavedScriptDao
import com.coinepro.core.database.SavedScriptEntity
import com.coinepro.core.script.ScriptPaste
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import com.coinepro.core.script.ScriptPromptKit
import com.coinepro.feature.script.ScriptPasteBody
import com.coinepro.core.script.ScriptController
import com.coinepro.feature.script.ScriptMinePanelPreview
import com.coinepro.feature.script.ScriptPromptBody
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * **Bring your own script**, photographed (run Σ, S3 A and B).
 *
 * Four frames, and each answers a question the checklist would otherwise have to take on trust:
 *
 * * what a reader sees after pasting an assistant's answer that needed work — the list of repairs,
 *   each in their language, with the undo;
 * * what they see after pasting a sentence — the shortlist, not an error;
 * * what the prompt kit looks like with a real prompt in it, in Persian;
 * * and the same in English, because the prompt is generated and the English half of it is the
 *   half nobody looks at by accident.
 *
 * The bodies are rendered rather than the sheets: a `ModalBottomSheet` draws into its own window
 * and an off-device capture of the activity comes back empty, which is the reason
 * `ScriptPasteBody` exists as its own composable.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScriptPasteProofTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** What an assistant asked for «a NamaScript indicator» actually returns. */
    private val messy = """
        //@version=5
        indicator("EMA Cross", overlay=true)
        fastLen = input.int(9, "Fast", minval=1)
        fast = ema(close, fastLen)
        slow = ta.rma(close, 21)
        plot(fast, "Fast", color=color.orange, linewidth=2)
        plotshape(fast > slow && close(1) < slow, style=shape.triangleup)
    """.trimIndent()

    @Test
    @Config(sdk = [34], qualifiers = FA_PHONE)
    fun `a pasted script shows what was fixed`() {
        val paste = ScriptPaste.read(messy)
        assertTrue("the fixture needed no repair, so the frame proves nothing", paste.fixes.size >= 3)
        proof("sigma1-paste-fixes-phone-fa") {
            CoineProSheetBody(title = "چسباندن اسکریپت", subtitle = "به عنوان اسکریپت تریدینگ‌ویو خوانده و ترجمه شد") {
                ScriptPasteBody(paste = paste, onKeep = {}, onUndo = {}, onUseTemplate = {})
            }
        }
        // The undo has to be on the frame: the repairs were applied without asking, and a reader
        // who cannot see the way back has to read all of them before trusting any of them.
        assertTrue(
            "the undo is not on the frame",
            composeRule.onAllNodesWithText("برگرداندن اصلاح‌ها").fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    @Config(sdk = [34], qualifiers = FA_PHONE)
    fun `a pasted sentence shows a shortlist rather than an error`() {
        val paste = ScriptPaste.read("وقتی میانگین ۲۰ از میانگین ۵۰ رد شد بخر و وقتی برعکس شد بفروش")
        assertTrue("no template was offered", paste.templates.isNotEmpty())
        proof("sigma1-paste-templates-phone-fa") {
            CoineProSheetBody(title = "چسباندن اسکریپت", subtitle = "به عنوان توضیح خوانده شد") {
                ScriptPasteBody(paste = paste, onKeep = {}, onUndo = {}, onUseTemplate = {})
            }
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = FA_PHONE)
    fun `the prompt kit, in Persian`() {
        proof("sigma1-prompt-kit-phone-fa") {
            CoineProSheetBody(title = "گرفتن اسکریپت از هوش مصنوعی", subtitle = "نسخه‌ی متن ۱") {
                ScriptPromptBody(
                    prompt = ScriptPromptKit.prompt(symbol = "XAUUSD", timeframe = "۱ ساعته"),
                    onOpenAssistant = {},
                    onPasteResult = {},
                )
            }
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = EN_PHONE)
    fun `the prompt kit, in English`() {
        proof("sigma1-prompt-kit-phone-en") {
            CoineProSheetBody(title = "Get a script from an AI", subtitle = "Prompt version 1") {
                ScriptPromptBody(
                    prompt = ScriptPromptKit.prompt(symbol = "XAUUSD", timeframe = "1 hour", english = true),
                    onOpenAssistant = {},
                    onPasteResult = {},
                )
            }
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = FA_PHONE)
    fun `a script the reader owns, with its versions`() {
        // The panel with something in every field, which is the only version worth photographing:
        // an empty one proves the layout and nothing about what a reader sees after a fortnight.
        val controller = ScriptController(NoScripts(), CoroutineScope(Dispatchers.Unconfined))
        controller.openText(name = "میانگین من", source = "plot(ta.ema(close, 20), title = \"میانگین\")")
        controller.describe("وقتی قیمت از میانگین رد شد")
        controller.setColour(0xFF2962FF)
        controller.setTags("روند، میانگین، طلا")
        controller.setOwnPane(false)
        proof("sigma1-my-script-phone-fa") {
            CoineProSheetBody(title = "نمااسکریپت", subtitle = "XAUUSD") {
                ScriptMinePanelPreview(controller)
            }
        }
        assertTrue(
            "the panel is not on the frame",
            composeRule.onAllNodesWithText("این اسکریپت مال من است").fetchSemanticsNodes().isNotEmpty(),
        )
    }

    private fun proof(name: String, content: @Composable () -> Unit) {
        composeRule.setContent {
            CoineProTheme(darkTheme = true) {
                Surface(modifier = Modifier.fillMaxSize()) { content() }
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

    private companion object {
        val OUTPUT = File("build/proof")
        const val FA_PHONE = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi"
        const val EN_PHONE = "en-rUS-w411dp-h914dp-xxhdpi"
    }

    /** A store with nothing in it: this frame is about the panel, not about the library. */
    private class NoScripts : SavedScriptDao {
        override fun scripts(): Flow<List<SavedScriptEntity>> = MutableStateFlow(emptyList())
        override suspend fun byId(id: Long): SavedScriptEntity? = null
        override suspend fun count(): Int = 0
        override suspend fun insert(script: SavedScriptEntity): Long = 1
        override suspend fun update(script: SavedScriptEntity) = Unit
        override suspend fun delete(id: Long) = Unit
    }
}
