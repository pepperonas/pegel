package io.celox.pegel.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.log10
import kotlin.math.sqrt

class ChainPlanTest {

    private val tuning = Tuning()

    @Test
    fun `band edges sit halfway between the centres, the last at 20 kHz`() {
        assertEquals(BAND_COUNT, POST_EQ_CUTOFFS_HZ.size)
        for (i in 0 until BAND_COUNT - 1) {
            assertEquals(sqrt(BAND_CENTERS_HZ[i] * BAND_CENTERS_HZ[i + 1]), POST_EQ_CUTOFFS_HZ[i], 0.01f)
        }
        assertEquals(44.19f, POST_EQ_CUTOFFS_HZ[0], 0.01f)
        assertEquals(20000f, POST_EQ_CUTOFFS_HZ.last())
        assertTrue(POST_EQ_CUTOFFS_HZ.zipWithNext().all { (a, b) -> a < b })
    }

    @Test
    fun `the equalizer passes its gains on`() {
        val gains = listOf(1f, 2f, 3f, 4f, 5f, -1f, -2f, -3f, -4f, -5f)
        assertEquals(gains, planFor(SoundState(gainsDb = gains), tuning).postEqGainsDb)
    }

    @Test
    fun `a switched-off equalizer is flat whatever the sliders say`() {
        val plan = planFor(SoundState(eqOn = false, gainsDb = List(10) { 6f }), tuning)
        assertEquals(List(10) { 0f }, plan.postEqGainsDb)
    }

    @Test
    fun `bass is strength times the configured maximum, below the configured cutoff`() {
        val plan = planFor(SoundState(bassOn = true, bassStrength = 0.5f), tuning)
        assertEquals(7.5f, plan.bassGainDb)
        assertEquals(80f, plan.bassCutoffHz)
        assertEquals(0f, planFor(SoundState(bassOn = false, bassStrength = 0.5f), tuning).bassGainDb)
        assertEquals(30f, planFor(SoundState(bassOn = true, bassStrength = 1f), Tuning(120, 30, 20)).bassGainDb)
    }

    @Test
    fun `loudness is strength times its maximum`() {
        assertEquals(6f, planFor(SoundState(loudnessOn = true, loudnessStrength = 0.3f), tuning).limiterPostGainDb, 0.001f)
        assertEquals(0f, planFor(SoundState(loudnessOn = false, loudnessStrength = 0.3f), tuning).limiterPostGainDb)
    }

    @Test
    fun `balance turns the far side down and never the near side up`() {
        val centre = planFor(SoundState(balance = 0f), tuning)
        assertEquals(0f, centre.leftGainDb); assertEquals(0f, centre.rightGainDb)

        val right = planFor(SoundState(balance = 0.5f), tuning)
        assertEquals(20 * log10(0.5f), right.leftGainDb, 0.001f)
        assertEquals(-6.02f, right.leftGainDb, 0.01f)
        assertEquals(0f, right.rightGainDb)

        val left = planFor(SoundState(balance = -1f), tuning)
        assertEquals(0f, left.leftGainDb)
        assertEquals(-60f, left.rightGainDb)
    }

    @Test
    fun `virtualizer strength is per mille, or absent when off`() {
        assertNull(planFor(SoundState(virtualizerOn = false), tuning).virtualizerStrength)
        assertEquals(500, planFor(SoundState(virtualizerOn = true, virtualizerStrength = 0.5f), tuning).virtualizerStrength)
    }

    @Test
    fun `reverb passes through`() {
        assertEquals(ReverbRoom.PLATE, planFor(SoundState(reverb = ReverbRoom.PLATE), tuning).reverb)
    }

    @Test
    fun `the plan normalizes what it is given`() {
        val plan = planFor(
            SoundState(bassOn = true, bassStrength = 7f, gainsDb = List(10) { 99f }),
            Tuning(bassCutoffHz = 9999, bassMaxDb = 15, loudnessMaxDb = 20),
        )
        assertEquals(15f, plan.bassGainDb)
        assertEquals(150f, plan.bassCutoffHz)
        assertEquals(List(10) { 12f }, plan.postEqGainsDb)
    }
}
