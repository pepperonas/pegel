package io.celox.pegel.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApkReleaseTest {

    private val sha = "3cc76a91ab3bfb991530d4cf84df9dc277171a134f1db1cfa65a091989c44b46"

    private fun site(
        version: String = "v1.2.0",
        name: String = "pegel-v1.2.0.apk",
        url: String = "https://pegel.celox.io/files/pegel-v1.2.0.apk",
        size: Long = 5_000_000,
        sha256: String = sha,
    ) = """{"version":"$version","published":"2026-11-15T10:00:00Z","name":"$name","url":"$url","size":$size,"sha256":"$sha256","assets":[]}"""

    private fun github(
        digest: String? = "sha256:$sha",
        extra: String = "",
        name: String = "pegel-v1.2.0.apk",
    ) = """{"tag_name":"v1.2.0"$extra,"assets":[
        {"name":"SHA256SUMS.txt","size":90,"browser_download_url":"https://github.com/pepperonas/pegel/releases/download/v1.2.0/SHA256SUMS.txt"},
        {"name":"$name","size":5000000,${digest?.let { "\"digest\":\"$it\"," } ?: ""}
         "browser_download_url":"https://github.com/pepperonas/pegel/releases/download/v1.2.0/$name"}]}"""

    @Test
    fun `the product page describes the installable file`() {
        assertEquals(
            ApkRelease("v1.2.0", "pegel-v1.2.0.apk", "https://pegel.celox.io/files/pegel-v1.2.0.apk", 5_000_000, sha),
            ApkReleaseParser.fromSite(site()),
        )
    }

    @Test
    fun `a checksum in upper case is accepted and normalised`() {
        assertEquals(sha, ApkReleaseParser.fromSite(site(sha256 = sha.uppercase()))?.sha256)
    }

    @Test
    fun `only this app's file from this app's hosts is installable`() {
        assertNull("foreign host", ApkReleaseParser.fromSite(site(url = "https://evil.example/files/pegel-v1.2.0.apk")))
        assertNull("look-alike host", ApkReleaseParser.fromSite(site(url = "https://pegel.celox.io.evil.example/files/pegel-v1.2.0.apk")))
        assertNull("plain http", ApkReleaseParser.fromSite(site(url = "http://pegel.celox.io/files/pegel-v1.2.0.apk")))
        assertNull("another file name", ApkReleaseParser.fromSite(site(name = "other.apk", url = "https://pegel.celox.io/files/other.apk")))
        assertNull("url and name disagree", ApkReleaseParser.fromSite(site(url = "https://pegel.celox.io/files/pegel-v9.apk")))
        assertNull("path tricks", ApkReleaseParser.fromSite(site(url = "https://pegel.celox.io/files/../x/pegel-v1.2.0.apk")))
    }

    @Test
    fun `nothing to verify against means nothing to install`() {
        assertNull(ApkReleaseParser.fromSite(site(sha256 = "")))
        assertNull(ApkReleaseParser.fromSite(site(sha256 = "abc")))
        assertNull(ApkReleaseParser.fromSite(site(size = 0)))
        assertNull(ApkReleaseParser.fromSite(site(version = "")))
        assertNull(ApkReleaseParser.fromSite("<html>502</html>"))
        assertNull(ApkReleaseParser.fromGithub(github(digest = null)))
        assertNull(ApkReleaseParser.fromGithub(github(digest = "md5:abc")))
    }

    @Test
    fun `GitHub's release answer works as a fallback`() {
        assertEquals(
            ApkRelease("v1.2.0", "pegel-v1.2.0.apk", "https://github.com/pepperonas/pegel/releases/download/v1.2.0/pegel-v1.2.0.apk", 5_000_000, sha),
            ApkReleaseParser.fromGithub(github()),
        )
    }

    @Test
    fun `drafts and pre-releases are not offered`() {
        assertNull(ApkReleaseParser.fromGithub(github(extra = ""","draft":true""")))
        assertNull(ApkReleaseParser.fromGithub(github(extra = ""","prerelease":true""")))
    }

    @Test
    fun `the page is asked first and GitHub only when it has no answer`() {
        val asked = mutableListOf<String>()
        val both = ReleaseSource { url -> asked += url; if (url == ReleaseSource.SITE_URL) site() else github() }
        assertEquals("https://pegel.celox.io/files/pegel-v1.2.0.apk", both.latestApk()?.url)
        assertEquals(listOf(ReleaseSource.SITE_URL), asked)

        asked.clear()
        val pageDown = ReleaseSource { url -> asked += url; if (url == ReleaseSource.SITE_URL) null else github() }
        assertEquals("https://github.com/pepperonas/pegel/releases/download/v1.2.0/pegel-v1.2.0.apk", pageDown.latestApk()?.url)
        assertEquals(listOf(ReleaseSource.SITE_URL, ReleaseSource.GITHUB_URL), asked)

        assertNull(ReleaseSource { null }.latestApk())
    }
}
