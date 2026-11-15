package io.celox.pegel.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Starts and stops the checks. Off means no work is enqueued — nothing can reach the network. */
object UpdateScheduler {
    const val PERIODIC_WORK = "pegel-update-check"
    const val IMMEDIATE_WORK = "pegel-update-check-now"

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun setEnabled(context: Context, enabled: Boolean) {
        UpdateCheckStore.setEnabled(context, enabled)
        val wm = WorkManager.getInstance(context)
        if (enabled) {
            enqueuePeriodic(context)
            checkNow(context)
        } else {
            wm.cancelUniqueWork(PERIODIC_WORK)
            wm.cancelUniqueWork(IMMEDIATE_WORK)
            UpdateCheckStore.clearFindings(context)
        }
    }

    /** "Jetzt prüfen" — only ever runs with the switch on (the worker checks again). */
    fun checkNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<UpdateCheckWorker>().setConstraints(network).build(),
        )
    }

    /** App start: keeps the daily check alive unless it was switched off, and asks now if the last answer is old. */
    fun ensureScheduled(context: Context, nowMs: Long = System.currentTimeMillis()) {
        val enabled = UpdateCheckStore.isEnabled(context)
        if (UpdatePolicy.scheduleOnStart(enabled)) enqueuePeriodic(context)
        if (UpdatePolicy.checkOnStart(enabled, UpdateCheckStore.lastCheckAt(context), nowMs)) {
            // KEEP: a check that is already waiting for a connection is not replaced by another.
            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<UpdateCheckWorker>().setConstraints(network).build(),
            )
        }
    }

    private fun enqueuePeriodic(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            // First periodic run a day out: switching on already enqueues an immediate check, and
            // two checks racing each other could both announce the same version.
            PeriodicWorkRequestBuilder<UpdateCheckWorker>(24, TimeUnit.HOURS)
                .setConstraints(network)
                .setInitialDelay(24, TimeUnit.HOURS)
                .build(),
        )
    }
}

class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // A failed check is not retried early — the next daily run is soon enough.
        runCatching { UpdateChecker.check(applicationContext, ReleaseSource()) }
        Result.success()
    }
}
