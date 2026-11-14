package io.celox.pegel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What Pegel may do lives in the manifest. Pinned here so a new permission cannot slip in with an
 * unrelated change — "no ads, no tracking" is only credible while this list stays this short.
 */
class ManifestTest {

    private val manifest =
        (File("src/main/AndroidManifest.xml").takeIf { it.exists() } ?: File("app/src/main/AndroidManifest.xml"))
            .readText()

    private val permissions =
        Regex("""<uses-permission\s+android:name="([^"]+)"""").findAll(manifest).map { it.groupValues[1] }.toSet()

    @Test
    fun `exactly the expected permissions`() {
        assertEquals(
            setOf(
                "android.permission.MODIFY_AUDIO_SETTINGS",
                "android.permission.FOREGROUND_SERVICE",
                "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
                "android.permission.RECEIVE_BOOT_COMPLETED",
                "android.permission.POST_NOTIFICATIONS",
                "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
                // Only for the opt-in update check (off by default).
                "android.permission.INTERNET",
            ),
            permissions
        )
    }

    @Test
    fun `the per-app language setting is wired up`() {
        assertTrue(manifest.contains("android:localeConfig=\"@xml/locales_config\""))
    }
}
