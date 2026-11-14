package io.celox.pegel.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SoundStateTest {

    @Test
    fun `gains are clamped to the limit and snapped to half decibels`() {
        val s = SoundState(gainsDb = listOf(12.7f, -3.26f, Float.NaN, -40f, 0.24f, 0.26f, 0f, 0f, 0f, 0f)).normalized()
        assertEquals(listOf(12f, -3.5f, 0f, -12f, 0f, 0.5f, 0f, 0f, 0f, 0f), s.gainsDb)
    }

    @Test
    fun `a gain list of the wrong length is cut or padded to ten bands`() {
        assertEquals(List(10) { 0f }.toMutableList().also { it[0] = 3f }, SoundState(gainsDb = listOf(3f)).normalized().gainsDb)
        assertEquals(10, SoundState(gainsDb = List(14) { 1f }).normalized().gainsDb.size)
    }

    @Test
    fun `strengths and balance are clamped`() {
        val s = SoundState(bassStrength = 7f, loudnessStrength = -1f, virtualizerStrength = Float.NaN, balance = -3f).normalized()
        assertEquals(1f, s.bassStrength)
        assertEquals(0f, s.loudnessStrength)
        assertEquals(0f, s.virtualizerStrength)
        assertEquals(-1f, s.balance)
    }

    @Test
    fun `moving one band leaves the preset`() {
        val s = SoundState().withGain(3, 4.2f)
        assertEquals(4f, s.gainsDb[3])
        assertNull(s.presetId)
    }

    @Test
    fun `a band index outside the range changes nothing`() {
        assertEquals(SoundState(), SoundState().withGain(10, 4f))
        assertEquals(SoundState(), SoundState().withGain(-1, 4f))
    }

    @Test
    fun `choosing a preset takes its gains and its id`() {
        val rock = BUILT_IN_PRESETS.first { it.id == "rock" }
        val s = SoundState().withPreset(rock)
        assertEquals(rock.gainsDb, s.gainsDb)
        assertEquals("rock", s.presetId)
    }

    @Test
    fun `tuning snaps to its steps`() {
        assertEquals(Tuning(80, 15, 20), Tuning().normalized())
        assertEquals(Tuning(150, 30, 5), Tuning(bassCutoffHz = 999, bassMaxDb = 44, loudnessMaxDb = 1).normalized())
        assertEquals(Tuning(10, 5, 20), Tuning(bassCutoffHz = 3, bassMaxDb = 6, loudnessMaxDb = 22).normalized())
        assertEquals(Tuning(90, 15, 20), Tuning(bassCutoffHz = 86).normalized())
    }
}
