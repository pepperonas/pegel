package io.celox.pegel.audio

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** [SoundState] as JSON — the format of profiles in the database, backups and shared files. */
object SoundStateJson {

    fun toJson(s: SoundState): JSONObject = JSONObject().apply {
        put("eqOn", s.eqOn)
        put("gainsDb", JSONArray().also { a -> s.gainsDb.forEach { a.put(it.toDouble()) } })
        put("presetId", s.presetId ?: JSONObject.NULL)
        put("bassOn", s.bassOn)
        put("bassStrength", s.bassStrength.toDouble())
        put("loudnessOn", s.loudnessOn)
        put("loudnessStrength", s.loudnessStrength.toDouble())
        put("virtualizerOn", s.virtualizerOn)
        put("virtualizerStrength", s.virtualizerStrength.toDouble())
        put("reverb", s.reverb.name)
        put("balance", s.balance.toDouble())
    }

    /** Tolerant: missing or mistyped fields take their default, numbers are clamped. */
    fun fromJson(o: JSONObject): SoundState {
        val d = SoundState()
        return SoundState(
            eqOn = o.optBoolean("eqOn", d.eqOn),
            gainsDb = gains(o.optJSONArray("gainsDb")) ?: d.gainsDb,
            presetId = if (o.has("presetId")) o.optString("presetId").takeIf { !o.isNull("presetId") && it.isNotBlank() }
            else d.presetId,
            bassOn = o.optBoolean("bassOn", d.bassOn),
            bassStrength = o.optDouble("bassStrength", d.bassStrength.toDouble()).toFloat(),
            loudnessOn = o.optBoolean("loudnessOn", d.loudnessOn),
            loudnessStrength = o.optDouble("loudnessStrength", d.loudnessStrength.toDouble()).toFloat(),
            virtualizerOn = o.optBoolean("virtualizerOn", d.virtualizerOn),
            virtualizerStrength = o.optDouble("virtualizerStrength", d.virtualizerStrength.toDouble()).toFloat(),
            reverb = ReverbRoom.entries.firstOrNull { it.name == o.optString("reverb") } ?: ReverbRoom.NONE,
            balance = o.optDouble("balance", d.balance.toDouble()).toFloat(),
        ).normalized()
    }

    fun gains(a: JSONArray?): List<Float>? = a?.let { arr -> List(arr.length()) { arr.optDouble(it, 0.0).toFloat() } }

    fun encode(s: SoundState): String = toJson(s).toString()

    /** Null when [json] is not a JSON object at all. */
    fun decode(json: String): SoundState? = try {
        fromJson(JSONObject(json))
    } catch (_: JSONException) {
        null
    }
}
