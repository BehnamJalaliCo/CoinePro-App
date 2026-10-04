package com.coinepro.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.coinepro.core.designsystem.CoachCard
import com.coinepro.core.designsystem.CoachFilmFrame
import com.coinepro.core.designsystem.CoachScene
import com.coinepro.core.designsystem.CoachTip
import com.coinepro.core.designsystem.CoineProTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The coach's films on contact sheets (5.21.0): every scene at four moments of its loop, and the
 * card as a reader meets it. Written to `build/screenshots/coach-*.png` for review; not goldens —
 * a film is judged by eye, and a pixel gate on an illustration would only ever be re-recorded.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
class CoachFilmsRenderTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun contactSheetOne() = sheet(0)

    @Test fun contactSheetTwo() = sheet(1)

    @Test fun contactSheetThree() = sheet(2)

    @Test fun contactSheetFour() = sheet(3)

    @Test fun contactSheetFive() = sheet(4)

    /** Three films a sheet, two rows each, four moments of the loop at the card's own proportions. */
    private fun sheet(page: Int) {
        val scenes = CoachScene.entries.chunked(3)[page]
        capture("coach-films-${page + 1}") {
            Column(
                modifier = Modifier.fillMaxSize().background(Color(0xFF0D0F22)).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                scenes.forEach { scene ->
                    FRAMES.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            pair.forEach { frame ->
                                Box(Modifier.width(194.dp).height(100.dp).clip(RoundedCornerShape(10.dp))) {
                                    CoachFilmFrame(scene = scene, touch = true, frame = frame, modifier = Modifier.fillMaxSize())
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun theCardAsAReaderMeetsIt() {
        capture("coach-card") {
            Box(Modifier.fillMaxSize().background(Color(0xCC0D0F22)).padding(16.dp)) {
                CoachCard(tip = CoachTip.CHART_PINCH, touchFirst = true, onDone = {}, onNever = {})
            }
        }
    }

    private fun capture(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        // The card's film runs for ever; the clock is driven by hand so the render never waits on it.
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { CoineProTheme(darkTheme = true) { content() } }
        composeRule.mainClock.advanceTimeBy(1_500)
        shadowOf(Looper.getMainLooper()).idle()
        val view = composeRule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        OUTPUT_DIR.mkdirs()
        File(OUTPUT_DIR, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        val OUTPUT_DIR = File("build/screenshots")
        val FRAMES = listOf(0.2f, 0.42f, 0.6f, 0.82f)
    }
}
