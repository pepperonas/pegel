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
        assertEquals(6f, planFor(SoundState(loudnessOn = true, loudnessStrength = 0.3f), tuning).loudnessGainDb, 0.001f)
        assertEquals(0f, planFor(SoundState(loudnessOn = false, loudnessStrength = 0.3f), tuning).loudnessGainDb)
    }

    // Gain added behind a limiter clips; gain in front of it is what the limiter is there to catch.
    @Test
    fun `loudness is applied in front of the limiter, on top of the balance`() {
        val plan = planFor(SoundState(loudnessOn = true, loudnessStrength = 0.5f, balance = 0.5f), tuning)
        val (left, right) = inputGainsDb(plan)
        assertEquals(10f + 20 * log10(0.5f), left, 0.001f)
        assertEquals(10f, right, 0.001f)
        assertEquals(0f, LIMITER_POST_GAIN_DB)
        val quiet = inputGainsDb(planFor(SoundState(), tuning))
        assertEquals(0f, quiet.first); assertEquals(0f, quiet.second)
    }

    // Field finding: with a boost the level came "in waves" — the limiter ducked the whole mix on
    // every kick. Headroom in front means it has nothing to catch.
    @Test
    fun `a flat sound needs no headroom`() {
        assertEquals(0f, planFor(SoundState(), tuning).headroomDb)
        assertEquals(0f, planFor(SoundState(gainsDb = List(10) { -3f }), tuning).headroomDb)
    }

    @Test
    fun `the largest boost is taken off in front, so nothing reaches the limiter`() {
        val boosted = SoundState(gainsDb = listOf(0f, 0f, 0f, 0f, 0f, 4f, 6f, 0f, -3f, 0f))
        val plan = planFor(boosted, tuning)
        assertEquals(6f, plan.headroomDb)
        assertEquals(-6f to -6f, inputGainsDb(plan))
        assertEquals(0f, planFor(boosted.copy(eqOn = false), tuning).headroomDb)
    }

    @Test
    fun `bass adds to the bands it lies under, not to the others`() {
        // tuning: cutoff 80 Hz, 100 % = 15 dB → 7.5 dB at strength 0.5
        val bass = SoundState(bassOn = true, bassStrength = 0.5f)
        assertEquals(7.5f, planFor(bass, tuning).headroomDb)
        // 31 Hz and 62 Hz lie under the bass shelf: their boost stacks on top of it
        assertEquals(10.5f, planFor(bass.copy(gainsDb = listOf(3f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)), tuning).headroomDb)
        assertEquals(9.5f, planFor(bass.copy(gainsDb = listOf(0f, 2f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)), tuning).headroomDb)
        // 1 kHz does not: the larger of the two counts
        assertEquals(7.5f, planFor(bass.copy(gainsDb = listOf(0f, 0f, 0f, 0f, 0f, 5f, 0f, 0f, 0f, 0f)), tuning).headroomDb)
        assertEquals(9f, planFor(bass.copy(gainsDb = listOf(0f, 0f, 0f, 0f, 0f, 9f, 0f, 0f, 0f, 0f)), tuning).headroomDb)
        // a cut under the shelf takes some of it back
        assertEquals(4.5f, planFor(bass.copy(gainsDb = listOf(-3f, -3f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)), tuning).headroomDb)
    }

    @Test
    fun `loudness is the one thing that still pushes into the limiter`() {
        val plan = planFor(SoundState(gainsDb = List(10) { 4f }, loudnessOn = true, loudnessStrength = 0.3f), tuning)
        assertEquals(4f, plan.headroomDb)
        assertEquals(2f to 2f, inputGainsDb(plan)) // 6 dB loudness, 4 dB of it used up by the headroom
    }

    @Test
    fun `the limiter is a safety net just below full scale, not part of the sound`() {
        assertTrue(LIMITER_THRESHOLD_DB >= -1f && LIMITER_THRESHOLD_DB < 0f)
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
