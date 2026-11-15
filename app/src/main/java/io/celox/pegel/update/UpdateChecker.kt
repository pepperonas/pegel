package io.celox.pegel.update

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.celox.pegel.BuildConfig
import io.celox.pegel.MainActivity
import io.celox.pegel.R

/** One check: ask the source, remember the finding, notify once per new version. */
object UpdateChecker {

    private const val CHANNEL = "updates"
    private const val NOTIFICATION_ID = 0x50E6E1

    fun check(context: Context, source: LatestVersionSource): UpdatePolicy.Outcome {
        if (!UpdateCheckStore.isEnabled(context)) return UpdatePolicy.Outcome.DISABLED
        val latest = source.latestVersion()
        val outcome = UpdatePolicy.decide(
            enabled = true,
            latest = latest,
            installed = BuildConfig.VERSION_NAME,
            notified = UpdateCheckStore.notifiedVersion(context)
        )
        if (outcome != UpdatePolicy.Outcome.FAILED && latest != null) {
            UpdateCheckStore.recordLatest(context, latest)
            UpdateCheckStore.recordCheck(context, System.currentTimeMillis())
        }
        // Only a notification that was actually shown counts as "told" — otherwise a user who
        // grants the permission later would never hear about this version. The banner shows it
        // either way.
        if (outcome == UpdatePolicy.Outcome.NOTIFY && latest != null && notify(context, latest)) {
            UpdateCheckStore.markNotified(context, latest)
        }
        return outcome
    }

    /** The newer version to offer in the app, or null. */
    fun bannerVersion(context: Context): String? = UpdatePolicy.bannerVersion(
        UpdateCheckStore.isEnabled(context),
        UpdateCheckStore.latestSeen(context),
        BuildConfig.VERSION_NAME
    )

    fun downloadIntent(): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(ReleaseSource.DOWNLOAD_URL))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Opens the download page; false when the phone has no app for it (no browser). */
    fun openDownload(context: Context): Boolean = try {
        context.startActivity(downloadIntent())
        true
    } catch (_: android.content.ActivityNotFoundException) {
        false
    }

    // mayNotify() is the permission check; lint cannot see through the helper.
    @SuppressLint("MissingPermission")
    private fun notify(context: Context, latest: String): Boolean {
        if (!mayNotify(context)) return false
        val nm = NotificationManagerCompat.from(context)
        ensureChannel(context)
        // Opens the app: the update card on the first screen installs it from there.
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_pegel)
            .setContentTitle(context.getString(R.string.update_notification_title, AppVersion.display(latest)))
            .setContentText(context.getString(R.string.update_notification_body))
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .build()
        return runCatching { nm.notify(NOTIFICATION_ID, n) }.isSuccess
    }

    /**
     * Android ends the running app when it replaces it, so after an in-app update the app simply
     * vanishes. Called in the new version, only after a self-update (see [SelfUpdateMarker]).
     */
    @SuppressLint("MissingPermission")
    fun notifyUpdated(context: Context) {
        if (!mayNotify(context)) return
        ensureChannel(context)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_pegel)
            .setContentTitle(context.getString(R.string.update_done_title, BuildConfig.VERSION_NAME))
            .setContentText(context.getString(R.string.update_done_body))
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n) }
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun mayNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL, context.getString(R.string.update_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
    }
}
