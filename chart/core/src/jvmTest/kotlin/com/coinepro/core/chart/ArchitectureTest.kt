package com.coinepro.core.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * `:chart-core` has no platform in it, and this is where that stops being a sentence in a
 * build file and becomes a failing test.
 *
 * The JVM target's compiler is the first line: an `android.*` import does not resolve there at
 * all. This test is the second, and the one that names the file: it reads `commonMain` as text
 * and refuses any import that would tie the engine to Android, to Compose, to the JDK, or to one
 * of the app's Android-only modules. The JDK is on the list deliberately — `java.time` compiles
 * on both targets today and would not on the web one, and the five things the engine needs from a
 * platform go through `ChartPlatform.kt`'s `expect` declarations instead.
 */
class ArchitectureTest {

    @Test
    fun `commonMain imports nothing from a platform`() {
        val offenders = commonMainSources().flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                val trimmed = line.trim()
                if (!trimmed.startsWith("import ")) return@mapIndexedNotNull null
                val target = trimmed.removePrefix("import ").substringBefore(" as ").trim()
                val forbidden = FORBIDDEN_PREFIXES.firstOrNull { target.startsWith(it) } ?: return@mapIndexedNotNull null
                "${file.name}:${index + 1}: import $target ($forbidden is not available on every target)"
            }
        }
        assertTrue(offenders.joinToString("\n", prefix = "\n"), offenders.isEmpty())
    }

    @Test
    fun `commonMain has no expect declaration outside the platform seam`() {
        // One file owns the seam so that the next target's author has one file to implement.
        val declaring = commonMainSources()
            .filter { file -> file.readLines().any { EXPECT.containsMatchIn(it) } }
            .map { it.name }
        assertEquals(listOf("ChartPlatform.kt"), declaring)
    }

    @Test
    fun `the engine is not empty and is where the build says it is`() {
        val files = commonMainSources()
        assertTrue("expected the engine's sources under commonMain, found ${files.size}", files.size >= 30)
    }

    private fun commonMainSources(): List<File> {
        val root = File("src/commonMain/kotlin")
        assertTrue("run from the module directory: ${root.absolutePath}", root.isDirectory)
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.sortedBy { it.path }.toList()
    }

    private companion object {
        val FORBIDDEN_PREFIXES = listOf(
            "android.",
            "androidx.",
            "java.",
            "javax.",
            "kotlinx.coroutines.android",
            "com.coinepro.core.common",
            "com.coinepro.core.designsystem",
            "com.coinepro.core.datastore",
            "com.coinepro.core.database",
            "com.coinepro.core.network",
        )
        val EXPECT = Regex("""^\s*(internal\s+|public\s+)?expect\s""")
    }
}
