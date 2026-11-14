package io.celox.pegel

import android.app.Application
import io.celox.pegel.update.UpdateScheduler

class PegelApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // Re-creates the daily update check only if it was switched on — off means no work at all.
        UpdateScheduler.ensureScheduled(this)
    }
}

val android.content.Context.container: AppContainer get() = (applicationContext as PegelApp).container
