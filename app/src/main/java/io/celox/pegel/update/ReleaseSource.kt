package io.celox.pegel.update

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** Where the newest published version comes from. Faked in tests. */
fun interface LatestVersionSource {
    /** The newest release tag, e.g. "v1.10.0", or null when no source answered usefully. */
    fun latestVersion(): String?
}

/**
 * Asks GitHub's public releases API. [fetch] returns the response body or null; the default is a
 * plain GET with no identifiers — no install id, no device data, nothing but the request itself.
 */
class ReleaseSource(
    private val fetch: (String) -> String? = ::httpGet,
) : LatestVersionSource {

    override fun latestVersion(): String? = fetch(GITHUB_URL)?.let(::versionFromGithub)

    companion object {
        const val GITHUB_URL = "https://api.github.com/repos/pepperonas/pegel/releases/latest"
        const val DOWNLOAD_URL = "https://github.com/pepperonas/pegel/releases/latest"

        private const val TIMEOUT_MS = 10_000
        private const val MAX_BODY = 64 * 1024

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
