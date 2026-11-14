package io.celox.pegel.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import io.celox.pegel.MainActivity
import io.celox.pegel.R

object Notifications {
    const val CHANNEL_STATUS = "status"
    const val CHANNEL_ATTENTION = "attention"
    const val ID_STATUS = 1
    const val ID_ATTENTION = 2
    const val ID_APPLIED = 3

    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)

    fun ensureChannels(context: Context) {
        manager(context).createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_STATUS, context.getString(R.string.notif_channel_status), NotificationManager.IMPORTANCE_LOW
                ).apply { setShowBadge(false) },
                NotificationChannel(
                    CHANNEL_ATTENTION, context.getString(R.string.notif_channel_attention),
                    NotificationManager.IMPORTANCE_DEFAULT
                ),
            )
        )
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** The ongoing notification of the running service: where Pegel is attached, and which profile. */
    fun status(context: Context, title: String, text: String): Notification {
        val off = PendingIntent.getService(
            context, 1,
            Intent(context, PegelService::class.java).setAction(PegelService.ACTION_TURN_OFF),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_stat_pegel)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, context.getString(R.string.notif_action_off), off)
            .build()
    }

    fun updateStatus(context: Context, title: String, text: String) =
        notifySafely(context, ID_STATUS, status(context, title, text))

    /** A profile was applied because an output device became active. Disappears on its own. */
    fun postApplied(context: Context, device: String, profile: String) {
        val n = NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_stat_pegel)
            .setContentTitle(context.getString(R.string.notif_applied_title, profile))
            .setContentText(context.getString(R.string.notif_applied_text, device))
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .setTimeoutAfter(8_000)
            .build()
        notifySafely(context, ID_APPLIED, n)
    }

    /** Pegel is switched on but Android would not let the service start from the background. */
    fun postAttention(context: Context) {
        ensureChannels(context)
        val n = NotificationCompat.Builder(context, CHANNEL_ATTENTION)
            .setSmallIcon(R.drawable.ic_stat_pegel)
            .setContentTitle(context.getString(R.string.notif_attention_title))
            .setContentText(context.getString(R.string.notif_attention_text))
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build()
        notifySafely(context, ID_ATTENTION, n)
    }

    fun cancelAttention(context: Context) = manager(context).cancel(ID_ATTENTION)

    /** Without the notification permission (Android 13+) posting is simply a no-op. */
    private fun notifySafely(context: Context, id: Int, n: Notification) {
        try {
            manager(context).notify(id, n)
        } catch (_: SecurityException) {
        }
    }
}
