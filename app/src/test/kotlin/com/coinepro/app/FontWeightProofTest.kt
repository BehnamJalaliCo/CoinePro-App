package com.coinepro.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.platform.LocalFontFamilyResolver
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProFontFamily
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.designsystem.LocalTeachingDismissals
import org.junit.Assert.assertEquals
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
 * The four weights of IRANYekanX Pro on one frame (run A, 4.68.0), and the proof that they are
 * four faces and not one face emboldened: the same Persian line advances a different width at
 * 400, 500, 600 and 700, strictly increasing, which synthetic bold of a single file cannot
 * produce (it thickens strokes, it does not respace). The frame goes to `build/proof/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FontWeightProofTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun fourWeightsAreFourFaces() {
        val widths = FloatArray(WEIGHTS.size)
        proof("fonts-four-weights-fa") {
            // The ten Latin digits through the platform's text engine on the typeface Compose
            // resolves for each weight — the faces the frame is drawn with. The family's digits
            // advance 562 / 565 / 569 / 572 units in the four files (the consistency gate reads
            // the same numbers), so four real faces set the run at four widths, strictly wider
            // with the weight; a synthetic bold of one file thickens strokes and moves nothing.
            // A thousand pixels per em, so the engine's per-glyph rounding cannot fold 565 into 569.
            val resolver = LocalFontFamilyResolver.current
            WEIGHTS.forEachIndexed { index, (weight, _) ->
                val typeface = resolver.resolve(CoineProFontFamily, weight).value as Typeface
                widths[index] = Paint().apply { this.typeface = typeface; textSize = 1_000f }.measureText(DIGITS)
            }
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                WEIGHTS.forEach { (weight, name) ->
                    val style = TextStyle(fontFamily = CoineProFontFamily, fontWeight = weight, fontSize = 24.sp)
                    Text(text = name, style = MaterialTheme.typography.labelSmall, color = CoineProColors.TextMuted)
                    Text(text = SAMPLE, style = style, color = CoineProColors.TextPrimary)
                    Text(text = "0123456789 · 2,574.90 · +1.04%", style = style, color = CoineProColors.TextPrimary)
                }
            }
        }
        for (index in 1 until widths.size) {
            assertTrue(
                "${WEIGHTS[index].second} (${widths[index]} px) must set its digits wider than ${WEIGHTS[index - 1].second} (${widths[index - 1]} px): four files, four advances; all: ${widths.toList()}",
                widths[index] > widths[index - 1],
            )
        }
        assertEquals(4, widths.toSet().size)
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
        val view = composeRule.activity.window.decorView
        if (view.width == 0 || view.height == 0) {
            val metrics = composeRule.activity.resources.displayMetrics
            view.measure(
                View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, metrics.widthPixels, metrics.heightPixels)
        }
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        OUTPUT_DIR.mkdirs()
        File(OUTPUT_DIR, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        val OUTPUT_DIR = File("build/proof")
        const val SAMPLE = "طلا / دلار آمریکا — خوانش بازار و ابزارها"
        const val DIGITS = "0123456789"
        val WEIGHTS = listOf(
            FontWeight.Normal to "Regular 400",
            FontWeight.Medium to "Medium 500",
            FontWeight.SemiBold to "DemiBold 600",
            FontWeight.Bold to "Bold 700",
        )
    }
}
