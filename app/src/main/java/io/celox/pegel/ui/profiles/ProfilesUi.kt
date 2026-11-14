package io.celox.pegel.ui.profiles

import io.celox.pegel.data.DeviceEntity
import io.celox.pegel.data.Profile
import io.celox.pegel.device.DeviceKind
import io.celox.pegel.device.OutputDevice

data class ProfileRow(val profile: Profile, val active: Boolean, val modified: Boolean, val boundTo: List<DeviceEntity>)

/** [label] may be empty (nameless device, or the speaker) — the UI falls back to the kind's name. */
data class DeviceRow(
    val key: String,
    val label: String,
    val kind: DeviceKind,
    val profileId: Long?,
    val connected: Boolean,
    val forgettable: Boolean,
)

fun buildProfileRows(profiles: List<Profile>, activeId: Long?, modified: Boolean, devices: List<DeviceEntity>): List<ProfileRow> =
    profiles.map { p ->
        val active = p.id == activeId
        ProfileRow(p, active, modified = active && modified, boundTo = devices.filter { it.profileId == p.id })
    }

/**
 * The outputs a profile can be bound to: the speaker first (it always exists, seen or not), then
 * every stored device by name, plus the connected one if it is not stored yet.
 */
fun buildDeviceRows(devices: List<DeviceEntity>, connected: OutputDevice?): List<DeviceRow> {
    val stored = devices.associateBy { it.key }
    val speaker = DeviceRow(
        key = OutputDevice.SPEAKER_KEY, label = "", kind = DeviceKind.SPEAKER,
        profileId = stored[OutputDevice.SPEAKER_KEY]?.profileId,
        connected = connected?.key == OutputDevice.SPEAKER_KEY, forgettable = false,
    )
    val others = devices.filter { it.key != OutputDevice.SPEAKER_KEY }.map {
        DeviceRow(it.key, it.label, OutputDevice.kindOfKey(it.key), it.profileId, connected?.key == it.key, forgettable = true)
    }.toMutableList()
    if (connected != null && connected.key != OutputDevice.SPEAKER_KEY && connected.key !in stored) {
        others += DeviceRow(connected.key, connected.name.trim(), connected.kind, null, connected = true, forgettable = true)
    }
    return listOf(speaker) + others.sortedBy { it.label.lowercase() }
}
