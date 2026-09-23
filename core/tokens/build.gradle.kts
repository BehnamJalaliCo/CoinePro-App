/*
 * :core:tokens — the part of the design system a browser can have.
 *
 * The web port's first rule is «one implementation»: the terminal draws with the same colours, the
 * same figures and the same palette as the phone, because they are the same code. `:core:designsystem`
 * cannot be that code as a whole — it holds Android resources, a share card built on
 * `android.graphics`, Coil, the window-size library — so the pieces that are plain Compose are
 * lifted out into here, **in the same package**, one file at a time. Nothing that imports them
 * changes: `com.coinepro.core.designsystem.CoineProPalette` is still that name, it just lives in a
 * module with a browser target.
 *
 * Compose for the browser is JetBrains' Compose Multiplatform. **1.9.0 exactly, and that is a
 * constraint rather than a preference**: on Android its artifacts resolve to androidx Compose 1.9.0
 * and lifecycle 2.9.4, both *below* what the app already uses (the BOM's 1.9.2, lifecycle 2.10.0),
 * so Gradle keeps the app's versions and the phone is byte-for-byte the build it was. A newer
 * Compose Multiplatform would quietly upgrade the phone's Compose under a refactor that promised to
 * change nothing. Raise it together with the BOM, never alone.
 *
 * See docs/web/TERMINAL_BUILD_PROMPT.md and docs/web/PARITY.md §3.
 */
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs { browser() }
    androidLibrary {
        namespace = "com.coinepro.core.tokens"
        compileSdk = 36
        minSdk = 26
    }
    sourceSets {
        commonMain.dependencies {
            api(libs.jetbrains.compose.runtime)
            api(libs.jetbrains.compose.ui)
        }
    }
}

// The repository's gate is `./gradlew testDebugUnitTest`; this module has no tests of its own yet
// (the palette's contrast is tested where it always was), so the alias answers and does nothing.
tasks.register("testDebugUnitTest") {
    group = "verification"
    description = "Present so the repository's gate finds a task of this name on every module."
}
