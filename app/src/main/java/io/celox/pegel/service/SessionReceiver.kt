package io.celox.pegel.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import io.celox.pegel.container

/** A music player telling the world that it started or stopped playing on an audio session. */
data class SessionEvent(val open: Boolean, val sessionId: Int, val packageName: String)

/** Null for anything that is not a usable session announcement. */
fun parseSessionIntent(intent: Intent): SessionEvent? {
    val open = when (intent.action) {
        AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION,
        // A player's "Equalizer" button: it hands over its session the same way.
        AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL -> true
        AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> false
        else -> return null
    }
    val session = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioEffect.ERROR_BAD_VALUE)
    if (session <= 0) return null
    return SessionEvent(open, session, intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME) ?: "?")
}

/**
 * Whether an activity should act on the intent it was started with. A recreated activity (rotation,
 * theme change) and one brought back from recents both still carry the original intent — replaying
 * a player's "Equalizer" intent then would re-open a session that player closed long ago.
 */
fun isFreshLaunch(restored: Boolean, flags: Int): Boolean =
    !restored && flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0

/** Routes a session announcement to the engine and makes sure the service is up if Pegel is on. */
fun handleSessionIntent(context: Context, intent: Intent) {
    val event = parseSessionIntent(intent) ?: return
    val container = context.container
    if (event.open) container.engine.onSessionOpened(event.sessionId, event.packageName)
    else container.engine.onSessionClosed(event.sessionId)
    if (container.repository.settings.value.enabled) ServiceStarter.sync(context, enabled = true)
}

/**
 * Declared in the manifest for players that address the broadcast to equalizer apps explicitly.
 * Players that send it without a target never reach a manifest receiver (Android 8+), which is why
 * [PegelService] registers the same receiver again at runtime.
 */
class SessionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = handleSessionIntent(context, intent)
}
