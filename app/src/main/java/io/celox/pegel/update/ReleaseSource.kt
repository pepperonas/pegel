package io.celox.pegel.update

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** Where the newest published version comes from. Faked in tests. */
fun interface LatestVersionSource {
    /** The newest release tag, e.g. "v1.10.0", or null when no source answered usefully. */
    fun latestVersion(): String?
}

/** Where the installable file comes from. Faked in tests. */
interface ApkSource {
    /** The newest release file, or null when no source described one that can be verified. */
    fun latestApk(): ApkRelease?

    /** Writes [apk] to [target], resuming a partial file. Throws on any network or storage error. */
    fun download(apk: ApkRelease, target: File, onProgress: (have: Long, total: Long) -> Unit)
}

/**
 * Asks the product page first (its latest.json is mirrored from GitHub every 15 minutes, with no
 * API rate limit) and GitHub's public API only when the page is unreachable or answers garbage.
 * [fetch] returns the response body or null; the default is a plain GET with no identifiers — no
 * install id, no device data, nothing but the request itself.
 */
class ReleaseSource(
    private val fetch: (String) -> String? = ::httpGet,
) : LatestVersionSource, ApkSource {

    override fun latestVersion(): String? =
        fetch(SITE_URL)?.let(::versionFromSite)
            ?: fetch(GITHUB_URL)?.let(::versionFromGithub)

    override fun latestApk(): ApkRelease? =
        fetch(SITE_URL)?.let(ApkReleaseParser::fromSite)
            ?: fetch(GITHUB_URL)?.let(ApkReleaseParser::fromGithub)

    override fun download(apk: ApkRelease, target: File, onProgress: (Long, Long) -> Unit) {
        val plan = ResumePlan.of(target.length(), apk.size)
        if (plan.complete) return onProgress(apk.size, apk.size)
        if (plan.restart) target.delete()
        val conn = (URL(apk.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Pegel")
            plan.rangeFrom?.let { setRequestProperty("Range", "bytes=$it-") }
        }
        try {
            val resumed = conn.responseCode == HttpURLConnection.HTTP_PARTIAL
            if (!resumed && conn.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${conn.responseCode}")
            var have = if (resumed) plan.rangeFrom ?: 0L else 0L
            conn.inputStream.use { input ->
                FileOutputStream(target, resumed).use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        have += n
                        // More than announced is not this file; the checksum would refuse it anyway.
                        if (have > apk.size) throw IOException("longer than announced")
                        out.write(buf, 0, n)
                        onProgress(have, apk.size)
                    }
                }
            }
            if (have != apk.size) throw IOException("incomplete")
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val SITE_URL = "https://pegel.celox.io/latest.json"
        const val GITHUB_URL = "https://api.github.com/repos/pepperonas/pegel/releases/latest"
        const val DOWNLOAD_URL = "https://pegel.celox.io/download"

        private const val TIMEOUT_MS = 10_000
        private const val MAX_BODY = 64 * 1024

        fun versionFromSite(body: String): String? = stringField(body, "version")

        fun versionFromGithub(body: String): String? = stringField(body, "tag_name")

        private fun stringField(body: String, name: String): String? = try {
            (JSONObject(body).opt(name) as? String)?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }

        fun httpGet(url: String): String? {
            var conn: HttpURLConnection? = null
            return try {
                conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    instanceFollowRedirects = true
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "Pegel")
                }
                if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
                conn.inputStream.use { String(it.readCapped(MAX_BODY), Charsets.UTF_8) }
            } catch (_: Exception) {
                null
            } finally {
                conn?.disconnect()
            }
        }

        private fun InputStream.readCapped(max: Int): ByteArray {
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (out.size() < max) {
                val n = read(buf, 0, minOf(buf.size, max - out.size()))
                if (n < 0) break
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        }
    }
}

/** What to do with a file that may already be partly there. */
data class ResumePlan(val rangeFrom: Long?, val complete: Boolean, val restart: Boolean) {
    companion object {
        fun of(existing: Long, size: Long): ResumePlan = when {
            existing == size -> ResumePlan(null, complete = true, restart = false)
            existing > size -> ResumePlan(null, complete = false, restart = true)
            existing > 0 -> ResumePlan(existing, complete = false, restart = false)
            else -> ResumePlan(null, complete = false, restart = false)
        }
    }
}
