package io.celox.pegel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The About section paints the launcher layers with painterResource, which only accepts vector
 * drawables and bitmaps — a `<shape>` background crashed the Settings screen on first open.
 */
class LauncherIconTest {

    private val res = File("src/main/res").takeIf { it.exists() } ?: File("app/src/main/res")

    private fun root(path: String) =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(res, path)).documentElement.tagName

    @Test
    fun `every launcher layer is a vector drawable`() {
        for (layer in listOf("background", "foreground", "monochrome")) {
            assertEquals("ic_launcher_$layer", "vector", root("drawable/ic_launcher_$layer.xml"))
        }
    }

    @Test
    fun `the adaptive icon has a themed (monochrome) layer`() {
        for (name in listOf("ic_launcher", "ic_launcher_round")) {
            val xml = File(res, "mipmap-anydpi-v26/$name.xml").readText()
            assertTrue(name, xml.contains("<monochrome"))
        }
    }
}
