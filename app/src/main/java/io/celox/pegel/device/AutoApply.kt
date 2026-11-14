package io.celox.pegel.device

import io.celox.pegel.data.Profile
import io.celox.pegel.data.SoundRepository

/**
 * Applies the profile bound to an output device when that device becomes the active one.
 * Only a *change* of device triggers it — the same device reported again must not undo what the
 * user adjusted in the meantime.
 */
class AutoApply(private val repo: SoundRepository) {

    /** The profile that was applied, or null when nothing happened. */
    suspend fun onActiveDevice(device: OutputDevice?): Profile? {
        device ?: return null
        // Remembered in the repository, not here: this object dies with the service, and a restart
        // on the same output must not reload the profile over what the user changed since.
        if (device.key == repo.lastDeviceKey) return null
        repo.rememberDevice(device.key)
        repo.seeDevice(device.key, device.name.trim())
        val profile = repo.profileForDevice(device.key) ?: return null
        repo.loadProfile(profile.id)
        return profile
    }
}
