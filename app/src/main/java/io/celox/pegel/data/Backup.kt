package io.celox.pegel.data

import io.celox.pegel.audio.SessionMode
import io.celox.pegel.audio.SoundState
import io.celox.pegel.audio.SoundStateJson
import io.celox.pegel.audio.Tuning
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

const val BACKUP_FORMAT = 1

data class BackupProfile(val name: String, val state: SoundState)
data class BackupPreset(val name: String, val gainsDb: List<Float>)

/** [profile] is the *name* of the bound profile — row ids mean nothing on another phone. */
data class BackupDevice(
    val key: String, val label: String, val profile: String?,
    val type: String? = null, val suggestion: String? = null,
)

data class BackupData(
    val profiles: List<BackupProfile>,
    val presets: List<BackupPreset>,
    val devices: List<BackupDevice>,
    val settings: Settings,
    val working: SoundState,
    val activeProfile: String?,
)

sealed interface BackupError {
    data object Empty : BackupError
    data object NotJson : BackupError
    /** JSON, but not written by Pegel (or missing what makes it usable). */
    data object NotPegel : BackupError
    /** A full backup where a single profile was expected, or the other way round. */
    data object WrongKind : BackupError
    /** Written by a newer Pegel than this one. */
    data class NewerFormat(val found: Int) : BackupError
}

class BackupException(val error: BackupError) : Exception(error.toString())

/**
 * The file format of backups (`kind: backup`) and shared profiles (`kind: profile`). Reading is
 * strict about *what* the file is and lenient about its contents: a damaged entry is skipped so
 * the rest of a backup still arrives.
 */
object BackupCodec {
    private const val APP = "pegel"
    private const val KIND_BACKUP = "backup"
    private const val KIND_PROFILE = "profile"

    fun encode(d: BackupData): String = header(KIND_BACKUP).apply {
        put("settings", JSONObject().apply {
            val s = d.settings
            put("enabled", s.enabled)
            put("mode", s.mode.name)
            put("bassCutoffHz", s.tuning.bassCutoffHz)
            put("bassMaxDb", s.tuning.bassMaxDb)
            put("loudnessMaxDb", s.tuning.loudnessMaxDb)
            put("showReverb", s.showReverb)
            put("showBalance", s.showBalance)
            put("frameDurationMs", s.frameDurationMs)
            put("startOnBoot", s.startOnBoot)
        })
        put("working", SoundStateJson.toJson(d.working))
        put("activeProfile", d.activeProfile ?: JSONObject.NULL)
        put("profiles", JSONArray().also { a -> d.profiles.forEach { a.put(profileJson(it.name, it.state)) } })
        put("presets", JSONArray().also { a ->
            d.presets.forEach { p ->
                a.put(JSONObject().put("name", p.name).put("gains", JSONArray().also { g -> p.gainsDb.forEach { g.put(it.toDouble()) } }))
            }
        })
        put("devices", JSONArray().also { a ->
            d.devices.forEach { 
                val o = JSONObject().put("key", it.key).put("label", it.label).put("profile", it.profile ?: JSONObject.NULL)
                it.type?.let { t -> o.put("type", t) }
                it.suggestion?.let { s -> o.put("suggestion", s) }
                a.put(o)
            }
        })
    }.toString(2)

    fun decode(text: String): Result<BackupData> = open(text, KIND_BACKUP).map { o ->
        val def = Settings()
        val s = o.optJSONObject("settings") ?: JSONObject()
        BackupData(
            profiles = objects(o.optJSONArray("profiles")).mapNotNull(::profileOf),
            presets = objects(o.optJSONArray("presets")).mapNotNull { p ->
                val name = SoundRepository.cleanName(p.optString("name")) ?: return@mapNotNull null
                val gains = SoundStateJson.gains(p.optJSONArray("gains")) ?: return@mapNotNull null
                BackupPreset(name, SoundState(gainsDb = gains).normalized().gainsDb)
            },
            devices = objects(o.optJSONArray("devices")).mapNotNull { d ->
                val key = d.optString("key").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                BackupDevice(key, d.optString("label"), text(d, "profile"), text(d, "type"), text(d, "suggestion"))
            },
            settings = Settings(
                enabled = s.optBoolean("enabled", def.enabled),
                mode = SessionMode.entries.firstOrNull { it.name == s.optString("mode") } ?: def.mode,
                tuning = Tuning(
                    s.optInt("bassCutoffHz", def.tuning.bassCutoffHz),
                    s.optInt("bassMaxDb", def.tuning.bassMaxDb),
                    s.optInt("loudnessMaxDb", def.tuning.loudnessMaxDb),
                ),
                showReverb = s.optBoolean("showReverb", def.showReverb),
                showBalance = s.optBoolean("showBalance", def.showBalance),
                frameDurationMs = s.optInt("frameDurationMs", def.frameDurationMs),
                startOnBoot = s.optBoolean("startOnBoot", def.startOnBoot),
            ).normalized(),
            working = o.optJSONObject("working")?.let(SoundStateJson::fromJson) ?: SoundState(),
            activeProfile = text(o, "activeProfile"),
        )
    }

    fun encodeProfile(name: String, s: SoundState): String =
        header(KIND_PROFILE).put("profile", profileJson(name, s)).toString(2)

    fun decodeProfile(text: String): Result<BackupProfile> = open(text, KIND_PROFILE).mapCatching { o ->
        o.optJSONObject("profile")?.let(::profileOf) ?: throw BackupException(BackupError.NotPegel)
    }

    private fun header(kind: String) = JSONObject().put("app", APP).put("format", BACKUP_FORMAT).put("kind", kind)

    private fun profileJson(name: String, s: SoundState) = JSONObject().put("name", name).put("state", SoundStateJson.toJson(s))

    private fun profileOf(o: JSONObject): BackupProfile? {
        val name = SoundRepository.cleanName(o.optString("name")) ?: return null
        val state = o.optJSONObject("state") ?: return null
        return BackupProfile(name, SoundStateJson.fromJson(state))
    }

    /** Checks that [text] is a Pegel file of [kind] in a format this version understands. */
    private fun open(text: String, kind: String): Result<JSONObject> {
        if (text.isBlank()) return fail(BackupError.Empty)
        val o = try { JSONObject(text) } catch (_: JSONException) { return fail(BackupError.NotJson) }
        if (o.optString("app") != APP) return fail(BackupError.NotPegel)
        val format = o.opt("format") as? Int ?: return fail(BackupError.NotPegel)
        if (format > BACKUP_FORMAT) return fail(BackupError.NewerFormat(format))
        if (format < 1) return fail(BackupError.NotPegel)
        if (o.optString("kind") != kind) return fail(BackupError.WrongKind)
        return Result.success(o)
    }

    private fun <T> fail(e: BackupError): Result<T> = Result.failure(BackupException(e))

    private fun objects(a: JSONArray?): List<JSONObject> =
        if (a == null) emptyList() else (0 until a.length()).mapNotNull { a.optJSONObject(it) }

    private fun text(o: JSONObject, key: String): String? =
        if (o.isNull(key)) null else o.optString(key).takeIf { it.isNotBlank() }
}
