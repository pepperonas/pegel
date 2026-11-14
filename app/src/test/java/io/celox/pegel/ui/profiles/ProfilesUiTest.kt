package io.celox.pegel.ui.profiles

import io.celox.pegel.audio.SoundState
import io.celox.pegel.data.DeviceEntity
import io.celox.pegel.data.Profile
import io.celox.pegel.device.DeviceKind
import io.celox.pegel.device.OutputDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilesUiTest {

    private val auto = Profile(1, "Auto", SoundState())
    private val home = Profile(2, "Home", SoundState())
    private val buds = DeviceEntity("BLUETOOTH:Buds", "Buds", profileId = 1, lastSeen = 10)
    private val dac = DeviceEntity("USB:DAC", "DAC", profileId = null, lastSeen = 20)

    @Test
    fun `rows mark the active profile and whether it was changed`() {
        val rows = buildProfileRows(listOf(auto, home), activeId = 1, modified = true, devices = emptyList())
        assertEquals(listOf(true, false), rows.map { it.active })
        assertEquals(listOf(true, false), rows.map { it.modified })
    }

    @Test
    fun `modified only ever applies to the active profile`() {
        val rows = buildProfileRows(listOf(auto, home), activeId = null, modified = true, devices = emptyList())
        assertTrue(rows.none { it.active || it.modified })
    }

    @Test
    fun `each profile lists the devices bound to it`() {
        val speaker = DeviceEntity(OutputDevice.SPEAKER_KEY, "", profileId = 1, lastSeen = 0)
        val rows = buildProfileRows(listOf(auto, home), 1, false, listOf(buds, dac, speaker))
        assertEquals(listOf("BLUETOOTH:Buds", OutputDevice.SPEAKER_KEY), rows[0].boundTo.map { it.key })
        assertTrue(rows[1].boundTo.isEmpty())
    }

    @Test
    fun `the speaker is always offered, even before it was ever seen`() {
        val rows = buildDeviceRows(emptyList(), connected = null)
        val row = rows.single()
        assertEquals(OutputDevice.SPEAKER_KEY, row.key)
        assertEquals(DeviceKind.SPEAKER, row.kind)
        assertNull(row.profileId)
        assertFalse(row.forgettable)
    }

    @Test
    fun `the speaker comes first, then the others by name, and it is listed once`() {
        val speaker = DeviceEntity(OutputDevice.SPEAKER_KEY, "SM-S928B", profileId = 2, lastSeen = 0)
        val rows = buildDeviceRows(listOf(dac, speaker, buds), connected = null)
        assertEquals(listOf(OutputDevice.SPEAKER_KEY, "BLUETOOTH:Buds", "USB:DAC"), rows.map { it.key })
        assertEquals(2L, rows[0].profileId)
        assertEquals(listOf(DeviceKind.SPEAKER, DeviceKind.BLUETOOTH, DeviceKind.USB), rows.map { it.kind })
    }

    @Test
    fun `the connected device is marked, and only that one`() {
        val rows = buildDeviceRows(listOf(buds, dac), connected = OutputDevice(DeviceKind.BLUETOOTH, "Buds"))
        assertEquals(mapOf(OutputDevice.SPEAKER_KEY to false, "BLUETOOTH:Buds" to true, "USB:DAC" to false),
            rows.associate { it.key to it.connected })
    }

    @Test
    fun `a connected device that was never stored still shows up`() {
        val rows = buildDeviceRows(emptyList(), connected = OutputDevice(DeviceKind.WIRED, "Headphones"))
        assertEquals(listOf(OutputDevice.SPEAKER_KEY, "WIRED:Headphones"), rows.map { it.key })
        assertTrue(rows[1].connected)
    }

    @Test
    fun `everything but the speaker can be forgotten`() {
        val rows = buildDeviceRows(listOf(buds), connected = null)
        assertEquals(listOf(false, true), rows.map { it.forgettable })
    }

    @Test
    fun `a device without a name still gets a row the UI can label`() {
        val nameless = DeviceEntity("WIRED:", "", profileId = null, lastSeen = 0)
        assertEquals("", buildDeviceRows(listOf(nameless), null)[1].label)
    }
}
