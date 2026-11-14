package io.celox.pegel.data

import io.celox.pegel.audio.Preset
import io.celox.pegel.audio.SoundState
import io.celox.pegel.audio.SoundStateJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONException

data class Profile(val id: Long, val name: String, val state: SoundState)

enum class NameError { EMPTY, DUPLICATE }
class NameException(val error: NameError) : Exception(error.name)

const val NAME_MAX = 40
const val CUSTOM_PRESET_PREFIX = "custom:"

/**
 * The single source of truth for the service and the UI: the sound being edited ([state]), the
 * settings, the saved profiles and which devices they belong to.
 *
 * [state], [settings], [activeProfile] and [modified] are updated synchronously inside each
 * operation, so a caller sees its own change the moment the call returns.
 */
class SoundRepository private constructor(
    internal val db: PegelDatabase,
    private val store: SettingsStore,
    stored: Stored,
    active: Profile?,
) {
    private val lock = Mutex()

    private val _state = MutableStateFlow(stored.working)
    val state: StateFlow<SoundState> = _state

    private val _settings = MutableStateFlow(stored.settings)
    val settings: StateFlow<Settings> = _settings

    private val _active = MutableStateFlow(active)
    val activeProfile: StateFlow<Profile?> = _active

    private val _modified = MutableStateFlow(active != null && active.state != stored.working)
    val modified: StateFlow<Boolean> = _modified

    val profiles: Flow<List<Profile>> = db.profiles().observeAll().map { rows -> rows.map { it.toProfile() } }
    val customPresets: Flow<List<Preset>> = db.presets().observeAll().map { rows -> rows.map { it.toPreset() } }
    val devices: Flow<List<DeviceEntity>> = db.devices().observeAll()

    // ---- the sound being edited ----

    suspend fun update(transform: (SoundState) -> SoundState) = lock.withLock {
        setWorking(transform(_state.value).normalized(), _active.value)
    }

    suspend fun updateSettings(transform: (Settings) -> Settings) = lock.withLock {
        val next = transform(_settings.value).normalized()
        if (next == _settings.value) return@withLock
        _settings.value = next
        store.writeSettings(next)
    }

    private suspend fun setWorking(state: SoundState, active: Profile?) {
        _state.value = state
        _active.value = active
        _modified.value = active != null && active.state != state
        store.writeWorking(state, active?.id)
    }

    // ---- profiles ----

    suspend fun loadProfile(id: Long) = lock.withLock {
        val p = db.profiles().byId(id)?.toProfile() ?: return@withLock
        setWorking(p.state, p)
    }

    suspend fun saveActiveProfile() = lock.withLock {
        val active = _active.value ?: return@withLock
        val state = _state.value
        db.profiles().setState(active.id, SoundStateJson.encode(state), now())
        setWorking(state, active.copy(state = state))
    }

    suspend fun saveAsProfile(name: String): Result<Long> = lock.withLock {
        val clean = cleanName(name) ?: return@withLock fail(NameError.EMPTY)
        if (db.profiles().byName(clean) != null) return@withLock fail(NameError.DUPLICATE)
        val state = _state.value
        val id = db.profiles().insert(ProfileEntity(name = clean, stateJson = SoundStateJson.encode(state), updatedAt = now()))
        setWorking(state, Profile(id, clean, state))
        Result.success(id)
    }

    suspend fun renameProfile(id: Long, name: String): Result<Unit> = lock.withLock {
        val clean = cleanName(name) ?: return@withLock fail(NameError.EMPTY)
        val taken = db.profiles().byName(clean)
        if (taken != null && taken.id != id) return@withLock fail(NameError.DUPLICATE)
        db.profiles().setName(id, clean, now())
        _active.value?.takeIf { it.id == id }?.let { _active.value = it.copy(name = clean) }
        Result.success(Unit)
    }

    suspend fun deleteProfile(id: Long) = lock.withLock {
        db.profiles().delete(id) // bound devices lose their binding through the foreign key
        if (_active.value?.id == id) setWorking(_state.value, null)
    }

    suspend fun discardChanges() = lock.withLock {
        val active = _active.value ?: return@withLock
        setWorking(active.state, active)
    }

    // ---- custom presets ----

    suspend fun savePreset(name: String, gainsDb: List<Float>): Result<Long> = lock.withLock {
        val clean = cleanName(name) ?: return@withLock fail(NameError.EMPTY)
        if (db.presets().byName(clean) != null) return@withLock fail(NameError.DUPLICATE)
        val gains = SoundState(gainsDb = gainsDb).normalized().gainsDb
        val json = JSONArray().also { a -> gains.forEach { a.put(it.toDouble()) } }.toString()
        Result.success(db.presets().insert(PresetEntity(name = clean, gainsJson = json)))
    }

    suspend fun deletePreset(id: String) {
        val rowId = id.removePrefix(CUSTOM_PRESET_PREFIX).toLongOrNull() ?: return
        if (id.startsWith(CUSTOM_PRESET_PREFIX)) db.presets().delete(rowId)
    }

    // ---- devices ----

    /** Remembers an output device so a profile can be bound to it; keeps an existing binding. */
    suspend fun seeDevice(key: String, label: String) = lock.withLock {
        val old = db.devices().byKey(key)
        db.devices().upsert(DeviceEntity(key, label, old?.profileId, now()))
    }

    suspend fun bindDevice(key: String, profileId: Long?) = lock.withLock {
        val old = db.devices().byKey(key)
        db.devices().upsert(DeviceEntity(key, old?.label ?: key, profileId, old?.lastSeen ?: now()))
    }

    suspend fun forgetDevice(key: String) = db.devices().delete(key)

    suspend fun profileForDevice(key: String): Profile? =
        db.devices().byKey(key)?.profileId?.let { db.profiles().byId(it) }?.toProfile()

    companion object {
        suspend fun create(db: PegelDatabase, store: SettingsStore): SoundRepository {
            val stored = store.read()
            val active = stored.activeProfileId?.let { db.profiles().byId(it) }?.toProfile()
            return SoundRepository(db, store, stored, active)
        }

        /** Trimmed and capped; null when nothing is left. */
        fun cleanName(name: String): String? = name.trim().take(NAME_MAX).trim().ifEmpty { null }

        private fun <T> fail(e: NameError): Result<T> = Result.failure(NameException(e))
        private fun now() = System.currentTimeMillis()

        /** A row that cannot be read becomes a flat sound rather than a crash. */
        internal fun ProfileEntity.toProfile() = Profile(id, name, SoundStateJson.decode(stateJson) ?: SoundState())

        internal fun PresetEntity.toPreset(): Preset {
            val gains = try { SoundStateJson.gains(JSONArray(gainsJson)) } catch (_: JSONException) { null }
            return Preset("$CUSTOM_PRESET_PREFIX$id", name, SoundState(gainsDb = gains ?: emptyList()).normalized().gainsDb, false)
        }
    }
}
