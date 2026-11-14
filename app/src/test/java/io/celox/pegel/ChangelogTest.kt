package io.celox.pegel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The release workflow takes its notes from CHANGELOG.md (scripts/release-notes.sh). A version
 * without a section would publish an empty release page — so the build fails first, here. The
 * German changelog must tell the same story, version for version.
 */
class ChangelogTest {

    private val root = File("..").takeIf { File(it, "CHANGELOG.md").exists() } ?: File(".")
    private val files = listOf("CHANGELOG.md", "CHANGELOG.de.md").associateWith { File(root, it).readText() }

    private fun versions(text: String) =
        Regex("""^## \[([^\]]+)]""", RegexOption.MULTILINE).findAll(text).map { it.groupValues[1] }.toList()

    @Test
    fun `the running version has a section in both changelogs`() {
        val heading = "## [${BuildConfig.VERSION_NAME}] - "
        for ((name, text) in files) {
            assertTrue("$name has no section '$heading'", text.lines().any { it.startsWith(heading) })
        }
    }

    @Test
    fun `the newest section is the running version`() {
        for ((name, text) in files) assertEquals(name, BuildConfig.VERSION_NAME, versions(text).firstOrNull())
    }

    @Test
    fun `every section is dated`() {
        for ((name, text) in files) {
            val headings = text.lines().filter { it.startsWith("## [") }
            assertTrue(name, headings.isNotEmpty())
            headings.forEach { assertTrue("$name undated: $it", Regex("""^## \[[0-9.]+] - \d{4}-\d{2}-\d{2}$""").matches(it)) }
        }
    }

    @Test
    fun `both changelogs list the same versions on the same dates`() {
        val headings = files.mapValues { (_, text) -> text.lines().filter { it.startsWith("## [") } }
        assertEquals(headings["CHANGELOG.md"], headings["CHANGELOG.de.md"])
    }
}
