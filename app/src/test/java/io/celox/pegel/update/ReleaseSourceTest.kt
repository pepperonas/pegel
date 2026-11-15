package io.celox.pegel.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseSourceTest {

    @Test
    fun `the newest version comes from the product page, and nothing else is asked`() {
        val asked = mutableListOf<String>()
        val source = ReleaseSource { url -> asked += url; """{"version":"v1.2.0","name":"pegel-v1.2.0.apk"}""" }
        assertEquals("v1.2.0", source.latestVersion())
        assertEquals(listOf(ReleaseSource.SITE_URL), asked)
    }

    @Test
    fun `GitHub answers when the page does not`() {
        val asked = mutableListOf<String>()
        val source = ReleaseSource { url ->
            asked += url
            if (url == ReleaseSource.SITE_URL) "<html>502</html>" else """{"tag_name":"v1.3.0"}"""
        }
        assertEquals("v1.3.0", source.latestVersion())
        assertEquals(listOf(ReleaseSource.SITE_URL, ReleaseSource.GITHUB_URL), asked)
    }

    @Test
    fun `no usable answer yields null`() {
        assertNull(ReleaseSource { null }.latestVersion())
        assertNull(ReleaseSource { "<html>502</html>" }.latestVersion())
        assertNull(ReleaseSource { """{"tag_name":"","version":""}""" }.latestVersion())
        assertNull(ReleaseSource { """{"message":"API rate limit exceeded"}""" }.latestVersion())
    }

    @Test
    fun `all endpoints belong to this app`() {
        assertEquals("https://pegel.celox.io/latest.json", ReleaseSource.SITE_URL)
        assertEquals("https://api.github.com/repos/pepperonas/pegel/releases/latest", ReleaseSource.GITHUB_URL)
        assertEquals("https://pegel.celox.io/download", ReleaseSource.DOWNLOAD_URL)
    }
}
