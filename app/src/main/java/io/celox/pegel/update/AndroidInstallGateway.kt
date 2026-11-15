package io.celox.pegel.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import io.celox.pegel.container
import java.io.File

/** [InstallGateway] on Android's [PackageInstaller]: one session per update, result by broadcast. */
class AndroidInstallGateway(context: Context) : InstallGateway {
    private val context = context.applicationContext

    override fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    override fun install(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Once this app installed itself, later updates may go through without a prompt.
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val callback = PendingIntent.getBroadcast(
                context, sessionId,
                Intent(context, InstallResultReceiver::class.java).setPackage(context.packageName),
                // Mutable: the installer fills in the status extras.
                PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0),
            )
            // Lets the new version tell a self-update apart from a manual install afterwards.
            SelfUpdateMarker.set(context)
            session.commit(callback.intentSender)
        }
    }
}

/**
 * Receives the installer's status. [PackageInstaller.STATUS_PENDING_USER_ACTION] carries Android's
 * confirmation screen, which the app has to start; everything else is the final outcome.
 */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val installer = context.container.updateInstaller
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                intent.getParcelableExtra(Intent.EXTRA_INTENT)
            }
            if (confirm == null) return installer.onInstallResult(InstallOutcome.FAILED)
            runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure { installer.onInstallResult(InstallOutcome.FAILED) }
            return
        }
        if (outcomeOf(status) != InstallOutcome.SUCCESS) SelfUpdateMarker.clear(context)
        installer.onInstallResult(outcomeOf(status))
    }

    companion object {
        fun outcomeOf(status: Int): InstallOutcome = when (status) {
            PackageInstaller.STATUS_SUCCESS -> InstallOutcome.SUCCESS
            PackageInstaller.STATUS_FAILURE_ABORTED -> InstallOutcome.CANCELLED
            else -> InstallOutcome.FAILED
        }
    }
}

/** A one-shot flag: "the next package replacement is our own update". Plain prefs: read in a receiver. */
object SelfUpdateMarker {
    private const val PREFS = "pegel_self_update"
    private const val KEY = "pending"

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun set(c: Context) = prefs(c).edit().putBoolean(KEY, true).apply()

    fun clear(c: Context) = prefs(c).edit().remove(KEY).apply()

    fun consume(c: Context): Boolean {
        val pending = prefs(c).getBoolean(KEY, false)
        if (pending) clear(c)
        return pending
    }
}
