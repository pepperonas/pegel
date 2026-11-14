package io.celox.pegel.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDate

const val FILE_AUTHORITY = "io.celox.pegel.files"
private const val MAX_FILE_BYTES = 2 * 1024 * 1024

/** A profile name as a file name: nothing that could be a path, never empty. */
fun shareFileName(profileName: String): String {
    val safe = profileName
        .map { if (it.isLetterOrDigit() || it in " _-()") it else '_' }
        .joinToString("")
        .trim(' ', '_', '.')
    return (safe.ifEmpty { "profile" }) + ".pegel"
}

fun backupFileName(date: LocalDate = LocalDate.now()): String = "pegel-backup-$date.json"

/** The text of a picked document, or null if it cannot be read (or is absurdly large). */
fun readDocument(context: Context, uri: Uri): String? = try {
    context.contentResolver.openInputStream(uri)?.use { input ->
        val bytes = input.readNBytesCompat(MAX_FILE_BYTES + 1)
        if (bytes.size > MAX_FILE_BYTES) null else bytes.decodeToString()
    }
} catch (_: Exception) {
    null
}

fun writeDocument(context: Context, uri: Uri, text: String): Boolean = try {
    // "wt": truncate — an existing longer file must not keep its tail.
    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } != null
} catch (_: Exception) {
    false
}

/** Writes [text] into the shared cache folder and returns a send intent for it. */
fun shareProfileIntent(context: Context, profileName: String, text: String): Intent {
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() } // one shared file at a time; old ones are garbage
    val file = File(dir, shareFileName(profileName)).apply { writeText(text) }
    val uri = FileProvider.getUriForFile(context, FILE_AUTHORITY, file)
    return Intent(Intent.ACTION_SEND)
        .setType("application/json")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, file.name)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}

private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(8192)
    while (out.size() < max) {
        val n = read(buf, 0, minOf(buf.size, max - out.size()))
        if (n < 0) break
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}
