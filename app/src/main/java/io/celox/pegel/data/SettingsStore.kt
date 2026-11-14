package io.celox.pegel.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.celox.pegel.audio.SessionMode
import io.celox.pegel.audio.SoundState
import io.celox.pegel.audio.SoundStateJson
import io.celox.pegel.audio.Tuning
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException

/** Everything that is a setting rather than part of a sound. */
data class Settings(
    /** The master switch. Off on a fresh install: Pegel does nothing until asked. */
    val enabled: Boolean = false,
    val mode: SessionMode = SessionMode.AUTO,
    val tuning: Tuning = Tuning(),
    val showReverb: Boolean = false,
    val showBalance: Boolean = false,
    val frameDurationMs: Int = 10,
    val startOnBoot: Boolean = true,
) {
    fun normalized(): Settings =
        copy(tuning = tuning.normalized(), frameDurationMs = frameDurationMs.coerceIn(FRAME_MIN_MS, FRAME_MAX_MS))

    companion object {
        const val FRAME_MIN_MS = 10
        const val FRAME_MAX_MS = 1000
    }
}

/** What the store holds besides [Settings]: the sound being edited and which profile it belongs to. */
data class Stored(val settings: Settings, val working: SoundState, val activeProfileId: Long?, val lastDeviceKey: String? = null)

class SettingsStore(private val data: DataStore<Preferences>) {

    suspend fun read(): Stored {
        val p = data.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.first()
        val d = Settings()
        return Stored(
            settings = Settings(
                enabled = p[ENABLED] ?: d.enabled,
                mode = SessionMode.entries.firstOrNull { it.name == p[MODE] } ?: d.mode,
                tuning = Tuning(
                    p[BASS_CUTOFF] ?: d.tuning.bassCutoffHz,
                    p[BASS_MAX] ?: d.tuning.bassMaxDb,
                    p[LOUD_MAX] ?: d.tuning.loudnessMaxDb,
                ),
                showReverb = p[SHOW_REVERB] ?: d.showReverb,
                showBalance = p[SHOW_BALANCE] ?: d.showBalance,
                frameDurationMs = p[FRAME_MS] ?: d.frameDurationMs,
                startOnBoot = p[START_ON_BOOT] ?: d.startOnBoot,
            ).normalized(),
            working = p[WORKING]?.let(SoundStateJson::decode) ?: SoundState(),
            activeProfileId = p[ACTIVE_PROFILE],
            lastDeviceKey = p[LAST_DEVICE],
        )
    }

    suspend fun writeSettings(s: Settings) {
        data.edit {
            it[ENABLED] = s.enabled
            it[MODE] = s.mode.name
            it[BASS_CUTOFF] = s.tuning.bassCutoffHz
            it[BASS_MAX] = s.tuning.bassMaxDb
            it[LOUD_MAX] = s.tuning.loudnessMaxDb
            it[SHOW_REVERB] = s.showReverb
            it[SHOW_BALANCE] = s.showBalance
            it[FRAME_MS] = s.frameDurationMs
            it[START_ON_BOOT] = s.startOnBoot
        }
    }

    suspend fun writeWorking(state: SoundState, activeProfileId: Long?) {
        data.edit {
            it[WORKING] = SoundStateJson.encode(state)
            if (activeProfileId == null) it.remove(ACTIVE_PROFILE) else it[ACTIVE_PROFILE] = activeProfileId
        }
    }

    suspend fun writeLastDevice(key: String) {
        data.edit { it[LAST_DEVICE] = key }
    }

    private companion object {
        val LAST_DEVICE = stringPreferencesKey("last_device_key")
        val ENABLED = booleanPreferencesKey("enabled")
        val MODE = stringPreferencesKey("session_mode")
        val BASS_CUTOFF = intPreferencesKey("bass_cutoff_hz")
        val BASS_MAX = intPreferencesKey("bass_max_db")
        val LOUD_MAX = intPreferencesKey("loudness_max_db")
        val SHOW_REVERB = booleanPreferencesKey("show_reverb")
        val SHOW_BALANCE = booleanPreferencesKey("show_balance")
        val FRAME_MS = intPreferencesKey("frame_duration_ms")
        val START_ON_BOOT = booleanPreferencesKey("start_on_boot")
        val WORKING = stringPreferencesKey("working_state")
        val ACTIVE_PROFILE = longPreferencesKey("active_profile_id")
    }
}
