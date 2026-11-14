package io.celox.pegel.audio

import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Upper edge of each post-EQ band: halfway (geometrically) to the next centre, the last at 20 kHz. */
val POST_EQ_CUTOFFS_HZ: List<Float> = List(BAND_COUNT) { i ->
    if (i == BAND_COUNT - 1) 20000f else sqrt(BAND_CENTERS_HZ[i] * BAND_CENTERS_HZ[i + 1])
}

/** The far channel is never turned down further than this — it is silence for every practical purpose. */
const val BALANCE_FLOOR_DB = -60f

/**
 * What an effect chain has to do, in the units the audio effects take. A switched-off effect is
 * expressed as its neutral value, so a chain never needs to know about switches.
 */
data class ChainPlan(
    val postEqGainsDb: List<Float>,
    val bassCutoffHz: Float,
    val bassGainDb: Float,
    /** Loudness: gain pushed *into* the limiter (see [inputGainsDb]). */
    val loudnessGainDb: Float,
    val leftGainDb: Float,
    val rightGainDb: Float,
    /** 0..1000, or null when the virtualizer is off. */
    val virtualizerStrength: Int?,
    val reverb: ReverbRoom,
)

fun planFor(state: SoundState, tuning: Tuning): ChainPlan {
    val s = state.normalized()
    val t = tuning.normalized()
    return ChainPlan(
        postEqGainsDb = if (s.eqOn) s.gainsDb else List(BAND_COUNT) { 0f },
        bassCutoffHz = t.bassCutoffHz.toFloat(),
        bassGainDb = if (s.bassOn) s.bassStrength * t.bassMaxDb else 0f,
        loudnessGainDb = if (s.loudnessOn) s.loudnessStrength * t.loudnessMaxDb else 0f,
        leftGainDb = if (s.balance > 0f) attenuation(s.balance) else 0f,
        rightGainDb = if (s.balance < 0f) attenuation(-s.balance) else 0f,
        virtualizerStrength = if (s.virtualizerOn) (s.virtualizerStrength * 1000).roundToInt() else null,
        reverb = s.reverb,
    )
}

/**
 * Nothing is added behind the limiter: gain there would clip. Loudness raises the level in front
 * of it instead, which is exactly what the limiter is there to catch.
 */
const val LIMITER_POST_GAIN_DB = 0f

/** Input gain per channel (left, right): balance attenuation plus the loudness gain. */
fun inputGainsDb(plan: ChainPlan): Pair<Float, Float> =
    (plan.leftGainDb + plan.loudnessGainDb) to (plan.rightGainDb + plan.loudnessGainDb)

/** Linear in amplitude: halfway to one side halves the other side (-6 dB). */
private fun attenuation(amount: Float): Float =
    (20 * log10(1f - amount)).coerceAtLeast(BALANCE_FLOOR_DB) // log10(0) is -infinity, hence the floor
