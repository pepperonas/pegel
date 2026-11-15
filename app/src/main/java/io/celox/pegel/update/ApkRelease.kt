package io.celox.pegel.update

import org.json.JSONObject

/** One installable release file, with the checksum it must match. */
data class ApkRelease(
    val version: String,
    val name: String,
    val url: String,
    val size: Long,
    val sha256: String,
)

/**
 * Reads the installable file out of the product page's `latest.json` or GitHub's release answer.
 * Pure (string in, value out), and strict: a file is only installable when it is this app's release
 * file, from one of this app's two hosts, with a size and a SHA-256 to verify it against.
 */
object ApkReleaseParser {

    /** The file name the release workflow produces. Anything else is not the app. */
    private val NAME = Regex("""^pegel-v[0-9][0-9A-Za-z.\-]*\.apk$""")
    private val SHA256 = Regex("""^[0-9a-f]{64}$""")
    private val PREFIXES = listOf(
        "https://pegel.celox.io/files/",
        "https://github.com/pepperonas/pegel/releases/download/",
    )

    fun fromSite(body: String): ApkRelease? = parse(body) { o ->
        release(o.optString("version"), o.optString("name"), o.optString("url"), o.optLong("size"), o.optString("sha256"))
    }

    /** GitHub computes the SHA-256 itself (`digest: "sha256:…"`); without it there is nothing to verify against. */
    fun fromGithub(body: String): ApkRelease? = parse(body) { o ->
        if (o.optBoolean("draft") || o.optBoolean("prerelease")) return@parse null
        val assets = o.optJSONArray("assets") ?: return@parse null
        val apk = (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }
            .singleOrNull { NAME.matches(it.optString("name")) } ?: return@parse null
        // No digest, or another algorithm, leaves a string that is not a SHA-256 — and release() refuses it.
        val digest = apk.optString("digest")
        release(
            o.optString("tag_name"), apk.optString("name"), apk.optString("browser_download_url"),
            apk.optLong("size"), digest.removePrefix("sha256:"),
        )
    }

    private inline fun parse(body: String, read: (JSONObject) -> ApkRelease?): ApkRelease? = try {
        read(JSONObject(body))
    } catch (_: Exception) {
        null
    }

    private fun release(version: String, name: String, url: String, size: Long, sha256: String): ApkRelease? {
        val sha = sha256.lowercase()
        val ok = AppVersion.parse(version) != null && NAME.matches(name) && size > 0 && SHA256.matches(sha) &&
            PREFIXES.any { url.startsWith(it) } && url.endsWith("/$name") && !url.contains("..")
        return if (ok) ApkRelease(version, name, url, size, sha) else null
    }
}
