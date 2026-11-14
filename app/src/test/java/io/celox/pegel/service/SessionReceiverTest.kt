package io.celox.pegel.service

import android.content.Intent
import android.media.audiofx.AudioEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SessionReceiverTest {

    private fun intent(action: String, session: Int? = 42, pkg: String? = "a.b") = Intent(action).apply {
        session?.let { putExtra(AudioEffect.EXTRA_AUDIO_SESSION, it) }
        pkg?.let { putExtra(AudioEffect.EXTRA_PACKAGE_NAME, it) }
    }

    @Test
    fun `a player opening its session`() {
        assertEquals(
            SessionEvent(open = true, sessionId = 42, packageName = "a.b"),
            parseSessionIntent(intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION))
        )
    }

    @Test
    fun `a player closing its session`() {
        assertEquals(
            SessionEvent(open = false, sessionId = 42, packageName = "a.b"),
            parseSessionIntent(intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION))
        )
    }

    @Test
    fun `a player opening Pegel through its equalizer button announces its session too`() {
        assertEquals(
            SessionEvent(open = true, sessionId = 42, packageName = "a.b"),
            parseSessionIntent(intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL))
        )
    }

    @Test
    fun `without a usable session there is no event`() {
        val open = AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION
        assertNull(parseSessionIntent(intent(open, session = null)))
        assertNull(parseSessionIntent(intent(open, session = 0)))
        assertNull(parseSessionIntent(intent(open, session = -1)))
    }

    @Test
    fun `other broadcasts are ignored`() {
        assertNull(parseSessionIntent(intent(Intent.ACTION_BOOT_COMPLETED)))
        assertNull(parseSessionIntent(Intent()))
    }

    @Test
    fun `a missing package name does not lose the session`() {
        val e = parseSessionIntent(intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION, pkg = null))
        assertEquals(SessionEvent(true, 42, "?"), e)
    }

    // Reviewer finding: the activity replayed a player's "Equalizer" intent on every rotation and
    // every return from recents, re-opening a session the player had long closed.
    @Test
    fun `a launch intent is acted on once, not on every recreation`() {
        assertEquals(true, isFreshLaunch(restored = false, flags = 0))
        assertEquals(false, isFreshLaunch(restored = true, flags = 0))
        assertEquals(false, isFreshLaunch(restored = false, flags = Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY))
        assertEquals(true, isFreshLaunch(restored = false, flags = Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
