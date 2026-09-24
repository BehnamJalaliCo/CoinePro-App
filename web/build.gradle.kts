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
    id("org.jetbrains.kotlin.plugin.serialization")
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
            implementation(project(":namascript"))
            // What the phone's shared sources import that has a browser build of its own.
            implementation("org.jetbrains.compose.material3.adaptive:adaptive:1.2.0")
            implementation("org.jetbrains.compose.material3.adaptive:adaptive-layout:1.2.0")
            implementation("org.jetbrains.compose.material3.adaptive:adaptive-navigation:1.2.0")
            implementation("androidx.window:window-core:1.5.0")
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
            implementation(libs.coil.compose)
            implementation("io.coil-kt.coil3:coil-network-ktor3:${libs.versions.coil.get()}")
            implementation("io.ktor:ktor-client-js:3.1.3")
            implementation(libs.jetbrains.compose.foundation)
            implementation(libs.jetbrains.compose.ui)
            // The browser takes Material 3 1.9 — the phone's own Material version is untouched, since
            // this module is not on its classpath (see core/tokens for why the shared modules pin 1.8.2).
            implementation("org.jetbrains.compose.material3:material3:1.9.0")
            implementation(libs.kotlinx.coroutines.core)
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            implementation("org.jetbrains.androidx.navigation:navigation-compose:2.9.1")
            implementation("org.jetbrains.compose.material3:material3-adaptive-navigation-suite:1.9.0")
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
 * Every string the phone has, in both languages, as two JSON files the page reads at start.
 *
 * The web says what the phone says, word for word: the source is the same `values/` (English) and
 * `values-fa/` (Persian) XML every module already ships, merged the way Android merges resources —
 * one flat namespace, `:app` overriding a library. So a sentence fixed for the phone is fixed for
 * the browser in the same commit, and the house lint that guards those files guards these too.
 */
val exportTerminalStrings by tasks.registering {
    val roots = rootProject.subprojects.map { it.projectDir.resolve("src/main/res") }.filter { it.isDirectory }
        // `:app` last, so it wins — Android's own precedence for the application module.
        .sortedBy { if (it.path.contains("/app/src/")) 1 else 0 }
    val sources = roots.flatMap { root -> listOf("values", "values-fa").map { root.resolve(it) } }.filter { it.isDirectory }
    inputs.files(sources.map { dir -> fileTree(dir) { include("*.xml") } })
    val glyphFile = file("tools/glyphs.json")
    inputs.file(glyphFile)
    val out = layout.buildDirectory.dir("generated/terminal-strings")
    outputs.dir(out)
    doLast {
        fun unescape(raw: String): String {
            var text = raw.trim()
            if (text.length >= 2 && text.startsWith("\"") && text.endsWith("\"")) text = text.substring(1, text.length - 1)
            val b = StringBuilder()
            var i = 0
            while (i < text.length) {
                val c = text[i]
                if (c == '\\' && i + 1 < text.length) {
                    val n = text[i + 1]
                    when (n) {
                        'n' -> { b.append('\n'); i += 2 }
                        't' -> { b.append('\t'); i += 2 }
                        'u' -> { b.append(text.substring(i + 2, i + 6).toInt(16).toChar()); i += 6 }
                        else -> { b.append(n); i += 2 }
                    }
                } else { b.append(c); i++ }
            }
            return b.toString()
        }
        fun json(value: String): String = buildString {
            append('"')
            value.forEach { c ->
                when {
                    c == '"' -> append("\\\"")
                    c == '\\' -> append("\\\\")
                    c == '\n' -> append("\\n")
                    c == '\t' -> append("\\t")
                    c.code < 0x20 -> append("\\u" + c.code.toString(16).padStart(4, '0'))
                    else -> append(c)
                }
            }
            append('"')
        }
        // Characters IRANYekanX has no glyph for, written as ones it has — see tools/glyphs.json.
        @Suppress("UNCHECKED_CAST")
        val glyphs = (groovy.json.JsonSlurper().parse(glyphFile) as Map<String, Any>)["map"] as Map<String, String>
        fun glyphed(text: String): String = glyphs.entries.fold(text) { acc, (missing, present) -> acc.replace(missing, present) }
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
        for ((folder, language) in listOf("values" to "en", "values-fa" to "fa")) {
            val strings = linkedMapOf<String, String>()
            val plurals = linkedMapOf<String, Map<String, String>>()
            roots.map { it.resolve(folder) }.filter { it.isDirectory }.forEach { dir ->
                dir.listFiles { f -> f.extension == "xml" }!!.sortedBy { it.name }.forEach { file ->
                    val doc = factory.newDocumentBuilder().parse(file)
                    val nodes = doc.documentElement.childNodes
                    for (index in 0 until nodes.length) {
                        val node = nodes.item(index) as? org.w3c.dom.Element ?: continue
                        val name = node.getAttribute("name")
                        when (node.tagName) {
                            "string" -> strings[name] = glyphed(unescape(node.textContent))
                            "plurals" -> {
                                val items = node.getElementsByTagName("item")
                                plurals[name] = (0 until items.length).associate { k ->
                                    val item = items.item(k) as org.w3c.dom.Element
                                    item.getAttribute("quantity") to glyphed(unescape(item.textContent))
                                }
                            }
                        }
                    }
                }
            }
            val file = out.get().file("strings/$language.json").asFile
            file.parentFile.mkdirs()
            file.writeText(buildString {
                append("{\"strings\":{")
                append(strings.entries.joinToString(",") { json(it.key) + ":" + json(it.value) })
                append("},\"plurals\":{")
                append(plurals.entries.joinToString(",") { (key, forms) ->
                    json(key) + ":{" + forms.entries.joinToString(",") { json(it.key) + ":" + json(it.value) } + "}"
                })
                append("}}")
            })
        }
    }
}

