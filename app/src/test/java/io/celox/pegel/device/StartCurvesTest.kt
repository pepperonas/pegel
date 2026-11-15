package io.celox.pegel.device

import io.celox.pegel.audio.BAND_COUNT
import io.celox.pegel.audio.SoundState
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StartCurvesTest {

    private val curved = DeviceType.entries.filter { it != DeviceType.HEADPHONES }

    @Test
    fun `headphones have no start curve - they get a measured one or none`() {
        assertNull(startCurveDb(DeviceType.HEADPHONES))
    }

    @Test
    fun `every other type has a restrained curve on the band grid`() {
        for (type in curved) {
            val c = startCurveDb(type)!!
            assertEquals(type.name, BAND_COUNT, c.size)
            assertEquals(type.name, c, SoundState(gainsDb = c).normalized().gainsDb)
            assertTrue("$type is a starting point, not an effect", c.all { abs(it) <= 6f })
            assertTrue("$type must change something", c.any { it != 0f })
        }
    }

    @Test
    fun `small drivers are never asked for deep bass`() {
        for (type in listOf(DeviceType.PHONE_SPEAKER, DeviceType.SPEAKER_SMALL)) {
            val c = startCurveDb(type)!!
            assertTrue("$type 31 Hz", c[0] < 0f)
            assertTrue("$type 62 Hz", c[1] <= 0f)
        }
    }

    @Test
    fun `the curves differ from each other`() {
        val all = curved.map { startCurveDb(it) }
        assertEquals(all.size, all.toSet().size)
        assertNotEquals(startCurveDb(DeviceType.SPEAKER_SMALL), startCurveDb(DeviceType.SPEAKER_LARGE))
    }

    @Test
    fun `a stored type is read back, anything else is unknown`() {
        for (t in DeviceType.entries) assertEquals(t, DeviceType.of(t.name))
        assertNull(DeviceType.of(null))
        assertNull(DeviceType.of("TOASTER"))
    }
}
