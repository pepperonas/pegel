package io.celox.pegel.data

import io.celox.pegel.audio.ReverbRoom
import io.celox.pegel.audio.SessionMode
import io.celox.pegel.audio.SoundState
import io.celox.pegel.audio.Tuning
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {

    private val loud = SoundState(bassOn = true, bassStrength = 0.75f, reverb = ReverbRoom.PLATE).withGain(0, 6f)
    private val data = BackupData(
        profiles = listOf(BackupProfile("Loud", loud), BackupProfile("Flat", SoundState())),
        presets = listOf(BackupPreset("Mine", listOf(1f, 2f, 3f, 4f, 5f, 4f, 3f, 2f, 1f, 0f))),
        devices = listOf(BackupDevice("BLUETOOTH:Buds", "Buds", "Loud"), BackupDevice("SPEAKER", "", null)),
        settings = Settings(
            enabled = true, mode = SessionMode.PLAYERS_ONLY, tuning = Tuning(120, 25, 10),
            showReverb = true, showBalance = true, frameDurationMs = 50, startOnBoot = false,
        ),
        working = loud.withGain(1, -3f),
        activeProfile = "Loud",
    )

    private fun error(r: Result<*>) = (r.exceptionOrNull() as? BackupException)?.error

    @Test
    fun `a backup survives the round trip`() {
        assertEquals(data, BackupCodec.decode(BackupCodec.encode(data)).getOrThrow())
    }

    @Test
    fun `the file says what it is`() {
        val o = JSONObject(BackupCodec.encode(data))
        assertEquals("pegel", o.getString("app"))
        assertEquals(1, o.getInt("format"))
        assertEquals("backup", o.getString("kind"))
    }

    // Review focus 4
    @Test
    fun `anything that is not a Pegel backup is rejected with the reason`() {
        assertEquals(BackupError.Empty, error(BackupCodec.decode("")))
        assertEquals(BackupError.Empty, error(BackupCodec.decode("  \n ")))
        assertEquals(BackupError.NotJson, error(BackupCodec.decode("kein json")))
        assertEquals(BackupError.NotJson, error(BackupCodec.decode("[1,2]")))
        assertEquals(BackupError.NotPegel, error(BackupCodec.decode("{}")))
        assertEquals(BackupError.NotPegel, error(BackupCodec.decode("""{"app":"flat","format":1,"kind":"backup"}""")))
        assertEquals(BackupError.NewerFormat(2), error(BackupCodec.decode("""{"app":"pegel","format":2,"kind":"backup"}""")))
        assertEquals(BackupError.NotPegel, error(BackupCodec.decode("""{"app":"pegel","format":"x","kind":"backup"}""")))
    }

    @Test
    fun `a damaged entry is skipped, the rest arrives`() {
        val text = """{"app":"pegel","format":1,"kind":"backup",
            "profiles":[{"name":"Good","state":{"bassOn":true}}, {"state":{}}, {"name":"  ","state":{}}, {"name":"NoState"}, 7],
            "presets":[{"name":"P","gains":[1,2,3]}, {"gains":[1]}, "x"],
            "devices":[{"key":"SPEAKER","profile":"Good"}, {"label":"no key"}]}"""
        val d = BackupCodec.decode(text).getOrThrow()
        assertEquals(listOf("Good"), d.profiles.map { it.name })
        assertTrue(d.profiles.single().state.bassOn)
        assertEquals(listOf("P"), d.presets.map { it.name })
        assertEquals(10, d.presets.single().gainsDb.size)
        assertEquals(listOf(BackupDevice("SPEAKER", "", "Good")), d.devices)
        // absent sections fall back to defaults
        assertEquals(Settings(), d.settings)
        assertEquals(SoundState(), d.working)
    }

    @Test
    fun `settings from a file are normalized`() {
        val d = BackupCodec.decode(
            """{"app":"pegel","format":1,"kind":"backup","settings":{"frameDurationMs":99999,"bassCutoffHz":3,"mode":"NOPE"}}"""
        ).getOrThrow()
        assertEquals(1000, d.settings.frameDurationMs)
        assertEquals(10, d.settings.tuning.bassCutoffHz)
        assertEquals(SessionMode.AUTO, d.settings.mode)
    }

    @Test
    fun `a single profile survives the round trip`() {
        val text = BackupCodec.encodeProfile("Loud", loud)
        assertEquals("profile", JSONObject(text).getString("kind"))
        assertEquals(BackupProfile("Loud", loud), BackupCodec.decodeProfile(text).getOrThrow())
    }

    @Test
    fun `a backup is not a profile and a profile is not a backup`() {
        assertEquals(BackupError.WrongKind, error(BackupCodec.decodeProfile(BackupCodec.encode(data))))
        assertEquals(BackupError.WrongKind, error(BackupCodec.decode(BackupCodec.encodeProfile("Loud", loud))))
    }

    @Test
    fun `a profile file without a usable profile is rejected`() {
        assertEquals(BackupError.NotPegel, error(BackupCodec.decodeProfile("""{"app":"pegel","format":1,"kind":"profile"}""")))
        assertEquals(BackupError.NotPegel, error(BackupCodec.decodeProfile("""{"app":"pegel","format":1,"kind":"profile","profile":{"name":" "}}""")))
        assertEquals(BackupError.NewerFormat(9), error(BackupCodec.decodeProfile("""{"app":"pegel","format":9,"kind":"profile"}""")))
    }
}
