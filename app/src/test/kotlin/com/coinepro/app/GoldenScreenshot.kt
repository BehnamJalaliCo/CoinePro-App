package com.coinepro.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.provider.Settings
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.core.app.ApplicationProvider
import com.coinepro.core.designsystem.CoineProTheme
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.fail
import org.robolectric.Shadows.shadowOf

/**
 * A real pixel regression, rather than a folder of pictures nobody opens.
 *
 * ### What was there before
 *
 * `ScreenshotRenderTest` renders about a hundred and twenty screens and writes each one to
 * `build/screenshots`. That is genuinely useful — it is how a layout is reviewed, and several
 * faults in this app were found by looking at one — but it is **not a test**. Every one of those
 * cases passes as long as Compose does not throw, so a screen that lost its heading, doubled its
 * chrome or came back blank is a green build with a different PNG in a build directory that CI
 * deletes. Every regression this project has shipped in that class of fault shipped past it.
 *
 * This is the other half: a small, fixed matrix of surfaces whose pixels are **committed**, and a
 * comparison that fails the build when they move. It does not replace the render suite; it guards
 * the handful of screens where a point of chrome is the product.
 *
 * ### How a failure reads
 *
 * The count of differing pixels, that count as a share of the frame, and three files on disk: the
 * golden, what was actually rendered, and a diff with every differing pixel painted. A failure that
 * only says "images differ" sends the reader back to run it again with printf.
 *
 * ### What makes it deterministic
 *
 * Animation is switched off at the source — `ANIMATOR_DURATION_SCALE` is set to zero, which is the
 * same switch a reader who has turned animations off flips, and which every animated composable in
 * this app already consults through `continuousMotionAllowed()`. So a capture is the finished
 * frame, not whichever frame the render happened to stop on. Fixtures are fixed, there is no
 * network, and nothing here reads a clock the layout can see.
 *
 * ### Recording
 *
 * A golden that does not exist is written and the test fails, once, saying so — a first run cannot
 * silently bless whatever the screen looks like today. To re-record deliberately, run with
 * `-Dcoinepro.golden.record=true`; the files land in `app/src/test/goldens` and are committed like
 * any other source.
 */
internal object GoldenScreenshot {

    /** Where the committed pixels live. Source, not build output — they are reviewed in diffs. */
    private val GOLDEN_DIR = File("src/test/goldens")

    /** Where a failure leaves what it actually saw, beside the diff. */
    private val REPORT_DIR = File("build/golden-report")

    /**
     * How much of a frame may differ before the case fails, as a share of its pixels.
     *
     * A tenth of a per cent. Not zero: text rendering carries a little platform noise across JDK
     * and Robolectric versions, and a gate that fails on four anti-aliased pixels is a gate people
     * turn off. It is small enough that no layout change survives it — one point of padding on a
     * 411 pt phone moves about half a per cent of the frame.
     */
    private const val TOLERANCE = 0.001

    /**
     * How far one channel may move before the pixel counts as different.
     *
     * Eight of 255. Anti-aliasing along a glyph edge lands inside this; a changed colour token does
     * not — the smallest step in this app's own palette is far wider.
     */
    private const val CHANNEL_SLACK = 8

    private val recording: Boolean
        get() = System.getProperty("coinepro.golden.record") == "true"

    /**
     * Whether this is a machine, rather than somebody looking at the screen.
     *
     * Both variables, because either one on its own is a guess: `CI` is the convention every
     * runner sets and `GITHUB_ACTIONS` is the one that is definitely true here.
     */
    private val inCi: Boolean
        get() = System.getenv("CI") == "true" || System.getenv("GITHUB_ACTIONS") == "true"

    /**
     * The two things a golden gate must never do on a machine.
     *
     * **Re-record.** A recording run rewrites every baseline and passes. On a laptop that is the
     * point — somebody changed a layout, looked at the new pixels, and committed them. In CI it is
     * a gate that blesses whatever the build produced and then reports success, which is worse than
     * having no gate at all because it reads as one.
     *
     * **Bless a missing baseline.** Locally, a golden that does not exist is written and the case
     * fails once, so the next thing that happens is a person opening the PNG. On a machine there is
     * nobody to open it and nothing to commit it, so the file would be written into a build that is
     * thrown away and the case would fail for a reason that sounds temporary. It is not: a golden
     * that is not in the repository is a golden that was never reviewed.
     */
    private fun refuseToRecordOnAMachine(name: String, golden: File) {
        if (recording && inCi) {
            fail(
                "Golden record mode is on in CI. Re-recording is a decision somebody takes while " +
                    "looking at the pixels; a machine doing it silently turns this gate into a " +
                    "rubber stamp. Drop -Dcoinepro.golden.record from the CI invocation, " +
                    "re-record locally, and commit the result.",
            )
        }
        if (!golden.exists() && inCi) {
            fail(
                "No committed golden for '$name' (expected ${golden.path}). A baseline that is " +
                    "not in the repository has never been reviewed, so there is nothing here to " +
                    "compare against and nothing to bless. Record it locally and commit it.",
            )
        }
    }

