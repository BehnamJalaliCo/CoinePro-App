package com.coinepro.core.chart

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpace
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The image tool's picture, from the id in its text to the rectangle it lands in.
 *
 * Everything here is the arithmetic and the bookkeeping — no decoder, no file, no device. What is
 * deliberately not tested is the draw call itself: putting a bitmap on a canvas is Compose's job,
 * and a test that asserted it happened would be asserting that the line above it exists.
 */
class DrawingImageTest {

    /**
     * A picture with a size and nothing else.
     *
     * `ImageBitmap` is an interface, which is the only reason any of this is testable off a device:
     * a real one is a `Bitmap` and there is no Android runtime here.
     */
    private class FakeImage(override val width: Int, override val height: Int) : ImageBitmap {
        override val config: ImageBitmapConfig get() = ImageBitmapConfig.Argb8888
        override val colorSpace: ColorSpace get() = ColorSpaces.Srgb
        override val hasAlpha: Boolean get() = false
        override fun prepareToDraw() = Unit
        override fun readPixels(
            buffer: IntArray,
            startX: Int,
            startY: Int,
            width: Int,
            height: Int,
            bufferOffset: Int,
            stride: Int,
        ) = Unit
    }

    @After
    fun tearDown() = DrawingImages.clear()

    // ---------------------------------------------------------------- the id inside the text

    @Test
    fun `a bare id is a picture and no caption`() {
        val id = "img_0123456789abcdef"

        assertEquals(id, DrawingImages.idIn(id))
        assertNull(DrawingImages.captionIn(id))
    }

    @Test
    fun `an id and a caption share the one field`() {
        val text = DrawingImages.textFor("img_0123456789abcdef", "ورود روز جمعه")

        assertEquals("img_0123456789abcdef", DrawingImages.idIn(text))
        assertEquals("ورود روز جمعه", DrawingImages.captionIn(text))
    }

    @Test
    fun `an ordinary caption is left alone`() {
        // Every other drawing keeps its words in the same field. A note that happens to start with
        // a word must not be read as a missing picture.
        for (text in listOf("یادداشت", "image", "img_", "img_zzzz caption", "IMG_0123456789ABCDEF")) {
            assertNull(text, DrawingImages.idIn(text))
            assertEquals(text, text.trim(), DrawingImages.captionIn(text))
        }
    }

    @Test
    fun `nothing typed is nothing at all`() {
        assertNull(DrawingImages.idIn(null))
        assertNull(DrawingImages.idIn("   "))
        assertNull(DrawingImages.captionIn(null))
        assertNull(DrawingImages.captionIn("   "))
        assertEquals("img_0123456789abcdef", DrawingImages.textFor("img_0123456789abcdef", "  "))
    }

    // ---------------------------------------------------------------- what the frame knows

    @Test
    fun `a picture nobody has asked for is waiting, and one that is missing says so`() {
        val id = "img_00000000000000ff"

        assertSame(DrawingImage.Waiting, DrawingImages.imageFor(id))
        DrawingImages.markGone(id)
        assertSame(DrawingImage.Gone, DrawingImages.imageFor(id))
    }

    @Test
    fun `a picture that arrives replaces the answer it had before`() {
        val id = "img_00000000000000aa"
        DrawingImages.markGone(id)

        DrawingImages.put(id, FakeImage(120, 80))

        val shown = DrawingImages.imageFor(id)
        assertTrue(shown is DrawingImage.Shown)
        assertEquals(120, (shown as DrawingImage.Shown).bitmap.width)
    }

    @Test
    fun `an evicted picture is waiting again and not gone`() {
        // The bytes are still on disk; the cache simply let go of the decoded copy. Telling the
        // reader it was missing would be the cache lying about the store.
        val ids = (0..12).map { "img_%016x".format(it) }
        ids.forEach { DrawingImages.put(it, FakeImage(64, 64)) }

        val dropped = ids.count { DrawingImages.imageFor(it) is DrawingImage.Waiting }
        assertTrue("nothing was evicted at all", dropped > 0)
        assertTrue("the newest picture must survive", DrawingImages.imageFor(ids.last()) is DrawingImage.Shown)
        assertTrue(ids.none { DrawingImages.imageFor(it) is DrawingImage.Gone })
    }

    @Test
    fun `forgetting a picture leaves the reader waiting rather than told it is missing`() {
        val id = "img_00000000000000bb"
        DrawingImages.put(id, FakeImage(10, 10))

        DrawingImages.forget(id)

        assertSame(DrawingImage.Waiting, DrawingImages.imageFor(id))
    }

    // ---------------------------------------------------------------- where it lands

    @Test
    fun `two anchors are the same box whichever corner was dragged first`() {
        val forwards = imageFrame(Offset(10f, 20f), Offset(110f, 90f), span = 0f, imageWidth = 4, imageHeight = 3)
        val backwards = imageFrame(Offset(110f, 90f), Offset(10f, 20f), span = 0f, imageWidth = 4, imageHeight = 3)

        assertEquals(forwards, backwards)
        assertEquals(Rect(10f, 20f, 110f, 90f), forwards)
    }

    @Test
    fun `one anchor takes its width from the bars and its height from the picture`() {
        val frame = imageFrame(Offset(10f, 20f), b = null, span = 200f, imageWidth = 400, imageHeight = 200)

        assertEquals(200f, frame.width, 0.01f)
        assertEquals(100f, frame.height, 0.01f)
        assertEquals(Offset(10f, 20f), frame.topLeft)
    }

    @Test
    fun `a one-anchor picture grows with the bars`() {
        val near = imageFrame(Offset.Zero, b = null, span = 100f, imageWidth = 4, imageHeight = 3)
        val far = imageFrame(Offset.Zero, b = null, span = 300f, imageWidth = 4, imageHeight = 3)

        assertTrue("zooming in must make the picture bigger", far.width > near.width)
        assertEquals(near.width / near.height, far.width / far.height, 0.001f)
    }

    @Test
    fun `a picture is letterboxed inside its box and never stretched`() {
        val wide = fitImage(Rect(0f, 0f, 100f, 100f), imageWidth = 200, imageHeight = 100)

        assertEquals(100f, wide.width, 0.01f)
        assertEquals(50f, wide.height, 0.01f)
        assertEquals("centred in the space it did not fill", 25f, wide.top, 0.01f)
        assertEquals(2f, wide.width / wide.height, 0.001f)
    }

    @Test
    fun `a tall picture is pillarboxed by the same rule`() {
        val tall = fitImage(Rect(0f, 0f, 100f, 100f), imageWidth = 100, imageHeight = 200)

        assertEquals(50f, tall.width, 0.01f)
        assertEquals(100f, tall.height, 0.01f)
        assertEquals(25f, tall.left, 0.01f)
    }

    @Test
    fun `a picture that already fits keeps its own shape`() {
        val fitted = fitImage(Rect(0f, 0f, 90f, 60f), imageWidth = 3, imageHeight = 2)

        assertEquals(90f, fitted.width, 0.01f)
        assertEquals(60f, fitted.height, 0.01f)
    }

    @Test
    fun `a box with no area is handed straight back`() {
        // A two-point drawing mid-placement, both anchors on the same bar. Nothing to divide by.
        val degenerate = Rect(40f, 40f, 40f, 40f)

        assertEquals(degenerate, fitImage(degenerate, 100, 50))
        assertEquals(degenerate, fitImage(degenerate, 0, 0))
    }
}
