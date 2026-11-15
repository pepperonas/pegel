package io.celox.pegel.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.celox.pegel.container
import io.celox.pegel.update.SelfUpdateMarker
import io.celox.pegel.update.UpdateChecker

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // After a reboot, and after the app itself was updated (which kills the running service).
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED && SelfUpdateMarker.consume(context)) {
            UpdateChecker.notifyUpdated(context)
        }
        val settings = context.container.repository.settings.value
        if (settings.enabled && settings.startOnBoot) ServiceStarter.sync(context, enabled = true)
    }
}
