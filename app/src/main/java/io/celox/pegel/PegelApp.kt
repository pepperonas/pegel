package io.celox.pegel

import android.app.Application
import io.celox.pegel.update.UpdateScheduler

class PegelApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // Keeps the daily update check alive and asks now if the last answer is old — unless the
        // check was switched off: then no work is enqueued at all.
        // Wrapped: an update check must never keep the app from starting.
        runCatching { UpdateScheduler.ensureScheduled(this) }
    }
}

val android.content.Context.container: AppContainer get() = (applicationContext as PegelApp).container
