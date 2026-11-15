package io.celox.pegel.data

import androidx.room.withTransaction
import io.celox.pegel.audio.Preset
import io.celox.pegel.audio.SoundState
import io.celox.pegel.audio.SoundStateJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONException

data class Profile(val id: Long, val name: String, val state: SoundState)

enum class NameError { EMPTY, DUPLICATE }
class NameException(val error: NameError) : Exception(error.name)

const val NAME_MAX = 40

/** Stored in [DeviceEntity.suggestion]: the user took a suggestion / said "not now". */
const val SUGGESTION_DONE = "done"
const val SUGGESTION_DISMISSED = "dismissed"
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

    // A slider produces dozens of changes a second. They take effect at once (the flows below);
    // storing them is left to one writer that always writes the *latest* state, so a burst costs
    // a couple of disk writes instead of one per step — and an older write can never land after a
    // newer one.
    private val writeLock = Mutex()
    private val dirty = Channel<Unit>(Channel.CONFLATED)
    private val writer = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        writer.launch { for (signal in dirty) persistWorking() }
    }

    private suspend fun persistWorking() = writeLock.withLock {
        store.writeWorking(_state.value, _active.value?.id)
    }

    /** Waits until the sound being edited is on disk. */
    suspend fun flush() = persistWorking()

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
        setWorking(transform(_state.value).normalized(), _active.value, wait = false)
    }

    suspend fun updateSettings(transform: (Settings) -> Settings) = lock.withLock {
        val next = transform(_settings.value).normalized()
        if (next == _settings.value) return@withLock
        _settings.value = next
        store.writeSettings(next)
    }

    /** [wait] = false for the slider path; profile operations are rare and wait for the disk. */
    private suspend fun setWorking(state: SoundState, active: Profile?, wait: Boolean = true) {
        _state.value = state
        _active.value = active
        _modified.value = active != null && active.state != state
        if (wait) persistWorking() else dirty.trySend(Unit)
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

    /** The output auto-apply acted on last — stored, so a restart on the same output is not a "change". */
    @Volatile var lastDeviceKey: String? = stored.lastDeviceKey
        private set

    suspend fun rememberDevice(key: String) {
        if (key == lastDeviceKey) return
        lastDeviceKey = key
        store.writeLastDevice(key)
    }

    /** Remembers an output device so a profile can be bound to it; keeps an existing binding. */
    suspend fun seeDevice(key: String, label: String) = lock.withLock {
        val old = db.devices().byKey(key)
        db.devices().upsert(DeviceEntity(key, label, old?.profileId, now(), old?.type, old?.suggestion))
    }

    suspend fun bindDevice(key: String, profileId: Long?) = lock.withLock {
        val old = db.devices().byKey(key)
        db.devices().upsert((old ?: DeviceEntity(key, key, null, now())).copy(profileId = profileId))
    }

    private suspend fun editDevice(key: String, label: String, change: (DeviceEntity) -> DeviceEntity) {
        db.devices().upsert(change(db.devices().byKey(key) ?: DeviceEntity(key, label, null, now())))
    }

    /** What the user said the device is — a [io.celox.pegel.device.DeviceType] name. */
    suspend fun setDeviceType(key: String, label: String, type: String?) = lock.withLock {
        editDevice(key, label) { it.copy(type = type) }
    }

    /** "Not now": Pegel does not offer a sound for this device again on its own. */
    suspend fun dismissSuggestion(key: String, label: String) = lock.withLock {
        editDevice(key, label) { it.copy(suggestion = SUGGESTION_DISMISSED) }
    }

    /**
     * Takes a suggested curve: a new profile that holds the curve and nothing else, bound to the
     * device and loaded. A taken name gets a number — a suggestion must not fail on a name.
     */
    suspend fun applySuggestion(
        key: String, label: String, profileName: String, gainsDb: List<Float>,
        /** False for a device that is not the active output: its profile must not replace the sound in use. */
        load: Boolean = true,
    ): Long = lock.withLock {
        val base = cleanName(profileName) ?: cleanName(label) ?: "Pegel"
        var name = base
        var n = 2
        while (db.profiles().byName(name) != null) name = "${base.take(NAME_MAX - 4).trim()} ${n++}"
        val state = SoundState(gainsDb = gainsDb, presetId = null).normalized()
        val id = db.profiles().insert(ProfileEntity(name = name, stateJson = SoundStateJson.encode(state), updatedAt = now()))
        editDevice(key, label) { it.copy(profileId = id, suggestion = SUGGESTION_DONE) }
        if (load) setWorking(state, Profile(id, name, state))
        id
    }

    suspend fun forgetDevice(key: String) = db.devices().delete(key)

    suspend fun profileForDevice(key: String): Profile? =
        db.devices().byKey(key)?.profileId?.let { db.profiles().byId(it) }?.toProfile()

    // ---- backup and sharing ----

    suspend fun exportAll(): BackupData = lock.withLock {
        val profiles = db.profiles().all().map { it.toProfile() }
        val names = profiles.associate { it.id to it.name }
        BackupData(
            profiles = profiles.map { BackupProfile(it.name, it.state) },
            presets = db.presets().all().map { it.toPreset() }.map { BackupPreset(it.name, it.gainsDb) },
            devices = db.devices().all().map { BackupDevice(it.key, it.label, names[it.profileId], it.type, it.suggestion) },
            settings = _settings.value,
            working = _state.value,
            activeProfile = _active.value?.name,
        )
    }

    suspend fun backupText(): String = BackupCodec.encode(exportAll())

    /**
     * Replaces profiles, presets, devices, settings and the working sound with [d]. The master
     * switch keeps its current position: reading a file must never switch Pegel on or off.
     */
    suspend fun importAll(d: BackupData) = lock.withLock {
        val ids = LinkedHashMap<String, Long>() // lower-case name → new row id
        val now = now()
        db.withTransaction {
            db.devices().clear(); db.presets().clear(); db.profiles().clear()
            for (p in d.profiles) {
                val name = cleanName(p.name) ?: continue
                if (name.lowercase() in ids) continue // names are unique ignoring case: the first one wins
                ids[name.lowercase()] = db.profiles().insert(ProfileEntity(name = name, stateJson = SoundStateJson.encode(p.state), updatedAt = now))
            }
            val presetNames = HashSet<String>()
            for (p in d.presets) {
                val name = cleanName(p.name) ?: continue
                if (!presetNames.add(name.lowercase())) continue
                val gains = SoundState(gainsDb = p.gainsDb).normalized().gainsDb
                db.presets().insert(PresetEntity(name = name, gainsJson = JSONArray().also { a -> gains.forEach { a.put(it.toDouble()) } }.toString()))
            }
            for (dev in d.devices) {
                db.devices().upsert(DeviceEntity(dev.key, dev.label, dev.profile?.let { ids[it.trim().lowercase()] }, now, dev.type, dev.suggestion))
            }
        }
        val settings = d.settings.normalized().copy(enabled = _settings.value.enabled)
        _settings.value = settings
        store.writeSettings(settings)
        val active = d.activeProfile?.let { ids[it.trim().lowercase()] }?.let { db.profiles().byId(it) }?.toProfile()
        setWorking(d.working.normalized(), active)
    }

    /** Reads a backup file's text and restores it; a file that is refused changes nothing. */
    suspend fun restoreFrom(text: String): Result<Unit> = BackupCodec.decode(text).map { importAll(it) }

    /** The shareable text of one profile, or null if it no longer exists. */
    suspend fun profileText(id: Long): String? =
        db.profiles().byId(id)?.toProfile()?.let { BackupCodec.encodeProfile(it.name, it.state) }

    /** Adds the profile from a shared file under a free name; the current sound is left alone. */
    suspend fun importProfile(text: String): Result<Long> = BackupCodec.decodeProfile(text).map { p ->
        lock.withLock {
            var name = p.name
            var n = 2
            while (db.profiles().byName(name) != null) {
                val suffix = " ($n)"
                name = p.name.take(NAME_MAX - suffix.length).trim() + suffix
                n++
            }
            db.profiles().insert(ProfileEntity(name = name, stateJson = SoundStateJson.encode(p.state), updatedAt = now()))
        }
    }

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
