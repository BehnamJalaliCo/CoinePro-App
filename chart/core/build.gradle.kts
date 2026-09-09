/*
 * :chart-core — the chart engine with no platform in it.
 *
 * Scales, series types, drawings (geometry and state), indicators, replay, the backtester, the
 * object tree, the viewport: everything that decides *what* a chart shows, and nothing that draws
 * it. Compose, Android, `java.*` are all absent from `commonMain` by construction — the module
 * compiles for the JVM target as well as Android, and `ArchitectureTest` fails on an import that
 * would break that. The five things a chart needs from its platform (a clock, a zone, two number
 * and date formatters) are `expect` declarations in `ChartPlatform.kt`, with one shared `actual`
 * for the two JVM-based targets.
 *
 * See docs/engineering/MODULES.md.
 */
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    jvm()
    androidLibrary {
        namespace = "com.coinepro.chart.core"
        compileSdk = 36
        minSdk = 26
    }
    sourceSets {
        // One `actual` for both JVM-based targets; the web target gets its own when it exists.
        val jvmShared by creating { dependsOn(commonMain.get()) }
        jvmMain.get().dependsOn(jvmShared)
        androidMain.get().dependsOn(jvmShared)
        jvmTest.dependencies { implementation(libs.junit) }
    }
}

// The repository's gate is `./gradlew testDebugUnitTest`; a multiplatform module has no such
// task, so this one answers to the name and runs the JVM tests, which are the module's tests.
tasks.register("testDebugUnitTest") {
    group = "verification"
    description = "Runs the JVM tests, under the name the Android modules use."
    dependsOn("jvmTest")
}
