package io.celox.pegel.device

import io.celox.pegel.data.Profile
import io.celox.pegel.data.SoundRepository

/**
 * Applies the profile bound to an output device when that device becomes the active one.
 * Only a *change* of device triggers it — the same device reported again must not undo what the
 * user adjusted in the meantime.
 */
class AutoApply(private val repo: SoundRepository) {
    private var lastKey: String? = null

    /** The profile that was applied, or null when nothing happened. */
    suspend fun onActiveDevice(device: OutputDevice?): Profile? {
        device ?: return null
        if (device.key == lastKey) return null
        lastKey = device.key
        repo.seeDevice(device.key, device.name.trim())
        val profile = repo.profileForDevice(device.key) ?: return null
        repo.loadProfile(profile.id)
        return profile
    }
}
