package com.coinepro.core.datastore

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The file side of [DrawingImageStore], which is all of it that can be tested off a device.
 *
 * [DrawingImageStore.put] decodes through `BitmapFactory` and there is no Android runtime here, so
 * these plant files under the store's own ids instead — which is exactly the state the store finds
 * after a restart, and the state every one of these behaviours is about.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DrawingImageStoreTest {

    private fun root(): File = Files.createTempDirectory("drawing-images").toFile()

    private fun plant(root: File, id: String, bytes: ByteArray = byteArrayOf(1, 2, 3)): File =
        File(root, id).apply { writeBytes(bytes) }

    @Test
    fun `an id is read back as the bytes that were stored under it`() = runTest {
        val root = root()
        val store = DrawingImageStore(root)
        val id = DrawingImageStore.newId()
        plant(root, id, byteArrayOf(7, 7, 9))

        assertArrayEquals(byteArrayOf(7, 7, 9), store.read(id))
    }

    @Test
    fun `a picture whose file is gone reads as absent rather than as a failure`() = runTest {
        val store = DrawingImageStore(root())

        // The reinstall case, and the whole missing-file contract: the caller gets null and decides
        // what to draw. Nothing here throws, so a chart carrying the drawing still opens.
        assertNull(store.read(DrawingImageStore.newId()))
    }

    @Test
    fun `a store pointed at a directory that does not exist answers rather than throws`() = runTest {
        val store = DrawingImageStore(File(root(), "never-created"))

        assertNull(store.read(DrawingImageStore.newId()))
        assertFalse(store.forget(DrawingImageStore.newId()))
    }

    @Test
    fun `text that is not an id never becomes a path`() = runTest {
        val root = root()
        val outside = File(root.parentFile, "outside-the-store").apply { writeBytes(byteArrayOf(1)) }
        val store = DrawingImageStore(root)

        // The id arrives in `Drawing.text`, which a reader can type into. Every one of these is a
        // string somebody could put there, and none of them may reach the filesystem.
        for (attempt in listOf("../${outside.name}", "img_../x", "", "img_", "IMG_0123456789abcdef", "img_zzzz")) {
            assertNull(attempt, store.read(attempt))
            assertFalse(attempt, store.forget(attempt))
        }
        assertTrue("a file outside the store must be untouched", outside.isFile)
        outside.delete()
    }

    @Test
    fun `forget removes the picture and says whether there was one`() = runTest {
        val root = root()
        val store = DrawingImageStore(root)
        val id = DrawingImageStore.newId()
        val file = plant(root, id)

        assertTrue(store.forget(id))
        assertFalse(file.isFile)
        assertFalse("nothing left to remove the second time", store.forget(id))
    }

    @Test
    fun `a sweep keeps what drawings still point at and drops what nothing does`() = runTest {
        val root = root()
        val store = DrawingImageStore(root)
        val kept = plant(root, DrawingImageStore.newId())
        val orphan = plant(root, DrawingImageStore.newId())
        val fragment = File(root, DrawingImageStore.newId() + ".part").apply { writeBytes(byteArrayOf(9)) }
        val foreign = File(root, "notes.txt").apply { writeText("someone else's") }

        store.sweep(setOf(kept.name))

        assertTrue(kept.isFile)
        assertFalse(orphan.isFile)
        assertFalse("a half-written picture is not a picture", fragment.isFile)
        assertTrue("a file the store cannot account for is left alone", foreign.isFile)
    }

    @Test
    fun `a fresh id is this store's shape and is not the last one`() {
        val first = DrawingImageStore.newId()
        val second = DrawingImageStore.newId()

        assertTrue(DrawingImageStore.isImageId(first))
        assertTrue(first.startsWith(DrawingImageStore.ID_PREFIX))
        assertNotEquals(first, second)
    }

    @Test
    fun `the sample brings the longest side to at or above the cap and never under it`() {
        // At or above is the point: a sample that overshoots throws away detail the exact scale
        // afterwards cannot get back.
        for (longest in listOf(1025, 2048, 3000, 4096, 12_000)) {
            val sample = DrawingImageStore.sampleSizeFor(longest, longest / 2, 1024)
            assertTrue("$longest sampled below the cap", longest / sample >= 1024)
            assertTrue("$longest sampled further than it had to be", longest / (sample * 2) < 1024)
        }
    }

    @Test
    fun `a picture already inside the cap is not sampled at all`() {
        assertTrue(DrawingImageStore.sampleSizeFor(800, 600, 1024) == 1)
        assertTrue("nonsense dimensions are a no-op, not a division", DrawingImageStore.sampleSizeFor(0, 0, 1024) == 1)
    }
}
