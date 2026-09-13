package com.coinepro.app

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A test that names no Android runs on the one `robolectric.properties` pins.
 *
 * This class deliberately carries **no** `@Config`. That is the whole point: it is the only test in
 * the repository whose job is to fail when the default changes, and it can only do that by being
 * silent about what it wants.
 *
 * What it is guarding against happened, and cost every APK build on `main` for a fortnight. The app
 * targets API 36; Robolectric's default is that same number; and the runner on GitHub's hosted
 * machines answers 36 with `UnsupportedOperationException` out of `DefaultSdkProvider`. One test
 * anywhere in a class without an explicit `@Config` took the whole class down with
 * `classMethod FAILED`, four classes went with it, and the build that produces the reader's APK
 * went red — while the same suite passed in a container that already held the jar for 36.
 *
 * That is the worst shape a failure can take: green where somebody is looking, red where nobody is.
 * A pinned default fixes it; this fixes it *visibly*, so the next person who raises `compileSdk`
 * sees one small test fail with a sentence rather than four classes fail with a stack trace.
 */
@RunWith(RobolectricTestRunner::class)
class RobolectricDefaultSdkTest {

    @Test
    fun `the default sdk is pinned rather than taken from the build`() {
        assertEquals(
            "app/src/test/resources/robolectric.properties is missing or no longer pins the default",
            PINNED,
            Build.VERSION.SDK_INT,
        )
    }

    private companion object {
        /** What `robolectric.properties` says, and what every deliberate `@Config` here names. */
        const val PINNED = 34
    }
}
