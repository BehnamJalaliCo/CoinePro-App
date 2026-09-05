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
