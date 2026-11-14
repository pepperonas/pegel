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

    @Test
    fun `the service is a special-use foreground service with its subtype declared`() {
        val service = Regex("""<service[\s\S]*?</service>""").find(manifest)!!.value
        assertTrue(service.contains("android:name=\".service.PegelService\""))
        assertTrue(service.contains("android:foregroundServiceType=\"specialUse\""))
        assertTrue(service.contains("android:exported=\"false\""))
        assertTrue(
            Regex("""android:name="android\.app\.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"\s+android:value="equalizer"""")
                .containsMatchIn(service)
        )
    }

    @Test
    fun `players can reach the session receiver`() {
        val receiver = Regex("""<receiver\s+android:name="\.service\.SessionReceiver"[\s\S]*?</receiver>""").find(manifest)!!.value
        assertTrue(receiver.contains("android:exported=\"true\""))
        assertTrue(receiver.contains("android.media.action.OPEN_AUDIO_EFFECT_CONTROL_SESSION"))
        assertTrue(receiver.contains("android.media.action.CLOSE_AUDIO_EFFECT_CONTROL_SESSION"))
    }

    @Test
    fun `Pegel starts again after a reboot`() {
        val receiver = Regex("""<receiver\s+android:name="\.service\.BootReceiver"[\s\S]*?</receiver>""").find(manifest)!!.value
        assertTrue(receiver.contains("android.intent.action.BOOT_COMPLETED"))
    }

    @Test
    fun `Pegel registers as the system equalizer panel`() {
        val activity = Regex("""<activity[\s\S]*?</activity>""").find(manifest)!!.value
        assertTrue(activity.contains("android.media.action.DISPLAY_AUDIO_EFFECT_CONTROL_PANEL"))
        assertTrue(activity.contains("android.intent.category.DEFAULT"))
        assertTrue(activity.contains("android.intent.category.CATEGORY_CONTENT_MUSIC"))
    }

    @Test
    fun `shared profile files go through a private file provider`() {
        val provider = Regex("""<provider\s+android:name="androidx\.core\.content\.FileProvider"[\s\S]*?</provider>""").find(manifest)!!.value
        assertTrue(provider.contains("android:authorities=\"io.celox.pegel.files\""))
        assertTrue(provider.contains("android:exported=\"false\""))
        assertTrue(provider.contains("android:grantUriPermissions=\"true\""))
        val paths = File(File("src/main/res").takeIf { it.exists() } ?: File("app/src/main/res"), "xml/file_paths.xml").readText()
        // only the dedicated cache folder — never the whole cache, files or database directory
        assertEquals(1, Regex("<(cache|files|external|root)[a-z-]*-path").findAll(paths).count())
        assertTrue(paths.contains("""<cache-path name="shared" path="shared/" />"""))
    }
}
