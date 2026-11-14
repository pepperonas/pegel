package io.celox.pegel.audio

import kotlin.math.abs
import kotlin.math.min

/**
 * The line drawn above the sliders: [samples] values in dB, evenly spaced over the band axis (the
 * first is band 0, the last the top band). Monotone cubic interpolation (Fritsch–Carlson), so the
 * line passes through every slider and never swings beyond what the sliders show.
 *
 * It is a picture of the settings, not a measured frequency response.
 */
fun curveSamples(gainsDb: List<Float>, samples: Int): List<Float> {
    require(samples >= 2) { "a curve needs at least two samples" }
    val n = gainsDb.size
    if (n == 0) return List(samples) { 0f }
    if (n == 1) return List(samples) { gainsDb[0] }

    // Secant slopes between neighbours (bands are one unit apart).
    val d = FloatArray(n - 1) { gainsDb[it + 1] - gainsDb[it] }
    val m = FloatArray(n)
    m[0] = d[0]
    m[n - 1] = d[n - 2]
    for (i in 1 until n - 1) m[i] = (d[i - 1] + d[i]) / 2f
    // Limit the tangents so no segment overshoots its end points.
    for (i in 0 until n - 1) {
        if (d[i] == 0f) { m[i] = 0f; m[i + 1] = 0f; continue }
        val limit = 3f * abs(d[i])
        if (abs(m[i]) > limit) m[i] = if (m[i] > 0) limit else -limit
        if (abs(m[i + 1]) > limit) m[i + 1] = if (m[i + 1] > 0) limit else -limit
        // A tangent pointing against the segment would create a bump (this also flattens the
        // curve at every peak and dip, where the neighbouring slopes disagree).
        if (m[i] * d[i] < 0f) m[i] = 0f
        if (m[i + 1] * d[i] < 0f) m[i + 1] = 0f
    }

    return List(samples) { k ->
        val x = k.toFloat() * (n - 1) / (samples - 1)
        val i = min(x.toInt(), n - 2)
        val t = x - i
        val t2 = t * t
        val t3 = t2 * t
        val a = gainsDb[i]; val b = gainsDb[i + 1]
        val v = (2 * t3 - 3 * t2 + 1) * a + (t3 - 2 * t2 + t) * m[i] + (-2 * t3 + 3 * t2) * b + (t3 - t2) * m[i + 1]
        // Guard against the last bit of float error at the extremes.
        v.coerceIn(min(a, b), maxOf(a, b))
    }
}
