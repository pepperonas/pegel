package io.celox.pegel.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

enum class StartResult { STARTED, STOPPED, DENIED_NOTIFIED }

object ServiceStarter {
    /**
     * Brings the service in line with the master switch. Android 12+ may refuse to start a
     * foreground service while the app is in the background (a session broadcast is no exemption);
     * then the user gets a notification to tap instead of a crash.
     */
    fun sync(context: Context, enabled: Boolean): StartResult {
        val intent = Intent(context, PegelService::class.java)
        if (!enabled) {
            context.stopService(intent)
            return StartResult.STOPPED
        }
        return try {
            ContextCompat.startForegroundService(context, intent)
            Notifications.cancelAttention(context)
            StartResult.STARTED
        } catch (e: IllegalStateException) { // ForegroundServiceStartNotAllowedException
            Notifications.postAttention(context)
            StartResult.DENIED_NOTIFIED
        } catch (e: SecurityException) {
            Notifications.postAttention(context)
            StartResult.DENIED_NOTIFIED
        }
    }
}
