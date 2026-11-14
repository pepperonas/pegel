package io.celox.pegel.device

import android.media.AudioDeviceInfo

enum class DeviceKind { SPEAKER, WIRED, USB, BLUETOOTH, OTHER }

/**
 * A place music can come out of. Identified by kind and product name — that needs no Bluetooth
 * permission, at the price that two devices of the same model share one [key] (and one profile).
 */
data class OutputDevice(val kind: DeviceKind, val name: String) {
    val key: String
        get() = if (kind == DeviceKind.SPEAKER) SPEAKER_KEY else "${kind.name}:${name.trim()}"

    companion object {
        const val SPEAKER_KEY = "SPEAKER"

        /** The kind a stored key belongs to — for showing a device that is not connected. */
        fun kindOfKey(key: String): DeviceKind =
            DeviceKind.entries.firstOrNull { key == it.name || key.startsWith(it.name + ":") } ?: DeviceKind.OTHER
    }
}

// Values of AudioDeviceInfo.TYPE_* added after API 28; plain ints so they work on every version.
private const val TYPE_HEARING_AID = 23
private const val TYPE_BLE_HEADSET = 26
private const val TYPE_BLE_SPEAKER = 27
private const val TYPE_BLE_BROADCAST = 30

/** Null for routes that carry calls, not music. */
fun kindOf(audioDeviceInfoType: Int): DeviceKind? = when (audioDeviceInfoType) {
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> DeviceKind.SPEAKER
    AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> DeviceKind.WIRED
    AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_ACCESSORY -> DeviceKind.USB
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, TYPE_HEARING_AID, TYPE_BLE_HEADSET, TYPE_BLE_SPEAKER, TYPE_BLE_BROADCAST ->
        DeviceKind.BLUETOOTH
    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE, AudioDeviceInfo.TYPE_TELEPHONY, AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> null
    else -> DeviceKind.OTHER
}

/**
 * A port's name for the user. Built-in and unnamed ports report the phone's own model as their
 * product name — "Pixel 7 connected" for a pair of wired headphones helps nobody.
 */
fun deviceName(productName: CharSequence?, model: String): String {
    val name = productName?.toString()?.trim().orEmpty()
    return if (name.equals(model.trim(), ignoreCase = true)) "" else name
}

private val PRIORITY = listOf(DeviceKind.BLUETOOTH, DeviceKind.USB, DeviceKind.WIRED, DeviceKind.SPEAKER, DeviceKind.OTHER)

/**
 * Which of the connected outputs the music is most likely going to: Android routes to the most
 * recently connected "personal" device, which in practice is this order.
 */
fun pickActive(devices: List<OutputDevice>): OutputDevice? =
    PRIORITY.firstNotNullOfOrNull { kind -> devices.firstOrNull { it.kind == kind } }