kotlin.sourceSets.named("wasmJsMain") { resources.srcDir(exportTerminalStrings) }

/**
 * The `R` classes the phone's sources expect, generated for the browser.
 *
 * On Android `R.string.chart_title` is an integer the Android Gradle plugin invents. Here it is an
 * integer this task invents, one per resource across every module, and `webResourceNames` maps it
 * back to the resource's name — which is the key into the exported string tables or the file under
 * `drawable/`. The phone's code keeps writing `R.string.x` and `painterResource(R.drawable.y)`;
 * only what those calls resolve to changes. Same namespaces as the phone (`namespace` in each
 * module's build script), same non-transitive shape.
 */
val generateWebResources by tasks.registering {
    // Its action reads helpers declared in this script, which the configuration cache cannot store.
    notCompatibleWithConfigurationCache("reads script-level helpers")
    val modules = rootProject.subprojects
        .map { it.projectDir }
        .filter { it.resolve("src/main/res").isDirectory && it.resolve("build.gradle.kts").isFile }
    inputs.files(modules.map { it.resolve("build.gradle.kts") })
    inputs.files(modules.map { dir -> fileTree(dir.resolve("src/main/res")) { include("values/*.xml", "drawable*/**", "font/**") } })
    val out = layout.buildDirectory.dir("generated/web-resources/kotlin")
    outputs.dir(out)
    doLast {
        val keywords = setOf("as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in",
            "interface", "is", "null", "object", "package", "return", "super", "this", "throw", "true", "try",
            "typealias", "typeof", "val", "var", "when", "while")
        fun ident(name: String) = if (name in keywords) "`$name`" else name
        val names = ArrayList<String>()
        val values = LinkedHashMap<String, String>()
        val persianValues = LinkedHashMap<String, String>()
        fun id(kind: String, name: String): Int {
            names += "$kind/$name"
            return 0x7f000000 + names.size - 1
        }
        val root = out.get().asFile
        root.deleteRecursively()
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
        for (module in modules) {
            val namespace = Regex("namespace\\s*=\\s*\"([^\"]+)\"").find(module.resolve("build.gradle.kts").readText())
                ?.groupValues?.get(1) ?: continue
            val res = module.resolve("src/main/res")
            val kinds = sortedMapOf<String, MutableSet<String>>()
            val moduleValues = HashMap<String, String>()
            val modulePersian = HashMap<String, String>()
            res.resolve("values-fa").listFiles { f -> f.extension == "xml" }?.forEach { file ->
                val nodes = factory.newDocumentBuilder().parse(file).documentElement.childNodes
                for (i in 0 until nodes.length) {
                    val node = nodes.item(i) as? org.w3c.dom.Element ?: continue
                    if (node.tagName in setOf("color", "bool", "dimen", "integer")) {
                        modulePersian["${node.tagName}/${node.getAttribute("name").replace('.', '_')}"] = node.textContent.trim()
                    }
                }
            }
            fun add(kind: String, name: String) { kinds.getOrPut(kind) { sortedSetOf() } += name }
            res.resolve("values").listFiles { f -> f.extension == "xml" }?.forEach { file ->
                val nodes = factory.newDocumentBuilder().parse(file).documentElement.childNodes
                for (i in 0 until nodes.length) {
                    val node = nodes.item(i) as? org.w3c.dom.Element ?: continue
                    val name = node.getAttribute("name").replace('.', '_')
                    when (node.tagName) {
                        "string" -> add("string", name)
                        "plurals" -> add("plurals", name)
                        "color", "bool", "dimen", "integer" -> {
                            add(node.tagName, name)
                            moduleValues["${node.tagName}/$name"] = node.textContent.trim()
                        }
                    }
                }
            }
            res.listFiles { f -> f.isDirectory && f.name.startsWith("drawable") }?.forEach { dir ->
                dir.listFiles()?.forEach { add("drawable", it.nameWithoutExtension) }
            }
            res.resolve("font").listFiles()?.forEach { add("font", it.nameWithoutExtension) }
            if (kinds.isEmpty()) continue
            val file = root.resolve(namespace.replace('.', '/') + "/R.kt")
            file.parentFile.mkdirs()
            file.writeText(buildString {
                appendLine("// Generated by :web:generateWebResources from ${module.relativeTo(rootProject.projectDir)}/src/main/res. Do not edit.")
                appendLine("package $namespace")
                appendLine()
                appendLine("@Suppress(\"ClassName\", \"ObjectPropertyName\")")
                appendLine("object R {")
                for ((kind, entries) in kinds) {
                    appendLine("    object $kind {")
                    for (name in entries) {
                        val value = id(kind, name)
                        moduleValues["$kind/$name"]?.let { values[value.toString()] = it }
                        modulePersian["$kind/$name"]?.let { persianValues[value.toString()] = it }
                        appendLine("        const val ${ident(name)}: Int = $value")
                    }
                    appendLine("    }")
                }
                appendLine("}")
            })
        }
        val table = root.resolve("com/coinepro/web/res/WebResourceNames.kt")
        table.parentFile.mkdirs()
        table.writeText(buildString {
            appendLine("// Generated by :web:generateWebResources. Do not edit.")
            appendLine("package com.coinepro.web.res")
            appendLine()
            appendLine("/** `kind/name` for every generated resource id, indexed by `id - 0x7f000000`. */")
            appendLine("val webResourceNames: Array<String> = arrayOf(")
            names.forEach { appendLine("    \"$it\",") }
            appendLine(")")
            appendLine()
            appendLine("/** The literal value of every `color`, `bool`, `dimen` and `integer` resource, by id. */")
            appendLine("val webResourceValues: Map<Int, String> = mapOf(")
            values.forEach { (k, v) -> appendLine("    $k to \"${v.replace("\\", "\\\\").replace("\"", "\\\"")}\",") }
            appendLine(")")
            appendLine()
            appendLine("/** The same, where `values-fa/` says otherwise — the Persian wordmark is a lockup, the Latin one is not. */")
            appendLine("val webResourceValuesFa: Map<Int, String> = mapOf(")
            persianValues.forEach { (k, v) -> appendLine("    $k to \"${v.replace("\\", "\\\\").replace("\"", "\\\"")}\",") }
            appendLine(")")
        })
    }
}

