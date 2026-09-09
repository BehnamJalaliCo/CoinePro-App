package com.coinepro.app

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * «Mobile and tablet must be one»: the layout may differ by window class, the state may not.
 *
 * The plan's rule (§4.3) is that a composable which exists only for the expanded window must not
 * reach for a controller or store the compact layout does not have — because that is how a tablet
 * grows a feature the phone lacks, or a phone keeps state the tablet forgets. Two source-level
 * checks, on the tree as committed:
 *
 * 1. Every `*Workbench.kt` (the tablet-only arrangements of a page) names only the controllers and
 *    stores its `*Screen.kt` sibling names. The workbench is a layout of the screen's state, not a
 *    second screen.
 * 2. No controller or store class is referenced *only* from files that read the window class.
 *    A state holder every reference to which sits in an adaptive file is a state holder the
 *    phone cannot reach.
 */
class LayoutParityArchitectureTest {

    private val root = File("..").canonicalFile

    private val sources: List<File> by lazy {
        listOf("app", "feature", "core", "chart").flatMap { dir ->
            File(root, dir).walkTopDown()
                .filter { it.isFile && it.extension == "kt" && "/src/main/" in it.path.replace('\\', '/') }
                .toList()
        }
    }

    @Test
    fun `a workbench names no state its screen does not`() {
        val failures = mutableListOf<String>()
        for (workbench in sources.filter { it.name.endsWith("Workbench.kt") }) {
            val screen = File(workbench.parentFile, workbench.name.removeSuffix("Workbench.kt") + "Screen.kt")
            if (!screen.exists()) continue
            val extra = stateNames(workbench.readText()) - stateNames(screen.readText())
            if (extra.isNotEmpty()) failures += "${workbench.name} reaches ${extra.sorted()} which ${screen.name} does not"
        }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun `no state holder is reachable only from an adaptive layout`() {
        val adaptive = sources.filter { file -> ADAPTIVE_MARKERS.any { it in file.readText() } }.toSet()
        val declared = sources.flatMap { file ->
            DECLARATION.findAll(file.readText()).map { it.groupValues[1] to file }.toList()
        }
        val failures = mutableListOf<String>()
        for ((name, home) in declared) {
            val referrers = sources.filter { it != home && Regex("\\b$name\\b").containsMatchIn(it.readText()) }
            if (referrers.isNotEmpty() && referrers.all { it in adaptive }) {
                failures += "$name is referenced only from ${referrers.map { it.name }}"
            }
        }
        assertEquals(emptyList<String>(), failures)
    }

    private fun stateNames(source: String): Set<String> =
        STATE_NAME.findAll(source).map { it.value }.toSet()

    private companion object {
        /** A type that holds state for a screen, by the repository's naming. */
        val STATE_NAME = Regex("""\b[A-Z][A-Za-z]*(?:Controller|Store|ViewModel)\b""")
        val DECLARATION = Regex("""\bclass\s+([A-Z][A-Za-z]*(?:Controller|Store|ViewModel))\b""")

        /** What a file reads when it lays out differently by window. */
        val ADAPTIVE_MARKERS = listOf(
            "showsTwoPanes",
            "showsNavigationRail",
            "prefersLabelledRail",
            "ChartWorkbenchColumns",
            "CoineProListDetail(",
            "CoineProWindowSize.EXPANDED",
        )
    }
}
