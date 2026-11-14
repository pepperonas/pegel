package io.celox.pegel.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.celox.pegel.container

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // After a reboot, and after the app itself was updated (which kills the running service).
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val settings = context.container.repository.settings.value
        if (settings.enabled && settings.startOnBoot) ServiceStarter.sync(context, enabled = true)
    }
}
