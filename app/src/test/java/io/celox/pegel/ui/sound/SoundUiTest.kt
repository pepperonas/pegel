package io.celox.pegel.ui.sound

import io.celox.pegel.audio.BUILT_IN_PRESETS
import io.celox.pegel.audio.ChainStatus
import io.celox.pegel.audio.EngineState
import io.celox.pegel.audio.Preset
import io.celox.pegel.audio.SoundState
import io.celox.pegel.audio.Target
import io.celox.pegel.data.Profile
import io.celox.pegel.data.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundUiTest {

    private val on = Settings(enabled = true)
    private val all = ChainStatus(dynamics = true, virtualizer = true, reverb = true)
    private val attached = EngineState(Target.GlobalMix, all, failed = false)
    private val mine = Preset("custom:7", "Mine", List(10) { 1f }, builtIn = false)

    private fun ui(
        settings: Settings = on, engine: EngineState = attached, active: Profile? = null, modified: Boolean = false,
        custom: List<Preset> = emptyList(), notifications: Boolean = true,
    ) = buildSoundUi(settings, SoundState(), custom, active, modified, engine, notifications)

    @Test
    fun `all is well when attached`() {
        val u = ui()
        assertNull(u.notice)
        assertTrue(u.virtualizerAvailable && u.reverbAvailable)
        assertEquals(Target.GlobalMix, u.target)
    }

    @Test
    fun `a refused attachment is the notice`() {
        assertEquals(Notice.ENGINE_FAILED, ui(engine = EngineState(Target.None, null, failed = true)).notice)
    }

    @Test
    fun `a chain without the equalizer part says so`() {
        val e = EngineState(Target.GlobalMix, all.copy(dynamics = false), failed = false)
        assertEquals(Notice.EQ_UNAVAILABLE, ui(engine = e).notice)
    }

    @Test
    fun `missing virtualizer or reverb lock their cards, not the screen`() {
        val u = ui(engine = EngineState(Target.GlobalMix, ChainStatus(true, virtualizer = false, reverb = false), false))
        assertNull(u.notice)
        assertFalse(u.virtualizerAvailable)
        assertFalse(u.reverbAvailable)
    }

    @Test
    fun `while Pegel is off nothing is reported as broken`() {
        val u = ui(settings = Settings(enabled = false), engine = EngineState(Target.None, null, failed = true), notifications = false)
        assertNull(u.notice)
        assertTrue(u.virtualizerAvailable && u.reverbAvailable)
    }

    @Test
    fun `a status left over from the last run locks nothing while Pegel is off`() {
        val stale = EngineState(Target.None, ChainStatus(dynamics = false, virtualizer = false, reverb = false), failed = false)
        val u = ui(settings = Settings(enabled = false), engine = stale)
        assertNull(u.notice)
        assertTrue(u.virtualizerAvailable && u.reverbAvailable)
    }

    @Test
    fun `blocked notifications are mentioned only when nothing worse is going on`() {
        assertEquals(Notice.NOTIFICATIONS_OFF, ui(notifications = false).notice)
        assertEquals(
            Notice.ENGINE_FAILED,
            ui(engine = EngineState(Target.None, null, failed = true), notifications = false).notice
        )
    }

    @Test
    fun `the profile label has three states`() {
        val p = Profile(1, "Auto", SoundState())
        assertEquals(ProfileLabel.Unsaved, ui().profileLabel)
        assertEquals(ProfileLabel.Named("Auto", modified = false), ui(active = p).profileLabel)
        assertEquals(ProfileLabel.Named("Auto", modified = true), ui(active = p, modified = true).profileLabel)
    }

    private val everything = SoundState(
        gainsDb = List(10) { 2f }, bassOn = true, loudnessOn = true, virtualizerOn = true,
        reverb = io.celox.pegel.audio.ReverbRoom.PLATE, balance = 0.3f,
    )

    @Test
    fun `the status names exactly the effects that change the sound`() {
        assertEquals(emptyList<ActiveEffect>(), activeEffects(SoundState(), all))
        assertEquals(ActiveEffect.entries, activeEffects(everything, all))
        // a switched-on equalizer with every band at zero changes nothing
        assertEquals(emptyList<ActiveEffect>(), activeEffects(SoundState(eqOn = true), all))
        assertEquals(emptyList<ActiveEffect>(), activeEffects(SoundState(eqOn = false, gainsDb = List(10) { 2f }), all))
        assertEquals(listOf(ActiveEffect.BALANCE), activeEffects(SoundState(balance = -0.5f), all))
    }

    @Test
    fun `an effect the device does not grant is not called active`() {
        val status = ChainStatus(dynamics = true, virtualizer = false, reverb = false)
        assertEquals(
            listOf(ActiveEffect.EQUALIZER, ActiveEffect.BASS, ActiveEffect.LOUDNESS, ActiveEffect.BALANCE),
            activeEffects(everything, status),
        )
        // equalizer, bass, loudness and balance all live in the one dynamics effect
        assertEquals(
            listOf(ActiveEffect.VIRTUALIZER, ActiveEffect.REVERB),
            activeEffects(everything, ChainStatus(dynamics = false, virtualizer = true, reverb = true)),
        )
        // not attached yet: nothing is known to be missing
        assertEquals(ActiveEffect.entries, activeEffects(everything, null))
    }

    @Test
    fun `the screen carries the active effects, and none while Pegel is off`() {
        assertEquals(emptyList<ActiveEffect>(), ui().effects)
        assertEquals(
            listOf(ActiveEffect.BASS),
            buildSoundUi(on, SoundState(bassOn = true), emptyList(), null, false, attached, true).effects,
        )
        assertEquals(
            emptyList<ActiveEffect>(),
            buildSoundUi(Settings(enabled = false), SoundState(bassOn = true), emptyList(), null, false, attached, true).effects,
        )
    }

    @Test
    fun `the screen says how far the level is lowered to keep boosts clean`() {
        fun headroom(s: SoundState, settings: Settings = on) =
            buildSoundUi(settings, s, emptyList(), null, false, attached, true).headroomDb
        assertEquals(0f, headroom(SoundState()))
        assertEquals(6f, headroom(SoundState(gainsDb = listOf(6f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f))))
        assertEquals(0f, headroom(SoundState(gainsDb = List(10) { 6f }), Settings(enabled = false)))
        // not when the device did not grant the effect that would do it
        val noDynamics = EngineState(Target.GlobalMix, ChainStatus(dynamics = false, virtualizer = true, reverb = true), false)
        assertEquals(0f, buildSoundUi(on, SoundState(gainsDb = List(10) { 6f }), emptyList(), null, false, noDynamics, true).headroomDb)
    }

    @Test
    fun `custom presets follow the built-in ones`() {
        val presets = ui(custom = listOf(mine)).presets
        assertEquals(BUILT_IN_PRESETS + mine, presets)
    }

    @Test
    fun `optional cards follow the settings`() {
        assertFalse(ui().showReverb); assertFalse(ui().showBalance)
        val u = ui(settings = on.copy(showReverb = true, showBalance = true))
        assertTrue(u.showReverb); assertTrue(u.showBalance)
    }
}

class LabelTest {
    @Test
    fun `band labels are short enough for ten columns`() {
        assertEquals(
            listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k"),
            io.celox.pegel.audio.BAND_CENTERS_HZ.map(::bandLabel)
        )
    }

    @Test
    fun `gain labels carry a sign, a real minus and the locale's decimal mark`() {
        assertEquals("0", gainLabel(0f))
        assertEquals("+3", gainLabel(3f, java.util.Locale.ENGLISH))
        assertEquals("+3.5", gainLabel(3.5f, java.util.Locale.ENGLISH))
        assertEquals("−12", gainLabel(-12f, java.util.Locale.ENGLISH))
        assertEquals("−0,5", gainLabel(-0.5f, java.util.Locale.GERMAN))
    }
}
