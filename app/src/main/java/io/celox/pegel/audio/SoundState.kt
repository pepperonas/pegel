package io.celox.pegel.audio

import kotlin.math.roundToInt

const val BAND_COUNT = 10
const val GAIN_LIMIT_DB = 12f
const val GAIN_STEP_DB = 0.5f

/** Room sizes of the reverb effect; [NONE] switches it off. */
enum class ReverbRoom { NONE, SMALL_ROOM, MEDIUM_ROOM, LARGE_ROOM, MEDIUM_HALL, LARGE_HALL, PLATE }

/**
 * Everything the user can shape about the sound — what a profile stores. Always [normalized]
 * before it is persisted or applied, so no other layer has to distrust the numbers.
 */
data class SoundState(
    val eqOn: Boolean = true,
    val gainsDb: List<Float> = List(BAND_COUNT) { 0f },
    /** The preset the gains came from; null once a band was moved by hand. */
    val presetId: String? = "flat",
    val bassOn: Boolean = false,
    val bassStrength: Float = 0.5f,
    val loudnessOn: Boolean = false,
    val loudnessStrength: Float = 0.3f,
    val virtualizerOn: Boolean = false,
    val virtualizerStrength: Float = 0.5f,
    val reverb: ReverbRoom = ReverbRoom.NONE,
    /** -1 = all left, 0 = centre, +1 = all right. */
    val balance: Float = 0f,
) {
    fun normalized(): SoundState = copy(
        gainsDb = List(BAND_COUNT) { snapGain(gainsDb.getOrElse(it) { 0f }) },
        bassStrength = unit(bassStrength),
        loudnessStrength = unit(loudnessStrength),
        virtualizerStrength = unit(virtualizerStrength),
        balance = if (balance.isNaN()) 0f else balance.coerceIn(-1f, 1f),
    )

    fun withGain(band: Int, db: Float): SoundState {
        if (band !in 0 until BAND_COUNT) return this
        val n = normalized()
        return n.copy(gainsDb = n.gainsDb.toMutableList().also { it[band] = snapGain(db) }, presetId = null)
    }

    fun withPreset(p: Preset): SoundState = copy(gainsDb = p.gainsDb, presetId = p.id).normalized()

    companion object {
        fun snapGain(db: Float): Float {
            if (db.isNaN()) return 0f
            return (db.coerceIn(-GAIN_LIMIT_DB, GAIN_LIMIT_DB) / GAIN_STEP_DB).roundToInt() * GAIN_STEP_DB
        }

        private fun unit(v: Float) = if (v.isNaN()) 0f else v.coerceIn(0f, 1f)
    }
}

/** How strong "100 %" is — global settings, not part of a profile. */
data class Tuning(val bassCutoffHz: Int = 80, val bassMaxDb: Int = 15, val loudnessMaxDb: Int = 20) {
    fun normalized(): Tuning = Tuning(
        bassCutoffHz = snap(bassCutoffHz, 10, 10, 150),
        bassMaxDb = snap(bassMaxDb, 5, 5, 30),
        loudnessMaxDb = snap(loudnessMaxDb, 5, 5, 30),
    )

    companion object {
        val BASS_CUTOFFS_HZ = (10..150 step 10).toList()
        val MAX_GAINS_DB = (5..30 step 5).toList()

        private fun snap(v: Int, step: Int, min: Int, max: Int) =
            ((v.toFloat() / step).roundToInt() * step).coerceIn(min, max)
    }
}
