package io.celox.pegel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** What the READMEs claim must be what the project is. */
class ReadmeSyncTest {

    private val root = File("..").takeIf { File(it, "README.md").exists() } ?: File(".")
    private val readmes = listOf("README.md", "README.de.md").associateWith { File(root, it).readText() }
    private val manifest = File(root, "app/src/main/AndroidManifest.xml").readText()
    private val gradle = File(root, "app/build.gradle.kts").readText()

    private fun section(text: String, index: Int): String = text.split(Regex("(?m)^## ")).drop(1)[index]

    @Test
    fun `both READMEs have the same sections in the same order`() {
        val counts = readmes.mapValues { (_, t) -> Regex("(?m)^##+ ").findAll(t).count() }
        assertEquals(counts["README.md"], counts["README.de.md"])
        val levels = readmes.mapValues { (_, t) -> Regex("(?m)^(##+) ").findAll(t).map { it.groupValues[1] }.toList() }
        assertEquals(levels["README.md"], levels["README.de.md"])
    }

    @Test
    fun `the permissions table lists exactly what the manifest requests`() {
        val requested = Regex("""<uses-permission\s+android:name="android\.permission\.([A-Z_]+)"""")
            .findAll(manifest).map { it.groupValues[1] }.toSet()
        assertTrue(requested.isNotEmpty())
        for ((name, text) in readmes) {
            val index = Regex("(?m)^## ").findAll(text).toList().indexOfFirst {
                text.substring(it.range.first).startsWith("## Permissions") || text.substring(it.range.first).startsWith("## Berechtigungen")
            }
            assertTrue("$name has no permissions section", index >= 0)
            val listed = Regex("`([A-Z][A-Z_]+)`").findAll(section(text, index)).map { it.groupValues[1] }.toSet()
            assertEquals(name, requested, listed)
        }
    }

    @Test
    fun `the sdk badges match the build`() {
        val min = Regex("""minSdk\s*=\s*(\d+)""").find(gradle)!!.groupValues[1]
        val target = Regex("""targetSdk\s*=\s*(\d+)""").find(gradle)!!.groupValues[1]
        for ((name, text) in readmes) {
            assertTrue("$name minSdk badge", text.contains("badge/minSdk-$min%20"))
            assertTrue("$name targetSdk badge", text.contains("badge/targetSdk-$target-"))
        }
    }

    @Test
    fun `the READMEs promise as many presets as there are`() {
        val n = io.celox.pegel.audio.BUILT_IN_PRESETS.size
        for ((name, text) in readmes) assertTrue("$name should say $n presets", text.contains("$n presets") || text.contains("$n Presets"))
    }

    @Test
    fun `no Play Store link - Pegel is distributed through GitHub releases`() {
        for ((name, text) in readmes) assertFalse(name, text.contains("play.google.com"))
    }

    @Test
    fun `the release workflow refuses an unexpected signing certificate`() {
        val workflow = File(root, ".github/workflows/release.yml").readText()
        assertTrue(workflow.contains("vars.SIGNING_CERT_SHA256"))
        assertTrue(workflow.contains("Unexpected signing certificate"))
        assertTrue(workflow.contains("pegel-\${{ steps.ver.outputs.name }}.apk"))
    }

    /** The release job holds the signing key: a moved tag of a third-party action must not reach it. */
    @Test
    fun `third-party actions are pinned to a commit`() {
        val workflows = File(root, ".github/workflows").listFiles { f -> f.extension == "yml" }.orEmpty()
        assertTrue(workflows.size >= 2)
        for (w in workflows) {
            val uses = Regex("""uses:\s*(\S+)""").findAll(w.readText()).map { it.groupValues[1] }.toList()
            assertTrue(w.name, uses.isNotEmpty())
            for (u in uses.filterNot { it.startsWith("actions/") }) {
                assertTrue("${w.name}: $u is not pinned to a commit SHA", Regex(""".+@[0-9a-f]{40}""").matches(u))
            }
        }
    }

    @Test
    fun `the keystore is gone before anything is published`() {
        val workflow = File(root, ".github/workflows/release.yml").readText()
        val cleanup = workflow.indexOf("rm -f release.jks")
        val publish = workflow.indexOf("action-gh-release")
        assertTrue(cleanup >= 0 && publish >= 0)
        assertTrue("clean up the keystore before the publish step", cleanup < publish)
        assertTrue(workflow.indexOf("assembleRelease") < cleanup)
    }

    @Test
    fun `the release workflow has no trigger that cannot work`() {
        val workflow = File(root, ".github/workflows/release.yml").readText()
        // A manual run has no tag: GITHUB_REF_NAME is the branch, so it could only ever fail.
        assertFalse(workflow.contains("workflow_dispatch"))
        assertTrue(workflow.contains("tags:"))
    }
}