kotlin.sourceSets.named("wasmJsMain") { kotlin.srcDir(generateWebResources) }

/**
 * `com.coinepro.app.BuildConfig`, for the browser: the release build's values, from the same
 * sources the phone's release reads them — `version.properties` for the version, the production
 * defaults in `app/build.gradle.kts` for the two API bases. What a page cannot have is empty, as an
 * unconfigured field is on the phone: no Firebase project, no signer to check, no pins (a page
 * cannot pin — the browser verifies certificates itself), no admin credential, no terminal link.
 */
val generateWebBuildConfig by tasks.registering {
    val versionFile = rootProject.file("version.properties")
    inputs.file(versionFile)
    val out = layout.buildDirectory.dir("generated/web-buildconfig/kotlin")
    outputs.dir(out)
    doLast {
        val props = versionFile.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") && '=' in it }
            .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
        val major = props.getValue("MAJOR").toInt()
        val minor = props.getValue("MINOR").toInt()
        val patch = props.getValue("PATCH").toInt()
        val pre = props["PRE_RELEASE"].orEmpty()
        val name = "$major.$minor.$patch" + if (pre.isEmpty()) "" else "-$pre"
        val code = major * 10_000_000 + minor * 100_000 + patch * 1_000
        val file = out.get().file("com/coinepro/app/BuildConfig.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |// Generated by :web:generateWebBuildConfig. Do not edit.
            |package com.coinepro.app
            |
            |object BuildConfig {
            |    const val DEBUG: Boolean = false
            |    const val APPLICATION_ID: String = "com.coinepro.app"
            |    const val BUILD_TYPE: String = "release"
            |    const val VERSION_NAME: String = "$name"
            |    const val VERSION_CODE: Int = $code
            |    const val BUILD_ENVIRONMENT: String = "production"
            |    const val API_BASE_URL: String = "https://coineprofx.com/api/"
            |    const val TRADEYAR_API_BASE_URL: String = "https://tradeyar.trade-future.ir/"
            |    const val TERMINAL_URL: String = ""
            |    const val ADMIN_PANEL: Boolean = false
            |    const val ADMIN_USERNAME: String = ""
            |    const val ADMIN_SALT: String = ""
            |    const val ADMIN_HASH: String = ""
            |    const val ADMIN_ITERATIONS: Int = 100000
            |    const val FIREBASE_PROJECT_ID: String = ""
            |    const val FIREBASE_APPLICATION_ID: String = ""
            |    const val FIREBASE_API_KEY: String = ""
            |    const val FIREBASE_SENDER_ID: String = ""
            |    const val EXPECTED_SIGNERS: String = ""
            |    const val PLAY_INTEGRITY_PROJECT: Long = 0L
            |    const val CERTIFICATE_PINS: String = ""
            |    const val CERTIFICATE_PINS_UNTIL: Long = 0L
            |    const val DIRECT_THIRD_PARTY_FEEDS: Boolean = true
            |}
            |""".trimMargin(),
        )
    }
}

kotlin.sourceSets.named("wasmJsMain") { kotlin.srcDir(generateWebBuildConfig) }

/**
 * Where each drawable is in the bundle, so the page asks for a picture once, at the right path,
 * instead of trying folders until one answers: `{"en": {name: path}, "fa": {name: path}}`, each
 * name resolved as Android resolves it on a high-density screen in that language.
 */
val indexTerminalDrawables by tasks.registering {
    val resDirs = rootProject.subprojects.map { it.projectDir.resolve("src/main/res") }.filter { it.isDirectory }
    inputs.files(resDirs.map { dir -> fileTree(dir) { include("drawable*/**") } })
    val out = layout.buildDirectory.dir("generated/terminal-drawables")
    outputs.dir(out)
    doLast {
        val densities = listOf("xxxhdpi", "xxhdpi", "xhdpi", "hdpi", "mdpi")
        fun resolve(persian: Boolean): Map<String, String> {
            val order = listOf("drawable") + (if (persian) densities.map { "drawable-fa-$it" } else emptyList()) + densities.map { "drawable-$it" }
            val found = sortedMapOf<String, String>()
            for (folder in order.reversed()) {
                resDirs.forEach { res ->
                    res.resolve(folder).listFiles { f -> f.extension in setOf("xml", "webp", "png") }?.forEach { file ->
                        found[file.nameWithoutExtension] = "$folder/${file.name}"
                    }
                }
            }
            return found
        }
        fun json(map: Map<String, String>) = map.entries.joinToString(",", "{", "}") { "\"${it.key}\":\"${it.value}\"" }
        val file = out.get().file("drawable-index.json").asFile
        file.parentFile.mkdirs()
        file.writeText("{\"en\":${json(resolve(false))},\"fa\":${json(resolve(true))}}")
    }
}

kotlin.sourceSets.named("wasmJsMain") { resources.srcDir(indexTerminalDrawables) }

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
    // The phone's drawables, unchanged — read at draw time by `VectorDrawables.kt` — from every
    // module that ships any, as Android merges them into one namespace.
    rootProject.subprojects.map { it.projectDir.resolve("src/main/res") }.filter { it.isDirectory }.forEach { res ->
        // Qualified folders keep their qualifier: `drawable-fa-xxxhdpi/` is the Persian wordmark.
        res.listFiles { f -> f.isDirectory && f.name.startsWith("drawable") }?.forEach { dir ->
            from(dir) { into(dir.name.replace(Regex("-v\\d+$"), "")); include("*.xml", "*.webp", "*.png") }
        }
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    // The phone's `assets/`: the help catalogue and its pictures, the legal pages.
    from(rootProject.file("core/help/src/main/assets")) { into("assets") }
    from(rootProject.file("feature/legal/src/main/assets")) { into("assets") }
    into(layout.buildDirectory.dir("terminal"))
}

/**
 * The phone's own sources, compiled for the browser.
 *
 * Not copied and not forked: these are the directories the Android modules build from, added to
 * this module's Wasm compilation. Where one of them reaches for something only Android has —
 * `R.string`, `java.time`, a `Context` — the browser gets a stand-in under the same name from
 * `src/shims/`, or a browser implementation of the file from `src/wasmJsMain/`. The phone's build is
 * not touched by any of it; it never sees this module.
 *
 * A file excluded here is listed with the reason, and its browser twin lives beside the shims.
 */
val sharedSources = listOf(
    // The chart's pickers, rail and icons draw Android drawables by `R`; the browser compiles them
    // here, beside the generated `R`, rather than in `:chart-ui`'s Wasm target.
    "chart/ui@androidMain",
    "core/model",
    "core/common",
    "core/symbols",
    "core/designsystem",
    "core/navigation",
    "core/network",
    "core/datastore",
    "core/database",
    "core/security",
    "core/diagnostics",
    "core/marketdata",
    "core/orderbook",
    "core/webhook",
    "core/membership",
    "core/chartevents",
    "core/script",
    "core/help",
    "core/signals",
    "core/notifications",
    "core/execution",
    "core/portfolio",
    "core/academy",
    "core/community",
    "core/aisignal",
    "core/aivision",
    "core/aiassistant",
    "core/marketintel",
    "core/announcements",
    "core/account",
    "core/auth",
    "core/guest",
    "core/journal",
    "core/papertrade",
    "core/backtest",
    "core/export",
    "core/watchlistsync",
    "core/update",
    "feature/admin",
    "feature/auth",
    "feature/home",
    "feature/script",
    "feature/screener",
    "feature/search",
    "feature/chart",
    "feature/portfolio",
    "feature/academy",
    "feature/terminal",
    "feature/signals",
    "feature/signal-detail",
    "feature/connections",
    "feature/execution",
    "feature/profile",
    "feature/kyc",
    "feature/account",
    "feature/guest",
    "feature/membership",
    "feature/notifications",
    "feature/alerts",
    "feature/dom",
    "feature/heatmap",
    "feature/journal",
    "feature/papertrade",
    "feature/ai",
    "feature/ai-vision",
    "feature/ai-assistant",
    "feature/news",
    "feature/calendar",
    "feature/explore",
    "feature/community",
    "feature/tools",
    "feature/activity",
    "feature/menu",
    "feature/legal",
    "app",
)

/**
 * Files of those modules the browser builds its own twin of, beside the shims, because they draw or
 * store with something only Android has. Each is listed with where its twin lives.
 */
val replacedFiles = listOf(
    // The Android entry points. The page's own is web/src/wasmJsMain/.../app/WebApp.kt, which composes
    // the same tree `MainActivity.setContent` does, from the same `WebGraph` the phone's module makes.
    "com/coinepro/app/MainActivity.kt",
    "com/coinepro/app/CoineProApplication.kt",
    // Home-screen widgets are an Android launcher surface; a page has no launcher to put one on.
    "com/coinepro/app/widget/MarketsWidget.kt",
    "com/coinepro/app/widget/SymbolWidget.kt",
    "com/coinepro/app/widget/WidgetConfigureActivity.kt",
    "com/coinepro/app/widget/SymbolWidgetConfigureActivity.kt",
    "com/coinepro/app/widget/WidgetSnapshotBridge.kt",
    // Credential Manager → Google Identity Services: web/src/wasmJsMain/.../app/auth/GoogleSignIn.web.kt
    "com/coinepro/app/auth/GoogleSignIn.kt",
    // `:chart-ui`'s Android actuals; the browser's are `:chart-ui`'s own `wasmJsMain`.
    "com/coinepro/core/chart/ChartUiPlatform.android.kt",
    "com/coinepro/core/chart/ChartStrokePredictor.kt",
    "com/coinepro/core/chart/ChartFrameRate.kt",
    // android.graphics + StaticLayout → Skia paragraph: web/src/wasmJsMain/.../designsystem/ShareCard.web.kt
    "com/coinepro/core/designsystem/ShareCard.kt",
    "com/coinepro/core/designsystem/ShareImage.kt",
    // No title on a phone → the tab's title: web/src/wasmJsMain/.../designsystem/WindowTitle.web.kt
    "com/coinepro/core/designsystem/WindowTitle.kt",
)

/**
 * The shared sources as the browser compiles them: copied into the build directory by
 * `tools/share_sources.py`, which makes the three edits a JVM-free build needs and no others —
 * wire classes marked `@Serializable`, Retrofit services implemented without reflection, blocking
 * calls made suspending. Read the script's header for exactly what it touches.
 */
val shareSources by tasks.registering(Exec::class) {
    val script = file("tools/share_sources.py")
    val out = layout.buildDirectory.dir("generated/shared/kotlin")
    inputs.file(script)
    inputs.files(sharedSources.flatMap { entry ->
        val module = entry.substringBefore('@')
        val sets = if ('@' in entry) listOf(entry.substringAfter('@')) else listOf("main", "release")
        sets.map { fileTree(rootProject.file("$module/src/$it/kotlin")) { include("**/*.kt") } }
    })
    outputs.dir(out)
    commandLine(
        "python3", script.absolutePath, rootProject.projectDir.absolutePath, out.get().asFile.absolutePath,
        sharedSources.joinToString(","), replacedFiles.joinToString(","),
    )
}

kotlin.sourceSets.named("wasmJsMain") {
    kotlin.srcDir("src/shims/kotlin")
    kotlin.srcDir(files(layout.buildDirectory.dir("generated/shared/kotlin")).builtBy(shareSources))
}

kotlin.compilerOptions {
    optIn.add("kotlinx.serialization.ExperimentalSerializationApi")
    freeCompilerArgs.add("-Xexpect-actual-classes")
}

/**
 * Per-package stand-ins for what the JVM's standard library adds to Kotlin: `String.format`,
 * `lowercase(Locale)`, `synchronized`, `@Volatile`, `System` and `Math`.
 *
 * On the JVM these arrive through default imports the Wasm compiler does not have, and an extension
 * cannot be imported into a file that never asked for it. So each package the shared sources
 * declare gets a small file of internal declarations under the same package name, which a file in
 * that package sees without an import — exactly as it saw the JVM's.
 */
val generateJvmCompat by tasks.registering {
    // Its action reads helpers declared in this script, which the configuration cache cannot store.
    notCompatibleWithConfigurationCache("reads script-level helpers")
    val roots = sharedSources.flatMap { entry ->
        val module = entry.substringBefore('@')
        if ('@' in entry) listOf(rootProject.file("$module/src/${entry.substringAfter('@')}/kotlin"))
        else listOf(rootProject.file("$module/src/main/kotlin"), rootProject.file("$module/src/release/kotlin"))
    }.filter { it.isDirectory }
    inputs.files(roots.map { fileTree(it) { include("**/*.kt") } })
    val out = layout.buildDirectory.dir("generated/jvm-compat/kotlin")
    outputs.dir(out)
    doLast {
        val packages = roots.flatMap { root ->
            root.walkTopDown().filter { it.extension == "kt" }.mapNotNull { file ->
                Regex("^package\\s+([\\w.]+)", RegexOption.MULTILINE).find(file.readText())?.groupValues?.get(1)
            }.toList()
        }.toSortedSet()
        val dir = out.get().asFile
        dir.deleteRecursively()
        packages.forEach { pkg ->
            val file = dir.resolve(pkg.replace('.', '/') + "/WebJvmCompat.kt")
            file.parentFile.mkdirs()
            file.writeText(JVM_COMPAT_TEMPLATE.replace("__PACKAGE__", pkg))
        }
    }
}

val JVM_COMPAT_TEMPLATE = """
// Generated by :web:generateJvmCompat. Do not edit. See web/build.gradle.kts.
@file:Suppress("unused", "NOTHING_TO_INLINE", "EXTENSION_SHADOWED_BY_MEMBER", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")
package __PACKAGE__

import androidx.savedstate.read
import kotlin.math.pow
import kotlin.math.ulp

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
internal annotation class Volatile
@PublishedApi internal inline fun String.lowercase(locale: java.util.Locale?): String = this.lowercase()
@PublishedApi internal inline fun String.uppercase(locale: java.util.Locale?): String = this.uppercase()
@PublishedApi internal inline fun String.toLowerCase(locale: java.util.Locale?): String = this.lowercase()
@PublishedApi internal inline fun String.toUpperCase(locale: java.util.Locale?): String = this.uppercase()
@PublishedApi internal inline fun String.capitalize(locale: java.util.Locale): String = this.replaceFirstChar { it.uppercase() }
@PublishedApi internal inline fun Char.titlecase(locale: java.util.Locale): String = this.uppercase()
@PublishedApi internal inline fun String.Companion.format(format: String, vararg args: Any?): String = com.coinepro.web.jvm.formatJvm(format, *args)
@PublishedApi internal inline fun String.Companion.format(locale: java.util.Locale?, format: String, vararg args: Any?): String = com.coinepro.web.jvm.formatJvm(format, *args)
@PublishedApi internal inline fun String.format(vararg args: Any?): String = com.coinepro.web.jvm.formatJvm(this, *args)
@PublishedApi internal inline fun String.format(locale: java.util.Locale?, vararg args: Any?): String = com.coinepro.web.jvm.formatJvm(this, *args)
@PublishedApi internal inline fun <R> synchronized(lock: Any, block: () -> R): R = block()
@PublishedApi internal fun LongArray.binarySearch(element: Long, fromIndex: Int = 0, toIndex: Int = size): Int {
    var lo = fromIndex; var hi = toIndex - 1
    while (lo <= hi) { val mid = (lo + hi) ushr 1; val v = this[mid]; if (v < element) lo = mid + 1 else if (v > element) hi = mid - 1 else return mid }
    return -(lo + 1)
}
@PublishedApi internal fun IntArray.binarySearch(element: Int, fromIndex: Int = 0, toIndex: Int = size): Int {
    var lo = fromIndex; var hi = toIndex - 1
    while (lo <= hi) { val mid = (lo + hi) ushr 1; val v = this[mid]; if (v < element) lo = mid + 1 else if (v > element) hi = mid - 1 else return mid }
    return -(lo + 1)
}
@PublishedApi internal fun DoubleArray.binarySearch(element: Double, fromIndex: Int = 0, toIndex: Int = size): Int {
    var lo = fromIndex; var hi = toIndex - 1
    while (lo <= hi) { val mid = (lo + hi) ushr 1; val v = this[mid]; if (v < element) lo = mid + 1 else if (v > element) hi = mid - 1 else return mid }
    return -(lo + 1)
}
@Target(AnnotationTarget.TYPE, AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
internal annotation class JvmSuppressWildcards(val suppress: Boolean = true)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR)
internal annotation class JvmOverloads
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
internal annotation class JvmField
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
internal annotation class Transient
/** Navigation's arguments: a `SavedState` in the browser, read as the phone reads its `Bundle`. */
internal fun androidx.savedstate.SavedState.getString(key: String): String? = read { getStringOrNull(key) }
internal fun androidx.savedstate.SavedState.getLong(key: String): Long = read { getLongOrNull(key) ?: 0L }
internal fun androidx.savedstate.SavedState.getInt(key: String): Int = read { getIntOrNull(key) ?: 0 }
internal fun androidx.savedstate.SavedState.getBoolean(key: String): Boolean = read { getBooleanOrNull(key) ?: false }
@PublishedApi internal fun <K, V> MutableMap<K, V>.putIfAbsent(key: K, value: V): V? = this[key] ?: run { this[key] = value; null }
internal typealias Thread = com.coinepro.web.jvm.WebThread
internal typealias Runtime = com.coinepro.web.jvm.WebRuntime
internal typealias Runnable = com.coinepro.web.jvm.WebRunnable
internal typealias InterruptedException = kotlin.Exception
internal typealias CloneNotSupportedException = kotlin.Exception
@PublishedApi internal object Charsets {
    val UTF_8: java.nio.charset.Charset = java.nio.charset.StandardCharsets.UTF_8
    val US_ASCII: java.nio.charset.Charset = java.nio.charset.StandardCharsets.US_ASCII
    val ISO_8859_1: java.nio.charset.Charset = java.nio.charset.StandardCharsets.ISO_8859_1
}
@PublishedApi internal inline fun String.toByteArray(charset: java.nio.charset.Charset = java.nio.charset.StandardCharsets.UTF_8): ByteArray = this.encodeToByteArray()
@PublishedApi internal inline fun String(bytes: ByteArray, charset: java.nio.charset.Charset): String = bytes.decodeToString()
@PublishedApi internal inline fun String(bytes: ByteArray, offset: Int, length: Int, charset: java.nio.charset.Charset): String = bytes.decodeToString(offset, offset + length)
@PublishedApi internal inline fun String(chars: CharArray): String = chars.concatToString()
@PublishedApi internal inline fun ByteArray.toString(charset: java.nio.charset.Charset): String = this.decodeToString()
@PublishedApi internal inline fun java.io.InputStream.bufferedReader(charset: java.nio.charset.Charset): java.io.BufferedReader = this.bufferedReader()
@PublishedApi internal inline fun java.io.InputStream.reader(charset: java.nio.charset.Charset): java.io.Reader = this.reader()
@PublishedApi internal inline fun Throwable.printStackTrace(out: java.io.PrintWriter) = com.coinepro.web.jvm.printStackTraceTo(this, out)
internal inline val kotlinx.coroutines.Dispatchers.IO: kotlinx.coroutines.CoroutineDispatcher get() = kotlinx.coroutines.Dispatchers.Default
internal val <T : Any> kotlin.reflect.KClass<T>.java: kotlin.reflect.KClass<T> get() = this
internal val <T : Any> T.javaClass: kotlin.reflect.KClass<T> get() = @Suppress("UNCHECKED_CAST") (this::class as kotlin.reflect.KClass<T>)
@PublishedApi internal object System {
    fun currentTimeMillis(): Long = com.coinepro.web.jvm.nowMillisJs().toLong()
    fun nanoTime(): Long = com.coinepro.web.jvm.nanoTimeJs().toLong()
    fun lineSeparator(): String = "\n"
    fun getProperty(key: String): String? = null
    fun getProperty(key: String, fallback: String): String = fallback
    fun identityHashCode(o: Any?): Int = o.hashCode()
    fun arraycopy(src: Any, srcPos: Int, dest: Any, destPos: Int, length: Int) {
        when (src) {
            is ByteArray -> src.copyInto(dest as ByteArray, destPos, srcPos, srcPos + length)
            is IntArray -> src.copyInto(dest as IntArray, destPos, srcPos, srcPos + length)
            is LongArray -> src.copyInto(dest as LongArray, destPos, srcPos, srcPos + length)
            is DoubleArray -> src.copyInto(dest as DoubleArray, destPos, srcPos, srcPos + length)
            is FloatArray -> src.copyInto(dest as FloatArray, destPos, srcPos, srcPos + length)
            is CharArray -> src.copyInto(dest as CharArray, destPos, srcPos, srcPos + length)
            is Array<*> -> @Suppress("UNCHECKED_CAST") (src as Array<Any?>).copyInto(dest as Array<Any?>, destPos, srcPos, srcPos + length)
        }
    }
}
@PublishedApi internal object Math {
    const val PI: Double = kotlin.math.PI
    const val E: Double = kotlin.math.E
    fun abs(x: Double): Double = kotlin.math.abs(x)
    fun abs(x: Float): Float = kotlin.math.abs(x)
    fun abs(x: Int): Int = kotlin.math.abs(x)
    fun abs(x: Long): Long = kotlin.math.abs(x)
    fun max(a: Double, b: Double): Double = kotlin.math.max(a, b)
    fun max(a: Float, b: Float): Float = kotlin.math.max(a, b)
    fun max(a: Int, b: Int): Int = kotlin.math.max(a, b)
    fun max(a: Long, b: Long): Long = kotlin.math.max(a, b)
    fun min(a: Double, b: Double): Double = kotlin.math.min(a, b)
    fun min(a: Float, b: Float): Float = kotlin.math.min(a, b)
    fun min(a: Int, b: Int): Int = kotlin.math.min(a, b)
    fun min(a: Long, b: Long): Long = kotlin.math.min(a, b)
    fun round(x: Double): Long = kotlin.math.floor(x + 0.5).toLong()
    fun round(x: Float): Int = kotlin.math.floor(x + 0.5f).toInt()
    fun rint(x: Double): Double = kotlin.math.round(x)
    fun floor(x: Double): Double = kotlin.math.floor(x)
    fun ceil(x: Double): Double = kotlin.math.ceil(x)
    fun pow(a: Double, b: Double): Double = a.pow(b)
    fun sqrt(x: Double): Double = kotlin.math.sqrt(x)
    fun cbrt(x: Double): Double = kotlin.math.cbrt(x)
    fun log(x: Double): Double = kotlin.math.ln(x)
    fun log10(x: Double): Double = kotlin.math.log10(x)
    fun log1p(x: Double): Double = kotlin.math.ln1p(x)
    fun exp(x: Double): Double = kotlin.math.exp(x)
    fun expm1(x: Double): Double = kotlin.math.expm1(x)
    fun signum(x: Double): Double = kotlin.math.sign(x)
    fun signum(x: Float): Float = kotlin.math.sign(x)
    fun toRadians(x: Double): Double = x / 180.0 * kotlin.math.PI
    fun toDegrees(x: Double): Double = x * 180.0 / kotlin.math.PI
    fun sin(x: Double): Double = kotlin.math.sin(x)
    fun cos(x: Double): Double = kotlin.math.cos(x)
    fun tan(x: Double): Double = kotlin.math.tan(x)
    fun atan(x: Double): Double = kotlin.math.atan(x)
    fun atan2(y: Double, x: Double): Double = kotlin.math.atan2(y, x)
    fun hypot(x: Double, y: Double): Double = kotlin.math.hypot(x, y)
    fun floorDiv(a: Long, b: Long): Long = java.time.floorDivCompat(a, b)
    fun floorDiv(a: Int, b: Int): Int = java.time.floorDivCompat(a.toLong(), b.toLong()).toInt()
    fun floorMod(a: Long, b: Long): Long = a - java.time.floorDivCompat(a, b) * b
    fun floorMod(a: Int, b: Int): Int = (a - java.time.floorDivCompat(a.toLong(), b.toLong()) * b).toInt()
    fun random(): Double = kotlin.random.Random.nextDouble()
    fun ulp(x: Double): Double = x.ulp
    fun addExact(a: Long, b: Long): Long = a + b
    fun multiplyExact(a: Long, b: Long): Long = a * b
    fun toIntExact(a: Long): Int = a.toInt()
    fun clamp(v: Double, lo: Double, hi: Double): Double = v.coerceIn(lo, hi)
}
@PublishedApi internal object Character {
    fun isDigit(c: Char): Boolean = c.isDigit()
    fun isLetter(c: Char): Boolean = c.isLetter()
    fun isLetterOrDigit(c: Char): Boolean = c.isLetterOrDigit()
    fun isWhitespace(c: Char): Boolean = c.isWhitespace()
    fun isSpaceChar(c: Char): Boolean = c.isWhitespace()
    fun isUpperCase(c: Char): Boolean = c.isUpperCase()
    fun isLowerCase(c: Char): Boolean = c.isLowerCase()
    fun toUpperCase(c: Char): Char = c.uppercaseChar()
    fun toLowerCase(c: Char): Char = c.lowercaseChar()
    fun getNumericValue(c: Char): Int = c.digitToIntOrNull() ?: -1
    fun digit(c: Char, radix: Int): Int = c.digitToIntOrNull(radix) ?: -1
    fun isHighSurrogate(c: Char): Boolean = c.isHighSurrogate()
    fun isLowSurrogate(c: Char): Boolean = c.isLowSurrogate()
    fun getType(c: Char): Int = c.category.ordinal
}
"""

kotlin.sourceSets.named("wasmJsMain") { kotlin.srcDir(generateJvmCompat) }
