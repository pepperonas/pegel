package io.celox.pegel.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CurveTest {

    private val shapes = listOf(
        listOf(6f, 4f, 2f, 0f, -2f, -2f, 0f, 2f, 4f, 6f),
        listOf(12f, -12f, 12f, -12f, 12f, -12f, 12f, -12f, 12f, -12f),
        listOf(0f, 0f, 0f, 0f, 9f, 0f, 0f, 0f, 0f, 0f),
        listOf(-4f, -3f, -1f, 1f, 3f, 4f, 4f, 2f, 0f, -2f),
    )

    @Test
    fun `a flat equalizer draws a flat line`() {
        assertEquals(List(64) { 0f }, curveSamples(List(10) { 0f }, 64))
    }

    @Test
    fun `the curve passes through every band`() {
        // 91 samples: band i sits exactly on sample i * 10.
        for (g in shapes) {
            val c = curveSamples(g, 91)
            assertEquals(91, c.size)
            for (i in 0 until 10) assertEquals("band $i of $g", g[i], c[i * 10], 0.0001f)
        }
    }

    @Test
    fun `the curve never leaves the range the sliders span`() {
        for (g in shapes) {
            val c = curveSamples(g, 257)
            assertTrue("$g overshoots: ${c.min()}..${c.max()}", c.min() >= g.min() - 0.0001f && c.max() <= g.max() + 0.0001f)
        }
    }

    @Test
    fun `between two bands the curve stays between them`() {
        val c = curveSamples(shapes[1], 91)
        for (i in 1 until 10) assertTrue(c[i] in -12.0001f..12.0001f)
        val mid = curveSamples(listOf(0f, 10f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f), 91)[5]
        assertTrue("midpoint $mid", mid > 0f && mid < 10f)
    }

    @Test
    fun `fewer than two samples is a programming error`() {
        assertThrows(IllegalArgumentException::class.java) { curveSamples(List(10) { 0f }, 1) }
    }
}
