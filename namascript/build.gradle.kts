/*
 * :namascript — the NamaScript language: lexer, parser, interpreter, built-ins, the reference and
 * the shipped presets. No platform in it: it compiles for the JVM (its tests) and Android (the
 * app), and the web terminal will take it as is. It speaks the engine's types (`:chart-core`) and
 * nothing else; saving scripts and running them on a chart is `:core:script`'s job.
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
        namespace = "com.coinepro.namascript"
        compileSdk = 36
        minSdk = 26
    }
    sourceSets {
        commonMain.dependencies { api(project(":chart-core")) }
        jvmTest.dependencies { implementation(libs.junit) }
    }
}

tasks.register("testDebugUnitTest") {
    group = "verification"
    description = "Runs the JVM tests, under the name the Android modules use."
    dependsOn("jvmTest")
}
