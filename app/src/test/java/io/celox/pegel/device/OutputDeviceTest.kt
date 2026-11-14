package io.celox.pegel.device

import android.media.AudioDeviceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OutputDeviceTest {

    @Test
    fun `device types map to the kinds a profile can be bound to`() {
        listOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER)
            .forEach { assertEquals(DeviceKind.BLUETOOTH, kindOf(it)) }
        listOf(AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
            .forEach { assertEquals(DeviceKind.WIRED, kindOf(it)) }
        listOf(AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_ACCESSORY)
            .forEach { assertEquals(DeviceKind.USB, kindOf(it)) }
        assertEquals(DeviceKind.SPEAKER, kindOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER))
        assertEquals(DeviceKind.OTHER, kindOf(AudioDeviceInfo.TYPE_HDMI))
    }

    @Test
    fun `call audio routes are not music outputs`() {
        listOf(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE, AudioDeviceInfo.TYPE_TELEPHONY, AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
            .forEach { assertNull(kindOf(it)) }
    }

    @Test
    fun `the most personal output wins`() {
        val speaker = OutputDevice(DeviceKind.SPEAKER, "Phone")
        val wired = OutputDevice(DeviceKind.WIRED, "Headphones")
        val usb = OutputDevice(DeviceKind.USB, "DAC")
        val bt = OutputDevice(DeviceKind.BLUETOOTH, "Buds")
        val other = OutputDevice(DeviceKind.OTHER, "TV")
        assertEquals(bt, pickActive(listOf(speaker, other, wired, usb, bt)))
        assertEquals(usb, pickActive(listOf(speaker, wired, usb)))
        assertEquals(wired, pickActive(listOf(speaker, wired)))
        assertEquals(speaker, pickActive(listOf(other, speaker)))
        assertEquals(other, pickActive(listOf(other)))
        assertNull(pickActive(emptyList()))
    }

    @Test
    fun `of two bluetooth devices the first listed wins`() {
        val a = OutputDevice(DeviceKind.BLUETOOTH, "A")
        val b = OutputDevice(DeviceKind.BLUETOOTH, "B")
        assertEquals(a, pickActive(listOf(a, b)))
    }

    @Test
    fun `the speaker has one key whatever the phone calls it`() {
        assertEquals("SPEAKER", OutputDevice(DeviceKind.SPEAKER, "SM-S928B").key)
        assertEquals("SPEAKER", OutputDevice(DeviceKind.SPEAKER, "").key)
    }

    @Test
    fun `other devices are keyed by kind and trimmed name`() {
        assertEquals("BLUETOOTH:Buds", OutputDevice(DeviceKind.BLUETOOTH, "  Buds ").key)
        assertEquals("WIRED:", OutputDevice(DeviceKind.WIRED, " ").key)
    }

    // Review focus 5
    @Test
    fun `two devices of the same kind and name share a key by design`() {
        assertEquals(OutputDevice(DeviceKind.BLUETOOTH, "JBL Flip").key, OutputDevice(DeviceKind.BLUETOOTH, "JBL Flip ").key)
    }

    @Test
    fun `a port that only reports the phone's own model has no name`() {
        assertEquals("", deviceName("Pixel 7", model = "Pixel 7"))
        assertEquals("", deviceName(" pixel 7 ", model = "Pixel 7"))
        assertEquals("", deviceName(null, model = "Pixel 7"))
        assertEquals("WH-1000XM4", deviceName(" WH-1000XM4 ", model = "Pixel 7"))
    }
}
