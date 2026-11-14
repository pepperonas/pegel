package io.celox.pegel.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SoundStateJsonTest {

    private val odd = SoundState(
        eqOn = false, gainsDb = listOf(1f, -2f, 3.5f, -4.5f, 5f, -6f, 7f, -8f, 9f, -10f), presetId = null,
        bassOn = true, bassStrength = 0.25f, loudnessOn = true, loudnessStrength = 0.75f,
        virtualizerOn = true, virtualizerStrength = 1f, reverb = ReverbRoom.LARGE_HALL, balance = -0.5f,
    )

    @Test
    fun `a state survives the round trip`() {
        assertEquals(odd, SoundStateJson.decode(SoundStateJson.encode(odd)))
        assertEquals(SoundState(), SoundStateJson.decode(SoundStateJson.encode(SoundState())))
    }

    @Test
    fun `missing fields fall back to the defaults`() {
        assertEquals(SoundState(), SoundStateJson.decode("{}"))
        assertEquals(SoundState(bassOn = true), SoundStateJson.decode("""{"bassOn":true}"""))
    }

    @Test
    fun `an unknown reverb name means none`() {
        assertEquals(ReverbRoom.NONE, SoundStateJson.decode("""{"reverb":"CATHEDRAL"}""")!!.reverb)
    }

    @Test
    fun `values outside the range come back clamped`() {
        val s = SoundStateJson.decode("""{"gainsDb":[99,-99],"bassStrength":5,"balance":9}""")!!
        assertEquals(12f, s.gainsDb[0]); assertEquals(-12f, s.gainsDb[1]); assertEquals(10, s.gainsDb.size)
        assertEquals(1f, s.bassStrength); assertEquals(1f, s.balance)
    }

    @Test
    fun `wrongly typed fields do not throw`() {
        assertEquals(SoundState(), SoundStateJson.decode("""{"gainsDb":"loud","bassOn":"yes","balance":"left"}"""))
    }

    @Test
    fun `anything that is not a json object is rejected`() {
        assertNull(SoundStateJson.decode("kein json"))
        assertNull(SoundStateJson.decode("[]"))
        assertNull(SoundStateJson.decode(""))
    }
}
