package io.celox.pegel.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseSourceTest {

    @Test
    fun `the newest release tag comes from GitHub, and nothing else is asked`() {
        val asked = mutableListOf<String>()
        val source = ReleaseSource { url -> asked += url; """{"tag_name":"v1.2.0","name":"Pegel 1.2.0"}""" }
        assertEquals("v1.2.0", source.latestVersion())
        assertEquals(listOf(ReleaseSource.GITHUB_URL), asked)
    }

    @Test
    fun `no usable answer yields null`() {
        assertNull(ReleaseSource { null }.latestVersion())
        assertNull(ReleaseSource { "<html>502</html>" }.latestVersion())
        assertNull(ReleaseSource { """{"tag_name":""}""" }.latestVersion())
        assertNull(ReleaseSource { """{"message":"API rate limit exceeded"}""" }.latestVersion())
    }

    @Test
    fun `all endpoints belong to this app`() {
        assertEquals("https://api.github.com/repos/pepperonas/pegel/releases/latest", ReleaseSource.GITHUB_URL)
        assertEquals("https://github.com/pepperonas/pegel/releases/latest", ReleaseSource.DOWNLOAD_URL)
    }
}
