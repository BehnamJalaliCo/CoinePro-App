/*
 * :chart-ui — the Compose chart, and the first module of the web port.
 *
 * This is a Kotlin Multiplatform module with **one target today**, Android. That looks like
 * ceremony for nothing and is not: `docs/web/PARITY.md` §3a counted what is actually Android in
 * these 15,242 lines and found **22 imports**, of which seven are `LocalDensity` and
 * `LocalLayoutDirection` — which Compose Multiplatform has under the same names — and seven more
 * are two files, `ChartFrameRate.kt` and `ChartStrokePredictor.kt`. Everything else draws on
 * `androidx.compose.ui.graphics.Canvas`, which is Compose's own API and identical in a browser.
 *
 * So the browser target is a source-set split rather than a rewrite, and this is the split's
 * first half: the sources sit in `androidMain` from now on, where an eventual `commonMain` can be
 * lifted out from under them a file at a time with the build green after each one. Adding
 * `wasmJs` later adds a target; it does not move a module.
 *
 * See docs/engineering/MODULES.md and docs/web/PARITY.md §3.
 */
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    androidLibrary {
        namespace = "com.coinepro.core.chart"
        compileSdk = 36
        minSdk = 26
        // Robolectric with native graphics, for the one test that has to draw: the static layer's
        // bitmap cache is only provably a cache with real pixels behind it.
        withHostTestBuilder {}.configure { isIncludeAndroidResources = true }
    }
    sourceSets {
        androidMain.dependencies {
            // The engine, re-exported: every consumer of the Compose chart also speaks its types.
            api(project(":chart-core"))
            implementation(project(":core:designsystem"))
            implementation(project.dependencies.platform(libs.androidx.compose.bom))
            implementation(libs.androidx.compose.foundation)
            implementation(libs.androidx.compose.ui)
            implementation(libs.androidx.compose.material3)
            // The stylus's next point, predicted from its recent path — see the freehand tool.
            implementation(libs.androidx.input.motionprediction)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
            implementation(libs.robolectric)
            implementation(libs.androidx.test.core)
        }
    }
}

// The repository's gate is `./gradlew testDebugUnitTest`; a multiplatform module has no such task,
// so this one answers to the name and runs the Android host tests, which are the module's tests.
tasks.register("testDebugUnitTest") {
    group = "verification"
    description = "Runs the Android host tests, under the name the Android modules use."
    dependsOn("testAndroidHostTest")
}
