/*
 * :web — the Pro Chart terminal, in a browser, served at pro-chart.com/terminal/.
 *
 * Not a second implementation of anything: the chart is `:chart-ui`'s `CoineProChart`, drawn by the
 * same Kotlin that draws it on the phone, over `:chart-core`'s engine. What lives here is only what
 * a browser page needs around it — the relay client, the font loader, the address bar, the shell.
 *
 * Open and read-only by the owner's decision of 2026-09-21: no sign-in, no session, no gate.
 * See docs/web/TERMINAL_BUILD_PROMPT.md.
 *
 * `./gradlew :web:terminalBundle` writes the bundle to `web/build/terminal/`; that directory is what
 * the server puts at `site/terminal` and pre-compresses (docs/web/SERVER.md §6.2).
 */
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("terminal")
        browser {
            commonWebpackConfig { outputFileName = "terminal.js" }
        }
        binaries.executable()
    }
    sourceSets {
        wasmJsMain.dependencies {
            implementation(project(":chart-ui"))
            implementation(libs.jetbrains.compose.foundation)
            implementation(libs.jetbrains.compose.ui)
            implementation(libs.jetbrains.compose.material3)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}

// The one typeface, from the one place it lives. Copied at build time rather than checked in twice:
// `core/designsystem/src/main/res/font/` is the source, and a second copy is a copy that drifts.
val copyTerminalFonts by tasks.registering(Copy::class) {
    from(rootProject.file("core/designsystem/src/main/res/font")) {
        include("iranyekanx_*.ttf")
        into("fonts")
    }
    into(layout.buildDirectory.dir("generated/terminal-fonts"))
}

kotlin.sourceSets.named("wasmJsMain") { resources.srcDir(copyTerminalFonts) }

/**
 * The bundle the server serves, assembled from the compiler's own ES modules — no webpack.
 *
 * Kotlin/Wasm already emits browser-ready modules (`terminal.mjs` and its `.wasm`, shrunk by
 * Binaryen). What webpack would add is a bundling pass over three files and a dev server, and what
 * it would cost is the plugin's test tooling, whose Karma is fetched from a GitHub archive at build
 * time — a download that has nothing to do with the page and has failed builds on its own. So the
 * modules are shipped as they are, next to Skia's runtime (`skiko.mjs`/`skiko.wasm`) and the one npm
 * package the code imports by name (`@js-joda/core`, through kotlinx-datetime), which `index.html`
 * maps with an import map. Eleven files, four of them the typeface; every source is on the list below.
 */
val terminalBundle by tasks.registering(Sync::class) {
    group = "distribution"
    description = "Assembles the pro-chart.com/terminal/ bundle into build/terminal."
    val compiled = tasks.named("wasmJsProductionExecutableCompileSync")
    dependsOn(compiled, rootProject.tasks.named("kotlinWasmNpmInstall"), tasks.named("wasmJsProcessResources"))
    val modules = rootProject.layout.buildDirectory.dir("wasm/node_modules")
    from(layout.buildDirectory.dir("compileSync/wasmJs/main/productionExecutable/optimized")) {
        include("terminal.mjs", "terminal.uninstantiated.mjs", "terminal.wasm")
    }
    from(modules.map { it.dir("skiko-js-wasm-runtime") }) { include("skiko.mjs", "skiko.wasm") }
    from(modules.map { it.dir("@js-joda/core/dist") }) { include("js-joda.esm.js") }
    from(layout.buildDirectory.dir("processedResources/wasmJs/main"))
    into(layout.buildDirectory.dir("terminal"))
}
