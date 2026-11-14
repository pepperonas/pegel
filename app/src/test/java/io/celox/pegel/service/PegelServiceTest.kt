package io.celox.pegel.service

import android.app.NotificationManager
import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import io.celox.pegel.container
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class PegelServiceTest {

    private val app: Context = ApplicationProvider.getApplicationContext()
    private val manager = app.getSystemService(NotificationManager::class.java)

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun `while running the service shows its status notification`() {
        runBlocking { app.container.repository.updateSettings { it.copy(enabled = true) } }
        val controller = Robolectric.buildService(PegelService::class.java).create()
        idle()
        assertTrue(shadowOf(manager).allNotifications.any { it.channelId == Notifications.CHANNEL_STATUS })
        controller.destroy()
    }

    @Test
    fun `a stopped service leaves no notification behind`() {
        runBlocking { app.container.repository.updateSettings { it.copy(enabled = true) } }
        val controller = Robolectric.buildService(PegelService::class.java).create()
        idle()
        controller.destroy()
        idle()
        assertEquals(emptyList<Int>(), shadowOf(manager).activeNotifications.map { it.id })
    }

    /**
     * Found on the emulator: after "off" an ongoing "Waiting for a player" notification stayed
     * behind. Shutting the engine down changes its state, and the collector that mirrors the state
     * into the notification re-posted it inline — after the service had given up its foreground
     * notification. Robolectric's service shadow removes notifications on destroy by itself, so the
     * test above cannot see that; what is pinned here is the order that fixes it.
     */
    @Test
    fun `the service stops mirroring the engine before it shuts the engine down`() {
        val file = java.io.File("src/main/java/io/celox/pegel/service/PegelService.kt").takeIf { it.exists() }
            ?: java.io.File("app/src/main/java/io/celox/pegel/service/PegelService.kt")
        val code = file.readLines().joinToString("\n") { it.substringBefore("//") } // comments quote the old order
        val onDestroy = code.substringAfter("override fun onDestroy()").substringBefore("super.onDestroy()")
        val stopMirror = onDestroy.indexOf("mirror?.cancel()")
        val shutdown = onDestroy.indexOf("engine.shutdown()")
        val cancel = onDestroy.indexOf(".cancel(Notifications.ID_STATUS)")
        assertTrue("onDestroy must cancel the mirror job", stopMirror >= 0)
        assertTrue("onDestroy must shut the engine down", shutdown >= 0)
        assertTrue("onDestroy must remove the status notification", cancel >= 0)
        assertTrue("mirror first, then shutdown", stopMirror < shutdown)
        assertTrue("the notification is removed after the last thing that could post it", shutdown < cancel)
        // and the job that is cancelled really is the one that posts the status
        assertTrue(Regex("""mirror = lifecycleScope\.launch \{\s*combine\(engine\.state""").containsMatchIn(code))
    }
}
