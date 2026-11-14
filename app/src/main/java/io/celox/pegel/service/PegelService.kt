package io.celox.pegel.service

import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.audiofx.AudioEffect
import android.os.Build
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import io.celox.pegel.R
import io.celox.pegel.audio.EngineState
import io.celox.pegel.audio.Target
import io.celox.pegel.audio.planFor
import io.celox.pegel.container
import io.celox.pegel.data.Profile
import io.celox.pegel.device.AutoApply
import io.celox.pegel.device.DeviceKind
import io.celox.pegel.device.DeviceWatcher
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Keeps the effect engine alive while the master switch is on. The service owns no state: it
 * mirrors the repository into the engine and the engine into its notification.
 */
class PegelService : LifecycleService() {

    private val sessionReceiver = SessionReceiver()
    private var receiverRegistered = false

    override fun onCreate() {
        super.onCreate()
        val repo = container.repository
        val engine = container.engine

        Notifications.ensureChannels(this)
        ServiceCompat.startForeground(
            this, Notifications.ID_STATUS,
            Notifications.status(this, getString(R.string.app_name), getString(R.string.notif_starting)),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )

        // Players that broadcast without a target only reach a receiver registered at runtime.
        ContextCompat.registerReceiver(
            this, sessionReceiver,
            IntentFilter().apply {
                addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
                addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
            },
            ContextCompat.RECEIVER_EXPORTED,
        )
        receiverRegistered = true

        lifecycleScope.launch {
            var frame = repo.settings.value.frameDurationMs
            combine(repo.state, repo.settings) { state, settings -> state to settings }.collect { (state, settings) ->
                if (!settings.enabled) {
                    stopSelf()
                    return@collect
                }
                // The block size is fixed when a chain is created: rebuild the chains to change it.
                if (settings.frameDurationMs != frame) {
                    frame = settings.frameDurationMs
                    engine.setEnabled(false)
                }
                engine.setMode(settings.mode)
                engine.setPlan(planFor(state, settings.tuning))
                engine.setEnabled(true)
            }
        }

        lifecycleScope.launch {
            val autoApply = AutoApply(repo)
            DeviceWatcher(this@PegelService).active.collect { device ->
                val applied = autoApply.onActiveDevice(device) ?: return@collect
                val label = if (device!!.kind == DeviceKind.SPEAKER) getString(R.string.device_speaker)
                else device.name.trim().ifEmpty { getString(R.string.device_unnamed) }
                Notifications.postApplied(this@PegelService, label, applied.name)
            }
        }

        lifecycleScope.launch {
            combine(engine.state, repo.activeProfile, repo.modified) { e, p, m -> Triple(e, p, m) }.collect { (e, p, m) ->
                Notifications.updateStatus(this@PegelService, targetText(e), profileText(p, m))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_TURN_OFF) {
            lifecycleScope.launch { container.repository.updateSettings { it.copy(enabled = false) } }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (receiverRegistered) unregisterReceiver(sessionReceiver)
        container.engine.shutdown()
        super.onDestroy()
    }

    private fun targetText(e: EngineState): String = when (val t = e.target) {
        Target.GlobalMix -> getString(R.string.target_global)
        is Target.Players -> t.packages.joinToString { appLabel(it) }
        Target.None -> getString(if (e.failed) R.string.target_failed else R.string.target_waiting)
    }

    private fun profileText(p: Profile?, modified: Boolean): String = when {
        p == null -> getString(R.string.profile_unsaved)
        modified -> getString(R.string.profile_modified, p.name)
        else -> p.name
    }

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) {
        pkg
    }

    companion object {
        const val ACTION_TURN_OFF = "io.celox.pegel.action.TURN_OFF"
    }
}