    /**
     * Render [content] and hold it to [name]'s committed pixels.
     *
     * [darkTheme] is pinned rather than left to the host configuration, for the reason the render
     * suite pins it: a capture must be the theme it is named for whatever the machine reports.
     */
    fun <A : ComponentActivity> AndroidComposeTestRule<*, A>.assertMatchesGolden(
        name: String,
        darkTheme: Boolean = true,
        content: @Composable () -> Unit,
    ) {
        stopAnimations()
        setContent {
            CoineProTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) { content() }
            }
        }
        waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        compare(name, draw(activity.window.decorView, activity.resources.displayMetrics))
    }

    /**
     * Animations off, at the switch the app itself reads.
     *
     * Not a Compose test-clock trick: this app gates its own motion on the platform's animator
     * scale, so setting it to zero is both the honest way to freeze a capture and, incidentally, a
     * check that every animated surface in the matrix actually honours the setting.
     */
    private fun stopAnimations() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f,
        )
    }

    /**
     * The decor view's pixels.
     *
     * Compose's own `captureToImage` goes through the platform window-capture path, which has no
     * real window under Robolectric. Drawing the decor view straight into a bitmap produces the
     * same pixels without needing one — and the fallback measure uses the configuration the test is
     * actually running in, so a case that overrides the qualifier is not measured at some other
     * width.
     */
    private fun draw(view: View, metrics: android.util.DisplayMetrics): BufferedImage {
        if (view.width == 0 || view.height == 0) {
            view.measure(
                View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, metrics.widthPixels, metrics.heightPixels)
        }
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val image = BufferedImage(bitmap.width, bitmap.height, BufferedImage.TYPE_INT_RGB)
        image.setRGB(0, 0, bitmap.width, bitmap.height, pixels, 0, bitmap.width)
        return image
    }

    private fun compare(name: String, actual: BufferedImage) {
        GOLDEN_DIR.mkdirs()
        val golden = File(GOLDEN_DIR, "$name.png")
        refuseToRecordOnAMachine(name, golden)

        if (recording || !golden.exists()) {
            ImageIO.write(actual, "png", golden)
            if (!recording) {
                fail(
                    "No golden for '$name'. One has been written to ${golden.path} — look at it, " +
                        "and commit it if it is what this screen should be.",
                )
            }
            return
        }

        val expected = ImageIO.read(golden)
        if (expected.width != actual.width || expected.height != actual.height) {
            report(name, actual, null)
            fail(
                "Golden '$name' is ${expected.width}×${expected.height} and the render is " +
                    "${actual.width}×${actual.height}. A size change is a layout change; see " +
                    "${REPORT_DIR.path}.",
            )
            return
        }

        val diff = BufferedImage(actual.width, actual.height, BufferedImage.TYPE_INT_RGB)
        var differing = 0
        for (y in 0 until actual.height) {
            for (x in 0 until actual.width) {
                val a = actual.getRGB(x, y)
                val b = expected.getRGB(x, y)
                if (apart(a, b)) {
                    differing += 1
                    diff.setRGB(x, y, DIFF_INK)
                } else {
                    // The golden, dimmed, so the differing pixels are read against the layout they
                    // are in rather than against a blank sheet.
                    diff.setRGB(x, y, dim(b))
                }
            }
        }
        if (differing == 0) return
        val ratio = differing.toDouble() / (actual.width * actual.height)
        if (ratio <= TOLERANCE) return
        report(name, actual, diff)
        fail(
            "Golden '$name' differs: $differing pixels, " +
                String.format(java.util.Locale.US, "%.4f%%", ratio * 100) +
                " of the frame (tolerance " +
                String.format(java.util.Locale.US, "%.4f%%", TOLERANCE * 100) + ").\n" +
                "  golden: ${golden.path}\n" +
                "  actual: ${File(REPORT_DIR, "$name-actual.png").path}\n" +
                "  diff:   ${File(REPORT_DIR, "$name-diff.png").path}\n" +
                "If the change is intended, re-record with -Dcoinepro.golden.record=true and " +
                "commit the new pixels.",
        )
    }

    private fun report(name: String, actual: BufferedImage, diff: BufferedImage?) {
        REPORT_DIR.mkdirs()
        ImageIO.write(actual, "png", File(REPORT_DIR, "$name-actual.png"))
        diff?.let { ImageIO.write(it, "png", File(REPORT_DIR, "$name-diff.png")) }
    }

    /** Whether two pixels differ by more than [CHANNEL_SLACK] on any channel. */
    private fun apart(a: Int, b: Int): Boolean {
        if (a == b) return false
        for (shift in intArrayOf(16, 8, 0)) {
            val delta = ((a shr shift) and 0xFF) - ((b shr shift) and 0xFF)
            if (delta > CHANNEL_SLACK || delta < -CHANNEL_SLACK) return true
        }
        return false
    }

    private fun dim(rgb: Int): Int {
        var out = 0
        for (shift in intArrayOf(16, 8, 0)) {
            val channel = (rgb shr shift) and 0xFF
            out = out or (((channel * 3 + 0xFF) / 5) shl shift)
        }
        return out
    }

    /** Magenta: a colour this app's palette contains nowhere, so a diff cannot be mistaken. */
    private const val DIFF_INK = 0xFFFF00FF.toInt()
}
