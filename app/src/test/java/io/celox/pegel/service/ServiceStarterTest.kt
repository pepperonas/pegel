package io.celox.pegel.service

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ServiceStarterTest {

    private val app: Context = ApplicationProvider.getApplicationContext()
    private val notifications = shadowOf(app.getSystemService(NotificationManager::class.java))

    /** Android 12+ refusing a foreground-service start from the background. */
    private class Refusing(base: Context, private val error: RuntimeException) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun startForegroundService(service: Intent?): ComponentName = throw error
    }

    @Test
    fun `switching on starts the service`() {
        assertEquals(StartResult.STARTED, ServiceStarter.sync(app, enabled = true))
        assertEquals(PegelService::class.java.name, shadowOf(app as android.app.Application).nextStartedService.component!!.className)
        assertTrue(notifications.allNotifications.isEmpty())
    }

    @Test
    fun `switching off stops it`() {
        assertEquals(StartResult.STOPPED, ServiceStarter.sync(app, enabled = false))
        assertEquals(PegelService::class.java.name, shadowOf(app as android.app.Application).nextStoppedService.component!!.className)
    }

    // Review focus 2
    @Test
    fun `a refused start becomes a notification instead of a crash`() {
        val result = ServiceStarter.sync(Refusing(app, IllegalStateException("not allowed")), enabled = true)
        assertEquals(StartResult.DENIED_NOTIFIED, result)
        val posted = notifications.allNotifications.single()
        assertEquals(Notifications.CHANNEL_ATTENTION, posted.channelId)
    }

    @Test
    fun `a security exception is handled the same way`() {
        assertEquals(
            StartResult.DENIED_NOTIFIED,
            ServiceStarter.sync(Refusing(app, SecurityException("no")), enabled = true)
        )
        assertEquals(1, notifications.allNotifications.size)
    }
}
