package com.coinepro.core.script

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * `:namascript` has no platform in it. The JVM compiler is the first line of defence; this test
 * names the file when an import would tie the language to Android, to Compose, to the JDK or to
 * an Android-only module of the app. The language needs nothing from a platform: what it needs
 * from the world (a clock, a formatter) it takes from `:chart-core`'s seam.
 */
class ArchitectureTest {

    @Test
    fun `commonMain imports nothing from a platform`() {
        val root = File("src/commonMain/kotlin")
        assertTrue("run from the module directory: ${root.absolutePath}", root.isDirectory)
        val files = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.sortedBy { it.path }.toList()
        assertTrue("expected the language's sources under commonMain, found ${files.size}", files.size >= 12)
        val offenders = files.flatMap { file ->
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

    private companion object {
        val FORBIDDEN_PREFIXES = listOf(
            "android.",
            "androidx.",
            "java.",
            "javax.",
            "kotlinx.coroutines",
            "com.coinepro.core.common",
            "com.coinepro.core.designsystem",
            "com.coinepro.core.datastore",
            "com.coinepro.core.database",
        )
    }
}
