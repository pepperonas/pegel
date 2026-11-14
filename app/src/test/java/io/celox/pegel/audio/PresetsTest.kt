package io.celox.pegel.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetsTest {

    @Test
    fun `ten band centres an octave apart from 31 Hz to 16 kHz`() {
        assertEquals(BAND_COUNT, BAND_CENTERS_HZ.size)
        assertEquals(31.25f, BAND_CENTERS_HZ.first())
        assertEquals(16000f, BAND_CENTERS_HZ.last())
        BAND_CENTERS_HZ.zipWithNext().forEach { (a, b) -> assertEquals(a * 2, b) }
    }

    @Test
    fun `thirteen built-in presets with unique ids`() {
        assertEquals(13, BUILT_IN_PRESETS.size)
        assertEquals(13, BUILT_IN_PRESETS.map { it.id }.toSet().size)
        assertTrue(BUILT_IN_PRESETS.all { it.builtIn })
    }

    @Test
    fun `every preset has ten gains inside the range, on the half-decibel grid`() {
        for (p in BUILT_IN_PRESETS) {
            assertEquals(p.id, BAND_COUNT, p.gainsDb.size)
            assertTrue(p.id, p.gainsDb.all { it in -GAIN_LIMIT_DB..GAIN_LIMIT_DB && (it * 2) % 1f == 0f })
        }
    }

    @Test
    fun `flat is flat and comes first`() {
        assertEquals("flat", BUILT_IN_PRESETS.first().id)
        assertEquals(List(BAND_COUNT) { 0f }, BUILT_IN_PRESETS.first().gainsDb)
    }

    @Test
    fun `no two presets sound the same`() {
        assertEquals(BUILT_IN_PRESETS.size, BUILT_IN_PRESETS.map { it.gainsDb }.toSet().size)
    }
}
