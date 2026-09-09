buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.0")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.google.services) apply false
}

/**
 * Nothing that is not an asset may sit in an `assets/` directory.
 *
 * `content.json.orig` shipped in a release once: a merge tool's leftover, packaged and downloaded
 * by every reader. `check-cross-phase-consistency.py` catches it in CI; this catches it on every
 * build, before the APK is assembled, so a developer's own `assembleRelease` refuses too.
 */
val checkStrayAssets by tasks.registering {
    group = "verification"
    description = "Fails when a *.orig, *.bak, *.rej or *.tmp file sits under any module's src/main/assets."
    // Plain files, resolved now: the configuration cache forbids a task body that reaches back
    // into a Project, so nothing below touches one.
    val roots = subprojects.map { it.projectDir.resolve("src/main/assets") }.filter { it.isDirectory }
    val repository = rootDir
    inputs.files(roots.map { fileTree(it) })
    doLast {
        val stray = roots.flatMap { root ->
            root.walkTopDown().filter { it.isFile && it.extension in setOf("orig", "bak", "rej", "tmp") }.toList()
        }
        check(stray.isEmpty()) { "Stray files under assets/: " + stray.joinToString { it.relativeTo(repository).path } }
    }
}

/**
 * The unqualified resource set is English.
 *
 * `values/` is what every device outside the two shipped languages falls back to, and since 4.52.0
 * it is English; Persian is `values-fa/`. A Persian word that lands in `values/` — a key added to
 * one file and not the other — would reach every such reader untranslated, so this fails the build
 * on any Arabic-script character in a translatable string there. `check-cross-phase-consistency.py`
 * runs the same rule in CI with the same allow-list; this one runs on every `assembleRelease`.
 */
val checkDefaultLocaleIsEnglish by tasks.registering {
    group = "verification"
    description = "Fails when a translatable string under any module's src/main/res/values/ contains Arabic script."
    val valuesDirs = subprojects.map { it.projectDir.resolve("src/main/res/values") }.filter { it.isDirectory }
    val repository = rootDir
    inputs.files(valuesDirs.map { fileTree(it) { include("*.xml") } })
    doLast {
        val arabic = Regex("[\\u0600-\\u06FF\\uFB50-\\uFDFF\\uFE70-\\uFEFF]")
        val entry = Regex("""<(string|item|plurals)\s+name="([^"]+)"([^>]*)>(.*?)</\1>""", RegexOption.DOT_MATCHES_ALL)
        // English strings that quote a Persian word on purpose. Keep in step with the Python gate.
        val quotingPersian = setOf("search_empty_hint")
        val offenders = valuesDirs.flatMap { dir ->
            dir.listFiles { file -> file.extension == "xml" }.orEmpty().flatMap { file ->
                entry.findAll(file.readText()).mapNotNull { match ->
                    val (_, name, attributes, body) = match.destructured
                    if ("translatable=\"false\"" in attributes || name in quotingPersian) return@mapNotNull null
                    if (arabic.containsMatchIn(body)) "${file.relativeTo(repository).path}: $name" else null
                }.toList()
            }
        }
        check(offenders.isEmpty()) {
            "Arabic script in the default (English) resource set — move the text to values-fa/:\n" + offenders.joinToString("\n")
        }
    }
}
